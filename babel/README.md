# babel

The Haskell package that defines the data model and validates the dataset.

- `src/Babel/Model.hs`: the types, smart constructors and
  [autodocodec](https://hackage.haskell.org/package/autodocodec) codecs. These
  are the source of truth. JSON Schema and the Java/C#/TS/Python bindings will
  be derived from the same codecs later.
- `src/Babel/Load.hs`: walks the data root, decodes every file, and merges the
  overlay layers. It reports unknown fields and non-canonical formatting.
- `src/Babel/Check.hs`: whole-dataset rules (licences, references, is-a cycles,
  value ranges, versioning) plus warning-only soft checks.
- `src/Babel/Format.hs`: canonical JSON rendering.
- `src/Babel/Spoilage.hs`: finds spoilage recipes and works out which happens
  first under given conditions (Q10 scaling, water-activity gate).
- `app/Main.hs`: the `babel` CLI.
- `test/Main.hs`: the seed data must pass, and one mutation per rule must fail
  with exactly that rule.

Library dependencies come from nixpkgs through `devenv.nix`
(`ghcWithPackages`), so cabal never needs Hackage. When you add a dependency to
`babel.cabal`, add it there too.

## Unknown fields

autodocodec ignores keys it doesn't know. `Load.unknownFields` closes that gap
generically. It compares each file with the re-encoding of its decoded value.
For every key the re-encoding lacks, it replaces that key's value with `[null]`
and decodes again. If decoding still succeeds, the decoder never read the key,
so the key is reported as unknown. A known key that merely holds its default
value is reported as a formatting issue instead.

## Validator rules

| Rule tag | Meaning |
| --- | --- |
| `json`, `decode` | Not JSON, or does not match the model (bad slug, unknown enum value, empty outputs, invalid semver, intensity outside 0–3, …) |
| `unknown-field` | A key the model does not have |
| `format` | Not canonically formatted (`--fix` rewrites it) |
| `layout`, `filename` | Unexpected file or folder; file name is not `<id>.json` |
| `overlay-conflict`, `duplicate-key`, `duplicate-id` | Same key in two licence folders of one layer; duplicates |
| `licence-mismatch` | A source's licence, or a recipe's source licence, differs from its folder |
| `dangling-ref` | An ID that does not resolve |
| `dimension`, `amount` | Duration not in a time unit (or an amount in one); non-positive quantity |
| `isa-cycle` | Cycle in the ingredient or technique is-a DAG |
| `select-bounds` | Not `0 ≤ min ≤ max ≤ number of choices` |
| `weight`, `chance`, `all-chance` | Weight ≤ 0; chance outside (0, 1]; a branch whose outputs are all chance-gated |
| `water-activity`, `density`, `q10`, `temp-range` | Value out of range |
| `ambient-time` | No environment and a time-based end without a sensitivity |
| `rename-*` | `from` still exists, `to` never resolves, a loop, or `since` after the dataset version |
| `calories`, `ratio` (warnings) | kcal inconsistent with macros; implausible output/input mass ratio |
