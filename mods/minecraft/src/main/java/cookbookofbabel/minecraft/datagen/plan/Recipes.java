package cookbookofbabel.minecraft.datagen.plan;

import cookbookofbabel.minecraft.dataset.Choice;
import cookbookofbabel.minecraft.dataset.Choice.Leaf;
import cookbookofbabel.minecraft.dataset.Choice.Select;
import cookbookofbabel.minecraft.dataset.IngredientId;
import cookbookofbabel.minecraft.dataset.Outcome;
import cookbookofbabel.minecraft.dataset.Outcome.Output;
import cookbookofbabel.minecraft.dataset.Recipe;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Questions about, and rewrites of, the dataset's recipes. */
public final class Recipes {
    private Recipes() {}

    public static List<Leaf> leaves(Select select) {
        return select.of().stream()
                .flatMap(c -> c instanceof Select nested ? leaves(nested).stream() : Stream.of((Leaf) c))
                .toList();
    }

    /** Every output of every branch. */
    public static List<Output> outputs(Outcome outcome) {
        if (outcome instanceof Outcome.Outputs o) {
            return o.outputs();
        }
        return ((Outcome.OneOf) outcome).branches().stream().flatMap(b -> outputs(b.outputs()).stream()).toList();
    }

    public static boolean consumes(Recipe recipe, IngredientId ingredient) {
        return leaves(recipe.inputs()).stream().anyMatch(l -> l.ingredient().equals(ingredient));
    }

    public static boolean produces(Recipe recipe, IngredientId ingredient) {
        return outputs(recipe.outputs()).stream().anyMatch(o -> o.ingredient().equals(ingredient));
    }

    public static Recipe renameInputs(Recipe r, IngredientId from, IngredientId to) {
        return new Recipe(r.id(), r.source(), r.technique(), rename(r.inputs(), from, to), r.outputs(), r.temperatureC(), r.end(), r.issues());
    }

    public static Recipe renameOutputs(Recipe r, IngredientId from, IngredientId to) {
        return new Recipe(r.id(), r.source(), r.technique(), r.inputs(), rename(r.outputs(), from, to), r.temperatureC(), r.end(), r.issues());
    }

    /** Everything that goes into making the ingredient, through every recipe that makes it. */
    public static Set<IngredientId> provenance(Collection<Recipe> recipes, IngredientId ingredient) {
        var seen = new LinkedHashSet<IngredientId>();
        var todo = new ArrayDeque<IngredientId>(List.of(ingredient));
        while (!todo.isEmpty()) {
            var next = todo.pop();
            for (var recipe : recipes) {
                if (produces(recipe, next)) {
                    for (var leaf : leaves(recipe.inputs())) {
                        if (seen.add(leaf.ingredient())) {
                            todo.add(leaf.ingredient());
                        }
                    }
                }
            }
        }
        return seen;
    }

    /** What can be made from ingredients no recipe makes, by any recipe, taking all its inputs. */
    public static Set<IngredientId> reachable(Collection<Recipe> recipes, Collection<IngredientId> leaves) {
        var made = new LinkedHashSet<>(leaves);
        var changed = true;
        while (changed) {
            changed = false;
            for (var recipe : recipes) {
                if (made.containsAll(leaves(recipe.inputs()).stream().map(Leaf::ingredient).toList())) {
                    for (var output : outputs(recipe.outputs())) {
                        changed |= made.add(output.ingredient());
                    }
                }
            }
        }
        return made;
    }

    private static Select rename(Select select, IngredientId from, IngredientId to) {
        return new Select(select.min(), select.max(), select.of().stream()
                .map(c -> c instanceof Select nested
                        ? (Choice) rename(nested, from, to)
                        : renameLeaf((Leaf) c, from, to))
                .toList());
    }

    private static Choice renameLeaf(Leaf leaf, IngredientId from, IngredientId to) {
        return leaf.ingredient().equals(from) ? new Leaf(to, leaf.amount()) : leaf;
    }

    private static Outcome rename(Outcome outcome, IngredientId from, IngredientId to) {
        if (outcome instanceof Outcome.Outputs o) {
            return new Outcome.Outputs(o.outputs().stream()
                    .map(out -> out.ingredient().equals(from) ? new Output(to, out.amount(), out.role(), out.chance()) : out)
                    .toList());
        }
        return new Outcome.OneOf(((Outcome.OneOf) outcome).branches().stream()
                .map(b -> new Outcome.Branch(b.weight(), rename(b.outputs(), from, to)))
                .toList());
    }
}
