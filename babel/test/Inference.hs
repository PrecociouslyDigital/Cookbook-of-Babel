{- | Laws of nutrition inference and of masses in yield units, on small
worlds built directly rather than through files.
-}
module Inference (inferenceProps) where

import Babel.Layout (Layer (..))
import Babel.Load (Located (..), World (..))
import Babel.Mass (Yield (..), massIn, yields)
import Babel.Model (Amount (..), Constants, Dimension (..), End (..), IngredientId (..), Leaf (..), Measure (..), MeasureValue (..), Origin (..), Outcome (..), Output (..), Per100g (..), Recipe (..), RecipeId (..), Role (..), Select (..), SourceId (..), TechniqueId (..), Unit (..), UnitId (..), leaves, measureKey)
import Babel.Nutrition (Derived (..), derivedPer100g, nutritionOf, nutritionTable)
import Data.Graph (SCC (..), flattenSCC, graphFromEdges, reachable, stronglyConnComp, vertices)
import Data.Map.Strict qualified as Map
import Data.Set qualified as Set
import Gen (genConstants)
import Hedgehog (Gen, Property, PropertyT, annotateShow, assert, cover, failure, forAll, property, success, withTests, (===))
import Hedgehog.Gen qualified as Gen
import Hedgehog.Range qualified as Range
import Reference qualified
import Test.Tasty (TestName, TestTree, localOption, mkTimeout)
import Test.Tasty.Hedgehog (testPropertyNamed)

inferenceProps :: [TestTree]
inferenceProps =
    [ prop "one input yielding its own mass gives that input's values" singleInput
    , prop "a mix of two inputs has kcal between theirs" mixBetween
    , prop "a stated value beats an inferred one" statedWins
    , prop "taking away a byproduct undoes adding it" byproductInverse
    , prop "a batch weighs what one run of its recipe gives out" batchMass
    , prop "inference ends on any recipe graph, cycles included" terminates
    , prop "outside cycles, inference agrees with the path-by-path search" agreesOutsideCycles
    , prop "an inferred value is its recipe applied to the values it used" consistent
    , prop "no inferred value rests on itself" wellFounded
    , localOption (mkTimeout 10_000_000) (prop "a ladder of shared sub-recipes is worked out once per rung" ladder)
    ]
  where
    prop :: TestName -> Property -> TestTree
    prop name = testPropertyNamed name (fromString name)

-- * Worlds

worldOf :: Constants -> [Recipe] -> [Measure] -> World
worldOf c rs ms =
    World
        { dataset = Nothing
        , renames = []
        , constants = Just c
        , units = Map.fromList [(u.id, u) | u <- [Unit (UnitId "g") Mass 1, Unit (UnitId "batch") Count 1]]
        , media = mempty
        , forms = mempty
        , texture = mempty
        , taste = mempty
        , serving = mempty
        , sources = mempty
        , ingredients = mempty
        , techniques = mempty
        , recipes = Map.fromList [(r.id, located r) | r <- rs]
        , measures = Map.fromList [((m.ingredient, measureKey m.value), located m) | m <- ms]
        }
  where
    located x = Located x "test" Reviewed OriginalWork

-- | A step taking in and giving out these amounts; the first output is the main one.
recipe :: Text -> [(Text, Amount)] -> [(Text, Amount)] -> Recipe
recipe rid ins outs =
    Recipe
        { id = RecipeId rid
        , source = SourceId "s"
        , sourceRef = Nothing
        , technique = TechniqueId "t"
        , inputs = Select Nothing Nothing [Right (Leaf (IngredientId i) a) | (i, a) <- ins]
        , outputs = case [Output (IngredientId o) a role Nothing | ((o, a), role) <- zip outs (Main : repeat Byproduct)] of
            o : os -> Outputs (o :| os)
            [] -> error "recipe: no outputs"
        , environment = Nothing
        , end = ByCoreTemp 70
        , stateNote = Nothing
        , issues = []
        }

grams :: Double -> Amount
grams g = Amount g (UnitId "g")

stated :: Text -> Per100g -> Measure
stated i n = Measure (IngredientId i) (SourceId "s") (Nutrition n) []

