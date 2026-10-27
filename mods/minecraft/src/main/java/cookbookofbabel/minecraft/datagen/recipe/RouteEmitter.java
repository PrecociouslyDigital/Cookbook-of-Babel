package cookbookofbabel.minecraft.datagen.recipe;

import com.simibubi.create.content.fluids.transfer.EmptyingRecipe;
import com.google.gson.JsonObject;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.fan.processing.SplashingRecipe;
import com.simibubi.create.content.kinetics.millstone.MillingRecipe;
import com.simibubi.create.content.kinetics.mixer.CompactingRecipe;
import com.simibubi.create.content.kinetics.mixer.MixingRecipe;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder;
import com.simibubi.create.foundation.fluid.FluidIngredient;
import cookbookofbabel.minecraft.BabelTags;
import cookbookofbabel.minecraft.CookbookOfBabel;
import cookbookofbabel.minecraft.dataset.Amount;
import cookbookofbabel.minecraft.dataset.IngredientId;
import cookbookofbabel.minecraft.dataset.NamedDish;
import cookbookofbabel.minecraft.datagen.plan.Amounts;
import cookbookofbabel.minecraft.datagen.plan.Candidates;
import cookbookofbabel.minecraft.datagen.plan.Candidates.Target;
import cookbookofbabel.minecraft.datagen.plan.Condition;
import cookbookofbabel.minecraft.datagen.plan.Dataset;
import cookbookofbabel.minecraft.datagen.plan.Mapping;
import cookbookofbabel.minecraft.datagen.plan.Mod;
import cookbookofbabel.minecraft.datagen.plan.Planned;
import cookbookofbabel.minecraft.datagen.plan.Planned.FluidIn;
import cookbookofbabel.minecraft.datagen.plan.Planned.FluidOut;
import cookbookofbabel.minecraft.datagen.plan.Planned.ItemIn;
import cookbookofbabel.minecraft.datagen.plan.Planned.ItemOut;
import cookbookofbabel.minecraft.datagen.plan.Quantize;
import cookbookofbabel.minecraft.datagen.plan.RouteKind;
import cookbookofbabel.minecraft.datagen.recipe.brewinandchewin.Keg;
import cookbookofbabel.minecraft.datagen.recipe.farmersrespite.Kettle;
import cookbookofbabel.minecraft.datagen.recipe.pneumaticcraft.ThermoPlant;
import cookbookofbabel.minecraft.datagen.recipe.hexerei.DryingRack;
import cookbookofbabel.minecraft.datagen.recipe.hexerei.MixingCauldron;
import cookbookofbabel.minecraft.datagen.recipe.hexerei.PestleAndMortar;
import cookbookofbabel.minecraft.datagen.recipe.thermal.Thermal;
import cookbookofbabel.minecraft.datagen.recipe.theurgy.Theurgy;
import cookbookofbabel.minecraft.datagen.recipe.vintagedelight.FermentingJar;
import cookbookofbabel.minecraft.datagen.recipe.Resolved.Exact;
import cookbookofbabel.minecraft.datagen.recipe.Resolved.FluidInput;
import cookbookofbabel.minecraft.datagen.recipe.Resolved.Tagged;
import cookbookofbabel.minecraft.substance.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.conditions.FalseCondition;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.ModLoadedCondition;
import net.minecraftforge.common.crafting.conditions.NotCondition;
import net.minecraftforge.common.crafting.conditions.TagEmptyCondition;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import vectorwing.farmersdelight.data.builder.CookingPotRecipeBuilder;
import vectorwing.farmersdelight.data.builder.CuttingBoardRecipeBuilder;

/**
 * Writes planned steps as recipes through each machine's own builder, so a change to an upstream
 * recipe format breaks the build instead of the game. One planned step becomes a recipe per output
 * candidate ({@link Candidates}) and per choice of fluid for each fluid input.
 */
