-- | Judgement-call numbers, loaded from @registries/constants.json@.
module Babel.Model.Constants (
    Constants (..),
    KcalPerGram (..),
    Tolerance (..),
    RatioBounds (..),
    TempBands,
    BandEntry (..),
    mkTempBands,
    tempBandEntries,
    bandFromCelsius,
)
where

import Autodocodec (HasCodec (..), bimapCodec)
import Babel.Codec (IsId (..), genericCodec)
import Babel.Model.Id (TechniqueId, TempBandId)

{- | Numbers the checks and helpers depend on that are judgement calls, not
schema. They live in @registries/constants.json@ so changing one is a data
change, reviewed like any other.
-}
data Constants = Constants
    { tempBands :: TempBands
    , spoilageTechnique :: TechniqueId
    -- ^ Recipes whose technique is this one or below it are spoilage.
    , kcalPerGram :: KcalPerGram
    , calorieTolerance :: Tolerance
    -- ^ How far kcal may stray from the macros before a warning.
    , massRatio :: RatioBounds
    -- ^ Plausible output/input mass ratio of one step; outside it, a warning.
    }
    deriving stock (Eq, Generic, Show)

instance HasCodec Constants where codec = genericCodec

data KcalPerGram = KcalPerGram {protein :: Double, carbs :: Double, fat :: Double}
    deriving stock (Eq, Generic, Show)

instance HasCodec KcalPerGram where codec = genericCodec

-- | Off by more than @max kcal (fraction * expected)@.
data Tolerance = Tolerance {kcal :: Double, fraction :: Double}
    deriving stock (Eq, Generic, Show)

instance HasCodec Tolerance where codec = genericCodec

data RatioBounds = RatioBounds {min :: Double, max :: Double}
    deriving stock (Eq, Generic, Show)

instance HasCodec RatioBounds where codec = genericCodec

-- | One band as written in JSON: the last band has no upper bound.
data BandEntry = BandEntry {id :: TempBandId, belowC :: Maybe Double}
    deriving stock (Eq, Generic, Show)

instance HasCodec BandEntry where codec = genericCodec

{- | Bounded bands with strictly increasing upper bounds (exclusive), then
one open-ended top band. Only 'mkTempBands' builds these, which is what
makes 'bandFromCelsius' total.
-}
data TempBands = TempBands [(TempBandId, Double)] TempBandId
    deriving stock (Eq, Show)

mkTempBands :: [BandEntry] -> Either String TempBands
mkTempBands entries = case reverse entries of
    BandEntry top Nothing : rest -> do
        bounded <- forM (reverse rest) $ \case
            BandEntry b (Just c) -> Right (b, c)
            BandEntry b Nothing -> Left ("only the last band may omit belowC, but " <> show (idText b) <> " does")
        let bounds = map snd bounded
            ids = top : map fst bounded
        unless (and (zipWith (<) bounds (drop 1 bounds))) $ Left "belowC must strictly increase"
        unless (length (ordNub ids) == length ids) $ Left "band IDs must be unique"
        Right (TempBands bounded top)
    _ -> Left "the last band must omit belowC, so every temperature has a band"

tempBandEntries :: TempBands -> [BandEntry]
tempBandEntries (TempBands bounded top) = [BandEntry b (Just c) | (b, c) <- bounded] ++ [BandEntry top Nothing]

instance HasCodec TempBands where
    codec = bimapCodec mkTempBands tempBandEntries codec

-- | The first band whose bound lies above the temperature, else the top one.
bandFromCelsius :: TempBands -> Double -> TempBandId
bandFromCelsius (TempBands bounded top) c = maybe top fst (find ((c <) . snd) bounded)
