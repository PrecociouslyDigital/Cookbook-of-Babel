{- | The Cookbook namespace of a Wikimedia pages-articles dump: every page
by title, with redirects kept so links can be followed to the page they
end up at.
-}
module Wikibooks.Dump (
    Title,
    titleText,
    mkTitle,
    cookbookTitle,
    Body (..),
    Cookbook,
    cookbookFromPages,
    readCookbook,
    resolve,
    article,
    articles,
)
where

import Conduit (ConduitT, MonadThrow, runConduit, sinkList, sourceHandle, yield, (.|))
import Data.Char (isSpace, toUpper)
import Data.Map.Strict qualified as Map
import Data.Text qualified as T
import Data.XML.Types (Event, Name (..))
import Text.XML.Stream.Parse (def, ignoreAnyTreeContent, ignoreAttrs, many_, matching, parseBytes, requireAttr, tag', tagIgnoreAttrs)
import Text.XML.Stream.Parse qualified as X

{- | A Cookbook page name, without the namespace, as MediaWiki compares
them: underscores are spaces, runs of spaces collapse, and the first
letter is upper case.
-}
newtype Title = Title Text
    deriving newtype (Eq, Ord, Show)

titleText :: Title -> Text
titleText (Title t) = t

mkTitle :: Text -> Title
mkTitle raw = Title (upperFirst (T.unwords (T.words (T.map underscoreToSpace (T.takeWhile (/= '#') raw)))))
  where
    underscoreToSpace c = if c == '_' then ' ' else c
    upperFirst t = maybe t (\(c, rest) -> T.cons (toUpper c) rest) (T.uncons t)

-- | The title a full page name refers to, when it is in the Cookbook namespace.
cookbookTitle :: Text -> Maybe Title
cookbookTitle full = case T.breakOn ":" (T.dropWhile (== ':') (T.strip full)) of
    (ns, rest)
        | T.toLower (T.strip ns) == "cookbook"
        , Just (_, name) <- T.uncons rest
        , not (T.all isSpace name) ->
            Just (mkTitle name)
    _ -> Nothing

data Body
    = Redirect Title
    | Article {revid :: Int, wikitext :: Text}
    deriving stock (Eq, Show)

newtype Cookbook = Cookbook (Map Title Body)

cookbookFromPages :: [(Title, Body)] -> Cookbook
cookbookFromPages = Cookbook . Map.fromList

-- | Stream a decompressed dump and keep its Cookbook pages.
readCookbook :: Handle -> IO Cookbook
readCookbook h = fmap cookbookFromPages . runConduit $ sourceHandle h .| parseBytes def .| pages .| sinkList

{- | Follow redirects to the title they end up at. A redirect loop ends at
the first title seen twice.
-}
resolve :: Cookbook -> Title -> Title
resolve (Cookbook m) = go []
  where
    go seen t = case Map.lookup t m of
        Just (Redirect next) | t `notElem` seen -> go (t : seen) next
        _ -> t

-- | The article a title ends up at, after redirects.
article :: Cookbook -> Title -> Maybe (Title, Int, Text)
article c t = case Map.lookup target (let Cookbook m = c in m) of
    Just (Article r w) -> Just (target, r, w)
    _ -> Nothing
  where
    target = resolve c t

-- | Every article, in title order.
articles :: Cookbook -> [(Title, Int, Text)]
articles (Cookbook m) = [(t, r, w) | (t, Article r w) <- Map.toList m]

-- * Parsing

named :: Text -> X.NameMatcher Name
named n = matching ((== n) . nameLocalName)

{- | Cookbook pages, in dump order. A redirect out of the namespace is
dropped, so its title reads as a page that does not exist.
-}
pages :: (MonadThrow m) => ConduitT Event (Title, Body) m ()
pages = void . X.force "mediawiki" . tagIgnoreAttrs (named "mediawiki") $ do
    many_ (X.ignoreTree (named "siteinfo") ignoreAttrs)
    fix $ \loop ->
        page >>= \case
            Nothing -> pure ()
            Just kept -> traverse_ yield kept >> loop

page :: (MonadThrow m) => ConduitT Event o m (Maybe (Maybe (Title, Body)))
page = tagIgnoreAttrs (named "page") $ do
    title <- leaf "title"
    ns <- leaf "ns"
    redirect <- skipUntil ["redirect", "revision"] >> tag' (named "redirect") (requireAttr "title" <* ignoreAttrs) pure
    skipUntil ["revision"]
    (revid, text) <- X.force "revision" . tagIgnoreAttrs (named "revision") $ do
        r <- leaf "id"
        skipUntil ["text"]
        t <- X.force "text" (tag' (named "text") ignoreAttrs (const X.content))
        many_ ignoreAnyTreeContent
        pure (r, t)
    many_ ignoreAnyTreeContent
    pure $ do
        guard (ns == "102")
        name <- cookbookTitle title
        body <- case redirect of
            Just target -> Redirect <$> cookbookTitle target
            Nothing -> Article <$> readMaybe (toString revid) <*> pure text
        pure (name, body)
  where
    leaf n = X.force (toString n) (X.tagNoAttr (named n) X.content)

-- | Skip sibling elements up to the next one with one of these local names.
skipUntil :: (MonadThrow m) => [Text] -> ConduitT Event o m ()
skipUntil ns = many_ (X.ignoreTree (matching ((`notElem` ns) . nameLocalName)) ignoreAttrs)
