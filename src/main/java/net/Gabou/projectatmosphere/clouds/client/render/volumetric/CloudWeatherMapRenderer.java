package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.Gabou.projectatmosphere.client.render.shader.VolumetricCloudShaders;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import org.lwjgl.opengl.GL20;

import java.util.List;

/**
 * Rasterizes the interpolated cloud cells into the camera-following weather
 * map. The map is world-anchored and camera-snapped to its texel grid so
 * cloud silhouettes never swim as the camera moves.
 */
public final class CloudWeatherMapRenderer {
    public static final int MAX_CELLS = 96;
    public static final float WEATHER_EXTENT = 4096.0F;
    public static final float ADAPTIVE_TARGET_RADIUS_TEXELS = 2.25F;
    public static final float MAX_ADAPTIVE_FOOTPRINT_SCALE = 1.5F;

    private static final float[] posRadiusArray = new float[MAX_CELLS * 4];
    private static final float[] shapeArray = new float[MAX_CELLS * 4];
    private static final float[] mediaArray = new float[MAX_CELLS * 4];
    private static final float[] morphologyArray = new float[MAX_CELLS * 4];
    private static final float[] dynamicsArray = new float[MAX_CELLS * 4];

    private static double lastOriginX;
    private static double lastOriginZ;
    private static long lastInputSignature = Long.MIN_VALUE;
    private static int lastWeatherTextureId = -1;
    private static int lastMorphologyTextureId = -1;
    private static int lastStormStructureTextureId = -1;
    private static int lastStormLayerHeightTextureId = -1;
    private static int lastStormTowerTextureId = -1;
    private static Result lastResult = Result.EMPTY;
    private static long cacheHits;
    private static long cacheMisses;

    private CloudWeatherMapRenderer() {
    }

    public record Result(
            boolean rendered,
            double originX,
            double originZ,
            float slabBaseY,
            float slabTopY,
            int cellCount,
            float maxPrecipitation,
            FootprintStats footprintStats
    ) {
        public static final Result EMPTY = new Result(
                false,
                0.0D,
                0.0D,
                120.0F,
                320.0F,
                0,
                0.0F,
                FootprintStats.unknown()
        );
    }

    public record FootprintStats(
            boolean adaptiveEnabled,
            float targetRadiusTexels,
            float minAdaptiveScale,
            float averageAdaptiveScale,
            float maxAdaptiveScale,
            float minEffectiveRadiusTexels,
            float averageEffectiveRadiusTexels,
            float maxEffectiveRadiusTexels
    ) {
        private static FootprintStats unknown() {
            return new FootprintStats(
                    false,
                    ADAPTIVE_TARGET_RADIUS_TEXELS,
                    Float.NaN,
                    Float.NaN,
                    Float.NaN,
                    Float.NaN,
                    Float.NaN,
                    Float.NaN
            );
        }
    }

    public static double lastOriginX() {
        return lastOriginX;
    }

    public static double lastOriginZ() {
        return lastOriginZ;
    }

    public static String cacheStatus() {
        return "weatherMapCacheHits=" + cacheHits + " misses=" + cacheMisses;
    }

    public static void invalidateCache() {
        lastInputSignature = Long.MIN_VALUE;
        lastWeatherTextureId = -1;
        lastMorphologyTextureId = -1;
        lastStormStructureTextureId = -1;
        lastStormLayerHeightTextureId = -1;
        lastStormTowerTextureId = -1;
        lastResult = Result.EMPTY;
    }

