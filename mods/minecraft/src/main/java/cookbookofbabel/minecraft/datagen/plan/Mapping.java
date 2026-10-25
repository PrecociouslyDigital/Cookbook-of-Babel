package cookbookofbabel.minecraft.datagen.plan;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import cookbookofbabel.minecraft.dataset.Amount;
import cookbookofbabel.minecraft.dataset.IngredientId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What one mod provides for dataset ingredients, curated by hand in {@code mappings/<mod>.json}.
 * Everything in it counts only while that mod is loaded.
 */
public record Mapping(Mod mod, Map<IngredientId, Entry> entries) {
    /**
     * @param itemTags tags whose items count as the ingredient; a {@code forge:} tag brings its
     *     {@code c:} mirror along
     * @param items items to make when a step yields the ingredient, most preferred first
     * @param fluids the same, for a step that yields it as a fluid
     */
    public record Entry(List<String> itemTags, List<String> fluidTags, List<ExternalItem> items, List<String> fluids) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Codec.STRING.listOf().optionalFieldOf("itemTags", List.of()).forGetter(Entry::itemTags),
                        Codec.STRING.listOf().optionalFieldOf("fluidTags", List.of()).forGetter(Entry::fluidTags),
                        ExternalItem.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Entry::items),
                        Codec.STRING.listOf().optionalFieldOf("fluids", List.of()).forGetter(Entry::fluids))
                .apply(i, Entry::new));
    }

    /** How much of the ingredient one of the item is; by default, one item of the mod's own. */
    public record ExternalItem(String id, Optional<Amount> amount) {
        static final Codec<ExternalItem> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Codec.STRING.fieldOf("id").forGetter(ExternalItem::id),
                        Amount.CODEC.optionalFieldOf("amount").forGetter(ExternalItem::amount))
                .apply(i, ExternalItem::new));
    }

    private static final Codec<Map<IngredientId, Entry>> ENTRIES = Codec.unboundedMap(IngredientId.CODEC, Entry.CODEC);

    public Optional<Entry> entry(IngredientId ingredient) {
        return Optional.ofNullable(entries.get(ingredient));
    }

    /** One file per mod, named after it; mods without a file provide nothing. */
    public static List<Mapping> readAll(Path directory) throws IOException {
        var mappings = new java.util.ArrayList<Mapping>();
        for (var mod : Mod.values()) {
            var file = directory.resolve(mod.id + ".json");
            if (Files.exists(file)) {
                var json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                mappings.add(new Mapping(mod, ENTRIES.parse(JsonOps.INSTANCE, json).getOrThrow(false, message -> {
                    throw new IllegalStateException("can't read " + file + ": " + message);
                })));
            }
        }
        return mappings;
    }

    /** {@code forge:x} and its {@code c:x} mirror, which newer packs use. */
    public static List<String> withMirrors(List<String> tags) {
        return tags.stream()
                .flatMap(t -> t.startsWith("forge:") ? java.util.stream.Stream.of(t, "c:" + t.substring(6)) : java.util.stream.Stream.of(t))
                .distinct()
                .toList();
    }
}
