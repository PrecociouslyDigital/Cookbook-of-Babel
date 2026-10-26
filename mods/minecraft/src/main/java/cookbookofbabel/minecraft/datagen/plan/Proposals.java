package cookbookofbabel.minecraft.datagen.plan;

import cookbookofbabel.minecraft.dataset.Ingredient;
import cookbookofbabel.minecraft.dataset.IngredientId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Other mods' items and fluids that name a dataset ingredient, proposed for a mapping. A proposal
 * is a guess, so it only becomes a mapping when someone copies it into {@code mappings/<mod>.json}.
 */
public final class Proposals {
    private Proposals() {}

    /** An item or fluid, by its ID and its English name if it has one. */
    public record Named(String id, Optional<String> englishName) {}

    /**
     * Each ingredient that something names exactly: by its ID, label or an alias, against the
     * thing's ID path or English name, ignoring case, punctuation and plurals.
     */
    public static Map<IngredientId, List<String>> match(List<Ingredient> ingredients, List<Named> things) {
        var byKey = new HashMap<String, List<String>>();
        for (var thing : things) {
            var path = thing.id().substring(thing.id().indexOf(':') + 1);
            Stream.concat(Stream.of(path), thing.englishName().stream())
                    .flatMap(name -> keys(name).stream())
                    .distinct()
                    .forEach(k -> byKey.computeIfAbsent(k, x -> new ArrayList<>()).add(thing.id()));
        }
        var proposals = new TreeMap<IngredientId, List<String>>();
        for (var ingredient : ingredients) {
            var matched = Stream.concat(Stream.of(ingredient.id().value(), ingredient.label()), ingredient.aliases().stream())
                    .flatMap(name -> keys(name).stream())
                    .flatMap(k -> byKey.getOrDefault(k, List.of()).stream())
                    .distinct()
                    .sorted()
                    .toList();
            if (!matched.isEmpty()) {
                proposals.put(ingredient.id(), matched);
            }
        }
        return proposals;
    }

    /**
     * A name as lower-case words joined by underscores, once for each way its last word might be
     * singular: "cookies" could be "cookie" or "cooky", so a name matches another when their keys
     * share one.
     */
    static Set<String> keys(String name) {
        var words = Stream.of(name.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")).filter(w -> !w.isEmpty()).toList();
        if (words.isEmpty()) {
            return Set.of();
        }
        var head = String.join("_", words.subList(0, words.size() - 1));
        return singulars(words.get(words.size() - 1)).stream()
                .map(last -> head.isEmpty() ? last : head + "_" + last)
                .collect(Collectors.toSet());
    }

    private static Set<String> singulars(String word) {
        var forms = new java.util.HashSet<String>();
        forms.add(word);
        if (word.endsWith("ies")) {
            forms.add(word.substring(0, word.length() - 3) + "y");
        }
        if (word.endsWith("es")) {
            forms.add(word.substring(0, word.length() - 2));
        }
        if (word.endsWith("s") && !word.endsWith("ss")) {
            forms.add(word.substring(0, word.length() - 1));
        }
        return forms;
    }
}
