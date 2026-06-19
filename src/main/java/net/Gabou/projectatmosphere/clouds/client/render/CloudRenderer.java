package net.Gabou.projectatmosphere.clouds.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.client.Minecraft;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderController;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderFrameContext;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderSnapshot;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderStateHolder;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderStateUpdater;
import net.Gabou.projectatmosphere.client.render.pipeline.AtmospherePipelineAdapter;
import net.Gabou.projectatmosphere.client.render.pipeline.AtmospherePipelineAdapters;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.Locale;

/**
 * Point d'entrée du futur rendu live des nuages.
 * Cette classe ne gère pas le rendu debug et ne lit jamais debugSnapshot.
 */
public final class CloudRenderer {
    private static final CloudGpuTimer RAYMARCH_GPU_TIMER = new CloudGpuTimer();
    private static final CloudGpuTimer COMPOSITE_GPU_TIMER = new CloudGpuTimer();
    private static final float COMPOSITE_DEPTH_BIAS = 0.0005F;
    private static final float VISIBLE_ALPHA_THRESHOLD = 0.001F;
    private static final DepthProbePoint[] PROBE_POINTS = new DepthProbePoint[] {
            new DepthProbePoint("center", 0.50F, 0.50F),
            new DepthProbePoint("upper", 0.50F, 0.42F),
            new DepthProbePoint("lower", 0.50F, 0.58F)
    };

    private CloudRenderer() {

    }

    public static void onMainWindowResize(int width, int height) {
        CloudRaymarchRenderer.resetTemporalState();
        CloudRenderTargetManager.onResize(width, height);
    }

    public static void onClientLevelChanged() {
        CloudRenderStateUpdater.clearCurrentSnapshots();
        CloudShadowRenderer.clear();
        CloudRaymarchRenderer.resetTemporalState();
        CloudRenderFallbackState.resetAll();
        CloudRenderTargetManager.onLevelChanged();
    }

    public static void shutdown() {
        CloudRenderStateUpdater.clearCurrentSnapshots();
        CloudShadowRenderer.clear();
        CloudRaymarchRenderer.resetTemporalState();
        CloudRenderFallbackState.resetAll();
        CloudRenderTargetManager.shutdown();
    }

