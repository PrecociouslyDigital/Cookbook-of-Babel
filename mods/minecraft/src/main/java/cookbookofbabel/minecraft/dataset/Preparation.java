package cookbookofbabel.minecraft.dataset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** One technique done to one ingredient, and the one ingredient the dataset says it makes. */
public record Preparation(IngredientId ingredient, TechniqueId technique, IngredientId makes) {
    public static final Codec<Preparation> CODEC = RecordCodecBuilder.create(i -> i.group(
                    IngredientId.CODEC.fieldOf("ingredient").forGetter(Preparation::ingredient),
                    TechniqueId.CODEC.fieldOf("technique").forGetter(Preparation::technique),
                    IngredientId.CODEC.fieldOf("makes").forGetter(Preparation::makes))
            .apply(i, Preparation::new));
}
