package cookbookofbabel.minecraft.datagen;

import cookbookofbabel.minecraft.CookbookOfBabel;
import cookbookofbabel.minecraft.Registration;
import cookbookofbabel.minecraft.client.ClientSetup;
import cookbookofbabel.minecraft.dataset.Form;
import cookbookofbabel.minecraft.substance.Container;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.generators.ItemModelBuilder;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;

/**
 * One model per form for substances, one per container for portions, each picked by an item
 * property ({@link ClientSetup}). Named dishes take their form's model, and spice seeds the cocoa
 * bean's. The contents' layer is tinted with the colour of what's in the stack.
 */
final class ItemModels extends ItemModelProvider {
    private static final ResourceLocation UNKNOWN = new ResourceLocation("item/barrier");

    ItemModels(PackOutput output, ExistingFileHelper files) {
        super(output, CookbookOfBabel.ID, files);
    }

    @Override
    protected void registerModels() {
        var substance = flat("substance", UNKNOWN);
        for (var form : Form.values()) {
            substance.override()
                    .predicate(CookbookOfBabel.id(ClientSetup.FORM), form.ordinal())
                    .model(flat("item/substance/" + form.getSerializedName(), texture(form)))
                    .end();
        }

        var portion = flat("portion", UNKNOWN);
        for (var container : Container.values()) {
            var name = "item/portion/" + container.getSerializedName();
            var model = switch (container) {
                case BOTTLE -> withExistingParent(name, "item/generated")
                        .texture("layer0", mcLoc("item/potion_overlay"))
                        .texture("layer1", mcLoc("item/potion"));
                case PAPER_TWIST -> flat(name, new ResourceLocation("item/paper"));
                case BOWL -> withExistingParent(name, "item/generated")
                        // Vanilla's bowl sprite, with the contents masked out of the vanilla
                        // soups drawn above it; the bowl itself must stay untinted.
                        .texture("layer0", mcLoc("item/bowl"))
                        .texture("layer1", modLoc("item/bowl_contents"));
            };
            portion.override()
                    .predicate(CookbookOfBabel.id(ClientSetup.CONTAINER), container.ordinal())
                    .model(model)
                    .end();
        }

        flat("spice_seeds", new ResourceLocation("item/cocoa_beans"));
        // The calf's own meat, beef recoloured pink.
        withExistingParent("veal", "item/generated").texture("layer0", modLoc("item/veal"));

        Registration.DISHES.forEach((dish, item) -> getBuilder(item.getId().getPath())
                .parent(new ModelFile.UncheckedModelFile(modLoc("item/substance/" + dish.form().getSerializedName()))));
    }

    private ItemModelBuilder flat(String name, ResourceLocation texture) {
        return withExistingParent(name, "item/generated").texture("layer0", texture);
    }

    /** Vanilla stand-ins, picked by a Jev scoring pass: silhouettes that read as the form and
     *  stay one-to-one across forms, pale enough to take a tint. */
    private static ResourceLocation texture(Form form) {
        var item = switch (form) {
            case WHOLE -> "clay_ball";
            case PIECE -> "quartz";
            case SLICE -> "paper";
            case SHRED -> "string";
            case DICE -> "iron_nugget";
            case MASH -> "snowball";
            case POWDER -> "bone_meal";
            case GRANULE -> "sugar";
            case PASTE -> "white_dye";
            case BATTER -> "light_gray_dye";
            case LIQUID -> "potion";
            case FOAM -> "firework_star";
            case GEL -> "slime_ball";
            case CURD -> "glowstone_dust";
            case SOLID -> "iron_ingot";
            case GAS -> "phantom_membrane";
        };
        return new ResourceLocation("item/" + item);
    }
}
