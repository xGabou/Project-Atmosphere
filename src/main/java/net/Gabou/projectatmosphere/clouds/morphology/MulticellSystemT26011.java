package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.morphology.PersistentConvectionT2608.Scenario;
import net.Gabou.projectatmosphere.clouds.morphology.SystemGrowthT2609.Env;
import net.Gabou.projectatmosphere.clouds.morphology.VerticalGrowthT26010.Profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static net.Gabou.projectatmosphere.clouds.morphology.ConvectiveVolumeT2607.stream;

/**
 * T260.11 experimental prototype (never production): MINECRAFT-SCALE MULTICELL ORGANISATION. A system is a set of
 * persistent CONVECTIVE GROUPS; every group is an unchanged T260.10 cell cluster ({@link VerticalGrowthT26010}: same
 * cells, towers, plume depth, merging) built in its own slightly perturbed local environment. What is new is only how
 * groups are initiated, placed and timed:
 * <ul>
 *   <li>the seed's T260.9 environment (identical to T260.10 for the same seed) sets a forcing extent: weak, dry systems
 *   stay a few groups wide, vigorous moist ones organise over tens of km (rarely ~60 km);</li>
 *   <li>propagation: every mature, still-active group may trigger a new group on its outflow boundary. Strong shear
 *   aligns the triggering along a boundary across the shear (a band of groups with a downshear push); weak shear
 *   scatters it (clusters). A per-system bias makes one flank more active (asymmetry);</li>
 *   <li>gaps are kept, not filled: a new group must start beyond 0.75 of the group spacing from every other group,
 *   and initiation stops at the forcing extent;</li>
 *   <li>groups started through strong inhibition fail as shallow cumulus groups; older groups (upwind / first) weaken
 *   and dissipate while new ones develop, so developing, mature and weakening regions coexist.</li>
 * </ul>
 * Coordinates are metres in the frame drifting with the steering wind (the groups share the wind). Deterministic: the
 * same seed and system time give the same groups, cells and states. Groups keep their identity (seed, birth, position)
 * at every system time.
 */
public final class MulticellSystemT26011 {
    private MulticellSystemT26011() {}

    static final long S_ORG = 0x0A6E2611L, S_GSPAWN = 0x65A72611L, S_GENV = 0x6E4F2611L, S_GSEED = 0x65EE2611L;
    /** Group build horizon (T260.10's), system horizon (minutes). */
    public static final float GROUP_T_END = 46, T_END = 150;

    public record Group(int index, long seed, Env env, float birth, float x, float z, int parent, boolean failed) {
        /** Group-local time at system time t. */
        public float local(float t) { return t - birth; }
    }

    public record Plan(long seed, Env env, Profile profile, float extent, float spacing, float lineAz, float lineness, float flankBias, List<Group> groups) {
        public String describe() {
            return String.format(Locale.ROOT, "%s extent=%.1fkm spacing=%.1fkm lineAz=%.0fdeg lineness=%.2f flankBias=%.2f groups=%d",
                    env.describe(), extent / 1000, spacing / 1000, Math.toDegrees(lineAz), lineness, flankBias, groups.size());
        }
    }

