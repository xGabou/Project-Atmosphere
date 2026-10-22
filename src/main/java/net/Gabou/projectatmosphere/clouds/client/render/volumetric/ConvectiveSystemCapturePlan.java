package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveSystemT260;
import net.Gabou.projectatmosphere.clouds.morphology.StormMorphologyPrototype.Bounds;

import java.util.ArrayList;
import java.util.List;

/** T260 camera planning (pure): shared by the native capture and the CPU gallery so both use identical cameras. */
public final class ConvectiveSystemCapturePlan {
    private ConvectiveSystemCapturePlan() {}
    public static final double FLOOR_Y = 650 - 1400;
    /** Photographic lens (options FOV) for whole-system views: near-orthographic like the T259 ground photographs. */
    public static final int PHOTO_OPTION_FOV = 45;

    /** Options FOV per view: close and below keep the player's own lens (in-game experience), others use the photo lens. */
    public static int optionFov(String view, int playerOption) {
        return view.equals("close") || view.equals("below") || view.equals("gameplay") ? playerOption : PHOTO_OPTION_FOV;
    }

    /** Fitted pose relative to the fixture origin; the complete system box frames every view of a seed. */
    public static double[] pose(String view, Bounds b, double effectiveFov, double aspect) { return pose(view, b, null, effectiveFov, aspect); }

