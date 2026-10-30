#!/usr/bin/env python3
"""Bulk TypeSafe-Jev hue pass: picks a colour family per food word via choice
questions in concurrent batches, and saves word -> bucket."""
import concurrent.futures
import json
import pathlib
import subprocess
import tempfile

HERE = pathlib.Path(__file__).parent
BATCH = 64
WORKERS = 8

BUCKETS = {
    "red": "ripe tomato, strawberry, red pepper, paprika",
    "pink": "ham, salmon flesh, cured meats, pink grapefruit, pickled ginger",
    "maroon": "beetroot, red wine, kidney beans, dark berries, liver",
    "orange": "carrot, cheddar, sweet potato, pumpkin, apricot, turmeric",
    "yellow": "butter, lemon, egg yolk, sweetcorn, banana flesh, mustard",
    "cream": "milk, flour, rice, cream, oats, most uncooked dough",
    "green": "leafy herbs, salad greens, cucumber, lime, pistachio, avocado",
    "brown": "chocolate, roasted meats, mushrooms, whole grains, soy sauce, coffee",
    "tan": "bread crust, toast, peanut butter, caramel, fried batter",
    "purple": "red cabbage, plum, blueberry, aubergine flesh, lavender",
    "blue": "blue corn, blue curaçao, butterfly pea, blue potato (rare foods)",
    "grey": "fresh fish flesh, oyster, ash, unglazed pottery, seeds with husk",
}


def load_words():
    try:
        with open(HERE / "food_words.json") as f:
            all_words = json.load(f)
    except (OSError, ValueError, TypeError) as err:
        raise SystemExit(f"cannot read food words: {err}") from err
    return sorted(w for w, p in all_words.items() if p and p >= 0.5)


def run(chunk):
    req = {
        "state": (
            "Assigning colour families to food words for a food mod's item tints. The word will tint "
            "every ingredient whose label carries it, multiplied over a pale sprite. Judge the typical "
            "ripe, raw, or served appearance of the food the word denotes. When a word spans several "
            "(like pepper), pick the family a shopper would name first."
        ),
        "questions": {
            t: {
                "type": "choice",
                "instructions": f"Which colour family best fits the typical appearance of \"{t}\"?",
                "criteria": dict(BUCKETS),
            }
            for t in chunk
        },
    }
    with tempfile.NamedTemporaryFile("w", suffix=".json", dir=HERE, delete=False) as f:
        json.dump(req, f)
        path = f.name
    try:
        with open(path) as stdin:
            out = subprocess.run(
                ["node", str(HERE / "jevi.mjs")],
                stdin=stdin, capture_output=True, text=True, timeout=180, cwd=HERE,
            )
    finally:
        pathlib.Path(path).unlink(missing_ok=True)
    if out.returncode != 0:
        return None
    try:
        return json.loads(out.stdout)["answers"]
    except (ValueError, KeyError) as err:
        print(f"batch answer unreadable: {err}")
        return None


def main():
    words = load_words()
    chunks = [words[i : i + BATCH] for i in range(0, len(words), BATCH)]
    results = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=WORKERS) as pool:
        futs = {pool.submit(run, c): c for c in chunks}
        for i, fut in enumerate(concurrent.futures.as_completed(futs)):
            answers = fut.result()
            if answers:
                for name, a in answers.items():
                    results[name] = a.get("choice")
            print(f"{i+1}/{len(chunks)} batches done ({len(results)} words)")
    try:
        with open(HERE / "hue_buckets.json", "w") as f:
            json.dump(results, f, sort_keys=True)
    except OSError as err:
        raise SystemExit(f"cannot write hue_buckets.json: {err}") from err
    from collections import Counter

    print("bucket spread:", Counter(results.values()).most_common())


if __name__ == "__main__":
    main()
