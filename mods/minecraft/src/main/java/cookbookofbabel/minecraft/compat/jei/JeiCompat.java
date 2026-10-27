package cookbookofbabel.minecraft.compat.jei;

import cookbookofbabel.minecraft.BabelTags;
import cookbookofbabel.minecraft.CookbookOfBabel;
import cookbookofbabel.minecraft.Registration;
import cookbookofbabel.minecraft.substance.Container;
import cookbookofbabel.minecraft.substance.FluidKind;
import cookbookofbabel.minecraft.substance.PortionItem;
import cookbookofbabel.minecraft.substance.Substance;
import cookbookofbabel.minecraft.substance.Substances;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.helpers.IPlatformFluidHelper;
import mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public final class JeiCompat implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return CookbookOfBabel.id("jei");
    }

    /** Each substance is its own entry, told apart by the substance it names. */
    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(VanillaTypes.ITEM_STACK, Registration.SUBSTANCE_ITEM.get(), (stack, context) -> named(stack.getTag()));
        registration.registerSubtypeInterpreter(VanillaTypes.ITEM_STACK, Registration.PORTION.get(), (stack, context) ->
                PortionItem.contents(stack).map(fluid -> named(fluid.getTag())).orElse(IIngredientSubtypeInterpreter.NONE));
    }

    @Override
    public <T> void registerFluidSubtypes(ISubtypeRegistration registration, IPlatformFluidHelper<T> fluids) {
        for (var kind : FluidKind.values()) {
            registration.registerSubtypeInterpreter(ForgeTypes.FLUID_STACK, kind.fluid(), (fluid, context) -> named(fluid.getTag()));
        }
    }

    /** Hide the mod's own substances and dishes where other mods provide the ingredient. */
    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        var registries = level.registryAccess();
        var ingredients = runtime.getIngredientManager();
        registries.registry(Substance.REGISTRY).ifPresent(substances -> {
            var provided = substances.holders()
                    .filter(s -> s.key().location().getNamespace().equals(CookbookOfBabel.ID)
                            && BabelTags.providedElsewhere(registries, s.key().location().getPath()))
                    .toList();
            ingredients.removeIngredientsAtRuntime(
                    VanillaTypes.ITEM_STACK, provided.stream().map(s -> Substances.stackOf(s, 1)).toList());
            ingredients.removeIngredientsAtRuntime(
                    ForgeTypes.FLUID_STACK,
                    provided.stream().flatMap(s -> Substances.fluidOf(s, Container.PORTION_MB).stream()).toList());
        });
        ingredients.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, Registration.DISHES.entrySet().stream()
                .filter(d -> BabelTags.providedElsewhere(registries, d.getKey().id()))
                .map(d -> new ItemStack(d.getValue().get()))
                .toList());
    }

    private static String named(CompoundTag tag) {
        return tag == null ? IIngredientSubtypeInterpreter.NONE : tag.getString(Substances.KEY);
    }
}
