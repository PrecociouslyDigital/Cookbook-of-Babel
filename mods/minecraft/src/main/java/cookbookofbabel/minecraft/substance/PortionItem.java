package cookbookofbabel.minecraft.substance;

import cookbookofbabel.minecraft.Lookups;
import cookbookofbabel.minecraft.Registration;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidHandlerItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link Container} holding one portion of a flowable substance. Its fluid says what it holds;
 * the substance's carrier says what it is held in, so the two can't disagree. Anything that works
 * with Forge's fluid capability, such as Create's spout and item drain, can fill and empty it.
 */
public final class PortionItem extends Item {
    public PortionItem(Properties properties) {
        super(properties);
    }

    static ItemStack filled(FluidStack fluid, int count) {
        var stack = new ItemStack(Registration.PORTION.get(), count);
        stack.getOrCreateTag().put(FluidHandlerItemStack.FLUID_NBT_KEY, fluid.writeToNBT(new CompoundTag()));
        return stack;
    }

    public static Optional<FluidStack> contents(ItemStack stack) {
        return Optional.ofNullable(stack.getTagElement(FluidHandlerItemStack.FLUID_NBT_KEY))
                .map(FluidStack::loadFluidStackFromNBT)
                .filter(fluid -> !fluid.isEmpty());
    }

    /** The vessel the portion is carried in: the carrier of the substance it names. */
    public static Optional<Container> container(ItemStack stack) {
        return Lookups.current().flatMap(lookup -> Substances.of(stack, lookup))
                .map(s -> ((Carrier.Flowable) s.value().carrier()).container());
    }

    @Override
    public Component getName(ItemStack stack) {
        return container(stack)
                .map(c -> (Component) Component.translatable(
                        getDescriptionId() + "." + c.getSerializedName(), Substances.name(stack)))
                .orElseGet(Substances::unknown);
    }

    /** Recipes that use up a portion hand back its empty container. */
    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return container(stack).isPresent();
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        return container(stack).map(c -> new ItemStack(c.empty())).orElse(ItemStack.EMPTY);
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new Handler(stack);
    }

    /** Never refilled, and only ever drained whole, so a portion is always exactly one portion. */
    private static final class Handler extends FluidHandlerItemStack {
        Handler(ItemStack stack) {
            super(stack, Container.PORTION_MB);
        }

        @Override
        public boolean canFillFluidType(FluidStack fluid) {
            return false;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return maxDrain < getFluid().getAmount() ? FluidStack.EMPTY : super.drain(maxDrain, action);
        }

        @Override
        protected void setContainerToEmpty() {
            var empty = Lookups.current().flatMap(lookup -> Substances.of(getFluid(), lookup))
                    .map(s -> ((Carrier.Flowable) s.value().carrier()).container())
                    .map(c -> new ItemStack(c.empty()));
            super.setContainerToEmpty();
            container = empty.orElse(ItemStack.EMPTY);
        }
    }
}
