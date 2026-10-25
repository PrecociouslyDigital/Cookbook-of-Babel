package cookbookofbabel.minecraft.datagen.plan;

import static org.assertj.core.api.Assertions.assertThat;

import cookbookofbabel.minecraft.datagen.plan.Quantize.Exact;
import cookbookofbabel.minecraft.datagen.plan.Quantize.Kind;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class QuantizeProperties {
    record Case(List<Exact> inputs, List<Exact> outputs, Quantize.Limits limits) {}

    @Provide
    Arbitrary<Case> cases() {
        var exact = Combinators.combine(
                        Arbitraries.doubles().between(0.01, 2000),
                        Arbitraries.of(Kind.values()),
                        Arbitraries.of(true, false, false, false))
                .as(Exact::new);
        var limits = Combinators.combine(Arbitraries.integers().between(1, 64), Arbitraries.of(1000))
                .as(Quantize.Limits::new);
        return Combinators.combine(exact.list().ofMinSize(1).ofMaxSize(6), exact.list().ofMinSize(1).ofMaxSize(4), limits)
                .as(Case::new);
    }

    @Property
    void amountsArePositiveAndWithinLimits(@ForAll("cases") Case c) {
        if (!(Quantize.quantize(c.inputs(), c.outputs(), c.limits()) instanceof Quantize.Quantized q)) {
            return;
        }
        var items = 0;
        for (var i = 0; i < c.inputs().size(); i++) {
            assertThat(q.inputs().get(i)).isPositive();
            if (c.inputs().get(i).kind() == Kind.ITEMS) {
                items += q.inputs().get(i);
            } else {
                assertThat(q.inputs().get(i)).isLessThanOrEqualTo(c.limits().tankMillibuckets());
            }
        }
        assertThat(items).isLessThanOrEqualTo(c.limits().maxItemInputs());
        for (var i = 0; i < c.outputs().size(); i++) {
            var cap = c.outputs().get(i).kind() == Kind.ITEMS ? Quantize.MAX_STACK : c.limits().tankMillibuckets();
            assertThat(q.outputs().get(i)).isPositive().isLessThanOrEqualTo(cap);
        }
    }

    /** Between any two amounts that count, the ratio survives rounding up to the reported error. */
    @Property
    void ratiosStayWithinTheReportedError(@ForAll("cases") Case c) {
        if (!(Quantize.quantize(c.inputs(), c.outputs(), c.limits()) instanceof Quantize.Quantized q)) {
            return;
        }
        var exact = new ArrayList<Double>();
        var rounded = new ArrayList<Integer>();
        for (var i = 0; i < c.inputs().size(); i++) {
            if (!c.inputs().get(i).negligible()) {
                exact.add(c.inputs().get(i).value() * q.factor());
                rounded.add(q.inputs().get(i));
            }
        }
        for (var i = 0; i < c.outputs().size(); i++) {
            if (!c.outputs().get(i).negligible()) {
                exact.add(c.outputs().get(i).value() * q.factor());
                rounded.add(q.outputs().get(i));
            }
        }
        var e = q.maxError();
        for (var i = 0; i < exact.size(); i++) {
            for (var j = 0; j < exact.size(); j++) {
                var ratio = (rounded.get(i) / exact.get(i)) / (rounded.get(j) / exact.get(j));
                assertThat(ratio).isBetween((1 - e) / (1 + e) - 1e-9, (1 + e) / Math.max(1e-9, 1 - e) + 1e-9);
            }
        }
    }

    /** When the result keeps within tolerance, no smaller factor would have. */
    @Property
    void noSmallerFactorKeepsWithinTolerance(@ForAll("cases") Case c) {
        if (!(Quantize.quantize(c.inputs(), c.outputs(), c.limits()) instanceof Quantize.Quantized q) || !q.withinTolerance()) {
            return;
        }
        for (var factor : Quantize.FACTORS) {
            if (factor < q.factor()) {
                var smaller = Quantize.at(factor, c.inputs(), c.outputs());
                assertThat(Quantize.fits(smaller, c.inputs(), c.outputs(), c.limits()) && smaller.withinTolerance()).isFalse();
            }
        }
    }
}
