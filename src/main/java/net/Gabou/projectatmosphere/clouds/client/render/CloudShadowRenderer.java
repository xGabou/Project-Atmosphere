package net.Gabou.projectatmosphere.clouds.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.api.CloudShadowMapAccess;
import net.Gabou.projectatmosphere.clouds.api.CloudShadowSnapshot;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderFrameContext;
import net.Gabou.projectatmosphere.clouds.client.CloudRenderSnapshot;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;

public final class CloudShadowRenderer {
    private static final int DEFAULT_GRID_RESOLUTION = 64;
    private static final float MIN_SHADOW_BOUNDS_RADIUS = 256.0F;
    private static final float SHADOW_FADE_RADIUS_SCALE = 1.25F;
    private static final float MAX_CELL_ADDITIVE_SHADOW = 0.92F;

    private CloudShadowRenderer() {
    }

    public static void update(
            @NotNull CloudRenderFrameContext frameContext,
            @NotNull List<CloudRenderSnapshot> snapshots,
            @Nullable RenderTarget shadowTarget
    ) {
        if (!isEnabled() || snapshots.isEmpty()) {
            CloudShadowMapAccess.clear();
            return;
        }

        ShadowBounds bounds = resolveBounds(frameContext, snapshots);
        if (!bounds.isValid()) {
            CloudShadowMapAccess.clear();
            return;
        }

        int resolution = DEFAULT_GRID_RESOLUTION;
        float[] values = new float[resolution * resolution];
        for (int z = 0; z < resolution; z++) {
            float worldZ = Mth.lerp(z / (float) (resolution - 1), bounds.minZ(), bounds.maxZ());
            for (int x = 0; x < resolution; x++) {
                float worldX = Mth.lerp(x / (float) (resolution - 1), bounds.minX(), bounds.maxX());
                values[z * resolution + x] = sampleCloudOcclusion(snapshots, worldX, worldZ);
            }
        }

        boolean uploaded = shadowTarget != null && uploadShadowTexture(shadowTarget, values, resolution, resolution);

        Matrix4f worldToShadow = new Matrix4f()
                .identity()
                .translate(-bounds.minX(), 0.0F, -bounds.minZ())
                .scale(1.0F / Math.max(1.0F, bounds.maxX() - bounds.minX()), 1.0F, 1.0F / Math.max(1.0F, bounds.maxZ() - bounds.minZ()));
        int textureId = uploaded ? shadowTarget.getColorTextureId() : -1;
        CloudShadowMapAccess.publishSnapshot(new CloudShadowSnapshot(
                true,
                textureId,
                resolution,
                resolution,
                bounds.minX(),
                bounds.minZ(),
                bounds.maxX(),
                bounds.maxZ(),
                worldToShadow,
                frameContext.getWorldTime(),
                values
        ));
    }

    public static void clear() {
        CloudShadowMapAccess.clear();
    }

    private static float sampleCloudOcclusion(@NotNull List<CloudRenderSnapshot> snapshots, float worldX, float worldZ) {
        float accumulated = 0.0F;
        for (CloudRenderSnapshot snapshot : snapshots) {
            Vec3 center = snapshot.getRegionCenter();
            if (center == null) {
                continue;
            }

            float dx = worldX - (float) center.x();
            float dz = worldZ - (float) center.z();
            float radius = Math.max(1.0F, snapshot.getRegionRadius() * SHADOW_FADE_RADIUS_SCALE);
            float distanceNorm = Mth.sqrt(dx * dx + dz * dz) / radius;
            if (distanceNorm >= 1.0F) {
                continue;
            }

            float footprint = 1.0F - smoothstep(0.52F, 1.0F, distanceNorm);
            float density = Mth.clamp(snapshot.getDensity() * snapshot.getDensityMultiplier(), 0.0F, 1.0F);
            float coverage = Mth.clamp(snapshot.getCoverage() * snapshot.getCoverageMultiplier(), 0.0F, 1.0F);
            float stormBias = Math.max(snapshot.getStormVisualTier().getShadowBias(), snapshot.getStormCoreDarkening());
            float precipitationBias = snapshot.getPrecipitationTier().getRepresentativeIntensity() * 0.22F;
            float contribution = snapshot.getShadowContribution()
                    * footprint
                    * Mth.clamp(density * 0.45F + coverage * 0.35F + stormBias * 0.22F + precipitationBias, 0.0F, 1.0F);

            accumulated = 1.0F - ((1.0F - accumulated) * (1.0F - contribution));
            if (accumulated >= MAX_CELL_ADDITIVE_SHADOW) {
                return MAX_CELL_ADDITIVE_SHADOW;
            }
        }
        return Mth.clamp(accumulated, 0.0F, MAX_CELL_ADDITIVE_SHADOW);
    }