    public static Plan plan(long seed) {
        Env e = SystemGrowthT2609.env(seed);
        Profile pr = VerticalGrowthT26010.profile(e);
        Random g = stream(seed, S_ORG, 0, 0);
        // Forcing extent: vigour = instability x moisture supply. ~9 km (single group / small multicell) to ~35 km,
        // rarely ~60 km for very vigorous, moist environments.
        float vigour = e.instab() * (.45f + .55f * e.moist());
        float u = g.nextFloat(), w = g.nextFloat();
        float extent = 9000 + 26000 * vigour * (.55f + .45f * u);
        if (w > .9f && vigour > .55f) extent += 25000 * (w - .9f) / .1f;
        // Organisation: the outflow boundary lies across the shear; how strongly triggering follows it rises with shear.
        float lineAz = e.shearAz() + (float) Math.PI / 2 + (g.nextFloat() - .5f) * .9f;
        float lineness = VerticalGrowthT26010.smooth(.15f, .75f, e.shear());
        float spacing = 4600 + 3800 * g.nextFloat();
        float flankBias = .5f + .38f * g.nextFloat(); // probability a trigger goes to the favoured flank
        int maxGroups = Math.max(1, Math.min(18, Math.round(1.6f * extent / spacing)));
        List<Group> gs = new ArrayList<>();
        gs.add(group(seed, e, 0, -14, 0, 0, -1, false));
        int step = 0;
        for (float t = -10; t <= T_END - 30 && gs.size() < maxGroups; t += 3, step++) {
            int n = gs.size();
            for (int i = 0; i < n && gs.size() < maxGroups; i++) {
                Group p = gs.get(i);
                float age = t - p.birth;
                if (p.failed || age < 10 || age > 48) continue;
                Random q = stream(seed, S_GSPAWN, i, step);
                float rate = (.12f + .30f * e.moist()) * (.45f + .55f * e.instab());
                if (q.nextFloat() > rate) continue;
                // Direction: along the boundary (favoured flank more often) blended with a free direction by lineness,
                // plus a downshear push.
                float side = q.nextFloat() < flankBias ? 1 : -1;
                float la = lineAz + (side < 0 ? (float) Math.PI : 0) + (q.nextFloat() - .5f) * .6f;
                float fa = (float) (q.nextFloat() * Math.PI * 2);
                float dx = lineness * (float) Math.cos(la) + (1 - lineness) * (float) Math.cos(fa) + .35f * e.shear() * (float) Math.cos(e.shearAz());
                float dz = lineness * (float) Math.sin(la) + (1 - lineness) * (float) Math.sin(fa) + .35f * e.shear() * (float) Math.sin(e.shearAz());
                float len = (float) Math.hypot(dx, dz);
                if (len < 1e-3f) continue;
                float dist = spacing * (.75f + .7f * q.nextFloat());
                float x = p.x + dist * dx / len, z = p.z + dist * dz / len;
                if (Math.hypot(x, z) > .5f * extent) continue; // beyond the forcing
                boolean free = true;
                for (Group o : gs) if (Math.hypot(x - o.x, z - o.z) < .75f * spacing) { free = false; break; }
                if (!free) continue; // natural gaps: never squeeze a group between others
                boolean failed = q.nextFloat() < .45f * e.cin();
                gs.add(group(seed, e, gs.size(), t + 3 * q.nextFloat(), x, z, i, failed));
            }
        }
        return new Plan(seed, e, pr, extent, spacing, lineAz, lineness, flankBias, gs);
    }

    /** A group's local environment: the system's, perturbed (own instability / moisture / inhibition, small size). */
    static Group group(long seed, Env e, int index, float birth, float x, float z, int parent, boolean failed) {
        Random q = stream(seed, S_GENV, index, 0);
        float instab = clamp(e.instab() + .14f * (float) q.nextGaussian(), .15f, 1);
        float moist = clamp(e.moist() + .12f * (float) q.nextGaussian(), .1f, 1);
        float cin = clamp(e.cin() + .12f * (float) q.nextGaussian() + (failed ? .5f : 0), 0, 1);
        int maxCells = 2 + Math.round(6 * moist * (.4f + .6f * q.nextFloat()));
        Env ge = new Env(instab, e.shear(), e.shearAz(), moist, cin, e.windX(), e.windZ(), maxCells);
        long gseed = PersistentConvectionT2608.mix(seed ^ S_GSEED, index + 1);
        return new Group(index, gseed, ge, birth, x, z, parent, failed);
    }

    /** The unchanged T260.10 cell cluster of a group (its own seed and environment, local frame). */
    public static Scenario scenario(Group g) {
        PersistentConvectionT2608.VARIANT = 4;
        return VerticalGrowthT26010.build(g.seed(), g.env(), GROUP_T_END);
    }

    /**
     * T260.12: the group's T260.10 cluster plus its connection cells (identical cluster when it has none). The
     * attachment timeline is extended only when connection cells exist, so they can merge with the towers.
     */
    public static Scenario scenario(Group g, Plan p, java.util.List<ConnectionsT26012.Connection> cs) {
        PersistentConvectionT2608.VARIANT = 4;
        return VerticalGrowthT26010.build(g.seed(), g.env(), GROUP_T_END, s -> {
            float last = ConnectionsT26012.addTo(s, p, g, cs);
            if (!Float.isNaN(last)) s.tEnd = Math.min(T_END, Math.max(s.tEnd, last + 6));
        });
    }

    static float clamp(float v, float a, float b) { return Math.max(a, Math.min(b, v)); }
}
