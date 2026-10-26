package cookbookofbabel.minecraft;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;

/**
 * The tags the mod's recipes are written against, one set per dataset ingredient.
 *
 * <ul>
 *   <li>{@code ingredients/<id>}: everything that counts as the ingredient, its is-a descendants
 *       included.
 *   <li>{@code external/<id>}: only what other mods and packs provide for it. While it is non-empty
 *       the mod stops producing its own substance or dish for the ingredient, and hides it.
 * </ul>
 */
public final class BabelTags {
    private BabelTags() {}

    public static TagKey<Item> ingredientItems(String ingredient) {
        return TagKey.create(Registries.ITEM, CookbookOfBabel.id("ingredients/" + ingredient));
    }

    public static TagKey<Fluid> ingredientFluids(String ingredient) {
        return TagKey.create(Registries.FLUID, CookbookOfBabel.id("ingredients/" + ingredient));
    }

    public static TagKey<Item> externalItems(String ingredient) {
        return TagKey.create(Registries.ITEM, CookbookOfBabel.id("external/" + ingredient));
    }

    public static TagKey<Fluid> externalFluids(String ingredient) {
        return TagKey.create(Registries.FLUID, CookbookOfBabel.id("external/" + ingredient));
    }

    /** Whether other mods or packs provide the ingredient, so the mod's own stack of it is hidden. */
    public static boolean providedElsewhere(HolderLookup.Provider registries, String ingredient) {
        return filled(registries, Registries.ITEM, externalItems(ingredient))
                || filled(registries, Registries.FLUID, externalFluids(ingredient));
    }

    private static <T> boolean filled(HolderLookup.Provider registries, ResourceKey<? extends Registry<? extends T>> registry, TagKey<T> tag) {
        return registries.lookup(registry).flatMap(r -> r.get(tag)).map(set -> set.size() > 0).orElse(false);
    }
}
