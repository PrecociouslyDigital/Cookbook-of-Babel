{- | Canonical JSON rendering. A file is canonical when it is byte-identical
to 'render' of its own decoded value; @babel validate --fix@ rewrites files
that are not.

Key order comes from the codecs: autodocodec's 'toEncodingViaCodec' emits
fields in the order the codec lists them. Going through 'Data.Aeson.Value'
would lose that, because its objects are sorted maps. So the compact
encoding is re-read as an order-preserving token stream and re-indented:
2 spaces, @"key": value@, one element per line, a trailing newline.
-}
module Babel.Format (render) where

import Autodocodec (HasCodec, toEncodingViaCodec)
import Data.Aeson.Decoding.ByteString.Lazy (lbsToTokens)
import Data.Aeson.Decoding.Tokens (Lit (..), Number (..), TkArray (..), TkRecord (..), Tokens (..))
import Data.Aeson.Encoding qualified as E
import Data.Aeson.Key qualified as Key
import Data.ByteString.Builder (Builder, toLazyByteString)
import Data.ByteString.Builder qualified as B

render :: (HasCodec a) => a -> LByteString
render a = case value 0 (lbsToTokens compact) of
    Right (b, _) -> toLazyByteString (b <> "\n")
    -- Unreachable: the input is aeson's own encoding. Fall back to it rather
    -- than crash.
    Left _ -> compact <> "\n"
  where
    compact = E.encodingToLazyByteString (toEncodingViaCodec a)

-- | Render one value at the given depth, returning what follows it.
value :: Int -> Tokens k e -> Either e (Builder, k)
value depth = \case
    TkLit l k -> Right (lit l, k)
    TkText t k -> Right (E.fromEncoding (E.text t), k)
    TkNumber n k -> Right (number n, k)
    TkArrayOpen arr -> array depth arr
    TkRecordOpen rec -> record depth rec
    TkErr e -> Left e
  where
    lit = \case
        LitNull -> "null"
        LitTrue -> "true"
        LitFalse -> "false"
    number = \case
        NumInteger i -> B.integerDec i
        NumDecimal s -> E.fromEncoding (E.scientific s)
        NumScientific s -> E.fromEncoding (E.scientific s)

array :: Int -> TkArray k e -> Either e (Builder, k)
array depth = \case
    TkArrayEnd k -> Right ("[]", k)
    arr -> go True "[" arr
  where
    go isFirst acc = \case
        TkItem t -> do
            (b, rest) <- value (depth + 1) t
            go False (acc <> (if isFirst then "\n" else ",\n") <> indent (depth + 1) <> b) rest
        TkArrayEnd k -> Right (acc <> "\n" <> indent depth <> "]", k)
        TkArrayErr e -> Left e

record :: Int -> TkRecord k e -> Either e (Builder, k)
record depth = \case
    TkRecordEnd k -> Right ("{}", k)
    rec -> go True "{" rec
  where
    go isFirst acc = \case
        TkPair key t -> do
            (b, rest) <- value (depth + 1) t
            let pair = indent (depth + 1) <> E.fromEncoding (E.text (Key.toText key)) <> ": " <> b
            go False (acc <> (if isFirst then "\n" else ",\n") <> pair) rest
        TkRecordEnd k -> Right (acc <> "\n" <> indent depth <> "}", k)
        TkRecordErr e -> Left e

indent :: Int -> Builder
indent n = B.string7 (replicate (2 * n) ' ')
