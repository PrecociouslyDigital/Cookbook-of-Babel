package cookbookofbabel.minecraft.mixin;

import java.util.List;
import net.joefoxe.hexerei.tileentity.MixingCauldronTile;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hexerei's cauldron clears its ingredient slots outright when it crafts. The pot and the keg hand
 * back what an ingredient leaves behind ({@code ejectIngredientRemainder}); this makes the cauldron
 * pay too, so a bowl fed in comes back out. Skipped when Hexerei isn't installed.
 */
@Pseudo
@Mixin(MixingCauldronTile.class)
public abstract class MixingCauldronTileMixin {
    @Shadow(remap = false)
    public NonNullList<ItemStack> items;

    @Inject(method = "craftTheItem", at = @At("HEAD"), remap = false)
    private void cookbook_of_babel$snapshot(CallbackInfo ci) {
        cookbookofbabel.minecraft.compat.Remainders.snapshot(items);
    }

    @Inject(method = "craftTheItem", at = @At("RETURN"), remap = false)
    private void cookbook_of_babel$eject(CallbackInfo ci) {
        var machine = (net.minecraft.world.level.block.entity.BlockEntity) (Object) this;
        List<ItemStack> before = cookbookofbabel.minecraft.compat.Remainders.takeSnapshot();
        if (before != null) {
            cookbookofbabel.minecraft.compat.Remainders.eject(machine, before, items);
        }
    }
}
