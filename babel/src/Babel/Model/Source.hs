-- | Sources, their SPDX-mapped licences, and the origins they come from.
module Babel.Model.Source (
    Source (..),
    Licence (..),
    licenceSpdx,
    satisfies,
    licenceExpression,
    Origin (..),
    originSlug,
    originLicence,
)
where

import Autodocodec (HasCodec (..), stringConstCodec)
import Babel.Codec (genericCodec)
import Babel.Model.Id (SourceId)
import Data.List.NonEmpty qualified as NE

{- | SPDX-mapped. 'Original' (@LicenseRef-original@) is content written for
this project.
-}
data Licence = Cc0 | Original | CcBy40 | CcBySa40 | Odbl10
    deriving stock (Eq, Ord, Show, Enum, Bounded)

licenceSpdx :: Licence -> Text
licenceSpdx = \case
    Cc0 -> "CC0-1.0"
    Original -> "LicenseRef-original"
    CcBy40 -> "CC-BY-4.0"
    CcBySa40 -> "CC-BY-SA-4.0"
    Odbl10 -> "ODbL-1.0"

{- | Whether distributing under the first licence also meets the second's
terms. Licences are not totally ordered: share-alike CC-BY-SA and ODbL
each demand their own terms, so neither satisfies the other.
-}
satisfies :: Licence -> Licence -> Bool
satisfies a b = a == b || b `elem` weaker a
  where
    weaker = \case
        Cc0 -> []
        Original -> [Cc0]
        CcBy40 -> [Cc0, Original]
        CcBySa40 -> [Cc0, Original, CcBy40]
        Odbl10 -> [Cc0, Original]

-- | Licences that all apply, as an SPDX expression: @CC-BY-SA-4.0 AND ODbL-1.0@.
licenceExpression :: Set Licence -> Text
licenceExpression ls
    | null ls = "NONE"
    | otherwise = mconcat (intersperse " AND " (map licenceSpdx (toList ls)))

instance HasCodec Licence where
    codec = stringConstCodec (NE.fromList [(l, licenceSpdx l) | l <- [minBound ..]])

{- | The body of work an entity comes from, recorded only by the entity's
folder. A body of work has one licence ('originLicence').
-}
data Origin = OriginalWork | Wikibooks | OpenFoodFacts
    deriving stock (Eq, Ord, Show, Enum, Bounded)

originSlug :: Origin -> Text
originSlug = \case
    OriginalWork -> "original"
    Wikibooks -> "wikibooks"
    OpenFoodFacts -> "openfoodfacts"

originLicence :: Origin -> Licence
originLicence = \case
    OriginalWork -> Original
    Wikibooks -> CcBySa40
    OpenFoodFacts -> Odbl10

data Source = Source {id :: SourceId, name :: Text, url :: Maybe Text, licence :: Licence}
    deriving stock (Eq, Generic, Show)

instance HasCodec Source where codec = genericCodec