    /**
     * Silhouette extremes of a recipe (6 axis extremes per macro bubble, inflated for lumps): the containment fit
     * uses these points, not the box corners, so the system fills the frame instead of the empty box diagonal.
     */
    public static float[] extremes(ConvectiveSystemT260.Recipe r) {
        List<float[]> pts = new ArrayList<>();
        for (var b : r.bubbles()) {
            if (b.tier() != 0 && b.tier() != 4) continue;
            float k = 1.2f;
            pts.add(new float[]{b.x() - b.rx() * k, b.y(), b.z()}); pts.add(new float[]{b.x() + b.rx() * k, b.y(), b.z()});
            pts.add(new float[]{b.x(), Math.max(0, b.y() - b.ry() * k), b.z()}); pts.add(new float[]{b.x(), b.y() + b.ry() * k, b.z()});
            pts.add(new float[]{b.x(), b.y(), b.z() - b.rz() * k}); pts.add(new float[]{b.x(), b.y(), b.z() + b.rz() * k});
            // Base-level horizontal corners (flat bases reach the bubble's full width at the condensation level).
            float yb = Math.max(0, b.y() - b.ry());
            for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2)
                pts.add(new float[]{b.x() + sx * b.rx() * .85f, yb, b.z() + sz * b.rz() * .85f});
        }
        float[] out = new float[pts.size() * 3];
        for (int i = 0; i < pts.size(); i++) System.arraycopy(pts.get(i), 0, out, i * 3, 3);
        return out;
    }

    public static double[] pose(String view, Bounds b, float[] points, double effectiveFov, double aspect) {
        if (points != null) {
            float[] e = {1e9f, 1e9f, 1e9f, -1e9f, -1e9f, -1e9f};
            for (int i = 0; i < points.length; i += 3) for (int a = 0; a < 3; a++) { e[a] = Math.min(e[a], points[i + a]); e[a + 3] = Math.max(e[a + 3], points[i + a]); }
            b = new Bounds(e[0], e[1], e[2], e[3], e[4], e[5]);
        }
        double cx = (b.minX() + b.maxX()) * .5, cz = (b.minZ() + b.maxZ()) * .5;
        double h = b.maxY() - Math.max(0, b.minY()), cy = Math.max(0, b.minY()) + .40 * h, d = 2200;
        // T260.6: a player standing on the ground 3 km away with the player's own lens (no fitting).
        if (view.equals("gameplay")) return new double[]{cx, FLOOR_Y + 80, cz + 3000, cx, cy, cz};
        double x = cx, y = cy, z = cz + d;
        switch (view) {
            case "opposite_side" -> z = cz - d;
            case "front" -> { x = cx - d; z = cz; }
            case "back" -> { x = cx + d; z = cz; }
            case "above" -> { y = cy + d; z = cz + .2 * d; }
            case "below" -> { y = FLOOR_Y; z = cz + .4 * d; }
            case "low_angle" -> { x = cx - .707 * d; y = FLOOR_Y; z = cz + .707 * d; }
            case "upwind" -> { x = cx - d; y = cy + .12 * d; z = cz; }
            case "downwind" -> { x = cx + d; y = cy + .12 * d; z = cz; }
            case "crosswind_left" -> { y = cy + .15 * d; z = cz - d; }
            case "crosswind_right" -> { y = cy + .15 * d; z = cz + d; }
            case "side", "close", "far" -> { }
            default -> throw new IllegalArgumentException("Unknown T260 view " + view);
        }
        double[] fitted = points == null ? StormMorphologyCapturePlan.fit(new double[]{x, y, z, cx, cy, cz}, b, b, 0, 0, 0, effectiveFov, aspect, FLOOR_Y)
                : fitPoints(new double[]{x, y, z, cx, cy, cz}, points, effectiveFov, aspect);
        if (view.equals("close") || view.equals("far")) {
            // close: inside the fitted distance (crops allowed, framing logged only); far: distant, ground-photo scale.
            double k = view.equals("close") ? .55 : 2.6;
            if (view.equals("close")) {
                // Never inside the cloud: stay at least 150 blocks outside the system's (horizontal) extent box.
                double fx = fitted[0] - fitted[3], fz = fitted[2] - fitted[5], fd = Math.hypot(fx, fz);
                double need = 0;
                for (int t = 0; t < 4000; t += 10) {
                    double px = fitted[3] + fx / fd * t, pz = fitted[5] + fz / fd * t;
                    if (px < b.minX() - 150 || px > b.maxX() + 150 || pz < b.minZ() - 150 || pz > b.maxZ() + 150) { need = t; break; }
                }
                k = Math.max(k, need / fd);
            }
            for (int a = 0; a < 3; a++) fitted[a] = fitted[a + 3] + (fitted[a] - fitted[a + 3]) * k;
            fitted[1] = Math.max(FLOOR_Y, fitted[1]);
        }
        return fitted;
    }

    /** Moves the camera along its view line until every point is inside 90 % of the frame (camera floor kept). */
    static double[] fitPoints(double[] pose, float[] pts, double fov, double aspect) {
        double[] out = pose.clone();
        out[1] = Math.max(FLOOR_Y, out[1]);
        for (int pass = 0; pass < 40; pass++) {
            double dx = out[3] - out[0], dy = out[4] - out[1], dz = out[5] - out[2], n = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double fx = dx / n, fy = dy / n, fz = dz / n, h = Math.hypot(fx, fz), rx = -fz / h, rz = fx / h;
            double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;
            double ty = Math.tan(Math.toRadians(fov) * .5) * .90, tx = ty * aspect, need = 0;
            for (int i = 0; i < pts.length; i += 3) {
                double vx = pts[i] - out[3], vy = pts[i + 1] - out[4], vz = pts[i + 2] - out[5], along = vx * fx + vy * fy + vz * fz;
                need = Math.max(need, Math.max(64 - along, Math.max(Math.abs(vx * rx + vz * rz) / tx - along, Math.abs(vx * ux + vy * uy + vz * uz) / ty - along)));
            }
            if (Math.abs(need - n) < 8) return out;
            double next = Math.max(need + 4, 200);
            out[0] = out[3] - fx * next; out[1] = Math.max(FLOOR_Y, out[4] - fy * next); out[2] = out[5] - fz * next;
        }
        return out;
    }

    /**
     * Native 4x close view: base camera kept, aim at the primary's actual crown (the highest primary macro bubble,
     * which includes the downwind lean; T260.1 aimed at the anchor column and the above view missed the crown).
     */
    public static double[] zoomPose(String view, Bounds b, double[] base, ConvectiveSystemT260.Recipe r) {
        double[] p = base.clone();
        float px = 0, pz = 0, py = (float) (b.maxY() * .85);
        if (r != null) {
            float best = -1e9f;
            for (var x : r.bubbles()) {
                if (r.components().get(x.comp()).role() != ConvectiveSystemT260.Role.PRIMARY || (x.tier() != 0 && x.tier() != 4)) continue;
                if (x.y() + x.ry() > best) { best = x.y() + x.ry(); px = x.x(); pz = x.z(); py = x.y(); }
            }
            py = Math.max(py, best * (view.equals("above") ? .85f : .80f));
        }
        p[3] = px;
        p[5] = pz;
        p[4] = py;
        return p;
    }

}
