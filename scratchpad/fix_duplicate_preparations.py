# ruff: noqa
#!/usr/bin/env python3
"""Resolve the 37 duplicate-preparation conflicts in the reviewed chains.

Strategies (per the coordinator's decision with the user):
- reuse the extracted prep output where semantics match, naming it in a
  reviewed ingredient override (butter_heat -> "melted butter", ...);
- route conflicting steps through exempt techniques (separate/assemble
  family, incl. press/churn/distill/strain/crystallize/coat/shape);
- add a faithful second input so the step is multi-input (never a preparation);
- switch to a fresh pair (blend for spice grinders, boil+crystallize for jaggery).
"""
import json, glob, os

R = 'data/layers/reviewed/LicenseRef-original/original'
REC, ING = f'{R}/recipes', f'{R}/ingredients'

def load(p):
    return json.load(open(p))
def save(p, d):
    json.dump(d, open(p, 'w'), indent=2)
    open(p, 'a').write('\n')

def edit_recipe(name, *, technique=None, set_inputs=None, add_inputs=None,
                set_outputs=None, add_outputs=None):
    p = f'{REC}/{name}.json'
    d = load(p)
    if technique: d['technique'] = technique
    if set_inputs is not None:
        d['inputs'] = {'of': [{'ingredient': i, 'amount': a} for i, a in set_inputs]}
    if add_inputs:
        d['inputs']['of'] += [{'ingredient': i, 'amount': a} for i, a in add_inputs]
    if set_outputs is not None:
        d['outputs'] = [{'ingredient': i, 'amount': a, **({'role': r} if r else {})}
                        for i, a, r in set_outputs]
    if add_outputs:
        d['outputs'] += [{'ingredient': i, 'amount': a, **({'role': r} if r else {})}
                         for i, a, r in add_outputs]
    save(p, d)

def delete(path):
    os.remove(path)

def rewire(old, new):
    """Replace ingredient references across all reviewed recipes."""
    n = 0
    for f in glob.glob(f'{REC}/*.json'):
        d = load(f)
        text = json.dumps(d)
        if f'"{old}"' not in text:
            continue
        d = json.loads(text.replace(f'"{old}"', f'"{new}"'))
        save(f, d)
        n += 1
    return n

def name_prep(ing, label, note):
    """Reviewed override naming a consolidated prep output."""
    d = {"id": ing, "label": label, "named": True,
         "issues": [{"field": "named", "kind": "estimate", "note": note}]}
    # copy form/density from the extracted ingredient if present
    for f in glob.glob(f'data/layers/**/ingredients/{ing}.json', recursive=True):
        try:
            e = json.load(open(f))
        except Exception:
            continue
        for k in ('form', 'densityGPerMl', 'waterActivity'):
            if k in e and k not in d:
                d[k] = e[k]
        break
    save(f'{ING}/{ing}.json', d)

# ---- 1. reuse + rewire (delete our single-input cut/roast steps) -----------
for f in ['fried_chicken.cut', 'grilled_eggplant.slice', 'onion_rings.cut',
          'fried_onion.slice', 'potato_chips.slice', 'potato_chips.soak',
          'negitoro.cut', 'tomato_juice.cut', 'pineapple_juice.cut',
          'buttercream.grind_sugar', 'jaggery.evaporate_juice',
          'garlic_powder.slice', 'garlic_powder.dry_slices']:
    delete(f'{REC}/{f}.json')

edit_recipe('fried_chicken.coat', set_inputs=[('chicken_cut', {"qty": 700, "unit": "g"}),
    ('egg', {"qty": 2, "unit": "piece"}), ('wheat_flour', {"qty": 120, "unit": "g"}),
    ('salt', {"qty": 6, "unit": "g"}), ('pepper', {"qty": 3, "unit": "g"})])
edit_recipe('grilled_eggplant.grill', set_inputs=[('eggplant_cut', {"qty": 700, "unit": "g"}),
    ('olive_oil', {"qty": 45, "unit": "ml"}), ('salt', {"qty": 6, "unit": "g"}),
    ('paprika', {"qty": 4, "unit": "g"}), ('garlic', {"qty": 10, "unit": "g"})])
