package cookbookofbabel.minecraft.substance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import cookbookofbabel.minecraft.dataset.Form;

/**
 * How a substance exists in the game. A substance has exactly one carrier, so it can't exist as an
 * item and, separately, as a fluid that could drift apart from it.
 */
public sealed interface Carrier {
    /** One item, distinguished by the substance in its NBT. */
    record Solid() implements Carrier {}

    /** A fluid; as an item, a {@link Container} filled with one portion of it. */
    record Flowable(Container container) implements Carrier {}

    Carrier SOLID = new Solid();

    Codec<Carrier> CODEC = Codec.STRING.comapFlatMap(Carrier::parse, Carrier::name);

    /** The carrier a substance of this form has unless its definition says otherwise. */
    static Carrier forForm(Form form) {
        return switch (form) {
            case LIQUID, BATTER, FOAM -> new Flowable(Container.BOTTLE);
            case POWDER, GRANULE -> new Flowable(Container.PAPER_TWIST);
            case WHOLE, PIECE, SLICE, SHRED, DICE, MASH, PASTE, GEL, CURD, SOLID -> SOLID;
        };
    }

    private static DataResult<Carrier> parse(String name) {
        if (name.equals("solid")) {
            return DataResult.success(SOLID);
        }
        return Container.named(name)
                .<DataResult<Carrier>>map(c -> DataResult.success(new Flowable(c)))
                .orElseGet(() -> DataResult.error(() -> "unknown carrier " + name + " (expected solid or a container)"));
    }

    private static String name(Carrier carrier) {
        return carrier instanceof Flowable f ? f.container().getSerializedName() : "solid";
    }
}
