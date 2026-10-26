package cookbookofbabel.minecraft.datagen.plan;

import cookbookofbabel.minecraft.dataset.Export;
import cookbookofbabel.minecraft.dataset.Ingredient;
import cookbookofbabel.minecraft.dataset.IngredientId;
import cookbookofbabel.minecraft.dataset.Technique;
import cookbookofbabel.minecraft.dataset.TechniqueId;
import cookbookofbabel.minecraft.dataset.Unit;
import cookbookofbabel.minecraft.substance.Carrier;
import cookbookofbabel.minecraft.substance.Container;
import cookbookofbabel.minecraft.substance.Nutrition;
import cookbookofbabel.minecraft.substance.Portions;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The export, indexed for the questions datagen asks of it. */
public final class Dataset {
    private final Map<IngredientId, Ingredient> ingredients;
    private final Map<IngredientId, List<IngredientId>> children = new HashMap<>();
    private final Map<TechniqueId, Technique> techniques;
    private final Map<String, Unit> units;
    private final Map<IngredientId, Double> gramsPerPiece = new HashMap<>();
    private final Map<IngredientId, Nutrition> nutrition;
    private final double negligibleMassFraction;

    public Dataset(Export export) {
        ingredients = export.ingredients().stream().collect(Collectors.toMap(Ingredient::id, Function.identity()));
        techniques = export.techniques().stream().collect(Collectors.toMap(Technique::id, Function.identity()));
        units = export.units().stream().collect(Collectors.toMap(Unit::id, Function.identity()));
        nutrition = export.nutrition();
        negligibleMassFraction = export.constants().negligibleMassFraction();
        for (var ingredient : export.ingredients()) {
            for (var parent : ingredient.parents()) {
                children.computeIfAbsent(parent, p -> new ArrayList<>()).add(ingredient.id());
            }
        }
        for (var measure : export.measures()) {
            measure.gramsPer().filter(g -> g.unit().equals("piece")).ifPresent(g -> gramsPerPiece.put(measure.ingredient(), g.grams()));
        }
    }

    public Ingredient ingredient(IngredientId id) {
        var ingredient = ingredients.get(id);
        if (ingredient == null) {
            throw new IllegalArgumentException("no ingredient " + id + " in the export");
        }
        return ingredient;
    }

    public List<Ingredient> ingredients() {
        return ingredients.values().stream().sorted((a, b) -> a.id().compareTo(b.id())).toList();
    }

    public boolean hasIngredient(IngredientId id) {
        return ingredients.containsKey(id);
    }

    public Optional<Technique> technique(TechniqueId id) {
        return Optional.ofNullable(techniques.get(id));
    }

    public Unit unit(String id) {
        var unit = units.get(id);
        if (unit == null) {
            throw new IllegalArgumentException("no unit " + id + " in the export");
        }
        return unit;
    }

    public Optional<Nutrition> nutrition(IngredientId id) {
        return Optional.ofNullable(nutrition.get(id));
    }

    public double negligibleMassFraction() {
        return negligibleMassFraction;
    }

    /** The temperature a technique is typically done at, from it or its nearest ancestor. */
    public Optional<Double> typicalTempC(TechniqueId id) {
        return lineage(id).stream()
                .flatMap(t -> technique(t).flatMap(Technique::typicalTemp).stream())
                .map(Technique.TempRange::midpoint)
                .findFirst();
    }

    public List<IngredientId> children(IngredientId id) {
        return children.getOrDefault(id, List.of());
    }

    /** The ingredient and everything below it in the is-a DAG. */
    public Set<IngredientId> descendants(IngredientId id) {
        var seen = new LinkedHashSet<IngredientId>();
        var todo = new ArrayDeque<IngredientId>(List.of(id));
        while (!todo.isEmpty()) {
            var next = todo.pop();
            if (seen.add(next)) {
                todo.addAll(children(next));
            }
        }
        return seen;
    }

    public boolean isA(IngredientId id, IngredientId ancestor) {
        return descendants(ancestor).contains(id);
    }

    /** The technique and its ancestors, nearest first. */
    public List<TechniqueId> lineage(TechniqueId id) {
        var seen = new LinkedHashSet<TechniqueId>();
        var todo = new ArrayDeque<TechniqueId>(List.of(id));
        while (!todo.isEmpty()) {
            var next = todo.removeFirst();
            if (seen.add(next)) {
                technique(next).ifPresent(t -> todo.addAll(t.parents()));
            }
        }
        return List.copyOf(seen);
    }

    /** Named dishes are items of their own; every other ingredient is a substance. */
    public Optional<Container> container(IngredientId id) {
        var ingredient = ingredient(id);
        return !ingredient.named() && Carrier.forForm(ingredient.form()) instanceof Carrier.Flowable f
                ? Optional.of(f.container())
                : Optional.empty();
    }

    /** What one item of the ingredient weighs: its stated piece mass, else its form's. */
    public double gramsPerItem(IngredientId id) {
        return Optional.ofNullable(gramsPerPiece.get(id)).orElseGet(() -> Portions.gramsPerItem(ingredient(id).form()));
    }

    public Optional<Double> statedGramsPerPiece(IngredientId id) {
        return Optional.ofNullable(gramsPerPiece.get(id));
    }
}
