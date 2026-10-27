package cookbookofbabel.minecraft.clock;

import cookbookofbabel.minecraft.room.Climate;
import cookbookofbabel.minecraft.room.Rooms;
import cookbookofbabel.minecraft.substance.Substance;
import java.util.Optional;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.SlotItemHandler;

/**
 * Runs food's transitions. Nothing ticks: a stack's clock tag dates its transitions, and whenever the stack is seen —
 * put into a container, opened up, dropped, picked up or carried — it is brought up to date, becomes what it has
 * turned into, and is re-dated to the conditions it is now in.
 */
public final class ItemClocks {
    private static final String TAG = "clock";
    /** Enough steps to follow food through everything it became since it was last seen, but not round a cycle. */
    private static final int MAX_STEPS = 8;
    private static final int PLAYER_INTERVAL_TICKS = 100;

    private static ItemClocks installed;

    private final Rooms rooms;
    private final Climate climate;

    public ItemClocks(Rooms rooms, Climate climate) {
        this.rooms = rooms;
        this.climate = climate;
    }

    /** Makes these the clocks the game's hooks use. */
    public static void install(ItemClocks clocks) {
        installed = clocks;
        MinecraftForge.EVENT_BUS.register(clocks);
    }

    public static ItemClocks get() {
        return installed;
    }

    public Rooms rooms() {
        return rooms;
    }

    /** What to keep in a block entity in place of {@code stack}. */
    public ItemStack observe(ItemStack stack, BlockEntity holder) {
        var level = holder.getLevel();
        if (level == null || level.isClientSide) {
            return stack;
        }
        var held = holder instanceof HoldsFood h
                ? h.cookbook_of_babel$holder()
                : new ContainerHolder(rooms, level, holder.getBlockPos());
        return observe(stack, held, level);
    }

    /** {@code stack} as it is now, having been in its holder since it was last seen; itself if nothing changed. */
    public ItemStack observe(ItemStack stack, Holder holder, Level level) {
        var registries = level.registryAccess();
        var substances = registries.lookup(Substance.REGISTRY);
        long now = level.getGameTime();
        long bucket = ClockConfig.bucketTicks();
        var current = stack;
        long since = now;
        for (int step = 0; step < MAX_STEPS; step++) {
            var food = Food.of(current, registries);
            if (food.isEmpty()) {
                return current;
            }
            var transitions = Transitions.from(food.get().id(), registries);
            if (transitions.isEmpty()) {
                return withClock(current, Optional.empty());
            }
            var environment = holder.exposure().around(food.get().waterActivity());
            long from = since;
            var clock = read(current).orElseGet(() -> StackClock.start(transitions, environment, from, bucket));
            var done = clock.firstDone(transitions, now);
            if (done.isEmpty()) {
                return withClock(current, Optional.of(clock.reanchor(transitions, environment, now, bucket)));
            }
            var becomes = transitions.get(done.get().transition()).output();
            int count = current.getCount();
            var output = substances.flatMap(s -> Food.stackOf(becomes, count, s));
            if (output.isEmpty()) {
                return withClock(current, Optional.of(clock));
            }
            current = output.get();
            since = done.get().at();
        }
        return current;
    }

    public static Optional<StackClock> read(ItemStack stack) {
        return Optional.ofNullable(stack.getTagElement(TAG))
                .flatMap(tag -> StackClock.CODEC.parse(NbtOps.INSTANCE, tag).result());
    }

    private static ItemStack withClock(ItemStack stack, Optional<StackClock> clock) {
        if (read(stack).equals(clock)) {
            return stack;
        }
        var copy = stack.copy();
        clock.ifPresentOrElse(
                c -> copy.addTagElement(TAG, StackClock.CODEC.encodeStart(NbtOps.INSTANCE, c).getOrThrow(false, e -> {})),
                () -> copy.removeTagKey(TAG));
        return copy;
    }

    /**
     * Putting an item back where it is runs it through the same hooks as anything else put there, which know how
     * that place holds food.
     */
    @SubscribeEvent
    void containerOpened(PlayerContainerEvent.Open event) {
        for (var slot : event.getContainer().slots) {
            if (slot.hasItem() && hooked(slot)) {
                slot.set(slot.getItem());
            }
        }
    }

    private static boolean hooked(Slot slot) {
        return slot.container instanceof RandomizableContainerBlockEntity
                || slot.container instanceof CompoundContainer
                || (slot instanceof SlotItemHandler s
                        && s.getItemHandler() instanceof OwnedItemHandler handler
                        && handler.cookbook_of_babel$owner().isPresent());
    }

    @SubscribeEvent
    void playerTick(TickEvent.PlayerTickEvent event) {
        var player = event.player;
        if (event.phase != TickEvent.Phase.END
                || player.level().isClientSide
                || player.tickCount % PLAYER_INTERVAL_TICKS != 0) {
            return;
        }
        var inventory = player.getInventory();
        var holder = new EntityHolder(climate, player);
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            var stack = inventory.getItem(i);
            var seen = observe(stack, holder, player.level());
            if (seen != stack) {
                inventory.setItem(i, seen);
            }
        }
    }

    @SubscribeEvent
    void itemAppeared(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof ItemEntity item) {
            observe(item);
        }
    }

    /** Brought up to date as it lay on the ground, before it joins the player's inventory. */
    @SubscribeEvent
    void itemPickedUp(EntityItemPickupEvent event) {
        observe(event.getItem());
    }

    private void observe(ItemEntity item) {
        var stack = item.getItem();
        var seen = observe(stack, new EntityHolder(climate, item), item.level());
        if (seen != stack) {
            item.setItem(seen);
        }
    }
}
