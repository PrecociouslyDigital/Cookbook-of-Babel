package cookbookofbabel.minecraft.spice;

import cookbookofbabel.minecraft.Registration;
import cookbookofbabel.minecraft.substance.Substance;
import cookbookofbabel.minecraft.substance.Substances;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Which spice a planted crop is. Its NBT is a substance stack's ({@link Substances#tagOf}), so a
 * seed's {@code BlockEntityTag} plants it and the loot table copies it back onto the drops.
 */
public final class SpiceCropBlockEntity extends BlockEntity {
    /** Empty only until the planting seed's tag is loaded, or for a crop placed without one. */
    private Optional<ResourceKey<Substance>> spice = Optional.empty();

    public SpiceCropBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.SPICE_CROP_ENTITY.get(), pos, state);
    }

    public Optional<ResourceKey<Substance>> spice() {
        return spice;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        spice = Substances.named(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        spice.ifPresent(s -> tag.merge(Substances.tagOf(s)));
    }

    /** Clients tint the crop by its spice, so they need it too. */
    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** The tint is baked into the chunk's mesh, so a newly known spice needs the block redrawn. */
    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        super.onDataPacket(connection, packet);
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
        }
    }
}
