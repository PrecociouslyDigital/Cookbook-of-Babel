{- | Requests to mammoth-1, OpenRouter's decision model. A request is a
state and a set of typed questions; the model answers each with
probabilities. An 'Ask' builds the questions and the decoder of their
answers together, so decoding yields a complete typed record or says
which answer is missing.
-}
module Wikibooks.Questions (
    Judged (..),
    Ask,
    noul,
    choice,
    score,
    scoped,
    refine,
    questionNames,
    Request (..),
    Part (..),
    requestParts,
    Key,
    keyText,
    mkKey,
    answer,
    model,
    maxRequestBytes,
    maxOptions,
    Vocabulary (..),
    StepAnswers (..),
    PageAnswers (..),
    pageRequest,
    IngredientAnswers (..),
    ingredientRequest,
)
where

import Babel.Codec (IsId (..))
import Babel.Model.Common (Intensity, mkIntensity)
import Babel.Model.Id (FormId, MediumId, TasteAttrId, TechniqueId, TextureAttrId)
import Babel.Model.Registry (Term (..))
import Crypto.Hash.SHA256 qualified as SHA256
import Data.Aeson (Value (..), object, (.=))
import Data.Aeson qualified as Aeson
import Data.Aeson.Key qualified as Key
import Data.Aeson.KeyMap qualified as KM
import Data.ByteString.Builder (byteStringHex, toLazyByteString)
import Data.List (lookup)
import Data.List.NonEmpty qualified as NE
import Data.Map.Strict qualified as Map
import Data.Scientific (toRealFloat)
import Data.Text qualified as T
import Wikibooks.Dump (titleText)
import Wikibooks.Page (Line (..), RecipePage (..), Step (..))

-- * Questions

-- | An answer, and the probability the model gave it.
data Judged a = Judged {value :: a, probability :: Double}
    deriving stock (Eq, Show, Functor, Foldable, Traversable)

{- | Questions, and how to read their answers back. Names are relative to
the 'scoped' prefixes around them.
-}
newtype Ask a = Ask (Text -> ([Question], KM.KeyMap Value -> Either Text a))
    deriving stock (Functor)

-- | A question as sent, and how many options it offers the model.
data Question = Question {name :: Text, body :: Value, options :: Int}

instance Applicative Ask where
    pure a = Ask (const ([], const (Right a)))
    Ask f <*> Ask x = Ask $ \prefix ->
        let (qf, df) = f prefix
            (qx, dx) = x prefix
         in (qf <> qx, \answers -> df answers <*> dx answers)

-- | Prefix the names of every question inside.
scoped :: Text -> Ask a -> Ask a
scoped name (Ask f) = Ask (\prefix -> f (prefix <> name <> "__"))

-- | Reject answers that decode but make no sense.
refine :: (a -> Either Text b) -> Ask a -> Ask b
refine check (Ask f) = Ask (second (>=> check) . f)

questionNames :: Ask a -> [Text]
questionNames (Ask f) = map (.name) (fst (f ""))

question :: Text -> Int -> Value -> (KM.KeyMap Value -> Either Text a) -> Ask a
question name options body decode = Ask $ \prefix ->
    let full = prefix <> name
     in ( [Question full body options]
        , \answers -> case KM.lookup (Key.fromText full) answers of
            Just (Object a) -> first (\e -> "answer " <> full <> ": " <> e) (decode a)
            _ -> Left ("no answer to " <> full)
        )

-- | A yes/no question.
noul :: Text -> Text -> Text -> Text -> Ask (Judged Bool)
noul name instructions yes no =
    question name 2 (object ["type" .= ("noul" :: Text), "instructions" .= instructions, "criteria" .= object ["true" .= yes, "false" .= no]]) $ \a -> do
        p <- number "noul" a
        pure (Judged (p >= 0.5) (max p (1 - p)))

-- | Pick one option. Each option is a name, the value it stands for, and a description.
choice :: Text -> Text -> NonEmpty (Text, a, Text) -> Ask (Judged a)
choice name instructions options =
    question name (length options) (object ["type" .= ("choice" :: Text), "instructions" .= instructions, "criteria" .= object [Key.fromText k .= d | (k, _, d) <- toList options]]) $ \a -> do
        picked <- case KM.lookup "choice" a of
            Just (String k) -> Right k
            _ -> Left "no choice"
        v <- maybeToRight ("unknown option " <> picked) (lookup picked [(k, v) | (k, v, _) <- toList options])
        p <- probabilities a >>= maybeToRight "no probability for the choice" . Map.lookup picked
        pure (Judged v p)

