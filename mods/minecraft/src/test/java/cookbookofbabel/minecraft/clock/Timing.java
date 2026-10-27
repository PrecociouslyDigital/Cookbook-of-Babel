package cookbookofbabel.minecraft.clock;

import cookbookofbabel.minecraft.dataset.Sensitivity;

/**
 * A transition's timing alone. {@link Transition} can't be built outside a running game: its registry key starts up
 * Minecraft's registries.
 */
record Timing(long ticks, Sensitivity sensitivity) implements Timed {}
