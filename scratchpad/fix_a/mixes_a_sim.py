import json, glob, math
from itertools import combinations

FACTORS = [1/32,1/24,1/16,1/12,1/10,1/8,1/6,1/4,1/3,1/2,2/3,1.0,1.5,2.0,3.0,4.0,5.0,6.0,8.0,10.0,12.0,16.0]
SMALLEST = 1/32
TOL = 0.15
MAX_STACK = 64
TANK_MB = 1000
MAX_ERROR = 1.0
NEGL = 0.02

def gpi(form, gpp=None):
    if gpp is not None: return gpp
    return {"whole":150,"solid":400,"piece":50,"paste":50,"curd":50,"slice":30,"shred":25,"dice":25,
            "mash":100,"gel":100,"liquid":100,"batter":100,"foam":100,"powder":100,"granule":100,"gas":100}[form]

def flowable(form): return form in ("liquid","batter","foam","gas","powder","granule")

# ingredient: (form, density or None, gramsPerPiece or None)
ING = {
 "paprika":("powder",None,None), "garlic_powder":("powder",None,None), "onion_powder":("powder",None,None),
 "cayenne_pepper":("powder",None,None), "pepper":("granule",None,None), "oregano":("powder",None,None),
 "thyme":("whole",None,None), "salt":("granule",1.2,None), "cajun_seasoning":("powder",None,None),
 "mayonnaise":("paste",None,None), "ketchup":("paste",None,None), "horseradish":("whole",None,None),
 "onion":("whole",None,110), "creamy_russian_dressing":("liquid",None,None),
 "soy_sauce":("liquid",None,None), "white_sugar":("granule",0.85,None), "rice_vinegar":("liquid",None,None),
 "starch":("powder",0.55,None), "garlic":("whole",None,None), "ginger":("whole",None,None),
 "chile_flakes":("granule",None,None), "water":("liquid",1,None), "general_tso_s_sauce":("liquid",None,None),
 "chickpea":("whole",None,None), "tahini":("paste",None,None), "olive_oil":("liquid",None,None),
 "hummus":("paste",None,None),
}

def grams(i, amt):
    qty, unit = amt
    base = qty * {"g":1,"ml":1,"piece":1}[unit]
    form, dens, gpp = ING[i]
    if unit=="g": return base
    if unit=="ml": return base * (dens if dens is not None else 1.0)
    return base * gpi(form, gpp)

def mls(i, amt):
    qty, unit = amt
    if unit=="ml": return qty  # perMB=1
    return grams(i, amt) / (ING[i][1] if ING[i][1] is not None else 1.0)

def items_of(i, amt):
    qty, unit = amt
    form = ING[i][0]
    if flowable(form): return mls(i, amt) / 250.0
    if unit=="piece": return qty
    return grams(i, amt) / gpi(form, ING[i][2])

def mb_of(i, amt):
    return mls(i, amt)

def jr(x): return math.floor(x+0.5)  # java round

def quantize(ins, outs, maxItemInputs, maxOfEachItem):
    # ins: list of (value, kind) kind 'I' items or 'F' mB; negligible flags separate
    limits_in = {"I":maxOfEachItem, "F":TANK_MB}
    limits_out = {"I":MAX_STACK, "F":TANK_MB}
    factors = set(FACTORS)
    for vals, lims in ((ins,limits_in),(outs,limits_out)):
        for (v,kind),neg in vals:
            if neg or v<=0: continue
            for n in range(1, lims[kind]+1):
                f = n/v
                if f >= SMALLEST: factors.add(f)
    best=None
    for f in sorted(factors):
        counts=[]; errors=[]
        for lst in (ins,outs):
            for (v,kind),neg in lst:
                scaled=v*f
                w=min(2**31-1, max(1, jr(scaled)))
                if not neg: errors.append(abs(w-scaled)/scaled)
                counts.append((w,kind))
        in_counts = counts[:len(ins)]; out_counts=counts[len(ins):]
        total_items = sum(w for (w,k) in in_counts if k=='I')
        ok=True
        for (w,k),(v,_) in zip(in_counts,ins):
            if w > limits_in[k]: ok=False; break
        if ok:
            for (w,k) in out_counts:
                if w > limits_out[k]: ok=False; break
        if ok and total_items > maxItemInputs: ok=False
        if not ok: break
        me = max(errors) if errors else 0.0
        cand=(f,me,[w for w,k in in_counts],[w for w,k in out_counts])
        if me <= TOL: return cand
        if best is None or me < best[1]: best=cand
    return best

