-- | Walking the technique is-a DAG.
module Babel.Lineage (ancestors) where

import Babel.Load (Located (..), World (..))
import Babel.Model.Id (TechniqueId)
import Babel.Model.Technique (Technique (..))
import Data.Map.Strict qualified as Map

-- | A technique and everything above it in the is-a DAG, nearest first.
ancestors :: World -> TechniqueId -> [TechniqueId]
ancestors w t0 = go [] [t0]
  where
    go seen = \case
        [] -> reverse seen
        t : queue
            | t `elem` seen -> go seen queue
            | otherwise -> go (t : seen) (queue <> maybe [] (.value.parents) (Map.lookup t w.techniques))
