{-# LANGUAGE DataKinds #-}
{-# LANGUAGE UndecidableInstances #-}
{-# OPTIONS_GHC -Wno-orphans #-}

{- | Codec plumbing shared by the model: slug identifiers, and a generic
derivation of record codecs.

'genericCodec' turns a single-constructor record into a JSON object whose
keys are the field names, in declaration order (which is also the
canonical key order, see "Babel.Format"). How each field is encoded is
decided by its type through 'FieldCodec':

* @Maybe a@: optional key, omitted when 'Nothing';
* @[a]@, @Map k v@, 'Bool': optional key, omitted when empty or 'False';
* anything else: required key.

A type can pick its own default by giving its own 'FieldCodec' instance.
autodocodec deliberately ships no generic derivation, so that renaming a
Haskell field can't silently change the format. Here the Haskell types are
meant to be the source of truth for the format, so that coupling is the
point; changing a field name is a breaking model change.
-}
module Babel.Codec (
    -- * Identifiers
    IsId (..),
    Slug (..),
    DottedSlug (..),
    isSlug,
    idCodec,
    idMapCodec,

    -- * Generic records
    genericCodec,
    FieldCodec (..),
)
where

import Autodocodec
import Data.Char (isAsciiLower, isDigit)
import Data.Map.Strict qualified as Map
import Data.Scientific (fromFloatDigits, toRealFloat)
import Data.Text qualified as T
import GHC.Generics
import GHC.TypeLits (KnownSymbol, symbolVal)

-- * Identifiers

{- | A human-readable slug identifier. Every entity kind has its own
newtype, so an ingredient ID can never stand in for a technique ID.
Instances are derived via 'Slug' or 'DottedSlug'.
-}
class (Ord a) => IsId a where
    idText :: a -> Text
    mkId :: Text -> Either String a

-- | @^[a-z0-9]+(_[a-z0-9]+)*$@
newtype Slug = Slug Text
    deriving newtype (Eq, Ord, Show)

instance IsId Slug where
    idText (Slug t) = t
    mkId t
        | isSlug t = Right (Slug t)
        | otherwise = Left ("not a slug (lowercase a-z, 0-9, single underscores): " <> show t)

-- | Slugs joined by @.@, e.g. @pancakes.mix_dry@.
newtype DottedSlug = DottedSlug Text
    deriving newtype (Eq, Ord, Show)

instance IsId DottedSlug where
    idText (DottedSlug t) = t
    mkId t
        | all isSlug (T.splitOn "." t) = Right (DottedSlug t)
        | otherwise = Left ("not a dotted slug (slugs joined by '.'): " <> show t)

instance HasCodec Slug where codec = idCodec
instance HasCodec DottedSlug where codec = idCodec

isSlug :: Text -> Bool
isSlug t = not (T.null t) && all segment (T.splitOn "_" t)
  where
    segment s = not (T.null s) && T.all (\c -> isAsciiLower c || isDigit c) s

idCodec :: (IsId a) => JSONCodec a
idCodec = bimapCodec mkId idText textCodec

-- | A JSON object whose keys are IDs.
idMapCodec :: forall k v. (IsId k) => JSONCodec v -> JSONCodec (Map k v)
idMapCodec valueCodec' =
    bimapCodec
        (fmap Map.fromList . traverse (\(k, v) -> (,v) <$> mkId k) . Map.toList)
        (Map.mapKeys idText)
        (mapCodec valueCodec' :: JSONCodec (Map Text v))

{- | autodocodec leaves 'Double' out on purpose (NaN and infinities have no
JSON form). JSON numbers always decode to finite values, which is all the
data needs.
-}
instance HasCodec Double where
    codec = dimapCodec toRealFloat fromFloatDigits scientificCodec

-- * Generic records

genericCodec :: (Generic a, GRecord (Rep a)) => JSONCodec a
genericCodec = dimapCodec to from gRecord

class FieldCodec t where
    fieldCodec :: Text -> ObjectCodec t t

instance {-# OVERLAPPABLE #-} (HasCodec t) => FieldCodec t where
    fieldCodec = requiredField'

instance (HasCodec t) => FieldCodec (Maybe t) where
    fieldCodec = optionalField'

instance (Eq t, HasCodec t) => FieldCodec [t] where
    fieldCodec k = optionalFieldWithOmittedDefault' k []

instance (IsId k, Eq v, HasCodec v) => FieldCodec (Map k v) where
    fieldCodec k = optionalFieldWithOmittedDefaultWith' k (idMapCodec codec) Map.empty

instance FieldCodec Bool where
    fieldCodec k = optionalFieldWithOmittedDefault' k False

-- | Alternatives must be told apart by shape (e.g. a string vs a number).
instance (HasCodec a, HasCodec b) => FieldCodec (Either a b) where
    fieldCodec k = requiredFieldWith' k (disjointEitherCodec codec codec)

class GRecord f where
    gRecord :: JSONCodec (f p)

instance (KnownSymbol name, GFields f) => GRecord (D1 ('MetaData name m pkg nt) (C1 c f)) where
    gRecord = dimapCodec (M1 . M1) (unM1 . unM1) (object (toText (symbolVal (Proxy @name))) gFields)

class GFields f where
    gFields :: ObjectCodec (f p) (f p)

instance (GFields f, GFields g) => GFields (f :*: g) where
    gFields = (:*:) <$> gFields .= (\(x :*: _) -> x) <*> gFields .= (\(_ :*: y) -> y)

instance (KnownSymbol name, FieldCodec t) => GFields (S1 ('MetaSel ('Just name) u s l) (Rec0 t)) where
    gFields = dimapCodec (M1 . K1) (unK1 . unM1) (fieldCodec (toText (symbolVal (Proxy @name))))
