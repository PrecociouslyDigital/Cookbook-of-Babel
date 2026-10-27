{- | An ingredient's nutrition: stated by a measure, or else worked out
from what a recipe that makes it takes in. Inferred values are a query,
never stored, like spoilage.
-}
module Babel.Nutrition (
    Derived (..),
    derivedPer100g,
    nutritionOf,
    nutritionTable,
)
where

import Babel.Load (Located (..), World (..))
import Babel.Mass (Yield (..), makers, yields)
import Babel.Model.Constants (Constants (..))
import Babel.Model.Id (IngredientId, RecipeId, SourceId)
import Babel.Model.Measure (Measure (..), MeasureKey (..), MeasureValue (..), Per100g (..))
import Babel.Model.Recipe (Leaf (..), Output (..), Recipe (..))
import Babel.Settle (Rule (..), settle)
import Data.List (partition)
import Data.Map.Strict qualified as Map

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

No value rests on itself: ingredients that can be made from one another,
like chocolate and melted chocolate, are settled in rounds (see
'settle'). Within such a cycle, a recipe that needs a member not yet
settled gives way to a later recipe that doesn't.
-}
nutritionOf :: World -> IngredientId -> Maybe Derived
nutritionOf w i = join (Map.lookup i (resolve w))

-- | 'nutritionOf' for every ingredient with known nutrition, worked out together.
nutritionTable :: World -> Map IngredientId Derived
nutritionTable = Map.mapMaybe id . resolve

resolve :: World -> Map IngredientId (Maybe Derived)
resolve w = settle (Map.map (Rule [] . const . Just) statedValues <> Map.mapWithKey inferredRule withYields)
  where
    statedValues =
        Map.fromList
            [ (i, Stated n m.value.source)
            | ((i, NutritionKey), m) <- Map.toList w.measures
            , Nutrition n <- [m.value.value]
            ]
    known = yields w
    -- A recipe without a yield can't give nutrition.
    withYields = Map.map (mapMaybe (\r -> (r.id,) <$> Map.lookup r.id known)) (makers w)
    inferredRule target rs =
        Rule
            { needs = concatMap (uses target . snd) rs
            , value = \look -> asum [infer c look r y target | Just c <- [w.constants], (r, y) <- rs]
            }

-- | The ingredients a recipe for @target@ needs the nutrition of.
uses :: IngredientId -> Yield -> [IngredientId]
uses target y = [l.ingredient | (l, _) <- y.inputs] <> [o.ingredient | (o, _) <- y.outputs, o.ingredient /= target]

{- | An input lighter than 'negligibleMassFraction' of the whole may lack
nutrition and counts as none; any other input that lacks a nutrient
leaves that nutrient unknown. Another output with no nutrition at all
rules the recipe out, since its share can't be taken away.
-}
infer :: Constants -> (IngredientId -> Maybe Derived) -> RecipeId -> Yield -> IngredientId -> Maybe Derived
infer c look r y target = do
    let (mine, others) = partition ((== target) . (.ingredient) . fst) y.outputs
        made = sum (map snd mine)
        taken = sum (map snd y.inputs)
        negligible g = g < c.negligibleMassFraction * taken
        ins = [(l.ingredient, g, derivedPer100g <$> look l.ingredient) | (l, g) <- y.inputs]
    guard (made > 0)
    byproducts <- forM others $ \(o, g) -> (g,) . derivedPer100g <$> look o.ingredient
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
    pure (Inferred r result (ordNub [i | (i, g, Nothing) <- ins, negligible g]))