constants :: Gen Constants
constants = genConstants [TechniqueId "t"]

mass :: Gen Double
mass = Gen.double (Range.linearFrac 1 1000)

-- | Every nutrient stated, so each one can be compared.
full :: Gen Per100g
full = Per100g <$> v <*> v <*> v <*> v
  where
    v = Just <$> Gen.double (Range.linearFrac 0 100)

close :: Double -> Double -> Bool
close a b = abs (a - b) <= 1e-6 * max 1 (max (abs a) (abs b))

sameValues :: Per100g -> Per100g -> Bool
sameValues a b = and (zipWith (\x y -> fromMaybe False (close <$> x <*> y)) (nutrients a) (nutrients b))
  where
    nutrients n = [n.kcal, n.proteinG, n.fatG, n.carbsG]

inferred :: World -> Text -> PropertyT IO Per100g
inferred w i = case nutritionOf w (IngredientId i) of
    Just d@(Inferred{}) -> pure (derivedPer100g d)
    other -> annotateShow other >> failure

-- * Properties

singleInput :: Property
singleInput = property $ do
    c <- forAll constants
    g <- forAll mass
    n <- forAll full
    got <- inferred (worldOf c [recipe "r" [("a", grams g)] [("b", grams g)]] [stated "a" n]) "b"
    annotateShow got
    assert (sameValues got n)

mixBetween :: Property
mixBetween = property $ do
    c <- forAll constants
    (ga, gb) <- forAll ((,) <$> mass <*> mass)
    (na, nb) <- forAll ((,) <$> full <*> full)
    got <- inferred (worldOf c [recipe "r" [("a", grams ga), ("b", grams gb)] [("c", grams (ga + gb))]] [stated "a" na, stated "b" nb]) "c"
    annotateShow got
    case (got.kcal, na.kcal, nb.kcal) of
        (Just k, Just ka, Just kb) -> assert (min ka kb - 1e-9 <= k && k <= max ka kb + 1e-9)
        _ -> failure

statedWins :: Property
statedWins = property $ do
    c <- forAll constants
    (na, nb) <- forAll ((,) <$> full <*> full)
    let w = worldOf c [recipe "r" [("a", grams 100)] [("b", grams 100)]] [stated "a" na, stated "b" nb]
    nutritionOf w (IngredientId "b") === Just (Stated nb (SourceId "s"))

{- | Mix @b@ and @c@ into @a@, then split @a@ back into @b@ and the
byproduct @c@: what is left for @b@ is what @b@ was.
-}
byproductInverse :: Property
byproductInverse = property $ do
    c <- forAll constants
    (gb, gc) <- forAll ((,) <$> mass <*> mass)
    (nb, nc) <- forAll ((,) <$> full <*> full)
    let mix f = (\b c' -> (gb * b + gc * c') / (gb + gc)) <$> f nb <*> f nc
        na = Per100g (mix (.kcal)) (mix (.proteinG)) (mix (.fatG)) (mix (.carbsG))
    got <- inferred (worldOf c [recipe "r" [("a", grams (gb + gc))] [("b", grams gb), ("c", grams gc)]] [stated "a" na, stated "c" nc]) "b"
    annotateShow got
    assert (sameValues got nb)

batchMass :: Property
batchMass = property $ do
    c <- forAll constants
    g <- forAll mass
    q <- forAll (Gen.double (Range.linearFrac 0.1 10))
    let w = worldOf c [recipe "r" [("a", grams g)] [("b", Amount 1 (UnitId "batch"))]] []
    case massIn w (IngredientId "b") (Amount q (UnitId "batch")) of
        Just m -> assert (close m (q * g))
        Nothing -> failure

