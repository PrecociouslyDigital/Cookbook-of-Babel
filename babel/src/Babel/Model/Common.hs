{-# LANGUAGE OverloadedLists #-}

-- | Values shared by several entity kinds.
module Babel.Model.Common (
    Intensity,
    mkIntensity,
    intensityValue,
    Amount (..),
    Issue (..),
    IssueKind (..),
)
where

import Autodocodec (HasCodec (..), bimapCodec, stringConstCodec)
import Babel.Codec (genericCodec)
import Babel.Model.Id (UnitId)

-- | How strongly an attribute is present: 0 (none) to 3 (dominant).
newtype Intensity = Intensity Int deriving newtype (Eq, Ord, Show)

mkIntensity :: Int -> Either String Intensity
mkIntensity n
    | n >= 0 && n <= 3 = Right (Intensity n)
    | otherwise = Left ("intensity must be 0-3, got " <> show n)

intensityValue :: Intensity -> Int
intensityValue (Intensity n) = n

instance HasCodec Intensity where
    codec = bimapCodec mkIntensity intensityValue codec

-- | A quantity in a unit from the units registry.
data Amount = Amount {qty :: Double, unit :: UnitId}
    deriving stock (Eq, Generic, Show)

instance HasCodec Amount where codec = genericCodec

data IssueKind = Estimate | Uncertain | Missing | Conflict
    deriving stock (Eq, Show, Enum, Bounded)

instance HasCodec IssueKind where
    codec =
        stringConstCodec
            [ (Estimate, "estimate")
            , (Uncertain, "uncertain")
            , (Missing, "missing")
            , (Conflict, "conflict")
            ]

-- | An optional annotation flagging a field that needs attention.
data Issue = Issue {field :: Text, kind :: IssueKind, note :: Text}
    deriving stock (Eq, Generic, Show)

instance HasCodec Issue where codec = genericCodec
