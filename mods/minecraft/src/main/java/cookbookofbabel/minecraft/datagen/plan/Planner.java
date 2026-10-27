package cookbookofbabel.minecraft.datagen.plan;

import cookbookofbabel.minecraft.dataset.Amount;
import cookbookofbabel.minecraft.dataset.Choice.Leaf;
import cookbookofbabel.minecraft.dataset.IngredientId;
import cookbookofbabel.minecraft.dataset.Outcome;
import cookbookofbabel.minecraft.dataset.Outcome.Output;
import cookbookofbabel.minecraft.dataset.Outcome.Role;
import cookbookofbabel.minecraft.dataset.Recipe;
import cookbookofbabel.minecraft.datagen.plan.Planned.FluidIn;
import cookbookofbabel.minecraft.datagen.plan.Planned.FluidOut;
import cookbookofbabel.minecraft.datagen.plan.Planned.Heat;
import cookbookofbabel.minecraft.datagen.plan.Planned.ItemIn;
import cookbookofbabel.minecraft.datagen.plan.Planned.ItemOut;
import cookbookofbabel.minecraft.datagen.plan.Quantize.Exact;
import cookbookofbabel.minecraft.datagen.plan.Quantize.Kind;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Fits the dataset's steps to machines. */
public final class Planner {
    /** A real minute passes in a game second. */
    static final double TICKS_PER_REAL_SECOND = 20.0 / 60;
    /** Where a machine takes a duration and the step gives none. */
    static final double DEFAULT_TICKS = 200;
    /** Below this a step is at room temperature, and needn't be heated. */
    public static final double WARM_FROM_C = 30;
    static final double SUPERHEATED_FROM_C = 250;
    /** A tablespoon, added where a technique needs a medium the step didn't list. */
    static final Amount MEDIUM_AMOUNT = new Amount(15, "ml");
    static final int TANK_MB = 1000;
    /** Past this, rounding distorts a step beyond recognition, and the route is left out. */
    static final double MAX_ERROR = 1.0;

    private final Dataset dataset;
    private final Amounts amounts;
    private final Techniques techniques;
    private final Collection<Recipe> recipes;
    private final Provided provided;

    /** @param recipes every recipe, to trace what went into an ingredient */
    public Planner(Dataset dataset, Techniques techniques, Collection<Recipe> recipes, Provided provided) {
        this.dataset = dataset;
        this.amounts = new Amounts(dataset);
        this.techniques = techniques;
        this.recipes = recipes;
        this.provided = provided;
    }

    public sealed interface Result permits Planned, Rejected {}

    public record Rejected(String reason) implements Result {}

    /** A recipe's variants, or why it has none. */
    public sealed interface Steps {}

    public record Variants(List<Step> steps) implements Steps {}

    public record Unsupported(String reason) implements Steps {}

    public Steps steps(Recipe recipe) {
        if (!(recipe.outputs() instanceof Outcome.Outputs outputs)) {
            return new Unsupported("it yields one of several outcomes, which no route supports yet");
        }
        var combinations = Selections.expand(recipe.inputs(), Selections.MAX_VARIANTS);
        var steps = new ArrayList<Step>();
        for (var i = 0; i < combinations.size(); i++) {
            var path = recipe.id().replace('.', '/') + (combinations.size() > 1 ? "/v" + (i + 1) : "");
            var inputs = combinations.get(i);
            var medium = missingMedium(recipe, inputs).map(m -> new Leaf(m, MEDIUM_AMOUNT));
            var all = new ArrayList<>(inputs);
            medium.ifPresent(all::add);
            steps.add(new Step(path, recipe, List.copyOf(all), outputs.outputs(), medium));
        }
        return steps.isEmpty() ? new Unsupported("its inputs allow no non-empty selection") : new Variants(steps);
    }

    /**
     * The medium the technique uses up, unless an input already is one, or went into making the main
     * input: the oil hash browns were fried in is still in the pan when they're flipped.
     */
    Optional<IngredientId> missingMedium(Recipe recipe, List<Leaf> inputs) {
        return techniques.mediumFor(recipe.technique()).filter(medium -> {
            var present = inputs.stream().anyMatch(l -> dataset.isA(l.ingredient(), medium));
            var inherited = mainInput(inputs).map(main -> Recipes.provenance(recipes, main).stream()
                            .anyMatch(i -> dataset.hasIngredient(i) && dataset.isA(i, medium)))
                    .orElse(false);
            return !present && !inherited;
        });
    }

    private Optional<IngredientId> mainInput(List<Leaf> inputs) {
        return inputs.stream()
                .max(Comparator.comparingDouble(l -> amounts.grams(l.ingredient(), l.amount()).value()))
                .map(Leaf::ingredient);
    }

