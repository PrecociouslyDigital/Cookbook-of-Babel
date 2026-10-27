"""Compile datagen's recipes into one compact graph for the explorer page.

A node is anything a recipe consumes or produces: a dataset substance, a
finished dish, or another mod's item, fluid or tag such as water or a knife.
Each produced node lists its alternatives, one per (inputs, output count) pair,
with every machine that makes it from those inputs. Datagen disables recipes
whose inputs the game can't make, so those form dead alternatives alongside
any live ones.

Each target mod writes recipes in its own schema, so inputs and outputs
appear under several keys and in several encodings.
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


def tag_values(data, kind, name):
    path = data / "tags" / kind / "ingredients" / f"{name}.json"
    if not path.exists():
        return []
    return [v["id"] if isinstance(v, dict) else v for v in json.loads(path.read_text())["values"]]


def compile_graph(generated):
    data = generated / "data/cookbook_of_babel"
    substances = {p.stem: json.loads(p.read_text()) for p in (data / "cookbook_of_babel/substance").glob("*.json")}

    produced = defaultdict(lambda: defaultdict(set))
    for path in sorted((data / "recipes").rglob("*.json")):
        recipe = json.loads(path.read_text())
        inputs = inputs_of(recipe)
        outputs = list(outputs_of(recipe))
        if not outputs:
            raise ValueError(f"{path} produces nothing")
        for out, count in outputs:
            produced[out][inputs, count, dead(recipe)].add(machine_of(recipe))

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

    def alternative(inputs, count, is_dead, ms):
        slots = [[index[name], qty, unit] if unit else [index[name], qty] for name, qty, unit in inputs]
        return [sorted(machine_index[m] for m in ms), count, slots, int(is_dead)]

    def alternatives(alts):
        """Live ones first, so the page shows a way to make the node that works."""
        return [alternative(*key, ms) for key, ms in sorted(alts.items(), key=lambda alt: alt[0][2])]

    return {
        "nodes": [describe(n) for n in names],
        "ids": names,
        "machines": machines,
        "recipes": {index[out]: alternatives(alts) for out, alts in produced.items()},
        "accepted": accepted,
    }


if __name__ == "__main__":
    json.dump(compile_graph(Path(sys.argv[1])), sys.stdout, separators=(",", ":"))
