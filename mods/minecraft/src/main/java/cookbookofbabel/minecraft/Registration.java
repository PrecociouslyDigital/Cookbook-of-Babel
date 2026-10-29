package cookbookofbabel.minecraft;

import com.mojang.serialization.Codec;
import cookbookofbabel.minecraft.dataset.NamedDish;
import cookbookofbabel.minecraft.dish.NamedDishItem;
import cookbookofbabel.minecraft.spice.SpiceCropBlock;
import cookbookofbabel.minecraft.spice.SpiceCropBlockEntity;
import cookbookofbabel.minecraft.spice.SpiceGardenModifier;
import cookbookofbabel.minecraft.spice.SpiceGardens;
import cookbookofbabel.minecraft.spice.SpiceOfCrop;
import cookbookofbabel.minecraft.spice.SpiceSeedsItem;
import cookbookofbabel.minecraft.substance.FluidKind;
import cookbookofbabel.minecraft.substance.PortionItem;
import cookbookofbabel.minecraft.substance.Substance;
import cookbookofbabel.minecraft.substance.SubstanceFluid;
import cookbookofbabel.minecraft.substance.SubstanceFluidType;
import cookbookofbabel.minecraft.substance.SubstanceItem;
import cookbookofbabel.minecraft.substance.Substances;
import cookbookofbabel.minecraft.veal.VealModifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Everything the mod registers: the carriers of substances, the named dishes, and the spice crop. */
public final class Registration {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, CookbookOfBabel.ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(ForgeRegistries.FLUIDS, CookbookOfBabel.ID);
    private static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, CookbookOfBabel.ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CookbookOfBabel.ID);
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, CookbookOfBabel.ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, CookbookOfBabel.ID);
    private static final DeferredRegister<LootItemFunctionType> LOOT_FUNCTION_TYPES =
            DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, CookbookOfBabel.ID);
    private static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, CookbookOfBabel.ID);

    public static final RegistryObject<SubstanceItem> SUBSTANCE_ITEM =
            ITEMS.register("substance", () -> new SubstanceItem(new Item.Properties()));
    public static final RegistryObject<PortionItem> PORTION =
            ITEMS.register("portion", () -> new PortionItem(new Item.Properties()));
    /** The dataset has no beef for it, and no other mod makes one: the meat of a calf. */
    public static final RegistryObject<Item> VEAL = ITEMS.register("veal", () -> new Item(new Item.Properties()
            .food(new FoodProperties.Builder().nutrition(3).saturationMod(0.3f).build())));

    public static final RegistryObject<FluidType> SUBSTANCE_FLUID_TYPE = FLUID_TYPES.register(
            "substance",
            () -> new SubstanceFluidType(FluidType.Properties.create().descriptionId("fluid_type." + CookbookOfBabel.ID + ".substance")));

    public static final Map<FluidKind, RegistryObject<Fluid>> SUBSTANCE_FLUIDS = new EnumMap<>(Arrays.stream(FluidKind.values())
            .collect(Collectors.toMap(Function.identity(), k -> FLUIDS.register(k.toString(), () -> fluidIn(k)))));

    public static final Map<NamedDish, RegistryObject<NamedDishItem>> DISHES = NamedDish.values().stream()
            .collect(Collectors.toMap(Function.identity(), d -> ITEMS.register(d.id(), () -> new NamedDishItem(d, new Item.Properties())),
                    (a, b) -> a, LinkedHashMap::new));

    public static final RegistryObject<SpiceCropBlock> SPICE_CROP =
            BLOCKS.register("spice_crop", () -> new SpiceCropBlock(BlockBehaviour.Properties.copy(Blocks.WHEAT)));
    public static final RegistryObject<SpiceSeedsItem> SPICE_SEEDS =
            ITEMS.register("spice_seeds", () -> new SpiceSeedsItem(SPICE_CROP.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<SpiceCropBlockEntity>> SPICE_CROP_ENTITY = BLOCK_ENTITY_TYPES.register(
            "spice_crop", () -> BlockEntityType.Builder.of(SpiceCropBlockEntity::new, SPICE_CROP.get()).build(null));
    public static final RegistryObject<LootItemFunctionType> SPICE_OF_CROP =
            LOOT_FUNCTION_TYPES.register("spice_of_crop", () -> new LootItemFunctionType(new SpiceOfCrop.Serializer()));
    public static final RegistryObject<Codec<SpiceGardenModifier>> SPICE_GARDEN =
            LOOT_MODIFIERS.register("spice_garden", SpiceGardenModifier.CODEC);
    public static final RegistryObject<Codec<VealModifier>> VEAL_DROP =
            LOOT_MODIFIERS.register("veal", VealModifier.CODEC);

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + CookbookOfBabel.ID))
            .icon(() -> new ItemStack(Items.BOOK))
            .displayItems((parameters, output) -> {
                var registries = parameters.holders();
                registries.lookup(Substance.REGISTRY).ifPresent(substances -> substances.listElements()
                        .filter(s -> !BabelTags.providedElsewhere(registries, s.key().location().getPath()))
                        .sorted(Comparator.comparing((Holder.Reference<Substance> s) -> s.key().location()))
                        .forEach(s -> output.accept(Substances.stackOf(s, 1))));
                DISHES.forEach((dish, item) -> {
                    if (!BabelTags.providedElsewhere(registries, dish.id())) {
                        output.accept(item.get());
                    }
                });
                output.accept(VEAL.get());
                SpiceGardens.spices().forEach(spice -> output.accept(SpiceSeedsItem.of(spice)));
            })
            .build());

    private Registration() {}

    static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        FLUIDS.register(modBus);
        FLUID_TYPES.register(modBus);
        TABS.register(modBus);
        BLOCKS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        LOOT_FUNCTION_TYPES.register(modBus);
        LOOT_MODIFIERS.register(modBus);
    }

    private static Fluid fluidIn(FluidKind kind) {
        Supplier<Fluid> self = () -> SUBSTANCE_FLUIDS.get(kind).get();
        return new SubstanceFluid(new ForgeFlowingFluid.Properties(SUBSTANCE_FLUID_TYPE, self, self));
    }
}
