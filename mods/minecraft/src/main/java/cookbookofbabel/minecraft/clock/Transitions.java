package cookbookofbabel.minecraft.clock;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.stream.Collectors;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;

/**
 * Transitions by the ingredient they act on. A datapack registry is built afresh whenever a world's registries are,
 * so the index is kept per registry instance and goes when the registry does.
 */
public final class Transitions {
    private static final Map<Registry<Transition>, Map<ResourceLocation, Map<ResourceLocation, Transition>>> INDEXES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private Transitions() {}

    /** The transitions that act on {@code input}, by transition ID. */
    public static Map<ResourceLocation, Transition> from(ResourceLocation input, RegistryAccess registries) {
        return registries.registry(Transition.REGISTRY)
                .map(registry -> INDEXES.computeIfAbsent(registry, Transitions::index).getOrDefault(input, Map.of()))
                .orElse(Map.of());
    }

    /** Progress is time over duration, so a transition that takes no time is left out rather than divided by. */
    private static Map<ResourceLocation, Map<ResourceLocation, Transition>> index(Registry<Transition> registry) {
        return registry.entrySet().stream()
                .filter(e -> e.getValue().ticks() > 0)
                .collect(Collectors.groupingBy(
                        e -> e.getValue().input(),
                        Collectors.toMap(e -> e.getKey().location(), Map.Entry::getValue, (a, b) -> a, TreeMap::new)));
    }
}
