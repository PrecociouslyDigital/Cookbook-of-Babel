---
paths:
  - "data/layers/extracted/**"
---
Extractors under `data/extractors/` generate this layer and recreate their
folders from scratch on every run, so a hand edit here is lost on the next run.
To correct an extracted entity, put the fix in `reviewed/`, which overlays it,
or change the extractor.
Spoilage recipes from FoodKeeper (`CC0-1.0/foodkeeper/`) are keyed by their ID,
`<ingredient>.<technique>`, so a reviewed recipe with the same ID replaces one.
