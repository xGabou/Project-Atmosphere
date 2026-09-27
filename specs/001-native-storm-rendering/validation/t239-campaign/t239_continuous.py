# T239 TEMP: continuous storm body (PaStormGeometry 1). Production 0 is baked
# into FINAL; any other value takes the monolith.
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


FIELD = r'''
// =====================================================================
// T239 TEMP. The continuous storm body.
//
// One continuous SDF per storm group instead of the ten-lobe union. The
// group's descriptors are read only as skeleton data: the storm floor, the
// base footprint, the updraft axis (tower lobe centres) and the deck extents
// (anvil lobes). Frame: origin at the lower updraft, u along the wind (tower
// centroid -> anvil centroid), c across it, y up.
//
//   base    flat-bottomed broad slab, superellipse footprint (n = 3) with
//           separate upwind/downwind and left/right extents, gentle taper;
//   updraft one column along the tower axis, radius Rt * f(h): 1.3 at the
//           base junction, 1.0 through the middle, widening into the deck;
//   deck    one sheet over the along-wind coordinate: compact upwind root,
//           long downwind reach, thickness thinning downwind, fan-shaped
//           cross width with rounded root and tip;
//   joins   smooth-min base|column (k 0.6 Rt) and column|deck (k 0.9 Rt).
//
// The edge band is a fixed physical width (T239 Task 1: a band proportional
// to lobe radius is what leaves a narrow lobe all edge), and the interior
// fill is owned explicitly: base, updraft and transition interiors take the
// structural-continuity fill, the deck the strength fill.
// =====================================================================
int paContinuousRegion = 0;
bool paContinuousInteriorFill = false;

float paContinuousSmin(float a, float b, float k, out float weightA) {
    weightA = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, weightA) - k * weightA * (1.0 - weightA);
}

void paContinuousStormGroupField(
        vec3 p,
        int witnessIndex,
        int groupSlot,
        out float groupDistance,
        out float groupMinimumRadius,
        out float groupStrength,
        out float groupSoftness,
        out float groupHeight01,
        out int groupActiveRoleMask,
        out bool ownsGroup,
        out float groupMinClearance) {
    int firstIndex = stormGroupFirstIndex(witnessIndex, groupSlot);
    int endIndex = stormGroupEndIndex(witnessIndex, groupSlot);
    float floorY = 1.0e9;
    float baseTop = -1.0e9;
    float deckBottom = 1.0e9;
    float deckTop = -1.0e9;
    vec2 towerLo = vec2(0.0);
    vec2 towerHi = vec2(0.0);
    float towerLoY = 1.0e9;
    float towerHiY = -1.0e9;
    float towerRadiusSum = 0.0;
    vec2 anvilSum = vec2(0.0);
    float anvilWeight = 0.0;
    float baseStrength = 0.0;
    float towerStrength = 0.0;
    float anvilStrength = 0.0;
    int baseCount = 0;
    int towerCount = 0;
    int anvilCount = 0;
    ownsGroup = false;
    for (int index = firstIndex; index < MAX_STORM_LOBES; index++) {
        if (index >= endIndex) {
            break;
        }
        vec4 t0 = stormDescriptorTexel(index, 0);
        vec4 t1 = stormDescriptorTexel(index, 1);
        vec4 t2 = stormDescriptorTexel(index, 2);
        vec4 t3 = stormDescriptorTexel(index, 3);
        if (t3.w < -0.5) {
            continue;
        }
        int packedTopology = int(floor(t3.w + 0.5));
        int role = packedTopology - (packedTopology / 8) * 8;
        vec2 ownershipCenter = t0.xy + t2.xy * 0.5;
        float extentX = length(vec2(t1.x * t1.w, t1.y * t1.z));
        float extentZ = length(vec2(t1.x * t1.z, t1.y * t1.w));
        vec2 ownershipRadii = max(vec2(extentX, extentZ) * 1.85, vec2(1.0));
        ownsGroup = ownsGroup || length((p.xz - ownershipCenter) / ownershipRadii) <= 1.0;
        if (role == 0) {
            floorY = min(floorY, t0.z);
            baseTop = max(baseTop, t0.w);
            baseStrength += saturate(t2.z);
            baseCount++;
        } else if (role == 2) {
            float mid = 0.5 * (t0.z + t0.w);
            if (mid < towerLoY) {
                towerLoY = mid;
                towerLo = t0.xy;
            }
            if (mid > towerHiY) {
                towerHiY = mid;
                towerHi = t0.xy;
            }
            towerRadiusSum += t1.x;
            towerStrength += saturate(t2.z);
            towerCount++;
        } else if (role == 3) {
            deckBottom = min(deckBottom, t0.z);
            deckTop = max(deckTop, t0.w);
            anvilSum += t0.xy * t1.x;
            anvilWeight += t1.x;
            anvilStrength += saturate(t2.z);
            anvilCount++;
        }
    }
    if (baseCount == 0 || towerCount == 0 || anvilCount == 0) {
        directStormGroupField(p, witnessIndex, groupSlot, groupDistance, groupMinimumRadius,
            groupStrength, groupSoftness, groupHeight01, groupActiveRoleMask, ownsGroup,
            groupMinClearance);
        return;
    }
    vec2 origin = towerLo;
    vec2 anvilCentre = anvilSum / max(anvilWeight, 1.0);
    vec2 windOffset = anvilCentre - 0.5 * (towerLo + towerHi);
    vec2 wind = length(windOffset) > 1.0 ? normalize(windOffset) : vec2(1.0, 0.0);
    vec2 crossDir = vec2(-wind.y, wind.x);

    // Extents in the wind frame.
    float baseUp = 1.0;
    float baseDown = 1.0;
    float baseLeft = 1.0;
    float baseRight = 1.0;
    float deckLo = 1.0e9;
    float deckHi = -1.0e9;
    float deckHalfWidth = 1.0;
    float deckCrossSum = 0.0;
    for (int index = firstIndex; index < MAX_STORM_LOBES; index++) {
        if (index >= endIndex) {
            break;
        }
        vec4 t0 = stormDescriptorTexel(index, 0);
        vec4 t1 = stormDescriptorTexel(index, 1);
        vec4 t3 = stormDescriptorTexel(index, 3);
        if (t3.w < -0.5) {
            continue;
        }
        int packedTopology = int(floor(t3.w + 0.5));
        int role = packedTopology - (packedTopology / 8) * 8;
        vec2 rel = t0.xy - origin;
        float lw = dot(rel, wind);
        float lc = dot(rel, crossDir);
        if (role == 0) {
            baseUp = max(baseUp, -(lw - 0.85 * t1.x));
            baseDown = max(baseDown, lw + 0.85 * t1.x);
            baseLeft = max(baseLeft, -(lc - 0.85 * t1.y));
            baseRight = max(baseRight, lc + 0.85 * t1.y);
        } else if (role == 3) {
            deckLo = min(deckLo, lw - 0.6 * t1.x);
            deckHi = max(deckHi, lw + 0.9 * t1.x);
            deckHalfWidth = max(deckHalfWidth, abs(lc) + 1.4 * t1.y);
            deckCrossSum += lc * t1.x;
        }
    }
    float deckCrossMean = deckCrossSum / max(anvilWeight, 1.0);

    vec2 rel = p.xz - origin;
    float w = dot(rel, wind);
    float c = dot(rel, crossDir);
    float towerRadius = 0.62 * towerRadiusSum / float(towerCount);

    // Base: broad, flat-bottomed, gently tapered slab.
    float baseHeight = max(baseTop - floorY, 1.0) * 0.85;
    float baseT = clamp((p.y - floorY) / baseHeight, 0.0, 1.0);
    float baseScale = 1.0 - 0.30 * baseT * baseT;
    float extentW = w > 0.0 ? baseDown : baseUp;
    float extentC = c > 0.0 ? baseRight : baseLeft;
    vec2 q = abs(vec2(w / extentW, c / extentC));
    float superRadius = pow(q.x * q.x * q.x + q.y * q.y * q.y, 1.0 / 3.0);
    float baseH = (superRadius - baseScale) * min(extentW, extentC);
    float baseV = max(floorY - p.y, p.y - (floorY + baseHeight));
    float baseRound = 0.30 * baseHeight;
    vec2 baseD = vec2(baseH, baseV) + baseRound;
    float dBase = length(max(baseD, 0.0)) + min(max(baseD.x, baseD.y), 0.0) - baseRound;

    // Updraft column.
    float deckMid = 0.5 * (deckBottom + deckTop);
    float axisT = clamp((p.y - towerLoY) / max(towerHiY - towerLoY, 1.0), 0.0, 1.0);
    vec2 axis = mix(towerLo, towerHi, axisT);
    float baseCrown = floorY + baseHeight;
    float columnT = (p.y - baseCrown) / max(deckBottom - baseCrown, 1.0);
    float columnScale = 1.0
        + 0.30 * (1.0 - smoothstep(-0.4, 0.25, columnT))
        + 0.45 * smoothstep(0.5, 1.2, columnT);
    float dColumn = max(length(p.xz - axis) - towerRadius * columnScale,
        max(floorY + 0.10 * baseHeight - p.y, p.y - deckMid));

    // Deck sheet.
    float deckLength = max(deckHi - deckLo, 1.0);
    float ud = clamp((w - deckLo) / deckLength, 0.0, 1.0);
    float deckSpan = max(deckTop - deckBottom, 20.0);
    float thickness = deckSpan * (1.15 - 0.55 * ud);
    float top = deckTop - 0.05 * deckSpan * ud;
    float bottom = top - thickness;
    float halfWidth = mix(1.3 * towerRadius, 0.85 * deckHalfWidth, smoothstep(0.0, 0.6, ud));
    float tip = smoothstep(0.7, 1.0, ud);
    float root = 1.0 - smoothstep(0.0, 0.15, ud);
    halfWidth *= sqrt(max(1.0 - 0.85 * tip * tip, 0.0)) * sqrt(max(1.0 - 0.6 * root * root, 0.0));
    float centreLine = deckCrossMean * ud;
    vec3 deckD = vec3(
        max(deckLo - w, w - deckHi),
        abs(c - centreLine) - halfWidth,
        max(bottom - p.y, p.y - top));
    float deckRound = min(0.35 * thickness, 40.0);
    deckD += deckRound;
    float dDeck = length(max(deckD, 0.0)) + min(max(deckD.x, max(deckD.y, deckD.z)), 0.0) - deckRound;

    // Joins.
    float baseWeight;
    float lower = paContinuousSmin(dBase, dColumn, 0.6 * towerRadius, baseWeight);
    float lowerWeight;
    float d = paContinuousSmin(lower, dDeck, 0.9 * towerRadius, lowerWeight);

    int region;
    float heightUpdraft = saturate((p.y - floorY) / max(deckMid - floorY, 1.0));
    float heightDeck = saturate((p.y - bottom) / max(thickness, 1.0));
    if (lowerWeight < 0.15) {
        region = 8;
        groupHeight01 = heightDeck;
    } else if (lowerWeight < 0.85) {
        region = 2;
        groupHeight01 = mix(heightDeck, heightUpdraft, lowerWeight);
    } else if (baseWeight >= 0.5) {
        region = 1;
        groupHeight01 = baseT;
    } else {
        region = 4;
        groupHeight01 = heightUpdraft;
    }
    float strengthBase = baseStrength / float(baseCount);
    float strengthTower = towerStrength / float(towerCount);
    float strengthAnvil = anvilStrength / float(anvilCount);
    groupStrength = mix(strengthAnvil, mix(strengthTower, strengthBase, baseWeight), lowerWeight);
    float softness = clamp(0.20 * towerRadius, 24.0, 48.0);
    groupSoftness = mix(min(softness, 0.30 * thickness), softness, lowerWeight);
    groupDistance = d;
    groupMinimumRadius = towerRadius;
    groupActiveRoleMask = d <= groupSoftness ? region : 0;
    float clearance = d - groupSoftness;
    groupMinClearance = clearance > 0.0 ? clearance * 0.8 : clearance;
    paContinuousRegion = region;
    paContinuousInteriorFill = region != 8 && d < -groupSoftness;
}

// Candidate witnesses identify only bounded complete groups.'''

