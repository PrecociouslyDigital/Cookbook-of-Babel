package cookbookofbabel.minecraft.datagen.plan;

import static org.assertj.core.api.Assertions.assertThat;

import cookbookofbabel.minecraft.dataset.Amount;
import cookbookofbabel.minecraft.dataset.Choice.Leaf;
import cookbookofbabel.minecraft.dataset.Form;
import cookbookofbabel.minecraft.dataset.Ingredient;
import cookbookofbabel.minecraft.dataset.IngredientId;
import cookbookofbabel.minecraft.dataset.Outcome;
import cookbookofbabel.minecraft.datagen.plan.Planned.FluidIn;
import cookbookofbabel.minecraft.datagen.plan.Planned.FluidOut;
import cookbookofbabel.minecraft.datagen.plan.Planned.ItemIn;
import cookbookofbabel.minecraft.datagen.plan.Planned.ItemOut;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.statistics.Statistics;
import net.jqwik.api.statistics.StatisticsReport;

class PlannerProperties {
    /** Solids, then liquids, then water. */
    private static final List<Ingredient> INGREDIENTS = List.of(
            ingredient("s0", Form.PIECE), ingredient("s1", Form.PIECE), ingredient("s2", Form.POWDER),
            ingredient("l0", Form.LIQUID), ingredient("l1", Form.LIQUID), ingredient("water", Form.LIQUID));

    /** A step in a small world, and what other mods provide in it. */
    record Case(List<Leaf> inputs, List<IngredientId> outputs, Provided provided) {}

    /**
     * Whatever a step is, a plan for a machine stays within everything the machine allows: its counts
     * of items, of each item, item kinds, fluids and outputs; nothing it supplies itself as an input;
     * and, where it ignores NBT, only things other mods provide. Every machine plans some step, so a
     * machine the generated steps never fit can't pass unchecked.
     */
    @Property
    @StatisticsReport(StatisticsReport.StatisticsReportMode.OFF)
    void plansFitTheirMachine(@ForAll("cases") Case c) {
        var world = World.of(c);
        for (var route : RouteKind.values()) {
            if (world.plan(route) instanceof Planned p) {
                Statistics.collect(route);
                assertFits(route, p, c, world.dataset());
            }
        }
        Statistics.coverage(coverage -> Arrays.stream(RouteKind.values()).forEach(r -> coverage.check(r).count(n -> n > 0)));
    }

    private static void assertFits(RouteKind route, Planned p, Case c, Dataset dataset) {
        var limits = route.constraints;
        var items = p.inputs().stream().filter(i -> i instanceof ItemIn).map(i -> (ItemIn) i).toList();
        var fluids = p.inputs().stream().filter(i -> i instanceof FluidIn).map(i -> (FluidIn) i).toList();
        assertThat(items.size()).as("%s item kinds", route).isBetween(limits.minItemKinds(), limits.itemKinds());
        assertThat(items.stream().mapToInt(ItemIn::count).sum()).as("%s items", route).isLessThanOrEqualTo(limits.maxItemInputs());
        assertThat(items).as("%s items of each kind", route).allMatch(i -> i.count() <= limits.maxOfEachItem());
        assertThat(fluids.size()).as("%s fluids", route).isBetween(limits.minFluidInputs(), limits.maxFluidInputs());
        assertThat(p.outputs().size()).as("%s outputs", route).isLessThanOrEqualTo(limits.maxOutputs());
        assertThat(p.outputs().stream().filter(o -> o instanceof ItemOut).count()).as("%s item outputs", route)
                .isLessThanOrEqualTo(limits.maxItemOutputs());
        assertThat(p.outputs().stream().filter(o -> o instanceof FluidOut).count()).as("%s fluid outputs", route)
                .isLessThanOrEqualTo(limits.maxFluidOutputs());
        route.supplies.ifPresent(s -> assertThat(p.inputs()).as("%s supplied inputs", route).noneMatch(i -> dataset.isA(i.ingredient(), s)));
        if (!route.nbt.onFluidInputs()) {
            assertThat(fluids).as("%s fluids without NBT", route).allMatch(f -> c.provided().fluids().contains(f.ingredient()));
        }
        if (!route.nbt.onResults()) {
            assertThat(p.outputs()).as("%s outputs without NBT", route).allMatch(o -> o instanceof FluidOut
                    ? c.provided().fluids().contains(o.ingredient())
                    : c.provided().items().contains(o.ingredient()));
        }
    }

