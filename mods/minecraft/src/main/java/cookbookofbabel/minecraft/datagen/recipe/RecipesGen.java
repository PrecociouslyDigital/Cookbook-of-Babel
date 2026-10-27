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
import cookbookofbabel.minecraft.datagen.plan.Reachability;
import cookbookofbabel.minecraft.datagen.plan.RouteKind;
import cookbookofbabel.minecraft.datagen.plan.Step;
import cookbookofbabel.minecraft.substance.Container;
import cookbookofbabel.minecraft.substance.Substance;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;
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

    /**
     * Plans every step before writing any, so that a recipe whose inputs nothing in the game can
     * make is written disabled, instead of loading and asking for things nobody has.
     */
    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> sink) {
        var carriers = new Carriers(context.dataset, registries.join().lookupOrThrow(Substance.REGISTRY));
        var emitter = new RouteEmitter(context.dataset, context.mappings, carriers);
        var planned = context.recipes().stream()
                .sorted(Comparator.comparing(Recipe::id))
                .flatMap(recipe -> plan(recipe, emitter).stream())
                .toList();
        var writing = planned.stream()
                .flatMap(p -> p.variants().stream())
                .flatMap(v -> v.attempts().stream())
                .flatMap(a -> a instanceof Ready ready && !ready.recipes().isEmpty() ? Stream.of(ready.plan()) : Stream.empty())
                .toList();
        var seeds = Reachability.seeds(context.dataset, context.recipes(), context.mappings);
        var reachability = Reachability.of(seeds, writing, context.dataset::descendants);
        for (var step : planned) {
            var variants = step.variants().stream()
                    .map(v -> new Coverage.Variant(v.step().path(), v.step().addedMedium().map(m -> m.ingredient()),
                            v.attempts().stream().map(a -> a.write(reachability, emitter, sink)).toList()))
                    .toList();
            context.coverage.add(new Coverage.Step(step.recipe().id(), step.recipe().technique().value(), Optional.empty(), variants));
        }
        fluidItems(carriers, sink);
    }

    /** A dataset step, planned for every machine it's bound to. */
    private record PlannedStep(Recipe recipe, List<PlannedVariant> variants) {}

    private record PlannedVariant(Step step, List<Attempt> attempts) {}

    /** What planning made of one variant in one machine, before anything is written. */
    private sealed interface Attempt {
        Coverage.Route write(Reachability reachability, RouteEmitter emitter, Consumer<FinishedRecipe> sink);
    }

    private record Refused(RouteKind route, String reason) implements Attempt {
        @Override
        public Coverage.Route write(Reachability reachability, RouteEmitter emitter, Consumer<FinishedRecipe> sink) {
            return new Coverage.Route(route, new Coverage.Rejected(reason));
        }
    }

    /** @param recipes none where every output candidate was filtered out, so it makes nothing */
    private record Ready(Planned planned, List<RouteEmitter.Emission> recipes) implements Attempt {
        Reachability.Plan plan() {
            return Reachability.Plan.of(planned);
        }

        @Override
        public Coverage.Route write(Reachability reachability, RouteEmitter emitter, Consumer<FinishedRecipe> sink) {
            var missing = reachability.missing(plan());
            if (recipes.isEmpty() || missing.isEmpty()) {
                recipes.forEach(r -> emitter.write(r, sink));
                return new Coverage.Route(planned.route(), new Coverage.Emitted(recipes.size(), planned.maxError(), planned.estimated(), planned.dropped()));
            }
            recipes.forEach(r -> emitter.write(r.disabled(), sink));
            return new Coverage.Route(planned.route(), new Coverage.Pruned(recipes.size(), missing));
        }
    }

    /** The step planned for each route, or empty if it has none; coverage records why either way. */
    private Optional<PlannedStep> plan(Recipe recipe, RouteEmitter emitter) {
        var binding = context.techniques.bindingFor(recipe.technique());
        var technique = recipe.technique().value();
        if (binding.routes().isEmpty()) {
            var note = binding instanceof Binding.Deferred d
                    ? d.reason()
                    : binding instanceof Binding.Elidable
                            ? "should happen over time, but skipping it would lose what it makes"
                            : "no machine does " + technique + " or any technique above it";
            context.coverage.add(new Coverage.Step(recipe.id(), technique, Optional.of(note), List.of()));
            return Optional.empty();
        }
        var steps = context.planner.steps(recipe);
        if (steps instanceof Planner.Unsupported unsupported) {
            context.coverage.add(new Coverage.Step(recipe.id(), technique, Optional.of(unsupported.reason()), List.of()));
            return Optional.empty();
        }
        var variants = new ArrayList<PlannedVariant>();
        for (var step : ((Planner.Variants) steps).steps()) {
            var attempts = new ArrayList<Attempt>();
            for (var route : binding.routes()) {
                var result = context.planner.plan(step, route);
                attempts.add(result instanceof Planned planned
                        ? new Ready(planned, emitter.emissions(planned))
                        : new Refused(route, ((Planner.Rejected) result).reason()));
            }
            variants.add(new PlannedVariant(step, attempts));
        }
        return Optional.of(new PlannedStep(recipe, variants));
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
