# T241 GALLERY TEMP: applied AFTER t241_capture.py. Adds, before the storm arms,
# a per-cloud-type gallery: each type is spawned just in time 5000 blocks along
# +X from the storm fixture, settled, then shot SIDE / UNDER / ABOVE in neutral
# (N, lighting mode 1) and production (P) lighting, aimed at the live bounds of
# the spawned region's clusters.
# usage: t241_gallery.py '<json type list>' <bodyCandidate>
import sys, json

p = r'C:\Users\matga\Downloads\Project-Atmosphere\src\main\java\net\Gabou\projectatmosphere\clouds\client\render\volumetric\StormT098CaptureDriver.java'
TYPES = json.loads(sys.argv[1])
# arms: [[suffix, lightMode, candidate], ...] (at most 3)
GARMS = json.loads(sys.argv[2])
assert 1 <= len(GARMS) <= 3
raw = open(p, encoding='utf-8', newline='').read()
crlf = '\r\n' in raw
s = raw.replace('\r\n', '\n')


def rep(a, b):
    global s
    assert s.count(a) == 1, (a[:70], s.count(a))
    s = s.replace(a, b)


types = ', '.join('"%s"' % t for t in TYPES)
rep("""    private static int t237AppliedLayout = -1; // T237 TEMP
""", """    private static int t237AppliedLayout = -1; // T237 TEMP
    private static final String[] T241G_TYPES = {""" + types + """}; // T241 GALLERY TEMP
    private static final net.Gabou.projectatmosphere.clouds.state.CloudRegionState[] T241G_REGIONS =
            new net.Gabou.projectatmosphere.clouds.state.CloudRegionState[T241G_TYPES.length]; // T241 GALLERY TEMP
    private static double[] t241GAim; // T241 GALLERY TEMP
    private static final String[] T241G_SUFFIX = {""" + ', '.join('"%s"' % a[0] for a in GARMS) + """}; // T241 GALLERY TEMP
    private static final int[] T241G_LIGHT = {""" + ', '.join(str(a[1]) for a in GARMS) + """}; // T241 GALLERY TEMP
    private static final int[] T241G_CAND = {""" + ', '.join(str(a[2]) for a in GARMS) + """}; // T241 GALLERY TEMP

    /** T241 GALLERY TEMP: {x, y, z, aimX, aimY, aimZ} for a type and pose, from live cluster bounds. */
    private static double[] t241GalleryPose(int type, int pose) {
        net.Gabou.projectatmosphere.clouds.state.CloudRegionState region = T241G_REGIONS[type];
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
        ProjectAtmosphere.LOGGER.info("T241G_BOUNDS type={} clusters={} centre=({},{}) ext={} base={} top={}",
                T241G_TYPES[type], region.getClusterCount(), fmt(cx), fmt(cz), fmt(ext), fmt(base), fmt(top));
        return switch (pose) {
            case 0 -> new double[] {cx + 0.55D * ext + 0.8D * h + 30.0D, midY, cz + 0.2D * ext, cx, midY, cz};
            case 1 -> new double[] {cx + 0.7D * ext + 20.0D, base - 25.0D - 0.2D * h, cz + 0.3D * ext, cx, midY, cz};
            default -> new double[] {cx + 0.5D * ext + 20.0D, top + 0.45D * ext + 0.6D * h + 20.0D, cz + 0.2D * ext, cx, midY, cz};
        };
    }
""")
rep("""        List<Shot> built = new ArrayList<>();
        // T237 TEMP.""", """        List<Shot> built = new ArrayList<>();
        { // T241 GALLERY TEMP
            String[] gPoses = {"SIDE", "UNDER", "ABOVE"};
            for (int gType = 0; gType < T241G_TYPES.length; gType++) {
                built.add(new Shot("T241G_" + T241G_TYPES[gType] + "_SPAWN", centreX, midY, centreZ,
                        "gallery spawn", VolumetricCloudRaymarchDebugView.FINAL, false, false, false,
                        Float.NaN, false, 2009 + gType * 10));
                for (int gPose = 0; gPose < gPoses.length; gPose++) {
                    for (int gArm = 0; gArm < T241G_SUFFIX.length; gArm++) {
                        built.add(new Shot("T241G_" + T241G_TYPES[gType] + "_" + gPoses[gPose]
                                        + "_" + T241G_SUFFIX[gArm], centreX, midY, centreZ,
                                "gallery", VolumetricCloudRaymarchDebugView.FINAL, false, false, false,
                                Float.NaN, false, 2000 + gType * 10 + gPose * 3 + gArm));
                    }
                }
            }
        }
        // T237 TEMP.""")
