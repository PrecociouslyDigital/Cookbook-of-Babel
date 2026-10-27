package cookbookofbabel.minecraft.clock;

import static org.assertj.core.api.Assertions.assertThat;

import cookbookofbabel.minecraft.dataset.Sensitivity;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.DoubleRange;

class RateProperties {
    @Provide
    Arbitrary<Sensitivity> sensitivities() {
        return Sensitivities.sensitivities();
    }

    @Provide
    Arbitrary<Environment> environments() {
        return Sensitivities.environments();
    }

    @Property
    void rateIsZeroExactlyOutsideTheBounds(
            @ForAll("sensitivities") Sensitivity s, @ForAll("environments") Environment e) {
        boolean outside = s.minTempC().filter(min -> e.tempC() < min).isPresent()
                || s.maxTempC().filter(max -> e.tempC() > max).isPresent()
                || s.minWaterActivity().filter(min -> e.waterActivity() < min).isPresent()
                || s.maxWaterActivity().filter(max -> e.waterActivity() > max).isPresent();
        if (outside) {
            assertThat(Rate.of(s, e)).isZero();
        } else {
            assertThat(Rate.of(s, e)).isPositive();
        }
    }

    @Property
    void boundsAreInclusive(@ForAll("sensitivities") Sensitivity s) {
        var atLower = new Environment(
                s.minTempC().or(s::maxTempC).orElse(s.refTempC()),
                s.minWaterActivity().or(s::maxWaterActivity).orElse(0.5));
        var atUpper = new Environment(
                s.maxTempC().or(s::minTempC).orElse(s.refTempC()),
                s.maxWaterActivity().or(s::minWaterActivity).orElse(0.5));
        assertThat(Rate.of(s, atLower)).isPositive();
        assertThat(Rate.of(s, atUpper)).isPositive();
    }

    @Provide
    Arbitrary<Sensitivity> quickeningWithWarmth() {
        return Sensitivities.unbounded(1, 5).filter(s -> s.q10() > 1);
    }

    @Property
    void warmerIsNeverSlowerWhenQ10ExceedsOne(
            @ForAll("quickeningWithWarmth") Sensitivity s,
            @ForAll("environments") Environment e,
            @ForAll @DoubleRange(min = 0, max = 50) double warmer) {
        var warm = new Environment(e.tempC() + warmer, e.waterActivity());
        assertThat(Rate.of(s, e)).isLessThanOrEqualTo(Rate.of(s, warm));
    }
}
