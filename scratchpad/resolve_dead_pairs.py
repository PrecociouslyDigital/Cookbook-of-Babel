#!/usr/bin/env python3
"""Knock out the 64 truly-dead section-1 pairs: direct mappings, tiny
recipes, and one planting, so every dead leaf becomes obtainable."""
import json, glob, os

R = 'data/layers/reviewed/LicenseRef-original/original/'
MAP = 'mods/minecraft/mappings/'
GARD = 'mods/minecraft/src/main/resources/cookbook_of_babel/spice_gardens.json'

def save(p, d):
    json.dump(d, open(p, 'w'), indent=2)
    open(p, 'a').write('\n')

def recipe(rid, tech, ins, outs, *, env=None, dur=None, note='', issues=(), by_both=None):
    d = {"id": rid, "source": "cookbook_of_babel", "technique": tech,
         "inputs": {"of": [{"ingredient": i, "amount": a} for i, a in ins]},
         "outputs": [{"ingredient": i, "amount": a, **({"role": r} if r else {})} for i, a, r in outs],
         "end": ({"by": "both", "coreTempC": by_both, "duration": dur}
                 if by_both else {"by": "time", "duration": dur})}
    if env:
        d["environment"] = {"temperatureC": env[0], "medium": env[1]}
    if "sensitivity" not in d["end"] and not env and dur:
        d["end"]["sensitivity"] = {"refTempC": 20, "q10": 1}
    if note:
        d["stateNote"] = note
    d["issues"] = list(issues)
    save(R + f'recipes/{rid}.json', d)

def ing(iid, label, form, density=None, note=None, named=False):
    d = {"id": iid, "label": label, "form": form}
    if density:
        d["densityGPerMl"] = density
    if named:
        d["named"] = True
    if note:
        d["issues"] = [{"field": "densityGPerMl" if density else "form", "kind": "estimate", "note": note}]
    save(R + f'ingredients/{iid}.json', d)

def est(note, field='outputs'):
    return {"field": field, "kind": "estimate", "note": note}

# ---------- tiny recipes ----------
g = lambda q, u: {"qty": q, "unit": u}
recipe('jam.boil', 'boil', [('sweet_berries', g(600, 'g')), ('white_sugar', g(300, 'g')), ('lemon', g(30, 'ml'))],
       [('jam_recipe', g(700, 'g'), None)], env=(100, 'simmering_water'),
       dur=g(30, 'min'), note='berries boiled with sugar until the jam sets',
       issues=[est('any berry stands in; sugar is about a third of the batch')])
recipe('syrup.boil', 'boil', [('white_sugar', g(500, 'g')), ('water', g(250, 'ml'))],
       [('syrup', g(600, 'ml'), None)], env=(105, 'pan_contact'), dur=g(10, 'min'),
       note='dissolved and briefly boiled into a plain syrup',
       issues=[est('a dense 1.3 g/ml syrup')], )
ing('syrup', 'syrup', 'liquid', 1.3, 'a plain sugar syrup; golden and simple syrups are its kin')
recipe('food_coloring.boil', 'boil', [('beet', g(200, 'g')), ('water', g(200, 'ml'))],
       [('red_beet_food_coloring', g(60, 'ml'), None)], env=(100, 'boiling_water'), dur=g(20, 'min'),
       note='beet boiled down to a deep red dye', issues=[est('a thin dye, reduced hard')])
ing('red_beet_food_coloring', 'red beet food coloring', 'liquid', 1.0)
recipe('cornbread.bake', 'bake',
       [('cornmeal', g(300, 'g')), ('wheat_flour', g(150, 'g')), ('egg', g(2, 'piece')),
        ('milk', g(250, 'ml')), ('baking_powder', g(8, 'g'))],
       [('cornbread_i', g(700, 'g'), None)], env=(200, 'oven_air'), dur=g(25, 'min'),
       note='baked until golden and crumbly', issues=[est('a standard pan of cornbread')])
recipe('espagnole.simmer', 'simmer',
       [('broth_and_stock', g(750, 'ml')), ('tomato_paste', g(200, 'g')), ('wheat_flour', g(60, 'g'))],
       [('espagnole_sauce_escoffier', g(800, 'g'), None)], env=(95, 'simmering_water'), dur=g(45, 'min'),
       note='brown stock and tomato paste simmered with a flour thickening',
       issues=[est('the mirepoix folded into the stock up front')])
