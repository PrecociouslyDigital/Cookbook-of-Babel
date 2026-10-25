package cookbookofbabel.minecraft.compat.jei;

import cookbookofbabel.minecraft.BabelTags;
import cookbookofbabel.minecraft.CookbookOfBabel;
import cookbookofbabel.minecraft.Registration;
import cookbookofbabel.minecraft.substance.Container;
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
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

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
        for (var container : Container.values()) {
            registration.registerSubtypeInterpreter(ForgeTypes.FLUID_STACK, container.fluid(), (fluid, context) -> named(fluid.getTag()));
        }
    }

    /** Hide the mod's own substances where other mods provide the ingredient (see {@link BabelTags}). */
    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        level.registryAccess().registry(Substance.REGISTRY).ifPresent(substances -> {
            var provided = substances.holders().filter(JeiCompat::providedElsewhere).toList();
            var ingredients = runtime.getIngredientManager();
            ingredients.removeIngredientsAtRuntime(
                    VanillaTypes.ITEM_STACK, provided.stream().map(s -> Substances.stackOf(s, 1)).toList());
            ingredients.removeIngredientsAtRuntime(
                    ForgeTypes.FLUID_STACK,
                    provided.stream().flatMap(s -> Substances.fluidOf(s, Container.PORTION_MB).stream()).toList());
        });
    }

    private static boolean providedElsewhere(Holder.Reference<Substance> substance) {
        var id = substance.key().location();
        return id.getNamespace().equals(CookbookOfBabel.ID)
                && !(ForgeRegistries.ITEMS.tags().getTag(BabelTags.externalItems(id.getPath())).isEmpty()
                        && ForgeRegistries.FLUIDS.tags().getTag(BabelTags.externalFluids(id.getPath())).isEmpty());
    }

    private static String named(CompoundTag tag) {
        return tag == null ? IIngredientSubtypeInterpreter.NONE : tag.getString(Substances.KEY);
    }
}
