package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.client.render.field.CloudFieldCompositeDebugMode;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldSnapshot;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Read-only diagnostics for the active weather-map volumetric cloud renderer.
 * This class records what the renderer already calculated; it does not decide
 * visibility, alter tuning, allocate targets, or touch shader state.
 */
public final class VolumetricCloudFrameDiagnostics {
    private static final Snapshot EMPTY = Snapshot.empty();

    private static volatile Snapshot latest = EMPTY;
    private static volatile boolean frameLogEnabled;

    private VolumetricCloudFrameDiagnostics() {
    }

    public static Snapshot latest() {
        return latest;
    }

    public static String formattedLatest() {
        return latest.format()
                + "\n\nCumulus diagnostic status=" + CumulusStageMapDiagnostics.status()
                + "\n" + CumulusStageMapDiagnostics.formattedLatest()
                + "\n\n" + VolumetricStabilityDiagnostics.formattedLatest()
                + "\n" + CloudWeatherMapRenderer.diagnosticSignatureStatus();
    }

    public static boolean isFrameLogEnabled() {
        return frameLogEnabled;
    }

    public static void setFrameLogEnabled(boolean enabled) {
        frameLogEnabled = enabled;
    }

    public static void record(Snapshot snapshot) {
        Snapshot safeSnapshot = snapshot == null ? EMPTY : snapshot;
        latest = safeSnapshot;
        if (frameLogEnabled) {
            ProjectAtmosphere.LOGGER.info("[VolumetricCloudDiagnostics] {}", safeSnapshot.shortLine());
        }
    }

    public static void captureLatestWeatherTextureStats() {
        Snapshot snapshot = latest;
        if (snapshot == null || snapshot == EMPTY) {
            return;
        }
        WeatherTextureStats stats = readWeatherTextureStats(
                VolumetricCloudRenderTargets.weatherTargetOrNull(),
                snapshot.weather().slabBaseY(),
                snapshot.weather().slabTopY(),
                snapshot.weather().cloudletsSplatted()
        );
        latest = snapshot.withWeatherTextureStats(stats);
    }

    public static String requestCumulusStageCapture() {
        return CumulusStageMapDiagnostics.requestCapture();
    }

    public static void pollCumulusStageCapture() {
        CumulusStageMapDiagnostics.poll();
    }

    public static void tryDispatchCumulusStageCapture(
            CloudWeatherMapRenderer.Result weather,
            long frameIndex,
            long gameTime,
            String roleSummary,
            float coverageMultiplier
    ) {
        if (weather == null || !weather.rendered()) {
            return;
        }
        CumulusStageMapDiagnostics.tryDispatch(
                VolumetricCloudRenderTargets.cumulusStageSupportTargetOrNull(),
                VolumetricCloudRenderTargets.cumulusStageBaseTargetOrNull(),
                VolumetricCloudRenderTargets.cumulusStageTopTargetOrNull(),
                weather.slabBaseY(),
                weather.slabTopY(),
                weather.originX(),
                weather.originZ(),
                CloudWeatherMapRenderer.WEATHER_EXTENT,
                frameIndex,
                gameTime,
                CloudWeatherMapRenderer.lastInputSignatureForDiagnostics(),
                roleSummary,
                CloudWeatherMapRenderer.diagnosticSignatureStatus(),
                coverageMultiplier
        );
    }

    public static void shutdownCumulusStageCapture() {
        CumulusStageMapDiagnostics.shutdown();
    }

    public static String requestStabilityCapture(int frames) {
        return VolumetricStabilityDiagnostics.requestCapture(frames);
    }

    public static String requestStormMaterialTrace(double x, double z, float yStart, float yEnd) {
        return StormMaterialRuntimeTrace.request(x, z, yStart, yEnd);
    }

    public static String stormMaterialTraceLatest() {
        return StormMaterialRuntimeTrace.latest();
    }

    public static String requestStormWorkloadCapture(String view) {
        return StormWorkloadRuntimeCapture.request(view);
    }

    /** T136: the counter readback for the cell just measured, or null. */
    public static String stormWorkloadResultLine() {
        StormWorkloadRuntimeCapture.WorkloadResult result =
                StormWorkloadRuntimeCapture.latestResult();
        return result == null ? null : result.format();
    }

    /**
     * T150: cloud density evaluations from the most recent completed workload
     * readback, or a negative value when there is none. The visibility guard
     * uses this as its authoritative "the march really produced cloud" signal.
     */
    public static double stormWorkloadCloudDensityCalls() {
        StormWorkloadRuntimeCapture.WorkloadResult result =
                StormWorkloadRuntimeCapture.latestResult();
        return result == null ? -1.0D : result.cloudDensityCalls();
    }

    public static void abortStormWorkloadCapture() {
        StormWorkloadRuntimeCapture.abort("driver_timeout");
    }

    public static boolean stormWorkloadActive() {
        return StormWorkloadRuntimeCapture.active();
    }

    public static String stormWorkloadLatest() {
        return StormWorkloadRuntimeCapture.latest();
    }

    public static String beginStormPerformanceSuite(double x, double y, double z) {
        return StormPerformanceSuite.begin(x, y, z);
    }

