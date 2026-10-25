package cookbookofbabel.minecraft.datagen.plan;

import cookbookofbabel.minecraft.dataset.IngredientId;
import cookbookofbabel.minecraft.dataset.Outcome;
import cookbookofbabel.minecraft.dataset.Recipe;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Skips steps that should just happen over time, like cooling, until the mod has a time mechanic.
 * A skipped step takes one ingredient to one other, and the recipe graph is rewired around it so
 * that everything it led to stays reachable.
 */
public final class Elision {
    private Elision() {}

    public enum Rewire {
        /** The steps that made its input make its output instead. */
        PRODUCERS_MAKE_OUTPUT,
        /** The steps that used its output use its input instead. */
        CONSUMERS_TAKE_INPUT
    }

    public record Elided(String recipe, IngredientId input, IngredientId output, Rewire rewire) {}

    /** {@code kept}: elidable steps that couldn't be skipped without losing what they make. */
    public record Result(List<Recipe> recipes, List<Elided> elided, List<String> kept) {}

    /** @param elidable whether a step's technique is one that should just happen over time */
    public static Result elide(List<Recipe> recipes, Predicate<Recipe> elidable) {
        var current = new ArrayList<>(recipes);
        var elided = new ArrayList<Elided>();
        var kept = new ArrayList<String>();
        for (var next = nextElidable(current, elidable, kept); next.isPresent(); next = nextElidable(current, elidable, kept)) {
            var step = next.get();
            var others = current.stream().filter(r -> r != step).toList();
            var input = Recipes.leaves(step.inputs()).get(0).ingredient();
            var output = Recipes.outputs(step.outputs()).get(0).ingredient();
            var rewire = rewire(others, input, output);
            if (rewire.isEmpty()) {
                kept.add(step.id());
                continue;
            }
            current.remove(step);
            current.replaceAll(r -> rewire.get() == Rewire.PRODUCERS_MAKE_OUTPUT
                    ? Recipes.renameOutputs(r, input, output)
                    : Recipes.renameInputs(r, output, input));
            elided.add(new Elided(step.id(), input, output, rewire.get()));
        }
        return new Result(List.copyOf(current), List.copyOf(elided), List.copyOf(kept));
    }

    /**
     * Producers can make the output instead only if nothing else needs the input. Consumers can take
     * the input instead only if something consumes the output, so a final product is still made, and
     * nothing else makes it, so no other way to it is cut off.
     */
    private static Optional<Rewire> rewire(List<Recipe> others, IngredientId input, IngredientId output) {
        var inputMadeElsewhere = others.stream().anyMatch(r -> Recipes.produces(r, input));
        var inputUsedElsewhere = others.stream().anyMatch(r -> Recipes.consumes(r, input));
        if (inputMadeElsewhere && !inputUsedElsewhere) {
            return Optional.of(Rewire.PRODUCERS_MAKE_OUTPUT);
        }
        var outputUsedElsewhere = others.stream().anyMatch(r -> Recipes.consumes(r, output));
        var outputMadeElsewhere = others.stream().anyMatch(r -> Recipes.produces(r, output));
        return outputUsedElsewhere && !outputMadeElsewhere ? Optional.of(Rewire.CONSUMERS_TAKE_INPUT) : Optional.empty();
    }

    private static Optional<Recipe> nextElidable(List<Recipe> recipes, Predicate<Recipe> elidable, List<String> kept) {
        return recipes.stream()
                .filter(elidable)
                .filter(Elision::oneToOne)
                .filter(r -> !kept.contains(r.id()))
                .min(Comparator.comparing(Recipe::id));
    }

    private static boolean oneToOne(Recipe recipe) {
        return Recipes.leaves(recipe.inputs()).size() == 1
                && recipe.outputs() instanceof Outcome.Outputs o
                && o.outputs().size() == 1;
    }
}
