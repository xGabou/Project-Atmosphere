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
 * <p>T260.17: the sun direction is a parameter (header texel 2 = (sun xyz, first directional texel)), and an optional
 * DIRECTIONAL block (same layout, one float per lattice point, appended after the first) packs 24 bits:
 * {@code tau(7) share(5) nx(4) ny(4) nz(4)} - the UNCAPPED sun optical depth (log2 code, 0..63), the sky share of the
 * ambient (sky radiance x visibility of the three upward rays over the total), and a filtered outward normal scaled by
 * the filtered gradient strength (4-bit signed per axis, 8 = 0) from box-averaged central differences of the density.
 * <p>T260.17 D: with the directional block a third block (same layout) packs {@code up(12) down(12)}: sky radiance x
 * visibility over the upward rays and ground radiance x visibility over the downward rays, from a 22-ray quadrature -
 * the six T260.5 rays at +-30 deg plus two GRAZING rings of eight rays at +-10 deg (first step 3 blocks) - so a
 * hanging mass occludes the low-angle light of its neighbours. Header texel 3 x = first texel of that block.
 */
public final class NeutralLightVolume {
    public static final float SPACING = 10f;
    public static final float[] SUN = {-0.579228f, 0.573576f, 0.579228f};
    public static final float SKY_L = .15f, GROUND_L = .145f, OD_MAX = 8f, AMBIENT_MAX = .16f;
    private static final int W = SparseBrickVolume.TEX_W;

    private final SparseBrickVolume volume;
    private final float extinction, minX, minY, minZ, maxX, maxY, maxZ;
    private final int nx, ny, nz, nxp;
    private final float spacing;
    private final float[] points;
    private final float[][] boxes;
    private final int computed;
    private final double bakeMs;
    /** T260.17: unit vector toward the sun; the directional block (null when not baked). */
    private final float[] sun;
    private final float[] directional, grazing;
    public static final float TAU_MAX = 63f;

    public NeutralLightVolume(SparseBrickVolume volume, float extinction) { this(volume, extinction, SPACING); }

    /** T260.11: a coarser lattice for km-wide region assemblies (the shader reads the spacing from the header). */
    public NeutralLightVolume(SparseBrickVolume volume, float extinction, float spacing) { this(volume, extinction, spacing, SUN, false); }

    /** T260.17: the sun toward {@code elevationDeg} above the horizon at the T260.5 azimuth. */
    public static float[] sun(double elevationDeg) {
        double e = Math.toRadians(elevationDeg), h = Math.cos(e) * Math.sqrt(.5);
        return new float[]{(float) -h, (float) Math.sin(e), (float) h};
    }

