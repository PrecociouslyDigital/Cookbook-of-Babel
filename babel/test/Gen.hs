{-# LANGUAGE RecordWildCards #-}

{- | Generators. 'genTree' builds datasets that are valid by construction
rather than by filtering:

* is-a parents are drawn only from IDs generated earlier, so both DAGs are
  acyclic;
* every reference is sampled from the IDs that exist;
* a recipe is filed under its source's licence;
* select bounds are drawn inside @0..n@, and every output block has at
  least one output that is not chance-gated;
* rename chains run from fresh slugs to an existing one.
-}
module Gen (
    genSlug,
    genDottedSlug,
    genBadSlug,
    genVersion,
    genBadVersion,
    genTree,
    genSensitivity,
    genTempBands,
    freshSlug,
    timeUnits,
    nonTimeUnits,
)
where

import Babel.Load (Layer (..))
import Babel.Model (Amount (..), AxisId (..), BandEntry (..), Branch (..), Constants (..), Dataset (..), Dimension (..), DottedSlug (..), End (..), EntityKind (..), Environment (..), FormId (..), Ingredient (..), IngredientId (..), IsA (..), IsId, Issue (..), KcalPerGram (..), Leaf (..), Medium (..), MediumId (..), Nutrition (..), Outcome (..), Output (..), Per100g (..), RatioBounds (..), Recipe (..), RecipeId (..), Rename (..), Select (..), Sensitivity (..), ServingTagId (..), Source (..), SourceId (..), SourceRef (..), TasteAttrId (..), Technique (..), TechniqueId (..), TempBandId (..), TempBands, TempRange (..), Term (..), TextureAttrId (..), Tolerance (..), Unit (..), UnitId (..), Version (..), mkIntensity, mkTempBands, renderVersion)
import Data.Map.Strict qualified as Map
import Data.Set qualified as Set
import Data.Text qualified as T
import Fixture (Placed (..), Tree (..))
import Hedgehog (Gen)
import Hedgehog.Gen qualified as Gen
import Hedgehog.Range qualified as Range

-- * Slugs and versions

genSlug :: Gen Text
genSlug = T.intercalate "_" <$> Gen.list (Range.linear 1 3) segment
  where
    segment = Gen.text (Range.linear 1 6) (Gen.element (['a' .. 'z'] ++ ['0' .. '9']))

genDottedSlug :: Gen Text
genDottedSlug = T.intercalate "." <$> Gen.list (Range.linear 1 3) genSlug

-- | Text that is not a slug, built by breaking a slug in one known way.
genBadSlug :: Gen Text
genBadSlug = do
    s <- genSlug
    Gen.element
        [ T.toUpper s <> "X"
        , s <> " " <> s
        , "_" <> s
        , s <> "_"
        , s <> "__" <> s
        , s <> "-" <> s
        , ""
        ]

-- | A slug not in the given set.
freshSlug :: Set.Set Text -> Gen Text
freshSlug taken = Gen.filter (`Set.notMember` taken) genSlug

genVersion :: Gen Version
genVersion = Version <$> n <*> n <*> n
  where
    n = Gen.int (Range.linear 0 30)

genBadVersion :: Gen Text
genBadVersion = do
    v <- renderVersion <$> genVersion
    Gen.element [v <> ".0", T.intercalate "." (take 2 (T.splitOn "." v)), "v" <> v, "0" <> v <> "0", v <> "-beta", "1..2", ""]

-- * Values

positive :: Gen Double
positive = Gen.double (Range.exponentialFloat 0.01 10000)

genText :: Gen Text
genText = Gen.text (Range.linear 1 24) Gen.unicode

genIssue :: Gen Issue
genIssue = Issue <$> genText <*> Gen.enumBounded <*> genText

subsetOf :: [a] -> Gen [a]
subsetOf = Gen.subsequence

-- | Units are fixed per dimension, plus a few extra generated ones.
coreUnits :: [Unit]
coreUnits = [Unit (UnitId "g") Mass 1, Unit (UnitId "ml") Volume 1, Unit (UnitId "piece") Count 1, Unit (UnitId "s") Time 1]

timeUnits, nonTimeUnits :: Tree -> [UnitId]
timeUnits t = [u.id | u <- t.unitReg, u.dimension == Time]
nonTimeUnits t = [u.id | u <- t.unitReg, u.dimension /= Time]

genTerms :: (IsId i) => (Text -> i) -> Int -> Int -> Gen [Term i]
genTerms wrap lo hi = do
    ids <- Set.toList <$> Gen.set (Range.linear lo hi) genSlug
    forM ids $ \i -> Term (wrap i) <$> genText <*> Gen.maybe genText

-- * Trees

genTree :: Gen Tree
genTree = do
    meta <- Dataset <$> genVersion
    extraUnits <- Set.toList <$> Gen.set (Range.linear 0 3) (Gen.filter (`notElem` ["g", "ml", "piece", "s"]) genSlug)
    unitReg <- (coreUnits ++) <$> forM extraUnits (\u -> Unit (UnitId u) <$> Gen.enumBounded <*> positive)
    mediumIds <- Set.toList <$> Gen.set (Range.linear 0 3) genSlug
    mediumReg <- forM mediumIds $ \m -> Medium (MediumId m) <$> genText <*> positive
    formReg <- genTerms FormId 1 4
    textureReg <- genTerms TextureAttrId 0 4
    tasteReg <- genTerms TasteAttrId 0 4
    servingReg <- genTerms ServingTagId 0 3

    sourceIds <- Set.toList <$> Gen.set (Range.linear 1 3) genSlug
    srcs <- forM sourceIds $ \s -> do
        lic <- Gen.enumBounded
        layer <- genLayer
        src <- Source (SourceId s) <$> genText <*> Gen.maybe genText <*> pure lic
        pure (Placed layer lic src)

    ingIds <- Set.toList <$> Gen.set (Range.linear 2 8) genSlug
    ings <- forM (zip [0 ..] ingIds) $ \(n, i) -> do
        parentIds <- subsetOf (take n ingIds)
        parents <- forM parentIds $ \p -> IsA (IngredientId p) . AxisId <$> genSlug
        ing <-
            Ingredient (IngredientId i)
                <$> genText
                <*> Gen.list (Range.linear 0 2) genText
                <*> pure parents
                <*> Gen.element [f.id | f <- formReg]
                <*> intensities [x.id | x <- textureReg]
                <*> intensities [x.id | x <- tasteReg]
                <*> subsetOf [x.id | x <- servingReg]
                <*> Gen.maybe positive
                <*> Gen.maybe (Gen.double (Range.linearFrac 0 1))
                <*> Gen.maybe (genNutrition (map (.entity.id) srcs))
                <*> Gen.bool
                <*> Gen.list (Range.linear 0 2) genIssue
        Placed <$> genLayer <*> Gen.enumBounded <*> pure ing

    techIds <- Set.toList <$> Gen.set (Range.linear 1 5) genSlug
    techs <- forM (zip [0 ..] techIds) $ \(n, t) -> do
        parents <- map TechniqueId <$> subsetOf (take n techIds)
        temp <- Gen.maybe $ do
            lo <- Gen.double (Range.linearFrac (-40) 300)
            hi <- Gen.double (Range.linearFrac lo 300)
            pure (TempRange lo hi)
        tech <- Technique (TechniqueId t) <$> genText <*> pure parents <*> pure temp <*> Gen.list (Range.linear 0 2) genIssue
        Placed <$> genLayer <*> Gen.enumBounded <*> pure tech

    consts <- genConstants [t.entity.id | t <- techs]
    let tree0 = Tree{renameLog = [], recs = [], ..}
    recIds <- Set.toList <$> Gen.set (Range.linear 1 6) genDottedSlug
    recs <- forM (zip [1 ..] recIds) $ \(n, r) -> do
        src <- Gen.element srcs
        recipe <- genRecipe tree0 (RecipeId r) src.entity.id n
        layer <- genLayer
        pure (Placed layer src.entity.licence recipe)

    renameLog <- genRenames meta.version ingIds
    pure Tree{..}
  where
    genLayer = Gen.element [Extracted, Reviewed]
    intensities ids = do
        keys <- subsetOf ids
        Map.fromList <$> forM keys (\k -> (k,) . either (error . toText) id . mkIntensity <$> Gen.int (Range.constant 0 3))

genNutrition :: [SourceId] -> Gen Nutrition
genNutrition sourceIds =
    Nutrition
        <$> (Per100g <$> m <*> m <*> m <*> m)
        <*> Gen.element sourceIds
  where
    m = Gen.maybe (Gen.double (Range.linearFrac 0 900))

-- | Step @n@ is unique per recipe, so 'sourceRef's never collide.
genRecipe :: Tree -> RecipeId -> SourceId -> Int -> Gen Recipe
genRecipe t rid src n = do
    sourceRef <- Gen.maybe (pure (SourceRef src n))
    technique <- Gen.element [x.entity.id | x <- t.techs]
    inputs <- genSelect 2 genLeaf
    outputs <- genOutcome 2
    environment <- Gen.maybe genEnvironment
    end <- genEnd (isNothing environment)
    Recipe rid src sourceRef technique inputs outputs environment end
        <$> Gen.maybe genText
        <*> Gen.list (Range.linear 0 2) genIssue
  where
    ingredient = Gen.element [x.entity.id | x <- t.ings]
    amount units = Amount <$> positive <*> Gen.element units
    genLeaf = Leaf <$> ingredient <*> amount (nonTimeUnits t)
    genOutput chance = Output <$> ingredient <*> amount (nonTimeUnits t) <*> Gen.enumBounded <*> chance
    genOutcome :: Int -> Gen Outcome
    genOutcome depth =
        Gen.recursive
            Gen.choice
            [outputs]
            [ OneOf <$> nonEmptyOf (Branch <$> positive <*> genOutcome (depth - 1)) | depth > 0
            ]
      where
        outputs = do
            certain <- genOutput (pure Nothing)
            others <- Gen.list (Range.linear 0 3) (genOutput (Gen.maybe (Gen.double (Range.linearFrac 0.01 1))))
            shuffled <- Gen.shuffle (certain : others)
            pure (Outputs (fromList' shuffled))
    genEnvironment =
        Environment
            <$> Gen.double (Range.linearFrac (-30) 300)
            <*> Gen.choice ((Right <$> positive) : [Left <$> Gen.element [m.id | m <- t.mediumReg] | not (null t.mediumReg)])
    genEnd ambient =
        Gen.choice
            [ ByCoreTemp <$> Gen.double (Range.linearFrac (-20) 120)
            , ByTime <$> amount (timeUnits t) <*> (if ambient then Just <$> genSensitivity else Gen.maybe genSensitivity)
            , ByBoth <$> Gen.double (Range.linearFrac (-20) 120) <*> amount (timeUnits t)
            ]
    nonEmptyOf g = fromList' <$> Gen.list (Range.linear 1 3) g
    fromList' = \case
        x : xs -> x :| xs
        [] -> error "fromList': generators never produce empty lists here"

-- | All four shapes of bounds that are valid for @n@ choices.
genSelect :: Int -> Gen Leaf -> Gen Select
genSelect depth leaf = do
    choices <-
        Gen.list
            (Range.linear 1 4)
            (if depth > 0 then Gen.frequency [(3, Right <$> leaf), (1, Left <$> genSelect (depth - 1) leaf)] else Right <$> leaf)
    let n = length choices
    lo <- Gen.int (Range.constant 0 n)
    hi <- Gen.int (Range.constant lo n)
    (minCount, maxCount) <- Gen.element [(Nothing, Nothing), (Just lo, Just hi), (Just lo, Nothing), (Nothing, Just n)]
    pure Select{..}

genSensitivity :: Gen Sensitivity
genSensitivity = do
    lo <- Gen.double (Range.linearFrac 0 1)
    hi <- Gen.double (Range.linearFrac lo 1)
    Sensitivity
        <$> Gen.double (Range.linearFrac (-20) 60)
        <*> Gen.double (Range.exponentialFloat 0.1 10)
        <*> Gen.maybe (pure lo)
        <*> Gen.maybe (pure hi)

{- | Chains of fresh slugs ending at an existing ingredient, all at or
before the dataset version.
-}
genRenames :: Version -> [Text] -> Gen [Rename]
genRenames v existing = do
    chains <- Gen.list (Range.linear 0 2) (Gen.list (Range.linear 1 3) genSlug)
    let fresh = dedupe (concatMap (filter (`notElem` existing)) chains)
    target <- Gen.element existing
    groups <- Gen.list (Range.singleton (length fresh)) (Gen.int (Range.constant 0 1))
    let chainsOf = Map.elems (Map.fromListWith (flip (++)) (zip groups (map pure fresh)))
    concat
        <$> forM
            chainsOf
            ( \slugs -> forM (zip slugs (drop 1 slugs ++ [target])) $ \(from, to) -> do
                since <- Gen.filter (<= v) genVersion
                pure (Rename since KIngredient (DottedSlug from) (DottedSlug to))
            )
  where
    dedupe = Set.toList . Set.fromList

genConstants :: [TechniqueId] -> Gen Constants
genConstants techIds = do
    tempBands <- genTempBands
    spoilageTechnique <- Gen.element techIds
    kcalPerGram <- KcalPerGram <$> positive <*> positive <*> positive
    calorieTolerance <- Tolerance <$> Gen.double (Range.linearFrac 0 100) <*> Gen.double (Range.linearFrac 0 1)
    lo <- positive
    hi <- Gen.double (Range.linearFrac lo (lo * 100))
    pure Constants{massRatio = RatioBounds lo hi, ..}

-- | Strictly increasing bounds, built by accumulating positive steps.
genTempBands :: Gen TempBands
genTempBands = do
    ids <- Set.toList <$> Gen.set (Range.linear 1 6) genSlug
    start <- Gen.double (Range.linearFrac (-50) 50)
    steps <- Gen.list (Range.singleton (length ids - 1)) (Gen.double (Range.linearFrac 0.5 50))
    let bounds = take (length ids - 1) (scanl (+) start steps)
        entries = zipWith BandEntry (map TempBandId ids) (map Just bounds ++ [Nothing])
    either (error . toText) pure (mkTempBands entries)
