package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import java.util.ArrayList;
import java.util.List;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
import net.Gabou.projectatmosphere.clouds.simulation.CloudGroupSpawner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Temporary, opt-in evidence capture for the 2026-10-08 cloud review. */
final class CloudReviewCapture {
    static final String[] TYPES = {"vapor_cluster", "cumulus_humilis", "cumulus_mediocris",
            "cumulus_congestus", "cumulonimbus_calvus", "cumulonimbus_capillatus", "supercell",
            "stratus_nebulosus", "stratocumulus", "nimbostratus", "cirrus"};
    static final List<String[]> SHOTS = new ArrayList<>();
    private static final CloudRegionState[] REGIONS = new CloudRegionState[TYPES.length];
    static {
        for (String type : TYPES) {
            SHOTS.add(new String[] {type, "SPAWN"});
            for (String view : List.of("side", "opposite_side", "front", "above", "below", "low_angle", "far")) {
                SHOTS.add(new String[] {type, view});
            }
            if (type.startsWith("cumulonimbus") || type.equals("supercell")) SHOTS.add(new String[] {type, "anvil"});
            if (type.equals("stratus_nebulosus") || type.equals("stratocumulus") || type.equals("nimbostratus") || type.equals("cirrus")) SHOTS.add(new String[] {type, "sheet_extent"});
        }
    }
    static boolean enabled() { return System.getenv("PA_CLOUD_REVIEW") != null; }
    static boolean spawning(int index) { return SHOTS.get(index)[1].equals("SPAWN"); }
    private static double ground(double x, double z) {
        var level = Minecraft.getInstance().level;
        return level == null ? 64 : Math.max(64, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int)Math.floor(x), (int)Math.floor(z)));
    }
    static double[] move(int index) {
        String type = SHOTS.get(index)[0], view = SHOTS.get(index)[1];
        int ti = List.of(TYPES).indexOf(type);
        if (view.equals("SPAWN")) {
            var server = Minecraft.getInstance().getSingleplayerServer();
            var fix = StormPerformanceBaseline.suiteFixture();
            if (server == null || fix == null) throw new IllegalStateException("Review needs integrated fixture");
            double x = fix.centerX() + 9000, z = fix.centerZ() + 9000 * ti;
            server.execute(() -> {
                server.overworld().getRandom().setSeed(20261008L + 7919L * ti);
                REGIONS[ti] = CloudGroupSpawner.spawnRequestedCloud(server.overworld(), BlockPos.containing(x, 0, z), type);
                ProjectAtmosphere.LOGGER.info("REVIEW_SPAWN type={} region={} seed={}", type, REGIONS[ti] == null ? "FAILED" : REGIONS[ti].getRegionId(), 20261008L + 7919L * ti);
            });
            return new double[] {x + 1500, 350, z, x, 300, z};
        }
        var region = REGIONS[ti];
        if (region == null) throw new IllegalStateException("Missing review region " + type);
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        double base = Double.MAX_VALUE, top = -Double.MAX_VALUE;
        for (var c : region.getClusters()) {
            minX = Math.min(minX, c.getCenter().x - c.getRadius()); maxX = Math.max(maxX, c.getCenter().x + c.getRadius());
            minZ = Math.min(minZ, c.getCenter().z - c.getRadius()); maxZ = Math.max(maxZ, c.getCenter().z + c.getRadius());
            base = Math.min(base, c.getBaseY()); top = Math.max(top, c.getTopY());
        }
        double cx = (minX + maxX) / 2, cz = (minZ + maxZ) / 2;
        double width = maxX - minX, depth = maxZ - minZ, ext = Math.max(width, depth) / 2, height = top - base;
        double d = Math.max(.55 * ext + .8 * height, ext + 25) + 10;
        double x = cx, z = cz + d, y = (base + top) / 2, ax = cx, ay = y, az = cz;
        switch (view) {
            case "opposite_side" -> z = cz - d;
            case "front" -> { x = cx - d; z = cz; }
            case "above" -> { x = cx - .3 * d; z = cz; y = top + .6 * d; }
            case "below" -> {
                x = cx + (height > 300 ? .85 : .1) * ext; z = cz + .2 * ext;
                double g = ground(x, z); y = Math.max(g + 12, Math.min(base - 15, g + .5 * Math.max(0, base - g)));
                ay = base + .25 * height;
            }
            case "low_angle" -> { x = cx - .707 * d; z = cz + .707 * d; y = ground(x, z) + 40; }
            case "far" -> { x = cx + .3 * d; z = cz + 2 * d; }
            case "anvil" -> { z = cz + 1.2 * d; y = top + .2 * d; ay = top - .1 * height; }
            case "sheet_extent" -> { x = cx - 1.4 * d; z = cz + .6 * d; y = top + .6 * d; }
        }
        if (!view.equals("below")) y = Math.max(y, ground(x, z) + 15);
        ProjectAtmosphere.LOGGER.info("REVIEW_POSE type={} view={} region={} bounds=({},{},{},{},{},{}) camera=({},{},{}) aim=({},{},{}) clusters={}",
                type, view, region.getRegionId(), minX, maxX, base, top, minZ, maxZ, x, y, z, ax, ay, az, region.getClusters().size());
        return new double[] {x, y, z, ax, ay, az};
    }
}
