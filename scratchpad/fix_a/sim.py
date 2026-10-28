import json, math
from itertools import combinations

DATA = json.load(open('scratchpad/fix_a/all_data.json'))
ING = DATA['ingredients']; MEAS = DATA['measures']
PROV_I = set(DATA['provided_items']); PROV_F = set(DATA['provided_fluids'])
NAMED = set(DATA['named']); INTERMED = set(DATA['intermediates'])
UNITS = {u['id']: u for u in json.load(open('data/registries/units.json'))}

FORM_GPI = {'whole':150,'solid':400,'piece':50,'slice':30,'shred':25,'dice':25,'paste':50,'curd':50,
            'mash':100,'gel':100,'liquid':100,'batter':100,'foam':100,'powder':100,'granule':100,'gas':100}

def form(id): return ING[id]['form']
def gpi(id): return MEAS[id] if id in MEAS else FORM_GPI[form(id)]
def carrier(id):
    f = form(id)
    if f in ('liquid','batter','foam','gas'): c = 'bottle'
    elif f in ('powder','granule'): c = 'paper_twist'
    else: c = None
    if c and id in INTERMED: c = 'bowl'
    return c
def container(id): return None if id in NAMED else carrier(id)

def grams(id, amt):
    u = UNITS[amt['unit']]; base = amt['qty']*u['toBase']
    if u['dimension']=='mass': return base, False
    if u['dimension']=='volume':
        d = ING[id].get('densityGPerMl')
        return base*(d if d else 1.0), d is None
    if u['dimension']=='count':
        return base*gpi(id), id not in MEAS
    raise Exception('time unit')

def mb(id, amt):
    per = 1000.0 if form(id)=='gas' else 1.0
    u = UNITS[amt['unit']]
    if u['dimension']=='volume': return amt['qty']*u['toBase']/per, False
    g, est = grams(id, amt)
    d = ING[id].get('densityGPerMl')
    return g/(d if d else 1.0)/per, est or (d is None)

def items(id, amt):
    if container(id):
        v, e = mb(id, amt); return v/250.0, e
    u = UNITS[amt['unit']]
    if u['dimension']=='count': return amt['qty']*u['toBase'], False
    g, e = grams(id, amt); return g/gpi(id), e

def gramsOf(id, amt): return grams(id, amt)[0]

# ---- Quantize ----
FACTORS = [1/32,1/24,1/16,1/12,1/10,1/8,1/6,1/4,1/3,1/2,2/3,1,3/2,2,3,4,5,6,8,10,12,16]
SMALLEST = FACTORS[0]; MAX_STACK = 64; TANK = 1000

def jround(x): return math.floor(x+0.5)  # Java Math.round

def quantize(ins, outs, maxItemInputs, maxOfEachItem, tank=TANK):
    vals = list(FACTORS)
    for a in ins:
        lim = maxOfEachItem if a['kind']=='items' else tank
        if not a['neg'] and a['value']>0:
            vals += [n/a['value'] for n in range(1, lim+1)]
    for a in outs:
        lim = MAX_STACK if a['kind']=='items' else tank
        if not a['neg'] and a['value']>0:
            vals += [n/a['value'] for n in range(1, lim+1)]
    factors = sorted(set(f for f in vals if f >= SMALLEST))
    def fits(qi, qo):
        n = 0
        for i, a in enumerate(ins):
            lim = maxOfEachItem if a['kind']=='items' else tank
            if qi[i] > lim: return False
            if a['kind']=='items': n += qi[i]
        for i, a in enumerate(outs):
            lim = MAX_STACK if a['kind']=='items' else tank
            if qo[i] > lim: return False
        return n <= maxItemInputs
    def at(f):
        errs = []
        qi = []
        for a in ins:
            s = a['value']*f
            w = min(2147483647, max(1, jround(s)))
            if not a['neg']: errs.append(abs(w-s)/s)
            qi.append(w)
        qo = []
        for a in outs:
            s = a['value']*f
            w = min(2147483647, max(1, jround(s)))
            if not a['neg']: errs.append(abs(w-s)/s)
            qo.append(w)
        return qi, qo, errs
    best = None
    for f in factors:
        qi, qo, errs = at(f)
        if not fits(qi, qo): break
        me = max(errs) if errs else 0.0
        if me <= 0.15: return {'factor': f, 'inputs': qi, 'outputs': qo, 'maxError': me}
        if best is None or me < best['maxError']: best = {'factor': f, 'inputs': qi, 'outputs': qo, 'maxError': me}
    return best

