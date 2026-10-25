package cookbookofbabel.minecraft.datagen.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * A recipe from an upstream builder, with its load conditions and without an advancement. Vanilla's
 * and Farmer's Delight's builders take a plain item as the result, though their recipe formats
 * read NBT on it, so {@code patch} adds the substance a result names.
 */
record Finished(FinishedRecipe recipe, List<ICondition> conditions, Consumer<JsonObject> patch) implements FinishedRecipe {
    @Override
    public void serializeRecipeData(JsonObject json) {
        recipe.serializeRecipeData(json);
        patch.accept(json);
        if (!conditions.isEmpty()) {
            var array = new JsonArray();
            conditions.forEach(c -> array.add(CraftingHelper.serialize(c)));
            json.add("conditions", array);
        }
    }

    @Override
    public ResourceLocation getId() {
        return recipe.getId();
    }

    @Override
    public RecipeSerializer<?> getType() {
        return recipe.getType();
    }

    @Override
    @Nullable
    public JsonObject serializeAdvancement() {
        return null;
    }

    @Override
    @Nullable
    public ResourceLocation getAdvancementId() {
        return null;
    }

    /** Replaces {@code json[key]} with the full stack: item, count and NBT. */
    static Consumer<JsonObject> result(String key, ItemStack stack) {
        return json -> json.add(key, stackJson(stack));
    }

    /** Adds each stack's NBT to the matching entry of the array {@code json[key]}. */
    static Consumer<JsonObject> results(String key, List<ItemStack> stacks) {
        return json -> {
            var array = json.getAsJsonArray(key);
            for (var i = 0; i < stacks.size(); i++) {
                var tag = stacks.get(i).getTag();
                if (tag != null) {
                    array.get(i).getAsJsonObject().addProperty("nbt", tag.toString());
                }
            }
        };
    }

    static JsonObject stackJson(ItemStack stack) {
        var json = new JsonObject();
        json.addProperty("item", ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());
        if (stack.getCount() > 1) {
            json.addProperty("count", stack.getCount());
        }
        if (stack.getTag() != null) {
            json.addProperty("nbt", stack.getTag().toString());
        }
        return json;
    }
}
