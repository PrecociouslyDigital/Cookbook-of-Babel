{- | Working out a value for every node of a graph where a node's value
depends on the values of others, cycles included: an ingredient's
nutrition on its recipe's inputs, a counted unit's mass on what the
recipe that yields it takes in.
-}
module Babel.Settle (
    Rule (..),
    settle,
)
where

import Data.Graph (SCC (..), flattenSCC, stronglyConnComp)
import Data.Map.Lazy qualified as LazyMap
import Data.Map.Strict qualified as Map
import Data.Set qualified as Set

{- | How to work out one node: the nodes it may look up, and its value
given a lookup for them. A node it looks up that has no rule is unknown.
-}
data Rule k v = Rule {needs :: [k], value :: (k -> Maybe v) -> Maybe v}

{- | Every node's value, each worked out once and only when looked up.

A node outside any cycle runs its rule against the final values of what
it needs. The members of a cycle are settled in rounds: each round,
every member not yet settled runs its rule against the members settled
in earlier rounds, the rest counting as unknown, and each value that
comes out is fixed. So no value rests on itself, and a cycle's members
settle in at most as many rounds as there are members.
-}
settle :: (Ord k) => Map k (Rule k v) -> Map k (Maybe v)
settle rules = table
  where
    final k = join (Map.lookup k table)
    table =
        LazyMap.fromList
            [ (k, Map.lookup k settled)
            | component <- stronglyConnComp [(k, k, rule.needs) | (k, rule) <- Map.toList rules]
            , let settled = settleComponent component
            , k <- flattenSCC component
            ]
    settleComponent = \case
        AcyclicSCC k -> maybe mempty (LazyMap.singleton k) (run final k)
        CyclicSCC members -> inRounds (Set.fromList members) members
    run look k = Map.lookup k rules >>= \rule -> rule.value look
    inRounds inCycle members = go mempty
      where
        go settled
            | Map.null new = settled
            | otherwise = go (settled <> new)
          where
            look k = if Set.member k inCycle then Map.lookup k settled else final k
            new = Map.fromList [(k, v) | k <- members, Map.notMember k settled, Just v <- [run look k]]
