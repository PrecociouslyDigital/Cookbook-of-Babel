package cookbookofbabel.minecraft.room;

import cookbookofbabel.minecraft.CookbookOfBabel;
import java.util.Arrays;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.levelgen.Heightmap;

/** A level's blocks, as the air of a room meets them. */
public record LevelCells(Level level) implements Function<BlockPos, Cell> {
    /** Stone, earth, terracotta and ores: walls that keep a room at the ground's temperature. */
    public static final TagKey<Block> COOLING_WALLS = TagKey.create(Registries.BLOCK, CookbookOfBabel.id("cooling_walls"));

    private static final Cell OPEN_DOOR = new Cell.OpenDoor();
    private static final Cell EXIT = new Cell.Exit();

    /**
     * Air stops at any block with a sturdy face, so slabs, stairs, trapdoors and closed doors seal
     * a room, while fences, panes and leaves let air through. The sky is overhead where nothing
     * above blocks motion: glass roofs a room but lets sky light in.
     */
    @Override
    public Cell apply(BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return EXIT;
        }
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.OPEN)) {
            return OPEN_DOOR;
        }
        if (Arrays.stream(Direction.values()).anyMatch(face -> state.isFaceSturdy(level, pos, face))) {
            return new Cell.Wall(state.is(COOLING_WALLS), RoomHeat.of(state));
        }
        if (pos.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ())) {
            return EXIT;
        }
        return new Cell.Open(level.getBrightness(LightLayer.SKY, pos), RoomHeat.of(state));
    }
}
