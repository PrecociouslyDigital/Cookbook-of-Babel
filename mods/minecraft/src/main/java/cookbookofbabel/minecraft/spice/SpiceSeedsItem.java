package cookbookofbabel.minecraft.spice;

import cookbookofbabel.minecraft.Lookups;
import cookbookofbabel.minecraft.Registration;
import cookbookofbabel.minecraft.substance.Substance;
import cookbookofbabel.minecraft.substance.Substances;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Seeds of every planted spice, told apart by the spice in their {@code BlockEntityTag}. Build and
 * read them only through {@link #of} and {@link #spice}.
 */
public final class SpiceSeedsItem extends ItemNameBlockItem {
    public SpiceSeedsItem(Block crop, Properties properties) {
        super(crop, properties);
    }

    public static ItemStack of(ResourceKey<Substance> spice) {
        var stack = new ItemStack(Registration.SPICE_SEEDS.get());
        BlockItem.setBlockEntityData(stack, Registration.SPICE_CROP_ENTITY.get(), Substances.tagOf(spice));
        return stack;
    }

    public static Optional<ResourceKey<Substance>> spice(ItemStack stack) {
        return stack.is(Registration.SPICE_SEEDS.get())
                ? Optional.ofNullable(BlockItem.getBlockEntityData(stack)).flatMap(Substances::named)
                : Optional.empty();
    }

    @Override
    public Component getName(ItemStack stack) {
        return spice(stack)
                .flatMap(spice -> Lookups.current().flatMap(lookup -> lookup.lookup(Substance.REGISTRY)).flatMap(r -> r.get(spice)))
                .<Component>map(spice -> Component.translatable(getDescriptionId() + ".of", Substances.name(spice)))
                .orElseGet(() -> Component.translatable(getDescriptionId()));
    }
}
