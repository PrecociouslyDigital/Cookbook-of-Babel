{- | The whole extraction as a pure function of the dump, the dataset and
the decisions answered so far. A page is emitted once every decision it
depends on is answered; the requests still unanswered are returned so
they can be sent.
-}
module Wikibooks.Extract (
    Status (..),
    Extraction (..),
    extract,
)
where

import Babel.Codec (IsId (..))
import Babel.Load (Located (..), World (..))
import Babel.Model.Id (FormId, IngredientId)
import Babel.Model.Ingredient (Ingredient (..))
import Babel.Model.Recipe (Recipe)
import Babel.Model.Source (Source (..))
import Babel.Model.Technique (Technique (..))
import Babel.Preparation (Preparation (..))
import Data.List.NonEmpty qualified as NE
import Data.Map.Strict qualified as Map
import Data.Set qualified as Set
import Data.Text qualified as T
import Decisions.Decide (Decided (..), Decisions, andThen, decide)
import Decisions.Request (Judged (..), Key, Part (..), requestParts)
import Wikibooks.Dump (Title, article, articles)
import Wikibooks.Emit (Dish (..), Env (..), PreparedStep (..), baseIngredient, baseLabel, dish, ingredientFor, lexicon, rejection)
import Wikibooks.Page (Line (..), RecipePage (..), recipePage)
import Wikibooks.Quantity (IngredientLine (..))
import Wikibooks.Questions (foodGroups, foodRequest, ingredientRequest, pageRequest, prepRequest)
import Wikibooks.Wikitext (intro)

data Status = Emitted | Skipped Text | Pending
    deriving stock (Eq, Show)

data Extraction = Extraction
    { sources :: [Source]
    , ingredients :: [Ingredient]
    , recipes :: [Recipe]
    , pending :: Map Key LByteString
    -- ^ The body of every request still to send.
    , referenced :: Set Key
    -- ^ Every request this extraction made, answered or not.
    , pages :: [(Title, Status)]
    -- ^ Every article in the Cookbook namespace.
    , unknownUnits :: Map Text Int
    -- ^ Words read as pieces, with how many ingredient lines use them.
    }

-- | A page that reads as a recipe, before its ingredients are looked at.
data Candidate = Waiting | Rejected Text | Ready Dish

