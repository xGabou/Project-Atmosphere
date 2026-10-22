package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.client.render.volumetric.StormAnatomyCapturePlan;
import net.Gabou.projectatmosphere.clouds.type.CloudFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudShapeProfile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 * T258 CPU iteration preview at the NATIVE cameras (T258 fitted poses, effective 121 degree lens,
 * 1200x675 target): flat-light alpha silhouettes of B v3 and Candidate C side by side, cropped at 1:1
 * native pixels, plus a headlight-shaded C crop as a structure aid. Iteration evidence only, never a
 * substitute for the native gallery. Args: outDir [views comma list] [levels comma list C0..C3].
 */
public final class StormAnatomyCPreview {
    static final String[] TYPES = {"cumulonimbus_calvus", "cumulonimbus_capillatus", "supercell"};
    static final long[] SEEDS = {20261008L, 20268927L, 20276846L};
    static final int W = 1200, H = 675;
    static final double FOV = 110 * 1.1;

    interface Field { float d(float x, float y, float z); }

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "build/t258-preview");
        out.mkdirs();
        List<String> views = Arrays.asList((args.length > 1 ? args[1] : "side,front,above,downwind,portrait_front").split(","));
        List<String> levels = Arrays.asList((args.length > 2 ? args[2] : "C3").split(","));
        String only = System.getenv("T258_PREVIEW_TYPE");
        for (String type : TYPES) {
            if (only != null && !type.contains(only)) continue;
            var shape = CloudShapeProfile.defaultFor(type, CloudFamily.CUMULONIMBUS, null);
            int cols = views.size() * (1 + levels.size() + 1);
            int cw = 340, ch = 230;
            BufferedImage sheet = new BufferedImage(cw * cols, ch * SEEDS.length, BufferedImage.TYPE_INT_RGB);
            for (int s = 0; s < SEEDS.length; s++) {
                long seed = SEEDS[s];
                var b = ConvectiveGrowthFieldPrototype.compile(type, shape, seed);
                var c3 = StormAnatomyCandidateC.compile(type, shape, seed);
                if (s == 0) System.out.printf("T258_PREVIEW type=%s H=%.0f voxel=%.2f dims=%s bounds=%s elements=%d%n", type, c3.height(), c3.voxel(),
                        Arrays.toString(c3.dims()), c3.bounds(), c3.elements().size());
                int col = 0;
                for (String view : views) {
                    double[] pose = StormAnatomyCapturePlan.pose(view, b.bounds(), c3.bounds(), FOV, W / (double) H);
                    float[] alphaB = render(b::referenceDistance, b.bounds(), b.feather(), pose, null);
                    int[] box = bbox(alphaB);
                    float[][] cs = new float[levels.size()][];
                    float[] shade = new float[W * H];
                    for (int l = 0; l < levels.size(); l++) {
                        var c = levels.get(l).equals("C3") ? c3 : StormAnatomyCandidateC.compile(type, shape, seed, StormAnatomyCandidateC.Level.valueOf(levels.get(l)));
                        cs[l] = render(c::sampledDistance, c.bounds(), c.feather(), pose, l == levels.size() - 1 ? shade : null);
                        box = union(box, bbox(cs[l]));
                    }
                    int cxp = (box[0] + box[2]) / 2, cyp = (box[1] + box[3]) / 2;
                    blit(sheet, alphaB, null, cxp, cyp, col++ * cw, s * ch, cw, ch, 0xffb0b0);
                    for (float[] a : cs) blit(sheet, a, null, cxp, cyp, col++ * cw, s * ch, cw, ch, 0xb0ffb0);
                    blit(sheet, cs[cs.length - 1], shade, cxp, cyp, col++ * cw, s * ch, cw, ch, 0xb0b0ff);
                }
            }
            File f = new File(out, "t258-preview-" + type + ".png");
            ImageIO.write(sheet, "png", f);
            System.out.println("T258_PREVIEW_SHEET " + f.getAbsolutePath() + " columns=" + views + " x [B v3, " + levels + ", shaded]");
        }
    }

    /** Flat alpha (extinction 0.06/block of density) along the native ray; optional headlight shading of the first 0.5 iso hit. */
    static float[] render(Field f, StormMorphologyPrototype.Bounds b, float feather, double[] pose, float[] shade) {
        double[] cam = {pose[0], pose[1], pose[2]};
        double[] fwd = norm(new double[]{pose[3] - pose[0], pose[4] - pose[1], pose[5] - pose[2]});
        double[] right = norm(cross(fwd, new double[]{0, 1, 0})), up = cross(right, fwd);
        double ty = Math.tan(Math.toRadians(FOV) / 2), tx = ty * W / H;
        float[] alpha = new float[W * H];
        IntStream.range(0, H).parallel().forEach(j -> {
            for (int i = 0; i < W; i++) {
                double sx = (2 * (i + .5) / W - 1) * tx, sy = (1 - 2 * (j + .5) / H) * ty;
                double[] d = norm(new double[]{fwd[0] + right[0] * sx + up[0] * sy, fwd[1] + right[1] * sx + up[1] * sy, fwd[2] + right[2] * sx + up[2] * sy});
                double[] sl = slab(cam, d, b);
                if (sl == null) continue;
                double t = sl[0], trans = 1;
                boolean shaded = shade == null;
                for (int step = 0; step < 6000 && t < sl[1] && trans > .004; step++) {
                    float x = (float) (cam[0] + d[0] * t), y = (float) (cam[1] + d[1] * t), z = (float) (cam[2] + d[2] * t);
                    float dist = f.d(x, y, z);
                    if (!shaded && dist < 0) {
                        float e = 6;
                        float gx = f.d(x + e, y, z) - f.d(x - e, y, z), gy = f.d(x, y + e, z) - f.d(x, y - e, z), gz = f.d(x, y, z + e) - f.d(x, y, z - e);
                        float gl = (float) Math.sqrt(gx * gx + gy * gy + gz * gz) + 1e-9f;
                        double ndl = -(gx * d[0] + gy * d[1] + gz * d[2]) / gl;
                        double sun = Math.max(0, (gx * .4 + gy * .8 + gz * .45) / gl);
                        shade[j * W + i] = (float) Math.max(.08, .55 * ndl + .45 * sun);
                        shaded = true;
                    }
                    double dens = Math.max(0, Math.min(1, .5 - dist / feather));
                    double step2 = dens > 0 ? Math.min(4, Math.max(1, Math.abs(dist) * .5)) : Math.max(dist - feather * .5, 1.5);
                    if (dens > 0) trans *= Math.exp(-dens * .06 * step2);
                    t += step2;
                }
                alpha[j * W + i] = (float) (1 - trans);
            }
        });
        return alpha;
    }

    static int[] bbox(float[] a) {
        int x0 = W, y0 = H, x1 = -1, y1 = -1;
        for (int k = 0; k < a.length; k++) if (a[k] > .01f) { int i = k % W, j = k / W; x0 = Math.min(x0, i); x1 = Math.max(x1, i); y0 = Math.min(y0, j); y1 = Math.max(y1, j); }
        return new int[]{x0, y0, x1, y1};
    }
    static int[] union(int[] a, int[] b) { return new int[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[2], b[2]), Math.max(a[3], b[3])}; }

    /** 1:1 native-pixel crop centred on (cx, cy): alpha over a dark ground, or shading if given. */
    static void blit(BufferedImage sheet, float[] a, float[] shade, int cx, int cy, int ox, int oy, int w, int h, int tag) {
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) {
            int x = cx - w / 2 + i, y = cy - h / 2 + j;
            int rgb = 0x10151d;
            if (x >= 0 && y >= 0 && x < W && y < H) {
                float v = a[y * W + x];
                if (shade != null) { float s = shade[y * W + x]; int g = (int) (255 * Math.min(1, s)); rgb = v > .02f ? (g << 16 | g << 8 | g) : 0x10151d; }
                else { int g = (int) (0x10 + (235 - 0x10) * v); rgb = g << 16 | g << 8 | Math.min(255, g + 8); }
            }
            if (i < 3 && j < 3) rgb = tag;
            if (i == w - 1 || j == h - 1) rgb = 0x303030;
            sheet.setRGB(ox + i, oy + j, rgb);
        }
    }

    static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    static double[] norm(double[] a) { double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); return new double[]{a[0] / l, a[1] / l, a[2] / l}; }
    static double[] slab(double[] o, double[] d, StormMorphologyPrototype.Bounds b) {
        double t0 = 0, t1 = 1e9;
        double[] lo = {b.minX(), b.minY(), b.minZ()}, hi = {b.maxX(), b.maxY(), b.maxZ()};
        for (int a = 0; a < 3; a++) {
            if (Math.abs(d[a]) < 1e-12) { if (o[a] < lo[a] || o[a] > hi[a]) return null; continue; }
            double ta = (lo[a] - o[a]) / d[a], tb = (hi[a] - o[a]) / d[a];
            t0 = Math.max(t0, Math.min(ta, tb)); t1 = Math.min(t1, Math.max(ta, tb));
        }
        return t0 < t1 ? new double[]{t0, t1} : null;
    }
}
