package net.Gabou.projectatmosphere.clouds.morphology;

import java.util.stream.IntStream;

/**
 * T260.5 controlled neutral lighting, baked (experimental fixture only, never production). For a frozen sparse-brick
 * volume, single-scattering inputs on a regular lattice in fixture-local coordinates:
 * <ul>
 * <li>sun optical depth: a white sun at 35 deg elevation (from +z rotated 45 deg toward -x), marched through the
 *     volume's own density times the march's extinction scale (first step 2 blocks growing x1.4 to 40, midpoints,
 *     stops past od 8 or at the last skip-box exit);</li>
 * <li>ambient radiance: the isotropic-phase mean, over six equal-solid-angle directions (elevation +-30 deg, azimuths
 *     60 deg apart, alternating hemispheres), of sky radiance 0.15 above / ground radiance 0.145 below times the
 *     marched transmittance over 380 blocks (6 .. 192).</li>
 * </ul>
 * The shader's reference mode (PaDiagnosticLightingMode 25) runs exactly these marches per sample, so baked versus
 * reference images differ only by the lattice interpolation and the 12-bit quantisation. Reads nothing but the volume.
 * <p>Texture layout, appended after the volume's rows (2048 texels wide): one float per lattice point, packed as
 * {@code q(od / 8) * 4096 + q(ambient / 0.16)} (12 bits each), four consecutive x points per RGBA texel; the last row
 * holds the header: texel 0 = (minX, minY, minZ, spacing), texel 1 = (nx, ny, nz, first light texel).
 */
public final class NeutralLightVolume {
    public static final float SPACING = 10f;
    public static final float[] SUN = {-0.579228f, 0.573576f, 0.579228f};
    public static final float SKY_L = .15f, GROUND_L = .145f, OD_MAX = 8f, AMBIENT_MAX = .16f;
    private static final int W = SparseBrickVolume.TEX_W;

    private final SparseBrickVolume volume;
    private final float extinction, minX, minY, minZ, maxX, maxY, maxZ;
    private final int nx, ny, nz, nxp;
    private final float[] points;
    private final float[][] boxes;
    private final int computed;
    private final double bakeMs;

