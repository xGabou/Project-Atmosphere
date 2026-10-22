package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveSystemT260.Bubble;
import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveSystemT260.Component;
import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveSystemT260.Recipe;
import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveSystemT260.Role;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * T260.7 system-scale diversity (separate experiment "S", sandbox only): a seeded character applied to the frozen D
 * recipe WITHOUT a new layout. Existing cells keep their index (so their identity and every per-cell stream) and are
 * only rescaled about their own base or moved along their bearing from the primary; new daughter cells are appended
 * after them, each a copy of an existing secondary / associated cell (translated, uniformly scaled, mirrored - never
 * rotated, so D's exact lump-parent ratios hold), with its own lower-mass mounds and its own identity (new component
 * index, so new streams). Width therefore comes from more cells, never from stretching one primitive.
 * <ul>
 * <li>COMPACT: non-primary cells pulled in (0.8) and smaller (0.85);</li>
 * <li>DOMINANT: primary 1.12, every other cell 0.72 - one towering cell;</li>
 * <li>MULTICELL: secondaries 1.05 and 2-3 strong daughters in the widest azimuth gaps;</li>
 * <li>WIDE: 4-6 daughters on a wide ring (1.5-2.3 x the source distance) - occasional very wide system;</li>
 * <li>ASYMMETRIC: 2-4 daughters chained outward on one flank, decreasing in size (flanking-line growth).</li>
 * </ul>
 */
public final class SystemDiversityT2607 {
    private SystemDiversityT2607() {}

    public enum Character { STANDARD, COMPACT, DOMINANT, MULTICELL, WIDE, ASYMMETRIC }

    /** The seed's character (its own stream); weights favour ordinary systems, wide ones are occasional. */
    public static Character character(long seed) {
        float u = ConvectiveVolumeT2607.stream(seed, ConvectiveVolumeT2607.S_SYS, 0, 0).nextFloat();
        if (u < .18f) return Character.COMPACT;
        if (u < .38f) return Character.DOMINANT;
        if (u < .62f) return Character.MULTICELL;
        if (u < .78f) return Character.ASYMMETRIC;
        if (u < .90f) return Character.WIDE;
        return Character.STANDARD;
    }

    public static Recipe apply(Recipe r) { return apply(r, character(r.seed())); }

