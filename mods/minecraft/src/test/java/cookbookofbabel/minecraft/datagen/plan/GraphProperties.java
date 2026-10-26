package cookbookofbabel.minecraft.datagen.plan;

import static org.assertj.core.api.Assertions.assertThat;

import cookbookofbabel.minecraft.dataset.Choice.Leaf;
import cookbookofbabel.minecraft.dataset.Amount;
import cookbookofbabel.minecraft.dataset.Ingredient;
import cookbookofbabel.minecraft.dataset.IngredientId;
import cookbookofbabel.minecraft.dataset.Recipe;
import cookbookofbabel.minecraft.dataset.Technique;
import cookbookofbabel.minecraft.dataset.TechniqueId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/** Properties of the is-a DAG, the technique table, elision and required media. */
class GraphProperties {
    @Provide
    Arbitrary<List<Ingredient>> dags() {
        return Arbitraries.integers().between(1, 10).flatMap(Worlds::dag);
    }

    /** What {@code accepting(x)} takes in: x and exactly the ingredients with x among their ancestors. */
    @Property
    void descendantsAreExactlyThoseBelow(@ForAll("dags") List<Ingredient> ingredients) {
        var dataset = new Dataset(Worlds.export(ingredients, List.of(), List.of()));
        var parents = new HashMap<IngredientId, List<IngredientId>>();
        ingredients.forEach(i -> parents.put(i.id(), i.parents()));
        for (var x : ingredients) {
            for (var y : ingredients) {
                assertThat(dataset.descendants(x.id()).contains(y.id())).isEqualTo(ancestors(y.id(), parents).contains(x.id()));
            }
        }
    }

    private static Set<IngredientId> ancestors(IngredientId id, HashMap<IngredientId, List<IngredientId>> parents) {
        var seen = new HashSet<IngredientId>();
        var todo = new ArrayDeque<>(List.of(id));
        while (!todo.isEmpty()) {
            var next = todo.pop();
            if (seen.add(next)) {
                todo.addAll(parents.getOrDefault(next, List.of()));
            }
        }
        return seen;
    }

    /** A technique the table doesn't name is done like its nearest ancestor that it does. */
    @Property
    void unboundTechniquesFallBackToTheirNearestBoundAncestor(
            @ForAll @IntRange(min = 0, max = 4) int unbound, @ForAll("tableKeys") TechniqueId bound, @ForAll boolean rooted) {
        var techniques = new ArrayList<Technique>();
        for (var i = 0; i < unbound; i++) {
            techniques.add(Worlds.technique("t" + i, i + 1 < unbound ? "t" + (i + 1) : rooted ? bound.value() : "root"));
        }
        techniques.add(Worlds.technique(bound.value()));
        techniques.add(Worlds.technique("root"));
        var table = new Techniques(new Dataset(Worlds.export(List.of(), techniques, List.of())));
        var start = new TechniqueId(unbound > 0 ? "t0" : bound.value());
        var expected = unbound == 0 || rooted ? Techniques.TABLE.get(bound) : new Binding.Unbound();
        assertThat(table.bindingFor(start)).isEqualTo(expected);
    }

    @Provide
    Arbitrary<TechniqueId> tableKeys() {
        return Arbitraries.of(Techniques.TABLE.keySet());
    }

    /** Recipe graphs over 8 ingredients, some steps cooling, each recipe one in and one out or two in. */
    @Provide
    Arbitrary<List<Recipe>> graphs() {
        var step = Arbitraries.integers().between(0, 7).list().ofMinSize(2).ofMaxSize(3)
                .flatMap(ids -> Arbitraries.of("mix", "cool").map(t -> List.of(ids, List.of(t))));
        return step.list().ofMinSize(1).ofMaxSize(8).map(steps -> {
            var recipes = new ArrayList<Recipe>();
            for (var i = 0; i < steps.size(); i++) {
                @SuppressWarnings("unchecked")
                var ids = (List<Integer>) steps.get(i).get(0);
                var technique = (String) steps.get(i).get(1).get(0);
                var inputs = ids.subList(0, ids.size() - 1).stream().map(Worlds::ingredient).distinct().toList();
                var output = Worlds.ingredient(ids.get(ids.size() - 1));
                if (!inputs.contains(output)) {
                    recipes.add(Worlds.recipe("r" + i, technique, inputs, List.of(output)));
                }
            }
            return recipes;
        });
    }

    @Property
    void elisionSkipsOnlyElidableSteps(@ForAll("graphs") List<Recipe> recipes) {
        var result = Elision.elide(recipes, r -> r.technique().value().equals("cool"));
        var kept = result.recipes().stream().map(Recipe::id).toList();
        recipes.stream().filter(r -> !r.technique().value().equals("cool")).forEach(r -> assertThat(kept).contains(r.id()));
        result.elided().forEach(e -> assertThat(recipes.stream().filter(r -> r.id().equals(e.recipe())).findFirst().orElseThrow().technique().value())
                .isEqualTo("cool"));
    }

    /** Every final product that could be made before elision still can be. */
    @Property
    void elisionKeepsFinalProductsReachable(@ForAll("graphs") List<Recipe> recipes) {
        var made = recipes.stream().flatMap(r -> Recipes.outputs(r.outputs()).stream()).map(o -> o.ingredient()).toList();
        var used = recipes.stream().flatMap(r -> Recipes.leaves(r.inputs()).stream()).map(Leaf::ingredient).toList();
        var leaves = used.stream().filter(i -> !made.contains(i)).toList();
        var finals = made.stream().filter(i -> !used.contains(i)).toList();
        var before = Recipes.reachable(recipes, leaves);
        var after = Recipes.reachable(Elision.elide(recipes, r -> r.technique().value().equals("cool")).recipes(), leaves);
        finals.stream().filter(before::contains).forEach(f -> assertThat(after).contains(f));
    }

    /**
     * A fry step takes fat: an input of its own, or one that went into its main input, or else the
     * planner adds it.
     */
    @Property
    void fryingAlwaysHasFat(@ForAll boolean fatInStep, @ForAll boolean fatUpstream, @ForAll boolean butterIsFat) {
        var oil = new IngredientId("oil_and_fat");
        var butter = new IngredientId("butter");
        var potato = Worlds.ingredient(0);
        var shreds = Worlds.ingredient(1);
        var fried = Worlds.ingredient(2);
        var ingredients = List.of(
                Worlds.solid(oil, List.of()),
                Worlds.solid(butter, butterIsFat ? List.of(oil) : List.of()),
                Worlds.solid(potato, List.of()),
                Worlds.solid(shreds, List.of()),
                Worlds.solid(fried, List.of()));
        var upstream = Worlds.recipe("prep", "cut", fatUpstream ? List.of(potato, butter) : List.of(potato), List.of(shreds));
        var inputs = fatInStep ? List.of(shreds, butter) : List.of(shreds);
        var fry = Worlds.recipe("fry", "fry", inputs, List.of(fried));
        var dataset = new Dataset(Worlds.export(ingredients, List.of(Worlds.technique("fry"), Worlds.technique("cut")), List.of(upstream, fry)));
        var planner = new Planner(dataset, new Techniques(dataset), List.of(upstream, fry), Provided.NOTHING);

        var leaves = inputs.stream().map(i -> new Leaf(i, new Amount(i.equals(butter) ? 10 : 200, "g"))).toList();
        var hasFat = butterIsFat && (fatInStep || fatUpstream);
        assertThat(planner.missingMedium(fry, leaves)).isEqualTo(hasFat ? java.util.Optional.empty() : java.util.Optional.of(oil));
    }
}
