{- | From products to one value per category: the median of each nutrient
over the plausible products filed under it, and the median serving of
those whose serving is one piece.
-}
module OpenFoodFacts.Aggregate (
    -- * Products
    ProductRow (..),
    Serving (..),
    parseProduct,
    plausible,

    -- * Tallies
    Tally (..),
    Tallies,
    Both (..),
    tally,
    tallyLines,

    -- * Summaries
    Scope (..),
    Summary (..),
    summarise,
    median,
    onePiece,

    -- * Thresholds
    minDirectProducts,
    minProductsPerNutrient,
    confidentProducts,
    minPieceProducts,
    maxKcal,
)
where

import Babel.Model.Measure (Per100g (..))
import Data.Char (isDigit)
import Data.Map.Strict qualified as Map
import Data.Set qualified as Set
import Data.Text qualified as T
import OpenFoodFacts.Taxonomy (Tag, Taxonomy (..), mkTag)

{- | Fewer products than this filed directly under a category, and its whole
subtree counts instead.
-}
minDirectProducts :: Int
minDirectProducts = 10

-- | A nutrient stated by fewer products than this is left out.
minProductsPerNutrient :: Int
minProductsPerNutrient = 5

-- | A value from fewer products than this is flagged uncertain.
confidentProducts :: Int
confidentProducts = 30

-- | A piece mass needs at least this many one-piece servings.
minPieceProducts :: Int
minPieceProducts = 3

-- | No food has more energy than pure fat, about 900 kcal per 100 g.
maxKcal :: Double
maxKcal = 900

kJPerKcal :: Double
kJPerKcal = 4.184

-- * Products

-- | A serving as labelled, and its weight.
data Serving = Serving {size :: !Text, grams :: !Double}
    deriving stock (Eq, Show)

-- | Nutrients are per 100 g; kcal comes from kJ where only that is given.
data ProductRow = ProductRow
    { tags :: [Tag]
    , nutrients :: !Per100g
    , serving :: !(Maybe Serving)
    }
    deriving stock (Eq, Show)

{- | One line of @products.tsv@ (see @fetch.sh@): code, categories, kcal,
kJ, protein, fat, carbohydrates, serving size, serving quantity.
-}
parseProduct :: Text -> Maybe ProductRow
parseProduct line = case T.splitOn "\t" line of
    [_code, tags, kcal, kj, protein, fat, carbs, size, quantity] ->
        let !energy = number kcal <|> fmap (/ kJPerKcal) (number kj)
            !p = number protein
            !f = number fat
            !c = number carbs
            !s = do
                g <- number quantity
                guard (g > 0 && not (T.null size))
                -- Copied, so the serving doesn't keep the whole input chunk alive.
                Just $! Serving (T.copy size) g
         in Just ProductRow{tags = mapMaybe mkTag (T.splitOn "," tags), nutrients = Per100g energy p f c, serving = s}
    _ -> Nothing
  where
    number t = case readMaybe (toString t) of
        Just x | not (isNaN x || isInfinite x) -> x `seq` Just x
        _ -> Nothing

{- | Each macro within 0-100 g, together no more than 100 g, and the energy
within 0-'maxKcal'. A label that breaks these is misread or mistyped.
-}
plausible :: Per100g -> Bool
plausible n =
    all (\m -> m >= 0 && m <= 100) macros
        && sum macros <= 100
        && maybe True (\k -> k >= 0 && k <= maxKcal) n.kcal
  where
    macros = catMaybes [n.proteinG, n.fatG, n.carbsG]

-- * Tallies

-- | What the products of one category said. 'products' counts those with nutrition.
data Tally = Tally {products :: !Int, nutrition :: ![Per100g], servings :: ![Serving]}
    deriving stock (Eq, Show)

instance Semigroup Tally where
    Tally a b c <> Tally a' b' c' = Tally (a + a') (b <> b') (c <> c')

instance Monoid Tally where
    mempty = Tally 0 [] []

{- | A product is filed directly under a category when none of its other
categories lies below it: plain milk, not the flavoured milks below milk.
Its subtree holds every product in it.
-}
data Both = Both {direct :: !Tally, subtree :: !Tally}
    deriving stock (Eq, Show)

