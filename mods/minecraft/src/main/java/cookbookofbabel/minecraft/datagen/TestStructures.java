package cookbookofbabel.minecraft.datagen;

import cookbookofbabel.minecraft.CookbookOfBabel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import net.minecraft.SharedConstants;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

/** An empty structure for GameTests to build in, so no binary structure file is checked in by hand. */
final class TestStructures implements DataProvider {
    static final int SIZE = 8;

    private final PackOutput output;

    TestStructures(PackOutput output) {
        this.output = output;
    }

    @Override
    @SuppressWarnings("deprecation")
    public CompletableFuture<?> run(CachedOutput cache) {
        var size = new ListTag();
        for (var i = 0; i < 3; i++) {
            size.add(IntTag.valueOf(SIZE));
        }
        var air = new CompoundTag();
        air.putString("Name", "minecraft:air");
        var palette = new ListTag();
        palette.add(air);
        var structure = new CompoundTag();
        structure.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        structure.put("size", size);
        structure.put("palette", palette);
        // Empty lists have no element type, which the structure data fixer rejects.
        var origin = new ListTag();
        for (var i = 0; i < 3; i++) {
            origin.add(IntTag.valueOf(0));
        }
        var block = new CompoundTag();
        block.put("pos", origin);
        block.putInt("state", 0);
        var blocks = new ListTag();
        blocks.add(block);
        structure.put("blocks", blocks);
        try {
            var bytes = new ByteArrayOutputStream();
            NbtIo.writeCompressed(structure, bytes);
            var path = output.getOutputFolder(PackOutput.Target.DATA_PACK).resolve(CookbookOfBabel.ID + "/structures/empty.nbt");
            cache.writeIfNeeded(path, bytes.toByteArray(), com.google.common.hash.Hashing.sha1().hashBytes(bytes.toByteArray()));
            return CompletableFuture.completedFuture(null);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public String getName() {
        return "GameTest structures";
    }
}
