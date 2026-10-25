package cookbookofbabel.minecraft.dataset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;

public record Ingredient(
        IngredientId id,
        String label,
        List<String> aliases,
        List<IngredientId> parents,
        Form form,
        Optional<Double> densityGPerMl,
        boolean named) {
    private static final Codec<IngredientId> IS_A =
            RecordCodecBuilder.create(i -> i.group(IngredientId.CODEC.fieldOf("target").forGetter(t -> t)).apply(i, t -> t));

    /** The is-a axis doesn't matter to the mod: a parent is a parent. */
    public static final Codec<Ingredient> CODEC = RecordCodecBuilder.create(i -> i.group(
                    IngredientId.CODEC.fieldOf("id").forGetter(Ingredient::id),
                    Codec.STRING.fieldOf("label").forGetter(Ingredient::label),
                    Codec.STRING.listOf().optionalFieldOf("aliases", List.of()).forGetter(Ingredient::aliases),
                    IS_A.listOf().optionalFieldOf("parents", List.of()).forGetter(Ingredient::parents),
                    Form.CODEC.fieldOf("form").forGetter(Ingredient::form),
                    Codec.DOUBLE.optionalFieldOf("densityGPerMl").forGetter(Ingredient::densityGPerMl),
                    Codec.BOOL.optionalFieldOf("named", false).forGetter(Ingredient::named))
            .apply(i, Ingredient::new));
}
