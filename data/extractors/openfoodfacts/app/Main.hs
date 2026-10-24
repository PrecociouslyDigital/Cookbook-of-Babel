{- | @babel-openfoodfacts extract@: recreate the extracted OpenFoodFacts
layer from the files @fetch.sh@ leaves in the cache, the dataset's
ingredients and the committed overrides. The report says what became of
every ingredient.
-}
module Main (main) where

import Autodocodec (HasCodec)
import Babel.Codec (IsId (..))
import Babel.Format (render)
import Babel.Layout (Kind (..), Layer (..), Placement (..), placementFolders)
import Babel.Load (Located (..), World (..), loadWorld, withoutExtracted)
import Babel.Model (IngredientId, Measure (..), Origin (..), Source (..), measureKey, measureStem)
import Data.Aeson qualified as Aeson
import Data.ByteString.Lazy qualified as BL
import Data.Map.Strict qualified as Map
import Data.Text qualified as T
import Data.Text.Lazy qualified as TL
import Data.Text.Lazy.Encoding qualified as TL
import OpenFoodFacts.Aggregate (tallyLines)
import OpenFoodFacts.Emit (Extraction (..), Row (..), emit, matchAll, outcomeName, wanted)
import OpenFoodFacts.Match (Override (..), englishIndex)
import OpenFoodFacts.Taxonomy (Taxonomy (..), mkTag, parseTaxonomy, tagText)
import Options.Applicative (Parser, command, execParser, help, helper, hsubparser, info, long, metavar, progDesc, showDefault, strOption, value)
import System.Directory (createDirectoryIfMissing, doesDirectoryExist, removeDirectoryRecursive)
import System.FilePath (joinPath, takeDirectory, (</>))
import System.IO (hSetEncoding, utf8)

data Paths = Paths {dataRoot :: FilePath, cache :: FilePath, matches :: FilePath, report :: FilePath}

main :: IO ()
main = do
    mapM_ (`hSetEncoding` utf8) [stdout, stderr]
    execParser (info (hsubparser (command "extract" (info paths (progDesc "Recreate the extracted OpenFoodFacts layer"))) <**> helper) mempty) >>= run

paths :: Parser Paths
paths =
    Paths
        <$> path "data" "data" "Data root"
        <*> path "cache" ".cache/openfoodfacts" "Where fetch.sh left the taxonomy, products and dataset URL"
        <*> path "matches" "data/extractors/openfoodfacts/matches.json" "Committed overrides: ingredient ID to category tag, or null"
        <*> path "report" "data/extractors/openfoodfacts/report.tsv" "Where to write the report"
  where
    path name def h = strOption (long name <> metavar "PATH" <> value def <> showDefault <> help h)

run :: Paths -> IO ()
run p = do
    (_, loaded) <- loadWorld False p.dataRoot
    let world = withoutExtracted OpenFoodFacts loaded
    taxonomy <- parseTaxonomy . decodeUtf8 <$> readFileBS (p.cache </> "categories.txt")
    url <- T.strip . decodeUtf8 <$> readFileBS (p.cache </> "dataset-url")
    overrides <- either die pure . readOverrides taxonomy =<< readFileLBS p.matches
    let ms = matchAll (englishIndex taxonomy) overrides [i.value | i <- Map.elems world.ingredients]
        want = wanted ms
    rows <- drop 1 . TL.lines . TL.decodeUtf8With lenientDecode <$> readFileLBS (p.cache </> "products.tsv")
    let x = emit url (tallyLines taxonomy want (map TL.toStrict rows)) ms
    writeLayer p.dataRoot x
    writeFileText p.report (renderReport [i | i <- Map.keys overrides, not (Map.member i world.ingredients)] x)

{- | @matches.json@ maps ingredient IDs to a category tag, or to @null@
when no category fits. Every tag must be in the taxonomy.
-}
readOverrides :: Taxonomy -> LByteString -> Either String (Map IngredientId Override)
readOverrides taxonomy bytes = do
    raw <- Aeson.eitherDecode bytes :: Either String (Map Text (Maybe Text))
    fmap Map.fromList . forM (Map.toList raw) $ \(k, v) -> do
        i <- mkId k
        o <- case v of
            Nothing -> Right NoCategory
            Just t -> case mkTag t of
                Just tag | Map.member tag taxonomy.categories -> Right (MatchTo tag)
                _ -> Left ("matches.json: " <> toString k <> ": " <> toString t <> " is not a category in the taxonomy")
        pure (i, o)

-- | The extracted OpenFoodFacts folders hold exactly this extraction's entities.
writeLayer :: FilePath -> Extraction -> IO ()
writeLayer root x = do
    let folder kind = root </> "layers" </> joinPath [toString f | (_, f) <- placementFolders (Placement Extracted OpenFoodFacts kind)]
    replaceFolder (takeDirectory (folder Sources))
    write (folder Sources) [(idText x.source.id, x.source)]
    write (folder Measures) [(measureStem m.ingredient (measureKey m.value), m) | m <- x.measures]
  where
    write :: (HasCodec a) => FilePath -> [(Text, a)] -> IO ()
    write dir entities = unless (null entities) $ do
        createDirectoryIfMissing True dir
        forM_ entities $ \(name, e) -> BL.writeFile (dir </> toString name <> ".json") (render e)
    replaceFolder dir = do
        exists <- doesDirectoryExist dir
        when exists (removeDirectoryRecursive dir)
        createDirectoryIfMissing True dir

-- | One row per ingredient, then one per override of an ingredient that no longer exists.
renderReport :: [IngredientId] -> Extraction -> Text
renderReport stale x =
    T.unlines $
        "ingredient\toutcome\tcategory\tdetail"
            : [T.intercalate "\t" [idText r.ingredient, outcomeName r.outcome, T.unwords (map tagText r.categories), r.detail] | r <- x.report]
                <> [idText i <> "\tstale override\t\tmatches.json names an ingredient the dataset doesn't have" | i <- stale]
