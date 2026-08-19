#version 150

// Independent Phase 4S equation fixture. This file intentionally does not
// include, preprocess, or delegate to the production storm shader or Java
// evaluator. Java supplies descriptor data only; every expected value is
// evaluated by this GLSL program in an RGBA32F framebuffer.
//
// It is written from contracts/storm-density-composition.md, not copied from
// either implementation, so agreement between the three is evidence rather
// than tautology.

const int FIXTURE_LOBES = 8;
const int FIXTURE_CASES = 8;
const float PI = 3.14159265358979323846;

// Derived constants, mirroring the contract. See
// specs/001-native-storm-rendering/validation/morphology-thresholds.md.
const float MIN_EDGE_BLOCKS = 11.363636;   // half the lowest detail wavelength
const float CAP_ROUNDING_FRACTION = 0.35;
const float LOBE_BLEND_FRACTION = 0.25;
const float GROUP_BLEND_FRACTION = 0.18;
const float MIN_BLEND_BLOCKS = 4.0;
const float MAX_BLEND_BLOCKS = 48.0;
const float CORE_FILL = 0.45;
const float EROSION = 0.44;

uniform vec4 FixturePositionHeight[FIXTURE_LOBES];
uniform vec4 FixtureRadiusRotation[FIXTURE_LOBES];
uniform vec4 FixtureShearMedia[FIXTURE_LOBES];
uniform vec4 FixtureMeta[FIXTURE_LOBES]; // x=group, y=role
uniform vec3 FixtureProbe[FIXTURE_CASES];
// x = base field, y = detail FBM. Supplied so the noise stages are exercised
// without this fixture needing the baked 3D volumes.
uniform vec2 FixtureNoise[FIXTURE_CASES];

out vec4 fragColor;

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
}

vec3 independentDomainWarp(vec3 p) {
    return vec3(
        sin(p.x * 0.00173 + p.y * 0.00091 - p.z * 0.00127 + 1.7),
        sin(-p.x * 0.00111 + p.y * 0.00149 + p.z * 0.00083 - 2.3),
        sin(p.x * 0.00079 - p.y * 0.00131 + p.z * 0.00191 + 4.1)
    );
}

void independentRoleProfile(
        float height01,
        int role,
        out float profileRadius,
        out float shearProgress) {
    if (role == 0) {
        profileRadius = mix(0.98, 0.52, height01)
            + 0.12 * pow(sin(PI * height01), 0.70);
        shearProgress = height01 * 0.12;
    } else if (role == 1) {
        profileRadius = mix(0.84, 0.56, height01)
            + 0.18 * pow(sin(PI * height01), 0.65);
        shearProgress = smoothstep(0.0, 1.0, height01) * 0.35;
    } else if (role == 2) {
        profileRadius = mix(0.74, 0.48, height01)
            + 0.22 * pow(sin(PI * height01), 0.65);
        shearProgress = pow(height01, 1.6);
    } else {
        profileRadius = mix(0.32, 1.0, smoothstep(0.0, 0.62, height01))
            + 0.08 * pow(sin(PI * height01), 0.55)
            - 0.10 * smoothstep(0.88, 1.0, height01);
        shearProgress = smoothstep(0.0, 0.65, height01);
    }
}

