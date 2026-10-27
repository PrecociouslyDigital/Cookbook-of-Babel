package cookbookofbabel.minecraft.clock;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;

/**
 * What a food stack's {@code clock} tag holds: the environment it has been in since it was last anchored, and a clock
 * per transition of its ingredient, keyed by the transition's ID. Clocks only change when the environment does, so
 * stacks that were anchored alike stay equal and keep stacking.
 */
public record StackClock(Environment environment, Map<ResourceLocation, Clock> clocks) {
    /**
     * Environments are kept to this coarseness, so small swings in the air neither re-anchor food nor stop stacks made
     * a moment apart from stacking.
     */
    private static final double TEMP_STEPS_PER_C = 2;
    private static final double WATER_ACTIVITY_STEPS = 100;

    public StackClock {
        clocks = Map.copyOf(clocks);
    }

    /** Each transition at the start, from {@code now}. */
    public static StackClock start(
            Map<ResourceLocation, ? extends Timed> transitions, Environment environment, long now, long bucket) {
        var env = coarse(environment);
        return new StackClock(env, transitions.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, t -> Clock.anchored(0, now, t.getValue().rate(env), t.getValue().ticks(), bucket))));
    }

    /**
     * The same progress, carried on from {@code now} in another environment. Unchanged when the environment is the same
     * one and the transitions are the ones it already counts; a transition it doesn't count yet starts from nothing.
     */
    public StackClock reanchor(
            Map<ResourceLocation, ? extends Timed> transitions, Environment environment, long now, long bucket) {
        var env = coarse(environment);
        if (env.equals(this.environment) && clocks.keySet().equals(transitions.keySet())) {
            return this;
        }
        return new StackClock(env, transitions.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, t -> {
            var transition = t.getValue();
            return Clock.anchored(
                    progress(t.getKey(), transition, now), now, transition.rate(env), transition.ticks(), bucket);
        })));
    }

    /** How far a transition has got by {@code now}; nothing for one it doesn't count. */
    public double progress(ResourceLocation id, Timed transition, long now) {
        return Optional.ofNullable(clocks.get(id))
                .map(c -> c.progress(now, transition.rate(environment), transition.ticks()))
                .orElse(0.0);
    }

    /** The transition that will be done first if the environment holds, and when; perhaps already past. */
    public Optional<Finish> next(Map<ResourceLocation, ? extends Timed> transitions, long now) {
        return transitions.entrySet().stream()
                .flatMap(t -> finish(t.getKey(), t.getValue(), now).stream())
                .min(Finish.ORDER);
    }

    private Optional<Finish> finish(ResourceLocation id, Timed transition, long now) {
        var clock = clocks.get(id);
        if (clock == null) {
            return Optional.empty();
        }
        var at = clock.finishesAt(now, transition.rate(environment), transition.ticks());
        return at.isPresent() ? Optional.of(new Finish(id, at.getAsLong())) : Optional.empty();
    }

    /** Babel's {@code firstSpoilage}: of the transitions done by {@code now}, the one that got there first. */
    public Optional<Finish> firstDone(Map<ResourceLocation, ? extends Timed> transitions, long now) {
        return next(transitions, now).filter(f -> f.at() <= now);
    }

    public record Finish(ResourceLocation transition, long at) {
        static final Comparator<Finish> ORDER =
                Comparator.comparingLong(Finish::at).thenComparing(Finish::transition);
    }

    private static Environment coarse(Environment e) {
        return new Environment(
                Math.round(e.tempC() * TEMP_STEPS_PER_C) / TEMP_STEPS_PER_C,
                Math.round(e.waterActivity() * WATER_ACTIVITY_STEPS) / WATER_ACTIVITY_STEPS);
    }

    private static final Codec<Environment> ENVIRONMENT = RecordCodecBuilder.create(i -> i.group(
                    Codec.DOUBLE.fieldOf("tempC").forGetter(Environment::tempC),
                    Codec.DOUBLE.fieldOf("waterActivity").forGetter(Environment::waterActivity))
            .apply(i, Environment::new));

    private static final Codec<Clock.Running> RUNNING = Codec.LONG.fieldOf("start").xmap(Clock.Running::new, Clock.Running::start).codec();
    private static final Codec<Clock.Stopped> STOPPED =
            Codec.DOUBLE.fieldOf("progress").xmap(Clock.Stopped::new, Clock.Stopped::progress).codec();
    private static final Codec<Clock> CLOCK = Codec.either(RUNNING, STOPPED).xmap(
            e -> e.map(Function.identity(), Function.identity()),
            c -> c instanceof Clock.Running r ? Either.left(r) : Either.right((Clock.Stopped) c));

    public static final Codec<StackClock> CODEC = RecordCodecBuilder.create(i -> i.group(
                    ENVIRONMENT.fieldOf("environment").forGetter(StackClock::environment),
                    Codec.unboundedMap(ResourceLocation.CODEC, CLOCK).fieldOf("transitions").forGetter(StackClock::clocks))
            .apply(i, StackClock::new));
}
