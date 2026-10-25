package cookbookofbabel.minecraft.dataset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;

/** One step: ingredients in, one technique, ingredients out. A dish is a chain of them. */
public record Recipe(
        String id,
        String source,
        TechniqueId technique,
        Choice.Select inputs,
        Outcome outputs,
        Optional<Double> temperatureC,
        End end,
        List<Issue> issues) {
    public record Issue(String field, String kind, String note) {
        static final Codec<Issue> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Codec.STRING.fieldOf("field").forGetter(Issue::field),
                        Codec.STRING.fieldOf("kind").forGetter(Issue::kind),
                        Codec.STRING.fieldOf("note").forGetter(Issue::note))
                .apply(i, Issue::new));
    }

    /** Only the temperature of the environment matters to the mod, not its medium. */
    private static final Codec<Double> TEMPERATURE =
            RecordCodecBuilder.create(i -> i.group(Codec.DOUBLE.fieldOf("temperatureC").forGetter(t -> t)).apply(i, t -> t));

    public static final Codec<Recipe> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.STRING.fieldOf("id").forGetter(Recipe::id),
                    Codec.STRING.fieldOf("source").forGetter(Recipe::source),
                    TechniqueId.CODEC.fieldOf("technique").forGetter(Recipe::technique),
                    Choice.SELECT.fieldOf("inputs").forGetter(Recipe::inputs),
                    Outcome.CODEC.fieldOf("outputs").forGetter(Recipe::outputs),
                    TEMPERATURE.optionalFieldOf("environment").forGetter(Recipe::temperatureC),
                    End.CODEC.fieldOf("end").forGetter(Recipe::end),
                    Issue.CODEC.listOf().optionalFieldOf("issues", List.of()).forGetter(Recipe::issues))
            .apply(i, Recipe::new));
}
