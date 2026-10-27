{- | Turn a recipe page and Jev's answers about it into babel
entities: a source, one recipe per step that works on food, and the
intermediate each step makes. A step that is a preparation
("Babel.Preparation") makes the dataset's one item for it, which other
pages make too; any other step makes an intermediate of the page's own.
A line prepared on its own before its step, such as a chopped onion, gets
a recipe of its own that makes the item for that preparation.
-}
module Wikibooks.Emit (
    Env (..),
    mkEnv,
    lexicon,
    ingredientFor,
    preparedId,
    Dish (..),
    PreparedStep (..),
    dish,
    rejection,
    ambientSensitivity,
    baseIngredient,
    baseLabel,
    confidenceThreshold,
)
where

import Babel.Codec (IsId (..))
import Babel.Lineage (ancestors)
import Babel.Load (Located (..), World (..), withoutExtracted)
import Babel.Mass (massByUnit)
import Babel.Model.Common (Amount (..), Issue (..), IssueKind (..), intensityValue)
import Babel.Model.Constants (BandEntry (..), Constants (..), StorageTemps (..), TempBands, bandFromCelsius, tempBandEntries)
import Babel.Model.Id (FormId, IngredientId (..), MediumId (..), RecipeId, SourceId (..), TechniqueId, UnitId (..))
import Babel.Model.Ingredient (Ingredient (..))
import Babel.Model.Recipe (End (..), Environment (..), Leaf (..), Outcome (..), Output (..), Recipe (..), Role (..), Select (..), Sensitivity (..), SourceRef (..))
import Babel.Model.Registry (Dimension (..), Medium (id, label), Term (..), Unit (..))
import Babel.Model.Source (Origin (..), Source (..), originLicence)
import Babel.Model.Technique (Technique (..), TempRange (..))
import Babel.Preparation (Preparation (..), determinesOutput, preparationOf, preparations)
import Babel.Slug (slugify)
import Data.ByteString qualified as BS
import Data.Char (isAlphaNum, isAscii, toLower)
import Data.List (lookup)
import Data.List.NonEmpty qualified as NE
import Data.Map.Strict qualified as Map
import Data.Text qualified as T
import Decisions.Request (Judged (..))
import Numeric (showHex)
import Wikibooks.Dump (Cookbook, Title, article, resolve, titleText)
import Wikibooks.Page (Line (..), RecipePage (..), Step (..))
import Wikibooks.Quantity (Duration (..), IngredientLine (..), Lexicon (..), duration, temperatureC, unitAliases)
import Wikibooks.Questions (FirstUse (..), IngredientAnswers (..), PageAnswers (..), Place (..), Prep (..), StepAnswers (..), StepMedium (..), Vocabulary (..))
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
    , prepared :: Map Preparation IngredientId
    -- ^ What the dataset makes by each preparation.
    , constants :: Constants
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
    preparing <- maybeToRight "no preparation techniques to choose from" (nonEmpty (NE.filter (determinesOutput loaded . fst) techniques))
    forms <- maybeToRight "no forms to choose from" (nonEmpty [(f.id, describe f.label f.description) | f <- Map.elems loaded.forms])
    forM_ [u | u <- namedUnits <> map snd unitAliases, not (Map.member u loaded.units)] $ \u ->
        Left ("the extractor uses the unit " <> idText u <> ", which is not in registries/units.json")
    forM_ [m | m <- Map.keys loaded.media, isNothing (lookup m mediumTemperatures)] $ \m ->
        Left ("Wikibooks.Emit.mediumTemperatures has no entry for the medium " <> idText m)
    prepared <- forM (preparations world) $ \makes -> case Map.keys makes of
        [made] -> Right made
        several -> Left ("the dataset makes " <> T.intercalate " and " (map idText several) <> " by one preparation")
    pure
        Env
            { cookbook
            , world
            , known = Map.fromList [(s, i.value.id) | i <- Map.elems world.ingredients, Just s <- map slugify (idText i.value.id : i.value.aliases)]
            , vocabulary =
                Vocabulary
                    { techniques
                    , preparations = preparing
                    , media = [(m.id, m.label) | m <- Map.elems loaded.media]
                    , forms
                    , texture = Map.elems loaded.texture
                    , taste = Map.elems loaded.taste
                    }
            , prepared
            , constants
            }
  where
    world = withoutExtracted Wikibooks loaded
    describe label = maybe label (\d -> label <> ": " <> d)
    describeTechnique t = case mapMaybe (\p -> (.value.label) <$> Map.lookup p loaded.techniques) t.parents of
        [] -> t.label
        ps -> t.label <> " (a way to " <> T.intercalate ", " ps <> ")"

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

