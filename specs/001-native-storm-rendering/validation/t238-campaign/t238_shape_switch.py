# T238 TEMP: PaLobeShape switch. 0 = production role profiles (baked into FINAL),
# 1 = T238 slab anvil + column tower. Any non-zero value takes the monolith.
ROOT = 'C:/Users/matga/Downloads/Project-Atmosphere/'
VOL = 'src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/'
SH = 'src/main/resources/assets/projectatmosphere/shaders/core/'


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


edit(SH + 'cloud_atmosphere_volume.fsh', [
    ("""uniform int PaLowerDeckRelief;
""", """uniform int PaLowerDeckRelief;
// T238 TEMP. Lobe primitive: 0 production role profiles, 1 slab anvil +
// column tower. FINAL bakes 0.
uniform int PaLobeShape;
"""),
    ("""    } else if (role == 2) {
        // Broad root across the core's upper half before continuous tapering.
        // Keep this mirrored with StormLobeEvaluator.
        profileRadius = mix(1.25, 0.60, height01)""", """    } else if (role == 2 && PaLobeShape == 1) {
        // T238 column: a wider root where it overlaps the base, a near-constant
        // body, a slight flare where it feeds the anvil. Range [0.82, 1.00].
        profileRadius = 0.82
            + 0.18 * (1.0 - smoothstep(0.0, 0.22, height01))
            + 0.08 * smoothstep(0.82, 1.0, height01);
        verticalShape = smoothstep(0.0, 0.28, height01)
            * (1.0 - smoothstep(0.72, 1.0, height01));
        shearProgress = pow(height01, 1.6);
    } else if (role == 2) {
        // Broad root across the core's upper half before continuous tapering.
        // Keep this mirrored with StormLobeEvaluator.
        profileRadius = mix(1.25, 0.60, height01)"""),
    ("""    } else {
        // Broaden the anvil's root and canopy for a tower-fed upper deck;""", """    } else if (PaLobeShape == 1) {
        // T238 slab: widest at mid-deck, the rim thinning toward the flat top
        // and bottom faces (a disc, not a cup). Range [1.35, 1.80].
        float paDeck = abs(2.0 * height01 - 1.0);
        profileRadius = 1.80 - 0.45 * paDeck * paDeck * paDeck;
        verticalShape = smoothstep(0.0, 0.35, height01)
            * (1.0 - smoothstep(0.76, 1.0, height01));
        shearProgress = smoothstep(0.0, 0.65, height01);
    } else {
        // Broaden the anvil's root and canopy for a tower-fed upper deck;"""),
])
edit(SH + 'cloud_atmosphere_volume.json', [
    ("""    { "name": "PaLowerDeckRelief", "type": "int", "count": 1, "values": [ 1 ] },
""", """    { "name": "PaLowerDeckRelief", "type": "int", "count": 1, "values": [ 1 ] },
    { "name": "PaLobeShape", "type": "int", "count": 1, "values": [ 0 ] },
"""),
])
edit('build.gradle', [
    ("""        'uniform int PaLowerDeckRelief;'           : 'const int PaLowerDeckRelief = 1;',
""", """        'uniform int PaLowerDeckRelief;'           : 'const int PaLowerDeckRelief = 1;',
        'uniform int PaLobeShape;'                 : 'const int PaLobeShape = 0;', // T238 TEMP
"""),
])
edit(VOL + 'VolumetricCloudDebugConfig.java', [
    ("""    public static void setDiagnosticLightingMode(int mode) {""", """    private static volatile int lobeShapeMode; // T238 TEMP

    /** T238 TEMP. PaLobeShape: 0 production, 1 slab anvil + column tower. */
    public static int lobeShapeMode() {
        return lobeShapeMode;
    }

    public static void setLobeShapeMode(int mode) {
        lobeShapeMode = mode;
    }

    public static void setDiagnosticLightingMode(int mode) {"""),
])
edit(VOL + 'VolumetricCloudRenderer.java', [
    ("""        shader.safeGetUniform("PaLowerDeckRelief").set(VolumetricCloudDebugConfig.lowerDeckReliefMode());
""", """        shader.safeGetUniform("PaLowerDeckRelief").set(VolumetricCloudDebugConfig.lowerDeckReliefMode());
        shader.safeGetUniform("PaLobeShape").set(VolumetricCloudDebugConfig.lobeShapeMode()); // T238 TEMP
"""),
    ("""                && VolumetricCloudDebugConfig.diagnosticLightingMode() == 0
                // T232.""", """                && VolumetricCloudDebugConfig.diagnosticLightingMode() == 0
                && VolumetricCloudDebugConfig.lobeShapeMode() == 0 // T238 TEMP
                // T232."""),
])
print('ok')
