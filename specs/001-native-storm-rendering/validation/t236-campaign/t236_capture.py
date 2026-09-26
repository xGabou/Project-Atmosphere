# T236 TEMP capture set: per view, arms from ARMS (code 400+index).
# arm spec: (name, lightingMode, underMode-free placeholder). Revert with git checkout.
import sys, json

p = r'C:\Users\matga\Downloads\Project-Atmosphere\src\main\java\net\Gabou\projectatmosphere\clouds\client\render\volumetric\StormT098CaptureDriver.java'
ARMS = json.loads(sys.argv[1]) if len(sys.argv) > 1 else [["N", 1, 0], ["P", 0, 0]]
raw = open(p, encoding='utf-8', newline='').read()
crlf = '\r\n' in raw
s = raw.replace('\r\n', '\n')


def rep(a, b):
    global s
    assert s.count(a) == 1, (a[:70], s.count(a))
    s = s.replace(a, b)


arm_names = ', '.join('"%s"' % a[0] for a in ARMS)
arm_modes = ', '.join(str(a[1]) for a in ARMS)
arm_shape = ', '.join(str(a[2]) for a in ARMS)
rep("""        List<Shot> built = new ArrayList<>();
        // 1 FAR: whole silhouette in frame.
""", """        List<Shot> built = new ArrayList<>();
        // T236 TEMP. Shape reference set on ONE storm: per view each arm
        // back to back (arm code 400 + index in originJitter).
        String[] t236Poses = {"WARMUP", "FAR", "FAR_OPP", "FAR_LAT", "FAR_LAT_OPP", "SIDE", "SIDE_OPP",
                "LATERAL", "ABOVE", "LOWSIDE", "NEAREDGE", "THROUGH"};
        double[][] t236At = {
                {centreX + radius * 2.6D, midY, centreZ},
                {centreX + radius * 2.6D, midY, centreZ},
                {centreX - radius * 2.6D, midY, centreZ},
                {centreX, midY, centreZ + radius * 2.6D},
                {centreX, midY, centreZ - radius * 2.6D},
                {centreX + radius * 1.7D, midY, centreZ},
                {centreX - radius * 1.7D, midY, centreZ},
                {centreX, midY, centreZ + radius * 1.9D},
                {centreX + radius * 0.6D, baseY + height * 1.45D, centreZ},
                {centreX + radius * 1.45D, baseY + height * 0.10D, centreZ},
                {centreX + radius * 1.12D, baseY + height * 0.55D, centreZ},
                {centreX + radius * 0.82D, baseY + height * 0.62D, centreZ}};
        String[] t236Arms = {""" + arm_names + """};
        for (int pose = 0; pose < t236Poses.length; pose++) {
            for (int arm = 0; arm < t236Arms.length; arm++) {
                built.add(new Shot("T236_" + t236Arms[arm] + "_" + t236Poses[pose],
                        t236At[pose][0], t236At[pose][1], t236At[pose][2],
                        "T236 arm " + t236Arms[arm] + " at " + t236Poses[pose],
                        VolumetricCloudRaymarchDebugView.FINAL, false, false, false,
                        Float.NaN, false, 400 + arm));
            }
        }
        // 1 FAR: whole silhouette in frame.
""")
rep("""                if (shot.originJitter() >= 0) {
                    VolumetricCloudDebugConfig.setMacroEnvelopeMode(3);""", """                if (shot.originJitter() >= 400) { // T236 TEMP
                    int t236Arm = shot.originJitter() - 400;
                    int[] t236Modes = {""" + arm_modes + """};
                    int[] t236Shape = {""" + arm_shape + """};
                    VolumetricCloudDebugConfig.setDiagnosticLightingMode(t236Modes[t236Arm]);
                    VolumetricCloudDebugConfig.setShapeCandidateMode(t236Shape[t236Arm]);
                    VolumetricCloudDebugConfig.setFinalProgramOverride(
                            t236Modes[t236Arm] != 0 || t236Shape[t236Arm] != 0
                                    ? CoreCostDiagnosticProgram.DIAGNOSTIC_MONOLITH : null);
                } else if (shot.originJitter() >= 0) {
                    VolumetricCloudDebugConfig.setFinalProgramOverride(null); // T236 TEMP
                    VolumetricCloudDebugConfig.setMacroEnvelopeMode(3);""")
rep("""        VolumetricCloudDebugConfig.setT136ConstantLighting(false);
        CloudFieldVolumeRenderConfig.setCompositeDebugMode(CloudFieldCompositeDebugMode.FINAL);""", """        VolumetricCloudDebugConfig.setT136ConstantLighting(false);
        VolumetricCloudDebugConfig.setDiagnosticLightingMode(0); // T236 TEMP
        VolumetricCloudDebugConfig.setShapeCandidateMode(0); // T236 TEMP
        VolumetricCloudDebugConfig.setFinalProgramOverride(null); // T236 TEMP
        CloudFieldVolumeRenderConfig.setCompositeDebugMode(CloudFieldCompositeDebugMode.FINAL);""")
if crlf:
    s = s.replace('\n', '\r\n')
open(p, 'w', encoding='utf-8', newline='').write(s)
print('ok', ARMS)
