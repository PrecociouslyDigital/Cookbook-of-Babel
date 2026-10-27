-- | How much an amount of an ingredient weighs.
module Babel.Mass (
    massByUnit,
    massInGrams,
    massIn,
    Yield (..),
    yields,
    makers,
)
where

import Babel.Load (Located (..), World (..))
import Babel.Model.Common (Amount (..))
import Babel.Model.Id (IngredientId, RecipeId, UnitId)
import Babel.Model.Ingredient (Ingredient (..))
import Babel.Model.Measure (Measure (..), MeasureKey (..), MeasureValue (..))
import Babel.Model.Recipe (Leaf (..), Outcome (..), Output (..), Recipe (..), leaves, minimalLeaves)
import Babel.Model.Registry (Dimension (..), Unit (..))
import Babel.Settle (Rule (..), settle)
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

{- | 'massInGrams', and an amount in the unit a recipe yields the
ingredient in weighs that recipe's output, pro rata: one @batch@ of a
step's output weighs what one run of the step gives out. The first
recipe in ID order that yields the unit and has a 'Yield' decides it; a
cycle of recipes can't weigh itself (see 'settle'). 'ratioCheck' leaves
this out, since it would only compare a step with itself.
-}
massIn :: World -> IngredientId -> Amount -> Maybe Double
massIn w = massVia w (lookupIn (yieldedUnits w))

{- | For each unit recipes yield an ingredient in, the output of the first
such recipe and what that output weighs.
-}
type YieldedUnits = IngredientId -> Maybe (Map UnitId (Output, Double))

massVia :: World -> YieldedUnits -> IngredientId -> Amount -> Maybe Double
massVia w yielded ing a = massInGrams w ing a <|> viaRecipe
  where
    viaRecipe = do
        (o, g) <- Map.lookup a.unit =<< yielded ing
        Just (a.qty / o.amount.qty * g / fromMaybe 1 o.chance)

yieldedUnits :: World -> Map IngredientId (Maybe (Map UnitId (Output, Double)))
yieldedUnits w = settle (Map.mapWithKey rule (makers w))
  where
    rule ing rs =
        Rule
            { needs = [l.ingredient | r <- rs, l <- leaves r.inputs, isNothing (massInGrams w l.ingredient l.amount)]
            , value = \yielded ->
                let byUnit =
                        Map.fromListWith
                            (\_ earlier -> earlier)
                            [ (o.amount.unit, (o, g))
                            | r <- rs
                            , Just y <- [yieldVia w yielded r]
                            , (o, g) <- y.outputs
                            , o.ingredient == ing
                            , o.amount.qty > 0
                            ]
                 in byUnit <$ guard (not (Map.null byUnit))
            }

{- | Recipes with a plain list of outputs, in ID order, under each
ingredient they make.
-}
makers :: World -> Map IngredientId [Recipe]
makers w =
    Map.fromListWith
        (<>)
        [ (o, [r])
        | r <- reverse (map (.value) (Map.elems w.recipes))
        , Outputs os <- [r.outputs]
        , o <- ordNub (map (.ingredient) (toList os))
        ]

{- | One run of a recipe's least variant ('minimalLeaves'): each leaf with
its grams, and each output with the grams it is expected to weigh, chance
included.
-}
data Yield = Yield {inputs :: [(Leaf, Double)], outputs :: [(Output, Double)]}
    deriving stock (Show)

{- | The yield of every recipe that has one. The least variant gives out
@y@ times what it takes in, where @y@ is the recipe's stated output over
all of its leaves, optional ones too; an extracted recipe states the
output of every leaf it lists. When an output isn't in grams, @y@ is 1
and a single output takes it all. A recipe has no yield when it is a
'OneOf' recipe, has an input of unknown mass ('massIn'), or has several
outputs some of which aren't in grams.
-}
yields :: World -> Map RecipeId Yield
yields w = Map.mapMaybe (yieldVia w (lookupIn (yieldedUnits w)) . (.value)) w.recipes

yieldVia :: World -> YieldedUnits -> Recipe -> Maybe Yield
yieldVia w yielded r = do
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
    grams l = massVia w yielded l.ingredient l.amount

lookupIn :: (Ord k) => Map k (Maybe v) -> k -> Maybe v
lookupIn m k = join (Map.lookup k m)
