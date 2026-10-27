{- | Which FoodKeeper product an ingredient is. A committed override decides
first. Otherwise Jev picks a category, from the products each one lists,
and then a product in it. Everything it needs is in the request, so Jev,
not a model with knowledge of the world, can answer.

An unsure answer, or none, takes the match of the nearest is-a ancestor:
a russet potato keeps like a potato.
-}
module FoodKeeper.Match (
    Described (..),
    describedState,
    categoryRequest,
    productRequest,
    minConfidence,
    Own (..),
    ownMatch,
    Resolution (..),
    resolve,
)
where

import Babel.Codec (IsId (..))
import Babel.Model.Id (FormId, IngredientId)
import Babel.Slug (slugify)
import Data.Aeson (Value, object, (.=))
import Data.Map.Strict qualified as Map
import Data.Set qualified as Set
import Data.Text qualified as T
import Decisions.Decide (Decided (..), Decisions, andThen, decide)
import Decisions.Request (Judged (..), Model (..), Part, Request (..), choice, requestParts)
import FoodKeeper.Source (Category (..), CategoryId, FoodKeeper (..), FoodProduct (..), ProductId, categoryLabel, productLabel, productsIn)
import FoodKeeper.Tables (Override (..))

-- | What Jev is told about an ingredient.
data Described = Described
    { id :: IngredientId
    , label :: Text
    , aliases :: [Text]
    , form :: FormId
    , parents :: [(IngredientId, Text)]
    -- ^ Its is-a parents, nearest first, with their labels.
    , openFoodFacts :: Maybe Text
    -- ^ The OpenFoodFacts category it matched.
    }
    deriving stock (Eq, Show)

describedState :: Described -> Value
describedState d =
    object $
        ["ingredient" .= d.label, "form" .= idText d.form]
            <> ["aliases" .= d.aliases | not (null d.aliases)]
            <> ["is_a" .= map snd d.parents | not (null d.parents)]
            <> ["openfoodfacts_category" .= c | Just c <- [d.openFoodFacts]]

categoryRequest :: FoodKeeper -> Described -> Request (Judged (Maybe CategoryId))
categoryRequest fk d =
    Request
        { model = Jev
        , state = describedState d
        , ask =
            choice
                "category"
                "Which FoodKeeper category lists this ingredient as it is usually bought? Each option lists the products in it."
                (options "none of these: not a food or drink, or no category lists anything like it" [(categoryLabel c, c.id, describe c) | c <- Map.elems fk.categories])
        }
  where
    describe c = categoryLabel c <> ": " <> T.intercalate "; " (ordNub (map (.name) (productsIn fk c.id)))

productRequest :: FoodKeeper -> CategoryId -> Described -> Request (Judged (Maybe ProductId))
productRequest fk c d =
    Request
        { model = Jev
        , state = describedState d
        , ask =
            choice
                "product"
                ("Which product in FoodKeeper's " <> foldMap categoryLabel (Map.lookup c fk.categories) <> " category is this ingredient, as it is usually bought?")
                (options "no close match" [(productLabel p, p.id, describe p) | p <- productsIn fk c])
        }
  where
    describe p = productLabel p <> (if null p.keywords then "" else "; keywords: " <> T.intercalate ", " p.keywords)

{- | @none@, then an option per entry keyed by the slug of its label. A
label whose slug is taken gets its position appended, so keys are unique.
-}
options :: Text -> [(Text, a, Text)] -> NonEmpty (Text, Maybe a, Text)
options none entries = ("none", Nothing, none) :| go (Set.singleton "none") (zip [1 :: Int ..] entries)
  where
    go _ [] = []
    go taken ((n, (label, a, description)) : rest) =
        let slug = fromMaybe "option" (slugify label)
            key = if Set.member slug taken then slug <> "_" <> show n else slug
         in (key, Just a, description) : go (Set.insert key taken) rest

-- | Answers less sure than this fall back to an ancestor's match.
minConfidence :: Double
minConfidence = 0.6

-- | What decides an ingredient's product on its own.
data Own
    = Chosen ProductId Text
    | -- | @matches.json@ says no product fits, so none is inherited either.
      Excluded
    | -- | No sure answer: an ancestor's match may stand in.
      Unsure Text
    deriving stock (Eq, Show)

{- | The decision for one ingredient, and the request parts it took: the
category request, then the product request once a category is sure.
-}
ownMatch :: FoodKeeper -> Decisions -> Maybe Override -> Described -> (Decided Own, [Part])
ownMatch fk ds override d = case override of
    Just (MatchTo p) -> (Answered (Chosen p "by matches.json"), [])
    Just NoProduct -> (Answered Excluded, [])
    Nothing -> (decided, requestParts byCategory <> foldMap requestParts byProduct)
  where
    byCategory = categoryRequest fk d
    category = decide ds byCategory
    byProduct = case category of
        Answered c | Right cid <- sure "category" c -> Just (productRequest fk cid d)
        _ -> Nothing
    decided =
        category `andThen` \c -> case sure "category" c of
            Left why -> Answered (Unsure why)
            Right cid ->
                decide ds (productRequest fk cid d) <&> \p -> case sure "product" p of
                    Left why -> Unsure why
                    Right pid -> Chosen pid ("judged by Jev, " <> percent c.probability <> " sure of the category and " <> percent p.probability <> " of the product")

sure :: Text -> Judged (Maybe a) -> Either Text a
sure what j = case j.value of
    Nothing -> Left ("Jev chose no " <> what <> " (" <> percent j.probability <> " sure)")
    Just a
        | j.probability < minConfidence -> Left ("Jev was only " <> percent j.probability <> " sure of the " <> what)
        | otherwise -> Right a

percent :: Double -> Text
percent p = show (round (p * 100) :: Int) <> "%"

data Resolution
    = Resolved ProductId Text
    | Unresolved Text
    | -- | A decision it depends on is not answered yet.
      Waiting
    deriving stock (Eq, Show)

{- | An ingredient's own match, or, when it is unsure, that of its nearest
ancestor with one, breadth first. Ancestors with no decision of their own
are passed through.
-}
resolve :: (IngredientId -> [IngredientId]) -> Map IngredientId (Decided Own) -> IngredientId -> Resolution
resolve parentsOf owns i = case Map.lookup i owns of
    Nothing -> Unresolved "not asked"
    Just (Unasked _) -> Waiting
    Just (Answered (Chosen p how)) -> Resolved p how
    Just (Answered Excluded) -> Unresolved "matches.json says no product fits"
    Just (Answered (Unsure why)) -> inherit why
    Just (Unusable why) -> inherit why
  where
    inherit why = go (Set.singleton i) (parentsOf i)
      where
        go _ [] = Unresolved why
        go seen (a : queue)
            | Set.member a seen = go seen queue
            | otherwise = case Map.lookup a owns of
                Just (Unasked _) -> Waiting
                Just (Answered (Chosen p how)) -> Resolved p ("inherited from " <> idText a <> ", " <> how <> "; its own: " <> why)
                _ -> go (Set.insert a seen) (queue <> parentsOf a)