edit(SH + 'cloud_atmosphere_volume.fsh', [
    ("""uniform int PaLowerDeckRelief;
""", """uniform int PaLowerDeckRelief;
// T239 TEMP. Storm geometry: 0 the role-lobe union, 1 the continuous storm
// body. FINAL bakes 0.
uniform int PaStormGeometry;
"""),
    ("""
// Candidate witnesses identify only bounded complete groups.""", FIELD),
    ("""        bool ownsGroup;
        float groupMinClearance;
        directStormGroupField(
            p, witnessIndex, groupSlot,
            groupDistance, groupMinimumRadius, groupStrength, groupSoftness,
            groupHeight01, groupActiveRoleMask, ownsGroup, groupMinClearance
        );
#ifdef PA_ARM_CLEARANCE_FIRST_GROUP""", """        bool ownsGroup;
        float groupMinClearance;
        if (PaStormGeometry == 1) {
            paContinuousStormGroupField(
                p, witnessIndex, groupSlot,
                groupDistance, groupMinimumRadius, groupStrength, groupSoftness,
                groupHeight01, groupActiveRoleMask, ownsGroup, groupMinClearance
            );
        } else {
        directStormGroupField(
            p, witnessIndex, groupSlot,
            groupDistance, groupMinimumRadius, groupStrength, groupSoftness,
            groupHeight01, groupActiveRoleMask, ownsGroup, groupMinClearance
        );
        }
#ifdef PA_ARM_CLEARANCE_FIRST_GROUP"""),
    ("""    if (PaMacroEnvelope != 0 && coverage > 0.12 && coverage < 0.98) {""",
     """    if (PaMacroEnvelope != 0 && PaStormGeometry == 0 && coverage > 0.12 && coverage < 0.98) {"""),
    ("""    bool embeddedConvectiveOverlap = (activeRoleMask & 7) == 7;""",
     """    bool embeddedConvectiveOverlap = PaStormGeometry == 1
        ? paContinuousInteriorFill : (activeRoleMask & 7) == 7;"""),
    ("""                (directStormActiveRoleMask & 7) == 7)""",
     """                PaStormGeometry == 1 ? paContinuousInteriorFill
                    : (directStormActiveRoleMask & 7) == 7)"""),
    ("""            bool embeddedConvectiveOverlap = (directStormActiveRoleMask & 7) == 7;""",
     """            bool embeddedConvectiveOverlap = PaStormGeometry == 1
                ? paContinuousInteriorFill : (directStormActiveRoleMask & 7) == 7;"""),
    ("""            if (PaMacroEnvelope != 0 && cloud > 0.12 && cloud < 0.98) {""",
     """            if (PaMacroEnvelope != 0 && PaStormGeometry == 0 && cloud > 0.12 && cloud < 0.98) {"""),
])
edit(SH + 'cloud_atmosphere_volume.json', [
    ("""    { "name": "PaLowerDeckRelief", "type": "int", "count": 1, "values": [ 1 ] },
""", """    { "name": "PaLowerDeckRelief", "type": "int", "count": 1, "values": [ 1 ] },
    { "name": "PaStormGeometry", "type": "int", "count": 1, "values": [ 0 ] },
"""),
])
edit('build.gradle', [
    ("""        'uniform int PaLowerDeckRelief;'           : 'const int PaLowerDeckRelief = 1;',
""", """        'uniform int PaLowerDeckRelief;'           : 'const int PaLowerDeckRelief = 1;',
        'uniform int PaStormGeometry;'             : 'const int PaStormGeometry = 0;', // T239 TEMP
"""),
])
edit(VOL + 'VolumetricCloudDebugConfig.java', [
    ("""    public static void setDiagnosticLightingMode(int mode) {""", """    private static volatile int stormGeometryMode; // T239 TEMP

    /** T239 TEMP. PaStormGeometry: 0 role-lobe union, 1 continuous storm body. */
    public static int stormGeometryMode() {
        return stormGeometryMode;
    }

    public static void setStormGeometryMode(int mode) {
        stormGeometryMode = mode;
    }

    public static void setDiagnosticLightingMode(int mode) {"""),
])
edit(VOL + 'VolumetricCloudRenderer.java', [
    ("""        shader.safeGetUniform("PaLowerDeckRelief").set(VolumetricCloudDebugConfig.lowerDeckReliefMode());
""", """        shader.safeGetUniform("PaLowerDeckRelief").set(VolumetricCloudDebugConfig.lowerDeckReliefMode());
        shader.safeGetUniform("PaStormGeometry").set(VolumetricCloudDebugConfig.stormGeometryMode()); // T239 TEMP
"""),
    ("""                && VolumetricCloudDebugConfig.diagnosticLightingMode() == 0
                // T232.""", """                && VolumetricCloudDebugConfig.diagnosticLightingMode() == 0
                && VolumetricCloudDebugConfig.stormGeometryMode() == 0 // T239 TEMP
                // T232."""),
])
print('ok')