    public static String stormPerformanceSuiteLatest() {
        return StormPerformanceSuite.latest();
    }

    public static void tryCaptureStormWorkload(RenderTarget cloudTarget) {
        StormWorkloadRuntimeCapture.capture(cloudTarget);
    }

    public static void tryCaptureStormReferenceImage(RenderTarget cloudTarget) {
        StormReferenceImageCapture.capture(cloudTarget);
    }

    public static void tickT132AutoDriver() {
        StormT132AutoDriver.tick();
    }

    public static void tryCaptureStormMaterialTrace(RenderTarget cloudTarget) {
        StormMaterialRuntimeTrace.capture(cloudTarget);
    }

    public static void tryCaptureStormProductionRayTrace(RenderTarget cloudTarget) {
        StormProductionRayTrace.capture(cloudTarget);
    }

    /**
     * T098 production ray trace. Each requested pixel is traced twice: arm A is
     * unmodified production, arm B disables only the outer weather-gated
     * empty-space skip.
     */
    public static String requestStormProductionRayTrace(
            String setLabel,
            java.util.List<int[]> pixels,
            java.util.List<String> labels,
            int frameWidth,
            int frameHeight) {
        java.util.List<StormProductionRayTrace.Pixel> targets = new java.util.ArrayList<>();
        for (int index = 0; index < pixels.size(); index++) {
            int[] pixel = pixels.get(index);
            targets.add(new StormProductionRayTrace.Pixel(
                    index < labels.size() ? labels.get(index) : ("pixel" + index),
                    pixel[0], pixel[1]));
        }
        return StormProductionRayTrace.request(setLabel, targets, frameWidth, frameHeight);
    }

    public static String stormProductionRayTraceLatest() {
        return StormProductionRayTrace.latest();
    }

    public static boolean stormProductionRayTraceActive() {
        return StormProductionRayTrace.active();
    }

    public static String beginStormPerformanceBaseline(double x, double y, double z) {
        return StormPerformanceBaseline.begin(x, y, z);
    }

    public static String captureStormPerformanceBaseline(
            String viewpoint, double x, double y, double z, float yaw, float pitch
    ) {
        return StormPerformanceBaseline.capture(viewpoint, x, y, z, yaw, pitch);
    }

    public static String stormPerformanceBaselineLatest() {
        return StormPerformanceBaseline.latest();
    }

    public static void observeStormPerformanceBaseline(RenderTarget cloudTarget) {
        StormPerformanceBaseline.observe(cloudTarget);
    }

    public static void observeStormPerformanceSuite(RenderTarget cloudTarget) {
        StormPerformanceSuite.observe(cloudTarget);
    }

    public static void pollStabilityCapture() {
        VolumetricStabilityDiagnostics.poll();
    }

    public static void tryDispatchStabilityCapture(
            RenderTarget cloudTarget,
            net.Gabou.projectatmosphere.clouds.client.render.depth.SceneDepthFrame sceneDepth,
            long hookFrameIndex,
            long gameTime,
            float partialTick,
            long weatherInputSignature,
            String qualityName,
            VolumetricCloudRenderer.LastDrawInputs raymarchInputs,
            net.Gabou.projectatmosphere.clouds.client.render.field.CloudFieldCompositeRenderer.LastDrawInputs compositeInputs,
            boolean composited
    ) {
        VolumetricStabilityDiagnostics.tryDispatch(
                cloudTarget,
                sceneDepth,
                hookFrameIndex,
                gameTime,
                partialTick,
                weatherInputSignature,
                qualityName,
                raymarchInputs,
                compositeInputs,
                composited
        );
    }

    public static void shutdownStabilityCapture() {
        VolumetricStabilityDiagnostics.shutdown();
    }

    public static String targetSize(RenderTarget target) {
        if (target == null) {
            return "unknown";
        }
        return target.width + "x" + target.height
                + " view=" + target.viewWidth + "x" + target.viewHeight
                + " color=" + target.getColorTextureId()
                + " depth=" + target.getDepthTextureId();
    }

    public static CellInfo cellInfo(int index, VolumetricRenderCell cell) {
        if (cell == null) {
            return new CellInfo(
                    index, 0.0D, 0.0D, 0.0F, 0.0F, 0.0F, 0.0F,
                    0.0F, 0, "unknown", 0.0F, 0.0F
            );
        }
        return new CellInfo(
                index,
                cell.x(),
                cell.z(),
                cell.baseY(),
                cell.topY(),
                cell.radiusMajor(),
                cell.radiusMinor(),
                cell.density(),
                cell.cloudProfile(),
                cell.envelopeRole().name().toLowerCase(Locale.ROOT),
                cell.verticalDevelopment(),
                cell.precipitationIntensity()
        );
    }

