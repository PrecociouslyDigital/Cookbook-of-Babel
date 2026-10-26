#!/usr/bin/env bash
# Builds the recipe graph explorer into build/explorer from the committed datagen
# output. Serve that folder over HTTP to use it; the page fetches graph.json.
set -euo pipefail
cd "$(dirname "$0")/.."

out=build/explorer
mkdir -p "$out"
python3 explorer/extract.py src/generated/resources >"$out/graph.json"
{
	printf '<!doctype html>\n<meta charset="utf-8">\n<meta name="viewport" content="width=device-width, initial-scale=1">\n'
	cat explorer/index.html
} >"$out/index.html"