    /**
     * Prépare le rendu live des nuages à partir du contexte de frame courant.
     *
     * @param frameContext contexte de rendu de la frame courante
     */
    public static void render(@NotNull CloudRenderFrameContext frameContext) {
        CloudGlDebug.ensureInitialized();
        CloudGlDebug.pushGroup("cloud-frame");
        Minecraft minecraft = Minecraft.getInstance();
        try {
            RenderTarget mainTarget = minecraft.getMainRenderTarget();
            if (mainTarget == null) {
                return;
            }

            CloudRenderTargetManager.prepareTargets(frameContext.getRenderProfile());
            RenderTarget cloudTarget = CloudRenderTargetManager.getCloudColorTarget();
            if (cloudTarget == null) {
                return;
            }

            boolean usesIntermediateTarget = cloudTarget != mainTarget;
            boolean downscaled = frameContext.getRenderProfile().getResolutionScale() < 0.999F;
            if (usesIntermediateTarget) {
                cloudTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
                cloudTarget.clear(Minecraft.ON_OSX);
            }

            List<CloudRenderSnapshot> sourceSnapshots = CloudRenderStateHolder.getInstance().getCurrentSnapshots();
            List<CloudRenderSnapshot> renderableSnapshots = CloudRenderController.getRenderableLiveSnapshots();
            List<CloudRenderLodPlan> renderPlans = CloudRenderLodManager.createPlans(frameContext, renderableSnapshots);
            CloudRenderDiagnostics.beginFrame(
                    frameContext,
                    mainTarget,
                    cloudTarget,
                    sourceSnapshots.size(),
                    renderPlans.size(),
                    downscaled
            );

            String screenName = minecraft.screen == null ? "none" : minecraft.screen.getClass().getSimpleName();
            CloudShadowRenderer.update(frameContext, renderableSnapshots);
            FallbackDarkeningPass.updateFrame(frameContext, minecraft.level);
            FallbackDarkeningPass.applyTerrainDarkening(frameContext, mainTarget, null);
            AtmospherePipelineAdapter pipelineAdapter = AtmospherePipelineAdapters.select();
            int shadowDepthTextureId = -1;
            String stateSignature = screenName
                    + "|"
                    + pipelineAdapter.id()
                    + "|"
                    + frameContext.getRenderProfile().getRaymarchSteps()
                    + "|"
                    + formatProbeFloat(frameContext.getRenderProfile().getResolutionScale())
                    + "|"
                    + mainTarget.getColorTextureId()
                    + "|"
                    + mainTarget.getDepthTextureId()
                    + "|"
                    + cloudTarget.getColorTextureId()
                    + "|"
                    + cloudTarget.getDepthTextureId()
                    + "|"
                    + shadowDepthTextureId
                    + "|"
                    + usesIntermediateTarget
                    + "|"
                    + downscaled;
            if (CloudRenderDiagnostics.shouldLogStateSnapshot(frameContext.getWorldTime(), stateSignature)) {
                ProjectAtmosphere.LOGGER.info(
                        "[CloudState] stage=BEFORE_WEATHER screen={} worldTime={} adapter={} quality={} steps={} scale={} main={}x{} mainColor={} mainDepth={} cloud={}x{} cloudColor={} cloudDepth={} shadowDepth={} intermediate={} downscaled={}",
                        screenName,
                        frameContext.getWorldTime(),
                        pipelineAdapter.id(),
                        CloudRenderDiagnostics.getCurrentQualityName(),
                        frameContext.getRenderProfile().getRaymarchSteps(),
                        formatProbeFloat(frameContext.getRenderProfile().getResolutionScale()),
                        mainTarget.width,
                        mainTarget.height,
                        mainTarget.getColorTextureId(),
                        mainTarget.getDepthTextureId(),
                        cloudTarget.width,
                        cloudTarget.height,
                        cloudTarget.getColorTextureId(),
                        cloudTarget.getDepthTextureId(),
                        shadowDepthTextureId,
                        usesIntermediateTarget,
                        downscaled
                );
            }

            boolean depthProbeEnabled = CloudRenderDiagnostics.shouldLogDepthProbe(frameContext.getWorldTime());
            float[] preSceneDepths = depthProbeEnabled ? captureSceneDepths(mainTarget) : null;
            CloudGlDebug.pushGroup("cloud-raymarch-submit");
            try {
                cloudTarget.bindWrite(true);
                int sceneDepthTextureId = mainTarget.getDepthTextureId();
                boolean renderedAny = false;
                for (CloudRenderLodPlan plan : renderPlans) {
                    long raymarchCpuStart = CloudRenderDiagnostics.nowNs();
                    CloudRenderFrameContext lodFrameContext = frameContext.withRenderProfile(plan.renderProfile());
                    if (renderSnapshot(lodFrameContext, plan.snapshot(), cloudTarget, sceneDepthTextureId, RAYMARCH_GPU_TIMER)) {
                        renderedAny = true;
                        CloudRenderFallbackState.recordRenderedSnapshot(plan.snapshot());
                        CloudRenderDiagnostics.recordRaymarchCpuTime(raymarchCpuStart);
                        CloudRenderDiagnostics.recordRendered(plan.snapshot());
                    } else {
                        CloudRenderDiagnostics.recordRaymarchCpuTime(raymarchCpuStart);
                        CloudRenderDiagnostics.recordSubmitSkipped();
                    }
                }

                CloudGlDebug.checkErrors("cloud-raymarch-submit");

                if (depthProbeEnabled) {
                    logDepthProbeFrame(frameContext, mainTarget, cloudTarget, usesIntermediateTarget, preSceneDepths, renderPlans);
                }

                if (usesIntermediateTarget) {
                    long compositeCpuStart = CloudRenderDiagnostics.nowNs();
                    COMPOSITE_GPU_TIMER.begin();
                    CloudGlDebug.pushGroup("cloud-composite-pass");
                    try {
                        RenderTarget compositeSource = cloudTarget;
                        // Keep temporal accumulation disabled until the direct cloud target path is stable.
                        // A bad history resolve can make cloudTarget contain pixels while the composited source is empty.
                        CloudRenderTargetManager.invalidateCloudHistory();
                        CloudRenderDiagnostics.recordCompositeSubmitted(
                                CloudRaymarchRenderer.compositeTarget(
                                        compositeSource,
                                        cloudTarget,
                                        mainTarget,
                                        sceneDepthTextureId,
                                        frameContext.getRenderProfile().getCompositeBlurRadius(),
                                        frameContext.getRenderProfile().getCompositeBlurStrength()
                                )
                        );
                        CloudGlDebug.checkErrors("cloud-composite-submit");
                    } finally {
                        COMPOSITE_GPU_TIMER.end();
                        CloudGlDebug.popGroup();
                    }
                    CloudRenderDiagnostics.recordCompositeCpuTime(compositeCpuStart);
                } else {
                    COMPOSITE_GPU_TIMER.poll();
                }
                RAYMARCH_GPU_TIMER.poll();
                CloudRenderDiagnostics.recordGpuTimings(
                        RAYMARCH_GPU_TIMER.getLastMilliseconds(),
                        usesIntermediateTarget ? COMPOSITE_GPU_TIMER.getLastMilliseconds() : 0.0F,
                        RAYMARCH_GPU_TIMER.isSupported() && COMPOSITE_GPU_TIMER.isSupported(),
                        RAYMARCH_GPU_TIMER.hasResult(),
                        usesIntermediateTarget && COMPOSITE_GPU_TIMER.hasResult(),
                        RAYMARCH_GPU_TIMER.getLastResultAgeFrames(),
                        usesIntermediateTarget ? COMPOSITE_GPU_TIMER.getLastResultAgeFrames() : -1,
                        RAYMARCH_GPU_TIMER.getPendingQueries(),
                        usesIntermediateTarget ? COMPOSITE_GPU_TIMER.getPendingQueries() : 0
                );
            } finally {
                CloudGlDebug.popGroup();
            }
        } finally {
            CloudRenderDiagnostics.finishFrame();
            CloudGlDebug.popGroup();
        }
    }

