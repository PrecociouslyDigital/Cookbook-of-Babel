{- | Quantities written in recipe text: ingredient amounts, oven
temperatures and step durations.
-}
module Wikibooks.Quantity (
    Qty (..),
    qtyValue,
    unitAliases,
    descriptors,
    IngredientLine (..),
    Lexicon (..),
    ingredientLine,
    temperatureC,
    Duration (..),
    duration,
)
where

import Babel.Model.Common (Amount (..))
import Babel.Model.Id (UnitId (..))
import Data.Char (isAlpha, isAlphaNum, isDigit, isPunctuation)
import Data.List (lookup)
import Data.Text qualified as T
import Text.Megaparsec (Parsec, anySingle, choice, eof, notFollowedBy, oneOf, parse, satisfy, takeWhile1P, takeWhileP, try)
import Text.Megaparsec.Char (char, char', space, space1, string, string')
import Wikibooks.Dump (Title, mkTitle)
import Wikibooks.Wikitext (Inline (..), plain)

type Parser = Parsec Void Text

-- * Numbers

data Qty = Exact Double | Range Double Double
    deriving stock (Eq, Show)

-- | A range counts as its midpoint.
qtyValue :: Qty -> Double
qtyValue = \case
    Exact x -> x
    Range lo hi -> (lo + hi) / 2

unicodeFractions :: [(Char, Double)]
unicodeFractions =
    [ ('½', 1 / 2)
    , ('⅓', 1 / 3)
    , ('⅔', 2 / 3)
    , ('¼', 1 / 4)
    , ('¾', 3 / 4)
    , ('⅕', 1 / 5)
    , ('⅖', 2 / 5)
    , ('⅗', 3 / 5)
    , ('⅘', 4 / 5)
    , ('⅙', 1 / 6)
    , ('⅚', 5 / 6)
    , ('⅛', 1 / 8)
    , ('⅜', 3 / 8)
    , ('⅝', 5 / 8)
    , ('⅞', 7 / 8)
    ]

-- | @3@, @1.5@, @.5@, @1/2@, @½@, @1 ½@, @1½@ or @1 1/2@.
number :: Parser Double
number = choice [try mixed, try fraction, unicode, decimal]
  where
    digits = takeWhile1P (Just "digit") isDigit
    int = digits >>= maybe empty pure . readMaybe . toString
    unicode = satisfy (`elem` map fst unicodeFractions) >>= maybe empty pure . (`lookup` unicodeFractions)
    fraction = do
        n <- int
        _ <- oneOf ['/', '⁄']
        d <- int
        if d == 0 then empty else pure (n / d)
    mixed = do
        whole <- int
        part <- unicode <|> (takeWhile1P Nothing (== ' ') *> (try unicode <|> fraction))
        pure (whole + part)
    decimal = do
        whole <- optional digits
        frac <- optional (try (char '.' *> digits))
        case (whole, frac) of
            (Nothing, Nothing) -> empty
            _ -> maybe empty pure (readMaybe (toString (fromMaybe "0" whole <> "." <> fromMaybe "0" frac)))

qty :: Parser Qty
qty = do
    lo <- number
    hi <- optional . try $ do
        space
        _ <- choice [string "-", string "–", string "—", string' "to" <* space1, string' "or" <* space1]
        space
        number
    pure (maybe (Exact lo) (Range lo) hi)

-- * Units

