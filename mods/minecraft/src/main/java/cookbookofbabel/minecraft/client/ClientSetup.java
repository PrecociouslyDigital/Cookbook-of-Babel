package cookbookofbabel.minecraft.client;

import cookbookofbabel.minecraft.CookbookOfBabel;
import cookbookofbabel.minecraft.Registration;
import cookbookofbabel.minecraft.substance.Colors;
import cookbookofbabel.minecraft.substance.PortionItem;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = CookbookOfBabel.ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    /** The property the item models' overrides pick a form or container model by; -1 when unknown. */
    public static final String FORM = "form";
    public static final String CONTAINER = "container";

    private ClientSetup() {}

    @SubscribeEvent
    static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(Registration.SUBSTANCE_ITEM.get(), CookbookOfBabel.id(FORM), (stack, level, entity, seed) ->
                    ClientSubstances.of(stack).map(s -> (float) s.value().form().ordinal()).orElse(-1f));
            ItemProperties.register(Registration.PORTION.get(), CookbookOfBabel.id(CONTAINER), (stack, level, entity, seed) ->
                    PortionItem.container(stack).map(c -> (float) c.ordinal()).orElse(-1f));
        });
    }

    /** Layer 0 of every model carries the colour. */
    @SubscribeEvent
    static void colors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, layer) -> layer == 0 ? ClientSubstances.color(stack) : -1, Registration.SUBSTANCE_ITEM.get());
        event.register(
                (stack, layer) -> layer == 0 ? PortionItem.contents(stack).map(ClientSubstances::color).orElse(-1) : -1,
                Registration.PORTION.get());
        Registration.DISHES.forEach((dish, item) ->
                event.register((stack, layer) -> layer == 0 ? Colors.forId(dish.id) : -1, item.get()));
    }
}
