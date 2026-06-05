package net.Gabou.projectatmosphere.clouds.backend;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;

public final class CloudRegionStorage {

    private static final String TAG_REGIONS = "CloudRegions";

    private CloudRegionStorage() {

    }

    /**
     * Sauvegarde toutes les régions de nuage du registre dans un tag racine.
     *
     * @param registry registre de régions de nuage à sauvegarder
     * @return tag racine sérialisé
     */
    public static @NotNull CompoundTag save(@NotNull CloudRegionRegistry registry) {
        CompoundTag root = new CompoundTag();
        ListTag regions = new ListTag();

        for (CloudRegionState state : registry.getAll()) {
            if (state != null) {
                regions.add(state.save());
            }
        }

        root.put(TAG_REGIONS, regions);
        return root;
    }

    /**
     * Charge les régions de nuage dans le registre cible.
     *
     * @param root tag racine sérialisé
     * @param targetRegistry registre à remplir
     */
    public static void load(
            @NotNull CompoundTag root,
            @NotNull CloudRegionRegistry targetRegistry
    ) {
        targetRegistry.clear();

        if (!root.contains(TAG_REGIONS, Tag.TAG_LIST)) {
            return;
        }

        ListTag regions = root.getList(TAG_REGIONS, Tag.TAG_COMPOUND);

        for (int i = 0; i < regions.size(); i++) {
            CompoundTag regionTag = regions.getCompound(i);
            CloudRegionState state = CloudRegionState.load(regionTag);
            targetRegistry.add(state);
        }
    }
}