package cookbookofbabel.minecraft.room;

/** A reading of the air somewhere. */
public record Air(double tempC, double relativeHumidity) {
    public Air {
        if (!(relativeHumidity >= 0 && relativeHumidity <= 100)) {
            throw new IllegalArgumentException("relative humidity " + relativeHumidity + " is outside 0–100");
        }
    }
}
