package cookbookofbabel.minecraft.dataset;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import cookbookofbabel.minecraft.substance.Nutrition;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A named dish of the dataset, registered as an item of its own. The dishes are generated from the
 * export into a resource, which is read once before items register.
 *
 * @param label the English name, used when no translation is loaded
 */
public record NamedDish(String id, String label, Form form, Optional<Nutrition> nutrition) {
    private static final String RESOURCE = "/cookbook_of_babel/named_dishes.json";

    private static final Codec<NamedDish> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.STRING.fieldOf("id").forGetter(NamedDish::id),
                    Codec.STRING.fieldOf("label").forGetter(NamedDish::label),
                    Form.CODEC.fieldOf("form").forGetter(NamedDish::form),
                    Nutrition.CODEC.optionalFieldOf("nutrition").forGetter(NamedDish::nutrition))
            .apply(i, NamedDish::new));

    private static final List<NamedDish> ALL = read();
    private static final Map<String, NamedDish> BY_ID =
            ALL.stream().collect(Collectors.toMap(NamedDish::id, Function.identity()));

    public static List<NamedDish> values() {
        return ALL;
    }

    public static Optional<NamedDish> byId(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    private static List<NamedDish> read() {
        try (var in = NamedDish.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("no " + RESOURCE + "; run generateDatasetSources");
            }
            var json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            return CODEC.listOf().parse(JsonOps.INSTANCE, json).getOrThrow(false, message -> {
                throw new IllegalStateException("can't read " + RESOURCE + ": " + message);
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
