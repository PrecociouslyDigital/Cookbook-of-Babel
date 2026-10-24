{- | The parts of MediaWiki markup the extractor reads: sections, list
items, Cookbook links, and the few templates that carry quantities.
Everything else is reduced to its visible text or dropped.
-}
module Wikibooks.Wikitext (
    Inline (..),
    inlines,
    plain,
    Section (..),
    sections,
    sectionBody,
    bullets,
    numbered,
    intro,
    PageKind (..),
    pageKind,
    templateNames,
)
where

import Data.Char (isAlpha, isDigit, isSpace, isUpper)
import Data.List (lookup)
import Data.Text qualified as T
import Text.Megaparsec (Parsec, anySingle, choice, eof, manyTill, parseMaybe, satisfy, takeWhile1P, takeWhileP, try)
import Text.Megaparsec.Char (char, string, string')
import Wikibooks.Dump (Title, cookbookTitle, titleText)

-- * Inline markup

-- | Visible text, and links into the Cookbook with the text they show.
data Inline = Words Text | Link Title Text
    deriving stock (Eq, Show)

-- | The text a reader sees, with whitespace runs collapsed.
plain :: [Inline] -> Text
plain = T.unwords . T.words . foldMap (\case Words t -> t; Link _ t -> t)

type Parser = Parsec Void Text

inlines :: Text -> [Inline]
inlines t = fromMaybe [Words t] (parseMaybe (concat <$> manyTill inline eof) t)

inline :: Parser [Inline]
inline =
    choice
        [ try link
        , try template
        , try (string "<!--" *> manyTill anySingle (string "-->") $> [])
        , try ref
        , try (string' "<br" *> takeWhileP Nothing (/= '>') *> char '>' $> [Words " "])
        , try (char '<' *> optional (char '/') *> satisfy isAlpha *> takeWhileP Nothing (`notElem` ['<', '>']) *> char '>' $> [])
        , try (string "''" *> takeWhileP Nothing (== '\'') $> [])
        , try (string "__" *> takeWhile1P Nothing isUpper *> string "__" $> [])
        , try externalLink
        , try entity
        , one . Words <$> takeWhile1P Nothing (`notElem` ['[', '{', '<', '\'', '&', '_'])
        , one . Words . one <$> anySingle
        ]

-- | The inside of a balanced @open … close@ pair, nested pairs included.
bracketed :: Text -> Text -> Parser Text
bracketed open close = string open *> (T.concat <$> manyTill part (string close))
  where
    part = choice [(\i -> open <> i <> close) <$> try (bracketed open close), one <$> anySingle]

-- | Split on @|@ outside nested links and templates.
splitPipes :: Text -> [Text]
splitPipes = go (0 :: Int) ""
  where
    go depth acc s = case T.uncons s of
        Nothing -> [acc]
        Just (c, rest)
            | T.isPrefixOf "[[" s || T.isPrefixOf "{{" s -> go (depth + 1) (acc <> T.take 2 s) (T.drop 2 s)
            | (T.isPrefixOf "]]" s || T.isPrefixOf "}}" s) && depth > 0 -> go (depth - 1) (acc <> T.take 2 s) (T.drop 2 s)
            | c == '|' && depth == 0 -> acc : go depth "" rest
            | otherwise -> go depth (T.snoc acc c) rest

link :: Parser [Inline]
link = do
    inner <- bracketed "[[" "]]"
    trail <- takeWhileP Nothing isAlpha
    let (target, shown) = case splitPipes inner of
            t : rest@(_ : _) -> (t, T.intercalate "|" rest)
            _ -> (inner, "")
        prefix = T.toLower (T.strip (T.takeWhile (/= ':') (T.dropWhile (== ':') target)))
    pure $ case cookbookTitle target of
        _ | prefix `elem` ["file", "image", "category", "media"] -> []
        Just title -> [Link title (orTitle (plain (inlines shown)) (titleText title) <> trail)]
        Nothing -> inlines (orTitle shown (T.takeWhileEnd (/= ':') target)) <> [Words trail]
  where
    orTitle shown fallback = if T.null (T.strip shown) then fallback else shown

template :: Parser [Inline]
template = do
    inner <- bracketed "{{" "}}"
    pure $ case map T.strip (splitPipes inner) of
        name : args -> expand (T.toLower name) [a | a <- args, not (T.any (== '=') a)]
        [] -> []
  where
    expand name args = case (name, args) of
        ("convert", n : sep : n2 : u : _) | sep `elem` ["-", "–", "to", "and", "or"] -> [Words (n <> "-" <> n2 <> " " <> unit u)]
        ("convert", n : u : _) -> [Words (n <> " " <> unit u)]
        ("frac", [a, b, c]) -> [Words (a <> " " <> b <> "/" <> c)]
        ("frac", [a, b]) -> [Words (a <> "/" <> b)]
        ("frac", [b]) -> [Words ("1/" <> b)]
        (_, a : _) | name `elem` ["nowrap", "nobr", "nowrap begin"] -> inlines a
        _ -> []
    unit = \case
        "F" -> "°F"
        "C" -> "°C"
        "USoz" -> "oz"
        "USfloz" -> "fl oz"
        "impfloz" -> "fl oz"
        "USpt" -> "pt"
        "USqt" -> "qt"
        u -> u

ref :: Parser [Inline]
ref = do
    _ <- string' "<ref"
    attrs <- takeWhileP Nothing (/= '>')
    _ <- char '>'
    unless ("/" `T.isSuffixOf` attrs) (void (manyTill anySingle (string' "</ref>")))
    pure []

externalLink :: Parser [Inline]
externalLink = do
    _ <- char '['
    _ <- string' "http" <|> string "//"
    _ <- takeWhileP Nothing (\c -> not (isSpace c) && c /= ']')
    shown <- takeWhileP Nothing (/= ']')
    _ <- char ']'
    pure [Words (T.strip shown)]

entity :: Parser [Inline]
entity = do
    _ <- char '&'
    name <- takeWhile1P Nothing (\c -> isAlpha c || isDigit c || c == '#')
    _ <- char ';'
    maybe empty (pure . one . Words) (decode name)
  where
    decode name = case T.uncons name of
        Just ('#', n) -> one . chr <$> readMaybe (toString n)
        _ -> lookup name named
    named =
        [ ("nbsp", " ")
        , ("thinsp", " ")
        , ("amp", "&")
        , ("lt", "<")
        , ("gt", ">")
        , ("quot", "\"")
        , ("deg", "°")
        , ("frac12", "½")
        , ("frac14", "¼")
        , ("frac34", "¾")
        , ("ndash", "–")
        , ("mdash", "—")
        , ("times", "×")
        , ("minus", "−")
        ]

-- * Page structure

-- | A heading and the lines under it, up to the next heading of any level.
data Section = Section {level :: Int, heading :: Text, body :: [Text]}
    deriving stock (Eq, Show)

-- | The lines before the first heading, and every section in order.
sections :: Text -> ([Text], [Section])
sections text = (lead, go rest)
  where
    (lead, rest) = break (isJust . headingOf) (T.lines text)
    go = \case
        [] -> []
        l : ls
            | Just (n, h) <- headingOf l ->
                let (b, more) = break (isJust . headingOf) ls in Section n h b : go more
            | otherwise -> go ls
    headingOf l =
        let s = T.strip l
            n = T.length (T.takeWhile (== '=') s)
            inner = T.strip (T.dropAround (== '=') s)
         in if n >= 2 && T.length (T.takeWhileEnd (== '=') s) == n && not (T.null inner)
                then Just (n, inner)
                else Nothing

{- | The lines of the first section whose heading the predicate accepts,
with its subsections.
-}
sectionBody :: (Text -> Bool) -> Text -> Maybe [Text]
sectionBody wanted text = case break (wanted . (.heading)) (snd (sections text)) of
    (_, s : more) -> Just (s.body <> concat [sub.body | sub <- takeWhile ((> s.level) . (.level)) more])
    _ -> Nothing

-- | Bulleted lines at any depth, without their markers.
bullets :: [Text] -> [Text]
bullets ls = [T.strip (T.dropWhile (== '*') l) | l <- map T.stripStart ls, "*" `T.isPrefixOf` l]

{- | The items of numbered lists. Deeper items and @#:@ continuation lines
belong to the item above them.
-}
numbered :: [Text] -> [Text]
numbered = go Nothing
  where
    go current = \case
        [] -> maybeToList current
        l : ls -> case T.uncons (T.stripStart l) of
            Just ('#', rest) -> case T.uncons rest of
                Just (c, _) | c `elem` ['#', '*', ':'] -> go (fmap (<> " " <> T.dropWhile (`elem` ['#', '*', ':']) rest) current) ls
                _ -> maybeToList current <> go (Just (T.strip rest)) ls
            _ -> go current ls

{- | The page's opening prose: the text before the first heading, without
templates, navigation lines or files.
-}
intro :: Text -> Text
intro text =
    T.unwords
        [ l
        | l <- map T.strip (T.lines (foldMap visible (inlines (T.unlines (fst (sections text))))))
        , not (T.null l)
        , not ("|" `T.isPrefixOf` l)
        ]
  where
    visible = \case
        Words t -> t
        Link _ t -> t

-- * Page kinds

-- | What a page describes, from the type template it carries.
data PageKind = UnitPage | TechniquePage | EquipmentPage | OtherPage
    deriving stock (Eq, Show)

pageKind :: Text -> PageKind
pageKind text = fromMaybe OtherPage (asum [lookup n kinds | n <- templateNames text])
  where
    kinds = [("unit", UnitPage), ("technique", TechniquePage), ("equipment", EquipmentPage)]

-- | The names of the templates a page uses, lower-cased.
templateNames :: Text -> [Text]
templateNames text =
    [ T.toLower (T.strip (T.takeWhile (`notElem` ['|', '}']) (T.drop 2 occurrence)))
    | (_, occurrence) <- T.breakOnAll "{{" text
    ]
