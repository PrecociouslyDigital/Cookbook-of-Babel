package cookbookofbabel.minecraft.datagen.plan;

import cookbookofbabel.minecraft.dataset.Choice;
import cookbookofbabel.minecraft.dataset.Choice.Leaf;
import cookbookofbabel.minecraft.dataset.Choice.Select;
import java.util.ArrayList;
import java.util.List;

/**
 * Expands a recipe's inputs into the concrete sets of ingredients it can take in. Minecraft recipes
 * have no optional inputs, so each set becomes a recipe of its own.
 */
public final class Selections {
    /** More variants than this of one step would flood recipe viewers. */
    public static final int MAX_VARIANTS = 8;

    private Selections() {}

    /**
     * Every combination the selection allows, up to {@code cap}: the largest first, so the first
     * variant uses every ingredient it can. Never an empty combination.
     */
    public static List<List<Leaf>> expand(Select select, int cap) {
        return combinations(select, cap).stream().filter(c -> !c.isEmpty()).toList();
    }

    private static List<List<Leaf>> combinations(Select select, int cap) {
        var options = select.of().stream().map(choice -> expandChoice(choice, cap)).toList();
        var result = new ArrayList<List<Leaf>>();
        var most = Math.min(select.most(), options.size());
        for (var size = most; size >= Math.max(0, select.least()) && result.size() < cap; size--) {
            for (var chosen : subsets(options.size(), size)) {
                for (var combination : product(chosen.stream().map(options::get).toList(), cap - result.size())) {
                    result.add(combination);
                }
                if (result.size() >= cap) {
                    break;
                }
            }
        }
        return result;
    }

    /** A nested selection that picks nothing isn't picked at all, so it can't count as a choice. */
    private static List<List<Leaf>> expandChoice(Choice choice, int cap) {
        return choice instanceof Select nested
                ? combinations(nested, cap).stream().filter(c -> !c.isEmpty()).toList()
                : List.of(List.of((Leaf) choice));
    }

    /** Index sets of the given size, in lexicographic order. */
    static List<List<Integer>> subsets(int n, int size) {
        var result = new ArrayList<List<Integer>>();
        subsets(0, n, size, new ArrayList<>(), result);
        return result;
    }

    private static void subsets(int from, int n, int size, List<Integer> current, List<List<Integer>> out) {
        if (current.size() == size) {
            out.add(List.copyOf(current));
            return;
        }
        for (var i = from; i <= n - (size - current.size()); i++) {
            current.add(i);
            subsets(i + 1, n, size, current, out);
            current.remove(current.size() - 1);
        }
    }

    /** One pick from each list, concatenated, up to {@code cap} results. */
    private static List<List<Leaf>> product(List<List<List<Leaf>>> lists, int cap) {
        List<List<Leaf>> result = List.of(List.of());
        for (var options : lists) {
            var next = new ArrayList<List<Leaf>>();
            for (var prefix : result) {
                for (var option : options) {
                    if (next.size() >= cap) {
                        break;
                    }
                    var joined = new ArrayList<>(prefix);
                    joined.addAll(option);
                    next.add(joined);
                }
            }
            result = next;
        }
        return result;
    }
}
