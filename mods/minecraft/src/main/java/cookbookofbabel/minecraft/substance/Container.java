package cookbookofbabel.minecraft.substance;

import com.mojang.serialization.Codec;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * A vessel a flowable substance is carried in as an item. Each filled vessel holds one portion of
 * one fluid kind, so a filled vessel knows what it empties back into from its fluid alone. An
 * empty vessel fills with any kind it holds, which is how a spout fills a bowl with either.
 */
public enum Container implements StringRepresentable {
    /** For liquids, batters and foams. */
    BOTTLE("bottle") {
        @Override
        public boolean holds(FluidKind kind) {
            return kind == FluidKind.LIQUID;
        }
    },
    /** A twist of paper, for powders and granules. Their portion is a bulk volume. */
    PAPER_TWIST("paper_twist") {
        @Override
        public boolean holds(FluidKind kind) {
            return kind == FluidKind.BULK;
        }
    },
    /**
     * The kitchen bowl, for intermediates handled by hand: it holds liquids and powders alike, and
     * FD's pot serves its meals in one.
     */
    BOWL("bowl") {
        @Override
        public boolean holds(FluidKind kind) {
            return true;
        }
    };

    public static final int PORTION_MB = 250;
    public static final Codec<Container> CODEC = StringRepresentable.fromEnum(Container::values);

    private final String name;

    Container(String name) {
        this.name = name;
    }

    /** Whether an empty vessel of this kind can be filled with the fluid kind. */
    public abstract boolean holds(FluidKind kind);

    public Item empty() {
        return switch (this) {
            case BOTTLE -> Items.GLASS_BOTTLE;
            case PAPER_TWIST -> Items.PAPER;
            case BOWL -> Items.BOWL;
        };
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
