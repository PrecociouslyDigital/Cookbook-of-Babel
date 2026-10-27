{- | The FoodKeeper data file: its categories and products, and how long
each product keeps, as bought, in the pantry, the fridge and the freezer.

A storage place has several windows. A product as bought is described by
the plain one (e.g. @Refrigerate_*@) or, when that is empty, the one
counted from the date of purchase (@DOP_Refrigerate_*@); no product has
both. The after-opening and after-thawing windows describe something else
than the ingredient as bought, so they are not read.
-}
module FoodKeeper.Source (
    FoodKeeper (..),
    CategoryId (..),
    Category (..),
    categoryLabel,
    ProductId (..),
    FoodProduct (..),
    productLabel,
    productsIn,
    Storage (..),
    Metric (..),
    Window,
    mkWindow,
    windowSeconds,
    describeWindow,
    parseFoodKeeper,
)
where

import Data.Aeson (Object, Value, withObject, (.:), (.:?))
import Data.Aeson qualified as Aeson
import Data.Aeson.Key qualified as Key
import Data.Aeson.KeyMap qualified as KM
import Data.Aeson.Types (Parser, parseEither)
import Data.Map.Strict qualified as Map
import Data.Text qualified as T

newtype CategoryId = CategoryId Int
    deriving stock (Eq, Ord, Show)

newtype ProductId = ProductId Int
    deriving stock (Eq, Ord, Show)

data Category = Category {id :: CategoryId, name :: Text, subcategory :: Maybe Text}
    deriving stock (Eq, Show)

-- | @Meat: Fresh@, or just @Beverages@.
categoryLabel :: Category -> Text
categoryLabel c = c.name <> foldMap (": " <>) c.subcategory

data Metric = Hours | Days | Weeks | Months | Years
    deriving stock (Eq, Show, Enum, Bounded)

metricSeconds :: Metric -> Double
metricSeconds = \case
    Hours -> 3600
    Days -> day
    Weeks -> 7 * day
    Months -> 365.25 / 12 * day
    Years -> 365.25 * day
  where
    day = 86400

{- | How long a product keeps: between two positive bounds, in one metric.
Only 'mkWindow' builds one.
-}
data Window = Window {low :: Double, high :: Double, metric :: Metric}
    deriving stock (Eq, Show)

{- | A window from FoodKeeper's bounds. A product that gives only one bound
("up to 18 months") keeps that long.
-}
mkWindow :: Maybe Double -> Maybe Double -> Metric -> Maybe Window
mkWindow lo hi m = case (lo <|> hi, hi <|> lo) of
    (Just l, Just h) | 0 < l && l <= h -> Just (Window l h m)
    _ -> Nothing

-- | The geometric mean of the bounds: the middle of a range that spans a ratio, like 1–4 weeks.
windowSeconds :: Window -> Double
windowSeconds w = sqrt (w.low * w.high) * metricSeconds w.metric

-- | @3–5 days@, or @2 months@.
describeWindow :: Window -> Text
describeWindow w = bounds <> " " <> T.toLower (show w.metric)
  where
    bounds
        | w.low == w.high = number w.low
        | otherwise = number w.low <> "–" <> number w.high
    number x = if x == fromIntegral (round x :: Int) then show (round x :: Int) else show x

data Storage = Storage {pantry :: Maybe Window, fridge :: Maybe Window, freezer :: Maybe Window}
    deriving stock (Eq, Show)

data FoodProduct = FoodProduct
    { id :: ProductId
    , category :: CategoryId
    , name :: Text
    , subtitle :: Maybe Text
    , keywords :: [Text]
    , storage :: Storage
    }
    deriving stock (Eq, Show)

-- | @Cheese (hard such as cheddar, swiss, block parmesan)@
productLabel :: FoodProduct -> Text
productLabel p = p.name <> foldMap (\s -> " (" <> s <> ")") p.subtitle

-- | Every category a product names is one of 'categories'.
data FoodKeeper = FoodKeeper {categories :: Map CategoryId Category, products :: Map ProductId FoodProduct}
    deriving stock (Eq, Show)

productsIn :: FoodKeeper -> CategoryId -> [FoodProduct]
productsIn fk c = [p | p <- Map.elems fk.products, p.category == c]

-- * Parsing

{- | The file is @{"sheets": [{"name", "data": [row]}]}@, each row a list of
one-key objects.
-}
parseFoodKeeper :: LByteString -> Either String FoodKeeper
parseFoodKeeper bytes = do
    sheets <- parseEither sheetsP =<< Aeson.eitherDecode bytes
    let sheet name = maybeToRight ("no " <> toString name <> " sheet") (Map.lookup name sheets)
    categories <- traverse (parseEither categoryP) =<< sheet "Category"
    products <- traverse (parseEither productP) =<< sheet "Product"
    let fk = FoodKeeper (Map.fromList [(c.id, c) | c <- categories]) (Map.fromList [(p.id, p) | p <- products])
    forM_ products $ \p ->
        unless (Map.member p.category fk.categories) $
            Left ("product " <> toString (productLabel p) <> " names an unknown category")
    pure fk

sheetsP :: Value -> Parser (Map Text [Object])
sheetsP = withObject "FoodKeeper" $ \o -> do
    sheets <- o .: "sheets"
    fmap Map.fromList . forM sheets . withObject "sheet" $ \s -> do
        name <- s .: "name"
        rows <- s .: "data" :: Parser [[Value]]
        (name,) <$> traverse (fmap (foldl' KM.union KM.empty) . traverse (withObject "cell" pure)) rows

categoryP :: Object -> Parser Category
categoryP r = Category . CategoryId <$> r .: "ID" <*> (clean <$> r .: "Category_Name") <*> (fmap clean <$> r .:? "Subcategory_Name")

productP :: Object -> Parser FoodProduct
productP r =
    FoodProduct . ProductId
        <$> r .: "ID"
        <*> (CategoryId <$> r .: "Category_ID")
        <*> (clean <$> r .: "Name")
        <*> (mfilter (not . T.null) . fmap clean <$> r .:? "Name_subtitle")
        <*> (maybe [] (filter (not . T.null) . map clean . T.splitOn ",") <$> r .:? "Keywords")
        <*> (Storage <$> asBought "Pantry" <*> asBought "Refrigerate" <*> asBought "Freeze")
  where
    asBought place = (<|>) <$> window place <*> window ("DOP_" <> place)
    window prefix = do
        lo <- r .:? Key.fromText (prefix <> "_Min")
        hi <- r .:? Key.fromText (prefix <> "_Max")
        metric <- r .:? Key.fromText (prefix <> "_Metric")
        case metric of
            Nothing -> pure Nothing
            Just m -> (\ms -> ms >>= mkWindow lo hi) <$> metricP m

-- | 'Nothing' for the metrics that give no length of time.
metricP :: Text -> Parser (Maybe Metric)
metricP = \case
    "Hours" -> pure (Just Hours)
    "Days" -> pure (Just Days)
    "Weeks" -> pure (Just Weeks)
    "Months" -> pure (Just Months)
    "Years" -> pure (Just Years)
    "Year" -> pure (Just Years)
    m
        | m `elem` ["When Ripe", "Indefinitely", "Not Recommended", "Package use-by date"] -> pure Nothing
        | otherwise -> fail ("unknown metric " <> show m)

-- | Collapse runs of whitespace, which the file has inside names.
clean :: Text -> Text
clean = T.unwords . T.words
