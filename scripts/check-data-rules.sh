#!/usr/bin/env bash
# Each folder level under data/layers/ is a tag axis, and each folder name a
# tag on the entries inside it. Every tag in use needs a Claude rule at
# .claude/rules/data/<axis>/<tag>.md whose frontmatter globs exactly the
# entries carrying that tag, and every rule must name a folder `babel layout`
# allows. With --fix, missing rules are created empty.
set -euo pipefail

root=$(git rev-parse --show-toplevel)
rules=.claude/rules/data
fix=false
[[ ${1-} == --fix ]] && fix=true

# axes[depth] is an axis name, allowed[depth] the folder names it allows.
axes=() allowed=()
while read -r axis folders; do
	axes+=("$axis")
	allowed+=(" $folders ")
done < <(babel layout)

depth_of() {
	local depth
	for depth in "${!axes[@]}"; do
		if [[ ${axes[depth]} == "$1" ]]; then
			echo "$depth"
			return
		fi
	done
	return 1
}

# The entries tagged <tag> on the axis at <depth>.
glob() {
	local depth=$1 tag=$2 prefix=data/layers
	for ((i = 0; i < depth; i++)); do prefix+='/*'; done
	printf '%s/%s/**' "$prefix" "$tag"
}

frontmatter() {
	printf -- '---\npaths:\n  - "%s"\n---\n' "$1"
}

# Folder names in use on the axis at <depth>, limited to ones babel layout allows.
tags_in_use() {
	git -C "$root" ls-files -- data/layers |
		awk -F/ -v field=$(($1 + 3)) -v fields=$((${#axes[@]} + 3)) 'NF == fields { print $field }' |
		sort -u |
		while IFS= read -r tag; do
			if [[ ${allowed[$1]} == *" $tag "* ]]; then echo "$tag"; fi
		done
}

failed=false
fail() {
	printf '%s\n' "$1" >&2
	failed=true
}

for depth in "${!axes[@]}"; do
	while IFS= read -r tag; do
		rule=$rules/${axes[depth]}/$tag.md
		if [[ -f $root/$rule ]]; then
			continue
		elif $fix; then
			mkdir -p "$(dirname "$root/$rule")"
			frontmatter "$(glob "$depth" "$tag")" >"$root/$rule"
		else
			fail "$rule: missing (create it with $0 --fix)"
		fi
	done < <(tags_in_use "$depth")
done

# A rule may be written before any entry carries its tag, but not for a folder
# that cannot exist, since its glob would never match anything.
while IFS= read -r file; do
	rel=${file#"$root/$rules/"}
	axis=${rel%%/*}
	tag=${rel#*/}
	tag=${tag%.md}
	if [[ $rel != */*.md || $tag == */* ]] || ! depth=$(depth_of "$axis") || [[ ${allowed[depth]} != *" $tag "* ]]; then
		fail "$rules/$rel: no such folder in \`babel layout\`; remove it"
	elif [[ $(head -n 4 "$file") != "$(frontmatter "$(glob "$depth" "$tag")")" ]]; then
		fail "$rules/$rel: frontmatter must be exactly:"$'\n'"$(frontmatter "$(glob "$depth" "$tag")")"
	fi
done < <(find "$root/$rules" -type f | sort)

if $failed; then exit 1; fi
