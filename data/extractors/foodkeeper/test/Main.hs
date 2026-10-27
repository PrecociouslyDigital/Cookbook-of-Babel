{- | Properties of the fit, the matching and the extraction, against a small
FoodKeeper file in the real shape and the real registries in @data/@.
Decisions are hand-authored answers in the API's shape.
-}
module Main (main) where

import Babel.Diagnostic (renderDiagnostic)
import Babel.Layout (Layer (..))
import Babel.Load (Located (..), World (..), loadWorld)
import Babel.Model (Amount (..), Constants (..), End (..), FormId (..), IngredientId (..), IsId (..), Recipe (..), RecipeId (..), Unit (..))
import Babel.Spoilage (Conditions (..), timeAt)
import Babel.Validate (Report (..), validate)
import Data.Aeson (Value (..), object, (.=))
import Data.Aeson qualified as Aeson
import Data.Aeson.Key qualified as Key
import Data.Aeson.KeyMap qualified as KM
import Data.Map.Strict qualified as Map
import Data.Set qualified as Set
import Data.Text qualified as T
import Decisions.Decide (Decided (..), Decisions, decide)
import Decisions.Request (Judged (..), Part (..), Request, requestParts)
import FoodKeeper.Extract (Env (..), Extraction (..), Row (..), Status (..), describeAll, extract, writeLayer)
import FoodKeeper.Kinetics (Fit (..), Spoiling (..), aboveFreezing, freezerC, fridgeC, frozen, pantryC)
import FoodKeeper.Match (Described (..), categoryRequest, productRequest)
import FoodKeeper.Source (CategoryId (..), FoodKeeper (..), FoodProduct (..), Metric (..), ProductId (..), Storage (..), mkWindow, parseFoodKeeper, productsIn, windowSeconds)
import FoodKeeper.Tables (Entry (..), Override (..), Tables (..), readTables)
import Hedgehog (Gen, Property, annotate, annotateShow, assert, evalIO, failure, forAll, property, withTests, (===))
import Hedgehog.Gen qualified as Gen
import Hedgehog.Range qualified as Range
import System.Directory (copyFile, createDirectoryIfMissing, doesDirectoryExist, listDirectory)
import System.FilePath ((</>))
import System.IO.Temp (withSystemTempDirectory)
import Test.Tasty (defaultMain, testGroup)
import Test.Tasty.HUnit (testCase, (@?=))
import Test.Tasty.Hedgehog (testPropertyNamed)

-- | The data root, from this package's folder.
dataRoot :: FilePath
dataRoot = "../.."

main :: IO ()
main = do
    (_, world) <- loadWorld False dataRoot
    constants <- maybe (fail "no constants") pure world.constants
    fk <- either fail pure . parseFoodKeeper =<< readFileLBS "test/fixtures/foodkeeper.json"
    tables <- either fail pure (readTables fk constants fixtureTechniques)
    let env = Env{foodKeeper = fk, tables, overrides = mempty, url = "https://example.org/foodkeeper.json"}
    defaultMain $
        testGroup
            "babel-foodkeeper"
            [ testCase "the fixture reads back its products and windows" (fixtureReads fk)
            , prop "a fit reproduces the windows it was fitted to" (fitReproduces world)
            , prop "an emitted recipe reproduces its windows to three significant figures" (emittedReproduces world env)
            , testPropertyNamed "every emitted recipe validates, and a reviewed recipe replaces it" "validates" (withTests 25 (emittedValidates env))
            , prop "the product question offers exactly the products of the chosen category" (productsOfCategory env)
            , prop "an unsure or empty answer falls back to an ancestor's match" (fallsBack env)
            , prop "changing one ingredient asks again only the questions that describe it" (reaskIsLocal env)
            , prop "extraction does not depend on the order of the ingredients" (orderIndependent env)
            ]
  where
    prop name = testPropertyNamed name (fromString name)

fixtureTechniques :: LByteString
fixtureTechniques =
    Aeson.encode $
        object
            [ "categories" .= object ["Baked Goods: Bakery" .= ("mold" :: Text), "Dairy Products & Eggs" .= ("sour" :: Text), "Produce: Fresh Vegetables" .= ("spoil" :: Text)]
            , "products" .= [object ["product" .= (1 :: Int), "name" .= ("Butter" :: Text), "technique" .= ("rancid" :: Text)]]
            , "frozen" .= ("freezer_burn" :: Text)
            ]