public final class RouteEmitter {
    private static final TagKey<Item> KNIVES = TagKey.create(Registries.ITEM, new ResourceLocation("forge", "tools/knives"));
    /** Builders insist on an advancement criterion; {@link Finished} drops the advancement. */
    private static final InventoryChangeTrigger.TriggerInstance UNUSED = InventoryChangeTrigger.TriggerInstance.hasItems(Items.AIR);
    private static final float EXPERIENCE = 0.1f;
    /** Beyond this many fluid variants of one step, the rest are left out. */
    private static final int MAX_FLUID_VARIANTS = 4;

    private final Dataset dataset;
    private final Amounts amounts;
    private final List<Mapping> mappings;
    private final Carriers carriers;

    public RouteEmitter(Dataset dataset, List<Mapping> mappings, Carriers carriers) {
        this.dataset = dataset;
        this.amounts = new Amounts(dataset);
        this.mappings = mappings;
        this.carriers = carriers;
    }

    /** One of the fluids a fluid input can be filled with, and when it can. */
    private record FluidChoice(FluidInput input, List<Condition> conditions) {}

    /** One recipe a planned step becomes, not yet written. */
    public record Emission(Planned planned, String path, Target target, List<FluidInput> fluids, List<Condition> conditions) {
        /** The same recipe, written so that it never loads. */
        public Emission disabled() {
            var all = new ArrayList<>(conditions);
            all.add(new Condition.False());
            return new Emission(planned, path, target, fluids, List.copyOf(all));
        }
    }

    /** The recipes a planned step becomes: one per output candidate and choice of fluids. */
    public List<Emission> emissions(Planned planned) {
        var main = planned.outputs().get(0);
        var external = BabelTags.externalItems(main.ingredient().value()).location().toString();
        var candidates = (main instanceof ItemOut
                        ? Candidates.of(main.ingredient(), mappings, Candidates::items, external)
                        : Candidates.of(main.ingredient(), mappings, Candidates::fluids, external))
                .stream()
                .filter(c -> planned.route().nbt.onResults() || withoutNbt(c.target(), main))
                .toList();
        var fluidVariants = fluidVariants(planned);
        var emissions = new ArrayList<Emission>();
        for (var candidate : candidates) {
            for (var v = 0; v < fluidVariants.size(); v++) {
                var path = planned.step().path()
                        + (candidates.size() > 1 ? "/" + candidateName(candidate.target()) : "")
                        + (fluidVariants.size() > 1 ? "/f" + (v + 1) : "");
                var conditions = new ArrayList<Condition>();
                if (!planned.route().mod.alwaysLoaded()) {
                    conditions.add(new Condition.ModLoaded(planned.route().mod));
                }
                conditions.addAll(candidate.conditions());
                var fluids = fluidVariants.get(v);
                fluids.forEach(f -> conditions.addAll(f.conditions()));
                emissions.add(new Emission(planned, path, candidate.target(), fluids.stream().map(FluidChoice::input).toList(),
                        List.copyOf(conditions)));
            }
        }
        return emissions;
    }

    /** Whether a machine that ignores NBT makes the target: another mod's thing, or a named dish's item. */
    private static boolean withoutNbt(Target target, Planned.Out main) {
        return target instanceof Target.External || (main instanceof ItemOut && NamedDish.byId(main.ingredient().value()).isPresent());
    }

