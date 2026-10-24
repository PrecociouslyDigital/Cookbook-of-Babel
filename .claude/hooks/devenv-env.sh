#!/usr/bin/env bash
# Exports the devenv shell's environment into $CLAUDE_ENV_FILE, which Claude
# Code runs before every Bash command, so tool calls behave as if they ran in
# `devenv shell`. Runs on SessionStart and again when devenv.{nix,yaml,lock}
# change. Later lines in the env file override earlier ones.
set -euo pipefail

if [ -z "${CLAUDE_ENV_FILE:-}" ]; then
  exit 0
fi
if ! command -v devenv >/dev/null 2>&1; then
  echo "devenv not found; Bash commands run outside the devenv shell" >&2
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel)}"

outer=$(mktemp)
trap 'rm -f "$outer"' EXIT
env -0 >"$outer"

# Print an export for every variable the devenv shell adds or changes. %q
# quoting keeps multi-line values intact. devenv's task progress goes to
# stderr, so stdout holds only the exports.
# shellcheck disable=SC2016
devenv shell -- bash -c '
  declare -A before
  while IFS= read -r -d "" kv; do before[${kv%%=*}]=${kv#*=}; done <"$1"
  for name in $(compgen -e); do
    case $name in _ | PWD | OLDPWD | SHLVL | DEVENV_CMDLINE) continue ;; esac
    if [ "${before[$name]-__unset__}" != "${!name}" ]; then
      printf "export %s=%q\n" "$name" "${!name}"
    fi
  done
' bash "$outer" >>"$CLAUDE_ENV_FILE"
