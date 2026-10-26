package cookbookofbabel.minecraft;

import cookbookofbabel.minecraft.dataset.NamedDish;
import cookbookofbabel.minecraft.dish.NamedDishItem;
import cookbookofbabel.minecraft.substance.Container;
import cookbookofbabel.minecraft.substance.PortionItem;
import cookbookofbabel.minecraft.substance.Substance;
import cookbookofbabel.minecraft.substance.SubstanceFluid;
import cookbookofbabel.minecraft.substance.SubstanceFluidType;
import cookbookofbabel.minecraft.substance.SubstanceItem;
import cookbookofbabel.minecraft.substance.Substances;
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
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Everything the mod registers: the carriers of substances, and the named dishes. */
public final class Registration {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, CookbookOfBabel.ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(ForgeRegistries.FLUIDS, CookbookOfBabel.ID);
    private static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, CookbookOfBabel.ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CookbookOfBabel.ID);

    public static final RegistryObject<SubstanceItem> SUBSTANCE_ITEM =
            ITEMS.register("substance", () -> new SubstanceItem(new Item.Properties()));
    public static final RegistryObject<PortionItem> PORTION =
            ITEMS.register("portion", () -> new PortionItem(new Item.Properties()));

    public static final RegistryObject<FluidType> SUBSTANCE_FLUID_TYPE = FLUID_TYPES.register(
            "substance",
            () -> new SubstanceFluidType(FluidType.Properties.create().descriptionId("fluid_type." + CookbookOfBabel.ID + ".substance")));

    public static final Map<Container, RegistryObject<Fluid>> SUBSTANCE_FLUIDS = new EnumMap<>(Arrays.stream(Container.values())
            .collect(Collectors.toMap(Function.identity(), c -> FLUIDS.register(c.fluidName, () -> fluidIn(c)))));

    public static final Map<NamedDish, RegistryObject<NamedDishItem>> DISHES = NamedDish.values().stream()
            .collect(Collectors.toMap(Function.identity(), d -> ITEMS.register(d.id(), () -> new NamedDishItem(d, new Item.Properties())),
                    (a, b) -> a, LinkedHashMap::new));

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
            })
            .build());

    private Registration() {}

    static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        FLUIDS.register(modBus);
        FLUID_TYPES.register(modBus);
        TABS.register(modBus);
    }

    private static Fluid fluidIn(Container container) {
        Supplier<Fluid> self = () -> SUBSTANCE_FLUIDS.get(container).get();
        return new SubstanceFluid(new ForgeFlowingFluid.Properties(SUBSTANCE_FLUID_TYPE, self, self));
    }
}
