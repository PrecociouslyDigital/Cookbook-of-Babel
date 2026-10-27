package cookbookofbabel.minecraft.room;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Rooms measured afresh at every query, by flood-filling the level's air. {@link StoredRooms} keeps them. */
public record LevelRooms(Climate climate, Supplier<RoomConstants> constants) implements Rooms {
    @Override
    public Air at(Level level, BlockPos container) {
        return Room.reading(new LevelCells(level), container, climate.at(level, container), constants.get());
    }
}
