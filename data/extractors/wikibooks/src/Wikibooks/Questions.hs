{- | The questions the extractor asks the decision models: about a recipe
page, about a base ingredient, and whether a recipe's words name a food.
-}
module Wikibooks.Questions (
    Vocabulary (..),
    StepAnswers (..),
    PageAnswers (..),
    pageRequest,
    IngredientAnswers (..),
    ingredientRequest,
    foodGroups,
    foodRequest,
)
where

import Babel.Codec (IsId (..))
import Babel.Model.Common (Intensity, mkIntensity)
import Babel.Model.Id (FormId, MediumId, TasteAttrId, TechniqueId, TextureAttrId)
import Babel.Model.Registry (Term (..))
import Crypto.Hash.SHA256 qualified as SHA256
import Data.Aeson (object, (.=))
import Data.Aeson.Key qualified as Key
import Data.ByteString qualified as BS
import Data.List.NonEmpty qualified as NE
import Data.Map.Strict qualified as Map
import Data.Text qualified as T
import Decisions.Request (Judged (..), Model (..), Request (..), choice, noul, refine, scoped, score)
import Wikibooks.Dump (titleText)
import Wikibooks.Page (Line (..), RecipePage (..), Step (..))

-- | The registries the models choose from, with each entry's description.
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
        { model = Jev
        , state =
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
        { model = Mammoth
        , state = object ["ingredient" .= label, "description" .= T.take 1500 description]
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

{- | Names to ask about together, each in a group its hash picks, so a new
name changes only its own group's request.
-}
foodGroups :: [Text] -> [[Text]]
foodGroups names = Map.elems (Map.fromListWith (<>) [(groupOf n, [n]) | n <- sortNub names])
  where
    groupOf n = BS.head (SHA256.hash (encodeUtf8 n)) `mod` 16

-- | Whether each name, which a recipe lists as an ingredient, names a food.
foodRequest :: [Text] -> Request (Map Text (Judged Bool))
foodRequest names =
    Request
        { model = Mammoth
        , state = object ["ingredients" .= names]
        , ask = Map.fromList <$> traverse isFood (zip [1 :: Int ..] names)
        }
  where
    isFood (i, n) =
        (n,)
            <$> scoped
                (show i)
                ( noul
                    "is_food"
                    ("A recipe lists \"" <> n <> "\" as an ingredient. Does it name a food or drink?")
                    "a food, drink or cooking ingredient"
                    "not a food: a temperature, a measurement, a container, a placeholder, or a fragment of a sentence"
                )