// Real signed geometric distance to the lobe surface, in world-space blocks.
// Valid outside the surface as well as inside it.
float independentLobeDistance(int descriptorIndex, vec3 p) {
    vec4 positionHeight = FixturePositionHeight[descriptorIndex];
    vec4 radiusRotation = FixtureRadiusRotation[descriptorIndex];
    vec4 shearMedia = FixtureShearMedia[descriptorIndex];
    int role = int(FixtureMeta[descriptorIndex].y + 0.5);

    float span = max(positionHeight.w - positionHeight.z, 1.0);
    float centreY = (positionHeight.z + positionHeight.w) * 0.5;
    float halfSpan = span * 0.5;
    float height01 = clamp((p.y - positionHeight.z) / span, 0.0, 1.0);

    float profileRadius;
    float shearProgress;
    independentRoleProfile(height01, role, profileRadius, shearProgress);

    vec2 local = p.xz - positionHeight.xy - shearMedia.xy * shearProgress;
    vec2 oriented = vec2(
        local.x * radiusRotation.w + local.y * radiusRotation.z,
        -local.x * radiusRotation.z + local.y * radiusRotation.w
    );
    vec2 radii = max(radiusRotation.xy * profileRadius, vec2(1.0));
    float radial = length(oriented / radii);
    float groupOffset = max(FixtureMeta[descriptorIndex].x, 0.0) * 997.0;
    vec3 warp = independentDomainWarp(
        p * 2.3 + vec3(groupOffset, groupOffset * 0.61, -groupOffset * 0.73)
    );
    radial += dot(warp, vec3(0.45, 0.20, 0.35)) * 0.08;

    float gradientLength = length(oriented / (radii * radii));
    float effectiveRadius = gradientLength > 1.0e-9
        ? radial / gradientLength
        : min(radii.x, radii.y);

    float wall = (radial - 1.0) * effectiveRadius;
    float cap = abs(p.y - centreY) - halfSpan;
    float rounding = min(min(effectiveRadius, halfSpan) * CAP_ROUNDING_FRACTION, MIN_EDGE_BLOCKS);
    float roundedWall = wall + rounding;
    float roundedCap = cap + rounding;
    vec2 outside = max(vec2(roundedWall, roundedCap), vec2(0.0));
    return length(outside) + min(max(roundedWall, roundedCap), 0.0) - rounding;
}

float independentEdgeWidth(int descriptorIndex) {
    int role = int(FixtureMeta[descriptorIndex].y + 0.5);
    float edgeSoftness = FixtureShearMedia[descriptorIndex].w;
    float normalized = role == 3
        ? max(0.12, edgeSoftness * 1.25)
        : max(0.06, edgeSoftness * 0.62);
    return max(
        MIN_EDGE_BLOCKS,
        normalized * min(
            FixtureRadiusRotation[descriptorIndex].x,
            FixtureRadiusRotation[descriptorIndex].y
        )
    );
}

float independentSmallerRadius(int descriptorIndex) {
    return min(
        FixtureRadiusRotation[descriptorIndex].x,
        FixtureRadiusRotation[descriptorIndex].y
    );
}

float independentBlend(float firstRadius, float secondRadius, float fraction) {
    float smaller = firstRadius <= 0.0 ? secondRadius : min(firstRadius, secondRadius);
    return clamp(smaller * fraction, MIN_BLEND_BLOCKS, MAX_BLEND_BLOCKS);
}

float independentBlendFactor(float first, float second, float blendRadius) {
    return saturate(0.5 + 0.5 * (second - first) / max(blendRadius, 0.0001));
}

float independentSmoothMinimum(float first, float second, float blendRadius) {
    float radius = max(blendRadius, 0.0001);
    float h = saturate(0.5 + 0.5 * (second - first) / radius);
    return mix(second, first, h) - radius * h * (1.0 - h);
}

// Group distance union with continuous material blending.
void independentGroupField(
        int groupSlot,
        vec3 p,
        out float groupDistance,
        out float groupStrength,
        out float groupSoftness,
        out float groupMinimumRadius,
        out bool found) {
    groupDistance = 1.0e9;
    groupStrength = 0.0;
    groupSoftness = 0.0;
    groupMinimumRadius = 1000000.0;
    found = false;
    float previousRadius = 0.0;
    for (int descriptorIndex = 0; descriptorIndex < FIXTURE_LOBES; descriptorIndex++) {
        if (int(FixtureMeta[descriptorIndex].x + 0.5) != groupSlot) {
            continue;
        }
        // No lobe is skipped for evaluating to zero density.
        float lobeDistance = independentLobeDistance(descriptorIndex, p);
        float lobeRadius = independentSmallerRadius(descriptorIndex);
        float lobeStrength = saturate(FixtureShearMedia[descriptorIndex].z);
        float lobeSoftness = independentEdgeWidth(descriptorIndex);
        if (!found) {
            groupDistance = lobeDistance;
            groupStrength = lobeStrength;
            groupSoftness = lobeSoftness;
            found = true;
        } else {
            float blend = independentBlend(previousRadius, lobeRadius, LOBE_BLEND_FRACTION);
            float mixFactor = independentBlendFactor(groupDistance, lobeDistance, blend);
            groupDistance = independentSmoothMinimum(groupDistance, lobeDistance, blend);
            groupStrength = mix(lobeStrength, groupStrength, mixFactor);
            groupSoftness = mix(lobeSoftness, groupSoftness, mixFactor);
        }
        previousRadius = lobeRadius;
        groupMinimumRadius = min(groupMinimumRadius, lobeRadius);
    }
}

