package cookbookofbabel.minecraft.clock;

import cookbookofbabel.minecraft.room.Climate;
import net.minecraft.world.entity.Entity;

/** A player's inventory or a dropped item: wrapped up, at the temperature outside. */
public record EntityHolder(Climate climate, Entity entity) implements Holder {
    @Override
    public Exposure exposure() {
        return new Exposure.Closed(climate.at(entity.level(), entity.blockPosition()).tempC());
    }
}