rep("""                if (shot.originJitter() >= 500) { // T237 TEMP
""", """                t241GAim = null; // T241 GALLERY TEMP
                if (shot.originJitter() >= 2000) {
                    int gCode = shot.originJitter() - 2000;
                    int gType = gCode / 10;
                    int gSub = gCode % 10;
                    VolumetricCloudDebugConfig.setPrecipitationOverride(Float.NaN);
                    if (gSub == 9) {
                        VolumetricCloudDebugConfig.setDiagnosticLightingMode(0);
                        net.minecraft.server.MinecraftServer gServer = Minecraft.getInstance().getSingleplayerServer();
                        StormPerformanceBaseline.SuiteFixture gFix = StormPerformanceBaseline.suiteFixture();
                        if (gServer != null && gFix != null) {
                            double gx = gFix.centerX() + 2000.0D * (gType + 1);
                            double gz = gFix.centerZ();
                            gServer.execute(() -> {
                                T241G_REGIONS[gType] = net.Gabou.projectatmosphere.clouds.simulation.CloudGroupSpawner
                                        .spawnRequestedCloud(gServer.overworld(),
                                                net.minecraft.core.BlockPos.containing(gx, 0.0D, gz), T241G_TYPES[gType]);
                                ProjectAtmosphere.LOGGER.info("T241G_SPAWN type={} ok={}", T241G_TYPES[gType],
                                        T241G_REGIONS[gType] != null);
                            });
                            t241GAim = new double[] {gx + 1500.0D, 300.0D, gz, gx, 300.0D, gz};
                        }
                        t237ExtraSettle = true;
                    } else {
                        VolumetricCloudDebugConfig.setDiagnosticLightingMode(T241G_LIGHT[gSub % 3]);
                        t241GAim = t241GalleryPose(gType, gSub / 3);
                    }
                } else if (shot.originJitter() >= 500) { // T237 TEMP
""")
rep("""                float pitch = pitchTo(shot);
                player.connection.sendCommand(String.format(Locale.ROOT,
                        "tp @s %.5f %.5f %.5f %.3f %.3f",
                        shot.x(), shot.y(), shot.z(), yaw, pitch));
""", """                float pitch = pitchTo(shot);
                double tpX = shot.x(), tpY = shot.y(), tpZ = shot.z();
                if (t241GAim != null) { // T241 GALLERY TEMP
                    tpX = t241GAim[0]; tpY = t241GAim[1]; tpZ = t241GAim[2];
                    yaw = yawTo(tpX, tpZ, t241GAim[3], t241GAim[5]);
                    double gdy = t241GAim[4] - tpY;
                    double gh = Math.hypot(t241GAim[3] - tpX, t241GAim[5] - tpZ);
                    pitch = (float) -Math.toDegrees(Math.atan2(gdy, Math.max(gh, 1.0D)));
                }
                player.connection.sendCommand(String.format(Locale.ROOT,
                        "tp @s %.5f %.5f %.5f %.3f %.3f",
                        tpX, tpY, tpZ, yaw, pitch));
""")
rep("""                settleFrames = t237ExtraSettle ? -150 : 0; // T237 TEMP: relayout sync
""", """                settleFrames = t237ExtraSettle ? (t241GAim != null && shot.originJitter() >= 2000
                        && (shot.originJitter() - 2000) % 10 == 9 ? -900 : -150) : 0; // T237/T241G TEMP
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
rep("""    private static double[] t241GAim; // T241 GALLERY TEMP
""", """    private static double[] t241GAim; // T241 GALLERY TEMP
    private static long t242FrameMark; // T242 TEMP
""")
if crlf:
    s = s.replace('\n', '\r\n')
open(p, 'w', encoding='utf-8', newline='').write(s)
print('gallery ok', TYPES, GARMS)
