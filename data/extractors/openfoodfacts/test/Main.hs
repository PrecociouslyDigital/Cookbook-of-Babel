{- | Properties of the taxonomy parser, the matching and the aggregation,
and a golden test of a small taxonomy and product file end to end.
-}
module Main (main) where

import Babel.Model (FormId (..), Ingredient (..), IngredientId (..), Issue (..), IssueKind (..), Measure (..), MeasureValue (..), Per100g (..), Source (..), SourceId (..), UnitId (..))
import Data.Map.Strict qualified as Map
import Data.Set qualified as Set
import Hedgehog (Gen, Property, annotateShow, assert, forAll, property, (===))
import Hedgehog.Gen qualified as Gen
import Hedgehog.Range qualified as Range
import OpenFoodFacts.Aggregate (Both (..), ProductRow (..), Tally (..), median, plausible, tally, tallyLines)
import OpenFoodFacts.Emit (Extraction (..), emit, matchAll, wanted)
import OpenFoodFacts.Match (Match (..), Override (..), englishIndex, match)
import OpenFoodFacts.Taxonomy (Category (..), Tag, Taxonomy (..), mkTag, parseTaxonomy)
import Test.Tasty (defaultMain, testGroup)
import Test.Tasty.HUnit (testCase, (@?=))
import Test.Tasty.Hedgehog (testPropertyNamed)

main :: IO ()
main =
    defaultMain $
        testGroup
            "babel-openfoodfacts"
            [ testCase "the taxonomy reads names, parents and children" taxonomyFixture
            , prop "a median lies between the least and the greatest value" medianBetween
            , prop "a median does not depend on the order of the values" medianPermutation
            , prop "the median of one value is that value" medianSingleton
            , prop "every product a tally keeps is plausible" talliesPlausible
            , prop "an override beats a matching name" overrideWins
            , testCase "a small product file gives the expected measures" golden
            ]
  where
    prop name = testPropertyNamed name (fromString name)

-- * Taxonomy

fixture :: Taxonomy
fixture =
    parseTaxonomy . unlines $
        [ "# a comment"
        , "synonyms:en: flavoured, flavored"
        , ""
        , "en: Dairies"
        , "fr: Produits laitiers"
        , ""
        , "< en: Dairies"
        , "en: Milks, milk"
        , "fr: Laits"
        , "wikidata:en: Q8495"
        , ""
        , "< en: milk"
        , "en: Flavoured milks"
        , "who_id:en: 7"
        , ""
        , "< fr: Laits"
        , "en: Whole milks"
        , ""
        , "en: Eggs"
        ]

tag :: Text -> Tag
tag t = fromMaybe (error ("not a tag: " <> t)) (mkTag t)

taxonomyFixture :: IO ()
taxonomyFixture = do
    Map.keys fixture.categories @?= map tag ["en:dairies", "en:eggs", "en:flavoured-milks", "en:milks", "en:whole-milks"]
    ((.english) <$> Map.lookup (tag "en:milks") fixture.categories) @?= Just ["Milks", "milk"]
    ((.parents) <$> Map.lookup (tag "en:flavoured-milks") fixture.categories) @?= Just [tag "en:milks"]
    Map.lookup (tag "en:milks") fixture.children @?= Just (Set.fromList [tag "en:flavoured-milks", tag "en:whole-milks"])

-- * Medians

values :: Gen (NonEmpty Double)
values = Gen.nonEmpty (Range.linear 1 50) (Gen.double (Range.linearFrac (-1000) 1000))

medianBetween :: Property
medianBetween = property $ do
    xs <- forAll values
    let m = median xs
    assert (foldl' min (head xs) xs <= m && m <= foldl' max (head xs) xs)

medianPermutation :: Property
medianPermutation = property $ do
    xs <- forAll values
    ys <- forAll (Gen.shuffle (toList xs))
    Just (median xs) === (median <$> nonEmpty ys)

medianSingleton :: Property
medianSingleton = property $ do
    x <- forAll (Gen.double (Range.linearFrac (-1000) 1000))
    median (x :| []) === x

-- * Tallies

