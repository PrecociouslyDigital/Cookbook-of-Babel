package cookbookofbabel.minecraft.room;

/** What the air spreading through a room meets at one position. */
public sealed interface Cell {
    /** How much a block here warms (positive) or chills (negative) the room it borders or fills. */
    double heatC();

    /** Air, or anything air passes through, lit by this much sky light (0–15). */
    record Open(int skylight, double heatC) implements Cell {
        public Open {
            if (skylight < 0 || skylight > 15) {
                throw new IllegalArgumentException("sky light " + skylight + " is outside 0–15");
            }
        }
    }

    /** A block that stops air. Stone and earth keep the ground's temperature; anything else doesn't. */
    record Wall(boolean cooling, double heatC) implements Cell {}

    /** An open door: it lets the outside air in, so it counts as several walls that don't cool. */
    record OpenDoor() implements Cell {
        @Override
        public double heatC() {
            return 0;
        }
    }

    /** Where air leaves any room: the sky is overhead, or nothing is known because it isn't loaded. */
    record Exit() implements Cell {
        @Override
        public double heatC() {
            return 0;
        }
    }
}
