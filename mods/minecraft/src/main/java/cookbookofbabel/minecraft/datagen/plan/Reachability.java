package cookbookofbabel.minecraft.datagen.plan;

import cookbookofbabel.minecraft.dataset.IngredientId;
import cookbookofbabel.minecraft.dataset.Recipe;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What the game can make, starting from what it has without any of the mod's recipes and running
 * every planned recipe whose inputs it can make. A planned recipe whose inputs it can't make is
 * dead: it could only ever consume things nobody has.
 */
public final class Reachability {
    /** What a planned recipe takes and makes, as far as reachability goes. */
    public record Plan(Set<IngredientId> inputs, Set<IngredientId> outputs) {
        /**
         * Planned rather than dataset ingredients: they include a medium the planner added, and
         * leave out what the machine supplies or the step drops.
         */
        public static Plan of(Planned planned) {
            return new Plan(
                    planned.inputs().stream().map(Planned.In::ingredient).collect(Collectors.toUnmodifiableSet()),
                    planned.outputs().stream().map(Planned.Out::ingredient).collect(Collectors.toUnmodifiableSet()));
        }
    }

    private final Set<IngredientId> available;
    private final Function<IngredientId, Set<IngredientId>> descendants;
    private final Map<IngredientId, Set<IngredientId>> memoDescendants = new HashMap<>();

    private Reachability(Set<IngredientId> available, Function<IngredientId, Set<IngredientId>> descendants) {
        this.available = available;
        this.descendants = descendants;
    }

    /**
     * What the game starts with: the ingredients no recipe makes, which are the mod's own raw
     * substances, and whatever another mod provides. Not the named dishes, which only their steps
     * make. Other mods' things that recipes take besides, like a knife or the water a fan blows
     * through, aren't ingredients of a plan at all.
     */
    public static Set<IngredientId> seeds(Dataset dataset, Collection<Recipe> recipes, List<Mapping> mappings) {
        var made = recipes.stream()
                .flatMap(r -> Recipes.outputs(r.outputs()).stream())
                .map(o -> o.ingredient())
                .collect(Collectors.toSet());
        var seeds = dataset.ingredients().stream().map(i -> i.id()).filter(i -> !made.contains(i)).collect(Collectors.toSet());
        mappings.forEach(m -> seeds.addAll(m.entries().keySet()));
        return seeds;
    }

    /**
     * @param descendants an ingredient and everything that counts as it, since a recipe accepts
     *     anything below its input in the is-a DAG
     */
    public static Reachability of(Set<IngredientId> seeds, Collection<Plan> plans, Function<IngredientId, Set<IngredientId>> descendants) {
        var reachability = new Reachability(new HashSet<>(seeds), descendants);
        var waiting = new ArrayList<>(plans);
        var changed = true;
        while (changed) {
            changed = false;
            for (var it = waiting.iterator(); it.hasNext(); ) {
                var plan = it.next();
                if (reachability.live(plan)) {
                    it.remove();
                    changed |= reachability.available.addAll(plan.outputs());
                }
            }
        }
        return reachability;
    }

    /** The plan's inputs the game can't make, nor anything that counts as them; none if it's live. */
    public List<IngredientId> missing(Plan plan) {
        return plan.inputs().stream().filter(i -> !satisfied(i)).sorted().toList();
    }

    public boolean live(Plan plan) {
        return missing(plan).isEmpty();
    }

    private boolean satisfied(IngredientId input) {
        return memoDescendants.computeIfAbsent(input, descendants).stream().anyMatch(available::contains);
    }
}