class Route:
    def __init__(self, name, maxItemInputs, minItemKinds, itemKinds, maxOfEachItem,
                 maxFluidInputs, maxItemOutputs, maxFluidOutputs, maxOutputs,
                 minFluidInputs=0, nbt='kept', supplies=None):
        self.name=name
        self.c=dict(maxItemInputs=maxItemInputs, minItemKinds=minItemKinds, itemKinds=itemKinds,
                    maxOfEachItem=maxOfEachItem, minFluidInputs=minFluidInputs,
                    maxFluidInputs=maxFluidInputs, maxItemOutputs=maxItemOutputs,
                    maxFluidOutputs=maxFluidOutputs, maxOutputs=maxOutputs)
        self.nbt=nbt; self.supplies=supplies

def mk(short):  # short ctor: (maxItemInputs, maxFluidInputs, maxItemOutputs, maxFluidOutputs, chanceOutputs, minTicks, maxTicks)
    mi, mf, mo, mfo, ch, _, _ = short
    return Route('x', mi, 1, mi, mi, mf, mo, mfo, mo+mfo, 0, 'kept' if ch else 'ignored')
# hand-build the specific routes we need:
CREATE_MIXING = Route('create_mixing', 64, 0, 64, 64, 2, 4, 2, 6)
CREATE_HEATED = Route('create_heated_mixing', 64, 0, 64, 64, 2, 4, 2, 6)
SHAPELESS = Route('shapeless_crafting', 9, 1, 9, 9, 0, 1, 0, 1)
HEXEREI = Route('hexerei_mixing_cauldron', 8, 1, 8, 8, 1, 1, 0, 1, minFluidInputs=1, nbt='on_results')
ITEM_DRAIN = Route('create_item_drain', 1, 1, 1, 1, 0, 1, 1, 2)
CENTRIFUGE = Route('thermal_centrifuge', 1, 1, 1, 1, 1, 4, 1, 5)
SPLASHING = Route('create_splashing', 1, 1, 1, 1, 0, 12, 0, 12, supplies='water')

TECH_ROUTES = {
 'mix': [CREATE_MIXING, SHAPELESS, HEXEREI],
 'blend': [CREATE_MIXING],
 'carbonate': [CREATE_MIXING],
 'evaporate': [CREATE_HEATED],
 'separate': [ITEM_DRAIN, CREATE_MIXING, CENTRIFUGE],
 'soak': [CREATE_MIXING, SPLASHING],
}

def fluids_allowed(use, route):
    fl=[]
    for i in use:
        id = i['ingredient']
        if container(id) and (route.nbt=='kept' or id in PROV_F) and id not in fl: fl.append(id)
    return fl

