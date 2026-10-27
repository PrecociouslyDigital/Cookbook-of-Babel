{-# LANGUAGE RecordWildCards #-}

{- | The whole extraction as a pure function of the FoodKeeper file, the
committed tables, the dataset's ingredients and the decisions answered so
far: a spoilage recipe for each matched ingredient and place where it
keeps, the requests still to send, and a report row per ingredient.

Only raw ingredients, the ones no recipe makes, are asked about. Whatever
a recipe makes, a preparation included, spoils by its own recipes or
inherits its base's.
-}
module FoodKeeper.Extract (
    Env (..),
    rawIngredients,
    describeAll,
    Extraction (..),
    Row (..),
    Status (..),
    statusName,
    extract,
    writeLayer,
    renderReport,
)
where

import Autodocodec (HasCodec)
import Babel.Codec (IsId (..))
import Babel.Format (render)
import Babel.Layout (Kind (..), Layer (..), Placement (..), placementFolders)
import Babel.Load (Located (..), World (..))
import Babel.Model.Common (Amount (..), Issue (..), IssueKind (..))
import Babel.Model.Id (IngredientId (..), RecipeId (..), SourceId (..), UnitId (..))
import Babel.Model.Ingredient (Ingredient (..), IsA (..))
import Babel.Model.Recipe (Branch (..), End (..), Leaf (..), Outcome (..), Output (..), Recipe (..), Role (..), Select (..), Sensitivity (..))
import Babel.Model.Source (Origin (..), Source (..), originLicence)
import Data.ByteString.Lazy qualified as BL
import Data.Map.Strict qualified as Map
import Data.Set qualified as Set
import Data.Text qualified as T
import Decisions.Decide (Decided (..), Decisions)
import Decisions.Request (Key, Part (..))
import FoodKeeper.Kinetics (Fit (..), Spoiling (..), aboveFreezing, frozen)
import FoodKeeper.Match (Described (..), Resolution (..), ownMatch, resolve)
import FoodKeeper.Source (FoodKeeper, FoodProduct (..), productLabel)
import FoodKeeper.Tables (Entry (..), Override, Tables (..))
import System.Directory (createDirectoryIfMissing, doesDirectoryExist, removeDirectoryRecursive)
import System.FilePath (joinPath, takeDirectory, (</>))

data Env = Env
    { foodKeeper :: FoodKeeper
    , tables :: Tables
    , overrides :: Map IngredientId Override
    , url :: Text
    -- ^ Where the FoodKeeper file is published.
    }

-- | Ingredients no recipe outputs.
rawIngredients :: World -> [Ingredient]
rawIngredients w = [i.value | i <- Map.elems w.ingredients, not (Set.member i.value.id made)]
  where
    made = Set.fromList [o.ingredient | r <- Map.elems w.recipes, o <- outputsOf r.value.outputs]
    outputsOf = \case
        Outputs os -> toList os
        OneOf bs -> concatMap (outputsOf . (.outputs)) bs

-- | The raw ingredients, as Jev is told about them, given the OpenFoodFacts category of each that has one.
describeAll :: World -> Map IngredientId Text -> [Described]
describeAll w off = map describe (rawIngredients w)
  where
    describe i =
        Described
            { id = i.id
            , label = i.label
            , aliases = i.aliases
            , form = i.form
            , parents = [(p.target, maybe (idText p.target) (.value.label) (Map.lookup p.target w.ingredients)) | p <- i.parents]
            , openFoodFacts = Map.lookup i.id off
            }

data Extraction = Extraction
    { source :: Source
    , recipes :: [Recipe]
    , pending :: Map Key LByteString
    -- ^ The body of every request still to send.
    , referenced :: Set Key
    -- ^ Every request this extraction made, answered or not.
    , report :: [Row]
    -- ^ One per ingredient asked about.
    }

data Row = Row {ingredient :: IngredientId, status :: Status, product :: Maybe Text, detail :: Text}
    deriving stock (Eq, Show)

data Status = Emitted | NoStorageTime | Unmatched | Pending
    deriving stock (Eq, Show)

statusName :: Status -> Text
statusName = \case
    Emitted -> "emitted"
    NoStorageTime -> "no storage time"
    Unmatched -> "unmatched"
    Pending -> "pending"

extract :: Env -> Decisions -> [Described] -> Extraction
extract env ds described =
    Extraction
        { source = Source{id = sourceId, name = "FoodKeeper (USDA FSIS)", url = Just env.url, licence = originLicence FoodKeeper}
        , recipes = sortOn (.id) (concatMap snd results)
        , pending = Map.fromList [(p.key, p.body) | (Unasked ps, _) <- Map.elems owns, p <- ps]
        , referenced = Set.fromList [p.key | (_, ps) <- Map.elems owns, p <- ps]
        , report = map fst results
        }
  where
    byId = Map.fromList [(d.id, d) | d <- described]
    owns = Map.mapWithKey (\i d -> ownMatch env.foodKeeper ds (Map.lookup i env.overrides) d) byId
    parentsOf i = maybe [] (map fst . (.parents)) (Map.lookup i byId)
    results = map outcomeOf (Map.keys byId)
    outcomeOf i = case resolve parentsOf (fmap fst owns) i of
        Waiting -> (Row i Pending Nothing "", [])
        Unresolved why -> (Row i Unmatched Nothing why, [])
        Resolved p how -> case Map.lookup p env.tables.catalogue of
            Nothing -> (Row i Unmatched Nothing "the product is not in the FoodKeeper file", [])
            Just e ->
                let rs = recipesFor env.tables i e how
                 in (Row i (if null rs then NoStorageTime else Emitted) (Just (productLabel e.product)) how, rs)

sourceId :: SourceId
sourceId = SourceId "foodkeeper"

{- | A step for each side of freezing the product has a storage window for.
A recipe is keyed by its ID alone, so a reviewed @<ingredient>.<technique>@
replaces the one made here.
-}
recipesFor :: Tables -> IngredientId -> Entry -> Text -> [Recipe]
recipesFor tables i e how =
    [ spoilage s fit
    | (s, fitTo) <- [(e.spoiling, aboveFreezing), (tables.frozen, frozen)]
    , Just fit <- [fitTo s e.product.storage]
    ]
  where
    portion = Amount 100 (UnitId "g")
    spoilage s fit =
        Recipe
            { id = RecipeId (idText i <> "." <> idText s.technique)
            , source = sourceId
            , sourceRef = Nothing
            , technique = s.technique
            , inputs = Select Nothing Nothing [Right (Leaf i portion)]
            , outputs = Outputs (Output (IngredientId "spoiled_food") portion Main Nothing :| [])
            , environment = Nothing
            , end = ByTime (duration fit.seconds) (Just (let Sensitivity{..} = fit.sensitivity in Sensitivity{q10 = significant q10, ..}))
            , stateNote = Nothing
            , issues = [Issue "end" Estimate ("FoodKeeper's " <> productLabel e.product <> ": " <> fit.note <> "; matched " <> how)]
            }

-- | In hours below a day, otherwise in days.
duration :: Double -> Amount
duration s
    | s < day = Amount (significant (s / 3600)) (UnitId "h")
    | otherwise = Amount (significant (s / day)) (UnitId "d")
  where
    day = 86400

-- | Three significant figures: the windows are no more precise than that.
significant :: Double -> Double
significant x
    | x == 0 = 0
    -- Scaling by an exact integer power of ten keeps 91.3 from printing as 91.30000000000001.
    | decimals >= 0 = fromInteger (round (x * 10 ^ decimals)) / 10 ^ decimals
    | otherwise = fromInteger (round (x / 10 ^ negate decimals) * 10 ^ negate decimals)
  where
    decimals = 2 - floor (logBase 10 (abs x)) :: Int

-- | The extracted FoodKeeper folders hold exactly this extraction's entities.
writeLayer :: FilePath -> Extraction -> IO ()
writeLayer root x = do
    let folder kind = root </> "layers" </> joinPath [toString f | (_, f) <- placementFolders (Placement Extracted FoodKeeper kind)]
    replaceFolder (takeDirectory (folder Sources))
    write (folder Sources) [(idText x.source.id, x.source)]
    write (folder Recipes) [(idText r.id, r) | r <- x.recipes]
  where
    write :: (HasCodec a) => FilePath -> [(Text, a)] -> IO ()
    write dir entities = unless (null entities) $ do
        createDirectoryIfMissing True dir
        forM_ entities $ \(name, e) -> BL.writeFile (dir </> toString name <> ".json") (render e)
    replaceFolder dir = do
        exists <- doesDirectoryExist dir
        when exists (removeDirectoryRecursive dir)
        createDirectoryIfMissing True dir

-- | One row per ingredient asked about, then one per override of an ingredient that isn't.
renderReport :: [IngredientId] -> Extraction -> Text
renderReport stale x =
    T.unlines $
        "ingredient\toutcome\tproduct\tdetail"
            : [T.intercalate "\t" [idText r.ingredient, statusName r.status, fromMaybe "" r.product, r.detail] | r <- x.report]
                <> [idText i <> "\tstale override\t\tmatches.json names an ingredient that is not a raw ingredient of the dataset" | i <- stale]
