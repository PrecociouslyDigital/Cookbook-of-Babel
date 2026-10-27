package cookbookofbabel.minecraft.datagen.recipe.brewinandchewin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cookbookofbabel.minecraft.compat.brewinandchewin.KegTemperature;
import cookbookofbabel.minecraft.datagen.recipe.Resolved;
import cookbookofbabel.minecraft.datagen.recipe.Resolved.Exact;
import cookbookofbabel.minecraft.datagen.recipe.Resolved.FluidInput;
import cookbookofbabel.minecraft.datagen.recipe.Resolved.FluidOutput;
import cookbookofbabel.minecraft.datagen.recipe.Resolved.ItemOutput;
import java.util.Optional;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Brewin' and Chewin's keg: up to four items and a base fluid ferment, at one of five
 * temperatures, into one item or fluid. It reads results by name alone, without NBT.
 */
public final class Keg {
    private Keg() {}

    public static JsonObject fermenting(Resolved r) {
        var json = new JsonObject();
        json.addProperty("type", "brewinandchewin:fermenting");
        var ingredients = new JsonArray();
        r.eachItem().forEach(i -> ingredients.add(Resolved.json(i)));
        json.add("ingredients", ingredients);
        r.fluids().stream().findFirst().ifPresent(f -> json.add("basefluid", fluid(f)));
        var result = new JsonObject();
        if (r.main() instanceof ItemOutput item) {
            result.addProperty("item", ForgeRegistries.ITEMS.getKey(item.stack().getItem()).toString());
            result.addProperty("count", item.stack().getCount());
        } else {
            var fluid = ((FluidOutput) r.main()).stack();
            result.addProperty("fluid", ForgeRegistries.FLUIDS.getKey(fluid.getFluid()).toString());
            result.addProperty("count", fluid.getAmount());
        }
        json.add("result", result);
        json.addProperty("fermentingtime", r.ticks());
        json.addProperty("temperature", temperature(r.planned().step().recipe().temperatureC()));
        json.addProperty("experience", 0.1);
        return json;
    }

    private static JsonObject fluid(FluidInput input) {
        var json = new JsonObject();
        json.addProperty("fluid", ForgeRegistries.FLUIDS.getKey(((Exact) input).stack().getFluid()).toString());
        json.addProperty("count", input.millibuckets());
        return json;
    }

    /** A step giving no temperature is at room temperature, the middle level. */
    static int temperature(Optional<Double> celsius) {
        return celsius.map(KegTemperature::of).orElse(KegTemperature.NORMAL).level;
    }
}
