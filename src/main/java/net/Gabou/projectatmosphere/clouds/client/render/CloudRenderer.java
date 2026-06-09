package net.Gabou.projectatmosphere.clouds.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderController;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderFrameContext;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderSnapshot;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderStateHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Point d'entrée du futur rendu live des nuages.
 * Cette classe ne gère pas le rendu debug et ne lit jamais debugSnapshot.
 */
public final class CloudRenderer {
    private static final CloudGpuTimer RAYMARCH_GPU_TIMER = new CloudGpuTimer();
    private static final CloudGpuTimer COMPOSITE_GPU_TIMER = new CloudGpuTimer();

    private CloudRenderer() {

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

        boolean downscaled = cloudTarget != mainTarget;
        if (downscaled) {
            cloudTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            cloudTarget.clear(Minecraft.ON_OSX);
        }

        List<CloudRenderSnapshot> sourceSnapshots = CloudRenderStateHolder.getInstance().getCurrentSnapshots();
        List<CloudRenderSnapshot> renderableSnapshots = CloudRenderController.getRenderableLiveSnapshots();
        CloudRenderDiagnostics.beginFrame(
                frameContext,
                mainTarget,
                cloudTarget,
                sourceSnapshots.size(),
                renderableSnapshots.size(),
                downscaled
        );

        try {
            cloudTarget.bindWrite(true);
            int sceneDepthTextureId = mainTarget.getDepthTextureId();
            for (CloudRenderSnapshot snapshot : renderableSnapshots) {
                long raymarchCpuStart = CloudRenderDiagnostics.nowNs();
                if (renderSnapshot(frameContext, snapshot, cloudTarget, sceneDepthTextureId, RAYMARCH_GPU_TIMER)) {
                    CloudRenderDiagnostics.recordRaymarchCpuTime(raymarchCpuStart);
                    CloudRenderDiagnostics.recordRendered(snapshot);
                } else {
                    CloudRenderDiagnostics.recordRaymarchCpuTime(raymarchCpuStart);
                    CloudRenderDiagnostics.recordSubmitSkipped();
                }
            }

            if (downscaled) {
                long compositeCpuStart = CloudRenderDiagnostics.nowNs();
                COMPOSITE_GPU_TIMER.begin();
                CloudRenderDiagnostics.recordCompositeSubmitted(CloudRaymarchRenderer.compositeTarget(cloudTarget, mainTarget));
                COMPOSITE_GPU_TIMER.end();
                CloudRenderDiagnostics.recordCompositeCpuTime(compositeCpuStart);
            } else {
                COMPOSITE_GPU_TIMER.poll();
            }
            RAYMARCH_GPU_TIMER.poll();
            CloudRenderDiagnostics.recordGpuTimings(
                    RAYMARCH_GPU_TIMER.getLastMilliseconds(),
                    downscaled ? COMPOSITE_GPU_TIMER.getLastMilliseconds() : 0.0F,
                    RAYMARCH_GPU_TIMER.isSupported() && COMPOSITE_GPU_TIMER.isSupported(),
                    RAYMARCH_GPU_TIMER.hasResult(),
                    downscaled && COMPOSITE_GPU_TIMER.hasResult(),
                    RAYMARCH_GPU_TIMER.getLastResultAgeFrames(),
                    downscaled ? COMPOSITE_GPU_TIMER.getLastResultAgeFrames() : -1,
                    RAYMARCH_GPU_TIMER.getPendingQueries(),
                    downscaled ? COMPOSITE_GPU_TIMER.getPendingQueries() : 0
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
}
