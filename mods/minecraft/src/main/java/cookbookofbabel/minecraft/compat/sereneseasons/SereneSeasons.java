package cookbookofbabel.minecraft.compat.sereneseasons;

import cookbookofbabel.minecraft.room.BiomeClimate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import sereneseasons.api.season.SeasonHelper;
import sereneseasons.season.SeasonHooks;

/** Serene Seasons' seasons, loaded only when it is. */
public final class SereneSeasons {
    private SereneSeasons() {}

    /** The biome's temperature, shifted by the season. */
    public static double tempC(Level level, BlockPos pos) {
        return BiomeClimate.celsius(SeasonHooks.getBiomeTemperature(level, level.getBiome(pos), pos));
    }

    /** Which of the twelve sub-seasons it is. */
    public static int subSeason(Level level) {
        return SeasonHelper.getSeasonState(level).getSubSeason().ordinal();
    }
}
