import sys

sys.path.insert(0, 'scratchpad/fix_a')
import sim

SHAPELESS = sim.SHAPELESS
HEXEREI = sim.HEXEREI
ROUTES = [SHAPELESS, HEXEREI]


def mk(ing, qty, unit):
    return {'ingredient': ing, 'amount': {'qty': qty, 'unit': unit}}


def show(name, ins, outs, routes=ROUTES):
    print('===', name)
    for r in routes:
        res = sim.plan(r, ins, outs)
        if res[0] == 'planned':
            q = res[1]
            print(f"  {r.name}: PLANNED {q['maxError']*100:.1f}% factor={q['factor']:.4f} in={q['inputs']} out={q['outputs']}")
        else:
            print(f"  {r.name}: {res[1]}")


G = sim.gramsOf
for name, ins, outs in [
    ('pork_pot_pie A: 340/255/230->825',
     [mk('pastry_dough', 340, 'g'), mk('pork', 255, 'g'), mk('gravy', 230, 'g')],
     [mk('unbaked_pork_pot_pie', 825, 'g')]),
    ('pork_pot_pie B: 400/255/230->885',
     [mk('pastry_dough', 400, 'g'), mk('pork', 255, 'g'), mk('gravy', 230, 'g')],
     [mk('unbaked_pork_pot_pie', 885, 'g')]),
    ('pork_pot_pie C: 460/255/230->945',
     [mk('pastry_dough', 460, 'g'), mk('pork', 255, 'g'), mk('gravy', 230, 'g')],
     [mk('unbaked_pork_pot_pie', 945, 'g')]),
    ('pork_pot_pie D: 340/300/230->870',
     [mk('pastry_dough', 340, 'g'), mk('pork', 300, 'g'), mk('gravy', 230, 'g')],
     [mk('unbaked_pork_pot_pie', 870, 'g')]),
    ('pork_pot_pie E: 400/300/230->930',
     [mk('pastry_dough', 400, 'g'), mk('pork', 300, 'g'), mk('gravy', 230, 'g')],
     [mk('unbaked_pork_pot_pie', 930, 'g')]),
    ('pork_pot_pie F: 340/255/200->795',
     [mk('pastry_dough', 340, 'g'), mk('pork', 255, 'g'), mk('gravy', 200, 'g')],
     [mk('unbaked_pork_pot_pie', 795, 'g')]),
    ('sausage: meat1000->1000',
     [mk('sausage_meat', 1000, 'g')], [mk('sausage', 1000, 'g')]),
    ('sausage: meat1050->1050',
     [mk('sausage_meat', 1050, 'g')], [mk('sausage', 1050, 'g')]),
    ('stuffed_peppers: 600/170/150/5->1055',
     [mk('bell_pepper', 600, 'g'), mk('rice', 170, 'g'), mk('pork', 150, 'g'), mk('salt', 5, 'g')],
     [mk('unbaked_stuffed_peppers', 1055, 'g')]),
    ('stuffed_peppers: 600/170/150/5->1000',
     [mk('bell_pepper', 600, 'g'), mk('rice', 170, 'g'), mk('pork', 150, 'g'), mk('salt', 5, 'g')],
     [mk('unbaked_stuffed_peppers', 1000, 'g')]),
    ('stuffed_peppers: 600/175/150/5->1055',
     [mk('bell_pepper', 600, 'g'), mk('rice', 175, 'g'), mk('pork', 150, 'g'), mk('salt', 5, 'g')],
     [mk('unbaked_stuffed_peppers', 1055, 'g')]),
    ('fried_chicken: orig amounts, flour dropped',
     [mk('chicken_cut', 700, 'g'), mk('egg', 2, 'piece'), mk('salt', 6, 'g'), mk('pepper', 3, 'g')],
     [mk('breaded_chicken', 1360, 'g')]),
    ('fried_chicken: egg 2.2',
     [mk('chicken_cut', 700, 'g'), mk('egg', 2.2, 'piece'), mk('salt', 6, 'g'), mk('pepper', 3, 'g')],
     [mk('breaded_chicken', 1360, 'g')]),
    ('fried_green_tomatoes hexerei: milk, cornmeal180, egg45',
     [mk('tomato', 500, 'g'), mk('egg', 45, 'g'), mk('cornmeal', 180, 'g'), mk('salt', 4, 'g'), mk('milk', 120, 'ml')],
     [mk('breaded_green_tomato_slices', 820, 'g')]),
    ('fried_green_tomatoes hexerei: milk, cornmeal180, egg50',
     [mk('tomato', 500, 'g'), mk('egg', 50, 'g'), mk('cornmeal', 180, 'g'), mk('salt', 4, 'g'), mk('milk', 120, 'ml')],
     [mk('breaded_green_tomato_slices', 820, 'g')]),
    ('lamington: cake+choc only',
     [mk('cake', 500, 'g'), mk('chocolate_coating', 250, 'g')],
     [mk('lamington', 800, 'g')]),
]:
    show(name, ins, outs)
