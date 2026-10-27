package cookbookofbabel.minecraft.room;

import cookbookofbabel.minecraft.compat.coldsweat.ColdSweat;
import cookbookofbabel.minecraft.compat.sereneseasons.SereneSeasons;
import java.util.function.ToIntFunction;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

/**
 * The best outside climate the loaded mods give. Each mod's class is only touched when the mod is
 * loaded.
 */
public final class Climates {
    private Climates() {}

    /** Cold Sweat, else Serene Seasons, else vanilla. */
    public static Climate best() {
        var mods = ModList.get();
        if (mods.isLoaded("cold_sweat")) {
            return new BiomeClimate(ColdSweat::tempC);
        }
        if (mods.isLoaded("sereneseasons")) {
            return new BiomeClimate(SereneSeasons::tempC);
        }
        return new BiomeClimate(BiomeClimate::vanillaTempC);
    }

    /** Which season it is, so rooms are measured again when it changes; always the same without Serene Seasons. */
    public static ToIntFunction<Level> season() {
        return ModList.get().isLoaded("sereneseasons") ? SereneSeasons::subSeason : level -> 0;
    }
}
