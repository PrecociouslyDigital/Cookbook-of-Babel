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
    // Mods that only provide ingredients come after every mod with machines, so mapping them
    // never moves an output another mod already provides.
    ENDERS_DELIGHT("endersdelight"),
    VEGGIES_DELIGHT("veggiesdelight"),
    /** Its meats and fish only, and its game-mob foods are nobody's recipe. */
    ALEXS_MOBS("alexsmobs"),
    /** Chevon for goats, which vanilla goats drop nothing of. */
    GOATED_GOATS("goated_goats"),
    /** Duck, venison and their eggs, off the ducks and deer it adds. */
    ENVIRONMENTAL("environmental"),
    /** Turkey and its pieces, off the turkeys it adds. */
    AUTUMNITY("autumnity"),
    /** Two dozen extra fish, all tagged into the raw-fish tag. */
    AQUACULTURE("aquaculture"),
    CROPTOPIA("croptopia"),
    PAMHC2_FOODEXTENDED("pamhc2foodextended"),
    PAMHC2_FOODCORE("pamhc2foodcore"),
    PAMHC2_CROPS("pamhc2crops"),
    PAMHC2_TREES("pamhc2trees"),
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
