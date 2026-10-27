package cookbookofbabel.minecraft.clock;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import cookbookofbabel.minecraft.dataset.End;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;
import net.jqwik.api.Example;
import net.minecraft.resources.ResourceLocation;

/**
 * The clock agrees with babel on which of bread's transitions comes first, and how long it takes. The table is babel's
 * own answers, each row from {@code babel query spoilage bread --temp <temp_c> --aw <water_activity>}; the transitions
 * are read from the same dataset recipes babel read.
 */
class SpoilageParity {
    private static final Path RECIPES = Path.of("../../data/layers/reviewed/LicenseRef-original/original/recipes");
    private static final Pattern ANSWER = Pattern.compile("(\\S+) after about (\\S+) d \\(.*\\)");
    private static final String NONE = "no time-based spoilage applies under these conditions";
    private static final long TICKS_PER_DAY = 24_000;
    /** Babel prints days to one decimal place. */
    private static final double PRINTED = 0.05;

    @Example
    void breadSpoilsFirstWhereAndWhenBabelSays() throws IOException {
        var transitions = new TreeMap<ResourceLocation, Timing>();
        for (var id : new String[] {"bread.stale", "bread.mold"}) {
            transitions.put(Sensitivities.id(id), timing(id));
        }
        try (var table = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream("bread_spoilage.tsv")), StandardCharsets.UTF_8))) {
            var rows = table.lines().skip(1).map(line -> line.split("\t")).toList();
            assertThat(rows).isNotEmpty();
            for (var row : rows) {
                var environment = new Environment(Double.parseDouble(row[0]), Double.parseDouble(row[1]));
                var next = StackClock.start(transitions, environment, 0, 1).next(transitions, 0);
                if (row[2].equals(NONE)) {
                    assertThat(next).as("%s °C, aw %s", row[0], row[1]).isEmpty();
                    continue;
                }
                var answer = ANSWER.matcher(row[2]);
                assertThat(answer.matches()).as(row[2]).isTrue();
                assertThat(next).as("%s °C, aw %s", row[0], row[1]).isPresent();
                assertThat(next.get().transition()).isEqualTo(Sensitivities.id(answer.group(1)));
                assertThat((double) next.get().at() / TICKS_PER_DAY)
                        .as("%s °C, aw %s", row[0], row[1])
                        .isBetween(
                                Double.parseDouble(answer.group(2)) - PRINTED - 1.0 / TICKS_PER_DAY,
                                Double.parseDouble(answer.group(2)) + PRINTED + 1.0 / TICKS_PER_DAY);
            }
        }
    }

    /** Days become ticks at a fixed scale; any scale would do, as both sides use the same one. */
    private static Timing timing(String recipe) throws IOException {
        var json = JsonParser.parseString(Files.readString(RECIPES.resolve(recipe + ".json"))).getAsJsonObject();
        var end = (End.ByTime) End.CODEC.parse(JsonOps.INSTANCE, json.get("end")).getOrThrow(false, e -> {});
        assertThat(end.duration().unit()).isEqualTo("d");
        long ticks = Math.round(end.duration().qty() * TICKS_PER_DAY);
        return new Timing(ticks, end.sensitivity().orElseThrow());
    }
}
