# T240: the continuous storm body becomes the production storm geometry.
import sys
P = r'C:\Users\matga\Downloads\Project-Atmosphere\src\main\resources\assets\projectatmosphere\shaders\core\cloud_atmosphere_volume.fsh'
raw = open(P, encoding='utf-8', newline='').read()
crlf = '\r\n' in raw
s = raw.replace('\r\n', '\n')


def rep(a, b, n=1):
    global s
    assert s.count(a) == n, (a[:90], s.count(a))
    s = s.replace(a, b)


BODY = r'''
// =====================================================================
// T240. The continuous storm body - the production storm geometry (T239
// accepted it; see validation/t239-continuous-storm-body.md).
//
// One continuous SDF per storm group, evaluated from the group's skeleton
// block: two descriptor-texture rows per group slot after the MAX_STORM_LOBES
// descriptor rows, derived once on the CPU (StormBodySkeleton) from the
// group's descriptors. A sample reads ten texels instead of walking ten lobes.
// Mirrored by StormBodyEvaluator - keep the two in step.
//
//   base    flat-bottomed slab (0.75 of the BASE lobes' height), footprint a
//           superellipse (n = 3) over the separate upwind / downwind / left /
//           right reach, radius scale 1 - 0.20 t^2, corners rounded 0.18 h;
//   updraft one column along the leaning tower axis, radius
//           Rt * (1 + 0.30 (1 - smoothstep(-0.4, 0.25, t))
//                   + 0.30 smoothstep(0.5, 1.2, t));
//   deck    one flat-topped sheet over the along-wind coordinate: thickness
//           span * (0.85 - 0.45 u), half-width fanning from 1.3 Rt to the
//           deck half-width by u = 0.45, rounded root and tip, cross drift;
//   joins   smooth minimum base|column then |deck, k = 0.6 Rt.
//
// The base and deck cross terms are divided by their analytic gradient
// magnitude, so the result is a first-order distance: the march's safe
// advance and the shared field's dilation rely on it (Lipschitz bounds below,
// checked by the geometry sandbox).
//
// Regions (mode 17): 1 base, 2 transition, 4 updraft, 8 anvil deck. The edge
// band is a fixed physical width (T239 Task 1) and the interior fill is owned
// here: base, updraft and transition interiors take the structural-continuity
// fill, the deck the strength fill.
// =====================================================================
const float STORM_BODY_LIPSCHITZ_HORIZONTAL = 1.4;
const float STORM_BODY_LIPSCHITZ_VERTICAL = 1.5;
int paBodyRegion = 0;
bool paBodyInteriorFill = false;

vec4 stormBodyTexel(int groupSlot, int texelIndex) {
    int row = MAX_STORM_LOBES + 2 * groupSlot + texelIndex / 5;
    return texelFetch(StormDescriptorSampler, ivec2(texelIndex - (texelIndex / 5) * 5, row), 0);
}

float stormBodyDeckHalfWidth(float ud, float towerRadius, float halfWidthMax) {
    float halfWidth = mix(1.3 * towerRadius, halfWidthMax, smoothstep(0.0, 0.45, ud));
    float tip = smoothstep(0.78, 1.0, ud);
    float root = 1.0 - smoothstep(0.0, 0.15, ud);
    return halfWidth * sqrt(max(1.0 - 0.85 * tip * tip, 0.0))
        * sqrt(max(1.0 - 0.6 * root * root, 0.0));
}

float stormBodySminWeight(float a, float b, float k) {
    return clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
}

float stormBodySmin(float a, float b, float k, float weightA) {
    return mix(b, a, weightA) - k * weightA * (1.0 - weightA);
}

// Distance from p to the group's conservative AABB (0 inside).
float stormBodyAabbDistance(vec3 p, vec4 aabbXZ, vec4 aabbY) {
    vec3 lo = vec3(aabbXZ.x, aabbY.x, aabbXZ.y);
    vec3 hi = vec3(aabbXZ.z, aabbY.y, aabbXZ.w);
    return length(max(max(lo - p, p - hi), vec3(0.0)));
}

void stormBodyGroupField(
        vec3 p,
        int groupSlot,
        out float groupDistance,
        out float groupMinimumRadius,
        out float groupStrength,
        out float groupSoftness,
        out float groupHeight01,
        out int groupActiveRoleMask,
        out bool ownsGroup,
        out float groupMinClearance) {
    groupDistance = 1.0e9;
    groupMinimumRadius = 1000000.0;
    groupStrength = 0.0;
    groupSoftness = 0.0;
    groupHeight01 = 0.0;
    groupActiveRoleMask = 0;
    ownsGroup = false;
    groupMinClearance = 1.0e9;
    paBodyRegion = 0;
    paBodyInteriorFill = false;
    vec4 a4 = stormBodyTexel(groupSlot, 4);
    if (a4.w < 0.0) {
        return;
    }
    vec4 a0 = stormBodyTexel(groupSlot, 0);
    vec4 a1 = stormBodyTexel(groupSlot, 1);
    vec4 b3 = stormBodyTexel(groupSlot, 8);
    vec4 b4 = stormBodyTexel(groupSlot, 9);
    vec2 rel = p.xz - a0.xy;
    float band = a1.w;
    float towerRadius = a4.z;
    // Ownership: within the base's own reach of the updraft (the legacy
    // family fallback is suppressed there).
    vec4 b2 = stormBodyTexel(groupSlot, 7);
    ownsGroup = length(rel) <= 1.85 * b2.y;
    groupMinimumRadius = towerRadius;
    // Conservative early out: the body and its band lie inside the AABB.
    float aabbDistance = stormBodyAabbDistance(p, b3, b4) - paStormFieldRejectMargin;
    if (aabbDistance > band) {
        groupDistance = aabbDistance;
        groupSoftness = band;
        groupMinClearance = aabbDistance - band;
        return;
    }
    vec4 a2 = stormBodyTexel(groupSlot, 2);
    vec4 a3 = stormBodyTexel(groupSlot, 3);
    vec4 b0 = stormBodyTexel(groupSlot, 5);
    vec4 b1 = stormBodyTexel(groupSlot, 6);
    vec2 wind = a0.zw;
    float w = dot(rel, wind);
    float c = dot(rel, vec2(-wind.y, wind.x));
    float floorY = a1.x;
    float baseHeight = a1.y;
    float deckMid = a1.z;

    // Base.
    float baseT = clamp((p.y - floorY) / baseHeight, 0.0, 1.0);
    float baseScale = 1.0 - 0.20 * baseT * baseT;
    float extentW = w > 0.0 ? a2.y : a2.x;
    float extentC = c > 0.0 ? a2.w : a2.z;
    vec2 q = abs(vec2(w / extentW, c / extentC));
    float superRadius = pow(q.x * q.x * q.x + q.y * q.y * q.y, 1.0 / 3.0);
    float baseLimit = -baseScale * max(extentW, extentC);
    float baseH = baseLimit;
    if (superRadius >= 1.0e-4) {
        float inv = 1.0 / (superRadius * superRadius);
        vec2 gradient = vec2(q.x * q.x * inv / extentW, q.y * q.y * inv / extentC);
        baseH = max((superRadius - baseScale) / max(length(gradient), 1.0e-6), baseLimit);
    }
    float baseV = max(floorY - p.y, p.y - (floorY + baseHeight));
    float baseRound = 0.18 * baseHeight;
    vec2 baseD = vec2(baseH, baseV) + baseRound;
    float dBase = length(max(baseD, 0.0)) + min(max(baseD.x, baseD.y), 0.0) - baseRound;

    // Updraft column.
    float axisT = clamp((p.y - a4.x) / max(a4.y - a4.x, 1.0), 0.0, 1.0);
    vec2 axis = mix(a3.xy, a3.zw, axisT);
    float deckBottom = b1.x;
    float deckTop = b1.y;
    float crown = floorY + baseHeight;
    float columnT = (p.y - crown) / max(deckBottom - crown, 1.0);
    float columnScale = 1.0
        + 0.30 * (1.0 - smoothstep(-0.4, 0.25, columnT))
        + 0.30 * smoothstep(0.5, 1.2, columnT);
    float dColumn = max(length(p.xz - axis) - towerRadius * columnScale,
        max(floorY + 0.10 * baseHeight - p.y, p.y - deckMid));

    // Deck sheet.
    float deckLength = max(b0.y - b0.x, 1.0);
    float ud = clamp((w - b0.x) / deckLength, 0.0, 1.0);
    float span = max(deckTop - deckBottom, 20.0);
    float thickness = span * (0.85 - 0.45 * ud);
    float top = deckTop - 0.05 * span * ud;
    float bottom = top - thickness;
    float halfWidth = stormBodyDeckHalfWidth(ud, towerRadius, b0.z);
    float crossOffset = c - b0.w * ud;
    float widthSlope = (stormBodyDeckHalfWidth(min(ud + 0.01, 1.0), towerRadius, b0.z)
        - stormBodyDeckHalfWidth(max(ud - 0.01, 0.0), towerRadius, b0.z)) / (0.02 * deckLength);
    float crossSlope = -sign(crossOffset) * b0.w / deckLength - widthSlope;
    float dCross = (abs(crossOffset) - halfWidth) / sqrt(1.0 + crossSlope * crossSlope);
    float deckRound = min(0.20 * thickness, 20.0);
    vec3 deckD = vec3(max(b0.x - w, w - b0.y), dCross, max(bottom - p.y, p.y - top)) + deckRound;
    float dDeck = length(max(deckD, 0.0))
        + min(max(deckD.x, max(deckD.y, deckD.z)), 0.0) - deckRound;

    // Joins.
    float k = 0.6 * towerRadius;
    float baseWeight = stormBodySminWeight(dBase, dColumn, k);
    float lower = stormBodySmin(dBase, dColumn, k, baseWeight);
    float lowerWeight = stormBodySminWeight(lower, dDeck, k);
    float d = stormBodySmin(lower, dDeck, k, lowerWeight);

    int region;
    float heightUpdraft = clamp((p.y - floorY) / max(deckMid - floorY, 1.0), 0.0, 1.0);
    float heightDeck = clamp((p.y - bottom) / max(thickness, 1.0), 0.0, 1.0);
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
    groupStrength = mix(b2.x, mix(b1.w, b1.z, baseWeight), lowerWeight);
    groupSoftness = mix(min(band, 0.30 * thickness), band, lowerWeight);
    groupDistance = d;
    groupActiveRoleMask = d <= groupSoftness ? region : 0;
    float clearance = d - groupSoftness;
    groupMinClearance = clearance > 0.0 ? clearance * 0.8 : clearance;
    paBodyRegion = region;
    paBodyInteriorFill = region != 8 && d < -groupSoftness;
    if (paStormFieldBuildNode) {
        paLastStormMaxVerticalSlope = max(paLastStormMaxVerticalSlope,
            STORM_BODY_LIPSCHITZ_VERTICAL);
        paLastStormMaxAspect = max(paLastStormMaxAspect, STORM_BODY_LIPSCHITZ_HORIZONTAL);
    }
}

// T240. The segment test for a body group: does the segment touch the
// group's conservative AABB (slab test)?
bool stormBodySegmentMayIntersect(vec3 segmentStart, vec3 segmentEnd, int groupSlot) {
    vec4 validity = stormBodyTexel(groupSlot, 4);
    if (validity.w < 0.0) {
        return false;
    }
    vec4 xz = stormBodyTexel(groupSlot, 8);
    vec4 yRange = stormBodyTexel(groupSlot, 9);
    vec3 lo = vec3(xz.x, yRange.x, xz.y);
    vec3 hi = vec3(xz.z, yRange.y, xz.w);
    vec3 direction = segmentEnd - segmentStart;
    vec3 inverse = 1.0 / (abs(direction) + vec3(1.0e-6)) * sign(direction + vec3(1.0e-9));
    vec3 t0 = (lo - segmentStart) * inverse;
    vec3 t1 = (hi - segmentStart) * inverse;
    vec3 tMin = min(t0, t1);
    vec3 tMax = max(t0, t1);
    float enter = max(max(tMin.x, tMin.y), max(tMin.z, 0.0));
    float exit = min(min(tMax.x, tMax.y), min(tMax.z, 1.0));
    return enter <= exit;
}

// T240. Whether the T213 macro envelope / the T230 lower-deck relief act on
// this storm body. The legacy lobe union (monolith A/B only) always had both.
bool paStormMacroAllowed() {
#ifdef PA_LEGACY_LOBE_UNION
    if (PaStormGeometry == 0) {
        return true;
    }
#endif
    return PaBodyMacro >= 1;
}

bool paStormReliefAllowed() {
#ifdef PA_LEGACY_LOBE_UNION
    if (PaStormGeometry == 0) {
        return true;
    }
#endif
    return PaBodyMacro >= 2;
}

// Candidate witnesses identify only bounded complete groups.'''