    /**
     * Route un snapshot live valide vers la passe de rendu appropriée.
     *
     * @param frameContext contexte de rendu de la frame courante
     * @param snapshot snapshot live valide
     */
    private static boolean renderSnapshot(
            @NotNull CloudRenderFrameContext frameContext,
            @Nullable CloudRenderSnapshot snapshot,
            @NotNull RenderTarget cloudTarget,
            int sceneDepthTextureId,
            @NotNull CloudGpuTimer gpuTimer
    ) {
        if (snapshot == null) {
            return false;
        }
        return CloudRaymarchRenderer.renderSnapshot(frameContext, snapshot, cloudTarget, sceneDepthTextureId, gpuTimer);
    }

    private static float[] captureSceneDepths(@NotNull RenderTarget mainTarget) {
        mainTarget.bindWrite(false);
        float[] sceneDepths = new float[PROBE_POINTS.length];
        for (int i = 0; i < PROBE_POINTS.length; i++) {
            DepthProbePoint probePoint = PROBE_POINTS[i];
            int pixelX = toPixelX(probePoint.screenUx(), mainTarget.width);
            int pixelY = toPixelY(probePoint.screenUy(), mainTarget.height);
            sceneDepths[i] = readDepthPixel(pixelX, pixelY);
        }
        return sceneDepths;
    }

