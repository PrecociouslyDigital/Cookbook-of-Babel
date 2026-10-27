package cookbookofbabel.minecraft.datagen;

import cookbookofbabel.minecraft.dataset.IngredientId;
import cookbookofbabel.minecraft.datagen.plan.PlannedTransition;
import cookbookofbabel.minecraft.datagen.plan.Quantize;
import cookbookofbabel.minecraft.datagen.plan.RouteKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** What datagen made of each dataset step, for {@code coverage.md}. */
public final class Coverage {
    public sealed interface Outcome {
        /** How the route reads in {@code coverage.md}. */
        String describe();
    }

    public record Emitted(int recipes, double maxError, boolean estimated, List<String> dropped) implements Outcome {
        @Override
        public String describe() {
            return count(recipes)
                    + ", rounding " + percent(maxError) + (maxError > Quantize.TOLERANCE ? " ⚠" : "")
                    + (estimated ? ", estimated" : "")
                    + (dropped.isEmpty() ? "" : ", drops " + code(dropped.stream()));
        }
    }

    /** Written disabled because the game can't make some of its inputs, or anything that counts as them. */
    public record Pruned(int recipes, List<IngredientId> missing) implements Outcome {
        public Pruned {
            if (missing.isEmpty()) {
                throw new IllegalArgumentException("a pruned route misses some input");
            }
        }

        @Override
        public String describe() {
            return count(recipes) + ", disabled: the game can't make " + code(missing.stream().map(IngredientId::value));
        }
    }

    public record Rejected(String reason) implements Outcome {
        @Override
        public String describe() {
            return "none, " + reason;
        }
    }

    public record Route(RouteKind route, Outcome outcome) {}

    public record Variant(String path, Optional<IngredientId> addedMedium, List<Route> routes) {}

    /** A step as a transition, written unless the game can't make its input: then {@code missing} names it. */
    public record Kept(PlannedTransition transition, List<IngredientId> missing) {
        public boolean live() {
            return missing.isEmpty();
        }
    }

    /** {@code note}: why the step is neither done in a machine nor a transition, if it is neither. */
    public record Step(String recipe, String technique, Optional<String> note, List<Variant> variants, Optional<Kept> transition) {
        /** Whether some route wrote a recipe that loads. */
        boolean emitted() {
            return variants.stream()
                    .flatMap(v -> v.routes().stream())
                    .anyMatch(r -> r.outcome() instanceof Emitted e && e.recipes() > 0);
        }

        /** Whether the step was written as a transition. */
        boolean kept() {
            return transition.filter(Kept::live).isPresent();
        }
    }

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

    static String percent(double fraction) {
        return Math.round(fraction * 100) + "%";
    }

    private static String count(int recipes) {
        return recipes + (recipes == 1 ? " recipe" : " recipes");
    }

    static String code(Stream<String> names) {
        return names.map(n -> "`" + n + "`").collect(Collectors.joining(", "));
    }
}
