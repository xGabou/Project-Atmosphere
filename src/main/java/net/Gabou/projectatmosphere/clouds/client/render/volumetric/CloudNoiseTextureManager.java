package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import com.mojang.blaze3d.systems.RenderSystem;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.stream.IntStream;

/**
 * Bakes and owns the tiling 3D noise textures used by the volumetric cloud
 * renderer, plus a small blue-noise dither texture. Pixel generation runs on a
 * worker thread once per session; GL upload happens lazily on the render
 * thread when the data is ready.
 *
 * Texture layout follows the standard Nubis/Horizon packing:
 * base 128^3 RGBA8: R = Perlin-Worley, GBA = Worley FBM octaves.
 * detail 32^3 RGBA8: RGB = high-frequency Worley FBM octaves.
 */
public final class CloudNoiseTextureManager {
    private static final int BASE_SIZE = 128;
    private static final int DETAIL_SIZE = 32;
    private static final int BLUE_SIZE = 64;

    private static volatile boolean bakeStarted;
    private static volatile boolean bakeFailed;
    private static volatile long bakeGeneration;
    private static volatile byte[] basePixels;
    private static volatile byte[] detailPixels;
    private static volatile byte[] bluePixels;

    private static int baseTextureId = -1;
    private static int detailTextureId = -1;
    private static int blueNoiseTextureId = -1;

    private CloudNoiseTextureManager() {
    }

    /**
     * Ensures noise textures exist on the GPU. Starts the asynchronous bake on
     * first call and uploads once pixel data is available.
     *
     * @return true when all textures are ready for sampling
     */
    public static boolean ensureReady() {
        if (!RenderSystem.isOnRenderThread() || bakeFailed) {
            return false;
        }
        if (baseTextureId > 0 && detailTextureId > 0 && blueNoiseTextureId > 0) {
            return true;
        }
        if (!bakeStarted) {
            bakeStarted = true;
            long generation = ++bakeGeneration;
            Thread worker = new Thread(() -> bakeAll(generation), "PA-CloudNoiseBake");
            worker.setDaemon(true);
            worker.start();
            return false;
        }
        byte[] base = basePixels;
        byte[] detail = detailPixels;
        byte[] blue = bluePixels;
        if (base == null || detail == null || blue == null) {
            return false;
        }
        try {
            baseTextureId = upload3d(base, BASE_SIZE);
            detailTextureId = upload3d(detail, DETAIL_SIZE);
            blueNoiseTextureId = upload2d(blue, BLUE_SIZE);
            basePixels = null;
            detailPixels = null;
            bluePixels = null;
            ProjectAtmosphere.LOGGER.info(
                    "[VolumetricClouds] noise textures ready base={} detail={} blue={}",
                    baseTextureId, detailTextureId, blueNoiseTextureId);
            return true;
        } catch (Throwable throwable) {
            bakeFailed = true;
            ProjectAtmosphere.LOGGER.error("[VolumetricClouds] noise texture upload failed", throwable);
            return false;
        }
    }

    public static int baseTextureId() {
        return baseTextureId;
    }

    public static int detailTextureId() {
        return detailTextureId;
    }

    public static int blueNoiseTextureId() {
        return blueNoiseTextureId;
    }

    public static int blueNoiseSize() {
        return BLUE_SIZE;
    }

    public static void shutdown() {
        if (baseTextureId > 0) {
            GL11.glDeleteTextures(baseTextureId);
            baseTextureId = -1;
        }
        if (detailTextureId > 0) {
            GL11.glDeleteTextures(detailTextureId);
            detailTextureId = -1;
        }
        if (blueNoiseTextureId > 0) {
            GL11.glDeleteTextures(blueNoiseTextureId);
            blueNoiseTextureId = -1;
        }
        // Drop any stale CPU payload too. A resource reload after a completed
        // upload otherwise leaves bakeStarted true with no data to upload.
        bakeGeneration++;
        bakeStarted = false;
        bakeFailed = false;
        basePixels = null;
        detailPixels = null;
        bluePixels = null;
    }

    // -----------------------------------------------------------------
    // CPU bake
    // -----------------------------------------------------------------

    private static void bakeAll(long generation) {
        try {
            long startedAt = System.nanoTime();
            byte[] base = new byte[BASE_SIZE * BASE_SIZE * BASE_SIZE * 4];
            IntStream.range(0, BASE_SIZE).parallel().forEach(z -> bakeBaseSlice(base, z));
            if (generation != bakeGeneration) {
                return;
            }
            basePixels = base;

            byte[] detail = new byte[DETAIL_SIZE * DETAIL_SIZE * DETAIL_SIZE * 4];
            IntStream.range(0, DETAIL_SIZE).parallel().forEach(z -> bakeDetailSlice(detail, z));
            if (generation != bakeGeneration) {
                return;
            }
            detailPixels = detail;

            byte[] blue = bakeBlueNoise(BLUE_SIZE);
            if (generation != bakeGeneration) {
                return;
            }
            bluePixels = blue;
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
            ProjectAtmosphere.LOGGER.info("[VolumetricClouds] noise bake finished in {} ms", elapsedMs);
        } catch (Throwable throwable) {
            if (generation != bakeGeneration) {
                return;
            }
            bakeFailed = true;
            ProjectAtmosphere.LOGGER.error("[VolumetricClouds] noise bake failed", throwable);
        }
    }