    public Result plan(Step step, RouteKind route) {
        var c = route.constraints;
        var inputs = step.inputs().stream()
                .filter(l -> route.supplies.filter(s -> dataset.isA(l.ingredient(), s)).isEmpty())
                .toList();
        if (inputs.isEmpty()) {
            return new Rejected("takes nothing besides the " + route.supplies.orElseThrow() + " it supplies");
        }
        var products = step.outputs().stream().filter(o -> o.role() != Role.BYPRODUCT).toList();
        var byproducts = step.outputs().stream().filter(o -> o.role() == Role.BYPRODUCT).toList();
        for (var product : products) {
            if (product.chance().isPresent() && !c.chanceOutputs()) {
                return new Rejected("can't make " + product.ingredient() + " by chance");
            }
        }
        var outputs = new ArrayList<Output>();
        var fluidOutputs = new java.util.HashSet<IngredientId>();
        var dropped = new ArrayList<String>();
        for (var output : concat(products, byproducts)) {
            var ingredient = output.ingredient();
            var nbt = route.nbt.onResults();
            var asFluid = dataset.container(ingredient).isPresent() && fluidOutputs.size() < c.maxFluidOutputs()
                    && (nbt || provided.fluids().contains(ingredient));
            var asItem = outputs.size() - fluidOutputs.size() < c.maxItemOutputs() && (nbt || provided.items().contains(ingredient));
            var fits = (asFluid || asItem) && outputs.size() < c.maxOutputs();
            var rollable = output.chance().isEmpty() || c.chanceOutputs();
            if (fits && rollable) {
                outputs.add(output);
                if (asFluid) {
                    fluidOutputs.add(ingredient);
                }
            } else if (output.role() == Role.BYPRODUCT) {
                dropped.add(ingredient.value());
            } else if (!nbt && !provided.items().contains(ingredient) && !provided.fluids().contains(ingredient)) {
                return new Rejected("can't make the mod's own " + ingredient + ", and no other mod provides it");
            } else {
                return new Rejected("makes " + products.size() + " things; at most " + c.maxOutputs() + " fit");
            }
        }

        var shape = shapeProblem(route, inputs, outputs, fluidOutputs);
        if (shape.isPresent()) {
            return new Rejected(shape.get());
        }

        var outputGrams = outputs.stream().mapToDouble(o -> amounts.grams(o.ingredient(), o.amount()).value()).sum();
        var exactOut = new ArrayList<Exact>();
        var estimated = false;
        for (var output : outputs) {
            var fluid = fluidOutputs.contains(output.ingredient());
            var converted = fluid ? amounts.millibuckets(output.ingredient(), output.amount()) : amounts.items(output.ingredient(), output.amount());
            estimated |= converted.estimated();
            exactOut.add(new Exact(converted.value(), fluid ? Kind.MILLIBUCKETS : Kind.ITEMS, negligible(output.ingredient(), output.amount(), outputGrams)));
        }

        var fit = bestFit(inputs, exactOut, route);
        if (fit.isEmpty()) {
            return new Rejected(fluidsAllowed(inputs, route).size() < c.minFluidInputs()
                    ? "needs a fluid it can take, and has none"
                    : limits(route).exceeded());
        }
        var fluidInputs = fit.get().fluids();
        var q = fit.get().quantized();
        if (q.maxError() > MAX_ERROR) {
            return new Rejected("whole items would distort it by " + Math.round(q.maxError() * 100) + "%");
        }
        estimated |= fit.get().estimated();

        var ins = new ArrayList<Planned.In>();
        for (var i = 0; i < inputs.size(); i++) {
            var id = inputs.get(i).ingredient();
            ins.add(fluidInputs.contains(id) ? new FluidIn(id, q.inputs().get(i)) : new ItemIn(id, q.inputs().get(i)));
        }
        var outs = new ArrayList<Planned.Out>();
        for (var i = 0; i < outputs.size(); i++) {
            var output = outputs.get(i);
            outs.add(fluidOutputs.contains(output.ingredient())
                    ? new FluidOut(output.ingredient(), q.outputs().get(i))
                    : new ItemOut(output.ingredient(), q.outputs().get(i), output.chance()));
        }
        return new Planned(route, step, ins, outs, ticks(route, step.recipe()), heat(route, step.recipe()), q.maxError(), estimated, dropped);
    }

    private record Fit(Set<IngredientId> fluids, Quantize.Quantized quantized, boolean estimated) {}