    public static RenderBounds boundsForCells(List<VolumetricRenderCell> cells) {
        if (cells == null || cells.isEmpty()) {
            return RenderBounds.unknown();
        }
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        float baseY = Float.POSITIVE_INFINITY;
        float topY = Float.NEGATIVE_INFINITY;
        for (VolumetricRenderCell cell : cells) {
            if (cell == null) {
                continue;
            }
            minX = Math.min(minX, cell.x() - cell.radiusMajor());
            maxX = Math.max(maxX, cell.x() + cell.radiusMajor());
            minZ = Math.min(minZ, cell.z() - cell.radiusMinor());
            maxZ = Math.max(maxZ, cell.z() + cell.radiusMinor());
            baseY = Math.min(baseY, cell.baseY());
            topY = Math.max(topY, cell.topY());
        }
        if (!Double.isFinite(minX) || !Double.isFinite(maxX)
                || !Double.isFinite(minZ) || !Double.isFinite(maxZ)
                || !Float.isFinite(baseY) || !Float.isFinite(topY)) {
            return RenderBounds.unknown();
        }
        return new RenderBounds(minX, maxX, minZ, maxZ, baseY, topY);
    }

    public static InputHeightStats inputHeightStats(List<VolumetricRenderCell> cells) {
        if (cells == null || cells.isEmpty()) {
            return InputHeightStats.unknown();
        }
        float minBase = Float.POSITIVE_INFINITY;
        float maxBase = Float.NEGATIVE_INFINITY;
        float minTop = Float.POSITIVE_INFINITY;
        float maxTop = Float.NEGATIVE_INFINITY;
        float minThickness = Float.POSITIVE_INFINITY;
        float maxThickness = Float.NEGATIVE_INFINITY;
        double totalThickness = 0.0D;
        int count = 0;
        for (VolumetricRenderCell cell : cells) {
            if (cell == null || !Float.isFinite(cell.baseY()) || !Float.isFinite(cell.topY())) {
                continue;
            }
            float thickness = Math.max(0.0F, cell.topY() - cell.baseY());
            minBase = Math.min(minBase, cell.baseY());
            maxBase = Math.max(maxBase, cell.baseY());
            minTop = Math.min(minTop, cell.topY());
            maxTop = Math.max(maxTop, cell.topY());
            minThickness = Math.min(minThickness, thickness);
            maxThickness = Math.max(maxThickness, thickness);
            totalThickness += thickness;
            count++;
        }
        if (count == 0) {
            return InputHeightStats.unknown();
        }
        return new InputHeightStats(
                minBase,
                maxBase,
                minTop,
                maxTop,
                minThickness,
                (float) (totalThickness / count),
                maxThickness
        );
    }

    public static FieldInfo fieldInfo(
            CloudFieldSnapshot snapshot,
            int renderedCloudlets,
            int skippedCloudlets,
            boolean skippedByMaxCells,
            boolean skippedByRenderCap,
            boolean skippedByLodHydration,
            boolean skippedByDistanceOrCulling,
            String skipReason
    ) {
        if (snapshot == null) {
            return FieldInfo.unknown("null_snapshot");
        }
        Vec3 center = snapshot.center();
        Vec3 wind = snapshot.windVector();
        float radius = snapshot.radius();
        return new FieldInfo(
                snapshot.fieldId(),
                snapshot.sourceKind().serializedName(),
                snapshot.morphologyMembership().groupId(),
                snapshot.morphologyMembership().memberIndex(),
                snapshot.morphologyMembership().memberCount(),
                snapshot.morphologyFamily().name().toLowerCase(Locale.ROOT),
                snapshot.morphologyMembership().stageFor(snapshot.morphologyFamily())
                        .name().toLowerCase(Locale.ROOT),
                center.x(),
                center.y(),
                center.z(),
                wind.x(),
                wind.z(),
                radius,
                snapshot.baseY(),
                snapshot.topY(),
                center.x() - radius,
                center.x() + radius,
                center.z() - radius,
                center.z() + radius,
                snapshot.activeCloudletCount(),
                snapshot.targetCloudletCount(),
                renderedCloudlets,
                Math.max(0, skippedCloudlets),
                skippedByMaxCells,
                skippedByRenderCap,
                skippedByLodHydration,
                skippedByDistanceOrCulling,
                skipReason == null || skipReason.isBlank() ? "none" : skipReason
        );
    }

