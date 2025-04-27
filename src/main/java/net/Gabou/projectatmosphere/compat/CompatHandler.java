package net.Gabou.projectatmosphere.compat;

import net.minecraftforge.fml.ModList;

public class CompatHandler {
    public static final boolean SERENE_SEASONS_LOADED = ModList.get().isLoaded("sereneseasons");

    public static boolean isSereneSeasonsAvailable() {
        return SERENE_SEASONS_LOADED;
    }
}
