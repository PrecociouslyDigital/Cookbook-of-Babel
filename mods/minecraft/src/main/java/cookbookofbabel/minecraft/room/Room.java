package cookbookofbabel.minecraft.room;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;

/**
 * The enclosed air around a container, Vintage Story-style: what the air from the container
 * reaches before every way out is walled off.
 *
 * @param volume how many positions the air fills, the container's own included
 * @param size the length of the air's bounding box along x, y and z
 * @param coolingWalls the walls of stone or earth around the air
 * @param plainWalls the other walls around it
 * @param openDoors the open doors in its walls, each half of a door counted
 * @param meanSkylight how much sky light reaches the air, on average, from 0 (none) to 1 (full)
 * @param coolingC the summed chill of the freeze sources in and around it (≤ 0)
 * @param heatingC the summed warmth of the heat sources in and around it (≥ 0)
 */
public record Room(
        int volume, Vec3i size, int coolingWalls, int plainWalls, int openDoors,
        double meanSkylight, double coolingC, double heatingC) {
    public Room {
        if (volume < 1 || coolingWalls < 0 || plainWalls < 0 || openDoors < 0
                || !(meanSkylight >= 0 && meanSkylight <= 1) || !(coolingC <= 0) || !(heatingC >= 0)) {
            throw new IllegalArgumentException("not a room: " + volume + " positions, " + coolingWalls + "/" + plainWalls
                    + " cooling/plain walls, " + openDoors + " open doors, sky light " + meanSkylight
                    + ", " + coolingC + "/" + heatingC + " °C of freeze/heat sources");
        }
    }

    /**
     * The room the air from {@code container} fills in {@code world}, or empty if it is outside:
     * the air reaches the sky or an unknown position, or spreads further than a room can be.
     */
    public static Optional<Room> survey(Function<BlockPos, Cell> world, BlockPos container, RoomConstants constants) {
        var origin = world.apply(container);
        if (origin instanceof Cell.Exit) {
            return Optional.empty();
        }
        // Sorted, so the sums below add up in the same order whichever way the air spread.
        var interior = new TreeMap<BlockPos, Cell>();
        var boundary = new TreeMap<BlockPos, Cell>();
        var min = new int[] {container.getX(), container.getY(), container.getZ()};
        var max = min.clone();
        interior.put(container, origin);
        var frontier = new ArrayDeque<BlockPos>();
        frontier.add(container);
        while (!frontier.isEmpty()) {
            var pos = frontier.poll();
            for (var direction : Direction.values()) {
                var next = pos.relative(direction);
                if (interior.containsKey(next) || boundary.containsKey(next)) {
                    continue;
                }
                var cell = world.apply(next);
                if (cell instanceof Cell.Exit) {
                    return Optional.empty();
                }
                if (!(cell instanceof Cell.Open)) {
                    boundary.put(next, cell);
                    continue;
                }
                var coordinates = new int[] {next.getX(), next.getY(), next.getZ()};
                for (var axis = 0; axis < 3; axis++) {
                    min[axis] = Math.min(min[axis], coordinates[axis]);
                    max[axis] = Math.max(max[axis], coordinates[axis]);
                    if (max[axis] - min[axis] + 1 > constants.maxRoomSize()) {
                        return Optional.empty();
                    }
                }
                interior.put(next, cell);
                frontier.add(next);
            }
        }
        var size = new Vec3i(max[0] - min[0] + 1, max[1] - min[1] + 1, max[2] - min[2] + 1);
        var skylight = interior.values().stream()
                .mapToInt(cell -> cell instanceof Cell.Open open ? open.skylight() : 0)
                .sum();
        var heats = Stream.concat(interior.values().stream(), boundary.values().stream()).mapToDouble(Cell::heatC).toArray();
        return Optional.of(new Room(
                interior.size(),
                size,
                count(boundary, cell -> cell instanceof Cell.Wall wall && wall.cooling()),
                count(boundary, cell -> cell instanceof Cell.Wall wall && !wall.cooling()),
                count(boundary, cell -> cell instanceof Cell.OpenDoor),
                skylight / (15.0 * interior.size()),
                Arrays.stream(heats).filter(c -> c < 0).sum(),
                Arrays.stream(heats).filter(c -> c > 0).sum()));
    }

    /** The air in the room around {@code container}, or {@code outside} if it isn't in one. */
    public static Air reading(Function<BlockPos, Cell> world, BlockPos container, Air outside, RoomConstants constants) {
        return survey(world, container, constants).map(room -> room.air(outside, constants)).orElse(outside);
    }

    /**
     * Small enough to keep the ground's temperature: every axis within {@code cellarSize}, or two
     * axes within it and the third within {@code altCellarSize}, as long as the room holds no more
     * than {@code altCellarVolume}.
     */
    public boolean cellar(RoomConstants constants) {
        var sorted = new int[] {size.getX(), size.getY(), size.getZ()};
        Arrays.sort(sorted);
        var small = sorted[2] <= constants.cellarSize();
        var longer = volume <= constants.altCellarVolume()
                && sorted[1] <= constants.cellarSize()
                && sorted[2] <= constants.altCellarSize();
        return small || longer;
    }

    /**
     * How far the room takes the ground's temperature instead of the outside air's, from 0 to 1.
     * Stone and earth walls raise it, other walls and open doors lower it, cellars keep more of it
     * than bigger rooms, and sky light lowers it.
     */
    public double coolingScore(RoomConstants constants) {
        var nonCooling = plainWalls + constants.doorWeight() * openDoors;
        var uncooled = Math.min(1, nonCooling / Math.max(1, coolingWalls));
        var enclosure = cellar(constants) ? 1 : constants.roomWeight();
        return clamp(enclosure * (1 - uncooled) - constants.skylightPenalty() * meanSkylight, 0, 1);
    }

    /**
     * Between the outside and the ground's temperature, by the cooling score; then freeze sources
     * chill it, no lower than they are themselves, and heat sources warm it, no higher than their
     * ceiling.
     */
    public double tempC(double outsideC, RoomConstants constants) {
        var base = outsideC + (constants.groundTempC() - outsideC) * coolingScore(constants);
        var cooled = Math.min(base, Math.max(base + coolingC, constants.freezeSourceFloorC()));
        return Math.max(cooled, Math.min(cooled + heatingC, constants.heatSourceCeilingC()));
    }

    public Air air(Air outside, RoomConstants constants) {
        var tempC = tempC(outside.tempC(), constants);
        return new Air(tempC, humidity(outside, tempC, coolingC < 0, constants));
    }

    /**
     * The relative humidity of outside air brought to {@code tempC}: its water stays, so warmer
     * air holds it at a lower relative humidity and cooler air at a higher one, up to where it
     * condenses. Meltwater from freeze sources adds a little.
     */
    public static double humidity(Air outside, double tempC, boolean meltwater, RoomConstants constants) {
        var carried = outside.relativeHumidity() * saturationVapourPressure(outside.tempC()) / saturationVapourPressure(tempC);
        return Math.min(carried + (meltwater ? constants.meltwaterRH() : 0), constants.condensationRH());
    }

    /** The Magnus formula, in hPa. */
    static double saturationVapourPressure(double tempC) {
        return 6.112 * Math.exp(17.62 * tempC / (243.12 + tempC));
    }

    private static int count(TreeMap<BlockPos, Cell> cells, Predicate<Cell> which) {
        return (int) cells.values().stream().filter(which).count();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
