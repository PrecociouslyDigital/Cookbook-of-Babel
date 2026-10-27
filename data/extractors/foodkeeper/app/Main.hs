{- | @babel-foodkeeper extract@: recreate the extracted FoodKeeper layer from
the file @fetch.sh@ leaves in the cache, the dataset, the committed tables
and the decisions answered so far. Unanswered requests go to the pending
folder for @decide.sh@; the report says what became of every raw
ingredient.
-}
module Main (main) where

import Babel.Load (World (..), loadWorld, withoutExtracted)
import Babel.Model (IngredientId, IsId (..), Origin (..))
import Data.Map.Strict qualified as Map
import Data.Set qualified as Set
import Data.Text qualified as T
import Decisions.Store (Store (..), readDecisions, writeRequests)
import FoodKeeper.Extract (Env (..), Extraction (..), describeAll, extract, renderReport, writeLayer)
import FoodKeeper.Match (Described (..))
import FoodKeeper.Source (parseFoodKeeper)
import FoodKeeper.Tables (readOverrides, readTables)
import Options.Applicative (Parser, command, execParser, help, helper, hsubparser, info, long, metavar, progDesc, showDefault, strOption, value)
import System.IO (hSetEncoding, utf8)

data Paths = Paths
    { dataRoot :: FilePath
    , source :: FilePath
    , store :: Store
    , techniques :: FilePath
    , matches :: FilePath
    , openFoodFacts :: FilePath
    , report :: FilePath
    }

main :: IO ()
main = do
    mapM_ (`hSetEncoding` utf8) [stdout, stderr]
    execParser (info (hsubparser (command "extract" (info paths (progDesc "Recreate the extracted FoodKeeper layer"))) <**> helper) mempty) >>= run

paths :: Parser Paths
paths =
    Paths
        <$> path "data" "data" "Data root"
        <*> path "source" ".cache/foodkeeper/foodkeeper.json" "The FoodKeeper file fetch.sh verified"
        <*> ( Store
                <$> path "decisions" "data/extractors/foodkeeper/decisions" "Answered decisions, one <key>.json each"
                <*> path "pending" ".cache/foodkeeper/pending" "Where to write the requests still to send"
            )
        <*> path "techniques" "data/extractors/foodkeeper/techniques.json" "How each FoodKeeper category and product spoils"
        <*> path "matches" "data/extractors/foodkeeper/matches.json" "Committed overrides: ingredient ID to product, or null"
        <*> path "openfoodfacts-report" "data/extractors/openfoodfacts/report.tsv" "The OpenFoodFacts extractor's report, for each ingredient's category"
        <*> path "report" "data/extractors/foodkeeper/report.tsv" "Where to write the report"
  where
    path name def h = strOption (long name <> metavar "PATH" <> value def <> showDefault <> help h)

run :: Paths -> IO ()
run p = do
    (_, loaded) <- loadWorld False p.dataRoot
    let world = withoutExtracted FoodKeeper loaded
    constants <- maybe (die "the constants registry does not load") pure world.constants
    fk <- either (die . ("foodkeeper.json: " <>)) pure . parseFoodKeeper =<< readFileLBS p.source
    tables <- either die pure . readTables fk constants =<< readFileLBS p.techniques
    overrides <- either die pure . readOverrides fk =<< readFileLBS p.matches
    off <- openFoodFactsCategories . decodeUtf8 <$> readFileBS p.openFoodFacts
    answered <- readDecisions p.store
    let described = describeAll world off
        x = extract Env{foodKeeper = fk, tables, overrides, url = "https://www.fsis.usda.gov/shared/data/EN/foodkeeper.json"} answered described
        asked = Set.fromList [d.id | d <- described]
    writeLayer p.dataRoot x
    writeRequests p.store answered x.pending x.referenced
    writeFileText p.report (renderReport [i | i <- Map.keys overrides, not (Set.member i asked)] x)

{- | The category each ingredient matched in the OpenFoodFacts report, when
it matched exactly one.
-}
openFoodFactsCategories :: Text -> Map IngredientId Text
openFoodFactsCategories report =
    Map.fromList
        [ (i, category)
        | row <- drop 1 (lines report)
        , _ : _ : categories : _ <- [T.splitOn "\t" row]
        , [category] <- [T.words categories]
        , Right i <- [mkId (T.takeWhile (/= '\t') row)]
        ]
