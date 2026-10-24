-- | Properties of the parsers.
module Main (main) where

import Babel.Model (Amount (..))
import Data.Text qualified as T
import Hedgehog (Gen, Property, annotateShow, assert, failure, forAll, property, (===))
import Hedgehog.Gen qualified as Gen
import Hedgehog.Range qualified as Range
import Test.Tasty (defaultMain, testGroup)
import Test.Tasty.Hedgehog (testPropertyNamed)
import Wikibooks.Dump (Title, mkTitle, titleText)
import Wikibooks.Quantity (IngredientLine (..), Lexicon (..), ingredientLine, unitAliases)
import Wikibooks.Wikitext (inlines)

main :: IO ()
main =
    defaultMain $
        testGroup
            "babel-wikibooks"
            [ testGroup
                "properties"
                [prop "an ingredient line reads back the amount and food it was written with" lineRoundTrip]
            ]
  where
    prop name = testPropertyNamed name (fromString name)

-- * Ingredient lines

-- | A quantity as a recipe might write it, and its value.
genQty :: Gen (Text, Double)
genQty =
    Gen.choice
        [ (\n -> (show n, fromIntegral n)) <$> Gen.int (Range.linear 1 999)
        , (\n d -> (show n <> "." <> show d, fromIntegral n + fromIntegral d / 10)) <$> Gen.int (Range.linear 0 99) <*> Gen.int (Range.linear 1 9)
        , do
            d <- Gen.int (Range.linear 2 16)
            n <- Gen.int (Range.linear 1 (d - 1))
            pure (show n <> "/" <> show d, fromIntegral n / fromIntegral d)
        , do
            (c, v) <- Gen.element [('½', 0.5), ('¼', 0.25), ('¾', 0.75), ('⅓', 1 / 3), ('⅛', 0.125)]
            whole <- Gen.int (Range.linear 0 9)
            sep <- Gen.element ["", " "]
            pure (if whole == 0 then one c else show whole <> sep <> one c, fromIntegral whole + v)
        ]

-- | A food's page title: one to three words.
genTitle :: Gen Title
genTitle = mkTitle . T.unwords <$> Gen.list (Range.linear 1 3) (Gen.text (Range.linear 3 8) Gen.lower)

lineRoundTrip :: Property
lineRoundTrip = property $ do
    (qtyText, qty) <- forAll genQty
    (alias, unit) <- forAll (Gen.element unitAliases)
    food <- forAll genTitle
    let line = qtyText <> " " <> alias <> " [[Cookbook:" <> titleText food <> "|" <> T.toLower (titleText food) <> "]], chopped"
    annotateShow line
    case ingredientLine Lexicon{isFood = (== food), isTool = const False, weighs = const False} (inlines line) of
        Nothing -> failure
        Just parsed -> do
            parsed.ingredient === food
            parsed.amount.unit === unit
            assert (abs (parsed.amount.qty - qty) < 1e-9)
