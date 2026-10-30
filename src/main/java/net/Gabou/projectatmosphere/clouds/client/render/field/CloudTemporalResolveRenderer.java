package net.Gabou.projectatmosphere.clouds.client.render.field;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.Gabou.projectatmosphere.client.render.shader.CloudFieldVolumeShaders;
import net.Gabou.projectatmosphere.clouds.client.render.CloudGpuTimer;
import net.Gabou.projectatmosphere.clouds.client.render.CloudTextureUnitContract;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.CloudTemporalPhaseState;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.FullscreenQuad;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.VolumetricCloudDebugConfig;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.VolumetricCloudRenderTargets;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

/**
 * T207. Resolves the four raw temporal phases into the lattice target - twice
 * the phase resolution - and composites the current frame's rain onto it.
 * The composite then upsamples that target exactly as it upsamples a cloud
 * target of the same size, so the display path is unchanged.
 *
 * <p>Inputs are the phase targets and the rain-pass target, and nothing else
 * temporal: the resolved target this pass writes is never bound as an input
 * here or anywhere (the sandbox asserts it), which is what keeps the
 * reconstruction non-recursive.
 */
public final class CloudTemporalResolveRenderer {
    private static final CloudGpuTimer GPU_TIMER = new CloudGpuTimer();
    private static volatile boolean lastFrameResolved;
    private static volatile int lastValidPhases;
    private static volatile float lastGpuMilliseconds = -1.0F;
    // E41. The camera of the frame the motion history holds, and the phase generation it belongs to.
    private static final Matrix4f motionPrevViewProj = new Matrix4f();
    private static final Vector3f motionPrevCamera = new Vector3f();
    private static long motionPrevGeneration = Long.MIN_VALUE;
    private static volatile float lastMotionWeight;

    private CloudTemporalResolveRenderer() {
    }

    /**
     * Resolves into the lattice target. Returns null when the pass could not
     * run - the caller composites the current phase directly then, so a frame
     * is never left without clouds.
     */
    public static RenderTarget resolve(
            RenderTarget rainPassTarget,
            Matrix4f invProj,
            Matrix4f invViewRot,
            Matrix4f viewProj,
            Vector3f cameraPos,
            float maxRenderDistance,
            long frameIndex
    ) {
        lastFrameResolved = false;
        ShaderInstance shader = CloudFieldVolumeShaders.getTemporalResolveShader();
        if (shader == null || !VolumetricCloudRenderTargets.phaseTargetsReady()) {
            return null;
        }
        int current = CloudTemporalPhaseState.currentPhase();
        if (!CloudTemporalPhaseState.valid(current, frameIndex)) {
            return null;
        }
        RenderTarget phase0 = VolumetricCloudRenderTargets.phaseTarget(0);
        RenderTarget resolved = VolumetricCloudRenderTargets.prepareTemporalResolvedTarget(
                phase0.width * 2, phase0.height * 2);
        if (resolved == null) {
            return null;
        }

        GPU_TIMER.poll();
        lastGpuMilliseconds = GPU_TIMER.isSupported() && GPU_TIMER.hasResult()
                ? GPU_TIMER.getLastMilliseconds() : -1.0F;
        VolumetricCloudRenderTargets.clearAndBind(resolved);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> shader);

