package cookbookofbabel.minecraft.room;

import cookbookofbabel.minecraft.CookbookOfBabel;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The room readings of the containers in one chunk, saved with it. */
final class Readings implements ICapabilitySerializable<ListTag> {
    static final ResourceLocation ID = CookbookOfBabel.id("room_readings");
    static final Capability<Readings> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {});

    /** The air of a container's room, as measured in a season. */
    record Reading(Air air, int season) {}

    private final LevelChunk chunk;
    private final Map<BlockPos, Reading> byContainer = new HashMap<>();
    private final LazyOptional<Readings> self = LazyOptional.of(() -> this);

    Readings(LevelChunk chunk) {
        this.chunk = chunk;
    }

    Optional<Reading> get(BlockPos container) {
        return Optional.ofNullable(byContainer.get(container));
    }

    void put(BlockPos container, Reading reading) {
        byContainer.put(container.immutable(), reading);
        chunk.setUnsaved(true);
    }

    void remove(BlockPos container) {
        if (byContainer.remove(container) != null) {
            chunk.setUnsaved(true);
        }
    }

    void invalidate() {
        self.invalidate();
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        return CAPABILITY.orEmpty(capability, self);
    }

    /** Only readings whose container still stands: one destroyed without a break event leaves none behind. */
    @Override
    public ListTag serializeNBT() {
        var list = new ListTag();
        byContainer.forEach((pos, reading) -> {
            if (chunk.getBlockEntities().containsKey(pos)) {
                var entry = new CompoundTag();
                entry.putLong("pos", pos.asLong());
                entry.putDouble("tempC", reading.air().tempC());
                entry.putDouble("relativeHumidity", reading.air().relativeHumidity());
                entry.putInt("season", reading.season());
                list.add(entry);
            }
        });
        return list;
    }

    @Override
    public void deserializeNBT(ListTag list) {
        byContainer.clear();
        for (var i = 0; i < list.size(); i++) {
            var entry = list.getCompound(i);
            byContainer.put(BlockPos.of(entry.getLong("pos")), new Reading(
                    new Air(entry.getDouble("tempC"), entry.getDouble("relativeHumidity")), entry.getInt("season")));
        }
    }
}
