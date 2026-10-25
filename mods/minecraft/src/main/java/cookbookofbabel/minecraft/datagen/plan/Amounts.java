package cookbookofbabel.minecraft.datagen.plan;

import cookbookofbabel.minecraft.dataset.Amount;
import cookbookofbabel.minecraft.dataset.IngredientId;
import cookbookofbabel.minecraft.dataset.Unit.Dimension;
import cookbookofbabel.minecraft.substance.Container;

/**
 * Converts the dataset's amounts into what Minecraft counts: items, or millibuckets of a fluid. 1 ml
 * is 1 mB (a bulk volume, for powders), so a litre is a bucket.
 */
public final class Amounts {
    /** Where the dataset gives no density, assume water's, and say so. */
    private static final double DEFAULT_DENSITY_G_PER_ML = 1.0;

    private final Dataset dataset;

    public Amounts(Dataset dataset) {
        this.dataset = dataset;
    }

    /** A converted amount; {@code estimated} when a missing density or piece mass was assumed. */
    public record Converted(double value, boolean estimated) {}

    public Converted grams(IngredientId ingredient, Amount amount) {
        var unit = dataset.unit(amount.unit());
        var base = amount.qty() * unit.toBase();
        return switch (unit.dimension()) {
            case MASS -> new Converted(base, false);
            case VOLUME -> {
                var density = dataset.ingredient(ingredient).densityGPerMl();
                yield new Converted(base * density.orElse(DEFAULT_DENSITY_G_PER_ML), density.isEmpty());
            }
            case COUNT -> new Converted(
                    base * dataset.gramsPerItem(ingredient),
                    dataset.statedGramsPerPiece(ingredient).isEmpty());
            case TIME -> throw new IllegalArgumentException("a time is not an amount of " + ingredient);
        };
    }

    public Converted millibuckets(IngredientId ingredient, Amount amount) {
        var unit = dataset.unit(amount.unit());
        if (unit.dimension() == Dimension.VOLUME) {
            return new Converted(amount.qty() * unit.toBase(), false);
        }
        var grams = grams(ingredient, amount);
        var density = dataset.ingredient(ingredient).densityGPerMl();
        return new Converted(
                grams.value() / density.orElse(DEFAULT_DENSITY_G_PER_ML), grams.estimated() || density.isEmpty());
    }

    /** Counted amounts are one item each; anything else is divided into portions. */
    public Converted items(IngredientId ingredient, Amount amount) {
        var unit = dataset.unit(amount.unit());
        if (dataset.container(ingredient).isPresent()) {
            var mb = millibuckets(ingredient, amount);
            return new Converted(mb.value() / Container.PORTION_MB, mb.estimated());
        }
        if (unit.dimension() == Dimension.COUNT) {
            return new Converted(amount.qty() * unit.toBase(), false);
        }
        var grams = grams(ingredient, amount);
        return new Converted(grams.value() / dataset.gramsPerItem(ingredient), grams.estimated());
    }

    public double seconds(Amount duration) {
        var unit = dataset.unit(duration.unit());
        if (unit.dimension() != Dimension.TIME) {
            throw new IllegalArgumentException("not a duration: " + duration);
        }
        return duration.qty() * unit.toBase();
    }
}
