package cookbookofbabel.minecraft.clock;

import cookbookofbabel.minecraft.dataset.Sensitivity;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.minecraft.resources.ResourceLocation;

/** Sensitivities, environments and transitions for properties, valid by construction. */
final class Sensitivities {
    private Sensitivities() {}

    static Arbitrary<Sensitivity> sensitivities() {
        return Combinators.combine(
                        Arbitraries.doubles().between(-10, 50),
                        Arbitraries.doubles().between(0.2, 5),
                        bounds(-30, 80),
                        bounds(0, 1))
                .as((ref, q10, temp, aw) -> new Sensitivity(ref, q10, aw.min, aw.max, temp.min, temp.max));
    }

    /** Sensitivities without bounds, so every environment has a rate. */
    static Arbitrary<Sensitivity> unbounded(double minQ10, double maxQ10) {
        return Combinators.combine(Arbitraries.doubles().between(0, 40), Arbitraries.doubles().between(minQ10, maxQ10))
                .as((ref, q10) -> new Sensitivity(
                        ref, q10, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
    }

    static Arbitrary<Environment> environments() {
        return Combinators.combine(Arbitraries.doubles().between(-40, 100), Arbitraries.doubles().between(0, 1))
                .as(Environment::new);
    }

    /** One to four transitions of one ingredient, by ID. */
    static Arbitrary<Map<ResourceLocation, Timing>> transitions(Arbitrary<Sensitivity> sensitivities, long maxTicks) {
        var transition = Combinators.combine(Arbitraries.longs().between(1, maxTicks), sensitivities)
                .as(Timing::new);
        return transition.list().ofMinSize(1).ofMaxSize(4).map(list -> {
            var byId = new TreeMap<ResourceLocation, Timing>();
            for (int i = 0; i < list.size(); i++) {
                byId.put(id("t" + i), list.get(i));
            }
            return byId;
        });
    }

    static ResourceLocation id(String path) {
        return new ResourceLocation("test", path);
    }

    private record Bounds(Optional<Double> min, Optional<Double> max) {}

    private static Arbitrary<Bounds> bounds(double lowest, double highest) {
        var bound = Arbitraries.doubles().between(lowest, highest).optional(0.5);
        return Combinators.combine(bound, bound).as((a, b) -> a.isPresent() && b.isPresent() && a.get() > b.get()
                ? new Bounds(b, a)
                : new Bounds(a, b));
    }
}
