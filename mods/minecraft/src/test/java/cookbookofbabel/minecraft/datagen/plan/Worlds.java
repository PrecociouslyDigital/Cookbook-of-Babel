package cookbookofbabel.minecraft.datagen.plan;

import cookbookofbabel.minecraft.dataset.Amount;
import cookbookofbabel.minecraft.dataset.Choice;
import cookbookofbabel.minecraft.dataset.End;
import cookbookofbabel.minecraft.dataset.Export;
import cookbookofbabel.minecraft.dataset.Form;
import cookbookofbabel.minecraft.dataset.Ingredient;
import cookbookofbabel.minecraft.dataset.IngredientId;
import cookbookofbabel.minecraft.dataset.Outcome;
import cookbookofbabel.minecraft.dataset.Recipe;
import cookbookofbabel.minecraft.dataset.Technique;
import cookbookofbabel.minecraft.dataset.TechniqueId;
import cookbookofbabel.minecraft.dataset.Unit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;

/** Small datasets for properties, valid by construction: parents only point at earlier entries. */
final class Worlds {
    static final List<Unit> UNITS = List.of(
            new Unit("g", Unit.Dimension.MASS, 1),
            new Unit("ml", Unit.Dimension.VOLUME, 1),
            new Unit("piece", Unit.Dimension.COUNT, 1),
            new Unit("s", Unit.Dimension.TIME, 1));

    private Worlds() {}

    static IngredientId ingredient(int i) {
        return new IngredientId("i" + i);
    }

    static Ingredient solid(IngredientId id, List<IngredientId> parents) {
        return new Ingredient(id, id.value(), List.of(), parents, Form.PIECE, Optional.empty(), false);
    }

    static Export export(List<Ingredient> ingredients, List<Technique> techniques, List<Recipe> recipes) {
        return new Export(List.of(), List.of(), ingredients, techniques, recipes, List.of(), Map.of(), UNITS, 0.02);
    }

    /** An is-a DAG of {@code n} solid ingredients; each may have parents among the earlier ones. */
    static Arbitrary<List<Ingredient>> dag(int n) {
        return Arbitraries.integers().between(0, Integer.MAX_VALUE).list().ofSize(n).map(seeds -> {
            var ingredients = new ArrayList<Ingredient>();
            for (var i = 0; i < n; i++) {
                var parents = new ArrayList<IngredientId>();
                for (var j = 0; j < i; j++) {
                    if (Integer.rotateLeft(seeds.get(i), j) % 4 == 0) {
                        parents.add(ingredient(j));
                    }
                }
                ingredients.add(solid(ingredient(i), parents));
            }
            return ingredients;
        });
    }

    static Recipe recipe(String id, String technique, List<IngredientId> inputs, List<IngredientId> outputs) {
        var leaves = inputs.stream().<Choice>map(i -> new Choice.Leaf(i, new Amount(100, "g"))).toList();
        var outs = outputs.stream()
                .map(o -> new Outcome.Output(o, new Amount(100, "g"), Outcome.Role.MAIN, Optional.empty()))
                .toList();
        return new Recipe(id, "source", new TechniqueId(technique), new Choice.Select(Optional.empty(), Optional.empty(), leaves),
                new Outcome.Outputs(outs), Optional.empty(), new End.ByTime(new Amount(60, "s")), List.of());
    }

    static Technique technique(String id, String... parents) {
        return new Technique(new TechniqueId(id), java.util.Arrays.stream(parents).map(TechniqueId::new).toList(), Optional.empty());
    }

    static <A, B> Arbitrary<Map.Entry<A, B>> pair(Arbitrary<A> a, Arbitrary<B> b) {
        return Combinators.combine(a, b).as(Map::entry);
    }
}
