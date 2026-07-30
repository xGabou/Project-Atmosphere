#version 150

#moj_import <projectatmosphere:cloud_weather_footprint.glsl>

// Auxiliary world-anchored map for morphology-driven volumetric profiles.
// R = categorical (cloud type profile * 8 + envelope role) / 63
// G = vertical development
// B = coupled condensed-water / material-darkness trait
// A = precipitation intensity

uniform vec2 WeatherOrigin;
uniform float WeatherExtent;
uniform float RegionalCoverage;
uniform float RegionalEnergy;
uniform float WeatherCoverageScale;
uniform float WorldTime;
uniform int CellCount;

const int MAX_CELLS = 96;
uniform vec4 CellPosRadius[MAX_CELLS];
uniform vec4 CellShape[MAX_CELLS];
uniform vec4 CellMedia[MAX_CELLS];
uniform vec4 CellMorphology[MAX_CELLS];
uniform vec4 CellDynamics[MAX_CELLS];

in vec2 texCoord;
out vec4 fragColor;

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm2(vec2 p) {
    return valueNoise(p) * 0.65
        + valueNoise(p * 2.13 + vec2(19.7)) * 0.35;
}

float profileWeight(float profile, float expected) {
    return 1.0 - smoothstep(0.20, 0.90, abs(profile - expected));
}

