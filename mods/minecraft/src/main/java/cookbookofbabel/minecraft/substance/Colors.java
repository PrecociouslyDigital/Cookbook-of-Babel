package cookbookofbabel.minecraft.substance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.util.Mth;

/** Tint colours, written in JSON as {@code #rrggbb}. */
public final class Colors {
    public static final Codec<Integer> CODEC = Codec.STRING.comapFlatMap(Colors::parse, Colors::format);

    private Colors() {}

    /**
     * A stable, muted colour for an ID, for anything the dataset's tint registry doesn't cover. The
     * same ID always gets the same colour, so regenerating doesn't recolour anything.
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

    /** Parses {@code #rrggbb}; empty when the string isn't one. */
    public static Optional<Integer> parseHex(String hex) {
        return hex != null && hex.matches("#[0-9a-fA-F]{6}")
                ? Optional.of(Integer.parseUnsignedInt(hex.substring(1), 16))
                : Optional.empty();
    }

    /**
     * The colour for the first word in the label or aliases that the tint table knows, jittered
     * per ID; empty when no word matches.
     */
    public static Optional<Integer> tint(Map<String, Integer> tints, String label, List<String> aliases, String id) {
        for (var word : words(label)) {
            var base = tints.get(word);
            if (base != null) return Optional.of(jitter(base, id));
        }
        for (var alias : aliases) {
            for (var word : words(alias)) {
                var base = tints.get(word);
                if (base != null) return Optional.of(jitter(base, id));
            }
        }
        return Optional.empty();
    }

    private static List<String> words(String text) {
        var bare = text.replaceAll("\\(.*?\\)", " ").toLowerCase(Locale.ROOT);
        return List.of(bare.split("[^a-z']+"));
    }

    /** Nudges a word's colour per ID, so everything sharing a word doesn't render identically. */
    public static int jitter(int rgb, String id) {
        var hsb = rgbToHsb(rgb);
        int hash = id.hashCode();
        float hue = (hsb[0] + ((hash & 0xFF) / 255f - 0.5f) * (10f / 360f) + 1f) % 1f;
        float sat = Mth.clamp(hsb[1] * (0.85f + 0.2f * ((hash >>> 8 & 0xFF) / 255f)), 0f, 1f);
        float value = Mth.clamp(hsb[2] * (0.92f + 0.12f * ((hash >>> 16 & 0xFF) / 255f)), 0f, 1f);
        return Mth.hsvToRgb(hue, sat, value) & 0xFFFFFF;
    }

    private static float[] rgbToHsb(int rgb) {
        float r = (rgb >> 16 & 0xFF) / 255f, g = (rgb >> 8 & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float hue;
        if (delta == 0) {
            hue = 0;
        } else if (max == r) {
            hue = ((g - b) / delta % 6f + 6f) % 6f / 6f;
        } else if (max == g) {
            hue = ((b - r) / delta + 2f) / 6f;
        } else {
            hue = ((r - g) / delta + 4f) / 6f;
        }
        return new float[] {hue, max == 0 ? 0 : delta / max, max};
    }

    private static String format(int rgb) {
        return String.format("#%06x", rgb & 0xFFFFFF);
    }
}
