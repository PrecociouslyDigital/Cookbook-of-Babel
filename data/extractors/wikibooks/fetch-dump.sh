#!/usr/bin/env bash
# Downloads the pinned Wikibooks pages-articles dump into .cache/wikibooks/,
# verifies it against the dump's published SHA-1, and prints its path.
# Pinning the date keeps every extraction reproducible; bump it deliberately.
set -euo pipefail

date=20261001
name=enwikibooks-$date-pages-articles.xml.bz2
base=https://dumps.wikimedia.org/enwikibooks/$date

root=$(git rev-parse --show-toplevel)
cache=$root/.cache/wikibooks
dump=$cache/$name
mkdir -p "$cache"

verify() {
	local expected
	expected=$(curl -fsS "$base/enwikibooks-$date-sha1sums.txt" | awk -v name="$name" '$2 == name { print $1 }')
	[[ -n $expected ]] && [[ $(shasum -a 1 "$dump" | cut -d' ' -f1) == "$expected" ]]
}

if [[ ! -f $dump.verified ]]; then
	curl -fsSL --retry 3 -C - -o "$dump" "$base/$name"
	if ! verify; then
		rm -f "$dump"
		echo "$name: checksum mismatch; deleted it, rerun to download again" >&2
		exit 1
	fi
	touch "$dump.verified"
fi
echo "$dump"
