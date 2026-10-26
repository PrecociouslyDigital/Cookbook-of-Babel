package cookbookofbabel.minecraft.datagen.recipe.pneumaticcraft;

import com.google.gson.JsonObject;
import cookbookofbabel.minecraft.datagen.recipe.Resolved;
import cookbookofbabel.minecraft.datagen.recipe.Resolved.Exact;
import cookbookofbabel.minecraft.datagen.recipe.Resolved.FluidOutput;
import cookbookofbabel.minecraft.datagen.recipe.Resolved.ItemOutput;
import cookbookofbabel.minecraft.datagen.recipe.Resolved.Tagged;

/**
 * PneumaticCraft's thermopneumatic processing plant: an item, a fluid or both make an item, a
 * fluid or both, above a temperature in kelvin.
 */
public final class ThermoPlant {
    /** Below this a step is at room temperature, and the plant needn't be heated. */
    private static final double WARM_FROM_C = 30;
    private static final double KELVIN = 273.15;

    private ThermoPlant() {}

    public static JsonObject recipe(Resolved r) {
        var json = new JsonObject();
        json.addProperty("type", "pneumaticcraft:thermo_plant");
        r.items().stream().findFirst().ifPresent(i -> json.add("item_input", Resolved.json(i.ingredient())));
        r.fluids().stream().findFirst().ifPresent(f -> json.add("fluid_input", fluid(f)));
        for (var output : r.outputs()) {
            if (output instanceof ItemOutput item) {
                json.add("item_output", Resolved.json(item.stack()));
            } else {
                json.add("fluid_output", Resolved.json(((FluidOutput) output).stack(), "amount"));
            }
        }
        r.planned().step().recipe().temperatureC().filter(c -> c >= WARM_FROM_C).ifPresent(c -> {
            var temperature = new JsonObject();
            temperature.addProperty("min_temp", (int) Math.round(c + KELVIN));
            json.add("temperature", temperature);
        });
        return json;
    }

    /** PneumaticCraft's own fluid ingredient, which matches NBT exactly where the fluid has any. */
    private static JsonObject fluid(Resolved.FluidInput input) {
        JsonObject json;
        if (input instanceof Exact exact) {
            json = Resolved.json(exact.stack(), "amount");
            json.addProperty("fuzzyNBT", false);
        } else {
            json = new JsonObject();
            json.addProperty("tag", ((Tagged) input).tag().location().toString());
            json.addProperty("amount", input.millibuckets());
        }
        json.addProperty("type", "pneumaticcraft:fluid");
        return json;
    }
}
