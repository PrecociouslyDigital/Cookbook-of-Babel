#!/usr/bin/env bash
# Each folder level under data/layers/ is a tag axis, and each folder name a
# tag on the entries inside it. Every tag in use needs a Claude rule at
# .claude/rules/data/<axis>/<tag>.md whose frontmatter globs exactly the
# entries carrying that tag. With --fix, missing rules are created empty.
set -euo pipefail

axes=(layer licence origin kind)

root=$(git rev-parse --show-toplevel)
fix=false
[[ ${1-} == --fix ]] && fix=true

# The entries tagged <tag> on the axis at <depth> (0 is the layer).
glob() {
	local depth=$1 tag=$2 prefix=data/layers
	for ((i = 0; i < depth; i++)); do prefix+='/*'; done
	printf '%s/%s/**' "$prefix" "$tag"
}

frontmatter() {
	printf -- '---\npaths:\n  - "%s"\n---\n' "$1"
}

failed=false
for depth in "${!axes[@]}"; do
	axis=${axes[depth]}
	while IFS= read -r tag; do
		rule=.claude/rules/data/$axis/$tag.md
		expected=$(frontmatter "$(glob "$depth" "$tag")")
		if [[ ! -f $root/$rule ]]; then
			if $fix; then
				mkdir -p "$(dirname "$root/$rule")"
				printf '%s\n' "$expected" >"$root/$rule"
			else
				echo "$rule: missing (create it with $0 --fix)" >&2
				failed=true
			fi
		elif [[ $(head -n 4 "$root/$rule") != "$expected" ]]; then
			echo "$rule: frontmatter must be exactly:" >&2
			echo "$expected" >&2
			failed=true
		fi
	done < <(find "$root/data/layers" -mindepth $((depth + 1)) -maxdepth $((depth + 1)) -type d -exec basename {} \; | sort -u)
done

if $failed; then exit 1; fi
