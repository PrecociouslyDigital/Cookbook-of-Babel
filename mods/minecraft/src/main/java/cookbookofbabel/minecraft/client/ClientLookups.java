package cookbookofbabel.minecraft.client;

import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;

public final class ClientLookups {
    private ClientLookups() {}

    /** {@link Minecraft#getInstance} is null in datagen runs. */
    public static Optional<HolderLookup.Provider> current() {
        return Optional.ofNullable(Minecraft.getInstance()).map(mc -> mc.level).map(level -> level.registryAccess());
    }
}
