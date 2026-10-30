{-# LANGUAGE OverloadedLists #-}

-- | Entries of the units, media and vocabulary registries.
module Babel.Model.Registry (
    Unit (..),
    Dimension (..),
    Medium (..),
    Term (..),
    Tint (..),
)
where

import Autodocodec (HasCodec (..), stringConstCodec)
import Babel.Codec (genericCodec)
import Babel.Model.Id (MediumId, TintId, UnitId)

data Dimension = Mass | Volume | Count | Time
    deriving stock (Eq, Show, Enum, Bounded)

instance HasCodec Dimension where
    codec = stringConstCodec [(Mass, "mass"), (Volume, "volume"), (Count, "count"), (Time, "time")]

-- | 'toBase' converts to the dimension's base unit: g, ml, piece or s.
data Unit = Unit {id :: UnitId, dimension :: Dimension, toBase :: Double}
    deriving stock (Eq, Generic, Show)

instance HasCodec Unit where codec = genericCodec

-- | A heat-transfer medium; 'h' is an order-of-magnitude coefficient in W/m²K.
data Medium = Medium {id :: MediumId, label :: Text, h :: Double}
    deriving stock (Eq, Generic, Show)

instance HasCodec Medium where codec = genericCodec

{- | An entry in one of the vocabulary registries (forms, texture, taste,
serving). The ID type says which registry it belongs to.
-}
data Term i = Term {id :: i, label :: Text, description :: Maybe Text}
    deriving stock (Eq, Generic, Show)

instance (HasCodec i) => HasCodec (Term i) where codec = genericCodec

{- | One food word's tint: an ingredient whose label carries the word inherits
its colour, so "tomato paste" and "tomato, dissolve" tint alike.
-}
data Tint = Tint {id :: TintId, color :: Text}
    deriving stock (Eq, Generic, Show)

instance HasCodec Tint where codec = genericCodec
