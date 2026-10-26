package cookbookofbabel.minecraft.dataset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * Bounded bands with strictly increasing upper bounds (exclusive), then one open-ended top band, so every temperature
 * has exactly one band. The constructor rejects anything else, which is what makes {@link #bandOf} total.
 */
public record TempBands(List<Bounded> bounded, String top) {
    public record Bounded(String id, double belowC) {}

    public TempBands {
        bounded = List.copyOf(bounded);
        for (var k = 1; k < bounded.size(); k++) {
            if (!(bounded.get(k - 1).belowC() < bounded.get(k).belowC())) {
                throw new IllegalArgumentException("belowC must strictly increase");
            }
        }
        var ids = new HashSet<String>();
        ids.add(top);
        for (var band : bounded) {
            if (!ids.add(band.id())) {
                throw new IllegalArgumentException("band IDs must be unique, but " + band.id() + " repeats");
            }
        }
    }

    /** The first band whose bound lies above the temperature, else the top one. */
    public String bandOf(double celsius) {
        return bounded.stream()
                .filter(b -> celsius < b.belowC())
                .findFirst()
                .map(Bounded::id)
                .orElse(top);
    }

    /** One band as written in JSON: the last band has no upper bound. */
    private record Entry(String id, Optional<Double> belowC) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Codec.STRING.fieldOf("id").forGetter(Entry::id),
                        Codec.DOUBLE.optionalFieldOf("belowC").forGetter(Entry::belowC))
                .apply(i, Entry::new));
    }

    public static final Codec<TempBands> CODEC = Entry.CODEC.listOf().comapFlatMap(TempBands::fromEntries, TempBands::entries);

    private static DataResult<TempBands> fromEntries(List<Entry> entries) {
        if (entries.isEmpty() || entries.get(entries.size() - 1).belowC().isPresent()) {
            return DataResult.error(() -> "the last band must omit belowC, so every temperature has a band");
        }
        var bounded = new ArrayList<Bounded>();
        for (var entry : entries.subList(0, entries.size() - 1)) {
            if (entry.belowC().isEmpty()) {
                return DataResult.error(() -> "only the last band may omit belowC, but " + entry.id() + " does");
            }
            bounded.add(new Bounded(entry.id(), entry.belowC().get()));
        }
        try {
            return DataResult.success(new TempBands(bounded, entries.get(entries.size() - 1).id()));
        } catch (IllegalArgumentException e) {
            return DataResult.error(e::getMessage);
        }
    }

    private List<Entry> entries() {
        var entries = new ArrayList<Entry>();
        bounded.forEach(b -> entries.add(new Entry(b.id(), Optional.of(b.belowC()))));
        entries.add(new Entry(top, Optional.empty()));
        return entries;
    }
}