{- | The dataset's one item for a preparation, or a new ID for it. The ID
depends on the preparation alone, so every page making it agrees.
-}
preparedId :: Env -> Preparation -> IngredientId
preparedId env p = Map.findWithDefault (IngredientId (idText p.ingredient <> "_" <> idText p.technique)) p env.prepared

-- | What one page adds to the dataset.
data Dish = Dish
    { source :: Source
    , recipes :: NonEmpty Recipe
    , made :: [Ingredient]
    -- ^ The page's own intermediates, and the dish itself unless it already exists.
    , prepared :: [PreparedStep]
    , uses :: Map IngredientId Title
    -- ^ Ingredients the recipes consume but do not make, with the page each links to.
    }
    deriving stock (Show)

-- | A step that makes the item for a preparation, which other pages may make too.
data PreparedStep = PreparedStep
    { preparation :: Preparation
    , makes :: IngredientId
    , form :: Judged FormId
    , dishName :: Maybe Text
    -- ^ The page's dish, when this step is the page's last: a name for the item.
    }
    deriving stock (Show)

uncertain :: Text -> Judged a -> [Issue]
uncertain field j =
    [ Issue field Uncertain ("the decision model gave this answer a probability of " <> show2 j.probability)
    | j.probability < confidenceThreshold
    ]

estimate :: Text -> Text -> Issue
estimate field = Issue field Estimate

show2 :: Double -> Text
show2 x = show (fromIntegral (round (x * 100) :: Int) / 100 :: Double)

-- | An ingredient line, its number, the ingredient it names, and how Jev says it is first used.
data Use = Use {number :: Int, line :: Line, ingredient :: IngredientId, firstUse :: Judged (Maybe FirstUse)}

-- | The technique a line is prepared by on its own, if any, and the form that leaves it in.
usePrep :: Use -> Maybe (Judged TechniqueId, Judged FormId)
usePrep u = do
    FirstUse{prep} <- u.firstUse.value
    p <- sequenceA prep
    pure (p.value.technique <$ p, p.value.form)

-- | A step that works on food, with the ingredient lines it takes in.
data Kept = Kept {step :: Step, answers :: StepAnswers, intake :: [Use]}

-- | What the step before made, and whether that is a preparation's item.
data Previous = Previous {leaf :: Leaf, prepared :: Bool}

{- | Why a page's answers rule out building a dish. Checked before asking
how each line is prepared, so a rejected page is never asked that.
-}
rejection :: Env -> RecipePage -> PageAnswers use -> Maybe Text
rejection env page answers = leftToMaybe (outline env page answers)

-- | The dish a page makes, and its steps that work on food.
outline :: Env -> RecipePage -> PageAnswers use -> Either Text (IngredientId, NonEmpty Kept)
outline env page answers = do
    unless answers.singleDish.value $
        Left ("Jev judged it not a single dish (p=" <> show2 answers.singleDish.probability <> ")")
    dishId <- maybeToRight "the title names no ingredient" (ingredientFor env page.title)
    traverse_ (lineIngredient env . fst) answers.ingredients
    kept <- maybeToRight "Jev judged that no step works on food" (nonEmpty [Kept s a [] | (s, a) <- toList answers.steps, a.actsOnFood.value])
    pure (dishId, kept)

