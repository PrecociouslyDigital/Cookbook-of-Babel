{- | The data model, re-exported in one place. These types are the source
of truth: the JSON files are decoded through their codecs, and JSON Schema
and language bindings will be derived from the same codecs.

Import what you need with an explicit list (hlint enforces that). Library
modules import the specific "Babel.Model.*" submodule instead, so they only
recompile when that part of the model changes.
-}
module Babel.Model (
    -- * Identifiers
    module Babel.Model.Id,
    IsId (..),
    Slug (..),
    DottedSlug (..),
    isSlug,

    -- * Entities
    module Babel.Model.Common,
    module Babel.Model.Ingredient,
    module Babel.Model.Technique,
    module Babel.Model.Recipe,
    module Babel.Model.Source,

    -- * Registries and metadata
    module Babel.Model.Registry,
    module Babel.Model.Dataset,
    module Babel.Model.Constants,
)
where

import Babel.Codec (DottedSlug (..), IsId (..), Slug (..), isSlug)
import Babel.Model.Common
import Babel.Model.Constants
import Babel.Model.Dataset
import Babel.Model.Id
import Babel.Model.Ingredient
import Babel.Model.Recipe
import Babel.Model.Registry
import Babel.Model.Source
import Babel.Model.Technique
