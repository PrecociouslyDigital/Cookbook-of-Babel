-- | One entry point for the CLI and the tests: load, check, report.
module Babel.Validate (
    Report (..),
    validate,
)
where

import Babel.Check (checkWorld, effectiveLicence)
import Babel.Diagnostic (Diagnostic)
import Babel.Load (World, loadWorld)
import Babel.Model.Source (Licence)

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