edit_recipe('onion_rings.batter', set_inputs=[('onion_cut', {"qty": 300, "unit": "g"}),
    ('wheat_flour', {"qty": 100, "unit": "g"}), ('water', {"qty": 150, "unit": "ml"})])
edit_recipe('fried_onion.pan_fry', set_inputs=[('onion_cut', {"qty": 300, "unit": "g"}),
    ('olive_oil', {"qty": 30, "unit": "ml"}), ('salt', {"qty": 4, "unit": "g"})])
edit_recipe('potato_chips.fry', set_inputs=[('potato_cut', {"qty": 700, "unit": "g"}),
    ('vegetable_oil', {"qty": 500, "unit": "ml"})])
edit_recipe('negitoro.mix', set_inputs=[('tuna_cut', {"qty": 300, "unit": "g"}),
    ('green_onion', {"qty": 60, "unit": "g"})])
edit_recipe('tomato_juice.press', set_inputs=[('tomato_cut', {"qty": 900, "unit": "g"})])
edit_recipe('pineapple_juice.press', set_inputs=[('pineapple_cut', {"qty": 800, "unit": "g"})],
            add_outputs=[('pineapple_skin', {"qty": 120, "unit": "g"}, 'byproduct')])
edit_recipe('coffee.grind_beans', set_inputs=[('coffee_beans_roast', {"qty": 500, "unit": "g"})])
edit_recipe('coffee.roast_beans', set_outputs=[('coffee_beans_roast', {"qty": 480, "unit": "g"}, None)])
edit_recipe('tahini.roast', set_outputs=[('sesame_seed_roast', {"qty": 480, "unit": "g"}, None)])
edit_recipe('tahini.grind', set_inputs=[('sesame_seed_roast', {"qty": 480, "unit": "g"})])
edit_recipe('za_atar.mix', set_inputs=[('thyme', {"qty": 40, "unit": "g"}),
    ('sumac', {"qty": 30, "unit": "g"}), ('sesame_seed_roast', {"qty": 60, "unit": "g"}),
    ('salt', {"qty": 10, "unit": "g"})])
edit_recipe('manioc_flour.dry', set_outputs=[('cassava_dry', {"qty": 880, "unit": "g"}, None)])
edit_recipe('manioc_flour.grind', set_inputs=[('cassava_dry', {"qty": 880, "unit": "g"})])
edit_recipe('dehydrated_broth.evaporate',
            set_outputs=[('broth_and_stock_evaporate', {"qty": 400, "unit": "ml"}, None)])
edit_recipe('dehydrated_broth.mix_bouillon',
            set_inputs=[('broth_and_stock_evaporate', {"qty": 400, "unit": "ml"}),
                        ('salt', {"qty": 150, "unit": "g"})])
edit_recipe('buttercream.mix', set_inputs=[('butter', {"qty": 300, "unit": "g"}),
    ('white_sugar_grind', {"qty": 400, "unit": "g"})])
edit_recipe('garlic_powder.grind', set_inputs=[('dehydrated_garlic', {"qty": 120, "unit": "g"})])
edit_recipe('dehydrated_garlic.dry_slices', set_inputs=[('garlic_chop', {"qty": 700, "unit": "g"})],
            set_outputs=[('dehydrated_garlic', {"qty": 120, "unit": "g"}, None)])
edit_recipe('grits.grind', technique='separate', set_inputs=[('corn_grind', {"qty": 900, "unit": "g"})],
            set_outputs=[('grits', {"qty": 700, "unit": "g"}, None),
                         ('corn_flour', {"qty": 180, "unit": "g"}, 'byproduct')])
edit_recipe('rice_rava.grind', technique='separate', set_inputs=[('rice_grind', {"qty": 900, "unit": "g"})])
edit_recipe('bun.bake', set_inputs=[('bun_dough', {"qty": 855, "unit": "g"})])

# ---- 2. exempt-technique re-routes ----------------------------------------
edit_recipe('seasoning_cubes.dry', technique='shape')
edit_recipe('clarified_butter.heat', technique='separate', set_inputs=[('butter_heat', {"qty": 900, "unit": "g"})],
            add_outputs=[('milk_solids', {"qty": 120, "unit": "g"}, 'byproduct')])
