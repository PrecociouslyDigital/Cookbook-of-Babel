{- | @babel-wikibooks extract@: read a decompressed Wikibooks dump on stdin
and recreate the extracted Wikibooks layer from it and the decisions
answered so far. Unanswered requests go to the pending folder for
@decide.sh@; the report says what became of every page.
-}
module Main (main) where

import Autodocodec (HasCodec)
import Babel.Format (render)
import Babel.Layout (Kind (..), Layer (..), Placement (..), placementFolders)
import Babel.Load (loadWorld)
import Babel.Model (Ingredient (..), IsId (..), Origin (..), Recipe (..), Source (..))
import Data.ByteString.Lazy qualified as BL
import Data.Map.Strict qualified as Map
import Data.Text qualified as T
import Data.Text.IO qualified as T
import Decisions.Store (Store (..), readDecisions, writeRequests)
import Options.Applicative (Parser, command, execParser, help, helper, hsubparser, info, long, metavar, progDesc, showDefault, strOption, value)
import System.Directory (createDirectoryIfMissing, doesDirectoryExist, removeDirectoryRecursive)
import System.FilePath (joinPath, takeDirectory, (</>))
import System.IO (hSetEncoding, utf8)
import Wikibooks.Dump (readCookbook, titleText)
import Wikibooks.Emit (mkEnv)
import Wikibooks.Extract (Extraction (..), Status (..), extract)

data Paths = Paths {dataRoot :: FilePath, store :: Store, report :: FilePath}

main :: IO ()
main = do
    mapM_ (`hSetEncoding` utf8) [stdout, stderr]
    execParser (info (hsubparser (command "extract" (info paths (progDesc "Recreate the extracted Wikibooks layer from a dump on stdin"))) <**> helper) mempty) >>= run

paths :: Parser Paths
paths =
    Paths
        <$> path "data" "data" "Data root"
        <*> ( Store
                <$> path "decisions" "data/extractors/wikibooks/decisions" "Answered decisions, one <key>.json each"
                <*> path "pending" ".cache/wikibooks/pending" "Where to write the requests still to send"
            )
        <*> path "report" "data/extractors/wikibooks/report.tsv" "Where to write the report"
  where
    path name def h = strOption (long name <> metavar "PATH" <> value def <> showDefault <> help h)

run :: Paths -> IO ()
run p = do
    cookbook <- readCookbook stdin
    (_, world) <- loadWorld False p.dataRoot
    env <- either (\e -> T.hPutStrLn stderr e >> exitFailure) pure (mkEnv cookbook world)
    answered <- readDecisions p.store
    let x = extract env answered
    writeLayer p.dataRoot x
    writeRequests p.store answered x.pending x.referenced
    writeFileText p.report (report x)

replaceFolder :: FilePath -> IO ()
replaceFolder dir = do
    exists <- doesDirectoryExist dir
    when exists (removeDirectoryRecursive dir)
    createDirectoryIfMissing True dir

-- | The extracted Wikibooks folders hold exactly this extraction's entities.
writeLayer :: FilePath -> Extraction -> IO ()
writeLayer root x = do
    let folder kind = root </> "layers" </> joinPath [toString f | (_, f) <- placementFolders (Placement Extracted Wikibooks kind)]
    replaceFolder (takeDirectory (folder Sources))
    write (folder Sources) [(idText s.id, s) | s <- x.sources]
    write (folder Ingredients) [(idText i.id, i) | i <- x.ingredients]
    write (folder Recipes) [(idText r.id, r) | r <- x.recipes]
  where
    write :: (HasCodec a) => FilePath -> [(Text, a)] -> IO ()
    write dir entities = unless (null entities) $ do
        createDirectoryIfMissing True dir
        forM_ entities $ \(name, e) -> BL.writeFile (dir </> toString name <> ".json") (render e)

-- | One row per page, then one per word read as a unit of pieces.
report :: Extraction -> Text
report x =
    T.unlines $
        "kind\tname\tstatus\tdetail"
            : [T.intercalate "\t" ["page", titleText t, statusName s, clean (detail s)] | (t, s) <- x.pages]
                <> [T.intercalate "\t" ["unit", w, show n, ""] | (w, n) <- sortOn (Down . snd) (Map.toList x.unknownUnits)]
  where
    statusName = \case
        Emitted -> "emitted"
        Skipped _ -> "skipped"
        Pending -> "pending"
    detail = \case
        Skipped why -> why
        _ -> ""
    clean = T.map (\c -> if c == '\t' || c == '\n' then ' ' else c)
