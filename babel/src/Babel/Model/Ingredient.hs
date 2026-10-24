-- | Ingredients and the is-a DAG between them.
module Babel.Model.Ingredient (
    Ingredient (..),
    IsA (..),
    Nutrition (..),
    Per100g (..),
)
where

import Autodocodec (HasCodec (..))
import Babel.Codec (genericCodec)
import Babel.Model.Common (Intensity, Issue)
import Babel.Model.Id (AxisId, FormId, IngredientId, ServingTagId, SourceId, TasteAttrId, TextureAttrId)

{- | One edge of the is-a DAG. The axis names the classification it belongs
to (e.g. @botanical@, @culinary@), so one ingredient can sit under
several parents without the hierarchy becoming ambiguous.
-}
data IsA = IsA {target :: IngredientId, axis :: AxisId}
    deriving stock (Eq, Generic, Show)

instance HasCodec IsA where codec = genericCodec

data Per100g = Per100g
    { kcal :: Maybe Double
    , proteinG :: Maybe Double
    , fatG :: Maybe Double
    , carbsG :: Maybe Double
    }
    deriving stock (Eq, Generic, Show)

instance HasCodec Per100g where codec = genericCodec

data Nutrition = Nutrition {per100g :: Per100g, source :: SourceId}
    deriving stock (Eq, Generic, Show)

instance HasCodec Nutrition where codec = genericCodec

{- | Whether an ingredient is an intermediate, and how it spoils, are
computed from the recipe graph and never stored here. Spoilage, melting
and setting are all recipes; 'form' is the ingredient's own state.
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
    , nutrition :: Maybe Nutrition
    , named :: Bool
    -- ^ A named dish or product ("croutons") rather than a generic food.
    , issues :: [Issue]
    }
    deriving stock (Eq, Generic, Show)

instance HasCodec Ingredient where codec = genericCodec