    private static void logDepthProbeFrame(
            @NotNull CloudRenderFrameContext frameContext,
            @NotNull RenderTarget mainTarget,
            @NotNull RenderTarget cloudTarget,
            boolean usesIntermediateTarget,
            @Nullable float[] preSceneDepths,
            @NotNull List<CloudRenderLodPlan> renderPlans
    ) {
        if (preSceneDepths == null || preSceneDepths.length != PROBE_POINTS.length) {
            return;
        }

        ProjectAtmosphere.LOGGER.info(
                "[CloudProbe] worldTime={} quality={} path={} main={}x{} cloud={}x{} scale={} steps={}",
                frameContext.getWorldTime(),
                CloudRenderDiagnostics.getCurrentQualityName(),
                usesIntermediateTarget ? "intermediate" : "direct",
                mainTarget.width,
                mainTarget.height,
                cloudTarget.width,
                cloudTarget.height,
                formatProbeFloat(frameContext.getRenderProfile().getResolutionScale()),
                frameContext.getRenderProfile().getRaymarchSteps()
        );

        for (int i = 0; i < PROBE_POINTS.length; i++) {
            DepthProbePoint probePoint = PROBE_POINTS[i];
            int pixelX = toPixelX(probePoint.screenUx(), cloudTarget.width);
            int pixelY = toPixelY(probePoint.screenUy(), cloudTarget.height);
            float cloudDepth = readDepthPixel(pixelX, pixelY);
            float cloudAlpha = readAlphaPixel(pixelX, pixelY) / 255.0F;
            float sceneDepth = preSceneDepths[i];
            float rejectMargin = sceneDepth + COMPOSITE_DEPTH_BIAS - cloudDepth;
            boolean wouldDiscard = sceneDepth + COMPOSITE_DEPTH_BIAS < cloudDepth;

            ProjectAtmosphere.LOGGER.info(
                    "[CloudProbe] {} uv={} px={} sceneDepth={} cloudDepth={} alpha={} margin={} decision={}",
                    probePoint.label(),
                    formatProbePoint(probePoint.screenUx(), probePoint.screenUy()),
                    pixelX + "," + pixelY,
                    formatProbeFloat(sceneDepth),
                    formatProbeFloat(cloudDepth),
                    formatProbeFloat(cloudAlpha),
                    formatProbeFloat(rejectMargin),
                    wouldDiscard ? "DISCARD" : "KEEP"
            );
        }

        OutputProbe maxProbe = scanCloudOutput(cloudTarget);
        if (maxProbe != null) {
            CloudRenderDiagnostics.recordMaxOutputAlpha(maxProbe.alpha());
            mainTarget.bindWrite(false);
            float maxSceneDepth = readDepthPixel(
                    toPixelX(maxProbe.uvX(), mainTarget.width),
                    toPixelY(maxProbe.uvY(), mainTarget.height)
            );
            cloudTarget.bindWrite(false);
            boolean maxWouldDiscard = maxSceneDepth + COMPOSITE_DEPTH_BIAS < maxProbe.depth();
            ProjectAtmosphere.LOGGER.info(
                    "[CloudProbe] maxAlpha uv={} px={} sceneDepth={} cloudDepth={} alpha={} margin={} decision={}",
                    formatProbePoint(maxProbe.uvX(), maxProbe.uvY()),
                    maxProbe.pixelX() + "," + maxProbe.pixelY(),
                    formatProbeFloat(maxSceneDepth),
                    formatProbeFloat(maxProbe.depth()),
                    formatProbeFloat(maxProbe.alpha()),
                    formatProbeFloat(maxSceneDepth + COMPOSITE_DEPTH_BIAS - maxProbe.depth()),
                    maxWouldDiscard ? "DISCARD" : "KEEP"
            );
            if (maxProbe.alpha() <= VISIBLE_ALPHA_THRESHOLD) {
                logRayAabbDiagnostics(frameContext, renderPlans);
            }
        }
    }