{- | A level on an ordered scale, lowest first. The answer is the most
probable level.
-}
score :: Text -> Text -> NonEmpty Text -> Ask (Judged Int)
score name instructions levels =
    question name (length levels) (object ["type" .= ("score" :: Text), "instructions" .= instructions, "criteria" .= toList levels]) $ \a -> do
        ps <- probabilities a
        let byLevel = [(i, p) | i <- [0 .. length levels - 1], Just p <- [Map.lookup (show i) ps]]
        case sortOn (Down . snd) byLevel of
            (i, p) : _ -> Right (Judged i p)
            [] -> Left "no level probabilities"

number :: Text -> KM.KeyMap Value -> Either Text Double
number k a = case KM.lookup (Key.fromText k) a of
    Just (Number n) -> Right (toRealFloat n)
    _ -> Left ("no number " <> k)

probabilities :: KM.KeyMap Value -> Either Text (Map Text Double)
probabilities a = case KM.lookup "probabilities" a of
    Just (Object ps) -> Right (Map.fromList [(Key.toText k, toRealFloat n) | (k, Number n) <- KM.toList ps])
    _ -> Left "no probabilities"

-- * Requests

data Request a = Request {state :: Value, ask :: Ask a}

model :: Text
model = "baseten/mammoth-1:free"

{- | mammoth-1 reads at most 16k tokens. JSON runs at about 2.6 bytes a
token; this leaves room for the model's own prompt.
-}
maxRequestBytes :: Int64
maxRequestBytes = 36000

-- | The API takes at most this many options across a request's questions.
maxOptions :: Int
maxOptions = 512

-- | One request as sent to the API, and the name of its answer file.
data Part = Part {key :: Key, body :: LByteString}

{- | The request as sent: as few parts as keep each within 'maxOptions',
each with the whole state. Bodies are compact JSON with object keys in
sorted order, so equal requests are equal bytes. Parts split on option
counts alone, so rewording a question changes only the part it is in.
-}
requestParts :: Request a -> [Part]
requestParts r = map part (pack [] 0 questions)
  where
    Ask f = r.ask
    questions = fst (f "")
    pack acc _ [] = [reverse acc | not (null acc)]
    pack acc n (q : qs)
        | n + q.options > maxOptions && not (null acc) = reverse acc : pack [q] q.options qs
        | otherwise = pack (q : acc) (n + q.options) qs
    part qs =
        let body = Aeson.encode (object ["model" .= model, "state" .= r.state, "questions" .= object [Key.fromText q.name .= q.body | q <- qs]])
         in Part (Key (decodeUtf8 (toLazyByteString (byteStringHex (SHA256.hashlazy body))))) body

-- | The SHA-256 of a request body, in hex: the name of its answer file.
newtype Key = Key Text
    deriving newtype (Eq, Ord, Show)

keyText :: Key -> Text
keyText (Key t) = t

-- | A key from its hex text: 64 lower-case hex digits.
mkKey :: Text -> Maybe Key
mkKey t = Key t <$ guard (T.length t == 64 && T.all (`elem` ("0123456789abcdef" :: String)) t)