    public NeutralLightVolume(SparseBrickVolume volume, float extinction) {
        long t0 = System.nanoTime();
        this.volume = volume;
        this.extinction = extinction;
        var b = volume.bounds();
        minX = b.minX(); minY = b.minY(); minZ = b.minZ(); maxX = b.maxX(); maxY = b.maxY(); maxZ = b.maxZ();
        nx = (int) Math.ceil((maxX - minX) / SPACING) + 1;
        ny = (int) Math.ceil((maxY - minY) / SPACING) + 1;
        nz = (int) Math.ceil((maxZ - minZ) / SPACING) + 1;
        nxp = (nx + 3) / 4;
        boxes = new float[volume.boxCount()][];
        for (int i = 0; i < boxes.length; i++) boxes[i] = volume.box(i);
        points = new float[nxp * 4 * ny * nz];
        int[] grid = volume.grid();
        float cell = SparseBrickVolume.B * volume.voxel();
        int[] count = new int[nz];
        IntStream.range(0, nz).parallel().forEach(k -> {
            for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
                float x = minX + i * SPACING, y = minY + j * SPACING, z = minZ + k * SPACING;
                // Only points whose coarse cell or a neighbour holds material can be read by a material sample.
                int ci = (int) Math.floor((x - minX) / cell), cj = (int) Math.floor((y - minY) / cell), ck = (int) Math.floor((z - minZ) / cell);
                boolean near = false;
                for (int dk = -1; dk <= 1 && !near; dk++) for (int dj = -1; dj <= 1 && !near; dj++) for (int di = -1; di <= 1 && !near; di++) {
                    int a = ci + di, c = cj + dj, e = ck + dk;
                    if (a >= 0 && c >= 0 && e >= 0 && a < grid[0] && c < grid[1] && e < grid[2] && volume.cellState(a, c, e) != SparseBrickVolume.EMPTY) near = true;
                }
                if (!near) continue;
                float od = opticalDepth(x, y, z, SUN[0], SUN[1], SUN[2], 2f, 1.4f, 40f, 72, 8f);
                float ambient = 0;
                for (int n = 0; n < 6; n++) {
                    double az = Math.toRadians(37.0 + 60.0 * n), el = Math.toRadians((n & 1) == 0 ? 30.0 : -30.0);
                    float dx = (float) (Math.cos(el) * Math.sin(az)), dy = (float) Math.sin(el), dz = (float) (Math.cos(el) * Math.cos(az));
                    ambient += (dy > 0 ? SKY_L : GROUND_L) * (float) Math.exp(-opticalDepth(x, y, z, dx, dy, dz, 6f, 2f, 192f, 6, 8f));
                }
                ambient /= 6f;
                int qo = Math.round(Math.min(od, OD_MAX) / OD_MAX * 4095f), qa = Math.round(Math.min(ambient, AMBIENT_MAX) / AMBIENT_MAX * 4095f);
                points[(k * ny + j) * nxp * 4 + i] = qo * 4096f + qa;
                count[k]++;
            }
        });
        int c = 0;
        for (int v : count) c += v;
        computed = c;
        bakeMs = (System.nanoTime() - t0) / 1e6;
    }

    /** Mirror of the shader's paNeutralOpticalDepth (local coordinates, last skip-box exit). */
    private float opticalDepth(float x, float y, float z, float dx, float dy, float dz, float first, float growth, float maxStep, int steps, float odCap) {
        float exitT = exit(x, y, z, 1f / dx, 1f / dy, 1f / dz);
        float raw = 0, s = 0, h = first, cap = odCap / extinction;
        for (int i = 0; i < steps; i++) {
            if (s >= exitT || raw > cap) break;
            float hh = Math.min(h, exitT - s);
            float m = s + .5f * hh;
            float px = x + dx * m, py = y + dy * m, pz = z + dz * m;
            if (px >= minX && py >= minY && pz >= minZ && px <= maxX && py <= maxY && pz <= maxZ) raw += volume.density(px, py, pz) * hh;
            s += hh;
            h = Math.min(h * growth, maxStep);
        }
        return raw * extinction;
    }

    private float exit(float x, float y, float z, float ix, float iy, float iz) {
        if (boxes.length == 0) {
            float ex = Math.max((minX - x) * ix, (maxX - x) * ix), ey = Math.max((minY - y) * iy, (maxY - y) * iy), ez = Math.max((minZ - z) * iz, (maxZ - z) * iz);
            return Math.max(Math.min(ex, Math.min(ey, ez)), 0);
        }
        float far = 0;
        for (float[] b : boxes) {
            float ax = (b[0] - x) * ix, bx = (b[3] - x) * ix, ay = (b[1] - y) * iy, by = (b[4] - y) * iy, az = (b[2] - z) * iz, bz = (b[5] - z) * iz;
            float a = Math.max(Math.max(Math.min(ax, bx), Math.min(ay, by)), Math.min(az, bz));
            float e = Math.min(Math.min(Math.max(ax, bx), Math.max(ay, by)), Math.max(az, bz));
            if (e >= Math.max(a, 0)) far = Math.max(far, e);
        }
        return far;
    }

    /** The volume's texture with the light lattice and its header row appended. */
    public float[] appendTo(float[] base) {
        if (base.length % (W * 4) != 0) throw new IllegalStateException("Volume texture is not whole rows");
        int baseTexels = base.length / 4, lightTexels = points.length / 4;
        int rows = base.length / (W * 4) + (lightTexels + W - 1) / W + 1;
        float[] out = java.util.Arrays.copyOf(base, rows * W * 4);
        System.arraycopy(points, 0, out, baseTexels * 4, points.length);
        int header = (rows - 1) * W * 4;
        out[header] = minX; out[header + 1] = minY; out[header + 2] = minZ; out[header + 3] = SPACING;
        out[header + 4] = nx; out[header + 5] = ny; out[header + 6] = nz; out[header + 7] = baseTexels;
        return out;
    }

    public int rows(int baseRows) { return baseRows + (points.length / 4 + W - 1) / W + 1; }
    public long signature(long base) {
        long h = base * 0x9E3779B97F4A7C15L ^ 0x5DEECE66DL;
        for (float v : points) h = (h ^ Float.floatToRawIntBits(v)) * 0x100000001B3L;
        return h;
    }
    public String stats() {
        return String.format(java.util.Locale.ROOT, "grid=%dx%dx%d spacing=%.1f computed=%d texels=%d bakeMs=%.0f", nx, ny, nz, SPACING, computed, points.length / 4, bakeMs);
    }
}
