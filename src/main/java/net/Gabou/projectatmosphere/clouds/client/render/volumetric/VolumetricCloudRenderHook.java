package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.client.atmosphere.AtmosphereClientState;
import net.Gabou.projectatmosphere.client.render.shader.VolumetricCloudShaders;
import net.Gabou.projectatmosphere.clouds.analytics.CloudCellAnalyticsPass;
import net.Gabou.projectatmosphere.clouds.cell.CloudCell;
import net.Gabou.projectatmosphere.clouds.cell.client.ClientCloudCellCache;
import net.Gabou.projectatmosphere.clouds.client.ClientCloudFieldCache;
import net.Gabou.projectatmosphere.clouds.client.render.ClientShaderPipelineHelper;
import net.Gabou.projectatmosphere.clouds.client.render.field.CloudFieldCompositeRenderer;
import net.Gabou.projectatmosphere.clouds.client.render.field.CloudFieldCompositeDebugMode;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldRendererInput;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldSnapshot;
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

        // 1. Gather cells: prefer the synced cell simulation, fall back to
        // legacy CloudField snapshots so the renderer works from day one.
        List<CloudCell> presentedCells = ClientCloudCellCache.presentCells(dimensionId, gameTime, partialTick);
        List<VolumetricRenderCell> renderCells = new ArrayList<>();
        if (!presentedCells.isEmpty()) {
            presentedCells.sort(Comparator.comparingDouble(cell -> cell.distanceSqrTo(cameraPos.x(), cameraPos.z())));
            for (CloudCell cell : presentedCells) {
                if (renderCells.size() >= CloudWeatherMapRenderer.MAX_CELLS) {
                    break;
                }
                if (cell.isVisuallyRelevant()) {
                    renderCells.add(VolumetricRenderCell.fromCell(cell));
                }
            }
        } else {
            CloudFieldRendererInput input = ClientCloudFieldCache.createRendererInput(cameraPos, gameTime, partialTick);
            for (CloudFieldSnapshot snapshot : input.fields()) {
                if (renderCells.size() >= CloudWeatherMapRenderer.MAX_CELLS) {
                    break;
                }
                if (snapshot != null && snapshot.hasVisibleClouds()
                        && (dimensionId.isBlank() || dimensionId.equals(snapshot.dimensionId()))) {
                    renderCells.add(VolumetricRenderCell.fromFieldSnapshot(snapshot));
                }
            }
        }

        // 2. Interior fog tracking runs even when nothing is visible so the
        // whiteout releases smoothly after leaving a cloud.
        CameraCloudDensityTracker.update(presentedCells, cameraPos);

        AtmosphereClientState.Snapshot atmosphere = AtmosphereClientState.getSnapshot();
        float regionalCoverage = Mth.clamp((atmosphere.cloudCover() - 0.45F) * 1.4F, 0.0F, 1.0F);
        float regionalEnergy = Mth.clamp(atmosphere.rainIntensity() * 0.8F, 0.0F, 1.0F);

        if (renderCells.isEmpty() && regionalCoverage <= 0.01F) {
            lastStatus = "no_clouds";
            VolumetricCloudRenderer.invalidateHistory();
            return;
        }

        VolumetricQualityProfile profile = VolumetricQualityProfile.forQuality(
                AtmoCommonConfig.CLOUD_RAYMARCH_QUALITY.get());
        VolumetricCloudLighting.Frame lighting = VolumetricCloudLighting.resolve(level, cameraPos, partialTick);

        // 3. Weather map splat (every frame; cells move every frame).
        CloudWeatherMapRenderer.Result weather = CloudWeatherMapRenderer.render(
                renderCells,
                cameraPos.x(),
                cameraPos.z(),
                regionalCoverage,
                regionalEnergy,
                worldTimeTicks,
                profile.weatherMapSize()
        );
        if (!weather.rendered()) {
            lastStatus = "weather_map_unavailable";
            return;
        }

        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        Matrix4f projection = new Matrix4f(event.getProjectionMatrix());
        Matrix4f viewRotation = new Matrix4f(event.getPoseStack().last().pose());
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
                    presentedCells,
                    lighting.lightDirection(),
                    cameraPos.x(),
                    cameraPos.z(),
                    weather.slabBaseY(),
                    groundY
            );
            boolean applyPost = !ClientShaderPipelineHelper.isConservativeShaderPathPreferred();
            if (applyPost) {
                float daylight = Mth.clamp(lighting.lightDirection().y * 2.6F, 0.0F, 1.0F)
                        * (1.0F - lighting.nightFactor());
                VolumetricCloudShadowRenderer.applyGroundShadows(
                        mainTarget,
                        new Matrix4f(projection).invert(),
                        new Matrix4f(viewRotation).invert(),
                        cameraPosF,
                        lighting.lightDirection(),
                        weather.slabBaseY(),
                        groundY,
                        0.38F,
                        daylight
                );
            }
        }

        // 5. Raymarch with temporal reprojection.
        Vector3f windVec = averageWind(presentedCells);
        VolumetricCloudRenderer.FunnelUniforms funnels = buildFunnels(presentedCells, cameraPos);
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
                funnels
        );
        if (!rendered) {
            lastStatus = "raymarch_not_ready";
            if (mainTarget != null) {
                mainTarget.bindWrite(true);
            }
            return;
        }

        // 6. Depth-aware upsample composite into the main target.
        boolean composited = CloudFieldCompositeRenderer.composite(
                VolumetricCloudRenderTargets.currentCloudTarget(),
                mainTarget,
                CloudFieldCompositeDebugMode.FINAL
        );
        if (!composited && mainTarget != null) {
            mainTarget.bindWrite(true);
        }
        VolumetricCloudRenderer.finishFrame();

        // 7. GPU analytics (Medium+ with GL 4.3): measure per-cell footprints
        // from the weather map and hand digests to the merge/split logic.
        if (profile.analyticsEnabled() && !presentedCells.isEmpty()) {
            CloudCellAnalyticsPass.tick(
                    presentedCells,
                    VolumetricCloudRenderTargets.prepareWeatherTarget(profile.weatherMapSize()),
                    weather,
                    gameTime
            );
        }

        lastStatus = "rendered cells=" + renderCells.size()
                + " weatherCells=" + weather.cellCount()
                + " funnels=" + funnels.count();
    }

    private static Vector3f averageWind(List<CloudCell> cells) {
        if (cells == null || cells.isEmpty()) {
            return new Vector3f(0.015F, 0.0F, 0.006F);
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
