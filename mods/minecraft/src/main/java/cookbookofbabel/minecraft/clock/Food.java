package cookbookofbabel.minecraft.clock;

import cookbookofbabel.minecraft.CookbookOfBabel;
import cookbookofbabel.minecraft.Registration;
import cookbookofbabel.minecraft.dataset.NamedDish;
import cookbookofbabel.minecraft.dish.NamedDishItem;
import cookbookofbabel.minecraft.substance.Substance;
import cookbookofbabel.minecraft.substance.Substances;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * A stack as the dataset ingredient it is, a substance or a named dish, by the ID transitions name it by.
 *
 * @param waterActivity its own, before any drying by the air
 */
public record Food(ResourceLocation id, double waterActivity) {
    /**
     * For food the dataset gives no water activity: fresh food's typical 0.95–0.99, from the moist end, so food of
     * unknown moisture spoils as fresh food would rather than keeping forever.
     */
    public static final double UNKNOWN_WATER_ACTIVITY = 0.97;

    public static Optional<Food> of(ItemStack stack, HolderLookup.Provider registries) {
        if (stack.getItem() instanceof NamedDishItem dish) {
            return Optional.of(new Food(idOf(dish.dish), known(dish.dish.waterActivity())));
        }
        return Substances.of(stack, registries)
                .map(s -> new Food(s.key().location(), known(s.value().waterActivity())));
    }

    /** {@code count} of the ingredient with this ID: its named dish, or its substance's item or portions. */
    public static Optional<ItemStack> stackOf(
            ResourceLocation id, int count, HolderLookup.RegistryLookup<Substance> substances) {
        var dish = dishOf(id);
        if (dish.isPresent()) {
            return dish.map(d -> new ItemStack(Registration.DISHES.get(d).get(), count));
        }
        return substances.get(ResourceKey.create(Substance.REGISTRY, id)).map(s -> Substances.stackOf(s, count));
    }

    public static Optional<Component> name(ResourceLocation id, HolderLookup.Provider registries) {
        var dish = dishOf(id);
        if (dish.isPresent()) {
            return dish.map(d -> new ItemStack(Registration.DISHES.get(d).get()).getHoverName());
        }
        return registries.lookup(Substance.REGISTRY)
                .flatMap(r -> r.get(ResourceKey.create(Substance.REGISTRY, id)))
                .map(Substances::name);
    }

    public static ResourceLocation idOf(NamedDish dish) {
        return CookbookOfBabel.id(dish.id());
    }

    private static Optional<NamedDish> dishOf(ResourceLocation id) {
        return id.getNamespace().equals(CookbookOfBabel.ID) ? NamedDish.byId(id.getPath()) : Optional.empty();
    }

    private static double known(Optional<Double> waterActivity) {
        return waterActivity.orElse(UNKNOWN_WATER_ACTIVITY);
    }
}
