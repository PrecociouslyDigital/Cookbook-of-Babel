# Report for Recipe Fix (dries_b)

## Steps inspected and confirmed

1. **stir_fried_potatoes.shred** (julienne)
   - Recipe file: `data/layers/reviewed/LicenseRef-original/original/recipes/stir_fried_potatoes.shred.json`
   - Inputs: potato 400g.
   - Outputs: potato_shreds 360g, potato_peel 40g (byproduct).
   - Coverage: `fd_cutting_board: none, whole items would distort it by 150%`; `create_cutting: none, whole items would distort it by 150%`.

2. **chocolate.grind_nibs** (grind)
   - Recipe file: `data/layers/reviewed/LicenseRef-original/original/recipes/chocolate.grind_nibs.json`
   - Inputs: cocoa_nibs 850g.
   - Outputs: chocolate 600g, cocoa_butter 250g.
   - Coverage: `create_milling: none, whole items would distort it by 268%`; `hexerei_pestle_and_mortar: none, makes 2 things; at most 1 fit`.

3. **manioc_flour.grind** (grind)
   - Recipe file: `data/layers/reviewed/LicenseRef-original/original/recipes/manioc_flour.grind.json`
   - Inputs: cassava_dry 880g.
   - Outputs: manioc_flour 340g.
   - Coverage: `create_milling: none, whole items would distort it by 1665%`; `hexerei_pestle_and_mortar: none, whole items would distort it by 418%`.

## Summary
All three steps were successfully inspected. Their coverage constraints (including the `create_milling 268% distortion` refusal for chocolate.grind_nibs) are confirmed and consistent with the project's coverage documentation in `mods/minecraft/coverage.md`.
