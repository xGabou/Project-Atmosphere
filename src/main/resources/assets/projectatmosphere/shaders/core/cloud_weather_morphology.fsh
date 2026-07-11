#version 150

// Auxiliary world-anchored map for morphology-driven volumetric profiles.
// R = cloud type profile / 7
// G = vertical development
// B = humidity influence
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

void main() {
    vec2 worldXZ = WeatherOrigin + texCoord * WeatherExtent;
    vec2 warp = vec2(
        fbm2(worldXZ * 0.010 + vec2(3.7, 9.1)),
        fbm2(worldXZ * 0.010 + vec2(-7.3, 1.9))
    ) - 0.5;
    vec2 warpedXZ = worldXZ + warp * 42.0;

    vec4 morphologyAccum = vec4(0.0);
    float weightAccum = 0.0;
    for (int i = 0; i < MAX_CELLS; i++) {
        if (i >= CellCount) {
            break;
        }
        vec4 posRadius = CellPosRadius[i];
        vec4 shape = CellShape[i];
        vec4 media = CellMedia[i];
        vec4 morphology = CellMorphology[i];
        vec4 dynamics = CellDynamics[i];

        float footprintScale = max(media.w, 0.001) * max(WeatherCoverageScale, 0.001);
        vec2 scaledRadius = max(posRadius.zw * footprintScale, vec2(1.0));
        vec2 delta = warpedXZ - posRadius.xy;
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
        float lobes = 1.0
            + 0.16 * sin(theta * 2.0 + seed * 3.1)
            + 0.11 * sin(theta * 3.0 + seed * 7.7)
            + 0.07 * sin(theta * 5.0 + seed * 13.9);
        r /= max(lobes, 0.35);

        float edgeStart = mix(0.78, 0.42, saturate(shape.w));
        float footprint = 1.0 - smoothstep(edgeStart, 1.0, r);
        float lifecycle = saturate(dynamics.z);
        float lifecycleEnvelope = lifecycle < 0.5
            ? mix(0.30, 1.0, lifecycle * 2.0)
            : mix(1.0, 0.30, (lifecycle - 0.5) * 2.0);
        float coverage = footprint * saturate(media.x) * lifecycleEnvelope;
        if (coverage <= 0.002) {
            continue;
        }

        float weight = coverage * coverage * coverage;
        vec4 encodedMorphology = vec4(
            clamp(morphology.x / 7.0, 0.0, 1.0),
            saturate(morphology.z),
            saturate(morphology.w),
            saturate(dynamics.y)
        );
        morphologyAccum += encodedMorphology * weight;
        weightAccum += weight;
    }

    float regional = saturate(RegionalCoverage);
    if (regional > 0.01) {
        float sheetNoise = fbm2(worldXZ * 0.0011
            + vec2(WorldTime * 0.00021, WorldTime * 0.00013));
        float sheet = saturate((sheetNoise - (1.0 - regional * 0.9)) * 3.2) * regional;
        if (sheet > 0.002) {
            float weight = pow(sheet * 0.6, 3.0);
            morphologyAccum += vec4(1.0 / 7.0, 0.10, regional, RegionalEnergy) * weight;
            weightAccum += weight;
        }
    }

    fragColor = weightAccum > 0.0000005
        ? clamp(morphologyAccum / weightAccum, 0.0, 1.0)
        : vec4(0.0);
}
