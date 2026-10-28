package cookbookofbabel.minecraft.spice;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import cookbookofbabel.minecraft.substance.Substance;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

/**
 * Adds the seeds of one of a garden's spices to what the garden drops, picked at random. Its
 * conditions say which garden and how often.
 */
public final class SpiceGardenModifier extends LootModifier {
    public static final Supplier<Codec<SpiceGardenModifier>> CODEC = Suppliers.memoize(() -> RecordCodecBuilder.create(i -> codecStart(i)
            .and(ResourceKey.codec(Substance.REGISTRY).listOf().fieldOf("spices").forGetter(m -> m.spices))
            .apply(i, SpiceGardenModifier::new)));

    private final List<ResourceKey<Substance>> spices;

    public SpiceGardenModifier(LootItemCondition[] conditions, List<ResourceKey<Substance>> spices) {
        super(conditions);
        if (spices.isEmpty()) {
            throw new IllegalArgumentException("a spice garden modifier needs a spice");
        }
        this.spices = List.copyOf(spices);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        generatedLoot.add(SpiceSeedsItem.of(spices.get(context.getRandom().nextInt(spices.size()))));
        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