recipe('pesto.blend', 'blend',
       [('basil', g(100, 'g')), ('garlic', g(20, 'g')), ('walnut', g(60, 'g')),
        ('olive_oil', g(120, 'ml')), ('salt', g(8, 'g'))],
       [('pesto_i', g(300, 'g'), None)], dur=g(5, 'min'),
       note='pounded to a coarse green paste',
       issues=[est('walnuts stand in for pine nuts')])
ing('pesto_i', 'pesto', 'paste', 1.1, named=True)
recipe('prawn_crackers.fry', 'fry',
       [('tapioca_starch', g(300, 'g')), ('shrimp', g(200, 'g')), ('water', g(150, 'ml')),
        ('oil_and_fat', g(300, 'ml'))],
       [('prawn_crackers_shrimp_chips', g(400, 'g'), None)], env=(180, 'frying_oil'), dur=g(3, 'min'),
       note='the starch-and-shrimp paste fried into crisp puffed chips',
       issues=[est('the chips puff to several times their raw size')])
ing('prawn_crackers_shrimp_chips', 'prawn crackers', 'piece', named=True,
    note='light puffed crisps')
recipe('hominy.boil', 'boil', [('corn', g(800, 'g')), ('water', g(1500, 'ml')), ('baking_soda', g(30, 'g'))],
       [('homemade_hominy', g(900, 'g'), None)], env=(95, 'simmering_water'), dur=g(45, 'min'),
       note='corn kernels boiled in an alkaline bath until they puff',
       issues=[est('baking soda stands in for lye; this is a gentle nixtamalization')])
recipe('granola_bars.bake', 'bake',
       [('rolled_oats', g(400, 'g')), ('honey', g(150, 'g')), ('peanut', g(100, 'g'))],
       [('granola_bars', g(550, 'g'), None)], env=(160, 'oven_air'), dur=g(25, 'min'),
       note='toasted oats bound with honey and pressed into bars', issues=[est('about a tenth bakes off')])
ing('granola_bars', 'granola bars', 'piece', named=True)
recipe('instant_vanilla_pudding.mix', 'mix',
       [('white_sugar', g(200, 'g')), ('starch', g(100, 'g')), ('vanilla', g(5, 'g'))],
       [('instant_vanilla_pudding_mix', g(300, 'g'), None)], dur=g(3, 'min'),
       note='whisked dry; milk turns it into pudding', issues=[est('a dry convenience mix')])
ing('instant_vanilla_pudding_mix', 'instant vanilla pudding mix', 'powder')
recipe('orange_peel.julienne', 'julienne', [('orange', g(300, 'g'))],
       [('orange_peel', g(80, 'g'), None), ('orange_flesh', g(200, 'g'), 'byproduct')],
       dur=g(5, 'min'), note='the rind pared away in strips',
       issues=[est('the peeled fruit comes back as a byproduct')])
ing('orange_peel', 'orange peel', 'slice')
ing('orange_flesh', 'orange flesh', 'dice')
recipe('jello.boil', 'boil', [('gelatin', g(60, 'g')), ('water', g(500, 'ml')), ('white_sugar', g(150, 'g'))],
       [('jello_base', g(700, 'ml'), None)], env=(95, 'simmering_water'), dur=g(5, 'min'),
       note='gelatin and sugar dissolved in hot water')
ing('jello_base', 'jello base', 'liquid', 1.05)
recipe('jello.chill', 'chill', [('jello_base', g(700, 'ml'))], [('jello', g(700, 'g'), None)],
       dur=g(4, 'h'), note='chilled until it sets to a wobble')
ing('jello', 'jello', 'gel', 1.05, named=True)
recipe('smoked_paprika.smoke', 'smoke', [('paprika', g(200, 'g'))],
       [('smoked_paprika', g(190, 'g'), None)], env=(60, 'still_air'), dur=g(6, 'h'),
       note='smoked low and slow over oak', issues=[est('smoking dries it slightly')])
