---
paths:
  - "data/extractors/**"
---

# Extractors

- An extractor turns a source into the `extracted` layer and owns its folder
  there. It is a pure function of its inputs, so committed results can be
  regenerated and diffed.
- The Wikibooks extractor is a Haskell package in `babel/cabal.project`. Run it
  end to end with `secretspec run --reason "…" -- data/extractors/wikibooks/run.sh`,
  and its tests with `cd babel && cabal test babel-wikibooks`.
- The OpenFoodFacts extractor is one too. Run it end to end with
  `data/extractors/openfoodfacts/run.sh` (no secrets; the first fetch downloads
  about 8 GB), and its tests with `cd babel && cabal test babel-openfoodfacts`.
  Correct a wrong or missing category in its `matches.json`, never in the
  extracted layer.
- `decisions/` freezes the decision models' answers, keyed by the SHA-256 of
  each request. Changing a question's wording, a registry it lists, or the page
  text it quotes makes new requests. Questions about a page go to Jev, which is
  paid but cheap; questions about an ingredient need knowledge of the world, so
  they go to mammoth-1, which answers only about 1,000 a day for free; batch
  changes to ingredient questions.
- An extractor that asks the decision models builds on `babel-decisions`
  (`data/extractors/decisions/`): typed questions, request keys, dependent
  decisions, and `decide.sh`, which answers its pending folder into its own
  `decisions/`. Test it with `cd babel && cabal test babel-decisions`.