    public record Snapshot(
            long capturedAtMillis,
            long frameIndex,
            long gameTime,
            float partialTick,
            double cameraX,
            double cameraY,
            double cameraZ,
            boolean rendererActive,
            String qualityProfile,
            String cloudTargetSize,
            String mainTargetSize,
            boolean historyConsumedThisFrame,
            boolean historyTargetValidAfterFrame,
            String compositeMode,
            int sceneDepthTextureId,
            boolean sceneDepthAvailable,
            int fieldsReceived,
            List<FieldInfo> fields,
            int renderCellCount,
            List<CellInfo> cells,
            RenderBounds renderBounds,
            WeatherInfo weather,
            DepthCompositeInfo depthComposite
    ) {
        public Snapshot {
            qualityProfile = safe(qualityProfile);
            cloudTargetSize = safe(cloudTargetSize);
            mainTargetSize = safe(mainTargetSize);
            compositeMode = safe(compositeMode);
            fields = List.copyOf(fields == null ? List.of() : fields);
            cells = List.copyOf(cells == null ? List.of() : cells);
            renderBounds = renderBounds == null ? RenderBounds.unknown() : renderBounds;
            weather = weather == null ? WeatherInfo.unknown() : weather;
            depthComposite = depthComposite == null ? DepthCompositeInfo.unknown() : depthComposite;
        }

        static Snapshot empty() {
            return new Snapshot(
                    0L,
                    0L,
                    0L,
                    0.0F,
                    0.0D,
                    0.0D,
                    0.0D,
                    false,
                    "unknown",
                    "unknown",
                    "unknown",
                    false,
                    false,
                    "unknown",
                    -1,
                    false,
                    0,
                    List.of(),
                    0,
                    List.of(),
                    RenderBounds.unknown(),
                    WeatherInfo.unknown(),
                    DepthCompositeInfo.unknown()
            );
        }

        public String shortLine() {
            return "frame=" + frameIndex
                    + " gameTime=" + gameTime
                    + " active=" + rendererActive
                    + " quality=" + qualityProfile
                    + " fields=" + fieldsReceived
                    + " cells=" + renderCellCount
                    + " dropped=" + weather.cloudletsDroppedBeforeSplat()
                    + " slab=" + fmt(weather.slabBaseY()) + ".." + fmt(weather.slabTopY())
                    + " target=" + cloudTargetSize
                    + " history=" + historyConsumedThisFrame + "/" + historyTargetValidAfterFrame;
        }

        Snapshot withWeatherTextureStats(WeatherTextureStats stats) {
            return new Snapshot(
                    capturedAtMillis,
                    frameIndex,
                    gameTime,
                    partialTick,
                    cameraX,
                    cameraY,
                    cameraZ,
                    rendererActive,
                    qualityProfile,
                    cloudTargetSize,
                    mainTargetSize,
                    historyConsumedThisFrame,
                    historyTargetValidAfterFrame,
                    compositeMode,
                    sceneDepthTextureId,
                    sceneDepthAvailable,
                    fieldsReceived,
                    fields,
                    renderCellCount,
                    cells,
                    renderBounds,
                    weather.withTextureStats(stats),
                    depthComposite
            );
        }

        public String format() {
            StringBuilder builder = new StringBuilder("Volumetric cloud diagnostics");
            builder.append("\nGeneral")
                    .append("\nframe=").append(frameIndex)
                    .append(" capturedAtMs=").append(capturedAtMillis)
                    .append(" gameTime=").append(gameTime)
                    .append(" partial=").append(fmt(partialTick))
                    .append("\ncamera=").append(fmt(cameraX)).append(",").append(fmt(cameraY)).append(",").append(fmt(cameraZ))
                    .append("\nactive=").append(rendererActive)
                    .append(" quality=").append(qualityProfile)
                    .append("\ncloudTarget=").append(cloudTargetSize)
                    .append("\nmainTarget=").append(mainTargetSize)
                    .append("\nhistoryEnabled=").append(VolumetricCloudDebugConfig.historyEnabled())
                    .append(" historyConsumedThisFrame=").append(historyConsumedThisFrame)
                    .append(" historyTargetValidAfterFrame=").append(historyTargetValidAfterFrame)
                    .append("\ncompositeMode=").append(compositeMode)
                    .append("\nsceneDepthTextureId=").append(sceneDepthTextureId)
                    .append(" sceneDepthAvailable=").append(sceneDepthAvailable);

            builder.append("\n\nField info")
                    .append("\nreceived=").append(fieldsReceived);
            if (fields.isEmpty()) {
                builder.append("\nnone");
            } else {
                for (FieldInfo field : fields) {
                    builder.append("\n- ").append(field.shortLine());
                }
            }

            builder.append("\n\nRender cell info")
                    .append("\ncount=").append(renderCellCount)
                    .append("\nglobalMinX=").append(fmt(renderBounds.minX()))
                    .append(" globalMaxX=").append(fmt(renderBounds.maxX()))
                    .append(" globalMinZ=").append(fmt(renderBounds.minZ()))
                    .append(" globalMaxZ=").append(fmt(renderBounds.maxZ()))
                    .append("\nglobalBaseY=").append(fmt(renderBounds.baseY()))
                    .append(" globalTopY=").append(fmt(renderBounds.topY()));
            if (cells.isEmpty()) {
                builder.append("\nnone");
            } else {
                for (CellInfo cell : cells) {
                    builder.append("\n- ").append(cell.shortLine());
                }
            }

            builder.append("\n\nWeather map info")
                    .append("\norigin=").append(fmt(weather.originX())).append(",").append(fmt(weather.originZ()))
                    .append(" extent=").append(fmt(weather.extent()))
                    .append(" textureSize=").append(weather.textureSize())
                    .append("\nsplattedCloudlets=").append(weather.cloudletsSplatted())
                    .append(" droppedBeforeSplat=").append(weather.cloudletsDroppedBeforeSplat())
                    .append("\nslabBase=").append(fmt(weather.slabBaseY()))
                    .append(" slabTop=").append(fmt(weather.slabTopY()))
                    .append("\ninputMinBaseY=").append(fmt(weather.inputMinBaseY()))
                    .append(" inputMaxTopY=").append(fmt(weather.inputMaxTopY()))
                    .append("\ninputCloudletBaseRange=")
                    .append(fmt(weather.inputHeightStats().minBaseY())).append("..")
                    .append(fmt(weather.inputHeightStats().maxBaseY()))
                    .append(" inputCloudletTopRange=")
                    .append(fmt(weather.inputHeightStats().minTopY())).append("..")
                    .append(fmt(weather.inputHeightStats().maxTopY()))
                    .append("\ninputCloudletThickness min/avg/max=")
                    .append(fmt(weather.inputHeightStats().minThickness())).append("/")
                    .append(fmt(weather.inputHeightStats().averageThickness())).append("/")
                    .append(fmt(weather.inputHeightStats().maxThickness()))
                    .append("\noverlapCollapsedToTexelHeightRange=").append(weather.overlapCollapsedToTexelHeightRange())
                    .append("\ncoverageRange=").append(weather.textureStats().coverageRange())
                    .append(" activeTexels=").append(weather.textureStats().activeTexels())
                    .append(" activeTexelPercent=").append(weather.textureStats().activeTexelPercent())
                    .append(" estimatedTexelsPerCloudlet=").append(weather.textureStats().estimatedTexelsPerCloudlet())
                    .append("\nworldUnitsPerWeatherTexel=").append(fmt(weather.worldUnitsPerWeatherTexel()))
                    .append(" cloudletRadiusTexels avg/min/max=")
                    .append(fmt(weather.averageCloudletRadiusTexels()))
                    .append("/")
                    .append(fmt(weather.minCloudletRadiusTexels()))
                    .append("/")
                    .append(fmt(weather.maxCloudletRadiusTexels()))
                    .append("\nadaptiveWeatherFootprint=").append(weather.adaptiveWeatherFootprintEnabled())
                    .append(" targetRadiusTexels=").append(fmt(weather.targetRadiusTexels()))
                    .append(" weatherCoverageScale=").append(fmt(VolumetricCloudDebugConfig.weatherCoverageScale()))
                    .append(" sentinelHeights=")
                    .append(VolumetricCloudDebugConfig.sentinelHeightsEnabled() ? "on" : "off")
                    .append("\nadaptiveFootprintScale min/avg/max=")
                    .append(fmt(weather.minAdaptiveFootprintScale()))
                    .append("/")
                    .append(fmt(weather.averageAdaptiveFootprintScale()))
                    .append("/")
                    .append(fmt(weather.maxAdaptiveFootprintScale()))
                    .append("\neffectiveRadiusTexels min/avg/max=")
                    .append(fmt(weather.minEffectiveRadiusTexels()))
                    .append("/")
                    .append(fmt(weather.averageEffectiveRadiusTexels()))
                    .append("/")
                    .append(fmt(weather.maxEffectiveRadiusTexels()))
                    .append("\nbaseHeightRange=").append(weather.textureStats().baseHeightRange())
                    .append(" topHeightRange=").append(weather.textureStats().topHeightRange())
                    .append("\nthicknessRange=").append(weather.textureStats().thicknessRange())
                    .append(" thicknessMinAvgMax=").append(weather.textureStats().thicknessMinAvgMax())
                    .append("\nthicknessPercent below4/below8/below12/above20=")
                    .append(weather.textureStats().thicknessBelow4Percent()).append("/")
                    .append(weather.textureStats().thicknessBelow8Percent()).append("/")
                    .append(weather.textureStats().thicknessBelow12Percent()).append("/")
                    .append(weather.textureStats().thicknessAbove20Percent())
                    .append("\nheightVariance base/top/thickness=")
                    .append(weather.textureStats().baseHeightVariance()).append("/")
                    .append(weather.textureStats().topHeightVariance()).append("/")
                    .append(weather.textureStats().thicknessVariance())
                    .append("\ntexelsWithMultipleCloudlets=").append(weather.textureStats().multiCloudletTexels());

            builder.append("\n\nDepth and composite")
                    .append("\nsceneRayLimitEnabled=").append(depthComposite.sceneRayLimitEnabled())
                    .append(" depthAwareCompositeEnabled=").append(depthComposite.depthAwareCompositeEnabled())
                    .append("\ndepthTolerance=").append(depthComposite.depthTolerance())
                    .append("\ndownscaleFactor=").append(fmt(depthComposite.downscaleFactor()))
                    .append(" upsampleMode=").append(depthComposite.upsampleMode())
                    .append("\nrejectionLogicActive=").append(depthComposite.rejectionLogicActive())
                    .append(" rejectedSamples=").append(depthComposite.rejectedSamples());
            return builder.toString();
        }
    }

