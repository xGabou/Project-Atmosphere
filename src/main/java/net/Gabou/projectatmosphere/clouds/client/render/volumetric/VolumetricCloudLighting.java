package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import net.Gabou.projectatmosphere.client.atmosphere.AtmosphereClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Resolves the per-frame lighting inputs for the volumetric cloud raymarch:
 * real sun/moon direction from world time, sun color with sunset/sunrise
 * phases, and sky-driven ambient colors. Because the light march attenuates
 * along the actual sun direction, low sun angles naturally light cloud
 * undersides in warm colors while tops stay neutral.
 */
public final class VolumetricCloudLighting {
    private static final Vector3f DEEP_RED_SUN = new Vector3f(1.85F, 0.24F, 0.10F);
    private static final Vector3f ORANGE_SUN = new Vector3f(1.65F, 0.52F, 0.18F);
    private static final Vector3f GOLD_SUN = new Vector3f(1.32F, 0.88F, 0.44F);
    private static final Vector3f DAY_SUN = new Vector3f(1.00F, 0.97F, 0.90F);
    private static final Vector3f MOON_LIGHT = new Vector3f(0.14F, 0.17F, 0.24F);
    private static final Vector3f NIGHT_AMBIENT = new Vector3f(0.045F, 0.06F, 0.10F);

    private VolumetricCloudLighting() {
    }

    /**
     * Immutable lighting inputs for one frame.
     */
    public record Frame(
            Vector3f lightDirection,
            Vector3f lightColor,
            Vector3f ambientTop,
            Vector3f ambientBottom,
            float sunsetStrength,
            float nightFactor,
            float stormDarkening
    ) {
    }

    public static Frame resolve(ClientLevel level, Vec3 cameraPosition, float partialTick) {
        float timeOfDay = level.getTimeOfDay(partialTick);
        // ClientLevel#getTimeOfDay already uses Minecraft's celestial phase:
        // zero is midday and 0.5 is midnight. Subtracting another quarter
        // turn made a bright daytime sky select the moon-light branch.
        float celestial = timeOfDay * ((float) Math.PI * 2.0F);
        Vector3f sunDirection = new Vector3f(
                -Mth.sin(celestial),
                Mth.cos(celestial),
                0.12F
        ).normalize();

        boolean sunUp = sunDirection.y > -0.06F;
        Vector3f lightDirection = sunUp
                ? new Vector3f(sunDirection)
                : new Vector3f(-sunDirection.x, -sunDirection.y, sunDirection.z).normalize();

        float dayTime = (float) Math.floorMod(level.getDayTime(), 24000L);
        SunPhase phase = resolveSunPhase(level, dayTime, partialTick);

        Vector3f sunColor = resolveSunColor(level, phase, partialTick);
        float nightFactor = Mth.clamp(level.getStarBrightness(partialTick) * 2.0F, 0.0F, 1.0F);
        Vector3f lightColor = sunUp
                ? sunColor
                : mix(new Vector3f(MOON_LIGHT), sunColor, Math.max(0.0F, 1.0F - nightFactor) * 0.4F);

        Vec3 skyColorVec = level.getSkyColor(cameraPosition, partialTick);
        Vector3f skyColor = new Vector3f((float) skyColorVec.x(), (float) skyColorVec.y(), (float) skyColorVec.z());

        float daylight = Mth.clamp(sunDirection.y * 3.0F, 0.0F, 1.0F);
        Vector3f ambientTop = mix(new Vector3f(NIGHT_AMBIENT), maxEach(skyColor, new Vector3f(0.30F, 0.36F, 0.46F)), daylight);
        ambientTop = mix(ambientTop, new Vector3f(ORANGE_SUN).mul(0.42F), phase.window * 0.35F);
        Vector3f groundTone = mix(new Vector3f(NIGHT_AMBIENT).mul(0.8F), new Vector3f(0.32F, 0.33F, 0.34F), daylight);
        Vector3f ambientBottom = mix(groundTone, new Vector3f(skyColor).mul(0.55F), 0.35F);
        ambientBottom = mix(ambientBottom, new Vector3f(DEEP_RED_SUN).mul(0.30F), phase.window * 0.30F);

        AtmosphereClientState.Snapshot atmosphere = AtmosphereClientState.getSnapshot();
        float stormDarkening = Mth.clamp(
                atmosphere.rainIntensity() * 0.45F + level.getRainLevel(partialTick) * 0.30F,
                0.0F, 0.6F
        );

        return new Frame(
                lightDirection,
                lightColor,
                ambientTop,
                ambientBottom,
                phase.window,
                nightFactor,
                stormDarkening
        );
    }