{- | Unit words as recipes write them, by the registry unit they mean.
Matching ignores case and a trailing full stop, except for the single
letters @t@ (teaspoon) and @T@ (tablespoon).
-}
unitAliases :: [(Text, UnitId)]
unitAliases =
    concat
        [ UnitId "mg" ~> ["mg", "milligram", "milligrams"]
        , UnitId "g" ~> ["g", "gram", "grams", "gramme", "grammes", "gr", "gm", "gms"]
        , UnitId "kg" ~> ["kg", "kgs", "kilogram", "kilograms", "kilo", "kilos"]
        , UnitId "oz" ~> ["oz", "ounce", "ounces"]
        , UnitId "lb" ~> ["lb", "lbs", "pound", "pounds"]
        , UnitId "ml" ~> ["ml", "milliliter", "milliliters", "millilitre", "millilitres", "cc"]
        , UnitId "cl" ~> ["cl", "centiliter", "centiliters", "centilitre", "centilitres"]
        , UnitId "dl" ~> ["dl", "deciliter", "deciliters", "decilitre", "decilitres"]
        , UnitId "l" ~> ["l", "liter", "liters", "litre", "litres", "ltr"]
        , UnitId "fl_oz" ~> ["fl oz", "fl. oz", "fl.oz", "fluid ounce", "fluid ounces"]
        , UnitId "pt" ~> ["pt", "pint", "pints"]
        , UnitId "qt" ~> ["qt", "quart", "quarts"]
        , UnitId "gal" ~> ["gal", "gallon", "gallons"]
        , UnitId "tsp" ~> ["t", "tsp", "tsps", "teaspoon", "teaspoons", "teaspoonful", "teaspoonfuls"]
        , UnitId "tbsp" ~> ["T", "tbsp", "tbsps", "tbs", "tbl", "tbls", "tblsp", "tablespoon", "tablespoons", "tablespoonful", "tablespoonfuls"]
        , UnitId "cup" ~> ["c", "cup", "cups", "cupful", "cupfuls"]
        , UnitId "pinch" ~> ["pinch", "pinches"]
        , UnitId "dash" ~> ["dash", "dashes"]
        , UnitId "drop" ~> ["drop", "drops"]
        , UnitId "piece" ~> ["piece", "pieces", "pc", "pcs", "ea", "each"]
        , UnitId "dozen" ~> ["dozen"]
        , UnitId "clove" ~> ["clove", "cloves"]
        , UnitId "slice" ~> ["slice", "slices"]
        , UnitId "sprig" ~> ["sprig", "sprigs"]
        , UnitId "stalk" ~> ["stalk", "stalks", "rib", "ribs"]
        , UnitId "head" ~> ["head", "heads"]
        , UnitId "bunch" ~> ["bunch", "bunches"]
        , UnitId "handful" ~> ["handful", "handfuls"]
        , UnitId "stick" ~> ["stick", "sticks"]
        , UnitId "strip" ~> ["strip", "strips", "rasher", "rashers"]
        , UnitId "sheet" ~> ["sheet", "sheets"]
        , UnitId "loaf" ~> ["loaf", "loaves"]
        , UnitId "can" ~> ["can", "cans", "tin", "tins"]
        , UnitId "jar" ~> ["jar", "jars"]
        , UnitId "bottle" ~> ["bottle", "bottles"]
        , UnitId "package" ~> ["package", "packages", "packet", "packets", "pack", "packs", "pkg", "sachet", "sachets"]
        , UnitId "bag" ~> ["bag", "bags"]
        , UnitId "box" ~> ["box", "boxes"]
        ]
  where
    unit ~> words' = [(w, unit) | w <- words']

{- | Words that may sit between a count and the food it counts, as in
"2 large eggs", without being a unit.
-}
descriptors :: [Text]
descriptors =
    ["large", "medium", "small", "whole", "fresh", "ripe", "big", "extra-large", "jumbo", "medium-sized", "medium-size", "mid-sized", "average-size", "small-sized", "large-sized"]
        <> ["green", "red", "yellow", "white", "black", "boneless", "skinless", "dried", "thin", "thick", "few", "little", "standard"]

normalizeUnitWord :: Text -> Text
normalizeUnitWord w = case T.dropWhileEnd (== '.') w of
    s | s `elem` ["t", "T"] -> s
    s -> T.toLower s

-- | The longest run of up to three words that is a unit alias.
unitWords :: [Text] -> Maybe (UnitId, [Text])
unitWords ws =
    asum
        [ (,drop n ws) <$> lookup (normalizeUnitWord (T.unwords (take n ws))) aliasMap
        | n <- [3, 2, 1]
        , length ws >= n
        ]
  where
    aliasMap = [(normalizeUnitWord a, u) | (a, u) <- unitAliases]

-- * Ingredient lines

data IngredientLine = IngredientLine
    { ingredient :: Title
    , amount :: Amount
    , optional :: Bool
    , estimates :: [Text]
    -- ^ Why the amount is an estimate, when it is one.
    , unknownUnit :: Maybe Text
    -- ^ The word after the count, when it is neither a unit nor a descriptor.
    }
    deriving stock (Eq, Show)

-- | What reading an ingredient line needs to know about the wider dataset.
data Lexicon = Lexicon
    { isFood :: Title -> Bool
    -- ^ Whether a link leads to a food, rather than a unit, technique or tool.
    , isTool :: Title -> Bool
    -- ^ Whether a link leads to a piece of equipment.
    , weighs :: UnitId -> Bool
    -- ^ Whether a unit measures mass, which a line prefers to other measures.
    }

