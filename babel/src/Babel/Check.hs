{- | Whole-dataset rules on a loaded 'World': licences, references, graph
and value checks, versioning, and soft (warning-only) plausibility checks.
-}
module Babel.Check (
    checkWorld,
    effectiveLicence,
    findCycles,
    massInGrams,
)
where

import Babel.Diagnostic
import Babel.Load
import Babel.Model
import Data.List.NonEmpty qualified as NE
import Data.Map.Strict qualified as Map
import Data.Set qualified as Set
import Data.Text qualified as T

checkWorld :: World -> [Diagnostic]
checkWorld w =
    concat
        [ licenceChecks w
        , concatMap (ingredientChecks w) (Map.elems w.ingredients)
        , concatMap (techniqueChecks w) (Map.elems w.techniques)
        , concatMap (recipeChecks w) (Map.elems w.recipes)
        , cycleChecks "ingredient" [(i.value.id, map (.target) i.value.parents) | i <- Map.elems w.ingredients] (fmap (.path) w.ingredients)
        , cycleChecks "technique" [(t.value.id, t.value.parents) | t <- Map.elems w.techniques] (fmap (.path) w.techniques)
        , versionChecks w
        , maybe [] (constantsChecks w) w.constants
        ]

-- | The strictest licence of anything in the build.
effectiveLicence :: World -> Licence
effectiveLicence w =
    maximumOn licenceStrictness Cc0 $
        concat
            [ map (.licence) (Map.elems w.sources)
            , map (.licence) (Map.elems w.ingredients)
            , map (.licence) (Map.elems w.techniques)
            , map (.licence) (Map.elems w.recipes)
            ]
  where
    maximumOn f = foldr (\x acc -> if f x > f acc then x else acc)

-- * Licences

licenceChecks :: World -> [Diagnostic]
licenceChecks w =
    [ errorIn "licence-mismatch" (Just s.path) $
        "source licence " <> licenceSpdx s.value.licence <> " does not match its folder " <> licenceSpdx s.licence
    | s <- Map.elems w.sources
    , s.value.licence /= s.licence
    ]
        ++ [ errorIn "licence-mismatch" (Just r.path) $
                "recipe is in the "
                    <> licenceSpdx r.licence
                    <> " folder but its source "
                    <> idText r.value.source
                    <> " is "
                    <> licenceSpdx s.value.licence
           | r <- Map.elems w.recipes
           , Just s <- [Map.lookup r.value.source w.sources]
           , s.value.licence /= r.licence
           ]

-- * Entities

-- | A reference check: report the ID if it is not a key of the map.
ref :: (IsId k) => FilePath -> Text -> Map k v -> k -> [Diagnostic]
ref p what m k
    | Map.member k m = []
    | otherwise = [errorIn "dangling-ref" (Just p) ("unknown " <> what <> " " <> idText k)]

ingredientChecks :: World -> Located Ingredient -> [Diagnostic]
ingredientChecks w (Located i p _ _) =
    concat
        [ concatMap (ref p "parent ingredient" w.ingredients . (.target)) i.parents
        , ref p "form" w.forms i.form
        , concatMap (ref p "texture attribute" w.texture) (Map.keys i.texture)
        , concatMap (ref p "taste attribute" w.taste) (Map.keys i.taste)
        , concatMap (ref p "serving tag" w.serving) i.servingTags
        , maybe [] (ref p "nutrition source" w.sources . (.source)) i.nutrition
        , [ errorIn "water-activity" (Just p) "waterActivity must be within 0-1"
          | Just aw <- [i.waterActivity]
          , aw < 0 || aw > 1
          ]
        , [ errorIn "density" (Just p) "densityGPerMl must be positive"
          | Just d <- [i.densityGPerMl]
          , d <= 0
          ]
        , fromMaybe [] (calorieCheck p <$> w.constants <*> fmap (.per100g) i.nutrition)
        ]

techniqueChecks :: World -> Located Technique -> [Diagnostic]
techniqueChecks w (Located t p _ _) =
    concatMap (ref p "parent technique" w.techniques) t.parents
        ++ [ errorIn "temp-range" (Just p) "typicalTemp minC is above maxC"
           | Just r <- [t.typicalTemp]
           , r.minC > r.maxC
           ]

