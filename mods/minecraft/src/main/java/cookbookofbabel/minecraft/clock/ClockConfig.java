package cookbookofbabel.minecraft.clock;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ClockConfig {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.IntValue BUCKET_TICKS;

    static {
        var builder = new ForgeConfigSpec.Builder();
        BUCKET_TICKS = builder
                .comment(
                        "Food is dated to the nearest this many ticks, so stacks made around the same time still stack.",
                        "Larger buckets stack more readily; a transition can run up to half a bucket early or late,",
                        "though never by more than a hundredth of its own duration.")
                .defineInRange("bucketTicks", 1000, 1, 24000);
        SPEC = builder.build();
    }

    private ClockConfig() {}

    public static long bucketTicks() {
        return BUCKET_TICKS.get();
    }
}
