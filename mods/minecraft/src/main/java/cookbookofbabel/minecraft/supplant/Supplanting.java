package cookbookofbabel.minecraft.supplant;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import cookbookofbabel.minecraft.CookbookOfBabel;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import cookbookofbabel.minecraft.supplant.ToolRecipes;
import java.util.ArrayList;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * If this mod makes an item, only this mod makes it. For an ingredient the mod makes, other mods'
 * items lose every recipe but the mod's own: the item the mod's recipes make is
 * <em>supplanted</em>, and the ingredient's other items are <em>withdrawn</em>, leaving one item
 * per ingredient. {@code withdrawn.json} lists further items to withdraw: processed foods with
 * shortcut recipes that the mod neither makes nor maps.
 *
 * <p>Which item the mod's recipes make depends on the loaded mods, so the split is computed from
 * the loaded recipes. A group none of whose items those recipes make is left alone, so nothing
 * loses its only way to be made.
 */
public final class Supplanting {
    /** Generated: each ingredient a live recipe of the mod makes, and every item mapped to it. */
    private static final String GROUPS = "/cookbook_of_babel/supplanted.json";
    /** Curated: the items to withdraw, and why. */
    private static final String WITHDRAWN = "/cookbook_of_babel/withdrawn.json";

    private static final Map<ResourceLocation, String> ALL_WITHDRAWN =
            read(WITHDRAWN, Codec.unboundedMap(ResourceLocation.CODEC, Codec.STRING));

    /** The recipe manager of a data load whose tags aren't bound yet. */
    private static volatile RecipeManager loading;

    private Supplanting() {}

    /** Read lazily: datagen calls {@link #curated} before it has written {@code supplanted.json}. */
    private static final class Groups {
        static final Map<String, List<ResourceLocation>> ALL =
                read(GROUPS, Codec.unboundedMap(Codec.STRING, ResourceLocation.CODEC.listOf()));
    }

    /** Every item listed in {@code withdrawn.json}, loaded or not. */
    public static Set<ResourceLocation> curated() {
        return ALL_WITHDRAWN.keySet();
    }

    /** @param withdrawn including those {@code withdrawn.json} lists, where loaded */
    public record Split(Set<Item> supplanted, Set<Item> withdrawn) {
        /** Whether the recipe is another mod's and makes a supplanted or withdrawn item. */
        public boolean takes(Recipe<?> recipe, RegistryAccess registries) {
            if (recipe.getId().getNamespace().equals(CookbookOfBabel.ID)) {
                return false;
            }
            var result = result(recipe, registries).getItem();
            return supplanted.contains(result) || withdrawn.contains(result);
        }
    }

    public static Split split(Collection<Recipe<?>> recipes, RegistryAccess registries) {
        var made = recipes.stream()
                .filter(r -> r.getId().getNamespace().equals(CookbookOfBabel.ID))
                .map(r -> result(r, registries).getItem())
                .collect(Collectors.toSet());
        var supplanted = new HashSet<Item>();
        var withdrawn = new HashSet<Item>(loaded(ALL_WITHDRAWN.keySet()));
        for (var items : Groups.ALL.values()) {
            var loaded = loaded(items);
            var ours = loaded.stream().filter(made::contains).collect(Collectors.toSet());
            if (!ours.isEmpty()) {
                supplanted.addAll(ours);
                loaded.stream().filter(i -> !ours.contains(i)).forEach(withdrawn::add);
            }
        }
        return new Split(Set.copyOf(supplanted), Set.copyOf(withdrawn));
    }

    /**
     * Removes other mods' recipes for supplanted and withdrawn items once a server's data has
     * loaded. Tags are bound after every recipe has loaded and before any is synced. The first load
     * has no server to ask for a recipe manager, so keep the load's own from when it began.
     */
    public static void register(IEventBus forgeBus) {
        forgeBus.addListener((AddReloadListenerEvent event) -> loading = event.getServerResources().getRecipeManager());
        forgeBus.addListener((TagsUpdatedEvent event) -> {
            var manager = loading;
            if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD || manager == null) {
                return;
            }
            loading = null;
            var registries = event.getRegistryAccess();
            var split = split(manager.getRecipes(), registries);
            var kept = new ArrayList<Recipe<?>>(manager.getRecipes());
            kept.removeIf(r -> split.takes(r, registries));
            for (var i = 0; i < kept.size(); i++) {
                // Dependency tools work their meals in the grid; the rewritten recipe takes
                // their place, keeping the original's id.
                var replacement = ToolRecipes.replacement(kept.get(i), split, registries);
                if (replacement != null) {
                    kept.set(i, replacement);
                }
            }
            manager.replaceRecipes(kept);
        });
    }

    /** Some special recipes, like map cloning, have no fixed result, and a few mods throw for them. */
    public static ItemStack result(Recipe<?> recipe, RegistryAccess registries) {
        try {
            return recipe.getResultItem(registries);
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    private static Set<Item> loaded(Collection<ResourceLocation> items) {
        return items.stream()
                .filter(ForgeRegistries.ITEMS::containsKey)
                .map(ForgeRegistries.ITEMS::getValue)
                .collect(Collectors.toSet());
    }

    private static <T> T read(String resource, Codec<T> codec) {
        try (var in = Supplanting.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("no " + resource);
            }
            var json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            return codec.parse(JsonOps.INSTANCE, json).getOrThrow(false, message -> {
                throw new IllegalStateException("can't read " + resource + ": " + message);
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
