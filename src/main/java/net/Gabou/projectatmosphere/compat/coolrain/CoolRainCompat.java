package net.Gabou.projectatmosphere.compat.coolrain;

import net.neoforged.fml.ModList;

public final class CoolRainCompat {
    private static final String MOD_ID = "coolrain";

    private static Boolean loaded;

    private CoolRainCompat() {
    }

    public static boolean isLoaded() {
        Boolean cached = loaded;
        if (cached != null) {
            return cached;
        }
        loaded = ModList.get().isLoaded(MOD_ID);
        return loaded;
    }
}
