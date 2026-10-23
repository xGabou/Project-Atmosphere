package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.morphology.MulticellSystemT26011.Group;
import net.Gabou.projectatmosphere.clouds.morphology.MulticellSystemT26011.Plan;
import net.Gabou.projectatmosphere.clouds.morphology.PersistentConvectionT2608.CellSpec;
import net.Gabou.projectatmosphere.clouds.morphology.PersistentConvectionT2608.Scenario;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static net.Gabou.projectatmosphere.clouds.morphology.ConvectiveVolumeT2607.stream;

/**
 * T260.12 experimental prototype (never production): ORGANIC CONNECTIONS between neighbouring T260.11 groups.
 * <p>
 * A connection is a persistent, seed-driven entity of a PAIR of groups. Its strength A(t) is integrated over system
 * time (it grows while the pair's formation potential exceeds the pair's own threshold and decays otherwise), so it
 * develops gradually, never appears at once and is the same at every stage that is asked for. The potential is the
 * product of:
 * <ul>
 *   <li>proximity: the gap between the groups' footprints (estimated radii) against the group spacing;</li>
 *   <li>cloud-base compatibility: the groups' own condensation levels (from their local moisture) must be close;</li>
 *   <li>relative maturity: both groups mature (developing and weakening groups feed connections less, dissipating
 *   ones not at all) - so connections around weakening groups dissipate;</li>
 *   <li>moisture: moist boundary layers keep lower cloud between towers;</li>
 *   <li>shear: neighbours along the outflow boundary (across the shear) connect more readily than neighbours lined up
 *   with the shear, and the lower mass is displaced downshear;</li>
 *   <li>overlap / merging state: overlapping footprints form a compact LOCALISED MERGER instead of a long bridge;
 *   shallow (failed) groups connect weakly.</li>
 * </ul>
 * Geometry: a connection is NOT a shape - it is a set of 2-6 shallow, broad persistent C4 cells (the same cells as every
 * tower, so the same surface) scattered along and beside the line between the groups, with their own sizes, offsets and
 * activation levels. A cell initiates when A(t) first reaches its level (cells nearest a group first, spreading into
 * the gap), and dissipates when A(t) falls well below it. Low A leaves a partial bridge or nothing; many pairs never
 * connect (the threshold is seeded per pair), so large openings remain. The cells are added to BOTH neighbouring groups'
 * scenarios (identical identity and system position), where T260.8 pair merging blends them into the towers' lower
 * masses; each group stays a separate region volume.
 */
public final class ConnectionsT26012 {
    private ConnectionsT26012() {}

    static final long S_PAIR = 0xC0EE2612L, S_CCELL = 0xCCE12612L;
    static final float DT = .5f, T_GROW = 12, T_DECAY = 18;

    /** One connection cell in the system frame (metres; steering-wind frame) with its system-time life. */
    public record CellPlan(long id, long seed, float x, float z, float hPot, float rPot, float level, float birth, float decay) {}

    public record Connection(int a, int b, long seed, float threshold, boolean merger, float[] strength, List<CellPlan> cells) {
        /** Strength at system time t. */
        public float at(float t) {
            int k = Math.max(0, Math.min(strength.length - 1, Math.round((t - T0) / DT)));
            return strength[k];
        }
    }

    static final float T0 = -14;

    /** Estimated footprint radius of a group (metres) from its cell budget. */
    static float radius(Group g) { return 2000 + 500 * g.env().maxCells(); }

    /** The group's own condensation level relative to the system's (metres; damped so neighbours stay comparable). */
    public static float baseOffset(Plan p, Group g) {
        float lclG = 400 + 1600 * (1 - g.env().moist()), lclS = p.profile().baseAgl();
        return .6f * (lclG - lclS);
    }

    /** Group maturity over its local time (0 before ~6 min, 1 when mature, back to 0 while weakening / dissipating). */
    static float maturity(Group g, float tau) {
        float m = VerticalGrowthT26010.smooth(6, 20, tau) * (1 - VerticalGrowthT26010.smooth(55, 88, tau));
        return g.failed() ? .45f * m : m;
    }