    public void write(Emission emission, Consumer<FinishedRecipe> sink) {
        var p = emission.planned();
        var path = emission.path();
        var mainTarget = emission.target();
        var fluids = emission.fluids();
        var conditions = forge(emission.conditions());
        var items = new ArrayList<Ingredient>();
        var counts = new ArrayList<Integer>();
        for (var in : p.inputs()) {
            if (in instanceof ItemIn item) {
                items.add(carriers.accepting(item.ingredient()));
                counts.add(item.count());
            }
        }
        var outputs = new ArrayList<Object>();
        for (var i = 0; i < p.outputs().size(); i++) {
            outputs.add(stack(p.outputs().get(i), i == 0 ? Optional.of(mainTarget) : Optional.empty()));
        }
        var route = p.route();
        // An expression, so that a route without a case here doesn't compile.
        Runnable emit = switch (route) {
            case CREATE_MIXING, CREATE_HEATED_MIXING -> () -> create(MixingRecipe::new, route == RouteKind.CREATE_HEATED_MIXING ? "heated/" + path : path, p, items, counts, fluids, outputs, conditions, sink);
            case CREATE_COMPACTING -> () -> create(CompactingRecipe::new, path, p, items, counts, fluids, outputs, conditions, sink);
            case CREATE_MILLING -> () -> create(MillingRecipe::new, path, p, items, counts, fluids, outputs, conditions, sink);
            case CREATE_CUTTING -> () -> create(CuttingRecipe::new, path, p, items, counts, fluids, outputs, conditions, sink);
            case CREATE_ITEM_DRAIN -> () -> create(EmptyingRecipe::new, path, p, items, counts, fluids, outputs, conditions, sink);
            case CREATE_DEPLOYING -> () -> create(DeployerApplicationRecipe::new, path, p, items, counts, fluids, outputs, conditions, sink);
            case CREATE_SPLASHING -> () -> create(SplashingRecipe::new, path, p, items, counts, fluids, outputs, conditions, sink);
            // Garnished's fan recipes are Create processing recipes of a type of their own.
            case GARNISHED_FREEZING -> () -> create(SplashingRecipe::new, path, p, items, counts, fluids, outputs, List.of(),
                    retyped("garnished:freezing", id(route, path), conditions, sink));
            case BNC_KEG -> () -> json(Keg::fermenting, p, path, items, counts, fluids, outputs, conditions, sink);
            case FR_KETTLE -> () -> json(Kettle::brewing, p, path, items, counts, fluids, outputs, conditions, sink);
            case VD_FERMENTING_JAR -> () -> json(FermentingJar::fermenting, p, path, items, counts, fluids, outputs, conditions, sink);
            case PNC_THERMO_PLANT -> () -> json(ThermoPlant::recipe, p, path, items, counts, fluids, outputs, conditions, sink);
            case THERMAL_PRESS -> () -> json(Thermal::press, p, path, items, counts, fluids, outputs, conditions, sink);
            case THERMAL_CENTRIFUGE -> () -> json(Thermal::centrifuge, p, path, items, counts, fluids, outputs, conditions, sink);
            case THERMAL_SMELTER -> () -> json(Thermal::smelter, p, path, items, counts, fluids, outputs, conditions, sink);
            case THERMAL_BOTTLER -> () -> json(Thermal::bottler, p, path, items, counts, fluids, outputs, conditions, sink);
            case THERMAL_CHILLER -> () -> json(Thermal::chiller, p, path, items, counts, fluids, outputs, conditions, sink);
            case THEURGY_CALCINATION -> () -> json(Theurgy::calcination, p, path, items, counts, fluids, outputs, conditions, sink);
            case THEURGY_FERMENTATION -> () -> json(Theurgy::fermentation, p, path, items, counts, fluids, outputs, conditions, sink);
            case THEURGY_LIQUEFACTION -> () -> json(Theurgy::liquefaction, p, path, items, counts, fluids, outputs, conditions, sink);
            case HEXEREI_DRYING_RACK -> () -> json(DryingRack::recipe, p, path, items, counts, fluids, outputs, conditions, sink);
            case HEXEREI_PESTLE_AND_MORTAR -> () -> json(PestleAndMortar::recipe, p, path, items, counts, fluids, outputs, conditions, sink);
            case HEXEREI_MIXING_CAULDRON -> () -> json(MixingCauldron::recipe, p, path, items, counts, fluids, outputs, conditions, sink);
            case SHAPELESS_CRAFTING -> () -> {
                var result = (ItemStack) first(outputs);
                var builder = ShapelessRecipeBuilder.shapeless(RecipeCategory.FOOD, result.getItem(), result.getCount());
                for (var i = 0; i < items.size(); i++) {
                    builder.requires(items.get(i), counts.get(i));
                }
                builder.unlockedBy("unused", UNUSED).save(patched(sink, conditions, Finished.result("result", result)), id(route, path));
            };
            case SMELTING, SMOKING, BLASTING, CAMPFIRE -> () -> {
                var result = (ItemStack) first(outputs);
                var builder = switch (route) {
                    case SMELTING -> SimpleCookingRecipeBuilder.smelting(items.get(0), RecipeCategory.FOOD, result.getItem(), EXPERIENCE, p.ticks());
                    case SMOKING -> SimpleCookingRecipeBuilder.smoking(items.get(0), RecipeCategory.FOOD, result.getItem(), EXPERIENCE, p.ticks());
                    case BLASTING -> SimpleCookingRecipeBuilder.blasting(items.get(0), RecipeCategory.MISC, result.getItem(), EXPERIENCE, p.ticks());
                    default -> SimpleCookingRecipeBuilder.campfireCooking(items.get(0), RecipeCategory.FOOD, result.getItem(), EXPERIENCE, p.ticks());
                };
                builder.unlockedBy("unused", UNUSED).save(patched(sink, conditions, Finished.result("result", result)), id(route, path));
            };
            case FD_COOKING_POT -> () -> {
                var result = (ItemStack) first(outputs);
                var builder = CookingPotRecipeBuilder.cookingPotRecipe(result.getItem(), result.getCount(), p.ticks(), EXPERIENCE);
                for (var i = 0; i < items.size(); i++) {
                    builder.addIngredient(items.get(i), counts.get(i));
                }
                builder.unlockedBy("unused", UNUSED).save(patched(sink, conditions, Finished.result("result", result)), id(route, path));
            };
            case FD_CUTTING_BOARD -> () -> {
                var stacks = outputs.stream().map(o -> (ItemStack) o).toList();
                var chances = p.outputs().stream().map(o -> ((ItemOut) o).chance().orElse(1.0).floatValue()).toList();
                var builder = CuttingBoardRecipeBuilder.cuttingRecipe(items.get(0), Ingredient.of(KNIVES), stacks.get(0).getItem(), stacks.get(0).getCount(), chances.get(0));
                for (var i = 1; i < stacks.size(); i++) {
                    builder.addResultWithChance(stacks.get(i).getItem(), chances.get(i), stacks.get(i).getCount());
                }
                builder.save(patched(sink, conditions, Finished.results("result", stacks)), id(route, path));
            };
        };
        emit.run();
    }

