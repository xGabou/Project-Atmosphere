package net.Gabou.projectatmosphere.clouds.state;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/**
 * Saved world data for native Project Atmosphere cloud regions.
 */
final class CloudRegionSavedData extends SavedData {
    private static final String DATA_NAME = "projectatmosphere_cloud_regions";

    private final CloudRegionRegistry registry = new CloudRegionRegistry();

    public CloudRegionSavedData() {
    }

    public CloudRegionSavedData(@NotNull CompoundTag tag) {
        CloudRegionStorage.load(tag, registry);
    }

    static @NotNull CloudRegionSavedData get(@NotNull ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        CloudRegionSavedData::new,
                        (tag, provider) -> new CloudRegionSavedData(tag)
                ),
                DATA_NAME
        );
    }

    @NotNull CloudRegionRegistry getRegistry() {
        return registry;
    }

    void markChanged() {
        setDirty();
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        tag.merge(CloudRegionStorage.save(registry));
        return tag;
    }
}
