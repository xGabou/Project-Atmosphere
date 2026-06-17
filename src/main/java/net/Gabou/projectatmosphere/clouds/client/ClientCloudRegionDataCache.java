package net.Gabou.projectatmosphere.clouds.client;

import net.Gabou.projectatmosphere.clouds.transport.CloudRegionRenderData;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Cache client des données de régions de nuage reçues du serveur.
 * Cette classe stocke seulement les données transportables.
 * Elle ne crée pas de snapshot de rendu et ne fait aucun rendu.
 */
public final class ClientCloudRegionDataCache {

    private static volatile List<CloudRegionRenderData> currentRegions = List.of();

    private ClientCloudRegionDataCache() {

    }

    public static @NotNull List<CloudRegionRenderData> getCurrentRegions() {
        return currentRegions;
    }

    public static void setCurrentRegions(Collection<CloudRegionRenderData> regions) {
        currentRegions = regions != null ? List.copyOf(regions) : List.of();
    }

    public static void clear() {
        currentRegions = List.of();
    }

    public static boolean hasRegions() {
        return !currentRegions.isEmpty();
    }
}