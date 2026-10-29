package cookbookofbabel.minecraft.datagen;

import cookbookofbabel.minecraft.CookbookOfBabel;
import cookbookofbabel.minecraft.Registration;
import cookbookofbabel.minecraft.spice.Garden;
import cookbookofbabel.minecraft.spice.SpiceGardenModifier;
import cookbookofbabel.minecraft.spice.SpiceGardens;
import cookbookofbabel.minecraft.spice.SpiceOfCrop;
import cookbookofbabel.minecraft.substance.Substances;
import cookbookofbabel.minecraft.veal.VealModifier;
import java.util.List;
import java.util.Set;
import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.AlternativesEntry;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.functions.CopyNbtFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.nbt.ContextNbtProvider;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.common.data.GlobalLootModifierProvider;
import net.minecraftforge.common.loot.LootTableIdCondition;

/** The spice crop's models and loot, and the spice seeds the wild gardens drop. */
final class SpiceCropGen {
    /** How often a broken garden also drops the seeds of one of its spices. */
    private static final float SEED_CHANCE = 0.25f;

    private SpiceCropGen() {}

    /**
     * Vanilla's tintable foliage, so the colour handler can tint it per spice: grass while it grows,
     * a fern once it's ready to harvest.
     */
    static final class Models extends BlockStateProvider {
        Models(PackOutput output, ExistingFileHelper files) {
            super(output, CookbookOfBabel.ID, files);
        }

        @Override
        protected void registerStatesAndModels() {
            var crop = Registration.SPICE_CROP.get();
            var growing = cross("block/spice_crop_growing", "block/grass");
            var ready = cross("block/spice_crop_ready", "block/fern");
            getVariantBuilder(crop).forAllStates(state -> ConfiguredModel.builder()
                    .modelFile(crop.isMaxAge(state) ? ready : growing)
                    .build());
        }

        private ModelFile cross(String name, String texture) {
            return models().withExistingParent(name, mcLoc("block/tinted_cross")).texture("cross", mcLoc(texture)).renderType("cutout");
        }
    }

    static LootTableProvider loot(PackOutput output) {
        return new LootTableProvider(output, Set.of(), List.of(new LootTableProvider.SubProviderEntry(Loot::new, LootContextParamSets.BLOCK)));
    }

    /**
     * Wheat's drops: a ripe crop gives its spice and one or more seeds, an unripe one a seed. The
     * seed copies the crop's spice from the block entity, and the harvest becomes that spice.
     */
    private static final class Loot extends BlockLootSubProvider {
        Loot() {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags());
        }

        @Override
        protected void generate() {
            var crop = Registration.SPICE_CROP.get();
            var ripe = LootItemBlockStatePropertyCondition.hasBlockStateProperties(crop)
                    .setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(CropBlock.AGE, crop.getMaxAge()));
            var spice = LootItem.lootTableItem(Registration.SUBSTANCE_ITEM.get()).apply(SpiceOfCrop.spiceOfCrop()).when(ripe);
            add(crop, applyExplosionDecay(crop, LootTable.lootTable()
                    .withPool(LootPool.lootPool().add(AlternativesEntry.alternatives(spice, seed())))
                    .withPool(LootPool.lootPool().when(ripe).add(seed()
                            .apply(ApplyBonusCount.addBonusBinomialDistributionCount(Enchantments.BLOCK_FORTUNE, 0.5714286f, 3))))));
        }

        private static LootPoolSingletonContainer.Builder<?> seed() {
            return LootItem.lootTableItem(Registration.SPICE_SEEDS.get())
                    .apply(CopyNbtFunction.copyData(ContextNbtProvider.BLOCK_ENTITY).copy(Substances.KEY, "BlockEntityTag." + Substances.KEY));
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return List.of(Registration.SPICE_CROP.get());
        }
    }

    /**
     * Every global loot modifier: one per garden that grows any planted spice, and the calf that
     * drops veal.
     */
    static final class Gardens extends GlobalLootModifierProvider {
        Gardens(PackOutput output) {
            super(output, CookbookOfBabel.ID);
        }

        @Override
        protected void start() {
            for (var garden : Garden.values()) {
                var spices = SpiceGardens.growingIn(garden);
                if (!spices.isEmpty()) {
                    add("spice_seeds/" + garden.getSerializedName(), new SpiceGardenModifier(new LootItemCondition[] {
                        LootTableIdCondition.builder(garden.lootTable()).build(),
                        LootItemRandomChanceCondition.randomChance(SEED_CHANCE).build()
                    }, spices));
                }
            }
            add("veal", new VealModifier(new LootItemCondition[] {
                LootTableIdCondition.builder(new ResourceLocation("entities/cow")).build()
            }));
        }
    }
}
