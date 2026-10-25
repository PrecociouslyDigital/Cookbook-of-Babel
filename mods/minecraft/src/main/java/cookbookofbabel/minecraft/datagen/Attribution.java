package cookbookofbabel.minecraft.datagen;

import com.google.common.hash.Hashing;
import cookbookofbabel.minecraft.dataset.Export;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/**
 * {@code ATTRIBUTION.md} and {@code LICENSE-data} at the root of the generated resources, so the
 * jar and the standalone datapack carry the dataset's sources and the licences its data is under.
 * The mod's code is CC0; the data it generates is not.
 */
final class Attribution implements DataProvider {
    private static final Map<String, String> LICENCE_TEXTS = Map.of(
            "CC0-1.0", "https://creativecommons.org/publicdomain/zero/1.0/legalcode",
            "CC-BY-4.0", "https://creativecommons.org/licenses/by/4.0/legalcode",
            "CC-BY-SA-4.0", "https://creativecommons.org/licenses/by-sa/4.0/legalcode",
            "ODbL-1.0", "https://opendatacommons.org/licenses/odbl/1-0/",
            "LicenseRef-original", "content written for the Cookbook of Babel; see its repository");

    private final PackOutput output;
    private final Export export;

    Attribution(PackOutput output, Export export) {
        this.output = output;
        this.export = export;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        try {
            write(cache, "ATTRIBUTION.md", attribution());
            write(cache, "LICENSE-data", licence());
            return CompletableFuture.completedFuture(null);
        } catch (java.io.IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private String attribution() {
        var out = new StringBuilder("# Attribution\n\n");
        out.append("The recipes, substances and tags in this mod's data are derived from the Cookbook of Babel dataset, ");
        out.append("which draws on these sources:\n\n");
        export.sources().stream().sorted(Comparator.comparing(s -> s.id())).forEach(s -> {
            out.append("- ").append(s.url().map(u -> "[" + s.name() + "](" + u + ")").orElse(s.name()));
            out.append(", ").append(s.licence()).append('\n');
        });
        out.append("\nThe data as a whole is available under ").append(String.join(" AND ", export.licence()))
                .append("; see LICENSE-data.\n");
        return out.toString();
    }

    private String licence() {
        var out = new StringBuilder("The data generated from the Cookbook of Babel dataset and bundled with this mod ");
        out.append("(everything under data/ and the substance definitions) is licensed under ")
                .append(String.join(" AND ", export.licence())).append(".\n\n");
        export.licence().forEach(l -> out.append(l).append(": ").append(LICENCE_TEXTS.getOrDefault(l, "see the dataset")).append('\n'));
        out.append("\nThe mod's code is dedicated to the public domain under CC0-1.0.\n");
        return out.toString();
    }

    @SuppressWarnings("deprecation")
    private void write(CachedOutput cache, String name, String text) throws java.io.IOException {
        var bytes = text.getBytes(StandardCharsets.UTF_8);
        cache.writeIfNeeded(output.getOutputFolder().resolve(name), bytes, Hashing.sha1().hashBytes(bytes));
    }

    @Override
    public String getName() {
        return "Attribution";
    }
}