genProduct :: Gen ProductRow
genProduct = do
    tags <- Gen.subsequence (Map.keys fixture.categories)
    let amount = Gen.maybe (Gen.double (Range.linearFrac (-50) 1000))
    n <- Per100g <$> amount <*> amount <*> amount <*> amount
    pure ProductRow{tags, nutrients = n, serving = Nothing}

talliesPlausible :: Property
talliesPlausible = property $ do
    ps <- forAll (Gen.list (Range.linear 0 50) genProduct)
    let tallies = foldl' (tally fixture (Map.keysSet fixture.categories)) mempty ps
        kept = concat [t.nutrition | b <- Map.elems tallies, t <- [b.direct, b.subtree]]
    annotateShow kept
    assert (all plausible kept)
    -- Every plausible product with nutrition is in the subtree of each of its categories.
    sum [b.subtree.products | b <- Map.elems tallies] === sum [length p.tags | p <- ps, plausible p.nutrients, p.nutrients /= Per100g Nothing Nothing Nothing Nothing]

-- * Matching

ingredient :: Text -> Text -> Ingredient
ingredient i label =
    Ingredient
        { id = IngredientId i
        , label
        , aliases = []
        , parents = []
        , form = FormId "solid"
        , texture = mempty
        , taste = mempty
        , servingTags = []
        , densityGPerMl = Nothing
        , waterActivity = Nothing
        , named = False
        , issues = []
        }

overrideWins :: Property
overrideWins = property $ do
    c <- forAll (Gen.element (Map.elems fixture.categories))
    name <- forAll (Gen.element c.english)
    other <- forAll (Gen.element (Map.keys fixture.categories))
    let i = ingredient "food" name
    assert (match (englishIndex fixture) Nothing i /= Unmatched)
    match (englishIndex fixture) (Just (MatchTo other)) i === Overridden other

-- * Golden

products :: [Text]
products =
    [ "1\ten:dairies,en:milks\t\t267.8\t3.2\t3.6\t4.8\t\t"
    , "2\ten:dairies,en:milks\t46\t\t3.4\t1.5\t4.9\t\t"
    , "3\ten:dairies,en:milks\t66\t\t3.3\t3.5\t4.7\t\t"
    , "4\ten:dairies,en:milks,en:flavoured-milks\t80\t\t3\t2\t10\t\t"
    , "5\ten:dairies,en:milks,en:flavoured-milks\t90\t\t3.1\t2.5\t12\t\t"
    , "6\ten:dairies,en:milks\t60\t\t3\t150\t5\t\t"
    , "7\ten:eggs\t140\t\t12.5\t9.5\t0.2\t1 egg (50 g)\t50"
    , "8\ten:eggs\t143\t\t12.5\t10\t0.3\t1 large egg (60 g)\t60"
    , "9\ten:eggs\t155\t\t12.5\t11\t0.4\t2 eggs (100 g)\t100"
    , "10\ten:eggs\t138\t\t12.5\t9\t0.8\t1 piece (48g)\t48"
    , "11\ten:eggs\t147\t\t12.5\t10\t0.9\t100 ml\t100"
    , "12\ten:eggs\t150\t\t12.5\t10\t1.0\t\t"
    ]

golden :: IO ()
golden = do
    let ms = matchAll (englishIndex fixture) mempty [ingredient "egg" "egg", ingredient "milk" "milk"]
        x = emit "https://example.org/food.parquet" (tallyLines fixture (wanted ms) products) ms
        off = SourceId "openfoodfacts"
        note k n = [Issue "value" Uncertain n | k] <> [Issue "value" Estimate n | not k]
    x.measures
        @?= [ Measure (IngredientId "egg") off (Nutrition (Per100g (Just 145) (Just 12.5) (Just 10) (Just 0.6))) (note True "median of 6 products in en:eggs and the categories below it")
            , Measure (IngredientId "egg") off (GramsPer (UnitId "piece") 50) (note True "median of 3 one-piece servings of products in en:eggs and the categories below it")
            , Measure (IngredientId "milk") off (Nutrition (Per100g (Just 66) (Just 3.2) (Just 2.5) (Just 4.9))) (note True "median of 5 products in en:milks and the categories below it")
            ]
    x.source.url @?= Just "https://example.org/food.parquet"
