{-# LANGUAGE OverloadedLists #-}

{- | Facts about one ingredient, each with its own source: its nutrition, or
how much one of a counted unit of it weighs. They are their own entities so
that each one can come from a different source and be reviewed on its own.
-}
module Babel.Model.Measure (
    Measure (..),
    MeasureValue (..),
    Per100g (..),
    MeasureKey (..),
    measureKey,
    measureStem,
)
where

import Autodocodec (HasCodec (..), discriminatedUnionCodec, mapToDecoder, mapToEncoder, object, requiredField', (.=))
import Babel.Codec (IsId (..), genericCodec)
import Babel.Model.Common (Issue)
import Babel.Model.Id (IngredientId, SourceId, UnitId)

data Per100g = Per100g
    { kcal :: Maybe Double
    , proteinG :: Maybe Double
    , fatG :: Maybe Double
    , carbsG :: Maybe Double
    }
    deriving stock (Eq, Generic, Show)

instance HasCodec Per100g where codec = genericCodec

{- | 'GramsPer' is the mass of one of a count unit, e.g. one @piece@ of
egg. Mass and volume units don't need one: they convert through the units
registry and the ingredient's density.
-}
data MeasureValue
    = Nutrition Per100g
    | GramsPer UnitId Double
    deriving stock (Eq, Show)

instance HasCodec MeasureValue where
    codec = object "MeasureValue" $ discriminatedUnionCodec "kind" enc dec
      where
        nutrition = requiredField' "per100g"
        gramsPer = (,) <$> requiredField' "unit" .= fst <*> requiredField' "grams" .= snd
        enc = \case
            Nutrition n -> ("nutrition", mapToEncoder n nutrition)
            GramsPer u g -> ("gramsPer", mapToEncoder (u, g) gramsPer)
        dec =
            [ ("nutrition", ("Nutrition", mapToDecoder Nutrition nutrition))
            , ("gramsPer", ("GramsPer", mapToDecoder (uncurry GramsPer) gramsPer))
            ]

data Measure = Measure
    { ingredient :: IngredientId
    , source :: SourceId
    , value :: MeasureValue
    , issues :: [Issue]
    }
    deriving stock (Eq, Generic, Show)

instance HasCodec Measure where codec = genericCodec

-- | What a measure is about. An ingredient has at most one measure per key.
data MeasureKey = NutritionKey | GramsPerKey UnitId
    deriving stock (Eq, Ord, Show)

measureKey :: MeasureValue -> MeasureKey
measureKey = \case
    Nutrition _ -> NutritionKey
    GramsPer u _ -> GramsPerKey u

-- | The file name a measure is stored under, e.g. @egg.grams_per_piece@.
measureStem :: IngredientId -> MeasureKey -> Text
measureStem i k = idText i <> "." <> keyText
  where
    keyText = case k of
        NutritionKey -> "nutrition"
        GramsPerKey u -> "grams_per_" <> idText u
