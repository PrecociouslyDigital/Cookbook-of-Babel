# Cookbook of Babel

- Read `PLAN.md` for the design. The Haskell types in `babel/src/Babel/Model.hs`
  are the source of truth for the data model; the JSON in `data/` is the
  canonical data.
- Work inside the devenv shell: `devenv shell -- <cmd>`, e.g.
  `devenv shell -- bash -c 'cd babel && cabal test'` and
  `devenv shell -- babel validate`. Claude Code's Bash calls already get the
  devenv environment from `.claude/hooks/devenv-env.sh`, so there the prefix
  is optional.
- Haskell dependencies come from nixpkgs (`devenv.nix`, `ghcWithPackages`);
  keep that list in sync with `babel/babel.cabal`. Don't `cabal update`.
- The package uses relude as its Prelude and enables `DuplicateRecordFields`,
  `NoFieldSelectors` and `OverloadedRecordDot` globally (`babel.cabal`; mirror
  any change in `.hlint.yaml`). Read fields with `x.field`. To update a field
  name shared by two types, rebuild with `RecordWildCards`.
- New records get `deriving stock (Generic)` and `codec = genericCodec`; the
  field names and their order are the JSON keys and the canonical key order.
- Judgement-call numbers go in `data/registries/constants.json`, not in code.
- Imports are explicit or qualified (hlint enforces it in `.hlint.yaml`). Inside
  the library, import the specific `Babel.Model.*` submodule; elsewhere import
  from `Babel.Model`. A new model module goes in `babel.cabal` and in
  `Babel.Model`'s re-exports.
- Format with fourmolu (defaults), lint with hlint, `cabal-fmt` the cabal file.
- After editing data, run `babel validate --fix` to canonicalize, then
  `babel validate`.
- Every new validator rule gets a mutation property in `babel/test/Main.hs`
  that asserts exactly its rule tag on a mutated generated dataset. Keep
  `test/Gen.hs` valid by construction; never filter on the validator's output.
- `devenv.yaml` uses `git+https` inputs on purpose: sandboxed sessions can't
  fetch GitHub tarballs.