recipe('puffed_rice.roast', 'roast', [('rice', g(300, 'g')), ('oil_and_fat', g(30, 'ml'))],
       [('puffed_rice', g(280, 'g'), None)], env=(220, 'pan_contact'), dur=g(4, 'min'),
       note='tossed in a hot pan until the kernels pop', issues=[est('a few kernels refuse to pop')])
recipe('taco_seasoning_packet.shape', 'shape', [('taco_seasoning_mix', g(200, 'g'))],
       [('taco_seasoning_packet', g(200, 'g'), None)], dur=g(2, 'min'),
       note='pressed into single-use packets', issues=[est('the wrapper weighs nothing')])
recipe('shortening.chill', 'chill', [('vegetable_oil', g(500, 'ml'))],
       [('shortening', g(480, 'g'), None)], dur=g(4, 'h'),
       note='chilled and worked until it holds a solid white block',
       issues=[est('fractionation fudged as simple chilling')])
recipe('gravy_powder.dry', 'evaporate',
       [('broth_and_stock', g(500, 'ml')), ('wheat_flour', g(200, 'g'))],
       [('gravy_powder', g(250, 'g'), None)], env=(120, 'pan_contact'), dur=g(40, 'min'),
       note='stock and flour dried to a granular instant gravy base')
ing('gravy_powder', 'gravy powder', 'granule')
d = {"id": "smoke_powder.dry", "source": "cookbook_of_babel", "technique": "dry",
     "inputs": {"of": [{"ingredient": "liquid_smoke", "amount": g(100, 'ml')},
                        {"ingredient": "starch", "amount": g(100, 'g')}]},
     "outputs": [{"ingredient": "smoke_powder", "amount": g(150, 'g')}],
     "end": {"by": "time", "duration": g(12, 'h'), "sensitivity": {"refTempC": 20, "q10": 1}},
     "stateNote": "the smoke flavor dried onto a starch carrier",
     "issues": [est('maltodextrin stands in for generic starch')]}
save(R + 'recipes/smoke_powder.dry.json', d)
ing('smoke_powder', 'smoke powder', 'powder')
recipe('powdered_creamer.dry', 'evaporate', [('milk', g(1000, 'ml')), ('white_sugar', g(100, 'g'))],
       [('powdered_creamer', g(150, 'g'), None)], env=(120, 'pan_contact'), dur=g(50, 'min'),
       note='milk dried to a coffee whitener')
recipe('salt_and_pepper.mix', 'mix', [('salt', g(100, 'g')), ('pepper', g(30, 'g'))],
       [('salt_and_black_pepper', g(130, 'g'), None)], dur=g(2, 'min'),
       note='the table shaker blend', issues=[est('about three parts salt to one of pepper')])
recipe('drippings.separate', 'separate', [('chicken_heat', g(900, 'g'))],
       [('drippings', g(80, 'g'), None), ('chicken', g(820, 'g'), 'byproduct')],
       dur=g(2, 'min'), note='the bird lifted out and the pan drippings poured off',
       issues=[est('the chicken goes back for carving, a little drier')])
recipe('lemon_lime_soda_i.mix', 'mix',
       [('lemon', g(150, 'g')), ('lime', g(100, 'g')), ('white_sugar', g(200, 'g')), ('water', g(1000, 'ml'))],
       [('lemon_lime_soda_i', g(1300, 'ml'), None)], dur=g(5, 'min'),
       note='juice, sugar and water stirred and chilled',
       issues=[est('carbonation fudged as plain soda water')])

# ---------- direct mappings ----------
def set_map(fn, ing_id, entry):
    p = MAP + fn + '.json'
    d = json.load(open(p))
    d.setdefault(ing_id, entry)
    save(p, d)

BERRIES = {'items': [{'id': 'minecraft:sweet_berries'}], 'itemTags': ['forge:crops/berries']}
BREAD = {'items': [{'id': 'minecraft:bread'}], 'itemTags': ['forge:bread/wheat']}
def from_target(target):
    for f in glob.glob(MAP + '*.json'):
        d = json.load(open(f))
        if target in d:
            return f.split('/')[-1][:-5], json.loads(json.dumps(d[target]))
    raise KeyError(target)

