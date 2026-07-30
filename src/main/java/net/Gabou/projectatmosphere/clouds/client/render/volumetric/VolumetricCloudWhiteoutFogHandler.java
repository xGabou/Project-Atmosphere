package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import net.Gabou.projectatmosphere.clouds.client.render.ClientCloudRenderOwnership;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.joml.Vector3f;

/**
 * In-cloud whiteout for the PA-native volumetric renderer: when the camera is
 * inside cloud, Minecraft fog closes in and takes the cloud's ambient color,
 * so world geometry fades exactly like the raymarched volume around it.
 */
public final class VolumetricCloudWhiteoutFogHandler {
    private VolumetricCloudWhiteoutFogHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.NONE) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (!ClientCloudRenderOwnership.ownsBaseCloudRendering(level)
                || !ClientCloudVisualDensity.hasRenderedData()) {
            return;
        }
        float density = CameraCloudDensityTracker.smoothedCameraDensity();
        if (density <= 0.03F) {
            return;
        }

        // Extinction-matched fog range: denser cloud pulls visibility in hard.
        float visibility = Mth.lerp(Mth.clamp(density * 1.6F, 0.0F, 1.0F), 380.0F, 14.0F);
        float near = Math.min(event.getNearPlaneDistance(), visibility * 0.08F);
        float far = Math.min(event.getFarPlaneDistance(), visibility);
        event.setNearPlaneDistance(near);
        event.setFarPlaneDistance(Math.max(near + 4.0F, far));
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (!ClientCloudRenderOwnership.ownsBaseCloudRendering(level)
                || !ClientCloudVisualDensity.hasRenderedData()) {
            return;
        }
        float density = CameraCloudDensityTracker.smoothedCameraDensity();
        if (density <= 0.03F) {
            return;
        }

        float partialTick = (float) event.getPartialTick();
        VolumetricCloudLighting.Frame lighting = VolumetricCloudLighting.resolve(
                level,
                Minecraft.getInstance().gameRenderer.getMainCamera().getPosition(),
                partialTick
        );
        // Inside dense cloud the ambient term dominates: mix top and bottom
        // ambient into a hazy interior tone.
        Vector3f interior = new Vector3f(lighting.ambientTop()).lerp(lighting.ambientBottom(), 0.42F);
        interior.mul(1.0F - lighting.stormDarkening() * 0.4F);
        float blend = Mth.clamp(density * 1.5F, 0.0F, 1.0F);
        event.setRed(Mth.lerp(blend, event.getRed(), Mth.clamp(interior.x, 0.0F, 1.0F)));
        event.setGreen(Mth.lerp(blend, event.getGreen(), Mth.clamp(interior.y, 0.0F, 1.0F)));
        event.setBlue(Mth.lerp(blend, event.getBlue(), Mth.clamp(interior.z, 0.0F, 1.0F)));
    }
}
