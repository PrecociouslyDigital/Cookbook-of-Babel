package cookbookofbabel.minecraft.clock;

import java.util.OptionalLong;

/**
 * How far one transition of a stack has got. A running clock stores the virtual tick it would have started at had it
 * always run at its current rate, so nothing needs updating while conditions hold. A clock whose rate is zero has no
 * such tick, and keeps its progress instead.
 */
public sealed interface Clock {
    /**
     * Ticks beyond which a double no longer holds every whole tick. A transition that would take longer than this
     * counts as stopped.
     */
    long HORIZON = 1L << 52;

    /**
     * The most of a transition's duration that rounding to a bucket may cost or gain it. Without this cap, a step
     * shorter than half a bucket could be done the moment it starts.
     */
    double MAX_ROUNDING = 0.01;

    /** Progress at {@code now}, given the rate it was anchored at; 1 is done. */
    double progress(long now, double rate, long ticks);

    /** The first tick at which it is done, if it ever will be at this rate. */
    OptionalLong finishesAt(long now, double rate, long ticks);

    /**
     * A clock at {@code progress} from {@code now} on, running at {@code rate}. A running clock's start is rounded to
     * the nearest whole number of {@code bucket}s, so stacks anchored around the same time carry equal tags and still
     * stack. For a short transition the bucket shrinks to {@link #MAX_ROUNDING} of its duration at this rate; that
     * depends only on the transition and the rate, so stacks of the same food anchored alike still agree.
     */
    static Clock anchored(double progress, long now, double rate, long ticks, long bucket) {
        double p = Math.max(0, progress);
        double elapsed = p * ticks / rate;
        if (!(rate > 0) || !(elapsed < HORIZON)) {
            return new Stopped(p);
        }
        long step = Math.max(1, Math.min(bucket, (long) (MAX_ROUNDING * ticks / rate)));
        return new Running(Math.round((now - elapsed) / step) * step);
    }

    record Running(long start) implements Clock {
        @Override
        public double progress(long now, double rate, long ticks) {
            return ((double) now - start) * rate / ticks;
        }

        @Override
        public OptionalLong finishesAt(long now, double rate, long ticks) {
            double exact = start + ticks / rate;
            if (!(rate > 0) || !(Math.abs(exact) < HORIZON)) {
                return OptionalLong.empty();
            }
            // Rounding can put the closed form a tick off either way; settle it by the test progress itself makes.
            long at = (long) Math.ceil(exact);
            while (progress(at - 1, rate, ticks) >= 1) {
                at--;
            }
            while (progress(at, rate, ticks) < 1) {
                at++;
            }
            return OptionalLong.of(at);
        }
    }

    record Stopped(double progress) implements Clock {
        public Stopped {
            if (!(progress >= 0)) {
                throw new IllegalArgumentException("progress " + progress + " is not a non-negative number");
            }
        }

        @Override
        public double progress(long now, double rate, long ticks) {
            return progress;
        }

        /** When a stopped clock got done can't be known, only that it is. */
        @Override
        public OptionalLong finishesAt(long now, double rate, long ticks) {
            return progress >= 1 ? OptionalLong.of(now) : OptionalLong.empty();
        }
    }
}
