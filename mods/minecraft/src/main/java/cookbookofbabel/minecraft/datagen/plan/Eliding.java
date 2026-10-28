package cookbookofbabel.minecraft.datagen.plan;

import cookbookofbabel.minecraft.dataset.Amount;
import cookbookofbabel.minecraft.dataset.Choice.Leaf;
import cookbookofbabel.minecraft.dataset.IngredientId;
import cookbookofbabel.minecraft.dataset.Outcome;
import cookbookofbabel.minecraft.dataset.Outcome.Output;
import cookbookofbabel.minecraft.dataset.Recipe;
import cookbookofbabel.minecraft.dataset.TechniqueId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Discards crafting-grid steps that turn one required ingredient into exactly one other. The
 * dataset keeps them because a cook shapes, folds and coats; here they would be no-op crafts. The
 * step's input and output become one ingredient. The output keeps its name when the input is the
 * previous step's private intermediate (made by one step, used only by this one, not pinned), so a
 * dish stays named; otherwise the input keeps its name and users of the output use it, with amounts
 * rescaled. Discarding one step can expose the next, so this repeats until nothing changes.
 */
public final class Eliding {
    private Eliding() {}

    /** @param dropped recipes that eliding left the same as a kept one, so no machine gets two */
    public record Result(List<Recipe> recipes, List<Elided> elided, List<Merging.Dropped> dropped) {}

    /** @param kept which of {@code input} and {@code output} the two became */
    public record Elided(String recipe, IngredientId input, IngredientId output, IngredientId kept) {}

    /** How many of {@code per} an amount of the ingredient is, unless telling needs an estimate. */
    @FunctionalInterface
    public interface Ratio {
        Optional<Double> of(IngredientId ingredient, Amount amount, Amount per);
    }

    /**
     * @param crafted whether a technique is done on a crafting grid, among other places
     * @param pinned ingredients that must keep existing: named dishes, mapped ingredients and
     *     planted spices
     */
    public static Result elide(
            List<Recipe> recipes,
            Predicate<TechniqueId> crafted,
            Set<IngredientId> pinned,
            Ratio ratio,
            UnaryOperator<TechniqueId> machineOf) {
        var current = recipes.stream().sorted(Comparator.comparing(Recipe::id)).toList();
        var elided = new ArrayList<Elided>();
        var dropped = new ArrayList<Merging.Dropped>();
        var changed = true;
        while (changed) {
            var pass = new Pass(current, pinned, ratio);
            current.stream()
                    .filter(r -> crafted.test(r.technique()))
                    .forEach(r -> pass.elide(r).ifPresent(elided::add));
            var distinct = Merging.distinct(pass.result(), machineOf);
            dropped.addAll(distinct.dropped());
            changed = !pass.rewritten.isEmpty() || !distinct.dropped().isEmpty();
            current = distinct.recipes();
        }
        return new Result(current, List.copyOf(elided), List.copyOf(dropped));
    }

    /** A step that turns one required ingredient into one other. */
    private record Single(Recipe step, Leaf input, Output output) {
        static Optional<Single> of(Recipe r) {
            var inputs = r.inputs();
            if (inputs.of().size() != 1 || inputs.least() != 1 || !(inputs.of().get(0) instanceof Leaf input)) {
                return Optional.empty();
            }
            if (!(r.outputs() instanceof Outcome.Outputs outputs) || outputs.outputs().size() != 1) {
                return Optional.empty();
            }
            var output = outputs.outputs().get(0);
            return output.chance().isPresent() ? Optional.empty() : Optional.of(new Single(r, input, output));
        }
    }

    /**
     * One sweep over the recipes, indexed once. A step is only elided while none of the recipes it
     * would rewrite was rewritten earlier in the sweep, so the index stays true for it; the rest wait
     * for the next sweep.
     */
    private static final class Pass {
        private final List<Recipe> recipes;
        private final Set<IngredientId> pinned;
        private final Ratio ratio;
        private final Map<IngredientId, List<Recipe>> producers = new HashMap<>();
        private final Map<IngredientId, List<Recipe>> consumers = new HashMap<>();
        /** By recipe ID: the new recipe, or nothing for a discarded step. */
        final Map<String, Optional<Recipe>> rewritten = new HashMap<>();

