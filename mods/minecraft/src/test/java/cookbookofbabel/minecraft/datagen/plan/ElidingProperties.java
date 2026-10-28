package cookbookofbabel.minecraft.datagen.plan;

import static org.assertj.core.api.Assertions.assertThat;

import cookbookofbabel.minecraft.dataset.IngredientId;
import cookbookofbabel.minecraft.dataset.Recipe;
import cookbookofbabel.minecraft.dataset.TechniqueId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** Properties of discarding single-ingredient crafts. */
class ElidingProperties {
    /** Shaping is done on a crafting grid; baking isn't. */
    private static final Predicate<TechniqueId> CRAFTED = t -> t.value().equals("shape");

    private static final UnaryOperator<TechniqueId> MACHINE_OF = t -> t;

    /** Every amount here is in grams, so any two compare. */
    private static final Eliding.Ratio RATIO = (ingredient, amount, per) -> Optional.of(amount.qty() / per.qty());

    record World(List<Recipe> recipes, Set<IngredientId> pinned) {}

    @Provide
    Arbitrary<World> worlds() {
        var ingredient = Arbitraries.integers().between(0, 7).map(Worlds::ingredient);
        var ingredients = ingredient.list().ofMinSize(1).ofMaxSize(2).uniqueElements();
        return Combinators.combine(
                        Combinators.combine(Arbitraries.of("shape", "bake"), ingredients, ingredients).as(Spec::new).list().ofMaxSize(12),
                        ingredient.set().ofMaxSize(3))
                .as((specs, pinned) -> {
                    var recipes = new ArrayList<Recipe>();
                    for (var i = 0; i < specs.size(); i++) {
                        var spec = specs.get(i);
                        recipes.add(Worlds.recipe(String.format("r%02d", i), spec.technique(), spec.inputs(), spec.outputs()));
                    }
                    return new World(recipes, pinned);
                });
    }

    private record Spec(String technique, List<IngredientId> inputs, List<IngredientId> outputs) {}

    private static Eliding.Result elide(World world) {
        return Eliding.elide(world.recipes(), CRAFTED, world.pinned(), RATIO, MACHINE_OF);
    }

    private static Set<IngredientId> ingredients(List<Recipe> recipes) {
        var all = new HashSet<IngredientId>();
        recipes.forEach(r -> {
            Recipes.leaves(r.inputs()).forEach(l -> all.add(l.ingredient()));
            Recipes.outputs(r.outputs()).forEach(o -> all.add(o.ingredient()));
        });
        return all;
    }

    @Property
    void elidingTwiceChangesNothingMore(@ForAll("worlds") World world) {
        var once = elide(world);
        var twice = Eliding.elide(once.recipes(), CRAFTED, world.pinned(), RATIO, MACHINE_OF);
        assertThat(twice.elided()).isEmpty();
        assertThat(twice.recipes()).isEqualTo(once.recipes());
    }

    @Property
    void onlyAnUnpinnedIngredientIsElidedAway(@ForAll("worlds") World world) {
        for (var e : elide(world).elided()) {
            var gone = e.kept().equals(e.input()) ? e.output() : e.input();
            assertThat(e.kept()).isIn(e.input(), e.output());
            if (!gone.equals(e.kept())) {
                assertThat(world.pinned()).doesNotContain(gone);
            }
        }
    }

    @Property
    void everyStepIsKeptElidedOrDropped(@ForAll("worlds") World world) {
        var result = elide(world);
        var accounted = Stream.of(
                        result.recipes().stream().map(Recipe::id),
                        result.elided().stream().map(Eliding.Elided::recipe),
                        result.dropped().stream().map(Merging.Dropped::recipe))
                .flatMap(s -> s)
                .sorted()
                .toList();
        assertThat(accounted).isEqualTo(world.recipes().stream().map(Recipe::id).sorted().toList());
    }

    @Property
    void noIngredientIsInvented(@ForAll("worlds") World world) {
        assertThat(ingredients(world.recipes())).containsAll(ingredients(elide(world).recipes()));
    }

    @Property
    void onlyCraftingStepsAreElided(@ForAll("worlds") World world) {
        var shaped = world.recipes().stream().filter(r -> CRAFTED.test(r.technique())).map(Recipe::id).toList();
        assertThat(shaped).containsAll(elide(world).elided().stream().map(Eliding.Elided::recipe).toList());
    }
}
