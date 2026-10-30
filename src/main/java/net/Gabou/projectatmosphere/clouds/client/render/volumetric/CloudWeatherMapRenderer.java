package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.Gabou.projectatmosphere.client.render.shader.VolumetricCloudShaders;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import java.util.Arrays;
import java.util.List;

/**
 * Rasterizes the interpolated cloud cells into the camera-following weather
 * map. The map is world-anchored and camera-snapped to its texel grid so
 * cloud silhouettes never swim as the camera moves.
 */
public final class CloudWeatherMapRenderer {
    /**
     * C1 (stabilisation capacity): was 96, the uniform-array limit (about 12 cumulus). The cells are now a data
     * texture with per-tile lists (see {@link #uploadCellTextures}), so the map can hold a whole cumulus field.
     */
    public static final int MAX_CELLS = 1024;
    /** C1: 32 x 32 tiles of 128 blocks over the map, at most CELL_TILE_CAP cells each (shader constants). */
    static final int CELL_TILE_GRID = 32;
    static final int CELL_TILE_CAP = 64;
    private static int cellDataTexture;
    private static int cellTileTexture;
    private static final java.nio.FloatBuffer CELL_DATA = org.lwjgl.BufferUtils.createFloatBuffer(MAX_CELLS * 5 * 4);
    private static final java.nio.FloatBuffer CELL_TILES =
            org.lwjgl.BufferUtils.createFloatBuffer(CELL_TILE_GRID * CELL_TILE_GRID * (CELL_TILE_CAP + 1));
    private static final int[] CELL_TILE_COUNTS = new int[CELL_TILE_GRID * CELL_TILE_GRID];
    private static long cellTileOverflows;
    public static final float WEATHER_EXTENT = 4096.0F;
    public static final float ADAPTIVE_TARGET_RADIUS_TEXELS = 2.25F;
    public static final float MAX_ADAPTIVE_FOOTPRINT_SCALE = 1.5F;

    private static final float[] posRadiusArray = new float[MAX_CELLS * 4];
    private static final float[] shapeArray = new float[MAX_CELLS * 4];
    private static final float[] mediaArray = new float[MAX_CELLS * 4];
    private static final float[] morphologyArray = new float[MAX_CELLS * 4];
    private static final float[] dynamicsArray = new float[MAX_CELLS * 4];
    private static final VolumetricRenderCell[] rasterCells = new VolumetricRenderCell[MAX_CELLS];

    private static double lastOriginX;
    /** T225. The render cells of the last weather-map update. */
    private static volatile List<VolumetricRenderCell> lastCells = List.of();

    public static List<VolumetricRenderCell> lastCells() {
        return lastCells;
    }
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
    private static int lastStormCandidateTextureId = -1;
    private static int lastStormDescriptorTextureId = -1;
    private static int lastPuffCandidateTextureId = -1;
    // Diagnostic-only: whether the last weather-map bake found any cell whose
    // envelopeRole fell in the structured BASE..ANVIL range. This is the
    // authoritative indication that the submitted render-cell set contained
    // direct severe roles. The coordinator independently decides which
    // complete groups fit its bounded descriptor capacity.
    private static boolean lastHasSevereStructures;
    private static boolean lastHasStructuredCumulus;
    private static Result lastResult = Result.EMPTY;
    private static long cacheHits;
    private static long cacheMisses;
    // T253. The inputs the pre-blurred coverage was last built from.
    private static long nsCoverageGeneration = Long.MIN_VALUE;
    private static float nsCoverageMul = Float.NaN;
    private static int nsCoverageWeatherId = -1;
    private static int nsCoverageTargetId = -1;
    private static long nsCoverageBuilds;

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

