package cookbookofbabel.minecraft.dataset;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** The dataset's form registry. Generated from the export; do not edit. */
public enum Form implements StringRepresentable {
    BATTER("batter"),
    CURD("curd"),
    DICE("dice"),
    FOAM("foam"),
    GAS("gas"),
    GEL("gel"),
    GRANULE("granule"),
    LIQUID("liquid"),
    MASH("mash"),
    PASTE("paste"),
    PIECE("piece"),
    POWDER("powder"),
    SHRED("shred"),
    SLICE("slice"),
    SOLID("solid"),
    WHOLE("whole");

    public static final Codec<Form> CODEC = StringRepresentable.fromEnum(Form::values);

    private final String id;

    Form(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
