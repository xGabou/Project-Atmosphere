package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import java.util.stream.IntStream;

/**
 * GL-free tiling noise field shared by the baked GPU textures and every CPU
 * mirror of the shader density path.
 *
 * <p>{@link CloudNoiseTextureManager} owns baking scheduling and GL upload;
 * this class owns the pixel values themselves and the trilinear/repeat
 * sampling rule that mirrors {@code texture(BaseNoiseSampler, ...)} and
 * {@code texture(DetailNoiseSampler, ...)}. Keeping the math here means the
 * Java visual-density path and the deterministic sandboxes measure exactly
 * the noise the GPU samples, with no second implementation to drift.
 *
 * <p>Texture layout follows the standard Nubis/Horizon packing:
 * base 128^3 RGBA8: R = Perlin-Worley, GBA = Worley FBM octaves.
 * detail 32^3 RGBA8: RGB = high-frequency Worley FBM octaves.
 */
public final class CloudNoiseFieldModel {
    public static final int BASE_SIZE = 128;
    public static final int DETAIL_SIZE = 32;

    /**
     * Detail FBM channel weights used by the shader. Their squares set each
     * octave's nominal share of the detail field variance, which is how the
     * per-band morphology thresholds are derived.
     */
    public static final double DETAIL_WEIGHT_R = 0.625D;
    public static final double DETAIL_WEIGHT_G = 0.25D;
    public static final double DETAIL_WEIGHT_B = 0.125D;

    /**
     * Base Worley FBM octave periods over the unit texture domain, and the
     * detail Worley FBM octave periods. A period of {@code n} means the octave
     * repeats {@code n} times across one texture tile, so its wavelength in
     * blocks is {@code 1 / (scale * n)} for the domain scale in use.
     */
    public static final int[] BASE_FBM_PERIODS = {8, 16, 32};
    public static final int[] DETAIL_FBM_PERIODS = {2, 4, 8};

    private CloudNoiseFieldModel() {
    }

    // -----------------------------------------------------------------
    // Baking
    // -----------------------------------------------------------------

    /** Bakes the full 128^3 RGBA8 base volume. Pure CPU; safe off-thread. */
    public static byte[] bakeBase() {
        byte[] base = new byte[BASE_SIZE * BASE_SIZE * BASE_SIZE * 4];
        IntStream.range(0, BASE_SIZE).parallel().forEach(z -> bakeBaseSlice(base, z));
        return base;
    }

    /** Bakes the full 32^3 RGBA8 detail volume. Pure CPU; safe off-thread. */
    public static byte[] bakeDetail() {
        byte[] detail = new byte[DETAIL_SIZE * DETAIL_SIZE * DETAIL_SIZE * 4];
        IntStream.range(0, DETAIL_SIZE).parallel().forEach(z -> bakeDetailSlice(detail, z));
        return detail;
    }

    static void bakeBaseSlice(byte[] out, int z) {
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

    static void bakeDetailSlice(byte[] out, int z) {
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

    // -----------------------------------------------------------------
    // CPU sampling mirror of GL_LINEAR + GL_REPEAT
    // -----------------------------------------------------------------

    /**
     * Samples the baked base volume the way the shader does: normalized
     * domain coordinates, wrapped, trilinear-filtered, 8-bit quantized.
     * {@code out} receives R, G, B, A in 0..1.
     */
    public static void sampleBase(byte[] pixels, double x, double y, double z, double[] out) {
        sampleVolume(pixels, BASE_SIZE, x, y, z, out);
    }

    /** Samples the baked detail volume. {@code out} receives R, G, B, A in 0..1. */
    public static void sampleDetail(byte[] pixels, double x, double y, double z, double[] out) {
        sampleVolume(pixels, DETAIL_SIZE, x, y, z, out);
    }

    private static void sampleVolume(
            byte[] pixels,
            int size,
            double x,
            double y,
            double z,
            double[] out
    ) {
        double tx = wrapTexel(x, size);
        double ty = wrapTexel(y, size);
        double tz = wrapTexel(z, size);
        int x0 = (int) Math.floor(tx);
        int y0 = (int) Math.floor(ty);
        int z0 = (int) Math.floor(tz);
        double fx = tx - x0;
        double fy = ty - y0;
        double fz = tz - z0;
        int x1 = Math.floorMod(x0 + 1, size);
        int y1 = Math.floorMod(y0 + 1, size);
        int z1 = Math.floorMod(z0 + 1, size);
        x0 = Math.floorMod(x0, size);
        y0 = Math.floorMod(y0, size);
        z0 = Math.floorMod(z0, size);
        for (int channel = 0; channel < 4; channel++) {
            double c000 = texel(pixels, size, x0, y0, z0, channel);
            double c100 = texel(pixels, size, x1, y0, z0, channel);
            double c010 = texel(pixels, size, x0, y1, z0, channel);
            double c110 = texel(pixels, size, x1, y1, z0, channel);
            double c001 = texel(pixels, size, x0, y0, z1, channel);
            double c101 = texel(pixels, size, x1, y0, z1, channel);
            double c011 = texel(pixels, size, x0, y1, z1, channel);
            double c111 = texel(pixels, size, x1, y1, z1, channel);
            double x00 = c000 + (c100 - c000) * fx;
            double x10 = c010 + (c110 - c010) * fx;
            double x01 = c001 + (c101 - c001) * fx;
            double x11 = c011 + (c111 - c011) * fx;
            double y0v = x00 + (x10 - x00) * fy;
            double y1v = x01 + (x11 - x01) * fy;
            out[channel] = y0v + (y1v - y0v) * fz;
        }
    }

    /**
     * GL samples at texel centres, so normalized coordinate {@code c} maps to
     * texel coordinate {@code c * size - 0.5}.
     */
    private static double wrapTexel(double normalized, int size) {
        return normalized * size - 0.5D;
    }

    private static double texel(byte[] pixels, int size, int x, int y, int z, int channel) {
        int index = (((z * size + y) * size + x) * 4) + channel;
        return (pixels[index] & 0xFF) / 255.0D;
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
}
