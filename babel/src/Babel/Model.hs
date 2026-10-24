{-# LANGUAGE DerivingVia #-}
{-# LANGUAGE OverloadedLists #-}

{- | The data model. These types are the source of truth: the JSON files are
decoded through the codecs here, and later JSON Schema and language
bindings are derived from the same codecs.
-}
module Babel.Model (
    -- * Identifiers
    IsId (..),
    Slug (..),
    DottedSlug (..),
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
    AxisId (..),

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

    -- * Tuning constants
    Constants (..),
    KcalPerGram (..),
    Tolerance (..),
    RatioBounds (..),
    TempBandId (..),
    TempBands,
    BandEntry (..),
    mkTempBands,
    tempBandEntries,
    bandFromCelsius,
)
where

import Autodocodec
import Babel.Codec
import Data.Char (isDigit)
import Data.List.NonEmpty qualified as NE
import Data.Text qualified as T

-- * Identifiers

newtype IngredientId = IngredientId Text
    deriving newtype (Eq, Ord, Show)
    deriving (IsId) via Slug

newtype TechniqueId = TechniqueId Text
    deriving newtype (Eq, Ord, Show)
    deriving (IsId) via Slug

newtype RecipeId = RecipeId Text
    deriving newtype (Eq, Ord, Show)
    deriving (IsId) via DottedSlug

newtype SourceId = SourceId Text
    deriving newtype (Eq, Ord, Show)
    deriving (IsId) via Slug

newtype UnitId = UnitId Text
    deriving newtype (Eq, Ord, Show)
    deriving (IsId) via Slug

newtype MediumId = MediumId Text
    deriving newtype (Eq, Ord, Show)
    deriving (IsId) via Slug

newtype FormId = FormId Text
    deriving newtype (Eq, Ord, Show)
    deriving (IsId) via Slug

newtype TextureAttrId = TextureAttrId Text
    deriving newtype (Eq, Ord, Show)
    deriving (IsId) via Slug

newtype TasteAttrId = TasteAttrId Text
    deriving newtype (Eq, Ord, Show)
    deriving (IsId) via Slug

newtype ServingTagId = ServingTagId Text
    deriving newtype (Eq, Ord, Show)
    deriving (IsId) via Slug

newtype AxisId = AxisId Text
    deriving newtype (Eq, Ord, Show)
    deriving (IsId) via Slug

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
instance HasCodec AxisId where codec = idCodec

-- * Shared values

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

-- * Ingredients

{- | One edge of the is-a DAG. The axis names the classification it belongs
to (e.g. @botanical@, @culinary@), so one ingredient can sit under
several parents without the hierarchy becoming ambiguous.
-}
data IsA = IsA {target :: IngredientId, axis :: AxisId}
    deriving stock (Eq, Generic, Show)

instance HasCodec IsA where codec = genericCodec

data Per100g = Per100g
    { kcal :: Maybe Double
    , proteinG :: Maybe Double
    , fatG :: Maybe Double
    , carbsG :: Maybe Double
    }
    deriving stock (Eq, Generic, Show)

instance HasCodec Per100g where codec = genericCodec

data Nutrition = Nutrition {per100g :: Per100g, source :: SourceId}
    deriving stock (Eq, Generic, Show)

instance HasCodec Nutrition where codec = genericCodec

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
    deriving stock (Eq, Generic, Show)

instance HasCodec Ingredient where codec = genericCodec

-- * Techniques

data TempRange = TempRange {minC :: Double, maxC :: Double}
    deriving stock (Eq, Generic, Show)

instance HasCodec TempRange where codec = genericCodec

data Technique = Technique
    { id :: TechniqueId
    , label :: Text
    , parents :: [TechniqueId]
    , typicalTemp :: Maybe TempRange
    , issues :: [Issue]
    }
    deriving stock (Eq, Generic, Show)

instance HasCodec Technique where codec = genericCodec

-- * Recipes

{- | Where an extracted step came from: a stable key used to match reviewed
overlays against re-extracted data.
-}
data SourceRef = SourceRef {source :: SourceId, step :: Int}
    deriving stock (Eq, Generic, Ord, Show)

instance HasCodec SourceRef where codec = genericCodec

data Leaf = Leaf {ingredient :: IngredientId, amount :: Amount}
    deriving stock (Eq, Generic, Show)

instance HasCodec Leaf where codec = genericCodec

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

-- | Most outputs are the main product, so @role@ may be left out.
instance FieldCodec Role where
    fieldCodec k = optionalFieldWithOmittedDefault' k Main

-- | 'chance' rolls independently per output.
data Output = Output
    { ingredient :: IngredientId
    , amount :: Amount
    , role :: Role
    , chance :: Maybe Double
    }
    deriving stock (Eq, Generic, Show)

instance HasCodec Output where codec = genericCodec

data Branch = Branch {weight :: Double, outputs :: Outcome}
    deriving stock (Eq, Generic, Show)

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

instance HasCodec Branch where codec = genericCodec

{- | The heat-transfer medium: a registry alias, or a heat-transfer
coefficient @h@ in W/m²K.
-}
data Environment = Environment {temperatureC :: Double, medium :: Either MediumId Double}
    deriving stock (Eq, Generic, Show)

instance HasCodec Environment where codec = genericCodec

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
    deriving stock (Eq, Generic, Show)

instance HasCodec Sensitivity where codec = genericCodec

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
    deriving stock (Eq, Generic, Show)

instance HasCodec Recipe where codec = genericCodec

-- * Sources and licences

{- | SPDX-mapped. 'Original' (@LicenseRef-original@) is content written for
this project.
-}
data Licence = Cc0 | Original | CcBy40 | CcBySa40
    deriving stock (Eq, Ord, Show, Enum, Bounded)

licenceSpdx :: Licence -> Text
licenceSpdx = \case
    Cc0 -> "CC0-1.0"
    Original -> "LicenseRef-original"
    CcBy40 -> "CC-BY-4.0"
    CcBySa40 -> "CC-BY-SA-4.0"

licenceFromSpdx :: Text -> Maybe Licence
licenceFromSpdx t = find ((== t) . licenceSpdx) universe

{- | Higher is stricter. A build's effective licence is the strictest one
of anything it includes.
-}
licenceStrictness :: Licence -> Int
licenceStrictness = fromEnum

instance HasCodec Licence where
    codec = stringConstCodec (NE.fromList [(l, licenceSpdx l) | l <- [minBound ..]])

data Source = Source {id :: SourceId, name :: Text, url :: Maybe Text, licence :: Licence}
    deriving stock (Eq, Generic, Show)

instance HasCodec Source where codec = genericCodec

-- * Registries

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

-- * Tuning constants

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

newtype TempBandId = TempBandId Text
    deriving newtype (Eq, Ord, Show)
    deriving (IsId) via Slug

instance HasCodec TempBandId where codec = idCodec

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
