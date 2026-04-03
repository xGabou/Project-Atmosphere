package net.Gabou.projectatmosphere.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.modules.tornado.TornadoInstance;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;
import net.Gabou.projectatmosphere.modules.weather.StormLifecyclePhase;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class SimpleCloudsTornadoRenderer {
    public static final SimpleCloudsTornadoRenderer INSTANCE = new SimpleCloudsTornadoRenderer();

    private static final int MAX_STORMS = 8;
    private static final float CLOUD_BLEND_PAD_ABOVE_CLOUD_BASE_WORLD = 28.0F;
    private static final float GROUND_CONTACT_EXTENSION_WORLD = 12.0F;
    private static final float GROUND_CONTACT_PADDING_WORLD = 2.0F;
    private static final float MIN_VISUAL_WORLD_WIDTH = 28.0F;
    private static final float MIN_VISUAL_WORLD_STORM_SIZE = 140.0F;
    private static final float MIN_VISUAL_WORLD_HEIGHT = 120.0F;
    private static final float MAX_RAY_DISTANCE_CLOUD = 420.0F;
    private static final float WHITEOUT_STRENGTH = 0.40F;
    private static final float WHITEOUT_THRESHOLD = 0.12F;
    private static final float RAY_STEP_CLOUD = 0.42F;
    private static final float WALLCLOUD_LOWER_WORLD = 15.0F;
    private static final float FUNNEL_TOP_OFFSET_WORLD = 13.125F;
    private static final float FUNNEL_BASE_PADDING_WORLD = 3.75F;
    private static final float WALLCLOUD_GATE_BELOW_ORIGIN_WORLD = 8.5F;
    private static final float TOUCHDOWN_TOP_BLEND_WORLD = 3.75F;
    private static final float CONNECTION_BLEND_WORLD = 1.8F;

    private ClientLevel preparedLevel;
    private long preparedGameTime = Long.MIN_VALUE;
    private float preparedPartialTick = Float.NaN;
    private final List<PreparedTornado> preparedTornadoes = new ArrayList<>();
    private final VolumeBoxMesh volumeBox = new VolumeBoxMesh();
    private int resolvedDebugStormIndex = -1;
    private long lastRenderOpaqueLogGameTime = Long.MIN_VALUE;
    private long lastRenderTransparencyLogGameTime = Long.MIN_VALUE;
    private long lastDiagnosticReportGameTime = Long.MIN_VALUE;

    private SimpleCloudsTornadoRenderer() {
    }

    public void prepareFrame(ClientLevel level, float partialTick) {
        if (this.preparedLevel == level
                && this.preparedGameTime == level.getGameTime()
                && Float.compare(this.preparedPartialTick, partialTick) == 0) {
            return;
        }

        boolean shouldDebugLog = shouldDebugLog(level);
        this.preparedLevel = level;
        this.preparedGameTime = level.getGameTime();
        this.preparedPartialTick = partialTick;
        this.preparedTornadoes.clear();

        float animationTime = TornadoManager.getShaderTime() + partialTick * 0.05F;
        for (TornadoInstance tornado : TornadoManager.getClientTornadoes()) {
            if (this.preparedTornadoes.size() >= MAX_STORMS) {
                break;
            }
            this.preparedTornadoes.add(PreparedTornado.from(level, tornado, animationTime, partialTick));
        }

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (camera != null && TornadoRenderDebugState.isActive()) {
            this.resolvedDebugStormIndex = this.resolveDebugStormIndex(
                    camera.getPosition(),
                    new Vec3(camera.getLookVector())
            );
        } else {
            this.resolvedDebugStormIndex = -1;
        }

        if (shouldDebugLog) {
            debug(
                    "prepareFrame complete gameTime={} tornadoes={} debugState={} resolvedDebugStorm={}",
                    level.getGameTime(),
                    this.preparedTornadoes.size(),
                    TornadoRenderDebugState.describe(),
                    this.resolvedDebugStormIndex
            );
        }
    }

    public void renderOpaque(SimpleCloudsRenderer renderer, PoseStack stack, Matrix4f projMat,
                             float partialTick, float cloudR, float cloudG, float cloudB) {
        this.renderOpaque(renderer, stack, projMat, partialTick, cloudR, cloudG, cloudB, renderer.getCloudTarget().getDepthTextureId(), true);
    }

    public void renderOpaque(SimpleCloudsRenderer renderer, PoseStack stack, Matrix4f projMat,
                             float partialTick, float cloudR, float cloudG, float cloudB,
                             int depthTextureId, boolean writeDepth) {
        ClientLevel level = Minecraft.getInstance().level;
        if (shouldDebugLog(level) && level != null && this.lastRenderOpaqueLogGameTime != level.getGameTime()) {
            this.lastRenderOpaqueLogGameTime = level.getGameTime();
            debug(
                    "renderOpaque called gameTime={} tornadoes={} shaderReady={} debugState={} resolvedDebugStorm={}",
                    level.getGameTime(),
                    this.preparedTornadoes.size(),
                    TornadoShaders.isReady(),
                    TornadoRenderDebugState.describe(),
                    this.resolvedDebugStormIndex
            );
        }
        if (this.preparedTornadoes.isEmpty() || !TornadoShaders.isReady()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ShaderInstance shader = TornadoShaders.getShader();
        if (shader == null) {
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // Keep the volume depth-aware so it sits inside the world instead of reading like a flat overlay.
        // The shader still raymarchs against its own max distance rather than using copied scene depth as a
        // hard clip plane, which preserves the earlier horizon fix over water and long flat terrain.
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(writeDepth);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> shader);

        AbstractTexture tornadoTexture = mc.getTextureManager().getTexture(TornadoShaders.TORNADO_TEXTURE);
        AbstractTexture noiseTexture = mc.getTextureManager().getTexture(TornadoShaders.NOISE_TEXTURE);
        AbstractTexture flowTexture = mc.getTextureManager().getTexture(TornadoShaders.FLOW_TEXTURE);
        shader.setSampler("TornadoSampler", tornadoTexture);
        shader.setSampler("NoiseSampler", noiseTexture);
        shader.setSampler("FlowSampler", flowTexture);
        shader.setSampler("DepthSampler", depthTextureId);

        shader.safeGetUniform("ModelViewMat").set(stack.last().pose());
        shader.safeGetUniform("ProjMat").set(projMat);
        Matrix4f inverseProj = new Matrix4f(projMat).invert();
        Matrix4f inverseModelView = new Matrix4f(stack.last().pose()).invert();
        shader.safeGetUniform("InverseProjMat").set(inverseProj);
        shader.safeGetUniform("InverseModelViewMat").set(inverseModelView);

        float scale = SimpleCloudsConstants.CLOUD_SCALE;
        float cloudHeight = CloudManager.get(level).getCloudHeight();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 cameraPosCloud = new Vec3(
                cameraPos.x / scale,
                (cameraPos.y - cloudHeight) / scale,
                cameraPos.z / scale
        );
        shader.safeGetUniform("CameraPos").set((float) cameraPosCloud.x, (float) cameraPosCloud.y, (float) cameraPosCloud.z);

        TornadoRenderDebugState.Mode debugMode = TornadoRenderDebugState.isActive()
                ? TornadoRenderDebugState.getMode()
                : TornadoRenderDebugState.Mode.OFF;
        shader.safeGetUniform("CloudScale").set(scale);

        shader.safeGetUniform("CloudColor").set(cloudR, cloudG, cloudB, 1.0F);
        shader.safeGetUniform("AnimationTime").set(TornadoManager.getShaderTime() + partialTick * 0.05F);
        shader.safeGetUniform("MaxDistance").set(MAX_RAY_DISTANCE_CLOUD);
        shader.safeGetUniform("OutSize").set((float) mc.getWindow().getWidth(), (float) mc.getWindow().getHeight());
        shader.safeGetUniform("FogStart").set(renderer.getFogStart());
        shader.safeGetUniform("FogEnd").set(renderer.getFogEnd());
        float[] fogColor = RenderSystem.getShaderFogColor();
        shader.safeGetUniform("FogColor").set(fogColor[0], fogColor[1], fogColor[2], fogColor[3]);

        this.maybeEmitDiagnosticReport(level, inverseProj, inverseModelView, cameraPos, cameraPosCloud, writeDepth);

        List<Integer> renderOrder = new ArrayList<>();
        for (int i = 0; i < this.preparedTornadoes.size(); i++) {
            if (debugMode != TornadoRenderDebugState.Mode.OFF && i != this.resolvedDebugStormIndex) {
                continue;
            }
            renderOrder.add(i);
        }
        renderOrder.sort((left, right) -> Double.compare(
                this.preparedTornadoes.get(right).centerWorld().distanceToSqr(cameraPos),
                this.preparedTornadoes.get(left).centerWorld().distanceToSqr(cameraPos)
        ));

        for (int index : renderOrder) {
            PreparedTornado tornado = this.preparedTornadoes.get(index);
            this.applyStormUniforms(shader, tornado);
            shader.safeGetUniform("DebugMode").set(debugMode.shaderValue());
            shader.safeGetUniform("DebugSelectedStorm").set(debugMode == TornadoRenderDebugState.Mode.OFF ? -1 : 0);
            shader.safeGetUniform("DebugFreeze").set(TornadoRenderDebugState.isFreezeEnabled() ? 1 : 0);
            shader.safeGetUniform("VolumeMin").set(
                    (float) tornado.boundsMinCloud().x,
                    (float) tornado.boundsMinCloud().y,
                    (float) tornado.boundsMinCloud().z
            );
            shader.safeGetUniform("VolumeMax").set(
                    (float) tornado.boundsMaxCloud().x,
                    (float) tornado.boundsMaxCloud().y,
                    (float) tornado.boundsMaxCloud().z
            );
            shader.apply();
            this.volumeBox.draw(shader, stack.last().pose(), projMat);
            shader.clear();
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }

    public void renderTransparency(SimpleCloudsRenderer renderer, PoseStack stack, Matrix4f projMat,
                                   float partialTick, float cloudR, float cloudG, float cloudB) {
        ClientLevel level = Minecraft.getInstance().level;
        if (shouldDebugLog(level) && level != null && this.lastRenderTransparencyLogGameTime != level.getGameTime()) {
            this.lastRenderTransparencyLogGameTime = level.getGameTime();
            debug("renderTransparency called gameTime={} tornadoes={}", level.getGameTime(), this.preparedTornadoes.size());
        }
    }

    private void applyStormUniforms(ShaderInstance shader, PreparedTornado tornado) {
        float[] stormPositions = new float[MAX_STORMS * 3];
        float[] stormHeights = new float[MAX_STORMS];
        float[] stormWidths = new float[MAX_STORMS];
        float[] stormSizes = new float[MAX_STORMS];
        float[] stormSpins = new float[MAX_STORMS];
        float[] stormIntensities = new float[MAX_STORMS];
        float[] stormShapes = new float[MAX_STORMS];
        float[] stormProgress = new float[MAX_STORMS];

        stormPositions[0] = tornado.centerX();
        stormPositions[1] = tornado.bottomY();
        stormPositions[2] = tornado.centerZ();
        stormHeights[0] = tornado.height();
        stormWidths[0] = tornado.width();
        stormSizes[0] = tornado.stormSize();
        stormSpins[0] = tornado.spin();
        stormIntensities[0] = tornado.intensity();
        stormShapes[0] = tornado.shape();
        stormProgress[0] = tornado.touchdownProgress();

        shader.safeGetUniform("StormCount").set(1);
        shader.safeGetUniform("StormPositions").set(stormPositions);
        shader.safeGetUniform("StormHeights").set(stormHeights);
        shader.safeGetUniform("StormWidths").set(stormWidths);
        shader.safeGetUniform("StormSizes").set(stormSizes);
        shader.safeGetUniform("StormSpins").set(stormSpins);
        shader.safeGetUniform("StormIntensities").set(stormIntensities);
        shader.safeGetUniform("StormShapes").set(stormShapes);
        shader.safeGetUniform("StormProgress").set(stormProgress);
    }

    public float sampleWhiteoutAtCamera(ClientLevel level, Vec3 cameraPos, float partialTick) {
        if (TornadoRenderDebugState.isActive()) {
            return 0.0F;
        }

        float scale = SimpleCloudsConstants.CLOUD_SCALE;
        float cloudHeight = CloudManager.get(level).getCloudHeight();
        float sampleX = (float) cameraPos.x / scale;
        float sampleY = ((float) cameraPos.y - cloudHeight) / scale;
        float sampleZ = (float) cameraPos.z / scale;
        float strongest = 0.0F;
        float animationTime = TornadoManager.getShaderTime() + partialTick * 0.05F;

        for (TornadoInstance tornado : TornadoManager.getClientTornadoes()) {
            PreparedTornado prepared = PreparedTornado.from(level, tornado, animationTime, partialTick);
            float density = sampleAnalyticalDensity(sampleX, sampleY, sampleZ, prepared);
            float whiteout = Mth.clamp((density - WHITEOUT_THRESHOLD) / 0.18F, 0.0F, 1.0F) * WHITEOUT_STRENGTH;
            strongest = Math.max(strongest, whiteout);
        }
        return strongest;
    }

    public void close() {
        this.preparedLevel = null;
        this.preparedGameTime = Long.MIN_VALUE;
        this.preparedPartialTick = Float.NaN;
        this.preparedTornadoes.clear();
        this.resolvedDebugStormIndex = -1;
        this.volumeBox.close();
    }

    private int resolveDebugStormIndex(Vec3 cameraPosWorld, Vec3 cameraLook) {
        int requestedStormIndex = TornadoRenderDebugState.getRequestedStormIndex();
        if (requestedStormIndex >= 0 && requestedStormIndex < this.preparedTornadoes.size()) {
            return requestedStormIndex;
        }
        if (this.preparedTornadoes.isEmpty()) {
            return -1;
        }

        int bestVisible = -1;
        double bestVisibleDistanceSqr = Double.MAX_VALUE;
        int bestOverall = 0;
        double bestOverallDistanceSqr = Double.MAX_VALUE;
        Vec3 normalizedLook = cameraLook.lengthSqr() > 0.0D ? cameraLook.normalize() : new Vec3(0.0D, 0.0D, 1.0D);

        for (int i = 0; i < this.preparedTornadoes.size(); i++) {
            PreparedTornado tornado = this.preparedTornadoes.get(i);
            Vec3 tornadoCenter = tornado.centerWorld();
            Vec3 toStorm = tornadoCenter.subtract(cameraPosWorld);
            double distanceSqr = toStorm.lengthSqr();
            if (distanceSqr < bestOverallDistanceSqr) {
                bestOverallDistanceSqr = distanceSqr;
                bestOverall = i;
            }
            if (distanceSqr <= 0.0001D) {
                continue;
            }

            double dot = toStorm.normalize().dot(normalizedLook);
            if (dot > 0.15D && distanceSqr < bestVisibleDistanceSqr) {
                bestVisibleDistanceSqr = distanceSqr;
                bestVisible = i;
            }
        }
        return bestVisible >= 0 ? bestVisible : bestOverall;
    }

    private void maybeEmitDiagnosticReport(ClientLevel level, Matrix4f inverseProj, Matrix4f inverseModelView,
                                           Vec3 cameraPosWorld, Vec3 cameraPosCloud, boolean writeDepth) {
        boolean requested = TornadoRenderDebugState.consumeDiagnosticReportRequest();
        boolean periodic = TornadoRenderDebugState.isActive()
                && shouldDebugLog(level)
                && this.lastDiagnosticReportGameTime != level.getGameTime();
        if (!requested && !periodic) {
            return;
        }

        this.lastDiagnosticReportGameTime = level.getGameTime();
        debug(
                "renderState mode={} freeze={} writeDepth={} blend=srcalpha,1-srcalpha depthTest=LEQUAL cull=disabled proxyVolume=true resolvedStorm={}",
                TornadoRenderDebugState.getMode().token(),
                TornadoRenderDebugState.isFreezeEnabled(),
                writeDepth,
                this.resolvedDebugStormIndex
        );

        if (this.resolvedDebugStormIndex < 0 || this.resolvedDebugStormIndex >= this.preparedTornadoes.size()) {
            debug("diagnostic skipped: no selected tornado. preparedCount={}", this.preparedTornadoes.size());
            return;
        }

        PreparedTornado tornado = this.preparedTornadoes.get(this.resolvedDebugStormIndex);
        CenterRayDiagnostic diagnostic = sampleCenterRayDiagnostic(tornado, inverseProj, inverseModelView, cameraPosCloud);
        debug(
                "selectedStorm index={} id={} renderPosWorld=({}, {}, {}) cloudHeightWorld={} cloudScale={} renderBottomWorld={} terrainSurfaceWorld={} bottomWorld={} topWorld={} bottomYCloud={} heightCloud={} heightWorld={} widthCloud={} widthWorld={} stormSizeCloud={} stormSizeWorld={} boundsRadiusCloud={} boundsRadiusWorld={} wallcloudRadiusWorld={}",
                this.resolvedDebugStormIndex,
                tornado.id(),
                fmt(tornado.renderPosWorld().x), fmt(tornado.renderPosWorld().y), fmt(tornado.renderPosWorld().z),
                fmt(tornado.cloudHeightWorld()),
                fmt(tornado.scale()),
                fmt(tornado.renderBottomWorld()),
                fmt(tornado.terrainSurfaceWorld()),
                fmt(tornado.bottomWorld()),
                fmt(tornado.topWorld()),
                fmt(tornado.bottomY()),
                fmt(tornado.height()),
                fmt(tornado.heightWorld()),
                fmt(tornado.width()),
                fmt(tornado.widthWorld()),
                fmt(tornado.stormSize()),
                fmt(tornado.stormSizeWorld()),
                fmt(tornado.boundsRadiusCloud()),
                fmt(tornado.boundsRadiusWorld()),
                fmt(tornado.wallcloudRadiusWorld())
        );

        if (diagnostic == null) {
            debug(
                    "centerRay cameraWorld=({}, {}, {}) cameraCloud=({}, {}, {}) note=center ray did not intersect selected tornado AABB",
                    fmt(cameraPosWorld.x), fmt(cameraPosWorld.y), fmt(cameraPosWorld.z),
                    fmt(cameraPosCloud.x), fmt(cameraPosCloud.y), fmt(cameraPosCloud.z)
            );
            return;
        }

        debug(
                "centerRay cameraWorld=({}, {}, {}) cameraCloud=({}, {}, {}) rayEndCloud=({}, {}, {}) rayDirCloud=({}, {}, {}) tNear={} tFar={} stepSize={} samplePosCloud=({}, {}, {}) samplePosWorld=({}, {}, {}) tornadoOriginCloud=({}, {}, {}) tornadoOriginWorld=({}, {}, {}) localPosCloud=({}, {}, {}) localPosWorld=({}, {}, {}) radialDistanceWorld={} height01={} heightMask={} funnelRadiusWorld={} density={} alpha={} wallcloudRadiusWorld={} wallcloudLowerWorld={} connectionRadiusWorld={}",
                fmt(cameraPosWorld.x), fmt(cameraPosWorld.y), fmt(cameraPosWorld.z),
                fmt(cameraPosCloud.x), fmt(cameraPosCloud.y), fmt(cameraPosCloud.z),
                fmt(diagnostic.rayEndCloud().x), fmt(diagnostic.rayEndCloud().y), fmt(diagnostic.rayEndCloud().z),
                fmt(diagnostic.rayDirectionCloud().x), fmt(diagnostic.rayDirectionCloud().y), fmt(diagnostic.rayDirectionCloud().z),
                fmt(diagnostic.tNear()),
                fmt(diagnostic.tFar()),
                fmt(diagnostic.stepSize()),
                fmt(diagnostic.samplePosCloud().x), fmt(diagnostic.samplePosCloud().y), fmt(diagnostic.samplePosCloud().z),
                fmt(diagnostic.samplePosWorld().x), fmt(diagnostic.samplePosWorld().y), fmt(diagnostic.samplePosWorld().z),
                fmt(diagnostic.tornadoOriginCloud().x), fmt(diagnostic.tornadoOriginCloud().y), fmt(diagnostic.tornadoOriginCloud().z),
                fmt(diagnostic.tornadoOriginWorld().x), fmt(diagnostic.tornadoOriginWorld().y), fmt(diagnostic.tornadoOriginWorld().z),
                fmt(diagnostic.localPosCloud().x), fmt(diagnostic.localPosCloud().y), fmt(diagnostic.localPosCloud().z),
                fmt(diagnostic.localPosWorld().x), fmt(diagnostic.localPosWorld().y), fmt(diagnostic.localPosWorld().z),
                fmt(diagnostic.funnelSample().radialDistanceWorld()),
                fmt(diagnostic.funnelSample().height01()),
                fmt(diagnostic.funnelSample().heightMask()),
                fmt(diagnostic.funnelSample().funnelRadiusWorld()),
                fmt(diagnostic.funnelSample().density()),
                fmt(diagnostic.funnelSample().alpha()),
                fmt(diagnostic.funnelSample().wallcloudRadiusWorld()),
                fmt(diagnostic.funnelSample().wallcloudLowerWorld()),
                fmt(diagnostic.funnelSample().connectionRadiusWorld())
        );
    }

    private static CenterRayDiagnostic sampleCenterRayDiagnostic(PreparedTornado tornado, Matrix4f inverseProj,
                                                                Matrix4f inverseModelView, Vec3 cameraPosCloud) {
        Vec3 rayEndCloud = reconstructPosition(0.5F, 0.5F, 1.0F, inverseProj, inverseModelView);
        Vec3 rayDirectionCloud = rayEndCloud.subtract(cameraPosCloud);
        if (rayDirectionCloud.lengthSqr() <= 0.000001D) {
            return null;
        }
        rayDirectionCloud = rayDirectionCloud.normalize();

        Vec3 boundsMin = new Vec3(
                tornado.centerX() - tornado.boundsRadiusCloud(),
                tornado.bottomY() - (8.0F / tornado.scale()),
                tornado.centerZ() - tornado.boundsRadiusCloud()
        );
        Vec3 boundsMax = new Vec3(
                tornado.centerX() + tornado.boundsRadiusCloud(),
                tornado.bottomY() + tornado.height() + (12.0F / tornado.scale()),
                tornado.centerZ() + tornado.boundsRadiusCloud()
        );
        AabbHit hit = intersectAabb(cameraPosCloud, rayDirectionCloud, boundsMin, boundsMax);
        if (hit == null) {
            return null;
        }

        float interval = hit.far() - hit.near();
        int steps = Mth.clamp(Mth.floor(interval / RAY_STEP_CLOUD), 18, 52);
        float stepSize = interval / Math.max(steps, 1);
        float jitter = hash1(0.5F * Minecraft.getInstance().getWindow().getWidth()
                + 0.5F * Minecraft.getInstance().getWindow().getHeight()
                + tornado.seed() * 17.13F);
        float t = hit.near() + stepSize * (0.20F + jitter * 0.80F);

        Vec3 samplePosCloud = cameraPosCloud.add(rayDirectionCloud.scale(t));
        Vec3 samplePosWorld = new Vec3(
                samplePosCloud.x * tornado.scale(),
                samplePosCloud.y * tornado.scale() + tornado.cloudHeightWorld(),
                samplePosCloud.z * tornado.scale()
        );
        Vec3 tornadoOriginCloud = tornado.originCloud();
        Vec3 tornadoOriginWorld = tornado.originWorld();
        Vec3 localPosCloud = samplePosCloud.subtract(tornadoOriginCloud);
        Vec3 localPosWorld = samplePosWorld.subtract(tornadoOriginWorld);
        return new CenterRayDiagnostic(
                rayEndCloud,
                rayDirectionCloud,
                hit.near(),
                hit.far(),
                stepSize,
                samplePosCloud,
                samplePosWorld,
                tornadoOriginCloud,
                tornadoOriginWorld,
                localPosCloud,
                localPosWorld,
                sampleDeterministicFunnel(samplePosCloud, tornado)
        );
    }

    private static Vec3 reconstructPosition(float u, float v, float depth, Matrix4f inverseProj, Matrix4f inverseModelView) {
        Vector4f ndc = new Vector4f(u * 2.0F - 1.0F, v * 2.0F - 1.0F, depth * 2.0F - 1.0F, 1.0F);
        inverseProj.transform(ndc);
        ndc.div(ndc.w);
        inverseModelView.transform(ndc);
        ndc.div(ndc.w);
        return new Vec3(ndc.x, ndc.y, ndc.z);
    }

    private static AabbHit intersectAabb(Vec3 ro, Vec3 rd, Vec3 bmin, Vec3 bmax) {
        double invX = 1.0D / rd.x;
        double invY = 1.0D / rd.y;
        double invZ = 1.0D / rd.z;

        double t0x = (bmin.x - ro.x) * invX;
        double t1x = (bmax.x - ro.x) * invX;
        double t0y = (bmin.y - ro.y) * invY;
        double t1y = (bmax.y - ro.y) * invY;
        double t0z = (bmin.z - ro.z) * invZ;
        double t1z = (bmax.z - ro.z) * invZ;

        double minX = Math.min(t0x, t1x);
        double minY = Math.min(t0y, t1y);
        double minZ = Math.min(t0z, t1z);
        double maxX = Math.max(t0x, t1x);
        double maxY = Math.max(t0y, t1y);
        double maxZ = Math.max(t0z, t1z);

        double tNear = Math.max(Math.max(minX, minY), minZ);
        double tFar = Math.min(Math.min(maxX, maxY), maxZ);
        if (tFar <= Math.max(tNear, 0.0D)) {
            return null;
        }
        return new AabbHit((float) Math.max(tNear, 0.0D), (float) tFar);
    }

    private static float hash1(float p) {
        float value = Mth.frac(p * 0.1031F);
        value *= value + 33.33F;
        value *= value + value;
        return Mth.frac(value);
    }

    private static float sampleAnalyticalDensity(float sampleX, float sampleY, float sampleZ, PreparedTornado tornado) {
        return sampleDeterministicFunnel(new Vec3(sampleX, sampleY, sampleZ), tornado).density();
    }

    private static DeterministicFunnelSample sampleDeterministicFunnel(Vec3 samplePosCloud, PreparedTornado tornado) {
        float sampleXWorld = (float) samplePosCloud.x * tornado.scale();
        float sampleYWorld = (float) samplePosCloud.y * tornado.scale() + tornado.cloudHeightWorld();
        float sampleZWorld = (float) samplePosCloud.z * tornado.scale();
        float localXWorld = sampleXWorld - (float) tornado.renderPosWorld().x;
        float localZWorld = sampleZWorld - (float) tornado.renderPosWorld().z;
        float localYWorld = sampleYWorld - tornado.bottomWorld();
        float topWorld = tornado.topWorld();
        float funnelTopWorld = Math.max(topWorld - FUNNEL_TOP_OFFSET_WORLD, tornado.bottomWorld() + FUNNEL_BASE_PADDING_WORLD);
        float height01 = Mth.clamp(localYWorld / Math.max(tornado.heightWorld(), 0.001F), 0.0F, 1.0F);
        float heightMask = Mth.clamp((sampleYWorld - tornado.bottomWorld()) / Math.max(funnelTopWorld - tornado.bottomWorld(), 0.001F), 0.0F, 1.0F);
        float funnelRadiusWorld = sampleFunnelRadiusWorld(tornado, sampleYWorld, funnelTopWorld);
        float radialDistanceWorld = Mth.sqrt(localXWorld * localXWorld + localZWorld * localZWorld);
        float radialMask = 1.0F - Mth.clamp(radialDistanceWorld / Math.max(funnelRadiusWorld, 0.001F), 0.0F, 1.0F);
        float density = 0.0F;
        if (sampleYWorld >= tornado.bottomWorld() && sampleYWorld <= funnelTopWorld) {
            density = radialMask;
        }
        float alpha = Mth.clamp(density * 0.85F, 0.0F, 1.0F);
        float wallcloudLowerWorld = WALLCLOUD_LOWER_WORLD
                * (float) Math.pow(Math.max(0.0F, 1.0F - Mth.clamp(radialDistanceWorld / Math.max(tornado.wallcloudRadiusWorld(), 0.001F), 0.0F, 1.0F)), 0.25F)
                * Mth.clamp((tornado.intensity() - 0.45F) * 2.2F, 0.0F, 1.0F);
        float connectionRadiusWorld = Math.max(
                funnelRadiusWorld * Mth.lerp(tornado.intensity(), 1.8F, 2.5F),
                tornado.stormSizeWorld() * 0.28F
        );
        return new DeterministicFunnelSample(height01, heightMask, radialMask, funnelRadiusWorld, density, alpha,
                tornado.wallcloudRadiusWorld(), wallcloudLowerWorld, connectionRadiusWorld);
    }

    private static float sampleFunnelRadiusWorld(PreparedTornado tornado, float sampleYWorld, float funnelTopWorld) {
        float widthWorld = tornado.widthWorld();
        float stormSizeWorld = tornado.stormSizeWorld();
        float percFunnelHeight = Mth.clamp(
                (sampleYWorld - tornado.bottomWorld()) / Math.max(funnelTopWorld - tornado.bottomWorld(), 0.001F),
                0.0F,
                1.0F
        );
        float torShape = Mth.lerp(Mth.clamp(widthWorld / 62.5F, 0.0F, 1.0F), tornado.shape(), 20.0F);
        float funnelRadiusWorld = (widthWorld / 2.5F)
                + ((widthWorld / 2.5F) * percFunnelHeight * tornado.touchdownProgress())
                + ((stormSizeWorld / Math.max(Mth.lerp(tornado.touchdownProgress(), torShape + 2.0F, torShape), 0.001F))
                * percFunnelHeight * percFunnelHeight * percFunnelHeight * percFunnelHeight);
        return Mth.lerp((1.0F - percFunnelHeight) * (1.0F - tornado.touchdownProgress()), funnelRadiusWorld, 0.0F);
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static boolean shouldDebugLog(ClientLevel level) {
        return ProjectAtmosphere.DEBUG_MODE && level != null && level.getGameTime() % 20L == 0L;
    }

    private static float sampleTerrainSurfaceY(ClientLevel level, Vec3 renderPos, float radius) {
        int centerX = Mth.floor(renderPos.x);
        int centerZ = Mth.floor(renderPos.z);
        int sampleOffset = Math.max(2, Mth.floor(Math.min(radius * 0.45F, 10.0F)));

        float highestSurface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centerX, centerZ) - 1.0F;
        highestSurface = Math.max(highestSurface, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centerX + sampleOffset, centerZ) - 1.0F);
        highestSurface = Math.max(highestSurface, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centerX - sampleOffset, centerZ) - 1.0F);
        highestSurface = Math.max(highestSurface, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centerX, centerZ + sampleOffset) - 1.0F);
        highestSurface = Math.max(highestSurface, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centerX, centerZ - sampleOffset) - 1.0F);
        return highestSurface;
    }

    private static void debug(String message, Object... args) {
        if (ProjectAtmosphere.DEBUG_MODE) {
            ProjectAtmosphere.LOGGER.info("[TornadoDebug] " + message, args);
        }
    }

    private record PreparedTornado(UUID id, float centerX, float centerZ, float bottomY, float height,
                                   float width, float stormSize, float spin, float intensity,
                                   float shape, float touchdownProgress, float seed, float animationTime,
                                   Vec3 renderPosWorld, float renderBottomWorld, float terrainSurfaceWorld,
                                   float bottomWorld, float topWorld, float cloudHeightWorld, float scale,
                                   float boundsRadiusCloud, float boundsRadiusWorld, float wallcloudRadiusWorld) {
        static PreparedTornado from(ClientLevel level, TornadoInstance tornado, float animationTime, float partialTick) {
            float scale = SimpleCloudsConstants.CLOUD_SCALE;
            float cloudHeight = CloudManager.get(level).getCloudHeight();
            Vec3 renderPos = tornado.getRenderPosition(partialTick);
            float renderBottomY = tornado.getRenderBottomY(partialTick);
            float renderRadius = tornado.getRenderRadius(partialTick);
            float terrainSurfaceY = sampleTerrainSurfaceY(level, renderPos, renderRadius);
            float contactExtension = Math.max(GROUND_CONTACT_EXTENSION_WORLD, renderBottomY - terrainSurfaceY + GROUND_CONTACT_PADDING_WORLD);
            float centerX = (float) renderPos.x / scale;
            float centerZ = (float) renderPos.z / scale;
            float bottomWorld = renderBottomY - contactExtension;
            float topWorld = Math.max(
                    renderBottomY + tornado.getRenderHeight(partialTick),
                    cloudHeight + CLOUD_BLEND_PAD_ABOVE_CLOUD_BASE_WORLD
            );
            float bottomY = (bottomWorld - cloudHeight) / scale;
            float height = Math.max((topWorld - bottomWorld) / scale, MIN_VISUAL_WORLD_HEIGHT / scale);
            float width = Math.max(renderRadius * 2.0F, MIN_VISUAL_WORLD_WIDTH) / scale;
            float stormSize = Math.max(MIN_VISUAL_WORLD_STORM_SIZE / scale, Math.max(width * 4.25F, height * 0.34F));
            float boundsRadiusCloud = Math.max(width * 5.4F, stormSize * 0.58F);
            float boundsRadiusWorld = boundsRadiusCloud * scale;
            float wallcloudRadiusWorld = stormSize * scale * 0.35F;
            float intensity = Mth.clamp(tornado.getNormalizedIntensity(), 0.0F, 1.0F);
            float touchdownProgress = switch (tornado.getPhase()) {
                case FORMING -> Mth.clamp(intensity * 1.35F, 0.0F, 0.92F);
                case ACTIVE -> Mth.clamp(0.72F + intensity * 0.35F, 0.0F, 1.0F);
                case DISSIPATING -> Mth.clamp(intensity * 1.10F, 0.0F, 1.0F);
                default -> 0.0F;
            };
            float seed = (Math.abs(tornado.getId().hashCode()) % 10000) / 10000.0F;
            float shape = 8.0F + seed * 10.0F;
            return new PreparedTornado(
                    tornado.getId(),
                    centerX,
                    centerZ,
                    bottomY,
                    height,
                    width,
                    stormSize,
                    tornado.getTwist(),
                    intensity,
                    shape,
                    touchdownProgress,
                    seed,
                    animationTime,
                    renderPos,
                    renderBottomY,
                    terrainSurfaceY,
                    bottomWorld,
                    topWorld,
                    cloudHeight,
                    scale,
                    boundsRadiusCloud,
                    boundsRadiusWorld,
                    wallcloudRadiusWorld
            );
        }

        float heightWorld() {
            return this.height * this.scale;
        }

        float widthWorld() {
            return this.width * this.scale;
        }

        float stormSizeWorld() {
            return this.stormSize * this.scale;
        }

        Vec3 originCloud() {
            return new Vec3(this.centerX, this.bottomY, this.centerZ);
        }

        Vec3 boundsMinCloud() {
            return new Vec3(
                    this.centerX - this.boundsRadiusCloud,
                    this.bottomY - (8.0F / this.scale),
                    this.centerZ - this.boundsRadiusCloud
            );
        }

        Vec3 boundsMaxCloud() {
            return new Vec3(
                    this.centerX + this.boundsRadiusCloud,
                    this.bottomY + this.height + (12.0F / this.scale),
                    this.centerZ + this.boundsRadiusCloud
            );
        }

        Vec3 originWorld() {
            return new Vec3(this.renderPosWorld.x, this.bottomWorld, this.renderPosWorld.z);
        }

        Vec3 centerWorld() {
            return new Vec3(this.renderPosWorld.x, (this.bottomWorld + this.topWorld) * 0.5F, this.renderPosWorld.z);
        }
    }

    private record AabbHit(float near, float far) {
    }

    private record CenterRayDiagnostic(Vec3 rayEndCloud, Vec3 rayDirectionCloud, float tNear, float tFar,
                                       float stepSize, Vec3 samplePosCloud, Vec3 samplePosWorld,
                                       Vec3 tornadoOriginCloud, Vec3 tornadoOriginWorld,
                                       Vec3 localPosCloud, Vec3 localPosWorld,
                                       DeterministicFunnelSample funnelSample) {
    }

    private record DeterministicFunnelSample(float height01, float heightMask, float radialMask,
                                             float funnelRadiusWorld, float density, float alpha,
                                             float wallcloudRadiusWorld, float wallcloudLowerWorld,
                                             float connectionRadiusWorld) {
        float radialDistanceWorld() {
            return this.radialMask >= 1.0F ? 0.0F : this.funnelRadiusWorld * (1.0F - this.radialMask);
        }
    }
}