    private static ShadowBounds resolveBounds(
            @NotNull CloudRenderFrameContext frameContext,
            @NotNull List<CloudRenderSnapshot> snapshots
    ) {
        Vec3 camera = frameContext.getCameraPosition();
        float minX = (float) camera.x() - MIN_SHADOW_BOUNDS_RADIUS;
        float maxX = (float) camera.x() + MIN_SHADOW_BOUNDS_RADIUS;
        float minZ = (float) camera.z() - MIN_SHADOW_BOUNDS_RADIUS;
        float maxZ = (float) camera.z() + MIN_SHADOW_BOUNDS_RADIUS;

        for (CloudRenderSnapshot snapshot : snapshots) {
            Vec3 center = snapshot.getRegionCenter();
            if (center == null) {
                continue;
            }

            float radius = Math.max(1.0F, snapshot.getRegionRadius() * SHADOW_FADE_RADIUS_SCALE);
            minX = Math.min(minX, (float) center.x() - radius);
            maxX = Math.max(maxX, (float) center.x() + radius);
            minZ = Math.min(minZ, (float) center.z() - radius);
            maxZ = Math.max(maxZ, (float) center.z() + radius);
        }

        return new ShadowBounds(minX, minZ, maxX, maxZ);
    }

    private static boolean isEnabled() {
        try {
            return AtmoCommonConfig.ENABLE_CLOUD_SHADOW_MAP.get();
        } catch (IllegalStateException exception) {
            return true;
        }
    }

    private static boolean uploadShadowTexture(@NotNull RenderTarget shadowTarget, float[] values, int width, int height) {
        int textureId = shadowTarget.getColorTextureId();
        if (textureId < 0 || width <= 0 || height <= 0 || values == null || values.length < width * height) {
            return false;
        }
        if (shadowTarget.width < width || shadowTarget.height < height) {
            ProjectAtmosphere.LOGGER.warn(
                    "[CloudShadow] upload.skip reason=targetTooSmall target={}x{} upload={}x{} texture={}",
                    shadowTarget.width,
                    shadowTarget.height,
                    width,
                    height,
                    textureId
            );
            return false;
        }

        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
        for (int i = 0; i < width * height; i++) {
            int shadow = Mth.clamp(Math.round(values[i] * 255.0F), 0, 255);
            pixels.put((byte) shadow);
            pixels.put((byte) shadow);
            pixels.put((byte) shadow);
            pixels.put((byte) 255);
        }
        pixels.flip();

        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int previousUnpackAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        int previousUnpackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        try {
            // Other renderers may leave a PBO bound; CPU ByteBuffer uploads require unpack buffer 0.
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            GL11.glTexImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    GL11.GL_RGBA8,
                    width,
                    height,
                    0,
                    GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE,
                    pixels
            );
            return logGlError("shadow-upload", false);
        } catch (RuntimeException exception) {
            ProjectAtmosphere.LOGGER.warn(
                    "[CloudShadow] upload.failed texture={} size={}x{} message={}",
                    textureId,
                    width,
                    height,
                    exception.getMessage()
            );
            return false;
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, previousUnpackAlignment);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, previousUnpackBuffer);
        }
    }

    private static boolean logGlError(String context, boolean defaultValue) {
        int error = GL11.glGetError();
        if (error == GL11.GL_NO_ERROR) {
            return true;
        }
        ProjectAtmosphere.LOGGER.warn(
                "[CloudShadow] glError context={} code=0x{}",
                context,
                String.format(Locale.ROOT, "%04X", error)
        );
        return defaultValue;
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        if (edge0 == edge1) {
            return value < edge0 ? 0.0F : 1.0F;
        }
        float t = Mth.clamp((value - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private record ShadowBounds(float minX, float minZ, float maxX, float maxZ) {
        boolean isValid() {
            return maxX > minX && maxZ > minZ;
        }
    }
}
