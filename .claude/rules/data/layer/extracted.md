---
paths:
  - "data/layers/extracted/**"
---
Extractors under `data/extractors/` generate this layer and recreate their
folders from scratch on every run, so a hand edit here is lost on the next run.
To correct an extracted entity, put the fix in `reviewed/`, which overlays it,
or change the extractor.