recipeChecks :: World -> Located Recipe -> [Diagnostic]
recipeChecks w (Located r p _ _) =
    concat
        [ ref p "source" w.sources r.source
        , maybe [] (ref p "sourceRef source" w.sources . (.source)) r.sourceRef
        , ref p "technique" w.techniques r.technique
        , selectChecks r.inputs
        , concatMap leafChecks (leaves r.inputs)
        , outcomeChecks r.outputs
        , case r.environment of
            Just env -> either (ref p "medium" w.media) (const []) env.medium
            Nothing -> []
        , endChecks r.end
        , [ errorIn "ambient-time" (Just p) $
                "a time-based end with no environment needs a sensitivity;"
                    <> " otherwise the time means nothing under game-supplied conditions"
          | isNothing r.environment
          , ByTime _ Nothing <- [r.end]
          ]
        , maybe [] (\c -> ratioCheck c w p r) w.constants
        ]
  where
    err rule = errorIn rule (Just p)

    selectChecks s =
        [ err "select-bounds" $
            "select needs 0 <= min <= max <= number of choices, got min "
                <> showT (selectMin s)
                <> ", max "
                <> showT (selectMax s)
                <> ", "
                <> showT (length s.choices)
                <> " choices"
        | not (0 <= selectMin s && selectMin s <= selectMax s && selectMax s <= length s.choices)
        ]
            ++ concatMap selectChecks (lefts s.choices)

    leafChecks l = ref p "ingredient" w.ingredients l.ingredient ++ amountChecks l.amount

    amountChecks a =
        case Map.lookup a.unit w.units of
            Nothing -> ref p "unit" w.units a.unit
            Just u -> [err "dimension" ("an ingredient amount cannot be measured in " <> idText a.unit) | u.dimension == Time]
            ++ [err "amount" "amounts must be positive" | a.qty <= 0]

    outcomeChecks = \case
        Outputs os ->
            concatMap outputChecks (NE.toList os)
                ++ [ err "all-chance" "every output in this branch is chance-gated, so it can yield nothing"
                   | not (any certain os)
                   ]
        OneOf bs ->
            concat
                [ [err "weight" ("branch weights must be positive, got " <> showT b.weight) | b.weight <= 0]
                    ++ outcomeChecks b.outputs
                | b <- NE.toList bs
                ]

    certain o = maybe True (>= 1) o.chance

    outputChecks o =
        ref p "ingredient" w.ingredients o.ingredient
            ++ amountChecks o.amount
            ++ [ err "chance" ("chance must be in (0, 1], got " <> showT c)
               | Just c <- [o.chance]
               , c <= 0 || c > 1
               ]

    endChecks = \case
        ByCoreTemp _ -> []
        ByTime d s -> durationChecks d ++ maybe [] sensitivityChecks s
        ByBoth _ d -> durationChecks d

    durationChecks d =
        case Map.lookup d.unit w.units of
            Nothing -> ref p "unit" w.units d.unit
            Just u -> [err "dimension" ("a duration must use a time unit, not " <> idText d.unit) | u.dimension /= Time]
            ++ [err "amount" "durations must be positive" | d.qty <= 0]

    sensitivityChecks s =
        [ err "water-activity" "sensitivity needs 0 <= minWaterActivity <= maxWaterActivity <= 1"
        | let lo = fromMaybe 0 s.minWaterActivity
              hi = fromMaybe 1 s.maxWaterActivity
        , not (0 <= lo && lo <= hi && hi <= 1)
        ]
            ++ [err "q10" "q10 must be positive" | s.q10 <= 0]

leaves :: Select -> [Leaf]
leaves s = rights s.choices ++ concatMap leaves (lefts s.choices)

-- * Graphs

