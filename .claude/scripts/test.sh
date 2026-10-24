#!/usr/bin/env bash
# Runs the babel suite (or the tests matching a tasty pattern) and prints one
# block per failure: the test, where it failed, the expected-vs-actual diff, and
# the diagnostics the validator reported. .claude/scripts/test-focused.sh shows a
# failure in full, generated inputs included.
set -euo pipefail

root=$(git rev-parse --show-toplevel)
"$root/.claude/scripts/test-focused.sh" "$@" | awk '
	/FAIL/ { print; context = 2; next }
	context > 0 { print; context--; next }
	/│ (━━━ Failed|[-+] )/ || /┃ │ (error|warning)\[/ {
		sub(/^.*│ /, "    ")
		sub(/\/[^ ]*\/data\//, "data/")
		print
		next
	}
	/tests? (passed|failed) \(/ && !/[✓✗]/ { print }
'