def plan(route, ins, outs):
    c = route.c
    use = [i for i in ins if not (route.supplies and i['ingredient']==route.supplies)]
    if not use: return ('rejected', 'takes nothing besides the supplied')
    products=[o for o in outs if o.get('role')!='byproduct']
    byproducts=[o for o in outs if o.get('role')=='byproduct']
    conv=[]; fluidOuts=set(); rejected=None
    for o in products+byproducts:
        ing=o['ingredient']
        onlyAnItem = ing in PROV_I and ing not in PROV_F
        asFluid = container(ing) and len(fluidOuts)<c['maxFluidOutputs'] and not onlyAnItem and (route.nbt!='ignored' or ing in PROV_F)
        asItem = (len(conv)-len(fluidOuts))<c['maxItemOutputs'] and (route.nbt!='ignored' or ing in PROV_I)
        if (asFluid or asItem) and len(conv)<c['maxOutputs']:
            conv.append(o)
            if asFluid: fluidOuts.add(ing)
        elif o.get('role')=='byproduct': pass
        elif not (container(ing) and (route.nbt!='ignored' or ing in PROV_F)) and ing not in PROV_I:
            return ('rejected', f"can't make the mod's own {ing}")
        else:
            return ('rejected', f"makes {len(products)} things; at most {c['maxOutputs']} fit")
    # shape problem
    if route.name=='create_item_drain':
        if not (len(use)==1 and len(conv)==2 and len(fluidOuts)==1):
            return ('rejected','the item drain takes one item and yields one item and one fluid')
    if route.name=='hexerei_mixing_cauldron':
        if len(conv)!=1 or fluidOuts: return ('rejected','the mixing cauldron makes exactly one item')
    most = c['itemKinds']+c['maxFluidInputs']
    if most==1 and len(use)!=1: return ('rejected', f"takes one input, not {len(use)}")
    if most < len(use): return ('rejected', f"takes at most {most} inputs, not {len(use)}")
    outTotal = sum(gramsOf(o['ingredient'], o['amount']) for o in conv)
    exouts=[]
    for o in conv:
        ing=o['ingredient']
        if ing in fluidOuts: v,k = mb(ing,o['amount'])[0], 'mB'
        else: v,k = items(ing,o['amount'])[0], 'items'
        exouts.append(dict(value=v, kind=k, neg=gramsOf(ing,o['amount'])<0.02*outTotal))
    inTotal = sum(gramsOf(i['ingredient'], i['amount']) for i in use)
    fl = fluids_allowed(use, route)
    best=None; bestE=float('inf')
    for size in range(c['minFluidInputs'], min(c['maxFluidInputs'], len(fl))+1):
        for sub in combinations(fl, size):
            fs=set(sub)
            itemLeaves = [i for i in use if i['ingredient'] not in fs]
            if len(itemLeaves)<c['minItemKinds'] or len(itemLeaves)>c['itemKinds']: continue
            ex=[]
            for i in use:
                if i['ingredient'] in fs: v,k = mb(i['ingredient'], i['amount'])[0], 'mB'
                else: v,k = items(i['ingredient'], i['amount'])[0], 'items'
                ex.append(dict(value=v, kind=k, neg=gramsOf(i['ingredient'], i['amount'])<0.02*inTotal))
            q = quantize(ex, exouts, c['maxItemInputs'], c['maxOfEachItem'])
            if q and q['maxError']<bestE: bestE=q['maxError']; best=q
    if best is None:
        return ('rejected', 'needs a fluid it can take, and has none' if len(fl)<c['minFluidInputs'] else "doesn't fit at any scale")
    if best['maxError']>1.0:
        return ('rejected', f"whole items would distort it by {round(best['maxError']*100)}%")
    return ('planned', best)

REC='data/layers/reviewed/LicenseRef-original/original/recipes'
def get(id):
    return json.load(open(f'{REC}/{id}.json'))

TARGETS=['cajun_seasoning.mix','creamy_russian_dressing.mix','general_tso_s_chicken.mix_sauce','hummus.mix',
 'italian_dressing.mix','taco_seasoning_mix.mix','berbere_spice.grind','carbonated_water.carbonate',
 'salt.evaporate','niter_kibbeh.separate','semolina.grind','bulgogi.marinate']


def plan_all(tech, ins, outs, routes=None):
    res={}
    for r in (routes or TECH_ROUTES[tech]):
        res[r.name]=plan(r, ins, outs)
    return res

if __name__=='__main__':
    import sys
    for t in TARGETS:
        r=get(t); res=plan_all(r['technique'], r['inputs']['of'], r['outputs'])
        print('===', t)
        for k,v in res.items():
            if v[0]=='planned': print(f'  {k}: PLANNED {v[1]["maxError"]*100:.0f}%')
            else: print(f'  {k}: {v[1]}')
