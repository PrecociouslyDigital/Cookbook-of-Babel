{- | Properties of the parsers and of the dish chains built from any
answers, and a golden test of one real page with fixed answers. They run
against the real registries in @data/@, the ones extraction asks the models
to choose from.
-}
module Main (main) where

import Babel.Load (World, loadWorld)
import Babel.Model (Amount (..), IsId (..), Leaf (..), Outcome (..), Output (..), Recipe (..), Select (..), SourceRef (..))
import Data.Aeson qualified as Aeson
import Data.Aeson.Key qualified as Key
import Data.Char (isNumber)
import Data.List.NonEmpty qualified as NE
import Data.Map.Strict qualified as Map
import Data.Text qualified as T
import Hedgehog (Gen, Property, PropertyT, annotateShow, assert, failure, forAll, property, (===))
import Hedgehog.Gen qualified as Gen
import Hedgehog.Range qualified as Range
import Test.Tasty (defaultMain, testGroup)
import Test.Tasty.HUnit (assertFailure, testCase, (@?=))
import Test.Tasty.Hedgehog (testPropertyNamed)
import Wikibooks.Dump (Title, article, cookbookFromPages, mkTitle, readCookbook, titleText)
import Wikibooks.Emit (Dish (..), Env (..), dish, ingredientFor, lexicon, mkEnv)
import Wikibooks.Page (Line (..), RecipePage (..), Step (..), recipePage)
import Wikibooks.Quantity (IngredientLine (..), Lexicon (..), ingredientLine, unitAliases)
import Wikibooks.Questions (IngredientAnswers, Judged (..), PageAnswers (..), Part (..), Request (..), StepAnswers (..), Vocabulary (..), answer, ingredientRequest, pageRequest, questionNames, requestParts)
import Wikibooks.Wikitext (inlines)

