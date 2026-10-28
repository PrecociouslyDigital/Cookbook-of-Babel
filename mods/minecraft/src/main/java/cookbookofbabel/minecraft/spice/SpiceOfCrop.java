package cookbookofbabel.minecraft.spice;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import cookbookofbabel.minecraft.Registration;
import cookbookofbabel.minecraft.substance.Substance;
import cookbookofbabel.minecraft.substance.Substances;
import java.util.Set;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Turns a broken spice crop's harvest into its spice, as many as the entry rolled: an item for a
 * whole spice, a portion for a ground one. A crop that names no spice yields nothing.
 */
public final class SpiceOfCrop extends LootItemConditionalFunction {
    private SpiceOfCrop(LootItemCondition[] conditions) {
        super(conditions);
    }

    public static LootItemConditionalFunction.Builder<?> spiceOfCrop() {
        return simpleBuilder(SpiceOfCrop::new);
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        var substances = context.getLevel().registryAccess().registryOrThrow(Substance.REGISTRY);
        return context.getParamOrNull(LootContextParams.BLOCK_ENTITY) instanceof SpiceCropBlockEntity crop
                ? crop.spice().flatMap(substances::getHolder).map(s -> Substances.stackOf(s, stack.getCount())).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.BLOCK_ENTITY);
    }

    @Override
    public LootItemFunctionType getType() {
        return Registration.SPICE_OF_CROP.get();
    }

    public static final class Serializer extends LootItemConditionalFunction.Serializer<SpiceOfCrop> {
        @Override
        public SpiceOfCrop deserialize(JsonObject json, JsonDeserializationContext context, LootItemCondition[] conditions) {
            return new SpiceOfCrop(conditions);
        }
    }
}
