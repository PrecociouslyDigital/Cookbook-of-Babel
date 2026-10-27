package cookbookofbabel.minecraft.mixin;

import cookbookofbabel.minecraft.clock.ItemClocks;
import cookbookofbabel.minecraft.clock.OwnedItemHandler;
import java.util.Optional;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Food put into the handler of a block entity that claimed it is seen there, before the owner hears of the change. */
@Mixin(value = ItemStackHandler.class, remap = false)
public abstract class ItemStackHandlerMixin implements OwnedItemHandler {
    @Shadow
    protected NonNullList<ItemStack> stacks;

    @Unique
    private BlockEntity cookbook_of_babel$owner;

    @Override
    public void cookbook_of_babel$own(BlockEntity owner) {
        cookbook_of_babel$owner = owner;
    }

    @Override
    public Optional<BlockEntity> cookbook_of_babel$owner() {
        return Optional.ofNullable(cookbook_of_babel$owner);
    }

    @Inject(
            method = "setStackInSlot",
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/items/ItemStackHandler;onContentsChanged(I)V"))
    private void cookbook_of_babel$set(int slot, ItemStack stack, CallbackInfo ci) {
        cookbook_of_babel$observe(slot);
    }

    @Inject(
            method = "insertItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/items/ItemStackHandler;onContentsChanged(I)V"))
    private void cookbook_of_babel$insert(
            int slot, ItemStack stack, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
        cookbook_of_babel$observe(slot);
    }

    @Unique
    private void cookbook_of_babel$observe(int slot) {
        if (cookbook_of_babel$owner != null) {
            stacks.set(slot, ItemClocks.get().observe(stacks.get(slot), cookbook_of_babel$owner));
        }
    }
}
