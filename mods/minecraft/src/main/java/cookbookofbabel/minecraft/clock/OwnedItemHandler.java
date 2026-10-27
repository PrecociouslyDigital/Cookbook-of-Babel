package cookbookofbabel.minecraft.clock;

import java.util.Optional;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Forge's {@code ItemStackHandler}, mixed in to know the block entity it belongs to, which nothing in Forge records.
 * Food put into the handler of a known owner is observed there.
 */
public interface OwnedItemHandler {
    void cookbook_of_babel$own(BlockEntity owner);

    Optional<BlockEntity> cookbook_of_babel$owner();
}
