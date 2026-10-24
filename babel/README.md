# babel

The Haskell package that defines the data model and validates the dataset.

- `src/Babel/Model.hs`: the types, smart constructors and
  [autodocodec](https://hackage.haskell.org/package/autodocodec) codecs. These
  are the source of truth. JSON Schema and the Java/C#/TS/Python bindings will
  be derived from the same codecs later.
- `src/Babel/Codec.hs`: slug IDs, plus `genericCodec`, which derives a record's
  codec from its fields (see below).
- `src/Babel/Load.hs`: walks the data root, decodes every file, and merges the
  overlay layers. It reports unknown fields and non-canonical formatting.
- `src/Babel/Check.hs`: whole-dataset rules (licences, references, is-a cycles,
  value ranges, versioning) plus warning-only soft checks.
- `src/Babel/Format.hs`: canonical JSON rendering. Key order is the codec's
  field order, so there is no separate list of keys to maintain.
- `src/Babel/Spoilage.hs`: finds spoilage recipes and works out which happens
  first under given conditions (Q10 scaling, water-activity gate).
- `app/Main.hs`: the `babel` CLI.
- `test/`: Hedgehog properties over generated datasets (see Tests below).

The package uses [relude](https://hackage.haskell.org/package/relude) as its
Prelude (through cabal `mixins`), and turns on `DuplicateRecordFields`,
`NoFieldSelectors` and `OverloadedRecordDot` everywhere, so fields are read as
`recipe.inputs`. One cost: plain record *update* of a field name shared by two
types in scope (`id`, `label`, `parents`, `outputs`, …) is ambiguous. Use a
`RecordWildCards` rebuild for those, e.g. `Recipe{outputs = o, ..}`.

Library dependencies come from nixpkgs through `devenv.nix`
(`ghcWithPackages`), so cabal never needs Hackage. When you add a dependency to
`babel.cabal`, add it there too.

## Codecs

Records get their codec from `genericCodec`: one JSON key per field, named
after the field, in declaration order. The field's type decides the rest:

- `Maybe a` is an optional key;
- lists, `Map`s and `Bool` are omitted when empty or `False`;
- `Role` is omitted when `main`;
- anything else is required.

So renaming a Haskell field renames the JSON key: a breaking model change, by
design. Hand-written codecs remain only where the JSON is not a plain record:

- `Select`, because `of` is a Haskell keyword;
- `Outcome` (an array, or `{"oneOf": …}`) and the `End` union;
- validated wrappers: IDs, `Intensity`, `Version`, `TempBands`;
- enums.

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
| `constants` | `registries/constants.json` out of range (its spoilage technique must also resolve) |
| `ambient-time` | No environment and a time-based end without a sensitivity |
| `rename-*` | `from` still exists, `to` never resolves, a loop, or `since` after the dataset version |
| `calories`, `ratio` (warnings) | kcal inconsistent with macros; implausible output/input mass ratio |

## Tests

`cabal test` runs Hedgehog properties (100 cases each by default; pass
`--test-options=--hedgehog-tests=1000` for more).

- `test/Gen.hs` generates datasets that are valid *by construction*: is-a
  parents come only from earlier IDs, references are sampled from IDs that
  exist, recipes are filed under their source's licence, and so on. The
  generator never re-implements a check.
- **Validity:** every generated dataset validates cleanly. Every value survives
  `render` and decode unchanged, and the rendering means the same JSON as the
  codec's encoding.
- **Rules:** each rule gets a targeted mutation of a random dataset (a fresh
  dangling ID, an edge back to an ancestor, bounds outside `0..n`, …), and
  validation must report *exactly* that rule. File-level mutations cover:
  - an unknown key injected at any object path;
  - compact formatting, which `--fix` must repair;
  - renamed files, stray files and bad semver;
  - decode failures.
- **Overlays and laws:**
  - reviewed beats extracted;
  - `findCycles` is sound, and complete for a back edge;
  - Q10 scaling and the water-activity gate in `timeAt`;
  - `bandFromCelsius` picks the band whose range holds the temperature.
- **Seed data:** a few example tests check the real data: it validates, and
  bread spoilage gives plausible answers.

Seeding the validator with deliberate bugs confirms the properties bite.
Loosening the select bound, silencing the unknown-field probe, or making
`--fix` a no-op each fails its property.
