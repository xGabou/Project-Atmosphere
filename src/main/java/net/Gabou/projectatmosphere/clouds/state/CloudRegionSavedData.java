package net.Gabou.projectatmosphere.clouds.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/**
 * Données sauvegardées du monde pour les régions de nuage backend.
 * Cette classe gère seulement la persistance des nuages.
 */
final class CloudRegionSavedData extends SavedData {

    private static final String DATA_NAME = "projectatmosphere_cloud_regions";

    private final CloudRegionRegistry registry = new CloudRegionRegistry();

    public CloudRegionSavedData() {

    }

    public CloudRegionSavedData(@NotNull CompoundTag tag) {
        CloudRegionStorage.load(tag, registry);
    }

    /**
     * Récupère ou crée les données sauvegardées de régions de nuage pour un niveau serveur.
     *
     * @param level niveau serveur
     * @return données sauvegardées des régions de nuage
     */
    static @NotNull CloudRegionSavedData get(@NotNull ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                CloudRegionSavedData::new,
                CloudRegionSavedData::new,
                DATA_NAME
        );
    }

    /**
     * Retourne le registre backend vivant.
     *
     * @return registre des régions de nuage
     */
    @NotNull CloudRegionRegistry getRegistry() {
        return registry;
    }

    /**
     * Marque les données comme modifiées.
     */
    void markChanged() {
        setDirty();
    }

    /**
     * Sauvegarde le registre dans un tag NBT.
     *
     * @param tag tag racine
     * @return tag racine sauvegardé
     */
    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        tag.merge(CloudRegionStorage.save(registry));
        return tag;
    }
}