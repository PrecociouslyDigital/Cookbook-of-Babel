package cookbookofbabel.minecraft.substance;

import cookbookofbabel.minecraft.Registration;
import cookbookofbabel.minecraft.dataset.Form;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.world.level.material.Fluid;

/**
 * The kind of fluid a flowable substance flows as, whichever vessel carries it. Vessels are {@link
 * Container}s; two vessels may hold one kind, but a kind is one fluid, so a filled vessel knows
 * what it empties into from its fluid alone.
 */
public enum FluidKind {
    /** Liquids, batters and foams. */
    LIQUID("liquid"),
    /** Powders and granules, as a bulk volume. */
    BULK("bulk");

    private final String name;

    FluidKind(String name) {
        this.name = name;
    }

    /** The fluid a substance of this form flows as; solids don't flow. */
    public static FluidKind forForm(Form form) {
        return switch (form) {
            case LIQUID, BATTER, FOAM, GAS -> LIQUID;
            case POWDER, GRANULE -> BULK;
            default -> throw new IllegalArgumentException(form + " doesn't flow");
        };
    }

    /** The kind whose fluid this is, or empty for a fluid of another mod. */
    public static Optional<FluidKind> of(Fluid fluid) {
        return Arrays.stream(values()).filter(k -> k.fluid().isSame(fluid)).findFirst();
    }

    public Fluid fluid() {
        return Registration.SUBSTANCE_FLUIDS.get(this).get();
    }

    @Override
    public String toString() {
        return name;
    }
}
