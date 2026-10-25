---
paths:
  - "mods/minecraft/**"
---

# The Minecraft mod

## Toolchain

- Java 17 comes from devenv (`languages.java` in `devenv.nix`), Gradle from the
  pinned wrapper. Run Gradle from `mods/minecraft` as `./gradlew …`.
- Tasks that read the dataset run `babel export`, so `babel` must be on the
  PATH; devenv provides it. A plain `./gradlew build` doesn't need it, because
  everything generated from the dataset is committed.
- Versions of every upstream live in `gradle/libs.versions.toml`. After
  changing a dependency, run `scripts/pin-dependencies.sh` to refresh
  `gradle/verification-metadata.xml` and the plugin lockfiles. Don't lock the
  project's own configurations: locking them stops ModDevGradle from
  remapping mod dependencies, which then crash at runtime.

## Generated files

- `src/generated/` and `coverage.md` are generated: the Java enums mirroring the
  dataset (`generateDatasetSources`) and everything datagen writes. Never edit
  them by hand.
- Regenerate them after changing `data/`, babel's export, or datagen code with
  `./gradlew runData`. It exports the dataset and regenerates the enums first.
