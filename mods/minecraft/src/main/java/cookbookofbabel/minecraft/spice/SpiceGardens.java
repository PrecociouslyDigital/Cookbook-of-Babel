package cookbookofbabel.minecraft.spice;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import cookbookofbabel.minecraft.dataset.IngredientId;
import cookbookofbabel.minecraft.substance.Substance;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.resources.ResourceKey;

/**
 * The spices the mod plants itself, because no mapped mod grows them, and the gardens their seeds
 * turn up in. Curated by hand in {@code cookbook_of_babel/spice_gardens.json}; datagen checks it
 * against the dataset and the mappings.
 */
public final class SpiceGardens {
    private static final String RESOURCE = "/cookbook_of_babel/spice_gardens.json";

    private static final Codec<Map<IngredientId, List<Garden>>> CODEC =
            Codec.unboundedMap(IngredientId.CODEC, Garden.CODEC.listOf());

    private static final Map<IngredientId, Set<Garden>> ALL = read();

    private SpiceGardens() {}

    /** Every planted spice, by ID, and the gardens it grows wild in. */
    public static Map<IngredientId, Set<Garden>> all() {
        return ALL;
    }

    public static List<ResourceKey<Substance>> spices() {
        return ALL.keySet().stream().map(SpiceGardens::substance).toList();
    }

    public static List<ResourceKey<Substance>> growingIn(Garden garden) {
        return ALL.entrySet().stream()
                .filter(e -> e.getValue().contains(garden))
                .map(e -> substance(e.getKey()))
                .toList();
    }

    private static ResourceKey<Substance> substance(IngredientId spice) {
        return ResourceKey.create(Substance.REGISTRY, spice.location());
    }

    private static Map<IngredientId, Set<Garden>> read() {
        try (var in = SpiceGardens.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("no " + RESOURCE);
            }
            var json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            var gardens = CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(false, message -> {
                throw new IllegalStateException("can't read " + RESOURCE + ": " + message);
            });
            var sorted = new TreeMap<IngredientId, Set<Garden>>();
            gardens.forEach((spice, where) -> {
                if (where.isEmpty()) {
                    throw new IllegalStateException(RESOURCE + " plants " + spice + " in no garden");
                }
                sorted.put(spice, Collections.unmodifiableSet(EnumSet.copyOf(where)));
            });
            return Collections.unmodifiableMap(sorted);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
