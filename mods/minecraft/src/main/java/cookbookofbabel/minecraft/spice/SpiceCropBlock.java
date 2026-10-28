package cookbookofbabel.minecraft.spice;

import cookbookofbabel.minecraft.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One crop for every spice the mod plants; its block entity says which. Growing only changes the
 * block's age, which keeps the block entity.
 */
public final class SpiceCropBlock extends CropBlock implements EntityBlock {
    public SpiceCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return Registration.SPICE_SEEDS.get();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpiceCropBlockEntity(pos, state);
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) instanceof SpiceCropBlockEntity crop
                ? crop.spice().map(SpiceSeedsItem::of).orElseGet(() -> super.getCloneItemStack(level, pos, state))
                : super.getCloneItemStack(level, pos, state);
    }
}
