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
- `decisions/` freezes mammoth-1's answers, keyed by the SHA-256 of each request.
  Changing a question's wording, a registry it lists, or the page text it
  quotes makes new requests, and the free model answers only about 1,000 a day,
  so batch such changes.
