package cookbookofbabel.minecraft.datagen.recipe.hexerei;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cookbookofbabel.minecraft.datagen.recipe.Resolved;
import cookbookofbabel.minecraft.datagen.recipe.Resolved.ItemOutput;

/** Hexerei's pestle and mortar: up to five items, one per slot, ground into one. */
public final class PestleAndMortar {
    private PestleAndMortar() {}

    public static JsonObject recipe(Resolved r) {
        var json = new JsonObject();
        json.addProperty("type", "hexerei:pestle_and_mortar");
        var ingredients = new JsonArray();
        r.eachItem().forEach(i -> ingredients.add(Resolved.json(i)));
        json.add("ingredients", ingredients);
        json.add("output", Resolved.json(((ItemOutput) r.main()).stack()));
        json.addProperty("grindingTime", r.ticks());
        return json;
    }
}
