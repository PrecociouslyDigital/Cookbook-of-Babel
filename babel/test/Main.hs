{-# LANGUAGE OverloadedRecordDot #-}

{- | The seed data must pass. Each mutation copies it to a temporary
directory, breaks exactly one rule, and checks that validation reports
exactly the expected error rules.
-}
module Main (main) where

import Babel.Diagnostic
import Babel.Format (render)
import Babel.Load (World (..))
import Babel.Model
import Babel.Spoilage
import Babel.Validate
import Control.Monad (forM_, unless)
import Data.Aeson (Value (..), eitherDecodeFileStrict)
import Data.Aeson qualified as Aeson
import Data.Aeson.Key qualified as Key
import Data.Aeson.KeyMap qualified as KM
import Data.ByteString qualified as BS
import Data.ByteString.Lazy qualified as BL
import Data.List (nub, sort)
import Data.Map.Strict qualified as Map
import Data.Maybe (fromMaybe)
import Data.Text (Text)
import Data.Text qualified as T
import Data.Vector qualified as V
import System.Directory
import System.FilePath ((</>))
import System.IO (hSetEncoding, stdout, utf8)
import System.IO.Temp (withSystemTempDirectory)
import Test.Tasty
import Test.Tasty.HUnit
import Text.Read (readMaybe)

seed :: FilePath
seed = "../data"

orig, sa :: FilePath
orig = "layers/reviewed/LicenseRef-original"
sa = "layers/reviewed/CC-BY-SA-4.0"

main :: IO ()
main = do
    hSetEncoding stdout utf8
    defaultMain $
        testGroup
            "babel"
            [ testCase "seed data passes with no warnings" $ do
                r <- validate False seed
                map renderDiagnostic r.diagnostics @?= []
                licenceSpdx r.licence @?= "CC-BY-SA-4.0"
            , testGroup "decoding" (map mutation decoding)
            , testGroup "references and layout" (map mutation referencesAndLayout)
            , testGroup "graph and values" (map mutation graphAndValues)
            , testGroup "versioning" (map mutation versioning)
            , testGroup "overlays" overlays
            , testGroup "formatting" formatting
            , testGroup "soft checks" softChecks
            , testGroup "spoilage" spoilage
            ]

-- * Mutations

data Mutation = Mutation String [Text] (FilePath -> IO ())

mutation :: Mutation -> TestTree
mutation (Mutation name expected act) = testCase name $ withSeedCopy $ \root -> do
    act root
    r <- validate False root
    let rules = sort (nub [d.rule | d <- r.diagnostics, isError d])
    unless (rules == sort expected) $
        assertFailure . T.unpack . T.unlines $
            ("expected error rules " <> T.pack (show expected) <> ", got:") : map renderDiagnostic r.diagnostics

decoding :: [Mutation]
decoding =
    [ Mutation "bad ID pattern" ["decode"] $
        edit (orig </> "recipes/bread.croutons.json") ["technique"] (String "Toast")
    , Mutation "unknown enum value" ["decode"] $
        edit (orig </> "recipes/stir_fried_potatoes.shred.json") ["outputs", "1", "role"] (String "garnish")
    , Mutation "extra field" ["unknown-field"] $
        edit (orig </> "ingredients/potato.json") ["nutrition", "per100g", "fibreG"] (Number 2)
    , Mutation "empty outputs" ["decode"] $
        edit (orig </> "recipes/bread.mold.json") ["outputs"] (Array mempty)
    , Mutation "intensity out of range" ["decode"] $
        edit (orig </> "ingredients/russet_potato.json") ["texture", "hardness"] (Number 4)
    ]

referencesAndLayout :: [Mutation]
referencesAndLayout =
    [ Mutation "dangling reference" ["dangling-ref"] $
        edit (orig </> "recipes/bread.croutons.json") ["technique"] (String "flambe")
    , Mutation "unknown texture attribute" ["dangling-ref"] $
        edit (orig </> "ingredients/potato.json") ["texture", "crunchiness"] (Number 1)
    , Mutation "same key in two licence folders" ["overlay-conflict"] $ \root -> do
        createDirectoryIfMissing True (root </> sa </> "ingredients")
        copyFile (root </> orig </> "ingredients/salt.json") (root </> sa </> "ingredients/salt.json")
    , Mutation "recipe source licence differs from its folder" ["licence-mismatch"] $ \root ->
        renameFile (root </> sa </> "recipes/pancakes.cook.json") (root </> orig </> "recipes/pancakes.cook.json")
    , Mutation "source licence differs from its folder" ["licence-mismatch"] $
        edit (sa </> "sources/wikibooks_pancakes_north_american.json") ["licence"] (String "CC-BY-4.0")
    , Mutation "non-canonical formatting" ["format"] $ \root -> do
        let p = root </> orig </> "ingredients/salt.json"
        v <- readJson p
        BL.writeFile p (Aeson.encode (v :: Value))
    , Mutation "file name differs from ID" ["filename"] $ \root ->
        renameFile (root </> orig </> "ingredients/salt.json") (root </> orig </> "ingredients/sel.json")
    , Mutation "unknown licence folder" ["layout"] $ \root ->
        createDirectory (root </> "layers/reviewed/CC-BY-NC-4.0")
    , Mutation "duration in a non-time unit" ["dimension"] $
        edit (orig </> "recipes/bread.croutons.json") ["end", "duration", "unit"] (String "g")
    ]

graphAndValues :: [Mutation]
graphAndValues =
    [ Mutation "min > max" ["select-bounds"] $ \root -> do
        let p = orig </> "recipes/stir_fried_potatoes.stir_fry.json"
        edit p ["inputs", "of", "4", "min"] (Number 2) root
    , Mutation "is-a cycle" ["isa-cycle"] $
        edit (orig </> "ingredients/tuber.json") ["parents"] (Aeson.toJSON [Aeson.object ["target" Aeson..= ("russet_potato" :: Text), "axis" Aeson..= ("culinary" :: Text)]])
    , Mutation "technique is-a cycle" ["isa-cycle"] $
        edit (orig </> "techniques/cut.json") ["parents"] (Aeson.toJSON ["julienne" :: Text])
    , Mutation "zero weight" ["weight"] $
        edit (orig </> "recipes/fresh_cheese.age.json") ["outputs", "oneOf", "1", "weight"] (Number 0)
    , Mutation "chance > 1" ["chance"] $
        edit (orig </> "recipes/stir_fried_potatoes.shred.json") ["outputs", "1", "chance"] (Number 1.5)
    , Mutation "all-chance branch" ["all-chance"] $
        edit (orig </> "recipes/fresh_cheese.age.json") ["outputs", "oneOf", "0", "outputs", "0", "chance"] (Number 0.5)
    , Mutation "ambient time end without sensitivity" ["ambient-time"] $
        remove (orig </> "recipes/bread.mold.json") ["end", "sensitivity"]
    , Mutation "water-activity bounds inverted" ["water-activity"] $
        edit (orig </> "recipes/bread.mold.json") ["end", "sensitivity", "maxWaterActivity"] (Number 0.5)
    ]

versioning :: [Mutation]
versioning =
    [ Mutation "rename whose from still exists" ["rename-from-exists"] $
        renames [rename "0.1.0" "potato" "russet_potato"]
    , Mutation "rename loop" ["rename-loop", "rename-to-missing"] $
        renames [rename "0.1.0" "spud" "tater", rename "0.1.0" "tater" "spud"]
    , Mutation "rename to nothing" ["rename-to-missing"] $
        renames [rename "0.1.0" "spud" "tater"]
    , Mutation "rename since after version" ["rename-since"] $
        renames [rename "0.2.0" "spud" "potato"]
    , Mutation "invalid semver" ["decode"] $
        edit "dataset.json" ["version"] (String "1.0")
    , Mutation "a rename chain that ends at an existing slug passes" [] $
        renames [rename "0.1.0" "spud" "tater", rename "0.1.0" "tater" "potato"]
    ]
  where
    renames rs root = BL.writeFile (root </> "renames.json") (render (Aeson.toJSON rs))
    rename since from to =
        Aeson.object ["since" Aeson..= (since :: Text), "kind" Aeson..= ("ingredient" :: Text), "from" Aeson..= (from :: Text), "to" Aeson..= (to :: Text)]

-- * Overlays and formatting

overlays :: [TestTree]
overlays =
    [ testCase "a reviewed recipe replaces the extracted one with the same sourceRef" $ withSeedCopy $ \root -> do
        let extracted = "layers/extracted/CC-BY-SA-4.0/recipes"
        createDirectoryIfMissing True (root </> extracted)
        v <- readJson (root </> sa </> "recipes/pancakes.cook.json")
        let v' = setAt ["id"] (String "pancakes.step_6") (setAt ["stateNote"] (String "raw extraction") v)
        BL.writeFile (root </> extracted </> "pancakes.step_6.json") (render v')
        r <- validate False root
        map renderDiagnostic (filter isError r.diagnostics) @?= []
        assertBool "the extracted recipe is overridden" (Map.notMember (RecipeId "pancakes.step_6") r.world.recipes)
        assertBool "the reviewed recipe is kept" (Map.member (RecipeId "pancakes.cook") r.world.recipes)
    ]

formatting :: [TestTree]
formatting =
    [ testCase "--fix rewrites non-canonical files" $ withSeedCopy $ \root -> do
        let p = root </> orig </> "ingredients/salt.json"
        original <- BS.readFile p
        v <- readJson p
        BL.writeFile p (Aeson.encode (v :: Value))
        _ <- validate True root
        r <- validate False root
        map renderDiagnostic r.diagnostics @?= []
        rewritten <- BS.readFile p
        rewritten @?= original
    ]

-- * Soft checks

softChecks :: [TestTree]
softChecks =
    [ softCheck "calories inconsistent with macros" "calories" $
        edit (orig </> "ingredients/potato.json") ["nutrition", "per100g", "kcal"] (Number 300)
    , softCheck "implausible mass ratio" "ratio" $
        edit (orig </> "recipes/bread.croutons.json") ["outputs", "0", "amount", "qty"] (Number 2000)
    ]
  where
    softCheck :: String -> Text -> (FilePath -> IO ()) -> TestTree
    softCheck name rule act = testCase name $ withSeedCopy $ \root -> do
        act root
        r <- validate False root
        map renderDiagnostic (filter isError r.diagnostics) @?= []
        [d.rule | d <- r.diagnostics] @?= [rule]

-- * Spoilage

spoilage :: [TestTree]
spoilage =
    [ testCase "at 20 °C mold wins, after a few days" $ do
        (r, days) <- first (Conditions 20 Nothing)
        idText r.id @?= "bread.mold"
        assertBool ("days on the order of 1-10, got " <> show days) (days >= 1 && days <= 10)
    , testCase "at low water activity staling wins" $ do
        (r, _) <- first (Conditions 20 (Just 0.85))
        idText r.id @?= "bread.stale"
    , testCase "in the fridge staling wins" $ do
        (r, days) <- first (Conditions 4 Nothing)
        idText r.id @?= "bread.stale"
        assertBool ("days on the order of 1-10, got " <> show days) (days >= 1 && days <= 10)
    , testCase "spoilage paths of bread are mold and staling" $ do
        r <- validate False seed
        sort [idText x.id | x <- spoilagePaths r.world (IngredientId "bread")] @?= ["bread.mold", "bread.stale"]
    , testCase "temperature bands" $
        map bandFromCelsius [-18, 4, 20, 45, 80, 100, 190] @?= [Frozen, Chilled, Ambient, Warm, Hot, Boiling, Boiling]
    ]
  where
    first c = do
        r <- validate False seed
        maybe (assertFailure "no spoilage found") pure (firstSpoilage r.world c (IngredientId "bread"))

-- * Helpers

withSeedCopy :: (FilePath -> IO a) -> IO a
withSeedCopy act = withSystemTempDirectory "babel-test" $ \tmp -> do
    let root = tmp </> "data"
    copyTree seed root
    act root

copyTree :: FilePath -> FilePath -> IO ()
copyTree from to = do
    createDirectoryIfMissing True to
    names <- listDirectory from
    forM_ names $ \n -> do
        isDir <- doesDirectoryExist (from </> n)
        if isDir then copyTree (from </> n) (to </> n) else copyFile (from </> n) (to </> n)

readJson :: FilePath -> IO Value
readJson p = eitherDecodeFileStrict p >>= either (assertFailure . (("bad JSON in " <> p <> ": ") <>)) pure

{- | Set the value at a path (numeric segments index arrays) and write the
file back canonically, so only the intended rule breaks.
-}
edit :: FilePath -> [Text] -> Value -> FilePath -> IO ()
edit rel path new root = do
    let p = root </> rel
    v <- readJson p
    BL.writeFile p (render (setAt path new v))

remove :: FilePath -> [Text] -> FilePath -> IO ()
remove rel path root = do
    let p = root </> rel
    v <- readJson p
    BL.writeFile p (render (removeAt path v))

setAt :: [Text] -> Value -> Value -> Value
setAt [] new _ = new
setAt (k : rest) new v = case (v, readMaybe (T.unpack k)) of
    (Array a, Just i) | i < V.length a -> Array (a V.// [(i, setAt rest new (a V.! i))])
    (Object o, _) -> Object (KM.insert key (setAt rest new (fromMaybe Null (KM.lookup key o))) o)
    _ -> v
  where
    key = Key.fromText k

removeAt :: [Text] -> Value -> Value
removeAt [k] (Object o) = Object (KM.delete (Key.fromText k) o)
removeAt (k : rest) (Object o) = case KM.lookup (Key.fromText k) o of
    Just child -> Object (KM.insert (Key.fromText k) (removeAt rest child) o)
    Nothing -> Object o
removeAt _ v = v
