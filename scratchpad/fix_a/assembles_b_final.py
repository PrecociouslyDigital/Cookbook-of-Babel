import sys

sys.path.insert(0, 'scratchpad/fix_a')
import sim

SHAPELESS = sim.SHAPELESS
HEXEREI = sim.HEXEREI


def mk(ing, qty, unit):
    return {'ingredient': ing, 'amount': {'qty': qty, 'unit': unit}}


print('--- fried_green_tomatoes hexerei, mass-constrained (out <= in)')
best = []
for tomato in [425, 450, 475, 500, 525, 550, 575]:
    for egg in [43, 45, 47, 50, 55, 57]:
        for cmeal in [153, 165, 180, 195, 207]:
            for milkml in [100, 105, 120, 135]:
                ins = [mk('tomato', tomato, 'g'), mk('egg', egg, 'g'), mk('cornmeal', cmeal, 'g'), mk('salt', 4, 'g'), mk('milk', milkml, 'ml')]
                gin = sum(sim.gramsOf(x['ingredient'], x['amount']) for x in ins)
                for outq in [700, 750, 800, 820]:
                    if outq > gin:
                        continue
                    r = sim.plan(HEXEREI, ins, [mk('breaded_green_tomato_slices', outq, 'g')])
                    if r[0] == 'planned':
                        best.append((r[1]['maxError'], tomato, egg, cmeal, milkml, outq, gin))
best.sort()
for b in best[:10]:
    print(f"  err {b[0]*100:.1f}%: tomato {b[1]} egg {b[2]} cornmeal {b[3]} milk {b[4]}ml -> {b[5]}g (in {b[6]:.0f}g)")

print('--- pork_pot_pie hexerei with milk wash (pastry,pork,carrot,milk)')
best = []
for pastry in [380, 400, 420, 440, 460]:
    for pork in [255, 285, 300, 330, 345]:
        for carrot in [85, 100, 115]:
            for milkml in [150, 200, 250]:
                ins = [mk('pastry_dough', pastry, 'g'), mk('pork', pork, 'g'), mk('carrot', carrot, 'g'), mk('milk', milkml, 'ml')]
                gin = sum(sim.gramsOf(x['ingredient'], x['amount']) for x in ins)
                r = sim.plan(HEXEREI, ins, [mk('unbaked_pork_pot_pie', 1100, 'g')])
                if r[0] == 'planned':
                    best.append((r[1]['maxError'], pastry, pork, carrot, milkml, gin))
best.sort()
for b in best[:6]:
    print(f"  err {b[0]*100:.1f}%: pastry {b[1]} pork {b[2]} carrot {b[3]} milk {b[4]}ml (in {b[5]:.0f}g)")

print('--- stuffed_peppers shapeless, wider grid with mass constraint')
best = []
for bp in [510, 520, 550, 570, 600, 630, 650, 690]:
    for rice in [170, 180, 190, 200, 210, 230]:
        for pork in [128, 140, 150, 160, 170]:
            for tomato in [0, 100]:
                ins = [mk('bell_pepper', bp, 'g'), mk('rice', rice, 'g'), mk('pork', pork, 'g'), mk('salt', 5, 'g')]
                if tomato:
                    ins.append(mk('tomato', 100, 'g'))
                gin = sum(sim.gramsOf(x['ingredient'], x['amount']) for x in ins)
                for outq in [897, 950, 1000, 1055, 1100]:
                    if outq > gin:
                        continue
                    r = sim.plan(SHAPELESS, ins, [mk('unbaked_stuffed_peppers', outq, 'g')])
                    if r[0] == 'planned':
                        best.append((r[1]['maxError'], bp, rice, pork, tomato, outq))
best.sort()
for b in best[:8]:
    print(f"  err {b[0]*100:.1f}%: pepper {b[1]} rice {b[2]} pork {b[3]} tomato {b[4]} -> {b[5]}g")

