{- | Properties of the parsers and of the dish chains built from any
answers, and a golden test of one real page with fixed answers. They run
against the real registries in @data/@, the ones extraction asks the models
to choose from.
-}
module Main (main) where

import Babel.Check (sensitivityChecks)
import Babel.Load (World, loadWorld)
import Babel.Model (Amount (..), End (..), FormId (..), Ingredient (..), IngredientId (..), IsId (..), Leaf (..), Outcome (..), Output (..), Recipe (..), Select (..), Sensitivity (..), SourceRef (..), TechniqueId (..), UnitId (..), leaves)
import Babel.Preparation (Preparation (..), determinesOutput, preparationOf)
import Data.Aeson qualified as Aeson
import Data.Char (isNumber)
import Data.List.NonEmpty qualified as NE
import Data.Text qualified as T
import Decisions.Request (Judged (..), Request (..), answer, questionNames)
import Hedgehog (Gen, Property, PropertyT, annotateShow, assert, failure, forAll, property, (===))
import Hedgehog.Gen qualified as Gen
import Hedgehog.Range qualified as Range
import Test.Tasty (defaultMain, testGroup)
import Test.Tasty.HUnit (assertFailure, testCase, (@?=))
import Test.Tasty.Hedgehog (testPropertyNamed)
import Wikibooks.Dump (Title, article, cookbookFromPages, mkTitle, readCookbook, titleText)
import Wikibooks.Emit (Dish (..), Env (..), PreparedStep (..), dish, ingredientFor, lexicon, mkEnv, preparedId)
import Wikibooks.Page (Line (..), RecipePage (..), Step (..), recipePage)
import Wikibooks.Quantity (IngredientLine (..), Lexicon (..), ingredientLine, unitAliases)
import Wikibooks.Questions (FirstUse (..), PageAnswers (..), Place (..), Prep (..), StepAnswers (..), StepMedium (..), Vocabulary (..), pageRequest, prepRequest)
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
                , prop "each step takes in the output of the one before it, and each prepared line feeds one step" (chainConnected env)
                , prop "every ingredient line with a first step is taken in exactly once" (takenOnce env)
                , prop "a line prepared on its own feeds the step its first step names" (prepFeedsFirstStep env)
                , prop "a step nothing heats is bounded by the band of where it sits" (ambientBands env)
                , prop "no two questions about a page share a name" (namesUnique env)
                , prop "a step that is a preparation makes the dataset's one item for it" (preparationsShared env)
                ]
            , testGroup "real ingredient lines" [testCase (toString l) (realLine l expected) | (l, expected) <- realLines]
            , testCase "Pancakes (North American) becomes six steps" (pancakes world)
            , testCase "julienning potatoes for fries makes the reviewed potato_shreds" (frenchFries env)
            , testCase "potatoes julienned on their own make the reviewed potato_shreds for their step" (julienned env)
            , testCase "a dish that is one preparation names the item for it" (sashimi env)
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
genAnswered :: Env -> Gen (RecipePage, PageAnswers FirstUse)
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
                <*> judged (Gen.choice [Ambient <$> Gen.enumBounded, Medium <$> Gen.element (map fst v.media)])
                <*> judged (Gen.element (fst <$> v.forms))
    let prep = Prep <$> Gen.element (fst <$> v.preparations) <*> judged (Gen.element (fst <$> v.forms))
        firstUse = FirstUse <$> Gen.element (toList ((.number) <$> steps)) <*> judged (Gen.maybe prep)
    firstUses <- forM page.ingredients $ \l -> (l,) <$> judged (Gen.maybe firstUse)
    single <- judged (pure True)
    pure (page, PageAnswers{singleDish = single, steps = stepAnswers, ingredients = firstUses})
  where
    foods = [mkTitle "Milk", mkTitle "Flour"] <> [mkTitle ("Food " <> show n) | n <- [1 :: Int ..]]
    lineFor food = do
        amount <- Amount <$> Gen.double (Range.linearFrac 0.1 500) <*> Gen.element (ordNub (map snd unitAliases))
        isOptional <- Gen.bool
        pure (Line (titleText food) (IngredientLine food amount isOptional [] Nothing))
    judged g = Judged <$> g <*> Gen.double (Range.linearFrac 0 1)