    private static void logRayAabbDiagnostics(
            @NotNull CloudRenderFrameContext frameContext,
            @NotNull List<CloudRenderLodPlan> renderPlans
    ) {
        if (renderPlans.isEmpty()) {
            ProjectAtmosphere.LOGGER.info("[CloudProbe] rayAabb plans=0");
            return;
        }

        CloudRenderSnapshot closest = null;
        double closestHorizontal = Double.POSITIVE_INFINITY;
        double closestEdge = Double.POSITIVE_INFINITY;
        double closestVertical = Double.POSITIVE_INFINITY;
        boolean closestInside = false;
        int rayHits = 0;
        CloudRenderSnapshot firstHit = null;

        Vec3 camera = frameContext.getCameraPosition();
        for (CloudRenderLodPlan plan : renderPlans) {
            CloudRenderSnapshot snapshot = plan.snapshot();
            Vec3 center = snapshot.getRegionCenter();
            double dx = camera.x() - center.x();
            double dz = camera.z() - center.z();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            double edge = Math.max(0.0D, horizontal - snapshot.getRegionRadius());
            double vertical = verticalDistance(camera.y(), snapshot.getCloudBaseY(), snapshot.getCloudTopY());
            boolean inside = horizontal <= snapshot.getRegionRadius() && vertical <= 0.0D;
            if (edge < closestEdge) {
                closest = snapshot;
                closestHorizontal = horizontal;
                closestEdge = edge;
                closestVertical = vertical;
                closestInside = inside;
            }

            for (DepthProbePoint probePoint : PROBE_POINTS) {
                Vec3 ray = worldRay(frameContext, probePoint.screenUx(), probePoint.screenUy());
                if (intersectsAabb(camera, ray, snapshot)) {
                    rayHits++;
                    if (firstHit == null) {
                        firstHit = snapshot;
                    }
                }
            }
        }

        ProjectAtmosphere.LOGGER.info(
                "[CloudProbe] rayAabb plans={} sampledRays={} hits={} closest={} closestHorizontal={} closestEdge={} closestVertical={} closestInside={} firstHit={}",
                renderPlans.size(),
                renderPlans.size() * PROBE_POINTS.length,
                rayHits,
                describeProbeCloud(closest),
                formatProbeFloat((float) closestHorizontal),
                formatProbeFloat((float) closestEdge),
                formatProbeFloat((float) closestVertical),
                closestInside,
                describeProbeCloud(firstHit)
        );
    }

