package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.Gabou.projectatmosphere.client.render.shader.VolumetricCloudShaders;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import org.lwjgl.opengl.GL20;

import java.util.Arrays;
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
    private static long lastDomainSignature = Long.MIN_VALUE;
    private static long lastPositionSignature = Long.MIN_VALUE;
    private static long lastShapeSignature = Long.MIN_VALUE;
    private static long lastMediaSignature = Long.MIN_VALUE;
    private static long lastMorphologySignature = Long.MIN_VALUE;
    private static long lastDynamicsSignature = Long.MIN_VALUE;
    private static long domainSignatureChanges;
    private static final long[] positionSlotChanges = new long[4];
    private static final long[] shapeSlotChanges = new long[4];
    private static final long[] mediaSlotChanges = new long[4];
    private static final long[] morphologySlotChanges = new long[4];
    private static final long[] dynamicsSlotChanges = new long[4];
    private static final long[] previousPositionValues = new long[MAX_CELLS * 4];
    private static final long[] previousShapeValues = new long[MAX_CELLS * 4];
    private static final long[] previousMediaValues = new long[MAX_CELLS * 4];
    private static final long[] previousMorphologyValues = new long[MAX_CELLS * 4];
    private static final long[] previousDynamicsValues = new long[MAX_CELLS * 4];
    private static int previousBreakdownCount = -1;
    private static int lastWeatherTextureId = -1;
    private static int lastMorphologyTextureId = -1;
    private static int lastCumulusStageSupportTextureId = -1;
    private static int lastCumulusStageBaseTextureId = -1;
    private static int lastCumulusStageTopTextureId = -1;
    private static int lastStormStructureTextureId = -1;
    private static int lastStormLayerHeightTextureId = -1;
    private static int lastStormTowerTextureId = -1;
    private static int lastPuffCandidateTextureId = -1;
    // Diagnostic-only: whether the last weather-map bake found any cell whose
    // envelopeRole fell in the structured BASE..ANVIL range. This is the
    // authoritative gate for the storm-structure/height/tower passes (see
    // hasSevereStructures below); false means those three GPU passes were
    // skipped entirely and stormStructureShape() cannot contribute in the
    // raymarch, regardless of what the shader's DebugView modes would show.
    private static boolean lastHasSevereStructures;
    private static boolean lastHasStructuredCumulus;
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
        return "weatherMapCacheHits=" + cacheHits + " misses=" + cacheMisses
                + " inputSignature=" + hex(lastInputSignature)
                + " hasSevereStructures=" + lastHasSevereStructures
                + " hasStructuredCumulus=" + lastHasStructuredCumulus;
    }

    public static String diagnosticSignatureStatus() {
        return cacheStatus()
                + " components[domain=" + hex(lastDomainSignature)
                + " pos=" + hex(lastPositionSignature)
                + " shape=" + hex(lastShapeSignature)
                + " media=" + hex(lastMediaSignature)
                + " morphology=" + hex(lastMorphologySignature)
                + " dynamics=" + hex(lastDynamicsSignature) + "]"
                + " changes[domain=" + domainSignatureChanges
                + " pos=" + slotChanges(positionSlotChanges)
                + " shape=" + slotChanges(shapeSlotChanges)
                + " media=" + slotChanges(mediaSlotChanges)
                + " morphology=" + slotChanges(morphologySlotChanges)
                + " dynamics=" + slotChanges(dynamicsSlotChanges) + "]";
    }

    /** Read-only diagnostic access to the exact cache key used for the last map. */
    public static long lastInputSignatureForDiagnostics() {
        return lastInputSignature;
    }

    public static void invalidateCache() {
        lastInputSignature = Long.MIN_VALUE;
        lastDomainSignature = Long.MIN_VALUE;
        lastPositionSignature = Long.MIN_VALUE;
        lastShapeSignature = Long.MIN_VALUE;
        lastMediaSignature = Long.MIN_VALUE;
        lastMorphologySignature = Long.MIN_VALUE;
        lastDynamicsSignature = Long.MIN_VALUE;
        resetSignatureBreakdown();
        lastWeatherTextureId = -1;
        lastMorphologyTextureId = -1;
        lastCumulusStageSupportTextureId = -1;
        lastCumulusStageBaseTextureId = -1;
        lastCumulusStageTopTextureId = -1;
        lastStormStructureTextureId = -1;
        lastStormLayerHeightTextureId = -1;
        lastStormTowerTextureId = -1;
        lastPuffCandidateTextureId = -1;
        PuffLobeSpatialIndex.invalidate();
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
        ShaderInstance cumulusLayerShader = VolumetricCloudShaders.cumulusLayerSplatShader();
        ShaderInstance stormStructureShader = VolumetricCloudShaders.stormStructureSplatShader();
        ShaderInstance stormHeightShader = VolumetricCloudShaders.stormHeightSplatShader();
        if (shader == null || morphologyShader == null || cumulusLayerShader == null
                || stormStructureShader == null || stormHeightShader == null) {
            return Result.EMPTY;
        }
        RenderTarget target = VolumetricCloudRenderTargets.prepareWeatherTarget(mapSize);
        RenderTarget morphologyTarget = VolumetricCloudRenderTargets.prepareMorphologyTarget(mapSize);
        RenderTarget cumulusStageSupportTarget =
                VolumetricCloudRenderTargets.prepareCumulusStageSupportTarget(mapSize);
        RenderTarget cumulusStageBaseTarget =
                VolumetricCloudRenderTargets.prepareCumulusStageBaseTarget(mapSize);
        RenderTarget cumulusStageTopTarget =
                VolumetricCloudRenderTargets.prepareCumulusStageTopTarget(mapSize);
        RenderTarget stormStructureTarget = VolumetricCloudRenderTargets.prepareStormStructureTarget(mapSize);
        RenderTarget stormLayerHeightTarget =
                VolumetricCloudRenderTargets.prepareStormLayerHeightTarget(mapSize);
        RenderTarget stormTowerTarget = VolumetricCloudRenderTargets.prepareStormTowerTarget(mapSize);
        RenderTarget puffCandidateTarget = VolumetricCloudRenderTargets.preparePuffCandidateTarget();
        if (target == null || morphologyTarget == null
                || cumulusStageSupportTarget == null || cumulusStageBaseTarget == null
                 || cumulusStageTopTarget == null
                 || stormStructureTarget == null || stormLayerHeightTarget == null
                 || stormTowerTarget == null || puffCandidateTarget == null) {
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
        boolean hasStructuredCumulus = false;
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
            if (cell.cloudProfile() == 3
                    && (cell.envelopeRole() == VolumetricRenderCell.EnvelopeRole.BASE
                    || cell.envelopeRole() == VolumetricRenderCell.EnvelopeRole.CORE
                    || cell.envelopeRole() == VolumetricRenderCell.EnvelopeRole.TOWER
                    || cell.envelopeRole() == VolumetricRenderCell.EnvelopeRole.CROWN)) {
                hasStructuredCumulus = true;
            }
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
                cells,
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
        int cumulusStageSupportTextureId = cumulusStageSupportTarget.getColorTextureId();
        int cumulusStageBaseTextureId = cumulusStageBaseTarget.getColorTextureId();
        int cumulusStageTopTextureId = cumulusStageTopTarget.getColorTextureId();
        int stormStructureTextureId = stormStructureTarget.getColorTextureId();
        int stormLayerHeightTextureId = stormLayerHeightTarget.getColorTextureId();
        int stormTowerTextureId = stormTowerTarget.getColorTextureId();
        int puffCandidateTextureId = puffCandidateTarget.getColorTextureId();
        if (inputSignature == lastInputSignature
                && weatherTextureId == lastWeatherTextureId
                && morphologyTextureId == lastMorphologyTextureId
                && cumulusStageSupportTextureId == lastCumulusStageSupportTextureId
                && cumulusStageBaseTextureId == lastCumulusStageBaseTextureId
                && cumulusStageTopTextureId == lastCumulusStageTopTextureId
                && stormStructureTextureId == lastStormStructureTextureId
                && stormLayerHeightTextureId == lastStormLayerHeightTextureId
                && stormTowerTextureId == lastStormTowerTextureId
                && puffCandidateTextureId == lastPuffCandidateTextureId
                && lastResult.rendered()) {
            cacheHits++;
            return lastResult;
        }
        cacheMisses++;
        // The descriptor arrays and candidate grid are one representation.
        // Refresh them only together; a cache hit keeps the exact pair used by
        // the cached weather map instead of uploading new descriptors through
        // an older, quantized tile grid.
        PuffLobeSpatialIndex.updateDescriptors(cells);
        PuffLobeSpatialIndex.rebuildIfNeeded(
                puffCandidateTarget,
                originX,
                originZ,
                WEATHER_EXTENT
        );

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

        renderCumulusStageMap(
                cumulusStageSupportTarget,
                cumulusLayerShader,
                hasStructuredCumulus,
                originX,
                originZ,
                manualCoverageScale,
                count,
                0
        );
        renderCumulusStageMap(
                cumulusStageBaseTarget,
                cumulusLayerShader,
                hasStructuredCumulus,
                originX,
                originZ,
                manualCoverageScale,
                count,
                1
        );
        renderCumulusStageMap(
                cumulusStageTopTarget,
                cumulusLayerShader,
                hasStructuredCumulus,
                originX,
                originZ,
                manualCoverageScale,
                count,
                2
        );

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
        lastCumulusStageSupportTextureId = cumulusStageSupportTextureId;
        lastCumulusStageBaseTextureId = cumulusStageBaseTextureId;
        lastCumulusStageTopTextureId = cumulusStageTopTextureId;
        lastStormStructureTextureId = stormStructureTextureId;
        lastStormLayerHeightTextureId = stormLayerHeightTextureId;
        lastStormTowerTextureId = stormTowerTextureId;
        lastPuffCandidateTextureId = puffCandidateTextureId;
        lastHasSevereStructures = hasSevereStructures;
        lastHasStructuredCumulus = hasStructuredCumulus;
        lastResult = result;
        return result;
    }

    private static void renderCumulusStageMap(
            RenderTarget target,
            ShaderInstance shader,
            boolean hasStructuredCumulus,
            double originX,
            double originZ,
            float coverageScale,
            int count,
            int outputMode
    ) {
        VolumetricCloudRenderTargets.clearAndBind(target);
        if (!hasStructuredCumulus) {
            return;
        }
        RenderSystem.setShader(() -> shader);
        shader.safeGetUniform("WeatherOrigin").set((float) originX, (float) originZ);
        shader.safeGetUniform("WeatherExtent").set(WEATHER_EXTENT);
        shader.safeGetUniform("WeatherCoverageScale").set(coverageScale);
        shader.safeGetUniform("CellCount").set(count);
        shader.safeGetUniform("OutputMode").set(outputMode);
        shader.apply();
        uploadCellArrays(shader, false);
        try {
            FullscreenQuad.draw(shader);
        } finally {
            shader.clear();
        }
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
            List<VolumetricRenderCell> cells,
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
        long domainHash = 0xcbf29ce484222325L;
        domainHash = mix(domainHash, quantize(originX, 1.0D));
        domainHash = mix(domainHash, quantize(originZ, 1.0D));
        domainHash = mix(domainHash, quantize(slabBase, 8.0D));
        domainHash = mix(domainHash, quantize(slabTop, 8.0D));
        domainHash = mix(domainHash, count);
        domainHash = mix(domainHash, mapSize);
        domainHash = mix(domainHash, quantize(regionalCoverage, 512.0D));
        domainHash = mix(domainHash, quantize(regionalEnergy, 512.0D));
        domainHash = mix(domainHash, quantize(coverageScale, 512.0D));
        domainHash = mix(domainHash, includeRegionalLayer ? 1L : 0L);
        domainHash = mix(domainHash, adaptiveFootprint ? 1L : 0L);
        domainHash = mix(domainHash, sentinelHeights ? 1L : 0L);
        if (includeRegionalLayer && regionalCoverage > 0.01F) {
            // The broad regional sheet is animated; two-tick cadence keeps it
            // moving while avoiding a full map rebuild every rendered frame.
            domainHash = mix(domainHash, (long) Math.floor(worldTime * 0.5F));
        }
        hash = domainHash;
        long positionHash = mix(0xcbf29ce484222325L, count);
        long shapeHash = mix(0xcbf29ce484222325L, count);
        long mediaHash = mix(0xcbf29ce484222325L, count);
        long morphologyHash = mix(0xcbf29ce484222325L, count);
        long dynamicsHash = mix(0xcbf29ce484222325L, count);
        if (previousBreakdownCount != count) {
            resetPreviousComponentValues();
            previousBreakdownCount = count;
        }
        for (int i = 0; i < count * 4; i++) {
            double positionScale = (i & 3) < 2 ? 2.0D : 8.0D;
            long positionValue = quantize(posRadiusArray[i], positionScale);
            long shapeValue = quantize(shapeArray[i], 1024.0D);
            long mediaValue = quantize(mediaArray[i], 1024.0D);
            long morphologyValue = quantize(morphologyArray[i], 1024.0D);
            long dynamicsValue = quantize(dynamicsArray[i], 1024.0D);
            trackValueChange(previousPositionValues, positionSlotChanges, i, positionValue);
            trackValueChange(previousShapeValues, shapeSlotChanges, i, shapeValue);
            trackValueChange(previousMediaValues, mediaSlotChanges, i, mediaValue);
            trackValueChange(previousMorphologyValues, morphologySlotChanges, i, morphologyValue);
            trackValueChange(previousDynamicsValues, dynamicsSlotChanges, i, dynamicsValue);
            positionHash = mix(positionHash, positionValue);
            shapeHash = mix(shapeHash, shapeValue);
            mediaHash = mix(mediaHash, mediaValue);
            morphologyHash = mix(morphologyHash, morphologyValue);
            dynamicsHash = mix(dynamicsHash, dynamicsValue);
            hash = mix(hash, positionValue);
            hash = mix(hash, shapeValue);
            hash = mix(hash, mediaValue);
            hash = mix(hash, morphologyValue);
            hash = mix(hash, dynamicsValue);
        }
        // PUFF tier is deliberately transported outside CellMorphology so it
        // cannot reactivate the legacy structured-stage path. It still owns
        // direct descriptor geometry, therefore a tier-only network update
        // must invalidate the cached descriptor/candidate pair.
        for (int i = 0; i < count; i++) {
            VolumetricRenderCell cell = cells.get(i);
            long puffTierValue = cell.puffTier() == null
                    ? 3L
                    : cell.puffTier().gpuId();
            morphologyHash = mix(morphologyHash, puffTierValue);
            hash = mix(hash, puffTierValue);
        }
        if (lastDomainSignature != Long.MIN_VALUE && lastDomainSignature != domainHash) {
            domainSignatureChanges++;
        }
        lastDomainSignature = domainHash;
        lastPositionSignature = positionHash;
        lastShapeSignature = shapeHash;
        lastMediaSignature = mediaHash;
        lastMorphologySignature = morphologyHash;
        lastDynamicsSignature = dynamicsHash;
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

    private static String hex(long value) {
        return String.format("%016x", value);
    }

    private static void trackValueChange(long[] previous, long[] slotChanges, int index, long value) {
        long prior = previous[index];
        if (prior != Long.MIN_VALUE && prior != value) {
            slotChanges[index & 3]++;
        }
        previous[index] = value;
    }

    private static String slotChanges(long[] values) {
        return values[0] + "/" + values[1] + "/" + values[2] + "/" + values[3];
    }

    private static void resetSignatureBreakdown() {
        domainSignatureChanges = 0L;
        Arrays.fill(positionSlotChanges, 0L);
        Arrays.fill(shapeSlotChanges, 0L);
        Arrays.fill(mediaSlotChanges, 0L);
        Arrays.fill(morphologySlotChanges, 0L);
        Arrays.fill(dynamicsSlotChanges, 0L);
        resetPreviousComponentValues();
        previousBreakdownCount = -1;
    }

    private static void resetPreviousComponentValues() {
        Arrays.fill(previousPositionValues, Long.MIN_VALUE);
        Arrays.fill(previousShapeValues, Long.MIN_VALUE);
        Arrays.fill(previousMediaValues, Long.MIN_VALUE);
        Arrays.fill(previousMorphologyValues, Long.MIN_VALUE);
        Arrays.fill(previousDynamicsValues, Long.MIN_VALUE);
    }
}
