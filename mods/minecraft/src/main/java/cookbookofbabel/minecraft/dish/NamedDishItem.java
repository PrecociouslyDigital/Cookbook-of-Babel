package cookbookofbabel.minecraft.dish;

import cookbookofbabel.minecraft.dataset.Form;
import cookbookofbabel.minecraft.dataset.NamedDish;
import cookbookofbabel.minecraft.substance.Food;
import cookbookofbabel.minecraft.substance.Labels;
import cookbookofbabel.minecraft.substance.Portions;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A named dish of the dataset, registered as an item so other mods can tag and refer to it. */
public final class NamedDishItem extends Item {
    public final NamedDish dish;

    public NamedDishItem(NamedDish dish, Properties properties) {
        super(dish.nutrition()
                .flatMap(n -> Food.of(n, Portions.gramsPerItem(dish.form())))
                .map(properties::food)
                .orElse(properties));
        this.dish = dish;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatableWithFallback(getDescriptionId(stack), Labels.asName(dish.label()));
    }

    /** Whether the dish is eaten from a bowl: a bowl is consumed serving it, returned eating it. */
    public boolean servedInBowls() {
        return switch (dish.form()) {
            case LIQUID, MASH, GEL, CURD, FOAM, BATTER, PASTE -> true;
            default -> false;
        };
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return servedInBowls();
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        return servedInBowls() ? new ItemStack(Items.BOWL) : ItemStack.EMPTY;
    }
}
