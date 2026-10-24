{- | Validation findings. Errors fail the build; warnings (soft checks) are
printed and never fail it.
-}
module Babel.Diagnostic (
    Severity (..),
    Diagnostic (..),
    errorIn,
    warningIn,
    isError,
    renderDiagnostic,
)
where

import Data.Text qualified as T

data Severity = Error | Warning
    deriving stock (Eq, Ord, Show)

-- | 'rule' is a short stable tag (e.g. @dangling-ref@) that tests match on.
data Diagnostic = Diagnostic
    { severity :: Severity
    , rule :: Text
    , file :: Maybe FilePath
    , message :: Text
    }
    deriving stock (Eq, Show)

errorIn, warningIn :: Text -> Maybe FilePath -> Text -> Diagnostic
errorIn = Diagnostic Error
warningIn = Diagnostic Warning

isError :: Diagnostic -> Bool
isError d = severity d == Error
  where
    severity (Diagnostic s _ _ _) = s

renderDiagnostic :: Diagnostic -> Text
renderDiagnostic (Diagnostic sev r f m) =
    T.concat [label, "[", r, "] ", maybe "" (\p -> T.pack p <> ": ") f, m]
  where
    label = case sev of
        Error -> "error"
        Warning -> "warning"
