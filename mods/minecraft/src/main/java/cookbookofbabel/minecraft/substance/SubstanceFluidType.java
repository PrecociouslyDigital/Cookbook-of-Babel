package cookbookofbabel.minecraft.substance;

import cookbookofbabel.minecraft.client.SubstanceFluidRendering;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;

/** Shared by both substance fluids; it names and tints a stack after the substance in it. */
public final class SubstanceFluidType extends FluidType {
    public SubstanceFluidType(Properties properties) {
        super(properties);
    }

    @Override
    public Component getDescription(FluidStack stack) {
        return Substances.name(stack);
    }

    @Override
    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        consumer.accept(new SubstanceFluidRendering());
    }
}
