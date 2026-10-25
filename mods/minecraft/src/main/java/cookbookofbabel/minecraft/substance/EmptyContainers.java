package cookbookofbabel.minecraft.substance;

import cookbookofbabel.minecraft.CookbookOfBabel;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import org.jetbrains.annotations.Nullable;

/**
 * Gives every empty {@link Container} (glass bottles, paper) a fluid handler that fills it with one
 * portion of a substance carried in that container, and turns it into a {@link PortionItem}. So
 * filling needs no recipe per substance: a spout fills paper with flour, and won't fill a bottle
 * with it.
 */
public final class EmptyContainers {
    private EmptyContainers() {}

    public static void attach(AttachCapabilitiesEvent<ItemStack> event) {
        var stack = event.getObject();
        Container.emptiedAs(stack.getItem()).ifPresent(container ->
                event.addCapability(CookbookOfBabel.id("portion_filling"), new Filler(stack, container)));
    }

    private static final class Filler implements IFluidHandlerItem, ICapabilityProvider {
        private final LazyOptional<IFluidHandlerItem> self = LazyOptional.of(() -> this);
        private final Container kind;
        private ItemStack container;

        Filler(ItemStack container, Container kind) {
            this.container = container;
            this.kind = kind;
        }

        @Override
        public ItemStack getContainer() {
            return container;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return Container.PORTION_MB;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack fluid) {
            return fluid.getFluid().isSame(kind.fluid()) && fluid.hasTag() && fluid.getTag().contains(Substances.KEY);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (container.getCount() != 1 || resource.getAmount() < Container.PORTION_MB || !isFluidValid(0, resource)) {
                return 0;
            }
            if (action.execute()) {
                var portion = resource.copy();
                portion.setAmount(Container.PORTION_MB);
                container = PortionItem.filled(portion, 1);
            }
            return Container.PORTION_MB;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
            return ForgeCapabilities.FLUID_HANDLER_ITEM.orEmpty(capability, self);
        }
    }
}
