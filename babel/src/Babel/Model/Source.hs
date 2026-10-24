-- | Sources, their SPDX-mapped licences, and the origins they come from.
module Babel.Model.Source (
    Source (..),
    Licence (..),
    licenceSpdx,
    licenceFromSpdx,
    licenceStrictness,
    Origin (..),
    originSlug,
    originFromSlug,
)
where

import Autodocodec (HasCodec (..), stringConstCodec)
import Babel.Codec (genericCodec)
import Babel.Model.Id (SourceId)
import Data.List.NonEmpty qualified as NE

{- | SPDX-mapped. 'Original' (@LicenseRef-original@) is content written for
this project.
-}
data Licence = Cc0 | Original | CcBy40 | CcBySa40
    deriving stock (Eq, Ord, Show, Enum, Bounded)

licenceSpdx :: Licence -> Text
licenceSpdx = \case
    Cc0 -> "CC0-1.0"
    Original -> "LicenseRef-original"
    CcBy40 -> "CC-BY-4.0"
    CcBySa40 -> "CC-BY-SA-4.0"

licenceFromSpdx :: Text -> Maybe Licence
licenceFromSpdx t = find ((== t) . licenceSpdx) universe

{- | Higher is stricter. A build's effective licence is the strictest one
of anything it includes.
-}
licenceStrictness :: Licence -> Int
licenceStrictness = fromEnum

instance HasCodec Licence where
    codec = stringConstCodec (NE.fromList [(l, licenceSpdx l) | l <- [minBound ..]])

{- | The body of work an entity comes from. Unlike the licence, it is recorded
only by the entity's folder.
-}
data Origin = OriginalWork | Wikibooks
    deriving stock (Eq, Ord, Show, Enum, Bounded)

originSlug :: Origin -> Text
originSlug = \case
    OriginalWork -> "original"
    Wikibooks -> "wikibooks"

originFromSlug :: Text -> Maybe Origin
originFromSlug t = find ((== t) . originSlug) universe

data Source = Source {id :: SourceId, name :: Text, url :: Maybe Text, licence :: Licence}
    deriving stock (Eq, Generic, Show)

instance HasCodec Source where codec = genericCodec