-- | Check the dish built from generated answers, when one can be built.
forDish :: Env -> (PageAnswers FirstUse -> Dish -> PropertyT IO ()) -> Property
forDish env check = property $ do
    (page, answers) <- forAll (genAnswered env)
    either (const (pure ())) (check answers) (dish env page answers)

-- | What a recipe makes.
outputsOf :: Recipe -> [Leaf]
outputsOf r = [Leaf o.ingredient o.amount | Outputs os <- [r.outputs], o <- toList os]

-- | The recipes for the page's steps, and those for lines prepared on their own.
stepsAndPreps :: Dish -> ([(Int, Recipe)], [Recipe])
stepsAndPreps d = partitionEithers [maybe (Right r) (\s -> Left (s.step, r)) r.sourceRef | r <- toList d.recipes]

-- | The numbers of the steps that take in an ingredient.
consumers :: [(Int, Recipe)] -> IngredientId -> [Int]
consumers steps i = [n | (n, r) <- steps, i `elem` map (.ingredient) (leaves r.inputs)]

chainConnected :: Env -> Property
chainConnected env = forDish env $ \_ d -> do
    let (steps, preps) = stepsAndPreps d
    forM_ (zip steps (drop 1 steps)) $ \((_, made), (_, next)) -> take 1 (rights next.inputs.choices) === outputsOf made
    map fst steps === sort (ordNub (map fst steps))
    forM_ preps $ \p -> forM_ (outputsOf p) $ \o -> length (consumers steps o.ingredient) === 1

takenOnce :: Env -> Property
takenOnce env = forDish env $ \answers d -> do
    let chained = map (.ingredient) (concatMap outputsOf (toList d.recipes))
        taken = [l.ingredient | r <- toList d.recipes, l <- leaves r.inputs, l.ingredient `notElem` chained]
        assigned = [i | (l, j) <- toList answers.ingredients, isJust j.value, Just i <- [ingredientFor env l.parsed.ingredient]]
    sort taken === sort assigned

{- | A line goes in at the first step at or after the one Jev names that
makes a recipe, or at the last when none does; prepared on its own, it
goes in as what its preparation makes.
-}
prepFeedsFirstStep :: Env -> Property
prepFeedsFirstStep env = forDish env $ \answers d -> do
    let (steps, preps) = stepsAndPreps d
    forM_ answers.ingredients $ \(l, j) -> case j.value of
        Just (FirstUse n Judged{value = Just _}) -> do
            raw <- maybe failure pure (ingredientFor env l.parsed.ingredient)
            let numbers = map fst steps
                expected = find (>= n) numbers <|> viaNonEmpty last numbers
            case [p | p <- preps, map (.ingredient) (leaves p.inputs) == [raw]] of
                [p] -> map (consumers steps . (.ingredient)) (outputsOf p) === [toList expected]
                found -> annotateShow found >> failure
        _ -> pure ()

-- | Where an unheated step sits sets its reference temperature and its ceiling, and babel accepts both.
ambientBands :: Env -> Property
ambientBands env = forDish env $ \answers d -> do
    let (steps, preps) = stepsAndPreps d
        placeOf n = [place | (s, a) <- toList answers.steps, s.number == n, Ambient place <- [a.medium.value]]
        expected = \case
            Room -> (20, Nothing)
            Fridge -> (4, Just 8)
            Freezer -> (-18, Just 0)
        sensitivity r = [s | ByTime _ (Just s) <- [r.end]]
    forM_ steps $ \(n, r) -> map (\s -> (s.refTempC, s.maxTempC)) (sensitivity r) === map expected (placeOf n)
    forM_ preps $ \r -> map (\s -> (s.refTempC, s.maxTempC)) (sensitivity r) === [expected Room]
    forM_ (concatMap sensitivity (toList d.recipes)) $ \s -> sensitivityChecks "" s === []

{- | Pages agree on what a preparation makes, so the dataset has one item
for it. A preparation of the page's own intermediate is the page's alone.
-}
preparationsShared :: Env -> Property
preparationsShared env = forDish env $ \_ d -> do
    let own = map (.id) d.made
    forM_ (toList d.recipes) $ \r -> case preparationOf (determinesOutput env.world) r of
        Just (p, out) | p.ingredient `notElem` own -> out === preparedId env p
        _ -> pure ()

