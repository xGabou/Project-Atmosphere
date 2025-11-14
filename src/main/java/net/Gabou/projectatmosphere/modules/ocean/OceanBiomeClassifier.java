package net.Gabou.projectatmosphere.modules.ocean;

import net.Gabou.projectatmosphere.compat.CompatHandler;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class OceanBiomeClassifier {
    private static final Set<String> BASE_KEYWORDS = Set.of(
            "ocean",
            "sea",
            "abyss",
            "lagoon",
            "gulf",
            "sound",
            "bay"
    );

    private static final Set<String> MOD_KEYWORDS = new HashSet<>();

    private OceanBiomeClassifier() {
    }

    static {
        if (CompatHandler.isTectonicLoaded() || CompatHandler.isContinentsLoaded()) {
            MOD_KEYWORDS.add("shelf");
            MOD_KEYWORDS.add("trench");
            MOD_KEYWORDS.add("shoal");
            MOD_KEYWORDS.add("pelagic");
        }
    }

    static boolean isOcean(ResourceLocation biomeId) {
        if (biomeId == null) {
            return false;
        }
        String path = biomeId.getPath().toLowerCase(Locale.ROOT);
        if (path.contains("river") || path.contains("lake") || path.contains("swamp")) {
            return false;
        }
        for (String keyword : BASE_KEYWORDS) {
            if (path.contains(keyword)) {
                return true;
            }
        }
        for (String keyword : MOD_KEYWORDS) {
            if (path.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
