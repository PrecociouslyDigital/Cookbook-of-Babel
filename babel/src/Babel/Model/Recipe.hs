{-# LANGUAGE OverloadedLists #-}

-- | Recipes: one step each; a dish is a chain of them.
module Babel.Model.Recipe (
    Recipe (..),
    SourceRef (..),
    Select (..),
    Leaf (..),
    selectMin,
    selectMax,
    leaves,
    minimalLeaves,
    Outcome (..),
    Branch (..),
    Output (..),
    Role (..),
    Environment (..),
    End (..),
    Sensitivity (..),
)
where

import Autodocodec (HasCodec (..), dimapCodec, discriminatedUnionCodec, disjointEitherCodec, listCodec, mapToDecoder, mapToEncoder, named, object, optionalField', optionalFieldWithOmittedDefault', requiredField', requiredFieldWith', stringConstCodec, (.=))
import Babel.Codec (FieldCodec (..), genericCodec)
import Babel.Model.Common (Amount, Issue)
import Babel.Model.Id (IngredientId, MediumId, RecipeId, SourceId, TechniqueId)

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

-- | Every leaf a select could take in.
leaves :: Select -> [Leaf]
leaves s = rights s.choices ++ concatMap leaves (lefts s.choices)

{- | The leaves of the least a select takes in: its first 'selectMin'
choices, each at its own least.
-}
minimalLeaves :: Select -> [Leaf]
minimalLeaves s = concatMap (either minimalLeaves pure) (take (selectMin s) s.choices)

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
