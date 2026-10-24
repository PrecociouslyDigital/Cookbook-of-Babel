{- | Laws of nutrition inference and of masses in yield units, on small
worlds built directly rather than through files.
-}
module Inference (inferenceProps) where

import Babel.Layout (Layer (..))
import Babel.Load (Located (..), World (..))
import Babel.Mass (massIn)
import Babel.Model (Amount (..), Constants, Dimension (..), End (..), IngredientId (..), Leaf (..), Measure (..), MeasureValue (..), Origin (..), Outcome (..), Output (..), Per100g (..), Recipe (..), RecipeId (..), Role (..), Select (..), SourceId (..), TechniqueId (..), Unit (..), UnitId (..), measureKey)
import Babel.Nutrition (Derived (..), derivedPer100g, nutritionOf)
import Data.Map.Strict qualified as Map
import Gen (genConstants)
import Hedgehog (Gen, Property, PropertyT, annotateShow, assert, failure, forAll, property, success, (===))
import Hedgehog.Gen qualified as Gen
import Hedgehog.Range qualified as Range
import Test.Tasty (TestName, TestTree)
import Test.Tasty.Hedgehog (testPropertyNamed)

inferenceProps :: [TestTree]
inferenceProps =
    [ prop "one input yielding its own mass gives that input's values" singleInput
    , prop "a mix of two inputs has kcal between theirs" mixBetween
    , prop "a stated value beats an inferred one" statedWins
    , prop "taking away a byproduct undoes adding it" byproductInverse
    , prop "a batch weighs what one run of its recipe gives out" batchMass
    , prop "inference ends on any recipe graph, cycles included" terminates
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
