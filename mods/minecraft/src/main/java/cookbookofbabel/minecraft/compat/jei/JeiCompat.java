package cookbookofbabel.minecraft.compat.jei;

import com.google.gson.JsonParser;
import cookbookofbabel.minecraft.BabelTags;
import cookbookofbabel.minecraft.CookbookOfBabel;
import cookbookofbabel.minecraft.Registration;
import cookbookofbabel.minecraft.clock.Food;
import cookbookofbabel.minecraft.clock.Transition;
import cookbookofbabel.minecraft.dataset.NamedDish;
import cookbookofbabel.minecraft.compat.jei.KeptFoodCategory;
import cookbookofbabel.minecraft.spice.SpiceSeedsItem;
import cookbookofbabel.minecraft.substance.Container;
import cookbookofbabel.minecraft.substance.FluidKind;
import cookbookofbabel.minecraft.substance.PortionItem;
import cookbookofbabel.minecraft.substance.Substance;
import cookbookofbabel.minecraft.substance.Substances;
import cookbookofbabel.minecraft.supplant.Supplanting;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.helpers.IPlatformFluidHelper;
import mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public final class JeiCompat implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return CookbookOfBabel.id("jei");
    }

    /** Every kept-food step the datapack loads, whose both ends are things the game can stack. */
    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        var registries = level.registryAccess();
        var substances = registries.lookupOrThrow(Substance.REGISTRY);
        var kept = new ArrayList<Transition>();
        registries.registryOrThrow(Transition.REGISTRY).entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(e -> {
                    if (Food.stackOf(e.getValue().input(), 1, substances).isPresent()
                            && Food.stackOf(e.getValue().output(), 1, substances).isPresent()) {
                        kept.add(e.getValue());
                    }
                });
        registration.addRecipes(KeptFoodCategory.TYPE, kept);
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new KeptFoodCategory(registration.getJeiHelpers()));
    }

    /** Each substance, and each spice's seeds, is its own entry, told apart by the substance it names. */
    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(VanillaTypes.ITEM_STACK, Registration.SUBSTANCE_ITEM.get(), (stack, context) -> named(stack.getTag()));
        registration.registerSubtypeInterpreter(VanillaTypes.ITEM_STACK, Registration.PORTION.get(), (stack, context) ->
                PortionItem.contents(stack).map(fluid -> named(fluid.getTag())).orElse(IIngredientSubtypeInterpreter.NONE));
        registration.registerSubtypeInterpreter(VanillaTypes.ITEM_STACK, Registration.SPICE_SEEDS.get(), (stack, context) ->
                SpiceSeedsItem.spice(stack).map(spice -> spice.location().toString()).orElse(IIngredientSubtypeInterpreter.NONE));
    }

    @Override
    public <T> void registerFluidSubtypes(ISubtypeRegistration registration, IPlatformFluidHelper<T> fluids) {
        for (var kind : FluidKind.values()) {
            registration.registerSubtypeInterpreter(ForgeTypes.FLUID_STACK, kind.fluid(), (fluid, context) -> named(fluid.getTag()));
        }
    }

    /**
     * Hide the mod's own substances and dishes where other mods provide the ingredient, and the
     * other mods' items it withdraws.
     */
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
        var split = Supplanting.split(level.getRecipeManager().getRecipes(), registries);
        ingredients.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, split.withdrawn().stream().map(ItemStack::new).toList());
        hideUnreachable(ingredients, registries);
    }

    /**
     * The ingredients datagen found nothing in the game can make: their substance, fluid and dish
     * would sit in JEI looking craftable when they aren't. Datagen's reachability is the ceiling of
     * the game's, since conditions only ever disable recipes, so this never hides a keeper.
     */
    private static void hideUnreachable(mezz.jei.api.runtime.IIngredientManager ingredients, HolderLookup.Provider registries) {
        var stacks = new ArrayList<ItemStack>();
        var fluids = new ArrayList<net.minecraftforge.fluids.FluidStack>();
        for (var id : Unreachable.INGREDIENTS) {
            var key = ResourceKey.create(Substance.REGISTRY, CookbookOfBabel.id(id));
            registries.lookup(Substance.REGISTRY).flatMap(r -> r.get(key)).ifPresent(s -> {
                stacks.add(Substances.stackOf(s, 1));
                Substances.fluidOf(s, Container.PORTION_MB).ifPresent(fluids::add);
            });
            NamedDish.byId(id).ifPresent(dish -> stacks.add(new ItemStack(Registration.DISHES.get(dish).get())));
        }
        ingredients.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, stacks);
        ingredients.removeIngredientsAtRuntime(ForgeTypes.FLUID_STACK, fluids);
    }

    /** Generated: {@code cookbook_of_babel/unreachable.json}, read off the classpath like Supplanting's. */
    private static final class Unreachable {
        static final List<String> INGREDIENTS = read();

        private Unreachable() {}

        private static List<String> read() {
            try (var in = JeiCompat.class.getResourceAsStream("/cookbook_of_babel/unreachable.json")) {
                if (in == null) {
                    return List.of();
                }
                var json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
                var ids = new ArrayList<String>(json.size());
                json.forEach(e -> ids.add(e.getAsString()));
                return List.copyOf(ids);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static String named(CompoundTag tag) {
        return tag == null ? IIngredientSubtypeInterpreter.NONE : tag.getString(Substances.KEY);
    }
}