edit_recipe('bacon_fat.render', technique='separate', set_inputs=[('bacon_heat', {"qty": 800, "unit": "g"})])
edit_recipe('schmaltz.render', technique='separate', set_inputs=[('chicken_heat', {"qty": 900, "unit": "g"})])
edit_recipe('lard.cut', technique='separate')
edit_recipe('caramel.heat', technique='roast', set_inputs=[('white_sugar_heat', {"qty": 900, "unit": "g"})])
edit_recipe('semolina.grind', technique='separate', set_inputs=[('wheat', {"qty": 1000, "unit": "g"})],
            set_outputs=[('semolina', {"qty": 750, "unit": "g"}, None),
                         ('wheat_bran', {"qty": 120, "unit": "g"}, 'byproduct')])
edit_recipe('whole_wheat_flour.grind', technique='mix',
            set_inputs=[('wheat_flour', {"qty": 500, "unit": "g"}),
                        ('wheat_germ', {"qty": 50, "unit": "g"}),
                        ('wheat_bran', {"qty": 100, "unit": "g"})],
            set_outputs=[('whole_wheat_flour', {"qty": 650, "unit": "g"}, None)])

# ---- 3. faithful second inputs (multi-input steps are never preparations) --
edit_recipe('kefir.ferment', add_inputs=[('buttermilk', {"qty": 50, "unit": "g"})])
edit_recipe('whipped_cream.whisk', add_inputs=[('white_sugar', {"qty": 25, "unit": "g"})])

# ---- 4. fresh pairs -------------------------------------------------------
for f in ['almond_flour.grind', 'rice_flour.grind', 'oatmeal.grind',
          'ground_cardamom.grind', 'ground_cinnamon.grind', 'ground_coriander.grind',
          'ground_cumin.grind', 'ground_nutmeg.grind', 'ground_fresh_pepper.grind',
          'ground_sesame_seeds.grind']:
    edit_recipe(f, technique='blend')

# jaggery: cane juice -> cane syrup (boil) -> jaggery (crystallize, exempt)
edit_recipe('jaggery.boil_syrup'.replace('.boil_syrup', '') and 'x', technique=None) if False else None
save(f'{REC}/jaggery.boil_syrup.json', {
    "id": "jaggery.boil_syrup", "source": "cookbook_of_babel", "technique": "boil",
    "inputs": {"of": [{"ingredient": "cane_juice", "amount": {"qty": 1000, "unit": "ml"}}]},
    "outputs": [{"ingredient": "cane_syrup", "amount": {"qty": 300, "unit": "ml"}}],
    "environment": {"temperatureC": 105, "medium": "pan_contact"},
    "end": {"by": "time", "duration": {"qty": 2, "unit": "h"}},
    "stateNote": "boiled down to a thick golden syrup",
    "issues": [{"field": "outputs", "kind": "estimate", "note": "cane juice is about a seventh sugar; long open-pan boil"}]})
save(f'{REC}/jaggery.crystallize.json', {
    "id": "jaggery.crystallize", "source": "cookbook_of_babel", "technique": "crystallize",
    "inputs": {"of": [{"ingredient": "cane_syrup", "amount": {"qty": 300, "unit": "ml"}}]},
    "outputs": [{"ingredient": "jaggery", "amount": {"qty": 280, "unit": "g"}}],
    "end": {"by": "time", "duration": {"qty": 12, "unit": "h"}},
    "stateNote": "cooled in moulds into a solid block",
    "issues": [{"field": "outputs", "kind": "estimate", "note": "unrefined cane sugar, small losses to the pan"}]})
save(f'{ING}/cane_syrup.json', {
    "id": "cane_syrup", "label": "cane syrup", "form": "liquid", "densityGPerMl": 1.35,
    "issues": [{"field": "densityGPerMl", "kind": "estimate", "note": "a dense sugar syrup"}]})

# bun dough via shaping (assemble family)
save(f'{REC}/bun.shape.json', {
    "id": "bun.shape", "source": "cookbook_of_babel", "technique": "shape",
    "inputs": {"of": [{"ingredient": "dough", "amount": {"qty": 855, "unit": "g"}}]},
    "outputs": [{"ingredient": "bun_dough", "amount": {"qty": 855, "unit": "g"}}],
    "end": {"by": "time", "duration": {"qty": 5, "unit": "min"}},
    "stateNote": "divided and rounded into rolls",
    "issues": [{"field": "outputs", "kind": "estimate", "note": "shaping is mass-preserving"}]})
