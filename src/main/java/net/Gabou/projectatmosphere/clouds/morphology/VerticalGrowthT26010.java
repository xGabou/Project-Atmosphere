package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.morphology.PersistentConvectionT2608.CellSpec;
import net.Gabou.projectatmosphere.clouds.morphology.PersistentConvectionT2608.Scenario;
import net.Gabou.projectatmosphere.clouds.morphology.SystemGrowthT2609.Env;

import java.util.Locale;
import java.util.Random;

import static net.Gabou.projectatmosphere.clouds.morphology.ConvectiveVolumeT2607.stream;

/**
 * T260.10 experimental prototype: environment-driven VERTICAL development (test sources only). Same seed environment,
 * persistent cells, initiation / decay, shear organisation and merging as T260.9 ({@link SystemGrowthT2609}); C4 cells.
 * <p>
 * What changes is how deep each cell can grow. The environment draws are T260.9's; from them a small set of EXPERIMENTAL
 * APPROXIMATIONS (not an atmospheric simulation, no sounding, no microphysics) derives a vertical profile:
 * <ul>
 *   <li>cloud base (LCL) above ground: higher in drier boundary layers (reported, and the ground of the player view;
 *   the geometry is relative to the base, so base altitude and cloud depth are separate);</li>
 *   <li>undilute neutral-buoyancy depth Dn (equilibrium level above the base): grows with instability, bounded by a
 *   tropopause;</li>
 *   <li>CAPE proxy 4000 instab^2 J/kg: sets updraft speed and so the growth rate of the top;</li>
 *   <li>glaciation level: where a rising top turns calvus (softened, glaciating crown).</li>
 * </ul>
 * Each cell is an ENTRAINING PLUME: its updraft radius comes from forcing / moisture and its own draw, and its achievable
 * depth is Dn exp(-lambda), lambda = K * (0.2 / R) * Dn * (0.3 + 0.7 dryness) (fractional entrainment 0.2 / R, mixing
 * with drier environmental air erodes buoyancy faster). So wide updrafts in moist environments reach the equilibrium
 * level; narrow ones, or dry mid-levels, stop as congestus; cells initiated through inhibition fail as shallow cumulus.
 * Tower width follows the updraft and widens with depth (plume spreading); the top rises at a speed set by the diluted
 * CAPE; mature cells decay after a lifetime set by the environment (shear prolongs it, inhibition shortens it).
 * Different cells of one system therefore reach very different heights at different times.
 */
public final class VerticalGrowthT26010 {
    private VerticalGrowthT26010() {}

    // T260.9's cell and initiation streams, same draw order: every cell keeps its T260.9 identity (failure, timing,
    // drift, lifetime and lean draws); only how deep it can grow is new.
    static final long S_CELL = SystemGrowthT2609.S_CELL, S_SPAWN = SystemGrowthT2609.S_SPAWN;
    /** Entrainment efficiency (dimensionless), fractional entrainment rate per metre = 0.2 / R. */
    static final float K_ENTRAIN = .13f, EPS_R = .2f;

    /** Experimental vertical profile of a seed's environment (metres; 1 block = 1 m). */
    public record Profile(float baseAgl, float tropAgl, float dn, float cape, float glacAgl) {
        /** Equilibrium-level ceiling above the base, with a small overshoot. */
        public float ceiling() { return Math.min(dn * 1.06f + 300, tropAgl - baseAgl + 400); }
        /** Glaciation (calvus) level above the base. */
        public float glac() { return glacAgl - baseAgl; }
        public String describe() {
            return String.format(Locale.ROOT, "baseAGL=%.0f tropAGL=%.0f Dn=%.0f CAPE=%.0f glacAboveBase=%.0f", baseAgl, tropAgl, dn, cape, glac());
        }
    }

    public static Profile profile(Env e) {
        float base = 400 + 1600 * (1 - e.moist());
        float trop = 11500 + 1500 * e.instab();
        float dn = (trop - base) * (.25f + .75f * (float) Math.pow(e.instab(), 1.3));
        float cape = 4000 * e.instab() * e.instab();
        float glac = 6800;
        return new Profile(base, trop, dn, cape, glac);
    }