    private static Vector3f resolveSunColor(ClientLevel level, SunPhase phase, float partialTick) {
        Vector3f vanillaColor = new Vector3f(1.0F, 0.92F, 0.80F);
        DimensionSpecialEffects effects = level.effects();
        float[] sunrise = effects.getSunriseColor(level.getTimeOfDay(partialTick), partialTick);
        if (sunrise != null) {
            vanillaColor.set(sunrise[0], sunrise[1], sunrise[2]);
        }

        Vector3f redToOrange = mix(new Vector3f(ORANGE_SUN), new Vector3f(DEEP_RED_SUN), phase.redPeak);
        Vector3f orangeToGold = mix(redToOrange, new Vector3f(GOLD_SUN), phase.goldPhase);
        Vector3f skyDriven = mix(orangeToGold, vanillaColor, Mth.clamp(0.24F + phase.vanillaAlpha * 0.46F, 0.0F, 1.0F));
        return mix(new Vector3f(DAY_SUN), skyDriven, phase.window);
    }

    private static SunPhase resolveSunPhase(ClientLevel level, float dayTime, float partialTick) {
        float sunsetWindow = peakedWindow(dayTime, 11501.0F, 12500.0F, 14001.0F);
        float sunsetRed = peakedWindow(dayTime, 11750.0F, 12350.0F, 13050.0F);
        float sunsetGold = smoothWindow(dayTime, 12350.0F, 14001.0F);

        float adjustedSunriseTime = dayTime < 21911.0F ? dayTime + 24000.0F : dayTime;
        float sunriseWindow = peakedWindow(adjustedSunriseTime, 21911.0F, 23200.0F, 24250.0F);
        float sunriseRed = peakedWindow(adjustedSunriseTime, 22250.0F, 23050.0F, 23650.0F);
        float sunriseGold = smoothWindow(adjustedSunriseTime, 23250.0F, 24250.0F);

        float vanillaAlpha = 0.0F;
        float[] sunrise = level.effects().getSunriseColor(level.getTimeOfDay(partialTick), partialTick);
        if (sunrise != null) {
            vanillaAlpha = Mth.clamp(sunrise[3], 0.0F, 1.0F);
        }
        return new SunPhase(
                Mth.clamp(Math.max(sunsetWindow, sunriseWindow), 0.0F, 1.0F),
                Mth.clamp(Math.max(sunsetRed, sunriseRed), 0.0F, 1.0F),
                Mth.clamp(Math.max(sunsetGold, sunriseGold), 0.0F, 1.0F),
                vanillaAlpha
        );
    }

    private static float peakedWindow(float value, float start, float peak, float end) {
        if (value < start || value > end) {
            return 0.0F;
        }
        float rising = smoothstep(start, peak, value);
        float falling = 1.0F - smoothstep(peak, end, value);
        return Mth.clamp(rising * falling, 0.0F, 1.0F);
    }

    private static float smoothWindow(float value, float start, float end) {
        if (value < start || value > end) {
            return 0.0F;
        }
        return smoothstep(start, end, value);
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        if (edge0 == edge1) {
            return value < edge0 ? 0.0F : 1.0F;
        }
        float t = Mth.clamp((value - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static Vector3f mix(Vector3f first, Vector3f second, float factor) {
        float blend = Mth.clamp(factor, 0.0F, 1.0F);
        return new Vector3f(
                first.x + (second.x - first.x) * blend,
                first.y + (second.y - first.y) * blend,
                first.z + (second.z - first.z) * blend
        );
    }

    private static Vector3f maxEach(Vector3f first, Vector3f second) {
        return new Vector3f(
                Math.max(first.x, second.x),
                Math.max(first.y, second.y),
                Math.max(first.z, second.z)
        );
    }

    private record SunPhase(float window, float redPeak, float goldPhase, float vanillaAlpha) {
    }
}
