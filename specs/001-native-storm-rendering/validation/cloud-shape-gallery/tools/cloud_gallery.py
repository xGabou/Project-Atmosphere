# Cloud-shape reference gallery - capture-driver patch (applied AFTER t241_capture_repo.py).
# Production renderer only: lighting mode 0, no candidate/experiment switches. One cloud per family
# is spawned just in time 9000 blocks east of the storm fixture (9000 apart along +Z, clear of the
# fixture's anvil), settled, and then every named view is shot around that SAME instance.
# usage: cloud_gallery.py '<json type list>'
import sys, json

p = r'C:\Users\matga\Downloads\Project-Atmosphere\src\main\java\net\Gabou\projectatmosphere\clouds\client\render\volumetric\StormT098CaptureDriver.java'
TYPES = json.loads(sys.argv[1])

BASE = ['side', 'opposite_side', 'front', 'back', 'above', 'below', 'low_angle', 'far', 'close']
DIRECTIONAL = ['upwind', 'downwind', 'crosswind_left', 'crosswind_right']
LAYER = ['sheet_extent', 'underside', 'edge_profile', 'top_profile']
CONVECTIVE = ['lower_body', 'tower', 'transition']
STORM = ['anvil']
KIND = {
    'cumulonimbus_calvus': BASE + DIRECTIONAL + CONVECTIVE + STORM,
    'supercell': BASE + DIRECTIONAL + CONVECTIVE + STORM,
    'cumulus_humilis': BASE + CONVECTIVE,
    'cumulus_mediocris': BASE + CONVECTIVE,
    'cumulus_congestus': BASE + CONVECTIVE,
    'stratus_nebulosus': BASE + LAYER,
    'stratocumulus': BASE + LAYER,
    'nimbostratus': BASE + LAYER,
    'cirrus': BASE + DIRECTIONAL + LAYER,
}
SHOTS = [(ti, v) for ti, t in enumerate(TYPES) for v in KIND[t]]
assert len(SHOTS) < 1000

raw = open(p, encoding='utf-8', newline='').read()
crlf = '\r\n' in raw
s = raw.replace('\r\n', '\n')


def rep(a, b):
    global s
    assert s.count(a) == 1, (a[:70], s.count(a))
    s = s.replace(a, b)


