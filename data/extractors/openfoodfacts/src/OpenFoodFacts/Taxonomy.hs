{- | The OpenFoodFacts categories taxonomy (@taxonomies/food/categories.txt@
in openfoodfacts-server). Entries are separated by blank lines:

> < en: Animal fats
> en: Butters, cow butters
> fr: Beurres, beurre
> wikidata:en: Q34172

@<@ lines name parents, @lang:@ lines list names in that language (the
first is the canonical one), and @property:lang:@ lines are ignored. An
entry's tag is its first name's, e.g. @en:butters@.
-}
module OpenFoodFacts.Taxonomy (
    Tag,
    tagText,
    mkTag,
    tagFor,
    Category (..),
    Taxonomy (..),
    parseTaxonomy,
)
where

import Babel.Slug (slugify)
import Data.Char (isAsciiLower)
import Data.List qualified as List
import Data.Map.Strict qualified as Map
import Data.Set qualified as Set
import Data.Text qualified as T

-- | A category tag as products carry it: @en:butters@.
newtype Tag = Tag Text
    deriving newtype (Eq, Ord, Show)

tagText :: Tag -> Text
tagText (Tag t) = t

-- | A tag as written, e.g. in @matches.json@ or a product's tags.
mkTag :: Text -> Maybe Tag
mkTag t = case T.breakOn ":" t of
    (lang, rest) | isLanguage lang, Just name <- T.stripPrefix ":" rest, not (T.null name) -> Just (Tag t)
    _ -> Nothing

{- | The tag OpenFoodFacts gives a name: its slug with hyphens, so
@Butter oil or concentrated butter@ is @en:butter-oil-or-concentrated-butter@.
-}
tagFor :: Text -> Text -> Maybe Tag
tagFor lang name = Tag . ((lang <> ":") <>) . T.replace "_" "-" <$> slugify name

data Category = Category
    { tag :: Tag
    , english :: [Text]
    -- ^ The English names, canonical first.
    , parents :: [Tag]
    }
    deriving stock (Eq, Show)

data Taxonomy = Taxonomy
    { categories :: Map Tag Category
    , children :: Map Tag (Set Tag)
    }
    deriving stock (Show)

-- | One entry as written: its parents and names still in their own words.
data Entry = Entry {parentNames :: [(Text, Text)], names :: [(Text, [Text])]}

parseTaxonomy :: Text -> Taxonomy
parseTaxonomy source = Taxonomy{categories, children}
  where
    entries = mapMaybe entry (blocks (lines source))
    byName =
        Map.fromList
            [ (t, canonical)
            | (canonical, e) <- entries
            , (lang, ns) <- e.names
            , Just t <- map (tagFor lang) ns
            ]
    categories =
        Map.fromList
            [ ( canonical
              , Category
                    { tag = canonical
                    , english = concat [ns | ("en", ns) <- e.names]
                    , parents = ordNub (mapMaybe (\(lang, n) -> (`Map.lookup` byName) =<< tagFor lang n) e.parentNames)
                    }
              )
            | (canonical, e) <- entries
            ]
    children = Map.fromListWith (<>) [(p, Set.singleton c.tag) | c <- Map.elems categories, p <- c.parents]

-- | Lines grouped between blank lines, comments dropped.
blocks :: [Text] -> [[Text]]
blocks ls = filter (not . null) (map (filter (not . ("#" `T.isPrefixOf`))) (splitOnBlank ls))
  where
    splitOnBlank xs = case break (T.null . T.strip) xs of
        (b, []) -> [b]
        (b, _ : rest) -> b : splitOnBlank rest

-- | An entry and its tag, or nothing for a block without names (e.g. @synonyms:@ lists).
entry :: [Text] -> Maybe (Tag, Entry)
entry ls = do
    let e = Entry{parentNames = mapMaybe parentLine ls, names = mapMaybe nameLine ls}
    (lang, first' : _) <- listToMaybe e.names
    canonical <- tagFor lang first'
    pure (canonical, e)
  where
    parentLine l = do
        rest <- T.stripPrefix "<" l
        (lang, ns) <- nameLine (T.strip rest)
        n <- listToMaybe ns
        pure (lang, n)
    nameLine l = case T.breakOn ":" l of
        (lang, rest)
            | isLanguage lang
            , Just ns <- T.stripPrefix ":" rest
            , not (isProperty ns) ->
                Just (lang, filter (not . T.null) (map (T.replace "\\," "," . T.strip) (splitNames ns)))
        _ -> Nothing
    -- In @who_id:en: 7@ the text after the first colon starts with a language.
    isProperty rest = case T.breakOn ":" rest of
        (lang, after) -> isLanguage lang && not (T.null after)
    splitNames = map T.pack . go . T.unpack
      where
        go s = case break (== ',') s of
            (a, ',' : b) | "\\" `List.isSuffixOf` a -> case go b of
                n : more -> (a <> "," <> n) : more
                [] -> [a <> ","]
            (a, _ : b) -> a : go b
            (a, []) -> [a]

-- | @en@, @fr@, @nl_be@, @xx@: the language prefixes of names and tags.
isLanguage :: Text -> Bool
isLanguage t = case T.splitOn "_" t of
    [l] -> code l
    [l, region] -> code l && T.length region == 2 && T.all isAsciiLower region
    _ -> False
  where
    code l = T.length l `elem` [2, 3] && T.all isAsciiLower l
