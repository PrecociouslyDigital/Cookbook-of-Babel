package cookbookofbabel.minecraft.mixin;

import cookbookofbabel.minecraft.clock.ItemClocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Chests, barrels, shulker boxes and the like see food as it is put in. The hopper doesn't call up to its parent. */
@Mixin({RandomizableContainerBlockEntity.class, HopperBlockEntity.class})
public abstract class ContainerBlockEntityMixin {
    @ModifyVariable(method = "setItem", at = @At("HEAD"), argsOnly = true)
    private ItemStack cookbook_of_babel$observe(ItemStack stack) {
        return ItemClocks.get().observe(stack, (BlockEntity) (Object) this);
    }
}
