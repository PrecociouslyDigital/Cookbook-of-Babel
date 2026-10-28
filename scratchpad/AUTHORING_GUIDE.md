# Authoring guide: production chains for the reviewed layer

You are authoring **production chains**: reviewed-layer recipe + ingredient files that let
the game actually make dataset ingredients. Write files, sanity-check JSON, commit. Do NOT
run gradle, cabal, or babel validate (the coordinator does that).

## Inputs you get

- `scratchpad/audit/section2_chains.tsv` — chain sketches: `leaf<TAB>chain`. The chain is a
  sketch; you may improve the process (fudge freely, overcombine steps). `new:X` means
  ingredient X must exist; create it only if you OWN it (rule below).
- `scratchpad/audit/section3_supersede.tsv` / `section8_shortcuts.tsv` — for supersedure
  batches only.
- Committed examples to copy the shape from:
  `data/layers/reviewed/LicenseRef-original/original/recipes/soy_sauce.*.json`,
  `bread.bake.json`, `dough.knead.json`, and
  `.../ingredients/{moromi,wheat_flour,croutons}.json`.

## Where files go

- Recipes: `data/layers/reviewed/LicenseRef-original/original/recipes/<leaf>.<slug>.json`
  (one step per file; `<slug>` = short verb phrase, e.g. `butter.churn`, `soy_sauce.press`).
- New ingredients: `data/layers/reviewed/LicenseRef-original/original/ingredients/<id>.json`.
- Every recipe has `"source": "cookbook_of_babel"`.
- If you use non-mass units (piece, clove, ...), you must also add
  `measures/<ingredient>.grams_per_piece.json` — prefer just `g` and `ml` instead.

## Recipe JSON (one step)

```json
{
  "id": "soy_sauce.press",
  "source": "cookbook_of_babel",
  "technique": "press",
  "inputs": {"of": [{"ingredient": "aged_moromi", "amount": {"qty": 6000, "unit": "g"}}]},
  "outputs": [
    {"ingredient": "soy_sauce", "amount": {"qty": 3500, "unit": "ml"}},
    {"ingredient": "soy_cake", "amount": {"qty": 2000, "unit": "g"}, "role": "byproduct"}
  ],
  "end": {"by": "time", "duration": {"qty": 1, "unit": "d"},
          "sensitivity": {"refTempC": 20, "q10": 1}},
  "stateNote": "the liquid pressed out through cloth",
  "issues": [{"field": "outputs", "kind": "estimate", "note": "pressing leaves a third as cake"}]
}
```

- `environment` (only for heated steps): `{"temperatureC": N, "medium": "..."}`.
- `end` forms: `{"by":"time","duration":{...},"sensitivity":{...}}` for timed steps
  (include a sensitivity for anything biological or held: refTempC ~ 20-25, q10 2-3);
  `{"by":"coreTemp","coreTempC":N}` for "cook until it reaches temperature";
  `{"by":"both","coreTempC":N,"duration":{...}}` for both.
- Mass roughly balances: outputs ≤ inputs, losses as byproducts or an estimate issue.
- `issues`: add `{"field": "...", "kind": "estimate", "note": "..."}` wherever a yield,
  duration, temperature or density is simplified. That is expected and good.

## Ingredient JSON

```json
{
  "id": "moromi", "label": "moromi", "form": "mash",
  "densityGPerMl": 1.1,
  "issues": [{"field": "densityGPerMl", "kind": "estimate", "note": "a wet brine mash"}]
}
```

- Optional fields: `aliases`, `form`, `texture`/`taste` (ints 0-3), `servingTags`,
  `densityGPerMl`, `waterActivity`, `named` (true for finished dishes like croutons),
  `issues`.
- `form` vocabulary (use the closest): liquid, piece, solid, whole, paste, slice, granule,
  batter, dice, powder, shred, mash, foam, gel, curd, gas.
- Give liquids a density (1.0-1.2) with an estimate issue; powders ~0.5-0.8. Intermediates
  consumed only by chain steps can be minimal (id/label/form).

## Techniques (use only these; every step must have a non-Thermal route)

