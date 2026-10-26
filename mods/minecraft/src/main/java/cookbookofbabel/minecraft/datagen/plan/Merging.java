package cookbookofbabel.minecraft.datagen.plan;

import cookbookofbabel.minecraft.dataset.IngredientId;
import cookbookofbabel.minecraft.dataset.Preparation;
import cookbookofbabel.minecraft.dataset.Recipe;
import cookbookofbabel.minecraft.dataset.TechniqueId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.UnaryOperator;

/**
 * Merges what the dataset keeps apart but this game can't tell apart. The dataset has one ingredient
 * for each preparation, one technique done to one ingredient; this game does several techniques on
 * one machine, so julienned and chopped potato both come off the cutting board. The preparations of
 * one ingredient on one machine become a single ingredient, the smallest ID among them, and so do
 * the preparations of what was merged, until nothing more merges.
 */
public final class Merging {
    private Merging() {}

    /**
     * @param merged each merged-away ingredient, and the one it became
     * @param dropped recipes that merging left the same as a kept one, so no machine gets two
     */
    public record Result(List<Recipe> recipes, Map<IngredientId, IngredientId> merged, List<Dropped> dropped) {}

    public record Dropped(String recipe, String keptRecipe) {}

    private record Key(IngredientId ingredient, TechniqueId machine) {}

    /** What a game sees of a recipe: what goes in, the machine, and what comes out. */
    private record Signature(List<IngredientId> inputs, TechniqueId machine, List<IngredientId> outputs) {}

    /** @param machineOf what this game tells a technique's results apart by */
    public static Result merge(List<Recipe> recipes, List<Preparation> preparations, UnaryOperator<TechniqueId> machineOf) {
        var classes = new Classes();
        var changed = true;
        while (changed) {
            changed = false;
            var byKey = new HashMap<Key, List<IngredientId>>();
            for (var p : preparations) {
                byKey.computeIfAbsent(new Key(classes.find(p.ingredient()), machineOf.apply(p.technique())), k -> new ArrayList<>())
                        .add(p.makes());
            }
            for (var made : byKey.values()) {
                for (var other : made.subList(1, made.size())) {
                    changed |= classes.union(made.get(0), other);
                }
            }
        }
        var merged = classes.merged();
        UnaryOperator<IngredientId> rename = i -> merged.getOrDefault(i, i);
        var kept = new LinkedHashMap<Signature, Recipe>();
        var dropped = new ArrayList<Dropped>();
        recipes.stream().sorted(Comparator.comparing(Recipe::id)).map(r -> Recipes.rename(r, rename)).forEach(r -> {
            var twin = kept.putIfAbsent(signature(r, machineOf), r);
            if (twin != null) {
                dropped.add(new Dropped(r.id(), twin.id()));
            }
        });
        return new Result(List.copyOf(kept.values()), merged, List.copyOf(dropped));
    }

    private static Signature signature(Recipe r, UnaryOperator<TechniqueId> machineOf) {
        return new Signature(
                Recipes.leaves(r.inputs()).stream().map(l -> l.ingredient()).sorted().toList(),
                machineOf.apply(r.technique()),
                Recipes.outputs(r.outputs()).stream().map(o -> o.ingredient()).sorted().toList());
    }

    /** Ingredients merged so far, each class named by its smallest ID. */
    private static final class Classes {
        private final Map<IngredientId, IngredientId> parent = new HashMap<>();

        IngredientId find(IngredientId i) {
            var p = parent.getOrDefault(i, i);
            if (p.equals(i)) {
                return i;
            }
            var root = find(p);
            parent.put(i, root);
            return root;
        }

        /** Whether the two were apart until now. */
        boolean union(IngredientId a, IngredientId b) {
            var ra = find(a);
            var rb = find(b);
            if (ra.equals(rb)) {
                return false;
            }
            if (ra.compareTo(rb) < 0) {
                parent.put(rb, ra);
            } else {
                parent.put(ra, rb);
            }
            return true;
        }

        Map<IngredientId, IngredientId> merged() {
            var merged = new TreeMap<IngredientId, IngredientId>();
            List.copyOf(parent.keySet()).forEach(i -> {
                var root = find(i);
                if (!root.equals(i)) {
                    merged.put(i, root);
                }
            });
            return merged;
        }
    }
}
