package cookbookofbabel.minecraft.datagen.recipe.hexerei;

import com.google.gson.JsonObject;
import cookbookofbabel.minecraft.datagen.recipe.Resolved;
import cookbookofbabel.minecraft.datagen.recipe.Resolved.Exact;
import cookbookofbabel.minecraft.datagen.recipe.Resolved.ItemOutput;

/**
 * Hexerei's dipper, over its mixing cauldron: one item dipped in one fluid comes out coated. The
 * dipper draws the liquid from the cauldron below it, dips, and lets the coat dry.
 */
public final class Dipper {
    private Dipper() {}

    public static JsonObject recipe(Resolved r) {
        var json = new JsonObject();
        json.addProperty("type", "hexerei:dipper");
        var liquid = ((Exact) r.fluids().get(0)).stack();
        json.add("liquid", Resolved.json(liquid, "amount"));
        json.addProperty("fluidLevelsConsumed", liquid.getAmount());
        json.add("input", Resolved.json(r.eachItem().get(0)));
        json.add("output", Resolved.json(((ItemOutput) r.main()).stack()));
        json.addProperty("dippingTimeInTicks", Math.max(1, r.ticks()));
        json.addProperty("dryingTimeInTicks", 200);
        json.addProperty("numberOfDips", 1);
        return json;
    }
}
