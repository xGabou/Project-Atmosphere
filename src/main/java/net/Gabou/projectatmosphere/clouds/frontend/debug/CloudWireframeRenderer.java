package net.Gabou.projectatmosphere.clouds.frontend.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.Gabou.projectatmosphere.clouds.frontend.CloudRenderSnapshot;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CloudWireframeRenderer {

    private CloudWireframeRenderer() {
    }

    public static void render(CloudRenderSnapshot snapshot, PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPosition) {
        if (snapshot == null || poseStack == null || bufferSource == null || cameraPosition == null) {
            return;
        }

        if (!snapshot.isEnabled()) {
            return;
        }

        Vec3 center = snapshot.getRegionCenter();
        float radius = snapshot.getRegionRadius();
        float cloudBaseY = snapshot.getCloudBaseY();
        float cloudTopY = snapshot.getCloudTopY();

        if (center == null || radius <= 0.0F || cloudTopY <= cloudBaseY) {
            return;
        }

        double minX = -radius;
        double maxX = radius;
        double minY = cloudBaseY - center.y();
        double maxY = cloudTopY - center.y();
        double minZ = -radius;
        double maxZ = radius;

        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return;
        }

        Vec3 renderOffset = center.subtract(cameraPosition);
        drawBox(poseStack, bufferSource, renderOffset, minX, minY, minZ, maxX, maxY, maxZ, snapshot.getDebugColorOrTint());
    }

    private static void drawBox(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 renderOffset, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int color){
        float alpha = ((color >> 24) & 255) / 255.0F;
        float red = ((color >> 16) & 255) / 255.0F;
        float green = ((color >> 8) & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;

        AABB box = new AABB(minX, minY, minZ, maxX, maxY, maxZ);

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(renderOffset.x(), renderOffset.y(), renderOffset.z());
        LevelRenderer.renderLineBox(
                poseStack,
                consumer,
                box,
                red,
                green,
                blue,
                alpha
        );
        poseStack.popPose();
    }
}