    /**
     * Renders the weather map for this frame.
     *
     * @param cells interpolated render cells (already camera-filtered)
     * @param cameraX camera world X
     * @param cameraZ camera world Z
     * @param regionalCoverage 0..1 stratus/overcast layer amount
     * @param regionalEnergy 0..1 regional storminess
     * @param includeRegionalLayer whether the region-scale stratus sheet is
     *        splatted at all; spawned field clouds must not inherit the
     *        rain-coupled overcast layer, only autonomous/background rendering
     *        keeps it
     * @param worldTime world time (ticks with partial)
     * @param mapSize weather map resolution from the quality profile
     * @return per-frame weather map metadata for downstream passes
     */
    public static Result render(
            List<VolumetricRenderCell> cells,
            double cameraX,
            double cameraZ,
            float regionalCoverage,
            float regionalEnergy,
            boolean includeRegionalLayer,
            float worldTime,
            int mapSize
    ) {
        ShaderInstance shader = VolumetricCloudShaders.splatShader();
        ShaderInstance morphologyShader = VolumetricCloudShaders.morphologySplatShader();
        ShaderInstance stormStructureShader = VolumetricCloudShaders.stormStructureSplatShader();
        ShaderInstance stormHeightShader = VolumetricCloudShaders.stormHeightSplatShader();
        if (shader == null || morphologyShader == null
                || stormStructureShader == null || stormHeightShader == null) {
            return Result.EMPTY;
        }
        RenderTarget target = VolumetricCloudRenderTargets.prepareWeatherTarget(mapSize);
        RenderTarget morphologyTarget = VolumetricCloudRenderTargets.prepareMorphologyTarget(mapSize);
        RenderTarget stormStructureTarget = VolumetricCloudRenderTargets.prepareStormStructureTarget(mapSize);
        RenderTarget stormLayerHeightTarget =
                VolumetricCloudRenderTargets.prepareStormLayerHeightTarget(mapSize);
        RenderTarget stormTowerTarget = VolumetricCloudRenderTargets.prepareStormTowerTarget(mapSize);
        if (target == null || morphologyTarget == null
                || stormStructureTarget == null || stormLayerHeightTarget == null
                || stormTowerTarget == null) {
            return Result.EMPTY;
        }

        // Camera-snapped origin: the map scrolls in whole texels only.
        float texelSize = WEATHER_EXTENT / mapSize;
        double originX = Math.floor((cameraX - WEATHER_EXTENT * 0.5D) / texelSize) * texelSize;
        double originZ = Math.floor((cameraZ - WEATHER_EXTENT * 0.5D) / texelSize) * texelSize;
        lastOriginX = originX;
        lastOriginZ = originZ;

        // Slab bounds: envelope of all cell layers plus the regional sheet.
        float effectiveRegional = includeRegionalLayer ? Mth.clamp(regionalCoverage, 0.0F, 1.0F) : 0.0F;
        float slabBase = Float.MAX_VALUE;
        float slabTop = -Float.MAX_VALUE;
        float maxPrecipitation = 0.0F;
        int count = 0;
        for (VolumetricRenderCell cell : cells) {
            if (cell == null || count >= MAX_CELLS) {
                break;
            }
            slabBase = Math.min(slabBase, cell.baseY());
            slabTop = Math.max(slabTop, cell.topY());
            maxPrecipitation = Math.max(maxPrecipitation, cell.precipitationIntensity());
            count++;
        }
        if (effectiveRegional > 0.01F || count == 0) {
            slabBase = Math.min(slabBase, 150.0F);
            slabTop = Math.max(slabTop, 260.0F);
        }
        slabBase -= 6.0F;
        slabTop += 12.0F;
        float slabSpan = Math.max(slabTop - slabBase, 8.0F);

        boolean adaptiveFootprint = VolumetricCloudDebugConfig.adaptiveWeatherFootprintEnabled();
        float manualCoverageScale = VolumetricCloudDebugConfig.weatherCoverageScale();
        float minAdaptiveScale = Float.POSITIVE_INFINITY;
        float maxAdaptiveScale = Float.NEGATIVE_INFINITY;
        float totalAdaptiveScale = 0.0F;
        float minEffectiveRadiusTexels = Float.POSITIVE_INFINITY;
        float maxEffectiveRadiusTexels = Float.NEGATIVE_INFINITY;
        float totalEffectiveRadiusTexels = 0.0F;
        int footprintSamples = 0;
        boolean hasSevereStructures = false;

        count = 0;
        for (VolumetricRenderCell cell : cells) {
            if (cell == null || count >= MAX_CELLS) {
                break;
            }
            int base = count * 4;
            posRadiusArray[base] = (float) cell.x();
            posRadiusArray[base + 1] = (float) cell.z();
            posRadiusArray[base + 2] = cell.radiusMajor();
            posRadiusArray[base + 3] = cell.radiusMinor();
            shapeArray[base] = cell.orientationRadians();
            shapeArray[base + 1] = Mth.clamp((cell.baseY() - slabBase) / slabSpan, 0.0F, 1.0F);
            shapeArray[base + 2] = Mth.clamp((cell.topY() - slabBase) / slabSpan, 0.0F, 1.0F);
            shapeArray[base + 3] = cell.edgeSoftness();
            mediaArray[base] = cell.density();
            mediaArray[base + 1] = cell.energy();
            mediaArray[base + 2] = cell.seed01();
            float averageRadiusWorld = (cell.radiusMajor() + cell.radiusMinor()) * 0.5F;
            float projectedRadiusTexels = averageRadiusWorld / Math.max(texelSize, 0.001F);
            float adaptiveScale = adaptiveFootprintScale(projectedRadiusTexels, adaptiveFootprint);
            mediaArray[base + 3] = adaptiveScale;
            morphologyArray[base] = cell.cloudProfile();
            // Profile already identifies the shader family. Use the second
            // channel for the data-driven material darkness that was
            // previously dropped before reaching the GPU.
            morphologyArray[base + 1] = cell.materialDarkness();
            morphologyArray[base + 2] = cell.verticalDevelopment();
            morphologyArray[base + 3] = cell.humidity();
            dynamicsArray[base] = cell.anvilStrength();
            dynamicsArray[base + 1] = cell.precipitationIntensity();
            // Negative [-2,-1] values tag the field-level carrier while still
            // retaining the full [0,1] lifecycle value. The splat shader uses
            // that tag only to stabilize base/top; coverage remains unchanged.
            dynamicsArray[base + 2] = cell.macroCarrier()
                    ? -1.0F - cell.lifecycleStage()
                    : cell.lifecycleStage();
            // The primary splat shader does not receive CellMorphology. Pack
            // the integer profile plus a 1/16 role fraction in the formerly
            // reserved dynamics slot. Full-precision uniforms preserve both,
            // and this avoids another array/texture solely for envelope roles.
            dynamicsArray[base + 3] = cell.cloudProfile()
                    + cell.envelopeRole().gpuId() / 16.0F;
            if ((cell.cloudProfile() == 4 || cell.cloudProfile() == 7)
                    && cell.envelopeRole().gpuId() >= VolumetricRenderCell.EnvelopeRole.BASE.gpuId()
                    && cell.envelopeRole().gpuId() <= VolumetricRenderCell.EnvelopeRole.ANVIL.gpuId()) {
                hasSevereStructures = true;
            }
            if (Float.isFinite(projectedRadiusTexels)) {
                float effectiveRadiusTexels = projectedRadiusTexels * adaptiveScale * manualCoverageScale;
                minAdaptiveScale = Math.min(minAdaptiveScale, adaptiveScale);
                maxAdaptiveScale = Math.max(maxAdaptiveScale, adaptiveScale);
                totalAdaptiveScale += adaptiveScale;
                minEffectiveRadiusTexels = Math.min(minEffectiveRadiusTexels, effectiveRadiusTexels);
                maxEffectiveRadiusTexels = Math.max(maxEffectiveRadiusTexels, effectiveRadiusTexels);
                totalEffectiveRadiusTexels += effectiveRadiusTexels;
                footprintSamples++;
            }
            count++;
        }

        FootprintStats footprintStats = footprintSamples == 0
                ? FootprintStats.unknown()
                : new FootprintStats(
                        adaptiveFootprint,
                        ADAPTIVE_TARGET_RADIUS_TEXELS,
                        minAdaptiveScale,
                        totalAdaptiveScale / footprintSamples,
                        maxAdaptiveScale,
                        minEffectiveRadiusTexels,
                        totalEffectiveRadiusTexels / footprintSamples,
                        maxEffectiveRadiusTexels
                );

        long inputSignature = inputSignature(
                originX,
                originZ,
                slabBase,
                slabTop,
                count,
                effectiveRegional,
                regionalEnergy,
                includeRegionalLayer,
                worldTime,
                mapSize,
                manualCoverageScale,
                adaptiveFootprint,
                VolumetricCloudDebugConfig.sentinelHeightsEnabled()
        );
        int weatherTextureId = target.getColorTextureId();
        int morphologyTextureId = morphologyTarget.getColorTextureId();
        int stormStructureTextureId = stormStructureTarget.getColorTextureId();
        int stormLayerHeightTextureId = stormLayerHeightTarget.getColorTextureId();
        int stormTowerTextureId = stormTowerTarget.getColorTextureId();
        if (inputSignature == lastInputSignature
                && weatherTextureId == lastWeatherTextureId
                && morphologyTextureId == lastMorphologyTextureId
                && stormStructureTextureId == lastStormStructureTextureId
                && stormLayerHeightTextureId == lastStormLayerHeightTextureId
                && stormTowerTextureId == lastStormTowerTextureId
                && lastResult.rendered()) {
            cacheHits++;
            return lastResult;
        }
        cacheMisses++;

        VolumetricCloudRenderTargets.clearAndBind(target);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> shader);

