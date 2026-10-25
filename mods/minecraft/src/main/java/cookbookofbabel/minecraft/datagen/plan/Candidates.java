package cookbookofbabel.minecraft.datagen.plan;

import cookbookofbabel.minecraft.dataset.Amount;
import cookbookofbabel.minecraft.dataset.IngredientId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * What a step makes when it yields an ingredient. Other mods' items come first, in {@link Mod}
 * order, each only while its mod is loaded and no earlier one is; the mod's own substance or dish
 * is the last resort, and only while no other mod or pack provides the ingredient (its {@code
 * external} tag is empty). A recipe is emitted once per candidate, under that candidate's
 * conditions, so whichever mods are loaded, at most one of them loads.
 */
public final class Candidates {
    private Candidates() {}

    public sealed interface Target {
        /** {@code id} of an item or a fluid, depending on what the step yields. */
        record External(Mod mod, String id, Optional<Amount> amount) implements Target {}

        record Own() implements Target {}
    }

    public record Candidate(Target target, List<Condition> conditions) {}

    /**
     * @param provided what each mod provides for the ingredient (its first choice counts)
     * @param externalTag the ingredient's {@code external} item tag
     */
    public static List<Candidate> of(
            IngredientId ingredient,
            List<Mapping> mappings,
            BiFunction<Mod, Mapping.Entry, List<Target.External>> provided,
            String externalTag) {
        var candidates = new ArrayList<Candidate>();
        var earlier = new ArrayList<Mod>();
        for (var mapping : mappings) {
            var first = mapping.entry(ingredient).flatMap(e -> provided.apply(mapping.mod(), e).stream().findFirst());
            if (first.isEmpty()) {
                continue;
            }
            var conditions = new ArrayList<Condition>();
            if (!mapping.mod().alwaysLoaded()) {
                conditions.add(new Condition.ModLoaded(mapping.mod()));
            }
            earlier.forEach(m -> conditions.add(new Condition.Not(new Condition.ModLoaded(m))));
            candidates.add(new Candidate(first.get(), List.copyOf(conditions)));
            if (mapping.mod().alwaysLoaded()) {
                return candidates;
            }
            earlier.add(mapping.mod());
        }
        var own = new ArrayList<Condition>();
        earlier.forEach(m -> own.add(new Condition.Not(new Condition.ModLoaded(m))));
        own.add(new Condition.TagEmpty(externalTag));
        candidates.add(new Candidate(new Target.Own(), List.copyOf(own)));
        return candidates;
    }

    public static List<Target.External> items(Mod mod, Mapping.Entry entry) {
        return entry.items().stream().map(i -> new Target.External(mod, i.id(), i.amount())).toList();
    }

    public static List<Target.External> fluids(Mod mod, Mapping.Entry entry) {
        return entry.fluids().stream().map(f -> new Target.External(mod, f, Optional.empty())).toList();
    }
}
