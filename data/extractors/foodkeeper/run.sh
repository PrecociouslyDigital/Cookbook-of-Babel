#!/usr/bin/env bash
# Runs the whole FoodKeeper extraction: fetch the pinned file, extract, ask
# Jev every pending decision, and extract again, until nothing is pending.
# Then validates the dataset. Needs the API key:
#   secretspec run --reason "extract foodkeeper storage times" -- data/extractors/foodkeeper/run.sh [path/to/foodkeeper.json]
set -euo pipefail

# babel-foodkeeper stands in for the whole devenv shell: if it's missing, so
# are babel and the curl/jq decide.sh needs, so re-exec inside one.
if ! command -v babel-foodkeeper >/dev/null; then
	exec devenv shell -- "$0" "$@"
fi

here=$(cd "$(dirname "$0")" && pwd)
cd "$(git rev-parse --show-toplevel)"
source=$("$here/fetch.sh" "$@")

decisions=$here/decisions
pending=.cache/foodkeeper/pending

extract() { babel-foodkeeper extract --source "$source" --decisions "$decisions" --pending "$pending"; }
is_pending() { [[ -n $(ls -A "$pending") ]]; }

extract
while is_pending; do
	answered=true
	"$here/../decisions/decide.sh" "$decisions" "$pending" || answered=false
	extract
	$answered || break
done
babel validate data
