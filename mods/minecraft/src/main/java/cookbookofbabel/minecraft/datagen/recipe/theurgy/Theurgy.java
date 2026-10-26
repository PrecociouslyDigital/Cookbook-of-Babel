package cookbookofbabel.minecraft.datagen.recipe.theurgy;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import cookbookofbabel.minecraft.datagen.recipe.Resolved;
import cookbookofbabel.minecraft.datagen.recipe.Resolved.Exact;
import cookbookofbabel.minecraft.datagen.recipe.Resolved.ItemOutput;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Theurgy's alchemical apparatus. Each recipe gives its own time. Its fluid ingredients name a
 * fluid without NBT.
 */
public final class Theurgy {
    private Theurgy() {}

    /** The calcination oven: a stack of one item burns down to another. */
    public static JsonObject calcination(Resolved r) {
        var json = new JsonObject();
        json.addProperty("type", "theurgy:calcination");
        var item = r.items().get(0);
        json.add("ingredient", Resolved.json(item.ingredient()));
        json.addProperty("ingredient_count", item.count());
        json.add("result", Resolved.json(result(r)));
        json.addProperty("calcination_time", r.ticks());
        return json;
    }

    /** The fermentation vat: items in a fluid ferment into an item. */
    public static JsonObject fermentation(Resolved r) {
        var json = new JsonObject();
        json.addProperty("type", "theurgy:fermentation");
        var fluid = (Exact) r.fluids().get(0);
        json.add("fluid", fluid(fluid));
        json.addProperty("fluidAmount", fluid.millibuckets());
        var ingredients = new JsonArray();
        r.eachItem().forEach(i -> ingredients.add(Resolved.json(i)));
        json.add("ingredients", ingredients);
        // This one reads its result with the vanilla stack codec, not Forge's stack format.
        json.add("result", ItemStack.CODEC.encodeStart(JsonOps.INSTANCE, result(r)).getOrThrow(false, message -> {
            throw new IllegalStateException(message);
        }));
        json.addProperty("time", r.ticks());
        return json;
    }

    /** The liquefaction cauldron: an item steeped in a solvent becomes another item. */
    public static JsonObject liquefaction(Resolved r) {
        var json = new JsonObject();
        json.addProperty("type", "theurgy:liquefaction");
        json.add("ingredient", Resolved.json(r.items().get(0).ingredient()));
        var solvent = (Exact) r.fluids().get(0);
        json.add("solvent", fluid(solvent));
        json.addProperty("solvent_amount", solvent.millibuckets());
        json.add("result", Resolved.json(result(r)));
        json.addProperty("liquefaction_time", r.ticks());
        return json;
    }

    private static ItemStack result(Resolved r) {
        return ((ItemOutput) r.main()).stack();
    }

    private static JsonObject fluid(Exact fluid) {
        var json = new JsonObject();
        json.addProperty("fluid", ForgeRegistries.FLUIDS.getKey(fluid.stack().getFluid()).toString());
        return json;
    }
}
