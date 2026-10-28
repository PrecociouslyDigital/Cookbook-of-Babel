# Bake-family route fixes — report

Validator: recipes were re-planned through a faithful Python replica of the Minecraft
layer's Merging → Eliding → Planner (validated against coverage.md: 146/146 quantization
refusals reproduced, plus the 309%/611% cases). "rounding N%" ≤ 15% is a clean emit;
all 15 edits emit without the ⚠ over-tolerance flag.

One line per step. `strategy #` refers to the task's numbered strategies.

| step | change |
|---|---|
| bun.bake | strategy 4: input `bun_dough` was elided to `dough` (50 g/item) while a bun is 150 g/item — no mass-sensible amounts can emit. New ingredient **bun_dough_roll** (whole, 150 g/item): 450 g → bun 440 g. Smelting/smoking emit at 2% rounding; one roll (150 g) bakes into one bun (150 g). |
| cheesy_asparagus.bake | strategy 2: folded cheese (120 g), olive oil (30 ml) and pepper (2 g) into `boiled_asparagus` 632 g → 600 g. Emits at 5%; one asparagus piece in, one cheesy-asparagus piece out. |
| cinnamon_bread.bake | strategy 4: input swirl loaf is 50 g/item while the bread is 400 g/item — impossible. New ingredient **cinnamon_swirl_batch** (solid, 400 g/item): 990 g → 900 g. Emits at 10%; one batch item bakes into one bread item. |
| cracker.bake | strategy 2+1: salt folded into `dough` (535 g), output trimmed to 375 g (−15%, the old 440 g left rounding stuck between 1 and 2 items). Emits at 12%; one dough piece bakes into one cracker. |
| digestive_biscuit.bake | strategy 4: dough is 50 g/item vs a 150 g biscuit — impossible. New ingredient **digestive_dough_batch** (solid): 665 g → 580 g. Emits at 14%; one batch item makes two biscuits. |
| english_muffins.bake | strategy 4+1: dough 50 g/item vs 150 g muffins — impossible. New ingredient **muffin_dough_batch** (solid): 816 g → 700 g. Emits at 14%; one batch item makes two muffins. |
| hamburger_bun.bake | strategy 2+4: butter and sugar folded in; the plain `dough` (50 g/item) still can't reach the 57 g bun cleanly, so new ingredient **burger_bun_dough** (whole): 555 g → 475 g. Emits at 13%; one dough roll makes two buns. |
| hot_wings.bake | strategy 2: buffalo sauce folded into `chicken` (1175 g) → 1050 g. Emits at 14%; one chicken item grills into two sauce-coated wings. |
| pretzels.bake | strategy 2+4: salt folded in; `lyed_dough` is 50 g/item vs 150 g pretzels — impossible. New ingredient **lyed_dough_batch** (solid): 510 g → 440 g. Emits at 13%; one batch item makes two pretzels. |
| rhubarb_crisp.bake | strategy 2: crisp topping and cinnamon folded into `rhubarb_compote` (892 g) → 850 g. Emits at 5%; one compote item bakes into two crisp pieces. |
| treacle_tart.bake | strategy 2: pie crust folded into `treacle_filling` (930 g) → 870 g. Emits at 14%; one filling item bakes into two tart slices. |
| yorkshire_pudding.bake | strategy 4: batter is a bowl-carried flowable (1 item ≈ 250 ml) — the butter-folded batter can't reach the 400 g pudding cleanly. New ingredient **yorkshire_batter_batch** (solid): 480 g → 420 g. Emits at 14%; one batch item bakes into one pudding. |
| caramel.heat | strategy 5 (left unchanged): the input syrup is paper-twist items (~4.5 items at 900 g) and the output caramel is bowl-portions (190 g ≈ 0.6 items). Whole-item rounding of a ~4.7:1 evaporation between two different count formats gives ≥ 295% distortion at every scale; no amount tweak or single input can land it under the 100% rejection bar, and no intermediate can change the output's bowl conversion. |
| tortilla.roast | strategy 4: dough is 50 g/item vs a 150 g tortilla — impossible. New ingredient **tortilla_dough_ball** (whole, 150 g/item): 660 g → 600 g. Smelting/smoking/thermal smelter emit at 10%; one dough ball (150 g) presses into one tortilla (150 g). |
| bread.croutons | strategy 2: oil and salt folded into `stale_bread` (231 g) → 180 g. Emits at 4% (smelting, smoking and thermal smelter); one stale-bread item toasts into five crouton pieces. |
| grilled_eggplant.grill | strategy 2+4: oil, salt, paprika and garlic folded into the eggplant; chopped eggplant is 50 g/item while the grill needs ~1:1 items — new ingredient **seasoned_eggplant** (mash, 100 g/item): 765 g → 380 g. Campfire/smoking emit at 5%; one mash item grills into one eggplant piece. |
| rice_cake.grill | strategy 4: pounded rice is a 100 g/item mash vs a 400 g rice cake — impossible. New ingredient **rice_cake_dough** (solid): 1070 g → 940 g. Campfire/smoking emit at 14%; one batch item grills into one cake. |

## Notes for the coordinator

- All 16 edited steps emit on every bound route (smelting/smoking for bake/roast/toast,
  campfire/smoking for grill) with rounding inside the 15% tolerance.
- New ingredient files (reported, sibling shape, 10 files):
  `bun_dough_roll`, `cinnamon_swirl_batch`, `digestive_dough_batch`, `muffin_dough_batch`,
  `burger_bun_dough`, `lyed_dough_batch`, `yorkshire_batter_batch`, `seasoned_eggplant`,
  `rice_cake_dough`, `tortilla_dough_ball` — each a batch/roll form of the step's input so
  whole-item quantization lands inside tolerance. None is made by a step (they are seeds), so
  the coverage "unmapped leaves" section will grow by ten names; the chains they stand in for
  remain in the dataset.
- Mass per craft is conserved for every emitting recipe (e.g. 150 g dough roll → 150 g bun).
- Output/input mass ratios are all within the 0.2–2 massRatio band (babel only warns).
- The original 855 g → 440 g style yield estimates were preserved unless quantization
  demanded otherwise (cracker −15%, english muffins −3%, everything else untouched).
