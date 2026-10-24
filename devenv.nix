{ pkgs, lib, config, ... }:

let
  # One pinned GHC. nixpkgs' default package set uses this version, so GHC,
  # HLS and the Haskell tools below all come prebuilt from cache.nixos.org.
  hsPkgs = pkgs.haskell.packages.ghc910;
  # Library dependencies come from nixpkgs, so cabal never needs Hackage.
  # Keep in sync with babel/babel.cabal and every package in babel/cabal.project.
  ghc = hsPkgs.ghcWithPackages (p: [
    p.aeson
    p.autodocodec
    p.conduit
    p.containers
    p.cryptohash-sha256
    p.directory
    p.filepath
    p.hedgehog
    p.megaparsec
    p.optparse-applicative
    p.relude
    p.scientific
    p.tasty
    p.tasty-hedgehog
    p.tasty-hunit
    p.temporary
    p.text
    p.vector
    p.xml-conduit
    p.xml-types
  ]);
in
{
  packages = [
    pkgs.jq
    pkgs.secretspec
    hsPkgs.fourmolu
    hsPkgs.hlint
    hsPkgs.cabal-fmt
  ];

  # GHC tools read and print UTF-8 source and data; don't depend on the host locale.
  env.LANG = "C.UTF-8";

  # Secrets are declared in secretspec.toml; values live in the OS keychain.
  # Nothing checks for them on shell entry, so non-interactive shells (direnv,
  # Claude's SessionStart hook, CI) work without them. `secretspec run` refuses
  # to start a command while a required one is missing.
  env.SECRETSPEC_PROVIDER = "keyring";

  languages.haskell = {
    enable = true;
    package = ghc;
    stack.enable = false;
  };

  # Builds and runs whatever is checked out right now. It carries its own
  # toolchain, so the git hook works from outside the shell too.
  scripts.babel = {
    packages = [ ghc pkgs.cabal-install pkgs.git ];
    exec = ''
      root="''${DEVENV_ROOT:-$(git rev-parse --show-toplevel)}"
      cabal --project-dir="$root/babel" -v0 run babel:exe:babel -- "$@"
    '';
  };

  # Reads the folder layout from `babel layout`, so it carries babel too.
  scripts.check-data-rules = {
    packages = [ config.scripts.babel.scriptPackage pkgs.bash pkgs.git ];
    exec = ''
      root="''${DEVENV_ROOT:-$(git rev-parse --show-toplevel)}"
      exec bash "$root/scripts/check-data-rules.sh" "$@"
    '';
  };

  git-hooks.hooks = {
    babel-validate = {
      enable = true;
      name = "babel validate";
      entry = "${lib.getExe config.scripts.babel.scriptPackage} validate data";
      files = "^(data/|babel/)";
      pass_filenames = false;
    };
    data-rules = {
      enable = true;
      name = "data rules";
      entry = lib.getExe config.scripts.check-data-rules.scriptPackage;
      files = "^(data/layers/|\\.claude/rules/data/|babel/)";
      pass_filenames = false;
    };
    fourmolu.enable = true;
    hlint.enable = true;
    cabal-fmt.enable = true;
  };
}