-- | Every cycle in a graph given as adjacency lists, each reported once.
findCycles :: (Ord k) => [(k, [k])] -> [[k]]
findCycles edges = dedupe (snd (foldl' (visit []) (Set.empty, []) (map fst edges)))
  where
    adj = Map.fromListWith (++) edges
    -- Depth-first; a node is done once all its descendants are explored, so
    -- meeting a node still on the stack means a back edge, i.e. a cycle.
    visit stack (done, found) k
        | k `elem` stack = (done, (k : reverse (takeWhile (/= k) stack)) : found)
        | Set.member k done = (done, found)
        | otherwise =
            let (done', found') = foldl' (visit (k : stack)) (done, found) (Map.findWithDefault [] k adj)
             in (Set.insert k done', found')
    dedupe = Map.elems . Map.fromList . map (\c -> (Set.fromList c, c))

cycleChecks :: (IsId k) => Text -> [(k, [k])] -> Map k FilePath -> [Diagnostic]
cycleChecks kind edges paths =
    [ errorIn "isa-cycle" (Map.lookup k paths) $
        "is-a cycle among " <> kind <> "s: " <> T.intercalate " -> " (map idText (c ++ [k]))
    | c@(k : _) <- findCycles edges
    ]

-- * Versioning

versionChecks :: World -> [Diagnostic]
versionChecks w =
    concat
        [ [ err "rename-since" ("rename " <> showRename r <> " is from " <> renderVersion r.since <> ", after the dataset version " <> renderVersion v)
          | Just ds <- [w.dataset]
          , let v = ds.version
          , r <- w.renames
          , r.since > v
          ]
        , [ err "rename-from-exists" ("rename " <> showRename r <> ": " <> idText r.from <> " still exists")
          | r <- w.renames
          , Set.member (idText r.from) (idsOf w r.kind)
          ]
        , [ err "rename-to-missing" ("rename " <> showRename r <> ": " <> idText r.to <> " does not exist and is not renamed later")
          | (n, r) <- zip [0 :: Int ..] w.renames
          , not (resolves n r.kind (idText r.to) Set.empty)
          ]
        , [ err "rename-loop" ("renames loop back to an earlier slug: " <> T.intercalate " -> " (map snd (c ++ take 1 c)))
          | c <- findCycles [((r.kind, idText r.from), [(r.kind, idText r.to)]) | r <- w.renames]
          ]
        ]
  where
    err rule = errorIn rule (Just "renames.json")
    showRename r = entityKindName r.kind <> " " <> idText r.from <> " -> " <> idText r.to
    -- Follow later renames from a slug until one exists (or we loop).
    resolves n kind slug visited
        | Set.member slug (idsOf w kind) = True
        | Set.member slug visited = False
        | otherwise = case [(m, r) | (m, r) <- drop (n + 1) (zip [0 ..] w.renames), r.kind == kind, idText r.from == slug] of
            (m, r) : _ -> resolves m kind (idText r.to) (Set.insert slug visited)
            [] -> False

idsOf :: World -> EntityKind -> Set Text
idsOf w = \case
    KIngredient -> keys w.ingredients
    KTechnique -> keys w.techniques
    KRecipe -> keys w.recipes
    KSource -> keys w.sources
    KUnit -> keys w.units
    KMedium -> keys w.media
    KForm -> keys w.forms
    KTexture -> keys w.texture
    KTaste -> keys w.taste
    KServing -> keys w.serving
  where
    keys :: (IsId k) => Map k v -> Set Text
    keys = Set.fromList . map idText . Map.keys

-- * Constants

constantsChecks :: World -> Constants -> [Diagnostic]
constantsChecks w c =
    ref p "spoilage technique" w.techniques c.spoilageTechnique
        ++ [ errorIn "constants" (Just p) "massRatio needs 0 < min <= max"
           | not (0 < c.massRatio.min && c.massRatio.min <= c.massRatio.max)
           ]
        ++ [ errorIn "constants" (Just p) "calorieTolerance values must not be negative"
           | c.calorieTolerance.kcal < 0 || c.calorieTolerance.fraction < 0
           ]
  where
    p = "registries/constants.json"

-- * Soft checks

-- | Kilocalories should roughly match the macros at 'kcalPerGram'.
calorieCheck :: FilePath -> Constants -> Per100g -> [Diagnostic]
calorieCheck p c n = case (n.kcal, n.proteinG, n.fatG, n.carbsG) of
    (Just kcal, Just pr, Just fat, Just carbs)
        | let f = c.kcalPerGram
              expected = f.protein * pr + f.carbs * carbs + f.fat * fat
        , abs (kcal - expected) > max c.calorieTolerance.kcal (c.calorieTolerance.fraction * expected) ->
            [ warningIn "calories" (Just p) $
                "kcal " <> showT kcal <> " is inconsistent with macros (about " <> showT (fromIntegral (round expected :: Int) :: Double) <> ")"
            ]
    _ -> []

-- | An amount in grams, when the unit and the ingredient's density allow.
massInGrams :: World -> IngredientId -> Amount -> Maybe Double
massInGrams w ing a = do
    u <- Map.lookup a.unit w.units
    case u.dimension of
        Mass -> Just (a.qty * u.toBase)
        Volume -> do
            i <- Map.lookup ing w.ingredients
            d <- i.value.densityGPerMl
            Just (a.qty * u.toBase * d)
        _ -> Nothing

{- | For a step that uses all of its inputs and has a plain output list,
the expected output mass over the input mass should lie within
'massRatio'. Skipped when any amount has no mass.
-}
ratioCheck :: Constants -> World -> FilePath -> Recipe -> [Diagnostic]
ratioCheck c w p r = case (r.inputs, r.outputs) of
    (s, Outputs os)
        | null (lefts s.choices)
        , selectMin s == length s.choices
        , Just inMass <- sum <$> traverse (\l -> massInGrams w l.ingredient l.amount) (rights s.choices)
        , Just outMass <- sum <$> traverse (\o -> (* fromMaybe 1 o.chance) <$> massInGrams w o.ingredient o.amount) (NE.toList os)
        , inMass > 0
        , let ratio = outMass / inMass
        , ratio < c.massRatio.min || ratio > c.massRatio.max ->
            [ warningIn "ratio" (Just p) $
                "outputs weigh about " <> showT (round1 ratio) <> "x the inputs (" <> showT (round inMass :: Int) <> " g in, " <> showT (round outMass :: Int) <> " g out)"
            ]
    _ -> []
  where
    round1 x = fromIntegral (round (x * 10) :: Int) / 10 :: Double

showT :: (Show a) => a -> Text
showT = T.pack . show
