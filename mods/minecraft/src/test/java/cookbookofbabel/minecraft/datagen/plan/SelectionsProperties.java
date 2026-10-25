package cookbookofbabel.minecraft.datagen.plan;

import static org.assertj.core.api.Assertions.assertThat;

import cookbookofbabel.minecraft.dataset.Amount;
import cookbookofbabel.minecraft.dataset.Choice;
import cookbookofbabel.minecraft.dataset.Choice.Leaf;
import cookbookofbabel.minecraft.dataset.Choice.Select;
import cookbookofbabel.minecraft.dataset.IngredientId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class SelectionsProperties {
    /** Selections of up to 8 distinct leaves, nested at most twice, with bounds inside 0..n. */
    @Provide
    Arbitrary<Select> selects() {
        return Arbitraries.integers().between(0, Integer.MAX_VALUE).list().ofSize(24).map(seeds -> {
            var next = new AtomicInteger();
            return select(seeds, next, new AtomicInteger(), 0);
        });
    }

    private static Select select(List<Integer> seeds, AtomicInteger cursor, AtomicInteger leaves, int depth) {
        var size = 1 + seed(seeds, cursor) % 3;
        var choices = new ArrayList<Choice>();
        for (var i = 0; i < size && leaves.get() < 8; i++) {
            if (depth < 2 && seed(seeds, cursor) % 3 == 0) {
                choices.add(select(seeds, cursor, leaves, depth + 1));
            } else {
                choices.add(new Leaf(new IngredientId("l" + leaves.getAndIncrement()), new Amount(1, "g")));
            }
        }
        var n = choices.size();
        var lo = seed(seeds, cursor) % (n + 1);
        var hi = lo + seed(seeds, cursor) % (n - lo + 1);
        var bounded = seed(seeds, cursor) % 2 == 0;
        return new Select(bounded ? Optional.of(lo) : Optional.empty(), bounded ? Optional.of(hi) : Optional.empty(), choices);
    }

    private static int seed(List<Integer> seeds, AtomicInteger cursor) {
        return seeds.get(cursor.getAndIncrement() % seeds.size());
    }

    @Property
    void everyCombinationSatisfiesItsBounds(@ForAll("selects") Select select) {
        for (var combination : Selections.expand(select, Integer.MAX_VALUE)) {
            assertThat(valid(select, ids(combination))).as("%s from %s", combination, select).isTrue();
        }
    }

    @Property
    void everyAllowedCombinationIsProduced(@ForAll("selects") Select select) {
        var leaves = Recipes.leaves(select).stream().map(Leaf::ingredient).toList();
        var allowed = new HashSet<Set<IngredientId>>();
        for (var mask = 1; mask < 1 << leaves.size(); mask++) {
            var subset = new HashSet<IngredientId>();
            for (var i = 0; i < leaves.size(); i++) {
                if ((mask & 1 << i) != 0) {
                    subset.add(leaves.get(i));
                }
            }
            if (valid(select, subset)) {
                allowed.add(subset);
            }
        }
        var produced = Selections.expand(select, Integer.MAX_VALUE).stream().map(SelectionsProperties::ids).collect(Collectors.toSet());
        assertThat(produced).isEqualTo(allowed);
    }

    @Property
    void theCapIsRespected(@ForAll("selects") Select select) {
        assertThat(Selections.expand(select, Selections.MAX_VARIANTS)).hasSizeLessThanOrEqualTo(Selections.MAX_VARIANTS);
    }

    /** Whether picking exactly these leaves is something the selection allows. */
    private static boolean valid(Select select, Set<IngredientId> chosen) {
        var count = 0;
        for (var choice : select.of()) {
            var mine = Recipes.leaves(choice instanceof Select s ? s : new Select(Optional.empty(), Optional.empty(), List.of(choice)))
                    .stream().map(Leaf::ingredient).filter(chosen::contains).collect(Collectors.toSet());
            if (mine.isEmpty()) {
                continue;
            }
            count++;
            if (choice instanceof Select nested && !valid(nested, mine)) {
                return false;
            }
        }
        return count >= select.least() && count <= select.most();
    }

    private static Set<IngredientId> ids(List<Leaf> combination) {
        return combination.stream().map(Leaf::ingredient).collect(Collectors.toSet());
    }
}
