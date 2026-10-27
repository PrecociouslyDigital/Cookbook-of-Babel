package cookbookofbabel.minecraft.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.minecraft.core.BlockPos;

class RoomProperties {
    private static final RoomConstants CONSTANTS = RoomConstants.DEFAULT;

    /**
     * A box of walls around open air from (1, 1, 1) to {@code far}, under open sky everywhere else.
     * Some share of the walls cool, a few are freeze or heat sources, and the air is lit by some sky light.
     */
    record Box(BlockPos far, Map<BlockPos, Cell> cells) implements Function<BlockPos, Cell> {
        static Box random(int x, int y, int z, Random random) {
            var far = new BlockPos(x, y, z);
            var cells = new HashMap<BlockPos, Cell>();
            var coolingShare = random.nextDouble();
            for (var pos : BlockPos.betweenClosed(BlockPos.ZERO, far.offset(1, 1, 1))) {
                var heat = random.nextInt(10) == 0 ? random.nextDouble(-10, 10) : 0;
                cells.put(pos.immutable(), inside(far, pos)
                        ? new Cell.Open(random.nextInt(16), 0)
                        : new Cell.Wall(random.nextDouble() < coolingShare, heat));
            }
            return new Box(far, Map.copyOf(cells));
        }

        @Override
        public Cell apply(BlockPos pos) {
            return cells.getOrDefault(pos, new Cell.Exit());
        }

        Box with(BlockPos pos, Cell cell) {
            var changed = new HashMap<>(cells);
            changed.put(pos, cell);
            return new Box(far, changed);
        }

        BlockPos container() {
            return new BlockPos(1, 1, 1);
        }

        List<BlockPos> interior() {
            return cells.keySet().stream().filter(pos -> inside(far, pos)).sorted().toList();
        }

        /** The walls that touch the air through a face, not only along an edge. */
        List<BlockPos> faceWalls() {
            return cells.keySet().stream()
                    .filter(pos -> !inside(far, pos) && outsideAlong(pos) == 1)
                    .sorted()
                    .toList();
        }

        BlockPos anyFaceWall(Random random) {
            var walls = faceWalls();
            return walls.get(random.nextInt(walls.size()));
        }

        private int outsideAlong(BlockPos pos) {
            return (outside(pos.getX(), far.getX()) ? 1 : 0) + (outside(pos.getY(), far.getY()) ? 1 : 0)
                    + (outside(pos.getZ(), far.getZ()) ? 1 : 0);
        }

        private static boolean inside(BlockPos far, BlockPos pos) {
            return !outside(pos.getX(), far.getX()) && !outside(pos.getY(), far.getY()) && !outside(pos.getZ(), far.getZ());
        }

        private static boolean outside(int coordinate, int far) {
            return coordinate < 1 || coordinate > far;
        }
    }

    @Provide
    Arbitrary<Box> boxes() {
        var side = Arbitraries.integers().between(1, CONSTANTS.maxRoomSize() - 2);
        return Combinators.combine(side, side, side, Arbitraries.longs())
                .as((x, y, z, seed) -> Box.random(x, y, z, new Random(seed)));
    }

    @Provide
    Arbitrary<Air> outsideAir() {
        return Combinators.combine(Arbitraries.doubles().between(-30, 45), Arbitraries.doubles().between(0, 100))
                .as(Air::new);
    }

    @Provide
    Arbitrary<Double> tempsC() {
        return Arbitraries.doubles().between(-60, 60);
    }

    @Property
    void anEnclosedBoxIsARoom(@ForAll("boxes") Box box) {
        assertThat(Room.survey(box, box.container(), CONSTANTS)).isPresent();
    }

    @Property
    void anOpenedBoxIsNot(@ForAll("boxes") Box box, @ForAll Random random) {
        var opened = box.with(box.anyFaceWall(random), new Cell.Open(0, 0));
        assertThat(Room.survey(opened, opened.container(), CONSTANTS)).isEmpty();
    }

    @Property
    void aBoxLongerThanARoomIsNot(@ForAll Random random) {
        var box = Box.random(CONSTANTS.maxRoomSize() + 1, 1, 1, random);
        assertThat(Room.survey(box, box.container(), CONSTANTS)).isEmpty();
    }

