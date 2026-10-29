package cookbookofbabel.minecraft.clock;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import cookbookofbabel.minecraft.CookbookOfBabel;
import cookbookofbabel.minecraft.dataset.Sensitivity;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

/**
 * A one-in, one-out step that happens to food while it is kept, not in a machine: a held step such as chilling,
 * cooling or staling. A datapack registry at {@code data/<namespace>/cookbook_of_babel/transition/<id>.json}, written
 * by datagen.
 *
 * @param input the dataset ingredient it acts on, by the ID of its substance or named dish
 * @param output the dataset ingredient it becomes
 * @param ticks how long it takes at the sensitivity's reference conditions
 */
public record Transition(
        ResourceLocation input, ResourceLocation output, long ticks, Sensitivity sensitivity, Kind kind)
        implements Timed {
    public static final ResourceKey<Registry<Transition>> REGISTRY =
            ResourceKey.createRegistryKey(CookbookOfBabel.id("transition"));

    public static final Codec<Transition> CODEC = RecordCodecBuilder.create(i -> i.group(
                    ResourceLocation.CODEC.fieldOf("input").forGetter(Transition::input),
                    ResourceLocation.CODEC.fieldOf("output").forGetter(Transition::output),
                    Codec.LONG.fieldOf("ticks").forGetter(Transition::ticks),
                    Sensitivity.CODEC.fieldOf("sensitivity").forGetter(Transition::sensitivity),
                    Kind.CODEC.fieldOf("kind").forGetter(Transition::kind))
            .apply(i, Transition::new));

    public enum Kind implements StringRepresentable {
        /** A recipe step that only needs time in the right conditions. */
        HELD("held");

        static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        private final String name;

        Kind(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