    private <T extends ProcessingRecipe<?>> void create(
            ProcessingRecipeBuilder.ProcessingRecipeFactory<T> factory,
            String path,
            Planned p,
            List<Ingredient> items,
            List<Integer> counts,
            List<FluidInput> fluids,
            List<Object> outputs,
            List<ICondition> conditions,
            Consumer<FinishedRecipe> sink) {
        var builder = new ProcessingRecipeBuilder<>(factory, CookbookOfBabel.id(path));
        for (var i = 0; i < items.size(); i++) {
            for (var n = 0; n < counts.get(i); n++) {
                builder.require(items.get(i));
            }
        }
        fluids.forEach(f -> builder.require(f instanceof Exact exact
                ? FluidIngredient.fromFluidStack(exact.stack())
                : FluidIngredient.fromTag(((Tagged) f).tag(), f.millibuckets())));
        for (var i = 0; i < outputs.size(); i++) {
            if (outputs.get(i) instanceof ItemStack stack) {
                var chance = ((ItemOut) p.outputs().get(i)).chance().orElse(1.0).floatValue();
                builder.output(new ProcessingOutput(stack, chance));
            } else {
                builder.output((FluidStack) outputs.get(i));
            }
        }
        if (p.ticks() > 0) {
            builder.duration(p.ticks());
        }
        builder.requiresHeat(switch (p.heat()) {
            case NONE -> HeatCondition.NONE;
            case HEATED -> HeatCondition.HEATED;
            case SUPERHEATED -> HeatCondition.SUPERHEATED;
        });
        builder.build(patched(sink, conditions, json -> {}));
    }

