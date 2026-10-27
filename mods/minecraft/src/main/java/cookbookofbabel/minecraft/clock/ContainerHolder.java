package cookbookofbabel.minecraft.clock;

import cookbookofbabel.minecraft.room.Rooms;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** A chest, barrel or the like: shut, at the temperature of the room it stands in. */
public record ContainerHolder(Rooms rooms, Level level, BlockPos pos) implements Holder {
    @Override
    public Exposure exposure() {
        return new Exposure.Closed(rooms.at(level, pos).tempC());
    }
}