    /**
     * Which flowable inputs to take as fluids, where the machine has tanks: every choice is tried,
     * and the one whose rounding distorts the step least wins. Small amounts usually gain most,
     * since a fluid is measured in millibuckets but a portion is a quarter bucket.
     */
    private Optional<Fit> bestFit(List<Leaf> inputs, List<Exact> outputs, RouteKind route) {
        var c = route.constraints;
        var flowable = fluidsAllowed(inputs, route);
        var inputGrams = inputs.stream().mapToDouble(l -> amounts.grams(l.ingredient(), l.amount()).value()).sum();
        Optional<Fit> best = Optional.empty();
        for (var size = c.minFluidInputs(); size <= Math.min(c.maxFluidInputs(), flowable.size()); size++) {
            for (var chosen : Selections.subsets(flowable.size(), size)) {
                var fluids = chosen.stream().map(flowable::get).collect(Collectors.toSet());
                var itemKinds = inputs.stream().filter(l -> !fluids.contains(l.ingredient())).count();
                if (itemKinds < c.minItemKinds() || itemKinds > c.itemKinds()) {
                    continue;
                }
                var estimated = false;
                var exact = new ArrayList<Exact>();
                for (var leaf : inputs) {
                    var fluid = fluids.contains(leaf.ingredient());
                    var converted = fluid ? amounts.millibuckets(leaf.ingredient(), leaf.amount()) : amounts.items(leaf.ingredient(), leaf.amount());
                    estimated |= converted.estimated();
                    exact.add(new Exact(converted.value(), fluid ? Kind.MILLIBUCKETS : Kind.ITEMS, negligible(leaf.ingredient(), leaf.amount(), inputGrams)));
                }
                if (Quantize.quantize(exact, outputs, limits(route)) instanceof Quantize.Quantized q
                        && (best.isEmpty() || q.maxError() < best.get().quantized().maxError())) {
                    best = Optional.of(new Fit(fluids, q, estimated));
                }
            }
        }
        return best;
    }

    /** The inputs the machine could take as fluids: flowable ones, and only other mods' fluids where it ignores NBT. */
    private List<IngredientId> fluidsAllowed(List<Leaf> inputs, RouteKind route) {
        return inputs.stream()
                .map(Leaf::ingredient)
                .filter(i -> dataset.container(i).isPresent())
                .filter(i -> route.nbt.onFluidInputs() || provided.fluids().contains(i))
                .distinct()
                .toList();
    }

    private boolean negligible(IngredientId ingredient, Amount amount, double totalGrams) {
        return amounts.grams(ingredient, amount).value() < dataset.negligibleMassFraction() * totalGrams;
    }

    /** What a machine's layout rules out, beyond its counts. */
    private static Optional<String> shapeProblem(
            RouteKind route, List<Leaf> inputs, List<Output> outputs, Set<IngredientId> fluidOutputs) {
        var c = route.constraints;
        if (route == RouteKind.CREATE_ITEM_DRAIN) {
            var oneItemIn = inputs.size() == 1;
            var itemAndFluidOut = outputs.size() == 2 && fluidOutputs.size() == 1;
            return oneItemIn && itemAndFluidOut
                    ? Optional.empty()
                    : Optional.of("the item drain takes one item and yields one item and one fluid");
        }
        if (route == RouteKind.HEXEREI_MIXING_CAULDRON && (outputs.size() != 1 || !fluidOutputs.isEmpty())) {
            return Optional.of("the mixing cauldron makes exactly one item");
        }
        if (route == RouteKind.CREATE_DEPLOYING && inputs.size() != 2) {
            return Optional.of("deploying takes exactly two items");
        }
        var most = c.itemKinds() + c.maxFluidInputs();
        if (most == 1 && inputs.size() != 1) {
            return Optional.of("takes one input, not " + inputs.size());
        }
        if (most < inputs.size()) {
            return Optional.of("takes at most " + most + " inputs, not " + inputs.size());
        }
        return Optional.empty();
    }

    private int ticks(RouteKind route, Recipe recipe) {
        var c = route.constraints;
        if (!c.takesDuration()) {
            return 0;
        }
        var ticks = recipe.end().timeTaken().map(d -> ticksOf(amounts.seconds(d))).orElse(DEFAULT_TICKS);
        return (int) Math.max(c.minTicks(), Math.min(c.maxTicks(), Math.round(ticks)));
    }

    /** How long a real duration takes in the game, before any machine's limits. */
    static double ticksOf(double realSeconds) {
        return realSeconds * TICKS_PER_REAL_SECOND;
    }

    private static Quantize.Limits limits(RouteKind route) {
        return new Quantize.Limits(route.constraints.maxItemInputs(), route.constraints.maxOfEachItem(), TANK_MB);
    }

    private Heat heat(RouteKind route, Recipe recipe) {
        var temperature = recipe.temperatureC().or(() -> dataset.typicalTempC(recipe.technique()));
        var superheated = temperature.filter(t -> t >= SUPERHEATED_FROM_C).isPresent();
        var warm = temperature.filter(t -> t >= WARM_FROM_C).isPresent();
        return switch (route.heating()) {
            case NEVER -> Heat.NONE;
            case ALWAYS -> superheated ? Heat.SUPERHEATED : Heat.HEATED;
            case WHEN_WARM -> superheated ? Heat.SUPERHEATED : warm ? Heat.HEATED : Heat.NONE;
        };
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        var all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }
}
