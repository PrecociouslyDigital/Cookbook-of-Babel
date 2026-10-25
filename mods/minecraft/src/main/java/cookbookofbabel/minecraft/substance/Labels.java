package cookbookofbabel.minecraft.substance;

/** Dataset labels are lower case, like running text; item names start with a capital. */
public final class Labels {
    private Labels() {}

    public static String asName(String label) {
        return label.isEmpty() ? label : label.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + label.substring(1);
    }
}
