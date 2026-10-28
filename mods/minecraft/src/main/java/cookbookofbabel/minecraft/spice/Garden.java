package cookbookofbabel.minecraft.spice;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

/** The wild gardens of Pam's HarvestCraft 2: Crops, each a climate where some spices grow wild. */
public enum Garden implements StringRepresentable {
    ARID,
    FROST,
    SHADED,
    SOGGY,
    TROPICAL,
    WINDY;

    public static final Codec<Garden> CODEC = StringRepresentable.fromEnum(Garden::values);

    /** The garden block, whose loot table is named after it. */
    public ResourceLocation block() {
        return new ResourceLocation("pamhc2crops", getSerializedName() + "garden");
    }

    public ResourceLocation lootTable() {
        return block().withPrefix("blocks/");
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
