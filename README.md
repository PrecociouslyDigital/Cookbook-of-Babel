# Cookbook of Babel

A normalized, game-oriented recipe dataset. Each recipe is one step: inputs, a
technique, conditions, and outputs. Dishes are chains of steps, and so are
spoilage, melting and setting.

| Area | What it is | Licence |
| --- | --- | --- |
| [`data/`](data/README.md) | The dataset: canonical, human-readable JSON | Per layer folder (mixed CC) |
| [`babel/`](babel/README.md) | Haskell types (the source of truth for the model) and the `babel` validator | Not chosen yet |
| [`mods/`](mods/README.md) | Game mods built from the dataset (placeholder) | Not chosen yet |

The plan this milestone implements is in [`PLAN.md`](PLAN.md).

## Getting started

The toolchain is pinned with [devenv](https://devenv.sh) (Nix):

```sh
devenv shell                 # or `direnv allow` with the provided .envrc
babel validate               # checks data/; non-zero exit on any error
babel validate --fix         # also rewrites non-canonically formatted files
babel query spoilage bread --temp 20 --aw 0.95
(cd babel && cabal test)
```

Entering the shell installs a pre-commit hook that runs `babel validate`,
fourmolu, hlint and cabal-fmt.

## Invalid data can never be merged

The local hook can be skipped with `git commit --no-verify`, so **CI is the real
gate**: `.github/workflows/ci.yml` builds, tests, validates and runs every hook
on all files with the same devenv toolchain. To make it binding, enable branch
protection on the default branch with the `validate` check marked as required.
