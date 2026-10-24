# data

The canonical dataset. Every file is validated by `babel validate`, and must be
byte-identical to its canonical rendering (2-space indent, fixed key order,
trailing newline). Run `babel validate --fix` to get there.

```
dataset.json        {"version": "MAJOR.MINOR.PATCH"}
renames.json        [{"since", "kind", "from", "to"}]; append-only
registries/         units, media, forms, texture, taste, serving, constants
layers/{extracted,reviewed}/<licence>/{sources,ingredients,techniques,recipes}/<id>.json
```

## Layers and licences

- `extracted/` will hold machine-extracted data (milestone 2). `reviewed/` holds
  hand-written or hand-checked data, and overrides `extracted/`:
  - a recipe overrides one with the same `sourceRef`, or with the same `id` when
    it has no `sourceRef`;
  - everything else overrides by `id`.
- The `<licence>` folder is an SPDX ID and is the licence of everything inside
  it. A key may sit in only one licence folder per layer. A recipe's source must
  carry the licence of the recipe's folder.
- `LicenseRef-original` is content written for this project; its terms are up to
  the project owner (not chosen yet). The registries are original content too.
- `babel validate` prints the build's effective (strictest) licence. The
  Wikibooks pancakes make the current build CC-BY-SA-4.0.

## Constants

`registries/constants.json` holds the numbers that are judgement calls rather
than schema. Changing one is a reviewed data change, not a code change:

- `tempBands`: named temperature bands. Bounds must strictly increase, and the
  last band is open-ended, so every temperature has exactly one band.
- `spoilageTechnique`: recipes whose technique is this one or below it count as
  spoilage.
- `kcalPerGram` and `calorieTolerance`: the calories soft check.
- `massRatio`: the plausible output/input mass ratio of one step; outside it,
  the soft check warns.

The 0–3 intensity scale and the slug pattern are schema, so they stay in the
Haskell types.

## Versioning

- **Major**: renames, removals, or breaking model changes.
- **Minor**: additions.
- **Patch**: value corrections.

Data always uses current slugs. To rename, edit the data *and* append a
`renames.json` entry, so downstream code can migrate old IDs.

## Seed data

| Chain | Licence | Shows |
| --- | --- | --- |
| Stir-fried shredded potatoes | original | is-a (russet → potato → tuber), byproducts, an optional input |
| North American pancakes ([Wikibooks](https://en.wikibooks.org/w/index.php?title=Cookbook:Pancakes_(North_American)&oldid=4671208)) | CC-BY-SA-4.0 | `sourceRef` per step, nested optional selects, `part` outputs |
| Bread spoilage and croutons | original | spoilage as recipes with ambient `ByTime` + sensitivity |
| Fresh cheese | original | byproduct whey; a weighted `oneOf` (aged cheese 9 : spoiled 1) |

The numbers only need to be the right order of magnitude. Guesses carry an
`issues` entry of kind `estimate`. The heat-transfer coefficients in
`registries/media.json` are order-of-magnitude judgement values. Two points on
vocabularies:

- The forms vocabulary is our own wording. Importing LanguaL Facet E terms is
  still blocked on checking LanguaL's licence.
- IDDSI is deliberately not used, because its licence forbids derivatives.