def plan(name, ins, outs, route):
    # ins: list of (ing, (qty,unit)); outs: list of (ing,(qty,unit))
    CF = {"create":dict(maxItemInputs=64,maxOfEachItem=64,maxFluidIn=2,maxItemOut=4,maxFluidOut=2,minFluidIn=0,minItemKinds=0,itemKinds=64,nbt=True),
          "shapeless":dict(maxItemInputs=9,maxOfEachItem=9,maxFluidIn=0,maxItemOut=1,maxFluidOut=0,minFluidIn=0,minItemKinds=1,itemKinds=9,nbt=True),
          "hexerei":dict(maxItemInputs=8,maxOfEachItem=8,maxFluidIn=1,maxItemOut=1,maxFluidOut=0,minFluidIn=1,minItemKinds=1,itemKinds=8,nbt=True)}[route]
    if route=="hexerei" and len(outs)!=1: return "shape: exactly one item"
    ingram = sum(grams(i,a) for i,a in ins)
    outgram = sum(grams(i,a) for i,a in outs)
    provided_fluids = {"water"}
    if route=="hexerei":
        flow = [i for i,a in ins if flowable(ING[i][0]) and i in provided_fluids]
    else:
        flow = [i for i,a in ins if flowable(ING[i][0])]
    best=None
    for size in range(CF["minFluidIn"], min(CF["maxFluidIn"], len(flow))+1):
        for fluids in combinations(flow, size):
            itemK = len([i for i,_ in ins if i not in fluids])
            if itemK < CF["minItemKinds"] or itemK > CF["itemKinds"]: continue
            ein=[]; 
            for i,a in ins:
                if i in fluids: ein.append(((mb_of(i,a),'F'), grams(i,a) < NEGL*ingram))
                else: ein.append(((items_of(i,a),'I'), grams(i,a) < NEGL*ingram))
            # outputs: asFluid if flowable and slots allow and (nbt or provided fluid) and not onlyAnItem
            eout=[]; fo=0; io=0
            for i,a in outs:
                asfluid = flowable(ING[i][0]) and fo < CF["maxFluidOut"] and CF["nbt"]
                asitem = io < CF["maxItemOut"] and CF["nbt"]
                if asfluid: fo+=1; eout.append(((mb_of(i,a),'F'), grams(i,a) < NEGL*outgram))
                elif asitem: io+=1; eout.append(((items_of(i,a),'I'), grams(i,a) < NEGL*outgram))
            q = quantize(ein, eout, CF["maxItemInputs"], CF["maxOfEachItem"])
            if q and (best is None or q[1] < best[1]): best=q
    if best is None: return "too large / no fit"
    f, me, ic, oc = best
    return f"factor={f:.4g} maxError={me*100:.1f}%" + (" EMITS" if me<=MAX_ERROR else f" REJECT distort {math.ceil(me*100)}%")

def load(p):
    d=json.load(open(p))
    return ([(i["ingredient"],(i["amount"]["qty"],i["amount"]["unit"])) for i in d["inputs"]["of"]],
            [(o["ingredient"],(o["amount"]["qty"],o["amount"]["unit"])) for o in d["outputs"]])

import sys
if __name__=="__main__":
    for name,p,route in [("cajun","data/layers/reviewed/LicenseRef-original/original/recipes/cajun_seasoning.mix.json","create"),
                          ("cajun","data/layers/reviewed/LicenseRef-original/original/recipes/cajun_seasoning.mix.json","shapeless"),
                          ("cajun","data/layers/reviewed/LicenseRef-original/original/recipes/cajun_seasoning.mix.json","hexerei"),
                          ("russian","data/layers/reviewed/LicenseRef-original/original/recipes/creamy_russian_dressing.mix.json","create"),
                          ("russian","data/layers/reviewed/LicenseRef-original/original/recipes/creamy_russian_dressing.mix.json","shapeless"),
                          ("russian","data/layers/reviewed/LicenseRef-original/original/recipes/creamy_russian_dressing.mix.json","hexerei"),
                          ("gtso","data/layers/reviewed/LicenseRef-original/original/recipes/general_tso_s_chicken.mix_sauce.json","create"),
                          ("gtso","data/layers/reviewed/LicenseRef-original/original/recipes/general_tso_s_chicken.mix_sauce.json","shapeless"),
                          ("gtso","data/layers/reviewed/LicenseRef-original/original/recipes/general_tso_s_chicken.mix_sauce.json","hexerei"),
                          ("hummus","data/layers/reviewed/LicenseRef-original/original/recipes/hummus.mix.json","create"),
                          ("hummus","data/layers/reviewed/LicenseRef-original/original/recipes/hummus.mix.json","shapeless"),
                          ("hummus","data/layers/reviewed/LicenseRef-original/original/recipes/hummus.mix.json","hexerei")]:
        ins,outs = load(p)
        print(name, route, "->", plan(name, ins, outs, route))
