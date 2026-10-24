{- | Canonical JSON rendering: 2-space indent, a fixed key order, and a
trailing newline. Every data file must be byte-identical to the
rendering of its own decoded value; @babel validate --fix@ rewrites files
that are not.
-}
module Babel.Format (render, keyOrdering) where

import Data.Aeson (Value)
import Data.Aeson.Encode.Pretty
import Data.ByteString.Lazy (ByteString)
import Data.Text (Text)

render :: Value -> ByteString
render =
    encodePretty'
        defConfig
            { confIndent = Spaces 2
            , confCompare = keyOrdering
            , confTrailingNewline = True
            }

{- | Keys listed here come first, in this order (identity first, then
description, then the substance of a step, annotations last). Any other
key follows alphabetically.
-}
keyOrdering :: Text -> Text -> Ordering
keyOrdering = keyOrder firstKeys <> compare

firstKeys :: [Text]
firstKeys =
    [ "id"
    , "version"
    , "field"
    , "since"
    , "kind"
    , "from"
    , "to"
    , "label"
    , "name"
    , "url"
    , "licence"
    , "description"
    , "dimension"
    , "toBase"
    , "h"
    , "aliases"
    , "parents"
    , "target"
    , "axis"
    , "form"
    , "texture"
    , "taste"
    , "servingTags"
    , "densityGPerMl"
    , "waterActivity"
    , "nutrition"
    , "per100g"
    , "kcal"
    , "proteinG"
    , "fatG"
    , "carbsG"
    , "named"
    , "source"
    , "sourceRef"
    , "step"
    , "technique"
    , "typicalTemp"
    , "minC"
    , "maxC"
    , "inputs"
    , "min"
    , "max"
    , "of"
    , "ingredient"
    , "amount"
    , "qty"
    , "unit"
    , "role"
    , "chance"
    , "oneOf"
    , "weight"
    , "outputs"
    , "environment"
    , "temperatureC"
    , "medium"
    , "end"
    , "by"
    , "coreTempC"
    , "duration"
    , "sensitivity"
    , "refTempC"
    , "q10"
    , "minWaterActivity"
    , "maxWaterActivity"
    , "stateNote"
    , "note"
    , "issues"
    ]
