{-# LANGUAGE DerivingVia #-}

{- | Identifiers. Each entity kind has its own newtype, so an ingredient ID
can never stand in for a technique ID. 'IsId' comes via 'Slug', or
'DottedSlug' for recipes.
-}
module Babel.Model.Id (
    IngredientId (..),
    TechniqueId (..),
    RecipeId (..),
    SourceId (..),
    UnitId (..),
    MediumId (..),
    FormId (..),
    TextureAttrId (..),
    TasteAttrId (..),
    ServingTagId (..),
    TintId (..),
    AxisId (..),
    TempBandId (..),
)
where

import Autodocodec (HasCodec (..))
import Babel.Codec (DottedSlug (..), IsId (..), Slug (..), idCodec)

newtype IngredientId = IngredientId Text
    deriving newtype (Eq, Ord, Show)
    deriving (IsId) via Slug

newtype TechniqueId = TechniqueId Text
    deriving newtype (Eq, Ord, Show)
    deriving (IsId) via Slug

newtype RecipeId = RecipeId Text
    deriving newtype (Eq, Ord, Show)
    deriving (IsId) via DottedSlug

newtype SourceId = SourceId Text
    deriving newtype (Eq, Ord, Show)
    deriving (IsId) via Slug

newtype UnitId = UnitId Text
    deriving newtype (Eq, Ord, Show)
    deriving (IsId) via Slug

newtype MediumId = MediumId Text
    deriving newtype (Eq, Ord, Show)
    deriving (IsId) via Slug

newtype FormId = FormId Text
    deriving newtype (Eq, Ord, Show)
    deriving (IsId) via Slug

newtype TextureAttrId = TextureAttrId Text
    deriving newtype (Eq, Ord, Show)
    deriving (IsId) via Slug

newtype TasteAttrId = TasteAttrId Text
    deriving newtype (Eq, Ord, Show)
    deriving (IsId) via Slug

newtype ServingTagId = ServingTagId Text
    deriving newtype (Eq, Ord, Show)
    deriving (IsId) via Slug

-- | A food word in the tint registry, not an ingredient ID: labels carry it as a word.
newtype TintId = TintId Text
    deriving newtype (Eq, Ord, Show)
    deriving (IsId) via Slug

newtype AxisId = AxisId Text
    deriving newtype (Eq, Ord, Show)
    deriving (IsId) via Slug

newtype TempBandId = TempBandId Text
    deriving newtype (Eq, Ord, Show)
    deriving (IsId) via Slug

instance HasCodec IngredientId where codec = idCodec
instance HasCodec TechniqueId where codec = idCodec
instance HasCodec RecipeId where codec = idCodec
instance HasCodec SourceId where codec = idCodec
instance HasCodec UnitId where codec = idCodec
instance HasCodec MediumId where codec = idCodec
instance HasCodec FormId where codec = idCodec
instance HasCodec TextureAttrId where codec = idCodec
instance HasCodec TasteAttrId where codec = idCodec
instance HasCodec ServingTagId where codec = idCodec
instance HasCodec TintId where codec = idCodec
instance HasCodec AxisId where codec = idCodec
instance HasCodec TempBandId where codec = idCodec
