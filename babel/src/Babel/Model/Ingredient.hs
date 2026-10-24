-- | Ingredients and the is-a DAG between them.
module Babel.Model.Ingredient (
    Ingredient (..),
    IsA (..),
)
where

import Autodocodec (HasCodec (..))
import Babel.Codec (genericCodec)
import Babel.Model.Common (Intensity, Issue)
import Babel.Model.Id (AxisId, FormId, IngredientId, ServingTagId, TasteAttrId, TextureAttrId)

{- | One edge of the is-a DAG. The axis names the classification it belongs
to (e.g. @botanical@, @culinary@), so one ingredient can sit under
several parents without the hierarchy becoming ambiguous.
-}
data IsA = IsA {target :: IngredientId, axis :: AxisId}
    deriving stock (Eq, Generic, Show)

instance HasCodec IsA where codec = genericCodec

{- | Whether an ingredient is an intermediate, and how it spoils, are
computed from the recipe graph and never stored here. Spoilage, melting
and setting are all recipes; 'form' is the ingredient's own state.
Nutrition and piece masses are measures ("Babel.Model.Measure").
-}
data Ingredient = Ingredient
    { id :: IngredientId
    , label :: Text
    , aliases :: [Text]
    , parents :: [IsA]
    , form :: FormId
    , texture :: Map TextureAttrId Intensity
    , taste :: Map TasteAttrId Intensity
    , servingTags :: [ServingTagId]
    , densityGPerMl :: Maybe Double
    , waterActivity :: Maybe Double
    , named :: Bool
    -- ^ A named dish or product ("croutons") rather than a generic food.
    , issues :: [Issue]
    }
    deriving stock (Eq, Generic, Show)

instance HasCodec Ingredient where codec = genericCodec
