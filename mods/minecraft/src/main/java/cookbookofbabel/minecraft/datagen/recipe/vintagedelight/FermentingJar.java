package cookbookofbabel.minecraft.datagen.recipe.vintagedelight;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cookbookofbabel.minecraft.datagen.recipe.Resolved;
import cookbookofbabel.minecraft.datagen.recipe.Resolved.ItemOutput;

/** Vintage Delight's fermenting jar: up to six items, one per slot, make an item and maybe a second. */
public final class FermentingJar {
    private FermentingJar() {}

    public static JsonObject fermenting(Resolved r) {
        var json = new JsonObject();
        json.addProperty("type", "vintagedelight:fermenting");
        var ingredients = new JsonArray();
        r.eachItem().forEach(i -> ingredients.add(Resolved.json(i)));
        json.add("ingredients", ingredients);
        json.add("output", Resolved.json(((ItemOutput) r.outputs().get(0)).stack()));
        if (r.outputs().size() > 1) {
            json.add("secondaryOutput", Resolved.json(((ItemOutput) r.outputs().get(1)).stack()));
        }
        json.addProperty("processingTime", r.ticks());
        return json;
    }
}
