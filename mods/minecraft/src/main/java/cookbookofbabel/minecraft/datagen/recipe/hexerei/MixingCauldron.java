package cookbookofbabel.minecraft.datagen.recipe.hexerei;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cookbookofbabel.minecraft.datagen.plan.Planned.Heat;
import cookbookofbabel.minecraft.datagen.recipe.Resolved;
import cookbookofbabel.minecraft.datagen.recipe.Resolved.Exact;
import cookbookofbabel.minecraft.datagen.recipe.Resolved.ItemOutput;
import java.util.Optional;

/**
 * Hexerei's mixing cauldron: up to eight items, one per slot, stirred into a liquid, make one item.
 * The recipe uses up some of the liquid and turns what's left into its {@code liquidOutput}, so
 * the code names the liquid again to leave it as it was.
 */
public final class MixingCauldron {
    private MixingCauldron() {}

    public static JsonObject recipe(Resolved r) {
        var json = new JsonObject();
        json.addProperty("type", "hexerei:mixingcauldron");
        var liquid = ((Exact) r.fluids().get(0)).stack();
        json.add("liquid", Resolved.json(liquid, "amount"));
        json.add("liquidOutput", Resolved.json(liquid, "amount"));
        json.addProperty("fluidLevelsConsumed", liquid.getAmount());
        var ingredients = new JsonArray();
        r.eachItem().forEach(i -> ingredients.add(Resolved.json(i)));
        json.add("ingredients", ingredients);
        json.add("output", Resolved.json(((ItemOutput) r.main()).stack()));
        heatRequirement(r.planned().heat()).ifPresent(h -> json.addProperty("heatRequirement", h));
        return json;
    }

    private static Optional<String> heatRequirement(Heat heat) {
        return switch (heat) {
            case NONE -> Optional.empty();
            case HEATED -> Optional.of("heated");
            case SUPERHEATED -> Optional.of("superheated");
        };
    }
}