    private void json(
            Function<Resolved, JsonObject> recipe,
            Planned p,
            String path,
            List<Ingredient> items,
            List<Integer> counts,
            List<FluidInput> fluids,
            List<Object> outputs,
            List<ICondition> conditions,
            Consumer<FinishedRecipe> sink) {
        var resolvedItems = new ArrayList<Resolved.Item>();
        for (var i = 0; i < items.size(); i++) {
            resolvedItems.add(new Resolved.Item(items.get(i), counts.get(i)));
        }
        var resolvedOutputs = new ArrayList<Resolved.Output>();
        for (var i = 0; i < outputs.size(); i++) {
            resolvedOutputs.add(outputs.get(i) instanceof ItemStack stack
                    ? new Resolved.ItemOutput(stack, ((ItemOut) p.outputs().get(i)).chance())
                    : new Resolved.FluidOutput((FluidStack) outputs.get(i)));
        }
        var json = recipe.apply(new Resolved(p, resolvedItems, fluids, resolvedOutputs));
        sink.accept(new Finished(new JsonRecipe(id(p.route(), path), json), conditions, j -> {}));
    }

    /**
     * The stack an output makes. The main output follows its candidate; any other output is the
     * mod's own, or the vanilla item or fluid it maps to, which is always there.
     */
    private Object stack(Planned.Out out, Optional<Target> target) {
        if (out instanceof FluidOut fluid) {
            var chosen = target.or(() -> vanilla(out.ingredient(), Candidates::fluids));
            return chosen.filter(t -> t instanceof Target.External)
                    .map(t -> new FluidStack(registered(ForgeRegistries.FLUIDS, ((Target.External) t).id()), fluid.millibuckets()))
                    .orElseGet(() -> carriers.ownFluid(fluid.ingredient(), fluid.millibuckets()));
        }
        var item = (ItemOut) out;
        var chosen = target.or(() -> vanilla(out.ingredient(), Candidates::items));
        return chosen.filter(t -> t instanceof Target.External)
                .map(t -> {
                    var e = (Target.External) t;
                    return new ItemStack(registered(ForgeRegistries.ITEMS, e.id()), externalCount(item.ingredient(), item.count(), e.amount()));
                })
                .orElseGet(() -> carriers.own(item.ingredient(), item.count()));
    }

    private Optional<Target> vanilla(IngredientId ingredient, java.util.function.BiFunction<Mod, Mapping.Entry, List<Target.External>> provided) {
        return mappings.stream()
                .filter(m -> m.mod() == Mod.MINECRAFT)
                .flatMap(m -> m.entry(ingredient).stream().flatMap(e -> provided.apply(m.mod(), e).stream()))
                .<Target>map(t -> t)
                .findFirst();
    }

    private static <T> T registered(net.minecraftforge.registries.IForgeRegistry<T> registry, String id) {
        var key = new ResourceLocation(id);
        if (!registry.containsKey(key)) {
            throw new IllegalStateException("no " + registry.getRegistryName() + " " + id);
        }
        return registry.getValue(key);
    }

    /** How many of another mod's item make as much as {@code count} of the mod's own. */
    private int externalCount(IngredientId ingredient, int count, Optional<Amount> each) {
        if (each.isEmpty()) {
            return count;
        }
        var own = dataset.container(ingredient).isPresent()
                ? amounts.grams(ingredient, new Amount(Container.PORTION_MB, "ml")).value()
                : dataset.gramsPerItem(ingredient);
        var theirs = amounts.grams(ingredient, each.get()).value();
        return (int) Math.max(1, Math.min(Quantize.MAX_STACK, Math.round(count * own / theirs)));
    }

