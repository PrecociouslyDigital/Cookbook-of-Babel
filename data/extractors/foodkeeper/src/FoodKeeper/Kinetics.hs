{- | Fitting a spoilage step's duration and 'Sensitivity' to the times
FoodKeeper gives for a product in the pantry, the fridge and the freezer.

Each window's geometric mean is taken as the time at that place's
temperature. Two temperatures above freezing fix Q10; with one, the
technique's default Q10 from the constants registry stands in. Freezing
changes what limits a food's life, so the freezer gets a step of its own,
which never happens above 0 °C, while the other never happens below it.
-}
module FoodKeeper.Kinetics (
    Spoiling (..),
    Fit (..),
    pantryC,
    fridgeC,
    freezerC,
    aboveFreezing,
    frozen,
)
where

import Babel.Codec (IsId (..))
import Babel.Model.Constants (SpoilageKinetics (..))
import Babel.Model.Id (TechniqueId)
import Babel.Model.Recipe (Sensitivity (..))
import FoodKeeper.Source (Storage (..), Window, describeWindow, windowSeconds)

-- | A spoilage technique and its default kinetics.
data Spoiling = Spoiling {technique :: TechniqueId, kinetics :: SpoilageKinetics}
    deriving stock (Eq, Show)

-- | A step's duration at its reference temperature, how it scales, and how that was worked out.
data Fit = Fit {seconds :: Double, sensitivity :: Sensitivity, note :: Text}
    deriving stock (Eq, Show)

-- | The temperatures FoodKeeper's places stand for, in °C.
pantryC, fridgeC, freezerC :: Double
pantryC = 20
fridgeC = 4
freezerC = -18

aboveFreezing :: Spoiling -> Storage -> Maybe Fit
aboveFreezing s storage = case (storage.pantry, storage.fridge) of
    (Just p, Just f) ->
        Just $
            fit pantryC p ((windowSeconds f / windowSeconds p) ** (10 / (pantryC - fridgeC))) $
                kept [(p, "pantry"), (f, "fridge")] <> "; the geometric means at " <> degrees pantryC <> " and " <> degrees fridgeC <> " fix q10"
    (Just p, Nothing) -> Just (fit pantryC p s.kinetics.q10 (kept [(p, "pantry")] <> defaultFor s pantryC))
    (Nothing, Just f) -> Just (fit fridgeC f s.kinetics.q10 (kept [(f, "fridge")] <> defaultFor s fridgeC))
    (Nothing, Nothing) -> Nothing
  where
    fit ref w q10 =
        Fit
            (windowSeconds w)
            Sensitivity{refTempC = ref, q10, minWaterActivity = s.kinetics.minWaterActivity, maxWaterActivity = Nothing, minTempC = Just 0, maxTempC = Nothing}

frozen :: Spoiling -> Storage -> Maybe Fit
frozen s storage =
    storage.freezer <&> \w ->
        Fit
            (windowSeconds w)
            Sensitivity{refTempC = freezerC, q10 = s.kinetics.q10, minWaterActivity = s.kinetics.minWaterActivity, maxWaterActivity = Nothing, minTempC = Nothing, maxTempC = Just 0}
            (kept [(w, "freezer")] <> defaultFor s freezerC)

kept :: [(Window, Text)] -> Text
kept places = mconcat (intersperse " and " [describeWindow w <> " in the " <> place | (w, place) <- places]) <> ", as bought"

defaultFor :: Spoiling -> Double -> Text
defaultFor s c = "; the geometric mean at " <> degrees c <> ", with the default q10 for " <> idText s.technique

degrees :: Double -> Text
degrees c = show (round c :: Int) <> " °C"
