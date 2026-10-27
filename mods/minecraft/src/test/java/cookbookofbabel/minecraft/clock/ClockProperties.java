package cookbookofbabel.minecraft.clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.Scale;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;

class ClockProperties {
    private static final double EPSILON = 1e-9;

    @Provide
    Arbitrary<Map<ResourceLocation, Timing>> transitions() {
        return Sensitivities.transitions(Sensitivities.sensitivities(), 2_000_000);
    }

    @Provide
    Arbitrary<Environment> environments() {
        return Sensitivities.environments();
    }

    @Provide
    Arbitrary<List<Change>> changes() {
        return Combinators.combine(Sensitivities.environments(), Arbitraries.longs().between(0, 100_000))
                .as(Change::new)
                .list()
                .ofMaxSize(12);
    }

    /** The environment changing to {@code to}, {@code after} ticks on. */
    record Change(Environment to, long after) {}

    @Property
    void anchoringTwiceAtTheSameRateIsTheIdentity(
            @ForAll("transitions") Map<ResourceLocation, Timing> transitions,
            @ForAll("environments") Environment environment,
            @ForAll @LongRange(min = 0, max = 1_000_000_000) long start,
            @ForAll @LongRange(min = 0, max = 1_000_000) long later,
            @ForAll @LongRange(min = 1, max = 24_000) long bucket) {
        var clock = StackClock.start(transitions, environment, start, bucket);
        assertThat(clock.reanchor(transitions, environment, start + later, bucket)).isSameAs(clock);
        for (var t : transitions.entrySet()) {
            var c = clock.clocks().get(t.getKey());
            double rate = t.getValue().rate(clock.environment());
            long now = start + later;
            double progress = c.progress(now, rate, t.getValue().ticks());
            assertThat(Clock.anchored(progress, now, rate, t.getValue().ticks(), bucket)).isEqualTo(c);
        }
    }

    @Property
    void progressIsContinuousAcrossReanchoring(
            @ForAll("transitions") Map<ResourceLocation, Timing> transitions,
            @ForAll("environments") Environment first,
            @ForAll("changes") List<Change> changes) {
        long now = 0;
        var clock = StackClock.start(transitions, first, now, 1);
        for (var change : changes) {
            now += change.after();
            var next = clock.reanchor(transitions, change.to(), now, 1);
            for (var t : transitions.entrySet()) {
                double before = clock.progress(t.getKey(), t.getValue(), now);
                double after = next.progress(t.getKey(), t.getValue(), now);
                assertThat(after).isCloseTo(before, offset(oneTick(next, t.getValue(), before)));
            }
            clock = next;
        }
    }

    @Property
    void bucketingNeverSetsProgressBackByMoreThanABucket(
            @ForAll @DoubleRange(min = 0, max = 2) double progress,
            @ForAll @LongRange(min = 0, max = 1_000_000_000) long now,
            @ForAll @DoubleRange(min = 0.001, max = 1000) @Scale(6) double rate,
            @ForAll @LongRange(min = 1, max = 2_000_000) long ticks,
            @ForAll @LongRange(min = 1, max = 24_000) long bucket) {
        double anchored = Clock.anchored(progress, now, rate, ticks, bucket).progress(now, rate, ticks);
        assertThat(anchored).isGreaterThanOrEqualTo(progress - bucket * rate / ticks - EPSILON * (1 + progress));
    }

    /** However coarse the bucket, a short transition can't be finished, or set back, by the rounding alone. */
    @Property
    void bucketingMovesProgressByAHundredthOfTheDurationOrATickAtMost(
            @ForAll @DoubleRange(min = 0, max = 2) double progress,
            @ForAll @LongRange(min = 0, max = 1_000_000_000) long now,
            @ForAll @DoubleRange(min = 0.001, max = 1000) @Scale(6) double rate,
            @ForAll @LongRange(min = 1, max = 2_000_000) long ticks,
            @ForAll @LongRange(min = 1, max = 24_000) long bucket) {
        double anchored = Clock.anchored(progress, now, rate, ticks, bucket).progress(now, rate, ticks);
        double halfATick = 0.5 * rate / ticks;
        assertThat(anchored).isCloseTo(progress, offset(
                Math.max(Clock.MAX_ROUNDING / 2, halfATick) + halfATick + EPSILON * (1 + progress)));
    }

    @Property(tries = 300)
    void theFirstToFinishWinsAsTickingThroughFinds(
            @ForAll("smallTransitions") Map<ResourceLocation, Timing> transitions,
            @ForAll("environments") Environment environment) {
        var clock = StackClock.start(transitions, environment, 0, 1);
        long horizon = 200_000;
        Optional<StackClock.Finish> ticked = Optional.empty();
        for (long now = 0; now <= horizon && ticked.isEmpty(); now++) {
            long at = now;
            ticked = transitions.entrySet().stream()
                    .filter(t -> clock.progress(t.getKey(), t.getValue(), at) >= 1)
                    .map(t -> new StackClock.Finish(t.getKey(), at))
                    .findFirst();
        }
        var next = clock.next(transitions, 0).filter(f -> f.at() <= horizon);
        assertThat(next).isEqualTo(ticked);
        ticked.ifPresent(f -> {
            assertThat(clock.firstDone(transitions, f.at())).contains(f);
            assertThat(clock.firstDone(transitions, f.at() - 1)).isEmpty();
        });
    }

    @Provide
    Arbitrary<Map<ResourceLocation, Timing>> smallTransitions() {
        return Sensitivities.transitions(Sensitivities.sensitivities(), 500);
    }

    @Property
    void theTagReadsBackAsWritten(
            @ForAll("transitions") Map<ResourceLocation, Timing> transitions,
            @ForAll("environments") Environment environment,
            @ForAll @LongRange(min = 0, max = 1_000_000_000) long now,
            @ForAll @LongRange(min = 1, max = 24_000) long bucket) {
        var clock = StackClock.start(transitions, environment, now, bucket)
                .reanchor(transitions, new Environment(environment.tempC() - 30, environment.waterActivity()), now + 5000, bucket);
        var tag = StackClock.CODEC.encodeStart(NbtOps.INSTANCE, clock).result().orElseThrow();
        assertThat(StackClock.CODEC.parse(NbtOps.INSTANCE, tag).result()).contains(clock);
    }

    /** Re-anchoring rounds the start to a whole tick, which is worth this much progress, give or take arithmetic. */
    private static double oneTick(StackClock clock, Timing transition, double progress) {
        return transition.rate(clock.environment()) / transition.ticks() + EPSILON * (1 + progress);
    }
}
