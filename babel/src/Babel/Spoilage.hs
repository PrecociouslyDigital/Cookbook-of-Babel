{- | Spoilage is not a field on ingredients; it is ordinary recipes whose
technique descends from the spoilage technique named in the constants
registry. These helpers find those recipes and work out which one happens
first under given conditions.
-}
module Babel.Spoilage (
    Conditions (..),
    spoilagePaths,
    timeAt,
    firstSpoilage,
)
where

import Babel.Lineage (ancestors)
import Babel.Load (Located (..), World (..))
import Babel.Model.Common (Amount (..))
import Babel.Model.Constants (Constants (..))
import Babel.Model.Id (IngredientId)
import Babel.Model.Ingredient (Ingredient (..))
import Babel.Model.Recipe (End (..), Leaf (..), Recipe (..), Sensitivity (..), leaves)
import Babel.Model.Registry (Dimension (..), Unit (..))
import Data.Map.Strict qualified as Map

data Conditions = Conditions
    { tempC :: Double
    , waterActivity :: Maybe Double
    -- ^ Overrides the ingredient's own water activity when given.
    }
    deriving stock (Show)

{- | Recipes that spoil this ingredient: their technique is the spoilage
technique or below it, and the ingredient is one of their inputs.
-}
spoilagePaths :: World -> IngredientId -> [Recipe]
spoilagePaths w ing = case w.constants of
    Nothing -> []
    Just c ->
        [ r.value
        | r <- Map.elems w.recipes
        , c.spoilageTechnique `elem` ancestors w r.value.technique
        , ing `elem` map (.ingredient) (leaves r.value.inputs)
        ]

{- | How long a step of reference duration @d@ takes under these conditions,
scaling by Q10 per 10 °C away from the reference temperature. 'Nothing'
when the water activity is outside the step's bounds: it never happens.
-}
timeAt :: Conditions -> Sensitivity -> Double -> Maybe Double
timeAt c s d
    | Just aw <- c.waterActivity
    , aw < fromMaybe 0 s.minWaterActivity || aw > fromMaybe 1 s.maxWaterActivity =
        Nothing
    | otherwise = Just (d * s.q10 ** ((s.refTempC - c.tempC) / 10))

{- | The spoilage recipe that completes first, and when, in the unit its
own duration is written in. Only time-based recipes with a sensitivity
take part.
-}
firstSpoilage :: World -> Conditions -> IngredientId -> Maybe (Recipe, Amount)
firstSpoilage w c ing = viaNonEmpty head (sortWith (\(_, _, base) -> base) candidates) <&> \(r, a, _) -> (r, a)
  where
    ownAw = Map.lookup ing w.ingredients >>= (\i -> i.value.waterActivity)
    c' = Conditions c.tempC (c.waterActivity <|> ownAw)
    candidates =
        [ (r, Amount (t / u.toBase) d.unit, t)
        | r <- spoilagePaths w ing
        , ByTime d (Just s) <- [r.end]
        , Just u <- [Map.lookup d.unit w.units]
        , u.dimension == Time
        , Just t <- [timeAt c' s (d.qty * u.toBase)]
        ]
