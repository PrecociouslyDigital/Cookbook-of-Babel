package cookbookofbabel.minecraft;

import net.minecraft.core.registries.Registries;
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
 *       the mod stops producing its own substance for the ingredient.
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
}