    /**
     * Cooling walls take a room towards the ground's temperature, so a cooling wall in place of
     * another never warms a room when it's warmer outside, and never chills one when it's colder.
     */
    @Property
    void aCoolingWallTakesARoomTowardsTheGround(@ForAll("boxes") Box box, @ForAll("outsideAir") Air outside, @ForAll Random random) {
        var wall = box.anyFaceWall(random);
        var plain = tempC(box.with(wall, new Cell.Wall(false, 0)), outside);
        var cooling = tempC(box.with(wall, new Cell.Wall(true, 0)), outside);
        if (outside.tempC() >= CONSTANTS.groundTempC()) {
            assertThat(cooling).isLessThanOrEqualTo(plain);
        } else {
            assertThat(cooling).isGreaterThanOrEqualTo(plain);
        }
    }

    @Property
    void iceNeverWarms(@ForAll("boxes") Box box, @ForAll("outsideAir") Air outside, @ForAll Random random) {
        var wall = box.anyFaceWall(random);
        var plain = tempC(box.with(wall, new Cell.Wall(false, 0)), outside);
        var ice = tempC(box.with(wall, new Cell.Wall(false, -random.nextDouble(0.1, 10))), outside);
        assertThat(ice).isLessThanOrEqualTo(plain);
    }

    /** The air spreads to the same room wherever in it the fill starts, so the reading is the same. */
    @Property
    void theReadingDoesNotDependOnWhereTheFillStarts(@ForAll("boxes") Box box, @ForAll("outsideAir") Air outside, @ForAll Random random) {
        var interior = box.interior();
        var from = interior.get(random.nextInt(interior.size()));
        assertThat(Room.reading(box, from, outside, CONSTANTS)).isEqualTo(Room.reading(box, box.container(), outside, CONSTANTS));
    }

    @Property
    void coolingARoomNeverLowersItsHumidity(@ForAll("boxes") Box box, @ForAll("outsideAir") Air outside, @ForAll Random random) {
        var wall = box.anyFaceWall(random);
        var plain = Room.reading(box.with(wall, new Cell.Wall(false, 0)), box.container(), outside, CONSTANTS);
        var ice = Room.reading(box.with(wall, new Cell.Wall(false, -random.nextDouble(0.1, 10))), box.container(), outside, CONSTANTS);
        assertThat(ice.relativeHumidity()).isGreaterThanOrEqualTo(plain.relativeHumidity());
    }

    @Property
    void heatingARoomNeverRaisesItsHumidity(@ForAll("boxes") Box box, @ForAll("outsideAir") Air outside, @ForAll Random random) {
        var wall = box.anyFaceWall(random);
        var plain = Room.reading(box.with(wall, new Cell.Wall(false, 0)), box.container(), outside, CONSTANTS);
        var stove = Room.reading(box.with(wall, new Cell.Wall(false, random.nextDouble(0.1, 10))), box.container(), outside, CONSTANTS);
        assertThat(stove.relativeHumidity()).isLessThanOrEqualTo(plain.relativeHumidity());
    }

    @Property
    void warmerAirHoldsTheSameWaterAtALowerHumidity(
            @ForAll("outsideAir") Air outside,
            @ForAll("tempsC") double one, @ForAll("tempsC") double other, @ForAll boolean meltwater) {
        var low = Math.min(one, other);
        var high = Math.max(one, other);
        assertThat(Room.humidity(outside, high, meltwater, CONSTANTS))
                .isLessThanOrEqualTo(Room.humidity(outside, low, meltwater, CONSTANTS));
    }

    @Property
    void roomAirNeverHoldsMoreThanCondenses(@ForAll("boxes") Box box, @ForAll("outsideAir") Air outside) {
        assertThat(Room.reading(box, box.container(), outside, CONSTANTS).relativeHumidity())
                .isLessThanOrEqualTo(CONSTANTS.condensationRH());
    }

    @Property
    void outsideAirPassesThroughWhereThereIsNoRoom(@ForAll("boxes") Box box, @ForAll("outsideAir") Air outside, @ForAll Random random) {
        var opened = box.with(box.anyFaceWall(random), new Cell.Open(0, 0));
        assertThat(Room.reading(opened, opened.container(), outside, CONSTANTS)).isEqualTo(outside);
    }

    private static double tempC(Box box, Air outside) {
        return Room.reading(box, box.container(), outside, CONSTANTS).tempC();
    }
}
