{- | An ingredient's nutrition: stated by a measure, or else worked out
from what a recipe that makes it takes in. Inferred values are a query,
never stored, like spoilage.
-}
module Babel.Nutrition (
    Derived (..),
    derivedPer100g,
    nutritionOf,
)
where

import Babel.Load (Located (..), World (..))
import Babel.Mass (Yield (..), makers, yieldOf)
import Babel.Model.Constants (Constants (..))
import Babel.Model.Id (IngredientId, RecipeId, SourceId)
import Babel.Model.Measure (Measure (..), MeasureKey (..), MeasureValue (..), Per100g (..))
import Babel.Model.Recipe (Leaf (..), Output (..), Recipe (..))
import Data.List (partition)
import Data.Map.Strict qualified as Map
import Data.Set qualified as Set

data Derived
    = Stated Per100g SourceId
    | -- | The recipe, and the inputs left out because they lack nutrition but weigh too little to matter.
      Inferred RecipeId Per100g [IngredientId]
    deriving stock (Eq, Show)

derivedPer100g :: Derived -> Per100g
derivedPer100g = \case
    Stated n _ -> n
    Inferred _ n _ -> n

{- | A stated value wins. Otherwise the recipes that make the ingredient
are tried in ID order and the first that resolves is used. Each one runs
its least variant: the nutrients of its inputs, less those of its other
outputs, spread over what it yields of this ingredient, assuming that
output has the same make-up throughout.
-}
nutritionOf :: World -> IngredientId -> Maybe Derived
nutritionOf w = nutritionVia w Set.empty

-- | @seen@ holds the ingredients on the path being worked out, so cycles end.
nutritionVia :: World -> Set IngredientId -> IngredientId -> Maybe Derived
nutritionVia w seen ing = stated <|> inferred
  where
    stated = do
        m <- Map.lookup (ing, NutritionKey) w.measures
        case m.value.value of
            Nutrition n -> Just (Stated n m.value.source)
            GramsPer _ _ -> Nothing
    inferred = do
        guard (Set.notMember ing seen)
        asum [infer w (Set.insert ing seen) r ing | r <- makers w ing]

{- | An input lighter than 'negligibleMassFraction' of the whole may lack
nutrition and counts as none; any other input that lacks a nutrient
leaves that nutrient unknown. Another output with no nutrition at all
rules the recipe out, since its share can't be taken away.
-}
infer :: World -> Set IngredientId -> Recipe -> IngredientId -> Maybe Derived
infer w seen r target = do
    c <- w.constants
    y <- yieldOf w r
    let (mine, others) = partition ((== target) . (.ingredient) . fst) y.outputs
        made = sum (map snd mine)
        taken = sum (map snd y.inputs)
        negligible g = g < c.negligibleMassFraction * taken
        ins = [(l.ingredient, g, derivedPer100g <$> nutritionVia w seen l.ingredient) | (l, g) <- y.inputs]
    guard (made > 0)
    byproducts <- forM others $ \(o, g) -> (g,) . derivedPer100g <$> nutritionVia w seen o.ingredient
    let gramsIn nutrient (_, g, n) = case n of
            Nothing | negligible g -> Just 0
            _ -> (\x -> g * x / 100) <$> (nutrient =<< n)
        per100g nutrient = do
            total <- sum <$> traverse (gramsIn nutrient) ins
            given <- sum <$> traverse (\(g, n) -> (\x -> g * x / 100) <$> nutrient n) byproducts
            -- Less than nothing left means the inputs and byproducts disagree.
            guard (total >= given)
            Just ((total - given) / made * 100)
        result = Per100g (per100g (.kcal)) (per100g (.proteinG)) (per100g (.fatG)) (per100g (.carbsG))
    guard (any isJust [result.kcal, result.proteinG, result.fatG, result.carbsG])
    pure (Inferred r.id result (ordNub [i | (i, g, Nothing) <- ins, negligible g]))