# 1 define
rep("""// T239. The experimental continuous storm body (PaStormGeometry) exists in
// the monolith only: build.gradle strips this define from every lean program,
// so FINAL and the rain programs compile exactly as without it.
#define PA_CONTINUOUS_STORM_BODY 1
""", """// T240. The legacy role-lobe union survives only in the monolith, for
// same-storm A/B against the continuous body (PaStormGeometry 0). build.gradle
// strips this define from every lean program, so FINAL and the rain programs
// carry the continuous body alone.
#define PA_LEGACY_LOBE_UNION 1
""")
# 2 uniforms
rep("""#ifdef PA_CONTINUOUS_STORM_BODY
// T239 (experimental). Storm geometry: 0 the role-lobe union (production), 1
// the continuous storm body. Monolith only: PA_CONTINUOUS_STORM_BODY is a
// leanFinalStrippedDefines entry, so no lean program contains any of it.
uniform int PaStormGeometry;
#endif
""", """#ifdef PA_LEGACY_LOBE_UNION
// T240. Monolith A/B only: 0 the legacy role-lobe union, 1 the continuous
// storm body (production).
uniform int PaStormGeometry;
#endif
// T240. Macro layering on the continuous body: 0 none (the T239 clean body),
// 1 the T213 macro envelope, 2 envelope + the T230 lower-deck relief.
uniform int PaBodyMacro;
""")
# 3 function block
i = s.index("\n#ifdef PA_CONTINUOUS_STORM_BODY\n// =====")
j = s.index("// Candidate witnesses identify only bounded complete groups.", i)
s = s[:i] + BODY + s[j + len("// Candidate witnesses identify only bounded complete groups."):]
# 4 call site
rep("""#ifdef PA_CONTINUOUS_STORM_BODY
        if (PaStormGeometry != 0) {
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
#else
        directStormGroupField(
            p, witnessIndex, groupSlot,
            groupDistance, groupMinimumRadius, groupStrength, groupSoftness,
            groupHeight01, groupActiveRoleMask, ownsGroup, groupMinClearance
        );
#endif
""", """#ifdef PA_LEGACY_LOBE_UNION
        if (PaStormGeometry == 0) {
            directStormGroupField(
                p, witnessIndex, groupSlot,
                groupDistance, groupMinimumRadius, groupStrength, groupSoftness,
                groupHeight01, groupActiveRoleMask, ownsGroup, groupMinClearance
            );
        } else {
            stormBodyGroupField(
                p, groupSlot,
                groupDistance, groupMinimumRadius, groupStrength, groupSoftness,
                groupHeight01, groupActiveRoleMask, ownsGroup, groupMinClearance
            );
        }
#else
        stormBodyGroupField(
            p, groupSlot,
            groupDistance, groupMinimumRadius, groupStrength, groupSoftness,
            groupHeight01, groupActiveRoleMask, ownsGroup, groupMinClearance
        );
#endif
""")
# 5 macro gates
rep("""#ifdef PA_CONTINUOUS_STORM_BODY
    if (PaMacroEnvelope != 0 && PaStormGeometry == 0 && coverage > 0.12 && coverage < 0.98) {
#else
    if (PaMacroEnvelope != 0 && coverage > 0.12 && coverage < 0.98) {
#endif""", """    if (PaMacroEnvelope != 0 && paStormMacroAllowed() && coverage > 0.12 && coverage < 0.98) {""")
rep("""#ifdef PA_CONTINUOUS_STORM_BODY
            if (PaMacroEnvelope != 0 && PaStormGeometry == 0 && cloud > 0.12 && cloud < 0.98) {
#else
            if (PaMacroEnvelope != 0 && cloud > 0.12 && cloud < 0.98) {
#endif""", """            if (PaMacroEnvelope != 0 && paStormMacroAllowed() && cloud > 0.12 && cloud < 0.98) {""")
# 6 relief gates
rep("""        if (PaLowerDeckRelief != 0) {
            deckWeight += stormLowerDeckWeight(dominantHeight01, activeRoleMask);
        }""", """        if (PaLowerDeckRelief != 0 && paStormReliefAllowed()) {
            deckWeight += stormLowerDeckWeight(dominantHeight01, activeRoleMask);
        }""")
