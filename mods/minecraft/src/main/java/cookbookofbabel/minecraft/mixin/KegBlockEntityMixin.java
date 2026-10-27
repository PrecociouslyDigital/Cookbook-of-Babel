package cookbookofbabel.minecraft.mixin;

import cookbookofbabel.minecraft.clock.Holder;
import cookbookofbabel.minecraft.clock.HoldsFood;
import cookbookofbabel.minecraft.clock.OwnedItemHandler;
import cookbookofbabel.minecraft.compat.brewinandchewin.KegHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import umpaz.brewinandchewin.common.block.entity.KegBlockEntity;

/** Skipped when Brewin' and Chewin' isn't installed. */
@Pseudo
@Mixin(value = KegBlockEntity.class, remap = false)
public abstract class KegBlockEntityMixin implements HoldsFood {
    @Shadow
    @Final
    private ItemStackHandler inventory;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void cookbook_of_babel$claimInventory(BlockPos pos, BlockState state, CallbackInfo ci) {
        ((OwnedItemHandler) inventory).cookbook_of_babel$own((BlockEntity) (Object) this);
    }

    @Override
    public Holder cookbook_of_babel$holder() {
        return new KegHolder((KegBlockEntity) (Object) this);
    }
}
