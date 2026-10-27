package cookbookofbabel.minecraft.mixin;

import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Create's basin consumes its ingredient items outright when a recipe runs ({@code apply}). The
 * pot and the keg hand back what an ingredient leaves behind ({@code ejectIngredientRemainder});
 * this makes the basin pay too, so a bowl fed in comes back out beside it. Skipped when Create
 * isn't installed.
 */
@Pseudo
@Mixin(value = BasinRecipe.class, remap = false)
public abstract class BasinRecipeMixin {
    @Unique
    private static final ThreadLocal<List<ItemStack>> cookbook_of_babel$before = new ThreadLocal<>();

    @Inject(
            method = "apply(Lcom/simibubi/create/content/processing/basin/BasinBlockEntity;Lnet/minecraft/world/item/crafting/Recipe;Z)Z",
            at = @At("HEAD"))
    private static void cookbook_of_babel$snapshot(
            BasinBlockEntity basin, Recipe<?> recipe, boolean test, CallbackInfoReturnable<Boolean> cir) {
        if (!test) {
            var slots = new ArrayList<ItemStack>();
            var input = basin.getInputInventory();
            for (var i = 0; i < input.getSlots(); i++) {
                slots.add(input.getStackInSlot(i));
            }
            cookbook_of_babel$before.set(slots);
        }
    }

    @Inject(
            method = "apply(Lcom/simibubi/create/content/processing/basin/BasinBlockEntity;Lnet/minecraft/world/item/crafting/Recipe;Z)Z",
            at = @At("RETURN"))
    private static void cookbook_of_babel$eject(
            BasinBlockEntity basin, Recipe<?> recipe, boolean test, CallbackInfoReturnable<Boolean> cir) {
        var before = cookbook_of_babel$before.get();
        cookbook_of_babel$before.remove();
        if (test || before == null) {
            return;
        }
        var slots = new ArrayList<ItemStack>();
        var input = basin.getInputInventory();
        for (var i = 0; i < input.getSlots(); i++) {
            slots.add(input.getStackInSlot(i));
        }
        cookbookofbabel.minecraft.compat.Remainders.eject(basin, before, slots);
    }
}
