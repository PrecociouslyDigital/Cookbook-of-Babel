package cookbookofbabel.minecraft.substance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.util.Mth;

/** Tint colours, written in JSON as {@code #rrggbb}. */
public final class Colors {
    public static final Codec<Integer> CODEC = Codec.STRING.comapFlatMap(Colors::parse, Colors::format);

    private Colors() {}

    /**
     * A stable, muted colour for an ID, until the dataset records colours. The same ID always gets
     * the same colour, so regenerating doesn't recolour anything.
     */
    public static int forId(String id) {
        int hash = id.hashCode();
        float hue = (hash & 0xFFFF) / 65536f;
        float saturation = 0.25f + 0.35f * ((hash >>> 16 & 0xFF) / 255f);
        float value = 0.70f + 0.25f * ((hash >>> 24) / 255f);
        return Mth.hsvToRgb(hue, saturation, value) & 0xFFFFFF;
    }

    private static DataResult<Integer> parse(String hex) {
        if (!hex.matches("#[0-9a-fA-F]{6}")) {
            return DataResult.error(() -> "not a #rrggbb colour: " + hex);
        }
        return DataResult.success(Integer.parseInt(hex.substring(1), 16));
    }

    private static String format(int rgb) {
        return String.format("#%06x", rgb & 0xFFFFFF);
    }
}
