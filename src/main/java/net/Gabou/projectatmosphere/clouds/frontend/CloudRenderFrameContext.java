package net.Gabou.projectatmosphere.clouds.frontend;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Contexte immutable d'une frame de rendu cloud.
 * Cette classe regroupe les données client nécessaires au rendu live.
 */
public final class CloudRenderFrameContext {

    private final ClientLevel level;
    private final PoseStack poseStack;
    private final Vec3 cameraPosition;
    private final long worldTime;
    private final float partialTick;
    private final CloudRenderProfile renderProfile;

    public CloudRenderFrameContext(
            @NotNull ClientLevel level,
            @NotNull PoseStack poseStack,
            @NotNull Vec3 cameraPosition,
            @NotNull CloudRenderProfile renderProfile,
            long worldTime,
            float partialTick
    ) {
        this.level = level;
        this.poseStack = poseStack;
        this.cameraPosition = cameraPosition;
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