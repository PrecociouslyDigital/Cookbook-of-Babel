package cookbookofbabel.minecraft.compat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * What machines owe when they consume an ingredient: its crafting remainder, one per unit eaten.
 * FD's pot and B&C's keg pay this themselves ({@code ejectIngredientRemainder}), spawning the
 * leftover as an item beside the machine; the mixins use this to make the others pay too, so a bowl
 * fed to a machine comes back out of it.
 */
public final class Remainders {
    private static final ThreadLocal<List<ItemStack>> BEFORE = new ThreadLocal<>();

    private Remainders() {}

    /** Stacks the machine held before crafting, slot by slot. Call at the head of a crafting method. */
    public static void snapshot(List<ItemStack> slots) {
        var before = new ArrayList<ItemStack>(slots.size());
        for (var stack : slots) {
            before.add(stack.copy());
        }
        BEFORE.set(before);
    }

    /** The snapshot taken at the head, forgetting it. Null when there wasn't one. */
    public static List<ItemStack> takeSnapshot() {
        var before = BEFORE.get();
        BEFORE.remove();
        return before;
    }

    /**
     * Ejects the remainders of what the machine consumed since the snapshot: one per unit of every
     * stack that has a remainder and shrank, was emptied, or was replaced. Call at the return of a
     * crafting method, server-side only.
     */
    public static void eject(BlockEntity machine, List<ItemStack> before, List<ItemStack> slots) {
        var level = machine.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        for (var i = 0; i < before.size() && i < slots.size(); i++) {
            var was = before.get(i);
            var now = slots.get(i);
            if (was.isEmpty() || !was.hasCraftingRemainingItem()) {
                continue;
            }
            var units = sameItem(was, now) ? was.getCount() - now.getCount() : was.getCount();
            for (var n = 0; n < units; n++) {
                drop(level, machine.getBlockPos(), was.getCraftingRemainingItem());
            }
        }
    }

    private static boolean sameItem(ItemStack was, ItemStack now) {
        return !now.isEmpty() && ItemStack.isSameItemSameTags(was, now);
    }

    private static void drop(Level level, BlockPos pos, ItemStack stack) {
        if (!stack.isEmpty()) {
            var dropped = stack.copy();
            dropped.setCount(1);
            var entity = new net.minecraft.world.entity.item.ItemEntity(
                    level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, dropped);
            level.addFreshEntity(entity);
        }
    }
}