        shader.safeGetUniform("WeatherOrigin").set((float) originX, (float) originZ);
        shader.safeGetUniform("WeatherExtent").set(WEATHER_EXTENT);
        shader.safeGetUniform("SlabBaseY").set(slabBase);
        shader.safeGetUniform("SlabTopY").set(slabTop);
        shader.safeGetUniform("RegionalCoverage").set(effectiveRegional);
        shader.safeGetUniform("RegionalEnergy").set(Mth.clamp(regionalEnergy, 0.0F, 1.0F));
        shader.safeGetUniform("WeatherCoverageScale").set(manualCoverageScale);
        shader.safeGetUniform("SentinelHeightsEnabled").set(
                VolumetricCloudDebugConfig.sentinelHeightsEnabled() ? 1 : 0
        );
        shader.safeGetUniform("WorldTime").set(worldTime);
        shader.safeGetUniform("CellCount").set(count);
        shader.apply();
        uploadCellArrays(shader, false);
        try {
            FullscreenQuad.draw(shader);
        } finally {
            shader.clear();
        }

        VolumetricCloudRenderTargets.clearAndBind(morphologyTarget);
        RenderSystem.setShader(() -> morphologyShader);
        morphologyShader.safeGetUniform("WeatherOrigin").set((float) originX, (float) originZ);
        morphologyShader.safeGetUniform("WeatherExtent").set(WEATHER_EXTENT);
        morphologyShader.safeGetUniform("RegionalCoverage").set(effectiveRegional);
        morphologyShader.safeGetUniform("RegionalEnergy").set(Mth.clamp(regionalEnergy, 0.0F, 1.0F));
        morphologyShader.safeGetUniform("WeatherCoverageScale").set(manualCoverageScale);
        morphologyShader.safeGetUniform("WorldTime").set(worldTime);
        morphologyShader.safeGetUniform("CellCount").set(count);
        morphologyShader.apply();
        uploadCellArrays(morphologyShader, true);
        try {
            FullscreenQuad.draw(morphologyShader);
        } finally {
            morphologyShader.clear();
        }

