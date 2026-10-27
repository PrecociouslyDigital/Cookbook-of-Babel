package cookbookofbabel.minecraft.room;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.slf4j.Logger;

/**
 * How much blocks warm or chill the room they are in, from datapacks: each file in {@code
 * data/<namespace>/room_heat/} names a block tag and its °C per block. The mod's own files name the tags
 * the keg and cooking pot use, {@code brewinandchewin:freeze_sources} and {@code
 * farmersdelight:heat_sources}, so a room agrees with them on what is cold and what is hot.
 */
public final class RoomHeat extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "room_heat";

    public record Source(TagKey<Block> blocks, double celsius) {
        public static final Codec<Source> CODEC = RecordCodecBuilder.create(i -> i.group(
                        TagKey.codec(Registries.BLOCK).fieldOf("blocks").forGetter(Source::blocks),
                        Codec.DOUBLE.fieldOf("celsius").forGetter(Source::celsius))
                .apply(i, Source::new));
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile List<Source> sources = List.of();

    RoomHeat() {
        super(new Gson(), DIRECTORY);
    }

    /** The °C a block adds to its room; an unlit stove or campfire adds nothing. */
    public static double of(BlockState state) {
        if (state.hasProperty(BlockStateProperties.LIT) && !state.getValue(BlockStateProperties.LIT)) {
            return 0;
        }
        return sources.stream().filter(source -> state.is(source.blocks())).mapToDouble(Source::celsius).sum();
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler) {
        sources = new TreeMap<>(files).entrySet().stream()
                .flatMap(file -> Source.CODEC.parse(JsonOps.INSTANCE, file.getValue())
                        .resultOrPartial(problem -> LOGGER.error("Skipping room heat {}: {}", file.getKey(), problem))
                        .stream())
                .toList();
    }
}
