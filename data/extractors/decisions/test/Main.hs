{- | Properties of how requests are split into keyed parts, how answers are
read back from them, and how dependent decisions wait on earlier ones.
-}
module Main (main) where

import Data.Aeson (Value (..), object, (.=))
import Data.Aeson qualified as Aeson
import Data.Aeson.Key qualified as Key
import Data.Aeson.KeyMap qualified as KM
import Data.Map.Strict qualified as Map
import Decisions.Decide (Decided (..), andThen, decide)
import Decisions.Request (Ask, Judged (..), Key, Model (..), Part (..), Request (..), choice, keyText, maxOptions, mkKey, noul, requestParts)
import Hedgehog (Gen, Property, assert, failure, forAll, property, (===))
import Hedgehog.Gen qualified as Gen
import Hedgehog.Range qualified as Range
import Test.Tasty (defaultMain, testGroup)
import Test.Tasty.HUnit (testCase, (@?=))
import Test.Tasty.Hedgehog (testPropertyNamed)

main :: IO ()
main =
    defaultMain $
        testGroup
            "babel-decisions"
            [ testGroup
                "properties"
                [ prop "request keys do not depend on the order of the state's keys" keysIgnoreOrder
                , prop "rewording a question changes the key of only the part that holds it" rewordingIsLocal
                , prop "a part offers at most maxOptions, unless one question alone offers more" partsWithinOptions
                , prop "a request key reads back from its text" keysRoundTrip
                , prop "answering every part decodes the options chosen" answersRoundTrip
                , prop "a follow-up is asked only once the first decision is answered" followUpWaits
                ]
            , testCase "a request's body and key are fixed by its content" golden
            , testCase "a request too large for its model is unusable" tooLarge
            ]
  where
    prop name = testPropertyNamed name (fromString name)

-- | The number of options each question offers.
genOptionCounts :: Gen [Int]
genOptionCounts = Gen.list (Range.linear 1 12) (Gen.int (Range.linear 1 200))

{- | A choice question per count, named @q1@, @q2@, …, worded by its number.
Option @oN@ stands for @N@.
-}
choices :: (Int -> Text) -> [Int] -> Ask [Judged Int]
choices wording counts = traverse ask (zip [1 ..] counts)
  where
    ask (i, n) = choice ("q" <> show i) (wording i) (fmap (\o -> ("o" <> show o, o, "option " <> show o)) (0 :| [1 .. n - 1]))

choicesRequest :: (Int -> Text) -> [Int] -> Request [Judged Int]
choicesRequest wording counts = Request{model = Jev, state = object ["case" .= ("generated" :: Text)], ask = choices wording counts}

-- | The questions in a part's body, with how many options each offers.
partQuestions :: Part -> [(Text, Int)]
partQuestions p = case Aeson.decode p.body of
    Just (Object b) | Just (Object qs) <- KM.lookup "questions" b -> [(Key.toText k, options q) | (k, q) <- KM.toList qs]
    _ -> []
  where
    options = \case
        Object q | Just (Object c) <- KM.lookup "criteria" q -> KM.size c
        _ -> 0

keysIgnoreOrder :: Property
keysIgnoreOrder = property $ do
    pairs <- forAll (Map.toList . Map.fromList <$> Gen.list (Range.linear 1 10) ((,) <$> Gen.text (Range.linear 1 5) Gen.alpha <*> Gen.text (Range.linear 0 10) Gen.alpha))
    shuffled <- forAll (Gen.shuffle pairs)
    let keys ps = map (.key) (requestParts (choicesRequest (const "Which?") [3]){state = object [Key.fromText k .= x | (k, x) <- ps]})
    keys pairs === keys shuffled

rewordingIsLocal :: Property
rewordingIsLocal = property $ do
    counts <- forAll genOptionCounts
    reworded <- forAll (Gen.int (Range.linear 1 (length counts)))
    let wording i = "Which option does question " <> show i <> " pick?"
        keys w = map (.key) (requestParts (choicesRequest w counts))
        before = keys wording
        after = keys (\i -> if i == reworded then "Reworded: " <> wording i else wording i)
    length after === length before
    length (filter id (zipWith (/=) before after)) === 1

