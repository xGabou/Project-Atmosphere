# T237 TEMP capture set: per view, arms (name, lightingMode, layoutVersion); the
# storm is re-laid out on the server when the layout changes (extended settle).
# Revert with git checkout of StormT098CaptureDriver.java.
import sys, json

p = r'C:\Users\matga\Downloads\Project-Atmosphere\src\main\java\net\Gabou\projectatmosphere\clouds\client\render\volumetric\StormT098CaptureDriver.java'
ARMS = json.loads(sys.argv[1])
raw = open(p, encoding='utf-8', newline='').read()
crlf = '\r\n' in raw
s = raw.replace('\r\n', '\n')


def rep(a, b):
    global s
    assert s.count(a) == 1, (a[:70], s.count(a))
    s = s.replace(a, b)


names = ', '.join('"%s"' % a[0] for a in ARMS)
modes = ', '.join(str(a[1]) for a in ARMS)
layouts = ', '.join(str(a[2]) for a in ARMS)
rep("""    private static int settleFrames;
""", """    private static int settleFrames;
    private static int t237AppliedLayout = -1; // T237 TEMP
    private static boolean t237ExtraSettle; // T237 TEMP
    private static boolean t237TracePending; // T237 TEMP
    private static final int[] T237_MODES = {""" + modes + """}; // T237 TEMP
    private static final int[] T237_LAYOUTS = {""" + layouts + """}; // T237 TEMP
""")
rep("""        List<Shot> built = new ArrayList<>();
        // 1 FAR: whole silhouette in frame.
""", """        List<Shot> built = new ArrayList<>();
        // T237 TEMP. Storm-template A/B on ONE storm, re-laid out in place per
        // layout (arm code 500 + index in originJitter).
        String[] t237Poses = {"WARMUP", "LATERAL", "FAR_LAT", "SIDE", "ABOVE", "LOWSIDE", "TOWERLOW",
                "FAR", "NEAREDGE", "THROUGH", "FAR_LAT_OPP", "FAR_OPP"};
        double[][] t237At = {
                {centreX + radius * 2.6D, midY, centreZ},
                {centreX, midY, centreZ + radius * 1.9D},
                {centreX, midY, centreZ + radius * 2.6D},
                {centreX + radius * 1.7D, midY, centreZ},
                {centreX + radius * 0.6D, baseY + height * 1.45D, centreZ},
                {centreX + radius * 1.45D, baseY + height * 0.10D, centreZ},
                {centreX - radius * 1.2D, baseY + height * 0.02D, centreZ + radius * 1.6D},
                {centreX + radius * 2.6D, midY, centreZ},
                {centreX + radius * 1.12D, baseY + height * 0.55D, centreZ},
                {centreX + radius * 0.82D, baseY + height * 0.62D, centreZ},
                {centreX, midY, centreZ - radius * 2.6D},
                {centreX - radius * 2.6D, midY, centreZ}};
        String[] t237Arms = {""" + names + """};
        for (int pose = 0; pose < t237Poses.length; pose++) {
            for (int k = 0; k < t237Arms.length; k++) {
                // ABBA: odd poses run the arm list backwards (one relayout per pose).
                int arm = pose % 2 == 0 ? k : t237Arms.length - 1 - k;
                built.add(new Shot("T237_" + t237Arms[arm] + "_" + t237Poses[pose],
                        t237At[pose][0], t237At[pose][1], t237At[pose][2],
                        "T237 arm " + t237Arms[arm] + " at " + t237Poses[pose],
                        VolumetricCloudRaymarchDebugView.FINAL, false, false, false,
                        Float.NaN, false, 500 + arm));
            }
        }
        // 1 FAR: whole silhouette in frame.
""")
rep("""                if (shot.originJitter() >= 0) {
                    VolumetricCloudDebugConfig.setMacroEnvelopeMode(3);""", """                if (shot.originJitter() >= 500) { // T237 TEMP
                    int t237Arm = shot.originJitter() - 500;
                    VolumetricCloudDebugConfig.setDiagnosticLightingMode(T237_MODES[t237Arm]);
                    int t237Layout = T237_LAYOUTS[t237Arm];
                    if (t237Layout != t237AppliedLayout) {
                        net.minecraft.server.MinecraftServer t237Server =
                                Minecraft.getInstance().getSingleplayerServer();
                        if (t237Server != null) {
                            t237Server.execute(() -> ProjectAtmosphere.LOGGER.info("T237_RELAYOUT {}",
                                    net.Gabou.projectatmosphere.clouds.simulation.CloudGroupSpawner
                                            .relayoutStorms(t237Server.overworld(), t237Layout)));
                        }
                        t237AppliedLayout = t237Layout;
                        t237ExtraSettle = true;
                        t237TracePending = true;
                    } else if (t237TracePending) {
                        StormLobeDescriptor.t237ResetTrace(); // re-trace the synced layout
                        t237TracePending = false;
                    }
                } else if (shot.originJitter() >= 0) {
                    VolumetricCloudDebugConfig.setMacroEnvelopeMode(3);""")
rep("""                VolumetricCloudRenderer.invalidateHistory();
                settleFrames = 0;
                state = State.SETTLE;""", """                VolumetricCloudRenderer.invalidateHistory();
                settleFrames = t237ExtraSettle ? -150 : 0; // T237 TEMP: relayout sync
                t237ExtraSettle = false;
                state = State.SETTLE;""")
rep("""        VolumetricCloudDebugConfig.setT136ConstantLighting(false);
        CloudFieldVolumeRenderConfig.setCompositeDebugMode(CloudFieldCompositeDebugMode.FINAL);""", """        VolumetricCloudDebugConfig.setT136ConstantLighting(false);
        VolumetricCloudDebugConfig.setDiagnosticLightingMode(0); // T237 TEMP
        CloudFieldVolumeRenderConfig.setCompositeDebugMode(CloudFieldCompositeDebugMode.FINAL);""")
if crlf:
    s = s.replace('\n', '\r\n')
open(p, 'w', encoding='utf-8', newline='').write(s)
q = r'C:\Users\matga\Downloads\Project-Atmosphere\src\main\java\net\Gabou\projectatmosphere\clouds\client\render\volumetric\StormLobeDescriptor.java'
raw = open(q, encoding='utf-8', newline='').read(); crlf2 = '\r\n' in raw; t = raw.replace('\r\n', '\n')
a = '    private static int t215TraceBudget = 48;\n'
assert t.count(a) == 1
t = t.replace(a, a + '\n    /** T237 TEMP: re-arm the geometry trace after a relayout. */\n    static void t237ResetTrace() {\n        t215TraceBudget = 10;\n    }\n')
if crlf2: t = t.replace('\n', '\r\n')
open(q, 'w', encoding='utf-8', newline='').write(t)
print('ok', ARMS)
