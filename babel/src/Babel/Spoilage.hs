{-# LANGUAGE DuplicateRecordFields #-}
{-# LANGUAGE OverloadedRecordDot #-}
{-# LANGUAGE NoFieldSelectors #-}

{- | Spoilage is not a field on ingredients; it is ordinary recipes whose
technique descends from @spoil@. These helpers find those recipes and
work out which one happens first under given conditions.
-}
module Babel.Spoilage (
    Conditions (..),
    Days,
    spoilagePaths,
    timeAt,
    firstSpoilage,
)
where

import Babel.Load
import Babel.Model
import Control.Applicative ((<|>))
import Data.Either (lefts, rights)
import Data.List (minimumBy)
import Data.Map.Strict qualified as Map
import Data.Maybe (fromMaybe)
import Data.Ord (comparing)
import Data.Set qualified as Set

data Conditions = Conditions
    { tempC :: Double
    , waterActivity :: Maybe Double
    -- ^ Overrides the ingredient's own water activity when given.
    }
    deriving stock (Show)

type Days = Double

{- | Recipes that spoil this ingredient: their technique is @spoil@ or
below it, and the ingredient is one of their inputs.
-}
spoilagePaths :: World -> IngredientId -> [Recipe]
spoilagePaths w ing =
    [ r.value
    | r <- Map.elems w.recipes
    , TechniqueId "spoil" `Set.member` ancestors r.value.technique
    , ing `elem` map (.ingredient) (leaves r.value.inputs)
    ]
  where
    ancestors t0 = go Set.empty [t0]
    go seen = \case
        [] -> seen
        t : rest
            | t `Set.member` seen -> go seen rest
            | otherwise ->
                let ps = maybe [] (\l -> l.value.parents) (Map.lookup t w.techniques)
                 in go (Set.insert t seen) (ps ++ rest)
    leaves s = rights s.choices ++ concatMap leaves (lefts s.choices)

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

{- | The spoilage recipe that completes first, and after how many days.
Only time-based recipes with a sensitivity take part.
-}
firstSpoilage :: World -> Conditions -> IngredientId -> Maybe (Recipe, Days)
firstSpoilage w c ing = case candidates of
    [] -> Nothing
    xs -> Just (minimumBy (comparing snd) xs)
  where
    ownAw = Map.lookup ing w.ingredients >>= (\i -> i.value.waterActivity)
    c' = Conditions c.tempC (c.waterActivity <|> ownAw)
    candidates =
        [ (r, t / 86400)
        | r <- spoilagePaths w ing
        , ByTime d (Just s) <- [r.end]
        , Just u <- [Map.lookup d.unit w.units]
        , u.dimension == Time
        , Just t <- [timeAt c' s (d.qty * u.toBase)]
        ]
