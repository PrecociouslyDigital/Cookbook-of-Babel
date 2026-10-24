module Main (main) where

import Babel.Diagnostic (isError, renderDiagnostic)
import Babel.Layout (Axis (..), axes)
import Babel.Load (World (..))
import Babel.Model (Amount (..), Constants (..), IsId (..), Per100g (..), Recipe (..), bandFromCelsius, licenceExpression)
import Babel.Nutrition (Derived (..), derivedPer100g, nutritionOf)
import Babel.Spoilage (Conditions (..), firstSpoilage)
import Babel.Validate (Report (..), validate)
import Data.Map.Strict qualified as Map
import Data.Text qualified as T
import Data.Text.IO qualified as T
import Options.Applicative (Parser, auto, command, execParser, help, helper, hsubparser, info, long, metavar, option, progDesc, showDefault, strArgument, strOption, switch, value)
import System.IO (hSetEncoding, utf8)
import Text.Printf (printf)

data Command
    = Validate FilePath Bool
    | Spoilage FilePath Text Double (Maybe Double)
    | NutritionOf FilePath (Maybe Text)
    | Layout

main :: IO ()
main = do
    mapM_ (`hSetEncoding` utf8) [stdout, stderr]
    execParser (info (commands <**> helper) (progDesc "Cookbook of Babel dataset tools")) >>= run

commands :: Parser Command
commands =
    hsubparser $
        command "validate" (info validateP (progDesc "Check the dataset; exit non-zero on any error"))
            <> command "layout" (info (pure Layout) (progDesc "Print each folder level under layers/, outermost first, with the folder names it accepts"))
            <> command
                "query"
                ( info
                    ( hsubparser
                        ( command "spoilage" (info spoilageP (progDesc "Which spoilage recipe happens first"))
                            <> command "nutrition" (info nutritionP (progDesc "Nutrition per 100 g and where it comes from; a TSV of every ingredient when none is named"))
                        )
                    )
                    (progDesc "Ask questions of the dataset")
                )
  where
    dataRoot = strArgument (metavar "DATA_ROOT" <> value "data" <> showDefault <> help "Data root")
    validateP = Validate <$> dataRoot <*> switch (long "fix" <> help "Rewrite non-canonically formatted files")
    spoilageP =
        (\ing t aw root -> Spoilage root ing t aw)
            <$> strArgument (metavar "INGREDIENT")
            <*> option auto (long "temp" <> metavar "C" <> help "Temperature in °C")
            <*> optional (option auto (long "aw" <> metavar "X" <> help "Water activity (default: the ingredient's own)"))
            <*> dataOption
    nutritionP = flip NutritionOf <$> optional (strArgument (metavar "INGREDIENT")) <*> dataOption
    dataOption = strOption (long "data" <> metavar "DATA_ROOT" <> value "data" <> showDefault <> help "Data root")

run :: Command -> IO ()
run = \case
    Layout -> mapM_ (\a -> putTextLn (unwords (a.name : a.folders))) axes
    Validate root fixFiles -> do
        report <- validate fixFiles root
        let errors = filter isError report.diagnostics
        mapM_ (T.hPutStrLn stderr . renderDiagnostic) report.diagnostics
        putTextLn ("effective licence: " <> licenceExpression report.licence)
        unless (null errors) $ do
            T.hPutStrLn stderr (show (length errors) <> " error(s)")
            exitFailure
    Spoilage root ing t aw -> do
        world <- validWorld root
        ingId <- parseId ing
        case world.constants of
            Nothing -> exitFailure
            Just c -> case firstSpoilage world (Conditions t aw) ingId of
                Nothing -> putTextLn "no time-based spoilage applies under these conditions"
                Just (r, after) ->
                    printf
                        "%s after about %.1f %s (%s)\n"
                        (idText r.id)
                        after.qty
                        (idText after.unit)
                        (idText (bandFromCelsius c.tempBands t))
    NutritionOf root ing -> do
        world <- validWorld root
        case ing of
            Just i -> do
                ingId <- parseId i
                unless (Map.member ingId world.ingredients) $ T.hPutStrLn stderr ("no ingredient " <> i) >> exitFailure
                case nutritionOf world ingId of
                    Nothing -> putTextLn "no stated nutrition, and no recipe it can be inferred from"
                    Just d -> do
                        let n = derivedPer100g d
                        forM_ [("kcal", n.kcal), ("protein g", n.proteinG), ("fat g", n.fatG), ("carbs g", n.carbsG)] $ \(name, v) ->
                            printf "%-10s %s\n" (name :: Text) (maybe "unknown" (\x -> printf "%.1f" x :: String) v)
                        putTextLn (basis d)
            Nothing -> do
                putTextLn "ingredient\tbasis\tkcal\tprotein_g\tfat_g\tcarbs_g"
                forM_ (Map.keys world.ingredients) $ \i ->
                    let d = nutritionOf world i
                        n = maybe (Per100g Nothing Nothing Nothing Nothing) derivedPer100g d
                        cell = maybe "" (\x -> toText (printf "%.1f" x :: String))
                     in putTextLn (T.intercalate "\t" [idText i, maybe "" basis d, cell n.kcal, cell n.proteinG, cell n.fatG, cell n.carbsG])
  where
    basis = \case
        Stated _ src -> "stated by " <> idText src
        Inferred r _ [] -> "inferred from " <> idText r
        Inferred r _ left -> "inferred from " <> idText r <> ", leaving out " <> T.intercalate ", " (map idText left) <> " as negligible"

-- | The loaded dataset, or exit if it has errors.
validWorld :: FilePath -> IO World
validWorld root = do
    report <- validate False root
    when (any isError report.diagnostics) $ do
        T.hPutStrLn stderr "the dataset has errors; run `babel validate` first"
        exitFailure
    pure report.world

parseId :: (IsId a) => Text -> IO a
parseId t = either (\err -> T.hPutStrLn stderr (toText err) >> exitFailure) pure (mkId t)
