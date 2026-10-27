package cookbookofbabel.minecraft.compat.brewinandchewin;

import java.util.Arrays;
import java.util.Optional;

/**
 * Brewin' and Chewin's keg temperature levels, frigid to hot, as ranges of °C: datagen picks a recipe's level from its
 * temperature, and food kept in a keg is at its level's typical temperature. Needs nothing of the mod.
 *
 * @param belowC where the next level up starts
 * @param typicalC a temperature within the range, standing for the whole level
 */
public enum KegTemperature {
    FRIGID(1, 0, -10),
    COLD(2, 8, 4),
    NORMAL(3, 30, 20),
    WARM(4, 60, 45),
    HOT(5, Double.POSITIVE_INFINITY, 75);

    public final int level;
    public final double belowC;
    public final double typicalC;

    KegTemperature(int level, double belowC, double typicalC) {
        this.level = level;
        this.belowC = belowC;
        this.typicalC = typicalC;
    }

    public static KegTemperature of(double celsius) {
        return Arrays.stream(values()).filter(t -> celsius < t.belowC).findFirst().orElse(HOT);
    }

    public static Optional<KegTemperature> ofLevel(int level) {
        return Arrays.stream(values()).filter(t -> t.level == level).findFirst();
    }
}
