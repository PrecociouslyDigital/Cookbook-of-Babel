{- | Which OpenFoodFacts category an ingredient is. A committed override
decides first; otherwise the ingredient's names must name exactly one
category in English.
-}
module OpenFoodFacts.Match (
    Override (..),
    Match (..),
    Index,
    englishIndex,
    match,
)
where

import Babel.Codec (IsId (..))
import Babel.Model.Ingredient (Ingredient (..))
import Babel.Slug (slugify)
import Data.Map.Strict qualified as Map
import Data.Set qualified as Set
import Data.Text qualified as T
import OpenFoodFacts.Taxonomy (Category (..), Tag, Taxonomy (..))

-- | An entry of @matches.json@: a category, or @null@ for none.
data Override = MatchTo Tag | NoCategory
    deriving stock (Eq, Show)

data Match
    = Overridden Tag
    | -- | The ingredient's slug that named the category.
      Slug Text Tag
    | -- | @matches.json@ says no category fits.
      Excluded
    | Unmatched
    | Ambiguous Tag Tag [Tag]
    deriving stock (Eq, Show)

-- | Each slug of an English category name, and the categories it names.
type Index = Map Text (Set Tag)

englishIndex :: Taxonomy -> Index
englishIndex t =
    Map.fromListWith (<>) [(s, Set.singleton c.tag) | c <- Map.elems t.categories, Just s <- map slugify c.english]

{- | Exact slugs are tried before their plural and singular variants, so
@egg@ names @Egg@ even when another category is called @Eggs@.
-}
match :: Index -> Maybe Override -> Ingredient -> Match
match index override i = case override of
    Just (MatchTo t) -> Overridden t
    Just NoCategory -> Excluded
    Nothing -> fromMaybe Unmatched (asum (map tier [own, concatMap variants own]))
  where
    own = ordNub (idText i.id : mapMaybe slugify (i.label : i.aliases))
    tier slugs = case ordNub [(s, t) | s <- slugs, t <- maybe [] toList (Map.lookup s index)] of
        [] -> Nothing
        found -> Just $ case ordNub (map snd found) of
            [t] -> Slug (maybe "" fst (viaNonEmpty head found)) t
            a : b : rest -> Ambiguous a b rest
            [] -> Unmatched
    variants s =
        [s <> "s", s <> "es"]
            <> mapMaybe (`T.stripSuffix` s) ["s", "es"]
