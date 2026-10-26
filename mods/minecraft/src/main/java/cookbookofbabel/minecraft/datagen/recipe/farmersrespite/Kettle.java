package cookbookofbabel.minecraft.datagen.recipe.farmersrespite;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cookbookofbabel.minecraft.datagen.recipe.Resolved;
import cookbookofbabel.minecraft.datagen.recipe.Resolved.Exact;
import cookbookofbabel.minecraft.datagen.recipe.Resolved.FluidOutput;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Farmer's Respite's kettle: one or two items brewed in a base fluid make a fluid. Like Brewin' and
 * Chewin', whose authors also wrote it, it names fluids without NBT.
 */
public final class Kettle {
    private Kettle() {}

    public static JsonObject brewing(Resolved r) {
        var json = new JsonObject();
        json.addProperty("type", "farmersrespite:brewing");
        var ingredients = new JsonArray();
        r.eachItem().forEach(i -> ingredients.add(Resolved.json(i)));
        json.add("ingredients", ingredients);
        var base = ((Exact) r.fluids().get(0)).stack();
        json.add("base", fluid(ForgeRegistries.FLUIDS.getKey(base.getFluid()).toString(), base.getAmount()));
        var result = ((FluidOutput) r.main()).stack();
        json.add("result", fluid(ForgeRegistries.FLUIDS.getKey(result.getFluid()).toString(), result.getAmount()));
        json.addProperty("cookingtime", r.ticks());
        json.addProperty("experience", 0.35);
        return json;
    }

    private static JsonObject fluid(String id, int millibuckets) {
        var json = new JsonObject();
        json.addProperty("fluid", id);
        json.addProperty("count", millibuckets);
        return json;
    }
}
