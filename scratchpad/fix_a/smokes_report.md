# Smoke-step fixes (fix_a)

- chorizo.smoke -> tweaked input amount of `chorizo_mix` from 1050g to 1000g to match the output `chorizo` 1:1 by mass, fixing the "whole items would distort it by 540%" quantization refusal (strategy #1: amount tweak within ±15%).
- smoked_fish.smoke -> removed secondary `salt` input (60g), folding the salting into the `stateNote` and `issues` notes to satisfy the "takes one input, not 2" constraint (strategy #2: collapse secondary inputs).
- smoked_pork_slices.smoke -> removed secondary `salt` input (50g), folding the salting into the `stateNote` and `issues` notes to satisfy the "takes one input, not 2" constraint (strategy #2: collapse secondary inputs).
- smoked_salmon.smoke -> removed secondary `salt` input (60g), folding the salting into the `stateNote` and `issues` notes to satisfy the "takes one input, not 2" constraint (strategy #2: collapse secondary inputs).