fixtureReads :: FoodKeeper -> IO ()
fixtureReads fk = do
    Map.keys fk.products @?= map ProductId [1, 21, 27, 195, 297, 298]
    let numbered n = fromMaybe (error "no product") (Map.lookup (ProductId n) fk.products)
    (numbered 27).subtitle @?= Just "plain or flavored"
    (numbered 27).keywords @?= ["Milk", "plain", "flavored"]
    (numbered 27).storage @?= Storage Nothing Nothing (mkWindow (Just 3) (Just 3) Months)
    (numbered 1).storage.fridge @?= mkWindow (Just 1) (Just 2) Months
    (numbered 21).storage.freezer @?= Nothing
    (numbered 298).subtitle @?= Nothing
    (numbered 298).storage.pantry @?= mkWindow (Just 3) (Just 3) Months

-- * Kinetics

genStorage :: Gen Storage
genStorage = Storage <$> Gen.maybe window <*> Gen.maybe window <*> Gen.maybe window
  where
    window = do
        lo <- Gen.double (Range.linearFrac 0.5 30)
        hi <- Gen.double (Range.linearFrac lo 60)
        metric <- Gen.enumBounded
        maybe Gen.discard pure (mkWindow (Just lo) (Just hi) metric)

genSpoiling :: World -> Gen Spoiling
genSpoiling w = do
    c <- maybe Gen.discard pure w.constants
    (t, k) <- Gen.element (Map.toList (constantsKinetics c))
    pure (Spoiling t k)
  where
    constantsKinetics c = c.spoilageKinetics

-- | Two durations agree to a relative tolerance.
close :: Double -> Double -> Double -> Bool
close tolerance a b = abs (a - b) <= tolerance * max a b

-- | The time a step takes at a temperature, in seconds, if it happens there.
secondsAt :: Double -> Fit -> Maybe Double
secondsAt c fit = timeAt (Conditions c Nothing) fit.sensitivity fit.seconds

fitReproduces :: World -> Property
fitReproduces w = property $ do
    storage <- forAll genStorage
    s <- forAll (genSpoiling w)
    for_ (aboveFreezing s storage) $ \fit -> do
        annotateShow fit
        for_ [(pantryC, storage.pantry), (fridgeC, storage.fridge)] $ \(c, window) ->
            for_ window $ \win -> assert (maybe False (close 1e-9 (windowSeconds win)) (secondsAt c fit))
        secondsAt (-1) fit === Nothing
    for_ (frozen s storage) $ \fit -> do
        for_ storage.freezer $ \win -> assert (maybe False (close 1e-9 (windowSeconds win)) (secondsAt freezerC fit))
        secondsAt 1 fit === Nothing
    isJust (aboveFreezing s storage) === (isJust storage.pantry || isJust storage.fridge)

-- | The fixture, with one product's storage replaced.
withStorage :: Env -> ProductId -> Storage -> Env
withStorage env p storage = env{tables = Tables{catalogue = Map.adjust restore p env.tables.catalogue, frozen = env.tables.frozen}}
  where
    restore (Entry (FoodProduct i c n s k _) spoiling) = Entry (FoodProduct i c n s k storage) spoiling

emittedReproduces :: World -> Env -> Property
emittedReproduces w env0 = property $ do
    storage <- forAll genStorage
    let bread = ProductId 195
        env = (withStorage env0 bread storage){overrides = Map.singleton (IngredientId "bread") (MatchTo bread)}
        x = extract env mempty [described "bread" []]
        seconds r = case r.end of
            ByTime d (Just s) | Just u <- Map.lookup d.unit w.units -> Just (s, d.qty * u.toBase)
            _ -> Nothing
        at c r = seconds r >>= uncurry (timeAt (Conditions c Nothing))
        recipe suffix = find (\r -> idText r.id == "bread." <> suffix) x.recipes
    annotateShow x.recipes
    for_ [(pantryC, storage.pantry, "mold"), (fridgeC, storage.fridge, "mold"), (freezerC, storage.freezer, "freezer_burn")] $ \(c, window, suffix) ->
        for_ window $ \win -> case recipe suffix >>= at c of
            Just t -> assert (close 0.02 (windowSeconds win) t)
            Nothing -> annotate (toString suffix) >> failure

-- * Validation

{- | A data root with the registries and the original reviewed layer, which
has the ingredients the fixture's products are matched to and the reviewed
bread spoilage recipes. It refers to extracted ingredients it lacks, so it
has errors of its own.
-}
slimDataRoot :: FilePath -> IO ()
slimDataRoot to = do
    forM_ ["dataset.json", "renames.json"] $ \f -> copyFile (dataRoot </> f) (to </> f)
    copyTree (dataRoot </> "registries") (to </> "registries")
    copyTree (dataRoot </> "layers/reviewed/LicenseRef-original") (to </> "layers/reviewed/LicenseRef-original")
  where
    copyTree from dest = do
        createDirectoryIfMissing True dest
        names <- listDirectory from
        forM_ names $ \n -> do
            isDir <- doesDirectoryExist (from </> n)
            if isDir then copyTree (from </> n) (dest </> n) else copyFile (from </> n) (dest </> n)

