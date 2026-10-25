package cookbookofbabel.minecraft.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

/** Water's greyscale textures, tinted with the substance's colour. */
public final class SubstanceFluidRendering implements IClientFluidTypeExtensions {
    private static final ResourceLocation STILL = new ResourceLocation("block/water_still");
    private static final ResourceLocation FLOWING = new ResourceLocation("block/water_flow");

    @Override
    public ResourceLocation getStillTexture() {
        return STILL;
    }

    @Override
    public ResourceLocation getFlowingTexture() {
        return FLOWING;
    }

    @Override
    public int getTintColor(FluidStack stack) {
        return 0xFF000000 | ClientSubstances.color(stack);
    }
}
