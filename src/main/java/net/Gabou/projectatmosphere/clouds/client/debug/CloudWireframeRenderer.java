package net.Gabou.projectatmosphere.clouds.client.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderSnapshot;
import net.Gabou.projectatmosphere.clouds.client.render.CloudRenderAabb;
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

        CloudRenderAabb.Bounds bounds = CloudRenderAabb.compute(snapshot);
        if (bounds == null) {
            return;
        }

        double minX = bounds.min().x() - cameraPosition.x();
        double minY = bounds.min().y() - cameraPosition.y();
        double minZ = bounds.min().z() - cameraPosition.z();
        double maxX = bounds.max().x() - cameraPosition.x();
        double maxY = bounds.max().y() - cameraPosition.y();
        double maxZ = bounds.max().z() - cameraPosition.z();

        drawBox(poseStack, bufferSource, minX, minY, minZ, maxX, maxY, maxZ, snapshot.getDebugColorOrTint());
    }

    private static void drawBox(PoseStack poseStack, MultiBufferSource bufferSource, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int color){
        float alpha = ((color >> 24) & 255) / 255.0F;
        float red = ((color >> 16) & 255) / 255.0F;
        float green = ((color >> 8) & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;

        AABB box = new AABB(minX, minY, minZ, maxX, maxY, maxZ);

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());

        poseStack.pushPose();
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
