package cookbookofbabel.minecraft.dataset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;

/**
 * One sourced fact about an ingredient. The mod only reads how much one of a count unit weighs;
 * nutrition comes from the export's derived {@code nutrition} instead.
 */
public record Measure(IngredientId ingredient, Optional<GramsPer> gramsPer) {
    /** One {@code unit} (a count unit, e.g. {@code piece}) weighs {@code grams}. */
    public record GramsPer(String unit, double grams) {}

    private static final Codec<Optional<GramsPer>> VALUE = RecordCodecBuilder.create(i -> i.group(
                    Codec.STRING.fieldOf("kind").forGetter(v -> v.isPresent() ? "gramsPer" : "nutrition"),
                    Codec.STRING.optionalFieldOf("unit").forGetter(v -> v.map(GramsPer::unit)),
                    Codec.DOUBLE.optionalFieldOf("grams").forGetter(v -> v.map(GramsPer::grams)))
            .apply(i, (kind, unit, grams) -> kind.equals("gramsPer") && unit.isPresent() && grams.isPresent()
                    ? Optional.of(new GramsPer(unit.get(), grams.get()))
                    : Optional.empty()));

    public static final Codec<Measure> CODEC = RecordCodecBuilder.create(i -> i.group(
                    IngredientId.CODEC.fieldOf("ingredient").forGetter(Measure::ingredient),
                    VALUE.fieldOf("value").forGetter(Measure::gramsPer))
            .apply(i, Measure::new));
}
