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
import net.Gabou.projectatmosphere.clouds.client.render.ClientCloudRenderOwnership;
import net.Gabou.projectatmosphere.clouds.client.render.CloudRenderStateGuard;
import net.Gabou.projectatmosphere.clouds.client.render.depth.SceneDepthFrame;
import net.Gabou.projectatmosphere.clouds.client.render.depth.SceneDepthResolver;
import net.Gabou.projectatmosphere.clouds.client.render.field.CloudFieldCompositeRenderer;
import net.Gabou.projectatmosphere.clouds.client.render.field.CloudFieldVolumeRenderConfig;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldRendererInput;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldSnapshot;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldSourceKind;
import net.Gabou.projectatmosphere.clouds.field.CloudletId;
import net.Gabou.projectatmosphere.clouds.field.CloudletLayout;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

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
    private static Map<UUID, Integer> previousCloudletAllocations = Map.of();
    private static volatile CloudletBudgetStats lastCloudletBudgetStats = CloudletBudgetStats.empty();
    private static final VolumetricMaterialAdvectionTracker MATERIAL_ADVECTION =
            new VolumetricMaterialAdvectionTracker();

    private VolumetricCloudRenderHook() {
    }

    /** True when the volumetric pipeline owns cloud visuals this session. */
    public static boolean isRuntimeConfigured() {
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

    /** True only when this renderer is the selected base-cloud owner. */
    public static boolean isActive() {
        return ClientCloudRenderOwnership.ownsVolumetricPass(Minecraft.getInstance().level);
    }

    public static String status() {
        return "volumetricActive=" + isActive()
                + " status=" + lastStatus
                + " cells=" + ClientCloudCellCache.trackedCellCount()
                + " raymarchGpuMs=" + VolumetricCloudRenderer.lastGpuMilliseconds()
                + " governorScale=" + VolumetricCloudRenderer.governorStepScale()
                + " cloudlets[" + lastCloudletBudgetStats.summary() + "]"
                + " " + CloudWeatherMapRenderer.cacheStatus()
                + " analytics=" + CloudCellAnalyticsPass.status();
    }

    public static void setRuntimeEnabled(boolean enabled) {
        runtimeEnabled = enabled;
        if (!enabled) {
            lastStatus = "disabled";
            VolumetricCloudRenderer.invalidateHistory();
            VolumetricCloudRenderTargets.shutdown();
            CloudShadowMapAccess.clear();
            ClientCloudVisualDensity.clear();
            CameraCloudDensityTracker.reset();
            previousCloudletAllocations = Map.of();
            lastCloudletBudgetStats = CloudletBudgetStats.empty();
            MATERIAL_ADVECTION.reset();
            VolumetricMaterialDomainDiagnostics.reset();
        }
    }

    static void resetMaterialAdvection() {
        MATERIAL_ADVECTION.reset();
        VolumetricMaterialDomainDiagnostics.reset();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        VolumetricCloudFrameDiagnostics.pollCumulusStageCapture();
        VolumetricCloudFrameDiagnostics.pollStabilityCapture();
        if (!isActive() || !VolumetricCloudShaders.isReady()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        try (CloudRenderStateGuard.State ignored = CloudRenderStateGuard.capture()) {
            renderFrame(event, minecraft, level);
        } catch (Throwable throwable) {
            setRuntimeEnabled(false);
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
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cameraPos = event.getCamera().getPosition();
        String dimensionId = level.dimension().location().toString();
        long gameTime = level.getGameTime();
        float worldTimeTicks = (gameTime % 1_728_000L) + partialTick;

        // 1. Gather render cells. Spawned/native PA clouds still arrive as
        // CloudField snapshots; prefer them over the newer autonomous cell
        // simulation so manual cloud spawns render the cloud the user asked
        // for instead of unrelated background cells.
        List<CloudCell> presentedCells = ClientCloudCellCache.presentCells(dimensionId, gameTime, partialTick);
        List<CloudCell> renderedCellSources = new ArrayList<>();
        List<VolumetricRenderCell> renderCells = new ArrayList<>();
        CloudFieldRendererInput input = ClientCloudFieldCache.createRendererInput(cameraPos, gameTime, partialTick);
        List<CloudFieldSnapshot> fieldSnapshots = new ArrayList<>(input.fields());
        fieldSnapshots.sort(Comparator.comparingDouble(snapshot -> snapshot.center().distanceToSqr(cameraPos)));
        CloudletAllocationPlan cloudletPlan = allocateFieldCloudlets(fieldSnapshots, dimensionId, cameraPos);
        List<CloudFieldSnapshot> renderedFieldSources = cloudletPlan.orderedFields().subList(
                0,
                Math.min(cloudletPlan.orderedFields().size(), CloudWeatherMapRenderer.MAX_CELLS)
        );
        List<VolumetricCloudFrameDiagnostics.FieldInfo> fieldDiagnostics =
                buildFieldDiagnostics(fieldSnapshots, dimensionId, cloudletPlan);
        // Every visible field contributes one low-frequency macro envelope
        // before detail cloudlets consume the remaining slots. This preserves
        // mass through FAR_PROCEDURAL/HAZE and keeps a budget change from
        // deleting the entire cloud silhouette.
        for (CloudFieldSnapshot snapshot : renderedFieldSources) {
            addFieldMacroCell(
                    snapshot,
                    cloudletPlan.acceptedFor(snapshot.fieldId()),
                    renderCells
            );
        }
        for (CloudFieldSnapshot snapshot : renderedFieldSources) {
            addFieldCloudletCells(snapshot, cloudletPlan.acceptedFor(snapshot.fieldId()), renderCells);
        }

        String renderSource = renderCells.isEmpty() ? "none" : "fields";
        // A visible field whose LOD requests zero identifiable cloudlets must
        // stay at zero. Falling through to autonomous cells here used to
        // silently repopulate the frame and defeat the field LOD decision.
        if (renderCells.isEmpty() && cloudletPlan.visibleFields() == 0 && !presentedCells.isEmpty()) {
            presentedCells.sort(Comparator.comparingDouble(cell -> cell.distanceSqrTo(cameraPos.x(), cameraPos.z())));
            for (CloudCell cell : presentedCells) {
                if (renderCells.size() >= CloudWeatherMapRenderer.MAX_CELLS) {
                    break;
                }
                if (cell.isVisuallyRelevant()) {
                    renderCells.add(VolumetricRenderCell.fromCell(cell));
                    renderedCellSources.add(cell);
                }
            }
            renderSource = renderCells.isEmpty() ? "none" : "cells";
        } else if (!renderCells.isEmpty()) {
            // Severe cells carry funnel state derived from the same fields (or
            // an explicit command cell). Keep their parent volume represented
            // without allowing them to consume the field cloudlet budget.
            for (CloudCell cell : presentedCells) {
                if (cell.funnelStrength() <= 0.005F
                        || renderCells.size() >= CloudWeatherMapRenderer.MAX_CELLS) {
                    continue;
                }
                renderCells.add(VolumetricRenderCell.fromCell(cell));
            }
        }
        boolean renderingFields = "fields".equals(renderSource);

        AtmosphereClientState.Snapshot atmosphere = AtmosphereClientState.getSnapshot();
        float regionalCoverage = Mth.clamp((atmosphere.cloudCover() - 0.45F) * 1.4F, 0.0F, 1.0F);
        float regionalEnergy = Mth.clamp(atmosphere.rainIntensity() * 0.8F, 0.0F, 1.0F);

        if (renderCells.isEmpty() && regionalCoverage <= 0.01F) {
            setStatus("no_clouds", "cloudCover=" + atmosphere.cloudCover()
                    + " cells=" + presentedCells.size()
                    + " fields=" + fieldSnapshots.size()
                    + " source=" + renderSource);
            VolumetricCloudRenderer.invalidateHistory();
            CloudShadowMapAccess.clear();
            ClientCloudVisualDensity.clear();
            CameraCloudDensityTracker.update(0.0F);
            MATERIAL_ADVECTION.suspend();
            VolumetricMaterialDomainDiagnostics.reset();
            return;
        }

        AtmoCommonConfig.CloudRaymarchQuality configuredQuality = AtmoCommonConfig.CLOUD_RAYMARCH_QUALITY.get();
        VolumetricQualityProfile profile = VolumetricQualityProfile.forQuality(configuredQuality);
        if (VolumetricCloudDebugConfig.fullResolutionEnabled() && profile.resolutionScale() < 1.0F) {
            profile = profile.withResolutionScale(1.0F);
        }
        VolumetricCloudLighting.Frame lighting = VolumetricCloudLighting.resolve(level, cameraPos, partialTick);

        // Resolve this before any PA pass changes framebuffer bindings. Forge's
        // active target can be the main, Fabulous weather, or an external
        // pipeline target; the resolver detaches its depth for safe sampling.
        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        SceneDepthFrame sceneDepth = SceneDepthResolver.resolve(mainTarget);

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
            ClientCloudVisualDensity.clear();
            CameraCloudDensityTracker.update(0.0F);
            return;
        }

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
            float daylightFactor = Mth.clamp(
                    lighting.lightDirection().y * (1.0F - lighting.nightFactor()),
                    0.0F,
                    1.0F
            );
            VolumetricCloudShadowRenderer.applyGroundShadows(
                    mainTarget,
                    sceneDepth,
                    new Matrix4f(projection).invert(),
                    new Matrix4f(viewRotation).invert(),
                    cameraPosF,
                    lighting.lightDirection(),
                    weather.slabBaseY(),
                    groundY,
                    0.38F,
                    daylightFactor
            );
        } else {
            CloudShadowMapAccess.clear();
        }

        // 5. Raymarch with temporal reprojection.
        Vector3f windVec = resolveVisualWind(
                renderingFields ? renderedFieldSources : List.of(),
                renderingFields ? List.of() : renderedCellSources,
                cameraPos,
                gameTime
        );
        double renderTime = gameTime + partialTick;
        VolumetricMaterialAdvectionTracker.Frame materialAdvection = renderingFields
                ? MATERIAL_ADVECTION.updateFields(dimensionId, renderTime, renderedFieldSources)
                : (!renderedCellSources.isEmpty()
                        ? MATERIAL_ADVECTION.updateCells(dimensionId, renderTime, renderedCellSources)
                        : MATERIAL_ADVECTION.updateRegional(dimensionId, renderTime, windVec));
        if (materialAdvection.discontinuity()) {
            VolumetricCloudRenderer.invalidateHistory();
        }
        VolumetricMaterialDomainDiagnostics.observe(
                renderingFields ? renderedFieldSources : List.of(),
                renderingFields ? List.of() : renderedCellSources,
                windVec,
                worldTimeTicks
        );
        VolumetricCloudRenderer.FunnelUniforms funnels = buildFunnels(presentedCells, cameraPos);
        VolumetricCloudRenderer.Tuning tuning = renderingFields
                ? VolumetricCloudRenderer.Tuning.FIELDS
                : VolumetricCloudRenderer.Tuning.CELLS;
        boolean rendered = VolumetricCloudRenderer.render(
                mainTarget,
                sceneDepth,
                weather,
                lighting,
                projection,
                viewRotation,
                cameraPosF,
                worldTimeTicks,
                windVec,
                materialAdvection,
                profile,
                Math.max(300.0F, AtmoCommonConfig.CLOUD_RENDER_DISTANCE.get()),
                funnels,
                tuning,
                VolumetricCloudDebugConfig.sceneRayLimitEnabled()
        );
        if (!rendered) {
            setStatus("raymarch_not_ready", "");
            ClientCloudVisualDensity.clear();
            CameraCloudDensityTracker.update(0.0F);
            return;
        }

        // 6. Depth-aware upsample composite into the main target. The debug
        // mode from /pa system cloudFieldVolume composite <mode> applies here
        // too, so the raw volumetric buffer can be inspected in-game.
        boolean composited = CloudFieldCompositeRenderer.composite(
                VolumetricCloudRenderTargets.currentCloudTarget(),
                mainTarget,
                CloudFieldVolumeRenderConfig.compositeDebugMode(),
                VolumetricCloudDebugConfig.depthCompositeEnabled(),
                sceneDepth
        );
        if (composited) {
            ClientCloudVisualDensity.publishVolumetric(
                    dimensionId,
                    renderingFields
                            ? ClientCloudVisualDensity.Source.VOLUMETRIC_FIELDS
                            : ClientCloudVisualDensity.Source.VOLUMETRIC_CELLS,
                    renderCells,
                    weather,
                    regionalCoverage,
                    regionalEnergy,
                    includeRegionalLayer,
                    worldTimeTicks,
                    profile.weatherMapSize(),
                    tuning
            );
            CameraCloudDensityTracker.update(
                    ClientCloudVisualDensity.densityAt(dimensionId, cameraPos)
            );
        } else {
            ClientCloudVisualDensity.clear();
            CameraCloudDensityTracker.update(0.0F);
        }
        RenderTarget cloudTarget = VolumetricCloudRenderTargets.currentCloudTarget();
        VolumetricCloudFrameDiagnostics.tryDispatchStabilityCapture(
                cloudTarget,
                sceneDepth,
                frameCounter,
                gameTime,
                partialTick,
                CloudWeatherMapRenderer.lastInputSignatureForDiagnostics(),
                configuredQuality.name(),
                VolumetricCloudRenderer.lastDrawInputs(),
                CloudFieldCompositeRenderer.lastDrawInputs(),
                composited
        );
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
        VolumetricCloudFrameDiagnostics.tryDispatchCumulusStageCapture(
                weather,
                frameCounter,
                gameTime,
                summarizeCumulusEnvelopeRoles(renderCells),
                tuning.coverageMul()
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
                + " roles[" + summarizeEnvelopeRoles(renderCells) + "]"
                + " cloudlets[" + cloudletPlan.stats().summary() + "]"
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
                + " historyConfidence=" + String.format(
                        Locale.ROOT, "%.2f", VolumetricCloudRenderer.lastHistoryConfidence())
                + " cameraDensity=" + String.format(
                        Locale.ROOT, "%.3f", CameraCloudDensityTracker.smoothedCameraDensity())
                + " governorScale=" + String.format(
                        Locale.ROOT, "%.3f", VolumetricCloudRenderer.governorStepScale())
                + " resolutionScale=" + String.format(
                        Locale.ROOT, "%.3f", VolumetricCloudRenderer.lastResolutionScale())
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
                 + " " + PuffLobeSpatialIndex.status()
                 + " " + VolumetricMaterialDomainDiagnostics.status()
                + " " + materialAdvection.summary()
                + " gpuMs=" + VolumetricCloudRenderer.lastGpuMilliseconds());
    }

    private static String summarizeEnvelopeRoles(List<VolumetricRenderCell> renderCells) {
        int base = 0;
        int core = 0;
        int tower = 0;
        int crown = 0;
        int other = 0;
        for (VolumetricRenderCell cell : renderCells) {
            switch (cell.envelopeRole()) {
                case BASE -> base++;
                case CORE -> core++;
                case TOWER -> tower++;
                case CROWN -> crown++;
                default -> other++;
            }
        }
        return "base=" + base
                + ",core=" + core
                + ",tower=" + tower
                + ",crown=" + crown
                + ",other=" + other;
    }

    /** Mirrors the exact first-96/profile-3 filter used by the cumulus splat. */
    private static String summarizeCumulusEnvelopeRoles(List<VolumetricRenderCell> renderCells) {
        int base = 0;
        int core = 0;
        int tower = 0;
        int crown = 0;
        int other = 0;
        int count = Math.min(renderCells.size(), CloudWeatherMapRenderer.MAX_CELLS);
        for (int i = 0; i < count; i++) {
            VolumetricRenderCell cell = renderCells.get(i);
            if (cell.cloudProfile() != 3) {
                continue;
            }
            switch (cell.envelopeRole()) {
                case BASE -> base++;
                case CORE -> core++;
                case TOWER -> tower++;
                case CROWN -> crown++;
                default -> other++;
            }
        }
        return "base=" + base
                + ",core=" + core
                + ",tower=" + tower
                + ",crown=" + crown
                + ",other=" + other;
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

    private static void addFieldMacroCell(
            CloudFieldSnapshot snapshot,
            int acceptedDetailCount,
            List<VolumetricRenderCell> renderCells
    ) {
        if (snapshot == null || !snapshot.hasVisibleClouds()
                || renderCells.size() >= CloudWeatherMapRenderer.MAX_CELLS) {
            return;
        }
        renderCells.add(VolumetricRenderCell.fromFieldSnapshot(snapshot, acceptedDetailCount));
    }

    private static void addFieldCloudletCells(
            CloudFieldSnapshot snapshot,
            int cloudletCount,
            List<VolumetricRenderCell> renderCells
    ) {
        if (snapshot == null || snapshot.sourceKind() == CloudFieldSourceKind.PA_CLUSTER
                || cloudletCount <= 0
                || renderCells.size() >= CloudWeatherMapRenderer.MAX_CELLS) {
            return;
        }
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

    /**
     * Returns the renderer demand after the field LOD and hydration state have
     * been applied. In particular, FAR_PROCEDURAL and HAZE must stay at zero:
     * targetCloudletCount is a hydration target, not permission to bypass LOD.
     */
    private static int requestedCloudletCount(CloudFieldSnapshot snapshot) {
        if (snapshot == null || !snapshot.hasVisibleClouds()
                || snapshot.sourceKind() == CloudFieldSourceKind.PA_CLUSTER) {
            return 0;
        }
        return Math.min(
                CloudWeatherMapRenderer.MAX_CELLS,
                Math.min(snapshot.dynamicCloudletCount(), snapshot.targetCloudletCount())
        );
    }

    /**
     * Applies one strict frame-wide cloudlet budget. The weighted fair queue
     * first gives each visible requesting field one stable CloudletId, then
     * balances remaining slots according to distance, coverage, density,
     * storm intensity and source importance. A small retention bonus reduces
     * churn without ever exceeding the current quality budget.
     */
    private static CloudletAllocationPlan allocateFieldCloudlets(
            List<CloudFieldSnapshot> snapshots,
            String dimensionId,
            Vec3 cameraPos
    ) {
        List<CloudletCandidate> candidates = new ArrayList<>();
        List<CloudFieldSnapshot> visibleSnapshots = new ArrayList<>();
        Map<UUID, Integer> requestedByField = new HashMap<>();
        int visibleFields = 0;
        long totalRequested = 0L;

        for (CloudFieldSnapshot snapshot : snapshots) {
            if (snapshot == null || !snapshot.hasVisibleClouds()
                    || (!dimensionId.isBlank() && !dimensionId.equals(snapshot.dimensionId()))) {
                continue;
            }
            visibleFields++;
            visibleSnapshots.add(snapshot);
            int requested = requestedCloudletCount(snapshot);
            requestedByField.put(snapshot.fieldId(), requested);
            totalRequested += requested;
            if (requested > 0) {
                candidates.add(new CloudletCandidate(
                        snapshot,
                        requested,
                        cloudletPriority(snapshot, cameraPos)
                ));
            }
        }

        // One macro envelope is emitted before detail cloudlets for every
        // visible field that can fit in the weather map. Reserve those slots
        // up front so an allocation reported as accepted is never silently
        // truncated later by MAX_CELLS.
        int macroSlots = Math.min(visibleFields, CloudWeatherMapRenderer.MAX_CELLS);
        int detailCapacity = Math.max(0, CloudWeatherMapRenderer.MAX_CELLS - macroSlots);
        int budget = Math.max(0, Math.min(
                detailCapacity,
                CloudFieldVolumeRenderConfig.cloudletBudget()
        ));

        candidates.sort(Comparator
                .comparingDouble(CloudletCandidate::priority).reversed()
                .thenComparing(candidate -> candidate.snapshot().fieldId()));

        Map<UUID, Integer> acceptedByField = new LinkedHashMap<>();
        int remaining = budget;
        for (CloudletCandidate candidate : candidates) {
            if (remaining <= 0) {
                break;
            }
            acceptedByField.put(candidate.snapshot().fieldId(), 1);
            remaining--;
        }

        while (remaining > 0) {
            CloudletCandidate best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (CloudletCandidate candidate : candidates) {
                UUID fieldId = candidate.snapshot().fieldId();
                int accepted = acceptedByField.getOrDefault(fieldId, 0);
                if (accepted >= candidate.requested()) {
                    continue;
                }
                int previous = previousCloudletAllocations.getOrDefault(fieldId, 0);
                double retention = accepted < previous ? 1.12D : 1.0D;
                double score = candidate.priority() * retention / (accepted + 1.0D);
                if (score > bestScore || (score == bestScore && best != null
                        && fieldId.compareTo(best.snapshot().fieldId()) < 0)) {
                    best = candidate;
                    bestScore = score;
                }
            }
            if (best == null) {
                break;
            }
            UUID fieldId = best.snapshot().fieldId();
            acceptedByField.put(fieldId, acceptedByField.getOrDefault(fieldId, 0) + 1);
            remaining--;
        }

        int accepted = acceptedByField.values().stream().mapToInt(Integer::intValue).sum();
        int requested = (int) Math.min(Integer.MAX_VALUE, totalRequested);
        CloudletBudgetStats stats = new CloudletBudgetStats(
                requested,
                accepted,
                Math.max(0, requested - accepted),
                Math.max(0, budget - accepted),
                visibleFields,
                budget
        );
        previousCloudletAllocations = Map.copyOf(acceptedByField);
        lastCloudletBudgetStats = stats;

        visibleSnapshots.sort(Comparator
                .comparingDouble((CloudFieldSnapshot snapshot) -> cloudletPriority(snapshot, cameraPos)).reversed()
                .thenComparing(CloudFieldSnapshot::fieldId));
        List<CloudFieldSnapshot> orderedFields = List.copyOf(visibleSnapshots);
        return new CloudletAllocationPlan(
                orderedFields,
                Map.copyOf(requestedByField),
                Map.copyOf(acceptedByField),
                stats,
                visibleFields
        );
    }

    private static double cloudletPriority(CloudFieldSnapshot snapshot, Vec3 cameraPos) {
        double distance = Math.sqrt(snapshot.center().distanceToSqr(cameraPos));
        double distanceScore = 1.0D / (1.0D + distance / Math.max(256.0D, snapshot.radius()));
        double visualImportance = 0.30D
                + snapshot.effectiveCoverage() * 0.28D
                + snapshot.effectiveDensity() * 0.24D
                + snapshot.stormPotential() * 0.12D
                + snapshot.verticalDevelopment() * 0.06D;
        double sourceImportance = switch (snapshot.sourceKind()) {
            case MANUAL_DEBUG -> 1.18D;
            case PA_CLUSTER -> 1.12D;
            case PA_REGION -> 1.08D;
            case WEATHER_SUMMARY -> 1.0D;
            case UNKNOWN -> 0.92D;
        };
        return visualImportance * sourceImportance * (0.42D + distanceScore * 0.58D);
    }

    private static List<VolumetricCloudFrameDiagnostics.FieldInfo> buildFieldDiagnostics(
            List<CloudFieldSnapshot> snapshots,
            String dimensionId,
            CloudletAllocationPlan allocationPlan
    ) {
        if (snapshots == null || snapshots.isEmpty()) {
            return List.of();
        }
        List<VolumetricCloudFrameDiagnostics.FieldInfo> diagnostics = new ArrayList<>(snapshots.size());
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

            int requestedByRenderer = allocationPlan.requestedFor(snapshot.fieldId());
            int rendered = allocationPlan.acceptedFor(snapshot.fieldId());
            boolean skippedByMaxCells = requestedByRenderer > rendered;

            boolean skippedByRenderCap = targetCloudlets > requestedByRenderer;
            int skippedCloudlets = Math.max(0, targetCloudlets - rendered);
            String reason = "rendered";
            if (rendered <= 0 && skippedByMaxCells) {
                reason = "cloudlet_budget";
            } else if (rendered <= 0 && !snapshot.lodBand().hasIdentifiableCloudlets()) {
                reason = "lod_procedural_only";
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

    public record CloudletBudgetStats(
            int requested,
            int accepted,
            int rejected,
            int budgetRemaining,
            int visibleFields,
            int budget
    ) {
        private static CloudletBudgetStats empty() {
            return new CloudletBudgetStats(0, 0, 0, 0, 0, 0);
        }

        public String summary() {
            return "requested=" + requested
                    + ",accepted=" + accepted
                    + ",rejected=" + rejected
                    + ",remaining=" + budgetRemaining
                    + ",visibleFields=" + visibleFields
                    + ",budget=" + budget;
        }
    }

    private record CloudletCandidate(CloudFieldSnapshot snapshot, int requested, double priority) {
    }

    private record CloudletAllocationPlan(
            List<CloudFieldSnapshot> orderedFields,
            Map<UUID, Integer> requestedByField,
            Map<UUID, Integer> acceptedByField,
            CloudletBudgetStats stats,
            int visibleFields
    ) {
        private int requestedFor(UUID fieldId) {
            return requestedByField.getOrDefault(fieldId, 0);
        }

        private int acceptedFor(UUID fieldId) {
            return acceptedByField.getOrDefault(fieldId, 0);
        }
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

    private static Vector3f resolveVisualWind(
            List<CloudFieldSnapshot> fields,
            List<CloudCell> cells,
            Vec3 cameraPos,
            long gameTime
    ) {
        // Source ownership, not vector magnitude, selects the wind domain. A
        // rendered field can legitimately be calm (or several field vectors
        // can cancel); replacing that exact zero with forecast wind makes the
        // material/rain direction move while the authoritative cloud is frozen.
        if (fields != null && !fields.isEmpty()) {
            return averageFieldWind(fields);
        }
        if (cells != null && !cells.isEmpty()) {
            return averageCellWind(cells);
        }
        // Regional-only sheets have no field/cell to average. Use the same
        // synchronized forecast vector that drives server cloud motion.
        WindVector regionalWind = ForecastOrchestrator.getWind(
                BlockPos.containing(cameraPos), gameTime
        );
        float drift = Mth.clamp(
                (regionalWind == null ? 0.0F : regionalWind.baseSpeed())
                        * 0.05F * AtmoCommonConfig.CLOUD_WIND_DRIFT_SCALE.get().floatValue(),
                0.0F,
                0.45F
        );
        float angle = regionalWind == null ? 0.0F : regionalWind.angleRadians();
        return new Vector3f(
                (float) Math.cos(angle) * drift,
                0.0F,
                (float) Math.sin(angle) * drift
        );
    }

    private static Vector3f averageFieldWind(List<CloudFieldSnapshot> fields) {
        if (fields == null || fields.isEmpty()) {
            return new Vector3f();
        }
        double x = 0.0D;
        double z = 0.0D;
        double weight = 0.0D;
        for (CloudFieldSnapshot field : fields) {
            if (field == null) {
                continue;
            }
            double fieldWeight = Math.max(0.01D,
                    field.effectiveDensity() * field.effectiveCoverage() * Math.max(1.0F, field.radius()));
            x += field.windVector().x() * fieldWeight;
            z += field.windVector().z() * fieldWeight;
            weight += fieldWeight;
        }
        return weight <= 0.0D ? new Vector3f() : new Vector3f((float) (x / weight), 0.0F, (float) (z / weight));
    }

    private static Vector3f averageCellWind(List<CloudCell> cells) {
        if (cells == null || cells.isEmpty()) {
            return new Vector3f();
        }
        double x = 0.0D;
        double z = 0.0D;
        double weight = 0.0D;
        for (CloudCell cell : cells) {
            if (cell == null) {
                continue;
            }
            double cellWeight = Math.max(0.01D, cell.density() * cell.radiusMajor());
            x += cell.wind().x() * cellWeight;
            z += cell.wind().z() * cellWeight;
            weight += cellWeight;
        }
        return weight <= 0.0D ? new Vector3f() : new Vector3f((float) (x / weight), 0.0F, (float) (z / weight));
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
