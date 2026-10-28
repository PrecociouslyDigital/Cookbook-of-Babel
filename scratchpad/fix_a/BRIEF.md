# Recipe-fix brief (fix_a pass 2)

You are authoring recipe fixes in /Users/skye/workbench/Cookbook-of-Babel (a cooking-data mod).

## HARD RULES
1. NEVER run git, cabal, gradle, or any build/commit command — parallel siblings share this directory.
2. ONLY edit the recipe JSON files assigned to you (at `data/layers/reviewed/LicenseRef-original/original/recipes/<id>.json`). The ONLY other files you may touch are NEW ingredient JSONs you must create at `.../ingredients/<snake_case>.json` (report each).
3. Work briskly and tersely: read the file, write the new version with the write tool (full-file rewrite is fine), one short report line per step. Do not echo file contents into your messages.

## THE PROBLEM
Each assigned step has NO emitting route in the Minecraft layer. See `mods/minecraft/coverage.md` under `### <step> (<technique>)` for the exact refusal, e.g. `smelting: none, whole items would distort it by 309%`, `fd_cutting_board: none, takes one input, not 3`, `transition: not written: the game can't make X`. Your job: edit the recipe so it gains an emitting route, keeping it culinarily sensible.

## STRATEGIES (in preference order; the owner explicitly approved overcombining and fudging)
1. Tweak amounts/outputs within ~±15% if that alone fixes quantization. Calibrate on working siblings (e.g. `bread.bake`: dough 855 g → bread 750 g, oven_air 220 °C).
2. Collapse small secondary/seasoning inputs (salt, spices, pinches) into the main input or the stateNote — refusals like "takes one input, not N" usually mean merge the extras.
3. Reuse an existing named shared step instead of a one-off (grep `recipes/*.json` for named steps, e.g. `heat_butter`).
4. If the technique is impossible for the shape, invent ONE intermediate ingredient (sibling shape: `{"id","label","form","issues":[{"field","kind":"estimate","note"}]}`) and split the step into two steps with unique ids.
5. Last resort: leave the step and explain why in your report.

## FORMAT
2-space indent, keys in sibling order (`id, source, technique, inputs, outputs, environment, end, stateNote, issues`), trailing newline. Read 2–3 sibling recipe files first to match the schema exactly (inputs is an object with an `of` array of `{ingredient, amount:{qty,unit}}`).

## VALIDATOR CONSTRAINTS
Every referenced ingredient must exist as an ingredient file; step ids unique across recipes; amounts positive; units among g, ml, piece, h, min, s; temperatureC positive. When moderately unsure, take the stab anyway (the owner prefers amendable stabs).

## DELIVERABLE
Write your report to the path your task names, one line per step: `id -> what changed (strategy #)` or `id -> left because ...`. Finish by writing that report file.
