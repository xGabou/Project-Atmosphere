package net.Gabou.projectatmosphere.clouds.frontend;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Prépare le futur rendu raymarch des nuages live.
 * Cette classe ne lit pas le backend et ne touche jamais au debugSnapshot.
 */
public final class CloudRaymarchRenderer {

    private CloudRaymarchRenderer() {

    }

    /**
     * Prépare le rendu d'un snapshot live.
     * Le vrai shader volumétrique sera branché ici plus tard.
     *
     * @param frameContext contexte de rendu de la frame courante
     * @param snapshot snapshot live valide
     */
    public static void renderSnapshot(
            @NotNull CloudRenderFrameContext frameContext,
            @NotNull CloudRenderSnapshot snapshot
    ) {
        float effectiveDensity = CloudDensityProvider.getEffectiveDensity(snapshot);
        float effectiveCoverage = CloudDensityProvider.getEffectiveCoverage(snapshot);

        if (effectiveDensity <= 0.001F || effectiveCoverage <= 0.001F) {
            return;
        }

        CloudRenderProfile profile = frameContext.getRenderProfile();

        int steps = profile.getRaymarchSteps();
        float maxDistance = profile.getMaxRenderDistance();
        float resolutionScale = profile.getResolutionScale();
        float centerDensity = CloudDensityProvider.sampleDensity(snapshot, snapshot.getRegionCenter());

        if (centerDensity <= 0.001F || steps <= 0 || maxDistance <= 0.0F || resolutionScale <= 0.0F) {
            return;
        }

        renderTemporaryLiveBounds(frameContext, snapshot);
    }

    /**
     * Dessine une boîte live temporaire pour valider le pipeline client.
     * Ce rendu appartient au chemin live et ne réutilise pas le renderer debug.
     *
     * @param frameContext contexte de rendu de la frame courante
     * @param snapshot snapshot live validé
     */
    private static void renderTemporaryLiveBounds(
            @NotNull CloudRenderFrameContext frameContext,
            @NotNull CloudRenderSnapshot snapshot
    ) {
        Vec3 center = snapshot.getRegionCenter();
        Vec3 cameraPosition = frameContext.getCameraPosition();
        float radius = snapshot.getRegionRadius();
        float cloudBaseY = snapshot.getCloudBaseY();
        float cloudTopY = snapshot.getCloudTopY();

        if (center == null || cameraPosition == null || radius <= 0.0F || cloudTopY <= cloudBaseY) {
            return;
        }

        PoseStack poseStack = frameContext.getPoseStack();
        MultiBufferSource.BufferSource buffer = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer consumer = buffer.getBuffer(RenderType.lines());

        double minX = -radius;
        double maxX = radius;
        double minY = cloudBaseY - center.y();
        double maxY = cloudTopY - center.y();
        double minZ = -radius;
        double maxZ = radius;

        Vec3 renderOffset = center.subtract(cameraPosition);
        int color = snapshot.getDebugColorOrTint();
        float alpha = Math.max(0.35F, ((color >> 24) & 255) / 255.0F);
        float red = ((color >> 16) & 255) / 255.0F;
        float green = ((color >> 8) & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;

        poseStack.pushPose();
        poseStack.translate(renderOffset.x(), renderOffset.y(), renderOffset.z());
        LevelRenderer.renderLineBox(
                poseStack,
                consumer,
                new AABB(minX, minY, minZ, maxX, maxY, maxZ),
                red,
                green,
                blue,
                alpha
        );
        poseStack.popPose();
        buffer.endBatch(RenderType.lines());
    }
}
