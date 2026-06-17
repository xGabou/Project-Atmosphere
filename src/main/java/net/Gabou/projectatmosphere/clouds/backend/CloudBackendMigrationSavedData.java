package net.Gabou.projectatmosphere.clouds.backend;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

public final class CloudBackendMigrationSavedData extends SavedData {
    private static final String DATA_NAME = "projectatmosphere_cloud_backend_migration";

    private final CloudBackendMigrationState state;

    public CloudBackendMigrationSavedData() {
        this.state = new CloudBackendMigrationState();
    }

    public CloudBackendMigrationSavedData(@NotNull CompoundTag tag) {
        this.state = CloudBackendMigrationState.load(tag);
    }

    public static @NotNull CloudBackendMigrationSavedData get(@NotNull ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        CloudBackendMigrationSavedData::new,
                        (tag, provider) -> new CloudBackendMigrationSavedData(tag)
                ),
                DATA_NAME
        );
    }

    public @NotNull CloudBackendMigrationState state() {
        return state;
    }

    public void markChanged() {
        setDirty();
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        tag.merge(state.save());
        return tag;
    }
}
