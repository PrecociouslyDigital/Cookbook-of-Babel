package cookbookofbabel.minecraft.datagen.recipe.hexerei;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cookbookofbabel.minecraft.datagen.recipe.Resolved;
import cookbookofbabel.minecraft.datagen.recipe.Resolved.ItemOutput;

/** Hexerei's drying rack: an item hung up dries into another over time. */
public final class DryingRack {
    private DryingRack() {}

    public static JsonObject recipe(Resolved r) {
        var json = new JsonObject();
        json.addProperty("type", "hexerei:drying_rack");
        var ingredients = new JsonArray();
        ingredients.add(Resolved.json(r.items().get(0).ingredient()));
        json.add("ingredients", ingredients);
        json.add("output", Resolved.json(((ItemOutput) r.main()).stack()));
        json.addProperty("dryingTimeInTicks", r.ticks());
        return json;
    }
}
