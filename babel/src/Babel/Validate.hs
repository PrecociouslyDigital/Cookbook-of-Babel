-- | One entry point for the CLI and the tests: load, check, report.
module Babel.Validate (
    Report (..),
    validate,
)
where

import Babel.Check
import Babel.Diagnostic
import Babel.Load
import Babel.Model

data Report = Report
    { diagnostics :: [Diagnostic]
    , licence :: Licence
    -- ^ The build's effective (strictest) licence.
    , world :: World
    }

-- | Validate the data root. With @fix@, rewrite non-canonical files.
validate :: Bool -> FilePath -> IO Report
validate fixFiles root = do
    (loadDiags, w) <- loadWorld fixFiles root
    pure Report{diagnostics = loadDiags ++ checkWorld w, licence = effectiveLicence w, world = w}
