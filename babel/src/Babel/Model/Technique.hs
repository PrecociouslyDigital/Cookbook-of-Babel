-- | Techniques and their is-a DAG.
module Babel.Model.Technique (
    Technique (..),
    TempRange (..),
)
where

import Autodocodec (HasCodec (..))
import Babel.Codec (genericCodec)
import Babel.Model.Common (Issue)
import Babel.Model.Id (TechniqueId)

data TempRange = TempRange {minC :: Double, maxC :: Double}
    deriving stock (Eq, Generic, Show)

instance HasCodec TempRange where codec = genericCodec

data Technique = Technique
    { id :: TechniqueId
    , label :: Text
    , parents :: [TechniqueId]
    , typicalTemp :: Maybe TempRange
    , issues :: [Issue]
    }
    deriving stock (Eq, Generic, Show)

instance HasCodec Technique where codec = genericCodec