    public record FieldInfo(
            UUID fieldId,
            String sourceKind,
            UUID morphologyGroupId,
            int morphologyIndex,
            int morphologyCount,
            String morphologyFamily,
            String morphologyStage,
            double centerX,
            double centerY,
            double centerZ,
            double windX,
            double windZ,
            float radius,
            float baseY,
            float topY,
            double minX,
            double maxX,
            double minZ,
            double maxZ,
            int activeCloudletCount,
            int targetCloudletCount,
            int renderedCloudletCount,
            int skippedCloudletCount,
            boolean skippedByMaxCells,
            boolean skippedByRenderCap,
            boolean skippedByLodHydration,
            boolean skippedByDistanceOrCulling,
            String skipReason
    ) {
        public FieldInfo {
            sourceKind = safe(sourceKind);
            morphologyGroupId = morphologyGroupId == null ? new UUID(0L, 0L) : morphologyGroupId;
            morphologyFamily = safe(morphologyFamily);
            morphologyStage = safe(morphologyStage);
            skipReason = safe(skipReason);
        }

        static FieldInfo unknown(String reason) {
            return new FieldInfo(
                    new UUID(0L, 0L),
                    "unknown",
                    new UUID(0L, 0L),
                    0,
                    1,
                    "unknown",
                    "unknown",
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0F,
                    0.0F,
                    0.0F,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0,
                    0,
                    0,
                    0,
                    false,
                    false,
                    false,
                    false,
                    reason
            );
        }

        String shortLine() {
            return shortId(fieldId)
                    + " source=" + sourceKind
                    + " morphology=" + morphologyFamily + "/" + morphologyStage
                    + " group=" + shortId(morphologyGroupId)
                    + " member=" + morphologyIndex + "/" + morphologyCount
                    + " center=" + fmt(centerX) + "," + fmt(centerY) + "," + fmt(centerZ)
                    + " windXZ=" + fmt(windX) + "," + fmt(windZ)
                    + " radius=" + fmt(radius)
                    + " baseTop=" + fmt(baseY) + ".." + fmt(topY)
                    + " xz=[" + fmt(minX) + ".." + fmt(maxX) + "," + fmt(minZ) + ".." + fmt(maxZ) + "]"
                    + " cloudlets active=" + activeCloudletCount
                    + " target=" + targetCloudletCount
                    + " rendered=" + renderedCloudletCount
                    + " skipped=" + skippedCloudletCount
                    + " maxCells=" + skippedByMaxCells
                    + " renderCap=" + skippedByRenderCap
                    + " lodHydration=" + skippedByLodHydration
                    + " distanceCull=" + skippedByDistanceOrCulling
                    + " reason=" + skipReason;
        }
    }

