package cookbookofbabel.minecraft.substance;

import cookbookofbabel.minecraft.dataset.Form;

/**
 * How much of a substance one item is. Flowable substances come in {@link Container#PORTION_MB};
 * a solid item weighs what one piece of its form typically does.
 */
public final class Portions {
    private Portions() {}

    public static double gramsPerItem(Form form) {
        return switch (form) {
            case WHOLE -> 150;
            case SOLID -> 400;
            case PIECE, PASTE, CURD -> 50;
            case SLICE -> 30;
            case SHRED, DICE -> 25;
            case MASH, GEL, LIQUID, BATTER, FOAM, POWDER, GRANULE -> 100;
        };
    }
}
