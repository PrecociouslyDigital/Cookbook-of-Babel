package cookbookofbabel.minecraft.mixin;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vintage Delight's fermenting jar consumes its ingredients outright when it crafts. The pot and
 * the keg hand back what an ingredient leaves behind ({@code ejectIngredientRemainder}); this makes
 * the jar pay too. The jar isn't on the compile classpath, so this targets it by name. Skipped
 * when Vintage Delight isn't installed.
 */
@Pseudo
@Mixin(targets = "net.ribs.vintagedelight.block.entity.FermentingJarBlockEntity")
public abstract class FermentingJarBlockEntityMixin {
    @Shadow(remap = false)
    private ItemStackHandler itemHandler;

    @Inject(method = "craftItem", at = @At("HEAD"), remap = false)
    private void cookbook_of_babel$snapshot(CallbackInfo ci) {
        var slots = new ArrayList<ItemStack>(itemHandler.getSlots());
        for (var i = 0; i < itemHandler.getSlots(); i++) {
            slots.add(itemHandler.getStackInSlot(i));
        }
        cookbookofbabel.minecraft.compat.Remainders.snapshot(slots);
    }

    @Inject(method = "craftItem", at = @At("RETURN"), remap = false)
    private void cookbook_of_babel$eject(CallbackInfo ci) {
        var machine = (net.minecraft.world.level.block.entity.BlockEntity) (Object) this;
        var slots = new ArrayList<ItemStack>(itemHandler.getSlots());
        for (var i = 0; i < itemHandler.getSlots(); i++) {
            slots.add(itemHandler.getStackInSlot(i));
        }
        var before = cookbookofbabel.minecraft.compat.Remainders.takeSnapshot();
        if (before != null) {
            cookbookofbabel.minecraft.compat.Remainders.eject(machine, before, slots);
        }
    }
}
