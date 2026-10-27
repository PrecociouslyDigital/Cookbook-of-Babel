#!/usr/bin/env bash
# Copies the pinned FoodKeeper data file into .cache/foodkeeper/, checks it
# against the SHA-256 recorded here, and prints its path. USDA's CDN refuses
# scripted downloads, so the file is downloaded by hand in a browser and
# given to this script, by default from ~/Downloads. The pin is data v128;
# bump it deliberately.
#   fetch.sh [path/to/foodkeeper.json]
set -euo pipefail

url=https://www.fsis.usda.gov/shared/data/EN/foodkeeper.json
sha256=037df0d301126acd8a48824506f04d49d8be1a1144b81fa69e4170228dc2e91a

root=$(git rev-parse --show-toplevel)
cache=$root/.cache/foodkeeper
file=$cache/foodkeeper.json
mkdir -p "$cache"

checksum() { shasum -a 256 "$1" | cut -d' ' -f1; }

if [[ ! -f $file.verified ]]; then
	download=${1:-$HOME/Downloads/foodkeeper.json}
	if [[ ! -f $download ]]; then
		echo "no $download: download $url in a browser and pass its path to $0" >&2
		exit 1
	fi
	if [[ $(checksum "$download") != "$sha256" ]]; then
		echo "$download is not the pinned FoodKeeper file (SHA-256 $sha256); download $url in a browser, or bump the pin in $0" >&2
		exit 1
	fi
	cp "$download" "$file"
	touch "$file.verified"
fi
echo "$file"
