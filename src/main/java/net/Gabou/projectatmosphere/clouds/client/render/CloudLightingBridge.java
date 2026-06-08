package net.Gabou.projectatmosphere.clouds.client.render;

import net.Gabou.projectatmosphere.clouds.client.CloudRenderFrameContext;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderSnapshot;
import net.Gabou.projectatmosphere.compat.sky.AtmosphereSkySample;
import net.Gabou.projectatmosphere.compat.sky.AtmosphereSkySampler;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

/**
 * Fournit les donnees de lumiere minimales pour le rendu des nuages.
 * Cette couche reste volontairement legere pour le premier shader.
 */
public final class CloudLightingBridge {
    private static final float SUNSET_START_TICK = 11501.0F;
    private static final float SUNSET_END_TICK = 14001.0F;
    private static final float SUNRISE_START_TICK = 21911.0F;
    private static final float SUNRISE_END_TICK = 24250.0F;
    private static final Vector3f DEEP_RED_SUN = new Vector3f(1.85F, 0.14F, 0.07F);
    private static final Vector3f ORANGE_SUN = new Vector3f(1.65F, 0.46F, 0.14F);
    private static final Vector3f GOLD_SUN = new Vector3f(1.28F, 0.82F, 0.38F);
    private static final Vector3f NIGHT_AMBIENT = new Vector3f(0.08F, 0.10F, 0.16F);
    private static final Vector3f DAY_AMBIENT = new Vector3f(0.92F, 0.94F, 0.96F);

    private CloudLightingBridge() {

    }

    /**
     * Resout la couleur de nuage appliquee au shader.
     *
     * @param snapshot snapshot frontend courant
     * @param frameContext contexte de frame courant
     * @return couleur RGBA du nuage
     */
    public static float[] resolveCloudColor(@NotNull CloudRenderSnapshot snapshot, @NotNull CloudRenderFrameContext frameContext) {
        Vector3f ambientColor = resolveAmbientCloudColor(snapshot, frameContext);
        return new float[]{
                clamp01(ambientColor.x),
                clamp01(ambientColor.y),
                clamp01(ambientColor.z),
                1.0F
        };
    }

