package cookbookofbabel.minecraft.clock;

import cookbookofbabel.minecraft.dataset.Sensitivity;

/**
 * How many times faster than at its reference conditions a transition runs, babel's {@code Spoilage.timeAt} turned
 * into a rate: zero outside the sensitivity's bounds, which are inclusive.
 */
public final class Rate {
    private Rate() {}

    public static double of(Sensitivity s, Environment e) {
        boolean within = s.minTempC().map(min -> e.tempC() >= min).orElse(true)
                && s.maxTempC().map(max -> e.tempC() <= max).orElse(true)
                && e.waterActivity() >= s.minWaterActivity().orElse(0.0)
                && e.waterActivity() <= s.maxWaterActivity().orElse(1.0);
        return within ? Math.pow(s.q10(), (e.tempC() - s.refTempC()) / 10) : 0;
    }
}
