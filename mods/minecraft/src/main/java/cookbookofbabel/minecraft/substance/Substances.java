package cookbookofbabel.minecraft.substance;

import cookbookofbabel.minecraft.CookbookOfBabel;
import cookbookofbabel.minecraft.Lookups;
import cookbookofbabel.minecraft.Registration;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/**
 * The one place that builds and reads substance stacks. A stack names its substance under {@link
 * #KEY}: in its own NBT for a solid, in its fluid's for a flowable one.
 */
public final class Substances {
    public static final String KEY = "substance";

    private Substances() {}

    /** A solid substance item, or containers filled with a flowable one. */
    public static ItemStack stackOf(Holder.Reference<Substance> substance, int count) {
        if (substance.value().carrier() instanceof Carrier.Flowable flowable) {
            return PortionItem.filled(fluid(substance.key(), flowable.container(), Container.PORTION_MB), count);
        }
        var stack = new ItemStack(Registration.SUBSTANCE_ITEM.get(), count);
        stack.setTag(tagOf(substance.key()));
        return stack;
    }

    /** Empty for a solid substance, which has no fluid form. */
    public static Optional<FluidStack> fluidOf(Holder.Reference<Substance> substance, int millibuckets) {
        return substance.value().carrier() instanceof Carrier.Flowable flowable
                ? Optional.of(fluid(substance.key(), flowable.container(), millibuckets))
                : Optional.empty();
    }

    /**
     * The substance in a stack. Empty unless the stack is one {@link #stackOf} could have built: a
     * substance item naming a solid, or a portion of a flowable substance.
     */
    public static Optional<Holder.Reference<Substance>> of(ItemStack stack, HolderLookup.Provider lookup) {
        if (stack.is(Registration.PORTION.get())) {
            return PortionItem.contents(stack).flatMap(fluid -> of(fluid, lookup));
        }
        if (!stack.is(Registration.SUBSTANCE_ITEM.get())) {
            return Optional.empty();
        }
        return lookUp(stack.getTag(), lookup).filter(s -> s.value().carrier() instanceof Carrier.Solid);
    }

    /** The substance in a fluid stack, if its fluid is the one its carrier says. */
    public static Optional<Holder.Reference<Substance>> of(FluidStack fluid, HolderLookup.Provider lookup) {
        return Container.holding(fluid.getFluid()).flatMap(container -> lookUp(fluid.getTag(), lookup)
                .filter(s -> s.value().carrier().equals(new Carrier.Flowable(container))));
    }

    /** The NBT that names a substance, for a stack or for matching one. */
    public static CompoundTag tagOf(ResourceKey<Substance> substance) {
        var tag = new CompoundTag();
        tag.putString(KEY, substance.location().toString());
        return tag;
    }

    public static Component name(Holder.Reference<Substance> substance) {
        var id = substance.key().location();
        return Component.translatableWithFallback(
                "substance." + id.getNamespace() + "." + id.getPath(), Labels.asName(substance.value().label()));
    }

    public static Component name(ItemStack stack) {
        return Lookups.current().flatMap(lookup -> of(stack, lookup)).map(Substances::name).orElseGet(Substances::unknown);
    }

    public static Component name(FluidStack fluid) {
        return Lookups.current().flatMap(lookup -> of(fluid, lookup)).map(Substances::name).orElseGet(Substances::unknown);
    }

    public static Component unknown() {
        return Component.translatable("item." + CookbookOfBabel.ID + ".substance.unknown");
    }

    private static FluidStack fluid(ResourceKey<Substance> substance, Container container, int millibuckets) {
        return new FluidStack(container.fluid(), millibuckets, tagOf(substance));
    }

    private static Optional<Holder.Reference<Substance>> lookUp(CompoundTag tag, HolderLookup.Provider lookup) {
        return Optional.ofNullable(tag)
                .map(t -> ResourceLocation.tryParse(t.getString(KEY)))
                .flatMap(id -> lookup.lookup(Substance.REGISTRY)
                        .flatMap(registry -> registry.get(ResourceKey.create(Substance.REGISTRY, id))));
    }
}
