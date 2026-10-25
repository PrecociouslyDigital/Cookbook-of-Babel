package cookbookofbabel.minecraft.datagen.recipe;

import com.simibubi.create.content.fluids.transfer.EmptyingRecipe;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder;
import com.simibubi.create.foundation.fluid.FluidIngredient;
import cookbookofbabel.minecraft.CookbookOfBabel;
import cookbookofbabel.minecraft.dataset.Amount;
import cookbookofbabel.minecraft.dataset.Recipe;
import cookbookofbabel.minecraft.datagen.Coverage;
import cookbookofbabel.minecraft.datagen.DatagenContext;
import cookbookofbabel.minecraft.datagen.plan.Amounts;
import cookbookofbabel.minecraft.datagen.plan.Binding;
import cookbookofbabel.minecraft.datagen.plan.Condition;
import cookbookofbabel.minecraft.datagen.plan.Mod;
import cookbookofbabel.minecraft.datagen.plan.Planned;
import cookbookofbabel.minecraft.datagen.plan.Planner;
import cookbookofbabel.minecraft.substance.Container;
import cookbookofbabel.minecraft.substance.Substance;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.registries.ForgeRegistries;

/** Every dataset step, through every machine its technique is bound to. */
public final class RecipesGen extends RecipeProvider {
    private final DatagenContext context;
    private final CompletableFuture<HolderLookup.Provider> registries;

    public RecipesGen(PackOutput output, DatagenContext context, CompletableFuture<HolderLookup.Provider> registries) {
        super(output);
        this.context = context;
        this.registries = registries;
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> sink) {
        var carriers = new Carriers(context.dataset, registries.join().lookupOrThrow(Substance.REGISTRY));
        var emitter = new RouteEmitter(context.dataset, context.mappings, carriers);
        context.recipes().stream().sorted(Comparator.comparing(Recipe::id)).forEach(recipe -> step(recipe, emitter, sink));
        fluidItems(carriers, sink);
    }

    private void step(Recipe recipe, RouteEmitter emitter, Consumer<FinishedRecipe> sink) {
        var binding = context.techniques.bindingFor(recipe.technique());
        var technique = recipe.technique().value();
        if (!(binding instanceof Binding.Routes routes)) {
            var note = binding instanceof Binding.Deferred d
                    ? d.reason()
                    : binding instanceof Binding.Elidable
                            ? "should happen over time, but skipping it would lose what it makes"
                            : "no machine does " + technique + " or any technique above it";
            context.coverage.add(new Coverage.Step(recipe.id(), technique, Optional.of(note), List.of()));
            return;
        }
        if (context.planner.steps(recipe) instanceof Planner.Unsupported unsupported) {
            context.coverage.add(new Coverage.Step(recipe.id(), technique, Optional.of(unsupported.reason()), List.of()));
            return;
        }
        var variants = new ArrayList<Coverage.Variant>();
        for (var step : ((Planner.Variants) context.planner.steps(recipe)).steps()) {
            var lines = new ArrayList<Coverage.Route>();
            for (var route : routes.routes()) {
                var result = context.planner.plan(step, route);
                if (result instanceof Planned planned) {
                    var written = emitter.emit(planned, sink);
                    lines.add(new Coverage.Route(route, new Coverage.Emitted(written, planned.maxError(), planned.estimated(), planned.dropped())));
                } else {
                    lines.add(new Coverage.Route(route, new Coverage.Rejected(((Planner.Rejected) result).reason())));
                }
            }
            variants.add(new Coverage.Variant(step.path(), step.addedMedium().map(m -> m.ingredient()), lines));
        }
        context.coverage.add(new Coverage.Step(recipe.id(), technique, Optional.empty(), variants));
    }

    /**
     * Another mod's item that is really a container of a flowable ingredient, like Farmer's
     * Delight's milk bottle, empties into and fills from the mod's own fluid in Create. Skipped
     * where the item already handles fluids, or another mod maps a fluid for the ingredient and so
     * probably converts it already. The mod's own portions need none of this: their fluid handler
     * does it.
     */
    private void fluidItems(Carriers carriers, Consumer<FinishedRecipe> sink) {
        var amounts = new Amounts(context.dataset);
        for (var mapping : context.mappings) {
            mapping.entries().forEach((ingredient, entry) -> {
                var hasExternalFluid = context.mappings.stream()
                        .anyMatch(m -> m.entry(ingredient).map(e -> !e.fluids().isEmpty() || !e.fluidTags().isEmpty()).orElse(false));
                if (context.dataset.container(ingredient).isEmpty() || hasExternalFluid) {
                    return;
                }
                for (var external : entry.items()) {
                    var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(external.id()));
                    var stack = new ItemStack(item);
                    var empty = stack.getCraftingRemainingItem();
                    if (empty.isEmpty() || stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent()) {
                        continue;
                    }
                    var mb = (int) Math.round(amounts.millibuckets(ingredient, external.amount().orElse(new Amount(Container.PORTION_MB, "ml"))).value());
                    var fluid = carriers.ownFluid(ingredient, mb);
                    var conditions = new ArrayList<Condition>(List.of(new Condition.ModLoaded(Mod.CREATE)));
                    if (!mapping.mod().alwaysLoaded()) {
                        conditions.add(new Condition.ModLoaded(mapping.mod()));
                    }
                    var path = "fluid_items/" + ingredient.value() + "/" + external.id().replace(':', '/');
                    new ProcessingRecipeBuilder<>(EmptyingRecipe::new, CookbookOfBabel.id(path))
                            .require(Ingredient.of(item))
                            .output(empty)
                            .output(fluid)
                            .build(r -> sink.accept(new Finished(r, RouteEmitter.forge(conditions), json -> {})));
                    new ProcessingRecipeBuilder<>(FillingRecipe::new, CookbookOfBabel.id(path))
                            .require(Ingredient.of(empty.getItem()))
                            .require(FluidIngredient.fromFluidStack(fluid))
                            .output(stack)
                            .build(r -> sink.accept(new Finished(r, RouteEmitter.forge(conditions), json -> {})));
                }
            });
        }
    }
}
