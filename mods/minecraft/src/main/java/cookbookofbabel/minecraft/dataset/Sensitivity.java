package cookbookofbabel.minecraft.dataset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;

/**
 * How a step's duration scales with conditions. The duration is measured at {@code refTempC}; {@code q10} is the rate
 * multiplier per 10 °C warmer. Outside the water-activity or temperature bounds the step does not happen at all.
 */
public record Sensitivity(
        double refTempC,
        double q10,
        Optional<Double> minWaterActivity,
        Optional<Double> maxWaterActivity,
        Optional<Double> minTempC,
        Optional<Double> maxTempC) {
    public static final Codec<Sensitivity> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.DOUBLE.fieldOf("refTempC").forGetter(Sensitivity::refTempC),
                    Codec.DOUBLE.fieldOf("q10").forGetter(Sensitivity::q10),
                    Codec.DOUBLE.optionalFieldOf("minWaterActivity").forGetter(Sensitivity::minWaterActivity),
                    Codec.DOUBLE.optionalFieldOf("maxWaterActivity").forGetter(Sensitivity::maxWaterActivity),
                    Codec.DOUBLE.optionalFieldOf("minTempC").forGetter(Sensitivity::minTempC),
                    Codec.DOUBLE.optionalFieldOf("maxTempC").forGetter(Sensitivity::maxTempC))
            .apply(i, Sensitivity::new));
}
