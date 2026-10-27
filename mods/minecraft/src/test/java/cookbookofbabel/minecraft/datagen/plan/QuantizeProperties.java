package cookbookofbabel.minecraft.datagen.plan;

import static org.assertj.core.api.Assertions.assertThat;

import cookbookofbabel.minecraft.datagen.plan.Quantize.Exact;
import cookbookofbabel.minecraft.datagen.plan.Quantize.Kind;
import cookbookofbabel.minecraft.datagen.plan.Quantize.Quantized;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class QuantizeProperties {
    record Case(List<Exact> inputs, List<Exact> outputs, Quantize.Limits limits) {}

    /** Spread evenly over orders of magnitude, so small and large amounts appear equally often. */
    @Provide
    Arbitrary<Double> amounts() {
        return Arbitraries.doubles().between(-3, 3.3).map(e -> Math.pow(10, e));
    }

    @Provide
    Arbitrary<Quantize.Limits> limits() {
        return Combinators.combine(Arbitraries.integers().between(1, 64), Arbitraries.of(1, Quantize.MAX_STACK), Arbitraries.of(1000))
                .as(Quantize.Limits::new);
    }

    @Provide
    Arbitrary<Case> cases() {
        var exact = Combinators.combine(amounts(), Arbitraries.of(Kind.values()), Arbitraries.of(true, false, false, false))
                .as(Exact::new);
        return Combinators.combine(exact.list().ofMinSize(1).ofMaxSize(6), exact.list().ofMinSize(1).ofMaxSize(4), limits())
                .as(Case::new);
    }

    @Property
    void amountsArePositiveAndWithinLimits(@ForAll("cases") Case c) {
        if (!(Quantize.quantize(c.inputs(), c.outputs(), c.limits()) instanceof Quantized q)) {
            return;
        }
        var items = 0;
        for (var i = 0; i < c.inputs().size(); i++) {
            assertThat(q.inputs().get(i)).isPositive().isLessThanOrEqualTo(inputCap(c.inputs().get(i), c.limits()));
            if (c.inputs().get(i).kind() == Kind.ITEMS) {
                items += q.inputs().get(i);
            }
        }
        assertThat(items).isLessThanOrEqualTo(c.limits().maxItemInputs());
        for (var i = 0; i < c.outputs().size(); i++) {
            assertThat(q.outputs().get(i)).isPositive().isLessThanOrEqualTo(outputCap(c.outputs().get(i), c.limits()));
        }
    }

    /** Between any two amounts that count, the ratio survives rounding up to the reported error. */
    @Property
    void ratiosStayWithinTheReportedError(@ForAll("cases") Case c) {
        if (!(Quantize.quantize(c.inputs(), c.outputs(), c.limits()) instanceof Quantized q)) {
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

    /**
     * Adding factors drawn from the amounts never rounds a step worse, or at a larger scale, than
     * the round factors alone would.
     */
    @Property
    void neverWorseThanTheRoundFactorsAlone(@ForAll("cases") Case c) {
        var old = roundFactorsOnly(c);
        if (old.isEmpty()) {
            return;
        }
        assertThat(Quantize.quantize(c.inputs(), c.outputs(), c.limits())).isInstanceOfSatisfying(Quantized.class, q -> {
            if (old.get().withinTolerance()) {
                assertThat(q.withinTolerance()).isTrue();
                assertThat(q.factor()).isLessThanOrEqualTo(old.get().factor());
            } else {
                assertThat(q.withinTolerance() || q.maxError() <= old.get().maxError()).isTrue();
            }
        });
    }

    /**
     * If any round factor, or any amount-derived factor at or above the smallest round one, fits the
     * limits and keeps the step within tolerance, the chosen factor does too, and scales no larger.
     */
    @Property
    void findsTheSmallestFactorThatKeepsWithinTolerance(@ForAll("cases") Case c) {
        var result = Quantize.quantize(c.inputs(), c.outputs(), c.limits());
        for (var factor : everyFactorWorthTrying(c)) {
            var candidate = Quantize.at(factor, c.inputs(), c.outputs());
            if (Quantize.fits(candidate, c.inputs(), c.outputs(), c.limits()) && candidate.withinTolerance()) {
                assertThat(result).isInstanceOfSatisfying(Quantized.class, q -> {
                    assertThat(q.withinTolerance()).isTrue();
                    assertThat(q.factor()).isLessThanOrEqualTo(factor);
                });
            }
        }
    }

    @Property
    void noStepShrinksBelowTheSmallestRoundFactor(@ForAll("cases") Case c) {
        if (Quantize.quantize(c.inputs(), c.outputs(), c.limits()) instanceof Quantized q) {
            assertThat(q.factor()).isGreaterThanOrEqualTo(Quantize.SMALLEST);
        }
    }

    /** Capped so the factor that lands the amount on one is never below the smallest round factor. */
    @Provide
    Arbitrary<Double> slivers() {
        return Arbitraries.doubles().between(-3, 1.5).map(e -> Math.pow(10, e));
    }

    /**
     * However small the amount, a factor landing it on one whole item or millibucket is always
     * available, keeping it within tolerance.
     */
    @Property
    void aStepThatOnlyChangesItsInputKeepsItsAmount(
            @ForAll("slivers") double amount, @ForAll Kind in, @ForAll Kind out, @ForAll("limits") Quantize.Limits limits) {
        var result = Quantize.quantize(List.of(new Exact(amount, in, false)), List.of(new Exact(amount, out, false)), limits);
        assertThat(result).isInstanceOfSatisfying(Quantized.class, q -> {
            assertThat(q.withinTolerance()).isTrue();
            assertThat(q.inputs()).isEqualTo(q.outputs());
        });
    }

    private static Optional<Quantized> roundFactorsOnly(Case c) {
        Optional<Quantized> best = Optional.empty();
        for (var factor : Quantize.FACTORS) {
            var candidate = Quantize.at(factor, c.inputs(), c.outputs());
            if (!Quantize.fits(candidate, c.inputs(), c.outputs(), c.limits())) {
                continue;
            }
            if (candidate.withinTolerance()) {
                return Optional.of(candidate);
            }
            if (best.isEmpty() || candidate.maxError() < best.get().maxError()) {
                best = Optional.of(candidate);
            }
        }
        return best;
    }

    private static List<Double> everyFactorWorthTrying(Case c) {
        var factors = new ArrayList<>(Quantize.FACTORS);
        c.inputs().forEach(input -> landOnWholes(input, inputCap(input, c.limits()), factors));
        c.outputs().forEach(output -> landOnWholes(output, outputCap(output, c.limits()), factors));
        return factors;
    }

    private static void landOnWholes(Exact amount, int limit, List<Double> factors) {
        if (!amount.negligible()) {
            for (var n = 1; n <= limit; n++) {
                if (n / amount.value() >= Quantize.SMALLEST) {
                    factors.add(n / amount.value());
                }
            }
        }
    }

    private static int inputCap(Exact input, Quantize.Limits limits) {
        return input.kind() == Kind.ITEMS ? limits.maxOfEachItem() : limits.tankMillibuckets();
    }

    private static int outputCap(Exact output, Quantize.Limits limits) {
        return output.kind() == Kind.ITEMS ? Quantize.MAX_STACK : limits.tankMillibuckets();
    }
}
