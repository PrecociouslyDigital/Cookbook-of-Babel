"""Compile datagen's recipes into one compact graph for the explorer page.

A node is anything a recipe consumes or produces: a dataset substance, a
finished dish, or another mod's item, fluid or tag such as water or a knife.
Each produced node lists its alternatives, one per (inputs, output count) pair,
with every machine that makes it from those inputs. Datagen disables recipes
whose inputs the game can't make, so those form dead alternatives alongside
any live ones.

Each target mod writes recipes in its own schema, so inputs and outputs
appear under several keys and in several encodings.

Transitions, which turn one ingredient into another while kept, are
alternatives too, grouped by machine (storage for a held step, spoiling for
spoilage) and by the temperature band where they take their stated time.
Datagen only writes the ones whose input the game can make, so none are dead.
"""

import json
import re
import sys
from collections import defaultdict
from pathlib import Path

NS = "cookbook_of_babel:"
INGREDIENT_TAG = NS + "ingredients/"
CONTAINERS = {NS + "substance", NS + "portion"}
SUBSTANCE_NBT = re.compile(r'substance\\?"?:\s*\\?"cookbook_of_babel:(\w+)')
STEP_LABEL = re.compile(r"(.+), after step \d+$")
STEP_ID = re.compile(r"_step_\d+$")

LIST_INPUTS = "ingredients"
SINGLE_INPUTS = ("ingredient", "item_input", "fluid_input", "tool", "solvent", "fluid", "liquid")
AMOUNT_KEYS = {"solvent": "solvent_amount", "fluid": "fluidAmount", "liquid": "fluidLevelsConsumed"}
# Hexerei's cauldron also names a liquidOutput: what's left of its liquid, not a product.
OUTPUTS = ("result", "results", "output", "item_output", "fluid_output", "secondaryOutput")
TRANSITION_MACHINES = {"held": NS + "storage", "spoil": NS + "spoiling"}


def babel_ref(entry):
    """The dataset node an ingredient or result names, if it names one."""
    nbt = entry.get("nbt", entry["tag"] if isinstance(entry.get("tag"), dict) else None)
    if nbt is not None:
        found = SUBSTANCE_NBT.search(nbt if isinstance(nbt, str) else json.dumps(nbt))
        if found:
            return found.group(1)
    item = entry.get("item", entry.get("id", ""))
    if item.startswith(NS) and item not in CONTAINERS:
        return item.removeprefix(NS)
    tag = entry.get("tag")
    if isinstance(tag, str) and tag.startswith(INGREDIENT_TAG):
        return tag.removeprefix(INGREDIENT_TAG)
    return None


def foreign_ref(entry):
    for key in ("fluid", "fluidTag", "item", "id", "tag"):
        if isinstance(entry.get(key), str):
            return ("#" if "tag" in key.lower() else "") + entry[key]
    raise ValueError(f"unrecognised ingredient {entry}")


def ref(entry):
    return babel_ref(entry) or foreign_ref(entry)


def unwrapped(ingredient):
    """CoFH wraps an item ingredient as {"value": ..., "count": n} to give it a count."""
    return ingredient["value"] if isinstance(ingredient, dict) and "value" in ingredient else ingredient


def resolve(ingredient):
    ingredient = unwrapped(ingredient)
    alternatives = ingredient if isinstance(ingredient, list) else [ingredient]
    return next(filter(None, map(babel_ref, alternatives)), None) or foreign_ref(alternatives[0])


def quantity(ingredient, recipe, key):
    if key in AMOUNT_KEYS:
        return recipe[AMOUNT_KEYS[key]], "mB"
    if isinstance(ingredient, dict) and "amount" in ingredient:
        return ingredient["amount"], "mB"
    if isinstance(ingredient, dict) and "value" in ingredient:
        return ingredient.get("count", 1), ""
    return 1, ""


def inputs_of(recipe):
    slots = [(i, LIST_INPUTS) for i in recipe.get(LIST_INPUTS, [])]
    slots += [(recipe[k], k) for k in SINGLE_INPUTS if k in recipe]
    totals = defaultdict(int)
    units = {}
    for ingredient, key in slots:
        name = resolve(ingredient)
        qty, unit = quantity(ingredient, recipe, key)
        totals[name] += qty
        units[name] = unit
    return tuple(sorted((name, totals[name], units[name]) for name in totals))


def outputs_of(recipe):
    for key in OUTPUTS:
        value = recipe.get(key) or []
        for entry in value if isinstance(value, list) else [value]:
            yield ref(entry), entry.get("count", entry.get("Count", entry.get("amount", 1)))


def dead(recipe):
    return any(c.get("type") == "forge:false" for c in recipe.get("conditions", []))


def machine_of(recipe):
    heat = recipe.get("heatRequirement")
    return recipe["type"] + (f" ({heat})" if heat else "")


def band_of(celsius, bands):
    """The first band whose bound lies above the temperature, else the open-ended top one, as TempBands.bandOf."""
    return next((b["id"] for b in bands if "belowC" in b and celsius < b["belowC"]), bands[-1]["id"])


