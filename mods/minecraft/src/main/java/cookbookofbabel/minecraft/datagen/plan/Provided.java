package cookbookofbabel.minecraft.datagen.plan;

import cookbookofbabel.minecraft.dataset.IngredientId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The ingredients that have a stack without NBT: the fluids and items other mods map them to, and
 * the named dishes, which are items of their own. Only these fluids go into a machine that ignores
 * NBT on fluid inputs, and only these come out of one that ignores it on results (see {@link
 * RouteKind.Nbt}).
 */
public record Provided(Set<IngredientId> fluids, Set<IngredientId> items) {
    public static final Provided NOTHING = new Provided(Set.of(), Set.of());

    public static Provided of(List<Mapping> mappings, Dataset dataset) {
        var items = mapped(mappings, e -> !e.items().isEmpty());
        dataset.ingredients().stream().filter(i -> i.named()).forEach(i -> items.add(i.id()));
        return new Provided(Set.copyOf(mapped(mappings, e -> !e.fluids().isEmpty())), Set.copyOf(items));
    }

    private static Set<IngredientId> mapped(List<Mapping> mappings, Predicate<Mapping.Entry> provides) {
        return mappings.stream()
                .flatMap(m -> m.entries().entrySet().stream())
                .filter(e -> provides.test(e.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(HashSet::new));
    }
}
