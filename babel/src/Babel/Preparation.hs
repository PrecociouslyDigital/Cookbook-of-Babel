{- | A preparation is one technique done to one ingredient, such as
julienning a potato. Doing the same technique to the same ingredient
makes the same thing, whichever recipe does it, so the dataset has at most
one ingredient per preparation. Consumers may merge further: a game whose
one machine does every kind of cutting can treat the preparations of a
potato by any cut as one item.
-}
module Babel.Preparation (
    Preparation (..),
    Prepared (..),
    preparationOf,
    determinesOutput,
    preparations,
    prepared,
)
where

import Autodocodec (HasCodec (..))
import Babel.Codec (genericCodec)
import Babel.Lineage (ancestors)
import Babel.Load (Located (..), World (..))
import Babel.Model.Constants (Constants (..))
import Babel.Model.Id (IngredientId, RecipeId, TechniqueId)
import Babel.Model.Recipe (Leaf (..), Outcome (..), Output (..), Recipe (..), Role (..), leaves, minimalLeaves)
import Data.Map.Strict qualified as Map

data Preparation = Preparation {ingredient :: IngredientId, technique :: TechniqueId}
    deriving stock (Eq, Ord, Show)

-- | A preparation and the ingredient it makes, as the export lists them.
data Prepared = Prepared {ingredient :: IngredientId, technique :: TechniqueId, makes :: IngredientId}
    deriving stock (Eq, Generic, Show)

instance HasCodec Prepared where codec = genericCodec

{- | The preparation a recipe is, and what it makes: a recipe that always
takes in exactly one ingredient and makes one main output, by a technique
that determines its output.
-}
preparationOf :: (TechniqueId -> Bool) -> Recipe -> Maybe (Preparation, IngredientId)
preparationOf determines r = do
    [l] <- pure (leaves r.inputs)
    [_] <- pure (minimalLeaves r.inputs)
    Outputs os <- pure r.outputs
    [o] <- pure [o | o <- toList os, o.role == Main]
    guard (determines r.technique)
    pure (Preparation l.ingredient r.technique, o.ingredient)

{- | Whether one technique done to one ingredient always makes the same
thing. Techniques under a non-preparation don't: separating an egg makes a
yolk or a white.
-}
determinesOutput :: World -> TechniqueId -> Bool
determinesOutput w t = case w.constants of
    Nothing -> True
    Just c -> not (any (`elem` c.nonPreparations) (ancestors w t))

-- | Every preparation the recipes make, with what each makes and the recipes that make it.
preparations :: World -> Map Preparation (Map IngredientId [RecipeId])
preparations w =
    Map.fromListWith
        (Map.unionWith (<>))
        [ (p, Map.singleton made [r.value.id])
        | r <- Map.elems w.recipes
        , Just (p, made) <- [preparationOf (determinesOutput w) r.value]
        ]

prepared :: World -> [Prepared]
prepared w = [Prepared p.ingredient p.technique made | (p, makes) <- Map.toList (preparations w), made <- Map.keys makes]
