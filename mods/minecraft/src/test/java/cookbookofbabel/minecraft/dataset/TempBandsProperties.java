package cookbookofbabel.minecraft.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class TempBandsProperties {
    @Provide
    Arbitrary<TempBands> bands() {
        return Arbitraries.doubles().between(-100, 400).set().ofMaxSize(6).map(bounds -> {
            var bounded = new ArrayList<TempBands.Bounded>();
            bounds.stream().sorted().forEach(c -> bounded.add(new TempBands.Bounded("b" + bounded.size(), c)));
            return new TempBands(bounded, "top");
        });
    }

    @Property
    void bandOfPicksTheBandWhoseRangeHoldsTheTemperature(
            @ForAll("bands") TempBands bands, @ForAll double celsius) {
        var band = bands.bandOf(celsius);
        var below = Double.NEGATIVE_INFINITY;
        var above = Double.POSITIVE_INFINITY;
        for (var b : bands.bounded()) {
            if (b.id().equals(band)) {
                above = b.belowC();
                break;
            }
            below = b.belowC();
        }
        assertThat(celsius).isGreaterThanOrEqualTo(below).isLessThan(above);
    }

    @Property
    void boundsThatDoNotStrictlyIncreaseAreRejected(@ForAll("bands") TempBands bands) {
        if (bands.bounded().size() < 2) {
            return;
        }
        var swapped = new ArrayList<>(bands.bounded());
        var first = swapped.get(0);
        var second = swapped.get(1);
        swapped.set(0, new TempBands.Bounded(first.id(), second.belowC()));
        swapped.set(1, new TempBands.Bounded(second.id(), first.belowC()));
        assertThatThrownBy(() -> new TempBands(List.copyOf(swapped), bands.top()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
