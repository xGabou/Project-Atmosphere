package net.Gabou.projectatmosphere.compat.auroras;

import net.Gabou.projectatmosphere.compat.sky.AtmosphereSkyEffectController;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class AuroraCompatController {
    private AuroraCompatController() {
    }

    public static void setEnabled(boolean value) {
        AtmosphereSkyEffectController.setAuroraEnabled(value);
    }

    public static float scaleBrightness(float vanillaBrightness) {
        return AtmosphereSkyEffectController.scaleAuroraBrightness(vanillaBrightness);
    }

    public static float overrideRainLevel(float vanillaRainLevel) {
        return AtmosphereSkyEffectController.getRainLevelOverride(vanillaRainLevel);
    }

    public static boolean isActive() {
        return AtmosphereSkyEffectController.isAuroraActive();
    }
}
