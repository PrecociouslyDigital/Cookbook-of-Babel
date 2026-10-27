package cookbookofbabel.minecraft.room;

/**
 * The tunable numbers of the room model; {@link RoomConfig} reads them from the server config.
 *
 * @param groundTempC what a perfectly cooled room tends to: the ground's temperature a few metres
 *     down, which barely changes with the seasons
 * @param condensationRH the highest relative humidity room air keeps; above it water condenses on
 *     the walls
 * @param meltwaterRH what melting freeze sources add to a room's relative humidity
 * @param freezeSourceFloorC how cold freeze sources can make a room: ice melts rather than chilling
 *     the air below it
 * @param heatSourceCeilingC how warm heat sources can make a room
 * @param roomWeight how much a room bigger than a cellar takes the ground's temperature (a cellar's
 *     is 1)
 * @param skylightPenalty how much a room lit by the sky loses of its cooling
 * @param doorWeight how many non-cooling walls an open door counts as
 * @param maxRoomSize the longest a room can be along any axis; air that spreads further is outside
 * @param cellarSize the longest a cellar can be along any axis
 * @param altCellarSize the longest one axis of a cellar can be, if it is no bigger than {@code
 *     altCellarVolume}
 * @param altCellarVolume the most air a cellar with one longer axis can hold
 */
public record RoomConstants(
        double groundTempC,
        double condensationRH,
        double meltwaterRH,
        double freezeSourceFloorC,
        double heatSourceCeilingC,
        double roomWeight,
        double skylightPenalty,
        double doorWeight,
        int maxRoomSize,
        int cellarSize,
        int altCellarSize,
        int altCellarVolume) {
    /** Vintage Story's room and cellar sizes and weights, with a temperate ground. */
    public static final RoomConstants DEFAULT = new RoomConstants(10, 95, 5, 0, 40, 0.5, 0.4, 3, 14, 7, 9, 150);

    public RoomConstants {
        require(condensationRH >= 0 && condensationRH <= 100, "condensationRH " + condensationRH + " is outside 0–100");
        require(meltwaterRH >= 0, "meltwaterRH " + meltwaterRH + " is negative");
        require(freezeSourceFloorC <= heatSourceCeilingC,
                "freezeSourceFloorC " + freezeSourceFloorC + " is above heatSourceCeilingC " + heatSourceCeilingC);
        require(roomWeight >= 0 && roomWeight <= 1, "roomWeight " + roomWeight + " is outside 0–1");
        require(skylightPenalty >= 0, "skylightPenalty " + skylightPenalty + " is negative");
        require(doorWeight >= 0, "doorWeight " + doorWeight + " is negative");
        require(0 < cellarSize && cellarSize <= altCellarSize && altCellarSize <= maxRoomSize,
                "sizes must satisfy 0 < cellarSize ≤ altCellarSize ≤ maxRoomSize, but are "
                        + cellarSize + ", " + altCellarSize + ", " + maxRoomSize);
        require(altCellarVolume > 0, "altCellarVolume " + altCellarVolume + " isn't positive");
    }

    private static void require(boolean holds, String problem) {
        if (!holds) {
            throw new IllegalArgumentException(problem);
        }
    }
}