extract :: Env -> Decisions -> Extraction
extract env decisions =
    Extraction
        { sources = [d.source | d <- emitted]
        , ingredients = concatMap (.made) emitted <> newPrepared <> [ing | (i, Answered ing) <- Map.toList bases, i `Set.member` emittedUses]
        , recipes = concatMap (toList . (.recipes)) emitted
        , pending = Map.fromList [(p.key, p.body) | Unasked ps <- map (void . pageDecision . snd) recipePages <> map void (Map.elems bases), p <- ps]
        , referenced = Set.fromList (map (.key) (concatMap (requestParts . pageRequest env.vocabulary . snd) recipePages <> concat [requestParts r | (_, p) <- recipePages, Answered r <- [prepDecision p]] <> concatMap (requestParts . baseRequest) (Map.elems baseTitles) <> concatMap (requestParts . foodRequest) nameGroups))
        , pages = [(t, status t) | (t, _, _) <- articles env.cookbook]
        , unknownUnits = Map.fromListWith (+) [(T.toLower w, 1) | (_, p) <- recipePages, l <- toList p.ingredients, Just w <- [l.parsed.unknownUnit]]
        }
  where
    parsed = Map.fromList [(t, recipePage (lexicon env) t r w) | (t, r, w) <- articles env.cookbook]
    recipePages = [(t, p) | (t, Right p) <- Map.toList parsed]

    -- The extractor asks how each line is prepared only for a page that could make a dish.
    prepDecision p = decide decisions (pageRequest env.vocabulary p) `andThen` \a -> maybe (Answered (prepRequest env.vocabulary p a)) Unusable (rejection env p a)
    pageDecision p = prepDecision p `andThen` decide decisions

    candidates :: Map Title Candidate
    candidates = Map.fromList (claim preparedIds judged)
    judged = [(t, judge p) | (t, p) <- recipePages]
    judge p = case pageDecision p of
        Unasked{} -> Waiting
        Unusable e -> Rejected e
        Answered a -> either Rejected Ready (dish env p a)

    {- In title order, a page may only make what no earlier page makes.
    The items for preparations are everyone's, so no page may make one as
    its own. -}
    preparedIds = Set.fromList [idText s.makes | (_, Ready d) <- judged, s <- d.prepared]
    claim _ [] = []
    claim taken ((t, Ready d) : rest) = case filter (`Set.member` taken) (claims d) of
        clash : _ -> (t, Rejected (clashing clash)) : claim taken rest
        [] -> (t, Ready d) : claim (taken <> Set.fromList (claims d)) rest
    claim taken (c : rest) = c : claim taken rest
    claims d = idText d.source.id : map (\i -> idText i.id) d.made
    clashing i
        | i `Set.member` preparedIds = i <> " is the item for a preparation other pages make"
        | otherwise = "an earlier page already makes " <> i

    ready = [(t, d) | (t, Ready d) <- Map.toList candidates]
    makers = Map.fromList [(i.id, t) | (t, d) <- ready, i <- d.made]
    -- What a waiting page would make; pages that use it wait for it.
    awaited = Set.fromList [i | (t, Waiting) <- Map.toList candidates, Just i <- [ingredientFor env t]]

    -- The item for each preparation emitted pages make, built once from every step that makes it.
    prepared :: Map IngredientId Ingredient
    prepared = Map.mapWithKey preparedItem (Map.fromListWith (<>) [(s.makes, one s) | d <- emitted, s <- d.prepared])
    newPrepared = [i | i <- Map.elems prepared, not (Map.member i.id env.world.ingredients)]
    preparedItem :: IngredientId -> NonEmpty PreparedStep -> Ingredient
    preparedItem i steps =
        let p = (head steps).preparation
         in Ingredient
                { id = i
                , label = labelOf p.ingredient <> ", " <> maybe (idText p.technique) (.value.label) (Map.lookup p.technique env.world.techniques)
                , aliases = sortNub (mapMaybe (.dishName) (toList steps))
                , parents = []
                , form = mostCommon (fmap (.form.value) steps)
                , texture = mempty
                , taste = mempty
                , servingTags = []
                , densityGPerMl = Nothing
                , waterActivity = Nothing
                , named = False
                , issues = []
                }
    labelOf :: IngredientId -> Text
    labelOf i =
        fromMaybe (idText i) . asum $
            [ (.value.label) <$> Map.lookup i env.world.ingredients
            , case Map.lookup i bases of
                Just (Answered b) -> Just b.label
                _ -> Nothing
            , (.label) <$> Map.lookup i prepared
            ]

    -- Ingredients no page makes, each with the page its links lead to.
    baseTitles :: Map IngredientId Title
    baseTitles =
        Map.fromListWith
            preferArticle
            [(i, t) | (_, d) <- ready, (i, t) <- Map.toList d.uses, not (Map.member i makers), not (Set.member i awaited)]
    bases = Map.mapWithKey (\i t -> baseIngredient i t <$> (isFood t `andThen` \food -> if food then decide decisions (baseRequest t) else Unusable ("mammoth judged that \"" <> baseLabel t <> "\" is not a food"))) baseTitles

    -- A name with no page of its own comes from a recipe's words, which may name no food at all.
    nameGroups = foodGroups [baseLabel t | t <- Map.elems baseTitles, isNothing (article env.cookbook t)]
    foodVerdicts = Map.fromList [(n, decide decisions (foodRequest g) `andThen` verdict n) | g <- nameGroups, n <- g]
    verdict n = maybe (Unusable ("no verdict on " <> n)) (Answered . (.value)) . Map.lookup n
    isFood t = fromMaybe (Answered True) (Map.lookup (baseLabel t) foodVerdicts)
    baseRequest t =
        let label = baseLabel t
            description = maybe "" (\(_, _, w) -> intro w) (article env.cookbook t)
         in ingredientRequest env.vocabulary label (if T.null description then label else description)
    preferArticle a b = case (isJust (article env.cookbook a), isJust (article env.cookbook b)) of
        (True, False) -> a
        (False, True) -> b
        _ -> min a b

    -- What keeps a dish's own ingredients from being ready: Left for good, Right for now.
    blocker :: Dish -> Maybe (Either Text Text)
    blocker d =
        asum
            [ case Map.lookup i bases of
                Just (Unusable e) -> Just (Left ("ingredient " <> idText i <> ": " <> e))
                Just Unasked{} -> Just (Right "waiting on an ingredient decision")
                _ | Set.member i awaited -> Just (Right "waiting on the page that makes an ingredient")
                _ -> Nothing
            | i <- Map.keys d.uses
            ]
    dependencies d = mapMaybe (`Map.lookup` makers) (Map.keys d.uses)

    -- A dish that uses another page's dish is emitted only along with it.
    emittable = fixpoint (\ok -> Set.fromList [t | (t, d) <- ready, t `Set.member` ok, all (`Set.member` ok) (dependencies d)]) (Set.fromList [t | (t, d) <- ready, isNothing (blocker d)])
    -- Dishes that may still be emitted once more decisions come in.
    waiting = fixpoint (\w -> w <> Set.fromList [t | (t, d) <- ready, any (`Set.member` w) (dependencies d)]) (Set.fromList [t | (t, d) <- ready, maybe False isRight (blocker d)])
    emitted = [d | (t, d) <- ready, t `Set.member` emittable]
    emittedUses = Set.fromList (concatMap (Map.keys . (.uses)) emitted)

    status t = case (Map.lookup t parsed, Map.lookup t candidates) of
        (Just (Left reason), _) -> Skipped reason
        (_, Just (Rejected reason)) -> Skipped reason
        (_, Just (Ready d))
            | t `Set.member` emittable -> Emitted
            | t `Set.member` waiting -> Pending
            | Just (Left reason) <- blocker d -> Skipped reason
            | otherwise -> Skipped "uses a dish from another page that cannot be emitted"
        _ -> Pending

-- | The form most steps gave, the lowest on a tie.
mostCommon :: NonEmpty FormId -> FormId
mostCommon forms = head (NE.sortWith (\f -> (Down (length (NE.filter (== f) forms)), f)) forms)

fixpoint :: (Eq a) => (a -> a) -> a -> a
fixpoint f x = let x' = f x in if x' == x then x else fixpoint f x'
