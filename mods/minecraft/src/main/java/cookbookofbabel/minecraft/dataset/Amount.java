package cookbookofbabel.minecraft.dataset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** A quantity in a unit of the units registry. */
public record Amount(double qty, String unit) {
    public static final Codec<Amount> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.DOUBLE.fieldOf("qty").forGetter(Amount::qty),
                    Codec.STRING.fieldOf("unit").forGetter(Amount::unit))
            .apply(i, Amount::new));
}
