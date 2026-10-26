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
    CREATE("create"),
    /** Always loaded; an output mapped to vanilla shadows every later choice. */
    MINECRAFT("minecraft");

    public final String id;

    Mod(String id) {
        this.id = id;
    }

    public boolean alwaysLoaded() {
        return this == MINECRAFT;
    }

    public static Optional<Mod> byId(String id) {
        return Arrays.stream(values()).filter(m -> m.id.equals(id)).findFirst();
    }
}
