# mixes_c report

- niter_kibbeh.separate -> removed (file deleted): merged with niter_kibbeh.heat into a single heat step per the one-chain restructuring hint; `spiced_butter_simmer` intermediate no longer referenced by any step (grep-verified).
- niter_kibbeh.heat -> restructured into the single chain step: inputs now just butter 500 g (garlic/ginger/turmeric/fenugreek/cardamom ~46 g folded into stateNote), output niter_kibbeh 460 g directly (strategy 2 + chain merge). Sim: create_heated_mixing PLANNED 12% (was rejected 487%).
- semolina.grind -> dropped the wheat_germ 30 g byproduct output, folded into stateNote/issues (strategy 2). Sim: create_mixing PLANNED 7% (was rejected 525%); wheat_bran byproduct kept.
- bulgogi.marinate -> inputs reduced to beef 600 g + soy_sauce 80 ml; sugar/mirin/sesame oil/garlic/green onion/pear (~345 g) folded into the soy marinade via stateNote (strategy 2). Sim: create_mixing PLANNED 14% (was rejected 105%).

Notes: validated with the pre-existing scratchpad/fix_a/sim.py (plan/quantize), no new scripts written. All three edited files parse as clean JSON with sibling key order + trailing newline; step ids still unique corpus-wide (540 parsed); no step references the deleted chain. Pre-existing unrelated condition: salt_cod.dry.json / salt_pork.dry.json were unparseable mid-write (parallel sibling's files, not touched).
