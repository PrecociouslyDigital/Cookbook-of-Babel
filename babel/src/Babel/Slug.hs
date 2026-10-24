-- | Slugs made from names, the one way every extractor makes them.
module Babel.Slug (slugify) where

import Data.Char (isAsciiLower, isDigit)
import Data.List (lookup)
import Data.Text qualified as T

{- | A slug from a name: lower case ASCII letters and digits, single
underscores between words. Accented Latin letters lose their accents;
anything else separates words.
-}
slugify :: Text -> Maybe Text
slugify t = case T.intercalate "_" (filter (not . T.null) (T.split (== '_') (T.concatMap ascii (T.toLower t)))) of
    "" -> Nothing
    s -> Just s
  where
    ascii c
        | isAsciiLower c || isDigit c = one c
        | Just plainLetters <- lookup c accented = plainLetters
        | otherwise = "_"
    accented =
        [(c, one base) | (base, cs) <- latin, c <- cs]
            <> [('æ', "ae"), ('œ', "oe"), ('ß', "ss"), ('þ', "th"), ('ð', "d")]
    latin :: [(Char, String)]
    latin =
        [ ('a', "àáâãäåāăą")
        , ('c', "çćč")
        , ('d', "ď")
        , ('e', "èéêëēėęě")
        , ('g', "ğ")
        , ('i', "ìíîïīı")
        , ('l', "ł")
        , ('n', "ñńň")
        , ('o', "òóôõöøō")
        , ('r', "ř")
        , ('s', "śšş")
        , ('t', "ť")
        , ('u', "ùúûüūů")
        , ('y', "ýÿ")
        , ('z', "źżž")
        ]
