{- | The folders under @layers/@, one level per axis:

> layers/<layer>/<licence>/<origin>/<kind>/<id>.json

'placementFolders' is the only definition of this layout; the loader, the
axis listing behind @babel layout@, and the test fixtures all derive from it.
-}
module Babel.Layout (
    Layer (..),
    Kind (..),
    Placement (..),
    placements,
    placementFolders,
    Axis (..),
    axes,
)
where

import Babel.Model.Source (Origin, licenceSpdx, originLicence, originSlug)
import Data.List qualified as List

data Layer = Extracted | Reviewed
    deriving stock (Eq, Ord, Show, Enum, Bounded)

-- | The entity type a folder holds.
data Kind = Sources | Ingredients | Techniques | Recipes | Measures
    deriving stock (Eq, Ord, Show, Enum, Bounded)

{- | A folder that holds entity files. There is no licence: the origin
decides it, so an origin filed under another licence cannot be placed.
-}
data Placement = Placement {layer :: Layer, origin :: Origin, kind :: Kind}
    deriving stock (Eq, Ord, Show)

placements :: [Placement]
placements = [Placement l o k | l <- universe, o <- universe, k <- universe]

-- | The path to a placement, outermost first, each folder with its axis name.
placementFolders :: Placement -> [(Text, Text)]
placementFolders p =
    [ ("layer", layerFolder p.layer)
    , ("licence", licenceSpdx (originLicence p.origin))
    , ("origin", originSlug p.origin)
    , ("kind", kindFolder p.kind)
    ]
  where
    layerFolder = \case
        Extracted -> "extracted"
        Reviewed -> "reviewed"
    kindFolder = \case
        Sources -> "sources"
        Ingredients -> "ingredients"
        Techniques -> "techniques"
        Recipes -> "recipes"
        Measures -> "measures"

-- | A folder level and every folder name it can have.
data Axis = Axis {name :: Text, folders :: [Text]}
    deriving stock (Eq, Show)

-- | The folder levels, outermost first.
axes :: [Axis]
axes =
    [ Axis name (ordNub folders)
    | level <- List.transpose (map placementFolders placements)
    , (name : _, folders) <- [unzip level]
    ]
