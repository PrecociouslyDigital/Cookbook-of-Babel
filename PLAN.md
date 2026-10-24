# Cookbook of Babel: milestone 1, the Haskell validator for the data model

## Context
We want a normalized, game-oriented recipe dataset, starting with Wikibooks (3,816 recipe pages in
the Oct 2026 dump). Planned roadmap: **validator (this pass) → Wikibooks extraction → codegen →
expand**.

Settled in planning:
- **JSON files in git are the canonical data**, kept human-readable: slug IDs only, no UUIDs.
- **The Haskell types are the source of truth for the model.** We hand-write newtypes, records and
  smart constructors. JSON Schema and the Java/C#/TS/Python bindings come later and are *derived from
  these types*.
- **Goal: invalid data can never be merged.** Pre-commit catches it locally; CI is the real gate.
- **Numbers only need the right order of magnitude**, not precision.

Before this plan, the repo was empty. Drafts from the planning session used an older model and were not
kept; this file is the whole record of the design.

## Data model (as Haskell types in `Babel.Model`)
- **Newtypes**, each with a smart constructor that checks the slug pattern
  `^[a-z0-9]+(_[a-z0-9]+)*$` (dotted for `RecipeId`): `IngredientId`, `TechniqueId`, `RecipeId`,
  `SourceId`, `UnitId`, `MediumId`, `FormId`, `TextureAttrId`, `TasteAttrId`, `ServingTagId`. Each
  is a distinct type; none are aliases.
- **Ingredient**:
  - Identity: `id`, `label`, `aliases`.
  - `parents :: [IsA IngredientId]`, where `IsA = {target, axis}`. This is the is-a DAG, e.g.
    russet → potato → tuber.
  - Descriptive fields reference **registries**, not hardcoded enums:
    - `form :: FormId`, seeded from LanguaL Facet E ("physical state, shape or form").
    - `texture :: Map TextureAttrId Intensity` (0–3), seeded from Szczesniak / ISO texture
      attributes.
    - `taste :: Map TasteAttrId Intensity`, seeded with the basic tastes plus pungency. This is
      provisional.
    - `servingTags :: [ServingTagId]`.
    - Don't use IDDSI: its licence forbids derivatives. Before importing LanguaL terms, check
      LanguaL's licence.
  - Optional: `densityGPerMl`, `waterActivity` (0–1), `nutrition {per100g…, source}`.
  - `named :: Bool`, `issues`.
  - **Computed, never stored:** whether something is an intermediate, and its spoilage paths.
  - **There is no spoilage field and no state-change field.** Spoilage, melting and setting are all
    recipes. `form` describes the ingredient's own state, so melted chocolate is a separate
    ingredient.
  - `spoiled_food` is an ordinary ingredient; there is no terminus flag. Later nutrition derivation
    only fills in an ingredient's values when it has a single unambiguous producing recipe.
- **Technique**: `id`, `label`, `parents :: [TechniqueId]` (is-a DAG, e.g. julienne → chop → cut),
  plus a typical temperature range. The `age → spoil → {mold, stale, rancid, sour}` and
  `age → ferment` subtree exists so the spoilage helpers can find spoilage recipes. No validation
  rule depends on it.
