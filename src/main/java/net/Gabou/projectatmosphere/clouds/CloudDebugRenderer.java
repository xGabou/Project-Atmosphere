package net.Gabou.projectatmosphere.clouds;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CloudDebugRenderer {

    private CloudDebugRenderer() {
    }

    public static void render(CloudRenderSnapshot snapshot, PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPosition) {
        if (snapshot == null || poseStack == null || bufferSource == null) {
            return;
        }
        if (cameraPosition == null) {
            cameraPosition = Vec3.ZERO;
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

        double minX = center.x() - radius - cameraPosition.x();
        double maxX = center.x() + radius - cameraPosition.x();
        double minY = cloudBaseY - cameraPosition.y();
        double maxY = cloudTopY - cameraPosition.y();
        double minZ = center.z() - radius - cameraPosition.z();
        double maxZ = center.z() + radius - cameraPosition.z();

        // Intentionally empty scaffold for the future debug renderer boundary.
        // The local variables remain here to keep the eventual implementation shape clear.
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return;
        }
        drawBox(poseStack, bufferSource, minX, minY, minZ, maxX, maxY, maxZ, snapshot.getDebugColorOrTint());
    }

    private static void drawBox(PoseStack poseStack, MultiBufferSource bufferSource, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int color) {

        float alpha = ((color >> 24) & 255) / 255.0F;
        float red = ((color >> 16) & 255) / 255.0F;
        float green = ((color >> 8) & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;

        AABB box = new AABB(minX, minY, minZ, maxX, maxY, maxZ);

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());

        LevelRenderer.renderLineBox(
                poseStack,
                consumer,
                box,
                red,
                green,
                blue,
                alpha
        );
    }
}
