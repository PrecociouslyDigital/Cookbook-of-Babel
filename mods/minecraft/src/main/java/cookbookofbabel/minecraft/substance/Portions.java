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
            case MASH, GEL, LIQUID, BATTER, FOAM, POWDER, GRANULE, GAS -> 100;
        };
    }

    /**
     * How many millilitres of the substance a millibucket holds. Gases are carried compressed, a
     * litre to the millibucket, since their low density would otherwise overflow a tank.
     */
    public static double millilitresPerMillibucket(Form form) {
        return switch (form) {
            case GAS -> 1000;
            case WHOLE, SOLID, PIECE, PASTE, CURD, SLICE, SHRED, DICE, MASH, GEL, LIQUID, BATTER, FOAM, POWDER, GRANULE -> 1;
        };
    }
}
