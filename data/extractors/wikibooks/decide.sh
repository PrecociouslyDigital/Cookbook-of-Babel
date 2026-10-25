#!/usr/bin/env bash
# Sends every pending decision request to OpenRouter's Decisions API and
# saves the answers object of each response to decisions/<key>.json, where
# extract finds it: one line of sorted JSON, probabilities rounded to 4
# decimals, score legends dropped since the request already holds them.
# Needs the API key, so run it as
#   secretspec run --reason "answer pending wikibooks decisions" -- ./decide.sh
# Requests already answered are skipped, so an interrupted run resumes. Once
# a model's daily cap is reached its requests wait, the rest go on, and the
# run fails; run it again the next day.
set -euo pipefail

root=$(git rev-parse --show-toplevel)
pending=${PENDING:-$root/.cache/wikibooks/pending}
export decisions=$root/data/extractors/wikibooks/decisions
export compact='.answers | map_values(del(.legend)) | walk(if type == "number" then (. * 10000 | round) / 10000 else . end)'
: "${OPENROUTER_API_KEY:?run this under secretspec run}"
export capped
capped=$(mktemp -d)
trap 'rm -rf "$capped"' EXIT
mkdir -p "$decisions"

# Exit codes for xargs: 255 stops every worker; any other code fails only this request.
ask() {
	local request=$1 key model response status body attempt
	key=$(basename "$request" .json)
	[[ -f $decisions/$key.json ]] && return 0
	model=$(jq -r '.model | gsub("/"; "_")' "$request")
	[[ -f $capped/$model ]] && return 0
	for attempt in 1 2 3 4 5 6; do
		response=$(curl -sS --max-time 300 -w '\n%{http_code}' https://openrouter.ai/api/alpha/decisions \
			-H "Authorization: Bearer $OPENROUTER_API_KEY" -H 'Content-Type: application/json' \
			--data-binary @"$request") || { sleep $((2 ** attempt)); continue; }
		status=${response##*$'\n'}
		body=${response%$'\n'*}
		case $status in
		200)
			if jq -e '.answers | type == "object"' <<<"$body" >/dev/null; then
				jq -cS "$compact" <<<"$body" >"$decisions/$key.json.tmp"
				mv "$decisions/$key.json.tmp" "$decisions/$key.json"
				return 0
			fi
			echo "$key: no answers in the response: $body" >&2
			return 1
			;;
		429)
			if grep -qi 'per.day' <<<"$body"; then
				[[ -f $capped/$model ]] || echo "daily request cap reached: $body" >&2
				touch "$capped/$model"
				return 0
			fi
			sleep $((2 ** attempt))
			;;
		402)
			echo "out of credits: $body" >&2
			return 255
			;;
		5??) sleep $((2 ** attempt)) ;;
		*)
			echo "$key: HTTP $status: $body" >&2
			return 1
			;;
		esac
	done
	echo "$key: gave up after repeated failures" >&2
	return 1
}
export -f ask

find "$pending" -name '*.json' -print0 | xargs -0 -r -P "${PARALLEL:-4}" -n 1 bash -c 'ask "$1"' _
[[ -z $(ls -A "$capped") ]]
