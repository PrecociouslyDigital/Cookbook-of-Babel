{-# LANGUAGE TypeFamilies #-}

{- | Walk a data root, decode every file through "Babel.Model", and merge
the layers into one 'World'. Every problem found on the way is collected
(with its file path) rather than stopping at the first.

Layout:

> dataset.json, renames.json
> registries/{units,media,forms,texture,taste,serving,colors}.json
> layers/  (see "Babel.Layout")
-}
module Babel.Load (
    Located (..),
    World (..),
    loadWorld,
    withoutExtracted,
    loadFile,
)
where

import Autodocodec (HasCodec, parseJSONViaCodec, toJSONViaCodec)
import Babel.Codec (IsId (..))
import Babel.Diagnostic (Diagnostic, errorIn)
import Babel.Format (render)
import Babel.Layout (Kind (..), Layer (..), Placement (..), placementFolders, placements)
import Babel.Model.Constants (Constants)
import Babel.Model.Dataset (Dataset, Rename)
import Babel.Model.Id (FormId, IngredientId, MediumId, RecipeId, ServingTagId, SourceId, TasteAttrId, TechniqueId, TextureAttrId, TintId, UnitId)
import Babel.Model.Ingredient (Ingredient (..))
import Babel.Model.Measure (Measure (..), MeasureKey, measureKey, measureStem)
import Babel.Model.Recipe (Recipe (..), SourceRef (..))
import Babel.Model.Registry (Medium (..), Term (..), Tint (..), Unit (..))
import Babel.Model.Source (Origin, Source (..))
import Babel.Model.Technique (Technique (..))
import Data.Aeson (Value (..))
import Data.Aeson qualified as Aeson
import Data.Aeson.Key qualified as Key
import Data.Aeson.KeyMap qualified as KM
import Data.Aeson.Types (parseEither)
import Data.ByteString qualified as BS
import Data.ByteString.Lazy qualified as BL
import Data.Map.Strict qualified as Map
import Data.Text qualified as T
import Data.Vector qualified as V
import System.Directory (doesDirectoryExist, doesFileExist, listDirectory)
import System.FilePath (joinPath, takeBaseName, takeExtension, (</>))

-- | A decoded entity and where it came from.
data Located a = Located
    { value :: a
    , path :: FilePath
    , layer :: Layer
    , origin :: Origin
    }
    deriving stock (Show)

{- | The merged dataset: reviewed entities have replaced the extracted ones
they overlay.
-}
data World = World
    { dataset :: Maybe Dataset
    , renames :: [Rename]
    , constants :: Maybe Constants
    , units :: Map UnitId Unit
    , media :: Map MediumId Medium
    , forms :: Map FormId (Term FormId)
    , texture :: Map TextureAttrId (Term TextureAttrId)
    , taste :: Map TasteAttrId (Term TasteAttrId)
    , serving :: Map ServingTagId (Term ServingTagId)
    , colors :: Map TintId Tint
    , sources :: Map SourceId (Located Source)
    , ingredients :: Map IngredientId (Located Ingredient)
    , techniques :: Map TechniqueId (Located Technique)
    , recipes :: Map RecipeId (Located Recipe)
    , measures :: Map (IngredientId, MeasureKey) (Located Measure)
    }

{- | Load the data root. With @fixFiles@, files that decode cleanly but are not
canonically formatted are rewritten instead of reported.
-}
loadWorld :: Bool -> FilePath -> IO ([Diagnostic], World)
loadWorld fixFiles root = do
    (dDiags, dataset') <- required "dataset.json"
    (rDiags, renames') <- required "renames.json"
    (cDiags, constants') <- required ("registries" </> "constants.json")
    (uDiags, units') <- registry "units"
    (mDiags, media') <- registry "media"
    (fDiags, forms') <- registry "forms"
    (xDiags, texture') <- registry "texture"
    (tDiags, taste') <- registry "taste"
    (sDiags, serving') <- registry "serving"
    (kDiags, colors') <- registry "colors"
    (lDiags, raw) <- loadLayers fixFiles (root </> "layers")
    let (srcDiags, sources') = mergeById "source" (.id) raw.rawSources
        (ingDiags, ingredients') = mergeById "ingredient" (.id) raw.rawIngredients
        (tecDiags, techniques') = mergeById "technique" (.id) raw.rawTechniques
        (recDiags, recipes') = mergeRecipes raw.rawRecipes
        (meaDiags, measures') = mergeLayer "measure" (uncurry measureStem) (keyOf . (.value)) raw.rawMeasures
    pure
        ( concat
            [ dDiags
            , rDiags
            , cDiags
            , uDiags
            , mDiags
            , fDiags
            , xDiags
            , tDiags
            , sDiags
            , kDiags
            , lDiags
            , srcDiags
            , ingDiags
            , tecDiags
            , recDiags
            , meaDiags
            ]
        , World
            { dataset = dataset'
            , renames = fromMaybe [] renames'
            , constants = constants'
            , units = units'
            , media = media'
            , forms = forms'
            , texture = texture'
            , taste = taste'
            , serving = serving'
            , colors = colors'
            , sources = sources'
            , ingredients = ingredients'
            , techniques = techniques'
            , recipes = recipes'
            , measures = measures'
            }
        )
  where
    required :: (HasCodec a) => FilePath -> IO ([Diagnostic], Maybe a)
    required name = do
        let p = root </> name
        exists <- doesFileExist p
        if exists
            then loadFile fixFiles p
            else pure ([errorIn "layout" (Just p) "required file is missing"], Nothing)

    registry :: (HasCodec a, HasId a) => FilePath -> IO ([Diagnostic], Map (IdOf a) a)
    registry name = do
        let rel = "registries" </> (name <> ".json")
            p = root </> rel
        (diags, entries) <- required rel
        let xs = fromMaybe [] entries
            dupes = [k | (k, n) <- Map.toList (Map.fromListWith (+) [(getId x, 1 :: Int) | x <- xs]), n > 1]
        pure
            ( diags ++ [errorIn "duplicate-id" (Just p) ("duplicate ID " <> idText k) | k <- dupes]
            , Map.fromList [(getId x, x) | x <- xs]
            )

-- | Registry entries all carry an ID; this lets 'registry' index them.
class (IsId (IdOf a)) => HasId a where
    type IdOf a
    getId :: a -> IdOf a

instance HasId Unit where
    type IdOf Unit = UnitId
    getId u = u.id

instance HasId Medium where
    type IdOf Medium = MediumId
    getId m = m.id

instance HasId Tint where
    type IdOf Tint = TintId
    getId t = t.id

instance (IsId i) => HasId (Term i) where
    type IdOf (Term i) = i
    getId t = t.id

{- | The world as an extractor sees it: without the entities it
previously extracted for this origin, so a rerun doesn't treat its
last output as existing data.
-}
withoutExtracted :: Origin -> World -> World
withoutExtracted o w =
    World
        { dataset = w.dataset
        , renames = w.renames
        , constants = w.constants
        , units = w.units
        , media = w.media
        , forms = w.forms
        , texture = w.texture
        , taste = w.taste
        , serving = w.serving
        , sources = Map.filter kept w.sources
        , ingredients = Map.filter kept w.ingredients
        , techniques = Map.filter kept w.techniques
        , recipes = Map.filter kept w.recipes
        , measures = Map.filter kept w.measures
        }
  where
    kept :: Located a -> Bool
    kept x = not (x.layer == Extracted && x.origin == o)

-- * Single files

{- | Read one file and decode it. Besides decode errors this reports
fields the decoder ignored (unknown fields) and non-canonical formatting.
-}
loadFile :: forall a. (HasCodec a) => Bool -> FilePath -> IO ([Diagnostic], Maybe a)
loadFile fixFiles p = do
    bytes <- BL.fromStrict <$> BS.readFile p
    case Aeson.eitherDecode bytes of
        Left err -> pure ([errorIn "json" (Just p) (T.pack err)], Nothing)
        Right input -> case decode input of
            Left err -> pure ([errorIn "decode" (Just p) (T.pack err)], Nothing)
            Right a -> do
                let unknown = unknownFields decode input (toJSONViaCodec a)
                    canonical = render a
                formatDiags <-
                    if not (null unknown) || canonical == bytes
                        then pure []
                        else
                            if fixFiles
                                then [] <$ BS.writeFile p (BL.toStrict canonical)
                                else pure [errorIn "format" (Just p) "not canonically formatted (run `babel validate --fixFiles`)"]
                pure
                    ( [errorIn "unknown-field" (Just p) ("unknown field " <> renderPath k) | k <- unknown]
                        ++ formatDiags
                    , Just a
                    )
  where
    decode :: Value -> Either String a
    decode = parseEither parseJSONViaCodec

type Path = [Either Int Text]

renderPath :: Path -> Text
renderPath = T.concat . map (either (\i -> "[" <> T.pack (show i) <> "]") ("." <>))

{- | autodocodec ignores keys it does not know. Find them: walk the input
alongside its re-encoding, and for each key the re-encoding lacks,
replace its value with one no field accepts (@[null]@). If the input
still decodes, the decoder never looked at that key. (A known key that
merely holds its default value fails this probe, and is reported as a
formatting issue instead.)
-}
unknownFields :: (Value -> Either String a) -> Value -> Value -> [Path]
unknownFields decode input = go [] input
  where
    go path (Object i) (Object e) =
        concat
            [ case KM.lookup k e of
                Just ev -> go (path ++ [Right (Key.toText k)]) iv ev
                Nothing ->
                    [ path ++ [Right (Key.toText k)]
                    | isRight (decode (setAt (path ++ [Right (Key.toText k)]) probe input))
                    ]
            | (k, iv) <- KM.toList i
            ]
    go path (Array i) (Array e)
        | V.length i == V.length e =
            concat (V.toList (V.izipWith (\n iv ev -> go (path ++ [Left n]) iv ev) i e))
    go _ _ _ = []
    probe = Array (V.singleton Null)

setAt :: Path -> Value -> Value -> Value
setAt [] new _ = new
setAt (Right k : rest) new (Object o) =
    Object (runIdentity (KM.alterF (Identity . fmap (setAt rest new)) (Key.fromText k) o))
setAt (Left n : rest) new (Array a)
    | n >= 0 && n < V.length a = Array (a V.// [(n, setAt rest new (a V.! n))])
setAt _ _ v = v

-- * Layers

data Raw = Raw
    { rawSources :: [Located Source]
    , rawIngredients :: [Located Ingredient]
    , rawTechniques :: [Located Technique]
    , rawRecipes :: [Located Recipe]
    , rawMeasures :: [Located Measure]
    }

instance Semigroup Raw where
    Raw a b c d e <> Raw a' b' c' d' e' = Raw (a ++ a') (b ++ b') (c ++ c') (d ++ d') (e ++ e')

instance Monoid Raw where
    mempty = Raw [] [] [] [] []

loadLayers :: Bool -> FilePath -> IO ([Diagnostic], Raw)
loadLayers fixFiles layersDir = do
    exists <- doesDirectoryExist layersDir
    if not exists
        then pure ([errorIn "layout" (Just layersDir) "missing layers directory"], mempty)
        else do
            strays <- layoutErrors layersDir
            results <- forM placements $ \p -> do
                let dir = layersDir </> joinPath [toString folder | (_, folder) <- placementFolders p]
                present <- doesDirectoryExist dir
                if present then loadPlacement p dir else pure mempty
            pure ((strays, mempty) <> mconcat results)
  where
    loadPlacement p dir = case p.kind of
        Sources -> (\(d, xs) -> (d, mempty{rawSources = xs})) <$> entities p dir (\s -> idText s.id)
        Ingredients -> (\(d, xs) -> (d, mempty{rawIngredients = xs})) <$> entities p dir (\i -> idText i.id)
        Techniques -> (\(d, xs) -> (d, mempty{rawTechniques = xs})) <$> entities p dir (\t -> idText t.id)
        Recipes -> (\(d, xs) -> (d, mempty{rawRecipes = xs})) <$> entities p dir (\r -> idText r.id)
        Measures -> (\(d, xs) -> (d, mempty{rawMeasures = xs})) <$> entities p dir (uncurry measureStem . keyOf)

    entities :: (HasCodec a) => Placement -> FilePath -> (a -> Text) -> IO ([Diagnostic], [Located a])
    entities placement dir idOf = do
        names <- sort <$> listDirectory dir
        results <- forM names $ \name -> do
            let p = dir </> name
            isFile <- doesFileExist p
            if not isFile || takeExtension name /= ".json"
                then pure ([errorIn "layout" (Just p) "unexpected entry (expected <id>.json files only)"], Nothing)
                else do
                    (diags, decoded) <- loadFile fixFiles p
                    pure $ case decoded of
                        Nothing -> (diags, Nothing)
                        Just a ->
                            let nameDiags =
                                    [ errorIn "filename" (Just p) ("file name must be " <> idOf a <> ".json")
                                    | T.pack (takeBaseName name) /= idOf a
                                    ]
                             in (diags ++ nameDiags, Just (Located a p placement.layer placement.origin))
        pure (concatMap fst results, mapMaybe snd results)

-- | Everything under @layers/@ that is not on the way to a placement's folder.
layoutErrors :: FilePath -> IO [Diagnostic]
layoutErrors = go (map placementFolders placements)
  where
    -- @below@ holds the rest of the path to every placement under @dir@.
    go below dir = case [next | next : _ <- below] of
        [] -> pure []
        level@((axis, _) : _) -> do
            names <- sort <$> listDirectory dir
            fmap concat . forM names $ \name -> do
                let p = dir </> name
                isDir <- doesDirectoryExist p
                case [rest | (_, folder) : rest <- below, toString folder == name] of
                    _ | not isDir -> pure [errorIn "layout" (Just p) "unexpected file (expected a folder)"]
                    [] ->
                        pure
                            [ errorIn "layout" (Just p) $
                                "unexpected " <> axis <> " folder here (expected: " <> T.intercalate ", " (ordNub (map snd level)) <> ")"
                            ]
                    rests -> go rests p

-- * Overlays

{- | Within one layer, a key may live in only one origin folder.
Across layers, reviewed replaces extracted.
-}
mergeLayer :: (Ord k) => Text -> (k -> Text) -> (Located a -> k) -> [Located a] -> ([Diagnostic], Map k (Located a))
mergeLayer kind showKey key xs = (diags, merged)
  where
    byLayer layer = Map.fromListWith (flip (++)) [(key x, [x]) | x <- xs, x.layer == layer]
    pick layer = Map.mapMaybe (\case y : _ -> Just y; [] -> Nothing) (byLayer layer)
    merged = Map.union (pick Reviewed) (pick Extracted)
    diags =
        [ errorIn rule (Just x.path) msg
        | layer <- [Extracted, Reviewed]
        , (k, kept : others) <- Map.toList (byLayer layer)
        , let folders = ordNub [y.origin | y <- kept : others]
              (rule, msg)
                | length folders > 1 =
                    ( "overlay-conflict"
                    , "the same " <> kind <> " " <> showKey k <> " is in two folders of one layer (also " <> toText kept.path <> ")"
                    )
                | otherwise =
                    ("duplicate-key", "duplicate " <> kind <> " " <> showKey k <> " (also " <> toText kept.path <> ")")
        , x <- others
        ]

-- | A measure is keyed by its ingredient and what it measures.
keyOf :: Measure -> (IngredientId, MeasureKey)
keyOf m = (m.ingredient, measureKey m.value)

mergeById :: (IsId k) => Text -> (a -> k) -> [Located a] -> ([Diagnostic], Map k (Located a))
mergeById kind idOf = mergeLayer kind idText (\x -> idOf x.value)

{- | A reviewed recipe replaces the extracted one with the same 'sourceRef',
or, when it has none, the one with the same ID.
-}
mergeRecipes :: [Located Recipe] -> ([Diagnostic], Map RecipeId (Located Recipe))
mergeRecipes xs = (overlayDiags ++ idDiags, Map.fromList [(r.value.id, r) | r <- merged])
  where
    overlayKey r = maybe (Right r.value.id) Left r.value.sourceRef
    showKey = either (\s -> "with sourceRef " <> idText s.source <> "#" <> T.pack (show s.step)) idText
    (overlayDiags, byKey) = mergeLayer "recipe" showKey overlayKey xs
    merged = Map.elems byKey
    idDiags =
        [ errorIn "duplicate-id" (Just r.path) ("recipe ID " <> idText k <> " is also used by " <> toText kept.path)
        | (k, kept : others) <- Map.toList (Map.fromListWith (flip (++)) [(r.value.id, [r]) | r <- merged])
        , r <- others
        ]