for leaf, items in [
    ('berries', BERRIES), ('berry', BERRIES),
    ('flatbread', BREAD), ('soft_flatbread', BREAD),
    ('bun', BREAD), ('buns', BREAD), ('dog_buns', BREAD), ('ladi_pav', BREAD),
    ('breakfast_cereal', {'itemTags': ['forge:crops/spiceleaf']}),  # placeholder, recipe above covers it
]:
    pass  # breakfast_cereal gets a recipe; skip the placeholder

food_pair = [
    ('berries', BERRIES), ('berry', BREAD if False else BERRIES),
    ('flatbread', BREAD), ('soft_flatbread', BREAD), ('bun', BREAD), ('buns', BREAD),
    ('dog_buns', BREAD), ('ladi_pav', BREAD),
]
for leaf, e in food_pair:
    set_map('minecraft' if 'minecraft' in str(e) else 'croptopia', leaf, e)

# entries copied from an existing mapped target
copies = [
    ('red_snapper', 'fish'), ('snapper', 'fish'), ('squid', 'fish'),
    ('squid_tentacles', 'fish'), ('periwinkle', 'fish'), ('periwinkle_mollusc', 'fish'),
    ('fava_bean', 'pulses'), ('fava_beans', 'pulses'), ('cowpea', 'pulses'), ('black_eyed_peas', 'pulses'),
    ('cubanelle_pepper', 'bell_pepper'), ('cubanelle_peppers', 'bell_pepper'), ('italian_frying_peppers', 'bell_pepper'),
    ('romano_cheese', 'cheese'), ('pecorino_romano_cheese', 'cheese'),
    ('ham_hock', 'ham'), ('uncooked_ham_hock', 'ham'),
    ('vienna_sausage', 'sausage'), ('vienna_sausages', 'sausage'),
    ('milo_chocolate_drink_mix', 'chocolate'), ('milo_chocolate_drink_powder', 'chocolate'),
    ('hazelnut_oil', 'cooking_oil'), ('toasted_hazelnut_oil', 'cooking_oil'),
    ('spirits', 'rum'), ('liquor', 'rum'),
    ('ogiri', 'fish_sauce'), ('ogiri_okpei', 'fish_sauce'),
]
for leaf, target in copies:
    try:
        fn, e = from_target(target)
        set_map(fn, leaf, e)
    except KeyError:
        print('no mapping for target', target)

# spice-blend leaves onto the evidenced spiceleaf tag, with salt alongside
SPICE = {'items': [{'id': 'croptopia:salt'}], 'itemTags': ['forge:crops/spiceleaf']}
for leaf in ['herbs_and_spices', 'mixed_herbs', 'sweet_herbs', 'pasta_seasoning', 'herb_seasoning',
             'other_seasonings', 'seasoning', 'old_bay', 'old_bay_seasoning',
             'banga_spice', 'banga_spice_mix']:
    set_map('croptopia', leaf, json.loads(json.dumps(SPICE)))

# wood chunks smoke with logs on hand
LOG = {'items': [{'id': 'minecraft:oak_log'}]}
for leaf in ['applewood_chunks', 'dry_applewood_chunks', 'mesquite_wood_chunks',
             'mesquite_wood_chips', 'soaked_mesquite_wood_chips', 'mesquite_wood_chips_soak']:
    set_map('minecraft', leaf, json.loads(json.dumps(LOG)))

# iced water: water's entries plus ice
for f in glob.glob(MAP + '*.json'):
    d = json.load(open(f))
    if 'water' in d:
        e = json.loads(json.dumps(d['water']))
        e.setdefault('items', []).append({'id': 'minecraft:ice'})
        set_map(f.split('/')[-1][:-5], 'ice_water', e)
        set_map(f.split('/')[-1][:-5], 'iced_water', json.loads(json.dumps(e)))
        break

# ---------- plant mint ----------
d = json.load(open(GARD))
d.setdefault('mint', ['shaded', 'soggy'])
with open(GARD, 'w') as f:
    f.write('{\n' + ',\n'.join(f'  "{k}": {json.dumps(v)}' for k, v in d.items()) + '\n}\n')

print('knock-out applied')
