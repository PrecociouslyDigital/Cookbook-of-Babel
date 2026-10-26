package cookbookofbabel.minecraft.datagen.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import cookbookofbabel.minecraft.datagen.plan.Planned;
import java.util.List;
import java.util.Optional;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * A planned step with everything resolved to what its recipe names, for a machine whose recipes are
 * written as JSON: the ingredient and count of each item input, the fluid chosen for each fluid
 * input, and the stack of each output, the main one first.
 */
public record Resolved(Planned planned, List<Item> items, List<FluidInput> fluids, List<Output> outputs) {
    public record Item(Ingredient ingredient, int count) {}

    /** One fluid a fluid input can be filled with. */
    public sealed interface FluidInput {
        int millibuckets();
    }

    public record Exact(FluidStack stack) implements FluidInput {
        public int millibuckets() {
            return stack.getAmount();
        }
    }

    public record Tagged(TagKey<Fluid> tag, int millibuckets) implements FluidInput {}

    public sealed interface Output {}

    public record ItemOutput(ItemStack stack, Optional<Double> chance) implements Output {}

    public record FluidOutput(FluidStack stack) implements Output {}

    /** The item inputs one by one, for machines that list each item they take. */
    public List<Ingredient> eachItem() {
        return items.stream().flatMap(i -> java.util.Collections.nCopies(i.count(), i.ingredient()).stream()).toList();
    }

    public Output main() {
        return outputs.get(0);
    }

    /** The ticks the step takes, for a machine that times its recipes. */
    public int ticks() {
        return planned.ticks();
    }

    public static JsonElement json(Ingredient ingredient) {
        return ingredient.toJson();
    }

    /** {@code {"item", "count", "nbt"}}, the stack format of Forge's {@code CraftingHelper.getItemStack}. */
    public static JsonObject json(ItemStack stack) {
        var json = new JsonObject();
        json.addProperty("item", ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());
        json.addProperty("count", stack.getCount());
        if (stack.getTag() != null) {
            json.addProperty("nbt", stack.getTag().toString());
        }
        return json;
    }

    /** {@code {"fluid", <amountKey>, "nbt"}}; mods name the amount differently. */
    public static JsonObject json(FluidStack stack, String amountKey) {
        var json = new JsonObject();
        json.addProperty("fluid", ForgeRegistries.FLUIDS.getKey(stack.getFluid()).toString());
        json.addProperty(amountKey, stack.getAmount());
        if (stack.getTag() != null) {
            json.addProperty("nbt", stack.getTag().toString());
        }
        return json;
    }
}
