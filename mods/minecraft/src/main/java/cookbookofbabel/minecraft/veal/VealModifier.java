package cookbookofbabel.minecraft.veal;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import cookbookofbabel.minecraft.Registration;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.function.Supplier;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

/**
 * A calf killed for its meat gives veal, which the adult's loot table has no line for. Vanilla
 * predicates can't ask whether an entity is young, so the check lives here instead.
 */
public final class VealModifier extends LootModifier {
    public static final Supplier<Codec<VealModifier>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.create(i -> codecStart(i).apply(i, VealModifier::new)));

    public VealModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (context.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof Cow cow && cow.isBaby()) {
            generatedLoot.add(new ItemStack(Registration.VEAL.get()));
        }
        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