Bound in `Techniques.java` (children inherit the nearest ancestor's binding):

- mix (CREATE mixing + shapeless crafting + Hexerei cauldron); blend, whisk, dissolve,
  knead, fold, season → mix. carbonate → mix.
- assemble (crafting + deploying + cauldron); coat, shape → assemble.
- heat (heated mixing + FD cooking pot + Hexerei cauldron); bake, roast, toast, grill,
  boil, simmer, steam, fry, pan_fry, stir_fry, evaporate, calcine, ash → heat family.
- cut (FD cutting board + Create cutting); chop, julienne, grind (Create milling +
  Hexerei pestle) → cut family.
- separate (Create item drain + mixing + Thermal centrifuge); press (item drain + mixing
  + Thermal press), churn (mixing + centrifuge), distill (heated mixing), strain,
  crystallize → separate family.
- soak, rinse, steep (soak child; also FR kettle + Theurgy liquefaction).
- Held on the item clock (a container/chest works): cool, chill, dry (Hexerei drying rack
  too), freeze, ferment (VD jar, BNC keg, PNC thermo plant, Theurgy fermentation), age
  (VD jar, BNC keg), spoil family, sprout.
- smoke (smoker + campfire), salt curing uses age.

When unsure about duration/temperature/medium, grep an extracted step with the same
technique and copy its ballparks: e.g.
`grep -rl '"technique": "smoke"' data/layers/extracted --include=*.json | head -3`.
Media seen in the dataset: pan_contact, simmering_water, boiling_water, oven_air,
frying_oil, still_air, steam, convection_air.

## Units

Common: g, ml, kg, l, piece, pinch, tsp, tbsp, cup, min, h, d, s. Prefer g/ml/min/h/d.

## New ingredients

1. **Check first** whether the ingredient already exists anywhere in the dataset:
   `find data/layers -path '*ingredients/<id>.json'`. If it exists, just reference it.
   Vanilla/mod-provided things usually exist (e.g. `cocoa_beans` → map later in the
   minecraft layer; do not invent `new:` ids for them).
2. **Ownership of shared intermediates**: `grep 'new:<id>' scratchpad/audit/section2_chains.tsv`
   — the first chain row that mentions `new:<id>` owns it. If you don't own it, reference
   it without creating the file; the owner creates it.
3. If you need a new intermediate NOT mentioned by any chain sketch, create it (id:
   lowercase snake_case), and note it in your commit message.

## Fudging (explicitly allowed this pass)

- Overcombine steps; skip intermediate niceties; estimate yields.
- If a sketch input is unobtainable (e.g. `vodka`, `rennet`), substitute the closest
  obtainable stand-in or a plain process and record an estimate/uncertain issue:
  vodka → steep in water (or rum, which we make), rennet → acid (vinegar/lemon)
  coagulation, etc.
- The **cheese chain is intentionally LEFT OUT** this pass. `parmesan_cheese` may rebase
  on milk + salt (coagulate → age). `feta_cheese`: acid coagulation, not rennet.
- Known process corrections worth making where cheap: bagels/pretzels get an alkaline
  baking-soda dip (a soak step with baking_soda in the water) before baking; masa-type
  corn (tortillas) is nixtamalized (soak/boil corn with an alkaline agent) — for plain
  `cornmeal` the simple dry-grind chain is fine.

## Supersedure batches (section3_supersede.tsv / section8_shortcuts.tsv only)

- These list a mod item whose shortcut recipe we want superseded. Find or create the
  dataset ingredient it should be (grep the extracted ingredients for a matching id/label:
  `grep -rl '"label": "<name>"' data/layers/extracted --include=*.json | head`), then author
  a faithful but overcombined chain (1-3 steps) from roughly the shortcut's real
  ingredients using proper techniques (ferment/age/steam/fry instead of bare crafting).
- Outputs of dish chains: give the finished dish `"named": true` and copy sensible
  texture/taste/servingTags from a similar extracted dish ingredient.
- Do not touch the listed mod items or their recipes — making the ingredient is enough;
  the committed supplanting pass removes the shortcut automatically.
- JOKE items in section3 (epicbacon, gummycreepers, jellybeans, energydrink) are KEPT:
  skip them entirely.

## Batch assignments (work ONLY your leaves)

Every child gets the full tsv files but must only author chains for its listed leaves.

- **B1 dairy**: butter cream buttermilk sour_cream whipped_cream condensed_milk margarine
  yogurt creme_fraiche kefir quark mascarpone_cheese ricotta cottage_cheese cream_cheese
  paneer fromage_frais dulce_de_leche clarified_butter ice_cream custard caramel
- **B2 press_oils_juices**: olive_oil vegetable_oil sesame_oil peanut_oil coconut_oil
  walnut_oil almond_oil lemon_oil orange_juice apple_juice grape_juice
  orange_juice_concentrate coconut_milk coconut_water fruit_nectar apple_cider hard_cider
  malt rolled_oats
- **B3 spices**: paprika chili_powder chile_flakes curry_powder garlic_powder onion_powder
  ground_cumin ground_coriander ground_cinnamon ground_nutmeg ground_cardamom ground_ginger
  ground_sesame_seeds ground_crayfish ginger_garlic_paste ginger_green_chilli_paste
  curry_paste cajun_seasoning adobo_seasoning taco_seasoning_mix herbes_de_provence
  poultry_seasoning seasoning_salt onion_salt celery_salt jerk_seasoning berbere_spice
  mitmita_spice_blend sambhar_masala sazon bengali_five_spice_mix_panch_puran
  ground_gingerbread_spices za_atar thai_chili_paste sambal_oelek lemon_pepper
  ground_fresh_pepper
- **B4 sauces**: mustard mayonnaise worcestershire_sauce fish_sauce hoisin_sauce
  oyster_sauce hot_sauce banana_ketchup tomato_paste tomato_sauce tomato_puree salsa
  hummus tahini anchovy_paste shrimp_paste gravy roux italian_dressing
  creamy_russian_dressing sweet_pickle_relish tamarind_chutney sun_dried_tomatoes
  seasoning_cubes dehydrated_broth custard_powder cake_mix baking_mix
- **B5 flours_meals**: cornmeal grits semolina bulgur couscous rice_flour rice_rava
  chickpea_flour oat_flour almond_flour buckwheat_flour manioc_flour tapioca_starch
  tapioca_pearls potato_flour whole_wheat_flour wheat_germ gluten soy_flour bread_crumbs
  vanilla_wafer_crumbs instant_mashed_potato_flakes
- **B6 doughs_baked**: pasta noodle matzo matzo_meal cracker graham_cracker tortilla
  pretzels bagel puff_pastry phyllo_dough pastry_dough english_muffins digestive_biscuit
  vanilla_wafers cookie cake bun hamburger_bun toast sev rice_wafer
- **B7 meat_fish_curing**: bacon sausage lard suet schmaltz bacon_fat corned_beef salt_pork
  pancetta guanciale chorizo pepperoni salami gelatin stockfish salt_cod dryfish
  smoked_salmon smoked_fish smoked_pork_slices cured_anchovies bonito_flakes fishball
  negitoro ground_fish
- **B8 drinks_sweets_ferments**: beer rum brandy whiskey sherry gin tequila coffee
  coffee_liqueur rosewater orange_flower_water jasmine_water mirin carbonated_water
  sourdough_starter marshmallow meringue marzipan buttercream marmalade cherry_jam
  cranberry_sauce cherry_pie_filling fruit_filling fruit_cocktail nougat jaggery
  golden_syrup
- **B9 soy_veg_preserves**: tofu tempeh iru douchi sauerkraut pickle nutritional_yeast
  textured_vegetable_protein raisin prune dried_fruit dried_chili split_pea agar nori
  dashi_japanese_soup_stock yeast_extract(vegemite-style: boiling yeast with... see note:
  vegemite is §8; skip unless trivial) agar
  (dedupe: agar once)
- **B10 supersedes-3**: every non-JOKE row of `section3_supersede.tsv`
- **B11 supersedes-8a**: biscuit bulgogi caprese_salad chicken_curry cinnamon_bread
  cinnamon_rolls corn_dogs croissant egg_roll eggnog fried_green_tomatoes fruit_cake
  general_tso_s_chicken hot_dog hot_wings ketchup kimchi lamington lemonade
- **B12 supersedes-8b**: mustard_seed naan oatmeal onion_rings peach_pie
  peanut_butter_cookies pecan_pie pineapple_juice pork_and_beans pork_pot_pie potato_chips
  salt stuffed_peppers stuffing tea toad_in_the_hole tomato_juice vanilla veggie_salad
  vinegar yorkshire_pudding vegemite

Note for B12: `salt` — evaporating sea water (water -evaporate-> salt) is acceptable with
an estimate issue; `vinegar` — ferment apple_cider (or any wine) to vinegar, held;
`tea` — steep mapped tea_leaves in hot water; `vanilla` — dry/cure vanilla pods.

## Sanity checks before committing

```bash
for f in $(git status --porcelain | awk '{print $2}' | grep '\.json$'); do
  python3 -c "import json;json.load(open('$f'))" || echo "BAD $f"
done
# unique recipe ids within your batch:
grep -h '"id"' data/layers/reviewed/LicenseRef-original/original/recipes/*.json | sort | uniq -d
```

## Commit

Single commit on your worktree branch:
`Add <domain> production chains (leaf audit section 2)` or `... (supersedure)`.
List any shared intermediates you created and any ingredient ids you invented that were
not in your sketch, in the commit body.