    /** The pastry dough case: a liquid that quantizes as well as a fluid should go in as one. */
    @Property
    void liquidsGoInAsFluidsWhenTheyQuantizeAsWell() {
        var inputs = List.of(
                new Leaf(INGREDIENTS.get(2).id(), new Amount(500, "g")),
                new Leaf(INGREDIENTS.get(5).id(), new Amount(100, "ml")),
                new Leaf(INGREDIENTS.get(1).id(), new Amount(250, "g")),
                new Leaf(INGREDIENTS.get(0).id(), new Amount(5, "g")));
        var world = World.of(new Case(inputs, List.of(INGREDIENTS.get(2).id()), new Provided(Set.of(), Set.of())));
        var plan = world.plan(RouteKind.CREATE_MIXING);
        assertThat(plan).as("the basin takes the dough").isInstanceOf(Planned.class);
        assertThat(((Planned) plan).inputs()).as("the water goes in as its fluid")
                .anyMatch(i -> i instanceof FluidIn f && f.ingredient().value().equals("water"));
    }

    /** What another mod provides only as an item comes out as that item, never as the mod's own fluid. */
    @Property
    void whatOnlyAnItemProvidesComesOutAsTheItem(@ForAll("cases") Case c) {
        var world = World.of(c);
        for (var route : RouteKind.values()) {
            if (world.plan(route) instanceof Planned p) {
                assertThat(p.outputs()).as("%s", route).noneMatch(o -> o instanceof FluidOut
                        && c.provided().items().contains(o.ingredient()) && !c.provided().fluids().contains(o.ingredient()));
            }
        }
    }

    /** A machine that takes one of each item, like Thermal's, is never planned more than one of an item. */
    @Property
    void unitCountMachinesTakeOneOfEachItem(@ForAll("cases") Case c) {
        var world = World.of(c);
        Arrays.stream(RouteKind.values()).filter(r -> r.constraints.maxOfEachItem() == 1).forEach(route -> {
            if (world.plan(route) instanceof Planned p) {
                assertThat(p.inputs()).as("%s", route).allMatch(i -> !(i instanceof ItemIn item) || item.count() == 1);
            }
        });
    }

    /** The case's step, as the only recipe of a world of {@link #INGREDIENTS}. */
    private record World(Dataset dataset, Planner planner, Step step) {
        static World of(Case c) {
            var outputs = c.outputs().stream()
                    .map(o -> new Outcome.Output(o, new Amount(100, "g"), Outcome.Role.MAIN, Optional.empty()))
                    .toList();
            var recipe = Worlds.recipe("r", "mix", c.inputs().stream().map(Leaf::ingredient).toList(), c.outputs());
            var dataset = new Dataset(Worlds.export(INGREDIENTS, List.of(Worlds.technique("mix")), List.of(recipe)));
            var planner = new Planner(dataset, new Techniques(dataset), List.of(recipe), c.provided());
            return new World(dataset, planner, new Step("r", recipe, c.inputs(), outputs, Optional.empty()));
        }

        Planner.Result plan(RouteKind route) {
            return planner.plan(step, route);
        }
    }

    @Provide
    Arbitrary<Case> cases() {
        var ids = INGREDIENTS.stream().map(Ingredient::id).toList();
        var inputs = Arbitraries.of(ids).set().ofMinSize(1).ofMaxSize(4)
                .flatMap(chosen -> Arbitraries.integers().between(1, 1000).list().ofSize(chosen.size())
                        .map(grams -> IntStream.range(0, grams.size())
                                .mapToObj(i -> new Leaf(new ArrayList<>(chosen).get(i), new Amount(grams.get(i), "g")))
                                .toList()));
        var outputs = Arbitraries.of(ids).list().ofMinSize(1).ofMaxSize(2).uniqueElements();
        var provided = Combinators.combine(subsets(ids), subsets(ids)).as(Provided::new);
        return Combinators.combine(inputs, outputs, provided).as(Case::new);
    }

    private static Arbitrary<Set<IngredientId>> subsets(List<IngredientId> ids) {
        return Arbitraries.of(ids).set().ofMaxSize(ids.size());
    }

    private static Ingredient ingredient(String id, Form form) {
        return new Ingredient(new IngredientId(id), id, List.of(), List.of(), form, Optional.empty(), Optional.empty(), false);
    }
}