emittedValidates :: Env -> Property
emittedValidates env0 = property $ do
    storages <- forAll (traverse (const genStorage) others)
    picks <- forAll (Gen.list (Range.singleton (length ingredients)) (Gen.element (Map.keys env0.tables.catalogue)))
    let env1 = foldl' (\e (p, s) -> withStorage e p s) env0 (zip others storages)
        env = env1{overrides = Map.fromList (zip ingredients (map MatchTo (ProductId 195 : drop 1 picks)))}
    (baseline, report) <- evalIO . withSystemTempDirectory "foodkeeper" $ \root -> do
        slimDataRoot root
        before <- validate False root
        writeLayer root (extract env mempty (describeAll before.world mempty))
        (before.diagnostics,) <$> validate False root
    let problems = filter (`notElem` baseline) report.diagnostics
    annotate (toString (T.unlines (map renderDiagnostic problems)))
    assert (null problems)
    let breadMold = Map.lookup (RecipeId "bread.mold") report.world.recipes
    ((.layer) <$> breadMold) === Just Reviewed
  where
    ingredients = map IngredientId ["bread", "egg", "milk", "potato", "wheat_flour", "vegetable_oil", "russet_potato"]
    -- Bread keeps the fixture's bread windows, so bread.mold is always made, and overlaid.
    others = filter (/= ProductId 195) (Map.keys env0.tables.catalogue)

-- * Matching

described :: Text -> [Text] -> Described
described i parents = Described{id = IngredientId i, label = T.replace "_" " " i, aliases = [], form = FormId "solid", parents = [(IngredientId p, T.replace "_" " " p) | p <- parents], openFoodFacts = Nothing}

-- | The names of a part's options, by question.
partOptions :: Part -> [(Text, [Text])]
partOptions p = case Aeson.decode p.body of
    Just (Object b) | Just (Object qs) <- KM.lookup "questions" b -> [(Key.toText k, criteria q) | (k, q) <- KM.toList qs]
    _ -> []
  where
    criteria = \case
        Object q | Just (Object c) <- KM.lookup "criteria" q -> map Key.toText (KM.keys c)
        _ -> []

-- | Decisions answering a one-question request with this option, this sure.
answering :: Request a -> Text -> Double -> Decisions
answering r option p =
    Map.fromList
        [ (part.key, object [Key.fromText q .= object ["choice" .= option, "probabilities" .= object [Key.fromText option .= p]] | (q, _) <- partOptions part])
        | part <- requestParts r
        ]

-- | Each option of a one-question request, and the value choosing it decodes to.
optionValues :: Request (Judged a) -> [(Text, a)]
optionValues r =
    [ (o, j.value)
    | part <- requestParts r
    , (_, os) <- partOptions part
    , o <- os
    , Answered j <- [decide (answering r o 0.9) r]
    ]

-- | Decisions picking the option that decodes to this value.
choosing :: (Eq a) => Request (Judged a) -> a -> Double -> Decisions
choosing r v p = foldMap (\(o, _) -> answering r o p) (find ((== v) . snd) (optionValues r))

genCategory :: Env -> Gen CategoryId
genCategory env = Gen.element (Map.keys env.foodKeeper.categories)

productsOfCategory :: Env -> Property
productsOfCategory env = property $ do
    c <- forAll (genCategory env)
    let d = described "parsnip" []
        afterCategory = extract env (choosing (categoryRequest env.foodKeeper d) (Just c) 0.9) [d]
        asked = productRequest env.foodKeeper c d
    Map.keys afterCategory.pending === map (.key) (requestParts asked)
    sort (map snd (optionValues asked)) === sort (Nothing : [Just p.id | p <- productsIn env.foodKeeper c])

-- | Answers that leave an ingredient without a sure product of its own.
genUnsure :: Env -> Described -> Gen Decisions
genUnsure env d =
    Gen.choice
        [ pure (choosing byCategory Nothing 0.95)
        , choosing byCategory (Just veg) <$> Gen.double (Range.linearFrac 0.05 0.59)
        , pure (sureCategory <> choosing (productRequest env.foodKeeper veg d) Nothing 0.9)
        , (sureCategory <>) . choosing (productRequest env.foodKeeper veg d) (Just (ProductId 298)) <$> Gen.double (Range.linearFrac 0.05 0.59)
        ]
  where
    byCategory = categoryRequest env.foodKeeper d
    sureCategory = choosing byCategory (Just veg) 0.9
    veg = CategoryId 19

