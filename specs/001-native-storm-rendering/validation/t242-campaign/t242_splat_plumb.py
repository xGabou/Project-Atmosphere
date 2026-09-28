# T242 TEMP: splat-side PaShapeCandidate uniform for the weather, morphology and
# cumulus-layers splats.  python t242_splat_plumb.py install|install-extra|remove
import sys
import os

ROOT = r'C:\Users\matga\Downloads\Project-Atmosphere'
CORE = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'projectatmosphere', 'shaders', 'core')
REN = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'Gabou', 'projectatmosphere', 'clouds', 'client',
                   'render', 'volumetric', 'CloudWeatherMapRenderer.java')
NL = '\n'
UNI = 'uniform int PaShapeCandidate; // T242 TEMP' + NL
JUNI = '    { "name": "PaShapeCandidate", "type": "int", "count": 1, "values": [ 0 ] }'
UP = 'safeGetUniform("PaShapeCandidate").set(VolumetricCloudDebugConfig.shapeCandidate()); // T242 TEMP' + NL


def core(name):
    return os.path.join(CORE, name)


CELL = '    { "name": "CellCount", "type": "int", "count": 1, "values": [ 0 ] }'
OUTM = '    { "name": "OutputMode", "type": "int", "count": 1, "values": [ 0 ] }'
EDITS = [
    (core('cloud_weather_splat.fsh'), 'uniform int CellCount;' + NL, 'uniform int CellCount;' + NL + UNI),
    (core('cloud_weather_splat.json'), CELL, CELL + ',' + NL + JUNI),
    (REN, '        shader.safeGetUniform("CellCount").set(count);' + NL + '        shader.apply();' + NL,
     '        shader.safeGetUniform("CellCount").set(count);' + NL + '        shader.' + UP + '        shader.apply();' + NL),
    (core('cloud_weather_morphology.fsh'), 'uniform int CellCount;' + NL, 'uniform int CellCount;' + NL + UNI),
    (core('cloud_weather_morphology.json'), CELL, CELL + ',' + NL + JUNI),
    (REN, '        morphologyShader.safeGetUniform("CellCount").set(count);' + NL,
     '        morphologyShader.safeGetUniform("CellCount").set(count);' + NL + '        morphologyShader.' + UP),
    (core('cloud_weather_cumulus_layers.fsh'), 'uniform int OutputMode;' + NL, 'uniform int OutputMode;' + NL + UNI),
    (core('cloud_weather_cumulus_layers.json'), OUTM, OUTM + ',' + NL + JUNI),
    (REN, '        shader.safeGetUniform("OutputMode").set(outputMode);' + NL,
     '        shader.safeGetUniform("OutputMode").set(outputMode);' + NL + '        shader.' + UP),
]
EXTRA_START = 3


def io(p, a, b):
    raw = open(p, encoding='utf-8', newline='').read()
    nl = '\r\n' if '\r\n' in raw else '\n'
    s = raw.replace('\r\n', '\n')
    assert s.count(a) == 1, (p, a[:50], s.count(a))
    open(p, 'w', encoding='utf-8', newline='').write(s.replace(a, b).replace('\n', nl))


mode = sys.argv[1]
edits = EDITS[EXTRA_START:] if mode == 'install-extra' else EDITS
if mode == 'remove':
    for name in ('cloud_weather_splat.fsh', 'cloud_weather_morphology.fsh', 'cloud_weather_cumulus_layers.fsh'):
        t = open(core(name), encoding='utf-8').read().replace('\r\n', '\n').replace(UNI, '')
        assert 'PaShapeCandidate' not in t, ('splat candidate code remains', name)
    for p, a, b in edits:
        io(p, b, a)
else:
    for p, a, b in edits:
        io(p, a, b)
print(mode, 'ok')
