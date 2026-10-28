# mixes_b report

Verified with the existing planner replica `scratchpad/fix_a/sim.py` (reproduces every baseline refusal exactly); all four steps now emit via their refused route at <=15% rounding. No new ingredient files needed.

- italian_dressing.mix -> italian_seasoning 5 g -> 4.5 g (strategy 1): the pinch drops under the 2% negligible-mass threshold, so its 0.05-of-an-item no longer distorts; create_mixing now plans at 7% (shapeless 6%).
- taco_seasoning_mix.mix -> garlic_powder, onion_powder, oregano, paprika folded into chili_powder (200 -> 500 g), cumin 150 -> 170 g (strategies 1+2): the output fills a 1000 mB tank as a fluid, capping the batch factor at ~1.34, where the 0.2-0.4-item powders could never round; as fluids the powders are exact mB. create_mixing plans at 15% (shapeless 13%).
- carbonated_water.carbonate -> lemon 30 ml collapsed into the stateNote (strategy 2): at tank-capped factor 1 the whole lemon was 0.2 items (400%); water + baking_soda are exact mB. create_mixing now plans at 1%.
- salt.evaporate -> water byproduct (1940 ml) removed, salt 60 -> 68 g (strategies 1+2): the byproduct tank-capped the factor at 0.515 while salt-as-item (mods provide salt only as an item) needed factor >= 4.35 - impossible together; evaporated water isn't a collected product anyway. create_heated_mixing now plans at 14%.
