-- | Dataset version and the rename log.
module Babel.Model.Dataset (
    Dataset (..),
    Version (..),
    parseVersion,
    renderVersion,
    Rename (..),
    EntityKind (..),
    entityKindName,
)
where

import Autodocodec (HasCodec (..), JSONCodec, bimapCodec, dimapCodec, discriminatedUnionCodec, disjointEitherCodec, listCodec, mapToDecoder, mapToEncoder, named, object, optionalField', optionalFieldWithOmittedDefault', requiredField', requiredFieldWith', stringConstCodec, textCodec, (.=))
import Babel.Codec (DottedSlug (..), FieldCodec (..), IsId (..), Slug (..), genericCodec, idCodec)
import Data.Char (isDigit)
import Data.List.NonEmpty qualified as NE
import Data.Text qualified as T

data Version = Version {major :: Int, minor :: Int, patch :: Int}
    deriving stock (Eq, Ord, Show)

-- | Plain @MAJOR.MINOR.PATCH@; no pre-release or build metadata.
parseVersion :: Text -> Either String Version
parseVersion t = case traverse num (T.splitOn "." t) of
    Just [a, b, c] -> Right (Version a b c)
    _ -> Left ("not a MAJOR.MINOR.PATCH version: " <> show t)
  where
    num s
        | T.null s || not (T.all isDigit s) = Nothing
        | T.length s > 1 && T.head s == '0' = Nothing
        | otherwise = readMaybe (T.unpack s)

renderVersion :: Version -> Text
renderVersion v = T.intercalate "." (map (T.pack . show) [v.major, v.minor, v.patch])

instance HasCodec Version where
    codec = bimapCodec parseVersion renderVersion textCodec

newtype Dataset = Dataset {version :: Version}
    deriving stock (Eq, Generic, Show)

instance HasCodec Dataset where codec = genericCodec

data EntityKind
    = KIngredient
    | KTechnique
    | KRecipe
    | KSource
    | KUnit
    | KMedium
    | KForm
    | KTexture
    | KTaste
    | KServing
    deriving stock (Eq, Ord, Show, Enum, Bounded)

entityKindName :: EntityKind -> Text
entityKindName = \case
    KIngredient -> "ingredient"
    KTechnique -> "technique"
    KRecipe -> "recipe"
    KSource -> "source"
    KUnit -> "unit"
    KMedium -> "medium"
    KForm -> "form"
    KTexture -> "texture"
    KTaste -> "taste"
    KServing -> "serving"

instance HasCodec EntityKind where
    codec = stringConstCodec (NE.fromList [(k, entityKindName k) | k <- [minBound ..]])

{- | @from@ was renamed to @to@ in release @since@. The slugs are untyped
because 'kind' decides which ID type they belong to.
-}
data Rename = Rename {since :: Version, kind :: EntityKind, from :: DottedSlug, to :: DottedSlug}
    deriving stock (Eq, Generic, Show)

instance HasCodec Rename where codec = genericCodec
