package cookbookofbabel.minecraft.datagen.plan;

import cookbookofbabel.minecraft.clock.Transition;
import cookbookofbabel.minecraft.dataset.Amount;
import cookbookofbabel.minecraft.dataset.End;
import cookbookofbabel.minecraft.dataset.IngredientId;
import cookbookofbabel.minecraft.dataset.Outcome;
import cookbookofbabel.minecraft.dataset.Recipe;
import cookbookofbabel.minecraft.dataset.Sensitivity;
import java.util.Optional;
import java.util.Set;

/**
 * A dataset step that happens to food while it is kept rather than in a machine: one ingredient that becomes another
 * after a set time.
 *
 * @param duration as the dataset states it
 * @param ticks how long it takes in the game
 */
public record PlannedTransition(
        String recipe, IngredientId input, IngredientId output, Amount duration, long ticks, Sensitivity sensitivity, Transition.Kind kind) {
    /**
     * The sensitivity of a timed step that states none: food spoilage chemistry's rule of thumb,
     * about twice as fast per 10 °C warmer, so a hot room does hurry food that keeps no data of
     * its own.
     */
    static final Sensitivity UNSTATED = new Sensitivity(20, 2, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    /** Decay by any name: a step into this is going off, not cooking, and makes no transition. */
    private static final IngredientId DECAY = new IngredientId("spoiled_food");

    public PlannedTransition {
        if (ticks <= 0) {
            throw new IllegalArgumentException("a transition takes some time, not " + ticks + " ticks");
        }
    }

    /** The step as a transition, if its technique's binding makes it one and it turns one ingredient into another by time. */
    public static Optional<PlannedTransition> of(Recipe recipe, Binding binding, Amounts amounts) {
        var kind = binding.transition();
        var inputs = Recipes.leaves(recipe.inputs());
        if (kind.isEmpty()
                || inputs.size() != 1
                || !(recipe.outputs() instanceof Outcome.Outputs outputs)
                || outputs.outputs().size() != 1
                || outputs.outputs().get(0).chance().isPresent()
                || !(recipe.end() instanceof End.ByTime end)) {
            return Optional.empty();
        }
        var output = outputs.outputs().get(0).ingredient();
        if (output.equals(DECAY)) {
            return Optional.empty();
        }
        return Optional.of(new PlannedTransition(
                recipe.id(),
                inputs.get(0).ingredient(),
                output,
                end.duration(),
                ticks(amounts.seconds(end.duration())),
                end.sensitivity().orElse(UNSTATED),
                kind.get()));
    }

    /** Rounded like a machine's duration, but never down to nothing. A real-life day passes as a
     *  Minecraft day: 20 minutes, 24,000 ticks, so bread kept a week stales in about a week's keep. */
    static final double TICKS_PER_REAL_SECOND = 24_000.0 / (24d * 60 * 60);

    static long ticks(double realSeconds) {
        return Math.max(1, Math.round(TICKS_PER_REAL_SECOND * realSeconds));
    }

    public Transition transition() {
        return new Transition(input.location(), output.location(), ticks, sensitivity, kind);
    }

    public Reachability.Plan plan() {
        return new Reachability.Plan(Set.of(input), Set.of(output));
    }
}
