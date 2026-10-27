{- | The extractor's committed tables, checked against the FoodKeeper file
they were written for.

@techniques.json@ says how each product spoils: a spoilage technique for
every category, products that spoil differently from their category, and
the technique of the freezer step. Products are named by ID and label, so
a table written for another version of the file fails to load instead of
naming the wrong product.

@matches.json@ maps ingredient IDs to a product, or to @null@ when no
product fits; it decides before the models are asked.
-}
module FoodKeeper.Tables (
    Entry (..),
    Tables (..),
    readTables,
    Override (..),
    readOverrides,
)
where

import Babel.Codec (IsId (..))
import Babel.Model.Constants (Constants (..))
import Babel.Model.Id (IngredientId, TechniqueId)
import Data.Aeson (Object, Value (..), withObject, (.:))
import Data.Aeson qualified as Aeson
import Data.Aeson.Key qualified as Key
import Data.Aeson.KeyMap qualified as KM
import Data.Aeson.Types (Parser, parseEither)
import Data.Map.Strict qualified as Map
import FoodKeeper.Kinetics (Spoiling (..))
import FoodKeeper.Source (Category (..), FoodKeeper (..), FoodProduct (..), ProductId (..), categoryLabel, productLabel)

-- | A product and how it spoils.
data Entry = Entry {product :: FoodProduct, spoiling :: Spoiling}
    deriving stock (Eq, Show)

data Tables = Tables
    { catalogue :: Map ProductId Entry
    -- ^ Every product in the file.
    , frozen :: Spoiling
    }
    deriving stock (Eq, Show)

-- | Every technique must have default kinetics in the constants registry.
readTables :: FoodKeeper -> Constants -> LByteString -> Either String Tables
readTables fk constants bytes = do
    raw <- Aeson.eitherDecode bytes
    (categories, products, frozenTech) <- parseEither tablesP raw
    let byLabel = Map.fromList [(categoryLabel c, c.id) | c <- Map.elems fk.categories]
    forM_ (Map.keys categories) $ \l ->
        unless (Map.member l byLabel) (Left ("techniques.json: no category " <> show l))
    byCategory <- fmap Map.fromList . forM (Map.elems fk.categories) $ \c ->
        maybe (Left ("techniques.json: no technique for the category " <> show (categoryLabel c))) (fmap (c.id,) . spoiling) (Map.lookup (categoryLabel c) categories)
    exceptions <- fmap Map.fromList . forM products $ \(ref, t) -> (,) <$> productRef fk "techniques.json" ref <*> spoiling t
    frozen <- spoiling frozenTech
    catalogue <- forM fk.products $ \p ->
        Entry p <$> maybe (maybeToRight "unreachable: categories are checked above" (Map.lookup p.category byCategory)) Right (Map.lookup p.id exceptions)
    pure Tables{catalogue, frozen}
  where
    spoiling t =
        maybe
            (Left ("techniques.json: " <> toString (idText t) <> " has no spoilageKinetics in the constants registry"))
            (Right . Spoiling t)
            (Map.lookup t constants.spoilageKinetics)

-- | A product named in a table: its ID, and its label to check it by.
data ProductRef = ProductRef Int Text

tablesP :: Value -> Parser (Map Text TechniqueId, [(ProductRef, TechniqueId)], TechniqueId)
tablesP = withObject "techniques.json" $ \o -> do
    categories <- traverse techniqueP =<< o .: "categories"
    products <- traverse (withObject "product" (\p -> (,) <$> productRefP p <*> (techniqueP =<< p .: "technique"))) =<< o .: "products"
    frozen <- techniqueP =<< o .: "frozen"
    pure (categories, products, frozen)

productRefP :: Object -> Parser ProductRef
productRefP o = ProductRef <$> o .: "product" <*> o .: "name"

productRef :: FoodKeeper -> String -> ProductRef -> Either String ProductId
productRef fk table (ProductRef n label) = case Map.lookup (ProductId n) fk.products of
    Nothing -> Left (table <> ": no product " <> show n)
    Just p
        | productLabel p == label -> Right p.id
        | otherwise -> Left (table <> ": product " <> show n <> " is " <> show (productLabel p) <> ", not " <> show label)

techniqueP :: Text -> Parser TechniqueId
techniqueP = either fail pure . mkId

-- | An entry of @matches.json@.
data Override = MatchTo ProductId | NoProduct
    deriving stock (Eq, Show)

readOverrides :: FoodKeeper -> LByteString -> Either String (Map IngredientId Override)
readOverrides fk bytes = do
    raw <- Aeson.eitherDecode bytes
    entries <- parseEither (withObject "matches.json" (pure . KM.toList)) raw
    fmap Map.fromList . forM entries $ \(k, v) -> do
        i <- mkId (Key.toText k)
        o <- case v of
            Null -> Right NoProduct
            _ -> MatchTo <$> (productRef fk ("matches.json: " <> toString (Key.toText k)) =<< parseEither (withObject "product" productRefP) v)
        pure (i, o)
