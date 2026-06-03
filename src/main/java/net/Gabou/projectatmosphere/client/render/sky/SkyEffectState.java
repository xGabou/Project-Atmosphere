package net.Gabou.projectatmosphere.client.render.sky;

import net.minecraft.world.phys.Vec3;

/**
 * Lightweight per-frame flags for sky effects so shaders (e.g., Oculus) can query them.
 */
public final class SkyEffectState {
    private static boolean auroraActive;
    private static Vec3 auroraPos = Vec3.ZERO;
    private static boolean rainbowActive;
    private static Vec3 rainbowPos = Vec3.ZERO;

    private SkyEffectState() {
    }

    public static void beginFrame() {
        auroraActive = false;
        rainbowActive = false;
        auroraPos = Vec3.ZERO;
        rainbowPos = Vec3.ZERO;
    }

    public static void setAurora(boolean active, Vec3 pos) {
        auroraActive = active;
        if (pos != null) {
            auroraPos = pos;
        }
    }

    public static void setRainbow(boolean active, Vec3 pos) {
        rainbowActive = active;
        if (pos != null) {
            rainbowPos = pos;
        }
    }

    public static boolean isAuroraActive() {
        return auroraActive;
    }

    public static boolean isRainbowActive() {
        return rainbowActive;
    }

    public static Vec3 getAuroraPos() {
        return auroraPos;
    }

    public static Vec3 getRainbowPos() {
        return rainbowPos;
    }
}