lineIngredient :: Env -> Line -> Either Text IngredientId
lineIngredient env l = maybeToRight ("no ingredient ID for '" <> l.text <> "'") (ingredientFor env l.parsed.ingredient)

dish :: Env -> RecipePage -> PageAnswers FirstUse -> Either Text Dish
dish env page answers = do
    (dishId, kept) <- outline env page answers
    uses <- forM (NE.zip (1 :| [2 ..]) answers.ingredients) $ \(i, (l, j)) -> (\ing -> Use i l ing j) <$> lineIngredient env l
    let lastNumber = (last kept).step.number
        assigned = foldl' assign kept [(u, f.step) | u <- toList uses, Just f <- [u.firstUse.value]]
        unused =
            [ Issue "inputs" Missing ("no step uses " <> quote u.line.text <> " (p=" <> show2 u.firstUse.probability <> ")")
            | u <- toList uses
            , isNothing u.firstUse.value
            ]
        preheats = [(s.number, t) | (s, _) <- toList answers.steps, "preheat" `T.isInfixOf` T.toLower s.text, Just t <- [temperatureC s.text]]
    (recipes, made, prepared) <- chain dishId preheats lastNumber (uncertain "source" answers.singleDish) unused (toList assigned)
    recipes' <- maybeToRight "no step takes in any ingredient" (nonEmpty recipes)
    let madeIds = map (.id) made <> map (.makes) prepared
    pure
        Dish
            { source = Source{id = sourceId, name = "Wikibooks Cookbook: " <> titleText page.title, url = Just (pageUrl page.title page.revid), licence = originLicence Wikibooks}
            , recipes = recipes'
            , made = [i | i <- made, not (Map.member i.id env.world.ingredients)]
            , prepared
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
    -- A line goes in at the first kept step at or after the one Jev
    -- named, or the last kept step when none is.
    assign ks (u, n) =
        let target = maybe (last ks).step.number (.step.number) (find ((>= n) . (.step.number)) ks)
         in fmap (\k -> if k.step.number == target then k{intake = k.intake <> [u]} else k) ks
    sourceId = SourceId ("wikibooks_" <> page.slug)
    quote t = "'" <> t <> "'"

    {- The first recipe carries the issues about the page, the last those
    about the dish. A step that is a preparation of what the dataset or an
    earlier preparation made makes the item for that preparation; once a
    step takes in an intermediate of the page's own, every step after does
    too. -}
    chain dishId preheats lastNumber pageIssues dishIssues = go Nothing [] [] []
      where
        go _ rs ms ps [] = Right (reverse rs, reverse ms, reverse ps)
        go previous rs ms ps (k : ks) =
            case (previous, k.intake) of
                (Nothing, []) -> go Nothing rs ms ps ks
                _ -> do
                    let n = k.step.number
                        ownId = if n == lastNumber then dishId else IngredientId (page.slug <> "_step_" <> show n)
                    preps <- forM [(u, p) | u <- k.intake, Just p <- [usePrep u]] (uncurry (prepRecipe n))
                    let takenAs u = fromMaybe u.ingredient (listToMaybe [s.makes | (v, _, s) <- preps, v.number == u.number])
                        leaves = [(False, p.leaf) | Just p <- [previous]] <> [(u.line.parsed.optional, Leaf (takenAs u) u.line.parsed.amount) | u <- k.intake]
                        (outAmount, outNotes) = outputAmount (map snd leaves)
                        prepRecipes = reverse [r | (_, r, _) <- preps]
                        prepSteps = reverse [s | (_, _, s) <- preps]
                    recipeId <- stepRecipeId (".step_" <> show n)
                    (environment, envIssues) <- stepEnvironment k (latestPreheat n)
                    let (end, endIssues) = stepEnd k.step.text (ambientPlace k)
                        recipe outId =
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
                                        , concatMap (uncertain "inputs" . (.firstUse)) k.intake
                                        , [estimate "inputs" (quote u.line.text <> ": " <> note) | u <- k.intake, note <- u.line.parsed.estimates]
                                        , map (estimate "outputs") outNotes
                                        , envIssues
                                        , endIssues
                                        , if null rs then pageIssues else []
                                        , if n == lastNumber then dishIssues else []
                                        ]
                                }
                        preparation = do
                            guard (all (.prepared) previous)
                            fst <$> preparationOf (determinesOutput env.world) (recipe ownId)
                        intermediate =
                            Ingredient
                                { id = ownId
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
                    case preparation of
                        Just p ->
                            let outId = preparedId env p
                                step = PreparedStep p outId k.answers.form (dishLabel page.title <$ guard (n == lastNumber))
                             in go (Just (Previous (Leaf outId outAmount) True)) (recipe outId : prepRecipes <> rs) ms (step : prepSteps <> ps) ks
                        Nothing -> go (Just (Previous (Leaf ownId outAmount) False)) (recipe ownId : prepRecipes <> rs) (intermediate : ms) (prepSteps <> ps) ks
        latestPreheat n = viaNonEmpty last [t | (m, t) <- preheats, m <= n]

    {- A line prepared on its own for step n. It has no sourceRef, which
    reviewed overlays match on, so it never stands in for the step. -}
    prepRecipe n u (technique, form) = do
        recipeId <- stepRecipeId (".step_" <> show n <> ".prep_" <> show u.number)
        let p = Preparation u.ingredient technique.value
            makes = preparedId env p
            amount = u.line.parsed.amount
            (end, endIssues) = stepEnd u.line.text (Just Room)
            recipe =
                Recipe
                    { id = recipeId
                    , source = sourceId
                    , sourceRef = Nothing
                    , technique = technique.value
                    , inputs = Select Nothing Nothing [Right (Leaf u.ingredient amount)]
                    , outputs = Outputs (Output makes amount Main Nothing :| [])
                    , environment = Nothing
                    , end
                    , stateNote = Just u.line.text
                    , issues = uncertain "technique" technique <> [estimate "outputs" "assumed preparing it keeps its amount"] <> endIssues
                    }
        Right (u, recipe, PreparedStep p makes form Nothing)
    stepRecipeId suffix = first toText (mkId (page.slug <> suffix) :: Either String RecipeId)
    ambientPlace k = case k.answers.medium.value of
        Ambient place -> Just place
        Medium _ -> Nothing

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
        Ambient _ -> Right (Nothing, [])
        Medium m -> do
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

    stepEnd text place =
        let (amount, notes) = case duration text of
                Just d -> (d.amount, d.notes)
                Nothing -> (Amount 1 (UnitId "min"), ["no duration given; assumed 1 minute"])
         in ( ByTime amount (ambientSensitivity env.constants <$> place)
            , map (estimate "end") notes
            )

{- | How a step that nothing heats depends on where it sits. One in the
fridge or freezer happens only while it is that cold, so it stops above
the top of the temperature band its place is in; one at room temperature
has no such ceiling, so it carries on regardless.
-}
ambientSensitivity :: Constants -> Place -> Sensitivity
ambientSensitivity c place =
    Sensitivity
        { refTempC = tempC
        , q10 = 1
        , minWaterActivity = Nothing
        , maxWaterActivity = Nothing
        , minTempC = Nothing
        , maxTempC = case place of
            Room -> Nothing
            _ -> bandCeiling c.tempBands tempC
        }
  where
    tempC = case place of
        Room -> c.storageTempC.room
        Fridge -> c.storageTempC.fridge
        Freezer -> c.storageTempC.freezer

-- | The upper bound of the band a temperature is in, unless that is the open-ended top band.
bandCeiling :: TempBands -> Double -> Maybe Double
bandCeiling bands c = (.belowC) =<< find ((== bandFromCelsius bands c) . (.id)) (tempBandEntries bands)

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
