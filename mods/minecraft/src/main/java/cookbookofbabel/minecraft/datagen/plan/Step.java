package cookbookofbabel.minecraft.datagen.plan;

import cookbookofbabel.minecraft.dataset.Choice.Leaf;
import cookbookofbabel.minecraft.dataset.Outcome.Output;
import cookbookofbabel.minecraft.dataset.Recipe;
import java.util.List;
import java.util.Optional;

/**
 * One concrete variant of a dataset recipe: a set of inputs its selection allows, plus any medium
 * its technique needs that it didn't list.
 *
 * @param path where its recipes go, e.g. {@code basic_scones/step_2/v1}
 */
public record Step(String path, Recipe recipe, List<Leaf> inputs, List<Output> outputs, Optional<Leaf> addedMedium) {}