jstr = lambda xs: ', '.join('"%s"' % x for x in xs)
rep("""    private static int t237AppliedLayout = -1; // T237 TEMP
""", """    private static int t237AppliedLayout = -1; // T237 TEMP
    private static final String[] CG_TYPES = {""" + jstr(TYPES) + """}; // CLOUD GALLERY TEMP
    private static final int[] CG_SHOT_TYPE = {""" + ', '.join(str(t) for t, _ in SHOTS) + """};
    private static final String[] CG_SHOT_VIEW = {""" + jstr(v for _, v in SHOTS) + """};
    private static final net.Gabou.projectatmosphere.clouds.state.CloudRegionState[] CG_REGIONS =
            new net.Gabou.projectatmosphere.clouds.state.CloudRegionState[CG_TYPES.length];
    private static double[] t241GAim; // CLOUD GALLERY TEMP: {x, y, z, aimX, aimY, aimZ}
    private static String cgPoseNote = "";
    private static long t242FrameMark; // T242 TEMP

    private static double cgGround(double x, double z) {
        double g = 64.0D;
        net.minecraft.client.multiplayer.ClientLevel lvl = Minecraft.getInstance().level;
        if (lvl != null) {
            g = Math.max(g, lvl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                    (int) Math.floor(x), (int) Math.floor(z)));
        }
        return g;
    }

    /** CLOUD GALLERY TEMP: camera {x, y, z, aimX, aimY, aimZ} for a family and named view. */
    private static double[] cgPose(int type, String view) {
        net.Gabou.projectatmosphere.clouds.state.CloudRegionState region = CG_REGIONS[type];
        if (region == null) {
            return null;
        }
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        double base = Double.MAX_VALUE, top = -Double.MAX_VALUE;
        for (net.Gabou.projectatmosphere.clouds.state.CloudClusterState c : region.getClusters()) {
            net.minecraft.world.phys.Vec3 cc = c.getCenter();
            minX = Math.min(minX, cc.x - c.getRadius()); maxX = Math.max(maxX, cc.x + c.getRadius());
            minZ = Math.min(minZ, cc.z - c.getRadius()); maxZ = Math.max(maxZ, cc.z + c.getRadius());
            base = Math.min(base, c.getBaseY()); top = Math.max(top, c.getTopY());
        }
        if (minX > maxX) {
            return null;
        }
        double cx = 0.5D * (minX + maxX), cz = 0.5D * (minZ + maxZ);
        double ext = 0.5D * Math.max(maxX - minX, maxZ - minZ);
        double h = Math.max(top - base, 1.0D);
        double midY = base + 0.5D * h;
        // Shear axis: height-weighted cluster offset (upper mass leans downwind); +X if none.
        double sx = 0.0D, sz = 0.0D, mx = 0.0D, mz = 0.0D; int n = 0;
        for (net.Gabou.projectatmosphere.clouds.state.CloudClusterState c : region.getClusters()) {
            mx += c.getCenter().x; mz += c.getCenter().z; n++;
        }
        mx /= Math.max(n, 1); mz /= Math.max(n, 1);
        for (net.Gabou.projectatmosphere.clouds.state.CloudClusterState c : region.getClusters()) {
            double w = (c.getTopY() - base) / h;
            sx += w * (c.getCenter().x - mx); sz += w * (c.getCenter().z - mz);
        }
        double sl = Math.hypot(sx, sz);
        double fx = 1.0D, fz = 0.0D;
        if (sl > 1.0e-3D * Math.max(ext, 1.0D)) { fx = sx / sl; fz = sz / sl; }
        double rx = -fz, rz = fx;
        double d = Math.max(0.55D * ext + 0.8D * h, ext + 25.0D) + 10.0D;
        boolean storm = h > 300.0D;
        double camX, camZ, camY, aimX = cx, aimY = midY, aimZ = cz;
        switch (view) {
            case "side" -> { camX = cx + rx * d; camZ = cz + rz * d; camY = midY; }
            case "opposite_side" -> { camX = cx - rx * d; camZ = cz - rz * d; camY = midY; }
            case "front" -> { camX = cx - fx * d; camZ = cz - fz * d; camY = midY; }
            case "back" -> { camX = cx + fx * d; camZ = cz + fz * d; camY = midY; }
            case "above" -> { camX = cx - fx * 0.3D * d; camZ = cz - fz * 0.3D * d; camY = top + 0.6D * d; }
            case "below", "underside" -> {
                double off = "below".equals(view) ? 0.3D : 0.15D;
                camX = cx + rx * off * ext; camZ = cz + rz * off * ext;
                if (storm) { // under the anvil overhang, outside the trunk and its lower body
                    camX = cx + fx * 0.9D * ext + rx * 0.2D * ext; camZ = cz + fz * 0.9D * ext + rz * 0.2D * ext;
                }
                double g = cgGround(camX, camZ);
                camY = Math.max(g + 12.0D, Math.min(base - 15.0D, g + (storm ? 0.45D : 0.5D) * Math.max(base - g, 0.0D)));
                if ("underside".equals(view)) { aimX = cx + fx * 0.6D * ext; aimZ = cz + fz * 0.6D * ext; aimY = base; }
                else { aimY = base + 0.25D * h; }
                if (base - g < 30.0D) { cgPoseNote = "below-base clearance only " + (int) (base - g) + " blocks"; }
            }
            case "low_angle" -> {
                double ux = rx - fx, uz = rz - fz, ul = Math.hypot(ux, uz);
                camX = cx + ux / ul * d; camZ = cz + uz / ul * d; camY = cgGround(camX, camZ) + 40.0D;
            }
            case "far" -> {
                double ux = rx + 0.3D * fx, uz = rz + 0.3D * fz, ul = Math.hypot(ux, uz);
                camX = cx + ux / ul * 2.0D * d; camZ = cz + uz / ul * 2.0D * d; camY = midY;
            }
            case "close" -> { double cd = Math.max(0.6D * d, ext + 20.0D); camX = cx + rx * cd; camZ = cz + rz * cd; camY = midY; }
            case "upwind" -> { camX = cx - fx * d; camZ = cz - fz * d; camY = midY + 0.35D * d; }
            case "downwind" -> { camX = cx + fx * d; camZ = cz + fz * d; camY = midY + 0.35D * d; }
            case "crosswind_left" -> { camX = cx - rx * d; camZ = cz - rz * d; camY = midY + 0.35D * d; }
            case "crosswind_right" -> { camX = cx + rx * d; camZ = cz + rz * d; camY = midY + 0.35D * d; }
            case "sheet_extent" -> {
                camX = cx - fx * 1.4D * d + rx * 0.6D * d; camZ = cz - fz * 1.4D * d + rz * 0.6D * d; camY = top + 0.6D * d;
            }
            case "edge_profile" -> { camX = cx + rx * 1.4D * d; camZ = cz + rz * 1.4D * d; camY = midY; }
            case "top_profile" -> {
                camX = cx + rx * 1.2D * d; camZ = cz + rz * 1.2D * d; camY = top + 15.0D; aimY = top - 0.2D * h;
            }
            case "lower_body" -> { camX = cx + rx * d; camZ = cz + rz * d; camY = base + 0.15D * h; aimY = camY; }
            case "tower" -> { camX = cx + rx * d; camZ = cz + rz * d; camY = base + 0.45D * h; aimY = camY; }
            case "transition" -> { camX = cx + rx * d; camZ = cz + rz * d; camY = base + 0.75D * h; aimY = base + 0.72D * h; }
            case "anvil" -> { camX = cx + rx * 1.2D * d; camZ = cz + rz * 1.2D * d; camY = top + 0.2D * d; aimY = top - 0.1D * h; }
            default -> { camX = cx + rx * d; camZ = cz + rz * d; camY = midY; }
        }
        double gCam = cgGround(camX, camZ);
        if (camY < gCam + 15.0D && !"below".equals(view) && !"underside".equals(view)) {
            cgPoseNote = (cgPoseNote.isEmpty() ? "" : cgPoseNote + "; ") + "raised above terrain from " + (int) camY;
            camY = gCam + 15.0D;
        }
        ProjectAtmosphere.LOGGER.info("CG_POSE type={} view={} region={} centre=({},{}) ext={} base={} top={} shear=({},{}) "
                        + "ground={} cam=({},{},{}) aim=({},{},{}) note={}",
                CG_TYPES[type], view, region.getRegionId(), fmt(cx), fmt(cz), fmt(ext), fmt(base), fmt(top),
                fmt(fx), fmt(fz), fmt(gCam), fmt(camX), fmt(camY), fmt(camZ), fmt(aimX), fmt(aimY), fmt(aimZ), cgPoseNote);
        cgPoseNote = "";
        return new double[] {camX, camY, camZ, aimX, aimY, aimZ};
    }
""")
rep("""        List<Shot> built = new ArrayList<>();
        // T237 TEMP.""", """        List<Shot> built = new ArrayList<>();
        { // CLOUD GALLERY TEMP
            for (int gType = 0; gType < CG_TYPES.length; gType++) {
                built.add(new Shot("CG_" + CG_TYPES[gType] + "__SPAWN", centreX, midY, centreZ,
                        "gallery spawn", VolumetricCloudRaymarchDebugView.FINAL, false, false, false,
                        Float.NaN, false, 3000 + gType));
                for (int i = 0; i < CG_SHOT_TYPE.length; i++) {
                    if (CG_SHOT_TYPE[i] != gType) {
                        continue;
                    }
                    built.add(new Shot("CG_" + CG_TYPES[gType] + "__" + CG_SHOT_VIEW[i], centreX, midY, centreZ,
                            "gallery", VolumetricCloudRaymarchDebugView.FINAL, false, false, false,
                            Float.NaN, false, 2000 + i));
                }
            }
        }
        // T237 TEMP.""")