    public record CellInfo(
            int index,
            double x,
            double z,
            float baseY,
            float topY,
            float radiusMajor,
            float radiusMinor,
            float density,
            int cloudProfile,
            String envelopeRole,
            float verticalDevelopment,
            float precipitation
    ) {
        public CellInfo {
            envelopeRole = safe(envelopeRole);
        }

        String shortLine() {
            return "#" + index
                    + " center=" + fmt(x) + "," + fmt((baseY + topY) * 0.5F) + "," + fmt(z)
                    + " radius=" + fmt(radiusMajor) + "/" + fmt(radiusMinor)
                    + " baseTop=" + fmt(baseY) + ".." + fmt(topY)
                    + " density=" + fmt(density)
                    + " profile=" + cloudProfile
                    + " role=" + envelopeRole
                    + " vertical=" + fmt(verticalDevelopment)
                    + " precipitation=" + fmt(precipitation)
                    + " coverage=unknown";
        }
    }

    public record RenderBounds(
            double minX,
            double maxX,
            double minZ,
            double maxZ,
            float baseY,
            float topY
    ) {
        static RenderBounds unknown() {
            return new RenderBounds(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Float.NaN, Float.NaN);
        }
    }

    public record WeatherInfo(
            double originX,
            double originZ,
            float extent,
            String textureSize,
            int cloudletsSplatted,
            int cloudletsDroppedBeforeSplat,
            float slabBaseY,
            float slabTopY,
            float inputMinBaseY,
            float inputMaxTopY,
            InputHeightStats inputHeightStats,
            boolean overlapCollapsedToTexelHeightRange,
            float worldUnitsPerWeatherTexel,
            float averageCloudletRadiusTexels,
            float minCloudletRadiusTexels,
            float maxCloudletRadiusTexels,
            boolean adaptiveWeatherFootprintEnabled,
            float targetRadiusTexels,
            float averageAdaptiveFootprintScale,
            float minAdaptiveFootprintScale,
            float maxAdaptiveFootprintScale,
            float averageEffectiveRadiusTexels,
            float minEffectiveRadiusTexels,
            float maxEffectiveRadiusTexels,
            WeatherTextureStats textureStats
    ) {
        public WeatherInfo {
            textureSize = safe(textureSize);
            inputHeightStats = inputHeightStats == null ? InputHeightStats.unknown() : inputHeightStats;
            textureStats = textureStats == null ? WeatherTextureStats.unknown("not_captured") : textureStats;
        }

        static WeatherInfo unknown() {
            return new WeatherInfo(
                    Double.NaN,
                    Double.NaN,
                    Float.NaN,
                    "unknown",
                    0,
                    0,
                    Float.NaN,
                    Float.NaN,
                    Float.NaN,
                    Float.NaN,
                    InputHeightStats.unknown(),
                    true,
                    Float.NaN,
                    Float.NaN,
                    Float.NaN,
                    Float.NaN,
                    false,
                    Float.NaN,
                    Float.NaN,
                    Float.NaN,
                    Float.NaN,
                    Float.NaN,
                    Float.NaN,
                    Float.NaN,
                    WeatherTextureStats.unknown("unknown")
            );
        }

        WeatherInfo withTextureStats(WeatherTextureStats stats) {
            return new WeatherInfo(
                    originX,
                    originZ,
                    extent,
                    textureSize,
                    cloudletsSplatted,
                    cloudletsDroppedBeforeSplat,
                    slabBaseY,
                    slabTopY,
                    inputMinBaseY,
                    inputMaxTopY,
                    inputHeightStats,
                    overlapCollapsedToTexelHeightRange,
                    worldUnitsPerWeatherTexel,
                    averageCloudletRadiusTexels,
                    minCloudletRadiusTexels,
                    maxCloudletRadiusTexels,
                    adaptiveWeatherFootprintEnabled,
                    targetRadiusTexels,
                    averageAdaptiveFootprintScale,
                    minAdaptiveFootprintScale,
                    maxAdaptiveFootprintScale,
                    averageEffectiveRadiusTexels,
                    minEffectiveRadiusTexels,
                    maxEffectiveRadiusTexels,
                    stats
            );
        }
    }

