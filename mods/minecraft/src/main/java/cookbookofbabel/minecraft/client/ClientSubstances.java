package cookbookofbabel.minecraft.client;

import cookbookofbabel.minecraft.substance.Substance;
import cookbookofbabel.minecraft.substance.Substances;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/** What rendering needs to know about the substance in a stack. */
final class ClientSubstances {
    private static final int UNKNOWN_COLOR = 0xFFFFFF;

    private ClientSubstances() {}

    static Optional<Holder.Reference<Substance>> of(ItemStack stack) {
        return ClientLookups.current().flatMap(lookup -> Substances.of(stack, lookup));
    }

    static int color(ItemStack stack) {
        return of(stack).map(s -> s.value().color()).orElse(UNKNOWN_COLOR);
    }

    static int color(FluidStack fluid) {
        return ClientLookups.current()
                .flatMap(lookup -> Substances.of(fluid, lookup))
                .map(s -> s.value().color())
                .orElse(UNKNOWN_COLOR);
    }
}
