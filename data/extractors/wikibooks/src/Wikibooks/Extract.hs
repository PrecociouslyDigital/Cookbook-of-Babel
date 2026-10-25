{- | The whole extraction as a pure function of the dump, the dataset and
the decisions answered so far. A page is emitted once every decision it
depends on is answered; the requests still unanswered are returned so
they can be sent.
-}
module Wikibooks.Extract (
    Decisions,
    Status (..),
    Extraction (..),
    extract,
)
where

import Babel.Codec (IsId (..))
import Babel.Model.Id (IngredientId)
import Babel.Model.Ingredient (Ingredient (..))
import Babel.Model.Recipe (Recipe)
import Babel.Model.Source (Source (..))
import Data.Aeson (Value)
import Data.ByteString.Lazy qualified as BL
import Data.Map.Strict qualified as Map
import Data.Set qualified as Set
import Data.Text qualified as T
import Wikibooks.Dump (Title, article, articles)
import Wikibooks.Emit (Dish (..), Env (..), baseIngredient, baseLabel, dish, ingredientFor, lexicon)
import Wikibooks.Page (Line (..), RecipePage (..), recipePage)
import Wikibooks.Quantity (IngredientLine (..))
import Wikibooks.Questions (Key, Part (..), Request (..), answer, ingredientRequest, maxRequestBytes, pageRequest, requestParts)
import Wikibooks.Wikitext (intro)

-- | The @answers@ object of every decision made so far, by request key.
type Decisions = Map Key Value

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

-- | A decision this extraction needs: answered, with parts still to ask, or unusable.
data Decided a = Answered a | Unasked [Part] | Unusable Text
    deriving stock (Functor)

decide :: Decisions -> Request a -> Decided a
decide decisions r
    | Just big <- find ((> maxRequestBytes r.model) . BL.length . (.body)) parts =
        Unusable ("the decision request is too large (" <> show (BL.length big.body) <> " bytes)")
    | otherwise = case traverse (\p -> Map.lookup p.key decisions) parts of
        Nothing -> Unasked [p | p <- parts, not (Map.member p.key decisions)]
        Just answers -> either (\e -> Unusable ("decision: " <> e)) Answered (answer r answers)
  where
    parts = requestParts r

-- | A page that reads as a recipe, before its ingredients are looked at.
data Candidate = Waiting | Rejected Text | Ready Dish

extract :: Env -> Decisions -> Extraction
extract env decisions =
    Extraction
        { sources = [d.source | d <- emitted]
        , ingredients = concatMap (.made) emitted <> [ing | (i, Answered ing) <- Map.toList bases, i `Set.member` emittedUses]
        , recipes = concatMap (toList . (.recipes)) emitted
        , pending = Map.fromList [(p.key, p.body) | Unasked ps <- map (void . pageDecision . snd) recipePages <> map void (Map.elems bases), p <- ps]
        , referenced = Set.fromList (map (.key) (concatMap (requestParts . pageRequest env.vocabulary . snd) recipePages <> concatMap (requestParts . baseRequest) (Map.elems baseTitles)))
        , pages = [(t, status t) | (t, _, _) <- articles env.cookbook]
        , unknownUnits = Map.fromListWith (+) [(T.toLower w, 1) | (_, p) <- recipePages, l <- toList p.ingredients, Just w <- [l.parsed.unknownUnit]]
        }
  where
    parsed = Map.fromList [(t, recipePage (lexicon env) t r w) | (t, r, w) <- articles env.cookbook]
    recipePages = [(t, p) | (t, Right p) <- Map.toList parsed]

    pageDecision = decide decisions . pageRequest env.vocabulary

    candidates :: Map Title Candidate
    candidates = Map.fromList (claim Set.empty [(t, judge p) | (t, p) <- recipePages])
    judge p = case pageDecision p of
        Unasked{} -> Waiting
        Unusable e -> Rejected e
        Answered a -> either Rejected Ready (dish env p a)

    -- In title order, a page may only make what no earlier page makes.
    claim _ [] = []
    claim taken ((t, Ready d) : rest) = case filter (`Set.member` taken) (claims d) of
        clash : _ -> (t, Rejected ("an earlier page already makes " <> clash)) : claim taken rest
        [] -> (t, Ready d) : claim (taken <> Set.fromList (claims d)) rest
    claim taken (c : rest) = c : claim taken rest
    claims d = idText d.source.id : map (\i -> idText i.id) d.made

    ready = [(t, d) | (t, Ready d) <- Map.toList candidates]
    makers = Map.fromList [(i.id, t) | (t, d) <- ready, i <- d.made]
    -- What a waiting page would make; pages that use it wait for it.
    awaited = Set.fromList [i | (t, Waiting) <- Map.toList candidates, Just i <- [ingredientFor env t]]

    -- Ingredients no page makes, each with the page its links lead to.
    baseTitles :: Map IngredientId Title
    baseTitles =
        Map.fromListWith
            preferArticle
            [(i, t) | (_, d) <- ready, (i, t) <- Map.toList d.uses, not (Map.member i makers), not (Set.member i awaited)]
    bases = Map.mapWithKey (\i t -> baseIngredient i t <$> decide decisions (baseRequest t)) baseTitles
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

fixpoint :: (Eq a) => (a -> a) -> a -> a
fixpoint f x = let x' = f x in if x' == x then x else fixpoint f x'
