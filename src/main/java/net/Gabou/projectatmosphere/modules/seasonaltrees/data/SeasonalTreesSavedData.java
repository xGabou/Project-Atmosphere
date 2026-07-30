package net.Gabou.projectatmosphere.modules.seasonaltrees.data;

import net.Gabou.projectatmosphere.modules.seasonaltrees.core.TreeKey;
import net.Gabou.projectatmosphere.modules.seasonaltrees.core.TreeRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SeasonalTreesSavedData extends SavedData {
    private static final String DATA_NAME = "projectatmosphere_seasonal_trees";
    private static final String TAG_CHUNKS = "chunks";
    private static final String TAG_CHUNK_X = "x";
    private static final String TAG_CHUNK_Z = "z";
    private static final String TAG_TREES = "trees";

    private final Map<Long, Map<BlockPos, TreeRecord>> chunkTrees = new HashMap<>();

    public static SeasonalTreesSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SeasonalTreesSavedData::new, SeasonalTreesSavedData::load),
                DATA_NAME
        );
    }

    public static SeasonalTreesSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        SeasonalTreesSavedData data = new SeasonalTreesSavedData();
        ListTag chunks = tag.getList(TAG_CHUNKS, Tag.TAG_COMPOUND);
        for (Tag entry : chunks) {
            CompoundTag chunkTag = (CompoundTag) entry;
            int x = chunkTag.getInt(TAG_CHUNK_X);
            int z = chunkTag.getInt(TAG_CHUNK_Z);
            long key = ChunkPos.asLong(x, z);
            ListTag treeList = chunkTag.getList(TAG_TREES, Tag.TAG_COMPOUND);
            Map<BlockPos, TreeRecord> trees = new HashMap<>();
            for (Tag treeEntry : treeList) {
                TreeRecord record = TreeRecord.fromTag((CompoundTag) treeEntry);
                trees.put(record.key().rootPos(), record);
            }
            if (!trees.isEmpty()) {
                data.chunkTrees.put(key, trees);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag chunks = new ListTag();
        for (Map.Entry<Long, Map<BlockPos, TreeRecord>> entry : chunkTrees.entrySet()) {
            long chunkKey = entry.getKey();
            ChunkPos chunkPos = new ChunkPos(chunkKey);
            CompoundTag chunkTag = new CompoundTag();
            chunkTag.putInt(TAG_CHUNK_X, chunkPos.x);
            chunkTag.putInt(TAG_CHUNK_Z, chunkPos.z);

            ListTag treeList = new ListTag();
            for (TreeRecord record : entry.getValue().values()) {
                treeList.add(record.toTag());
            }
            chunkTag.put(TAG_TREES, treeList);
            chunks.add(chunkTag);
        }
        tag.put(TAG_CHUNKS, chunks);
        return tag;
    }

    public Collection<TreeRecord> getTreesInChunk(ChunkPos chunkPos) {
        Map<BlockPos, TreeRecord> trees = chunkTrees.get(chunkPos.toLong());
        if (trees == null) {
            return List.of();
        }
        return new ArrayList<>(trees.values());
    }

    public TreeRecord getTree(TreeKey key) {
        Map<BlockPos, TreeRecord> trees = chunkTrees.get(key.chunkPos().toLong());
        if (trees == null) {
            return null;
        }
        return trees.get(key.rootPos());
    }

    public boolean containsTree(ChunkPos chunkPos, BlockPos rootPos) {
        Map<BlockPos, TreeRecord> trees = chunkTrees.get(chunkPos.toLong());
        return trees != null && trees.containsKey(rootPos);
    }

    public void putTree(TreeRecord record) {
        chunkTrees.computeIfAbsent(record.key().chunkPos().toLong(), ignored -> new HashMap<>())
                .put(record.key().rootPos(), record);
        setDirty();
    }

    public void removeTree(TreeKey key) {
        Map<BlockPos, TreeRecord> trees = chunkTrees.get(key.chunkPos().toLong());
        if (trees == null) {
            return;
        }
        if (trees.remove(key.rootPos()) != null) {
            if (trees.isEmpty()) {
                chunkTrees.remove(key.chunkPos().toLong());
            }
            setDirty();
        }
    }
}
