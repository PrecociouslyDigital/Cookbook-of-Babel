package cookbookofbabel.minecraft.room;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** The air of the room a container stands in, or the outside air when it isn't in one. */
public interface Rooms {
    Air at(Level level, BlockPos container);
}
