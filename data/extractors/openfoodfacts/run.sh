#!/usr/bin/env bash
# Runs the whole OpenFoodFacts extraction: fetch the pinned products and
# taxonomy, extract, and validate the dataset.
set -euo pipefail

# babel-openfoodfacts stands in for the whole devenv shell: if it's missing,
# so are babel and the curl/duckdb the fetch needs, so re-exec inside one.
if ! command -v babel-openfoodfacts >/dev/null; then
	exec devenv shell -- "$0" "$@"
fi

here=$(cd "$(dirname "$0")" && pwd)
cd "$(git rev-parse --show-toplevel)"
cache=$("$here/fetch.sh")
babel-openfoodfacts extract --cache "$cache"
babel validate data