    /**
     * Resout la lumiere ambiante de base du volume de nuage.
     *
     * @param snapshot snapshot frontend courant
     * @param frameContext contexte de frame courant
     * @return couleur ambiante du nuage
     */
    public static Vector3f resolveAmbientCloudColor(@NotNull CloudRenderSnapshot snapshot, @NotNull CloudRenderFrameContext frameContext) {
        int color = snapshot.getDebugColorOrTint();
        float red = ((color >> 16) & 255) / 255.0F;
        float green = ((color >> 8) & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        float lifecycleFactor = CloudDensityProvider.getLifecycleFactor(snapshot);
        AtmosphereSkySample skySample = resolveSkySample(frameContext);
        float daylightFactor = skySample.daylightFactor();
        float atmosphericClarity = skySample.atmosphericClarity();
        float rainIntensity = skySample.rainIntensity();
        float sunsetStrength = resolveSunsetStrength(frameContext);
        float nightFactor = skySample.nightFactor();

        float brightness = 0.18F + daylightFactor * 0.66F + atmosphericClarity * 0.08F;
        brightness *= 1.0F - rainIntensity * 0.12F;
        brightness *= 0.92F + lifecycleFactor * 0.08F;
        brightness *= 1.0F - sunsetStrength * 0.26F;

        Vector3f resolvedColor = new Vector3f(red, green, blue).mul(brightness);
        Vector3f skyTint = sampleSkyTint(frameContext);
        Vector3f dayAmbient = mix(DAY_AMBIENT, skyTint, clamp01(0.20F + daylightFactor * 0.35F));
        resolvedColor = mix(resolvedColor, dayAmbient, clamp01(0.18F + daylightFactor * 0.22F));
        resolvedColor = mix(resolvedColor, NIGHT_AMBIENT, clamp01(nightFactor * 0.55F));

        return resolvedColor;
    }

    /**
     * Resout la couleur du soleil utilisee pour les bords et le glow horizon.
     *
     * @param snapshot snapshot frontend courant
     * @param frameContext contexte de frame courant
     * @return couleur solaire du nuage
     */
    public static Vector3f resolveSunColor(@NotNull CloudRenderSnapshot snapshot, @NotNull CloudRenderFrameContext frameContext) {
        SunPhase phase = resolveSunPhase(frameContext);
        float[] vanillaSunriseColor = sampleSunriseTint(frameContext);
        Vector3f vanillaColor = vanillaSunriseColor == null
                ? new Vector3f(1.0F, 0.92F, 0.80F)
                : new Vector3f(vanillaSunriseColor[0], vanillaSunriseColor[1], vanillaSunriseColor[2]);

        Vector3f redToOrange = mix(ORANGE_SUN, DEEP_RED_SUN, phase.redPeak);
        Vector3f orangeToGold = mix(redToOrange, GOLD_SUN, phase.goldPhase);
        Vector3f skyDriven = mix(orangeToGold, vanillaColor, clamp01(0.24F + phase.vanillaAlpha * 0.46F));

        return mix(new Vector3f(1.0F, 0.96F, 0.86F), skyDriven, phase.window);
    }

    /**
     * Resout la direction approximative des rayons solaires.
     *
     * @param frameContext contexte de frame courant
     * @return direction normalisee du soleil vers le monde
     */
    public static Vector3f resolveSunDirection(@NotNull CloudRenderFrameContext frameContext) {
        float dayTime = getDayTime(frameContext);
        float angle = dayTime / 24000.0F * (float) (Math.PI * 2.0D);
        float x = Mth.cos(angle);
        float y = Math.max(Mth.sin(angle), 0.05F);
        return new Vector3f(x, y, 0.18F).normalize();
    }

    /**
     * Resout la force rouge disponible au lever ou au coucher.
     *
     * @param frameContext contexte de frame courant
     * @return force de teinte sunset
     */
    public static float resolveSunsetStrength(@NotNull CloudRenderFrameContext frameContext) {
        return resolveSunPhase(frameContext).window;
    }

    /**
     * Resout la force du glow proche de l'horizon.
     *
     * @param frameContext contexte de frame courant
     * @return force du glow horizon
     */
    public static float resolveHorizonGlowStrength(@NotNull CloudRenderFrameContext frameContext) {
        SunPhase phase = resolveSunPhase(frameContext);
        return clamp01(0.20F + phase.window * 1.05F + phase.goldPhase * 0.25F);
    }

    /**
     * Resout la force du rim light sur les bords minces.
     *
     * @param frameContext contexte de frame courant
     * @return force de lumiere de bord
     */
    public static float resolveEdgeLightStrength(@NotNull CloudRenderFrameContext frameContext) {
        SunPhase phase = resolveSunPhase(frameContext);
        return clamp01(0.28F + phase.redPeak * 1.20F + phase.goldPhase * 0.55F);
    }

    /**
     * Resout l'assombrissement des surfaces basses du nuage.
     *
     * @param frameContext contexte de frame courant
     * @return facteur d'assombrissement du dessous
     */
    public static float resolveUndersideDarkening(@NotNull CloudRenderFrameContext frameContext) {
        AtmosphereSkySample skySample = resolveSkySample(frameContext);
        SunPhase phase = resolveSunPhase(frameContext);
        return clamp01(0.30F + skySample.nightFactor() * 0.32F + phase.window * 0.34F);
    }

    /**
     * Resout l'absorption interne de la lumiere dans les zones denses.
     *
     * @param snapshot snapshot frontend courant
     * @param frameContext contexte de frame courant
     * @return facteur d'absorption de lumiere
     */
    public static float resolveLightAbsorption(@NotNull CloudRenderSnapshot snapshot, @NotNull CloudRenderFrameContext frameContext) {
        SunPhase phase = resolveSunPhase(frameContext);
        float visibleDensity = CloudDensityProvider.getEffectiveDensity(snapshot) * CloudDensityProvider.getEffectiveCoverage(snapshot);
        return Mth.clamp(1.20F + visibleDensity * 1.55F + phase.window * 1.20F, 1.20F, 4.00F);
    }

    /**
     * Retourne la couleur de fog courante du moteur.
     *
     * @return couleur fog RGBA
     */
    public static float[] resolveFogColor() {
        return RenderSystem.getShaderFogColor();
    }

    private static float getDayTime(@NotNull CloudRenderFrameContext frameContext) {
        return (float) Math.floorMod(frameContext.getWorldTime(), 24000L);
    }

    private static AtmosphereSkySample resolveSkySample(@NotNull CloudRenderFrameContext frameContext) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return AtmosphereSkySample.NONE;
        }
        return AtmosphereSkySampler.sample(minecraft, frameContext.getPartialTick());
    }

    private static Vector3f sampleSkyTint(@NotNull CloudRenderFrameContext frameContext) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return new Vector3f(1.0F, 1.0F, 1.0F);
        }

        Vec3 skyColor = level.getSkyColor(frameContext.getCameraPosition(), frameContext.getPartialTick());
        return new Vector3f((float) skyColor.x(), (float) skyColor.y(), (float) skyColor.z());
    }

    private static float[] sampleSunriseTint(@NotNull CloudRenderFrameContext frameContext) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return null;
        }

        DimensionSpecialEffects effects = level.effects();
        return effects.getSunriseColor(level.getTimeOfDay(frameContext.getPartialTick()), frameContext.getPartialTick());
    }

    private static SunPhase resolveSunPhase(@NotNull CloudRenderFrameContext frameContext) {
        float dayTime = getDayTime(frameContext);
        float sunsetWindow = peakedWindow(dayTime, SUNSET_START_TICK, 12500.0F, SUNSET_END_TICK);
        float sunsetRed = peakedWindow(dayTime, 11750.0F, 12350.0F, 13050.0F);
        float sunsetGold = smoothWindow(dayTime, 12350.0F, SUNSET_END_TICK);

        float adjustedSunriseTime = dayTime < SUNRISE_START_TICK ? dayTime + 24000.0F : dayTime;
        float sunriseWindow = peakedWindow(adjustedSunriseTime, SUNRISE_START_TICK, 23200.0F, SUNRISE_END_TICK);
        float sunriseRed = peakedWindow(adjustedSunriseTime, 22250.0F, 23050.0F, 23650.0F);
        float sunriseGold = smoothWindow(adjustedSunriseTime, 23250.0F, SUNRISE_END_TICK);

        float[] vanillaSunriseColor = sampleSunriseTint(frameContext);
        float vanillaAlpha = vanillaSunriseColor == null ? 0.0F : vanillaSunriseColor[3];
        return new SunPhase(
                clamp01(Math.max(sunsetWindow, sunriseWindow)),
                clamp01(Math.max(sunsetRed, sunriseRed)),
                clamp01(Math.max(sunsetGold, sunriseGold)),
                clamp01(vanillaAlpha)
        );
    }

    private static float peakedWindow(float value, float start, float peak, float end) {
        if (value < start || value > end) {
            return 0.0F;
        }

        float rising = smoothstep(start, peak, value);
        float falling = 1.0F - smoothstep(peak, end, value);
        return clamp01(rising * falling);
    }

    private static float smoothWindow(float value, float start, float end) {
        if (value < start || value > end) {
            return 0.0F;
        }
        return smoothstep(start, end, value);
    }

    private static Vector3f mix(Vector3f first, Vector3f second, float factor) {
        float inverse = 1.0F - clamp01(factor);
        float blend = clamp01(factor);
        return new Vector3f(
                first.x * inverse + second.x * blend,
                first.y * inverse + second.y * blend,
                first.z * inverse + second.z * blend
        );
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        if (edge0 == edge1) {
            return value < edge0 ? 0.0F : 1.0F;
        }

        float t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3.0F - 2.0F * t);
    }

    private static float clamp01(float value) {
        return Mth.clamp(value, 0.0F, 1.0F);
    }

    private record SunPhase(float window, float redPeak, float goldPhase, float vanillaAlpha) {

    }
}
