-- | How much an amount of an ingredient weighs.
module Babel.Mass (
    massByUnit,
    massInGrams,
    massIn,
    Yield (..),
    yieldOf,
    makers,
)
where

import Babel.Load (Located (..), World (..))
import Babel.Model.Common (Amount (..))
import Babel.Model.Id (IngredientId)
import Babel.Model.Ingredient (Ingredient (..))
import Babel.Model.Measure (Measure (..), MeasureKey (..), MeasureValue (..))
import Babel.Model.Recipe (Leaf (..), Outcome (..), Output (..), Recipe (..), leaves, minimalLeaves)
import Babel.Model.Registry (Dimension (..), Unit (..))
import Data.Map.Strict qualified as Map
import Data.Set qualified as Set

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

{- | 'massInGrams', and an amount in the unit a recipe yields the
ingredient in weighs that recipe's output, pro rata: one @batch@ of a
step's output weighs what one run of the step gives out. 'ratioCheck'
leaves this out, since it would only compare a step with itself.
-}
massIn :: World -> IngredientId -> Amount -> Maybe Double
massIn w = massVia w Set.empty

-- | @seen@ holds the ingredients whose yield is already being worked out.
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

-- | Recipes with a plain list of outputs that make the ingredient, in ID order.
makers :: World -> IngredientId -> [Recipe]
makers w ing = [r.value | r <- Map.elems w.recipes, Outputs os <- [r.value.outputs], any ((== ing) . (.ingredient)) os]

{- | One run of a recipe's least variant ('minimalLeaves'): each leaf with
its grams, and each output with the grams it is expected to weigh, chance
included.
-}
data Yield = Yield {inputs :: [(Leaf, Double)], outputs :: [(Output, Double)]}
    deriving stock (Show)

{- | The least variant gives out @y@ times what it takes in, where @y@ is
the recipe's stated output over all of its leaves, optional ones too;
an extracted recipe states the output of every leaf it lists. When an
output isn't in grams, @y@ is 1 and a single output takes it all.
Nothing for a 'OneOf' recipe, an input of unknown mass, or several
outputs some of which aren't in grams.
-}
yieldOf :: World -> Recipe -> Maybe Yield
yieldOf w = yieldVia w Set.empty

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
