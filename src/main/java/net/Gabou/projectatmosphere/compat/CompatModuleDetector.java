package net.Gabou.projectatmosphere.compat;

import net.neoforged.fml.ModList;

final class CompatModuleDetector {
    private CompatModuleDetector() {
    }

    static boolean isSandStormsLoaded() {
        return ModList.get().isLoaded("sandstorm");
    }

    static boolean isAurorasLoaded() {
        return ModList.get().isLoaded("auroras");
    }

    static boolean isRainbowsLoaded() {
        return ModList.get().isLoaded("rainbows");
    }

    static boolean isTectonicLoaded() {
        return ModList.get().isLoaded("tectonic");
    }

    static boolean isContinentsLoaded() {
        return ModList.get().isLoaded("continents");
    }

    static boolean isDynamicTreesLoaded() {
        return ModList.get().isLoaded("dynamictrees");
    }
}
