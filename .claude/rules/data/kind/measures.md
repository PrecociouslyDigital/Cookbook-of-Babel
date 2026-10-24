---
paths:
  - "data/layers/*/*/*/measures/**"
---
A measure is one sourced fact about one ingredient: its nutrition per 100 g,
or how many grams are in one of a count unit, e.g. a `gramsPer` fact for
`piece`. A file is named after the ingredient and what it measures, e.g.
`egg.nutrition.json` or `egg.grams_per_piece.json`, so each fact overlays,
and is reviewed, on its own.
Nutrition that follows from a recipe's inputs is a query, `babel query
nutrition`, and is never stored.
