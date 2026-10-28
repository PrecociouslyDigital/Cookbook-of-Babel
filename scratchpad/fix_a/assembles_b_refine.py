import sys

sys.path.insert(0, 'scratchpad/fix_a')
import sim

SHAPELESS = sim.SHAPELESS
HEXEREI = sim.HEXEREI


def mk(ing, qty, unit):
    return {'ingredient': ing, 'amount': {'qty': qty, 'unit': unit}}


def detail(ins, outs, route=SHAPELESS):
    res = sim.plan(route, ins, outs)
    if res[0] != 'planned':
        print('   ', route.name, res[1])
        return
    q = res[1]
    print(f"    {route.name}: err {q['maxError']*100:.1f}% f={q['factor']:.4f} in={q['inputs']} out={q['outputs']}")
    # per-amount errors at chosen factor
    f = q['factor']
    inTotal = sum(sim.gramsOf(x['ingredient'], x['amount']) for x in ins)
    outTotal = sum(sim.gramsOf(x['ingredient'], x['amount']) for x in outs)
    for x in ins + outs:
        g = sim.gramsOf(x['ingredient'], x['amount'])
        v = sim.items(x['ingredient'], x['amount'])[0]
        neg = g < 0.02 * (inTotal if x in ins else outTotal)
        s = v * f
        print(f"      {x['ingredient']:28s} {g:7.1f}g {v:6.2f} items x{f:.3f} = {s:6.3f} {'NEG' if neg else ''}")


print('--- stuffed_peppers debug')
ins = [mk('bell_pepper', 600, 'g'), mk('rice', 170, 'g'), mk('pork', 150, 'g'), mk('salt', 5, 'g')]
outs = [mk('unbaked_stuffed_peppers', 1055, 'g')]
detail(ins, outs)

print('--- stuffed_peppers variants')
best = []
for rice in [150, 160, 170, 180, 190, 200, 210, 220, 230]:
    for pork in [130, 140, 150, 160, 170]:
        for bp in [510, 550, 600, 650, 690]:
            for outq in [900, 950, 1000, 1055]:
                gin = bp + rice + pork + 5
                if outq > gin:
                    continue
                i2 = [mk('bell_pepper', bp, 'g'), mk('rice', rice, 'g'), mk('pork', pork, 'g'), mk('salt', 5, 'g')]
                r = sim.plan(SHAPELESS, i2, [mk('unbaked_stuffed_peppers', outq, 'g')])
                if r[0] == 'planned':
                    best.append((r[1]['maxError'], bp, rice, pork, outq))
best.sort()
for b in best[:8]:
    print(f"  err {b[0]*100:.1f}%: pepper {b[1]} rice {b[2]} pork {b[3]} -> {b[4]}g")

print('--- pork_pot_pie variants (pastry,pork,gravy)')
best = []
for pastry in [340, 360, 380, 400, 420, 440, 460]:
    for pork in [255, 270, 285, 300, 315, 330, 345]:
        for gravy in [170, 180, 190, 200, 210, 220, 230]:
            for outq in [700, 750, 800, 850, 900, 950, 1000, 1050, 1100]:
                gin = pastry + pork + gravy
                if outq > gin:
                    continue
                i2 = [mk('pastry_dough', pastry, 'g'), mk('pork', pork, 'g'), mk('gravy', gravy, 'g')]
                r = sim.plan(SHAPELESS, i2, [mk('unbaked_pork_pot_pie', outq, 'g')])
                if r[0] == 'planned':
                    best.append((r[1]['maxError'], pastry, pork, gravy, outq))
best.sort()
for b in best[:8]:
    print(f"  err {b[0]*100:.1f}%: pastry {b[1]} pork {b[2]} gravy {b[3]} -> {b[4]}g")

print('--- fried_green_tomatoes hexerei variants')
best = []
for tomato in [425, 450, 475, 500, 525, 550, 575]:
    for egg in [43, 45, 47, 50, 53, 55, 57]:
        for cmeal in [153, 165, 180, 195, 207]:
            for milkml in [105, 120, 135]:
                i2 = [mk('tomato', tomato, 'g'), mk('egg', egg, 'g'), mk('cornmeal', cmeal, 'g'), mk('salt', 4, 'g'), mk('milk', milkml, 'ml')]
                r = sim.plan(HEXEREI, i2, [mk('breaded_green_tomato_slices', 820, 'g')])
                if r[0] == 'planned':
                    best.append((r[1]['maxError'], tomato, egg, cmeal, milkml))
best.sort()
for b in best[:8]:
    print(f"  err {b[0]*100:.1f}%: tomato {b[1]} egg {b[2]} cornmeal {b[3]} milk {b[4]}ml")
