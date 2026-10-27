package cookbookofbabel.minecraft.room;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** The outside air: weather, biome and season, without anything built around the position. */
public interface Climate {
    Air at(Level level, BlockPos pos);
}
