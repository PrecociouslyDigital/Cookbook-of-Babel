import sys

sys.path.insert(0, 'scratchpad/fix_a')
import sim

HEXEREI = sim.HEXEREI


def mk(ing, qty, unit):
    return {'ingredient': ing, 'amount': {'qty': qty, 'unit': unit}}


print('--- pork_pot_pie hexerei: inputs within +-15%, out within -15%..0 of 1100 and <= in')
best = []
for pastry in [340, 360, 380, 400, 420, 440, 460]:
    for pork in [255, 270, 285, 300, 315, 330, 345]:
        for carrot in [85, 100, 115]:
            for fluid, fq in [('milk', 150), ('milk', 200), ('milk', 250), ('milk', 300), ('milk', 350), ('water', 200), ('water', 250), ('water', 300), ('water', 350)]:
                ins = [mk('pastry_dough', pastry, 'g'), mk('pork', pork, 'g'), mk('carrot', carrot, 'g'), mk(fluid, fq, 'ml')]
                gin = sum(sim.gramsOf(x['ingredient'], x['amount']) for x in ins)
                for outq in [935, 950, 1000, 1050, 1100]:
                    if outq > gin:
                        continue
                    r = sim.plan(HEXEREI, ins, [mk('unbaked_pork_pot_pie', outq, 'g')])
                    if r[0] == 'planned':
                        best.append((r[1]['maxError'], pastry, pork, carrot, fluid, fq, outq, gin))
best.sort()
seen = set()
for b in best[:15]:
    key = (b[1], b[2], b[3], b[4], b[5], b[6])
    if key in seen:
        continue
    seen.add(key)
    print(f"  err {b[0]*100:.1f}%: pastry {b[1]} pork {b[2]} carrot {b[3]} {b[4]} {b[5]}ml -> {b[6]}g (in {b[7]:.0f}g)")
