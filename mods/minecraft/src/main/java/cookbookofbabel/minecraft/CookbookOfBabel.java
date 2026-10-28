package cookbookofbabel.minecraft;

import cookbookofbabel.minecraft.clock.ClockConfig;
import cookbookofbabel.minecraft.clock.ItemClocks;
import cookbookofbabel.minecraft.clock.Transition;
import cookbookofbabel.minecraft.room.Climates;
import cookbookofbabel.minecraft.room.StoredRooms;
import cookbookofbabel.minecraft.substance.EmptyContainers;
import cookbookofbabel.minecraft.substance.Substance;
import cookbookofbabel.minecraft.supplant.Supplanting;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DataPackRegistryEvent;

@Mod(CookbookOfBabel.ID)
public final class CookbookOfBabel {
    public static final String ID = "cookbook_of_babel";

    public CookbookOfBabel() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        Registration.register(modBus);
        modBus.addListener((DataPackRegistryEvent.NewRegistry event) -> {
            event.dataPackRegistry(Substance.REGISTRY, Substance.CODEC, Substance.CODEC);
            event.dataPackRegistry(Transition.REGISTRY, Transition.CODEC, Transition.CODEC);
        });
        MinecraftForge.EVENT_BUS.addGenericListener(net.minecraft.world.item.ItemStack.class, EmptyContainers::attach);
        var rooms = StoredRooms.register(modBus, MinecraftForge.EVENT_BUS);
        Supplanting.register(MinecraftForge.EVENT_BUS);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ClockConfig.SPEC);
        ItemClocks.install(new ItemClocks(rooms, Climates.best()));
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(ID, path);
    }
}
