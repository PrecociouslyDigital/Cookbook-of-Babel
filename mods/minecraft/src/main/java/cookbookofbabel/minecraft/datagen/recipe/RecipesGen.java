package cookbookofbabel.minecraft.datagen.recipe;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.content.fluids.transfer.EmptyingRecipe;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder;
import com.simibubi.create.foundation.fluid.FluidIngredient;
import cookbookofbabel.minecraft.CookbookOfBabel;
import cookbookofbabel.minecraft.clock.Transition;
import cookbookofbabel.minecraft.dataset.Amount;
import cookbookofbabel.minecraft.dataset.Recipe;
import cookbookofbabel.minecraft.datagen.Coverage;
import cookbookofbabel.minecraft.datagen.DatagenContext;
import cookbookofbabel.minecraft.datagen.plan.Amounts;
import cookbookofbabel.minecraft.datagen.plan.Condition;
import cookbookofbabel.minecraft.datagen.plan.Mod;
import cookbookofbabel.minecraft.datagen.plan.Planned;
import cookbookofbabel.minecraft.datagen.plan.PlannedTransition;
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
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.registries.ForgeRegistries;

/** Every dataset step, through every machine its technique is bound to, and as a transition where it is one. */
public final class RecipesGen extends RecipeProvider {
    private final DatagenContext context;
    private final CompletableFuture<HolderLookup.Provider> registries;
    private final PackOutput.PathProvider transitionPaths;
    /** What {@link #buildRecipes} found to write as transitions. */
    private List<PlannedTransition> transitions = List.of();

    public RecipesGen(PackOutput output, DatagenContext context, CompletableFuture<HolderLookup.Provider> registries) {
        super(output);
        this.context = context;
        this.registries = registries;
        var registry = Transition.REGISTRY.location();
        this.transitionPaths = output.createPathProvider(PackOutput.Target.DATA_PACK, registry.getNamespace() + "/" + registry.getPath());
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        var recipes = super.run(output);
        var written = transitions.stream()
                .map(t -> DataProvider.saveStable(output, encoded(t.transition()), transitionPaths.json(CookbookOfBabel.id(t.recipe()))))
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(recipes, CompletableFuture.allOf(written));
    }

    private static JsonElement encoded(Transition transition) {
        return Transition.CODEC.encodeStart(JsonOps.INSTANCE, transition).getOrThrow(false, message -> {
            throw new IllegalStateException("can't encode " + transition + ": " + message);
        });
    }

    /**
     * Plans every step before writing any, so that a recipe whose inputs nothing in the game can
     * make is written disabled, instead of loading and asking for things nobody has. A transition
     * has no way to be disabled, so one whose input the game can't make isn't written at all.
     */
    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> sink) {
        var carriers = new Carriers(context.dataset, registries.join().lookupOrThrow(Substance.REGISTRY));
        var emitter = new RouteEmitter(context.dataset, context.mappings, carriers);
        var amounts = new Amounts(context.dataset);
        var planned = context.recipes().stream()
                .sorted(Comparator.comparing(Recipe::id))
                .map(recipe -> plan(recipe, emitter, amounts))
                .toList();
        var machines = planned.stream()
                .flatMap(p -> p.variants().stream())
                .flatMap(v -> v.attempts().stream())
                .flatMap(a -> a instanceof Ready ready && !ready.recipes().isEmpty() ? Stream.of(ready.plan()) : Stream.empty());
        var kept = planned.stream().flatMap(p -> p.transition().stream()).map(PlannedTransition::plan);
        var seeds = Reachability.seeds(context.dataset, context.recipes(), context.mappings);
        var reachability = Reachability.of(seeds, Stream.concat(machines, kept).toList(), context.dataset::descendants);
        var live = new ArrayList<PlannedTransition>();
        for (var step : planned) {
            var variants = step.variants().stream()
                    .map(v -> new Coverage.Variant(v.step().path(), v.step().addedMedium().map(m -> m.ingredient()),
                            v.attempts().stream().map(a -> a.write(reachability, emitter, sink)).toList()))
                    .toList();
            var transition = step.transition().map(t -> new Coverage.Kept(t, reachability.missing(t.plan())));
            transition.filter(Coverage.Kept::live).ifPresent(k -> live.add(k.transition()));
            context.coverage.add(new Coverage.Step(step.recipe().id(), step.recipe().technique().value(), step.note(), variants, transition));
        }
        transitions = List.copyOf(live);
        fluidItems(carriers, sink);
    }

    /** A dataset step, planned for every machine it's bound to and as a transition; {@code note} says why it's neither. */
    private record PlannedStep(Recipe recipe, Optional<String> note, List<PlannedVariant> variants, Optional<PlannedTransition> transition) {}

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

    private PlannedStep plan(Recipe recipe, RouteEmitter emitter, Amounts amounts) {
        var binding = context.techniques.bindingFor(recipe.technique());
        var transition = PlannedTransition.of(recipe, binding, amounts);
        var technique = recipe.technique().value();
        if (binding.routes().isEmpty()) {
            var note = binding.transition().isPresent()
                    ? "it should happen over time, but it isn't one ingredient becoming another after a set time"
                    : "no machine does " + technique + " or any technique above it";
            return new PlannedStep(recipe, transition.isPresent() ? Optional.empty() : Optional.of(note), List.of(), transition);
        }
        var steps = context.planner.steps(recipe);
        if (steps instanceof Planner.Unsupported unsupported) {
            return new PlannedStep(recipe, Optional.of(unsupported.reason()), List.of(), transition);
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
        return new PlannedStep(recipe, Optional.empty(), variants, transition);
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
