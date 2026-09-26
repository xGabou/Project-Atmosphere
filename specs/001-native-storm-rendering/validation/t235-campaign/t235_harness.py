# T235 TEMP harness: variant programs (lean defines + extra defines) as a
# campaign-scoped route arm set. Revert with git checkout of the touched files.
# usage: python t235_harness.py  (edit VARIANTS below)
import sys

ROOT = 'C:/Users/matga/Downloads/Project-Atmosphere/'
V = 'src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/'

# (enum, serialized, route label, extra defines)
VARIANTS = [
    ('T235_PRE_FULL', 't235_pre_full', 'pre_full', ['PA_ARM_DESC_PRECOMPUTE 1']),
    ('T235_PRE_OWNER', 't235_pre_owner', 'pre_owner', ['PA_ARM_DESC_PRECOMPUTE_OWNER 1']),
    ('T235_PRE_EDGE', 't235_pre_edge', 'pre_edge', ['PA_ARM_DESC_PRECOMPUTE_EDGE 1']),
]
if len(sys.argv) > 1:
    import json
    VARIANTS = [tuple(v) for v in json.load(open(sys.argv[1]))]


def edit(path, pairs):
    raw = open(ROOT + path, encoding='utf-8', newline='').read()
    crlf = '\r\n' in raw
    s = raw.replace('\r\n', '\n')
    for a, b in pairs:
        assert s.count(a) == 1, (path, a[:70], s.count(a))
        s = s.replace(a, b)
    if crlf:
        s = s.replace('\n', '\r\n')
    open(ROOT + path, 'w', encoding='utf-8', newline='').write(s)


gradle_rows = ''.join(
    f"""        [name        : 'cloud_atmosphere_volume_{ser}',
         defines     : ['PA_PRECIPITATION_ABSENT 1', 'PA_ARM_NO_RAIN_SEGMENT 1',
                        'PA_RAIN_SPLIT_COMPOSITE 1'{''.join(", '%s'" % d for d in extra)}],
         keepUniforms: []],
""" for (_, ser, _, extra) in VARIANTS)
edit('build.gradle', [("""        [name        : 'cloud_atmosphere_volume_t171_dup_b',
         defines     : leanFinalDefines,
         keepUniforms: []],
""", """        [name        : 'cloud_atmosphere_volume_t171_dup_b',
         defines     : leanFinalDefines,
         keepUniforms: []],
        // T235 TEMP. Code-shape variants of FINAL.
""" + gradle_rows)])

enum_rows = ''.join(f'    {e}("{ser}"), // T235 TEMP\n' for (e, ser, _, _) in VARIANTS)
split = ''.join(f' || this == {e}' for (e, _, _, _) in VARIANTS)
edit(V + 'CoreCostDiagnosticProgram.java', [
    ("""    T171_DUP_B("t171_dup_b"),
""", """    T171_DUP_B("t171_dup_b"),
""" + enum_rows),
    ("""        return this == LEAN_FINAL || this == T171_DUP_A || this == T171_DUP_B
""", """        return this == LEAN_FINAL || this == T171_DUP_A || this == T171_DUP_B
                """ + split.strip() + """ // T235 TEMP
"""),
])

