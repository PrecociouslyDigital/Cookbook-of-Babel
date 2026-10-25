package cookbookofbabel.minecraft.datagen;

import cookbookofbabel.minecraft.dataset.IngredientId;
import cookbookofbabel.minecraft.datagen.plan.RouteKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** What datagen made of each dataset step, for {@code coverage.md}. */
public final class Coverage {
    public sealed interface Outcome {}

    public record Emitted(int recipes, double maxError, boolean estimated, List<String> dropped) implements Outcome {}

    public record Rejected(String reason) implements Outcome {}

    public record Route(RouteKind route, Outcome outcome) {}

    public record Variant(String path, Optional<IngredientId> addedMedium, List<Route> routes) {}

    /** {@code note}: why the step has no routes at all, if it has none. */
    public record Step(String recipe, String technique, Optional<String> note, List<Variant> variants) {}

    private final List<Step> steps = new ArrayList<>();

    public void add(Step step) {
        steps.add(step);
    }

    List<Step> steps() {
        return steps.stream().sorted(Comparator.comparing(Step::recipe)).toList();
    }

    static String routeName(RouteKind route) {
        return route.name().toLowerCase(Locale.ROOT);
    }
}
