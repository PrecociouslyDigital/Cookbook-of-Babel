---
paths:
  - "babel/**"
  - "devenv.nix"
  - "devenv.yaml"
  - ".hlint.yaml"
---

# babel

## Toolchain

- Bash calls already run in the devenv environment (`.claude/hooks/devenv-env.sh`).
  Elsewhere, prefix commands with `devenv shell --`.
- Build and test with `cd babel && cabal build all && cabal test`. For more
  Hedgehog cases, pass `--test-options=--hedgehog-tests=1000`.
- Haskell dependencies come from nixpkgs (`ghcWithPackages` in `devenv.nix`), so
  never run `cabal update`. A dependency added to `babel.cabal` must also be
  added to `devenv.nix`.
- `devenv.yaml` uses `git+https` inputs on purpose: sandboxed sessions can't
  fetch GitHub tarballs.
- Format with fourmolu (defaults), lint with hlint, and format `babel.cabal`
  with cabal-fmt. The pre-commit hook and CI run all three.

## Language

- relude is the Prelude. `DuplicateRecordFields`, `NoFieldSelectors` and
  `OverloadedRecordDot` are on everywhere, so read fields as `recipe.inputs`.
  hlint can't read `default-extensions`, so a change to them in `babel.cabal`
  must be copied to `.hlint.yaml`.
- Record update syntax is ambiguous when another type in scope has a field of
  the same name (`id`, `label`, `parents`, `outputs`, …). Rebuild the record
  with `RecordWildCards` instead: `Recipe{outputs = o, ..}`.
- Every import has an explicit list or is qualified; hlint enforces it. Library
  modules import the specific `Babel.Model.*` submodule they need, so a model
  change only recompiles its users. The CLI and tests import `Babel.Model`.
- Numbers that are judgement calls go in `data/registries/constants.json` and
  are decoded through `Babel.Model.Constants`. Numbers that are part of the
  schema, like the 0–3 intensity scale, stay in the types.

## Tests

- Every validator rule has a mutation property in `test/Main.hs`: mutate a
  generated dataset so that it breaks just that rule, and assert the report
  contains exactly that rule's tag.
- `test/Gen.hs` generates datasets that are valid by construction. It never
  filters on the validator's output and never re-implements a check.
- A new property must fail when the code it covers is broken. Confirm that by
  breaking the code temporarily and running the property.