{- | Read a bulleted ingredient line. The food is the first link to a
food, or else the words after the amount, up to a comma or a note such as
"to taste". 'Nothing' when neither names a food.
-}
ingredientLine :: Lexicon -> [Inline] -> Maybe IngredientLine
ingredientLine lexicon items = do
    let text = plain items
        (q, rest) = leadingQty text
        afterQty = T.words rest
        unit = unitWords (dropWhile ((`elem` descriptors) . clean) (dropOf afterQty)) <* q
        afterUnit = maybe afterQty snd unit
        linked = listToMaybe [(t, s) | Link t s <- items, lexicon.isFood t]
    food <- maybe (mkTitle <$> textName afterUnit) (Just . fst) linked
    let isOptional = "optional" `T.isInfixOf` T.toLower text
        line amount = IngredientLine food amount isOptional
        -- Only a link shows where the food's words start, and so which word is the unit.
        unknown = do
            (_, shown) <- linked
            find (\w -> clean w `notElem` ("of" : descriptors)) (wordsBefore (T.words shown) afterQty)
    pure $ case (q, unit) of
        (Nothing, _) -> line (Amount 1 (UnitId "pinch")) ["no quantity given; assumed a pinch"] Nothing
        (Just n, Just (u, following))
            | not (lexicon.weighs u)
            , Just (m, mu) <- parenthetical following
            , lexicon.weighs mu ->
                line (Amount (qtyValue m) mu) [] Nothing
            | otherwise -> line (Amount (qtyValue n) u) [] Nothing
        (Just n, Nothing) -> case parenthetical afterQty of
            Just (m, u) -> line (Amount (qtyValue m) u) [] Nothing
            Nothing -> line (Amount (qtyValue n) (UnitId "piece")) [quoted w <> " read as pieces" | Just w <- [unknown]] unknown
  where
    quoted w = "'" <> w <> "'"
    dropOf = \case
        w : ws | clean w == "of" -> ws
        ws -> ws
    -- The words up to where the food's own words start.
    wordsBefore food ws =
        case [i | i <- [0 .. length ws], map clean (take (length food) (drop i ws)) == map clean food] of
            i : _ -> take i ws
            [] -> ws

clean :: Text -> Text
clean = T.toLower . T.filter (\c -> not (isPunctuation c) || c == '-')

{- | A food's name from the words after its amount: without a leading
measure in parentheses or words like "chopped", and cut at the first
comma, parenthesis or note. At most four words, or it is likely a note.
-}
textName :: [Text] -> Maybe Text
textName ws = do
    let name = takeWhile (not . stop) (dropWhile ((`elem` preparation) . clean) (dropParenthetical (dropOf ws)))
        cut = T.toLower (T.unwords (map (T.dropWhileEnd (`elem` [',', ';', ':'])) (takeUntilPunctuation name)))
    guard (not (T.null cut) && length (T.words cut) <= 4)
    pure cut
  where
    dropOf = \case
        w : rest | clean w == "of" -> rest
        rest -> rest
    dropParenthetical = \case
        w : rest | "(" `T.isPrefixOf` w -> drop 1 (dropWhile (not . (")" `T.isSuffixOf`)) (w : rest))
        rest -> rest
    stop w = clean w `elem` ["or", "for", "to", "as", "per", "-", "–", "—"] || "(" `T.isPrefixOf` w
    -- Keep words up to and including one that ends in a comma or semicolon.
    takeUntilPunctuation = \case
        [] -> []
        w : rest
            | T.any (`elem` [',', ';', ':']) w -> [w]
            | otherwise -> w : takeUntilPunctuation rest
    preparation =
        descriptors
            <> ["a", "an", "the", "some", "several", "about"]
            <> ["additional", "extra", "more", "cold", "warm", "hot", "boiling", "lukewarm", "chilled", "softened", "melted", "beaten"]
            <> ["chopped", "finely", "roughly", "coarsely", "minced", "diced", "sliced", "grated", "shredded", "crushed"]

{- | The quantity a line opens with, and the text after it. "A" counts as
one only before a unit, as in "a pinch of salt": "a little oil" has no
quantity.
-}
leadingQty :: Text -> (Maybe Qty, Text)
leadingQty text = case parse ((,) <$> lead <*> takeWhileP Nothing (const True) <* eof) "" text of
    Right (Just (_, True), rest) | isNothing (unitWords (T.words rest)) -> (Nothing, text)
    Right (q, rest) -> (fst <$> q, rest)
    Left _ -> (Nothing, text)
  where
    lead = optional . try $ do
        q <- (string' "an" <|> string' "a") $> (Exact 1, True) <|> (,False) <$> qty
        notFollowedBy (satisfy (\c -> isAlpha c || isDigit c))
        guard (qtyValue (fst q) > 0)
        q <$ space

