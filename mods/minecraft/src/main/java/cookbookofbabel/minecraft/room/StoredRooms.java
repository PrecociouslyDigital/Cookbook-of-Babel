package cookbookofbabel.minecraft.room;

import java.util.Optional;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Rooms measured once and kept, Vintage Story-style: a container's room is measured when it is
 * placed, or when first asked about if it predates the mod, and kept in its chunk until the season
 * changes or the container breaks. Building around a container therefore changes its room from the
 * next season.
 */
public final class StoredRooms implements Rooms {
    private final Rooms measured;
    private final ToIntFunction<Level> season;

    public StoredRooms(Rooms measured, ToIntFunction<Level> season) {
        this.measured = measured;
        this.season = season;
    }

    /** The rooms of the best climate and season the loaded mods give, with everything they need registered. */
    public static StoredRooms register(IEventBus modBus, IEventBus forgeBus) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, RoomConfig.SPEC);
        var rooms = new StoredRooms(new LevelRooms(Climates.best(), RoomConfig::constants), Climates.season());
        modBus.addListener((RegisterCapabilitiesEvent event) -> event.register(Readings.class));
        forgeBus.addGenericListener(LevelChunk.class, rooms::attach);
        forgeBus.addListener((AddReloadListenerEvent event) -> event.addListener(new RoomHeat()));
        forgeBus.addListener(rooms::placed);
        forgeBus.addListener(rooms::broken);
        return rooms;
    }

    @Override
    public Air at(Level level, BlockPos container) {
        var season = this.season.applyAsInt(level);
        var readings = readings(level, container);
        return readings.flatMap(r -> r.get(container))
                .filter(reading -> reading.season() == season)
                .map(Readings.Reading::air)
                .orElseGet(() -> measure(level, container, readings));
    }

    private Air measure(Level level, BlockPos container, Optional<Readings> readings) {
        var air = measured.at(level, container);
        readings.ifPresent(r -> r.put(container, new Readings.Reading(air, season.applyAsInt(level))));
        return air;
    }

    private static Optional<Readings> readings(Level level, BlockPos pos) {
        return level.getChunkAt(pos).getCapability(Readings.CAPABILITY).resolve();
    }

    private void attach(AttachCapabilitiesEvent<LevelChunk> event) {
        if (!event.getObject().getLevel().isClientSide) {
            var readings = new Readings(event.getObject());
            event.addCapability(Readings.ID, readings);
            event.addListener(readings::invalidate);
        }
    }

    private void placed(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide && holdsItems(level, event.getPos())) {
            measure(level, event.getPos(), readings(level, event.getPos()));
        }
    }

    private void broken(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide) {
            readings(level, event.getPos()).ifPresent(r -> r.remove(event.getPos()));
        }
    }

    private static boolean holdsItems(Level level, BlockPos pos) {
        var entity = level.getBlockEntity(pos);
        return entity != null && entity.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent();
    }
}
