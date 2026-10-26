package cookbookofbabel.minecraft.datagen.recipe.thermal;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cookbookofbabel.minecraft.datagen.recipe.Resolved;
import cookbookofbabel.minecraft.datagen.recipe.Resolved.FluidOutput;
import cookbookofbabel.minecraft.datagen.recipe.Resolved.ItemOutput;

/**
 * Thermal Expansion's machines, given one item (see {@code RouteKind.THERMAL_PRESS}). A recipe
 * gives the energy it uses rather than a time; a machine draws {@link #RF_PER_TICK} without
 * augments.
 */
public final class Thermal {
    static final int RF_PER_TICK = 20;

    private Thermal() {}

    /** The multiservo press without a die: squeezes juice or oil out, and leaves what's pressed. */
    public static JsonObject press(Resolved r) {
        return recipe("thermal:press", r);
    }

    /** The centrifugal separator: splits an item into up to four items and a fluid. */
    public static JsonObject centrifuge(Resolved r) {
        return recipe("thermal:centrifuge", r);
    }

    private static JsonObject recipe(String type, Resolved r) {
        var json = new JsonObject();
        json.addProperty("type", type);
        json.add("ingredient", Resolved.json(r.items().get(0).ingredient()));
        var results = new JsonArray();
        for (var output : r.outputs()) {
            if (output instanceof ItemOutput stack) {
                var result = Resolved.json(stack.stack());
                stack.chance().ifPresent(c -> result.addProperty("chance", c));
                results.add(result);
            } else {
                results.add(Resolved.json(((FluidOutput) output).stack(), "amount"));
            }
        }
        json.add("result", results);
        json.addProperty("energy", r.ticks() * RF_PER_TICK);
        return json;
    }
}
