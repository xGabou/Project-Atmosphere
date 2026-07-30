package net.Gabou.projectatmosphere.clouds.client.debug.field;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldRendererInput;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldSnapshot;
import net.Gabou.projectatmosphere.clouds.field.CloudletId;
import net.Gabou.projectatmosphere.clouds.field.CloudletLayout;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws a simple world-space CloudField debug overlay from renderer input.
 */
public final class CloudFieldDebugRenderer {
    private static final int CIRCLE_SEGMENTS = 48;
    private static final int VERTICAL_COLUMNS = 8;

    private CloudFieldDebugRenderer() {
    }

    public static void render(CloudFieldRendererInput input, PoseStack poseStack, MultiBufferSource bufferSource) {
        if (input == null || poseStack == null || bufferSource == null || input.fields().isEmpty()) {
            return;
        }

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        for (CloudFieldSnapshot snapshot : input.fields()) {
            if (snapshot == null || !snapshot.hasVisibleClouds()) {
                continue;
            }
            renderField(snapshot, poseStack, consumer, bufferSource);
        }
    }

    private static void renderField(
            CloudFieldSnapshot snapshot,
            PoseStack poseStack,
            VertexConsumer consumer,
            MultiBufferSource bufferSource
    ) {
        CloudFieldDebugColors.Color lodColor = CloudFieldDebugColors.forLod(
                snapshot.lodBand(),
                snapshot.hydrationProgress()
        );
        Vec3 center = snapshot.center();
        float radius = Math.max(0.0F, snapshot.radius());
        float baseY = snapshot.baseY();
        float topY = Math.max(baseY + 1.0F, snapshot.topY());

        drawCircle(poseStack, consumer, center.x(), baseY, center.z(), radius, lodColor);
        drawCircle(poseStack, consumer, center.x(), topY, center.z(), radius, lodColor);
        drawVerticalColumns(poseStack, consumer, center, radius, baseY, topY, lodColor);
        drawLine(poseStack, consumer, new Vec3(center.x(), baseY, center.z()), new Vec3(center.x(), topY, center.z()), CloudFieldDebugColors.CENTER);
        drawMarkerBox(poseStack, bufferSource, center, centerMarkerSize(snapshot), CloudFieldDebugColors.CENTER);

        if (snapshot.previousCenter() != null && snapshot.previousCenter().distanceTo(center) > 0.05D) {
            drawLine(poseStack, consumer, snapshot.previousCenter(), center, CloudFieldDebugColors.PREVIOUS_CENTER);
            drawMarkerBox(poseStack, bufferSource, snapshot.previousCenter(), centerMarkerSize(snapshot) * 0.72D, CloudFieldDebugColors.PREVIOUS_CENTER);
        }

        if (snapshot.windVector() != null && snapshot.windVector().lengthSqr() > 0.000001D) {
            Vec3 windEnd = center.add(snapshot.windVector().normalize().scale(Math.min(48.0D, Math.max(8.0D, radius * 0.18D))));
            drawLine(poseStack, consumer, center, windEnd, CloudFieldDebugColors.WIND);
        }

        drawCloudletMarkers(snapshot, poseStack, bufferSource);
    }

    private static void drawCloudletMarkers(
            CloudFieldSnapshot snapshot,
            PoseStack poseStack,
            MultiBufferSource bufferSource
    ) {
        int sampleCount = Math.min(snapshot.dynamicCloudletCount(), CloudFieldDebugRenderConfig.maxCloudletMarkers());
        if (sampleCount <= 0) {
            return;
        }

        CloudFieldDebugColors.Color color = CloudFieldDebugColors.cloudlet(snapshot.hydrationProgress());
        for (int i = 0; i < sampleCount; i++) {
            CloudletLayout.Cloudlet cloudlet = CloudletLayout.generate(snapshot, CloudletId.of(i));
            Vec3 worldCenter = cloudlet.worldCenter(snapshot);
            double halfSize = Math.max(0.75D, Math.min(4.0D, cloudlet.horizontalRadius() * 0.055D));
            halfSize *= 0.45D + Math.max(0.0F, Math.min(1.0F, snapshot.hydrationProgress())) * 0.55D;
            drawMarkerBox(poseStack, bufferSource, worldCenter, halfSize, color);
        }
    }

    private static void drawCircle(
            PoseStack poseStack,
            VertexConsumer consumer,
            double centerX,
            double y,
            double centerZ,
            double radius,
            CloudFieldDebugColors.Color color
    ) {
        if (radius <= 0.0D) {
            return;
        }

        for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
            double a0 = (Math.PI * 2.0D) * (double) i / (double) CIRCLE_SEGMENTS;
            double a1 = (Math.PI * 2.0D) * (double) (i + 1) / (double) CIRCLE_SEGMENTS;
            Vec3 start = new Vec3(centerX + Math.cos(a0) * radius, y, centerZ + Math.sin(a0) * radius);
            Vec3 end = new Vec3(centerX + Math.cos(a1) * radius, y, centerZ + Math.sin(a1) * radius);
            drawLine(poseStack, consumer, start, end, color);
        }
    }

    private static void drawVerticalColumns(
            PoseStack poseStack,
            VertexConsumer consumer,
            Vec3 center,
            double radius,
            double baseY,
            double topY,
            CloudFieldDebugColors.Color color
    ) {
        if (radius <= 0.0D) {
            return;
        }

        for (int i = 0; i < VERTICAL_COLUMNS; i++) {
            double angle = (Math.PI * 2.0D) * (double) i / (double) VERTICAL_COLUMNS;
            double x = center.x() + Math.cos(angle) * radius;
            double z = center.z() + Math.sin(angle) * radius;
            drawLine(poseStack, consumer, new Vec3(x, baseY, z), new Vec3(x, topY, z), color);
        }
    }

    private static void drawMarkerBox(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Vec3 center,
            double halfSize,
            CloudFieldDebugColors.Color color
    ) {
        if (center == null || halfSize <= 0.0D) {
            return;
        }

        AABB box = new AABB(
                center.x() - halfSize,
                center.y() - halfSize,
                center.z() - halfSize,
                center.x() + halfSize,
                center.y() + halfSize,
                center.z() + halfSize
        );
        LevelRenderer.renderLineBox(
                poseStack,
                bufferSource.getBuffer(RenderType.lines()),
                box,
                color.red(),
                color.green(),
                color.blue(),
                color.alpha()
        );
    }

    private static void drawLine(
            PoseStack poseStack,
            VertexConsumer consumer,
            Vec3 start,
            Vec3 end,
            CloudFieldDebugColors.Color color
    ) {
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        Vec3 direction = end.subtract(start);
        Vec3 normalized = direction.lengthSqr() <= 0.0000001D ? new Vec3(0.0D, 1.0D, 0.0D) : direction.normalize();
        putLineVertex(pose, normal, consumer, start, normalized, color);
        putLineVertex(pose, normal, consumer, end, normalized, color);
    }

    private static void putLineVertex(
            Matrix4f pose,
            Matrix3f normal,
            VertexConsumer consumer,
            Vec3 position,
            Vec3 lineNormal,
            CloudFieldDebugColors.Color color
    ) {
        consumer.addVertex(pose, (float) position.x(), (float) position.y(), (float) position.z())
                .setColor(color.red(), color.green(), color.blue(), color.alpha())
                .setNormal((float) lineNormal.x(), (float) lineNormal.y(), (float) lineNormal.z());
    }

    private static double centerMarkerSize(CloudFieldSnapshot snapshot) {
        return Math.max(1.5D, Math.min(6.0D, snapshot.radius() * 0.018D));
    }
}
