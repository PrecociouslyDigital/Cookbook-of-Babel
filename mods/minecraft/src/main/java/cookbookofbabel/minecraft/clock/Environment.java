package cookbookofbabel.minecraft.clock;

/** The conditions food itself is in, which set how fast its transitions run. */
public record Environment(double tempC, double waterActivity) {
    public Environment {
        if (!(waterActivity >= 0 && waterActivity <= 1)) {
            throw new IllegalArgumentException("water activity " + waterActivity + " is outside 0–1");
        }
    }
}