    public record InputHeightStats(
            float minBaseY,
            float maxBaseY,
            float minTopY,
            float maxTopY,
            float minThickness,
            float averageThickness,
            float maxThickness
    ) {
        static InputHeightStats unknown() {
            return new InputHeightStats(
                    Float.NaN,
                    Float.NaN,
                    Float.NaN,
                    Float.NaN,
                    Float.NaN,
                    Float.NaN,
                    Float.NaN
            );
        }
    }

    public record WeatherTextureStats(
            int activeTexels,
            String coverageRange,
            String baseHeightRange,
            String topHeightRange,
            String thicknessRange,
            String thicknessMinAvgMax,
            String thicknessBelow4Percent,
            String thicknessBelow8Percent,
            String thicknessBelow12Percent,
            String thicknessAbove20Percent,
            String baseHeightVariance,
            String topHeightVariance,
            String thicknessVariance,
            String activeTexelPercent,
            String estimatedTexelsPerCloudlet,
            String multiCloudletTexels
    ) {
        public WeatherTextureStats {
            coverageRange = safe(coverageRange);
            baseHeightRange = safe(baseHeightRange);
            topHeightRange = safe(topHeightRange);
            thicknessRange = safe(thicknessRange);
            thicknessMinAvgMax = safe(thicknessMinAvgMax);
            thicknessBelow4Percent = safe(thicknessBelow4Percent);
            thicknessBelow8Percent = safe(thicknessBelow8Percent);
            thicknessBelow12Percent = safe(thicknessBelow12Percent);
            thicknessAbove20Percent = safe(thicknessAbove20Percent);
            baseHeightVariance = safe(baseHeightVariance);
            topHeightVariance = safe(topHeightVariance);
            thicknessVariance = safe(thicknessVariance);
            activeTexelPercent = safe(activeTexelPercent);
            estimatedTexelsPerCloudlet = safe(estimatedTexelsPerCloudlet);
            multiCloudletTexels = safe(multiCloudletTexels);
        }

        static WeatherTextureStats unknown(String reason) {
            String value = "unknown" + (reason == null || reason.isBlank() ? "" : "(" + reason + ")");
            return new WeatherTextureStats(
                    0,
                    value,
                    value,
                    value,
                    value,
                    value,
                    value,
                    value,
                    value,
                    value,
                    value,
                    value,
                    value,
                    value,
                    value,
                    "unknown"
            );
        }
    }

    public record DepthCompositeInfo(
            boolean sceneRayLimitEnabled,
            boolean depthAwareCompositeEnabled,
            String depthTolerance,
            float downscaleFactor,
            String upsampleMode,
            boolean rejectionLogicActive,
            String rejectedSamples
    ) {
        public DepthCompositeInfo {
            depthTolerance = safe(depthTolerance);
            upsampleMode = safe(upsampleMode);
            rejectedSamples = safe(rejectedSamples);
        }

        static DepthCompositeInfo unknown() {
            return new DepthCompositeInfo(false, false, "unknown", Float.NaN, "unknown", false, "unknown");
        }
    }