-- | Read the @answers@ objects the API returned for the parts of this request.
answer :: Request a -> [Value] -> Either Text a
answer r parts = do
    answers <- forM parts $ \case
        Object a -> Right a
        _ -> Left "answers are not an object"
    let Ask f = r.ask
    snd (f "") (foldl' KM.union KM.empty answers)

-- * The questions the extractor asks

-- | The registries mammoth chooses from, with each entry's description.
data Vocabulary = Vocabulary
    { techniques :: NonEmpty (TechniqueId, Text)
    , media :: [(MediumId, Text)]
    , forms :: NonEmpty (FormId, Text)
    , texture :: [Term TextureAttrId]
    , taste :: [Term TasteAttrId]
    }

registryOptions :: (IsId i) => NonEmpty (i, Text) -> NonEmpty (Text, i, Text)
registryOptions = fmap (\(i, d) -> (idText i, i, d))

data StepAnswers = StepAnswers
    { actsOnFood :: Judged Bool
    , technique :: Judged TechniqueId
    , medium :: Judged (Maybe MediumId)
    -- ^ 'Nothing': ambient conditions.
    , form :: Judged FormId
    -- ^ The form of the food when the step is done.
    }
    deriving stock (Eq, Show)

data PageAnswers = PageAnswers
    { singleDish :: Judged Bool
    , steps :: NonEmpty (Step, StepAnswers)
    , ingredients :: NonEmpty (Line, Judged (Maybe Int))
    -- ^ The number of the step that first uses each line, if any does.
    }
    deriving stock (Eq, Show)

{- | Every question about one recipe page, in one request: whether it is a
single dish, what each step does, and where each ingredient goes in.
-}
pageRequest :: Vocabulary -> RecipePage -> Request PageAnswers
pageRequest vocab page =
    Request
        { state =
            object
                [ "recipe" .= titleText page.title
                , "ingredients" .= object [key i .= l.text | (i, l) <- toList numberedLines]
                , "steps" .= object [key s.number .= s.text | s <- toList page.steps]
                ]
        , ask =
            PageAnswers
                <$> noul
                    "single_dish"
                    "Is this page a recipe for one specific dish, with one set of ingredients and steps?"
                    "a recipe for one dish"
                    "an overview, a collection of variations, a technique, or several recipes on one page"
                <*> traverse (\s -> (s,) <$> scoped "step" (step s.number)) page.steps
                <*> traverse (\(i, l) -> (l,) <$> scoped "ingredient" (firstStep i l.text)) numberedLines
        }
  where
    numberedLines = NE.zip (1 :| [2 :: Int ..]) page.ingredients
    key = Key.fromText . show
    step n =
        scoped (show n) $
            StepAnswers
                <$> noul
                    "acts_on_food"
                    ("Does step " <> show n <> " do something to food, such as combine, cut, heat, cool or rest it?")
                    "it works on food"
                    "it only prepares equipment, such as preheating an oven or greasing a pan, or only serves"
                <*> choice "technique" ("Which technique best describes what step " <> show n <> " does to the food?") (registryOptions vocab.techniques)
                <*> choice
                    "medium"
                    ("What carries heat to the food during step " <> show n <> "?")
                    (("ambient", Nothing, "nothing: the food is at room temperature or in the fridge") :| [(idText m, Just m, d) | (m, d) <- vocab.media])
                <*> choice "form" ("What form is the food in at the end of step " <> show n <> "?") (registryOptions vocab.forms)
    firstStep i l =
        scoped (show i) $
            choice
                "first_step"
                ("Ingredient " <> show i <> " is \"" <> l <> "\". Which step first uses it?")
                (("unused", Nothing, "no step uses it") :| [("step_" <> show s.number, Just s.number, "step " <> show s.number) | s <- toList page.steps])

data IngredientAnswers = IngredientAnswers
    { form :: Judged FormId
    , texture :: Map TextureAttrId (Judged Intensity)
    , taste :: Map TasteAttrId (Judged Intensity)
    }
    deriving stock (Eq, Show)

-- | The form, texture and taste of a base ingredient, as usually bought.
ingredientRequest :: Vocabulary -> Text -> Text -> Request IngredientAnswers
ingredientRequest vocab label description =
    Request
        { state = object ["ingredient" .= label, "description" .= T.take 1500 description]
        , ask =
            IngredientAnswers
                <$> choice "form" "What form is this ingredient in, as usually bought?" (registryOptions vocab.forms)
                <*> scoped "texture" (attributes (\t -> "How much " <> t <> " does this ingredient have, as usually bought?") vocab.texture)
                <*> scoped "taste" (attributes (\t -> "How " <> t <> " does this ingredient taste, as usually bought?") vocab.taste)
        }
  where
    attributes phrase terms = Map.fromList <$> traverse (attribute phrase) terms
    attribute phrase term =
        fmap (term.id,) . refine (traverse (first toText . mkIntensity)) $
            score (idText term.id) (phrase term.label <> maybe "" (\d -> " (" <> term.label <> ": " <> d <> ")") term.description) levels
    levels = "none" :| ["slight", "clear", "dominant"]