    /**
     * Create's fluid ingredients match one fluid each, so a fluid input's alternatives (the mod's own
     * fluid, and other mods' fluids for the ingredient) each make a variant of the recipe.
     */
    private List<List<FluidChoice>> fluidVariants(Planned planned) {
        List<List<FluidChoice>> variants = List.of(List.of());
        for (var in : planned.inputs()) {
            if (!(in instanceof FluidIn fluid)) {
                continue;
            }
            var choices = fluidChoices(fluid, planned.route());
            var next = new ArrayList<List<FluidChoice>>();
            for (var prefix : variants) {
                for (var choice : choices) {
                    if (next.size() < MAX_FLUID_VARIANTS) {
                        var joined = new ArrayList<>(prefix);
                        joined.add(choice);
                        next.add(joined);
                    }
                }
            }
            variants = next;
        }
        return variants;
    }

    /**
     * The mod's own fluid, where the machine matches NBT, and other mods' fluids for the
     * ingredient; their tags too, where it matches NBT, since a tag says nothing about it.
     */
    private List<FluidChoice> fluidChoices(FluidIn in, RouteKind route) {
        var choices = new ArrayList<FluidChoice>();
        var nbt = route.nbt.onFluidInputs();
        if (nbt) {
            choices.add(new FluidChoice(new Exact(carriers.ownFluid(in.ingredient(), in.millibuckets())), List.of()));
        }
        for (var mapping : mappings) {
            var gate = mapping.mod().alwaysLoaded() ? List.<Condition>of() : List.<Condition>of(new Condition.ModLoaded(mapping.mod()));
            mapping.entry(in.ingredient()).ifPresent(entry -> {
                entry.fluids().forEach(f -> choices.add(new FluidChoice(
                        new Exact(new FluidStack(registered(ForgeRegistries.FLUIDS, f), in.millibuckets())), gate)));
                if (nbt) {
                    Mapping.withMirrors(entry.fluidTags()).forEach(t -> choices.add(new FluidChoice(
                            new Tagged(TagKey.create(Registries.FLUID, new ResourceLocation(t)), in.millibuckets()), gate)));
                }
            });
        }
        return choices;
    }

    private static String candidateName(Target target) {
        return target instanceof Target.External e ? e.mod().id : "own";
    }

    private static ResourceLocation id(RouteKind route, String path) {
        return CookbookOfBabel.id(route.name().toLowerCase(Locale.ROOT) + "/" + path);
    }

    private static Object first(List<Object> outputs) {
        return outputs.get(0);
    }

    private static Consumer<FinishedRecipe> patched(Consumer<FinishedRecipe> sink, List<ICondition> conditions, Consumer<JsonObject> patch) {
        return recipe -> sink.accept(new Finished(recipe, conditions, patch));
    }

    /** Takes a recipe in another machine's format and writes it as one of {@code type}, under {@code id}. */
    private static Consumer<FinishedRecipe> retyped(String type, ResourceLocation id, List<ICondition> conditions, Consumer<FinishedRecipe> sink) {
        return recipe -> {
            var json = recipe.serializeRecipe();
            json.addProperty("type", type);
            sink.accept(new Finished(new JsonRecipe(id, json), conditions, j -> {}));
        };
    }

    static List<ICondition> forge(List<Condition> conditions) {
        return conditions.stream().map(RouteEmitter::forge).toList();
    }

    private static ICondition forge(Condition condition) {
        return condition.match(new Condition.Cases<>() {
            @Override
            public ICondition modLoaded(Mod mod) {
                return new ModLoadedCondition(mod.id);
            }

            @Override
            public ICondition not(Condition negated) {
                return new NotCondition(forge(negated));
            }

            @Override
            public ICondition tagEmpty(String tag) {
                return new TagEmptyCondition(tag);
            }

            @Override
            public ICondition never() {
                return FalseCondition.INSTANCE;
            }
        });
    }
}
