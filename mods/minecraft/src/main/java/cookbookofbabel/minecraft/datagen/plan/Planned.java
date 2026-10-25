package cookbookofbabel.minecraft.datagen.plan;

import cookbookofbabel.minecraft.dataset.IngredientId;
import java.util.List;
import java.util.Optional;

/** A step fitted to a machine: whole amounts, item or fluid each, a duration and a heat. */
public record Planned(
        RouteKind route,
        Step step,
        List<In> inputs,
        List<Out> outputs,
        int ticks,
        Heat heat,
        double maxError,
        boolean estimated,
        List<String> dropped)
        implements Planner.Result {
    public sealed interface In {
        IngredientId ingredient();
    }

    public record ItemIn(IngredientId ingredient, int count) implements In {}

    public record FluidIn(IngredientId ingredient, int millibuckets) implements In {}

    /** The first output is the step's main product. */
    public sealed interface Out {
        IngredientId ingredient();
    }

    public record ItemOut(IngredientId ingredient, int count, Optional<Double> chance) implements Out {}

    public record FluidOut(IngredientId ingredient, int millibuckets) implements Out {}

    public enum Heat {
        NONE,
        HEATED,
        SUPERHEATED
    }
}
