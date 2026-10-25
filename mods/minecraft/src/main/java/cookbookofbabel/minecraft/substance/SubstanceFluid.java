package cookbookofbabel.minecraft.substance;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.fluids.ForgeFlowingFluid;

/**
 * The fluid of every substance carried in one {@link Container}. It only ever exists in tanks,
 * pipes and portions: it has no block and no bucket, and is its own source and flowing form.
 */
public final class SubstanceFluid extends ForgeFlowingFluid {
    public SubstanceFluid(Properties properties) {
        super(properties);
    }

    @Override
    public Fluid getFlowing() {
        return this;
    }

    @Override
    public Item getBucket() {
        return Items.AIR;
    }

    @Override
    protected BlockState createLegacyBlock(FluidState state) {
        return Blocks.AIR.defaultBlockState();
    }

    @Override
    public boolean isSource(FluidState state) {
        return true;
    }

    @Override
    public int getAmount(FluidState state) {
        return 0;
    }
}
