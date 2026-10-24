{-# LANGUAGE OverloadedRecordDot #-}

module Main (main) where

import Babel.Diagnostic
import Babel.Model
import Babel.Spoilage
import Babel.Validate
import Control.Monad (unless, when)
import Data.Text qualified as T
import Data.Text.IO qualified as T
import Options.Applicative
import System.Exit (exitFailure)
import System.IO (hSetEncoding, stderr, stdout, utf8)
import Text.Printf (printf)

data Command
    = Validate FilePath Bool
    | Spoilage FilePath T.Text Double (Maybe Double)

main :: IO ()
main = do
    mapM_ (`hSetEncoding` utf8) [stdout, stderr]
    execParser (info (commands <**> helper) (progDesc "Cookbook of Babel dataset tools")) >>= run

commands :: Parser Command
commands =
    hsubparser $
        command "validate" (info validateP (progDesc "Check the dataset; exit non-zero on any error"))
            <> command "query" (info (hsubparser (command "spoilage" (info spoilageP (progDesc "Which spoilage recipe happens first")))) (progDesc "Ask questions of the dataset"))
  where
    dataRoot = strArgument (metavar "DATA_ROOT" <> value "data" <> showDefault <> help "Data root")
    validateP = Validate <$> dataRoot <*> switch (long "fix" <> help "Rewrite non-canonically formatted files")
    spoilageP =
        (\ing t aw root -> Spoilage root ing t aw)
            <$> strArgument (metavar "INGREDIENT")
            <*> option auto (long "temp" <> metavar "C" <> help "Temperature in °C")
            <*> optional (option auto (long "aw" <> metavar "X" <> help "Water activity (default: the ingredient's own)"))
            <*> strOption (long "data" <> metavar "DATA_ROOT" <> value "data" <> showDefault <> help "Data root")

run :: Command -> IO ()
run = \case
    Validate root fix -> do
        report <- validate fix root
        let errors = filter isError report.diagnostics
        mapM_ (T.hPutStrLn stderr . renderDiagnostic) report.diagnostics
        T.putStrLn ("effective licence: " <> licenceSpdx report.licence)
        unless (null errors) $ do
            T.hPutStrLn stderr (T.pack (show (length errors)) <> " error(s)")
            exitFailure
    Spoilage root ing t aw -> do
        report <- validate False root
        when (any isError report.diagnostics) $ do
            T.hPutStrLn stderr "the dataset has errors; run `babel validate` first"
            exitFailure
        case mkId ing of
            Left err -> T.hPutStrLn stderr (T.pack err) >> exitFailure
            Right ingId -> case firstSpoilage report.world (Conditions t aw) ingId of
                Nothing -> putStrLn "no time-based spoilage applies under these conditions"
                Just (r, days) -> printf "%s after about %.1f days (%s)\n" (T.unpack (idText r.id)) days (show (bandFromCelsius t))
