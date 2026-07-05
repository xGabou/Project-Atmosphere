package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.client.atmosphere.AtmosphereClientState;
import net.Gabou.projectatmosphere.client.render.shader.VolumetricCloudShaders;
import net.Gabou.projectatmosphere.clouds.analytics.CloudCellAnalyticsPass;
import net.Gabou.projectatmosphere.clouds.api.CloudShadowMapAccess;
import net.Gabou.projectatmosphere.clouds.cell.CloudCell;
import net.Gabou.projectatmosphere.clouds.cell.client.ClientCloudCellCache;
import net.Gabou.projectatmosphere.clouds.client.ClientCloudFieldCache;
import net.Gabou.projectatmosphere.clouds.client.render.field.CloudFieldCompositeRenderer;
import net.Gabou.projectatmosphere.clouds.client.render.field.CloudFieldVolumeRenderConfig;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldRendererInput;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldSnapshot;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldSourceKind;
import net.Gabou.projectatmosphere.clouds.field.CloudletId;
import net.Gabou.projectatmosphere.clouds.field.CloudletLayout;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * PA-native volumetric cloud pipeline hook. Replaces per-field AABB raymarch
 * passes with: cell weather-map splat, ground shadow pass, one global
 * raymarch with temporal reprojection, and a depth-aware composite.
 */
public final class VolumetricCloudRenderHook {
    private static volatile boolean runtimeEnabled = true;
    private static volatile String lastStatus = "not_rendered_yet";
    private static long frameCounter;
    private static long lastErrorLogMillis;
    private static long lastStatusLogMillis;
    private static String lastLoggedStatusKind = "";

    private VolumetricCloudRenderHook() {
    }

    /** True when the volumetric pipeline owns cloud visuals this session. */
    public static boolean isActive() {
        if (!runtimeEnabled) {
            return false;
        }
        try {
            return AtmoCommonConfig.CLOUD_VOLUMETRIC_RENDERER_ENABLED.get()
                    && AtmoCommonConfig.CLOUD_FIELD_RENDERER_ENABLED.get();
        } catch (Exception exception) {
            return false;
        }
    }

    public static String status() {
        return "volumetricActive=" + isActive()
                + " status=" + lastStatus
                + " cells=" + ClientCloudCellCache.trackedCellCount()
                + " raymarchGpuMs=" + VolumetricCloudRenderer.lastGpuMilliseconds()
                + " governorScale=" + VolumetricCloudRenderer.governorStepScale()
                + " analytics=" + CloudCellAnalyticsPass.status();
    }

