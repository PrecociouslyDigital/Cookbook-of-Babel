package cookbookofbabel.minecraft.supplant;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cookbookofbabel.minecraft.CookbookOfBabel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dependency mods craft meals in the crafting grid with their tools: croptopia's knife, frying pan
 * and mortar, pam's juicer and kin. A cook doesn't work a knife in a grid, so such a recipe is
 * rewritten as the implement it names: the knife cuts on the cutting board, the pan cooks in the
 * pot, the mortar grinds in the mill. The tool itself drops out, as it would be returned to the
 * crafter anyway, and the recipe keeps its id, so the original is simply gone.
 */
public final class ToolRecipes {
    private ToolRecipes() {}

    private enum Implement { CUTTING, POT, MILL }

    private static final Map<String, Implement> IMPLEMENTS = Map.of(
            "croptopia:knife", Implement.CUTTING,
            "pamhc2foodcore:cuttingboarditem", Implement.CUTTING,
            "croptopia:frying_pan", Implement.POT,
            "pamhc2foodcore:skilletitem", Implement.POT,
            "pamhc2foodcore:potitem", Implement.POT,
            "croptopia:mortar_and_pestle", Implement.MILL,
            "pamhc2foodcore:juiceritem", Implement.MILL);

    /** The most ingredients a rewrite takes: beyond it the recipe stays as its mod wrote it. */
    private static final int MOST_FOODS = 9;

    /** The recipe rewritten for its tool, or null where it keeps its grid: no tool, no meal left, too many foods. */
    public static Recipe<?> replacement(Recipe<?> recipe, Supplanting.Split split, RegistryAccess registries) {
        if (!(recipe instanceof CraftingRecipe) || recipe.getId().getNamespace().equals(CookbookOfBabel.ID)) {
            return null;
        }
        var result = Supplanting.result(recipe, registries);
        if (result.isEmpty() || split.supplanted().contains(result.getItem()) || split.withdrawn().contains(result.getItem())) {
            return null;
        }
        var tool = toolOf(recipe);
        if (tool == null) {
            return null;
        }
        var json = rewritten(recipe.getId(), tool, foods(recipe, tool), result);
        return json == null ? null : serializer(tool.serializer()).fromJson(recipe.getId(), json);
    }

    private record Tool(Item item, Implement implement, String serializer) {}

    private static Tool toolOf(Recipe<?> recipe) {
        for (var entry : IMPLEMENTS.entrySet()) {
            var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(entry.getKey()));
            if (item == null || recipe.getIngredients().stream().noneMatch(i -> i.test(new ItemStack(item)))) {
                continue;
            }
            var name = entry.getKey();
            return new Tool(item, entry.getValue(),
                    entry.getValue() == Implement.CUTTING ? "farmersdelight:cutting" : "farmersdelight:cooking");
        }
        return null;
    }

    /** The recipe's food inputs: everything but the tool and the grid's empty slots. */
    private static List<Ingredient> foods(Recipe<?> recipe, Tool tool) {
        var foods = new ArrayList<Ingredient>();
        var toolTaken = false;
        for (var ingredient : recipe.getIngredients()) {
            if (ingredient == Ingredient.EMPTY) {
                continue;
            }
            if (!toolTaken && ingredient.test(new ItemStack(tool.item()))) {
                toolTaken = true;
                continue;
            }
            foods.add(ingredient);
        }
        return foods;
    }

    private static JsonObject rewritten(ResourceLocation id, Tool tool, List<Ingredient> foods, ItemStack result) {
        if (foods.isEmpty() || foods.size() > MOST_FOODS) {
            return null;
        }
        var json = new JsonObject();
        switch (tool.implement()) {
            case CUTTING -> {
                if (foods.size() != 1) {
                    return null;
                }
                var toolJson = new JsonObject();
                toolJson.addProperty("item", ForgeRegistries.ITEMS.getKey(tool.item()).toString());
                json.addProperty("type", tool.serializer());
                json.add("ingredient", foods.get(0).toJson());
                json.add("tool", toolJson);
            }
            case MILL -> {
                var pestle = ForgeRegistries.RECIPE_SERIALIZERS.getValue(new ResourceLocation("hexerei:pestle_and_mortar"));
                if (pestle != null && foods.size() <= 5) {
                    json.addProperty("type", "hexerei:pestle_and_mortar");
                    json.add("ingredients", of(foods));
                    json.add("output", of(result));
                    json.addProperty("grindingTime", 200);
                } else if (foods.size() == 1) {
                    // No Hexerei, or a pestle too small: the mill grinds in Create instead.
                    json.addProperty("type", "create:milling");
                    json.add("ingredients", of(foods));
                    json.add("results", resultsOf(result));
                    json.addProperty("processingTime", 200);
                } else {
                    json.addProperty("type", tool.serializer());
                    json.add("ingredients", of(foods));
                    addResult(json, result);
                    json.addProperty("cookingtime", 200);
                    json.addProperty("experience", 0.35f);
                }
            }
            case POT -> {
                var potHolds = foods.size() <= 6;
                json.addProperty("type", potHolds ? tool.serializer() : "create:mixing");
                json.add("ingredients", of(foods));
                if (potHolds) {
                    addResult(json, result);
                    json.addProperty("cookingtime", 200);
                    json.addProperty("experience", 0.35f);
                } else {
                    // More than a pot holds: a heated basin takes the pan's place.
                    json.add("results", resultsOf(result));
                    json.addProperty("heatCondition", "heated");
                }
            }
        }
        return json;
    }

    private static void addResult(JsonObject json, ItemStack result) {
        json.add("result", of(result));
    }

    private static JsonArray of(List<Ingredient> ingredients) {
        var array = new JsonArray();
        ingredients.forEach(i -> array.add(i.toJson()));
        return array;
    }

    private static JsonArray resultsOf(ItemStack result) {
        var array = new JsonArray();
        array.add(of(result));
        return array;
    }

    /** {@code {"item", "count", "nbt"}}, the stack format of Forge's {@code CraftingHelper.getItemStack}. */
    private static JsonObject of(ItemStack stack) {
        var json = new JsonObject();
        json.addProperty("item", ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());
        json.addProperty("count", stack.getCount());
        if (stack.getTag() != null) {
            json.addProperty("nbt", stack.getTag().toString());
        }
        return json;
    }

    private static RecipeSerializer<?> serializer(String id) {
        var serializer = ForgeRegistries.RECIPE_SERIALIZERS.getValue(new ResourceLocation(id));
        if (serializer == null) {
            throw new IllegalStateException("no recipe serializer " + id + " to rewrite tool recipes into");
        }
        return serializer;
    }
}
