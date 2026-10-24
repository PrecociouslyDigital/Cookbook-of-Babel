{ pkgs, lib, config, ... }:

let
  # One pinned GHC. nixpkgs' default package set uses this version, so GHC,
  # HLS and the Haskell tools below all come prebuilt from cache.nixos.org.
  hsPkgs = pkgs.haskell.packages.ghc910;
  # Library dependencies come from nixpkgs, so cabal never needs Hackage.
  # Keep in sync with babel/babel.cabal.
  ghc = hsPkgs.ghcWithPackages (p: [
    p.aeson
    p.aeson-pretty
    p.autodocodec
    p.containers
    p.directory
    p.filepath
    p.optparse-applicative
    p.scientific
    p.tasty
    p.tasty-hunit
    p.temporary
    p.text
    p.vector
  ]);
in
{
  packages = [
    pkgs.jq
    hsPkgs.fourmolu
    hsPkgs.hlint
    hsPkgs.cabal-fmt
  ];

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

  git-hooks.hooks = {
    babel-validate = {
      enable = true;
      name = "babel validate";
      entry = "${lib.getExe config.scripts.babel.scriptPackage} validate data";
      files = "^(data/|babel/)";
      pass_filenames = false;
    };
    fourmolu.enable = true;
    hlint.enable = true;
    cabal-fmt.enable = true;
  };
}