save(f'{ING}/bun_dough.json', {
    "id": "bun_dough", "label": "bun dough", "form": "whole", "named": False})

# paprika from the sweet pods, hot pods as the byproduct
save(f'{REC}/chiles.separate.json', {
    "id": "chiles.separate", "source": "cookbook_of_babel", "technique": "separate",
    "inputs": {"of": [{"ingredient": "chiles", "amount": {"qty": 1000, "unit": "g"}}]},
    "outputs": [{"ingredient": "sweet_chiles", "amount": {"qty": 600, "unit": "g"}},
                {"ingredient": "hot_chiles", "amount": {"qty": 400, "unit": "g"}, "role": "byproduct"}],
    "end": {"by": "time", "duration": {"qty": 10, "unit": "min"}},
    "stateNote": "the mild sweet pods sorted from the hot ones",
    "issues": [{"field": "outputs", "kind": "estimate", "note": "a mixed chile harvest sorted by heat"}]})
save(f'{ING}/sweet_chiles.json', {"id": "sweet_chiles", "label": "sweet chiles", "form": "whole"})
save(f'{ING}/hot_chiles.json', {"id": "hot_chiles", "label": "hot chiles", "form": "whole"})
save(f'{ING}/dried_sweet_chiles.json', {"id": "dried_sweet_chiles", "label": "dried sweet chiles", "form": "whole"})
edit_recipe('paprika.dry_chiles', set_inputs=[('sweet_chiles', {"qty": 600, "unit": "g"})],
            set_outputs=[('dried_sweet_chiles', {"qty": 180, "unit": "g"}, None)])
edit_recipe('paprika.grind_dried_chiles', set_inputs=[('dried_sweet_chiles', {"qty": 180, "unit": "g"})])

# ---- 5. delete intermediates nothing extracted references -----------------
for f in ['chicken_pieces', 'eggplant_slices', 'onion_rings_cut', 'sliced_onion',
          'potato_slices', 'soaked_potato_slices', 'minced_tuna', 'diced_tomato',
          'pineapple_chunks', 'powdered_sugar', 'broth_concentrate', 'dried_cassava',
          'dried_garlic', 'garlic_slices', 'roasted_coffee_beans', 'toasted_sesame_seed']:
    p = f'{ING}/{f}.json'
    if os.path.exists(p):
        delete(p)

# ---- 6. name the consolidated preps in the reviewed layer ------------------
name_prep('butter_heat', 'melted butter', 'heating butter almost always means melting it; hundreds of dishes share this')
name_prep('bacon_heat', 'cooked bacon', 'heated bacon is cooked bacon; the drippings render out of it')
name_prep('chicken_heat', 'cooked chicken', 'plain heated chicken is simply cooked chicken')
name_prep('cream_whisk', 'whipped cream', 'whisking cream whips it; this is the unsweetened kind')
name_prep('chicken_cut', 'cut chicken', 'the dataset\u2019s generic cut-up chicken')
name_prep('onion_cut', 'cut onion', 'the dataset\u2019s generic cut onion')
name_prep('tomato_cut', 'cut tomato', 'the dataset\u2019s generic cut tomato')
name_prep('potato_cut', 'cut potato', 'the dataset\u2019s generic cut potato')
name_prep('garlic_chop', 'chopped garlic', 'the dataset\u2019s generic chopped garlic')
name_prep('eggplant_cut', 'cut eggplant', 'the dataset\u2019s generic cut eggplant')
name_prep('pineapple_cut', 'cut pineapple', 'the dataset\u2019s generic cut pineapple')
name_prep('tuna_cut', 'cut tuna', 'the dataset\u2019s generic cut tuna')
name_prep('coffee_beans_roast', 'roasted coffee beans', 'roasted coffee beans, ready to grind')
name_prep('sesame_seed_roast', 'toasted sesame seeds', 'dry-toasted sesame seeds')
name_prep('cassava_dry', 'dried cassava', 'cassava dried for milling')
name_prep('broth_and_stock_evaporate', 'reduced broth', 'broth boiled down to a concentrate')
name_prep('white_sugar_grind', 'powdered sugar', 'finely ground sugar, i.e. icing sugar')

print('applied all fixes')