rep("""                if (shot.originJitter() >= 500) { // T237 TEMP
""", """                t241GAim = null; // CLOUD GALLERY TEMP
                if (shot.originJitter() >= 3000) {
                    int gType = shot.originJitter() - 3000;
                    VolumetricCloudDebugConfig.setDiagnosticLightingMode(0);
                    net.minecraft.server.MinecraftServer gServer = Minecraft.getInstance().getSingleplayerServer();
                    StormPerformanceBaseline.SuiteFixture gFix = StormPerformanceBaseline.suiteFixture();
                    if (gServer != null && gFix != null) {
                        double gx = gFix.centerX() + 9000.0D;
                        double gz = gFix.centerZ() + 9000.0D * gType;
                        gServer.execute(() -> {
                            CG_REGIONS[gType] = net.Gabou.projectatmosphere.clouds.simulation.CloudGroupSpawner
                                    .spawnRequestedCloud(gServer.overworld(),
                                            net.minecraft.core.BlockPos.containing(gx, 0.0D, gz), CG_TYPES[gType]);
                            ProjectAtmosphere.LOGGER.info("CG_SPAWN type={} ok={} region={} at=({},{})", CG_TYPES[gType],
                                    CG_REGIONS[gType] != null,
                                    CG_REGIONS[gType] == null ? "-" : CG_REGIONS[gType].getRegionId(), gx, gz);
                        });
                        t241GAim = new double[] {gx + 1500.0D, 300.0D, gz, gx, 300.0D, gz};
                    }
                    t237ExtraSettle = true;
                } else if (shot.originJitter() >= 2000) {
                    int i = shot.originJitter() - 2000;
                    VolumetricCloudDebugConfig.setDiagnosticLightingMode(0);
                    VolumetricCloudDebugConfig.setPrecipitationOverride(Float.NaN);
                    t241GAim = cgPose(CG_SHOT_TYPE[i], CG_SHOT_VIEW[i]);
                } else if (shot.originJitter() >= 500) { // T237 TEMP
""")
rep("""                float pitch = pitchTo(shot);
                player.connection.sendCommand(String.format(Locale.ROOT,
                        "tp @s %.5f %.5f %.5f %.3f %.3f",
                        shot.x(), shot.y(), shot.z(), yaw, pitch));
""", """                float pitch = pitchTo(shot);
                double tpX = shot.x(), tpY = shot.y(), tpZ = shot.z();
                if (t241GAim != null) { // CLOUD GALLERY TEMP
                    tpX = t241GAim[0]; tpY = t241GAim[1]; tpZ = t241GAim[2];
                    yaw = yawTo(tpX, tpZ, t241GAim[3], t241GAim[5]);
                    double gdy = t241GAim[4] - tpY;
                    double gh = Math.hypot(t241GAim[3] - tpX, t241GAim[5] - tpZ);
                    pitch = (float) -Math.toDegrees(Math.atan2(gdy, Math.max(gh, 1.0D)));
                    ProjectAtmosphere.LOGGER.info("CG_CAMERA shot={} pos=({},{},{}) yaw={} pitch={}", shot.name(),
                            fmt(tpX), fmt(tpY), fmt(tpZ), yaw, pitch);
                }
                player.connection.sendCommand(String.format(Locale.ROOT,
                        "tp @s %.5f %.5f %.5f %.3f %.3f",
                        tpX, tpY, tpZ, yaw, pitch));
""")
rep("""                settleFrames = t237ExtraSettle ? -150 : 0; // T237 TEMP: relayout sync
""", """                settleFrames = t237ExtraSettle ? (shot.originJitter() >= 3000 ? -900 : -150) : 0; // T237/CG TEMP
""")
rep("""            case SETTLE -> {
                if (++settleFrames >= (resolutionLadder ? LADDER_SETTLE_FRAMES : SETTLE_FRAMES)) {""", """            case SETTLE -> {
                // T242 TEMP: the settle also waits for real presented frames - the
                // client tick keeps running while chunk generation starves rendering.
                if (++settleFrames >= (resolutionLadder ? LADDER_SETTLE_FRAMES : SETTLE_FRAMES)
                        && VolumetricCloudRenderHook.presentedFrames() - t242FrameMark >= 24) {""")
rep("""                VolumetricCloudRenderer.invalidateHistory();
                settleFrames = t237ExtraSettle""", """                VolumetricCloudRenderer.invalidateHistory();
                t242FrameMark = VolumetricCloudRenderHook.presentedFrames(); // T242 TEMP
                settleFrames = t237ExtraSettle""")
if crlf:
    s = s.replace('\n', '\r\n')
open(p, 'w', encoding='utf-8', newline='').write(s)
print('cloud gallery ok', len(TYPES), 'types', len(SHOTS), 'views')
