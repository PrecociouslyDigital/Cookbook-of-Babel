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

class PlannerProperties {
    /** Solids, then liquids, then water. */
    private static final List<Ingredient> INGREDIENTS = List.of(
            ingredient("s0", Form.PIECE), ingredient("s1", Form.PIECE), ingredient("s2", Form.POWDER),
            ingredient("l0", Form.LIQUID), ingredient("l1", Form.LIQUID), ingredient("water", Form.LIQUID));

    /** A step in a small world, and what other mods provide in it. */
    record Case(List<Leaf> inputs, List<IngredientId> outputs, Provided provided) {}

    /**
     * Whatever a step is, a plan for a machine stays within everything the machine allows: its counts
     * of items, item kinds, fluids and outputs; nothing it supplies itself as an input; and, where it
     * ignores NBT, only things other mods provide.
     */
    @Property
    void plansFitTheirMachine(@ForAll RouteKind route, @ForAll("cases") Case c) {
        var outputs = c.outputs().stream()
                .map(o -> new Outcome.Output(o, new Amount(100, "g"), Outcome.Role.MAIN, Optional.empty()))
                .toList();
        var recipe = Worlds.recipe("r", "mix", c.inputs().stream().map(Leaf::ingredient).toList(), c.outputs());
        var dataset = new Dataset(Worlds.export(INGREDIENTS, List.of(Worlds.technique("mix")), List.of(recipe)));
        var planner = new Planner(dataset, new Techniques(dataset), List.of(recipe), c.provided());
        if (!(planner.plan(new Step("r", recipe, c.inputs(), outputs, Optional.empty()), route) instanceof Planned p)) {
            return;
        }
        var limits = route.constraints;
        var items = p.inputs().stream().filter(i -> i instanceof ItemIn).map(i -> (ItemIn) i).toList();
        var fluids = p.inputs().stream().filter(i -> i instanceof FluidIn).map(i -> (FluidIn) i).toList();
        assertThat(items.size()).isBetween(limits.minItemKinds(), limits.itemKinds());
        assertThat(items.stream().mapToInt(ItemIn::count).sum()).isLessThanOrEqualTo(limits.maxItemInputs());
        assertThat(fluids.size()).isBetween(limits.minFluidInputs(), limits.maxFluidInputs());
        assertThat(p.outputs().size()).isLessThanOrEqualTo(limits.maxOutputs());
        assertThat(p.outputs().stream().filter(o -> o instanceof ItemOut).count()).isLessThanOrEqualTo(limits.maxItemOutputs());
        assertThat(p.outputs().stream().filter(o -> o instanceof FluidOut).count()).isLessThanOrEqualTo(limits.maxFluidOutputs());
        route.supplies.ifPresent(s -> assertThat(p.inputs()).noneMatch(i -> dataset.isA(i.ingredient(), s)));
        if (!route.nbt.onFluidInputs()) {
            assertThat(fluids).allMatch(f -> c.provided().fluids().contains(f.ingredient()));
        }
        if (!route.nbt.onResults()) {
            assertThat(p.outputs()).allMatch(o -> o instanceof FluidOut
                    ? c.provided().fluids().contains(o.ingredient())
                    : c.provided().items().contains(o.ingredient()));
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
        return new Ingredient(new IngredientId(id), id, List.of(), List.of(), form, Optional.empty(), false);
    }
}