void main() {
    vec2 worldXZ = WeatherOrigin + texCoord * WeatherExtent;
    vec2 warp = vec2(
        fbm2(worldXZ * 0.010 + vec2(3.7, 9.1)),
        fbm2(worldXZ * 0.010 + vec2(-7.3, 1.9))
    ) - 0.5;

    vec3 traitsAccum = vec3(0.0);
    float weightAccum = 0.0;
    float dominantWeight = -1.0;
    float dominantProfile = 0.0;
    float dominantRole = 0.0;
    for (int i = 0; i < MAX_CELLS; i++) {
        if (i >= CellCount) {
            break;
        }
        vec4 posRadius = CellPosRadius[i];
        vec4 shape = CellShape[i];
        vec4 media = CellMedia[i];
        vec4 morphology = CellMorphology[i];
        vec4 dynamics = CellDynamics[i];
        float profile = floor(clamp(morphology.x, 0.0, 7.0) + 0.5);
        float packedProfileRole = clamp(dynamics.w, 0.0, 7.999);
        float envelopeRole = floor(fract(packedProfileRole) * 16.0 + 0.5);
        float stratus = profileWeight(profile, 1.0);
        float stratocumulus = profileWeight(profile, 2.0);
        float cumulus = profileWeight(profile, 3.0);
        float storm = max(profileWeight(profile, 4.0), profileWeight(profile, 7.0));
        float nimbostratus = profileWeight(profile, 5.0);
        float cirrus = profileWeight(profile, 6.0);
        float sheet = max(stratus, nimbostratus);

        float footprintScale = max(media.w, 0.001) * max(WeatherCoverageScale, 0.001);
        vec2 scaledRadius = max(posRadius.zw * footprintScale, vec2(1.0));
        // Keep categorical morphology aligned with the analytic coverage map.
        // Warp is reserved for bounded contour detail below.
        vec2 delta = worldXZ - posRadius.xy;
        float maxRadius = max(scaledRadius.x, scaledRadius.y) * 1.45;
        if (dot(delta, delta) > maxRadius * maxRadius) {
            continue;
        }

        float cosO = cos(-shape.x);
        float sinO = sin(-shape.x);
        vec2 local = vec2(
            delta.x * cosO - delta.y * sinO,
            delta.x * sinO + delta.y * cosO
        );
        vec2 normalized = local / scaledRadius;
        float r = length(normalized);
        float theta = atan(normalized.y, normalized.x);
        float seed = media.z * 6.2831853;
        float lobeStrength = 1.0;
        lobeStrength = mix(lobeStrength, 0.20, sheet);
        lobeStrength = mix(lobeStrength, 0.72, stratocumulus);
        lobeStrength = mix(lobeStrength, 0.22, cumulus);
        lobeStrength = mix(lobeStrength, 0.76, storm);
        lobeStrength = mix(lobeStrength, 0.34, cirrus);
        float lobes = 1.0
            + 0.16 * lobeStrength * sin(theta * 2.0 + seed * 3.1)
            + 0.11 * lobeStrength * sin(theta * 3.0 + seed * 7.7)
            + 0.07 * lobeStrength * sin(theta * 5.0 + seed * 13.9);
        r /= max(lobes, 0.35);
        r += paSevereContourErosion(
            warp,
            theta,
            seed,
            int(profile),
            int(envelopeRole),
            r
        );

        float edgeStart = mix(0.78, 0.42, saturate(shape.w));
        edgeStart = mix(edgeStart, 0.76, sheet);
        edgeStart = mix(edgeStart, 0.62, stratocumulus);
        edgeStart = mix(edgeStart, 0.58, cirrus);
        float footprint = 1.0 - smoothstep(edgeStart, 1.0, r);
        bool macroCarrier = dynamics.z < -0.5;
        bool envelopeOnly = macroCarrier && int(envelopeRole) == 6;
        float lifecycle = saturate(macroCarrier ? -dynamics.z - 1.0 : dynamics.z);
        float lifecycleEnvelope = lifecycle < 0.5
            ? mix(0.30, 1.0, lifecycle * 2.0)
            : mix(1.0, 0.30, (lifecycle - 0.5) * 2.0);
        float categoricalCoverage = envelopeOnly
            ? 0.0
            : footprint * saturate(media.x) * lifecycleEnvelope;
        // Match the primary WeatherMap occupancy decision exactly. Rain packs
        // a small extra horizontal support there; omitting it here left valid
        // weather texels without a categorical owner.
        float weatherCoverage = categoricalCoverage
            * (1.0 + saturate(dynamics.y) * 0.16);
        if (weatherCoverage <= 0.002) {
            continue;
        }

        // Category dominance intentionally uses the same un-packed cubic
        // weight as cloud_weather_splat. Presence is tracked independently
        // below, so a legitimate fringe weight is never mistaken for empty.
        float weight = categoricalCoverage * categoricalCoverage * categoricalCoverage;
        float localCondensate = saturate(
            morphology.w * 0.62
                + max(media.y, max(dynamics.x, dynamics.y)) * 0.38
        );
        float waterAndDarkness = saturate(
            morphology.y * 0.80 + localCondensate * 0.20
        );
        traitsAccum += vec3(
            saturate(morphology.z),
            waterAndDarkness,
            saturate(dynamics.y)
        ) * weight;
        if (weight > dominantWeight) {
            dominantWeight = weight;
            dominantProfile = profile;
            dominantRole = envelopeRole;
        }
        weightAccum += weight;
    }

    float regional = saturate(RegionalCoverage);
    if (regional > 0.01) {
        float sheetNoise = fbm2(worldXZ * 0.0011
            + vec2(WorldTime * 0.00021, WorldTime * 0.00013));
        float sheet = saturate((sheetNoise - (1.0 - regional * 0.9)) * 3.2) * regional;
        if (sheet > 0.002) {
            float weight = pow(sheet * 0.6, 3.0);
            traitsAccum += vec3(0.10, regional, RegionalEnergy) * weight;
            if (weight > dominantWeight) {
                dominantWeight = weight;
                dominantProfile = 1.0;
                dominantRole = 0.0;
            }
            weightAccum += weight;
        }
    }

    bool hasCategory = dominantWeight >= 0.0;
    vec3 traits = weightAccum > 0.0
        ? clamp(traitsAccum / weightAccum, 0.0, 1.0)
        : vec3(0.0);
    float categoricalCode = clamp(dominantProfile * 8.0 + dominantRole, 0.0, 63.0);
    // Zero is reserved for an empty texel. All 64 legitimate profile/role
    // combinations, including profile 0 / role 0, occupy codes 1..64. The
    // target is RGBA8, whose 256 levels leave almost four byte values per code.
    float encodedCategory = (categoricalCode + 1.0) / 64.0;
    fragColor = hasCategory
        ? vec4(encodedCategory, traits)
        : vec4(0.0);
}
