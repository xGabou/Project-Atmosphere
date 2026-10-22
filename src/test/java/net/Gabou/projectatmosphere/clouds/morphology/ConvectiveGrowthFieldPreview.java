package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.type.CloudFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudShapeProfile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * T257 CPU iteration preview: orthographic first-surface views with gradient shading.
 * Diagnostic only - NOT native evidence. Args: output folder [types...].
 */
public final class ConvectiveGrowthFieldPreview {
    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "build/t257-preview");
        out.mkdirs();
        String[] types = {"cumulonimbus_calvus", "cumulonimbus_capillatus", "supercell"};
        long[] seeds = "1".equals(System.getenv("T257_PREVIEW_ONE_SEED")) ? new long[]{20261008L} : new long[]{20261008L, 20268927L, 20276846L};
        int cw = 420, ch = 300;
        // Native-scale flat silhouettes (the capture's side/portrait cameras, 1200x675 target, FOV 110):
        // A side | B side | A portrait | B portrait | B shaded ortho side (structure aid only).
        int nw = 520, nh = 300;
        BufferedImage sheet = new BufferedImage(nw * 5, nh * types.length * seeds.length, BufferedImage.TYPE_INT_RGB);
        int row = 0;
        for (String type : types) for (long seed : seeds) {
            var shape = CloudShapeProfile.defaultFor(type, CloudFamily.CUMULONIMBUS, null);
            StormGeometryProgram a = ConvectiveGrowthFieldA.compile(type, shape, seed);
            StormGeometryProgram b = ConvectiveGrowthFieldPrototype.compile(type, shape, seed);
            nativeSim(a, 2200, sheet, 0, row * nh, nw, nh);
            nativeSim(b, 2200, sheet, nw, row * nh, nw, nh);
            nativeSim(a, 1210, sheet, 2 * nw, row * nh, nw, nh);
            nativeSim(b, 1210, sheet, 3 * nw, row * nh, nw, nh);
            render(b, "side", sheet, 4 * nw, row * nh, nw, nh);
            row++;
        }
        probe();
        ImageIO.write(sheet, "png", new File(out, "t257-cpu-sheet.png"));
        System.out.println("T257_PREVIEW " + new File(out, "t257-cpu-sheet.png").getAbsolutePath());
    }

    /**
     * Flat silhouette exactly as the native side poses frame it: camera at local (650, 390, zCam) aiming
     * at (650, 390, 0), vertical FOV 110 on a 675-pixel target. Crop of w x h target pixels around the aim.
     */
    static void nativeSim(StormGeometryProgram p, float zCam, BufferedImage img, int ox, int oy, int w, int h) {
        float f = 337.5f / (float) Math.tan(Math.toRadians(55));
        float cx = 650, cy = 390;
        var b = p.bounds();
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) {
            float sx = (i + .5f - w / 2f) / f, sy = (h / 2f - j - .5f) / f;
            float dx = sx, dy = sy, dz = -1, len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            float[] d = {dx / len, dy / len, dz / len};
            float[] t = slab3(cx, cy, zCam, d, b, 12000);
            float alpha = 0;
            if (t != null) for (float tt = t[0]; tt < t[1]; tt += 4) {
                float dens = p.density(cx + d[0] * tt, cy + d[1] * tt, zCam + d[2] * tt, StormMorphologyPrototype.Scale.MESO);
                if (dens > 0) { alpha = 1 - (1 - alpha) * (float) Math.exp(-dens * 4 * .06f); if (alpha > .99f) break; }
            }
            int sky = (cy + d[1] * 2000 > 0) ? 0x6caefc : 0xaecbfc;
            int r0 = (sky >> 16) & 255, g0 = (sky >> 8) & 255, b0 = sky & 255;
            int cr = 0x9e, cg = 0xa8, cb = 0xbd;
            img.setRGB(ox + i, oy + j, ((int) (r0 + (cr - r0) * alpha) << 16) | ((int) (g0 + (cg - g0) * alpha) << 8) | (int) (b0 + (cb - b0) * alpha));
        }
    }

    static float[] slab3(float px, float py, float pz, float[] d, StormMorphologyPrototype.Bounds b, float depth) {
        return slab(px, py, pz, d, b, depth);
    }

    /** Canopy vertical occupancy downwind of the crown centre (diagnostic print). */
    static void probe() {
        for (String type : new String[]{"cumulonimbus_capillatus", "supercell"}) {
            var p = ConvectiveGrowthFieldPrototype.compile(type, CloudShapeProfile.defaultFor(type, CloudFamily.CUMULONIMBUS, null), 20261008L);
            float[] f = p.packed();
            float H = f[0], rx = f[60], ty = f[61], rz = f[62];
            for (float fx : new float[]{.3f, .6f, .9f, 1.1f, 1.4f}) {
                int lo = 999, hi = -1;
                for (int i = 0; i <= 120; i++) {
                    float y = ty - 1.0f * H + i * .01f * H;
                    if (p.density(rx + fx * H, y, rz, null) > .5f) { lo = Math.min(lo, i); hi = Math.max(hi, i); }
                }
                System.out.printf("T257_PROBE %s dx=%.1fH occupied %.2fH..%.2fH (rel canopy top)%n", type, fx, lo < 999 ? -1 + lo * .01f : 0, hi >= 0 ? -1 + hi * .01f : 0);
            }
        }
    }

    /** Common world framing for every type: 4400 x 3667 blocks centred near the storm. */
    static void render(StormGeometryProgram p, String view, BufferedImage img, int ox, int oy, int w, int h) {
        float span = 3400f, cx = 900, cy = 750, cz = 0;
        float[] right, up, fwd;
        switch (view) {
            case "front" -> { right = new float[]{0, 0, -1}; up = new float[]{0, 1, 0}; fwd = new float[]{1, 0, 0}; }
            case "above" -> { right = new float[]{1, 0, 0}; up = new float[]{0, 0, -1}; fwd = new float[]{0, -1, 0}; }
            case "below" -> { right = new float[]{1, 0, 0}; up = new float[]{0, 0, 1}; fwd = new float[]{0, 1, 0}; }
            case "oblique" -> { float a = .7071f; right = new float[]{a, 0, a}; up = new float[]{-.35f, .87f, .35f}; fwd = cross(up, right); }
            default -> { right = new float[]{1, 0, 0}; up = new float[]{0, 1, 0}; fwd = new float[]{0, 0, -1}; }
        }
        if (!view.equals("side")) cx = 400;
        if (view.equals("above") || view.equals("below")) cx = 800;
        var b = p.bounds();
        float depth = 6000, step = 4;
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) {
            float sx = (i + .5f - w / 2f) / w * span, sy = (h / 2f - j - .5f) / w * span;
            float px = cx + right[0] * sx + up[0] * sy - fwd[0] * depth / 2;
            float py = cy + right[1] * sx + up[1] * sy - fwd[1] * depth / 2;
            float pz = cz + right[2] * sx + up[2] * sy - fwd[2] * depth / 2;
            int colour = view.equals("below") || view.equals("above") ? 0x6caefc : (sy + cy > 0 ? 0x6caefc : 0xaecbfc);
            float t = 0, alpha = 0;
            float[] hit = null;
            // Coarse empty skip: AABB entry.
            float[] tt = slab(px, py, pz, fwd, b, depth);
            if (tt != null) {
                for (t = tt[0]; t < tt[1]; t += step) {
                    float x = px + fwd[0] * t, y = py + fwd[1] * t, z = pz + fwd[2] * t;
                    float d = p.density(x, y, z, StormMorphologyPrototype.Scale.MESO);
                    if (d > 0) {
                        alpha = 1 - (1 - alpha) * (float) Math.exp(-d * step * .06f);
                        if (hit == null && d > .5f) hit = new float[]{x, y, z};
                        if (alpha > .98f) break;
                    }
                }
            }
            if (alpha > .01f) {
                float shade = .62f;
                if (hit != null) {
                    float e = 6;
                    float nx = p.density(hit[0] - e, hit[1], hit[2], null) - p.density(hit[0] + e, hit[1], hit[2], null);
                    float ny = p.density(hit[0], hit[1] - e, hit[2], null) - p.density(hit[0], hit[1] + e, hit[2], null);
                    float nz = p.density(hit[0], hit[1], hit[2] - e, null) - p.density(hit[0], hit[1], hit[2] + e, null);
                    float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-6f;
                    float l = (nx * .45f + ny * .75f + nz * .48f) / len;
                    shade = .35f + .6f * Math.max(0, l);
                }
                int g = (int) (255 * shade);
                int bg = colour;
                int r0 = (bg >> 16) & 255, g0 = (bg >> 8) & 255, b0 = bg & 255;
                int r1 = (int) (r0 + (g - r0) * alpha), g1 = (int) (g0 + (g - g0) * alpha), b1 = (int) (b0 + (Math.min(255, g + 12) - b0) * alpha);
                colour = (r1 << 16) | (g1 << 8) | b1;
            }
            img.setRGB(ox + i, oy + j, colour);
        }
    }

    static float[] slab(float px, float py, float pz, float[] d, StormMorphologyPrototype.Bounds b, float depth) {
        float a = 0, c = depth;
        float[] o = {px, py, pz}, lo = {b.minX(), b.minY(), b.minZ()}, hi = {b.maxX(), b.maxY(), b.maxZ()};
        for (int k = 0; k < 3; k++) {
            if (Math.abs(d[k]) < 1e-6) { if (o[k] < lo[k] || o[k] > hi[k]) return null; continue; }
            float t0 = (lo[k] - o[k]) / d[k], t1 = (hi[k] - o[k]) / d[k];
            a = Math.max(a, Math.min(t0, t1)); c = Math.min(c, Math.max(t0, t1));
        }
        return c > a ? new float[]{a, c} : null;
    }

    static float[] cross(float[] a, float[] b) {
        float[] c = {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
        float l = (float) Math.sqrt(c[0] * c[0] + c[1] * c[1] + c[2] * c[2]);
        return new float[]{-c[0] / l, -c[1] / l, -c[2] / l};
    }
}
