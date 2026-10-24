#!/usr/bin/env bash
# Runs the babel tests matching a tasty pattern (all of them when omitted) and
# prints only the failures, in full. Further arguments go to tasty, e.g.
#   .claude/scripts/test-focused.sh 'origin folder' --hedgehog-tests=1000
set -euo pipefail

root=$(git rev-parse --show-toplevel)
pattern=${1-}
(($#)) && shift

tasty=(--hide-successes --color=never)
[[ -n $pattern ]] && tasty+=(--pattern "$pattern")
cabal --project-dir="$root/babel" -v0 test babel:test:babel-test --test-show-details=direct "${tasty[@]/#/--test-option=}" "${@/#/--test-option=}"