// Stage 4: bounded coverage envelope. Never a visible density.
float independentCoverageEnvelope(vec3 p, int maximumGroup) {
    float stormDistance = 1.0e9;
    float stormStrength = 0.0;
    float stormSoftness = 0.0;
    float previousGroupRadius = 0.0;
    bool started = false;
    for (int groupSlot = 0; groupSlot < 2; groupSlot++) {
        if (groupSlot > maximumGroup) {
            break;
        }
        float groupDistance;
        float groupStrength;
        float groupSoftness;
        float groupMinimumRadius;
        bool found;
        independentGroupField(
            groupSlot, p,
            groupDistance, groupStrength, groupSoftness, groupMinimumRadius, found
        );
        if (!found) {
            continue;
        }
        if (!started) {
            stormDistance = groupDistance;
            stormStrength = groupStrength;
            stormSoftness = groupSoftness;
            started = true;
        } else {
            float blend = independentBlend(
                previousGroupRadius, groupMinimumRadius, GROUP_BLEND_FRACTION);
            float mixFactor = independentBlendFactor(stormDistance, groupDistance, blend);
            stormDistance = independentSmoothMinimum(stormDistance, groupDistance, blend);
            stormStrength = mix(groupStrength, stormStrength, mixFactor);
            stormSoftness = mix(groupSoftness, stormSoftness, mixFactor);
        }
        previousGroupRadius = groupMinimumRadius;
    }
    if (!started) {
        return 0.0;
    }
    float softness = max(stormSoftness, MIN_EDGE_BLOCKS);
    return saturate(
        (1.0 - smoothstep(-softness, softness, stormDistance)) * saturate(stormStrength)
    );
}

// Stages 5 and 6: base noise remapping, then multi-scale erosion. No
// edge-exposure gate and no erosion floor: the interior is eroded too.
float independentFinalDensity(float coverage, float baseField, float detailFbm) {
    if (coverage <= 0.0) {
        return 0.0;
    }
    float lowerBound = mix(1.0, -CORE_FILL, saturate(coverage));
    float body = saturate((baseField - lowerBound) / max(1.0 - lowerBound, 0.0001));
    return max(body - (1.0 - detailFbm) * EROSION, 0.0);
}

void main() {
    int fixtureCase = clamp(int(floor(gl_FragCoord.x)), 0, FIXTURE_CASES - 1);
    float result;
    if (fixtureCase < 4) {
        // Per-lobe geometric distance, in blocks.
        result = independentLobeDistance(fixtureCase, FixtureProbe[fixtureCase]);
    } else if (fixtureCase == 4) {
        result = independentCoverageEnvelope(FixtureProbe[fixtureCase], 0);
    } else if (fixtureCase == 5) {
        result = independentCoverageEnvelope(FixtureProbe[fixtureCase], 1);
    } else {
        // Full composition through the noise stages.
        int groups = fixtureCase == 6 ? 0 : 1;
        float coverage = independentCoverageEnvelope(FixtureProbe[fixtureCase], groups);
        result = independentFinalDensity(
            coverage,
            FixtureNoise[fixtureCase].x,
            FixtureNoise[fixtureCase].y
        );
    }
    fragColor = vec4(result, 0.0, 0.0, 1.0);
}