main :: IO ()
main = do
    (_, world) <- loadWorld False "../.."
    env <- either (fail . toString) pure (mkEnv (cookbookFromPages []) world)
    defaultMain $
        testGroup
            "babel-wikibooks"
            [ testGroup
                "properties"
                [ prop "an ingredient line reads back the amount and food it was written with" lineRoundTrip
                , prop "a food named by a line's words has no numbers in its name" namesHaveNoNumbers
                , prop "each recipe takes in the output of the one before it" (chainConnected env)
                , prop "every ingredient line with a first step is taken in exactly once" (takenOnce env)
                , prop "request keys do not depend on the order of the state's keys" (keysIgnoreOrder env)
                , prop "no two questions in a page request share a name" (namesUnique env)
                ]
            , testGroup "real ingredient lines" [testCase (toString l) (realLine l expected) | (l, expected) <- realLines]
            , testCase "Pancakes (North American) becomes six steps" (pancakes world)
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

-- | Lines with no food link: amounts, units, sizes, notes and words.
namesHaveNoNumbers :: Property
namesHaveNoNumbers = property $ do
    ws <-
        forAll . Gen.list (Range.linear 1 8) $
            Gen.choice
                [ fst <$> genQty
                , fst <$> Gen.element unitAliases
                , Gen.element ["(about", "100°F)", "9-inch", "No.", "2", "or", "to", "taste", "of", "your", "choice"]
                , Gen.text (Range.linear 1 8) Gen.alpha
                ]
    for_ (ingredientLine pageLexicon (inlines (T.unwords ws))) $ \parsed ->
        assert (not (T.any isNumber (titleText parsed.ingredient)))

-- | Links to these pages lead to units or techniques; any other page is a food.
pageLexicon :: Lexicon
pageLexicon = Lexicon{isFood = (`notElem` notFood), isTool = const False, weighs = const False}
  where
    notFood = map mkTitle ["Cup", "Liter", "Milliliter", "Ounce", "Tablespoon", "Kilogram", "Pound", "Inch", "Batter"]

-- | Lines from the Cookbook, and the food each names, if any.
realLines :: [(Text, Maybe Text)]
realLines =
    [ ("1 [[Cookbook:Cup|cup]] lukewarm (about 100°F or 38°C) water", Just "Water")
    , ("About 3.5 [[Cookbook:Liter|L]] water", Just "Water")
    , ("250 [[Cookbook:Milliliter|ml]] hot/boiling water, or more as needed", Just "Water")
    , ("Enough cold water to form a [[Cookbook:Batter|batter]]", Just "Water")
    , ("1 leveled [[Cookbook:Tablespoon|tbsp]] salt", Just "Salt")
    , ("Shredded cheese of your choice", Just "Cheese")
    , ("2 pkgs. (4 serving size) vanilla instant pudding", Just "Vanilla instant pudding")
    , ("1 container (4.5 [[Cookbook:Ounce|oz]]) hickory smoke-flavored albacore tuna", Just "Hickory smoke-flavored albacore tuna")
    , ("1 [[Cookbook:Cup|cup]] [[Cookbook:Grating|grated]] [[Cookbook:Monterey Jack Cheese|Monterey Jack]] cheese", Just "Monterey Jack Cheese")
    , ("[[Cookbook:Ingredients|1 kg beef (thinly sliced)]]", Just "Beef")
    , ("4 [[Cookbook:Kilogram|kg]] (8.8 [[Cookbook:Pound|lb]]) fresh {{cb|apple}}s", Just "Apple")
    , ("~1 cup (250 ml) warm water", Just "Water")
    , ("1 ea. 4-bone standing rib roast, prime grade", Just "Standing rib roast")
    , ("2 cubes of mushroom base (bouillon) plus 1 cup of noodles", Just "Cubes of mushroom base")
    , ("The juice of ½–1 lemon, or to taste", Just "Juice of lemon")
    , ("1 fully-baked 9-[[Cookbook:Inch|inch]] graham cracker or pastry pie shell", Just "Fully-baked graham cracker")
    ]

realLine :: Text -> Maybe Text -> IO ()
realLine l expected = ((.ingredient) <$> ingredientLine pageLexicon (inlines l)) @?= (mkTitle <$> expected)

-- * Dish chains

{- | A recipe page and answers about it, both arbitrary. Each line names a
different food, so the lines a dish takes in can be told apart.
-}
genAnswered :: Env -> Gen (RecipePage, PageAnswers)
genAnswered env = do
    steps <- NE.zipWith Step (1 :| [2 ..]) <$> Gen.nonEmpty (Range.linear 1 8) (Gen.text (Range.linear 1 20) Gen.alpha)
    count <- Gen.int (Range.linear 1 10)
    first' <- lineFor (mkTitle "Salt")
    rest <- forM (take (count - 1) foods) lineFor
    let page = RecipePage{title = mkTitle "Generated Dish", revid = 1, slug = "generated_dish", intro = "", ingredients = first' :| rest, steps}
        v = env.vocabulary
    stepAnswers <- forM steps $ \s ->
        fmap (s,) $
            StepAnswers
                <$> judged Gen.bool
                <*> judged (Gen.element (fst <$> v.techniques))
                <*> judged (Gen.maybe (Gen.element (map fst v.media)))
                <*> judged (Gen.element (fst <$> v.forms))
    firstSteps <- forM page.ingredients $ \l -> (l,) <$> judged (Gen.maybe (Gen.element (toList ((.number) <$> steps))))
    single <- judged (pure True)
    pure (page, PageAnswers{singleDish = single, steps = stepAnswers, ingredients = firstSteps})
  where
    foods = [mkTitle "Milk", mkTitle "Flour"] <> [mkTitle ("Food " <> show n) | n <- [1 :: Int ..]]
    lineFor food = do
        amount <- Amount <$> Gen.double (Range.linearFrac 0.1 500) <*> Gen.element (ordNub (map snd unitAliases))
        isOptional <- Gen.bool
        pure (Line (titleText food) (IngredientLine food amount isOptional [] Nothing))
    judged g = Judged <$> g <*> Gen.double (Range.linearFrac 0 1)

-- | Check the dish built from generated answers, when one can be built.
forDish :: Env -> (PageAnswers -> Dish -> PropertyT IO ()) -> Property
forDish env check = property $ do
    (page, answers) <- forAll (genAnswered env)
    either (const (pure ())) (check answers) (dish env page answers)

-- | What each recipe makes.
outputsOf :: Dish -> [Leaf]
outputsOf d = [Leaf o.ingredient o.amount | r <- toList d.recipes, Outputs os <- [r.outputs], o <- toList os]

chainConnected :: Env -> Property
chainConnected env = forDish env $ \_ d -> do
    let rs = toList d.recipes
    forM_ (zip (outputsOf d) (drop 1 rs)) $ \(made, next) -> take 1 (rights next.inputs.choices) === [made]
    let numbers = mapMaybe (fmap (.step) . (.sourceRef)) rs
    numbers === sort (ordNub numbers)

takenOnce :: Env -> Property
takenOnce env = forDish env $ \answers d -> do
    let leaves s = rights s.choices <> concatMap leaves (lefts s.choices)
        chained = map (.ingredient) (outputsOf d)
        taken = [l.ingredient | r <- toList d.recipes, l <- leaves r.inputs, l.ingredient `notElem` chained]
        assigned = [i | (l, j) <- toList answers.ingredients, isJust j.value, Just i <- [ingredientFor env l.parsed.ingredient]]
    sort taken === sort assigned

keysIgnoreOrder :: Env -> Property
keysIgnoreOrder env = property $ do
    pairs <- forAll (Map.toList . Map.fromList <$> Gen.list (Range.linear 1 10) ((,) <$> Gen.text (Range.linear 1 5) Gen.alpha <*> Gen.text (Range.linear 0 10) Gen.alpha))
    shuffled <- forAll (Gen.shuffle pairs)
    let keys ps = map (.key) (requestParts ((ingredientRequest env.vocabulary "food" "description"){state = Aeson.object [Key.fromText k Aeson..= x | (k, x) <- ps]} :: Request IngredientAnswers))
    keys pairs === keys shuffled

namesUnique :: Env -> Property
namesUnique env = property $ do
    (page, _) <- forAll (genAnswered env)
    let names = questionNames (pageRequest env.vocabulary page).ask
    length (ordNub names) === length names

-- * Golden

{- | The real page, with answers mammoth once gave, matches how the
reviewed pancake recipes read it.
-}
pancakes :: World -> IO ()
pancakes world = do
    cookbook <- withFile "test/fixtures/pancakes.xml" ReadMode readCookbook
    env <- either (assertFailure . toString) pure (mkEnv cookbook world)
    let title = mkTitle "Pancakes (North American)"
    (_, revid, wikitext) <- maybe (assertFailure "the fixture has no pancakes page") pure (article cookbook title)
    page <- either (assertFailure . toString) pure (recipePage (lexicon env) title revid wikitext)
    answers <- either assertFailure pure =<< Aeson.eitherDecodeFileStrict "test/fixtures/pancakes.answers.json"
    decoded <- either (assertFailure . toString) pure (answer (pageRequest env.vocabulary page) [answers])
    d <- either (assertFailure . toString) pure (dish env page decoded)
    mapMaybe (fmap (.step) . (.sourceRef)) (toList d.recipes) @?= [1 .. 6]
    let mixDry = (head d.recipes).inputs.choices
    sort [idText l.ingredient | Right l <- mixDry] @?= ["baking_powder", "salt", "wheat_flour"]
    [idText l.ingredient | Left optionalSugar <- mixDry, Right l <- optionalSugar.choices] @?= ["white_sugar"]
