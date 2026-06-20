package net.Gabou.projectatmosphere.tools.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;

import java.util.Locale;

public final class WorldSpaceDebugCubeRenderer {
    private static final double HALF_SIZE = 5.0D;
    private static final int LOG_INTERVAL_TICKS = 80;

    private static boolean enabled;
    private static Vec3 anchorCenter;
    private static int lastLogTick = Integer.MIN_VALUE;

    private WorldSpaceDebugCubeRenderer() {
    }

    public static void setEnabled(boolean shouldEnable) {
        enabled = shouldEnable;
        anchorCenter = null;
        lastLogTick = Integer.MIN_VALUE;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static String status() {
        if (!enabled) {
            return "World-space test cube is off.";
        }

        if (anchorCenter == null) {
            return "World-space test cube is on; anchor will be captured on the next render frame.";
        }

        return "World-space test cube is on at center " + formatVec(anchorCenter) + ".";
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!enabled || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        if (anchorCenter == null) {
            anchorCenter = player.getPosition(event.getPartialTick()).add(0.0D, 20.0D, 0.0D);
        }

        Vec3 cameraPosition = event.getCamera().getPosition();
        AABB worldBox = new AABB(
                anchorCenter.x - HALF_SIZE,
                anchorCenter.y - HALF_SIZE,
                anchorCenter.z - HALF_SIZE,
                anchorCenter.x + HALF_SIZE,
                anchorCenter.y + HALF_SIZE,
                anchorCenter.z + HALF_SIZE
        );

        PoseStack poseStack = event.getPoseStack();
        logIfNeeded(event, poseStack, cameraPosition, worldBox);

        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffer.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
        LevelRenderer.renderLineBox(poseStack, consumer, worldBox, 1.0F, 0.0F, 0.0F, 1.0F);
        poseStack.popPose();

        buffer.endBatch(RenderType.lines());
    }

    private static void logIfNeeded(RenderLevelStageEvent event, PoseStack poseStack, Vec3 cameraPosition, AABB worldBox) {
        int renderTick = event.getRenderTick();
        if (renderTick - lastLogTick < LOG_INTERVAL_TICKS) {
            return;
        }

        lastLogTick = renderTick;
        Matrix4f pose = poseStack.last().pose();
        ProjectAtmosphere.LOGGER.info(
                "[WorldSpaceTestCube] stage={} camera={} anchor={} boxMin={} boxMax={} poseTranslation={}",
                event.getStage(),
                formatVec(cameraPosition),
                formatVec(anchorCenter),
                formatVec(new Vec3(worldBox.minX, worldBox.minY, worldBox.minZ)),
                formatVec(new Vec3(worldBox.maxX, worldBox.maxY, worldBox.maxZ)),
                formatPoseTranslation(pose)
        );
    }

    private static String formatVec(Vec3 vec) {
        return String.format(Locale.ROOT, "%.3f %.3f %.3f", vec.x, vec.y, vec.z);
    }

    private static String formatPoseTranslation(Matrix4f pose) {
        return String.format(Locale.ROOT, "%.3f %.3f %.3f", pose.m30(), pose.m31(), pose.m32());
    }
}
