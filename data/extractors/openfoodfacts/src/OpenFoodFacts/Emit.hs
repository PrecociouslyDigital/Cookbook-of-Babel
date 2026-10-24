{- | Turn matched ingredients and their categories' tallies into the
extracted OpenFoodFacts layer: one source, and the measures it states.
-}
module OpenFoodFacts.Emit (
    Matched (..),
    matchAll,
    wanted,
    Extraction (..),
    Row (..),
    Outcome (..),
    outcomeName,
    emit,
)
where

import Babel.Codec (IsId (..))
import Babel.Model.Common (Issue (..), IssueKind (..))
import Babel.Model.Id (IngredientId, SourceId (..), UnitId (..))
import Babel.Model.Ingredient (Ingredient (..))
import Babel.Model.Measure (Measure (..), MeasureValue (..))
import Babel.Model.Source (Origin (..), Source (..), originLicence)
import Babel.Slug (slugify)
import Data.Map.Strict qualified as Map
import Data.Set qualified as Set
import Data.Text qualified as T
import OpenFoodFacts.Aggregate (Scope (..), Summary (..), Tallies, confidentProducts, summarise)
import OpenFoodFacts.Match (Index, Match (..), Override, match)
import OpenFoodFacts.Taxonomy (Tag, tagText)

-- | An ingredient and the category it matched, if any.
data Matched = Matched {ingredient :: Ingredient, match :: Match}
    deriving stock (Show)

matchAll :: Index -> Map IngredientId Override -> [Ingredient] -> [Matched]
matchAll index overrides is = [Matched i (match index (Map.lookup i.id overrides) i) | i <- is]

-- | The categories whose products need tallying.
wanted :: [Matched] -> Set Tag
wanted ms = Set.fromList (mapMaybe (categoryOf . (.match)) ms)

categoryOf :: Match -> Maybe Tag
categoryOf = \case
    Overridden t -> Just t
    Slug _ t -> Just t
    _ -> Nothing

data Extraction = Extraction
    { source :: Source
    , measures :: [Measure]
    , report :: [Row]
    -- ^ What became of each ingredient.
    }

data Row = Row {ingredient :: IngredientId, outcome :: Outcome, categories :: [Tag], detail :: Text}
    deriving stock (Eq, Show)

data Outcome = Emitted | TooFewProducts | NoProducts | ExcludedByOverride | AmbiguousName | NoMatch
    deriving stock (Eq, Show)

outcomeName :: Outcome -> Text
outcomeName = \case
    Emitted -> "emitted"
    TooFewProducts -> "too few products"
    NoProducts -> "no products"
    ExcludedByOverride -> "excluded"
    AmbiguousName -> "ambiguous"
    NoMatch -> "unmatched"

sourceId :: SourceId
sourceId = SourceId "openfoodfacts"

-- | @url@ is the pinned dataset the values come from.
emit :: Text -> Tallies -> [Matched] -> Extraction
emit url tallies ms =
    Extraction
        { source = Source{id = sourceId, name = "Open Food Facts", url = Just url, licence = originLicence OpenFoodFacts}
        , measures = concat [ms' | (_, ms') <- results]
        , report = map fst results
        }
  where
    results = map outcomeOf ms
    outcomeOf m = case m.match of
        Overridden t -> found m t "by matches.json"
        Slug name t -> found m t ("by the name " <> name)
        Excluded -> (Row m.ingredient.id ExcludedByOverride [] "matches.json says no category fits", [])
        Ambiguous a b rest -> (Row m.ingredient.id AmbiguousName (a : b : rest) "", [])
        Unmatched -> (Row m.ingredient.id NoMatch [] "", [])
    found m t how = case Map.lookup t tallies of
        Nothing -> (Row m.ingredient.id NoProducts [t] how, [])
        Just b ->
            let s = summarise (nouns m.ingredient) t b
                measures = measuresOf m.ingredient.id s
             in (Row m.ingredient.id (if null measures then TooFewProducts else Emitted) [t] (how <> "; " <> describe s), measures)

measuresOf :: IngredientId -> Summary -> [Measure]
measuresOf i s =
    [Measure i sourceId (Nutrition n) [issue s.products ("median of " <> products s.products <> scope)] | Just n <- [s.per100g]]
        <> [ Measure i sourceId (GramsPer (UnitId "piece") g) [issue k ("median of " <> show k <> " one-piece servings of products" <> scope)]
           | Just (g, k) <- [s.piece]
           ]
  where
    scope = case s.scope of
        Direct -> " filed directly under " <> tagText s.tag
        Subtree -> " in " <> tagText s.tag <> " and the categories below it"
    issue count = Issue "value" (if count < confidentProducts then Uncertain else Estimate)

products :: Int -> Text
products 1 = "1 product"
products n = show n <> " products"

describe :: Summary -> Text
describe s =
    products s.products
        <> (if s.scope == Subtree then " in its subtree" else " filed directly")
        <> maybe "" (\(_, k) -> ", " <> show k <> " one-piece servings") s.piece

-- | The words a label may name one of this food by: @egg@, @eggs@.
nouns :: Ingredient -> [Text]
nouns i = ordNub (concat [[w, w <> "s", w <> "es"] | Just s <- map slugify [idText i.id, i.label], Just w <- [viaNonEmpty last (T.splitOn "_" s)]])
