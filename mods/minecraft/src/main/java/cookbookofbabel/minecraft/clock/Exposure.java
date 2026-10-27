package cookbookofbabel.minecraft.clock;

import cookbookofbabel.minecraft.room.Air;

/** How food in a holder meets the air around it. */
public sealed interface Exposure {
    /** The environment of food with the given water activity. */
    Environment around(double waterActivity);

    /** Shut away: it takes the air's temperature, but its moisture is its own. */
    record Closed(double tempC) implements Exposure {
        @Override
        public Environment around(double waterActivity) {
            return new Environment(tempC, waterActivity);
        }
    }

    /** Open to the air, which dries it towards the air's relative humidity but never wets it. */
    record Exposed(Air air) implements Exposure {
        @Override
        public Environment around(double waterActivity) {
            return new Environment(air.tempC(), Math.min(waterActivity, air.relativeHumidity() / 100));
        }
    }
}
