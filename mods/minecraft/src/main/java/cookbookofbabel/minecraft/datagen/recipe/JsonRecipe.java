package cookbookofbabel.minecraft.datagen.recipe;

import com.google.gson.JsonObject;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.jetbrains.annotations.Nullable;

/**
 * A recipe written as JSON, for a machine the mod doesn't compile against. Its serializer may not
 * exist while datagen runs, so the JSON names its type itself.
 */
record JsonRecipe(ResourceLocation id, JsonObject json) implements FinishedRecipe {
    @Override
    public JsonObject serializeRecipe() {
        return json.deepCopy();
    }

    @Override
    public void serializeRecipeData(JsonObject out) {
        json.entrySet().forEach(e -> out.add(e.getKey(), e.getValue().deepCopy()));
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    /** Unused: the JSON's {@code type} names the serializer. */
    @Override
    public RecipeSerializer<?> getType() {
        throw new UnsupportedOperationException(id + " names its type in its JSON");
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
}