    private static void bakeBaseSlice(byte[] out, int z) {
        float inv = 1.0F / BASE_SIZE;
        for (int y = 0; y < BASE_SIZE; y++) {
            for (int x = 0; x < BASE_SIZE; x++) {
                float u = x * inv;
                float v = y * inv;
                float w = z * inv;

                float perlin = perlinFbm(u, v, w, 4, 5, 1001);
                float worleyLow = worleyFbm(u, v, w, 8, 2101);
                float perlinWorley = remap01(perlin, worleyLow - 1.0F, 1.0F);

                float g = worleyFbm(u, v, w, 8, 3301);
                float b = worleyFbm(u, v, w, 16, 4409);
                float a = worleyFbm(u, v, w, 32, 5501);

                int index = ((z * BASE_SIZE + y) * BASE_SIZE + x) * 4;
                out[index] = toByte(perlinWorley);
                out[index + 1] = toByte(g);
                out[index + 2] = toByte(b);
                out[index + 3] = toByte(a);
            }
        }
    }

    private static void bakeDetailSlice(byte[] out, int z) {
        float inv = 1.0F / DETAIL_SIZE;
        for (int y = 0; y < DETAIL_SIZE; y++) {
            for (int x = 0; x < DETAIL_SIZE; x++) {
                float u = x * inv;
                float v = y * inv;
                float w = z * inv;
                float r = worleyFbm(u, v, w, 2, 6101);
                float g = worleyFbm(u, v, w, 4, 7207);
                float b = worleyFbm(u, v, w, 8, 8317);
                int index = ((z * DETAIL_SIZE + y) * DETAIL_SIZE + x) * 4;
                out[index] = toByte(r);
                out[index + 1] = toByte(g);
                out[index + 2] = toByte(b);
                out[index + 3] = (byte) 255;
            }
        }
    }

