package cookbookofbabel.minecraft.datagen;

import com.mojang.serialization.JsonOps;
import cookbookofbabel.minecraft.CookbookOfBabel;
import cookbookofbabel.minecraft.room.LevelCells;
import cookbookofbabel.minecraft.room.RoomHeat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

/** What the room model reads from data: which walls cool a room, and which blocks warm or chill it. */
final class RoomsGen {
    private RoomsGen() {}

    static final class CoolingWalls extends BlockTagsProvider {
        CoolingWalls(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, ExistingFileHelper files) {
            super(output, lookup, CookbookOfBabel.ID, files);
        }

        @Override
        protected void addTags(HolderLookup.Provider lookup) {
            tag(LevelCells.COOLING_WALLS)
                    .addTags(BlockTags.BASE_STONE_OVERWORLD, BlockTags.BASE_STONE_NETHER, BlockTags.STONE_BRICKS,
                            BlockTags.DIRT, BlockTags.TERRACOTTA)
                    .addOptionalTag(Tags.Blocks.STONE.location())
                    .addOptionalTag(Tags.Blocks.COBBLESTONE.location())
                    .addOptionalTag(Tags.Blocks.ORES.location());
        }
    }

    /** The keg's freeze sources and the cooking pot's heat sources, per block in a room. */
    static final class HeatSources implements DataProvider {
        private static final Map<String, RoomHeat.Source> SOURCES = Map.of(
                "freeze_sources", new RoomHeat.Source(blocks("brewinandchewin:freeze_sources"), -3),
                "heat_sources", new RoomHeat.Source(blocks("farmersdelight:heat_sources"), 6));

        private final PackOutput.PathProvider paths;

        HeatSources(PackOutput output) {
            paths = output.createPathProvider(PackOutput.Target.DATA_PACK, RoomHeat.DIRECTORY);
        }

        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            return CompletableFuture.allOf(SOURCES.entrySet().stream()
                    .map(source -> DataProvider.saveStable(cache,
                            RoomHeat.Source.CODEC.encodeStart(JsonOps.INSTANCE, source.getValue()).result().orElseThrow(),
                            paths.json(CookbookOfBabel.id(source.getKey()))))
                    .toArray(CompletableFuture[]::new));
        }

        @Override
        public String getName() {
            return "Room heat sources";
        }

        private static TagKey<Block> blocks(String tag) {
            return TagKey.create(Registries.BLOCK, new ResourceLocation(tag));
        }
    }
}
