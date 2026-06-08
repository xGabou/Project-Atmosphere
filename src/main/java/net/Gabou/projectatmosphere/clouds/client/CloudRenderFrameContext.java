package net.Gabou.projectatmosphere.clouds.client;

import net.Gabou.projectatmosphere.clouds.client.render.CloudRenderProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

/**
 * Contexte immutable d'une frame de rendu cloud.
 * Cette classe regroupe les données client nécessaires au rendu live.
 */
public final class CloudRenderFrameContext {

    private final ClientLevel level;
    private final PoseStack poseStack;
    private final Vec3 cameraPosition;
    private final Matrix4f modelViewMatrix;
    private final Matrix4f projectionMatrix;
    private final Matrix4f inverseModelViewMatrix;
    private final Matrix4f inverseProjectionMatrix;
    private final long worldTime;
    private final float partialTick;
    private final CloudRenderProfile renderProfile;

    public CloudRenderFrameContext(
            @NotNull ClientLevel level,
            @NotNull PoseStack poseStack,
            @NotNull Vec3 cameraPosition,
            @NotNull Matrix4f modelViewMatrix,
            @NotNull Matrix4f projectionMatrix,
            @NotNull CloudRenderProfile renderProfile,
            long worldTime,
            float partialTick
    ) {
        this.level = level;
        this.poseStack = poseStack;
        this.cameraPosition = cameraPosition;
        this.modelViewMatrix = new Matrix4f(modelViewMatrix);
        this.projectionMatrix = new Matrix4f(projectionMatrix);
        this.inverseModelViewMatrix = new Matrix4f(modelViewMatrix).invert();
        this.inverseProjectionMatrix = new Matrix4f(projectionMatrix).invert();
        this.worldTime = worldTime;
        this.partialTick = partialTick;
        this.renderProfile = renderProfile;
    }

    public @NotNull ClientLevel getLevel() {
        return level;
    }

    public @NotNull PoseStack getPoseStack() {
        return poseStack;
    }

    public @NotNull Vec3 getCameraPosition() {
        return cameraPosition;
    }

    public @NotNull Matrix4f getModelViewMatrix() {
        return new Matrix4f(modelViewMatrix);
    }

    public @NotNull Matrix4f getProjectionMatrix() {
        return new Matrix4f(projectionMatrix);
    }

    public @NotNull Matrix4f getInverseModelViewMatrix() {
        return new Matrix4f(inverseModelViewMatrix);
    }

    public @NotNull Matrix4f getInverseProjectionMatrix() {
        return new Matrix4f(inverseProjectionMatrix);
    }

    public long getWorldTime() {
        return worldTime;
    }

    public float getPartialTick() {
        return partialTick;
    }
    public @NotNull CloudRenderProfile getRenderProfile() {
        return renderProfile;
    }
}
