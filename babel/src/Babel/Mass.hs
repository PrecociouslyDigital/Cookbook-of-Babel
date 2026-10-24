-- | How much an amount of an ingredient weighs.
module Babel.Mass (
    massByUnit,
    massInGrams,
)
where

import Babel.Load (Located (..), World (..))
import Babel.Model.Common (Amount (..))
import Babel.Model.Id (IngredientId)
import Babel.Model.Ingredient (Ingredient (..))
import Babel.Model.Measure (Measure (..), MeasureKey (..), MeasureValue (..))
import Babel.Model.Registry (Dimension (..), Unit (..))
import Data.Map.Strict qualified as Map

{- | An amount in grams from its unit alone: a mass unit converts directly,
a volume unit through the ingredient's density.
-}
massByUnit :: World -> IngredientId -> Amount -> Maybe Double
massByUnit w ing a = do
    u <- Map.lookup a.unit w.units
    case u.dimension of
        Mass -> Just (a.qty * u.toBase)
        Volume -> do
            i <- Map.lookup ing w.ingredients
            d <- i.value.densityGPerMl
            Just (a.qty * u.toBase * d)
        _ -> Nothing

{- | Tries 'massByUnit' first; failing that, a counted amount weighs what
the ingredient's 'GramsPer' measure for exactly that unit says. Only that
unit: one @batch@ of egg is not one @piece@.
-}
massInGrams :: World -> IngredientId -> Amount -> Maybe Double
massInGrams w ing a = massByUnit w ing a <|> perUnit
  where
    perUnit = do
        m <- Map.lookup (ing, GramsPerKey a.unit) w.measures
        GramsPer _ g <- Just m.value.value
        Just (a.qty * g)
