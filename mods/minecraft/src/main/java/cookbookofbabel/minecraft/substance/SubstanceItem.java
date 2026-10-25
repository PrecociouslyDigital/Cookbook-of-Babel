package cookbookofbabel.minecraft.substance;

import cookbookofbabel.minecraft.Lookups;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Every solid substance, told apart by the substance in its NBT. */
public final class SubstanceItem extends Item {
    /**
     * Vanilla only asks a stack for its food when its item is edible, so the item is edible and a
     * substance without known energy offers this, which {@link #use} refuses to eat.
     */
    private static final FoodProperties NOTHING = new FoodProperties.Builder().build();

    public SubstanceItem(Properties properties) {
        super(properties.food(NOTHING));
    }

    @Override
    public Component getName(ItemStack stack) {
        return Substances.name(stack);
    }

    @Override
    public FoodProperties getFoodProperties(ItemStack stack, @Nullable LivingEntity entity) {
        var lookup = entity != null ? Optional.<HolderLookup.Provider>of(entity.level().registryAccess()) : Lookups.current();
        return lookup.flatMap(l -> foodOf(stack, l)).orElse(NOTHING);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        return foodOf(stack, level.registryAccess()).isPresent()
                ? super.use(level, player, hand)
                : InteractionResultHolder.pass(stack);
    }

    private static Optional<FoodProperties> foodOf(ItemStack stack, HolderLookup.Provider lookup) {
        return Substances.of(stack, lookup).flatMap(s -> s.value().nutrition()
                .flatMap(n -> Food.of(n, Portions.gramsPerItem(s.value().form()))));
    }
}
