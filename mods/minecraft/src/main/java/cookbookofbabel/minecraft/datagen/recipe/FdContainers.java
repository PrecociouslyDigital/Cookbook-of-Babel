package cookbookofbabel.minecraft.datagen.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The vessel each other mod's cooking-pot meal is served in, from the pot recipes those mods ship.
 * Our pot recipes for another mod's item cost the same container to serve as its own recipes do:
 * absent a container in its recipes, the meal serves container-free, as FD's do.
 */
final class FdContainers {
    private static final String TYPE = "farmersdelight:cooking";
    private static volatile Map<String, Item> byResult;

    private FdContainers() {}

    /** The container to serve {@code result} in, if its mod's pot recipes name one. */
    static Optional<Item> of(ResourceLocation result) {
        return Optional.ofNullable(scan().get(result.toString()));
    }

    private static Map<String, Item> scan() {
        var scanned = byResult;
        if (scanned == null) {
            synchronized (FdContainers.class) {
                if (byResult == null) {
                    byResult = read();
                }
                scanned = byResult;
            }
        }
        return scanned;
    }

    private static Map<String, Item> read() {
        var map = new HashMap<String, Item>();
        for (var mod : ModList.get().getModFiles()) {
            var path = mod.getFile().getFilePath();
            if (path == null || path.toString().isEmpty() || !Files.isRegularFile(path)) {
                continue; // Locators may name nothing at all, or an exploded directory.
            }
            try (var zip = new ZipFile(path.toFile())) {
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    if (!entry.isDirectory() && entry.getName().startsWith("data/")
                            && entry.getName().contains("/recipes/") && entry.getName().endsWith(".json")) {
                        readRecipe(zip, entry, map);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("can't scan " + path + " for pot recipes", e);
            }
        }
        return Map.copyOf(map);
    }

    private static void readRecipe(ZipFile zip, ZipEntry entry, Map<String, Item> map) {
        String text;
        try (var in = zip.getInputStream(entry)) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("can't read " + entry.getName(), e);
        }
        if (!text.contains(TYPE)) {
            return;
        }
        try {
            var json = JsonParser.parseString(text).getAsJsonObject();
            if (!json.has("type") || !json.get("type").getAsString().equals(TYPE)
                    || !json.has("result") || !json.get("result").getAsJsonObject().has("item")) {
                return;
            }
            var result = item(json.getAsJsonObject("result").get("item").getAsString());
            if (json.has("container") && json.get("container").getAsJsonObject().has("item")) {
                var container = item(json.getAsJsonObject("container").get("item").getAsString());
                map.putIfAbsent(result.toString(), container);
            }
        } catch (Exception ignored) {
            // Not our recipe format, or not parseable offline: other loaders can write stranger JSON.
        }
    }

    private static Item item(String id) {
        var key = new ResourceLocation(id);
        if (!ForgeRegistries.ITEMS.containsKey(key)) {
            throw new IllegalArgumentException("no item " + id + " in " + TYPE + " recipes");
        }
        return ForgeRegistries.ITEMS.getValue(key);
    }
}