    /** T260.17: any sun direction, with or without the directional block. */
    public NeutralLightVolume(SparseBrickVolume volume, float extinction, float spacing, float[] sunDir, boolean withDirectional) {
        long t0 = System.nanoTime();
        this.sun = sunDir.clone();
        final float[] SUN = this.sun;
        this.spacing = spacing;
        final float SPACING = spacing;
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
        directional = withDirectional ? new float[nxp * 4 * ny * nz] : null;
        grazing = withDirectional ? new float[nxp * 4 * ny * nz] : null;
        final float feather = 12f, h = .5f * spacing;
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
                float ambient = 0, skyPart = 0;
                for (int n = 0; n < 6; n++) {
                    double az = Math.toRadians(37.0 + 60.0 * n), el = Math.toRadians((n & 1) == 0 ? 30.0 : -30.0);
                    float dx = (float) (Math.cos(el) * Math.sin(az)), dy = (float) Math.sin(el), dz = (float) (Math.cos(el) * Math.cos(az));
                    float l = (dy > 0 ? SKY_L : GROUND_L) * (float) Math.exp(-opticalDepth(x, y, z, dx, dy, dz, 6f, 2f, 192f, 6, 8f));
                    ambient += l;
                    if (dy > 0) skyPart += l;
                }
                if (grazing != null) {
                    // up / down hemisphere radiance x visibility: the six rays above (weight 3 each side) and two rings of
                    // eight grazing rays at +-10 deg (weight 8 each side), i.e. a mean over 11 directions per hemisphere
                    float up = 0, down = 0;
                    for (int n = 0; n < 6; n++) {
                        double az = Math.toRadians(37.0 + 60.0 * n), el = Math.toRadians((n & 1) == 0 ? 30.0 : -30.0);
                        float dx = (float) (Math.cos(el) * Math.sin(az)), dy = (float) Math.sin(el), dz = (float) (Math.cos(el) * Math.cos(az));
                        float v = (float) Math.exp(-opticalDepth(x, y, z, dx, dy, dz, 6f, 2f, 192f, 6, 8f));
                        if (dy > 0) up += SKY_L * v; else down += GROUND_L * v;
                    }
                    for (int n = 0; n < 16; n++) {
                        double az = Math.toRadians(22.5 * (n >> 1) + ((n & 1) == 0 ? 0 : 11.25)), el = Math.toRadians((n & 1) == 0 ? 10.0 : -10.0);
                        float dx = (float) (Math.cos(el) * Math.sin(az)), dy = (float) Math.sin(el), dz = (float) (Math.cos(el) * Math.cos(az));
                        float v = (float) Math.exp(-opticalDepth(x, y, z, dx, dy, dz, 3f, 1.6f, 96f, 9, 8f));
                        if (dy > 0) up += SKY_L * v; else down += GROUND_L * v;
                    }
                    up /= 11f; down /= 11f;
                    int qu = Math.round(Math.min(up, AMBIENT_MAX) / AMBIENT_MAX * 4095f), qd = Math.round(Math.min(down, AMBIENT_MAX) / AMBIENT_MAX * 4095f);
                    grazing[(k * ny + j) * nxp * 4 + i] = qu * 4096f + qd;
                }
                if (directional != null) {
                    float tau = opticalDepth(x, y, z, SUN[0], SUN[1], SUN[2], 2f, 1.4f, 40f, 96, TAU_MAX);
                    float share = ambient > 1e-6f ? skyPart / ambient : .5f;
                    // filtered gradient: central differences at +-h, each averaged over a 3x3 cross-plane stencil at +-h
                    float gx = 0, gy = 0, gz = 0;
                    for (int a = -1; a <= 1; a++) for (int bb = -1; bb <= 1; bb++) {
                        float u = a * h, w = bb * h;
                        gx += dens(x + h, y + u, z + w) - dens(x - h, y + u, z + w);
                        gy += dens(x + u, y + h, z + w) - dens(x + u, y - h, z + w);
                        gz += dens(x + u, y + w, z + h) - dens(x + u, y + w, z - h);
                    }
                    float inv = 1f / (9f * 2f * h);
                    gx *= inv; gy *= inv; gz *= inv;
                    float m = (float) Math.sqrt(gx * gx + gy * gy + gz * gz);
                    float strength = Math.min(1f, m * feather * 1.5f), sc = m > 1e-6f ? strength / m : 0;
                    int qt = Math.round((float) (Math.log(1 + Math.min(tau, TAU_MAX)) / Math.log(2) / 6.0 * 127.0));
                    int qs = Math.round(share * 31f);
                    int qx = 8 + Math.round(-gx * sc * 7f), qy = 8 + Math.round(-gy * sc * 7f), qz = 8 + Math.round(-gz * sc * 7f);
                    directional[(k * ny + j) * nxp * 4 + i] = (((qt * 32 + qs) * 16 + qx) * 16 + qy) * 16f + qz;
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

    private float dens(float x, float y, float z) {
        if (x < minX || y < minY || z < minZ || x > maxX || y > maxY || z > maxZ) return 0;
        return volume.density(x, y, z);
    }

    public boolean directional() { return directional != null; }

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

    /**
     * T260.16: the shader's paNeutralBaked on the CPU - (sun od, ambient) decoded from the 12-bit lattice and trilinearly
     * interpolated at local point (x, y, z), cell clamped like the shader.
     */
    public float[] sample(float x, float y, float z) {
        float gx = (x - minX) / spacing, gy = (y - minY) / spacing, gz = (z - minZ) / spacing;
        int ci = Math.max(0, Math.min((int) Math.floor(gx), nx - 2)), cj = Math.max(0, Math.min((int) Math.floor(gy), ny - 2)), ck = Math.max(0, Math.min((int) Math.floor(gz), nz - 2));
        float fx = Math.max(0, Math.min(1, gx - ci)), fy = Math.max(0, Math.min(1, gy - cj)), fz = Math.max(0, Math.min(1, gz - ck));
        float[] out = new float[2];
        for (int c = 0; c < 8; c++) {
            int i = ci + (c & 1), j = cj + ((c >> 1) & 1), k = ck + ((c >> 2) & 1);
            float w = ((c & 1) == 0 ? 1 - fx : fx) * (((c >> 1) & 1) == 0 ? 1 - fy : fy) * (((c >> 2) & 1) == 0 ? 1 - fz : fz);
            float v = points[(k * ny + j) * nxp * 4 + i];
            float hi = (float) Math.floor(v / 4096f);
            out[0] += w * hi / 4095f * OD_MAX;
            out[1] += w * (v - hi * 4096f) / 4095f * AMBIENT_MAX;
        }
        return out;
    }

    /**
     * T260.18: the shader's paT17Grazing on the CPU - (sky radiance x visibility up, ground radiance x visibility down) of
     * the grazing block, trilinear like {@link #sample}; zeros without the block.
     */
    public float[] grazingSample(float x, float y, float z) {
        float[] out = new float[2];
        if (grazing == null) return out;
        float gx = (x - minX) / spacing, gy = (y - minY) / spacing, gz = (z - minZ) / spacing;
        int ci = Math.max(0, Math.min((int) Math.floor(gx), nx - 2)), cj = Math.max(0, Math.min((int) Math.floor(gy), ny - 2)), ck = Math.max(0, Math.min((int) Math.floor(gz), nz - 2));
        float fx = Math.max(0, Math.min(1, gx - ci)), fy = Math.max(0, Math.min(1, gy - cj)), fz = Math.max(0, Math.min(1, gz - ck));
        for (int c = 0; c < 8; c++) {
            int i = ci + (c & 1), j = cj + ((c >> 1) & 1), k = ck + ((c >> 2) & 1);
            float w = ((c & 1) == 0 ? 1 - fx : fx) * (((c >> 1) & 1) == 0 ? 1 - fy : fy) * (((c >> 2) & 1) == 0 ? 1 - fz : fz);
            float v = grazing[(k * ny + j) * nxp * 4 + i];
            float hi = (float) Math.floor(v / 4096f);
            out[0] += w * hi / 4095f * AMBIENT_MAX;
            out[1] += w * (v - hi * 4096f) / 4095f * AMBIENT_MAX;
        }
        return out;
    }

    /**
     * T260.18: the shader's paT17Baked on the CPU - {filtered normal x strength (xyz), tau, sky share}, each corner decoded
     * then trilinearly interpolated; neutral values without the block.
     */
    public float[] directionalSample(float x, float y, float z) {
        float[] out = new float[5];
        if (directional == null) { out[4] = .5f; return out; }
        float gx = (x - minX) / spacing, gy = (y - minY) / spacing, gz = (z - minZ) / spacing;
        int ci = Math.max(0, Math.min((int) Math.floor(gx), nx - 2)), cj = Math.max(0, Math.min((int) Math.floor(gy), ny - 2)), ck = Math.max(0, Math.min((int) Math.floor(gz), nz - 2));
        float fx = Math.max(0, Math.min(1, gx - ci)), fy = Math.max(0, Math.min(1, gy - cj)), fz = Math.max(0, Math.min(1, gz - ck));
        for (int c = 0; c < 8; c++) {
            int i = ci + (c & 1), j = cj + ((c >> 1) & 1), k = ck + ((c >> 2) & 1);
            float w = ((c & 1) == 0 ? 1 - fx : fx) * (((c >> 1) & 1) == 0 ? 1 - fy : fy) * (((c >> 2) & 1) == 0 ? 1 - fz : fz);
            double v = directional[(k * ny + j) * nxp * 4 + i];
            double qz = v % 16; v = Math.floor(v / 16);
            double qy = v % 16; v = Math.floor(v / 16);
            double qx = v % 16; v = Math.floor(v / 16);
            double qs = v % 32, qt = Math.floor(v / 32);
            out[0] += w * (float) ((qx - 8) / 7);
            out[1] += w * (float) ((qy - 8) / 7);
            out[2] += w * (float) ((qz - 8) / 7);
            out[3] += w * (float) (Math.pow(2, qt / 127.0 * 6.0) - 1);
            out[4] += w * (float) (qs / 31);
        }
        return out;
    }

    /** T260.16: the shader's paNeutralOpticalDepth from local point (x, y, z) along a unit direction (CPU mirror). */
    public float opticalDepth(float x, float y, float z, float[] dir, float first, float growth, float maxStep, int steps, float odCap) {
        return opticalDepth(x, y, z, dir[0], dir[1], dir[2], first, growth, maxStep, steps, odCap);
    }

    /** The volume's texture with the light lattice and its header row appended. */
    public float[] appendTo(float[] base) {
        if (base.length % (W * 4) != 0) throw new IllegalStateException("Volume texture is not whole rows");
        int baseTexels = base.length / 4, lightTexels = points.length / 4, dirTexels = directional == null ? 0 : directional.length / 4;
        int grazTexels = grazing == null ? 0 : grazing.length / 4;
        int rows = base.length / (W * 4) + (lightTexels + dirTexels + grazTexels + W - 1) / W + 1;
        float[] out = java.util.Arrays.copyOf(base, rows * W * 4);
        System.arraycopy(points, 0, out, baseTexels * 4, points.length);
        if (directional != null) System.arraycopy(directional, 0, out, (baseTexels + lightTexels) * 4, directional.length);
        if (grazing != null) System.arraycopy(grazing, 0, out, (baseTexels + lightTexels + dirTexels) * 4, grazing.length);
        int header = (rows - 1) * W * 4;
        out[header] = minX; out[header + 1] = minY; out[header + 2] = minZ; out[header + 3] = spacing;
        out[header + 4] = nx; out[header + 5] = ny; out[header + 6] = nz; out[header + 7] = baseTexels;
        out[header + 8] = sun[0]; out[header + 9] = sun[1]; out[header + 10] = sun[2]; out[header + 11] = directional == null ? 0 : baseTexels + lightTexels;
        out[header + 12] = grazing == null ? 0 : baseTexels + lightTexels + dirTexels;
        return out;
    }

    public int rows(int baseRows) {
        return baseRows + (points.length / 4 + (directional == null ? 0 : directional.length / 4) + (grazing == null ? 0 : grazing.length / 4) + W - 1) / W + 1;
    }
    /** Texture bytes added by the light (both blocks, RGBA32F). */
    public long textureBytes() { return 16L * ((points.length + (directional == null ? 0 : directional.length) + (grazing == null ? 0 : grazing.length)) / 4); }
    public long signature(long base) {
        long h = base * 0x9E3779B97F4A7C15L ^ 0x5DEECE66DL;
        for (float v : points) h = (h ^ Float.floatToRawIntBits(v)) * 0x100000001B3L;
        if (directional != null) for (float v : directional) h = (h ^ Float.floatToRawIntBits(v)) * 0x100000001B3L;
        if (grazing != null) for (float v : grazing) h = (h ^ Float.floatToRawIntBits(v)) * 0x100000001B3L;
        for (float v : sun) h = (h ^ Float.floatToRawIntBits(v)) * 0x100000001B3L;
        return h;
    }
    public String stats() {
        return String.format(java.util.Locale.ROOT, "grid=%dx%dx%d spacing=%.1f computed=%d texels=%d directional=%s sunElev=%.1f lightMB=%.1f bakeMs=%.0f", nx, ny, nz, spacing, computed,
                points.length / 4, directional != null, Math.toDegrees(Math.asin(sun[1])), textureBytes() / 1048576.0, bakeMs);
    }
}