edit(V + 'StormCampaignRegistry.java', [("""            new Campaign("T217M", "t217-deep-motion.txt",
                    "deepScatterMotionRunRequested", "t217mRun",
                    null, Routing.PERFORMANCE_ONLY),
""", """            new Campaign("T217M", "t217-deep-motion.txt",
                    "deepScatterMotionRunRequested", "t217mRun",
                    null, Routing.PERFORMANCE_ONLY),
            // T235 TEMP. FINAL code-shape variants on the T152 route.
            new Campaign("T235", "t235-codeshape-motion.txt",
                    "codeShapeMotionRunRequested", "t235mRun",
                    null, Routing.PERFORMANCE_ONLY),
""")])
edit(V + 'StormT132AutoDriver.java', [
    ("""    private static boolean t217mRun;
""", """    private static boolean t217mRun;
    private static boolean t235mRun; // T235 TEMP
"""),
    ("""    private static boolean deepScatterMotionRunRequested() {
        return Files.exists(T217M_MARKER);
    }
""", """    private static boolean deepScatterMotionRunRequested() {
        return Files.exists(T217M_MARKER);
    }

    private static final Path T235M_MARKER = Path.of("t235-codeshape-motion.txt"); // T235 TEMP

    /** T235 TEMP. FINAL code-shape variants under motion. */
    private static boolean codeShapeMotionRunRequested() {
        return Files.exists(T235M_MARKER);
    }
"""),
    ("""                            || deepScatterMotionRunRequested()
                            || gateEdgeMotionRunRequested()""", """                            || deepScatterMotionRunRequested()
                            || codeShapeMotionRunRequested()
                            || gateEdgeMotionRunRequested()"""),
    ("""                t217mRun = deepScatterMotionRunRequested();
""", """                t217mRun = deepScatterMotionRunRequested();
                t235mRun = codeShapeMotionRunRequested(); // T235 TEMP
"""),
    ("""                String begun = StormT152MovingCameraFixture.begin(t225mRun
                        ? StormT152MovingCameraFixture.ArmSet.RAIN_RAMP""", """                String begun = StormT152MovingCameraFixture.begin(t235mRun
                        ? StormT152MovingCameraFixture.ArmSet.T235_CODESHAPE // T235 TEMP
                        : t225mRun
                        ? StormT152MovingCameraFixture.ArmSet.RAIN_RAMP"""),
])

arm_rows = '        T235_FINAL("final", false, 1, 0.25F, -1, 0, 0, 3, 1, 1), // T235 TEMP\n' \
    + '        T235_MONO("mono", false, 1, 0.25F, -1, 0, 0, 3, 1, 1), // T235 TEMP\n' \
    + ''.join(f'        {e}_ARM("{label}", false, 1, 0.25F, -1, 0, 0, 3, 1, 1), // T235 TEMP\n'
              for (e, _, label, _) in VARIANTS) \
    + '        T235_FINAL_B("final_b", false, 1, 0.25F, -1, 0, 0, 3, 1, 1), // T235 TEMP\n'
arm_list = 'Arm.PRODUCTION_050, Arm.T235_FINAL, Arm.T235_MONO, ' \
    + ''.join(f'Arm.{e}_ARM, ' for (e, _, _, _) in VARIANTS) + 'Arm.T235_FINAL_B'
program_sel = '                    arm == Arm.T235_MONO ? CoreCostDiagnosticProgram.DIAGNOSTIC_MONOLITH\n' \
    + ''.join(f'                    : arm == Arm.{e}_ARM ? CoreCostDiagnosticProgram.{e}\n'
              for (e, _, _, _) in VARIANTS) + '                    : null);'
edit(V + 'StormT152MovingCameraFixture.java', [
    ("""        HOLD_BODY("hold_body",""", arm_rows + """        HOLD_BODY("hold_body","""),
    ("""        RAIN_RAMP(RouteShape.STATIONARY, Arm.HOLD_BODY, Arm.RAMP_BODY, Arm.RAMP_FULL);""",
     """        RAIN_RAMP(RouteShape.STATIONARY, Arm.HOLD_BODY, Arm.RAMP_BODY, Arm.RAMP_FULL),
        T235_CODESHAPE(""" + arm_list + """); // T235 TEMP"""),
    ("""            VolumetricCloudDebugConfig.setDeepScatterMode(arm.deepScatter);
""", """            VolumetricCloudDebugConfig.setDeepScatterMode(arm.deepScatter);
            VolumetricCloudDebugConfig.setFinalProgramOverride( // T235 TEMP
""" + program_sel + """
"""),
    ("""                VolumetricCloudDebugConfig.setPrecipitationOverride(Float.NaN);
            }
            historyOverrideApplied = false;""", """                VolumetricCloudDebugConfig.setPrecipitationOverride(Float.NaN);
                VolumetricCloudDebugConfig.setFinalProgramOverride(null); // T235 TEMP
            }
            historyOverrideApplied = false;"""),
])
print('installed', [v[1] for v in VARIANTS])