    public static Scenario build(long seed, float tEnd) {
        Env e = SystemGrowthT2609.env(seed);
        Profile pr = profile(e);
        Scenario s = new Scenario("deep", seed);
        s.tEnd = tEnd;
        s.daughterDecay = true;
        // Initial cells: T260.9's initiation stream (same count, spacing and times).
        Random g = stream(seed, SystemGrowthT2609.S_INIT, 0, 0);
        int n0 = 1 + (int) (g.nextFloat() * (1 + 3 * e.moist()));
        float area = 700 + 2200 * e.moist();
        for (int i = 0; i < n0; i++) {
            float a = (float) (g.nextFloat() * Math.PI * 2), r = i == 0 ? 0 : area * (.4f + .6f * g.nextFloat());
            CellSpec c = s.add("C" + (s.cells.size() + 1));
            cell(e, pr, c, r * (float) Math.cos(a), r * (float) Math.sin(a), -8 + 6 * g.nextFloat(), 1);
        }
        int step = 0;
        for (float t = -6; t <= tEnd - 4 && s.cells.size() < e.maxCells(); t += 2, step++) {
            int n = s.cells.size();
            for (int i = 0; i < n && s.cells.size() < e.maxCells(); i++) {
                CellSpec p = s.cells.get(i);
                float age = t - p.birth;
                if (age < .5f * p.tauH || t > p.decay - 4) continue;
                Random q = stream(seed, S_SPAWN, i, step);
                // Outflow / gust-front initiation: towering cells (more than about a third of the profile) trigger more.
                float rate = (.16f + .32f * e.moist()) * (.45f + .55f * e.instab()) * (p.hPot > .35f * pr.dn() ? 1.25f : .7f);
                if (q.nextFloat() > rate) continue;
                float spread = 1.5f - 1.15f * e.shear();
                float ang = e.shearAz() + spread * (float) q.nextGaussian() + (e.shear() < .3f && q.nextFloat() < .3f ? (float) Math.PI : 0);
                float rp = Scenario.baseR(p, t), rc = 1.45f * (220 + 380 * e.moist() * q.nextFloat());
                float dist = (.8f + .8f * q.nextFloat()) * (rp + rc);
                float x = Scenario.cx(p, t) + dist * (float) Math.cos(ang), z = Scenario.cz(p, t) + dist * (float) Math.sin(ang);
                boolean free = true;
                for (CellSpec o : s.cells) {
                    if (o.birth > t || t > o.decay + o.tauD) continue;
                    float dx = x - Scenario.cx(o, t), dz = z - Scenario.cz(o, t);
                    if (Math.hypot(dx, dz) < .7f * (Scenario.baseR(o, t) + rc)) { free = false; break; }
                }
                if (!free) continue;
                float tb = t + 2 * q.nextFloat();
                CellSpec c = s.add("C" + (s.cells.size() + 1));
                float favour = .75f + .5f * q.nextFloat();
                cell(e, pr, c, x, z, tb, favour);
            }
        }
        return s.prepare();
    }

    /** One cell as an entraining plume in the seed's profile (T260.9's own stream and draw order: own identity). */
    static void cell(Env e, Profile pr, CellSpec c, float x, float z, float birth, float favour) {
        Random q = stream(c.seed, S_CELL, 0, 0);
        float u = q.nextFloat(), v = q.nextFloat(), w = q.nextFloat(), f = q.nextFloat(), d = q.nextFloat(), j = q.nextFloat(), k = q.nextFloat();
        float leanJ = q.nextFloat(), leanQ = q.nextFloat(), m = q.nextFloat();
        boolean fail = f < .55f * e.cin() + .1f;
        float dry = 1 - e.moist();
        // Updraft (thermal) radius: forcing and moisture organise wider updrafts; downshear inflow favours some cells.
        float rb = (220 + 380 * e.moist() * (.4f + .6f * v) + 300 * u * u) * favour;
        if (fail) rb *= .6f;
        // Entraining plume: dilution erodes buoyancy, faster for narrow updrafts and in dry mid-levels.
        float lam = K_ENTRAIN * EPS_R * pr.dn() * (.3f + .7f * dry);
        c.plumeDn = pr.dn(); c.plumeL = lam;
        float lambda = lam / rb;
        float depth = pr.dn() * (float) Math.exp(-lambda);
        // Inhibition: a failed initiation stays a shallow cumulus.
        if (fail) depth = Math.min(depth, 300 + 900 * w);
        c.hPot = Math.max(220, Math.min(pr.ceiling(), depth));
        c.hCeil = pr.ceiling();
        float deep = smooth(1200, 6000, c.hPot);
        c.deep = deep;
        // Cloud radius: ~1.45 updraft radius plus plume spreading with depth (wide towers, not pillars).
        c.rPot = 1.45f * rb + .09f * c.hPot;
        c.r0 = Math.min(.45f * c.rPot, 110 + .12f * c.rPot);
        c.h0 = 110 + 60 * deep;
        // Top ascent: updraft ~ 0.3 sqrt(2 CAPE_diluted), the top rises at ~0.45 of it (metres per time unit = minute).
        float wUp = .3f * (float) Math.sqrt(2 * pr.cape() * Math.exp(-lambda)) + 1.5f;
        float rise = .45f * wUp * 60;
        c.tauH = Math.max(8, .65f * c.hPot / rise) * (.85f + .3f * m);
        c.tauR = .8f * c.tauH + 6 + 6 * w;
        c.vigor = .12f * (e.instab() - .5f) + .08f * (v - .5f) - (fail ? .08f : 0);
        // Life cycle (T260.9's lifetime), never ending before the tower has grown (~1.6 tauH) plus a mature phase.
        float life = (28 + 30 * d) * (.65f + .6f * e.instab()) * (1 - .4f * e.cin()) * (fail ? .5f : 1);
        c.decay = birth + Math.max(life, 1.6f * c.tauH + (fail ? 4 : 10));
        c.tauD = 12 + 6 * deep;
        c.dissolve = true;
        c.birth = birth;
        // Glaciation: a top that rises past the glaciation level turns calvus over ~1.2 km.
        c.calH = Math.max(PersistentConvectionT2608.HCAL, pr.glac());
        c.calW = 1200;
        c.kScale = Math.max(1, Math.min(2, c.rPot / 600));
        c.vx = e.windX() + (j - .5f) * 2; c.vz = e.windZ() + (k - .5f) * 2;
        c.x0 = x - c.vx * birth; c.z0 = z - c.vz * birth;
        // Shear tilt: the tower leans downshear by a tilt angle (2-12 deg), so a deep tower's top is offset by km.
        float tilt = .04f + .18f * e.shear() * (.6f + .4f * leanQ);
        c.envLeanAz = e.shearAz() + (leanJ - .5f) * .7f;
        c.envLean = Math.min(2.2f, tilt * c.hPot / c.rPot);
    }

    static float smooth(float a, float b, float x) { float t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); }
}