instance Semigroup Both where
    Both a b <> Both a' b' = Both (a <> a') (b <> b')

type Tallies = Map Tag Both

-- | Add a product to the tallies of the wanted categories it is in. Implausible products count for nothing.
tally :: Taxonomy -> Set Tag -> Tallies -> ProductRow -> Tallies
tally taxonomy wanted acc (ProductRow tags n s)
    | not (plausible n) = acc
    | otherwise = foldl' add acc [t | t <- tags, Set.member t wanted]
  where
    -- Matching the constructor keeps the tallies from holding on to the product.
    own = Set.fromList tags
    hasNutrition = any isJust [n.kcal, n.proteinG, n.fatG, n.carbsG]
    contribution = Tally (if hasNutrition then 1 else 0) [n | hasNutrition] (maybeToList s)
    add m t = Map.insertWith (<>) t (Both (if direct then contribution else mempty) contribution) m
      where
        direct = Set.disjoint (Map.findWithDefault Set.empty t taxonomy.children) own

-- | Tally every line of @products.tsv@, its header left out.
tallyLines :: Taxonomy -> Set Tag -> [Text] -> Tallies
tallyLines taxonomy wanted = foldl' (\acc l -> maybe acc (tally taxonomy wanted acc) (parseProduct l)) mempty

-- * Summaries

data Scope = Direct | Subtree
    deriving stock (Eq, Show)

data Summary = Summary
    { tag :: Tag
    , scope :: Scope
    , products :: Int
    , per100g :: Maybe Per100g
    , piece :: Maybe (Double, Int)
    -- ^ The median grams of a one-piece serving, and how many servings.
    }
    deriving stock (Eq, Show)

{- | The category's values. @nouns@ are the words a one-piece serving may
name the food by, besides @piece@ and its kin ("1 egg (50 g)").
-}
summarise :: [Text] -> Tag -> Both -> Summary
summarise nouns t b =
    Summary
        { tag = t
        , scope
        , products = chosen.products
        , per100g = if all isNothing [values.kcal, values.proteinG, values.fatG, values.carbsG] then Nothing else Just values
        , piece = case nonEmpty [s.grams | s <- chosen.servings, onePiece nouns s.size] of
            Just gs | length gs >= minPieceProducts -> Just (roundTo 1 (median gs), length gs)
            _ -> Nothing
        }
  where
    (scope, chosen) = if b.direct.products >= minDirectProducts then (Direct, b.direct) else (Subtree, b.subtree)
    values =
        Per100g
            { kcal = roundTo 0 <$> stat (.kcal)
            , proteinG = roundTo 1 <$> stat (.proteinG)
            , fatG = roundTo 1 <$> stat (.fatG)
            , carbsG = roundTo 1 <$> stat (.carbsG)
            }
    stat f = case nonEmpty (mapMaybe f chosen.nutrition) of
        Just xs | length xs >= minProductsPerNutrient -> Just (median xs)
        _ -> Nothing

-- | Round to this many decimal places.
roundTo :: Int -> Double -> Double
roundTo places x = fromIntegral (round (x * scale) :: Integer) / scale
  where
    scale = 10 ^ places

median :: NonEmpty Double -> Double
median xs = case drop ((n - 1) `div` 2) sorted of
    a : b : _ | even n -> (a + b) / 2
    a : _ -> a
    [] -> head xs
  where
    sorted = sort (toList xs)
    n = length sorted

{- | Whether a serving is one of something: @1 piece@, @1 large egg (50 g)@.
It must be weighed in grams, so a serving in millilitres never counts.
-}
onePiece :: [Text] -> Text -> Bool
onePiece nouns size = counted && weighed
  where
    (named, rest) = T.breakOn "(" (T.toLower size)
    counted = case words named of
        ["1", noun] -> noun `elem` pieceWords
        ["1", _, noun] -> noun `elem` pieceWords
        _ -> False
    pieceWords = ["piece", "pc", "unit", "item"] <> nouns
    weighed = any grams (words (T.map (\c -> if c `elem` ("()," :: String) then ' ' else c) (named <> rest)))
    grams w = w == "g" || (Just "g" == T.stripPrefix (T.dropWhile (\c -> isDigit c || c == '.') w) "g" && T.any isDigit w)