        // Preserve all four overlapping severe-cloud roles instead of the one
        // categorical winner stored by the morphology map. Empty/non-severe
        // scenes only clear the cached target and skip the third fullscreen
        // shader pass entirely.
        VolumetricCloudRenderTargets.clearAndBind(stormStructureTarget);
        if (hasSevereStructures) {
            RenderSystem.setShader(() -> stormStructureShader);
            stormStructureShader.safeGetUniform("WeatherOrigin").set((float) originX, (float) originZ);
            stormStructureShader.safeGetUniform("WeatherExtent").set(WEATHER_EXTENT);
            stormStructureShader.safeGetUniform("WeatherCoverageScale").set(manualCoverageScale);
            stormStructureShader.safeGetUniform("CellCount").set(count);
            stormStructureShader.apply();
            uploadCellArrays(stormStructureShader, false);
            try {
                FullscreenQuad.draw(stormStructureShader);
            } finally {
                stormStructureShader.clear();
            }
        }

        VolumetricCloudRenderTargets.clearAndBind(stormLayerHeightTarget);
        if (hasSevereStructures) {
            RenderSystem.setShader(() -> stormHeightShader);
            stormHeightShader.safeGetUniform("WeatherOrigin").set((float) originX, (float) originZ);
            stormHeightShader.safeGetUniform("WeatherExtent").set(WEATHER_EXTENT);
            stormHeightShader.safeGetUniform("WeatherCoverageScale").set(manualCoverageScale);
            stormHeightShader.safeGetUniform("CellCount").set(count);
            stormHeightShader.safeGetUniform("OutputMode").set(0);
            stormHeightShader.apply();
            uploadCellArrays(stormHeightShader, false);
            try {
                FullscreenQuad.draw(stormHeightShader);
            } finally {
                stormHeightShader.clear();
            }
        }

        // Reuse the endpoint shader in TOWER mode. CORE and TOWER used to
        // compete for the same argmax, discarding all secondary convection at
        // each texel before the raymarch. This target preserves an independent
        // support/base/top interval without duplicating the shader program.
        VolumetricCloudRenderTargets.clearAndBind(stormTowerTarget);
        if (hasSevereStructures) {
            RenderSystem.setShader(() -> stormHeightShader);
            stormHeightShader.safeGetUniform("WeatherOrigin").set((float) originX, (float) originZ);
            stormHeightShader.safeGetUniform("WeatherExtent").set(WEATHER_EXTENT);
            stormHeightShader.safeGetUniform("WeatherCoverageScale").set(manualCoverageScale);
            stormHeightShader.safeGetUniform("CellCount").set(count);
            stormHeightShader.safeGetUniform("OutputMode").set(1);
            stormHeightShader.apply();
            uploadCellArrays(stormHeightShader, false);
            try {
                FullscreenQuad.draw(stormHeightShader);
            } finally {
                stormHeightShader.clear();
            }
        }

