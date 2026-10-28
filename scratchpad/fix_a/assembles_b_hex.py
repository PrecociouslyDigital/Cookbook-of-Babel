import sys

sys.path.insert(0, 'scratchpad/fix_a')
import sim

SHAPELESS = sim.SHAPELESS
HEXEREI = sim.HEXEREI


def mk(ing, qty, unit):
    return {'ingredient': ing, 'amount': {'qty': qty, 'unit': unit}}


print('--- pork_pot_pie hexerei (with water fluid)')
best = []
for pastry in [340, 380, 400, 440, 460]:
    for pork in [255, 285, 300, 330, 345]:
        for extra in ['none', 'carrot', 'onion', 'gravy', 'carrot+onion', 'carrot+gravy', 'onion+gravy', 'carrot+onion+gravy']:
            for water in [150, 200, 250]:
                ins = [mk('pastry_dough', pastry, 'g'), mk('pork', pork, 'g'), mk('water', water, 'ml')]
                amounts = {'carrot': (85, 100, 115), 'onion': (85, 100, 115), 'gravy': (170, 200, 230)}
                for name in extra.split('+'):
                    if name == 'none':
                        continue
                    lo, mid, hi = amounts[name]
                    ins.append(mk(name, hi, 'g'))
                if extra == 'none' and water != 150:
                    continue
                outs = [mk('unbaked_pork_pot_pie', 1100, 'g')]
                gin = sum(sim.gramsOf(x['ingredient'], x['amount']) for x in ins)
                if gin < 1100:
                    continue
                r = sim.plan(HEXEREI, ins, outs)
                if r[0] == 'planned':
                    best.append((r[1]['maxError'], pastry, pork, extra, water))
best.sort()
for b in best[:6]:
    print(f"  err {b[0]*100:.1f}%: pastry {b[1]} pork {b[2]} +{b[3]} water {b[4]}ml")

print('--- stuffed_peppers hexerei (with water fluid)')
best = []
for bp in [510, 550, 600, 650, 690]:
    for rice in [170, 200, 230]:
        for pork in [130, 150, 170]:
            for water in [150, 200, 250]:
                ins = [mk('bell_pepper', bp, 'g'), mk('rice', rice, 'g'), mk('pork', pork, 'g'), mk('salt', 5, 'g'), mk('water', water, 'ml')]
                for outq in [900, 1000, 1055]:
                    r = sim.plan(HEXEREI, ins, [mk('unbaked_stuffed_peppers', outq, 'g')])
                    if r[0] == 'planned':
                        best.append((r[1]['maxError'], bp, rice, pork, water, outq))
best.sort()
for b in best[:6]:
    print(f"  err {b[0]*100:.1f}%: pepper {b[1]} rice {b[2]} pork {b[3]} water {b[4]}ml -> {b[5]}g")

print('--- sausage variants')
for meat in [850, 900, 950, 1000, 1050, 1150]:
    for outq in [850, 900, 950, 1000, 1050, 1150]:
        if outq > meat:
            continue
        r = sim.plan(SHAPELESS, [mk('sausage_meat', meat, 'g')], [mk('sausage', outq, 'g')])
        if r[0] == 'planned':
            print(f"  err {r[1]['maxError']*100:.1f}%: meat {meat} -> {outq}g")

print('--- lamington keep-coconut sanity (expected fail)')
r = sim.plan(SHAPELESS, [mk('cake', 500, 'g'), mk('chocolate_coating', 250, 'g'), mk('coconut', 120, 'g')], [mk('lamington', 800, 'g')])
print('  ', r[0], r[1] if r[0] != 'planned' else r[1]['maxError'])
