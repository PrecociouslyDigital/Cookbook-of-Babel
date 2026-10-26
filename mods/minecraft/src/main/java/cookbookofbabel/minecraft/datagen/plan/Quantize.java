package cookbookofbabel.minecraft.datagen.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns a step's exact amounts into whole items and millibuckets. The whole step is scaled by one
 * factor, the smallest that keeps every amount within its rounding tolerance and every count and
 * tank within its limit, so the proportions survive rounding. Nothing rounds down to zero: an amount
 * too small to matter ({@link Exact#negligible}) rounds up to one without counting against the
 * tolerance, so a pinch of salt becomes one item.
 */
public final class Quantize {
    /** How far a rounded amount may stray from its exact value, relatively. */
    public static final double TOLERANCE = 0.15;
    public static final int MAX_STACK = 64;

    static final List<Double> FACTORS = List.of(
            1 / 32.0, 1 / 24.0, 1 / 16.0, 1 / 12.0, 1 / 10.0, 1 / 8.0, 1 / 6.0, 1 / 4.0, 1 / 3.0, 1 / 2.0, 2 / 3.0, 1.0, 3 / 2.0, 2.0, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0, 12.0, 16.0);

    private Quantize() {}

    public enum Kind {
        ITEMS,
        MILLIBUCKETS
    }

    /** {@code negligible}: a small enough share of the step that rounding it up doesn't matter. */
    public record Exact(double value, Kind kind, boolean negligible) {}

    /** {@code maxItemInputs} counts items across all inputs, {@code maxOfEachItem} within one; a tank holds one fluid. */
    public record Limits(int maxItemInputs, int maxOfEachItem, int tankMillibuckets) {
        String exceeded() {
            return "doesn't fit in " + maxItemInputs + " input items, " + maxOfEachItem + " of each, and "
                    + tankMillibuckets + " mB tanks at any scale";
        }
    }

    public sealed interface Result {}

    /** {@code maxError} is the largest relative rounding error among the non-negligible amounts. */
    public record Quantized(double factor, List<Integer> inputs, List<Integer> outputs, double maxError) implements Result {
        public boolean withinTolerance() {
            return maxError <= TOLERANCE;
        }
    }

    public record TooLarge(String reason) implements Result {}

    public static Result quantize(List<Exact> inputs, List<Exact> outputs, Limits limits) {
        Optional<Quantized> best = Optional.empty();
        for (var factor : FACTORS) {
            var candidate = at(factor, inputs, outputs);
            if (!fits(candidate, inputs, outputs, limits)) {
                continue;
            }
            if (candidate.withinTolerance()) {
                return candidate;
            }
            if (best.isEmpty() || candidate.maxError() < best.get().maxError()) {
                best = Optional.of(candidate);
            }
        }
        return best.<Result>map(q -> q)
                .orElseGet(() -> new TooLarge(limits.exceeded()));
    }

    static Quantized at(double factor, List<Exact> inputs, List<Exact> outputs) {
        var errors = new ArrayList<Double>();
        var in = round(factor, inputs, errors);
        var out = round(factor, outputs, errors);
        return new Quantized(factor, in, out, errors.stream().mapToDouble(e -> e).max().orElse(0));
    }

    private static List<Integer> round(double factor, List<Exact> amounts, List<Double> errors) {
        var rounded = new ArrayList<Integer>();
        for (var amount : amounts) {
            var scaled = amount.value() * factor;
            var whole = (int) Math.max(1, Math.round(scaled));
            if (!amount.negligible()) {
                errors.add(Math.abs(whole - scaled) / scaled);
            }
            rounded.add(whole);
        }
        return rounded;
    }

    static boolean fits(Quantized q, List<Exact> inputs, List<Exact> outputs, Limits limits) {
        var items = 0;
        for (var i = 0; i < inputs.size(); i++) {
            if (inputs.get(i).kind() == Kind.ITEMS) {
                if (q.inputs().get(i) > limits.maxOfEachItem()) {
                    return false;
                }
                items += q.inputs().get(i);
            } else if (q.inputs().get(i) > limits.tankMillibuckets()) {
                return false;
            }
        }
        for (var i = 0; i < outputs.size(); i++) {
            var cap = outputs.get(i).kind() == Kind.ITEMS ? MAX_STACK : limits.tankMillibuckets();
            if (q.outputs().get(i) > cap) {
                return false;
            }
        }
        return items <= limits.maxItemInputs();
    }
}
