package cookbookofbabel.minecraft.datagen.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cookbookofbabel.minecraft.clock.Transition;
import cookbookofbabel.minecraft.dataset.Amount;
import cookbookofbabel.minecraft.dataset.Choice;
import cookbookofbabel.minecraft.dataset.End;
import cookbookofbabel.minecraft.dataset.IngredientId;
import cookbookofbabel.minecraft.dataset.Outcome;
import cookbookofbabel.minecraft.dataset.Recipe;
import cookbookofbabel.minecraft.dataset.Sensitivity;
import cookbookofbabel.minecraft.dataset.TechniqueId;
import java.util.List;
import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.LongRange;

/**
 * Properties of the steps that happen to food while it is kept: held steps. That what datagen writes decodes and
 * round-trips is a game test, since initialising {@link Transition} needs the game's registries.
 */
class TransitionProperties {
    private static final Dataset DATASET = new Dataset(Worlds.export(
            List.of(),
            List.of(Worlds.technique("cool"), Worlds.technique("chill"), Worlds.technique("dry"), Worlds.technique("freeze", "chill"),
                    Worlds.technique("stale"), Worlds.technique("mix")),
            List.of()));
    private static final Techniques TECHNIQUES = new Techniques(DATASET);
    private static final Amounts AMOUNTS = new Amounts(DATASET);

    @Provide
    Arbitrary<Recipe> steps() {
        var technique = Arbitraries.of("cool", "chill", "dry", "freeze", "stale", "mix");
        var inputs = Arbitraries.integers().between(0, 3).map(Worlds::ingredient).set().ofMinSize(1).ofMaxSize(3);
        var outputs = Arbitraries.integers().between(4, 6).map(Worlds::ingredient).set().ofMinSize(1).ofMaxSize(2);
        var chance = Arbitraries.of(0.5).optional(0.2);
        return Combinators.combine(technique, inputs, outputs, chance, ends()).as((t, ins, outs, c, end) -> new Recipe(
                "r", "source", new TechniqueId(t),
                new Choice.Select(Optional.empty(), Optional.empty(),
                        ins.stream().<Choice>map(i -> new Choice.Leaf(i, new Amount(100, "g"))).toList()),
                new Outcome.Outputs(outs.stream().map(o -> new Outcome.Output(o, new Amount(100, "g"), Outcome.Role.MAIN, c)).toList()),
                Optional.empty(), end, List.of()));
    }

    private Arbitrary<End> ends() {
        var seconds = Arbitraries.doubles().between(0, 1e7).map(s -> new Amount(s, "s"));
        return Arbitraries.oneOf(
                Combinators.combine(seconds, sensitivities().optional(0.5)).as(End.ByTime::new),
                Arbitraries.doubles().between(-20, 250).map(End.ByCoreTemp::new),
                seconds.map(d -> new End.ByBoth(70, d)));
    }

    private Arbitrary<Sensitivity> sensitivities() {
        var bound = Arbitraries.doubles().between(-30, 300).optional(0.5);
        var activity = Arbitraries.doubles().between(0, 1).optional(0.5);
        return Combinators.combine(Arbitraries.doubles().between(-30, 300), Arbitraries.doubles().between(0.1, 10), activity, activity, bound, bound)
                .as(Sensitivity::new);
    }

    /**
     * A held step is exactly one transition from its input to its output when it takes one ingredient to one other
     * after a set time, and none otherwise; no other step is one.
     */
    @Property
    void heldStepsOfOneIngredientByTimeAreTransitions(@ForAll("steps") Recipe step) {
        var binding = TECHNIQUES.bindingFor(step.technique());
        var transition = PlannedTransition.of(step, binding, AMOUNTS);
        var inputs = Recipes.leaves(step.inputs());
        var outputs = Recipes.outputs(step.outputs());
        var qualifies = binding.transition().isPresent()
                && inputs.size() == 1
                && outputs.size() == 1
                && outputs.get(0).chance().isEmpty()
                && step.end() instanceof End.ByTime;
        assertThat(transition.isPresent()).isEqualTo(qualifies);
        transition.ifPresent(t -> {
            var end = (End.ByTime) step.end();
            assertThat(t.input()).isEqualTo(inputs.get(0).ingredient());
            assertThat(t.output()).isEqualTo(outputs.get(0).ingredient());
            assertThat(t.kind()).isEqualTo(Transition.Kind.HELD);
            assertThat(t.sensitivity()).isEqualTo(end.sensitivity().orElse(PlannedTransition.UNSTATED));
            assertThat(t.ticks()).isEqualTo(PlannedTransition.ticks(AMOUNTS.seconds(end.duration())));
        });
    }

    @Property
    void ticksArePositiveAndMonotoneInDuration(
            @ForAll @DoubleRange(min = 0, max = 1e9) double a, @ForAll @DoubleRange(min = 0, max = 1e9) double b) {
        assertThat(PlannedTransition.ticks(a)).isPositive();
        assertThat(PlannedTransition.ticks(Math.min(a, b))).isLessThanOrEqualTo(PlannedTransition.ticks(Math.max(a, b)));
    }

    /** A step into spoiled food is going off, not cooking: held or not, it makes no transition. */
    @Property
    void decayMakesNoTransition(@ForAll("steps") Recipe step) {
        var decayed = new Recipe(
                step.id(), step.source(), step.technique(), step.inputs(),
                new Outcome.Outputs(List.of(new Outcome.Output(new IngredientId("spoiled_food"), new Amount(100, "g"), Outcome.Role.MAIN, Optional.empty()))),
                step.temperatureC(), step.end(), step.issues());
        assertThat(PlannedTransition.of(decayed, TECHNIQUES.bindingFor(decayed.technique()), AMOUNTS)).isEmpty();
    }

    @Property
    void aTransitionTakesSomeTime(@ForAll @LongRange(min = Long.MIN_VALUE, max = 0) long ticks) {
        assertThatThrownBy(() -> new PlannedTransition(
                        "r", new IngredientId("bread"), new IngredientId("stale_bread"), new Amount(1, "d"), ticks,
                        PlannedTransition.UNSTATED, Transition.Kind.HELD))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
