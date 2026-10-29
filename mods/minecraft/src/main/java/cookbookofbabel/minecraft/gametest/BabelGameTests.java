package cookbookofbabel.minecraft.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import cookbookofbabel.minecraft.BabelTags;
import cookbookofbabel.minecraft.CookbookOfBabel;
import cookbookofbabel.minecraft.Registration;
import cookbookofbabel.minecraft.clock.ContainerHolder;
import cookbookofbabel.minecraft.clock.Food;
import cookbookofbabel.minecraft.clock.ItemClocks;
import cookbookofbabel.minecraft.clock.Transition;
import cookbookofbabel.minecraft.clock.Transitions;
import cookbookofbabel.minecraft.dataset.NamedDish;
import cookbookofbabel.minecraft.room.Air;
import cookbookofbabel.minecraft.room.Climate;
import cookbookofbabel.minecraft.room.LevelRooms;
import cookbookofbabel.minecraft.room.RoomConstants;
import cookbookofbabel.minecraft.spice.Garden;
import cookbookofbabel.minecraft.spice.SpiceCropBlockEntity;
import cookbookofbabel.minecraft.spice.SpiceGardens;
import cookbookofbabel.minecraft.spice.SpiceSeedsItem;
import cookbookofbabel.minecraft.substance.Carrier;
import cookbookofbabel.minecraft.substance.Container;
import cookbookofbabel.minecraft.substance.FluidKind;
import cookbookofbabel.minecraft.substance.Substance;
import cookbookofbabel.minecraft.substance.Substances;
import cookbookofbabel.minecraft.supplant.Supplanting;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity;
import vectorwing.farmersdelight.common.registry.ModBlocks;
import vectorwing.farmersdelight.common.registry.ModItems;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;

/**
 * Run by {@code ./gradlew runGameTestServer}, with Create and Farmer's Delight loaded. They check
 * what only a running game can: that the generated data loads, and that recipes run in machines.
 */
@GameTestHolder(CookbookOfBabel.ID)
@PrefixGameTestTemplate(false)
public final class BabelGameTests {
    private static final String EMPTY = "empty";

    private BabelGameTests() {}

    /**
     * Every generated recipe whose conditions hold is in the recipe manager. An upstream format
     * change that makes a recipe fail to parse only logs an error; this turns it into a failure.
     */
    @GameTest(template = EMPTY)
    public static void everyGeneratedRecipeLoads(GameTestHelper helper) throws IOException {
        var server = helper.getLevel().getServer();
        var applicable = applicableRecipes(server, "recipes");
        var missing = applicable.keySet().stream().filter(id -> server.getRecipeManager().byKey(id).isEmpty()).toList();
        helper.assertTrue(!applicable.isEmpty(), "no generated recipe's conditions held");
        helper.assertTrue(missing.isEmpty(), missing.size() + " of " + applicable.size() + " generated recipes didn't load, e.g. "
                + missing.stream().limit(5).map(Object::toString).collect(Collectors.joining(", ")));
        helper.succeed();
    }

    /** Every generated transition is in the registry: one that fails to decode would be missing from it. */
    @GameTest(template = EMPTY)
    public static void everyGeneratedTransitionLoads(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var files = generatedTransitions(server);
        var loaded = server.registryAccess().registryOrThrow(Transition.REGISTRY).keySet().stream()
                .filter(id -> id.getNamespace().equals(CookbookOfBabel.ID))
                .count();
        helper.assertTrue(!files.isEmpty(), "no generated transitions");
        helper.assertTrue(loaded == files.size(), loaded + " of " + files.size() + " generated transitions loaded");
        helper.succeed();
    }

