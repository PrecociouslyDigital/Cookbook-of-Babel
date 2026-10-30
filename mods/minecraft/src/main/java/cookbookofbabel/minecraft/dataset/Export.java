package cookbookofbabel.minecraft.dataset;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import cookbookofbabel.minecraft.substance.Colors;
import cookbookofbabel.minecraft.substance.Nutrition;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The parts of {@code babel export} that the mod reads. The export is the dataset with its overlays
 * applied, validated, and encoded with the dataset's own file schema.
 */
public record Export(
        List<String> licence,
        List<Source> sources,
        List<Ingredient> ingredients,
        List<Technique> techniques,
        List<Recipe> recipes,
        List<Measure> measures,
        List<Tint> colors,
        Map<IngredientId, Nutrition> nutrition,
        List<Preparation> preparations,
        List<Unit> units,
        Constants constants) {
    public static final Codec<Export> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.STRING.listOf().optionalFieldOf("licence", List.of()).forGetter(Export::licence),
                    Source.CODEC.listOf().optionalFieldOf("sources", List.of()).forGetter(Export::sources),
                    Ingredient.CODEC.listOf().optionalFieldOf("ingredients", List.of()).forGetter(Export::ingredients),
                    Technique.CODEC.listOf().optionalFieldOf("techniques", List.of()).forGetter(Export::techniques),
                    Recipe.CODEC.listOf().optionalFieldOf("recipes", List.of()).forGetter(Export::recipes),
                    Measure.CODEC.listOf().optionalFieldOf("measures", List.of()).forGetter(Export::measures),
                    Tint.CODEC.listOf().optionalFieldOf("colors", List.of()).forGetter(Export::colors),
                    Codec.unboundedMap(IngredientId.CODEC, Nutrition.CODEC)
                            .optionalFieldOf("nutrition", Map.of())
                            .forGetter(Export::nutrition),
                    Preparation.CODEC.listOf().optionalFieldOf("preparations", List.of()).forGetter(Export::preparations),
                    Unit.CODEC.listOf().optionalFieldOf("units", List.of()).forGetter(Export::units),
                    Constants.CODEC.fieldOf("constants").forGetter(Export::constants))
            .apply(i, Export::new));

    public static Export read(Path path) throws IOException {
        var json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
        return CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(false, message -> {
            throw new IllegalStateException("can't read the dataset export " + path + ": " + message);
        });
    }

    /** The colour registry as a word → RGB table, dropping entries with malformed colours. */
    public Map<String, Integer> colorTable() {
        var table = new java.util.HashMap<String, Integer>(colors.size());
        for (var tint : colors) {
            Colors.parseHex(tint.color()).ifPresent(rgb -> table.put(tint.id(), rgb));
        }
        return table;
    }
}
