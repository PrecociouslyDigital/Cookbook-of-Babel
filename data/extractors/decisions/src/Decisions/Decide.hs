{- | Looking up a request's answer among the decisions made so far. An
extraction is a pure function of those decisions: it uses the answered
ones, and returns the parts still to ask so they can be sent.
-}
module Decisions.Decide (
    Decisions,
    Decided (..),
    decide,
    andThen,
)
where

import Data.Aeson (Value)
import Data.ByteString.Lazy qualified as BL
import Data.Map.Strict qualified as Map
import Decisions.Request (Key, Part (..), Request (..), answer, maxRequestBytes, requestParts)

-- | The @answers@ object of every decision made so far, by request key.
type Decisions = Map Key Value

-- | A decision this extraction needs: answered, with parts still to ask, or unusable.
data Decided a = Answered a | Unasked [Part] | Unusable Text
    deriving stock (Functor)

-- | A decision that can only be asked once an earlier one is answered.
andThen :: Decided a -> (a -> Decided b) -> Decided b
andThen d next = case d of
    Answered a -> next a
    Unasked ps -> Unasked ps
    Unusable e -> Unusable e

decide :: Decisions -> Request a -> Decided a
decide decisions r
    | Just big <- find ((> maxRequestBytes r.model) . BL.length . (.body)) parts =
        Unusable ("the decision request is too large (" <> show (BL.length big.body) <> " bytes)")
    | otherwise = case traverse (\p -> Map.lookup p.key decisions) parts of
        Nothing -> Unasked [p | p <- parts, not (Map.member p.key decisions)]
        Just answers -> either (\e -> Unusable ("decision: " <> e)) Answered (answer r answers)
  where
    parts = requestParts r
