package net.Gabou.projectatmosphere.clouds.frontend;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

/**
 * Prépare le futur rendu raymarch des nuages live.
 * Cette classe ne lit pas le backend et ne touche jamais au debugSnapshot.
 */
public final class CloudRaymarchRenderer {

    private static final int LIVE_SLICE_COUNT = 12;
    private static final float LIVE_ALPHA_SCALE = 0.55F;
    private static final float LIVE_MIN_ALPHA = 0.02F;

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

        renderLiveSlices(frameContext, snapshot);
    }

    /**
     * Dessine des tranches translucides temporaires pour matérialiser le volume live.
     * Ce rendu appartient au chemin live et ne réutilise jamais le renderer debug.
     *
     * @param frameContext contexte de rendu de la frame courante
     * @param snapshot snapshot live validé
     */
    private static void renderLiveSlices(
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

        if (center.distanceToSqr(cameraPosition) > square(frameContext.getRenderProfile().getMaxRenderDistance())) {
            return;
        }

        int color = snapshot.getDebugColorOrTint();
        float red = ((color >> 16) & 255) / 255.0F;
        float green = ((color >> 8) & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;

        PoseStack poseStack = frameContext.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cameraPosition.x(), -cameraPosition.y(), -cameraPosition.z());

        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        float height = cloudTopY - cloudBaseY;
        float sliceStep = height / LIVE_SLICE_COUNT;

        for (int sliceIndex = 0; sliceIndex < LIVE_SLICE_COUNT; sliceIndex++) {
            float sliceY = cloudBaseY + sliceStep * (sliceIndex + 0.5F);
            Vec3 samplePosition = new Vec3(center.x(), sliceY, center.z());
            float density = CloudDensityProvider.sampleDensity(snapshot, samplePosition);
            float alpha = density * LIVE_ALPHA_SCALE;

            if (alpha <= LIVE_MIN_ALPHA) {
                continue;
            }

            float normalizedVertical = (sliceIndex + 0.5F) / LIVE_SLICE_COUNT;
            float verticalShape = 1.0F - Math.abs(normalizedVertical - 0.5F) * 0.28F;
            float sliceRadius = radius * verticalShape;
            float minX = (float) center.x() - sliceRadius;
            float maxX = (float) center.x() + sliceRadius;
            float minZ = (float) center.z() - sliceRadius;
            float maxZ = (float) center.z() + sliceRadius;

            addSliceQuad(builder, matrix, minX, sliceY, minZ, maxX, maxZ, red, green, blue, alpha);
        }

        BufferUploader.drawWithShader(builder.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    /**
     * Ajoute un quad horizontal coloré dans le buffer live.
     *
     * @param builder buffer de géométrie
     * @param matrix matrice de pose courante
     * @param minX bord ouest
     * @param y altitude de la tranche
     * @param minZ bord nord
     * @param maxX bord est
     * @param maxZ bord sud
     * @param red composante rouge
     * @param green composante verte
     * @param blue composante bleue
     * @param alpha transparence de la tranche
     */
    private static void addSliceQuad(
            @NotNull BufferBuilder builder,
            @NotNull Matrix4f matrix,
            float minX,
            float y,
            float minZ,
            float maxX,
            float maxZ,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        builder.vertex(matrix, minX, y, minZ).color(red, green, blue, alpha).endVertex();
        builder.vertex(matrix, maxX, y, minZ).color(red, green, blue, alpha).endVertex();
        builder.vertex(matrix, maxX, y, maxZ).color(red, green, blue, alpha).endVertex();
        builder.vertex(matrix, minX, y, maxZ).color(red, green, blue, alpha).endVertex();
    }

    private static float square(float value) {
        return value * value;
    }
}
