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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
        CloudRenderTargetManager.onLevelChanged();
    }

    public static void shutdown() {
        CloudRenderStateUpdater.clearCurrentSnapshots();
        CloudShadowRenderer.clear();
        CloudRaymarchRenderer.resetTemporalState();
        CloudRenderTargetManager.shutdown();
    }

    /**
     * Prépare le rendu live des nuages à partir du contexte de frame courant.
     *
     * @param frameContext contexte de rendu de la frame courante
     */
    public static void render(@NotNull CloudRenderFrameContext frameContext) {
        Minecraft minecraft = Minecraft.getInstance();
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
                    "[CloudState] stage=AFTER_PARTICLES screen={} worldTime={} adapter={} quality={} steps={} scale={} main={}x{} mainColor={} mainDepth={} cloud={}x{} cloudColor={} cloudDepth={} shadowDepth={} intermediate={} downscaled={}",
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

        try {
            boolean depthProbeEnabled = CloudRenderDiagnostics.shouldLogDepthProbe(frameContext.getWorldTime());
            float[] preSceneDepths = depthProbeEnabled ? captureSceneDepths(mainTarget) : null;
            cloudTarget.bindWrite(true);
            int sceneDepthTextureId = mainTarget.getDepthTextureId();
            for (CloudRenderLodPlan plan : renderPlans) {
                long raymarchCpuStart = CloudRenderDiagnostics.nowNs();
                CloudRenderFrameContext lodFrameContext = frameContext.withRenderProfile(plan.renderProfile());
                if (renderSnapshot(lodFrameContext, plan.snapshot(), cloudTarget, sceneDepthTextureId, RAYMARCH_GPU_TIMER)) {
                    CloudRenderDiagnostics.recordRaymarchCpuTime(raymarchCpuStart);
                    CloudRenderDiagnostics.recordRendered(plan.snapshot());
                } else {
                    CloudRenderDiagnostics.recordRaymarchCpuTime(raymarchCpuStart);
                    CloudRenderDiagnostics.recordSubmitSkipped();
                }
            }

            logGlError("cloud-raymarch-submit");

            if (depthProbeEnabled) {
                logDepthProbeFrame(frameContext, mainTarget, cloudTarget, usesIntermediateTarget, preSceneDepths);
            }

            if (usesIntermediateTarget) {
                long compositeCpuStart = CloudRenderDiagnostics.nowNs();
                COMPOSITE_GPU_TIMER.begin();
                CloudRenderTargetManager.invalidateCloudHistory();
                CloudRenderDiagnostics.recordCompositeSubmitted(
                        CloudRaymarchRenderer.compositeTarget(
                                cloudTarget,
                                cloudTarget,
                                mainTarget,
                                sceneDepthTextureId,
                                frameContext.getRenderProfile().getCompositeBlurRadius(),
                                frameContext.getRenderProfile().getCompositeBlurStrength()
                        )
                );
                COMPOSITE_GPU_TIMER.end();
                CloudRenderDiagnostics.recordCompositeCpuTime(compositeCpuStart);
                logGlError("cloud-composite-submit");
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
            CloudRenderDiagnostics.finishFrame();
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
            @Nullable float[] preSceneDepths
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

    private static void logGlError(@NotNull String context) {
        int error = GL11.glGetError();
        if (error == GL11.GL_NO_ERROR) {
            return;
        }

        ProjectAtmosphere.LOGGER.warn(
                "[CloudState] glError context={} code=0x{}",
                context,
                String.format(Locale.ROOT, "%04X", error)
        );
    }

    private record DepthProbePoint(String label, float screenUx, float screenUy) {
    }
}