partsWithinOptions :: Property
partsWithinOptions = property $ do
    counts <- forAll (Gen.list (Range.linear 1 12) (Gen.int (Range.linear 1 (maxOptions + 100))))
    for_ (requestParts (choicesRequest (const "Which?") counts)) $ \p -> do
        let qs = partQuestions p
        assert (sum (map snd qs) <= maxOptions || length qs == 1)

keysRoundTrip :: Property
keysRoundTrip = property $ do
    counts <- forAll genOptionCounts
    for_ (requestParts (choicesRequest (const "Which?") counts)) $ \p ->
        mkKey (keyText p.key) === Just p.key

answersRoundTrip :: Property
answersRoundTrip = property $ do
    counts <- forAll genOptionCounts
    picks <- forAll (traverse (\n -> Gen.int (Range.constant 0 (n - 1))) counts)
    let request = choicesRequest (const "Which?") counts
        picked = Map.fromList (zip ["q" <> show i | i <- [1 :: Int ..]] picks)
        answerTo name = object ["choice" .= option, "probabilities" .= object [Key.fromText option .= (0.75 :: Double)]]
          where
            option = "o" <> maybe "" show (Map.lookup name picked)
        answers p = object [Key.fromText name .= answerTo name | (name, _) <- partQuestions p]
        decisions = Map.fromList [(p.key, answers p) | p <- requestParts request]
    case decide decisions request of
        Answered judged -> judged === map (`Judged` 0.75) picks
        _ -> failure

-- | The keys a decision still waits on; empty once it is answered.
waitingOn :: Decided a -> Maybe [Key]
waitingOn = \case
    Answered _ -> Just []
    Unasked ps -> Just (map (.key) ps)
    Unusable _ -> Nothing

followUpWaits :: Property
followUpWaits = property $ do
    firstAnswered <- forAll Gen.bool
    followUpAnswered <- forAll Gen.bool
    let yesNo name = Request{model = Jev, state = object ["about" .= name], ask = noul name "Is it?" "yes" "no"}
        keyOf name = map (.key) (requestParts (yesNo name))
        answeredBy name = Map.fromList [(k, object [Key.fromText name .= object ["noul" .= (0.9 :: Double)]]) | k <- keyOf name]
        decisions = (if firstAnswered then answeredBy "first" else mempty) <> (if followUpAnswered then answeredBy "follow_up" else mempty)
        decided = decide decisions (yesNo "first") `andThen` const (decide decisions (yesNo "follow_up"))
        expected
            | not firstAnswered = keyOf "first"
            | not followUpAnswered = keyOf "follow_up"
            | otherwise = []
    waitingOn decided === Just expected

-- | Answers are cached by key, so a change to how bodies are written would orphan every one.
golden :: IO ()
golden = do
    let r = Request{model = Jev, state = object ["recipe" .= ("Toast" :: Text)], ask = noul "burnt" "Is the toast burnt?" "black" "golden"}
    [(keyText p.key, p.body) | p <- requestParts r]
        @?= [
                ( "894498a3b9011ea49d82626539e33f957b1aeaabef45bc24f73290f7e69ff10c"
                , "{\"model\":\"typesafe/jev-1.14\",\"questions\":{\"burnt\":{\"criteria\":{\"false\":\"golden\",\"true\":\"black\"},\"instructions\":\"Is the toast burnt?\",\"type\":\"noul\"}},\"state\":{\"recipe\":\"Toast\"}}"
                )
            ]

tooLarge :: IO ()
tooLarge = do
    let r = Request{model = Jev, state = object ["text" .= replicate 40000 'x'], ask = noul "long" "Is it long?" "yes" "no"}
    isUnusable (decide mempty r) @?= True
  where
    isUnusable = \case
        Unusable _ -> True
        _ -> False
