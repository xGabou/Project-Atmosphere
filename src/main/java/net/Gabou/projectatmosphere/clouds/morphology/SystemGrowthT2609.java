package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.morphology.PersistentConvectionT2608.CellSpec;
import net.Gabou.projectatmosphere.clouds.morphology.PersistentConvectionT2608.Scenario;

import java.util.Random;

import static net.Gabou.projectatmosphere.clouds.morphology.ConvectiveVolumeT2607.stream;

/**
 * T260.9 experimental prototype: storm-scale morphology from persistent, environment-constrained cell development
 * (test sources only). Uses the T260.8 C4 cell model unchanged (surface, crown, growth, merging).
 * <p>
 * A seed draws an ENVIRONMENT, not a shape:
 * <ul>
 *   <li><b>instability</b> (CAPE proxy): how deep cells can grow and how vigorous they are;</li>
 *   <li><b>shear</b> (strength and direction): growth direction of every tower and where new cells initiate (on the
 *   downshear flank of mature cells; strong shear organises them along a line, weak shear lets them cluster);</li>
 *   <li><b>moisture / forcing</b>: how many cells initiate, how often, and how broad they are;</li>
 *   <li><b>inhibition</b> (CIN proxy): how often new cells fail (stay shallow and decay) and how long cells live;</li>
 *   <li><b>steering wind</b>: shared drift of the whole system.</li>
 * </ul>
 * The system then DEVELOPS: 1-3 initial cells; every mature, still-growing cell may trigger a new cell on its
 * propagation flank (deterministic per cell and time step); cells live, mature and decay with lifetimes set by the
 * environment. Width comes from many cells attaching (T260.8 pair merging), never from stretching one cell. Every cell
 * is a persistent T260.8 cell with its own identity, seed and growth history; same seed = same system at every time.
 */
public final class SystemGrowthT2609 {
    private SystemGrowthT2609() {}

    static final long S_ENV = 0xE4F12609L, S_INIT = 0x1A172609L, S_SPAWN = 0x5BA42609L, S_CELL = 0xCE112609L;

    /** The seed's environment (all in [0, 1] except angles and wind in blocks per time unit). */
    public record Env(float instab, float shear, float shearAz, float moist, float cin, float windX, float windZ, int maxCells) {
        public String describe() {
            return String.format(java.util.Locale.ROOT, "instab=%.2f shear=%.2f@%.0fdeg moist=%.2f cin=%.2f maxCells=%d", instab, shear, Math.toDegrees(shearAz), moist, cin, maxCells);
        }
    }

    public static Env env(long seed) {
        Random g = stream(seed, S_ENV, 0, 0);
        float instab = .15f + .85f * g.nextFloat(), shear = g.nextFloat(), shearAz = (float) (g.nextFloat() * Math.PI * 2);
        float moist = .1f + .9f * g.nextFloat(), cin = g.nextFloat() * g.nextFloat();
        float ws = 2 + 4 * g.nextFloat(), wa = shearAz + (g.nextFloat() - .5f) * 1.2f;
        int maxCells = 4 + Math.round(14 * moist * (.45f + .55f * instab));
        return new Env(instab, shear, shearAz, moist, cin, ws * (float) Math.cos(wa), ws * (float) Math.sin(wa), maxCells);
    }

