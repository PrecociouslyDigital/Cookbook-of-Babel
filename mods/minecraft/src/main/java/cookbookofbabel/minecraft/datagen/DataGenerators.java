package cookbookofbabel.minecraft.datagen;

import cookbookofbabel.minecraft.CookbookOfBabel;
import cookbookofbabel.minecraft.datagen.recipe.Carriers;
import cookbookofbabel.minecraft.datagen.recipe.RecipesGen;
import cookbookofbabel.minecraft.substance.Colors;
import cookbookofbabel.minecraft.substance.Substance;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraftforge.common.data.DatapackBuiltinEntriesProvider;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CookbookOfBabel.ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DataGenerators {
    private DataGenerators() {}

    @SubscribeEvent
    static void gather(GatherDataEvent event) {
        var generator = event.getGenerator();
        var output = generator.getPackOutput();
        var files = event.getExistingFileHelper();
        generator.addProvider(event.includeClient(), new ItemModels(output, files));
        generator.addProvider(event.includeClient(), new SpiceCropGen.Models(output, files));
        if (!event.includeServer()) {
            return;
        }
        var context = load();
        var substances = generator.addProvider(true, new DatapackBuiltinEntriesProvider(
                output, event.getLookupProvider(), substances(context), Set.of(CookbookOfBabel.ID)));
        var registries = substances.getRegistryProvider();
        generator.addProvider(true, new TagsGen.Items(output, registries, context, files));
        generator.addProvider(true, new TagsGen.Fluids(output, registries, context, files));
        generator.addProvider(true, new RoomsGen.CoolingWalls(output, event.getLookupProvider(), files));
        generator.addProvider(true, new RoomsGen.HeatSources(output));
        generator.addProvider(true, new RecipesGen(output, context, registries));
        generator.addProvider(true, new Attribution(output, context.export));
        generator.addProvider(true, new TestStructures(output));
        generator.addProvider(true, SpiceCropGen.loot(output));
        generator.addProvider(true, new SpiceCropGen.Gardens(output));
        generator.addProvider(true, new Reports(context));
    }

    /** One substance per ingredient that isn't a named dish, with its dataset carrier. */
    private static RegistrySetBuilder substances(DatagenContext context) {
        var tints = context.export.colorTable();
        return new RegistrySetBuilder().add(Substance.REGISTRY, bootstrap -> {
            for (var ingredient : context.dataset.ingredients()) {
                if (!ingredient.named()) {
                    var color = Colors.tint(tints, ingredient.label(), ingredient.aliases(), ingredient.id().value())
                            .orElseGet(() -> Colors.forId(ingredient.id().value()));
                    bootstrap.register(Carriers.key(ingredient.id()), new Substance(
                            ingredient.label(),
                            ingredient.form(),
                            color,
                            context.dataset.carrier(ingredient.id()),
                            context.dataset.nutrition(ingredient.id()),
                            ingredient.waterActivity()));
                }
            }
        });
    }

    private static DatagenContext load() {
        try {
            return DatagenContext.load();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
