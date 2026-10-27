{- | Nutrition and mass inference by a search from each ingredient
through the recipes that make it, carrying the path so far so cycles
end. Exponential on shared sub-recipes, but plainly right outside
cycles, so it is the oracle "Babel.Nutrition" and "Babel.Mass" are
checked against.
-}
module Reference (nutritionOf, massIn, inferFrom) where

import Babel.Load (Located (..), World (..))
import Babel.Mass (Yield (..), massInGrams)
import Babel.Model (Amount (..), Constants (..), IngredientId, Leaf (..), Measure (..), MeasureKey (..), MeasureValue (..), Outcome (..), Output (..), Per100g (..), Recipe (..), RecipeId, leaves, minimalLeaves)
import Babel.Nutrition (Derived (..), derivedPer100g)
import Data.List (partition)
import Data.Map.Strict qualified as Map
import Data.Set qualified as Set

nutritionOf :: World -> IngredientId -> Maybe Derived
nutritionOf w = nutritionVia w Set.empty

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
        c <- w.constants
        asum [inferFrom c (nutritionVia w (Set.insert ing seen)) r.id y ing | r <- makers w ing, Just y <- [yieldVia w Set.empty r]]

-- | One recipe's inference for @target@, given each other ingredient's nutrition.
inferFrom :: Constants -> (IngredientId -> Maybe Derived) -> RecipeId -> Yield -> IngredientId -> Maybe Derived
inferFrom c look r y target = do
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
            guard (total >= given)
            Just ((total - given) / made * 100)
        result = Per100g (per100g (.kcal)) (per100g (.proteinG)) (per100g (.fatG)) (per100g (.carbsG))
    guard (any isJust [result.kcal, result.proteinG, result.fatG, result.carbsG])
    pure (Inferred r result (ordNub [i | (i, g, Nothing) <- ins, negligible g]))

massIn :: World -> IngredientId -> Amount -> Maybe Double
massIn w = massVia w Set.empty

massVia :: World -> Set IngredientId -> IngredientId -> Amount -> Maybe Double
massVia w seen ing a = massInGrams w ing a <|> viaRecipe
  where
    viaRecipe = do
        guard (Set.notMember ing seen)
        listToMaybe
            [ a.qty / o.amount.qty * g / fromMaybe 1 o.chance
            | r <- makers w ing
            , Just y <- [yieldVia w (Set.insert ing seen) r]
            , (o, g) <- y.outputs
            , o.ingredient == ing
            , o.amount.unit == a.unit
            , o.amount.qty > 0
            ]

makers :: World -> IngredientId -> [Recipe]
makers w ing = [r.value | r <- Map.elems w.recipes, Outputs os <- [r.value.outputs], any ((== ing) . (.ingredient)) os]

yieldVia :: World -> Set IngredientId -> Recipe -> Maybe Yield
yieldVia w seen r = do
    os <- case r.outputs of
        Outputs os -> Just (toList os)
        OneOf _ -> Nothing
    ins <- traverse (\l -> (l,) <$> grams l) (minimalLeaves r.inputs)
    let taken = sum (map snd ins)
        stated = traverse (\o -> (* fromMaybe 1 o.chance) <$> massInGrams w o.ingredient o.amount) os
        everything = sum <$> traverse grams (leaves r.inputs)
    (shares, y) <- case (stated, os) of
        (Just gs, _) | sum gs > 0 -> Just (map (/ sum gs) gs, maybe 1 (\e -> if e > 0 then sum gs / e else 1) everything)
        (_, [_]) -> Just ([1], 1)
        _ -> Nothing
    pure Yield{inputs = ins, outputs = zip os [y * taken * share | share <- shares]}
  where
    grams l = massVia w seen l.ingredient l.amount
