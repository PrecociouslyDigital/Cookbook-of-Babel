package cookbookofbabel.minecraft.compat.coldsweat;

import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.util.world.WorldHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Cold Sweat's world temperature, loaded only when it is. */
public final class ColdSweat {
    private ColdSweat() {}

    /** Biome, season, weather, time of day, depth and nearby blocks, as Cold Sweat reckons them. */
    public static double tempC(Level level, BlockPos pos) {
        return Temperature.convert(WorldHelper.getTemperatureAt(level, pos), Temperature.Units.MC, Temperature.Units.C, true);
    }
}