-- | A measure in parentheses right after the count, or up to two words after it.
parenthetical :: [Text] -> Maybe (Qty, UnitId)
parenthetical ws = asum [measureIn (T.unwords rest) | rest <- [ws, drop 1 ws, drop 2 ws], "(" `T.isPrefixOf` T.unwords rest]
  where
    measureIn t = do
        let inside = T.takeWhile (`notElem` [')', '/', ';', ',']) (T.drop 1 t)
        (Just q, rest) <- Just (leadingQty (T.strip inside))
        (u, _) <- unitWords (T.words rest)
        Just (q, u)

-- * Temperatures and durations

-- | Every match of a parser, trying it at the start of each word.
scan :: Parser a -> Text -> [a]
scan p = fromRight [] . parse (catMaybes <$> many (Just <$> try p <|> Nothing <$ skipWord) <* eof) ""
  where
    skipWord = void (takeWhile1P Nothing isAlphaNum) <|> void anySingle

data Temperature = Celsius Double | Fahrenheit Double | GasMark Int

{- | The temperature a step names, in °C. Celsius wins over Fahrenheit
when a step gives both; gas marks are converted.
-}
temperatureC :: Text -> Maybe Double
temperatureC text = asum [listToMaybe [c | Celsius c <- found], listToMaybe [(f - 32) * 5 / 9 | Fahrenheit f <- found], listToMaybe (mapMaybe gas found)] >>= plausible
  where
    found = scan temperature text
    plausible c = if c > -30 && c < 400 then Just c else Nothing
    gas = \case
        GasMark n -> lookup n (zip [1 ..] [140, 150, 170, 180, 190, 200, 220, 230, 240])
        _ -> Nothing

temperature :: Parser Temperature
temperature = gasMark <|> reading
  where
    gasMark = string' "gas mark" *> space *> (GasMark . round <$> number)
    reading = do
        q <- qtyValue <$> qty
        gap <- takeWhileP Nothing (== ' ')
        marker <- optional (choice [string "°", string "º", try (string' "degrees"), try (string' "degree")]) <* space
        scale <- choice [Celsius <$ try (string' "celsius"), Celsius <$ try (string' "centigrade"), Fahrenheit <$ try (string' "fahrenheit"), Celsius <$ char' 'c', Fahrenheit <$ char' 'f']
        notFollowedBy (satisfy isAlpha)
        -- "2 C" is more often two cups than two degrees.
        case (scale q, marker) of
            (Celsius _, Nothing) | not (T.null gap) -> empty
            (t, _) -> pure t

data Duration = Duration {amount :: Amount, notes :: [Text]}
    deriving stock (Eq, Show)

{- | The first duration a step names; a range counts as its midpoint.
Flags a step that names several durations, since the first may not be
the step's whole length.
-}
duration :: Text -> Maybe Duration
duration text = case scan timeSpan (T.toLower text) of
    [] -> Nothing
    found@(Duration a notes : _) -> Just (Duration a (notes <> ["the step names several durations; used the first" | length found > 1]))

timeSpan :: Parser Duration
timeSpan = choice [counted, phrase]
  where
    counted = do
        q <- qty
        space
        (unit, scale) <- timeUnit
        notFollowedBy (satisfy isAlpha)
        pure (Duration (Amount (qtyValue q * scale) unit) [])
    phrase =
        choice
            [ try (string "half an hour") $> Duration (Amount 30 (UnitId "min")) []
            , try (string "an hour") $> Duration (Amount 1 (UnitId "h")) []
            , try (string "a minute") $> Duration (Amount 1 (UnitId "min")) []
            , string "overnight" $> Duration (Amount 8 (UnitId "h")) ["'overnight' read as 8 hours"]
            ]
    timeUnit =
        choice
            [ (UnitId "s", 1) <$ try (choice (map string ["seconds", "second", "secs", "sec"]))
            , (UnitId "min", 1) <$ try (choice (map string ["minutes", "minute", "mins", "min"]))
            , (UnitId "h", 1) <$ try (choice (map string ["hours", "hour", "hrs", "hr"]))
            , (UnitId "d", 1) <$ try (choice (map string ["days", "day"]))
            , (UnitId "d", 7) <$ try (choice (map string ["weeks", "week"]))
            ]
