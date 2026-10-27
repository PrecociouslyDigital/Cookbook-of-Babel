package cookbookofbabel.minecraft.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import cookbookofbabel.minecraft.BabelTags;
import cookbookofbabel.minecraft.CookbookOfBabel;
import cookbookofbabel.minecraft.Registration;
import cookbookofbabel.minecraft.clock.Food;
import cookbookofbabel.minecraft.clock.ItemClocks;
import cookbookofbabel.minecraft.clock.Transition;
import cookbookofbabel.minecraft.clock.Transitions;
import cookbookofbabel.minecraft.room.Air;
import cookbookofbabel.minecraft.room.LevelRooms;
import cookbookofbabel.minecraft.room.RoomConstants;
import cookbookofbabel.minecraft.substance.Carrier;
import cookbookofbabel.minecraft.substance.Container;
import cookbookofbabel.minecraft.substance.Substance;
import cookbookofbabel.minecraft.substance.Substances;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
     * For every flowable substance: its empty container fills with a portion of it, the portion
     * names it, and draining the portion gives back the same fluid and the empty container. The
     * other container refuses it.
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

            var other = Arrays.stream(Container.values()).filter(c -> c != container).findFirst().orElseThrow();
            var wrong = new ItemStack(other.empty()).getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElseThrow(IllegalStateException::new);
            helper.assertTrue(wrong.fill(fluid.copy(), FluidAction.SIMULATE) == 0, other + " accepted " + substance.key());
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

}