namesUnique :: Env -> Property
namesUnique env = property $ do
    (page, answers) <- forAll (genAnswered env)
    let names = questionNames (pageRequest env.vocabulary page).ask <> questionNames (prepRequest env.vocabulary page ((.step) <$> answers)).ask
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
    firstSteps <- either (assertFailure . toString) pure (answer (pageRequest env.vocabulary page) [answers])
    decoded <- either (assertFailure . toString) pure (answer (prepRequest env.vocabulary page firstSteps) [answers])
    d <- either (assertFailure . toString) pure (dish env page decoded)
    map (idText . (.id)) (toList d.recipes) @?= ["pancakes_north_american.step_" <> show n | n <- [1 .. 6 :: Int]]
    let mixDry = (head d.recipes).inputs.choices
    sort [idText l.ingredient | Right l <- mixDry] @?= ["baking_powder", "salt", "wheat_flour"]
    [idText l.ingredient | Left optionalSugar <- mixDry, Right l <- optionalSugar.choices] @?= ["white_sugar"]

-- * Preparations

{- | A page of one-line steps, each answered with the given technique, all
certain; each line is used at its step, prepared by its technique if any.
-}
answeredPage :: Text -> [(Text, Maybe (Int, Maybe Text))] -> [(Text, Text)] -> (RecipePage, PageAnswers FirstUse)
answeredPage title lines steps = (page, answers)
  where
    page =
        RecipePage
            { title = mkTitle title
            , revid = 1
            , slug = T.toLower (T.replace " " "_" title)
            , intro = ""
            , ingredients = fromList' [Line food (IngredientLine (mkTitle food) (Amount 100 (UnitId "g")) False [] Nothing) | (food, _) <- lines]
            , steps = fromList' (zipWith Step [1 ..] (map snd steps))
            }
    answers =
        PageAnswers
            { singleDish = sure True
            , steps = NE.zip page.steps (fromList' [StepAnswers (sure True) (sure (TechniqueId t)) (sure (Ambient Room)) (sure (FormId "piece")) | (t, _) <- steps])
            , ingredients = NE.zip page.ingredients (fromList' [sure (firstUse <$> use) | (_, use) <- lines])
            }
    firstUse (n, technique) = FirstUse n (sure ((\t -> Prep (TechniqueId t) (sure (FormId "shred"))) <$> technique))
    sure a = Judged a 1
    fromList' = fromMaybe (error "answeredPage needs lines and steps") . nonEmpty

frenchFries :: Env -> IO ()
frenchFries env = do
    let (page, answers) = answeredPage "French fries" [("Potato", Just (1, Nothing)), ("Vegetable oil", Just (2, Nothing))] [("julienne", "Cut the potatoes into strips."), ("fry", "Fry them in the oil.")]
    d <- either (assertFailure . toString) pure (dish env page answers)
    [idText o.ingredient | r <- toList d.recipes, Outputs os <- [r.outputs], o <- toList os] @?= ["potato_shreds", "french_fries"]
    map (idText . (.makes)) d.prepared @?= ["potato_shreds"]

julienned :: Env -> IO ()
julienned env = do
    let (page, answers) = answeredPage "French fries" [("Potato", Just (1, Just "julienne")), ("Vegetable oil", Just (1, Nothing))] [("fry", "Fry the potato strips in the oil.")]
    d <- either (assertFailure . toString) pure (dish env page answers)
    [(idText r.id, map (idText . (.ingredient)) (leaves r.inputs)) | r <- toList d.recipes]
        @?= [("french_fries.step_1.prep_1", ["potato"]), ("french_fries.step_1", ["potato_shreds", "vegetable_oil"])]
    map (idText . (.makes)) d.prepared @?= ["potato_shreds"]

sashimi :: Env -> IO ()
sashimi env = do
    let (page, answers) = answeredPage "Sashimi" [("Fish", Just (1, Nothing))] [("cut", "Slice the fish thinly.")]
    d <- either (assertFailure . toString) pure (dish env page answers)
    map (.id) d.made @?= []
    [(s.makes, s.dishName) | s <- d.prepared] @?= [(IngredientId "fish_cut", Just "sashimi")]
