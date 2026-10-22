package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.client.render.volumetric.ConvectiveSystemCapturePlan;
import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveSystemT260.Component;
import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveSystemT260.Recipe;
import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveSystemT260.Role;
import net.Gabou.projectatmosphere.clouds.morphology.StormMorphologyPrototype.Bounds;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * T260 CPU gallery and structural gates for the calvus system. All images use the NATIVE camera plan
 * (ConvectiveSystemCapturePlan, effective 121 degree lens, complete-system framing) unless stated as orthographic.
 * Outputs: component identification, top-down layout, high-resolution silhouettes, primary cross-sections, binary masks
 * for the T259 metric code, and t260-gates.json (gate A individual cell, gate B complete system).
 * Args: outDir [seeds comma list] [quick=1 renders only the side views].
 */
public final class ConvectiveSystemT260Gallery {
    static final long[] SEEDS = {20261015L, 20262741L, 20264467L};
    static final List<String> VIEWS = List.of("side", "opposite_side", "front", "back", "above", "below", "low_angle",
            "close", "far", "upwind", "downwind", "crosswind_left", "crosswind_right");
    static final double FOV = 110 * 1.1;
    static final List<String> json = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--t2604")) { T2604Attribution.main(Arrays.copyOfRange(args, 1, args.length)); return; } // T260.4 attribution
        Path out = Path.of(args.length > 0 ? args[0] : "build/t260-gallery");
        long[] seeds = args.length > 1 && !args[1].isBlank() ? Arrays.stream(args[1].split(",")).mapToLong(Long::parseLong).toArray() : SEEDS;
        boolean quick = args.length > 2 && args[2].equals("1");
        // Variant "A" = frozen T260.1, "B" = frozen T260.2, "C" = T260.3 layout. Framing = union of the extremes of the
        // variant pair in args[4] ("BC" default for T260.3; "AB" reproduces the T260.2 cameras).
        String variant = args.length > 3 ? args[3] : "C";
        String framing = args.length > 4 ? args[4] : "BC";
        if (args.length > 5) CalvusSystemC.Builder.CROWN_JITTER = Float.parseFloat(args[5]); // T260.3 crown study only
        if (args.length > 6 && args[6].equals("noflanks")) CalvusSystemC.Builder.FLANKS = false; // T260.3 independence check
        Files.createDirectories(out);
        for (long seed : seeds) {
            long t0 = System.nanoTime();
            Recipe r = variant(variant, seed);
            float[] framePoints = unionExtremes(seed, framing);
            System.out.printf(Locale.ROOT, "T260_RECIPE variant=%s seed=%d bubbles=%d domain=%s%n", variant, seed, r.bubbles().size(), ConvectiveSystemT260.Program.domain(r, null, 30));
            double recipeMs = (System.nanoTime() - t0) / 1e6;
            var full = new ConvectiveSystemT260.Program(r, null, 6, 12, .25f);
            boolean[] only = new boolean[r.components().size()];
            for (int c = 0; c < only.length; c++) only[c] = r.components().get(c).role() == Role.PRIMARY;
            var iso = new ConvectiveSystemT260.Program(r, only, 6, 12, .25f);
            var st = full.volume().stats();
            System.out.printf(Locale.ROOT, "T260_SIGNATURE variant=%s seed=%d full=%s iso=%s%n", variant, seed,
                    Long.toHexString(full.volume().signature()), Long.toHexString(iso.volume().signature()));
            System.out.printf(Locale.ROOT, "T260_SYSTEM seed=%d recipeMs=%.0f bubbles=%d components=%d bakeMs=%.0f bricks=%d full=%d textureMB=%.1f bounds=%s%n",
                    seed, recipeMs, r.bubbles().size(), r.components().size(), st.bakeMs(), st.bricks(), st.fullCells(), st.textureBytes() / 1048576.0, full.bounds());
            for (int c = 0; c < r.components().size(); c++) {
                Component k = r.components().get(c);
                long n = r.bubbles().stream().filter(b -> b.comp() == k.hashCode() * 0 + indexOf(r, k)).count();
                System.out.printf(Locale.ROOT, "  component %d %s role=%s group=%d top=%.0f anchor=(%.0f,%.0f) bubbles=%d%n", c, k.name(), k.role(), k.group(), k.top(), k.anchorX(), k.anchorZ(), n);
            }
            Path dir = out.resolve(Long.toString(seed));
            Files.createDirectories(dir.resolve("masks"));
            Bounds frame = full.bounds();
            List<String> views = quick ? List.of("side", "front", "above", "low_angle") : VIEWS;
            BufferedImage sheet = new BufferedImage(4 * 600, ((views.size() + 3) / 4) * 2 * 338, BufferedImage.TYPE_INT_RGB);
            int idx = 0;
            for (String view : views) {
                double fov = ConvectiveSystemCapturePlan.optionFov(view, 110) * 1.1;
                double[] pose = ConvectiveSystemCapturePlan.pose(view, frame, framePoints, fov, 1200 / 675.0);
                Image img = render(full, pose, 1200, 675, true, fov);
                ImageIO.write(img.alphaImage(), "png", dir.resolve("masks").resolve(view + "__alpha.png").toFile());
                ImageIO.write(img.mask(), "png", dir.resolve("masks").resolve(view + "__mask.png").toFile());
                BufferedImage comp = img.componentImage(r);
                label(comp, view + "  (component identification)", r);
                ImageIO.write(comp, "png", dir.resolve("components__" + view + ".png").toFile());
                Image isoImg = render(iso, pose, 1200, 675, false, fov);
                ImageIO.write(isoImg.alphaImage(), "png", dir.resolve("masks").resolve("iso_" + view + "__alpha.png").toFile());
                ImageIO.write(isoImg.mask(), "png", dir.resolve("masks").resolve("iso_" + view + "__mask.png").toFile());
                Graphics2D g = sheet.createGraphics();
                g.drawImage(scaled(img.alphaImage(), 600, 338), (idx % 4) * 600, (idx / 4) * 676, null);
                g.drawImage(scaled(comp, 600, 338), (idx % 4) * 600, (idx / 4) * 676 + 338, null);
                g.setColor(Color.YELLOW);
                g.setFont(new Font("SansSerif", Font.BOLD, 16));
                g.drawString(view, (idx % 4) * 600 + 8, (idx / 4) * 676 + 20);
                g.dispose();
                idx++;
                if (!quick && List.of("side", "front", "low_angle", "crosswind_left").contains(view)) {
                    Image hi = render(full, pose, 2400, 1350, false, fov);
                    ImageIO.write(hi.alphaImage(), "png", dir.resolve("hires__" + view + ".png").toFile());
                    Image hiIso = render(iso, pose, 2400, 1350, false, fov);
                    ImageIO.write(hiIso.alphaImage(), "png", dir.resolve("hires__iso_" + view + ".png").toFile());
                }
            }
            ImageIO.write(sheet, "png", dir.resolve("sheet.png").toFile());
            ImageIO.write(topDown(full, r), "png", dir.resolve("top_down_layout.png").toFile());
            ImageIO.write(sections(iso, r), "png", dir.resolve("primary_cross_sections.png").toFile());
            gates(seed, r, full, iso, dir, st, recipeMs);
        }
        Files.writeString(out.resolve("t260-gates.json"), "[\n" + String.join(",\n", json) + "\n]\n");
    }

    static int indexOf(Recipe r, Component k) { return r.components().indexOf(k); }

    static Recipe variant(String v, long seed) {
        return switch (v) { case "A" -> ConvectiveSystemT260.calvusA(seed); case "B" -> ConvectiveSystemT260.calvusB(seed);
            case "W" -> ConvectiveSystemT260.calvusBroad(seed); case "D" -> ConvectiveSystemT260.calvusD(seed); case "F", "G" -> { CalvusSystemD.Builder.NOTCH_OUT = v.equals("F") ? 1.2f : 1.8f; try { yield ConvectiveSystemT260.calvusD(seed); } finally { CalvusSystemD.Builder.NOTCH_OUT = .6f; } } case "E" -> { CalvusSystemD.Builder.NOTCH = false; try { yield ConvectiveSystemT260.calvusD(seed); } finally { CalvusSystemD.Builder.NOTCH = true; } } default -> ConvectiveSystemT260.calvus(seed); };
    }

    /** Framing points shared by the two variants of a seed (identical cameras for before/after). */
    static float[] unionExtremes(long seed, String pair) {
        float[] a = ConvectiveSystemCapturePlan.extremes(variant(pair.substring(0, 1), seed));
        float[] b = ConvectiveSystemCapturePlan.extremes(variant(pair.substring(1, 2), seed));
        float[] u = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, u, a.length, b.length);
        return u;
    }

    // ------------------------------------------------------------------ rendering

    record Image(int w, int h, float[] alpha, int[] owner) {
        BufferedImage alphaImage() {
            BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            for (int k = 0; k < alpha.length; k++) {
                int g = (int) (0x10 + (235 - 0x10) * alpha[k]);
                im.setRGB(k % w, k / w, g << 16 | g << 8 | Math.min(255, g + 8));
            }
            return im;
        }
        BufferedImage mask() {
            BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
            for (int k = 0; k < alpha.length; k++) im.getRaster().setSample(k % w, k / w, 0, alpha[k] > .5f ? 255 : 0);
            return im;
        }
        BufferedImage componentImage(Recipe r) {
            BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            for (int k = 0; k < alpha.length; k++) {
                int rgb = 0x10151d;
                if (alpha[k] > .02f) {
                    int c = owner[k] >= 0 ? colour(r, owner[k]) : 0x909090;
                    float a = Math.min(1, alpha[k] * 1.4f);
                    rgb = blend(0x10151d, c, a);
                }
                im.setRGB(k % w, k / w, rgb);
            }
            return im;
        }
    }

    static int colour(Recipe r, int c) {
        Component k = r.components().get(c);
        return switch (k.role()) {
            case PRIMARY -> 0xff6a3d;
            case SECONDARY -> k.group() == 0 ? (k.name().endsWith("1") ? 0xffd23d : 0xf0a830) : 0x7bdc5a;
            case LOWER_MASS -> 0x7d9cc8;
            case ASSOCIATED -> 0x4fd6d0;
            default -> 0xc0c0c0;
        };
    }

    static int blend(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t), bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    /** Flat-alpha march through the baked volume (extinction 0.06/block of density) at the native camera. */
    static Image render(ConvectiveSystemT260.Program p, double[] pose, int W, int H, boolean owners, double FOV) {
        var v = p.volume();
        Bounds b = v.bounds();
        double[] cam = {pose[0], pose[1], pose[2]};
        double[] fwd = norm(new double[]{pose[3] - pose[0], pose[4] - pose[1], pose[5] - pose[2]});
        double[] right = norm(cross(fwd, new double[]{0, 1, 0})), up = cross(right, fwd);
        double ty = Math.tan(Math.toRadians(FOV) / 2), tx = ty * W / H;
        float[] alpha = new float[W * H];
        int[] owner = new int[W * H];
        Arrays.fill(owner, -1);
        var field = p.field();
        IntStream.range(0, H).parallel().forEach(j -> {
            for (int i = 0; i < W; i++) {
                double sx = (2 * (i + .5) / W - 1) * tx, sy = (1 - 2 * (j + .5) / H) * ty;
                double[] d = norm(new double[]{fwd[0] + right[0] * sx + up[0] * sy, fwd[1] + right[1] * sx + up[1] * sy, fwd[2] + right[2] * sx + up[2] * sy});
                double[] sl = slab(cam, d, b);
                if (sl == null) continue;
                double t = sl[0], trans = 1;
                boolean found = !owners;
                for (int step = 0; step < 20000 && t < sl[1] && trans > .004; step++) {
                    float x = (float) (cam[0] + d[0] * t), y = (float) (cam[1] + d[1] * t), z = (float) (cam[2] + d[2] * t);
                    float dens = v.density(x, y, z);
                    if (dens > 0) {
                        trans *= Math.exp(-dens * .06 * 3);
                        if (!found && dens > .5f) { owner[j * W + i] = field.owner(x, y, z); found = true; }
                    }
                    t += 3;
                }
                alpha[j * W + i] = (float) (1 - trans);
            }
        });
        return new Image(W, H, alpha, owner);
    }

    static void label(BufferedImage im, String title, Recipe r) {
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        g.setColor(Color.WHITE);
        g.drawString(title, 12, 24);
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        int y = 46;
        List<String> seen = new ArrayList<>();
        for (int c = 0; c < r.components().size(); c++) {
            Component k = r.components().get(c);
            String key = legendName(k);
            if (seen.contains(key)) continue;
            seen.add(key);
            g.setColor(new Color(colour(r, c)));
            g.fillRect(12, y - 11, 14, 14);
            g.setColor(Color.WHITE);
            g.drawString(key, 32, y);
            y += 19;
        }
        g.dispose();
    }

    static String legendName(Component k) {
        return switch (k.role()) {
            case PRIMARY -> "primary tower";
            case SECONDARY -> k.group() == 0 ? (k.name().endsWith("1") ? "secondary tower 1 (attached)" : "secondary tower 2 (attached)") : "developing cell (detached)";
            case LOWER_MASS -> "lower cloud mass (attached)";
            case ASSOCIATED -> "associated cumulus (detached)";
            default -> k.name();
        };
    }

    /** Orthographic top-down layout: component colours by first hit from above, gaps visible, wind arrow, scale. */
    static BufferedImage topDown(ConvectiveSystemT260.Program p, Recipe r) {
        var v = p.volume();
        Bounds b = v.bounds();
        float px = 4;
        int W = (int) Math.ceil((b.maxX() - b.minX()) / px), H = (int) Math.ceil((b.maxZ() - b.minZ()) / px);
        BufferedImage im = new BufferedImage(W, H + 60, BufferedImage.TYPE_INT_RGB);
        var field = p.field();
        IntStream.range(0, H).parallel().forEach(j -> {
            for (int i = 0; i < W; i++) {
                float x = b.minX() + (i + .5f) * px, z = b.minZ() + (j + .5f) * px;
                int rgb = 0x10151d;
                for (float y = b.maxY(); y > b.minY(); y -= 3) {
                    if (v.density(x, y, z) > .5f) {
                        int o = field.owner(x, y, z);
                        float shade = .55f + .45f * Math.min(1, y / r.primaryHeight());
                        rgb = blend(0x10151d, o >= 0 ? colour(r, o) : 0x909090, shade);
                        break;
                    }
                }
                im.setRGB(i, j, rgb);
            }
        });
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        for (int c = 0; c < r.components().size(); c++) {
            Component k = r.components().get(c);
            int ix = (int) ((k.anchorX() - b.minX()) / px), iz = (int) ((k.anchorZ() - b.minZ()) / px);
            g.setColor(Color.BLACK);
            g.fillOval(ix - 3, iz - 3, 6, 6);
            g.setColor(Color.WHITE);
            if (k.role() != Role.LOWER_MASS) g.drawString(String.format(Locale.ROOT, "%s %.0f", k.name(), k.top()), ix + 5, iz - 4);
        }
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(3));
        g.drawLine(20, H + 30, 20 + (int) (500 / px), H + 30);
        g.drawString("500 blocks", 24, H + 50);
        g.drawLine(W - 160, H + 30, W - 60, H + 30);
        g.drawLine(W - 60, H + 30, W - 72, H + 22);
        g.drawLine(W - 60, H + 30, W - 72, H + 38);
        g.drawString("wind / downwind (+x)", W - 200, H + 52);
        g.drawString("TOP-DOWN LAYOUT (orthographic, +z down)", 20, H + 14);
        g.dispose();
        return im;
    }

    /** Horizontal cross-sections of the isolated primary at 15..90 % height (distance field, inside = white). */
    static BufferedImage sections(ConvectiveSystemT260.Program iso, Recipe r) {
        float[] fr = {.15f, .3f, .45f, .6f, .75f, .9f};
        int S = 300;
        BufferedImage im = new BufferedImage(S * fr.length, S + 30, BufferedImage.TYPE_INT_RGB);
        Component prim = primary(r);
        float span = r.primaryHeight() * 1.0f, px = span / S;
        Graphics2D g = im.createGraphics();
        for (int n = 0; n < fr.length; n++) {
            float y = r.primaryHeight() * fr[n];
            for (int j = 0; j < S; j++) for (int i = 0; i < S; i++) {
                float x = prim.anchorX() - span / 2 + (i + .5f) * px + r.primaryHeight() * .1f, z = prim.anchorZ() - span / 2 + (j + .5f) * px;
                float d = iso.volume().density(x, y, z);
                im.setRGB(n * S + i, j, d > .5f ? 0xf0f0f0 : (i == S / 2 || j == S / 2) ? 0x303848 : 0x10151d);
            }
            g.setColor(Color.YELLOW);
            g.drawString(String.format(Locale.ROOT, "%.0f%% H", fr[n] * 100), n * S + 8, S + 20);
        }
        g.dispose();
        return im;
    }

    static Component primary(Recipe r) { for (Component c : r.components()) if (c.role() == Role.PRIMARY) return c; return r.components().get(0); }

    static BufferedImage scaled(BufferedImage src, int w, int h) {
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return im;
    }

    // ------------------------------------------------------------------ gates

    /** Orthographic side silhouette (columns along the horizontal axis perpendicular to azimuth), 1 px = px blocks. */
    static boolean[][] ortho(ConvectiveSystemT260.Program p, double azimuth, float px) {
        var v = p.volume();
        Bounds b = v.bounds();
        double ux = Math.cos(azimuth), uz = Math.sin(azimuth); // view direction (horizontal)
        double rx = -uz, rz = ux;                               // image horizontal axis
        float cx = (b.minX() + b.maxX()) / 2, cz = (b.minZ() + b.maxZ()) / 2;
        float half = (float) Math.hypot(b.maxX() - b.minX(), b.maxZ() - b.minZ()) / 2;
        int W = (int) Math.ceil(2 * half / px), H = (int) Math.ceil((b.maxY() - Math.max(0, b.minY())) / px);
        boolean[][] m = new boolean[H][W];
        IntStream.range(0, H).parallel().forEach(j -> {
            float y = b.maxY() - (j + .5f) * px;
            for (int i = 0; i < W; i++) {
                double s = -half + (i + .5) * px;
                double x0 = cx + rx * s, z0 = cz + rz * s;
                for (double t = -half; t < half; t += 3) {
                    if (v.density((float) (x0 + ux * t), y, (float) (z0 + uz * t)) > .5f) { m[j][i] = true; break; }
                }
            }
        });
        return m;
    }

    record Profile(int summits, int notches, float deepestNotch, float overhang, float lean, float convexityDeficit, float aspect,
                   float w85, float topFlat, int towers) {}

    /** Silhouette metrics (same definitions as t259_silhouette.py where they overlap), normalised to silhouette height. */
    static Profile profile(boolean[][] m) {
        int H = m.length, W = m[0].length;
        int top = H, bottom = -1, left = W, rightC = -1;
        for (int j = 0; j < H; j++) for (int i = 0; i < W; i++) if (m[j][i]) { top = Math.min(top, j); bottom = Math.max(bottom, j); left = Math.min(left, i); rightC = Math.max(rightC, i); }
        if (bottom < 0) return new Profile(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        float h = bottom - top + 1;
        float[] topY = new float[W];
        for (int i = 0; i < W; i++) { topY[i] = -1; for (int j = 0; j < H; j++) if (m[j][i]) { topY[i] = (bottom - j + 1) / h; break; } }
        // Summits / towers: peaks of the top profile with prominence >= 3 % (summits) and >= 6 % H (towers).
        int summits = peaks(topY, .03f), towers = peaks(topY, .06f);
        // Notches: closing-based open valleys on the top profile (>= 1.5 % H deep), measured as local minima between peaks.
        float[] closed = closeProfile(topY, Math.max(2, Math.round(.10f * h)));
        int notches = 0;
        float deepest = 0;
        boolean in = false;
        float depth = 0;
        for (int i = 0; i < W; i++) {
            float dd = topY[i] < 0 ? 0 : closed[i] - topY[i];
            if (dd >= .015f) { if (!in) notches++; in = true; depth = Math.max(depth, dd); }
            else { if (in) deepest = Math.max(deepest, depth); in = false; depth = 0; }
        }
        // Overhang: area of sky directly below cloud within the silhouette's own vertical extent / cloud area.
        long area = 0, over = 0;
        for (int i = 0; i < W; i++) {
            boolean seen = false;
            for (int j = top; j <= bottom; j++) {
                if (m[j][i]) { area++; seen = true; } else if (seen && hasBelow(m, i, j, bottom)) over++;
            }
        }
        // Lean: summit column vs base centroid.
        int summitCol = 0;
        for (int i = 0; i < W; i++) if (topY[i] > topY[summitCol]) summitCol = i;
        double baseSum = 0; int baseN = 0;
        for (int j = bottom - Math.max(1, Math.round(h * .1f)); j <= bottom; j++) for (int i = 0; i < W; i++) if (m[j][i]) { baseSum += i; baseN++; }
        float lean = baseN == 0 ? 0 : (float) Math.abs(summitCol - baseSum / baseN) / h;
        // Convexity deficit: 1 - area / area(hull of the top profile) with the base closed (row-wise hull approx).
        long hull = 0;
        int[] hullTop = upperHull(topY, h);
        for (int i = left; i <= rightC; i++) hull += hullTop[i];
        float cd = hull == 0 ? 0 : 1 - (float) area / hull;
        float aspect = (rightC - left + 1) / h;
        int wmax = 0, w85 = 0;
        for (int j = top; j <= bottom; j++) {
            int l = -1, rr = -1;
            for (int i = 0; i < W; i++) if (m[j][i]) { if (l < 0) l = i; rr = i; }
            int w = l < 0 ? 0 : rr - l + 1;
            wmax = Math.max(wmax, w);
            if (Math.abs((bottom - j + 1) / h - .85f) < .5f / h + 1e-6f) w85 = w;
        }
        int flat = 0, cols = 0;
        for (int i = 0; i < W; i++) if (topY[i] >= 0) { cols++; if (topY[i] >= 1 - .03f) flat++; }
        return new Profile(summits, notches, deepest, area == 0 ? 0 : over / (float) area, lean, cd, aspect,
                wmax == 0 ? 0 : w85 / (float) wmax, cols == 0 ? 0 : flat / (float) cols, towers);
    }

    static boolean hasBelow(boolean[][] m, int i, int j, int bottom) { for (int k = j + 1; k <= bottom; k++) if (m[k][i]) return true; return false; }

    static int peaks(float[] y, float prominence) {
        int n = 0;
        for (int i = 0; i < y.length; i++) {
            if (y[i] < 0) continue;
            boolean max = true;
            for (int k = Math.max(0, i - 3); k <= Math.min(y.length - 1, i + 3); k++) if (y[k] > y[i] || (y[k] == y[i] && k < i)) { max = false; break; }
            if (!max) continue;
            float leftMin = y[i], rightMin = y[i];
            boolean leftHigher = false, rightHigher = false;
            for (int k = i - 1; k >= 0; k--) { if (y[k] > y[i]) { leftHigher = true; break; } leftMin = Math.min(leftMin, y[k] < 0 ? 0 : y[k]); }
            for (int k = i + 1; k < y.length; k++) { if (y[k] > y[i]) { rightHigher = true; break; } rightMin = Math.min(rightMin, y[k] < 0 ? 0 : y[k]); }
            float prom = y[i] - Math.max(leftHigher ? leftMin : 0, rightHigher ? rightMin : 0);
            if (!leftHigher && !rightHigher) prom = y[i];
            if (prom >= prominence) n++;
        }
        return n;
    }

    static float[] closeProfile(float[] y, int r) {
        float[] dil = new float[y.length], out = new float[y.length];
        for (int i = 0; i < y.length; i++) { float m = -1; for (int k = Math.max(0, i - r); k <= Math.min(y.length - 1, i + r); k++) m = Math.max(m, y[k]); dil[i] = m; }
        for (int i = 0; i < y.length; i++) { float m = 2; for (int k = Math.max(0, i - r); k <= Math.min(y.length - 1, i + r); k++) m = Math.min(m, dil[k]); out[i] = y[i] < 0 ? y[i] : Math.max(y[i], m); }
        return out;
    }

    static int[] upperHull(float[] y, float h) {
        int n = y.length;
        List<int[]> pts = new ArrayList<>();
        for (int i = 0; i < n; i++) if (y[i] >= 0) pts.add(new int[]{i, Math.round(y[i] * h)});
        int[] out = new int[n];
        if (pts.isEmpty()) return out;
        List<int[]> hull = new ArrayList<>();
        for (int[] p : pts) {
            while (hull.size() >= 2) {
                int[] a = hull.get(hull.size() - 2), b = hull.get(hull.size() - 1);
                if ((long) (b[0] - a[0]) * (p[1] - a[1]) - (long) (b[1] - a[1]) * (p[0] - a[0]) >= 0) hull.remove(hull.size() - 1);
                else break;
            }
            hull.add(p);
        }
        for (int k = 0; k + 1 < hull.size(); k++) {
            int[] a = hull.get(k), b = hull.get(k + 1);
            for (int i = a[0]; i <= b[0]; i++) out[i] = Math.round(a[1] + (b[1] - a[1]) * (i - a[0]) / (float) Math.max(1, b[0] - a[0]));
        }
        if (hull.size() == 1) out[hull.get(0)[0]] = hull.get(0)[1];
        return out;
    }

    static void gates(long seed, Recipe r, ConvectiveSystemT260.Program full, ConvectiveSystemT260.Program iso, Path dir,
                      SparseBrickVolume.Stats st, double recipeMs) throws Exception {
        float px = 6;
        int az = 8;
        // ---------------- Gate A: isolated primary, 8 orthographic azimuths + cross-sections.
        List<Profile> cell = new ArrayList<>();
        for (int a = 0; a < az; a++) cell.add(profile(ortho(iso, a * Math.PI * 2 / az, px)));
        float[] sec = sectionVariation(iso, r);
        int summitViews = 0, notchViews = 0, overhangViews = 0, cdViews = 0;
        float leanMax = 0;
        for (Profile p : cell) {
            if (p.summits >= 2) summitViews++;
            if (p.notches >= 1) notchViews++;
            if (p.overhang >= .01f) overhangViews++;
            if (p.convexityDeficit >= .05f) cdViews++;
            leanMax = Math.max(leanMax, p.lean);
        }
        // ---------------- Gate B: complete system.
        List<Profile> sys = new ArrayList<>();
        for (int a = 0; a < az; a++) sys.add(profile(ortho(full, a * Math.PI * 2 / az, px)));
        int towerViews = 0;
        float aspMin = 9, aspMax = 0;
        for (Profile p : sys) { if (p.towers >= 2) towerViews++; aspMin = Math.min(aspMin, p.aspect); aspMax = Math.max(aspMax, p.aspect); }
        int[] cc = connectivity(full, r);
        float[] hr = heightRatios(r);
        float[] mass = massPlanform(full, r);
        // Component visibility in the native views (component-ID renders were made from owners; recompute coverage cheaply here).
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "{\"seed\":%d,\"recipeMs\":%.1f,\"bubbles\":%d,\"components\":%d,\"bakeMs\":%.1f,\"bricks\":%d,\"fullCells\":%d,\"emptyCells\":%d,\"textureBytes\":%d,\"cpuBytes\":%d,",
                seed, recipeMs, r.bubbles().size(), r.components().size(), st.bakeMs(), st.bricks(), st.fullCells(), st.emptyCells(), st.textureBytes(), st.cpuBytes()));
        sb.append("\"gateA_cell\":{\"views\":").append(az).append(",\"profiles\":").append(profiles(cell));
        sb.append(String.format(Locale.ROOT, ",\"sectionShapeDissimilarity\":%.3f,\"sectionCentroidPath\":%.3f,\"sectionAreaCV\":%.3f", sec[0], sec[1], sec[2]));
        boolean aSummits = summitViews >= 6, aNotches = notchViews >= 5, aOverhang = overhangViews >= 6, aConvex = cdViews >= 6, aSections = sec[0] >= .05f && sec[1] >= .05f, aLean = leanMax >= .08f;
        sb.append(String.format(Locale.ROOT, ",\"checks\":{\"multiSummit_views>=6of8\":[%d,%s],\"notch_views>=5of8\":[%d,%s],\"undercut_views>=6of8\":[%d,%s],\"notCapsule_convexity>=0.05_views>=6of8\":[%d,%s],\"sectionsVary\":%s,\"asymmetricSummit_lean>=0.08\":[%.3f,%s]}",
                summitViews, aSummits, notchViews, aNotches, overhangViews, aOverhang, cdViews, aConvex, aSections, leanMax, aLean));
        boolean gateA = aSummits && aNotches && aOverhang && aConvex && aSections && aLean;
        sb.append(",\"pass\":").append(gateA).append("}");
        sb.append(",\"gateB_system\":{\"profiles\":").append(profiles(sys));
        boolean bTowers = towerViews >= 7, bAspect = aspMin >= 1.2f && aspMax <= 4.5f;
        boolean bConnect = cc[1] == 1 && cc[2] == cc[3] && cc[4] >= 2;
        boolean bHeights = hr[0] >= .55f && hr[0] <= .88f && hr[1] >= .1f;
        boolean bMass = mass[0] >= .08f && mass[1] >= .12f;
        boolean bPrimary = hr[2] >= .10f;
        sb.append(String.format(Locale.ROOT, ",\"connectivity\":{\"components3d\":%d,\"attachedGroupComponents3d\":%d,\"detachedRecipeComponents\":%d,\"detachedSeparate3d\":%d,\"minGapVoxels\":%d}",
                cc[0], cc[1], cc[3], cc[2], cc[4]));
        sb.append(String.format(Locale.ROOT, ",\"heights\":{\"tallestSecondaryOverPrimary\":%.3f,\"secondarySpread\":%.3f,\"primaryMargin\":%.3f}", hr[0], hr[1], hr[2]));
        sb.append(String.format(Locale.ROOT, ",\"lowerMass\":{\"planformConvexityDeficit\":%.3f,\"primaryOffsetFromMassCentroid\":%.3f,\"planformAspect\":%.2f}", mass[0], mass[1], mass[2]));
        sb.append(String.format(Locale.ROOT, ",\"checks\":{\"towers>=2_views>=7of8\":[%d,%s],\"aspect_1.2-4.5_all_views\":[[%.2f,%.2f],%s],\"connected_attached_separate_detached_gap>=2vox\":%s,\"secondaryHeights\":%s,\"notOvalWithPillar\":%s,\"primaryIdentifiable\":%s}",
                towerViews, bTowers, aspMin, aspMax, bAspect, bConnect, bHeights, bMass, bPrimary));
        boolean gateB = bTowers && bAspect && bConnect && bHeights && bMass && bPrimary;
        sb.append(",\"pass\":").append(gateB).append("}}");
        json.add(sb.toString());
        System.out.println("T260_GATES " + sb);
    }

    static String profiles(List<Profile> ps) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ps.size(); i++) {
            Profile p = ps.get(i);
            if (i > 0) sb.append(',');
            sb.append(String.format(Locale.ROOT, "{\"summits\":%d,\"towers\":%d,\"notches\":%d,\"deepestNotch\":%.3f,\"overhang\":%.3f,\"lean\":%.3f,\"convexityDeficit\":%.3f,\"aspect\":%.2f,\"w85\":%.2f,\"topFlat\":%.3f}",
                    p.summits, p.towers, p.notches, p.deepestNotch, p.overhang, p.lean, p.convexityDeficit, p.aspect, p.w85, p.topFlat));
        }
        return sb.append(']').toString();
    }

    /** Cross-section variation of the isolated primary at 6 heights: polar-profile dissimilarity, centroid path, area CV. */
    static float[] sectionVariation(ConvectiveSystemT260.Program iso, Recipe r) {
        float[] fr = {.15f, .3f, .45f, .6f, .75f, .9f};
        int bins = 72;
        float[][] prof = new float[fr.length][];
        float[][] cen = new float[fr.length][];
        float[] areas = new float[fr.length];
        Component prim = primary(r);
        float span = r.primaryHeight(), px = 4;
        int S = (int) (span / px);
        for (int n = 0; n < fr.length; n++) {
            float y = r.primaryHeight() * fr[n];
            double sx = 0, sz = 0; int cnt = 0;
            boolean[][] in = new boolean[S][S];
            for (int j = 0; j < S; j++) for (int i = 0; i < S; i++) {
                float x = prim.anchorX() - span / 2 + (i + .5f) * px + span * .1f, z = prim.anchorZ() - span / 2 + (j + .5f) * px;
                if (iso.volume().density(x, y, z) > .5f) { in[j][i] = true; sx += x; sz += z; cnt++; }
            }
            areas[n] = cnt * px * px;
            if (cnt == 0) { prof[n] = new float[bins]; cen[n] = new float[]{0, 0}; continue; }
            float mx = (float) (sx / cnt), mz = (float) (sz / cnt);
            cen[n] = new float[]{mx, mz};
            float[] rr = new float[bins];
            for (int k = 0; k < bins; k++) {
                double a = k * Math.PI * 2 / bins;
                float d = 0;
                for (float t = 0; t < span / 2; t += px * .5f) {
                    float x = mx + (float) Math.cos(a) * t, z = mz + (float) Math.sin(a) * t;
                    int i = (int) ((x - (prim.anchorX() - span / 2 + span * .1f)) / px), j = (int) ((z - (prim.anchorZ() - span / 2)) / px);
                    if (i < 0 || j < 0 || i >= S || j >= S) break;
                    if (in[j][i]) d = t;
                }
                rr[k] = d;
            }
            float mean = 0;
            for (float v : rr) mean += v / bins;
            for (int k = 0; k < bins; k++) rr[k] = mean > 0 ? rr[k] / mean : 0;
            prof[n] = rr;
        }
        float dis = 0, path = 0, meanR = 0;
        int pairs = 0;
        for (int n = 0; n + 1 < fr.length; n++) {
            float d = 0;
            for (int k = 0; k < bins; k++) d += Math.abs(prof[n][k] - prof[n + 1][k]) / bins;
            dis += d; pairs++;
            path += (float) Math.hypot(cen[n + 1][0] - cen[n][0], cen[n + 1][1] - cen[n][1]);
        }
        for (float a : areas) meanR += (float) Math.sqrt(a / Math.PI) / areas.length;
        float am = 0;
        for (float a : areas) am += a / areas.length;
        float var = 0;
        for (float a : areas) var += (a - am) * (a - am) / areas.length;
        return new float[]{dis / pairs, meanR == 0 ? 0 : path / pairs / meanR, am == 0 ? 0 : (float) Math.sqrt(var) / am};
    }

    /**
     * 3D connectivity on the voxel lattice (density > 0.5): {total 3D components, 3D components containing the attached
     * group, detached recipe components that are their own 3D component, detached recipe components, min gap (voxels)
     * between different 3D components (capped at 4)}.
     */
    static int[] connectivity(ConvectiveSystemT260.Program p, Recipe r) {
        var v = p.volume();
        Bounds b = v.bounds();
        float vx = v.voxel();
        int nx = (int) ((b.maxX() - b.minX()) / vx), ny = (int) ((b.maxY() - b.minY()) / vx), nz = (int) ((b.maxZ() - b.minZ()) / vx);
        java.util.BitSet occ = new java.util.BitSet(nx * ny * nz);
        IntStream.range(0, nz).parallel().forEach(k -> {
            java.util.BitSet local = new java.util.BitSet();
            for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++)
                if (v.density(b.minX() + (i + .5f) * vx, b.minY() + (j + .5f) * vx, b.minZ() + (k + .5f) * vx) > .5f) local.set((k * ny + j) * nx + i);
            synchronized (occ) { occ.or(local); }
        });
        int[] label = new int[nx * ny * nz];
        int labels = 0;
        int[] queue = new int[nx * ny * nz];
        for (int s = occ.nextSetBit(0); s >= 0; s = occ.nextSetBit(s + 1)) {
            if (label[s] != 0) continue;
            labels++;
            int head = 0, tail = 0;
            queue[tail++] = s;
            label[s] = labels;
            while (head < tail) {
                int c = queue[head++];
                int i = c % nx, j = (c / nx) % ny, k = c / (nx * ny);
                for (int dz = -1; dz <= 1; dz++) for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int ii = i + dx, jj = j + dy, kk = k + dz;
                    if (ii < 0 || jj < 0 || kk < 0 || ii >= nx || jj >= ny || kk >= nz) continue;
                    int o = (kk * ny + jj) * nx + ii;
                    if (occ.get(o) && label[o] == 0) { label[o] = labels; queue[tail++] = o; }
                }
            }
        }
        // Which 3D label holds each recipe component (by its anchor column, lowest occupied voxel above base).
        java.util.Set<Integer> attached = new java.util.HashSet<>();
        int detached = 0, separate = 0;
        java.util.Map<Integer, Integer> labelUse = new java.util.HashMap<>();
        int[] compLabel = new int[r.components().size()];
        for (int c = 0; c < r.components().size(); c++) {
            Component k = r.components().get(c);
            int lab = 0;
            for (var bub : r.bubbles()) {
                if (bub.comp() != c || bub.tier() != 0) continue;
                int i = (int) ((bub.x() - b.minX()) / vx), j = (int) ((bub.y() - b.minY()) / vx), kk = (int) ((bub.z() - b.minZ()) / vx);
                if (i < 0 || j < 0 || kk < 0 || i >= nx || j >= ny || kk >= nz) continue;
                int o = (kk * ny + j) * nx + i;
                if (label[o] != 0) { lab = label[o]; break; }
            }
            compLabel[c] = lab;
            if (k.group() == 0) attached.add(lab);
            labelUse.merge(lab, 1, Integer::sum);
        }
        for (int c = 0; c < r.components().size(); c++) {
            if (r.components().get(c).group() == 0) continue;
            detached++;
            if (compLabel[c] != 0 && !attached.contains(compLabel[c]) && labelUse.get(compLabel[c]) == 1) separate++;
        }
        // Minimum gap between different labels (search radius 4 voxels).
        int gap = 4;
        for (int s = occ.nextSetBit(0); s >= 0 && gap > 1; s = occ.nextSetBit(s + 1)) {
            int i = s % nx, j = (s / nx) % ny, k = s / (nx * ny);
            for (int dz = -gap; dz <= gap; dz++) for (int dy = -gap; dy <= gap; dy++) for (int dx = -gap; dx <= gap; dx++) {
                int ii = i + dx, jj = j + dy, kk = k + dz;
                if (ii < 0 || jj < 0 || kk < 0 || ii >= nx || jj >= ny || kk >= nz) continue;
                int o = (kk * ny + jj) * nx + ii;
                if (occ.get(o) && label[o] != label[s]) gap = Math.min(gap, Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) - 1);
            }
        }
        return new int[]{labels, attached.size(), separate, detached, gap};
    }

    /** {tallest secondary / primary, spread of secondary heights / primary, (primary - tallest other) / primary}. */
    static float[] heightRatios(Recipe r) {
        float hp = 0, best = 0, min = 1e9f;
        for (Component c : r.components()) if (c.role() == Role.PRIMARY) hp = c.top();
        for (Component c : r.components()) if (c.role() == Role.SECONDARY) { best = Math.max(best, c.top()); min = Math.min(min, c.top()); }
        float other = 0;
        for (Component c : r.components()) if (c.role() != Role.PRIMARY) other = Math.max(other, c.top());
        return new float[]{best / hp, (best - min) / hp, (hp - other) / hp};
    }

    /** Lower-mass planform from above: {convexity deficit, primary offset from its centroid / extent, aspect}. */
    static float[] massPlanform(ConvectiveSystemT260.Program p, Recipe r) {
        int massComp = -1;
        for (int c = 0; c < r.components().size(); c++) if (r.components().get(c).role() == Role.LOWER_MASS) massComp = c;
        Bounds b = p.bounds();
        float px = 8, y = r.primaryHeight() * .06f;
        int W = (int) ((b.maxX() - b.minX()) / px), H = (int) ((b.maxZ() - b.minZ()) / px);
        var f = p.field();
        List<double[]> pts = new ArrayList<>();
        double sx = 0, sz = 0;
        int n = 0;
        float minX = 1e9f, maxX = -1e9f, minZ = 1e9f, maxZ = -1e9f;
        for (int j = 0; j < H; j++) for (int i = 0; i < W; i++) {
            float x = b.minX() + (i + .5f) * px, z = b.minZ() + (j + .5f) * px;
            if (f.distance(x, y, z, massComp) < 0) { pts.add(new double[]{x, z}); sx += x; sz += z; n++; minX = Math.min(minX, x); maxX = Math.max(maxX, x); minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z); }
        }
        if (n == 0) return new float[]{0, 0, 0};
        double hull = hullArea(pts);
        float area = n * px * px;
        Component prim = primary(r);
        float extent = Math.max(maxX - minX, maxZ - minZ);
        float off = (float) Math.hypot(prim.anchorX() - sx / n, prim.anchorZ() - sz / n) / extent;
        return new float[]{(float) (1 - area / hull), off, Math.max(maxX - minX, maxZ - minZ) / Math.max(1, Math.min(maxX - minX, maxZ - minZ))};
    }

    static double hullArea(List<double[]> pts) {
        pts.sort((a, b) -> a[0] != b[0] ? Double.compare(a[0], b[0]) : Double.compare(a[1], b[1]));
        List<double[]> h = new ArrayList<>();
        for (int pass = 0; pass < 2; pass++) {
            int start = h.size();
            for (int i = 0; i < pts.size(); i++) {
                double[] p = pts.get(pass == 0 ? i : pts.size() - 1 - i);
                while (h.size() >= start + 2) {
                    double[] a = h.get(h.size() - 2), q = h.get(h.size() - 1);
                    if ((q[0] - a[0]) * (p[1] - a[1]) - (q[1] - a[1]) * (p[0] - a[0]) <= 0) h.remove(h.size() - 1); else break;
                }
                h.add(p);
            }
            h.remove(h.size() - 1);
        }
        double area = 0;
        for (int i = 0; i < h.size(); i++) { double[] a = h.get(i), q = h.get((i + 1) % h.size()); area += a[0] * q[1] - q[0] * a[1]; }
        return Math.abs(area) / 2;
    }

    static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    static double[] norm(double[] a) { double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); return new double[]{a[0] / l, a[1] / l, a[2] / l}; }
    static double[] slab(double[] o, double[] d, Bounds b) {
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
