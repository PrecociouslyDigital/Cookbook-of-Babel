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
import java.util.function.UnaryOperator;
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

    /** The recipe with every ingredient, in and out, renamed. */
    public static Recipe rename(Recipe r, UnaryOperator<IngredientId> rename) {
        return map(r,
                l -> new Leaf(rename.apply(l.ingredient()), l.amount()),
                o -> new Output(rename.apply(o.ingredient()), o.amount(), o.role(), o.chance()));
    }

    /** The recipe with every input leaf and every output of every branch rewritten. */
    public static Recipe map(Recipe r, UnaryOperator<Leaf> leaf, UnaryOperator<Output> output) {
        return new Recipe(r.id(), r.source(), r.technique(), map(r.inputs(), leaf), map(r.outputs(), output), r.temperatureC(), r.end(), r.issues());
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

    private static Select map(Select select, UnaryOperator<Leaf> leaf) {
        return new Select(select.min(), select.max(), select.of().stream()
                .map(c -> c instanceof Select nested ? (Choice) map(nested, leaf) : leaf.apply((Leaf) c))
                .toList());
    }

    private static Outcome map(Outcome outcome, UnaryOperator<Output> output) {
        if (outcome instanceof Outcome.Outputs o) {
            return new Outcome.Outputs(o.outputs().stream().map(output).toList());
        }
        return new Outcome.OneOf(((Outcome.OneOf) outcome).branches().stream()
                .map(b -> new Outcome.Branch(b.weight(), map(b.outputs(), output)))
                .toList());
    }
}
