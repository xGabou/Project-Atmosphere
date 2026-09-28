# T242 TEMP: install/remove the monolith-only shape-candidate plumbing.
#   python t242_plumb.py install|remove
# install: #define PA_SHAPE_EXPERIMENT + uniform int PaShapeCandidate (fsh), json entry,
#   gradle strip + const bake, VolumetricCloudDebugConfig shapeCandidate get/set,
#   renderer upload + lean-eligibility term.
# Candidate code in the fsh must sit inside #ifdef PA_SHAPE_EXPERIMENT blocks; remove
# refuses while any remains.
import sys

ROOT = r'C:\Users\matga\Downloads\Project-Atmosphere'
FSH = ROOT + r'\src\main\resources\assets\projectatmosphere\shaders\core\cloud_atmosphere_volume.fsh'
JSON = ROOT + r'\src\main\resources\assets\projectatmosphere\shaders\core\cloud_atmosphere_volume.json'
GRADLE = ROOT + r'\build.gradle'
CFG = ROOT + r'\src\main\java\net\Gabou\projectatmosphere\clouds\client\render\volumetric\VolumetricCloudDebugConfig.java'
REN = ROOT + r'\src\main\java\net\Gabou\projectatmosphere\clouds\client\render\volumetric\VolumetricCloudRenderer.java'

EDITS = [
    (FSH, "#define PA_LEGACY_LOBE_UNION 1\n",
     "#define PA_LEGACY_LOBE_UNION 1\n// T242 TEMP. Monolith-only shape candidates (stripped from lean programs).\n#define PA_SHAPE_EXPERIMENT 1\n"),
    (FSH, "uniform int PaBodyMacro;\n",
     "uniform int PaBodyMacro;\n#ifdef PA_SHAPE_EXPERIMENT\nuniform int PaShapeCandidate;\n#endif\n"),
    (JSON, '    { "name": "PaBodyMacro",',
     '    { "name": "PaShapeCandidate", "type": "int", "count": 1, "values": [ 0 ] },\n    { "name": "PaBodyMacro",'),
    (GRADLE, "def leanFinalStrippedDefines = ['PA_LEGACY_LOBE_UNION']",
     "def leanFinalStrippedDefines = ['PA_LEGACY_LOBE_UNION', 'PA_SHAPE_EXPERIMENT'] // T242 TEMP"),
    (CFG, "    public static int bodyMacroMode() {\n",
     "    private static volatile int shapeCandidate; // T242 TEMP\n\n    public static int shapeCandidate() {\n        return shapeCandidate;\n    }\n\n    public static void setShapeCandidate(int candidate) {\n        shapeCandidate = candidate;\n    }\n\n    public static int bodyMacroMode() {\n"),
]


def load(p):
    raw = open(p, encoding='utf-8', newline='').read()
    return ('\r\n' if '\r\n' in raw else '\n'), raw.replace('\r\n', '\n')


def save(p, nl, s):
    open(p, 'w', encoding='utf-8', newline='').write(s.replace('\n', nl))


def ren_edits(install):
    nl, s = load(REN)
    up_anchor = '        shader.safeGetUniform("PaBodyMacro")'
    up_line = '        shader.safeGetUniform("PaShapeCandidate").set(VolumetricCloudDebugConfig.shapeCandidate()); // T242 TEMP\n'
    lean_anchor = '                && VolumetricCloudDebugConfig.bodyMacroMode()'
    lean_line = '                && VolumetricCloudDebugConfig.shapeCandidate() == 0 // T242 TEMP\n'
    if install:
        assert s.count(up_anchor) == 1 and s.count(lean_anchor) == 1, (s.count(up_anchor), s.count(lean_anchor))
        i = s.index(up_anchor); s = s[:i] + up_line + s[i:]
        i = s.index(lean_anchor); s = s[:i] + lean_line + s[i:]
    else:
        assert s.count(up_line) == 1 and s.count(lean_line) == 1
        s = s.replace(up_line, '').replace(lean_line, '')
    save(REN, nl, s)


mode = sys.argv[1]
if mode == 'install':
    for p, a, b in EDITS:
        nl, s = load(p)
        assert s.count(a) == 1, (p, a[:40], s.count(a))
        save(p, nl, s.replace(a, b))
    nl, s = load(GRADLE)
    a = "        'uniform int PaBodyMacro;'"
    assert s.count(a) == 1, 'gradle bake anchor'
    i = s.index(a)
    s = s[:i] + "        'uniform int PaShapeCandidate;' : 'const int PaShapeCandidate = 0;', // T242 TEMP\n" + s[i:]
    save(GRADLE, nl, s)
    ren_edits(True)
else:
    nl, s = load(FSH)
    body = s.replace(EDITS[0][2], '').replace(EDITS[1][2], '')
    assert 'PaShapeCandidate' not in body and 'PA_SHAPE_EXPERIMENT' not in body, 'candidate code still in fsh'
    for p, a, b in EDITS:
        nl, s = load(p)
        assert s.count(b) == 1, (p, b[:40])
        save(p, nl, s.replace(b, a))
    nl, s = load(GRADLE)
    line = "        'uniform int PaShapeCandidate;' : 'const int PaShapeCandidate = 0;', // T242 TEMP\n"
    assert s.count(line) == 1
    save(GRADLE, nl, s.replace(line, ''))
    ren_edits(False)
print(mode, 'ok')