        for (int phase = 0; phase < CloudTemporalPhaseState.PHASES; phase++) {
            RenderTarget target = VolumetricCloudRenderTargets.phaseTarget(phase);
            shader.setSampler("Phase" + phase + "Color", target.getColorTextureId());
            shader.setSampler("Phase" + phase + "Depth", target.getDepthTextureId());
            shader.safeGetUniform("PhaseViewProj" + phase).set(
                    CloudTemporalPhaseState.viewProj(phase));
            Vector3f origin = CloudTemporalPhaseState.cameraPos(phase);
            // Camera-relative: absolute world coordinates never reach the shader.
            shader.safeGetUniform("PhaseCamDelta" + phase).set(
                    cameraPos.x - origin.x, cameraPos.y - origin.y, cameraPos.z - origin.z);
            shader.safeGetUniform("PhaseValid" + phase).set(
                    CloudTemporalPhaseState.valid(phase, frameIndex) ? 1 : 0);
            shader.safeGetUniform("PhaseJitter" + phase).set(
                    CloudTemporalPhaseState.jitterX(phase), CloudTemporalPhaseState.jitterY(phase));
        }
        shader.setSampler("RainPassSampler",
                rainPassTarget == null ? 0 : rainPassTarget.getColorTextureId());
        // E41. Motion history: only when the camera moved since the frame the copy holds, and that copy belongs
        // to the same phase generation (no cut, resize or reload since). A static frame takes weight 0 - the
        // exact T207 + E39 output.
        RenderTarget motionHistory = VolumetricCloudRenderTargets.prepareMotionHistoryTarget(
                resolved.width, resolved.height);
        // The history is needed while the phase averages have not converged - under motion every phase is
        // replaced each frame (convergence 0), and for the frames after a stop until they have caught up.
        boolean moved = motionPrevCamera.distanceSquared(cameraPos) >= 1.0e-4F
                || !motionPrevViewProj.equals(viewProj, 1.0e-5F);
        float unconverged = 1.0F - CloudTemporalPhaseState.convergence(frameIndex,
                Math.round(1.0F / Math.max(VolumetricCloudDebugConfig.phaseAccumulateMinWeight(), 0.02F)));
        float motionWeight = motionHistory != null
                && motionPrevGeneration == CloudTemporalPhaseState.generation()
                ? VolumetricCloudDebugConfig.motionHistoryWeight() * (moved ? 1.0F : unconverged) : 0.0F;
        lastMotionWeight = motionWeight;
        shader.setSampler("MotionHistoryColor", motionHistory == null ? 0 : motionHistory.getColorTextureId());
        shader.setSampler("MotionHistoryDepth", motionHistory == null ? 0 : motionHistory.getDepthTextureId());
        shader.safeGetUniform("MotionHistoryWeight").set(motionWeight);
        shader.safeGetUniform("MotionPrevViewProj").set(motionPrevViewProj);
        shader.safeGetUniform("MotionPrevCamDelta").set(
                cameraPos.x - motionPrevCamera.x, cameraPos.y - motionPrevCamera.y, cameraPos.z - motionPrevCamera.z);
        shader.safeGetUniform("InvProjMat").set(invProj);
        shader.safeGetUniform("InvViewRotMat").set(invViewRot);
        shader.safeGetUniform("CloudViewProjMat").set(viewProj);
        shader.safeGetUniform("CurrentPhase").set(current);
        shader.safeGetUniform("MaxRenderDistance").set(maxRenderDistance);
        shader.safeGetUniform("ResolveMode").set(VolumetricCloudDebugConfig.temporalMode());
        shader.safeGetUniform("DebugOutput").set(VolumetricCloudDebugConfig.temporalDebugOutput());
        shader.safeGetUniform("CameraDensity").set(
                net.Gabou.projectatmosphere.clouds.client.render.volumetric
                        .CameraCloudDensityTracker.smoothedCameraDensity());
        shader.safeGetUniform("RainPassLattice").set(
                rainPassTarget != null && rainPassTarget.width == phase0.width * 2 ? 1 : 0);
        shader.apply();
        // The aux attachments, on the PA units beyond Minecraft's twelve.
        int program = shader.getId();
        for (int phase = 0; phase < CloudTemporalPhaseState.PHASES; phase++) {
            int location = GL20.glGetUniformLocation(program, "Phase" + phase + "Aux");
            int texture = VolumetricCloudRenderTargets.phaseAuxTexture(phase);
            if (location >= 0 && texture > 0) {
                int unit = CloudTextureUnitContract.PUFF_CANDIDATE_UNIT + phase;
                GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
                GL20.glUniform1i(location, unit);
            }
        }
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);

        GPU_TIMER.begin();
        try {
            FullscreenQuad.draw(shader);
        } finally {
            GPU_TIMER.end();
            for (int phase = 0; phase < CloudTemporalPhaseState.PHASES; phase++) {
                GlStateManager._activeTexture(
                        GL13.GL_TEXTURE0 + CloudTextureUnitContract.PUFF_CANDIDATE_UNIT + phase);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            }
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            shader.clear();
        }
        // E41. This frame becomes the motion history (a copy; the resolved target is never read back).
        VolumetricCloudRenderTargets.copyIntoMotionHistory(resolved);
        motionPrevViewProj.set(viewProj);
        motionPrevCamera.set(cameraPos);
        motionPrevGeneration = CloudTemporalPhaseState.generation();
        lastFrameResolved = true;
        lastValidPhases = CloudTemporalPhaseState.validCount(frameIndex);
        return resolved;
    }

    /** Milliseconds the last resolve cost on the GPU, or -1 with no result. */
    public static float lastGpuMilliseconds() {
        return lastGpuMilliseconds;
    }

    /** E41: the motion-history weight the last resolve used (0 = static camera / off). */
    public static float lastMotionWeight() {
        return lastMotionWeight;
    }

    public static boolean lastFrameResolved() {
        return lastFrameResolved;
    }

    public static int lastValidPhases() {
        return lastValidPhases;
    }

    public static void markUnused() {
        lastFrameResolved = false;
        lastGpuMilliseconds = -1.0F;
    }

    public static void shutdown() {
        GPU_TIMER.close();
        lastFrameResolved = false;
        lastGpuMilliseconds = -1.0F;
    }
}
