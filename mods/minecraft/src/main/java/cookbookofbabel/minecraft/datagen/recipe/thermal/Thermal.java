package cookbookofbabel.minecraft.datagen.recipe.thermal;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cookbookofbabel.minecraft.datagen.recipe.Resolved;
import cookbookofbabel.minecraft.datagen.recipe.Resolved.Exact;
import cookbookofbabel.minecraft.datagen.recipe.Resolved.FluidOutput;
import cookbookofbabel.minecraft.datagen.recipe.Resolved.ItemOutput;

/**
 * Thermal Expansion's machines, given one of each item (see {@code Constraints.unitCounts}). A
 * recipe gives the energy it uses rather than a time; a machine draws {@link #RF_PER_TICK} without
 * augments. Fluids go in by name, without NBT.
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

    /** The induction smelter: melts up to three items together into up to four. */
    public static JsonObject smelter(Resolved r) {
        return recipe("thermal:smelter", r);
    }

    /** The fluid encapsulator: fills or coats an item with a fluid. */
    public static JsonObject bottler(Resolved r) {
        return recipe("thermal:bottler", r);
    }

    /** The blast chiller: sets a fluid, in a cast or not, into an item. */
    public static JsonObject chiller(Resolved r) {
        return recipe("thermal:chiller", r);
    }

    private static JsonObject recipe(String type, Resolved r) {
        var json = new JsonObject();
        json.addProperty("type", type);
        var ingredients = new JsonArray();
        for (var item : r.eachItem()) {
            // CoFH reads a bare array as several ingredients; under "value" it is one, of any of them.
            var wrapped = new JsonObject();
            wrapped.add("value", Resolved.json(item));
            ingredients.add(wrapped);
        }
        r.fluids().forEach(f -> ingredients.add(Resolved.json(((Exact) f).stack(), "amount")));
        json.add("ingredients", ingredients);
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
