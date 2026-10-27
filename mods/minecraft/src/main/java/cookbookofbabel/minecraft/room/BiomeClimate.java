package cookbookofbabel.minecraft.room;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * The outside air: a temperature from the best thermometer the loaded mods give, and a relative
 * humidity from the biome's downfall, which none of them model.
 */
public record BiomeClimate(Thermometer thermometer) implements Climate {
    /** The outside temperature at a position, in °C. */
    @FunctionalInterface
    public interface Thermometer {
        double tempC(Level level, BlockPos pos);
    }

    /** Vanilla's temperature where snow starts to fall, which is 0 °C. */
    private static final double SNOW_LINE = 0.15;
    /** °C per unit of vanilla temperature: plains (0.8) are 16 °C, deserts (2.0) 46 °C. */
    private static final double CELSIUS_PER_UNIT = 25;
    /** The relative humidity (%) of a biome where it never rains, and what full downfall adds. */
    private static final double DRY_RH = 30;
    private static final double DOWNFALL_RH = 60;

    @Override
    public Air at(Level level, BlockPos pos) {
        var downfall = level.getBiome(pos).value().getModifiedClimateSettings().downfall();
        return new Air(thermometer.tempC(level, pos), Math.max(0, Math.min(100, DRY_RH + DOWNFALL_RH * downfall)));
    }

    /** A vanilla biome temperature, such as {@link Biome#getBaseTemperature()}, in °C. */
    public static double celsius(double vanillaTemperature) {
        return (vanillaTemperature - SNOW_LINE) * CELSIUS_PER_UNIT;
    }

    /**
     * The biome's temperature, colder with altitude as vanilla's is, though without the noise
     * vanilla adds to its snow line, which it keeps private.
     */
    public static double vanillaTempC(Level level, BlockPos pos) {
        var climate = level.getBiome(pos).value().getModifiedClimateSettings();
        var temperature = climate.temperatureModifier().modifyTemperature(pos, climate.temperature());
        var aboveTreeline = Math.max(0, pos.getY() - 80);
        return celsius(temperature - aboveTreeline * 0.05f / 40f);
    }
}
