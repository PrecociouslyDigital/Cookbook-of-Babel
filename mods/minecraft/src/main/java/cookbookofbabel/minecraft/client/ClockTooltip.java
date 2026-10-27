package cookbookofbabel.minecraft.client;

import cookbookofbabel.minecraft.CookbookOfBabel;
import cookbookofbabel.minecraft.clock.Food;
import cookbookofbabel.minecraft.clock.ItemClocks;
import cookbookofbabel.minecraft.clock.Transition;
import cookbookofbabel.minecraft.clock.Transitions;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * How fresh food is and what it becomes next, read from its clock tag alone: the tag holds the conditions it was last
 * seen in, so the client needs nothing of rooms or climate.
 */
@Mod.EventBusSubscriber(modid = CookbookOfBabel.ID, value = Dist.CLIENT)
public final class ClockTooltip {
    private static final String KEY = "tooltip." + CookbookOfBabel.ID + ".clock.";
    private static final long TICKS_PER_HOUR = 1000;
    private static final long TICKS_PER_DAY = 24 * TICKS_PER_HOUR;

    private ClockTooltip() {}

    @SubscribeEvent
    static void tooltip(ItemTooltipEvent event) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        var registries = level.registryAccess();
        var stack = event.getItemStack();
        var food = Food.of(stack, registries);
        var clock = ItemClocks.read(stack);
        if (food.isEmpty() || clock.isEmpty()) {
            return;
        }
        var transitions = Transitions.from(food.get().id(), registries);
        long now = level.getGameTime();
        var lines = event.getToolTip();
        transitions.entrySet().stream()
                .filter(t -> t.getValue().kind() == Transition.Kind.SPOIL)
                .mapToDouble(t -> clock.get().progress(t.getKey(), t.getValue(), now))
                .max()
                .ifPresent(spoilt -> lines.add(Component.translatable(
                                KEY + "freshness", Math.round(100 * Math.max(0, 1 - spoilt)))
                        .withStyle(ChatFormatting.GRAY)));
        clock.get().next(transitions, now).ifPresent(next -> {
            var output = transitions.get(next.transition()).output();
            var name = Food.name(output, registries).orElseGet(() -> Component.literal(output.toString()));
            long remaining = next.at() - now;
            lines.add((remaining > 0
                            ? Component.translatable(KEY + "becomes", name, duration(remaining))
                            : Component.translatable(KEY + "becomes_now", name))
                    .withStyle(ChatFormatting.GRAY));
        });
    }

    private static Component duration(long ticks) {
        return ticks < TICKS_PER_DAY
                ? Component.translatable(KEY + "hours", (ticks + TICKS_PER_HOUR - 1) / TICKS_PER_HOUR)
                : Component.translatable(KEY + "days", "%.1f".formatted((double) ticks / TICKS_PER_DAY));
    }
}
