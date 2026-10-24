{- | A Cookbook article read as a recipe: its ingredient lines and its
numbered procedure steps.
-}
module Wikibooks.Page (
    RecipePage (..),
    Line (..),
    Step (..),
    recipePage,
)
where

import Babel.Slug (slugify)
import Data.Text qualified as T
import Wikibooks.Dump (Title, titleText)
import Wikibooks.Quantity (IngredientLine, Lexicon (..), ingredientLine)
import Wikibooks.Wikitext (Inline (..), bullets, inlines, intro, numbered, plain, sectionBody)

data RecipePage = RecipePage
    { title :: Title
    , revid :: Int
    , slug :: Text
    , intro :: Text
    , ingredients :: NonEmpty Line
    , steps :: NonEmpty Step
    }
    deriving stock (Eq, Show)

-- | An ingredient line as written, and what it was read as.
data Line = Line {text :: Text, parsed :: IngredientLine}
    deriving stock (Eq, Show)

-- | A procedure step, numbered from 1 across the whole procedure.
data Step = Step {number :: Int, text :: Text}
    deriving stock (Eq, Show)

-- | Read an article as a recipe, or say why it is not one.
recipePage :: Lexicon -> Title -> Int -> Text -> Either Text RecipePage
recipePage lexicon title revid wikitext = do
    slug <- maybeToRight "the title has no letters or digits to make an ID from" (slugify (titleText title))
    ingredientLines <- maybeToRight "no Ingredients section" (sectionBody (startsWith "ingredient") wikitext)
    procedure <- maybeToRight "no Procedure section" (sectionBody (startsWith "procedure") wikitext)
    ingredients <-
        maybeToRight "the Ingredients section has no ingredient lines" . nonEmpty
            =<< traverse line [l | l <- bullets ingredientLines, not (isHeading l)]
    steps <-
        maybeToRight "the Procedure section has no numbered steps" . nonEmpty $
            zipWith Step [1 ..] (filter (not . T.null) (map (plain . inlines) (numbered procedure)))
    pure RecipePage{title, revid, slug, intro = intro wikitext, ingredients, steps}
  where
    startsWith prefix heading = prefix `T.isPrefixOf` T.toLower heading
    -- "For the sauce:" and bold-only lines name a group of ingredients;
    -- some pages list their equipment with the ingredients.
    isHeading l =
        let items = inlines l
            shown = plain items
            links = [t | Link t _ <- items]
         in T.null shown
                || ":" `T.isSuffixOf` shown
                || ("'''" `T.isPrefixOf` l && null links)
                || (any lexicon.isTool links && not (any lexicon.isFood links))
    line l = case ingredientLine lexicon (inlines l) of
        Just parsed -> Right (Line (plain (inlines l)) parsed)
        Nothing -> Left ("no food link in the ingredient line " <> show (plain (inlines l)))