- **Recipe (one step)**:
  - `id :: RecipeId`, `source :: SourceId`, `technique`.
  - `sourceRef? {source, step}`: a stable, readable key for extracted steps, used for overlay
    matching.
  - `inputs :: Select`, where `Select = {min, max, of :: [Either Select Leaf]}` and
    `Leaf = {ingredient, amount}`. This covers all / anyOf / oneOf / optional.
  - `outputs :: Outcome`, where
    `Outcome = Outputs (NonEmpty Output) | OneOf (NonEmpty (Weight, Outcome))` and
    `Output = {ingredient, amount, role: Main|Byproduct|Part, chance?}`.
    - `chance` rolls independently per output (like Create's milling).
    - `OneOf` picks exactly one weighted branch (like Create's sequenced assembly).
  - `environment? {temperatureC, medium :: Either MediumId H}`. If absent, the step happens in
    ambient conditions supplied by the game.
  - `end :: End = ByCoreTemp | ByTime | ByBoth`, plus an optional `stateNote` (human/LLM-readable,
    e.g. "until golden").
    - `ByTime {duration, sensitivity?}`, where
      `Sensitivity = {refTempC, q10, minWaterActivity?, maxWaterActivity?}` and duration is
      measured at the reference conditions. This one shape covers spoilage, fermentation and later
      the time-to-temperature helper.
- **Source**: `{id, name, url, licence :: Licence}`, where `Licence` is an SPDX-mapped enum.
- **Registries**:
  - `units` (dimension, toBase)
  - `media` (alias → h in W/m²K, order-of-magnitude values)
  - `forms`, `texture`, `taste`, `serving`
- **`TempBand`**: an enum with a total function `bandFromCelsius`, defined once.
- **`Issue`**: `{field, kind, note}`, an optional annotation.

## Versioning and renames
- `dataset.json` holds `{version}`, a semver string.
- **Release semantics:**
  - **Major**: renames, removals, or breaking model changes.
  - **Minor**: additions.
  - **Patch**: value corrections.
- `renames.json` is a list of `{since, kind, from, to}` entries. Downstream code composes these to
  migrate IDs between versions.
- Data always uses current slugs. Renaming means editing the data *and* appending a rename entry.

## Layout (monorepo)
Three top-level areas, each with its own README. Licences can differ per area: the data is mixed
CC (licensed per layer folder), and the code licence is the user's choice (still open).
```
data/                      # canonical JSON: the dataset itself
  dataset.json, renames.json
  layers/{extracted,reviewed}/<licence>/{sources,ingredients,techniques,recipes}/<id>.json
  registries/{units,media,forms,texture,taste,serving}.json
babel/                     # Haskell codebase (cabal.project + package); later also extraction glue and codegen
mods/                      # future game mods (Minecraft/Create first); README placeholder only in this pass
```
`babel validate` takes the data root as an argument (default `data/`), so tests point it at fixture
trees instead.
```
babel/                     # cabal package
  src/Babel/Model.hs       # types + smart constructors + JSON decoding (unknown fields rejected)
  src/Babel/Load.hs        # walk layers, apply overlays, decode, collect errors with file paths
  src/Babel/Check.hs       # reference + graph + versioning + soft checks
  src/Babel/Format.hs      # canonical JSON rendering (2-space indent, trailing newline)
  src/Babel/Spoilage.hs    # spoilagePaths, timeAt (Q10 + water-activity gate),
                           #   firstSpoilage :: Conditions -> IngredientId -> Maybe (Recipe, Days)
  app/Main.hs              # babel validate [--fix] | babel query spoilage <ingredient> --temp C --aw X
  test/                    # tasty: good fixtures pass; one mutation fixture per rule
# repo root: devenv.nix, devenv.yaml, devenv.lock, .envrc, .github/workflows/ci.yml, README.md, CLAUDE.md
```
**JSON library:** the first implementation step is to choose between plain aeson and
`autodocodec`. Check `autodocodec`'s version and GHC compatibility on Hackage; it would make the
later JSON Schema output nearly free.

## Validator rules
1. **Decode** every file through `Babel.Model`. Report *all* errors with file paths.
2. **Filename** equals `<id>.json`, and the entity folder matches the type.
3. **Overlays**:
   - A reviewed recipe overrides an extracted one with the same `sourceRef`, or with the same `id`
     when there's no `sourceRef`.
   - Other entities override by `id`.
   - The same key in two licence folders of one layer is an error.
   - A recipe's `source` licence must match its folder.
   - Report the build's effective (strictest) licence.
4. **References**: every ingredient, technique, source, unit, medium and registry ID resolves.
5. **Graph and values**:
   - The is-a DAGs are acyclic.
   - Every `Select` satisfies `0 ≤ min ≤ max ≤ length of`.
   - Weights are > 0. `chance` lies in (0, 1]. Every `Outcome` branch can yield at least one output
     that isn't chance-gated.
   - Water-activity bounds satisfy `0 ≤ min ≤ max ≤ 1`.
   - A recipe with no `environment` and a time-based end must carry `sensitivity`. Otherwise the
     time would mean nothing under game-supplied conditions.
   - Recipe cycles across the whole graph (melt ↔ set) are allowed.
6. **Versioning**:
   - `version` is valid semver.
   - Each rename's `from` no longer exists, and its `to` exists (or was itself renamed later).
   - No rename loops back to an earlier slug.
   - Every rename's `since` ≤ `version`.
   - Not in this pass: enforcing the version bump size against the last release tag (follow-up).
7. **Soft checks** are printed as warnings and never fail the build: implausible ratios, calories
   inconsistent with macros.
8. **Formatting** must be canonical; `--fix` rewrites files.

## Seed data
Each item is a chain of single-step recipes, all in `reviewed/`:
- **Stir-fried shredded potatoes**: `LicenseRef-original`.
- **Wikibooks North American pancakes**: CC-BY-SA-4.0, with `sourceRef` on each step.
- **Bread spoilage**, `LicenseRef-original`:
  - bread → `spoiled_food` (mold)
  - bread → `stale_bread` (staling)
  - stale bread → croutons
  - Both spoilage recipes have no environment and use `ByTime` + sensitivity.
- **Fresh cheese**, `LicenseRef-original`:
  - milk + rennet → curds + whey (byproduct)
  - curds → `OneOf` {aged cheese 9, `spoiled_food` 1}

## Dev environment (devenv on Nix)
- **Files:**
  - `devenv.nix` and `devenv.yaml`
  - `devenv.lock`, which pins nixpkgs so GHC, cabal and HLS versions are reproducible
  - `.envrc` (`use devenv`) for direnv users
- **Shell contents:**
  - Haskell enabled with **one pinned GHC version**, cabal-install, and a matching
    haskell-language-server
  - fourmolu and hlint
  - `jq` for poking at JSON
- **The pre-commit hook is declared in devenv** (its git-hooks integration) rather than in a
  hand-written `.pre-commit-config.yaml`. Hooks: `babel validate`, fourmolu, hlint, and
  `cabal-fmt` if wanted.
- **Implementation checks** (from memory, verify first):
  - Option names: `languages.haskell.*`, `git-hooks.hooks.*`.
  - The chosen GHC must have an HLS build in nixpkgs.
- **This container:** Nix is present but devenv isn't. Get devenv through Nix (`nix profile install`
  or `nix run`). The first shell build may be slow.

## Enforcement
- **Pre-commit hook** (installed by entering the devenv shell) runs `babel validate`, formatting and
  lint.
- **CI** (GitHub Actions) installs Nix and devenv, uses a cache (Cachix or the GitHub Actions Nix
  cache), and runs `devenv shell -- cabal test` and `devenv shell -- babel validate`. This is the
  same toolchain as local development.
- **The user must enable branch protection** with the CI check marked required. The local hook can
  be bypassed with `--no-verify`.

## Verification
- `devenv shell -- ghc --version`, `cabal --version` and `haskell-language-server --version` all
  succeed, and HLS reports support for the pinned GHC.
- `cabal build && cabal test` (inside the devenv shell): good fixtures pass, and each mutation fixture fails with its expected
  error. Mutations:
  - **Decoding**: bad ID pattern, unknown enum value, extra field, empty outputs.
  - **References and layout**: dangling reference, unknown texture attribute, the same key in two
    licence folders, source licence ≠ folder, non-canonical formatting.
  - **Graph and values**: `min > max`, is-a cycle, zero weight, `chance` > 1, an all-chance branch,
    an ambient time end without sensitivity.
  - **Versioning**: a rename whose `from` still exists, a rename loop, invalid semver.
- Spoilage unit tests with bread, using values from the seed data and checked to the right order of
  magnitude: at 20 °C mold wins; at low water activity staling wins.
- `babel validate` passes on the seed data and exits non-zero on a broken copy. Run the pre-commit
  hook once locally to confirm it blocks a bad commit.
- Commit and push to branch `ccr-f8093941-ezepl4`; no PR unless the user asks.

## Out of scope
Codegen and JSON Schema output (milestone 3); Wikibooks extraction (milestone 2); nutrition and
FoodKeeper imports; the time-to-temperature helper; enforcing semver bump size against git tags;
the Minecraft exporter.
