{-# LANGUAGE TypeFamilies #-}

{- | Walk a data root, decode every file through "Babel.Model", and merge
the layers into one 'World'. Every problem found on the way is collected
(with its file path) rather than stopping at the first.

Layout:

> dataset.json, renames.json
> registries/{units,media,forms,texture,taste,serving}.json
> layers/{extracted,reviewed}/<licence>/{sources,ingredients,techniques,recipes}/<id>.json
-}
module Babel.Load (
    Layer (..),
    Located (..),
    World (..),
    loadWorld,
    loadFile,
)
where

import Autodocodec (HasCodec, parseJSONViaCodec, toJSONViaCodec)
import Babel.Diagnostic
import Babel.Format (render)
import Babel.Model
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
import System.FilePath (takeBaseName, takeExtension, (</>))

data Layer = Extracted | Reviewed
    deriving stock (Eq, Ord, Show)

-- | A decoded entity and where it came from.
data Located a = Located
    { value :: a
    , path :: FilePath
    , layer :: Layer
    , licence :: Licence
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
    , sources :: Map SourceId (Located Source)
    , ingredients :: Map IngredientId (Located Ingredient)
    , techniques :: Map TechniqueId (Located Technique)
    , recipes :: Map RecipeId (Located Recipe)
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
    (lDiags, raw) <- loadLayers fixFiles (root </> "layers")
    let (srcDiags, sources') = mergeById "source" (.id) raw.rawSources
        (ingDiags, ingredients') = mergeById "ingredient" (.id) raw.rawIngredients
        (tecDiags, techniques') = mergeById "technique" (.id) raw.rawTechniques
        (recDiags, recipes') = mergeRecipes raw.rawRecipes
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
            , lDiags
            , srcDiags
            , ingDiags
            , tecDiags
            , recDiags
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
            , sources = sources'
            , ingredients = ingredients'
            , techniques = techniques'
            , recipes = recipes'
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

instance (IsId i) => HasId (Term i) where
    type IdOf (Term i) = i
    getId t = t.id

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
    }

instance Semigroup Raw where
    Raw a b c d <> Raw a' b' c' d' = Raw (a ++ a') (b ++ b') (c ++ c') (d ++ d')

instance Monoid Raw where
    mempty = Raw [] [] [] []

loadLayers :: Bool -> FilePath -> IO ([Diagnostic], Raw)
loadLayers fixFiles layersDir = do
    exists <- doesDirectoryExist layersDir
    if not exists
        then pure ([errorIn "layout" (Just layersDir) "missing layers directory"], mempty)
        else do
            results <- forM' layersDir $ \layerName layerPath -> case layerName of
                "extracted" -> loadLayer Extracted layerPath
                "reviewed" -> loadLayer Reviewed layerPath
                _ -> pure (unexpected layerPath "layer (expected extracted or reviewed)", mempty)
            pure (mconcat results)
  where
    loadLayer layer layerPath =
        mconcat
            <$> forM'
                layerPath
                ( \licName licPath -> case licenceFromSpdx (T.pack licName) of
                    Nothing -> pure (unexpected licPath "licence folder (expected an SPDX ID this dataset knows)", mempty)
                    Just lic ->
                        mconcat
                            <$> forM'
                                licPath
                                ( \kindName kindPath -> case kindName of
                                    "sources" -> (\(d, xs) -> (d, mempty{rawSources = xs})) <$> entities layer lic kindPath (\s -> idText s.id)
                                    "ingredients" -> (\(d, xs) -> (d, mempty{rawIngredients = xs})) <$> entities layer lic kindPath (\i -> idText i.id)
                                    "techniques" -> (\(d, xs) -> (d, mempty{rawTechniques = xs})) <$> entities layer lic kindPath (\t -> idText t.id)
                                    "recipes" -> (\(d, xs) -> (d, mempty{rawRecipes = xs})) <$> entities layer lic kindPath (\r -> idText r.id)
                                    _ -> pure (unexpected kindPath "entity folder (expected sources, ingredients, techniques or recipes)", mempty)
                                )
                )

    entities :: (HasCodec a) => Layer -> Licence -> FilePath -> (a -> Text) -> IO ([Diagnostic], [Located a])
    entities layer lic dir idOf = do
        names <- sort <$> listDirectory dir
        results <- forM names $ \name -> do
            let p = dir </> name
            isFile <- doesFileExist p
            if not isFile || takeExtension name /= ".json"
                then pure (unexpected p "entry (expected <id>.json files only)", Nothing)
                else do
                    (diags, decoded) <- loadFile fixFiles p
                    pure $ case decoded of
                        Nothing -> (diags, Nothing)
                        Just a ->
                            let nameDiags =
                                    [ errorIn "filename" (Just p) ("file name must be " <> idOf a <> ".json")
                                    | T.pack (takeBaseName name) /= idOf a
                                    ]
                             in (diags ++ nameDiags, Just (Located a p layer lic))
        pure (concatMap fst results, mapMaybe snd results)

    unexpected p what = [errorIn "layout" (Just p) ("unexpected " <> what)]

-- | Run an action on each subdirectory (anything else is a layout error).
forM' :: FilePath -> (FilePath -> FilePath -> IO ([Diagnostic], Raw)) -> IO [([Diagnostic], Raw)]
forM' dir act = do
    names <- sort <$> listDirectory dir
    dirs <- filterM (doesDirectoryExist . (dir </>)) names
    let stray = [n | n <- names, n `notElem` dirs]
    rest <- forM dirs (\n -> act n (dir </> n))
    pure ((concat [[errorIn "layout" (Just (dir </> n)) "unexpected file (expected a folder)"] | n <- stray], mempty) : rest)

-- * Overlays

{- | Within one layer, a key may live in only one licence folder. Across
layers, reviewed replaces extracted.
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
        , let licences = ordNub [y.licence | y <- kept : others]
              (rule, msg)
                | length licences > 1 =
                    ( "overlay-conflict"
                    , "the same " <> kind <> " " <> showKey k <> " is in two licence folders of one layer (also " <> toText kept.path <> ")"
                    )
                | otherwise =
                    ("duplicate-key", "duplicate " <> kind <> " " <> showKey k <> " (also " <> toText kept.path <> ")")
        , x <- others
        ]

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