    public static Recipe apply(Recipe r, Character ch) {
        Random rnd = ConvectiveVolumeT2607.stream(r.seed(), ConvectiveVolumeT2607.S_SYS, 1, ch.ordinal());
        List<Component> comps = new ArrayList<>(r.components());
        List<Bubble> bubbles = new ArrayList<>(r.bubbles());
        int primary = 0;
        for (int c = 0; c < comps.size(); c++) if (comps.get(c).role() == Role.PRIMARY) primary = c;
        float px = comps.get(primary).anchorX(), pz = comps.get(primary).anchorZ();
        int lower = -1;
        for (int c = 0; c < comps.size(); c++) if (comps.get(c).role() == Role.LOWER_MASS) lower = c;
        // Owner of every lower-mass mound (and of its lumps: the owner of the parent mound), from the ORIGINAL layout.
        int[] own = new int[bubbles.size()];
        java.util.Arrays.fill(own, -1);
        if (lower >= 0) {
            var st = new ConvectiveVolumeT2607.Structure(r);
            for (int i = 0; i < bubbles.size(); i++) if (bubbles.get(i).comp() == lower && st.parentOf(i) < 0) own[i] = owner(bubbles.get(i), comps, bubbles, lower);
            for (int i = 0; i < bubbles.size(); i++) {
                int p = st.parentOf(i);
                while (p >= 0 && st.parentOf(p) >= 0) p = st.parentOf(p);
                if (bubbles.get(i).comp() == lower && p >= 0) own[i] = own[p];
            }
        }
        // 1. Rescale / move existing cells (identity kept).
        for (int c = 0; c < comps.size(); c++) {
            Component k = comps.get(c);
            if (k.role() == Role.LOWER_MASS) continue;
            boolean prim = c == primary;
            float s = 1, f = 1;
            switch (ch) {
                case COMPACT -> { if (!prim) { s = .85f; f = .8f; } }
                case DOMINANT -> s = prim ? 1.12f : .72f;
                case MULTICELL -> { if (k.role() == Role.SECONDARY) s = 1.05f; }
                default -> { }
            }
            if (s == 1 && f == 1) continue;
            float ax = k.anchorX(), az = k.anchorZ();
            float nx = px + (ax - px) * f, nz = pz + (az - pz) * f;
            for (int i = 0; i < bubbles.size(); i++) {
                Bubble b = bubbles.get(i);
                if (b.comp() == c) bubbles.set(i, xf(b, ax, az, nx, nz, s, false, false, c));
            }
            // Its lower-mass mounds move and scale with it.
            if (lower >= 0) for (int i = 0; i < bubbles.size(); i++) {
                Bubble b = bubbles.get(i);
                if (b.comp() == lower && own[i] == c) bubbles.set(i, xf(b, ax, az, nx, nz, s, false, false, lower));
            }
            comps.set(c, new Component(k.name(), k.role(), k.group(), k.blend(), nx, nz, k.top() * s));
        }
        // 2. Daughter cells.
        int n = switch (ch) {
            case MULTICELL -> 2 + rnd.nextInt(2);
            case WIDE -> 5 + rnd.nextInt(3);
            case ASYMMETRIC -> 2 + rnd.nextInt(3);
            default -> 0;
        };
        List<Integer> sources = new ArrayList<>(), strong = new ArrayList<>();
        for (int c = 0; c < r.components().size(); c++) {
            Role ro = comps.get(c).role();
            if (ro == Role.SECONDARY || ro == Role.ASSOCIATED) sources.add(c);
            if (ro == Role.SECONDARY) strong.add(c);
        }
        if (strong.isEmpty()) strong = sources;
        if (sources.isEmpty() || n == 0) return new Recipe(r.kind(), r.seed(), comps, bubbles, r.baseY(), r.baseBlend(), r.linkBlend(), r.primaryHeight(), r.windX(), r.windZ());
        float flank = (float) (rnd.nextFloat() * Math.PI * 2);
        float chainDist = 0;
        List<Bubble> macro = new ArrayList<>(), lumps = new ArrayList<>(), tier2 = new ArrayList<>();
        for (int d = 0; d < n; d++) {
            // Multicell / wide / flanking growth is carried by strong cells (secondaries); a few associated cumuli.
            List<Integer> pool = ch != Character.WIDE || rnd.nextFloat() < .7f ? strong : sources;
            int src = pool.get(rnd.nextInt(pool.size()));
            Component k = comps.get(src);
            float sd = (float) Math.hypot(k.anchorX() - px, k.anchorZ() - pz);
            float ext = extent(bubbles, src, k);
            float s, dist, az;
            switch (ch) {
                case WIDE -> { s = .7f + .35f * rnd.nextFloat(); dist = Math.max(sd, 2 * ext) * (1.7f + 1.0f * rnd.nextFloat()); az = gap(comps, px, pz, rnd); }
                case ASYMMETRIC -> {
                    s = Math.max(.55f, 1f - .14f * d) * (.9f + .2f * rnd.nextFloat());
                    chainDist = d == 0 ? Math.max(sd, 2 * ext) * 1.25f : chainDist + 1.55f * ext * s;
                    dist = chainDist; az = flank + (rnd.nextFloat() - .5f) * .5f;
                }
                default -> { s = .85f + .25f * rnd.nextFloat(); dist = Math.max(sd, 2 * ext) * (1.25f + .35f * rnd.nextFloat()); az = gap(comps, px, pz, rnd); }
            }
            boolean mx = rnd.nextBoolean(), mz = rnd.nextBoolean();
            float nx = px + (float) Math.cos(az) * dist, nz = pz + (float) Math.sin(az) * dist;
            int id = comps.size();
            comps.add(new Component(k.name() + "_daughter" + d, k.role(), k.group(), k.blend(), nx, nz, k.top() * s));
            int lid = -1;
            List<Bubble> mounds = new ArrayList<>();
            if (lower >= 0) for (int i = 0; i < bubbles.size(); i++) { Bubble b = bubbles.get(i); if (b.comp() == lower && own[i] == src && (b.tier() == 0 || b.tier() == 4)) mounds.add(b); }
            if (!mounds.isEmpty()) {
                lid = comps.size();
                comps.add(new Component("lower_mass_daughter" + d, Role.LOWER_MASS, k.group(), comps.get(lower).blend(), nx, nz, comps.get(lower).top()));
            }
            for (Bubble b : bubbles) {
                if (b.comp() != src) continue;
                Bubble q = xf(b, k.anchorX(), k.anchorZ(), nx, nz, s, mx, mz, id);
                (b.tier() == 0 || b.tier() == 4 ? macro : b.tier() == 2 ? tier2 : lumps).add(q);
            }
            for (Bubble b : mounds) macro.add(xf(b, k.anchorX(), k.anchorZ(), nx, nz, s, mx, mz, lid));
        }
        // Keep the recipe's section order (macro, tier 1/3 lumps, tier 2) so the T2606 anatomy parser applies.
        List<Bubble> out = new ArrayList<>();
        int i = 0;
        while (i < bubbles.size() && (bubbles.get(i).tier() == 0 || bubbles.get(i).tier() == 4)) out.add(bubbles.get(i++));
        out.addAll(macro);
        int j = i;
        while (j < bubbles.size() && bubbles.get(j).tier() != 2) out.add(bubbles.get(j++));
        out.addAll(lumps);
        while (j < bubbles.size()) out.add(bubbles.get(j++));
        out.addAll(tier2);
        return new Recipe(r.kind(), r.seed(), comps, out, r.baseY(), r.baseBlend(), r.linkBlend(), r.primaryHeight(), r.windX(), r.windZ());
    }