fallsBack :: Env -> Property
fallsBack env = property $ do
    let potato = described "potato" ["tuber"]
        tuber = described "tuber" []
        russet = described "russet_potato" ["potato"]
        sureOf d p = choosing (categoryRequest env.foodKeeper d) (Just (CategoryId 19)) 0.9 <> choosing (productRequest env.foodKeeper (CategoryId 19) d) (Just p) 0.9
    viaParent <- forAll Gen.bool
    unsure <- forAll (genUnsure env russet)
    potatoUnsure <- forAll (genUnsure env potato)
    let decisions = unsure <> sureOf tuber (ProductId 298) <> if viaParent then sureOf potato (ProductId 297) else potatoUnsure
        x = extract env decisions [potato, tuber, russet]
        row = find (\r -> r.ingredient == russet.id) x.report
    annotateShow x.report
    ((.status) <$> row) === Just Emitted
    ((.product) =<< row) === Just (if viaParent then "Potatoes" else "Garlic")
    assert (maybe False (T.isPrefixOf ("inherited from " <> if viaParent then "potato" else "tuber") . (.detail)) row)

-- * Requests

-- | Ingredients with distinct labels, each is-a some of the ones before it.
genIngredients :: Gen [Described]
genIngredients = do
    labels <- Set.toList <$> Gen.set (Range.linear 1 8) (Gen.text (Range.linear 3 10) Gen.lower)
    forM (zip [1 :: Int ..] labels) $ \(n, label) -> do
        parents <- Gen.subsequence (take (n - 1) (zip [1 :: Int ..] labels))
        aliases <- Gen.list (Range.linear 0 2) (Gen.text (Range.linear 3 8) Gen.lower)
        form <- Gen.element (map FormId ["solid", "liquid", "whole"])
        pure Described{id = ingredientId n, label, aliases, form, parents = [(ingredientId m, l) | (m, l) <- parents], openFoodFacts = Nothing}
  where
    ingredientId n = IngredientId ("i" <> show n)

-- | Answers to some of the ingredients' category and product questions.
genDecisions :: Env -> [Described] -> Gen Decisions
genDecisions env ds = fmap mconcat . forM ds $ \d -> do
    let byCategory = categoryRequest env.foodKeeper d
    category <- Gen.maybe (Gen.element (Nothing : map Just (Map.keys env.foodKeeper.categories)))
    p <- Gen.double (Range.linearFrac 0.3 1)
    case category of
        Nothing -> pure mempty
        Just c -> do
            let byCategoryAnswer = choosing byCategory c p
            chosen <- case c of
                Just cid | p >= 0.6 -> Gen.maybe (Gen.element (Nothing : map (Just . (.id)) (productsIn env.foodKeeper cid)))
                _ -> pure Nothing
            pure (byCategoryAnswer <> foldMap (\(cid, v) -> choosing (productRequest env.foodKeeper cid d) v p) ((,) <$> c <*> chosen))

reaskIsLocal :: Env -> Property
reaskIsLocal env = property $ do
    ds <- forAll genIngredients
    decisions <- forAll (genDecisions env ds)
    changed <- forAll (Gen.element ds)
    which <- forAll (Gen.element ["aliases", "form", "label"])
    let changed' = case which :: Text of
            "aliases" -> changed{aliases = "renamed" : changed.aliases}
            "form" -> changed{form = if changed.form == FormId "paste" then FormId "solid" else FormId "paste"}
            _ -> changed{label = changed.label <> " renamed"}
        relabel d = if d.id == changed.id then changed' else d{parents = [(p, if p == changed.id then changed'.label else l) | (p, l) <- d.parents]}
        ds' = map relabel ds
        keysOf x = x.referenced
        own d = (extract env decisions [d]).referenced
        before = keysOf (extract env decisions ds)
        after = keysOf (extract env decisions ds')
        affected = [(d, d') | (d, d') <- zip ds ds', d /= d']
    Set.difference before after === Set.unions [own d | (d, _) <- affected]
    Set.difference after before === Set.unions [own d' | (_, d') <- affected]
    assert (all (\(d, _) -> d.id == changed.id || changed.id `elem` map fst d.parents) affected)

orderIndependent :: Env -> Property
orderIndependent env = property $ do
    ds <- forAll genIngredients
    decisions <- forAll (genDecisions env ds)
    shuffled <- forAll (Gen.shuffle ds)
    let a = extract env decisions ds
        b = extract env decisions shuffled
    a.recipes === b.recipes
    a.pending === b.pending
    a.referenced === b.referenced
    a.report === b.report