terminates :: Property
terminates = property $ do
    c <- forAll constants
    let ids = ["a", "b", "c", "d"]
        amount = grams <$> mass
        side = Gen.list (Range.linear 1 3) ((,) <$> Gen.element ids <*> amount)
    rs <- forAll (Gen.list (Range.linear 0 6) ((,) <$> side <*> side))
    ns <- forAll (Gen.subsequence ids)
    n <- forAll full
    let w = worldOf c [recipe ("r" <> show k) ins outs | (k, (ins, outs)) <- zip [0 :: Int ..] rs] [stated i n | i <- ns]
        makes i r = i `elem` [o | (o, _) <- snd (fromMaybe ([], []) (Map.lookup r named))]
        named = Map.fromList [(RecipeId ("r" <> show k), x) | (k, x) <- zip [0 :: Int ..] rs]
    -- Whatever comes back is either stated or from a recipe that makes the ingredient.
    forM_ ids $ \i -> case nutritionOf w (IngredientId i) of
        Just (Inferred r _ _) -> assert (makes i r)
        Just (Stated _ _) -> assert (i `elem` ns)
        Nothing -> success

-- * Recipe graphs

{- | A small world of ingredients and the recipes between them: chains,
several recipes for one ingredient, inputs that lead back into cycles,
byproducts, inputs light enough to be negligible, and amounts counted in
batches as well as weighed. Recipe IDs are shuffled, so which recipe is
tried first is independent of the shape.
-}
genGraph :: Gen (Constants, [Recipe], [Measure])
genGraph = do
    c <- constants
    n <- Gen.int (Range.constant 2 6)
    let ingredient k = "i" <> show (k :: Int)
        anyIngredient = ingredient <$> Gen.int (Range.constant 0 (n - 1))
        below t = ingredient <$> Gen.int (Range.constant 0 (t - 1))
    steps <- fmap concat . forM [1 .. n - 1] $ \t -> Gen.list (Range.constant 0 3) $ do
        ins <- Gen.list (Range.constant 1 2) ((,) <$> Gen.frequency [(3, below t), (1, anyIngredient)] <*> amount)
        byproduct <- Gen.frequency [(4, pure Nothing), (1, Just <$> ((,) <$> anyIngredient <*> amount))]
        out <- amount
        pure (ins, (ingredient t, out) : [b | b@(i, _) <- toList byproduct, i /= ingredient t])
    ids <- Gen.shuffle [10 .. 9 + length steps]
    statedOnes <- Gen.subsequence [ingredient k | k <- [0 .. n - 1]]
    ms <- forM statedOnes $ \i -> stated i <$> partial
    pure (c, [recipe ("r" <> show k) ins outs | (k, (ins, outs)) <- zip (ids :: [Int]) steps], ms)
  where
    amount = Gen.frequency [(6, grams <$> mass), (1, grams <$> Gen.double (Range.linearFrac 0.001 1)), (2, Amount <$> Gen.double (Range.linearFrac 0.5 4) <*> pure (UnitId "batch"))]
    partial = Per100g <$> v <*> v <*> v <*> v
      where
        v = Gen.frequency [(5, Just <$> Gen.double (Range.linearFrac 0 100)), (1, pure Nothing)]

{- | The ingredients whose value could hang on a cycle: those from which a
cycle can be reached, going from what a recipe makes to everything else
in that recipe.
-}
nearCycles :: World -> Set IngredientId
nearCycles w = Set.fromList [keyOf v | v <- vertices g, any ((`Set.member` cyclic) . keyOf) (reachable g v)]
  where
    adjacency = Map.toList (Map.fromListWith (<>) [(o, others o r) | r <- map (.value) (Map.elems w.recipes), o <- outputsOf r])
    others o r = [l.ingredient | l <- leaves r.inputs] <> filter (/= o) (outputsOf r)
    outputsOf r = case r.outputs of
        Outputs os -> [x.ingredient | x <- toList os]
        OneOf _ -> []
    (g, ofVertex, _) = graphFromEdges [(i, i, is) | (i, is) <- adjacency]
    keyOf v = let (i, _, _) = ofVertex v in i
    cyclic = Set.fromList (concat [is | CyclicSCC is <- stronglyConnComp [(i, i, is) | (i, is) <- adjacency]])

-- | Every ingredient a world mentions.
ingredients :: World -> [IngredientId]
ingredients w = ordNub ([l.ingredient | r <- Map.elems w.recipes, l <- leaves r.value.inputs] <> [i | (i, _) <- Map.keys w.measures] <> [o.ingredient | r <- Map.elems w.recipes, Outputs os <- [r.value.outputs], o <- toList os])

