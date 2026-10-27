package cookbookofbabel.minecraft.clock;

import cookbookofbabel.minecraft.dataset.Sensitivity;

/** Something that takes {@code ticks} at its sensitivity's reference conditions, and longer or shorter elsewhere. */
public interface Timed {
    long ticks();

    Sensitivity sensitivity();

    default double rate(Environment environment) {
        return Rate.of(sensitivity(), environment);
    }
}
