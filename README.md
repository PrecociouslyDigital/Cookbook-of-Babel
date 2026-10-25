# Cookbook of Babel
This project is an effort to have a curated dataset of recipes, structured and normalized in a way that is useful for Game Development. It's a monorepo consisting of:
- babel, a haskell program that validates the normalized data
- data, a set of scripts/extractors and their results
- mods, "official" mods for games: so far a Minecraft (Forge 1.20.1) mod, in
  `mods/minecraft`, that cooks the dataset's recipes in Create, Farmer's
  Delight and vanilla machines


## Getting started

The toolchain is pinned with [devenv](https://devenv.sh) (Nix):

```sh
devenv shell                 # or `direnv allow` with the provided .envrc
babel validate               # checks data/; non-zero exit on any error
babel validate --fix         # also rewrites non-canonically formatted files
babel query spoilage bread --temp 20 --aw 0.95
babel query nutrition pancake_dry_mix   # stated, or inferred from its recipe
babel export --out world.json           # the merged dataset as one JSON document
(cd babel && cabal test)
```

Entering the shell installs a pre-commit hook that runs `babel validate`,
fourmolu, hlint and cabal-fmt.

## The Minecraft mod

```sh
cd mods/minecraft
./gradlew build              # the jar, from what's committed
./gradlew runData            # re-export the dataset and regenerate the datapack
./gradlew runClient          # play it, with Create, Farmer's Delight and JEI
./gradlew runGameTestServer  # check the generated recipes load and run
```

`mods/minecraft/coverage.md` lists what became of every dataset step.

## Secrets

Secrets are declared in `secretspec.toml` and kept in your OS keychain. Store
one with `secretspec set NAME`. They are never exported into the shell; run
whatever needs them under `secretspec run`, which refuses to start while a
required one is missing:

```sh
secretspec run -- <command>
```
