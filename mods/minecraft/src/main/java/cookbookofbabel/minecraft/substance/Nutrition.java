package cookbookofbabel.minecraft.substance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;

/** Per 100 g, as in the dataset; any value may be unknown. */
public record Nutrition(Optional<Double> kcal, Optional<Double> proteinG, Optional<Double> fatG, Optional<Double> carbsG) {
    public static final Codec<Nutrition> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.DOUBLE.optionalFieldOf("kcal").forGetter(Nutrition::kcal),
                    Codec.DOUBLE.optionalFieldOf("proteinG").forGetter(Nutrition::proteinG),
                    Codec.DOUBLE.optionalFieldOf("fatG").forGetter(Nutrition::fatG),
                    Codec.DOUBLE.optionalFieldOf("carbsG").forGetter(Nutrition::carbsG))
            .apply(i, Nutrition::new));
}
