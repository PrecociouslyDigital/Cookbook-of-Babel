#!/usr/bin/env bash
# Runs the whole Wikibooks extraction: fetch the dump, extract, ask the models
# every pending decision, and extract again, until nothing is pending or the
# day's free requests run out. Then validates the dataset. Needs the API key:
#   secretspec run --reason "extract the wikibooks cookbook" -- data/extractors/wikibooks/run.sh
set -euo pipefail

# babel-wikibooks stands in for the whole devenv shell: if it's missing, so
# are babel and the bzcat/curl/jq the pipeline needs, so re-exec inside one.
if ! command -v babel-wikibooks >/dev/null; then
	exec devenv shell -- "$0" "$@"
fi

here=$(cd "$(dirname "$0")" && pwd)
cd "$(git rev-parse --show-toplevel)"
dump=$("$here/fetch-dump.sh")

extract() { bzcat "$dump" | babel-wikibooks extract; }
pending() { [[ -n $(ls -A .cache/wikibooks/pending) ]]; }

extract
while pending; do
	answered=true
	"$here/decide.sh" || answered=false
	extract
	$answered || break
done
babel validate data