    public static List<CellInfo> cellInfos(List<VolumetricRenderCell> cells) {
        if (cells == null || cells.isEmpty()) {
            return List.of();
        }
        List<CellInfo> infos = new ArrayList<>(cells.size());
        for (int i = 0; i < cells.size(); i++) {
            infos.add(cellInfo(i, cells.get(i)));
        }
        return List.copyOf(infos);
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private static String shortId(UUID id) {
        if (id == null) {
            return "unknown";
        }
        String text = id.toString();
        return text.length() <= 8 ? text : text.substring(0, 8);
    }

    private static String fmt(double value) {
        if (!Double.isFinite(value)) {
            return "unknown";
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String fmt(float value) {
        if (!Float.isFinite(value)) {
            return "unknown";
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    public static String compositeName(CloudFieldCompositeDebugMode mode) {
        return mode == null ? "unknown" : mode.serializedName();
    }

    private static WeatherTextureStats readWeatherTextureStats(
            RenderTarget target,
            float slabBaseY,
            float slabTopY,
            int cloudletsSplatted
    ) {
        if (!RenderSystem.isOnRenderThread()) {
            return WeatherTextureStats.unknown("not_render_thread");
        }
        if (target == null || target.width <= 0 || target.height <= 0 || target.getColorTextureId() <= 0) {
            return WeatherTextureStats.unknown("no_weather_target");
        }
        if (!Float.isFinite(slabBaseY) || !Float.isFinite(slabTopY) || slabTopY <= slabBaseY) {
            return WeatherTextureStats.unknown("invalid_slab");
        }

        int width = target.width;
        int height = target.height;
        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            RenderSystem.bindTexture(target.getColorTextureId());
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        } finally {
            RenderSystem.bindTexture(previousTexture);
        }

        float slabSpan = slabTopY - slabBaseY;
        int activeTexels = 0;
        float minCoverage = Float.POSITIVE_INFINITY;
        float maxCoverage = Float.NEGATIVE_INFINITY;
        float minBase = Float.POSITIVE_INFINITY;
        float maxBase = Float.NEGATIVE_INFINITY;
        float minTop = Float.POSITIVE_INFINITY;
        float maxTop = Float.NEGATIVE_INFINITY;
        float minThickness = Float.POSITIVE_INFINITY;
        float maxThickness = Float.NEGATIVE_INFINITY;
        double baseSum = 0.0D;
        double baseSquareSum = 0.0D;
        double topSum = 0.0D;
        double topSquareSum = 0.0D;
        double thicknessSum = 0.0D;
        double thicknessSquareSum = 0.0D;
        int thicknessBelow4 = 0;
        int thicknessBelow8 = 0;
        int thicknessBelow12 = 0;
        int thicknessAbove20 = 0;

        int pixelCount = width * height;
        for (int pixel = 0; pixel < pixelCount; pixel++) {
            int base = pixel * 4;
            float coverage = (pixels.get(base) & 0xFF) / 255.0F;
            if (coverage <= 0.002F) {
                continue;
            }
            float baseY = slabBaseY + ((pixels.get(base + 1) & 0xFF) / 255.0F) * slabSpan;
            float topY = slabBaseY + ((pixels.get(base + 2) & 0xFF) / 255.0F) * slabSpan;
            float thickness = Math.max(0.0F, topY - baseY);

            activeTexels++;
            minCoverage = Math.min(minCoverage, coverage);
            maxCoverage = Math.max(maxCoverage, coverage);
            minBase = Math.min(minBase, baseY);
            maxBase = Math.max(maxBase, baseY);
            minTop = Math.min(minTop, topY);
            maxTop = Math.max(maxTop, topY);
            minThickness = Math.min(minThickness, thickness);
            maxThickness = Math.max(maxThickness, thickness);
            baseSum += baseY;
            baseSquareSum += (double) baseY * baseY;
            topSum += topY;
            topSquareSum += (double) topY * topY;
            thicknessSum += thickness;
            thicknessSquareSum += (double) thickness * thickness;
            if (thickness < 4.0F) {
                thicknessBelow4++;
            }
            if (thickness < 8.0F) {
                thicknessBelow8++;
            }
            if (thickness < 12.0F) {
                thicknessBelow12++;
            }
            if (thickness > 20.0F) {
                thicknessAbove20++;
            }
        }

        if (activeTexels == 0) {
            return new WeatherTextureStats(
                    0,
                    "none",
                    "none",
                    "none",
                    "none",
                    "none",
                    "0.00%",
                    "0.00%",
                    "0.00%",
                    "0.00%",
                    "0.00",
                    "0.00",
                    "0.00",
                    "0.00%",
                    "0.00",
                    "unknown"
            );
        }

        float activePercent = activeTexels * 100.0F / Math.max(1, pixelCount);
        float texelsPerCloudlet = activeTexels / (float) Math.max(1, cloudletsSplatted);
        double averageBase = baseSum / activeTexels;
        double averageTop = topSum / activeTexels;
        double averageThickness = thicknessSum / activeTexels;
        double baseVariance = Math.max(0.0D, baseSquareSum / activeTexels - averageBase * averageBase);
        double topVariance = Math.max(0.0D, topSquareSum / activeTexels - averageTop * averageTop);
        double thicknessVariance = Math.max(
                0.0D,
                thicknessSquareSum / activeTexels - averageThickness * averageThickness
        );

        return new WeatherTextureStats(
                activeTexels,
                fmt(minCoverage) + ".." + fmt(maxCoverage),
                fmt(minBase) + ".." + fmt(maxBase),
                fmt(minTop) + ".." + fmt(maxTop),
                fmt(minThickness) + ".." + fmt(maxThickness),
                fmt(minThickness) + "/" + fmt(averageThickness) + "/" + fmt(maxThickness),
                fmt(thicknessBelow4 * 100.0F / activeTexels) + "%",
                fmt(thicknessBelow8 * 100.0F / activeTexels) + "%",
                fmt(thicknessBelow12 * 100.0F / activeTexels) + "%",
                fmt(thicknessAbove20 * 100.0F / activeTexels) + "%",
                fmt(baseVariance),
                fmt(topVariance),
                fmt(thicknessVariance),
                fmt(activePercent) + "%",
                fmt(texelsPerCloudlet),
                "unknown"
        );
    }
}
