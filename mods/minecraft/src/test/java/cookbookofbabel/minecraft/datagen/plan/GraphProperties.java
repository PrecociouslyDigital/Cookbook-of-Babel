package cookbookofbabel.minecraft.datagen.plan;

import static org.assertj.core.api.Assertions.assertThat;

import cookbookofbabel.minecraft.dataset.Choice.Leaf;
import cookbookofbabel.minecraft.dataset.Amount;
import cookbookofbabel.minecraft.dataset.Ingredient;
import cookbookofbabel.minecraft.dataset.IngredientId;
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

/** Properties of the is-a DAG, the technique table and required media. */
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

    /** Only a machine makes its techniques' results alike; the kinds of spoiling stay apart. */
    @Property
    void techniquesDoneInNoMachineKeepTheirResultsApart(@ForAll("tableKeys") TechniqueId bound) {
        var table = new Techniques(new Dataset(Worlds.export(
                List.of(), List.of(Worlds.technique("child", bound.value()), Worlds.technique(bound.value())), List.of())));
        var child = new TechniqueId("child");
        assertThat(table.machineOf(child)).isEqualTo(Techniques.TABLE.get(bound).routes().isEmpty() ? child : bound);
    }

    @Provide
    Arbitrary<TechniqueId> tableKeys() {
        return Arbitraries.of(Techniques.TABLE.keySet());
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