    public static Scenario build(long seed, float tEnd) {
        Env e = env(seed);
        Scenario s = new Scenario("system", seed);
        s.tEnd = tEnd;
        s.daughterDecay = true;
        Random g = stream(seed, S_INIT, 0, 0);
        int n0 = 1 + (int) (g.nextFloat() * (1 + 3 * e.moist()));
        float area = 700 + 2200 * e.moist();
        for (int i = 0; i < n0; i++) {
            float a = (float) (g.nextFloat() * Math.PI * 2), r = i == 0 ? 0 : area * (.4f + .6f * g.nextFloat());
            CellSpec c = s.add("C" + (s.cells.size() + 1));
            cell(e, c, r * (float) Math.cos(a), r * (float) Math.sin(a), -8 + 6 * g.nextFloat(), 1);
        }
        // Development: mature, still-active cells trigger new cells on their propagation flank (time step 2).
        int step = 0;
        for (float t = -6; t <= tEnd - 4 && s.cells.size() < e.maxCells(); t += 2, step++) {
            int n = s.cells.size();
            for (int i = 0; i < n && s.cells.size() < e.maxCells(); i++) {
                CellSpec p = s.cells.get(i);
                float age = t - p.birth;
                if (age < .5f * p.tauH || t > p.decay - 4) continue;
                Random q = stream(seed, S_SPAWN, i, step);
                float rate = (.16f + .32f * e.moist()) * (.45f + .55f * e.instab()) * (p.hPot > 500 ? 1.25f : .7f);
                if (q.nextFloat() > rate) continue;
                // Propagation flank: downshear; spread narrows with shear (line organisation), weak shear also upshear.
                float spread = 1.5f - 1.15f * e.shear();
                float ang = e.shearAz() + spread * (float) q.nextGaussian() + (e.shear() < .3f && q.nextFloat() < .3f ? (float) Math.PI : 0);
                float rp = Scenario.baseR(p, t), rc = 200 + 200 * e.moist() * q.nextFloat();
                float dist = (.8f + .8f * q.nextFloat()) * (rp + rc);
                float x = Scenario.cx(p, t) + dist * (float) Math.cos(ang), z = Scenario.cz(p, t) + dist * (float) Math.sin(ang);
                // Never on top of a live cell: new growth starts beside existing growth (it may later attach).
                boolean free = true;
                for (CellSpec o : s.cells) {
                    if (o.birth > t || t > o.decay + o.tauD) continue;
                    float dx = x - Scenario.cx(o, t), dz = z - Scenario.cz(o, t);
                    if (Math.hypot(dx, dz) < .7f * (Scenario.baseR(o, t) + rc)) { free = false; break; }
                }
                if (!free) continue;
                float tb = t + 2 * q.nextFloat();
                CellSpec c = s.add("C" + (s.cells.size() + 1));
                float favour = .75f + .5f * q.nextFloat(); // downshear inflow: new cells can outgrow their parent
                cell(e, c, x, z, tb, favour);
            }
        }
        return s.prepare();
    }

    /** One cell's growth potential from the environment and its own stream (own identity); (x, z) = position at birth. */
    static void cell(Env e, CellSpec c, float x, float z, float birth, float favour) {
        Random q = stream(c.seed, S_CELL, 0, 0);
        float u = q.nextFloat(), v = q.nextFloat(), w = q.nextFloat(), f = q.nextFloat(), d = q.nextFloat(), j = q.nextFloat(), k = q.nextFloat();
        boolean fail = f < .55f * e.cin() + .1f;
        float depth = (float) Math.pow(u, .85f) * favour;
        c.hPot = Math.max(240, Math.min(1300, 300 + 1000 * e.instab() * depth));
        if (fail) c.hPot = Math.max(220, .45f * c.hPot);
        c.rPot = 200 + 230 * e.moist() * (.5f + .5f * v) + 110 * w * (1 - .5f * e.instab());
        c.tauH = 9 + 13 * (c.hPot / 1250f); c.tauR = 12 + 8 * w;
        c.vigor = .12f * (e.instab() - .5f) + .08f * (v - .5f) - (fail ? .08f : 0);
        float life = (28 + 30 * d) * (.65f + .6f * e.instab()) * (1 - .4f * e.cin()) * (fail ? .5f : 1);
        c.decay = birth + life; c.tauD = 12;
        c.dissolve = true;
        c.birth = birth;
        c.vx = e.windX() + (j - .5f) * 2; c.vz = e.windZ() + (k - .5f) * 2;
        c.x0 = x - c.vx * birth; c.z0 = z - c.vz * birth;
        c.envLeanAz = e.shearAz() + (q.nextFloat() - .5f) * .7f;
        c.envLean = .08f + .45f * e.shear() * (.6f + .4f * q.nextFloat());
    }
}