def tag_values(data, kind, name, seen=None):
    """Everything the tag accepts, resolving nested tags down to real items/fluids, as Dataset.descendants does in Java."""
    seen = seen if seen is not None else set()
    if name in seen:
        return []
    seen.add(name)
    path = data / "tags" / kind / "ingredients" / f"{name}.json"
    if not path.exists():
        return []
    try:
        entries = json.loads(path.read_text())["values"]
    except (OSError, ValueError) as e:
        print(f"warning: could not read tag {name}: {e}", file=sys.stderr)
        return []
    values = []
    for entry in entries:
        entry = entry["id"] if isinstance(entry, dict) else entry
        if entry.startswith("#" + INGREDIENT_TAG):
            values += tag_values(data, kind, entry.removeprefix("#" + INGREDIENT_TAG), seen)
        else:
            values.append(entry)
    return values


def compile_graph(generated, bands, resources=None):
    data = generated / "data/cookbook_of_babel"
    substances = {}
    for p in (data / "cookbook_of_babel/substance").glob("*.json"):
        try:
            substances[p.stem] = json.loads(p.read_text())
        except (OSError, ValueError) as e:
            print(f"warning: could not read substance {p}: {e}", file=sys.stderr)
    resources = resources or generated.parent.parent / "main/resources"
    gardens_path = resources / "cookbook_of_babel/spice_gardens.json"
    gardens = {}
    if gardens_path.exists():
        try:
            gardens = json.loads(gardens_path.read_text())
        except (OSError, ValueError) as e:
            print(f"warning: could not read {gardens_path}: {e}", file=sys.stderr)

    produced = defaultdict(lambda: defaultdict(set))
    for path in sorted((data / "recipes").rglob("*.json")):
        try:
            recipe = json.loads(path.read_text())
        except (OSError, ValueError) as e:
            print(f"warning: could not read recipe {path}: {e}", file=sys.stderr)
            continue
        inputs = inputs_of(recipe)
        outputs = list(outputs_of(recipe))
        if not outputs:
            raise ValueError(f"{path} produces nothing")
        for out, count in outputs:
            produced[out][inputs, count, dead(recipe)].add(machine_of(recipe))
    for path in sorted((data / "cookbook_of_babel/transition").glob("*.json")):
        try:
            transition = json.loads(path.read_text())
        except (OSError, ValueError) as e:
            print(f"warning: could not read transition {path}: {e}", file=sys.stderr)
            continue
        inputs = ((transition["input"].removeprefix(NS), 1, ""),)
        band = band_of(transition["sensitivity"]["refTempC"], bands)
        machine = f"{TRANSITION_MACHINES[transition['kind']]} ({band})"
        produced[transition["output"].removeprefix(NS)][inputs, 1, False].add(machine)
    # The spices the mod plants itself yield from the garden crop (a loot
    # function, not a recipe), so give the graph a producing edge for them.
    for spice, climates in gardens.items():
        produced[spice][(), 1, False].add("spice garden (" + ", ".join(climates) + ")")

    names = set(produced) | set(substances)
    names.update(name for alts in produced.values() for inputs, _, _ in alts for name, _, _ in inputs)
    names = sorted(names)
    index = {n: i for i, n in enumerate(names)}
    machines = sorted({m for alts in produced.values() for ms in alts.values() for m in ms})
    machine_index = {m: i for i, m in enumerate(machines)}

    dish_label = {}
    for name, sub in substances.items():
        if step := STEP_LABEL.match(sub["label"]):
            dish_label.setdefault(STEP_ID.sub("", name), step.group(1))

    def describe(name):
        if name in substances:
            sub = substances[name]
            return [sub["label"], sub["color"], sub["form"]]
        if ":" in name:
            return [name, "", "external"]
        label = dish_label.get(name, name.replace("_", " "))
        return [label[:1].upper() + label[1:], "", "dish"]

    accepted = {}
    for name in substances:
        outside = [v for kind in ("items", "fluids") for v in tag_values(data, kind, name) if NS not in v]
        if outside:
            accepted[index[name]] = outside
    # Own substances exist in game as the shared substance item carrying NBT,
    # which static tags cannot list - credit the planted spices explicitly.
    for spice in gardens:
        if spice in substances and spice in index:
            accepted.setdefault(index[spice], []).append(
                f"{NS}substance with NBT substance={NS}{spice}")

    def alternative(inputs, count, is_dead, ms):
        slots = [[index[name], qty, unit] if unit else [index[name], qty] for name, qty, unit in inputs]
        return [sorted(machine_index[m] for m in ms), count, slots, 1 if is_dead else 0]

    def alternatives(alts):
        """Live ones first, so the page shows a way to make the node that works."""
        return [alternative(key[0], key[1], key[2], ms) for key, ms in sorted(alts.items(), key=lambda alt: alt[0][2])]

    return {
        "nodes": [describe(n) for n in names],
        "ids": names,
        "machines": machines,
        "recipes": {index[out]: alternatives(alts) for out, alts in produced.items()},
        "accepted": accepted,
    }


if __name__ == "__main__":
    try:
        bands = json.loads(Path(sys.argv[2]).read_text())["tempBands"]
        resources = Path(sys.argv[3]) if len(sys.argv) > 3 else None
        json.dump(compile_graph(Path(sys.argv[1]), bands, resources), sys.stdout, separators=(",", ":"))
    except (OSError, ValueError, KeyError) as e:
        sys.exit(f"explorer extract failed: {e}")
