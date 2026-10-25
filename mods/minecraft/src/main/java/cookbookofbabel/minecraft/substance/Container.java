package cookbookofbabel.minecraft.substance;

import com.mojang.serialization.Codec;
import cookbookofbabel.minecraft.Registration;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;

/**
 * What a flowable substance is carried in as an item. Each container holds one portion and has a
 * fluid of its own, so a filled container knows what it empties back into from its fluid alone.
 */
public enum Container implements StringRepresentable {
    /** For liquids, batters and foams. */
    BOTTLE("bottle", "liquid"),
    /** A twist of paper, for powders and granules. Their portion is a bulk volume. */
    PAPER_TWIST("paper_twist", "bulk");

    public static final int PORTION_MB = 250;
    public static final Codec<Container> CODEC = StringRepresentable.fromEnum(Container::values);

    private final String name;
    /** The registry name of the fluid it holds. */
    public final String fluidName;

    Container(String name, String fluidName) {
        this.name = name;
        this.fluidName = fluidName;
    }

    public Item empty() {
        return switch (this) {
            case BOTTLE -> Items.GLASS_BOTTLE;
            case PAPER_TWIST -> Items.PAPER;
        };
    }

    public Fluid fluid() {
        return Registration.SUBSTANCE_FLUIDS.get(this).get();
    }

    public static Optional<Container> holding(Fluid fluid) {
        return Arrays.stream(values()).filter(c -> c.fluid().isSame(fluid)).findFirst();
    }

    public static Optional<Container> named(String name) {
        return Arrays.stream(values()).filter(c -> c.name.equals(name)).findFirst();
    }

    public static Optional<Container> emptiedAs(Item item) {
        return Arrays.stream(values()).filter(c -> c.empty() == item).findFirst();
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
