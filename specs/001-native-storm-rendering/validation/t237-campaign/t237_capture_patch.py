import os
HERE = os.path.dirname(os.path.abspath(__file__))
P = os.path.join(HERE, 't237_capture.py')
s = open(P, encoding='utf-8').read()


def rep(a, b):
    global s
    assert s.count(a) == 1, a[:60]
    s = s.replace(a, b)


rep('''        String[] t237Poses = {"WARMUP", "NEAREDGE", "THROUGH", "FAR", "FAR_OPP", "FAR_LAT",
                "FAR_LAT_OPP", "SIDE", "LATERAL", "ABOVE", "LOWSIDE", "TOWERLOW"};
        double[][] t237At = {
                {centreX + radius * 2.6D, midY, centreZ},
                {centreX + radius * 1.12D, baseY + height * 0.55D, centreZ},
                {centreX + radius * 0.82D, baseY + height * 0.62D, centreZ},
                {centreX + radius * 2.6D, midY, centreZ},
                {centreX - radius * 2.6D, midY, centreZ},
                {centreX, midY, centreZ + radius * 2.6D},
                {centreX, midY, centreZ - radius * 2.6D},
                {centreX + radius * 1.7D, midY, centreZ},
                {centreX, midY, centreZ + radius * 1.9D},
                {centreX + radius * 0.6D, baseY + height * 1.45D, centreZ},
                {centreX + radius * 1.45D, baseY + height * 0.10D, centreZ},
                {centreX - radius * 1.2D, baseY + height * 0.02D, centreZ + radius * 1.6D}};''',
'''        String[] t237Poses = {"WARMUP", "LATERAL", "FAR_LAT", "SIDE", "ABOVE", "LOWSIDE", "TOWERLOW",
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
                {centreX - radius * 2.6D, midY, centreZ}};''')
rep('''            for (int arm = 0; arm < t237Arms.length; arm++) {
                built.add(''', '''            for (int k = 0; k < t237Arms.length; k++) {
                // ABBA: odd poses run the arm list backwards (one relayout per pose).
                int arm = pose % 2 == 0 ? k : t237Arms.length - 1 - k;
                built.add(''')
rep('''                        t237AppliedLayout = t237Layout;
                        t237ExtraSettle = true;
                    }''', '''                        t237AppliedLayout = t237Layout;
                        t237ExtraSettle = true;
                        t237TracePending = true;
                    } else if (t237TracePending) {
                        StormLobeDescriptor.t237ResetTrace(); // re-trace the synced layout
                        t237TracePending = false;
                    }''')
rep('''    private static boolean t237ExtraSettle; // T237 TEMP
''', '''    private static boolean t237ExtraSettle; // T237 TEMP
    private static boolean t237TracePending; // T237 TEMP
''')
rep("print('ok', ARMS)", r"""q = r'C:\Users\matga\Downloads\Project-Atmosphere\src\main\java\net\Gabou\projectatmosphere\clouds\client\render\volumetric\StormLobeDescriptor.java'
raw = open(q, encoding='utf-8', newline='').read(); crlf2 = '\r\n' in raw; t = raw.replace('\r\n', '\n')
a = '    private static int t215TraceBudget = 48;\n'
assert t.count(a) == 1
t = t.replace(a, a + '\n    /** T237 TEMP: re-arm the geometry trace after a relayout. */\n    static void t237ResetTrace() {\n        t215TraceBudget = 10;\n    }\n')
if crlf2: t = t.replace('\n', '\r\n')
open(q, 'w', encoding='utf-8', newline='').write(t)
print('ok', ARMS)""")
open(P, 'w', encoding='utf-8').write(s)
print('patched')
