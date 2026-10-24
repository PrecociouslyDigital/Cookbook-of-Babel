# Cookbook of Babel

- Read `PLAN.md` for the design. The Haskell types in `babel/src/Babel/Model.hs`
  are the source of truth for the data model; the JSON in `data/` is the
  canonical data.
- Work inside the devenv shell: `devenv shell -- <cmd>`, e.g.
  `devenv shell -- bash -c 'cd babel && cabal test'` and
  `devenv shell -- babel validate`.
- Haskell dependencies come from nixpkgs (`devenv.nix`, `ghcWithPackages`);
  keep that list in sync with `babel/babel.cabal`. Don't `cabal update`.
- Format with fourmolu (defaults), lint with hlint, `cabal-fmt` the cabal file.
- After editing data, run `babel validate --fix` to canonicalize, then
  `babel validate`.
- Every new validator rule gets a mutation test in `babel/test/Main.hs` that
  asserts exactly its rule tag.
- `devenv.yaml` uses `git+https` inputs on purpose: sandboxed sessions can't
  fetch GitHub tarballs.
