import sys

sys.path.insert(0, 'scratchpad/fix_a')
import sim

SHAPELESS = sim.SHAPELESS
DEPLOYING = sim.Route('create_deploying', 2, 1, 2, 2, 0, 4, 0, 4)
HEXEREI = sim.HEXEREI
BOTTLER = sim.Route('thermal_bottler', 1, 1, 1, 1, 1, 1, 0, 1, minFluidInputs=1, nbt='on_results')
ROUTES = [SHAPELESS, DEPLOYING, HEXEREI, BOTTLER]


def show(step_id, ins=None, outs=None, routes=ROUTES):
    r = sim.get(step_id)
    i = ins if ins is not None else r['inputs']['of']
    o = outs if outs is not None else r['outputs']
    print('===', step_id)
    for route in routes:
        res = sim.plan(route, i, o)
        if res[0] == 'planned':
            q = res[1]
            print(f"  {route.name}: PLANNED {q['maxError']*100:.1f}% factor={q['factor']:.4f} in={q['inputs']} out={q['outputs']}")
        else:
            print(f"  {route.name}: {res[1]}")


if __name__ == '__main__':
    for t in ['pork_pot_pie.assemble', 'sausage.stuff', 'stuffed_peppers.assemble',
              'fried_chicken.coat', 'fried_green_tomatoes.coat', 'lamington.coat']:
        show(t)