    /**
     * Void-and-cluster blue noise ranking. Produces an ordered dither texture
     * whose energy spectrum has no low-frequency clumps, which keeps raymarch
     * jitter clean under temporal filtering.
     */
    private static byte[] bakeBlueNoise(int size) {
        int count = size * size;
        float[] energy = new float[count];
        boolean[] placed = new boolean[count];
        int[] rank = new int[count];
        float sigma = 1.9F;
        int radius = 6;
        float[] kernel = new float[(radius * 2 + 1) * (radius * 2 + 1)];
        int kernelIndex = 0;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                kernel[kernelIndex++] = (float) Math.exp(-(dx * dx + dy * dy) / (2.0F * sigma * sigma));
            }
        }

        // Seed with the minimum-energy (empty) point each iteration.
        int seedIndex = Integer.remainderUnsigned(hashInt(12345), count);
        for (int placedCount = 0; placedCount < count; placedCount++) {
            int best = -1;
            float bestEnergy = Float.MAX_VALUE;
            for (int i = 0; i < count; i++) {
                if (!placed[i] && energy[i] < bestEnergy) {
                    bestEnergy = energy[i];
                    best = i;
                }
            }
            if (placedCount == 0) {
                best = seedIndex;
            }
            placed[best] = true;
            rank[best] = placedCount;
            int bx = best % size;
            int by = best / size;
            kernelIndex = 0;
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    int nx = Math.floorMod(bx + dx, size);
                    int ny = Math.floorMod(by + dy, size);
                    energy[ny * size + nx] += kernel[kernelIndex++];
                }
            }
        }

        byte[] out = new byte[count * 4];
        for (int i = 0; i < count; i++) {
            int value = Math.round(rank[i] * 255.0F / (count - 1));
            out[i * 4] = (byte) value;
            out[i * 4 + 1] = (byte) value;
            out[i * 4 + 2] = (byte) value;
            out[i * 4 + 3] = (byte) 255;
        }
        return out;
    }

    // -----------------------------------------------------------------
    // Tiling noise primitives
    // -----------------------------------------------------------------

    private static float perlinFbm(float x, float y, float z, int basePeriod, int octaves, int seed) {
        float amplitude = 0.5F;
        float sum = 0.0F;
        float norm = 0.0F;
        int period = basePeriod;
        for (int i = 0; i < octaves; i++) {
            sum += perlin(x * period, y * period, z * period, period, seed + i * 131) * amplitude;
            norm += amplitude;
            amplitude *= 0.5F;
            period *= 2;
        }
        return clamp01(sum / norm * 0.5F + 0.5F);
    }

    private static float worleyFbm(float x, float y, float z, int basePeriod, int seed) {
        float w0 = worley(x * basePeriod, y * basePeriod, z * basePeriod, basePeriod, seed);
        float w1 = worley(x * basePeriod * 2, y * basePeriod * 2, z * basePeriod * 2, basePeriod * 2, seed + 17);
        float w2 = worley(x * basePeriod * 4, y * basePeriod * 4, z * basePeriod * 4, basePeriod * 4, seed + 41);
        return clamp01(w0 * 0.625F + w1 * 0.25F + w2 * 0.125F);
    }

    /** Inverted F1 Worley: 1 at feature points, 0 far away. */
    private static float worley(float x, float y, float z, int period, int seed) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        int zi = (int) Math.floor(z);
        float minDistSq = Float.MAX_VALUE;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int cx = xi + dx;
                    int cy = yi + dy;
                    int cz = zi + dz;
                    int hx = Math.floorMod(cx, period);
                    int hy = Math.floorMod(cy, period);
                    int hz = Math.floorMod(cz, period);
                    int h = hash3(hx, hy, hz, seed);
                    float fx = cx + unitFloat(h);
                    float fy = cy + unitFloat(hashInt(h + 0x9E3779B9));
                    float fz = cz + unitFloat(hashInt(h + 0x85EBCA6B));
                    float ddx = fx - x;
                    float ddy = fy - y;
                    float ddz = fz - z;
                    float distSq = ddx * ddx + ddy * ddy + ddz * ddz;
                    if (distSq < minDistSq) {
                        minDistSq = distSq;
                    }
                }
            }
        }
        return clamp01(1.0F - (float) Math.sqrt(minDistSq));
    }

    private static float perlin(float x, float y, float z, int period, int seed) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        int zi = (int) Math.floor(z);
        float xf = x - xi;
        float yf = y - yi;
        float zf = z - zi;
        float u = fade(xf);
        float v = fade(yf);
        float w = fade(zf);

        float n000 = gradDot(xi, yi, zi, xf, yf, zf, period, seed);
        float n100 = gradDot(xi + 1, yi, zi, xf - 1, yf, zf, period, seed);
        float n010 = gradDot(xi, yi + 1, zi, xf, yf - 1, zf, period, seed);
        float n110 = gradDot(xi + 1, yi + 1, zi, xf - 1, yf - 1, zf, period, seed);
        float n001 = gradDot(xi, yi, zi + 1, xf, yf, zf - 1, period, seed);
        float n101 = gradDot(xi + 1, yi, zi + 1, xf - 1, yf, zf - 1, period, seed);
        float n011 = gradDot(xi, yi + 1, zi + 1, xf, yf - 1, zf - 1, period, seed);
        float n111 = gradDot(xi + 1, yi + 1, zi + 1, xf - 1, yf - 1, zf - 1, period, seed);

        float x00 = lerp(n000, n100, u);
        float x10 = lerp(n010, n110, u);
        float x01 = lerp(n001, n101, u);
        float x11 = lerp(n011, n111, u);
        float y0 = lerp(x00, x10, v);
        float y1 = lerp(x01, x11, v);
        return lerp(y0, y1, w);
    }

    private static float gradDot(int xi, int yi, int zi, float xf, float yf, float zf, int period, int seed) {
        int h = hash3(Math.floorMod(xi, period), Math.floorMod(yi, period), Math.floorMod(zi, period), seed) & 15;
        float gu = h < 8 ? xf : yf;
        float gv = h < 4 ? yf : (h == 12 || h == 14 ? xf : zf);
        return ((h & 1) == 0 ? gu : -gu) + ((h & 2) == 0 ? gv : -gv);
    }

    private static int hash3(int x, int y, int z, int seed) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177 + seed * 144665;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    private static int hashInt(int value) {
        int h = value * 747796405 + -1403630843;
        h = ((h >>> ((h >>> 28) + 4)) ^ h) * 277803737;
        return h ^ (h >>> 22);
    }

    private static float unitFloat(int hash) {
        return (hash >>> 8) * (1.0F / 16777216.0F);
    }

    private static float fade(float t) {
        return t * t * t * (t * (t * 6.0F - 15.0F) + 10.0F);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float remap01(float value, float low, float high) {
        return clamp01((value - low) / Math.max(high - low, 0.0001F));
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static byte toByte(float value01) {
        return (byte) Math.round(clamp01(value01) * 255.0F);
    }

    // -----------------------------------------------------------------
    // GL upload
    // -----------------------------------------------------------------

    private static int upload3d(byte[] pixels, int size) {
        int textureId = GL11.glGenTextures();
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, textureId);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL11.GL_REPEAT);
        ByteBuffer buffer = MemoryUtil.memAlloc(pixels.length);
        try {
            buffer.put(pixels).flip();
            GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL11.GL_RGBA8, size, size, size, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        } finally {
            MemoryUtil.memFree(buffer);
        }
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
        return textureId;
    }

    private static int upload2d(byte[] pixels, int size) {
        int textureId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        ByteBuffer buffer = MemoryUtil.memAlloc(pixels.length);
        try {
            buffer.put(pixels).flip();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, size, size, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        } finally {
            MemoryUtil.memFree(buffer);
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return textureId;
    }
}
