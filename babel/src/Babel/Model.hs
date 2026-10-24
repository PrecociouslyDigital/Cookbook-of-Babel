{-# LANGUAGE DuplicateRecordFields #-}
{-# LANGUAGE OverloadedLists #-}
{-# LANGUAGE OverloadedRecordDot #-}
{-# LANGUAGE NoFieldSelectors #-}
{-# OPTIONS_GHC -Wno-orphans #-}

{- | The data model. These types are the source of truth: the JSON files are
decoded through the codecs here, and later JSON Schema and language
bindings are derived from the same codecs.
-}
module Babel.Model (
    -- * Identifiers
    IsId (..),
    idCodec,
    isSlug,
    IngredientId (..),
    TechniqueId (..),
    RecipeId (..),
    SourceId (..),
    UnitId (..),
    MediumId (..),
    FormId (..),
    TextureAttrId (..),
    TasteAttrId (..),
    ServingTagId (..),

    -- * Shared values
    Intensity,
    mkIntensity,
    intensityValue,
    Amount (..),
    Issue (..),
    IssueKind (..),

    -- * Ingredients
    Ingredient (..),
    IsA (..),
    Nutrition (..),
    Per100g (..),

    -- * Techniques
    Technique (..),
    TempRange (..),

    -- * Recipes
    Recipe (..),
    SourceRef (..),
    Select (..),
    Leaf (..),
    selectMin,
    selectMax,
    Outcome (..),
    Branch (..),
    Output (..),
    Role (..),
    Environment (..),
    End (..),
    Sensitivity (..),

    -- * Sources and licences
    Source (..),
    Licence (..),
    licenceSpdx,
    licenceFromSpdx,
    licenceStrictness,

    -- * Registries
    Unit (..),
    Dimension (..),
    Medium (..),
    Term (..),

    -- * Dataset metadata
    Dataset (..),
    Version (..),
    parseVersion,
    renderVersion,
    Rename (..),
    EntityKind (..),
    entityKindName,

    -- * Temperature bands
    TempBand (..),
    bandFromCelsius,
)
where

import Autodocodec
import Data.Char (isAsciiLower, isDigit)
import Data.List.NonEmpty (NonEmpty)
import Data.List.NonEmpty qualified as NE
import Data.Map.Strict (Map)
import Data.Map.Strict qualified as Map
import Data.Maybe (fromMaybe)
import Data.Scientific (fromFloatDigits, toRealFloat)
import Data.Text (Text)
import Data.Text qualified as T
import Text.Read (readMaybe)

-- * Identifiers

{- | A human-readable slug identifier. Every entity kind has its own newtype,
so an ingredient ID can never stand in for a technique ID.
-}
class (Ord a) => IsId a where
    idText :: a -> Text
    mkId :: Text -> Either String a

idCodec :: (IsId a) => JSONCodec a
idCodec = bimapCodec mkId idText textCodec

-- | @^[a-z0-9]+(_[a-z0-9]+)*$@
isSlug :: Text -> Bool
isSlug t = not (T.null t) && all segment (T.splitOn "_" t)
  where
    segment s = not (T.null s) && T.all (\c -> isAsciiLower c || isDigit c) s

slugWith :: (Text -> a) -> Text -> Either String a
slugWith wrap t
    | isSlug t = Right (wrap t)
    | otherwise = Left ("not a slug (lowercase a-z, 0-9, single underscores): " <> show t)

-- | Recipe IDs are dotted slugs, e.g. @pancakes.mix_batter@.
dottedSlugWith :: (Text -> a) -> Text -> Either String a
dottedSlugWith wrap t
    | all isSlug (T.splitOn "." t) = Right (wrap t)
    | otherwise = Left ("not a dotted slug (slugs joined by '.'): " <> show t)

newtype IngredientId = IngredientId Text deriving newtype (Eq, Ord, Show)
newtype TechniqueId = TechniqueId Text deriving newtype (Eq, Ord, Show)
newtype RecipeId = RecipeId Text deriving newtype (Eq, Ord, Show)
newtype SourceId = SourceId Text deriving newtype (Eq, Ord, Show)
newtype UnitId = UnitId Text deriving newtype (Eq, Ord, Show)
newtype MediumId = MediumId Text deriving newtype (Eq, Ord, Show)
newtype FormId = FormId Text deriving newtype (Eq, Ord, Show)
newtype TextureAttrId = TextureAttrId Text deriving newtype (Eq, Ord, Show)
newtype TasteAttrId = TasteAttrId Text deriving newtype (Eq, Ord, Show)
newtype ServingTagId = ServingTagId Text deriving newtype (Eq, Ord, Show)

instance IsId IngredientId where
    idText (IngredientId t) = t
    mkId = slugWith IngredientId

instance IsId TechniqueId where
    idText (TechniqueId t) = t
    mkId = slugWith TechniqueId

instance IsId RecipeId where
    idText (RecipeId t) = t
    mkId = dottedSlugWith RecipeId

instance IsId SourceId where
    idText (SourceId t) = t
    mkId = slugWith SourceId

instance IsId UnitId where
    idText (UnitId t) = t
    mkId = slugWith UnitId

instance IsId MediumId where
    idText (MediumId t) = t
    mkId = slugWith MediumId

instance IsId FormId where
    idText (FormId t) = t
    mkId = slugWith FormId

instance IsId TextureAttrId where
    idText (TextureAttrId t) = t
    mkId = slugWith TextureAttrId

instance IsId TasteAttrId where
    idText (TasteAttrId t) = t
    mkId = slugWith TasteAttrId

instance IsId ServingTagId where
    idText (ServingTagId t) = t
    mkId = slugWith ServingTagId

instance HasCodec IngredientId where codec = idCodec
instance HasCodec TechniqueId where codec = idCodec
instance HasCodec RecipeId where codec = idCodec
instance HasCodec SourceId where codec = idCodec
instance HasCodec UnitId where codec = idCodec
instance HasCodec MediumId where codec = idCodec
instance HasCodec FormId where codec = idCodec
instance HasCodec TextureAttrId where codec = idCodec
instance HasCodec TasteAttrId where codec = idCodec
instance HasCodec ServingTagId where codec = idCodec

-- | A JSON object whose keys are IDs.
idMapCodec :: forall k v. (IsId k) => JSONCodec v -> JSONCodec (Map k v)
idMapCodec valueCodec' =
    bimapCodec
        (fmap Map.fromList . traverse (\(k, v) -> (,v) <$> mkId k) . Map.toList)
        (Map.mapKeys idText)
        (mapCodec valueCodec' :: JSONCodec (Map Text v))

-- * Shared values

{- | autodocodec leaves 'Double' out on purpose (NaN and infinities have no
JSON form). JSON numbers always decode to finite values, which is all the
data needs.
-}
instance HasCodec Double where
    codec = dimapCodec toRealFloat fromFloatDigits scientificCodec

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
    deriving stock (Eq, Show)

instance HasCodec Amount where
    codec =
        object "Amount" $
            Amount
                <$> requiredField' "qty" .= (.qty)
                <*> requiredField' "unit" .= (.unit)

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
    deriving stock (Eq, Show)

instance HasCodec Issue where
    codec =
        object "Issue" $
            Issue
                <$> requiredField' "field" .= (.field)
                <*> requiredField' "kind" .= (.kind)
                <*> requiredField' "note" .= (.note)

issuesField :: ObjectCodec [Issue] [Issue]
issuesField = optionalFieldWithOmittedDefault' "issues" []

-- * Ingredients

{- | One edge of the is-a DAG. The axis names the classification it belongs
to (e.g. @botanical@, @culinary@), so one ingredient can sit under
several parents without the hierarchy becoming ambiguous.
-}
data IsA = IsA {target :: IngredientId, axis :: Text}
    deriving stock (Eq, Show)

instance HasCodec IsA where
    codec =
        object "IsA" $
            IsA
                <$> requiredField' "target" .= (.target)
                <*> requiredFieldWith' "axis" (bimapCodec (slugWith id) id textCodec) .= (.axis)

data Per100g = Per100g
    { kcal :: Maybe Double
    , proteinG :: Maybe Double
    , fatG :: Maybe Double
    , carbsG :: Maybe Double
    }
    deriving stock (Eq, Show)

instance HasCodec Per100g where
    codec =
        object "Per100g" $
            Per100g
                <$> optionalField' "kcal" .= (.kcal)
                <*> optionalField' "proteinG" .= (.proteinG)
                <*> optionalField' "fatG" .= (.fatG)
                <*> optionalField' "carbsG" .= (.carbsG)

data Nutrition = Nutrition {per100g :: Per100g, source :: SourceId}
    deriving stock (Eq, Show)

instance HasCodec Nutrition where
    codec =
        object "Nutrition" $
            Nutrition
                <$> requiredField' "per100g" .= (.per100g)
                <*> requiredField' "source" .= (.source)

{- | Whether an ingredient is an intermediate, and how it spoils, are
computed from the recipe graph and never stored here. Spoilage, melting
and setting are all recipes; 'form' is the ingredient's own state.
-}
data Ingredient = Ingredient
    { id :: IngredientId
    , label :: Text
    , aliases :: [Text]
    , parents :: [IsA]
    , form :: FormId
    , texture :: Map TextureAttrId Intensity
    , taste :: Map TasteAttrId Intensity
    , servingTags :: [ServingTagId]
    , densityGPerMl :: Maybe Double
    , waterActivity :: Maybe Double
    , nutrition :: Maybe Nutrition
    , named :: Bool
    -- ^ A named dish or product ("croutons") rather than a generic food.
    , issues :: [Issue]
    }
    deriving stock (Eq, Show)

instance HasCodec Ingredient where
    codec =
        object "Ingredient" $
            Ingredient
                <$> requiredField' "id" .= (.id)
                <*> requiredField' "label" .= (.label)
                <*> optionalFieldWithOmittedDefault' "aliases" [] .= (.aliases)
                <*> optionalFieldWithOmittedDefault' "parents" [] .= (.parents)
                <*> requiredField' "form" .= (.form)
                <*> optionalFieldWithOmittedDefaultWith' "texture" (idMapCodec codec) Map.empty .= (.texture)
                <*> optionalFieldWithOmittedDefaultWith' "taste" (idMapCodec codec) Map.empty .= (.taste)
                <*> optionalFieldWithOmittedDefault' "servingTags" [] .= (.servingTags)
                <*> optionalField' "densityGPerMl" .= (.densityGPerMl)
                <*> optionalField' "waterActivity" .= (.waterActivity)
                <*> optionalField' "nutrition" .= (.nutrition)
                <*> optionalFieldWithOmittedDefault' "named" False .= (.named)
                <*> issuesField .= (.issues)

-- * Techniques

data TempRange = TempRange {minC :: Double, maxC :: Double}
    deriving stock (Eq, Show)

instance HasCodec TempRange where
    codec =
        object "TempRange" $
            TempRange
                <$> requiredField' "minC" .= (.minC)
                <*> requiredField' "maxC" .= (.maxC)

data Technique = Technique
    { id :: TechniqueId
    , label :: Text
    , parents :: [TechniqueId]
    , typicalTemp :: Maybe TempRange
    , issues :: [Issue]
    }
    deriving stock (Eq, Show)

instance HasCodec Technique where
    codec =
        object "Technique" $
            Technique
                <$> requiredField' "id" .= (.id)
                <*> requiredField' "label" .= (.label)
                <*> optionalFieldWithOmittedDefault' "parents" [] .= (.parents)
                <*> optionalField' "typicalTemp" .= (.typicalTemp)
                <*> issuesField .= (.issues)

-- * Recipes

{- | Where an extracted step came from: a stable key used to match reviewed
overlays against re-extracted data.
-}
data SourceRef = SourceRef {source :: SourceId, step :: Int}
    deriving stock (Eq, Ord, Show)

instance HasCodec SourceRef where
    codec =
        object "SourceRef" $
            SourceRef
                <$> requiredField' "source" .= (.source)
                <*> requiredField' "step" .= (.step)

data Leaf = Leaf {ingredient :: IngredientId, amount :: Amount}
    deriving stock (Eq, Show)

instance HasCodec Leaf where
    codec =
        object "Leaf" $
            Leaf
                <$> requiredField' "ingredient" .= (.ingredient)
                <*> requiredField' "amount" .= (.amount)

{- | Pick between 'minCount' and 'maxCount' of 'choices'. Both default to
the number of choices, so a bare @{"of": [...]}@ means "all of these".
This one shape covers all / anyOf / oneOf / optional.
-}
data Select = Select
    { minCount :: Maybe Int
    , maxCount :: Maybe Int
    , choices :: [Either Select Leaf]
    }
    deriving stock (Eq, Show)

selectMin, selectMax :: Select -> Int
selectMin s = fromMaybe (length s.choices) s.minCount
selectMax s = fromMaybe (length s.choices) s.maxCount

instance HasCodec Select where
    codec =
        named "Select" . object "Select" $
            Select
                <$> optionalField' "min" .= (.minCount)
                <*> optionalField' "max" .= (.maxCount)
                <*> requiredFieldWith' "of" (listCodec (disjointEitherCodec codec codec)) .= (.choices)

data Role = Main | Byproduct | Part
    deriving stock (Eq, Show, Enum, Bounded)

instance HasCodec Role where
    codec = stringConstCodec [(Main, "main"), (Byproduct, "byproduct"), (Part, "part")]

-- | 'chance' rolls independently per output.
data Output = Output
    { ingredient :: IngredientId
    , amount :: Amount
    , role :: Role
    , chance :: Maybe Double
    }
    deriving stock (Eq, Show)

instance HasCodec Output where
    codec =
        object "Output" $
            Output
                <$> requiredField' "ingredient" .= (.ingredient)
                <*> requiredField' "amount" .= (.amount)
                <*> optionalFieldWithOmittedDefault' "role" Main .= (.role)
                <*> optionalField' "chance" .= (.chance)

data Branch = Branch {weight :: Double, outcome :: Outcome}
    deriving stock (Eq, Show)

{- | In JSON an 'Outputs' is a plain array, and a 'OneOf' is
@{"oneOf": [{"weight": w, "outputs": ...}, ...]}@. 'OneOf' picks exactly
one branch by weight.
-}
data Outcome
    = Outputs (NonEmpty Output)
    | OneOf (NonEmpty Branch)
    deriving stock (Eq, Show)

instance HasCodec Outcome where
    codec =
        named "Outcome" $
            dimapCodec
                (either Outputs OneOf)
                (\case Outputs os -> Left os; OneOf bs -> Right bs)
                (disjointEitherCodec codec (object "OneOf" (requiredField' "oneOf")))

instance HasCodec Branch where
    codec =
        object "Branch" $
            Branch
                <$> requiredField' "weight" .= (.weight)
                <*> requiredField' "outputs" .= (.outcome)

{- | The heat-transfer medium: a registry alias, or a heat-transfer
coefficient @h@ in W/m²K.
-}
data Environment = Environment {temperatureC :: Double, medium :: Either MediumId Double}
    deriving stock (Eq, Show)

instance HasCodec Environment where
    codec =
        object "Environment" $
            Environment
                <$> requiredField' "temperatureC" .= (.temperatureC)
                <*> requiredFieldWith' "medium" (disjointEitherCodec codec codec) .= (.medium)

{- | How a step's duration scales with conditions. The duration is measured
at 'refTempC'; Q10 is the rate multiplier per 10 °C warmer. Outside the
water-activity bounds the step does not happen at all.
-}
data Sensitivity = Sensitivity
    { refTempC :: Double
    , q10 :: Double
    , minWaterActivity :: Maybe Double
    , maxWaterActivity :: Maybe Double
    }
    deriving stock (Eq, Show)

instance HasCodec Sensitivity where
    codec =
        object "Sensitivity" $
            Sensitivity
                <$> requiredField' "refTempC" .= (.refTempC)
                <*> requiredField' "q10" .= (.q10)
                <*> optionalField' "minWaterActivity" .= (.minWaterActivity)
                <*> optionalField' "maxWaterActivity" .= (.maxWaterActivity)

{- | When a step is done. 'ByBoth' means: reach the core temperature, then
hold it for the duration.
-}
data End
    = ByCoreTemp {coreTempC :: Double}
    | ByTime {duration :: Amount, sensitivity :: Maybe Sensitivity}
    | ByBoth {coreTempC :: Double, duration :: Amount}
    deriving stock (Eq, Show)

instance HasCodec End where
    codec = object "End" $ discriminatedUnionCodec "by" enc dec
      where
        coreTemp = requiredField' "coreTempC"
        byTime = (,) <$> requiredField' "duration" .= fst <*> optionalField' "sensitivity" .= snd
        byBoth = (,) <$> requiredField' "coreTempC" .= fst <*> requiredField' "duration" .= snd
        enc = \case
            ByCoreTemp t -> ("coreTemp", mapToEncoder t coreTemp)
            ByTime d s -> ("time", mapToEncoder (d, s) byTime)
            ByBoth t d -> ("both", mapToEncoder (t, d) byBoth)
        dec =
            [ ("coreTemp", ("ByCoreTemp", mapToDecoder ByCoreTemp coreTemp))
            , ("time", ("ByTime", mapToDecoder (uncurry ByTime) byTime))
            , ("both", ("ByBoth", mapToDecoder (uncurry ByBoth) byBoth))
            ]

-- | One step. A dish is a chain of these.
data Recipe = Recipe
    { id :: RecipeId
    , source :: SourceId
    , sourceRef :: Maybe SourceRef
    , technique :: TechniqueId
    , inputs :: Select
    , outputs :: Outcome
    , environment :: Maybe Environment
    -- ^ Absent: ambient conditions supplied by the game.
    , end :: End
    , stateNote :: Maybe Text
    -- ^ Human/LLM-readable end state, e.g. "until golden".
    , issues :: [Issue]
    }
    deriving stock (Eq, Show)

instance HasCodec Recipe where
    codec =
        object "Recipe" $
            Recipe
                <$> requiredField' "id" .= (.id)
                <*> requiredField' "source" .= (.source)
                <*> optionalField' "sourceRef" .= (.sourceRef)
                <*> requiredField' "technique" .= (.technique)
                <*> requiredField' "inputs" .= (.inputs)
                <*> requiredField' "outputs" .= (.outputs)
                <*> optionalField' "environment" .= (.environment)
                <*> requiredField' "end" .= (.end)
                <*> optionalField' "stateNote" .= (.stateNote)
                <*> issuesField .= (.issues)

-- * Sources and licences

{- | SPDX-mapped. 'Original' (@LicenseRef-original@) is content written for
this project.
-}
data Licence = Cc0 | Original | CcBy40 | CcBySa40
    deriving stock (Eq, Show, Enum, Bounded)

licenceSpdx :: Licence -> Text
licenceSpdx = \case
    Cc0 -> "CC0-1.0"
    Original -> "LicenseRef-original"
    CcBy40 -> "CC-BY-4.0"
    CcBySa40 -> "CC-BY-SA-4.0"

licenceFromSpdx :: Text -> Maybe Licence
licenceFromSpdx t = lookup t (map (\l -> (licenceSpdx l, l)) [minBound .. maxBound])

{- | Higher is stricter. A build's effective licence is the strictest one
of anything it includes.
-}
licenceStrictness :: Licence -> Int
licenceStrictness = fromEnum

instance HasCodec Licence where
    codec = stringConstCodec (NE.fromList [(l, licenceSpdx l) | l <- [minBound ..]])

data Source = Source {id :: SourceId, name :: Text, url :: Maybe Text, licence :: Licence}
    deriving stock (Eq, Show)

instance HasCodec Source where
    codec =
        object "Source" $
            Source
                <$> requiredField' "id" .= (.id)
                <*> requiredField' "name" .= (.name)
                <*> optionalField' "url" .= (.url)
                <*> requiredField' "licence" .= (.licence)

-- * Registries

data Dimension = Mass | Volume | Count | Time
    deriving stock (Eq, Show, Enum, Bounded)

instance HasCodec Dimension where
    codec = stringConstCodec [(Mass, "mass"), (Volume, "volume"), (Count, "count"), (Time, "time")]

-- | 'toBase' converts to the dimension's base unit: g, ml, piece or s.
data Unit = Unit {id :: UnitId, dimension :: Dimension, toBase :: Double}
    deriving stock (Eq, Show)

instance HasCodec Unit where
    codec =
        object "Unit" $
            Unit
                <$> requiredField' "id" .= (.id)
                <*> requiredField' "dimension" .= (.dimension)
                <*> requiredField' "toBase" .= (.toBase)

-- | A heat-transfer medium; 'h' is an order-of-magnitude coefficient in W/m²K.
data Medium = Medium {id :: MediumId, label :: Text, h :: Double}
    deriving stock (Eq, Show)

instance HasCodec Medium where
    codec =
        object "Medium" $
            Medium
                <$> requiredField' "id" .= (.id)
                <*> requiredField' "label" .= (.label)
                <*> requiredField' "h" .= (.h)

{- | An entry in one of the vocabulary registries (forms, texture, taste,
serving). The ID type says which registry it belongs to.
-}
data Term i = Term {id :: i, label :: Text, description :: Maybe Text}
    deriving stock (Eq, Show)

instance (IsId i) => HasCodec (Term i) where
    codec =
        object "Term" $
            Term
                <$> requiredFieldWith' "id" idCodec .= (.id)
                <*> requiredField' "label" .= (.label)
                <*> optionalField' "description" .= (.description)

-- * Dataset metadata

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
    deriving stock (Eq, Show)

instance HasCodec Dataset where
    codec = object "Dataset" $ Dataset <$> requiredField' "version" .= (.version)

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

{- | @from@ was renamed to @to@ in release @since@. Slugs are kept as text
here because the kind decides which ID type they belong to.
-}
data Rename = Rename {since :: Version, kind :: EntityKind, from :: Text, to :: Text}
    deriving stock (Eq, Show)

instance HasCodec Rename where
    codec =
        object "Rename" $
            Rename
                <$> requiredField' "since" .= (.since)
                <*> requiredField' "kind" .= (.kind)
                <*> requiredFieldWith' "from" renameSlug .= (.from)
                <*> requiredFieldWith' "to" renameSlug .= (.to)
      where
        renameSlug = bimapCodec (dottedSlugWith Prelude.id) Prelude.id textCodec

-- * Temperature bands

data TempBand = Frozen | Chilled | Ambient | Warm | Hot | Boiling
    deriving stock (Eq, Ord, Show, Enum, Bounded)

{- | Upper bounds (exclusive): frozen < 0 °C, chilled < 8, ambient < 30,
warm < 60, hot < 100, boiling and above from 100.
-}
bandFromCelsius :: Double -> TempBand
bandFromCelsius c
    | c < 0 = Frozen
    | c < 8 = Chilled
    | c < 30 = Ambient
    | c < 60 = Warm
    | c < 100 = Hot
    | otherwise = Boiling
