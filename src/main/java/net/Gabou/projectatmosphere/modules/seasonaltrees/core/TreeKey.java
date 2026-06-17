package net.Gabou.projectatmosphere.modules.seasonaltrees.core;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

import java.util.Objects;

public final class TreeKey {
    private static final String TAG_DIMENSION = "dimension";
    private static final String TAG_CHUNK_X = "chunkX";
    private static final String TAG_CHUNK_Z = "chunkZ";
    private static final String TAG_ROOT_POS = "root";
    private static final String TAG_TREE_TYPE = "type";

    private final ResourceLocation dimensionId;
    private final ChunkPos chunkPos;
    private final BlockPos rootPos;
    private final TreeType treeType;

    public TreeKey(ResourceLocation dimensionId, ChunkPos chunkPos, BlockPos rootPos, TreeType treeType) {
        this.dimensionId = dimensionId;
        this.chunkPos = chunkPos;
        this.rootPos = rootPos;
        this.treeType = treeType;
    }

    public ResourceLocation dimensionId() {
        return dimensionId;
    }

    public ChunkPos chunkPos() {
        return chunkPos;
    }

    public BlockPos rootPos() {
        return rootPos;
    }

    public TreeType treeType() {
        return treeType;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_DIMENSION, dimensionId.toString());
        tag.putInt(TAG_CHUNK_X, chunkPos.x);
        tag.putInt(TAG_CHUNK_Z, chunkPos.z);
        tag.putLong(TAG_ROOT_POS, rootPos.asLong());
        tag.putString(TAG_TREE_TYPE, treeType.name());
        return tag;
    }

    public static TreeKey fromTag(CompoundTag tag) {
        ResourceLocation dimension = ResourceLocation.parse(tag.getString(TAG_DIMENSION));
        ChunkPos chunkPos = new ChunkPos(tag.getInt(TAG_CHUNK_X), tag.getInt(TAG_CHUNK_Z));
        BlockPos root = BlockPos.of(tag.getLong(TAG_ROOT_POS));
        TreeType treeType = tag.contains(TAG_TREE_TYPE)
                ? TreeType.valueOf(tag.getString(TAG_TREE_TYPE))
                : TreeType.DYNAMIC;
        return new TreeKey(dimension, chunkPos, root, treeType);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        TreeKey other = (TreeKey) obj;
        return Objects.equals(dimensionId, other.dimensionId)
                && Objects.equals(chunkPos, other.chunkPos)
                && Objects.equals(rootPos, other.rootPos)
                && treeType == other.treeType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimensionId, chunkPos, rootPos, treeType);
    }
}