    /** Uniform scale about the cell base (anchor, y = 0), optional mirror of the offsets, then translation. */
    static Bubble xf(Bubble b, float ax, float az, float nx, float nz, float s, boolean mx, boolean mz, int comp) {
        float ox = (b.x() - ax) * s * (mx ? -1 : 1), oz = (b.z() - az) * s * (mz ? -1 : 1);
        return new Bubble(nx + ox, b.y() * s, nz + oz, b.rx() * s, b.ry() * s, b.rz() * s, comp, b.tier());
    }

    /** Nearest non-lower cell to a lower-mass mound (its owner). */
    static int owner(Bubble m, List<Component> comps, List<Bubble> bubbles, int lower) {
        int best = -1; float bd = Float.MAX_VALUE;
        for (int c = 0; c < comps.size(); c++) {
            if (c == lower || comps.get(c).role() == Role.LOWER_MASS) continue;
            float d = (float) Math.hypot(m.x() - comps.get(c).anchorX(), m.z() - comps.get(c).anchorZ());
            if (d < bd) { bd = d; best = c; }
        }
        return best;
    }

    static float extent(List<Bubble> bubbles, int c, Component k) {
        float e = 0;
        for (Bubble b : bubbles) if (b.comp() == c) e = Math.max(e, (float) Math.hypot(b.x() - k.anchorX(), b.z() - k.anchorZ()) + Math.max(b.rx(), b.rz()));
        return e;
    }

    /** Azimuth in the widest gap between existing cells around the primary (plus jitter). */
    static float gap(List<Component> comps, float px, float pz, Random rnd) {
        List<Double> a = new ArrayList<>();
        for (Component c : comps) {
            if (c.role() == Role.LOWER_MASS || c.role() == Role.PRIMARY) continue;
            a.add(Math.atan2(c.anchorZ() - pz, c.anchorX() - px));
        }
        if (a.isEmpty()) return (float) (rnd.nextFloat() * Math.PI * 2);
        a.sort(Double::compare);
        double best = -1, at = 0;
        for (int i = 0; i < a.size(); i++) {
            double lo = a.get(i), hi = i + 1 < a.size() ? a.get(i + 1) : a.get(0) + Math.PI * 2;
            if (hi - lo > best) { best = hi - lo; at = (lo + hi) / 2; }
        }
        return (float) (at + (rnd.nextFloat() - .5f) * .35f * best);
    }
}
