package cookbookofbabel.minecraft.datagen.recipe;

import cookbookofbabel.minecraft.BabelTags;
import cookbookofbabel.minecraft.CookbookOfBabel;
import cookbookofbabel.minecraft.Registration;
import cookbookofbabel.minecraft.dataset.IngredientId;
import cookbookofbabel.minecraft.dataset.NamedDish;
import cookbookofbabel.minecraft.datagen.plan.Dataset;
import cookbookofbabel.minecraft.substance.Substance;
import cookbookofbabel.minecraft.substance.Substances;
import java.util.ArrayList;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.CompoundIngredient;
import net.minecraftforge.common.crafting.PartialNBTIngredient;
import net.minecraftforge.fluids.FluidStack;

/** The mod's own stacks of dataset ingredients, and the recipe ingredient for each. */
public final class Carriers {
    private final Dataset dataset;
    private final HolderLookup.RegistryLookup<Substance> substances;

    public Carriers(Dataset dataset, HolderLookup.RegistryLookup<Substance> substances) {
        this.dataset = dataset;
        this.substances = substances;
    }

    public static ResourceKey<Substance> key(IngredientId ingredient) {
        return ResourceKey.create(Substance.REGISTRY, CookbookOfBabel.id(ingredient.value()));
    }

    /** The named dish's item, or the substance's item or portions. */
    public ItemStack own(IngredientId ingredient, int count) {
        return NamedDish.byId(ingredient.value())
                .map(dish -> new ItemStack(Registration.DISHES.get(dish).get(), count))
                .orElseGet(() -> Substances.stackOf(substance(ingredient), count));
    }

    public FluidStack ownFluid(IngredientId ingredient, int millibuckets) {
        return Substances.fluidOf(substance(ingredient), millibuckets)
                .orElseThrow(() -> new IllegalArgumentException(ingredient + " has no fluid form"));
    }

    /**
     * Everything that counts as the ingredient: its {@code ingredients} tag, which holds what other
     * mods provide and the named dishes below it, plus the mod's own stack of it and of every
     * substance below it in the is-a DAG.
     */
    public Ingredient accepting(IngredientId ingredient) {
        var parts = new ArrayList<Ingredient>();
        parts.add(Ingredient.of(BabelTags.ingredientItems(ingredient.value())));
        for (var descendant : dataset.descendants(ingredient)) {
            if (!dataset.ingredient(descendant).named()) {
                var stack = own(descendant, 1);
                parts.add(PartialNBTIngredient.of(stack.getItem(), stack.getTag()));
            }
        }
        return CompoundIngredient.of(parts.toArray(Ingredient[]::new));
    }

    private Holder.Reference<Substance> substance(IngredientId ingredient) {
        return substances.get(key(ingredient))
                .orElseThrow(() -> new IllegalArgumentException("no substance for " + ingredient));
    }
}
