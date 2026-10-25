package cookbookofbabel.minecraft;

import cookbookofbabel.minecraft.client.ClientLookups;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * The registries of the world being played, for code that is handed a bare stack, such as an item's
 * name or a fluid's description. Empty outside a world.
 */
public final class Lookups {
    private Lookups() {}

    public static Optional<HolderLookup.Provider> current() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return Optional.of(server.registryAccess());
        }
        return FMLEnvironment.dist == Dist.CLIENT ? ClientLookups.current() : Optional.empty();
    }
}
