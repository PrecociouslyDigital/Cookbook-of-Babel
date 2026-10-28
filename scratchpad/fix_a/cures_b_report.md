# Cures-B route fixes — report

One line per step. `strategy #` refers to the task's numbered strategies.
All four steps emit on `vd_fermenting_jar` after the edit (`bnc_keg` still refuses: it ignores
NBT, so it can't make the mod's own outputs). Distortions verified with the fix_a planner
replica, which reproduces the original refusals exactly (1325/1900/1686/900%).

- guanciale.cure -> strategy #2: removed the `salt` input (100 g), folding the salting into the existing `stateNote` ("salted, peppered and hung to cure"); kept pork 1000 g and the pepper pinch (10 g, under the 2% negligible share). Emits at 5% rounding.
- mutton_jerky.age -> strategy #2: removed the `salt` input (60 g), folding the rubbing into the `stateNote` ("strips rubbed with salt and cured in the cold"); lamb 1000 g -> salted_mutton_strips 880 g unchanged. Emits at 14% rounding.
- pancetta.cure -> strategy #2: removed the `salt` input (80 g), folding the curing into the `stateNote` ("cured, peppered, rolled tight and hung to mature"); kept pork 1000 g and pepper 10 g. Emits at 11% rounding.
- pemmican.age -> strategy #2: removed the `salt` input (45 g), folding the salting into the `stateNote` ("salt-cured lean strips dried until rock-hard"); beef 900 g -> dried_lean_beef 430 g unchanged. Emits at 7% rounding.

No new ingredient files were needed; no outputs or durations changed.
