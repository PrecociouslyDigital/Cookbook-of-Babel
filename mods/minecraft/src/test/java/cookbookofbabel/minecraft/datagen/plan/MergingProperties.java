package cookbookofbabel.minecraft.datagen.plan;

import static org.assertj.core.api.Assertions.assertThat;

import cookbookofbabel.minecraft.dataset.IngredientId;
import cookbookofbabel.minecraft.dataset.Preparation;
import cookbookofbabel.minecraft.dataset.Recipe;
import cookbookofbabel.minecraft.dataset.TechniqueId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** Properties of merging preparations by the machine that does them. */
class MergingProperties {
    /** Three ways to cut, two to heat, and rinsing on its own. */
    private static final Map<String, String> MACHINES =
            Map.of("julienne", "cut", "chop", "cut", "cut", "cut", "boil", "heat", "heat", "heat", "rinse", "rinse");

    private static final UnaryOperator<TechniqueId> MACHINE_OF = t -> new TechniqueId(MACHINES.get(t.value()));

    /** A dataset's preparations: each ingredient-technique pair makes at most one thing. */
    @Provide
    Arbitrary<List<Preparation>> preparations() {
        var techniques = List.copyOf(MACHINES.keySet()).stream().sorted().toList();
        var ingredient = Arbitraries.integers().between(0, 7).map(Worlds::ingredient);
        return Worlds.pair(Worlds.pair(ingredient, Arbitraries.of(techniques)), ingredient)
                .list().ofMaxSize(16)
                .map(entries -> {
                    var made = new java.util.LinkedHashMap<Map.Entry<IngredientId, String>, IngredientId>();
                    entries.forEach(e -> made.putIfAbsent(e.getKey(), e.getValue()));
                    return made.entrySet().stream()
                            .map(e -> new Preparation(e.getKey().getKey(), new TechniqueId(e.getKey().getValue()), e.getValue()))
                            .toList();
                });
    }

    private static List<Recipe> recipesFor(List<Preparation> preparations) {
        var recipes = new ArrayList<Recipe>();
        for (var i = 0; i < preparations.size(); i++) {
            var p = preparations.get(i);
            recipes.add(Worlds.recipe("r" + i, p.technique().value(), List.of(p.ingredient()), List.of(p.makes())));
        }
        return recipes;
    }

    private static IngredientId renamed(Merging.Result result, IngredientId i) {
        return result.merged().getOrDefault(i, i);
    }

    @Property
    void oneIngredientOnOneMachineMakesOneThing(@ForAll("preparations") List<Preparation> preparations) {
        var result = Merging.merge(recipesFor(preparations), preparations, MACHINE_OF);
        for (var p : preparations) {
            for (var q : preparations) {
                if (renamed(result, p.ingredient()).equals(renamed(result, q.ingredient()))
                        && MACHINE_OF.apply(p.technique()).equals(MACHINE_OF.apply(q.technique()))) {
                    assertThat(renamed(result, p.makes())).isEqualTo(renamed(result, q.makes()));
                }
            }
        }
    }

    @Property
    void mergingTwiceChangesNothingMore(@ForAll("preparations") List<Preparation> preparations) {
        var once = Merging.merge(recipesFor(preparations), preparations, MACHINE_OF);
        var renamedPreparations = preparations.stream()
                .map(p -> new Preparation(renamed(once, p.ingredient()), p.technique(), renamed(once, p.makes())))
                .toList();
        var twice = Merging.merge(once.recipes(), renamedPreparations, MACHINE_OF);
        assertThat(twice.merged()).isEmpty();
        assertThat(twice.recipes()).isEqualTo(once.recipes());
    }

    @Property
    void onlyWhatAPreparationMakesIsMergedAway(@ForAll("preparations") List<Preparation> preparations) {
        var made = preparations.stream().map(Preparation::makes).toList();
        assertThat(made).containsAll(Merging.merge(recipesFor(preparations), preparations, MACHINE_OF).merged().keySet());
    }

    @Property
    void aDroppedRecipeIsTheSameAsOneKept(@ForAll("preparations") List<Preparation> preparations) {
        var recipes = recipesFor(preparations);
        var result = Merging.merge(recipes, preparations, MACHINE_OF);
        for (var dropped : result.dropped()) {
            var original = recipes.stream().filter(r -> r.id().equals(dropped.recipe())).findFirst().orElseThrow();
            var kept = result.recipes().stream().filter(r -> r.id().equals(dropped.keptRecipe())).findFirst().orElseThrow();
            assertThat(seen(Recipes.rename(original, i -> renamed(result, i)))).isEqualTo(seen(kept));
        }
        assertThat(result.recipes().size() + result.dropped().size()).isEqualTo(recipes.size());
    }

    /** What a game sees of a recipe: its inputs, machine and outputs. */
    private static List<Object> seen(Recipe r) {
        return List.of(
                Recipes.leaves(r.inputs()).stream().map(l -> l.ingredient()).sorted().toList(),
                MACHINE_OF.apply(r.technique()),
                Recipes.outputs(r.outputs()).stream().map(o -> o.ingredient()).sorted().toList());
    }
}
