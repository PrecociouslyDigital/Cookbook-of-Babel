package cookbookofbabel.minecraft.room;

import net.minecraftforge.common.ForgeConfigSpec;

/** The room model's constants, per world, in the server config. */
public final class RoomConfig {
    static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.DoubleValue GROUND_TEMP_C;
    private static final ForgeConfigSpec.DoubleValue CONDENSATION_RH;
    private static final ForgeConfigSpec.DoubleValue MELTWATER_RH;
    private static final ForgeConfigSpec.DoubleValue FREEZE_SOURCE_FLOOR_C;
    private static final ForgeConfigSpec.DoubleValue HEAT_SOURCE_CEILING_C;
    private static final ForgeConfigSpec.DoubleValue ROOM_WEIGHT;
    private static final ForgeConfigSpec.DoubleValue SKYLIGHT_PENALTY;
    private static final ForgeConfigSpec.DoubleValue DOOR_WEIGHT;
    private static final ForgeConfigSpec.IntValue MAX_ROOM_SIZE;
    private static final ForgeConfigSpec.IntValue CELLAR_SIZE;
    private static final ForgeConfigSpec.IntValue ALT_CELLAR_SIZE;
    private static final ForgeConfigSpec.IntValue ALT_CELLAR_VOLUME;

    static {
        var defaults = RoomConstants.DEFAULT;
        var builder = new ForgeConfigSpec.Builder().push("rooms");
        GROUND_TEMP_C = builder.comment("°C a perfectly cooled room, such as a stone cellar, tends to")
                .defineInRange("groundTempC", defaults.groundTempC(), -50, 50);
        CONDENSATION_RH = builder.comment("The highest relative humidity (%) room air keeps before water condenses on the walls")
                .defineInRange("condensationRH", defaults.condensationRH(), 0, 100);
        MELTWATER_RH = builder.comment("Relative humidity (%) melting freeze sources add to a room")
                .defineInRange("meltwaterRH", defaults.meltwaterRH(), 0, 100);
        FREEZE_SOURCE_FLOOR_C = builder.comment("°C freeze sources can chill a room to, and no lower")
                .defineInRange("freezeSourceFloorC", defaults.freezeSourceFloorC(), -100, 100);
        HEAT_SOURCE_CEILING_C = builder.comment("°C heat sources can warm a room to, and no higher")
                .defineInRange("heatSourceCeilingC", defaults.heatSourceCeilingC(), -100, 100);
        ROOM_WEIGHT = builder.comment("How much a room bigger than a cellar takes the ground's temperature (a cellar's is 1)")
                .defineInRange("roomWeight", defaults.roomWeight(), 0, 1);
        SKYLIGHT_PENALTY = builder.comment("How much of its cooling a room fully lit by the sky loses")
                .defineInRange("skylightPenalty", defaults.skylightPenalty(), 0, 1);
        DOOR_WEIGHT = builder.comment("How many non-cooling walls each half of an open door counts as")
                .defineInRange("doorWeight", defaults.doorWeight(), 0, 100);
        MAX_ROOM_SIZE = builder.comment("The longest a room can be along any axis; air that spreads further is outside")
                .defineInRange("maxRoomSize", defaults.maxRoomSize(), 1, 32);
        CELLAR_SIZE = builder.comment("The longest a cellar can be along any axis")
                .defineInRange("cellarSize", defaults.cellarSize(), 1, 32);
        ALT_CELLAR_SIZE = builder.comment("The longest one axis of a cellar can be, if it holds no more than altCellarVolume")
                .defineInRange("altCellarSize", defaults.altCellarSize(), 1, 32);
        ALT_CELLAR_VOLUME = builder.comment("The most air a cellar with one longer axis can hold")
                .defineInRange("altCellarVolume", defaults.altCellarVolume(), 1, 32 * 32 * 32);
        SPEC = builder.pop().build();
    }

    private RoomConfig() {}

    public static RoomConstants constants() {
        return new RoomConstants(
                GROUND_TEMP_C.get(),
                CONDENSATION_RH.get(),
                MELTWATER_RH.get(),
                FREEZE_SOURCE_FLOOR_C.get(),
                HEAT_SOURCE_CEILING_C.get(),
                ROOM_WEIGHT.get(),
                SKYLIGHT_PENALTY.get(),
                DOOR_WEIGHT.get(),
                MAX_ROOM_SIZE.get(),
                CELLAR_SIZE.get(),
                ALT_CELLAR_SIZE.get(),
                ALT_CELLAR_VOLUME.get());
    }
}