    /**
     * Every generated transition decodes, encodes back to the same transition, and names a substance or named dish
     * at both ends.
     */
    @GameTest(template = EMPTY)
    public static void generatedTransitionsRoundTripAndNameIngredients(GameTestHelper helper) throws IOException {
        var server = helper.getLevel().getServer();
        var substances = server.registryAccess().registryOrThrow(Substance.REGISTRY);
        for (var file : generatedTransitions(server).entrySet()) {
            try (var reader = file.getValue().openAsReader()) {
                var decoded = Transition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).result();
                helper.assertTrue(decoded.isPresent(), file.getKey() + " doesn't decode");
                var again = Transition.CODEC.encodeStart(JsonOps.INSTANCE, decoded.get()).result()
                        .flatMap(json -> Transition.CODEC.parse(JsonOps.INSTANCE, json).result());
                helper.assertTrue(again.equals(decoded), file.getKey() + " changes when encoded again: " + again);
                for (var end : List.of(decoded.get().input(), decoded.get().output())) {
                    var named = substances.containsKey(end)
                            || (end.getNamespace().equals(CookbookOfBabel.ID) && NamedDish.byId(end.getPath()).isPresent());
                    helper.assertTrue(named, file.getKey() + " names " + end + ", which is no substance or named dish");
                }
            }
        }
        helper.succeed();
    }

    private static Map<ResourceLocation, Resource> generatedTransitions(MinecraftServer server) {
        var registry = Transition.REGISTRY.location();
        return server.getResourceManager().listResources(registry.getNamespace() + "/" + registry.getPath(),
                id -> id.getNamespace().equals(CookbookOfBabel.ID) && id.getPath().endsWith(".json"));
    }

    /** The JSON of each generated recipe under {@code directory} whose conditions hold, by recipe ID. */
    private static Map<ResourceLocation, JsonObject> applicableRecipes(MinecraftServer server, String directory) throws IOException {
        var context = tags(server);
        var applicable = new TreeMap<ResourceLocation, JsonObject>();
        var files = server.getResourceManager().listResources(directory,
                id -> id.getNamespace().equals(CookbookOfBabel.ID) && id.getPath().endsWith(".json"));
        for (var file : files.entrySet()) {
            try (var reader = file.getValue().openAsReader()) {
                var json = JsonParser.parseReader(reader).getAsJsonObject();
                if (CraftingHelper.processConditions(json, "conditions", context)) {
                    var path = file.getKey().getPath();
                    applicable.put(new ResourceLocation(CookbookOfBabel.ID, path.substring("recipes/".length(), path.length() - ".json".length())), json);
                }
            }
        }
        return applicable;
    }

    /**
     * Every generated recipe that makes the mod's own substance item or portion names its substance.
     * A machine that drops a result's NBT would make a bare one, which is no substance at all.
     */
    @GameTest(template = EMPTY)
    public static void generatedResultsNameTheirSubstance(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var bare = helper.getLevel().getRecipeManager().getRecipes().stream()
                .filter(r -> r.getId().getNamespace().equals(CookbookOfBabel.ID))
                .filter(r -> {
                    var result = r.getResultItem(registries);
                    return (result.is(Registration.SUBSTANCE_ITEM.get()) || result.is(Registration.PORTION.get()))
                            && Substances.of(result, registries).isEmpty();
                })
                .map(r -> r.getId().toString())
                .toList();
        helper.assertTrue(bare.isEmpty(), bare.size() + " generated recipes make a bare substance, e.g. "
                + bare.stream().limit(5).collect(Collectors.joining(", ")));
        helper.succeed();
    }

    /**
     * For every flowable substance: its empty vessel fills with a portion of it, the portion names
     * it, and draining the portion gives back the same fluid and the empty vessel. A vessel that
     * doesn't hold the substance's fluid kind refuses it.
     */
    @GameTest(template = EMPTY)
    public static void portionsFillAndDrainBack(GameTestHelper helper) {
        var substances = helper.getLevel().registryAccess().registryOrThrow(Substance.REGISTRY);
        var flowable = substances.holders().filter(s -> s.value().carrier() instanceof Carrier.Flowable).toList();
        helper.assertTrue(!flowable.isEmpty(), "no flowable substances loaded");
        for (var substance : flowable) {
            var container = ((Carrier.Flowable) substance.value().carrier()).container();
            var fluid = Substances.fluidOf(substance, Container.PORTION_MB).orElseThrow();

            var empty = new ItemStack(container.empty());
            var filler = empty.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElseThrow(IllegalStateException::new);
            helper.assertTrue(filler.fill(fluid.copy(), FluidAction.EXECUTE) == Container.PORTION_MB, "couldn't fill " + container + " with " + substance.key());
            var portion = filler.getContainer();
            helper.assertTrue(Substances.of(portion, helper.getLevel().registryAccess()).equals(Optional.of(substance)), "the portion doesn't name " + substance.key());

            var drainer = portion.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElseThrow(IllegalStateException::new);
            helper.assertTrue(drainer.drain(Container.PORTION_MB - 1, FluidAction.SIMULATE).isEmpty(), "a portion drained partly");
            var drained = drainer.drain(1000, FluidAction.EXECUTE);
            helper.assertTrue(drained.isFluidStackIdentical(fluid), "draining " + substance.key() + " gave " + drained.getFluid() + " " + drained.getTag());
            helper.assertTrue(drainer.getContainer().is(container.empty()), "draining left " + drainer.getContainer());

            var kind = FluidKind.forForm(substance.value().form());
            for (var other : Container.values()) {
                if (other == container) {
                    continue;
                }
                var wrong = new ItemStack(other.empty()).getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElseThrow(IllegalStateException::new);
                helper.assertTrue(wrong.fill(fluid.copy(), FluidAction.SIMULATE) == (other.holds(kind) ? Container.PORTION_MB : 0),
                        other + (other.holds(kind) ? " refused " : " accepted ") + substance.key());
            }
        }
        helper.succeed();
    }

    /** A generated unheated mixing recipe runs in a basin under a mixer turned by a creative motor. */
    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void createMixingRuns(GameTestHelper helper) {
        var recipe = plainBasinRecipe(helper, AllRecipeTypes.MIXING.getType());
        var basin = new BlockPos(2, 1, 2);
        var mixer = basin.above(2);
        helper.setBlock(basin, AllBlocks.BASIN.getDefaultState());
        // The mixer is a cog: a cogwheel beside it, turned by the motor above, drives it.
        helper.setBlock(mixer, AllBlocks.MECHANICAL_MIXER.getDefaultState());
        helper.setBlock(mixer.east(), AllBlocks.COGWHEEL.getDefaultState());
        helper.setBlock(mixer.above().east(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.DOWN));
        // The mixer won't run below medium speed.
        ((CreativeMotorBlockEntity) helper.getBlockEntity(mixer.above().east())).generatedSpeed.setValue(64);
        var inventory = helper.getBlockEntity(basin).getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
        makes(helper, inventory, recipe.getIngredients(), recipe.getRollableResults().get(0).getStack(), recipe.getId(),
                () -> "the mixer turns at " + ((KineticBlockEntity) helper.getBlockEntity(mixer)).getSpeed() + " rpm");
    }

    /** A generated unheated compacting recipe runs in a basin under a press turned by a creative motor. */
    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void createCompactingRuns(GameTestHelper helper) {
        var recipe = plainBasinRecipe(helper, AllRecipeTypes.COMPACTING.getType());
        var basin = new BlockPos(2, 1, 2);
        var press = basin.above(2);
        helper.setBlock(basin, AllBlocks.BASIN.getDefaultState());
        // The press turns about the axis it faces along, so the motor drives it from in front.
        helper.setBlock(press, AllBlocks.MECHANICAL_PRESS.getDefaultState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        helper.setBlock(press.north(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.SOUTH));
        ((CreativeMotorBlockEntity) helper.getBlockEntity(press.north())).generatedSpeed.setValue(64);
        var inventory = helper.getBlockEntity(basin).getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
        makes(helper, inventory, recipe.getIngredients(), recipe.getRollableResults().get(0).getStack(), recipe.getId(),
                () -> "the press turns at " + ((KineticBlockEntity) helper.getBlockEntity(press)).getSpeed() + " rpm");
    }

    /**
     * A generated mixing cauldron recipe that needs no fire runs in Hexerei's cauldron. Hexerei is
     * only on the runtime classpath, so the recipe's liquid is read back from its generated JSON.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void hexereiMixingCauldronRuns(GameTestHelper helper) throws IOException {
        var level = helper.getLevel();
        var manager = level.getRecipeManager();
        var unheated = applicableRecipes(level.getServer(), "recipes/hexerei_mixing_cauldron").entrySet().stream()
                .filter(e -> !e.getValue().has("heatRequirement"))
                .filter(e -> manager.byKey(e.getKey()).map(r -> r.getIngredients().stream().allMatch(i -> i.isEmpty() || ownStack(i).isPresent())).orElse(false))
                .toList();
        // Two pages can do the same step, and the cauldron then picks either recipe.
        Function<Map.Entry<ResourceLocation, JsonObject>, String> inputs = e -> e.getValue().get("ingredients") + " in " + e.getValue().get("liquid");
        var sharingInputs = unheated.stream().collect(Collectors.groupingBy(inputs, Collectors.counting()));
        var chosen = unheated.stream()
                .filter(e -> sharingInputs.get(inputs.apply(e)) == 1)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no unheated generated mixing cauldron recipe of the mod's own items"));
        var recipe = manager.byKey(chosen.getKey()).orElseThrow();

        var pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ForgeRegistries.BLOCKS.getValue(new ResourceLocation("hexerei", "mixing_cauldron")));
        var cauldron = helper.getBlockEntity(pos);
        var liquid = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(chosen.getValue().getAsJsonObject("liquid").get("fluid").getAsString()));
        var tank = cauldron.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(IllegalStateException::new);
        helper.assertTrue(tank.fill(new FluidStack(liquid, 1000), FluidAction.EXECUTE) == 1000, "the cauldron didn't take a bucket of " + liquid);
        var inventory = cauldron.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
        makes(helper, inventory, recipe.getIngredients(), recipe.getResultItem(level.registryAccess()), recipe.getId(),
                () -> "its tank holds " + tank.getFluidInTank(0).getAmount() + " mB of " + tank.getFluidInTank(0).getFluid());
    }

    /** The first of a basin machine's generated recipes with no heat, no fluids and one result, whose inputs no other recipe shares. */
    private static <T extends BasinRecipe> T plainBasinRecipe(GameTestHelper helper, RecipeType<T> type) {
        var generated = helper.getLevel().getRecipeManager().getAllRecipesFor(type).stream()
                .filter(r -> r.getId().getNamespace().equals(CookbookOfBabel.ID))
                .toList();
        // Two pages can do the same step, and the basin then picks either recipe.
        var sharingInputs = generated.stream().collect(Collectors.groupingBy(BabelGameTests::inputs, Collectors.counting()));
        return generated.stream()
                .filter(r -> sharingInputs.get(inputs(r)) == 1)
                .filter(r -> r.getRequiredHeat() == HeatCondition.NONE && r.getFluidIngredients().isEmpty())
                .filter(r -> r.getRollableResults().size() == 1 && r.getFluidResults().isEmpty())
                .min(Comparator.comparing(r -> r.getId().toString()))
                .orElseThrow(() -> new IllegalStateException("no plain generated " + type + " recipe"));
    }

    /**
     * Puts the mod's own stack for each ingredient into a machine's inventory, and succeeds once the
     * result turns up in it. A machine may only look at its inventory when it changes, so the items
     * go in once the machine has had a second to start.
     */
    private static void makes(GameTestHelper helper, IItemHandler inventory, List<Ingredient> ingredients, ItemStack result,
            ResourceLocation recipe, Supplier<String> machineState) {
        helper.runAfterDelay(20, () -> {
            for (var ingredient : ingredients.stream().filter(i -> !i.isEmpty()).toList()) {
                var stack = matching(ingredient);
                for (var slot = 0; slot < inventory.getSlots() && !stack.isEmpty(); slot++) {
                    stack = inventory.insertItem(slot, stack, false);
                }
            }
        });
        helper.succeedWhen(() -> {
            for (var slot = 0; slot < inventory.getSlots(); slot++) {
                if (ItemStack.isSameItemSameTags(inventory.getStackInSlot(slot), result)) {
                    return;
                }
            }
            var contents = new ArrayList<String>();
            for (var slot = 0; slot < inventory.getSlots(); slot++) {
                if (!inventory.getStackInSlot(slot).isEmpty()) {
                    contents.add(inventory.getStackInSlot(slot) + " " + inventory.getStackInSlot(slot).getTag());
                }
            }
            helper.fail("hasn't made " + result + " with " + recipe + "; it holds " + contents + " and " + machineState.get());
        });
    }

    /** Farmer's Delight provides onions, so the mod's own onion is hidden; nothing provides baking soda. */
    @GameTest(template = EMPTY)
    public static void providedIngredientsAreHidden(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        helper.assertTrue(BabelTags.providedElsewhere(registries, "onion"), "onion isn't provided elsewhere");
        helper.assertFalse(BabelTags.providedElsewhere(registries, "baking_soda"), "baking soda is provided elsewhere");
        helper.succeed();
    }

    /** Only the mod makes what it supplants or withdraws, and the mod makes every item it supplants. */
    @GameTest(template = EMPTY)
    public static void onlyTheModMakesWhatItTakesOver(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var recipes = helper.getLevel().getRecipeManager().getRecipes();
        var split = Supplanting.split(recipes, registries);
        helper.assertTrue(!split.supplanted().isEmpty(), "the mod supplants nothing");
        var others = recipes.stream().filter(r -> split.takes(r, registries)).map(r -> r.getId().toString()).toList();
        helper.assertTrue(others.isEmpty(), "other mods still make what the mod took over: " + String.join(", ", others));
        var ours = recipes.stream()
                .filter(r -> r.getId().getNamespace().equals(CookbookOfBabel.ID))
                .map(r -> Supplanting.result(r, registries).getItem())
                .collect(Collectors.toSet());
        var unmade = split.supplanted().stream().filter(i -> !ours.contains(i)).map(Object::toString).toList();
        helper.assertTrue(unmade.isEmpty(), "supplanted, but the mod doesn't make " + String.join(", ", unmade));
        var made = split.withdrawn().stream()
                .filter(i -> recipes.stream().anyMatch(r -> Supplanting.result(r, registries).is(i)))
                .map(Object::toString)
                .toList();
        helper.assertTrue(made.isEmpty(), "withdrawn, but still made: " + String.join(", ", made));
        helper.succeed();
    }

    /** Bread is baked from dough and sugar drained from raw sugar, so vanilla's shortcut recipes for both are gone. */
    @GameTest(template = EMPTY)
    public static void breadAndSugarAreTheMods(GameTestHelper helper) {
        var split = Supplanting.split(helper.getLevel().getRecipeManager().getRecipes(), helper.getLevel().registryAccess());
        helper.assertTrue(split.supplanted().contains(Items.BREAD), "bread isn't supplanted");
        helper.assertTrue(split.supplanted().contains(Items.SUGAR), "sugar isn't supplanted");
        helper.succeed();
    }

    /** A generated cutting board recipe cuts with a knife. */
    @GameTest(template = EMPTY)
    public static void cuttingBoardCuts(GameTestHelper helper) {
        var level = helper.getLevel();
        var recipe = level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.CUTTING.get()).stream()
                .filter(r -> r.getId().getNamespace().equals(CookbookOfBabel.ID))
                .min(Comparator.comparing(r -> r.getId().toString()))
                .orElseThrow(() -> new IllegalStateException("no generated cutting board recipe"));
        var pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModBlocks.CUTTING_BOARD.get());
        var board = (CuttingBoardBlockEntity) helper.getBlockEntity(pos);
        board.addItem(matching(recipe.getIngredients().get(0)));
        helper.assertTrue(board.processStoredItemUsingTool(new ItemStack(ModItems.IRON_KNIFE.get()), helper.makeMockPlayer()),
                "the board didn't cut with " + recipe.getId());
        var result = recipe.getResultItem(level.registryAccess());
        helper.assertItemEntityPresent(result.getItem(), pos, 2);
        helper.succeed();
    }

    /**
     * A stone cellar with ice in its wall keeps a barrel colder than the same box of planks does,
     * on a warm day. Sky light inside the boxes settles before they are measured.
     */
    @GameTest(template = EMPTY)
    public static void stoneCellarWithIceIsColderThanPlanks(GameTestHelper helper) {
        var cellar = box(helper, new BlockPos(0, 1, 0), Blocks.STONE);
        var shed = box(helper, new BlockPos(4, 1, 0), Blocks.OAK_PLANKS);
        helper.setBlock(cellar.east(), Blocks.ICE);
        var rooms = new LevelRooms((level, pos) -> new Air(25, 60), () -> RoomConstants.DEFAULT);
        helper.runAfterDelay(20, () -> {
            var inCellar = rooms.at(helper.getLevel(), helper.absolutePos(cellar));
            var inShed = rooms.at(helper.getLevel(), helper.absolutePos(shed));
            helper.assertTrue(inCellar.tempC() < inShed.tempC(), "the cellar reads " + inCellar + ", the shed " + inShed);
            // Stone alone would hold the dark cellar just above the ground's temperature.
            helper.assertTrue(inCellar.tempC() < RoomConstants.DEFAULT.groundTempC(), "the ice didn't chill the cellar: " + inCellar);
            helper.succeed();
        });
    }

    /** A hollow 4³ box of {@code wall} with its corner at {@code corner}; returns where its barrel stands inside. */
    private static BlockPos box(GameTestHelper helper, BlockPos corner, Block wall) {
        for (var pos : BlockPos.betweenClosed(corner, corner.offset(3, 3, 3))) {
            var relative = pos.subtract(corner);
            var inside = IntStream.of(relative.getX(), relative.getY(), relative.getZ()).allMatch(c -> c == 1 || c == 2);
            helper.setBlock(pos, inside ? Blocks.AIR : wall);
        }
        var barrel = corner.offset(1, 1, 1);
        helper.setBlock(barrel, Blocks.BARREL);
        return barrel;
    }

    /**
     * Food is dated where it is put: a chest and a keg keep its own water activity, while on a drying rack the room's
     * air dries it. Food without transitions carries no date. Putting food in each runs that holder's hooks.
     */
    @GameTest(template = EMPTY)
    public static void foodIsDatedWhereItIsKept(GameTestHelper helper) {
        var level = helper.getLevel();
        var registries = level.registryAccess();
        var substances = registries.lookupOrThrow(Substance.REGISTRY);
        var food = registries.registryOrThrow(Transition.REGISTRY).stream()
                .flatMap(t -> Food.stackOf(t.input(), 1, substances).stream())
                .findFirst()
                .orElseGet(() -> Food.stackOf(CookbookOfBabel.id("bread"), 1, substances).orElseThrow());
        boolean dated = !Transitions.from(Food.of(food, registries).orElseThrow().id(), registries).isEmpty();

        var chestPos = new BlockPos(1, 1, 1);
        helper.setBlock(chestPos, Blocks.CHEST);
        var chest = (RandomizableContainerBlockEntity) helper.getBlockEntity(chestPos);
        chest.setItem(0, food.copy());
        var inChest = ItemClocks.read(chest.getItem(0));
        helper.assertTrue(inChest.isPresent() == dated, "food in a chest is dated: " + inChest.isPresent());

        var kegPos = new BlockPos(3, 1, 1);
        helper.setBlock(kegPos, ForgeRegistries.BLOCKS.getValue(new ResourceLocation("brewinandchewin", "keg")));
        var keg = helper.getBlockEntity(kegPos).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP)
                .orElseThrow(IllegalStateException::new);
        helper.assertTrue(keg.insertItem(0, food.copy(), false).isEmpty(), "the keg didn't take " + food);
        var inKeg = ItemClocks.read(keg.getStackInSlot(0));
        helper.assertTrue(inKeg.isPresent() == dated, "food in a keg is dated: " + inKeg.isPresent());

        var rackPos = new BlockPos(1, 1, 3);
        helper.setBlock(rackPos, ForgeRegistries.BLOCKS.getValue(new ResourceLocation("hexerei", "herb_drying_rack")));
        var rack = (RandomizableContainerBlockEntity) helper.getBlockEntity(rackPos);
        rack.setItem(0, food.copy());
        var onRack = ItemClocks.read(rack.getItem(0));
        helper.assertTrue(onRack.isPresent() == dated, "food on a drying rack is dated: " + onRack.isPresent());

        if (dated) {
            double own = inChest.get().environment().waterActivity();
            double air = ItemClocks.get().rooms().at(level, helper.absolutePos(rackPos)).relativeHumidity() / 100;
            helper.assertTrue(inKeg.get().environment().waterActivity() == own, "the keg changed the food's water activity");
            helper.assertTrue(Math.abs(onRack.get().environment().waterActivity() - Math.min(own, air)) < 0.01,
                    "the rack's air didn't dry the food to " + Math.min(own, air));
        }
        helper.succeed();
    }

    /**
     * The tests below date food under a warm day rather than the installed climate, so neither the biome nor the
     * weather of the test world matters.
     */
    private static final Air WARM_DAY = new Air(25, 70);
    private static final ResourceLocation BREAD = CookbookOfBabel.id("bread");
    private static final ResourceLocation BREAD_STALE = CookbookOfBabel.id("bread.stale");

    /**
     * Staling runs faster chilled than warm, its Q10 being below one: bread in an ice-cooled cellar's barrel goes
     * stale sooner than in a warm shed's.
     */
    @GameTest(template = EMPTY)
    public static void breadStalesSoonerInAnIceCooledCellarThanInAWarmShed(GameTestHelper helper) {
        var clocks = clocksOn(WARM_DAY);
        var shed = box(helper, new BlockPos(0, 1, 0), Blocks.OAK_PLANKS);
        var cellar = iceCellar(helper, new BlockPos(4, 1, 0));
        // Sky light inside the boxes settles before food is dated in them.
        helper.runAfterDelay(20, () -> {
            var level = helper.getLevel();
            long now = level.getGameTime();
            var inShed = clocks.observe(bread(level), barrel(clocks, helper, shed), level, now);
            var inCellar = clocks.observe(bread(level), barrel(clocks, helper, cellar), level, now);
            long shedStales = doneAt(inShed, BREAD_STALE, level, now)
                    .orElseThrow(() -> new IllegalStateException("bread in the shed never goes stale: " + ItemClocks.read(inShed)));
            var cellarStales = doneAt(inCellar, BREAD_STALE, level, now);
            helper.assertTrue(cellarStales.isPresent() && cellarStales.getAsLong() < shedStales,
                    "bread goes stale in the cellar at " + cellarStales + ", in the shed at " + shedStales);
            helper.succeed();
        });
    }

    /** A step done by keeping food in the fridge completes in an ice-cooled cellar, and never in a warm shed. */
    @GameTest(template = EMPTY)
    public static void chillingCompletesInACellarAndNotInAWarmShed(GameTestHelper helper) {
        var level = helper.getLevel();
        var substances = level.registryAccess().lookupOrThrow(Substance.REGISTRY);
        var chill = level.registryAccess().registryOrThrow(Transition.REGISTRY).entrySet().stream()
                .filter(e -> e.getValue().kind() == Transition.Kind.HELD)
                .filter(e -> e.getValue().sensitivity().maxTempC().filter(max -> max > 0 && max <= 8).isPresent())
                .filter(e -> Food.stackOf(e.getValue().input(), 1, substances).isPresent())
                .min(Comparator.comparing(e -> e.getKey().location()))
                .orElseThrow(() -> new IllegalStateException("no chilling transition of food with a stack"));
        var id = chill.getKey().location();
        var food = Food.stackOf(chill.getValue().input(), 1, substances).orElseThrow();
        var clocks = clocksOn(WARM_DAY);
        var shed = box(helper, new BlockPos(0, 1, 0), Blocks.OAK_PLANKS);
        var cellar = iceCellar(helper, new BlockPos(4, 1, 0));
        helper.runAfterDelay(20, () -> {
            long now = level.getGameTime();
            var inShed = clocks.observe(food.copy(), barrel(clocks, helper, shed), level, now);
            var inCellar = clocks.observe(food.copy(), barrel(clocks, helper, cellar), level, now);
            helper.assertTrue(doneAt(inShed, id, level, now).isEmpty(), id + " runs in the shed: " + ItemClocks.read(inShed));
            long chilled = doneAt(inCellar, id, level, now)
                    .orElseThrow(() -> new IllegalStateException(id + " never completes in the cellar: " + ItemClocks.read(inCellar)));

            var shedThen = clocks.observe(inShed, barrel(clocks, helper, shed), level, chilled);
            var cellarThen = clocks.observe(inCellar, barrel(clocks, helper, cellar), level, chilled);
            helper.assertTrue(is(cellarThen, chill.getValue().output(), level),
                    id + " in the cellar made " + cellarThen + " " + cellarThen.getTag());
            helper.assertFalse(is(shedThen, chill.getValue().output(), level), id + " completed in the shed");
            helper.succeed();
        });
    }

    /** Stacks of bread dated a second apart in the same barrel carry the same tag, so they stack, and still do later. */
    @GameTest(template = EMPTY)
    public static void breadDatedInTheSameBucketStacks(GameTestHelper helper) {
        var clocks = clocksOn(WARM_DAY);
        var shed = box(helper, new BlockPos(0, 1, 0), Blocks.OAK_PLANKS);
        helper.runAfterDelay(20, () -> {
            var level = helper.getLevel();
            var holder = barrel(clocks, helper, shed);
            // Each clock's buckets are whole multiples of its own size, so tick 0 is in the middle of one for all.
            var first = clocks.observe(bread(level), holder, level, 0);
            var second = clocks.observe(bread(level), holder, level, 20);
            helper.assertTrue(ItemClocks.read(first).isPresent(), "bread isn't dated");
            helper.assertTrue(ItemStack.isSameItemSameTags(first, second),
                    "bread dated a second apart doesn't stack: " + first.getTag() + " and " + second.getTag());

            long nextDay = 24000;
            var firstLater = clocks.observe(first, holder, level, nextDay);
            var secondLater = clocks.observe(second, holder, level, nextDay);
            helper.assertTrue(ItemStack.isSameItemSameTags(firstLater, secondLater),
                    "seen again, the stacks don't stack: " + firstLater.getTag() + " and " + secondLater.getTag());
            helper.succeed();
        });
    }

    /** Clocks whose rooms are measured under {@code outside}, whatever the test world's own climate. */
    private static ItemClocks clocksOn(Air outside) {
        Climate climate = (level, pos) -> outside;
        return new ItemClocks(new LevelRooms(climate, () -> RoomConstants.DEFAULT), climate);
    }

    /** A stone {@link #box} with ice in three of the walls around its barrel, chilling it to fridge temperature. */
    private static BlockPos iceCellar(GameTestHelper helper, BlockPos corner) {
        var barrel = box(helper, corner, Blocks.STONE);
        for (var wall : List.of(barrel.west(), barrel.north(), barrel.below())) {
            helper.setBlock(wall, Blocks.ICE);
        }
        return barrel;
    }

    private static ContainerHolder barrel(ItemClocks clocks, GameTestHelper helper, BlockPos pos) {
        return new ContainerHolder(clocks.rooms(), helper.getLevel(), helper.absolutePos(pos));
    }

    private static ItemStack bread(Level level) {
        return Food.stackOf(BREAD, 1, level.registryAccess().lookupOrThrow(Substance.REGISTRY)).orElseThrow();
    }

    private static Transition transition(Level level, ResourceLocation id) {
        return level.registryAccess().registryOrThrow(Transition.REGISTRY).getOptional(id).orElseThrow();
    }

    /** When transition {@code id} of a dated stack completes if it stays where it was dated; never if it is stopped. */
    private static OptionalLong doneAt(ItemStack dated, ResourceLocation id, Level level, long now) {
        var transition = transition(level, id);
        var clock = ItemClocks.read(dated).orElseThrow();
        return clock.clocks().get(id).finishesAt(now,transition.rate(clock.environment()), transition.ticks());
    }

    private static boolean is(ItemStack stack, ResourceLocation food, Level level) {
        return Food.of(stack, level.registryAccess()).map(Food::id).equals(Optional.of(food));
    }

    private static List<String> inputs(BasinRecipe recipe) {
        return recipe.getIngredients().stream().map(i -> i.toJson().toString()).sorted().toList();
    }

    /** The mod's own stack an ingredient accepts; its tags may be empty in a test world. */
    private static ItemStack matching(Ingredient ingredient) {
        return ownStack(ingredient)
                .orElseThrow(() -> new IllegalStateException("no stack of the mod's own matches " + ingredient.toJson()))
                .copy();
    }

    private static Optional<ItemStack> ownStack(Ingredient ingredient) {
        return Arrays.stream(ingredient.getItems()).filter(s -> s.hasTag() && ingredient.test(s)).findFirst();
    }

    /** Each registry's tags, collected once: every recipe's conditions look them up. */
    private static ICondition.IContext tags(MinecraftServer server) {
        var collected = new HashMap<ResourceKey<?>, Map<ResourceLocation, ?>>();
        return new ICondition.IContext() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public <T> Map<ResourceLocation, Collection<Holder<T>>> getAllTags(ResourceKey<? extends Registry<T>> key) {
                return (Map<ResourceLocation, Collection<Holder<T>>>) collected.computeIfAbsent(key, k -> server.registryAccess().registry(key)
                        .map(registry -> registry.getTags().collect(Collectors.toMap(
                                pair -> pair.getFirst().location(),
                                pair -> (Collection<Holder<T>>) (Collection) pair.getSecond().stream().toList())))
                        .orElse(Map.of()));
            }
        };
    }

    /**
     * Every planted spice grows from its seed and keeps its spice as it ages. Broken ripe, it drops
     * that spice and its seed, and nothing else.
     */
    @GameTest(template = EMPTY)
    public static void spiceCropsGrowAndDropTheirSpice(GameTestHelper helper) {
        var level = helper.getLevel();
        var crop = Registration.SPICE_CROP.get();
        var soil = new BlockPos(1, 1, 1);
        var at = helper.absolutePos(soil.above());
        var player = helper.makeMockPlayer();
        for (var spice : SpiceGardens.spices()) {
            helper.setBlock(soil, Blocks.FARMLAND);
            helper.setBlock(soil.above(), Blocks.AIR);
            var seeds = SpiceSeedsItem.of(spice);
            player.setItemInHand(InteractionHand.MAIN_HAND, seeds);
            var hit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(soil)), Direction.UP, helper.absolutePos(soil), false);
            seeds.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            helper.assertBlockPresent(crop, soil.above());
            while (!crop.isMaxAge(level.getBlockState(at))) {
                crop.growCrops(level, at, level.getBlockState(at));
            }
            var planted = (SpiceCropBlockEntity) level.getBlockEntity(at);
            helper.assertTrue(planted.spice().equals(Optional.of(spice)), "a ripe " + spice + " crop is " + planted.spice());
            var drops = Block.getDrops(level.getBlockState(at), level, at, planted);
            helper.assertTrue(drops.stream().anyMatch(d -> SpiceSeedsItem.spice(d).equals(Optional.of(spice))), spice + " dropped no seed of itself: " + drops);
            helper.assertTrue(drops.stream().anyMatch(d -> substanceOf(level, d).equals(Optional.of(spice))), spice + " dropped no " + spice + ": " + drops);
            helper.assertTrue(drops.stream().allMatch(d -> SpiceSeedsItem.spice(d).or(() -> substanceOf(level, d)).equals(Optional.of(spice))),
                    spice + " dropped something else: " + drops);
        }
        helper.succeed();
    }

    private static Optional<ResourceKey<Substance>> substanceOf(ServerLevel level, ItemStack stack) {
        return Substances.of(stack, level.registryAccess()).map(Holder.Reference::key);
    }

    /** Enough garden breaks that every spice of a garden is all but sure to turn up. */
    private static final int GARDEN_ROLLS = 400;

    /**
     * Each wild garden of HarvestCraft, broken many times over, drops the seeds of its own spices
     * and of no others. The rolls are seeded, so the test always sees the same drops.
     */
    @GameTest(template = EMPTY)
    public static void gardensDropOnlyTheirOwnSpices(GameTestHelper helper) {
        var level = helper.getLevel();
        for (var garden : Garden.values()) {
            var table = level.getServer().getLootData().getLootTable(garden.lootTable());
            helper.assertTrue(table != LootTable.EMPTY, "no loot table " + garden.lootTable());
            var params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)))
                    .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                    .withParameter(LootContextParams.BLOCK_STATE, ForgeRegistries.BLOCKS.getValue(garden.block()).defaultBlockState())
                    .create(LootContextParamSets.BLOCK);
            var found = new HashSet<ResourceKey<Substance>>();
            for (long seed = 0; seed < GARDEN_ROLLS; seed++) {
                table.getRandomItems(params, seed).forEach(stack -> SpiceSeedsItem.spice(stack).ifPresent(found::add));
            }
            var grown = Set.copyOf(SpiceGardens.growingIn(garden));
            helper.assertTrue(grown.containsAll(found), garden + " dropped seeds of " + found + ", not only " + grown);
            helper.assertTrue(found.isEmpty() == grown.isEmpty(), garden + " dropped seeds of " + found + " in " + GARDEN_ROLLS + " rolls");
        }
        helper.succeed();
    }
}
