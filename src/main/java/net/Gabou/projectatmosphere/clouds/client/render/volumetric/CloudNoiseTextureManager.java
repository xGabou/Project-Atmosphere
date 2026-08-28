package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

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
        int uploadedBase = -1;
        int uploadedDetail = -1;
        int uploadedBlue = -1;
        try {
            uploadedBase = upload3d(base, BASE_SIZE);
            uploadedDetail = upload3d(detail, DETAIL_SIZE);
            uploadedBlue = upload2d(blue, BLUE_SIZE);
            // Publish the set atomically. A partial upload must never be
            // mistaken for a complete sampler set on the following frame.
            baseTextureId = uploadedBase;
            detailTextureId = uploadedDetail;
            blueNoiseTextureId = uploadedBlue;
            // The base and detail volumes are NOT released after upload. They
            // are the authoritative source for the CPU mirror of the storm
            // density composition, which whiteout, camera density and rain
            // support all read; releasing them silently degraded that mirror
            // to constant median noise while the GPU sampled the real field,
            // so the two disagreed about where the storm was. Retaining them
            // costs about 8.1 MB, the same data the GPU already holds.
            // Blue noise is GPU-only dither and is still released.
            bluePixels = null;
            ProjectAtmosphere.LOGGER.info(
                    "[VolumetricClouds] noise textures ready base={} detail={} blue={}",
                    baseTextureId, detailTextureId, blueNoiseTextureId);
            return true;
        } catch (Throwable throwable) {
            deleteTexture(uploadedBase);
            deleteTexture(uploadedDetail);
            deleteTexture(uploadedBlue);
            baseTextureId = -1;
            detailTextureId = -1;
            blueNoiseTextureId = -1;
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

    /**
     * Baked base-volume pixels, or {@code null} while the bake is pending.
     * Exposed so the CPU visual-density mirror samples exactly the volume the
     * GPU samples rather than a second noise implementation. The array is
     * never mutated after publication; callers must not write to it.
     */
    static byte[] bakedBasePixels() {
        return basePixels;
    }

    /** Baked detail-volume pixels, or {@code null} while the bake is pending. */
    static byte[] bakedDetailPixels() {
        return detailPixels;
    }

    public static void shutdown() {
        deleteTexture(baseTextureId);
        deleteTexture(detailTextureId);
        deleteTexture(blueNoiseTextureId);
        baseTextureId = -1;
        detailTextureId = -1;
        blueNoiseTextureId = -1;
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
            byte[] base = CloudNoiseFieldModel.bakeBase();
            if (generation != bakeGeneration) {
                return;
            }
            basePixels = base;

            byte[] detail = CloudNoiseFieldModel.bakeDetail();
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

    /** Blue-noise ordering hash; the cloud noise fields live in CloudNoiseFieldModel. */
    private static int hashInt(int value) {
        int h = value * 747796405 + -1403630843;
        h = ((h >>> ((h >>> 28) + 4)) ^ h) * 277803737;
        return h ^ (h >>> 22);
    }

    // -----------------------------------------------------------------
    // GL upload
    // -----------------------------------------------------------------

    private static int upload3d(byte[] pixels, int size) {
        requirePixelLength(pixels, (long) size * size * size * 4L, "3D");
        int previousTexture = GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
        int textureId = GL11.glGenTextures();
        boolean uploaded = false;
        try {
            try (PixelUnpackState ignored = PixelUnpackState.beginTightCpuUpload()) {
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
                uploaded = true;
            }
        } finally {
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, previousTexture);
            if (!uploaded) {
                deleteTexture(textureId);
            }
        }
        return textureId;
    }

    private static int upload2d(byte[] pixels, int size) {
        requirePixelLength(pixels, (long) size * size * 4L, "2D");
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int textureId = GL11.glGenTextures();
        boolean uploaded = false;
        try {
            try (PixelUnpackState ignored = PixelUnpackState.beginTightCpuUpload()) {
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
                uploaded = true;
            }
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            if (!uploaded) {
                deleteTexture(textureId);
            }
        }
        return textureId;
    }

    private static void requirePixelLength(byte[] pixels, long expectedLength, String dimension) {
        if (pixels == null || expectedLength != pixels.length) {
            throw new IllegalArgumentException(
                    dimension + " noise upload expected " + expectedLength
                            + " RGBA bytes, got " + (pixels == null ? "null" : pixels.length));
        }
    }

    private static void deleteTexture(int textureId) {
        if (textureId > 0) {
            // Keep Mojang's tracked 2D binding cache coherent as well as
            // deleting the GL name. The same API safely deletes 3D names.
            GlStateManager._deleteTexture(textureId);
        }
    }

    /**
     * NativeImage uploads intentionally leave pixel-unpack parameters set for
     * their source sub-rectangle. A following glTexImage3D would apply those
     * row/skip values to our tightly packed buffer and may read beyond it.
     * A bound pixel-unpack buffer is even more dangerous because the direct
     * buffer address is then interpreted by OpenGL as a PBO byte offset.
     */
    private record PixelUnpackState(
            int buffer,
            int alignment,
            int rowLength,
            int imageHeight,
            int skipPixels,
            int skipRows,
            int skipImages
    ) implements AutoCloseable {
        private static PixelUnpackState beginTightCpuUpload() {
            PixelUnpackState state = new PixelUnpackState(
                    GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING),
                    GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT),
                    GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH),
                    GL11.glGetInteger(GL12.GL_UNPACK_IMAGE_HEIGHT),
                    GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS),
                    GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS),
                    GL11.glGetInteger(GL12.GL_UNPACK_SKIP_IMAGES)
            );
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, 0);
            return state;
        }

        @Override
        public void close() {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, alignment);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, rowLength);
            GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, imageHeight);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, skipPixels);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, skipRows);
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, skipImages);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, buffer);
        }
    }
}
