{-# LANGUAGE RecordWildCards #-}

{- | Turn a recipe page and mammoth's answers about it into babel
entities: a source, one recipe per step that works on food, and the
intermediate each step makes.
-}
module Wikibooks.Emit (
    Env (..),
    mkEnv,
    lexicon,
    ingredientFor,
    Dish (..),
    dish,
    baseIngredient,
    baseLabel,
    confidenceThreshold,
)
where

import Babel.Codec (IsId (..))
import Babel.Layout (Layer (..))
import Babel.Load (Located (..), World (..))
import Babel.Mass (massByUnit)
import Babel.Model.Common (Amount (..), Issue (..), IssueKind (..), intensityValue)
import Babel.Model.Constants (Constants (..))
import Babel.Model.Id (IngredientId (..), MediumId (..), RecipeId, SourceId (..), TechniqueId, UnitId (..))
import Babel.Model.Ingredient (Ingredient (..))
import Babel.Model.Recipe (End (..), Environment (..), Leaf (..), Outcome (..), Output (..), Recipe (..), Role (..), Select (..), Sensitivity (..), SourceRef (..))
import Babel.Model.Registry (Dimension (..), Medium (..), Term (..), Unit (..))
import Babel.Model.Source (Origin (..), Source (..), originLicence)
import Babel.Model.Technique (Technique (..), TempRange (..))
import Data.ByteString qualified as BS
import Data.Char (isAlphaNum, isAscii, toLower)
import Data.List (lookup)
import Data.Map.Strict qualified as Map
import Data.Text qualified as T
import Numeric (showHex)
import Wikibooks.Dump (Cookbook, Title, article, resolve, titleText)
import Wikibooks.Page (Line (..), RecipePage (..), Step (..), slugify)
import Wikibooks.Quantity (Duration (..), IngredientLine (..), Lexicon (..), duration, temperatureC, unitAliases)
import Wikibooks.Questions (IngredientAnswers (..), Judged (..), PageAnswers (..), StepAnswers (..), Vocabulary (..))
import Wikibooks.Wikitext (PageKind (..), pageKind)

-- | Answers less probable than this are flagged as uncertain.
confidenceThreshold :: Double
confidenceThreshold = 0.6

{- | The temperature a step in each medium is assumed to run at when
nothing else gives one. Every registry medium needs an entry.
-}
mediumTemperatures :: [(MediumId, Double)]
mediumTemperatures =
    [ (MediumId "still_air", 20)
    , (MediumId "oven_air", 180)
    , (MediumId "convection_air", 180)
    , (MediumId "frying_oil", 175)
    , (MediumId "pan_contact", 180)
    , (MediumId "simmering_water", 90)
    , (MediumId "boiling_water", 100)
    , (MediumId "steam", 100)
    ]

-- | Units the extractor writes without reading them from a page.
namedUnits :: [UnitId]
namedUnits = map UnitId ["g", "piece", "pinch", "batch", "s", "min", "h", "d"]

-- | Media an oven heats, where "preheat the oven" sets the temperature.
ovenMedia :: [MediumId]
ovenMedia = [MediumId "oven_air", MediumId "convection_air"]

-- | Everything the extractor knows besides the page at hand.
data Env = Env
    { cookbook :: Cookbook
    , world :: World
    -- ^ The dataset without the extracted Wikibooks layer.
    , known :: Map Text IngredientId
    -- ^ Existing ingredients by the slug of their ID and of each alias.
    , vocabulary :: Vocabulary
    }

{- | Index the dataset for extraction. The extracted Wikibooks layer is
left out: extraction recreates it.
-}
mkEnv :: Cookbook -> World -> Either Text Env
mkEnv cookbook loaded = do
    constants <- maybeToRight "registries/constants.json did not load" loaded.constants
    let spoilage t = constants.spoilageTechnique `elem` ancestors loaded t
    techniques <-
        maybeToRight "no techniques to choose from" . nonEmpty $
            [(t.value.id, describeTechnique t.value) | t <- Map.elems loaded.techniques, not (spoilage t.value.id)]
    forms <- maybeToRight "no forms to choose from" (nonEmpty [(f.id, describe f.label f.description) | f <- Map.elems loaded.forms])
    forM_ [u | u <- namedUnits <> map snd unitAliases, not (Map.member u loaded.units)] $ \u ->
        Left ("the extractor uses the unit " <> idText u <> ", which is not in registries/units.json")
    forM_ [m | m <- Map.keys loaded.media, isNothing (lookup m mediumTemperatures)] $ \m ->
        Left ("Wikibooks.Emit.mediumTemperatures has no entry for the medium " <> idText m)
    pure
        Env
            { cookbook
            , world
            , known = Map.fromList [(s, i.value.id) | i <- Map.elems world.ingredients, Just s <- map slugify (idText i.value.id : i.value.aliases)]
            , vocabulary =
                Vocabulary
                    { techniques
                    , media = [(m.id, m.label) | m <- Map.elems loaded.media]
                    , forms
                    , texture = Map.elems loaded.texture
                    , taste = Map.elems loaded.taste
                    }
            }
  where
    world = withoutExtracted loaded
    describe label = maybe label (\d -> label <> ": " <> d)
    describeTechnique t = case mapMaybe (\p -> (.value.label) <$> Map.lookup p loaded.techniques) t.parents of
        [] -> t.label
        ps -> t.label <> " (a way to " <> T.intercalate ", " ps <> ")"

withoutExtracted :: World -> World
withoutExtracted World{..} = World{ingredients = Map.filter (\i -> not (i.layer == Extracted && i.origin == Wikibooks)) ingredients, ..}

-- | A technique and everything above it in the is-a DAG, nearest first.
ancestors :: World -> TechniqueId -> [TechniqueId]
ancestors w t0 = go [] [t0]
  where
    go seen = \case
        [] -> reverse seen
        t : queue
            | t `elem` seen -> go seen queue
            | otherwise -> go (t : seen) (queue <> maybe [] (.value.parents) (Map.lookup t w.techniques))

lexicon :: Env -> Lexicon
lexicon env =
    Lexicon
        { isFood = maybe True ((== OtherPage) . kind) . article env.cookbook
        , isTool = maybe False ((== EquipmentPage) . kind) . article env.cookbook
        , weighs = \u -> maybe False ((== Mass) . (.dimension)) (Map.lookup u env.world.units)
        }
  where
    kind (_, _, text) = pageKind text

{- | The ingredient a link names: an existing ingredient whose ID or alias
matches the linked title, or the page it redirects to; else that page's
slug. The linked title goes first, since a redirect often leads from a
specific food to a general one ("White sugar" to "Sugar").
-}
ingredientFor :: Env -> Title -> Maybe IngredientId
ingredientFor env t = do
    target <- slugify (titleText (resolve env.cookbook t))
    pure . fromMaybe (Map.findWithDefault (IngredientId target) target env.known) $
        (`Map.lookup` env.known) =<< slugify (titleText t)

-- | What one page adds to the dataset.
data Dish = Dish
    { source :: Source
    , recipes :: NonEmpty Recipe
    , made :: [Ingredient]
    -- ^ The intermediates, and the dish itself unless it already exists.
    , uses :: Map IngredientId Title
    -- ^ Ingredients the recipes consume but do not make, with the page each links to.
    }
    deriving stock (Show)

uncertain :: Text -> Judged a -> [Issue]
uncertain field j =
    [ Issue field Uncertain ("mammoth gave this answer a probability of " <> show2 j.probability)
    | j.probability < confidenceThreshold
    ]

estimate :: Text -> Text -> Issue
estimate field = Issue field Estimate

show2 :: Double -> Text
show2 x = show (fromIntegral (round (x * 100) :: Int) / 100 :: Double)

-- | An ingredient line, the ingredient it names, and the step mammoth says first uses it.
data Use = Use {line :: Line, ingredient :: IngredientId, firstStep :: Judged (Maybe Int)}

-- | A step that works on food, with the ingredient lines it takes in.
data Kept = Kept {step :: Step, answers :: StepAnswers, intake :: [Use]}

dish :: Env -> RecipePage -> PageAnswers -> Either Text Dish
dish env page answers = do
    unless answers.singleDish.value $
        Left ("mammoth judged it not a single dish (p=" <> show2 answers.singleDish.probability <> ")")
    dishId <- maybeToRight "the title names no ingredient" (ingredientFor env page.title)
    uses <- traverse use answers.ingredients
    kept <- maybeToRight "mammoth judged that no step works on food" (nonEmpty [Kept s a [] | (s, a) <- toList answers.steps, a.actsOnFood.value])
    let lastNumber = (last kept).step.number
        assigned = foldl' assign kept [(u, n) | u <- toList uses, Just n <- [u.firstStep.value]]
        unused =
            [ Issue "inputs" Missing ("no step uses " <> quote u.line.text <> " (p=" <> show2 u.firstStep.probability <> ")")
            | u <- toList uses
            , isNothing u.firstStep.value
            ]
        preheats = [(s.number, t) | (s, _) <- toList answers.steps, "preheat" `T.isInfixOf` T.toLower s.text, Just t <- [temperatureC s.text]]
    (recipes, made) <- chain dishId preheats lastNumber (uncertain "source" answers.singleDish) unused (toList assigned)
    recipes' <- maybeToRight "no step takes in any ingredient" (nonEmpty recipes)
    let madeIds = map (.id) made
    pure
        Dish
            { source = Source{id = sourceId, name = "Wikibooks Cookbook: " <> titleText page.title, url = Just (pageUrl page.title page.revid), licence = originLicence Wikibooks}
            , recipes = recipes'
            , made = [i | i <- made, not (Map.member i.id env.world.ingredients)]
            , uses =
                Map.fromList
                    [ (u.ingredient, u.line.parsed.ingredient)
                    | k <- toList assigned
                    , u <- k.intake
                    , u.ingredient `notElem` madeIds
                    , not (Map.member u.ingredient env.world.ingredients)
                    ]
            }
  where
    use (l, j) = maybe (Left ("no ingredient ID for " <> quote l.text)) (\i -> Right (Use l i j)) (ingredientFor env l.parsed.ingredient)
    -- A line goes in at the first kept step at or after the one mammoth
    -- named, or the last kept step when none is.
    assign ks (u, n) =
        let target = maybe (last ks).step.number (.step.number) (find ((>= n) . (.step.number)) ks)
         in fmap (\k -> if k.step.number == target then k{intake = k.intake <> [u]} else k) ks
    sourceId = SourceId ("wikibooks_" <> page.slug)
    quote t = "'" <> t <> "'"

    -- The first recipe carries the issues about the page, the last those about the dish.
    chain dishId preheats lastNumber pageIssues dishIssues = go Nothing [] []
      where
        go _ rs ms [] = Right (reverse rs, reverse ms)
        go previous rs ms (k : ks) =
            case (previous, k.intake) of
                (Nothing, []) -> go Nothing rs ms ks
                _ -> do
                    let n = k.step.number
                        outId = if n == lastNumber then dishId else IngredientId (page.slug <> "_step_" <> show n)
                        leaves = [(False, l) | Just l <- [previous]] <> [(u.line.parsed.optional, Leaf u.ingredient u.line.parsed.amount) | u <- k.intake]
                        (outAmount, outNotes) = outputAmount (map snd leaves)
                    recipeId <- first toText (mkId (page.slug <> ".step_" <> show n) :: Either String RecipeId)
                    (environment, envIssues) <- stepEnvironment k (latestPreheat n)
                    let (end, endIssues) = stepEnd k.step (isNothing environment)
                        recipe =
                            Recipe
                                { id = recipeId
                                , source = sourceId
                                , sourceRef = Just (SourceRef sourceId n)
                                , technique = k.answers.technique.value
                                , inputs = Select Nothing Nothing [if opt then Left (Select (Just 0) (Just 1) [Right l]) else Right l | (opt, l) <- leaves]
                                , outputs = Outputs (Output outId outAmount Main Nothing :| [])
                                , environment
                                , end
                                , stateNote = Just k.step.text
                                , issues =
                                    concat
                                        [ uncertain "sourceRef" k.answers.actsOnFood
                                        , uncertain "technique" k.answers.technique
                                        , uncertain "environment" k.answers.medium
                                        , concatMap (uncertain "inputs" . (.firstStep)) k.intake
                                        , [estimate "inputs" (quote u.line.text <> ": " <> note) | u <- k.intake, note <- u.line.parsed.estimates]
                                        , map (estimate "outputs") outNotes
                                        , envIssues
                                        , endIssues
                                        , if null rs then pageIssues else []
                                        , if n == lastNumber then dishIssues else []
                                        ]
                                }
                        intermediate =
                            Ingredient
                                { id = outId
                                , label = if n == lastNumber then dishLabel page.title else dishLabel page.title <> ", after step " <> show n
                                , aliases = []
                                , parents = []
                                , form = k.answers.form.value
                                , texture = mempty
                                , taste = mempty
                                , servingTags = []
                                , densityGPerMl = Nothing
                                , waterActivity = Nothing
                                , named = n == lastNumber
                                , issues = uncertain "form" k.answers.form
                                }
                    go (Just (Leaf outId outAmount)) (recipe : rs) (intermediate : ms) ks
        latestPreheat n = viaNonEmpty last [t | (m, t) <- preheats, m <= n]

    {- The output weighs what the inputs weigh. A counted input has no
    weight, so a step with one yields a batch, which the next step takes
    in whole. -}
    outputAmount leaves =
        case traverse measure leaves of
            Nothing -> (Amount 1 (UnitId "batch"), [])
            Just measured ->
                let assumed = [idText l.ingredient | (l, (_, True)) <- zip leaves measured]
                 in ( Amount (fromIntegral (round (sum (map fst measured) * 10) :: Int) / 10) (UnitId "g")
                    , ["assumed 1 g/ml for " <> T.intercalate ", " assumed | not (null assumed)]
                    )
    -- Grams, and whether the density was assumed; nothing for a count.
    measure l = case massByUnit env.world l.ingredient l.amount of
        Just g -> Just (g, False)
        Nothing -> case Map.lookup l.amount.unit env.world.units of
            Just u | u.dimension == Volume -> Just (l.amount.qty * u.toBase, True)
            _ -> Nothing

    stepEnvironment k preheat = case k.answers.medium.value of
        Nothing -> Right (Nothing, [])
        Just m -> do
            (t, notes) <- maybeToRight ("step " <> show k.step.number <> ": no temperature for a heated step") (temperature k m preheat)
            Right (Just (Environment t (Left m)), map (estimate "environment") notes)
    temperature k m preheat =
        asum
            [ (,[]) <$> temperatureC k.step.text
            , (,[]) <$> (guard (m `elem` ovenMedia) *> preheat)
            , listToMaybe
                [ ((r.minC + r.maxC) / 2, ["no temperature given; used the middle of " <> idText t <> "'s typical range"])
                | t <- ancestors env.world k.answers.technique.value
                , Just r <- [(.value.typicalTemp) =<< Map.lookup t env.world.techniques]
                ]
            , (\c -> (c, ["no temperature given; assumed " <> show2 c <> " °C for " <> idText m])) <$> lookup m mediumTemperatures
            ]

    stepEnd s ambient =
        let (amount, notes) = case duration s.text of
                Just d -> (d.amount, d.notes)
                Nothing -> (Amount 1 (UnitId "min"), ["no duration given; assumed 1 minute"])
            sensitivity = Sensitivity 20 1 Nothing Nothing
         in ( ByTime amount (if ambient then Just sensitivity else Nothing)
            , map (estimate "end") notes
            )

-- | A dish's label from its page title: the title, first letter lower-cased.
dishLabel :: Title -> Text
dishLabel t = maybe "" (\(c, rest) -> T.cons (toLower c) rest) (T.uncons (titleText t))

-- | A food's label from its page title, lower-cased like every ingredient label.
baseLabel :: Title -> Text
baseLabel = T.toLower . titleText

-- | An ingredient no dataset file has yet, from mammoth's reading of its page.
baseIngredient :: IngredientId -> Title -> IngredientAnswers -> Ingredient
baseIngredient i title a =
    Ingredient
        { id = i
        , label = baseLabel title
        , aliases = []
        , parents = []
        , form = a.form.value
        , texture = present a.texture
        , taste = present a.taste
        , servingTags = []
        , densityGPerMl = Nothing
        , waterActivity = Nothing
        , named = False
        , issues =
            uncertain "form" a.form
                <> concat [uncertain ("texture." <> idText k) j | (k, j) <- Map.toList a.texture]
                <> concat [uncertain ("taste." <> idText k) j | (k, j) <- Map.toList a.taste]
        }
  where
    present = Map.filter ((> 0) . intensityValue) . fmap (.value)

{- | The page as it was in the dump: @oldid@ pins the revision, so the URL
keeps pointing at the text the data came from.
-}
pageUrl :: Title -> Int -> Text
pageUrl t revid = "https://en.wikibooks.org/w/index.php?title=Cookbook:" <> T.concatMap escape (titleText t) <> "&oldid=" <> show revid
  where
    escape c
        | c == ' ' = "_"
        | c `elem` ("()_:,-.~!*'" :: String) || (isAscii c && isAlphaNum c) = one c
        | otherwise = foldMap (\b -> "%" <> T.toUpper (T.justifyRight 2 '0' (toText (showHex b "")))) (BS.unpack (encodeUtf8 (one c :: Text)))
