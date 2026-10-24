{-# LANGUAGE RecordWildCards #-}
-- RecordWildCards rebuilds records whose fields (id, label, parents) are
-- shared between types, which plain record update can't disambiguate.
{-# OPTIONS_GHC -Wno-name-shadowing #-}

{- | Most of the suite is properties over generated datasets (see "Gen"):

* generated datasets validate cleanly, and every value survives 'render';
* each rule fires, and only that rule, on a random targeted mutation of a
  random dataset;
* overlay and helper laws hold.

The few example tests left check the real seed data, not the code.
-}
module Main (main) where

import Autodocodec (HasCodec, parseJSONViaCodec, toJSONViaCodec)
import Babel.Check (findCycles)
import Babel.Diagnostic (Diagnostic (..), isError, renderDiagnostic)
import Babel.Format (render)
import Babel.Load (Layer (..), Located (..), World (..))
import Babel.Model (Amount (..), AxisId (..), BandEntry (..), Branch (..), Dataset (..), DottedSlug (..), End (..), EntityKind (..), FormId (..), Ingredient (..), IngredientId (..), IsA (..), IsId (..), Leaf (..), Licence, Outcome (..), Output (..), Recipe (..), RecipeId (..), Rename (..), Role (..), Select (..), Sensitivity (..), SourceRef (..), Technique (..), TechniqueId (..), Term (..), UnitId (..), Version, bandFromCelsius, isSlug, licenceSpdx, mkTempBands, parseVersion, renderVersion, tempBandEntries)
import Babel.Spoilage (Conditions (..), firstSpoilage, timeAt)
import Babel.Validate (Report (..), validate)
import Data.Aeson (Value (..))
import Data.Aeson qualified as Aeson
import Data.Aeson.Key qualified as Key
import Data.Aeson.KeyMap qualified as KM
import Data.Aeson.Types (parseEither)
import Data.ByteString.Lazy qualified as BL
import Data.List.NonEmpty qualified as NE
import Data.Map.Strict qualified as Map
import Data.Set qualified as Set
import Data.Text qualified as T
import Data.Vector qualified as V
import Fixture (EntityFile (..), Placed (..), Tree (..), entityFiles, withTree)
import Gen (freshSlug, genBadSlug, genBadVersion, genDottedSlug, genSensitivity, genSlug, genTempBands, genTree, genVersion, nonTimeUnits)
import Hedgehog (Gen, Property, PropertyT, annotate, annotateShow, assert, evalIO, failure, forAll, property, success, tripping, (===))
import Hedgehog.Gen qualified as Gen
import Hedgehog.Range qualified as Range
import System.Directory (createDirectoryIfMissing, renameFile)
import System.FilePath (takeDirectory, (</>))
import System.IO (hSetEncoding, utf8)
import Test.Tasty (TestName, TestTree, defaultMain, testGroup)
import Test.Tasty.HUnit (assertBool, assertFailure, testCase, (@?=))
import Test.Tasty.Hedgehog (testPropertyNamed)

main :: IO ()
main = do
    hSetEncoding stdout utf8
    defaultMain $
        testGroup
            "babel"
            [ testGroup "codecs" codecProps
            , testGroup "validity" [prop "generated datasets validate cleanly" validProp]
            , testGroup "rules" ruleProps
            , testGroup "files" fileProps
            , testGroup "overlays" overlayProps
            , testGroup "laws" lawProps
            , testGroup "seed data" seedTests
            ]

prop :: TestName -> Property -> TestTree
prop name = testPropertyNamed name (fromString name)

-- * Running

check :: Tree -> PropertyT IO Report
check t = evalIO (withTree t (validate False))

errorRules :: Report -> [Text]
errorRules r = sort (ordNub [d.rule | d <- r.diagnostics, isError d])

showDiagnostics :: Report -> PropertyT IO ()
showDiagnostics r = annotate (toString (unlines (map renderDiagnostic r.diagnostics)))

validProp :: Property
validProp = property $ do
    t <- forAll genTree
    r <- check t
    showDiagnostics r
    errorRules r === []

-- * Codecs

decode :: (HasCodec a) => LByteString -> Either String a
decode = Aeson.eitherDecode >=> parseEither parseJSONViaCodec

{- | Decoding the rendering gives the value back, and the rendering means
the same JSON as the codec's encoding (so re-indenting lost nothing).
-}
codecLaws :: (HasCodec a, Eq a, Show a) => a -> PropertyT IO ()
codecLaws x = do
    tripping x render decode
    Aeson.decode (render x) === Just (toJSONViaCodec x)

codecProps :: [TestTree]
codecProps =
    [ prop "every value in a dataset survives render then decode" $ property $ do
        t <- forAll genTree
        mapM_ (codecLaws . (.entity)) t.srcs
        mapM_ (codecLaws . (.entity)) t.ings
        mapM_ (codecLaws . (.entity)) t.techs
        mapM_ (codecLaws . (.entity)) t.recs
        codecLaws t.meta
        codecLaws t.renameLog
        codecLaws t.consts
        codecLaws t.unitReg
        codecLaws t.mediumReg
        codecLaws t.formReg
        codecLaws t.textureReg
    , prop "rendering ends in one newline and indents by two" $ property $ do
        t <- forAll genTree
        f <- forAll (Gen.element (entityFiles t))
        let rendered = decodeUtf8 (render f.json) :: Text
        annotate (toString rendered)
        assert ("\n" `T.isSuffixOf` rendered && not ("\n\n" `T.isSuffixOf` rendered))
        assert (all (even . T.length . T.takeWhile (== ' ')) (lines rendered))
    ]

-- * Rules

{- | Mutate a valid dataset; validation must report exactly these rules
(a mutation that also trips a second rule is a broken test or a bug).
-}
rule :: TestName -> [Text] -> (Tree -> Gen Tree) -> TestTree
rule name expected mutate = prop name $ property $ do
    t <- forAll genTree
    t' <- forAll (mutate t)
    r <- check t'
    showDiagnostics r
    errorRules r === sort expected

ruleProps :: [TestTree]
ruleProps =
    [ rule "an unresolvable reference" ["dangling-ref"] danglingRef
    , rule "an is-a cycle" ["isa-cycle"] isaCycle
    , rule "select bounds outside 0 <= min <= max <= n" ["select-bounds"] badSelect
    , rule "a non-positive branch weight" ["weight"] badWeight
    , rule "a chance outside (0, 1]" ["chance"] badChance
    , rule "an outputs block that is all chance-gated" ["all-chance"] allChance
    , rule "an ambient time-based end without sensitivity" ["ambient-time"] ambientTime
    , rule "a water activity out of bounds" ["water-activity"] badWaterActivity
    , rule "a licence folder that doesn't match the source" ["licence-mismatch"] licenceMismatch
    , rule "the same key in two licence folders of a layer" ["overlay-conflict"] overlayConflict
    , rule "a duration in a non-time unit" ["dimension"] durationUnit
    , rule "a rename from a slug that still exists" ["rename-from-exists"] renameFromExists
    , rule "a rename loop" ["rename-loop", "rename-to-missing"] renameLoop
    , rule "a rename to nothing" ["rename-to-missing"] renameToNothing
    , rule "a rename after the dataset version" ["rename-since"] renameSince
    ]

ingIds, techIds, formIds :: Tree -> Set Text
ingIds t = Set.fromList [idText p.entity.id | p <- t.ings]
techIds t = Set.fromList [idText p.entity.id | p <- t.techs]
formIds t = Set.fromList [idText f.id | f <- t.formReg]

-- | Pick one element and replace it.
modifyOne :: [a] -> (a -> Gen a) -> Gen [a]
modifyOne xs f = do
    i <- Gen.int (Range.constant 0 (length xs - 1))
    forM (zip [0 ..] xs) $ \(j, x) -> if i == j then f x else pure x

onEntity :: (a -> Gen a) -> Placed a -> Gen (Placed a)
onEntity f p = (\e -> p{entity = e}) <$> f p.entity

onRecipe :: Tree -> (Recipe -> Gen Recipe) -> Gen Tree
onRecipe t f = (\rs -> t{recs = rs}) <$> modifyOne t.recs (onEntity f)

onIngredient :: Tree -> (Ingredient -> Gen Ingredient) -> Gen Tree
onIngredient t f = (\is -> t{ings = is}) <$> modifyOne t.ings (onEntity f)

danglingRef :: Tree -> Gen Tree
danglingRef t =
    Gen.choice
        [ do
            fresh <- IngredientId <$> freshSlug (ingIds t)
            onRecipe t $ \r -> pure r{inputs = appendLeaf (Leaf fresh (Amount 1 (UnitId "g"))) r.inputs}
        , do
            fresh <- TechniqueId <$> freshSlug (techIds t)
            onRecipe t $ \r -> pure r{technique = fresh}
        , do
            fresh <- FormId <$> freshSlug (formIds t)
            onIngredient t $ \i -> pure i{form = fresh}
        ]
  where
    -- An explicit max must grow too, or a defaulted min would pass it.
    appendLeaf l s = s{choices = s.choices ++ [Right l], maxCount = (+ 1) <$> s.maxCount}

-- | Add an edge from some ancestor (or the node itself) back down to it.
isaCycle :: Tree -> Gen Tree
isaCycle t =
    Gen.choice
        [ do
            a <- Gen.element t.ings
            b <- Gen.element (Set.toList (closure [(i.entity.id, map (.target) i.entity.parents) | i <- t.ings] a.entity.id))
            axis <- AxisId <$> genSlug
            let addTo Ingredient{..} = Ingredient{parents = parents ++ [IsA a.entity.id axis], ..}
            pure t{ings = [if p.entity.id == b then p{entity = addTo p.entity} else p | p <- t.ings]}
        , do
            a <- Gen.element t.techs
            b <- Gen.element (Set.toList (closure [(x.entity.id, x.entity.parents) | x <- t.techs] a.entity.id))
            let addTo Technique{..} = Technique{parents = parents ++ [a.entity.id], ..}
            pure t{techs = [if p.entity.id == b then p{entity = addTo p.entity} else p | p <- t.techs]}
        ]

-- | Everything reachable from a node, the node included.
closure :: (Ord k) => [(k, [k])] -> k -> Set k
closure edges = go Set.empty . pure
  where
    adj = Map.fromListWith (++) edges
    go seen = \case
        [] -> seen
        k : rest
            | Set.member k seen -> go seen rest
            | otherwise -> go (Set.insert k seen) (Map.findWithDefault [] k adj ++ rest)

badSelect :: Tree -> Gen Tree
badSelect t = onRecipe t $ \r -> do
    let s = r.inputs
        n = length s.choices
    (lo, hi) <-
        Gen.choice
            [ (\k -> (Just (k + 1), Just k)) <$> Gen.int (Range.constant 0 (n - 1))
            , (\k -> (Nothing, Just k)) <$> Gen.int (Range.constant (n + 1) (n + 5))
            , (\k -> (Just k, Nothing)) <$> Gen.int (Range.constant (-5) (-1))
            ]
    pure r{inputs = s{minCount = lo, maxCount = hi}}

badWeight :: Tree -> Gen Tree
badWeight t = onRecipe t $ \r -> do
    w <- Gen.double (Range.linearFrac (-5) 0)
    pure (withOutputs (OneOf (Branch w r.outputs NE.:| [Branch 1 r.outputs])) r)

withOutputs :: Outcome -> Recipe -> Recipe
withOutputs o Recipe{..} = Recipe{outputs = o, ..}

-- | Change the first plain output list in an outcome.
onFirstOutputs :: (NonEmpty Output -> NonEmpty Output) -> Outcome -> Outcome
onFirstOutputs f = \case
    Outputs os -> Outputs (f os)
    OneOf (Branch w o NE.:| bs) -> OneOf (Branch w (onFirstOutputs f o) NE.:| bs)

badChance :: Tree -> Gen Tree
badChance t = do
    ing <- (.entity.id) <$> Gen.element t.ings
    c <- Gen.choice [Gen.double (Range.linearFrac 1.001 10), Gen.double (Range.linearFrac (-5) 0)]
    let extra = Output ing (Amount 1 (UnitId "g")) Byproduct (Just c)
    onRecipe t $ \r -> pure (withOutputs (onFirstOutputs (<> pure extra) r.outputs) r)

allChance :: Tree -> Gen Tree
allChance t = do
    c <- Gen.double (Range.linearFrac 0.01 0.99)
    onRecipe t $ \r -> pure (withOutputs (onFirstOutputs (fmap (\o -> o{chance = Just c})) r.outputs) r)

ambientTime :: Tree -> Gen Tree
ambientTime t = onRecipe t $ \r -> pure r{environment = Nothing, end = ByTime (Amount 1 (UnitId "s")) Nothing}

badWaterActivity :: Tree -> Gen Tree
badWaterActivity t =
    Gen.choice
        [ do
            aw <- Gen.choice [Gen.double (Range.linearFrac (-5) (-0.001)), Gen.double (Range.linearFrac 1.001 5)]
            onIngredient t $ \Ingredient{..} -> pure Ingredient{waterActivity = Just aw, ..}
        , do
            lo <- Gen.double (Range.linearFrac 0 0.49)
            hi <- Gen.double (Range.linearFrac 0.5 1)
            let s = Sensitivity 20 2 (Just hi) (Just lo)
            onRecipe t $ \r -> pure r{end = ByTime (Amount 1 (UnitId "s")) (Just s)}
        ]

otherLicence :: Licence -> Gen Licence
otherLicence l = Gen.element [x | x <- universe, x /= l]

licenceMismatch :: Tree -> Gen Tree
licenceMismatch t =
    Gen.choice
        [ (\rs -> t{recs = rs}) <$> modifyOne t.recs (\p -> (\l -> p{folder = l}) <$> otherLicence p.folder)
        , (\ss -> t{srcs = ss}) <$> modifyOne t.srcs (\p -> (\l -> p{folder = l}) <$> otherLicence p.folder)
        ]

overlayConflict :: Tree -> Gen Tree
overlayConflict t =
    Gen.choice
        [ do
            p <- Gen.element t.ings
            l <- otherLicence p.folder
            pure t{ings = t.ings ++ [p{folder = l}]}
        , do
            p <- Gen.element t.techs
            l <- otherLicence p.folder
            pure t{techs = t.techs ++ [p{folder = l}]}
        ]

durationUnit :: Tree -> Gen Tree
durationUnit t = do
    u <- Gen.element (nonTimeUnits t)
    s <- genSensitivity
    onRecipe t $ \r -> pure r{end = ByTime (Amount 1 u) (Just s)}

-- | Slugs no rename and no ingredient uses yet.
freshRenameSlugs :: Tree -> Int -> Gen [Text]
freshRenameSlugs t n = do
    let taken = ingIds t <> Set.fromList (concat [[idText r.from, idText r.to] | r <- t.renameLog])
    Set.toList <$> Gen.set (Range.singleton n) (Gen.filter (`Set.notMember` taken) genSlug)

addRename :: Tree -> Version -> Text -> Text -> Tree
addRename t since from to = t{renameLog = t.renameLog ++ [Rename since KIngredient (DottedSlug from) (DottedSlug to)]}

atOrBefore :: Version -> Gen Version
atOrBefore v = Gen.filter (<= v) genVersion

renameFromExists :: Tree -> Gen Tree
renameFromExists t = do
    from <- Gen.element (toList (ingIds t))
    -- a rename onto itself would also be a loop
    case filter (/= from) (toList (ingIds t)) of
        [] -> Gen.discard
        others -> do
            to <- Gen.element others
            since <- atOrBefore t.meta.version
            pure (addRename t since from to)

renameLoop :: Tree -> Gen Tree
renameLoop t = do
    slugs <- freshRenameSlugs t 2
    since <- atOrBefore t.meta.version
    case slugs of
        [a, b] -> pure (addRename (addRename t since a b) since b a)
        _ -> Gen.discard

renameToNothing :: Tree -> Gen Tree
renameToNothing t = do
    slugs <- freshRenameSlugs t 2
    since <- atOrBefore t.meta.version
    case slugs of
        [a, b] -> pure (addRename t since a b)
        _ -> Gen.discard

renameSince :: Tree -> Gen Tree
renameSince t = do
    slugs <- freshRenameSlugs t 1
    to <- Gen.element (toList (ingIds t))
    since <- Gen.filter (> t.meta.version) genVersion
    case slugs of
        [a] -> pure (addRename t since a to)
        _ -> Gen.discard

-- * File-level edits

-- | A described edit to the files on disk, applied after 'writeTree'.
data Edit
    = Write FilePath LByteString
    | Move FilePath FilePath
    deriving stock (Show)

applyEdit :: FilePath -> Edit -> IO ()
applyEdit root = \case
    Write rel bytes -> do
        createDirectoryIfMissing True (takeDirectory (root </> rel))
        BL.writeFile (root </> rel) bytes
    Move from to -> renameFile (root </> from) (root </> to)

fileRule :: TestName -> [Text] -> (Tree -> Gen Edit) -> TestTree
fileRule name expected mkEdit = prop name $ property $ do
    t <- forAll genTree
    e <- forAll (mkEdit t)
    r <- evalIO (withTree t (\root -> applyEdit root e >> validate False root))
    showDiagnostics r
    errorRules r === sort expected

{- | A decode failure in the edited file; other rules may cascade from it
(e.g. references to an entity that no longer decodes).
-}
decodeRule :: TestName -> (Tree -> Gen (FilePath, Value)) -> TestTree
decodeRule name mk = prop name $ property $ do
    t <- forAll genTree
    (rel, v) <- forAll (mk t)
    r <- evalIO (withTree t (\root -> applyEdit root (Write rel (Aeson.encode v)) >> validate False root))
    showDiagnostics r
    assert (any (\d -> d.rule == "decode" && maybe False ((toText rel `T.isSuffixOf`) . toText) d.file) r.diagnostics)

type JsonPath = [Either Int Text]

{- | Paths to every object in a value, except the ID-keyed maps (texture,
taste) whose keys are data rather than fields.
-}
objectPaths :: Value -> [JsonPath]
objectPaths = go []
  where
    go path = \case
        Object o ->
            path
                : concat
                    [ go (path ++ [Right (Key.toText k)]) v
                    | (k, v) <- KM.toList o
                    , Key.toText k `notElem` ["texture", "taste"]
                    ]
        Array a -> concat (zipWith (\i v -> go (path ++ [Left i]) v) [0 ..] (V.toList a))
        _ -> []

modifyAt :: JsonPath -> (Value -> Value) -> Value -> Value
modifyAt [] f v = f v
modifyAt (Right k : rest) f (Object o) = Object (KM.mapWithKey (\k' v -> if Key.toText k' == k then modifyAt rest f v else v) o)
modifyAt (Left i : rest) f (Array a) = Array (V.imap (\j v -> if i == j then modifyAt rest f v else v) a)
modifyAt _ _ v = v

setKey :: Text -> Value -> Value -> Value
setKey k new = \case
    Object o -> Object (KM.insert (Key.fromText k) new o)
    v -> v

fileProps :: [TestTree]
fileProps =
    [ fileRule "an unknown field at any depth" ["unknown-field"] $ \t -> do
        f <- Gen.element (entityFiles t)
        path <- Gen.element (objectPaths f.json)
        key <- ("x_" <>) <$> genSlug
        pure (Write f.path (Aeson.encode (modifyAt path (setKey key (Number 1)) f.json)))
    , fileRule "a non-canonically formatted file" ["format"] $ \t -> do
        f <- Gen.element (entityFiles t)
        pure (Write f.path (Aeson.encode f.json))
    , fileRule "a file name that is not its ID" ["filename"] $ \t -> do
        f <- Gen.element (entityFiles t)
        name <- genSlug
        pure (Move f.path (takeDirectory f.path </> toString ("renamed_" <> name <> ".json")))
    , fileRule "an invalid semver" ["decode"] $ \_ -> do
        v <- genBadVersion
        pure (Write "dataset.json" (Aeson.encode (Aeson.object ["version" Aeson..= v])))
    , fileRule "a stray non-JSON file" ["layout"] $ \t -> do
        f <- Gen.element (entityFiles t)
        pure (Write (takeDirectory f.path </> "notes.txt") "hello")
    , fileRule "an unknown licence folder" ["layout"] $ \_ ->
        pure (Write "layers/reviewed/CC-BY-NC-4.0/ingredients/x.json" "{}")
    , decodeRule "an ID that is not a slug" $ \t -> do
        f <- Gen.element (entityFiles t)
        bad <- genBadSlug
        pure (f.path, setKey "id" (String bad) f.json)
    , decodeRule "an empty output list" $ \t -> do
        f <- Gen.element [f | f <- entityFiles t, f.kind == "recipes"]
        pure (f.path, setKey "outputs" (Array mempty) f.json)
    , decodeRule "an unknown enum value" $ \t -> do
        f <- Gen.element [f | f <- entityFiles t, f.kind == "recipes"]
        word <- Gen.filter (`notElem` ["time", "coreTemp", "both"]) genSlug
        pure (f.path, modifyAt [Right "end"] (setKey "by" (String word)) f.json)
    , decodeRule "an intensity outside 0-3" $ \t -> do
        f <- Gen.element [f | f <- entityFiles t, f.kind == "ingredients"]
        n <- Gen.int (Range.constant 4 100)
        key <- genSlug
        pure (f.path, setKey "taste" (Aeson.object [Key.fromText key Aeson..= n]) f.json)
    , prop "--fix makes every file canonical" $ property $ do
        t <- forAll genTree
        fs <- forAll (Gen.subsequence (entityFiles t))
        r <- evalIO $ withTree t $ \root -> do
            mapM_ (\f -> applyEdit root (Write f.path (Aeson.encode f.json))) fs
            _ <- validate True root
            validate False root
        showDiagnostics r
        errorRules r === []
    ]

-- * Overlays

overlayProps :: [TestTree]
overlayProps =
    [ prop "a reviewed recipe replaces the extracted one with its sourceRef" $ property $ do
        t <- forAll genTree
        p <- forAll (Gen.element t.recs)
        copyId <- forAll (RecipeId <$> Gen.filter (`notElem` [idText x.entity.id | x <- t.recs]) genDottedSlug)
        let ref = fromMaybe (SourceRef p.entity.source 1000) p.entity.sourceRef
            reviewed = p.entity{sourceRef = Just ref}
            copy = let Recipe{..} = reviewed in Recipe{id = copyId, stateNote = Just "extracted", ..}
            others = [x | x <- t.recs, x.entity.id /= p.entity.id]
            t' = t{recs = others ++ [Placed Reviewed p.folder reviewed, Placed Extracted p.folder copy]}
        r <- check t'
        showDiagnostics r
        errorRules r === []
        ((.value) <$> Map.lookup reviewed.id r.world.recipes) === Just reviewed
        Map.member copyId r.world.recipes === False
    , prop "a reviewed ingredient replaces the extracted one with its ID" $ property $ do
        t <- forAll genTree
        p <- forAll (Gen.element t.ings)
        let copy = let Ingredient{..} = p.entity in Ingredient{label = "extracted " <> label, ..}
            others = [x | x <- t.ings, x.entity.id /= p.entity.id]
            t' = t{ings = others ++ [Placed Reviewed p.folder p.entity, Placed Extracted p.folder copy]}
        r <- check t'
        showDiagnostics r
        errorRules r === []
        ((.value) <$> Map.lookup p.entity.id r.world.ingredients) === Just p.entity
    ]

-- * Laws

-- | Nodes 0..n-1 with edges only to smaller nodes, so there is no cycle.
genDag :: Gen [(Int, [Int])]
genDag = do
    n <- Gen.int (Range.linear 1 12)
    forM [0 .. n - 1] $ \i -> (i,) <$> Gen.subsequence [0 .. i - 1]

isCycleIn :: [(Int, [Int])] -> [Int] -> Bool
isCycleIn edges c = not (null c) && and (zipWith edge c (drop 1 c ++ take 1 c))
  where
    adj = Map.fromListWith (++) edges
    edge a b = b `elem` Map.findWithDefault [] a adj

approx :: Double -> Double -> Bool
approx a b = abs (a - b) <= 1e-9 * max 1 (max (abs a) (abs b))

lawProps :: [TestTree]
lawProps =
    [ prop "findCycles finds nothing in a DAG" $ property $ do
        g <- forAll genDag
        findCycles g === []
    , prop "findCycles reports only real cycles, and finds one when it exists" $ property $ do
        g <- forAll genDag
        i <- forAll (Gen.element (map fst g))
        j <- forAll (Gen.element (map fst g))
        let g' = g ++ [(j, [i])]
            cs = findCycles g'
        annotateShow cs
        assert (all (isCycleIn g') cs)
        -- The new edge j -> i closes a cycle exactly when j is reachable from i.
        (j `Set.member` closure g' i) === not (null cs)
    , prop "generated slugs are slugs and round-trip" $ property $ do
        s <- forAll genSlug
        assert (isSlug s)
        (idText <$> (mkId s :: Either String IngredientId)) === Right s
    , prop "broken slugs are rejected" $ property $ do
        s <- forAll genBadSlug
        assert (not (isSlug s))
    , prop "versions round-trip; malformed ones are rejected" $ property $ do
        v <- forAll genVersion
        parseVersion (renderVersion v) === Right v
        bad <- forAll genBadVersion
        assert (isLeft (parseVersion bad))
    , prop "timeAt is the reference duration at the reference temperature" $ property $ do
        s <- forAll genSensitivity
        d <- forAll (Gen.double (Range.exponentialFloat 1 1e7))
        timeAt (Conditions s.refTempC Nothing) s d === Just d
    , prop "timeAt scales by q10 per 10 degrees" $ property $ do
        s <- forAll genSensitivity
        d <- forAll (Gen.double (Range.exponentialFloat 1 1e7))
        c <- forAll (Gen.double (Range.linearFrac (-30) 80))
        case (timeAt (Conditions c Nothing) s d, timeAt (Conditions (c + 10) Nothing) s d) of
            (Just a, Just b) -> assert (approx (a / b) s.q10)
            other -> annotateShow other >> failure
    , prop "timeAt happens exactly inside the water-activity bounds" $ property $ do
        s <- forAll genSensitivity
        aw <- forAll (Gen.double (Range.linearFrac (-0.5) 1.5))
        let inside = aw >= fromMaybe 0 s.minWaterActivity && aw <= fromMaybe 1 s.maxWaterActivity
        isJust (timeAt (Conditions 20 (Just aw)) s 1) === inside
    , prop "bandFromCelsius picks the band whose range holds the temperature" $ property $ do
        bands <- forAll genTempBands
        c <- forAll (Gen.double (Range.linearFrac (-100) 400))
        let bounds = [(e.id, e.belowC) | e <- tempBandEntries bands]
            b = bandFromCelsius bands c
            lower = viaNonEmpty last [x | (_, Just x) <- takeWhile ((/= b) . fst) bounds]
            upper = snd =<< find ((== b) . fst) bounds
        annotateShow (b, lower, upper)
        assert (maybe True (c >=) lower && maybe True (c <) upper)
    , prop "temperature bands must strictly increase and end open" $ property $ do
        bands <- forAll genTempBands
        let entries = tempBandEntries bands
        case entries of
            a : b : rest@(_ : _) ->
                -- swapping the first two bounds breaks strict increase
                assert (isLeft (mkTempBands (BandEntry a.id b.belowC : BandEntry b.id a.belowC : rest)))
            _ -> success
        -- closing the top band leaves temperatures with no band
        assert (isLeft (mkTempBands [BandEntry e.id (Just (fromMaybe 1e9 e.belowC)) | e <- entries]))
    ]

-- * Seed data

{- | These check the real data, not the code: the seed must validate, and
its spoilage values must give plausible answers.
-}
seedTests :: [TestTree]
seedTests =
    [ testCase "the seed data validates with no warnings" $ do
        r <- validate False seed
        map renderDiagnostic r.diagnostics @?= []
        licenceSpdx r.licence @?= "CC-BY-SA-4.0"
    , testCase "bread molds first at 20 °C, within days" $ do
        (r, after) <- first (Conditions 20 Nothing)
        idText r.id @?= "bread.mold"
        idText after.unit @?= "d"
        assertBool (show after.qty) (after.qty >= 1 && after.qty <= 10)
    , testCase "bread stales first when too dry to mold" $ do
        (r, _) <- first (Conditions 20 (Just 0.85))
        idText r.id @?= "bread.stale"
    , testCase "bread stales first in the fridge" $ do
        (r, _) <- first (Conditions 4 Nothing)
        idText r.id @?= "bread.stale"
    ]
  where
    seed = "../data"
    first c = do
        r <- validate False seed
        maybe (assertFailure "no spoilage found") pure (firstSpoilage r.world c (IngredientId "bread"))
