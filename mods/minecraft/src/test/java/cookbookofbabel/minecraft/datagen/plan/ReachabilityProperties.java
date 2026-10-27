package cookbookofbabel.minecraft.datagen.plan;

import static org.assertj.core.api.Assertions.assertThat;

import cookbookofbabel.minecraft.clock.Transition;
import cookbookofbabel.minecraft.dataset.Amount;
import cookbookofbabel.minecraft.dataset.Ingredient;
import cookbookofbabel.minecraft.dataset.IngredientId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** Properties of pruning plans whose inputs the game can't make. */
class ReachabilityProperties {
    private static final int INGREDIENTS = 8;

    /** Plans over an is-a DAG, and what the game starts with. */
    record World(Dataset dataset, List<Reachability.Plan> plans, Set<IngredientId> seeds) {
        Reachability reachability() {
            return Reachability.of(seeds, plans, dataset::descendants);
        }

        List<Reachability.Plan> live() {
            var reachability = reachability();
            return plans.stream().filter(reachability::live).toList();
        }

        boolean satisfied(IngredientId input, Set<IngredientId> available) {
            return dataset.descendants(input).stream().anyMatch(available::contains);
        }
    }

    @Provide
    Arbitrary<World> worlds() {
        var ids = Arbitraries.integers().between(0, INGREDIENTS - 1).map(Worlds::ingredient).set();
        var plan = Combinators.combine(ids.ofMaxSize(3), ids.ofMinSize(1).ofMaxSize(2)).as(Reachability.Plan::new);
        return Combinators.combine(Worlds.dag(INGREDIENTS), plan.list().ofMaxSize(10), ids)
                .as((List<Ingredient> dag, List<Reachability.Plan> plans, Set<IngredientId> seeds) ->
                        new World(new Dataset(Worlds.export(dag, List.of(), List.of())), plans, seeds));
    }

    /** The live plans can run one after another, each on the seeds and what earlier ones made. */
    @Property
    void livePlansRunFromTheSeedsAlone(@ForAll("worlds") World world) {
        var available = new HashSet<>(world.seeds());
        var waiting = new ArrayList<>(world.live());
        var ran = true;
        while (ran) {
            ran = waiting.removeIf(p -> {
                var runs = p.inputs().stream().allMatch(i -> world.satisfied(i, available));
                if (runs) {
                    available.addAll(p.outputs());
                }
                return runs;
            });
        }
        assertThat(waiting).isEmpty();
    }

    /** A pruned plan misses an input that neither the seeds nor any live plan provides. */
    @Property
    void prunedPlansMissAnInputNothingLiveMakes(@ForAll("worlds") World world) {
        var available = new HashSet<>(world.seeds());
        world.live().forEach(p -> available.addAll(p.outputs()));
        var reachability = world.reachability();
        for (var plan : world.plans()) {
            var missing = reachability.missing(plan);
            assertThat(missing).isSubsetOf(plan.inputs());
            missing.forEach(i -> assertThat(world.satisfied(i, available)).isFalse());
            assertThat(reachability.live(plan)).isEqualTo(missing.isEmpty());
        }
    }

    @Property
    void pruningTwicePrunesNothingMore(@ForAll("worlds") World world) {
        var live = world.live();
        var again = new World(world.dataset(), live, world.seeds());
        assertThat(again.live()).isEqualTo(live);
    }

    @Property
    void anotherSeedKeepsEveryLivePlanLive(@ForAll("worlds") World world, @ForAll("ingredients") IngredientId extra) {
        var seeds = new HashSet<>(world.seeds());
        seeds.add(extra);
        var more = new World(world.dataset(), world.plans(), seeds);
        assertThat(more.live()).containsAll(world.live());
    }

    /**
     * A transition is a producer: with it, the game makes its output exactly when it made it already or makes the
     * transition's input.
     */
    @Property
    void transitionsMakeTheirOutputFromTheirInput(
            @ForAll("worlds") World world, @ForAll("ingredients") IngredientId input, @ForAll("ingredients") IngredientId output) {
        var transition = new PlannedTransition("t", input, output, new Amount(1, "d"), 1, PlannedTransition.UNSTATED, Transition.Kind.HELD).plan();
        var plans = new ArrayList<>(world.plans());
        plans.add(transition);
        var with = new World(world.dataset(), plans, world.seeds());
        var needsOutput = new Reachability.Plan(Set.of(output), Set.of());
        var without = world.reachability();
        assertThat(with.reachability().live(needsOutput)).isEqualTo(without.live(needsOutput) || without.live(transition));
    }

    @Provide
    Arbitrary<IngredientId> ingredients() {
        return Arbitraries.integers().between(0, INGREDIENTS - 1).map(Worlds::ingredient);
    }

    /** Seeds are what no recipe makes, plus whatever a mod maps. */
    @Property
    void seedsAreUnmadeOrMapped(@ForAll("worlds") World world, @ForAll("ingredients") IngredientId mapped) {
        var recipes = world.plans().stream()
                .map(p -> Worlds.recipe("r", "mix", List.copyOf(p.inputs()), List.copyOf(p.outputs())))
                .toList();
        var entry = new Mapping.Entry(List.of(), List.of(), List.of(), List.of());
        var seeds = Reachability.seeds(world.dataset(), recipes, List.of(new Mapping(Mod.CREATE, Map.of(mapped, entry))));
        var made = world.plans().stream().flatMap(p -> p.outputs().stream()).collect(Collectors.toSet());
        for (var ingredient : world.dataset().ingredients()) {
            assertThat(seeds.contains(ingredient.id())).isEqualTo(!made.contains(ingredient.id()) || ingredient.id().equals(mapped));
        }
    }
}
