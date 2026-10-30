package cookbookofbabel.minecraft.dataset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One food word's tint from the dataset's colour registry. An ingredient whose label or aliases
 * carry the word tints with the colour (plus a per-ingredient jitter), so "tomato paste, dissolve"
 * and "tomato, crushed" tint alike.
 */
public record Tint(String id, String color) {
    public static final Codec<Tint> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.STRING.fieldOf("id").forGetter(Tint::id),
                    Codec.STRING.fieldOf("color").forGetter(Tint::color))
            .apply(i, Tint::new));
}
