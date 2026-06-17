package net.Gabou.projectatmosphere.clouds.api;

import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.Arrays;

public final class CloudShadowSnapshot {
    public static final CloudShadowSnapshot EMPTY = new CloudShadowSnapshot(
            false,
            -1,
            0,
            0,
            0.0F,
            0.0F,
            0.0F,
            0.0F,
            new Matrix4f(),
            -1L,
            new float[0]
    );

    private final boolean valid;
    private final int textureId;
    private final int resolutionX;
    private final int resolutionZ;
    private final float minX;
    private final float minZ;
    private final float maxX;
    private final float maxZ;
    private final Matrix4f worldToShadowMatrix;
    private final long validityFrame;
    private final float[] shadowValues;

    public CloudShadowSnapshot(
            boolean valid,
            int textureId,
            int resolutionX,
            int resolutionZ,
            float minX,
            float minZ,
            float maxX,
            float maxZ,
            Matrix4f worldToShadowMatrix,
            long validityFrame,
            float[] shadowValues
    ) {
        this.valid = valid && resolutionX > 0 && resolutionZ > 0 && shadowValues != null && shadowValues.length >= resolutionX * resolutionZ;
        this.textureId = textureId;
        this.resolutionX = Math.max(0, resolutionX);
        this.resolutionZ = Math.max(0, resolutionZ);
        this.minX = Math.min(minX, maxX);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxZ = Math.max(minZ, maxZ);
        this.worldToShadowMatrix = worldToShadowMatrix == null ? new Matrix4f() : new Matrix4f(worldToShadowMatrix);
        this.validityFrame = validityFrame;
        this.shadowValues = shadowValues == null ? new float[0] : Arrays.copyOf(shadowValues, Math.max(0, this.resolutionX * this.resolutionZ));
    }

    public boolean isValid() {
        return valid;
    }

    public int getTextureId() {
        return textureId;
    }

    public int getResolutionX() {
        return resolutionX;
    }

    public int getResolutionZ() {
        return resolutionZ;
    }

    public float getMinX() {
        return minX;
    }

    public float getMinZ() {
        return minZ;
    }

    public float getMaxX() {
        return maxX;
    }

    public float getMaxZ() {
        return maxZ;
    }

    public Matrix4f getWorldToShadowMatrix() {
        return new Matrix4f(worldToShadowMatrix);
    }

    public long getValidityFrame() {
        return validityFrame;
    }

    public float[] copyShadowValues() {
        return Arrays.copyOf(shadowValues, shadowValues.length);
    }

    public float sampleShadowAt(double worldX, double worldZ) {
        if (!valid || resolutionX <= 0 || resolutionZ <= 0 || maxX <= minX || maxZ <= minZ) {
            return 0.0F;
        }

        float u = (float) ((worldX - minX) / (maxX - minX));
        float v = (float) ((worldZ - minZ) / (maxZ - minZ));
        if (u < 0.0F || u > 1.0F || v < 0.0F || v > 1.0F) {
            return 0.0F;
        }

        float gridX = u * (resolutionX - 1);
        float gridZ = v * (resolutionZ - 1);
        int x0 = Mth.clamp((int) Math.floor(gridX), 0, resolutionX - 1);
        int z0 = Mth.clamp((int) Math.floor(gridZ), 0, resolutionZ - 1);
        int x1 = Mth.clamp(x0 + 1, 0, resolutionX - 1);
        int z1 = Mth.clamp(z0 + 1, 0, resolutionZ - 1);
        float tx = gridX - x0;
        float tz = gridZ - z0;

        float a = sampleCell(x0, z0);
        float b = sampleCell(x1, z0);
        float c = sampleCell(x0, z1);
        float d = sampleCell(x1, z1);
        float ab = Mth.lerp(tx, a, b);
        float cd = Mth.lerp(tx, c, d);
        return Mth.clamp(Mth.lerp(tz, ab, cd), 0.0F, 1.0F);
    }

    private float sampleCell(int x, int z) {
        int index = z * resolutionX + x;
        if (index < 0 || index >= shadowValues.length) {
            return 0.0F;
        }
        return shadowValues[index];
    }
}
