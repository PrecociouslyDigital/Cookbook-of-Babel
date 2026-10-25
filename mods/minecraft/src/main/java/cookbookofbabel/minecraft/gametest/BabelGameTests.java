package cookbookofbabel.minecraft.gametest;

import com.google.gson.JsonParser;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.mixer.MixingRecipe;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import cookbookofbabel.minecraft.CookbookOfBabel;
import cookbookofbabel.minecraft.substance.Carrier;
import cookbookofbabel.minecraft.substance.Container;
import cookbookofbabel.minecraft.substance.Substance;
import cookbookofbabel.minecraft.substance.Substances;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
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
        var context = tags(server);
        var missing = new ArrayList<ResourceLocation>();
        var checked = 0;
        var files = server.getResourceManager().listResources("recipes", id -> id.getNamespace().equals(CookbookOfBabel.ID));
        for (var file : files.entrySet()) {
            try (var reader = file.getValue().openAsReader()) {
                var json = JsonParser.parseReader(reader).getAsJsonObject();
                if (!CraftingHelper.processConditions(json, "conditions", context)) {
                    continue;
                }
            }
            var path = file.getKey().getPath();
            var id = new ResourceLocation(CookbookOfBabel.ID, path.substring("recipes/".length(), path.length() - ".json".length()));
            checked++;
            if (server.getRecipeManager().byKey(id).isEmpty()) {
                missing.add(id);
            }
        }
        helper.assertTrue(checked > 0, "no generated recipe's conditions held");
        helper.assertTrue(missing.isEmpty(), missing.size() + " of " + checked + " generated recipes didn't load, e.g. "
                + missing.stream().limit(5).map(Object::toString).collect(Collectors.joining(", ")));
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
        var level = helper.getLevel();
        RecipeType<MixingRecipe> mixing = AllRecipeTypes.MIXING.getType();
        var recipe = level.getRecipeManager().getAllRecipesFor(mixing).stream()
                .filter(r -> r.getId().getNamespace().equals(CookbookOfBabel.ID))
                .filter(r -> r.getRequiredHeat() == HeatCondition.NONE && r.getFluidIngredients().isEmpty())
                .filter(r -> r.getRollableResults().size() == 1 && r.getFluidResults().isEmpty())
                .min(Comparator.comparing(r -> r.getId().toString()))
                .orElseThrow(() -> new IllegalStateException("no plain generated mixing recipe"));

        var basin = new BlockPos(2, 1, 2);
        helper.setBlock(basin, AllBlocks.BASIN.getDefaultState());
        // The mixer is a cog: a cogwheel beside it, turned by the motor above, drives it.
        helper.setBlock(basin.above(2), AllBlocks.MECHANICAL_MIXER.getDefaultState());
        helper.setBlock(basin.above(2).east(), AllBlocks.COGWHEEL.getDefaultState());
        helper.setBlock(basin.above(3).east(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.DOWN));
        // The mixer won't run below medium speed.
        ((CreativeMotorBlockEntity) helper.getBlockEntity(basin.above(3).east())).generatedSpeed.setValue(64);

        // The mixer only looks at the basin when its contents change, so fill it once the mixer turns.
        var inventory = helper.getBlockEntity(basin).getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
        helper.runAfterDelay(20, () -> {
            for (var ingredient : recipe.getIngredients()) {
                var stack = matching(ingredient);
                for (var slot = 0; slot < inventory.getSlots() && !stack.isEmpty(); slot++) {
                    stack = inventory.insertItem(slot, stack, false);
                }
            }
        });
        var result = recipe.getRollableResults().get(0).getStack();
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
            var mixer = (KineticBlockEntity) helper.getBlockEntity(basin.above(2));
            helper.fail("the basin hasn't made " + result + " with " + recipe.getId() + "; it holds " + contents
                    + " and the mixer turns at " + mixer.getSpeed() + " rpm");
        });
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

    /** The mod's own stack an ingredient accepts; its tags may be empty in a test world. */
    private static ItemStack matching(Ingredient ingredient) {
        return Arrays.stream(ingredient.getItems())
                .filter(s -> s.hasTag() && ingredient.test(s))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no stack of the mod's own matches " + ingredient.toJson()))
                .copy();
    }

    private static ICondition.IContext tags(MinecraftServer server) {
        return new ICondition.IContext() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public <T> Map<ResourceLocation, Collection<Holder<T>>> getAllTags(ResourceKey<? extends Registry<T>> key) {
                return server.registryAccess().registry(key)
                        .map(registry -> registry.getTags().collect(Collectors.toMap(
                                pair -> pair.getFirst().location(),
                                pair -> (Collection<Holder<T>>) (Collection) pair.getSecond().stream().toList())))
                        .orElse(Map.of());
            }
        };
    }

}
