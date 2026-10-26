{- | An extractor's decisions on disk: a folder of answers, one
@<key>.json@ each, and a folder of the requests still to send, which
@decide.sh@ reads.
-}
module Decisions.Store (
    Store (..),
    readDecisions,
    writeRequests,
)
where

import Data.Aeson qualified as Aeson
import Data.ByteString.Lazy qualified as BL
import Data.Map.Strict qualified as Map
import Data.Set qualified as Set
import Decisions.Decide (Decisions)
import Decisions.Request (Key, keyText, mkKey)
import System.Directory (createDirectoryIfMissing, doesDirectoryExist, listDirectory, removeDirectoryRecursive, removeFile)
import System.FilePath (takeBaseName, takeExtension, (</>))

data Store = Store {decisions :: FilePath, pending :: FilePath}

readDecisions :: Store -> IO Decisions
readDecisions store = do
    let dir = store.decisions
    exists <- doesDirectoryExist dir
    names <- if exists then listDirectory dir else pure []
    fmap Map.fromList . forM [n | n <- names, takeExtension n == ".json"] $ \n -> do
        v <- either (\e -> die ("decision " <> n <> ": " <> e)) pure =<< Aeson.eitherDecodeFileStrict (dir </> n)
        k <- maybe (die ("decision " <> n <> ": the file name is not a request key")) pure (mkKey (toText (takeBaseName n)))
        pure (k, v)

{- | Make the pending folder hold exactly the bodies of the requests still
to send. Once nothing is pending, every decision is either referenced by
the extraction or stale, and the stale ones are deleted.
-}
writeRequests :: Store -> Decisions -> Map Key LByteString -> Set Key -> IO ()
writeRequests store answered pending referenced = do
    exists <- doesDirectoryExist store.pending
    when exists (removeDirectoryRecursive store.pending)
    createDirectoryIfMissing True store.pending
    forM_ (Map.toList pending) $ \(k, body) -> BL.writeFile (store.pending </> keyFile k) body
    when (Map.null pending) $
        forM_ (Map.keys answered) $ \k ->
            unless (Set.member k referenced) (removeFile (store.decisions </> keyFile k))

keyFile :: Key -> FilePath
keyFile k = toString (keyText k) <> ".json"
