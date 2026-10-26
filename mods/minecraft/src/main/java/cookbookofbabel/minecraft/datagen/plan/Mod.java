package cookbookofbabel.minecraft.datagen.plan;

import java.util.Arrays;
import java.util.Optional;

/** The mods recipes are written for, in order of preference for a mapped output. */
public enum Mod {
    BREWIN_AND_CHEWIN("brewinandchewin"),
    FARMERS_RESPITE("farmersrespite"),
    VINTAGE_DELIGHT("vintagedelight"),
    RATS("rats"),
    FARMERS_DELIGHT("farmersdelight"),
    GARNISHED("garnished"),
    /** Its foods share the {@code thermal} namespace with the rest of the Thermal series. */
    THERMAL_CULTIVATION("thermal_cultivation", "thermal"),
    CREATE("create"),
    PNEUMATICCRAFT("pneumaticcraft"),
    /** Its machines' recipe types share the {@code thermal} namespace too. */
    THERMAL_EXPANSION("thermal_expansion", "thermal"),
    THEURGY("theurgy"),
    HEXEREI("hexerei"),
    /** Always loaded; an output mapped to vanilla shadows every later choice. */
    MINECRAFT("minecraft");

    public final String id;
    /** Where its items and fluids are registered: usually its ID. */
    public final String namespace;

    Mod(String id) {
        this(id, id);
    }

    Mod(String id, String namespace) {
        this.id = id;
        this.namespace = namespace;
    }

    public boolean alwaysLoaded() {
        return this == MINECRAFT;
    }

    public static Optional<Mod> byId(String id) {
        return Arrays.stream(values()).filter(m -> m.id.equals(id)).findFirst();
    }
}
