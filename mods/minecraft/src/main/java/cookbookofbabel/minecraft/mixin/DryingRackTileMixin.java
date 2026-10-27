package cookbookofbabel.minecraft.mixin;

import cookbookofbabel.minecraft.clock.Holder;
import cookbookofbabel.minecraft.clock.HoldsFood;
import cookbookofbabel.minecraft.clock.ItemClocks;
import cookbookofbabel.minecraft.compat.hexerei.DryingRackHolder;
import net.joefoxe.hexerei.tileentity.DryingRackTile;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The rack hangs food up by hand ({@code putItems}) or by automation ({@code setItem}), neither through its parent's
 * {@code setItem}. Skipped when Hexerei isn't installed.
 */
@Pseudo
@Mixin(DryingRackTile.class)
public abstract class DryingRackTileMixin implements HoldsFood {
    @Shadow(remap = false)
    protected NonNullList<ItemStack> items;

    @Inject(method = "setItem", at = @At("RETURN"))
    private void cookbook_of_babel$set(int slot, ItemStack stack, CallbackInfo ci) {
        cookbook_of_babel$observe();
    }

    @Inject(method = "putItems", at = @At("RETURN"), remap = false)
    private void cookbook_of_babel$put(int slot, ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        cookbook_of_babel$observe();
    }

    @Unique
    private void cookbook_of_babel$observe() {
        var rack = (BlockEntity) (Object) this;
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemClocks.get().observe(items.get(i), rack));
        }
    }

    @Override
    public Holder cookbook_of_babel$holder() {
        var rack = (BlockEntity) (Object) this;
        return new DryingRackHolder(ItemClocks.get().rooms(), rack.getLevel(), rack.getBlockPos());
    }
}