    public static List<Connection> plan(Plan p) {
        List<Connection> out = new ArrayList<>();
        var e = p.env();
        int n = p.groups().size();
        int steps = Math.round((MulticellSystemT26011.T_END - T0) / DT) + 1;
        // Every group's own cells (unchanged T260.10 clusters): footprints in the steering-wind frame over time.
        List<List<CellSpec>> cells = new ArrayList<>();
        for (Group g : p.groups()) cells.add(MulticellSystemT26011.scenario(g).cells);
        for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) {
            Group a = p.groups().get(i), b = p.groups().get(j);
            if (Math.hypot(b.x() - a.x(), b.z() - a.z()) > 3.2f * p.spacing()) continue; // never neighbours
            // Footprint gap over time: closest pair of live cells (edge to edge), and where it is.
            float[] gap = new float[steps];
            float[][] near = new float[steps][];
            for (int k = 0; k < steps; k++) {
                float t = T0 + k * DT;
                gap[k] = Float.MAX_VALUE;
                for (CellSpec ca : cells.get(i)) {
                    float ta = t - a.birth();
                    if (ta < ca.birth || ta > ca.decay + ca.tauD) continue;
                    float ax = a.x() + ca.x0 + (ca.vx - e.windX()) * ta, az = a.z() + ca.z0 + (ca.vz - e.windZ()) * ta, ra = PersistentConvectionT2608.Scenario.baseR(ca, ta);
                    for (CellSpec cb : cells.get(j)) {
                        float tb = t - b.birth();
                        if (tb < cb.birth || tb > cb.decay + cb.tauD) continue;
                        float bx = b.x() + cb.x0 + (cb.vx - e.windX()) * tb, bz = b.z() + cb.z0 + (cb.vz - e.windZ()) * tb, rb = PersistentConvectionT2608.Scenario.baseR(cb, tb);
                        float g = (float) Math.hypot(bx - ax, bz - az) - ra - rb;
                        if (g < gap[k]) { gap[k] = g; near[k] = new float[]{ax, az, ra, bx, bz, rb}; }
                    }
                }
            }
            Random q = stream(p.seed(), S_PAIR, i, j);
            float threshold = .22f + .38f * q.nextFloat();
            float base = 1 - VerticalGrowthT26010.smooth(70, 300, Math.abs(baseOffset(p, a) - baseOffset(p, b)) / .6f);
            float moist = .35f + .65f * .5f * (a.env().moist() + b.env().moist());
            float axis = (float) Math.atan2(b.z() - a.z(), b.x() - a.x());
            float shear = 1 - .4f * e.shear() * Math.abs((float) Math.cos(axis - e.shearAz()));
            float pot0 = base * moist * shear * (.85f + .3f * q.nextFloat());
            float[] s = new float[steps];
            float A = 0;
            int formed = -1;
            for (int k = 0; k < steps; k++) {
                float t = T0 + k * DT;
                float prox = gap[k] == Float.MAX_VALUE ? 0 : 1 - VerticalGrowthT26010.smooth(-.1f * p.spacing(), .75f * p.spacing(), gap[k]);
                float m = (float) Math.pow(Math.min(maturity(a, t - a.birth()), maturity(b, t - b.birth())), .7);
                float pot = pot0 * prox * m;
                A += DT * (pot > threshold ? (pot - threshold) / (1 - threshold) / T_GROW : -1 / T_DECAY);
                A = Math.max(0, Math.min(1, A));
                s[k] = A;
                if (formed < 0 && A > .1f) formed = k;
            }
            if (formed < 0) continue; // this pair never connects
            // Geometry is fixed where the groups were nearest when the connection began (then drifts with the wind).
            float[] nr = near[formed];
            float dx = nr[3] - nr[0], dz = nr[4] - nr[1], d = Math.max(1, (float) Math.hypot(dx, dz));
            float ux = dx / d, uz = dz / d, vx = -uz, vz = ux;
            boolean merger = gap[formed] < 0;
            float s0 = merger ? .5f * (d + nr[2] - nr[5]) - .3f * Math.min(nr[2], nr[5]) : .55f * nr[2];
            float s1 = merger ? s0 + .6f * Math.min(nr[2], nr[5]) : d - .55f * nr[5];
            List<CellPlan> cps = new ArrayList<>();
            long cseed = PersistentConvectionT2608.mix(p.seed() ^ S_PAIR, (long) i * 64 + j + 1);
            Random g = stream(cseed, S_CCELL, 0, 0);
            float span = Math.max(600, s1 - s0);
            int nc = merger ? 2 + (int) (g.nextFloat() * 2) : 3 + (int) (g.nextFloat() * Math.min(4.99f, 1 + 2.5f * span / 3000));
            for (int c = 0; c < nc; c++) {
                float f = (c + .2f + .6f * g.nextFloat()) / nc;
                float along = s0 + f * (s1 - s0);
                float lat = (float) g.nextGaussian() * .25f * span;
                float down = .12f * e.shear() * Math.min(nr[2], nr[5]);
                float x = nr[0] + ux * along + vx * lat + down * (float) Math.cos(e.shearAz());
                float z = nr[1] + uz * along + vz * lat + down * (float) Math.sin(e.shearAz());
                // Lower masses scaled to the cells they join (a bridge between km-wide tower bases is itself km wide),
                // shallow relative to the towers.
                float hPot = (500 + 1100 * g.nextFloat()) * (.6f + .4f * moist);
                float rPot = Math.max(800, Math.min(2600, Math.min(nr[2], nr[5]) * (.45f + .45f * g.nextFloat())));
                // Cells nearest a group first: the lower mass spreads from the towers into the gap.
                float edge = Math.min(f, 1 - f);
                float level = Math.min(.95f, .12f + .9f * edge + .15f * g.nextFloat());
                float birth = Float.NaN, decay = Float.NaN;
                for (int k = 0; k < steps; k++) {
                    float t = T0 + k * DT;
                    if (Float.isNaN(birth) && s[k] >= level) birth = t;
                    else if (!Float.isNaN(birth) && s[k] < .55f * level) { decay = t; break; }
                }
                if (Float.isNaN(birth)) continue; // this part of the bridge never forms
                long id = PersistentConvectionT2608.mix(cseed, c + 1);
                cps.add(new CellPlan(id, PersistentConvectionT2608.mix(id, 0x5EEDL), x, z, hPot, rPot, level, birth, Float.isNaN(decay) ? 1e9f : decay));
            }
            if (!cps.isEmpty()) out.add(new Connection(i, j, cseed, threshold, merger, s, cps));
        }
        return out;
    }

    /**
     * Adds every connection cell of group g's connections to its scenario (before prepare): identity and system
     * position are the connection's, so both neighbouring regions hold the same cell. Returns the latest local time a
     * connection cell exists (to extend the scenario's attachment timeline), or NaN when none.
     */
    static float addTo(Scenario s, Plan p, Group g, List<Connection> cs) {
        var e = p.env();
        float last = Float.NaN;
        for (Connection c : cs) {
            if (c.a() != g.index() && c.b() != g.index()) continue;
            for (CellPlan cp : c.cells()) {
                CellSpec cell = new CellSpec("X" + c.a() + "-" + c.b() + "." + cp.id() % 1000, cp.id(), cp.seed());
                Random q = stream(cp.seed(), S_CCELL, 1, 0);
                cell.hPot = cp.hPot();
                cell.rPot = cp.rPot();
                cell.r0 = Math.min(.45f * cell.rPot, 110 + .12f * cell.rPot);
                cell.h0 = 90;
                cell.tauH = 9 + 6 * q.nextFloat();
                cell.tauR = .8f * cell.tauH + 6 + 6 * q.nextFloat();
                cell.vigor = -.04f - .05f * q.nextFloat(); // lower cloud: quieter than the towers
                cell.birth = cp.birth() - g.birth();
                cell.decay = cp.decay() >= 1e8f ? 1e9f : cp.decay() - g.birth();
                cell.tauD = 14;
                cell.dissolve = true;
                cell.kScale = Math.max(1, Math.min(2, cell.rPot / 600));
                cell.vx = e.windX(); cell.vz = e.windZ();
                cell.x0 = cp.x() - g.x(); cell.z0 = cp.z() - g.z();
                cell.envLeanAz = e.shearAz();
                cell.envLean = Math.min(.6f, .05f * e.shear() * cell.hPot / cell.rPot * 4);
                s.cells.add(cell);
                last = Float.isNaN(last) ? cell.birth : Math.max(last, Math.min(cell.decay, cell.birth + 120));
            }
        }
        return last;
    }
}
