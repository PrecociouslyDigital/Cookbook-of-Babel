#!/usr/bin/env python3
"""Apply audit sections 1 (reverse dual-mappings), 4 (held-back mappings) and
5 (plantings) to the minecraft layer. Idempotent; prints what it did."""
import json, glob, os, copy, collections

MAP_DIR = 'mods/minecraft/mappings'
GARDENS = 'mods/minecraft/src/main/resources/cookbook_of_babel/spice_gardens.json'

def load(path):
    with open(path) as f:
        return json.load(f)

def save(path, d):
    with open(path, 'w') as f:
        json.dump(d, f, indent=2, sort_keys=True)
        f.write('\n')

def tsv(path):
    for line in open(path):
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        yield line.split('\t')

def ns_of(item):
    return item.split(':')[0]

# ---- section 4: held-back mappings ----------------------------------------
added = collections.Counter()
for item, ing in tsv('scratchpad/audit/section4_mappings.tsv'):
    path = f'{MAP_DIR}/{ns_of(item)}.json'
    if not os.path.exists(path):
        print(f'!! no mapping file for {item} ({path})')
        continue
    d = load(path)
    entry = d.setdefault(ing, {'items': []})
    entry.setdefault('items', [])
    if not any(i.get('id') == item for i in entry['items']):
        entry['items'].append({'id': item})
        added['s4:' + ns_of(item)] += 1
    save(path, d)
print('section4 added:', dict(added))

# ---- section 1: reverse dual-mappings --------------------------------------
# target's entry in each mod file is copied under the leaf in the same file.
mapped_targets = {}   # ingredient -> set of files with an entry
for f in glob.glob(f'{MAP_DIR}/*.json'):
    d = load(f)
    for ing in d:
        mapped_targets.setdefault(ing, set()).add(os.path.basename(f))

done, skipped_dead, already = 0, [], []
for leaf, verdict, target in tsv('scratchpad/audit/section1_verdicts.tsv'):
    files = mapped_targets.get(target)
    if not files:
        skipped_dead.append(f'{leaf}->{target}')
        continue
    for fn in sorted(files):
        path = f'{MAP_DIR}/{fn}'
        d = load(path)
        if leaf in d:
            already.append(f'{leaf} in {fn}')
            continue
        d[leaf] = copy.deepcopy(d[target])
        save(path, d)
        done += 1
print(f'section1: copied {done} entries; already-present {len(already)}; '
      f'dead targets {len(skipped_dead)}')
if skipped_dead:
    print('  dead-target pairs (needs a chain first):')
    for p in skipped_dead:
        print('   ', p)

# ---- section 5: plantings ---------------------------------------------------
g = load(GARDENS)
climate = {
    'buckwheat': ['frost'],      # cool-season grain
    'drumstick': ['tropical'],
    'grape_leaf': ['temperate' if False else 'windy'],  # grapevine: warm, exposed
    'kaffir_lime': ['tropical'],
    'utazi': ['tropical'],
    'water_celery': ['soggy'],   # aquatic herb
    'saffron': ['arid'],         # crocus likes dry summers
}
note = []
for leaf, cl in climate.items():
    if leaf in g:
        note.append(f'{leaf} already planted')
        continue
    g[leaf] = cl
    note.append(f'planted {leaf} {cl}')
save(GARDENS, g)
print('section5:', '; '.join(note))
