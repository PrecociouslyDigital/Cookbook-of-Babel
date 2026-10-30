package cookbookofbabel.minecraft.datagen;

import cookbookofbabel.minecraft.BabelTags;
import cookbookofbabel.minecraft.CookbookOfBabel;
import cookbookofbabel.minecraft.Registration;
import cookbookofbabel.minecraft.dataset.IngredientId;
import cookbookofbabel.minecraft.dataset.NamedDish;
import cookbookofbabel.minecraft.datagen.plan.Mapping;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.data.ExistingFileHelper;

/**
 * The {@code ingredients/<id>} and {@code external/<id>} tags of every dataset ingredient (see
 * {@link BabelTags}). Other mods' tags and items are optional entries, so a missing mod leaves a
 * tag smaller instead of failing to load.
 */
final class TagsGen {
    private TagsGen() {}

    static final class Items extends TagsProvider<Item> {
        private final DatagenContext context;

        Items(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, DatagenContext context, ExistingFileHelper files) {
            super(output, Registries.ITEM, lookup, CookbookOfBabel.ID, files);
            this.context = context;
        }

        @Override
        protected void addTags(HolderLookup.Provider lookup) {
            for (var ingredient : context.dataset.ingredients()) {
                var id = ingredient.id();
                var external = tag(BabelTags.externalItems(id.value()));
                for (var tag : TagsGen.mapped(context, id, Mapping.Entry::itemTags)) {
                    external.addOptionalTag(new ResourceLocation(tag));
                }
                for (var mapping : context.mappings) {
                    mapping.entry(id).ifPresent(e -> e.items().forEach(item ->
                            external.addOptional(new ResourceLocation(item.id()))));
                }
                // An ingredients tag with nothing in it reads as an accepted-but-empty ingredient, so
                // it is written only where something counts as the ingredient besides the mod's own
                // substance, which every recipe matches by NBT.
                if (!context.tagged(id)) {
                    continue;
                }
                var all = tag(BabelTags.ingredientItems(id.value()));
                for (var tag : TagsGen.mapped(context, id, Mapping.Entry::itemTags)) {
                    all.addOptionalTag(new ResourceLocation(tag));
                }
                for (var mapping : context.mappings) {
                    mapping.entry(id).ifPresent(e -> e.items().forEach(item ->
                            all.addOptional(new ResourceLocation(item.id()))));
                }
                NamedDish.byId(id.value()).ifPresent(dish ->
                        all.add(ResourceKey.create(Registries.ITEM, Registration.DISHES.get(dish).getId())));
                context.dataset.children(id).stream()
                        .filter(child -> context.tagged(child))
                        .forEach(child -> all.addTag(BabelTags.ingredientItems(child.value())));
            }
        }
    }

    static final class Fluids extends TagsProvider<Fluid> {
        private final DatagenContext context;

        Fluids(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, DatagenContext context, ExistingFileHelper files) {
            super(output, Registries.FLUID, lookup, CookbookOfBabel.ID, files);
            this.context = context;
        }

        /** Only flowable ingredients have fluid tags. */
        @Override
        protected void addTags(HolderLookup.Provider lookup) {
            for (var ingredient : context.dataset.ingredients()) {
                var id = ingredient.id();
                if (context.dataset.container(id).isEmpty()) {
                    continue;
                }
                var all = tag(BabelTags.ingredientFluids(id.value()));
                var external = tag(BabelTags.externalFluids(id.value()));
                for (var tag : mapped(id, Mapping.Entry::fluidTags)) {
                    all.addOptionalTag(new ResourceLocation(tag));
                    external.addOptionalTag(new ResourceLocation(tag));
                }
                for (var mapping : context.mappings) {
                    mapping.entry(id).ifPresent(e -> e.fluids().forEach(fluid -> {
                        all.addOptional(new ResourceLocation(fluid));
                        external.addOptional(new ResourceLocation(fluid));
                    }));
                }
                context.dataset.children(id).stream()
                        .filter(child -> context.dataset.container(child).isPresent())
                        .forEach(child -> all.addTag(BabelTags.ingredientFluids(child.value())));
            }
        }

        private List<String> mapped(IngredientId id, Function<Mapping.Entry, List<String>> tags) {
            return TagsGen.mapped(context, id, tags);
        }
    }

    private static List<String> mapped(DatagenContext context, IngredientId id, Function<Mapping.Entry, List<String>> tags) {
        return Mapping.withMirrors(context.mappings.stream()
                .flatMap(m -> m.entry(id).stream())
                .flatMap(e -> tags.apply(e).stream())
                .toList());
    }

    private static String constant(IngredientId id) {
        var upper = id.value().toUpperCase(java.util.Locale.ROOT);
        return Character.isDigit(upper.charAt(0)) ? "_" + upper : upper;
    }
}
