module Main (main) where

import Babel.Diagnostic (isError, renderDiagnostic)
import Babel.Load (World (..))
import Babel.Model (Amount (..), Constants (..), IsId (..), Recipe (..), bandFromCelsius, licenceSpdx)
import Babel.Spoilage (Conditions (..), firstSpoilage)
import Babel.Validate (Report (..), validate)
import Data.Text.IO qualified as T
import Options.Applicative (Parser, auto, command, execParser, help, helper, hsubparser, info, long, metavar, option, progDesc, showDefault, strArgument, strOption, switch, value)
import System.IO (hSetEncoding, utf8)
import Text.Printf (printf)

data Command
    = Validate FilePath Bool
    | Spoilage FilePath Text Double (Maybe Double)

main :: IO ()
main = do
    mapM_ (`hSetEncoding` utf8) [stdout, stderr]
    execParser (info (commands <**> helper) (progDesc "Cookbook of Babel dataset tools")) >>= run

commands :: Parser Command
commands =
    hsubparser $
        command "validate" (info validateP (progDesc "Check the dataset; exit non-zero on any error"))
            <> command "query" (info (hsubparser (command "spoilage" (info spoilageP (progDesc "Which spoilage recipe happens first")))) (progDesc "Ask questions of the dataset"))
  where
    dataRoot = strArgument (metavar "DATA_ROOT" <> value "data" <> showDefault <> help "Data root")
    validateP = Validate <$> dataRoot <*> switch (long "fix" <> help "Rewrite non-canonically formatted files")
    spoilageP =
        (\ing t aw root -> Spoilage root ing t aw)
            <$> strArgument (metavar "INGREDIENT")
            <*> option auto (long "temp" <> metavar "C" <> help "Temperature in °C")
            <*> optional (option auto (long "aw" <> metavar "X" <> help "Water activity (default: the ingredient's own)"))
            <*> strOption (long "data" <> metavar "DATA_ROOT" <> value "data" <> showDefault <> help "Data root")

run :: Command -> IO ()
run = \case
    Validate root fixFiles -> do
        report <- validate fixFiles root
        let errors = filter isError report.diagnostics
        mapM_ (T.hPutStrLn stderr . renderDiagnostic) report.diagnostics
        putTextLn ("effective licence: " <> licenceSpdx report.licence)
        unless (null errors) $ do
            T.hPutStrLn stderr (show (length errors) <> " error(s)")
            exitFailure
    Spoilage root ing t aw -> do
        report <- validate False root
        when (any isError report.diagnostics) $ do
            T.hPutStrLn stderr "the dataset has errors; run `babel validate` first"
            exitFailure
        case (mkId ing, report.world.constants) of
            (Left err, _) -> T.hPutStrLn stderr (toText err) >> exitFailure
            (_, Nothing) -> exitFailure
            (Right ingId, Just c) -> case firstSpoilage report.world (Conditions t aw) ingId of
                Nothing -> putTextLn "no time-based spoilage applies under these conditions"
                Just (r, after) ->
                    printf
                        "%s after about %.1f %s (%s)\n"
                        (idText r.id)
                        after.qty
                        (idText after.unit)
                        (idText (bandFromCelsius c.tempBands t))
