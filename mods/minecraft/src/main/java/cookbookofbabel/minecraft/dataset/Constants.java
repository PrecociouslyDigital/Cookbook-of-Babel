package cookbookofbabel.minecraft.dataset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The dataset's judgement-call numbers that the mod reads.
 *
 * @param negligibleMassFraction an input lighter than this share of a step's inputs is too small to matter
 */
public record Constants(TempBands tempBands, double negligibleMassFraction) {
    public static final Codec<Constants> CODEC = RecordCodecBuilder.create(i -> i.group(
                    TempBands.CODEC.fieldOf("tempBands").forGetter(Constants::tempBands),
                    Codec.DOUBLE.fieldOf("negligibleMassFraction").forGetter(Constants::negligibleMassFraction))
            .apply(i, Constants::new));
}