        Pass(List<Recipe> recipes, Set<IngredientId> pinned, Ratio ratio) {
            this.recipes = recipes;
            this.pinned = pinned;
            this.ratio = ratio;
            for (var r : recipes) {
                Recipes.outputs(r.outputs()).stream().map(Output::ingredient).distinct()
                        .forEach(i -> producers.computeIfAbsent(i, k -> new ArrayList<>()).add(r));
                Recipes.leaves(r.inputs()).stream().map(Leaf::ingredient).distinct()
                        .forEach(i -> consumers.computeIfAbsent(i, k -> new ArrayList<>()).add(r));
            }
        }

        List<Recipe> result() {
            return recipes.stream()
                    .flatMap(r -> rewritten.getOrDefault(r.id(), Optional.of(r)).stream())
                    .toList();
        }

        Optional<Elided> elide(Recipe r) {
            return Single.of(r).filter(s -> !rewritten.containsKey(r.id())).flatMap(s -> unchanged(s).or(() -> intoOutput(s)).or(() -> intoInput(s)));
        }

        /** A step that makes what it takes, as merging can leave one. */
        private Optional<Elided> unchanged(Single s) {
            var input = s.input().ingredient();
            if (!input.equals(s.output().ingredient())) {
                return Optional.empty();
            }
            rewritten.put(s.step().id(), Optional.empty());
            return Optional.of(new Elided(s.step().id(), input, input, input));
        }

        /** The input's only maker makes the output instead, when this step is its only user. */
        private Optional<Elided> intoOutput(Single s) {
            var input = s.input().ingredient();
            var made = producers.getOrDefault(input, List.of());
            if (pinned.contains(input) || made.size() != 1 || !consumers.get(input).equals(List.of(s.step()))) {
                return Optional.empty();
            }
            var maker = made.get(0);
            var output = s.output();
            if (maker == s.step() || rewritten.containsKey(maker.id()) || Recipes.produces(maker, output.ingredient())) {
                return Optional.empty();
            }
            var makes = Recipes.outputs(maker.outputs()).stream().filter(o -> o.ingredient().equals(input)).toList();
            if (makes.stream().anyMatch(o -> ratio.of(input, o.amount(), s.input().amount()).isEmpty())) {
                return Optional.empty();
            }
            rewritten.put(maker.id(), Optional.of(Recipes.map(maker, l -> l, o -> !o.ingredient().equals(input) ? o
                    : new Output(output.ingredient(), scaled(output.amount(), ratio.of(input, o.amount(), s.input().amount())), o.role(), o.chance()))));
            rewritten.put(s.step().id(), Optional.empty());
            return Optional.of(new Elided(s.step().id(), input, output.ingredient(), output.ingredient()));
        }

        /** What used the output uses the input instead. */
        private Optional<Elided> intoInput(Single s) {
            var input = s.input();
            var output = s.output().ingredient();
            var users = consumers.getOrDefault(output, List.of());
            if (pinned.contains(output) || !producers.get(output).equals(List.of(s.step()))) {
                return Optional.empty();
            }
            for (var user : users) {
                if (user == s.step() || rewritten.containsKey(user.id()) || Recipes.consumes(user, input.ingredient())) {
                    return Optional.empty();
                }
                if (Recipes.leaves(user.inputs()).stream()
                        .anyMatch(l -> l.ingredient().equals(output) && ratio.of(output, l.amount(), s.output().amount()).isEmpty())) {
                    return Optional.empty();
                }
            }
            for (var user : users) {
                rewritten.put(user.id(), Optional.of(Recipes.map(user, l -> !l.ingredient().equals(output) ? l
                        : new Leaf(input.ingredient(), scaled(input.amount(), ratio.of(output, l.amount(), s.output().amount()))), o -> o)));
            }
            rewritten.put(s.step().id(), Optional.empty());
            return Optional.of(new Elided(s.step().id(), input.ingredient(), output, input.ingredient()));
        }

        private static Amount scaled(Amount amount, Optional<Double> times) {
            return new Amount(amount.qty() * times.orElseThrow(), amount.unit());
        }
    }
}
