package cookbookofbabel.minecraft.compat.brewinandchewin;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;

class KegTemperatureProperties {
    @Example
    void eachLevelsTypicalTemperatureIsInThatLevel() {
        for (var level : KegTemperature.values()) {
            assertThat(KegTemperature.of(level.typicalC)).isEqualTo(level);
            assertThat(KegTemperature.ofLevel(level.level)).contains(level);
        }
    }

    @Property
    void warmerIsNeverALowerLevel(
            @ForAll @DoubleRange(min = -100, max = 200) double celsius,
            @ForAll @DoubleRange(min = 0, max = 100) double warmer) {
        assertThat(KegTemperature.of(celsius + warmer).level).isGreaterThanOrEqualTo(KegTemperature.of(celsius).level);
    }
}