    /**
     * T250. The step the weather origin snaps to: a whole number of map texels AND
     * of storm-field nodes / puff tiles (both 256 cells over the extent). Snapping
     * to single 8-block texels moved the 16-block field lattice by half a node on
     * every other re-centre - the non-storm light grid changed phase as the camera
     * moved. Texel centres stay on the same world lattice; the map only re-centres
     * half as often.
     */
    public static double originSnap(int mapSize) {
        double texel = WEATHER_EXTENT / Math.max(1, mapSize);
        double node = WEATHER_EXTENT / StormFieldGeometry.DEFAULT_XZ;
        return Math.max(texel, node);
    }

    public static double lastOriginX() {
        return lastOriginX;
    }

    public static double lastOriginZ() {
        return lastOriginZ;
    }

    /**
     * T253. Rebuilds the pre-blurred nimbostratus deck coverage (T248's nine
     * 40-block taps of saturate(coverage * CoverageMul), averaged) when the
     * weather raster was re-baked, the coverage multiplier changed, or either
     * target was recreated; otherwise the previous map is still exact.
     *
     * @return the number of blur passes drawn (0 or 1), for diagnostics
     */
    public static int refreshNsCoverage(RenderTarget weatherTarget, float coverageMul) {
        ShaderInstance blurShader = VolumetricCloudShaders.coverageBlurShader();
        RenderTarget nsCoverageTarget = VolumetricCloudRenderTargets.prepareNsCoverageTarget();
        if (blurShader == null || weatherTarget == null || nsCoverageTarget == null) {
            return 0;
        }
        int weatherId = weatherTarget.getColorTextureId();
        int nsId = nsCoverageTarget.getColorTextureId();
        if (cacheMisses == nsCoverageGeneration && coverageMul == nsCoverageMul
                && weatherId == nsCoverageWeatherId && nsId == nsCoverageTargetId) {
            return 0;
        }
        nsCoverageGeneration = cacheMisses;
        nsCoverageMul = coverageMul;
        nsCoverageWeatherId = weatherId;
        nsCoverageTargetId = nsId;
        nsCoverageBuilds++;
        VolumetricCloudRenderTargets.clearAndBind(nsCoverageTarget);
        RenderSystem.setShader(() -> blurShader);
        blurShader.setSampler("WeatherMapSampler", weatherId);
        blurShader.safeGetUniform("WeatherExtent").set(WEATHER_EXTENT);
        blurShader.safeGetUniform("CoverageMul").set(coverageMul);
        blurShader.apply();
        try {
            FullscreenQuad.draw(blurShader);
        } finally {
            blurShader.clear();
        }
        // P3 (stabilisation perf): the occupancy pyramid the march's empty-space jump reads (level 3 = 64-block
        // tiles). Only when the raster was re-baked, like the blur itself.
        int previousTexture = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
        RenderSystem.bindTexture(nsId);
        org.lwjgl.opengl.GL30.glGenerateMipmap(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
        RenderSystem.bindTexture(previousTexture);
        return 1;
    }

    /** T253. Blur passes drawn since start-up. */
    public static long nsCoverageBuilds() {
        return nsCoverageBuilds;
    }

    /** T199. Every miss is a bake, so this counts the weather rasters' generations. */
    public static long cacheMisses() {
        return cacheMisses;
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
        lastStormCandidateTextureId = -1;
        lastStormDescriptorTextureId = -1;
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
        if (shader == null || morphologyShader == null || cumulusLayerShader == null) {
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
        RenderTarget stormCandidateTarget = VolumetricCloudRenderTargets.prepareStormCandidateTarget();
        RenderTarget stormDescriptorTarget = VolumetricCloudRenderTargets.prepareStormDescriptorTarget();
        RenderTarget puffCandidateTarget = VolumetricCloudRenderTargets.preparePuffCandidateTarget();
        if (target == null || morphologyTarget == null
                || cumulusStageSupportTarget == null || cumulusStageBaseTarget == null
                 || cumulusStageTopTarget == null || stormCandidateTarget == null
                 || stormDescriptorTarget == null || puffCandidateTarget == null) {
            return Result.EMPTY;
        }

        // Camera-snapped origin: the map scrolls in whole snap steps only.
        float texelSize = WEATHER_EXTENT / mapSize;
        double snap = originSnap(mapSize);
        double originX = Math.floor((cameraX - WEATHER_EXTENT * 0.5D) / snap) * snap;
        double originZ = Math.floor((cameraZ - WEATHER_EXTENT * 0.5D) / snap) * snap;
        lastOriginX = originX;
        lastOriginZ = originZ;

        // Slab bounds: envelope of all cell layers plus the regional sheet.
        float effectiveRegional = includeRegionalLayer ? Mth.clamp(regionalCoverage, 0.0F, 1.0F) : 0.0F;
        float slabBase = Float.MAX_VALUE;
        float slabTop = -Float.MAX_VALUE;
        float maxPrecipitation = 0.0F;
        int count = 0;
        // T225. The cells behind this frame's maximum, for the local-versus-
        // global precipitation log.
        lastCells = cells;
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
            boolean severeDescriptorRole = StormLobeDescriptor.Role.supports(cell.envelopeRole())
                    && (cell.cloudProfile() == 4 || cell.cloudProfile() == 7);
            if (severeDescriptorRole
                    && StormGeometryBuildCoordinator.renderOwnsGroup(cell.morphologyGroupId())) {
                // A complete descriptor group owns its body and material
                // inputs. Keeping its member splats in the raster map makes
                // role footprints reappear as density/material borders.
                hasSevereStructures = true;
                continue;
            }
            rasterCells[count] = cell;
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
                rasterCells,
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
        int stormCandidateTextureId = stormCandidateTarget.getColorTextureId();
        int stormDescriptorTextureId = stormDescriptorTarget.getColorTextureId();
        int puffCandidateTextureId = puffCandidateTarget.getColorTextureId();
        if (inputSignature == lastInputSignature
                && weatherTextureId == lastWeatherTextureId
                && morphologyTextureId == lastMorphologyTextureId
                && cumulusStageSupportTextureId == lastCumulusStageSupportTextureId
                && cumulusStageBaseTextureId == lastCumulusStageBaseTextureId
                && cumulusStageTopTextureId == lastCumulusStageTopTextureId
                && stormCandidateTextureId == lastStormCandidateTextureId
                && stormDescriptorTextureId == lastStormDescriptorTextureId
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
        PuffLobeSpatialIndex.updateDescriptors(cells, cameraX, cameraZ);
        PuffLobeSpatialIndex.rebuildIfNeeded(
                puffCandidateTarget,
                originX,
                originZ,
                WEATHER_EXTENT
        );

        uploadCellTextures(count, originX, originZ, manualCoverageScale);
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
        bindCellSamplers(shader);
        shader.apply();
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
        bindCellSamplers(morphologyShader);
        morphologyShader.apply();
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
        lastStormCandidateTextureId = stormCandidateTextureId;
        lastStormDescriptorTextureId = stormDescriptorTextureId;
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
        bindCellSamplers(shader);
        shader.apply();
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

    /** C1. The weather passes read the cells from these two textures (bound before apply()). */
    private static void bindCellSamplers(ShaderInstance shader) {
        shader.setSampler("CellDataSampler", cellDataTexture);
        shader.setSampler("CellTileSampler", cellTileTexture);
    }

    /**
     * C1. Writes the cell data texture (5 RGBA32F texels per cell) and the per-tile cell lists. A cell is listed in
     * every 128-block tile its influence disc touches: the passes' own cull radius (max radius x adaptive footprint
     * x coverage scale x 1.45) or the splat's stratus anchor (1.35 x radius, evaluated before that cull), whichever
     * is larger, plus 2 blocks. Lists keep ascending cell order, so each texel sums its cells in the old order.
     */
    private static void uploadCellTextures(int count, double originX, double originZ, float coverageScale) {
        if (cellDataTexture == 0) {
            cellDataTexture = createFloatTexture(GL30.GL_RGBA32F, 5, MAX_CELLS, GL11.GL_RGBA);
            cellTileTexture = createFloatTexture(GL30.GL_R32F, CELL_TILE_CAP + 1, CELL_TILE_GRID * CELL_TILE_GRID,
                    GL11.GL_RED);
        }
        CELL_DATA.clear();
        for (int i = 0; i < count; i++) {
            int b = i * 4;
            CELL_DATA.put(posRadiusArray, b, 4).put(shapeArray, b, 4).put(mediaArray, b, 4)
                    .put(morphologyArray, b, 4).put(dynamicsArray, b, 4);
        }
        CELL_DATA.flip();
        java.util.Arrays.fill(CELL_TILE_COUNTS, 0);
        CELL_TILES.clear();
        for (int i = 0; i < CELL_TILES.capacity(); i++) {
            CELL_TILES.put(i, 0.0F);
        }
        float tile = WEATHER_EXTENT / CELL_TILE_GRID;
        int overflow = 0;
        for (int i = 0; i < count; i++) {
            int b = i * 4;
            float radius = Math.max(posRadiusArray[b + 2], posRadiusArray[b + 3]);
            float footprint = Math.max(mediaArray[b + 3], 0.001F) * Math.max(coverageScale, 0.001F) * 1.45F;
            float reach = radius * Math.max(footprint, 1.35F) + 2.0F;
            int minX = Math.max(0, (int) Math.floor((posRadiusArray[b] - reach - originX) / tile));
            int maxX = Math.min(CELL_TILE_GRID - 1, (int) Math.floor((posRadiusArray[b] + reach - originX) / tile));
            int minZ = Math.max(0, (int) Math.floor((posRadiusArray[b + 1] - reach - originZ) / tile));
            int maxZ = Math.min(CELL_TILE_GRID - 1, (int) Math.floor((posRadiusArray[b + 1] + reach - originZ) / tile));
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    int t = z * CELL_TILE_GRID + x;
                    int n = CELL_TILE_COUNTS[t];
                    if (n >= CELL_TILE_CAP) {
                        overflow++;
                        continue;
                    }
                    CELL_TILES.put(t * (CELL_TILE_CAP + 1) + 1 + n, i);
                    CELL_TILE_COUNTS[t] = n + 1;
                }
            }
        }
        for (int t = 0; t < CELL_TILE_COUNTS.length; t++) {
            CELL_TILES.put(t * (CELL_TILE_CAP + 1), CELL_TILE_COUNTS[t]);
        }
        if (overflow > 0) {
            cellTileOverflows += overflow;
            if (Long.bitCount(cellTileOverflows) == 1) {
                net.Gabou.projectatmosphere.ProjectAtmosphere.LOGGER.warn(
                        "C1 weather-map tile overflow: {} cell-tile entries dropped this bake ({} total)",
                        overflow, cellTileOverflows);
            }
        }
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        RenderSystem.bindTexture(cellDataTexture);
        if (count > 0) {
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 5, count, GL11.GL_RGBA, GL11.GL_FLOAT, CELL_DATA);
        }
        RenderSystem.bindTexture(cellTileTexture);
        CELL_TILES.position(0);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, CELL_TILE_CAP + 1, CELL_TILE_GRID * CELL_TILE_GRID,
                GL11.GL_RED, GL11.GL_FLOAT, CELL_TILES);
        RenderSystem.bindTexture(previous);
    }

    private static int createFloatTexture(int internalFormat, int width, int height, int format) {
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int id = GL11.glGenTextures();
        RenderSystem.bindTexture(id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, GL11.GL_FLOAT,
                (java.nio.FloatBuffer) null);
        RenderSystem.bindTexture(previous);
        return id;
    }

    private static long inputSignature(
            VolumetricRenderCell[] cells,
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
            VolumetricRenderCell cell = cells[i];
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
