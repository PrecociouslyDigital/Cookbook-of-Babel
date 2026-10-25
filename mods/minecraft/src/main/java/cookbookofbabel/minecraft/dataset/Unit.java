package cookbookofbabel.minecraft.dataset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** {@code toBase} converts to the dimension's base unit: g, ml, piece or s. */
public record Unit(String id, Dimension dimension, double toBase) {
    public enum Dimension {
        MASS,
        VOLUME,
        COUNT,
        TIME
    }

    private static final Codec<Dimension> DIMENSION =
            Codec.STRING.xmap(s -> Dimension.valueOf(s.toUpperCase(java.util.Locale.ROOT)), d -> d.name().toLowerCase(java.util.Locale.ROOT));

    public static final Codec<Unit> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.STRING.fieldOf("id").forGetter(Unit::id),
                    DIMENSION.fieldOf("dimension").forGetter(Unit::dimension),
                    Codec.DOUBLE.fieldOf("toBase").forGetter(Unit::toBase))
            .apply(i, Unit::new));
}