        Result result = new Result(
                true,
                originX,
                originZ,
                slabBase,
                slabTop,
                count,
                maxPrecipitation,
                footprintStats
        );
        lastInputSignature = inputSignature;
        lastWeatherTextureId = weatherTextureId;
        lastMorphologyTextureId = morphologyTextureId;
        lastStormStructureTextureId = stormStructureTextureId;
        lastStormLayerHeightTextureId = stormLayerHeightTextureId;
        lastStormTowerTextureId = stormTowerTextureId;
        lastResult = result;
        return result;
    }

    static float adaptiveFootprintScale(float projectedRadiusTexels, boolean enabled) {
        if (!enabled || !Float.isFinite(projectedRadiusTexels)) {
            return 1.0F;
        }
        float safeRadius = Math.max(projectedRadiusTexels, 0.001F);
        return Mth.clamp(
                ADAPTIVE_TARGET_RADIUS_TEXELS / safeRadius,
                1.0F,
                MAX_ADAPTIVE_FOOTPRINT_SCALE
        );
    }

    /**
     * Uploads the per-cell arrays directly; the vanilla uniform system only
     * supports vec4-sized uniforms, so array uniforms go through raw GL.
     */
    private static void uploadCellArrays(ShaderInstance shader, boolean includeMorphology) {
        int program = shader.getId();
        uploadVec4Array(program, "CellPosRadius", posRadiusArray);
        uploadVec4Array(program, "CellShape", shapeArray);
        uploadVec4Array(program, "CellMedia", mediaArray);
        if (includeMorphology) {
            uploadVec4Array(program, "CellMorphology", morphologyArray);
        }
        uploadVec4Array(program, "CellDynamics", dynamicsArray);
    }

    private static void uploadVec4Array(int program, String name, float[] values) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location < 0) {
            location = GL20.glGetUniformLocation(program, name + "[0]");
        }
        if (location < 0) {
            return;
        }
        // The shader gates access with CellCount. Uploading the preallocated
        // backing array avoids five per-frame slice allocations.
        GL20.glUniform4fv(location, values);
    }

    private static long inputSignature(
            double originX,
            double originZ,
            float slabBase,
            float slabTop,
            int count,
            float regionalCoverage,
            float regionalEnergy,
            boolean includeRegionalLayer,
            float worldTime,
            int mapSize,
            float coverageScale,
            boolean adaptiveFootprint,
            boolean sentinelHeights
    ) {
        long hash = 0xcbf29ce484222325L;
        hash = mix(hash, quantize(originX, 1.0D));
        hash = mix(hash, quantize(originZ, 1.0D));
        hash = mix(hash, quantize(slabBase, 8.0D));
        hash = mix(hash, quantize(slabTop, 8.0D));
        hash = mix(hash, count);
        hash = mix(hash, mapSize);
        hash = mix(hash, quantize(regionalCoverage, 512.0D));
        hash = mix(hash, quantize(regionalEnergy, 512.0D));
        hash = mix(hash, quantize(coverageScale, 512.0D));
        hash = mix(hash, includeRegionalLayer ? 1L : 0L);
        hash = mix(hash, adaptiveFootprint ? 1L : 0L);
        hash = mix(hash, sentinelHeights ? 1L : 0L);
        if (includeRegionalLayer && regionalCoverage > 0.01F) {
            // The broad regional sheet is animated; two-tick cadence keeps it
            // moving while avoiding a full map rebuild every rendered frame.
            hash = mix(hash, (long) Math.floor(worldTime * 0.5F));
        }
        for (int i = 0; i < count * 4; i++) {
            double positionScale = (i & 3) < 2 ? 2.0D : 8.0D;
            hash = mix(hash, quantize(posRadiusArray[i], positionScale));
            hash = mix(hash, quantize(shapeArray[i], 1024.0D));
            hash = mix(hash, quantize(mediaArray[i], 1024.0D));
            hash = mix(hash, quantize(morphologyArray[i], 1024.0D));
            hash = mix(hash, quantize(dynamicsArray[i], 1024.0D));
        }
        return hash;
    }

    private static long quantize(double value, double scale) {
        if (!Double.isFinite(value)) {
            return 0L;
        }
        return Math.round(value * scale);
    }

    private static long mix(long hash, long value) {
        long mixed = hash ^ value;
        return mixed * 0x100000001b3L;
    }
}