sameDerived :: Maybe Derived -> Maybe Derived -> Bool
sameDerived a b = case (a, b) of
    (Nothing, Nothing) -> True
    (Just (Stated n s), Just (Stated n' s')) -> s == s' && sameish n n'
    (Just (Inferred r n l), Just (Inferred r' n' l')) -> r == r' && l == l' && sameish n n'
    _ -> False
  where
    sameish x y = and (zipWith closeMaybe (nutrients x) (nutrients y))
    nutrients x = [x.kcal, x.proteinG, x.fatG, x.carbsG]

-- | Both unknown, or both known and 'close'.
closeMaybe :: Maybe Double -> Maybe Double -> Bool
closeMaybe a b = case (a, b) of
    (Nothing, Nothing) -> True
    (Just x, Just y) -> close x y
    _ -> False

agreesOutsideCycles :: Property
agreesOutsideCycles = withTests 1000 . property $ do
    (c, rs, ms) <- forAll genGraph
    let w = worldOf c rs ms
        away = filter (`Set.notMember` nearCycles w) (ingredients w)
        batch = Amount 1 (UnitId "batch")
    cover 30 "some ingredients away from cycles" (not (null away))
    cover 30 "some ingredients near a cycle" (length away < length (ingredients w))
    forM_ away $ \i -> do
        annotateShow i
        let (new, old) = (nutritionOf w i, Reference.nutritionOf w i)
        annotateShow (new, old)
        assert (sameDerived new old)
        let (newMass, oldMass) = (massIn w i batch, Reference.massIn w i batch)
        annotateShow (newMass, oldMass)
        assert (closeMaybe newMass oldMass)

{- | Rerunning the recipe an ingredient was inferred from, against every
other ingredient's value but excluding the inputs it originally left
out, gives the same value back. The search in "Reference" fails this
inside cycles, where an ingredient's value depended on where the
search began.
-}
consistent :: Property
consistent = withTests 5000 . property $ do
    (c, rs, ms) <- forAll genGraph
    let w = worldOf c rs ms
        ys = yields w
        table = nutritionTable w
    forM_ (Map.toList table) $ \case
        (i, d@(Inferred r _ left)) -> do
            y <- maybe failure pure (Map.lookup r ys)
            let look j = if j `elem` left then Nothing else Map.lookup j table
            annotateShow (i, d)
            assert (sameDerived (Reference.inferFrom c look r y i) (Just d))
        _ -> success

-- | Following each inferred value to the values it used never comes back round.
wellFounded :: Property
wellFounded = withTests 5000 . property $ do
    (c, rs, ms) <- forAll genGraph
    let w = worldOf c rs ms
        ys = yields w
        table = nutritionTable w
        used i = case Map.lookup i table of
            Just (Inferred r _ left)
                | Just y <- Map.lookup r ys ->
                    filter (`notElem` left) [l.ingredient | (l, _) <- y.inputs] <> [o.ingredient | (o, _) <- y.outputs, o.ingredient /= i]
            _ -> []
        components = stronglyConnComp [(i, i, used i) | i <- ingredients w]
    annotateShow [flattenSCC s | s@(CyclicSCC _) <- components]
    assert (null [() | CyclicSCC _ <- components])

{- | Each rung mixes equal parts of the two ingredients on the rung below,
in batches, so a search that works out each input afresh does so
2^rungs times; the bottom rung's values reach the top unchanged.
-}
ladder :: Property
ladder = withTests 20 . property $ do
    c <- forAll constants
    n <- forAll full
    g <- forAll mass
    let rungs = 40
        name :: Text -> Int -> Text
        name side k = side <> show k
        batch = Amount 1 (UnitId "batch")
        rung k = [(name s (k - 1), if k == 1 then grams g else batch) | s <- ["a", "b"]]
        rs = [recipe (name s k) (rung k) [(name s k, batch)] | k <- [1 .. rungs], s <- ["a", "b"]]
        w = worldOf c rs [stated (name s 0) n | s <- ["a", "b"]]
    got <- inferred w (name "a" rungs)
    annotateShow got
    assert (sameValues got n)
    massIn w (IngredientId (name "a" rungs)) batch === Just (2 ^ rungs * g)
