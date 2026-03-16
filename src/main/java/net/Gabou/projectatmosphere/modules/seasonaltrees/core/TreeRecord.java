package net.Gabou.projectatmosphere.modules.seasonaltrees.core;

import net.minecraft.nbt.CompoundTag;

public final class TreeRecord {
    private static final String TAG_KEY = "key";
    private static final String TAG_STATE = "state";

    private final TreeKey key;
    private final TreeState state;

    public TreeRecord(TreeKey key, TreeState state) {
        this.key = key;
        this.state = state;
    }

    public TreeKey key() {
        return key;
    }

    public TreeState state() {
        return state;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.put(TAG_KEY, key.toTag());
        tag.put(TAG_STATE, state.toTag());
        return tag;
    }

    public static TreeRecord fromTag(CompoundTag tag) {
        TreeKey key = TreeKey.fromTag(tag.getCompound(TAG_KEY));
        TreeState state = TreeState.fromTag(tag.getCompound(TAG_STATE));
        return new TreeRecord(key, state);
    }
}