    private static @Nullable OutputProbe scanCloudOutput(@NotNull RenderTarget cloudTarget) {
        if (cloudTarget.width <= 0 || cloudTarget.height <= 0) {
            return null;
        }

        ByteBuffer pixels = BufferUtils.createByteBuffer(cloudTarget.width * cloudTarget.height * 4);
        GL11.glReadPixels(0, 0, cloudTarget.width, cloudTarget.height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        OutputProbe best = null;
        for (int pixelY = 0; pixelY < cloudTarget.height; pixelY++) {
            int rowOffset = pixelY * cloudTarget.width * 4;
            for (int pixelX = 0; pixelX < cloudTarget.width; pixelX++) {
                int alphaByte = pixels.get(rowOffset + pixelX * 4 + 3) & 0xFF;
                float alpha = alphaByte / 255.0F;
                if (best == null || alpha > best.alpha()) {
                    float uvX = ((float) pixelX + 0.5F) / (float) cloudTarget.width;
                    float uvY = ((float) pixelY + 0.5F) / (float) cloudTarget.height;
                    best = new OutputProbe(uvX, uvY, pixelX, pixelY, readDepthPixel(pixelX, pixelY), alpha);
                    if (alphaByte == 255) {
                        return best;
                    }
                }
            }
        }
        return best;
    }

    private static Vec3 worldRay(@NotNull CloudRenderFrameContext frameContext, float uvX, float uvY) {
        Matrix4f inverseProjection = frameContext.getInverseProjectionMatrix();
        Matrix4f inverseModelView = frameContext.getInverseModelViewMatrix();
        Vector4f clip = new Vector4f(uvX * 2.0F - 1.0F, uvY * 2.0F - 1.0F, 1.0F, 1.0F);
        inverseProjection.transform(clip);
        if (Math.abs(clip.w()) > 0.000001F) {
            clip.div(clip.w());
        }

        float viewLength = (float) Math.sqrt(clip.x() * clip.x() + clip.y() * clip.y() + clip.z() * clip.z());
        if (viewLength > 0.000001F) {
            clip.set(clip.x() / viewLength, clip.y() / viewLength, clip.z() / viewLength, 0.0F);
        } else {
            clip.set(0.0F, 0.0F, -1.0F, 0.0F);
        }

        inverseModelView.transform(clip);
        double length = Math.sqrt(clip.x() * clip.x() + clip.y() * clip.y() + clip.z() * clip.z());
        if (length <= 0.000001D) {
            return new Vec3(0.0D, 0.0D, -1.0D);
        }
        return new Vec3(clip.x() / length, clip.y() / length, clip.z() / length);
    }

    private static boolean intersectsAabb(@NotNull Vec3 origin, @NotNull Vec3 direction, @NotNull CloudRenderSnapshot snapshot) {
        Vec3 center = snapshot.getRegionCenter();
        double radius = Math.max(1.0D, snapshot.getRegionRadius());
        double tMin = 0.0D;
        double tMax = Double.POSITIVE_INFINITY;

        AxisResult x = intersectAxis(origin.x(), direction.x(), center.x() - radius, center.x() + radius, tMin, tMax);
        if (!x.hit()) {
            return false;
        }
        tMin = x.min();
        tMax = x.max();

        AxisResult y = intersectAxis(origin.y(), direction.y(), snapshot.getCloudBaseY(), snapshot.getCloudTopY(), tMin, tMax);
        if (!y.hit()) {
            return false;
        }
        tMin = y.min();
        tMax = y.max();

        AxisResult z = intersectAxis(origin.z(), direction.z(), center.z() - radius, center.z() + radius, tMin, tMax);
        return z.hit() && z.max() > Math.max(z.min(), 0.0D);
    }

    private static AxisResult intersectAxis(double origin, double direction, double min, double max, double tMin, double tMax) {
        if (Math.abs(direction) <= 0.000001D) {
            return new AxisResult(origin >= min && origin <= max, tMin, tMax);
        }

        double inv = 1.0D / direction;
        double t0 = (min - origin) * inv;
        double t1 = (max - origin) * inv;
        double near = Math.min(t0, t1);
        double far = Math.max(t0, t1);
        double nextMin = Math.max(tMin, near);
        double nextMax = Math.min(tMax, far);
        return new AxisResult(nextMax > Math.max(nextMin, 0.0D), nextMin, nextMax);
    }

    private static double verticalDistance(double y, double minY, double maxY) {
        if (y < minY) {
            return minY - y;
        }
        if (y > maxY) {
            return y - maxY;
        }
        return 0.0D;
    }

    private static String describeProbeCloud(@Nullable CloudRenderSnapshot snapshot) {
        if (snapshot == null) {
            return "none";
        }

        Vec3 center = snapshot.getRegionCenter();
        return snapshot.getCloudTypeId()
                + "/r=" + formatProbeFloat(snapshot.getRegionRadius())
                + "/baseTop=" + formatProbeFloat(snapshot.getCloudBaseY()) + "-" + formatProbeFloat(snapshot.getCloudTopY())
                + "/center=" + formatProbeFloat((float) center.x()) + "," + formatProbeFloat((float) center.y()) + "," + formatProbeFloat((float) center.z());
    }

    private static float readDepthPixel(int pixelX, int pixelY) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer depthValue = stack.mallocFloat(1);
            GL11.glReadPixels(pixelX, pixelY, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depthValue);
            return depthValue.get(0);
        }
    }

    private static int readAlphaPixel(int pixelX, int pixelY) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer rgba = stack.malloc(4);
            GL11.glReadPixels(pixelX, pixelY, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, rgba);
            return rgba.get(3) & 0xFF;
        }
    }

    private static int toPixelX(float uv, int width) {
        if (width <= 1) {
            return 0;
        }

        return Math.max(0, Math.min(width - 1, (int) Math.floor(uv * (float) width)));
    }

    private static int toPixelY(float uv, int height) {
        if (height <= 1) {
            return 0;
        }

        return Math.max(0, Math.min(height - 1, (int) Math.floor(uv * (float) height)));
    }

    private static String formatProbeFloat(float value) {
        return String.format(Locale.ROOT, "%.5f", value);
    }

    private static String formatProbePoint(float x, float y) {
        return formatProbeFloat(x) + "," + formatProbeFloat(y);
    }

    private record DepthProbePoint(String label, float screenUx, float screenUy) {
    }

    private record OutputProbe(float uvX, float uvY, int pixelX, int pixelY, float depth, float alpha) {
    }

    private record AxisResult(boolean hit, double min, double max) {
    }
}
