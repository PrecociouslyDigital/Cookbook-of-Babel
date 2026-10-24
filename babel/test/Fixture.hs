{-# LANGUAGE RecordWildCards #-}

{- | A dataset as typed values, and how to lay it out on disk. Properties
generate and mutate 'Tree's, then validate what 'writeTree' produced.
-}
module Fixture (
    Placed (..),
    Tree (..),
    writeTree,
    entityPath,
    layerDir,
    withTree,
    entityFiles,
    EntityFile (..),
)
where

import Autodocodec (toJSONViaCodec)
import Babel.Format (render)
import Babel.Load (Layer (..))
import Babel.Model (Constants, Dataset, FormId, Ingredient (..), IsId (..), Licence, Medium, Recipe (..), Rename, ServingTagId, Source (..), TasteAttrId, Technique (..), Term, TextureAttrId, Unit, licenceSpdx)
import Data.Aeson (Value)
import Data.ByteString.Lazy qualified as BL
import Data.Text qualified as T
import System.Directory (createDirectoryIfMissing)
import System.FilePath (takeDirectory, (</>))
import System.IO.Temp (withSystemTempDirectory)

-- | An entity and the layer and licence folder it is written to.
data Placed a = Placed {layer :: Layer, folder :: Licence, entity :: a}
    deriving stock (Eq, Show)

{- | Field names are distinct from 'Babel.Load.World''s so record updates
stay unambiguous.
-}
data Tree = Tree
    { meta :: Dataset
    , renameLog :: [Rename]
    , consts :: Constants
    , unitReg :: [Unit]
    , mediumReg :: [Medium]
    , formReg :: [Term FormId]
    , textureReg :: [Term TextureAttrId]
    , tasteReg :: [Term TasteAttrId]
    , servingReg :: [Term ServingTagId]
    , srcs :: [Placed Source]
    , ings :: [Placed Ingredient]
    , techs :: [Placed Technique]
    , recs :: [Placed Recipe]
    }
    deriving stock (Show)

layerDir :: Layer -> FilePath
layerDir = \case
    Extracted -> "extracted"
    Reviewed -> "reviewed"

-- | Where an entity of this kind lives, relative to the data root.
entityPath :: FilePath -> Placed a -> T.Text -> FilePath
entityPath kind p ident =
    "layers" </> layerDir p.layer </> T.unpack (licenceSpdx p.folder) </> kind </> (T.unpack ident <> ".json")

writeTree :: FilePath -> Tree -> IO ()
writeTree root Tree{..} = do
    write "dataset.json" (render meta)
    write "renames.json" (render renameLog)
    write "registries/constants.json" (render consts)
    write "registries/units.json" (render unitReg)
    write "registries/media.json" (render mediumReg)
    write "registries/forms.json" (render formReg)
    write "registries/texture.json" (render textureReg)
    write "registries/taste.json" (render tasteReg)
    write "registries/serving.json" (render servingReg)
    mapM_ (\p -> write (entityPath "sources" p (idText p.entity.id)) (render p.entity)) srcs
    mapM_ (\p -> write (entityPath "ingredients" p (idText p.entity.id)) (render p.entity)) ings
    mapM_ (\p -> write (entityPath "techniques" p (idText p.entity.id)) (render p.entity)) techs
    mapM_ (\p -> write (entityPath "recipes" p (idText p.entity.id)) (render p.entity)) recs
    -- An empty layers/ directory is still a valid layout.
    createDirectoryIfMissing True (root </> "layers")
  where
    write rel bytes = do
        let p = root </> rel
        createDirectoryIfMissing True (takeDirectory p)
        BL.writeFile p bytes

-- | Write the tree to a fresh temporary data root and run an action on it.
withTree :: Tree -> (FilePath -> IO a) -> IO a
withTree t act = withSystemTempDirectory "babel-prop" $ \tmp -> do
    let root = tmp </> "data"
    writeTree root t
    act root

-- | One entity file: where it is written, its kind folder, and its JSON.
data EntityFile = EntityFile {path :: FilePath, kind :: FilePath, json :: Value}
    deriving stock (Show)

entityFiles :: Tree -> [EntityFile]
entityFiles t =
    [file "sources" p (idText p.entity.id) (toJSONViaCodec p.entity) | p <- t.srcs]
        ++ [file "ingredients" p (idText p.entity.id) (toJSONViaCodec p.entity) | p <- t.ings]
        ++ [file "techniques" p (idText p.entity.id) (toJSONViaCodec p.entity) | p <- t.techs]
        ++ [file "recipes" p (idText p.entity.id) (toJSONViaCodec p.entity) | p <- t.recs]
  where
    file k p ident = EntityFile (entityPath k p ident) k