    public static void setRuntimeEnabled(boolean enabled) {
        runtimeEnabled = enabled;
        if (!enabled) {
            lastStatus = "disabled";
            VolumetricCloudRenderer.invalidateHistory();
            VolumetricCloudRenderTargets.shutdown();
            CloudShadowMapAccess.clear();
            CameraCloudDensityTracker.reset();
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        if (!isActive() || !VolumetricCloudShaders.isReady()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        try {
            renderFrame(event, minecraft, level);
        } catch (Throwable throwable) {
            runtimeEnabled = false;
            CloudFieldCompositeRenderer.restoreRenderState();
            if (minecraft.getMainRenderTarget() != null) {
                minecraft.getMainRenderTarget().bindWrite(true);
            }
            lastStatus = "render_exception:" + throwable.getClass().getSimpleName();
            long now = System.currentTimeMillis();
            if (now - lastErrorLogMillis >= 10_000L) {
                lastErrorLogMillis = now;
                ProjectAtmosphere.LOGGER.error(
                        "[VolumetricClouds] render exception; volumetric pass disabled for this session", throwable);
            }
        }
    }

    private static void renderFrame(RenderLevelStageEvent event, Minecraft minecraft, ClientLevel level) {
        frameCounter++;
        float partialTick = event.getPartialTick();
        Vec3 cameraPos = event.getCamera().getPosition();
        String dimensionId = level.dimension().location().toString();
        long gameTime = level.getGameTime();
        float worldTimeTicks = (gameTime % 1_728_000L) + partialTick;

        // 1. Gather render cells. Spawned/native PA clouds still arrive as
        // CloudField snapshots; prefer them over the newer autonomous cell
        // simulation so manual cloud spawns render the cloud the user asked
        // for instead of unrelated background cells.
        List<CloudCell> presentedCells = ClientCloudCellCache.presentCells(dimensionId, gameTime, partialTick);
        List<VolumetricRenderCell> renderCells = new ArrayList<>();
        CloudFieldRendererInput input = ClientCloudFieldCache.createRendererInput(cameraPos, gameTime, partialTick);
        List<CloudFieldSnapshot> fieldSnapshots = new ArrayList<>(input.fields());
        fieldSnapshots.sort(Comparator.comparingDouble(snapshot -> snapshot.center().distanceToSqr(cameraPos)));
        List<VolumetricCloudFrameDiagnostics.FieldInfo> fieldDiagnostics =
                buildFieldDiagnostics(fieldSnapshots, dimensionId);
        for (CloudFieldSnapshot snapshot : fieldSnapshots) {
            if (renderCells.size() >= CloudWeatherMapRenderer.MAX_CELLS) {
                break;
            }
            if (snapshot != null && snapshot.hasVisibleClouds()
                    && (dimensionId.isBlank() || dimensionId.equals(snapshot.dimensionId()))) {
                addFieldRenderCells(snapshot, renderCells);
            }
        }

        String renderSource = renderCells.isEmpty() ? "none" : "fields";
        if (renderCells.isEmpty() && !presentedCells.isEmpty()) {
            presentedCells.sort(Comparator.comparingDouble(cell -> cell.distanceSqrTo(cameraPos.x(), cameraPos.z())));
            for (CloudCell cell : presentedCells) {
                if (renderCells.size() >= CloudWeatherMapRenderer.MAX_CELLS) {
                    break;
                }
                if (cell.isVisuallyRelevant()) {
                    renderCells.add(VolumetricRenderCell.fromCell(cell));
                }
            }
            renderSource = renderCells.isEmpty() ? "none" : "cells";
        }
        boolean renderingFields = "fields".equals(renderSource);

        // 2. Interior fog tracking runs even when nothing is visible so the
        // whiteout releases smoothly after leaving a cloud.
        CameraCloudDensityTracker.update(renderingFields ? List.<CloudCell>of() : presentedCells, cameraPos);

        AtmosphereClientState.Snapshot atmosphere = AtmosphereClientState.getSnapshot();
        float regionalCoverage = Mth.clamp((atmosphere.cloudCover() - 0.45F) * 1.4F, 0.0F, 1.0F);
        float regionalEnergy = Mth.clamp(atmosphere.rainIntensity() * 0.8F, 0.0F, 1.0F);

        if (renderCells.isEmpty() && regionalCoverage <= 0.01F) {
            setStatus("no_clouds", "cloudCover=" + atmosphere.cloudCover()
                    + " cells=" + presentedCells.size()
                    + " fields=" + fieldSnapshots.size()
                    + " source=" + renderSource);
            VolumetricCloudRenderer.invalidateHistory();
            return;
        }

        AtmoCommonConfig.CloudRaymarchQuality configuredQuality = AtmoCommonConfig.CLOUD_RAYMARCH_QUALITY.get();
        VolumetricQualityProfile profile = VolumetricQualityProfile.forQuality(configuredQuality);
        if (VolumetricCloudDebugConfig.fullResolutionEnabled() && profile.resolutionScale() < 1.0F) {
            profile = profile.withResolutionScale(1.0F);
        }
        VolumetricCloudLighting.Frame lighting = VolumetricCloudLighting.resolve(level, cameraPos, partialTick);

        // 3. Weather map splat (every frame; cells move every frame). Spawned
        // field clouds never inherit the rain-coupled regional sheet, so they
        // stay identical when the camera moves between rain and clear air.
        boolean includeRegionalLayer = !renderingFields;
        CloudWeatherMapRenderer.Result weather = CloudWeatherMapRenderer.render(
                renderCells,
                cameraPos.x(),
                cameraPos.z(),
                regionalCoverage,
                regionalEnergy,
                includeRegionalLayer,
                worldTimeTicks,
                profile.weatherMapSize()
        );
        if (!weather.rendered()) {
            setStatus("weather_map_unavailable", "");
            return;
        }

        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        Matrix4f projection = new Matrix4f(event.getProjectionMatrix());
        Matrix4f viewRotation = cameraViewRotation(event);
        Vector3f cameraPosF = new Vector3f((float) cameraPos.x(), (float) cameraPos.y(), (float) cameraPos.z());

        // 4. Ground shadows: GPU-resident end to end. Skipped when an external
        // shader pack owns the pipeline; the texture stays published for it.
        boolean shadowsEnabled = AtmoCommonConfig.ENABLE_CLOUD_SHADOW_MAP.get()
                && AtmoCommonConfig.ENABLE_VOLUMETRIC_GROUND_SHADOWS.get();
        float groundY = (float) level.getSeaLevel();
        if (shadowsEnabled) {
            if (frameCounter % Math.max(1, profile.shadowUpdateInterval()) == 0L) {
                VolumetricCloudShadowRenderer.renderShadowMap(
                        weather,
                        VolumetricCloudRenderTargets.prepareWeatherTarget(profile.weatherMapSize()),
                        lighting.lightDirection(),
                        cameraPos.x(),
                        cameraPos.z(),
                        groundY,
                        1.0F
                );
            }
            VolumetricCloudShadowRenderer.publishSnapshot(
                    renderingFields ? List.<CloudCell>of() : presentedCells,
                    lighting.lightDirection(),
                    cameraPos.x(),
                    cameraPos.z(),
                    weather.slabBaseY(),
                    groundY
            );
            // Do not run the legacy fullscreen shadow post here. It samples
            // mainTarget's depth texture while that same texture is attached
            // to the draw framebuffer, an undefined OpenGL feedback loop that
            // produces a black fullscreen oval on affected drivers. The
            // generated shadow map remains published for shader integrations;
            // an in-engine post must first copy scene depth to a detached
            // texture before sampling it.
        }

        // 5. Raymarch with temporal reprojection.
        Vector3f windVec = renderingFields ? defaultWind() : averageWind(presentedCells);
        VolumetricCloudRenderer.FunnelUniforms funnels = renderingFields
                ? VolumetricCloudRenderer.FunnelUniforms.NONE
                : buildFunnels(presentedCells, cameraPos);
        VolumetricCloudRenderer.Tuning tuning = renderingFields
                ? VolumetricCloudRenderer.Tuning.FIELDS
                : VolumetricCloudRenderer.Tuning.CELLS;
        boolean rendered = VolumetricCloudRenderer.render(
                mainTarget,
                weather,
                lighting,
                projection,
                viewRotation,
                cameraPosF,
                worldTimeTicks,
                windVec,
                profile,
                Math.max(300.0F, AtmoCommonConfig.CLOUD_RENDER_DISTANCE.get()),
                funnels,
                tuning,
                VolumetricCloudDebugConfig.sceneRayLimitEnabled()
        );
        if (!rendered) {
            setStatus("raymarch_not_ready", "");
            if (mainTarget != null) {
                mainTarget.bindWrite(true);
            }
            return;
        }

        // 6. Depth-aware upsample composite into the main target. The debug
        // mode from /pa system cloudFieldVolume composite <mode> applies here
        // too, so the raw volumetric buffer can be inspected in-game.
        boolean composited = CloudFieldCompositeRenderer.composite(
                VolumetricCloudRenderTargets.currentCloudTarget(),
                mainTarget,
                CloudFieldVolumeRenderConfig.compositeDebugMode(),
                VolumetricCloudDebugConfig.depthCompositeEnabled()
        );
        RenderTarget cloudTarget = VolumetricCloudRenderTargets.currentCloudTarget();
        if (!composited && mainTarget != null) {
            mainTarget.bindWrite(true);
        }
        VolumetricCloudRenderer.finishFrame();
        recordFrameDiagnostics(
                frameCounter,
                gameTime,
                partialTick,
                cameraPos,
                configuredQuality,
                profile,
                mainTarget,
                cloudTarget,
                weather,
                fieldSnapshots.size(),
                fieldDiagnostics,
                renderCells,
                composited
        );

        // 7. GPU analytics (Medium+ with GL 4.3): measure per-cell footprints
        // from the weather map and hand digests to the merge/split logic.
        if (profile.analyticsEnabled() && !renderingFields && !presentedCells.isEmpty()) {
            CloudCellAnalyticsPass.tick(
                    presentedCells,
                    VolumetricCloudRenderTargets.prepareWeatherTarget(profile.weatherMapSize()),
                    weather,
                    gameTime
            );
        }

        setStatus("rendered", "cells=" + renderCells.size()
                + " source=" + renderSource
                + " syncedCells=" + presentedCells.size()
                + " syncedFields=" + fieldSnapshots.size()
                + " weatherCells=" + weather.cellCount()
                + " fieldCloudlets=" + fieldCloudletCount(fieldSnapshots, dimensionId)
                + " regionalSource=" + (renderingFields
                        ? "disabled_for_fields"
                        : (regionalCoverage > 0.01F ? "enabled" : "none"))
                + " debug[depthComposite=" + VolumetricCloudDebugConfig.depthCompositeEnabled()
                + " sceneRayLimit=" + VolumetricCloudDebugConfig.sceneRayLimitEnabled()
                + " coveragePretest=" + VolumetricCloudDebugConfig.coveragePretestEnabled()
                + " pretestSamples=" + VolumetricCloudDebugConfig.coveragePretestSamples()
                + " pretestThreshold=" + String.format(Locale.ROOT, "%.4f", VolumetricCloudDebugConfig.coveragePretestThreshold())
                + " pretestDilation=" + VolumetricCloudDebugConfig.coveragePretestDilation()
                + " adaptiveWeatherFootprint=" + VolumetricCloudDebugConfig.adaptiveWeatherFootprintEnabled()
                + " history=" + VolumetricCloudDebugConfig.historyEnabled()
                + " sentinelHeights=" + VolumetricCloudDebugConfig.sentinelHeightsEnabled()
                + " weatherCoverageScale=" + String.format(Locale.ROOT, "%.2f", VolumetricCloudDebugConfig.weatherCoverageScale())
                + " fullres=" + VolumetricCloudDebugConfig.fullResolutionEnabled() + "]"
                + " historyValid=" + VolumetricCloudRenderer.lastHistoryValid()
                + " tuning[" + tuning.summary() + "]"
                + " regionalCoverage=" + String.format(java.util.Locale.ROOT, "%.3f", regionalCoverage)
                + " cloudCover=" + String.format(java.util.Locale.ROOT, "%.3f", atmosphere.cloudCover())
                + " slab=" + String.format(java.util.Locale.ROOT, "%.1f..%.1f", weather.slabBaseY(), weather.slabTopY())
                + " camY=" + String.format(java.util.Locale.ROOT, "%.1f", cameraPos.y())
                + " lightDir=" + format(lighting.lightDirection())
                + " lightColor=" + format(lighting.lightColor())
                + " ambTop=" + format(lighting.ambientTop())
                + " ambBot=" + format(lighting.ambientBottom())
                + " composited=" + composited
                + " funnels=" + funnels.count()
                + " gpuMs=" + VolumetricCloudRenderer.lastGpuMilliseconds());
    }

    /**
     * Records the frame status and mirrors it to the log, rate-limited so the
     * pipeline state is reconstructable from latest.log without a debugger:
     * immediately on status-kind changes, then at most every 5 seconds.
     */
    private static void setStatus(String kind, String detail) {
        lastStatus = detail.isEmpty() ? kind : kind + " " + detail;
        long now = System.currentTimeMillis();
        if (!kind.equals(lastLoggedStatusKind) || now - lastStatusLogMillis >= 5_000L) {
            lastStatusLogMillis = now;
            lastLoggedStatusKind = kind;
            ProjectAtmosphere.LOGGER.info("[VolumetricClouds] status {}", lastStatus);
        }
    }

    private static String format(org.joml.Vector3f v) {
        return String.format(java.util.Locale.ROOT, "(%.2f,%.2f,%.2f)", v.x, v.y, v.z);
    }

    private static void addFieldRenderCells(
            CloudFieldSnapshot snapshot,
            List<VolumetricRenderCell> renderCells
    ) {
        if (snapshot == null || renderCells.size() >= CloudWeatherMapRenderer.MAX_CELLS) {
            return;
        }
        int cloudletCount = renderCloudletCount(snapshot);
        for (int i = 0; i < cloudletCount; i++) {
            if (renderCells.size() >= CloudWeatherMapRenderer.MAX_CELLS) {
                return;
            }
            renderCells.add(VolumetricRenderCell.fromFieldCloudlet(
                    snapshot,
                    CloudletLayout.generate(snapshot, CloudletId.of(i))
            ));
        }
    }

    private static int renderCloudletCount(CloudFieldSnapshot snapshot) {
        if (snapshot == null || !snapshot.hasVisibleClouds()) {
            return 0;
        }
        int active = snapshot.dynamicCloudletCount();
        int target = Math.max(active, snapshot.targetCloudletCount());
        CloudFieldSourceKind sourceKind = snapshot.sourceKind();
        if (sourceKind == CloudFieldSourceKind.MANUAL_DEBUG) {
            return Math.max(active, Math.min(target, 48));
        }
        if (sourceKind == CloudFieldSourceKind.PA_REGION || sourceKind == CloudFieldSourceKind.PA_CLUSTER) {
            return Math.max(active, Math.min(target, 32));
        }
        return Math.max(active, Math.min(target, 24));
    }

    private static List<VolumetricCloudFrameDiagnostics.FieldInfo> buildFieldDiagnostics(
            List<CloudFieldSnapshot> snapshots,
            String dimensionId
    ) {
        if (snapshots == null || snapshots.isEmpty()) {
            return List.of();
        }
        List<VolumetricCloudFrameDiagnostics.FieldInfo> diagnostics = new ArrayList<>(snapshots.size());
        int simulatedUsedCells = 0;
        for (CloudFieldSnapshot snapshot : snapshots) {
            if (snapshot == null) {
                diagnostics.add(VolumetricCloudFrameDiagnostics.FieldInfo.unknown("null_snapshot"));
                continue;
            }

            int activeCloudlets = snapshot.activeCloudletCount();
            int targetCloudlets = snapshot.targetCloudletCount();
            boolean lodHydrationSkipped = targetCloudlets > activeCloudlets;
            boolean dimensionMatches = dimensionId == null
                    || dimensionId.isBlank()
                    || dimensionId.equals(snapshot.dimensionId());
            if (!dimensionMatches) {
                diagnostics.add(VolumetricCloudFrameDiagnostics.fieldInfo(
                        snapshot,
                        0,
                        Math.max(0, targetCloudlets),
                        false,
                        false,
                        lodHydrationSkipped,
                        false,
                        "wrong_dimension"
                ));
                continue;
            }
            if (!snapshot.hasVisibleClouds()) {
                diagnostics.add(VolumetricCloudFrameDiagnostics.fieldInfo(
                        snapshot,
                        0,
                        Math.max(0, targetCloudlets),
                        false,
                        false,
                        lodHydrationSkipped,
                        false,
                        "not_visible"
                ));
                continue;
            }

            int requestedByRenderer = renderCloudletCount(snapshot);
            int rendered = 0;
            boolean skippedByMaxCells = false;
            if (simulatedUsedCells >= CloudWeatherMapRenderer.MAX_CELLS) {
                skippedByMaxCells = requestedByRenderer > 0;
            } else {
                int remaining = CloudWeatherMapRenderer.MAX_CELLS - simulatedUsedCells;
                rendered = Math.min(requestedByRenderer, remaining);
                skippedByMaxCells = requestedByRenderer > remaining;
                simulatedUsedCells += rendered;
            }

            boolean skippedByRenderCap = targetCloudlets > requestedByRenderer;
            int skippedCloudlets = Math.max(0, targetCloudlets - rendered);
            String reason = "rendered";
            if (rendered <= 0 && skippedByMaxCells) {
                reason = "max_cells";
            } else if (skippedByMaxCells || skippedByRenderCap || lodHydrationSkipped) {
                reason = "partial";
            }

            diagnostics.add(VolumetricCloudFrameDiagnostics.fieldInfo(
                    snapshot,
                    rendered,
                    skippedCloudlets,
                    skippedByMaxCells,
                    skippedByRenderCap,
                    lodHydrationSkipped,
                    false,
                    reason
            ));
        }
        return List.copyOf(diagnostics);
    }

    private static void recordFrameDiagnostics(
            long frameIndex,
            long gameTime,
            float partialTick,
            Vec3 cameraPos,
            AtmoCommonConfig.CloudRaymarchQuality configuredQuality,
            VolumetricQualityProfile profile,
            RenderTarget mainTarget,
            RenderTarget cloudTarget,
            CloudWeatherMapRenderer.Result weather,
            int fieldsReceived,
            List<VolumetricCloudFrameDiagnostics.FieldInfo> fieldDiagnostics,
            List<VolumetricRenderCell> renderCells,
            boolean composited
    ) {
        VolumetricCloudFrameDiagnostics.RenderBounds bounds =
                VolumetricCloudFrameDiagnostics.boundsForCells(renderCells);
        int droppedBeforeSplat = 0;
        if (fieldDiagnostics != null) {
            for (VolumetricCloudFrameDiagnostics.FieldInfo field : fieldDiagnostics) {
                droppedBeforeSplat += Math.max(0, field.skippedCloudletCount());
            }
        }
        String qualityName = configuredQuality == null
                ? "unknown"
                : configuredQuality.name().toLowerCase(Locale.ROOT);
        boolean sceneDepthAvailable = mainTarget != null && mainTarget.getDepthTextureId() > 0;
        RenderTarget weatherTarget = VolumetricCloudRenderTargets.weatherTargetOrNull();
        String weatherTextureSize = weatherTarget == null
                ? profile.weatherMapSize() + "x" + profile.weatherMapSize() + " requested"
                : VolumetricCloudFrameDiagnostics.targetSize(weatherTarget);
        float worldUnitsPerWeatherTexel = CloudWeatherMapRenderer.WEATHER_EXTENT
                / Math.max(1, profile.weatherMapSize());
        float averageCloudletRadiusTexels = Float.NaN;
        float minCloudletRadiusTexels = Float.NaN;
        float maxCloudletRadiusTexels = Float.NaN;
        if (renderCells != null && !renderCells.isEmpty() && worldUnitsPerWeatherTexel > 0.0F) {
            float totalRadiusTexels = 0.0F;
            float minRadiusTexels = Float.POSITIVE_INFINITY;
            float maxRadiusTexels = Float.NEGATIVE_INFINITY;
            int radiusSamples = 0;
            for (VolumetricRenderCell cell : renderCells) {
                if (cell == null) {
                    continue;
                }
                float averageRadius = (cell.radiusMajor() + cell.radiusMinor()) * 0.5F;
                float radiusTexels = averageRadius / worldUnitsPerWeatherTexel;
                if (!Float.isFinite(radiusTexels)) {
                    continue;
                }
                totalRadiusTexels += radiusTexels;
                minRadiusTexels = Math.min(minRadiusTexels, radiusTexels);
                maxRadiusTexels = Math.max(maxRadiusTexels, radiusTexels);
                radiusSamples++;
            }
            if (radiusSamples > 0) {
                averageCloudletRadiusTexels = totalRadiusTexels / radiusSamples;
                minCloudletRadiusTexels = minRadiusTexels;
                maxCloudletRadiusTexels = maxRadiusTexels;
            }
        }
        VolumetricCloudFrameDiagnostics.WeatherInfo weatherInfo =
                new VolumetricCloudFrameDiagnostics.WeatherInfo(
                        weather.originX(),
                        weather.originZ(),
                        CloudWeatherMapRenderer.WEATHER_EXTENT,
                        weatherTextureSize,
                        weather.cellCount(),
                        droppedBeforeSplat,
                        weather.slabBaseY(),
                        weather.slabTopY(),
                        bounds.baseY(),
                        bounds.topY(),
                        VolumetricCloudFrameDiagnostics.inputHeightStats(renderCells),
                        true,
                        worldUnitsPerWeatherTexel,
                        averageCloudletRadiusTexels,
                        minCloudletRadiusTexels,
                        maxCloudletRadiusTexels,
                        weather.footprintStats().adaptiveEnabled(),
                        weather.footprintStats().targetRadiusTexels(),
                        weather.footprintStats().averageAdaptiveScale(),
                        weather.footprintStats().minAdaptiveScale(),
                        weather.footprintStats().maxAdaptiveScale(),
                        weather.footprintStats().averageEffectiveRadiusTexels(),
                        weather.footprintStats().minEffectiveRadiusTexels(),
                        weather.footprintStats().maxEffectiveRadiusTexels(),
                        VolumetricCloudFrameDiagnostics.WeatherTextureStats.unknown("not_captured")
                );
        VolumetricCloudFrameDiagnostics.DepthCompositeInfo depthComposite =
                new VolumetricCloudFrameDiagnostics.DepthCompositeInfo(
                        sceneDepthAvailable && VolumetricCloudDebugConfig.sceneRayLimitEnabled(),
                        composited && VolumetricCloudDebugConfig.depthCompositeEnabled(),
                        "max(0.00002,(1-selectedDepth)*0.08)",
                        profile.resolutionScale(),
                        VolumetricCloudDebugConfig.depthCompositeEnabled()
                                ? "depth-paired bilinear cloud_field_composite"
                                : "debug raw bilinear cloud_field_composite",
                        VolumetricCloudDebugConfig.depthCompositeEnabled(),
                        "unknown"
                );

        VolumetricCloudFrameDiagnostics.record(new VolumetricCloudFrameDiagnostics.Snapshot(
                System.currentTimeMillis(),
                frameIndex,
                gameTime,
                partialTick,
                cameraPos.x(),
                cameraPos.y(),
                cameraPos.z(),
                isActive(),
                qualityName,
                VolumetricCloudFrameDiagnostics.targetSize(cloudTarget),
                VolumetricCloudFrameDiagnostics.targetSize(mainTarget),
                VolumetricCloudRenderer.lastHistoryValid(),
                VolumetricCloudRenderTargets.isHistoryValid(),
                VolumetricCloudFrameDiagnostics.compositeName(CloudFieldVolumeRenderConfig.compositeDebugMode()),
                mainTarget == null ? -1 : mainTarget.getDepthTextureId(),
                sceneDepthAvailable,
                fieldsReceived,
                fieldDiagnostics == null ? List.of() : fieldDiagnostics,
                renderCells == null ? 0 : renderCells.size(),
                VolumetricCloudFrameDiagnostics.cellInfos(renderCells),
                bounds,
                weatherInfo,
                depthComposite
        ));
    }

    private static int fieldCloudletCount(List<CloudFieldSnapshot> snapshots, String dimensionId) {
        int count = 0;
        for (CloudFieldSnapshot snapshot : snapshots) {
            if (snapshot != null && snapshot.hasVisibleClouds()
                    && (dimensionId.isBlank() || dimensionId.equals(snapshot.dimensionId()))) {
                count += renderCloudletCount(snapshot);
            }
        }
        return count;
    }

    private static Matrix4f cameraViewRotation(RenderLevelStageEvent event) {
        Matrix4f viewRotation = new Matrix4f(event.getPoseStack().last().pose());
        // The volume shader expects camera-relative world->view rotation only.
        // Pose-stack translation here corrupts cloud depth and can composite a
        // local cloud as a fullscreen/oval slab.
        viewRotation.m30(0.0F);
        viewRotation.m31(0.0F);
        viewRotation.m32(0.0F);
        viewRotation.m03(0.0F);
        viewRotation.m13(0.0F);
        viewRotation.m23(0.0F);
        viewRotation.m33(1.0F);
        return viewRotation;
    }

    private static Vector3f defaultWind() {
        return new Vector3f(0.015F, 0.0F, 0.006F);
    }

    private static Vector3f averageWind(List<CloudCell> cells) {
        if (cells == null || cells.isEmpty()) {
            return defaultWind();
        }
        double x = 0.0D;
        double z = 0.0D;
        for (CloudCell cell : cells) {
            x += cell.wind().x();
            z += cell.wind().z();
        }
        return new Vector3f((float) (x / cells.size()), 0.0F, (float) (z / cells.size()));
    }

    /** Picks the two strongest nearby funnels for the analytic SDF slots. */
    private static VolumetricCloudRenderer.FunnelUniforms buildFunnels(List<CloudCell> cells, Vec3 cameraPos) {
        if (cells == null || cells.isEmpty()) {
            return VolumetricCloudRenderer.FunnelUniforms.NONE;
        }
        List<CloudCell> funnelCells = new ArrayList<>(2);
        for (CloudCell cell : cells) {
            if (cell.funnelStrength() > 0.02F) {
                funnelCells.add(cell);
            }
        }
        if (funnelCells.isEmpty()) {
            return VolumetricCloudRenderer.FunnelUniforms.NONE;
        }
        funnelCells.sort(Comparator.comparingDouble(cell -> cell.distanceSqrTo(cameraPos.x(), cameraPos.z())));

        float[] f0a = new float[4];
        float[] f0b = new float[4];
        float[] f1a = new float[4];
        float[] f1b = new float[4];
        int count = Math.min(2, funnelCells.size());
        fillFunnel(funnelCells.get(0), f0a, f0b);
        if (count > 1) {
            fillFunnel(funnelCells.get(1), f1a, f1b);
        }
        return new VolumetricCloudRenderer.FunnelUniforms(count, f0a, f0b, f1a, f1b);
    }

    private static void fillFunnel(CloudCell cell, float[] a, float[] b) {
        a[0] = (float) cell.x();
        a[1] = (float) cell.z();
        a[2] = cell.baseY() + 4.0F;
        a[3] = cell.funnelGroundY();
        b[0] = Math.max(10.0F, cell.radiusMinor() * 0.16F);
        b[1] = Math.max(3.0F, cell.radiusMinor() * 0.035F);
        b[2] = cell.funnelStrength();
        b[3] = (cell.seed() & 0xFFL) * 0.0246F;
    }
}
