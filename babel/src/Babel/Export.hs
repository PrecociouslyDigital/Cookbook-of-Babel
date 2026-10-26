{- | The merged dataset as one JSON document, for consumers that don't read
the layered file tree: game mods and other downstream targets. Overlays
are already applied, so every consumer sees the same world, and the rename
log comes along so consumers can migrate IDs they stored.

Entities are encoded with the codecs of their files, so the export's
schema is the file schema. Entity lists are sorted by ID, and the document is
rendered through "Babel.Format", so exporting the same data twice gives the
same bytes.

The export also carries each ingredient's nutrition, stated or inferred via
"Babel.Nutrition", and every preparation ("Babel.Preparation"), so
consumers don't have to re-derive them.
-}
module Babel.Export (
    Export (..),
    exportWorld,
)
where

import Autodocodec (HasCodec (..))
import Babel.Codec (genericCodec)
import Babel.Load (Located (..), World (..))
import Babel.Model.Constants (Constants)
import Babel.Model.Dataset (Dataset (..), Rename, Version)
import Babel.Model.Id (FormId, IngredientId, ServingTagId, TasteAttrId, TextureAttrId)
import Babel.Model.Ingredient (Ingredient)
import Babel.Model.Measure (Measure, Per100g)
import Babel.Model.Recipe (Recipe)
import Babel.Model.Registry (Medium, Term, Unit)
import Babel.Model.Source (Licence, Source)
import Babel.Model.Technique (Technique)
import Babel.Nutrition (derivedPer100g, nutritionOf)
import Babel.Preparation (Prepared, prepared)
import Data.Map.Strict qualified as Map
import Data.Set qualified as Set

data Export = Export
    { version :: Version
    , licence :: [Licence]
    -- ^ Every licence that applies to the export as a whole.
    , sources :: [Source]
    , ingredients :: [Ingredient]
    , techniques :: [Technique]
    , recipes :: [Recipe]
    , measures :: [Measure]
    , nutrition :: Map IngredientId Per100g
    -- ^ One entry per ingredient with known nutrition, stated or inferred.
    , preparations :: [Prepared]
    , units :: [Unit]
    , forms :: [Term FormId]
    , media :: [Medium]
    , texture :: [Term TextureAttrId]
    , taste :: [Term TasteAttrId]
    , serving :: [Term ServingTagId]
    , renames :: [Rename]
    , constants :: Constants
    }
    deriving stock (Eq, Generic, Show)

instance HasCodec Export where codec = genericCodec

{- | 'Nothing' when the world lacks @dataset.json@ or the constants, which
validation reports as errors.
-}
exportWorld :: Set Licence -> World -> Maybe Export
exportWorld licences w = do
    d <- w.dataset
    c <- w.constants
    pure
        Export
            { version = d.version
            , licence = Set.toAscList licences
            , sources = values w.sources
            , ingredients = values w.ingredients
            , techniques = values w.techniques
            , recipes = values w.recipes
            , measures = values w.measures
            , nutrition = Map.mapMaybeWithKey (\i _ -> derivedPer100g <$> nutritionOf w i) w.ingredients
            , preparations = prepared w
            , units = Map.elems w.units
            , forms = Map.elems w.forms
            , media = Map.elems w.media
            , texture = Map.elems w.texture
            , taste = Map.elems w.taste
            , serving = Map.elems w.serving
            , renames = w.renames
            , constants = c
            }
  where
    values :: Map k (Located a) -> [a]
    values = map (.value) . Map.elems
