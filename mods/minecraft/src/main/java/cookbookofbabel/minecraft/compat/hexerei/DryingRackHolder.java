package cookbookofbabel.minecraft.compat.hexerei;

import cookbookofbabel.minecraft.clock.Exposure;
import cookbookofbabel.minecraft.clock.Holder;
import cookbookofbabel.minecraft.room.Rooms;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Hexerei's drying rack: food hangs in the room's air, which dries it. Its own drying recipes keep their timings. */
public record DryingRackHolder(Rooms rooms, Level level, BlockPos pos) implements Holder {
    @Override
    public Exposure exposure() {
        return new Exposure.Exposed(rooms.at(level, pos));
    }
}