rep("""            paLastLowerDeckWeight = stormLowerDeckWeight(
                directStormHeight01, directStormActiveRoleMask);""", """            paLastLowerDeckWeight = paStormReliefAllowed()
                ? stormLowerDeckWeight(directStormHeight01, directStormActiveRoleMask)
                : 0.0;""")
# 7 overlap -> body fill
rep("""#ifdef PA_CONTINUOUS_STORM_BODY
    bool embeddedConvectiveOverlap = PaStormGeometry != 0
        ? paContinuousInteriorFill : (activeRoleMask & 7) == 7;
#else
    bool embeddedConvectiveOverlap = (activeRoleMask & 7) == 7;
#endif""", """#ifdef PA_LEGACY_LOBE_UNION
    bool embeddedConvectiveOverlap = PaStormGeometry == 0
        ? (activeRoleMask & 7) == 7 : paBodyInteriorFill;
#else
    bool embeddedConvectiveOverlap = paBodyInteriorFill;
#endif""")
rep("""#ifdef PA_CONTINUOUS_STORM_BODY
        paLastStormLowerBound = directStormAvailable && directStormCoverage > 0.001
            ? stormCoverageLowerBound(directStormCoverage, directStormStrength,
                PaStormGeometry != 0 ? paContinuousInteriorFill
                    : (directStormActiveRoleMask & 7) == 7)
            : 1.0;
#else
        paLastStormLowerBound = directStormAvailable && directStormCoverage > 0.001
            ? stormCoverageLowerBound(directStormCoverage, directStormStrength,
                (directStormActiveRoleMask & 7) == 7)
            : 1.0;
#endif""", """#ifdef PA_LEGACY_LOBE_UNION
        paLastStormLowerBound = directStormAvailable && directStormCoverage > 0.001
            ? stormCoverageLowerBound(directStormCoverage, directStormStrength,
                PaStormGeometry == 0 ? (directStormActiveRoleMask & 7) == 7
                    : paBodyInteriorFill)
            : 1.0;
#else
        paLastStormLowerBound = directStormAvailable && directStormCoverage > 0.001
            ? stormCoverageLowerBound(directStormCoverage, directStormStrength,
                paBodyInteriorFill)
            : 1.0;
#endif""")
rep("""#ifdef PA_CONTINUOUS_STORM_BODY
            bool embeddedConvectiveOverlap = PaStormGeometry != 0
                ? paContinuousInteriorFill : (directStormActiveRoleMask & 7) == 7;
#else
            bool embeddedConvectiveOverlap = (directStormActiveRoleMask & 7) == 7;
#endif""", """#ifdef PA_LEGACY_LOBE_UNION
            bool embeddedConvectiveOverlap = PaStormGeometry == 0
                ? (directStormActiveRoleMask & 7) == 7 : paBodyInteriorFill;
#else
            bool embeddedConvectiveOverlap = paBodyInteriorFill;
#endif""")
# 8 segment test
rep("""#ifdef PA_CONTINUOUS_STORM_BODY
    if (PaStormGeometry != 0) {
        return true; // T239: the continuous body is not bounded by lobe spheres
    }
#endif
""", "")
rep("""            if (stormGroupSegmentMayIntersect(
                    segmentStart, segmentEnd, witnessIndex, groupSlot)) {""", """#ifdef PA_LEGACY_LOBE_UNION
            bool paGroupHit = PaStormGeometry == 0
                ? stormGroupSegmentMayIntersect(segmentStart, segmentEnd, witnessIndex, groupSlot)
                : stormBodySegmentMayIntersect(segmentStart, segmentEnd, groupSlot);
#else
            bool paGroupHit = stormBodySegmentMayIntersect(segmentStart, segmentEnd, groupSlot);
#endif
            if (paGroupHit) {""")
assert 'PA_CONTINUOUS_STORM_BODY' not in s and 'paContinuous' not in s
if crlf:
    s = s.replace('\n', '\r\n')
open(P, 'w', encoding='utf-8', newline='').write(s)
print('ok')
