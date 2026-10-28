import sys

sys.path.insert(0, 'scratchpad/fix_a')
import sim

HEXEREI = sim.HEXEREI


def mk(ing, qty, unit):
    return {'ingredient': ing, 'amount': {'qty': qty, 'unit': unit}}


print('--- pork_pot_pie hexerei milk wash, out <= in')
best = []
for pastry in [340, 380, 400, 420, 440, 460]:
    for pork in [255, 285, 300, 330, 345]:
        for carrot in [85, 100, 115]:
            for milkml in [150, 200, 250, 300]:
                ins = [mk('pastry_dough', pastry, 'g'), mk('pork', pork, 'g'), mk('carrot', carrot, 'g'), mk('milk', milkml, 'ml')]
                gin = sum(sim.gramsOf(x['ingredient'], x['amount']) for x in ins)
                for outq in [800, 900, 1000, 1050, 1100]:
                    if outq > gin:
                        continue
                    r = sim.plan(HEXEREI, ins, [mk('unbaked_pork_pot_pie', outq, 'g')])
                    if r[0] == 'planned':
                        best.append((r[1]['maxError'], pastry, pork, carrot, milkml, outq, gin))
best.sort()
for b in best[:10]:
    print(f"  err {b[0]*100:.1f}%: pastry {b[1]} pork {b[2]} carrot {b[3]} milk {b[4]}ml -> {b[5]}g (in {b[6]:.0f}g)")
