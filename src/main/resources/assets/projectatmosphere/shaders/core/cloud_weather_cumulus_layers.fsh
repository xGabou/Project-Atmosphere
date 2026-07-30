#version 150

#moj_import <projectatmosphere:cloud_weather_footprint.glsl>

// Native profile-3 structured maps (RGBA16F):
// OutputMode 0 = BASE/CORE/TOWER/CROWN support.
// OutputMode 1 = continuously reconstructed lower endpoint * support.
// OutputMode 2 = continuously reconstructed upper endpoint * support.
// Support remains the exact maximum lobe support. Endpoints use a steep
// support-weighted blend so equal-strength lobe boundaries cannot replace one
// vertical interval with another in a single weather-map texel.

uniform vec2 WeatherOrigin;
uniform float WeatherExtent;
uniform float WeatherCoverageScale;
uniform int CellCount;
uniform int OutputMode;

const int MAX_CELLS = 96;
uniform vec4 CellPosRadius[MAX_CELLS];
uniform vec4 CellShape[MAX_CELLS];
uniform vec4 CellMedia[MAX_CELLS];
uniform vec4 CellDynamics[MAX_CELLS];

in vec2 texCoord;
out vec4 fragColor;

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
}

void accumulateStage(
        int role,
        float support,
        float localBase,
        float localTop,
        inout vec4 bestSupport,
        inout vec4 weightSum,
        inout vec4 weightedBase,
        inout vec4 weightedTop) {
    int channel = role == 2 ? 0 : role == 3 ? 1 : role == 4 ? 2 : 3;
    if (support > bestSupport[channel]) {
        bestSupport[channel] = support;
    }

    // Fourth power gives a half-strength neighbour 6.25% of the dominant
    // weight: enough to remove endpoint steps without averaging every weak
    // lobe into the stage core.
    float support2 = support * support;
    float weight = support2 * support2;
    weightSum[channel] += weight;
    weightedBase[channel] += localBase * weight;
    weightedTop[channel] += localTop * weight;
}

void main() {
    vec2 worldXZ = WeatherOrigin + texCoord * WeatherExtent;
    vec4 bestSupport = vec4(0.0);
    vec4 weightSum = vec4(0.0);
    vec4 weightedBase = vec4(0.0);
    vec4 weightedTop = vec4(0.0);

    for (int i = 0; i < MAX_CELLS; i++) {
        if (i >= CellCount) {
            break;
        }
        vec4 posRadius = CellPosRadius[i];
        vec4 shape = CellShape[i];
        vec4 media = CellMedia[i];
        vec4 dynamics = CellDynamics[i];
        float packedProfileRole = clamp(dynamics.w, 0.0, 7.999);
        int profile = int(floor(packedProfileRole + 0.0001));
        int role = int(floor(fract(packedProfileRole) * 16.0 + 0.5));
        if (profile != 3 || (role != 2 && role != 3 && role != 4 && role != 7)) {
            continue;
        }

        float footprintScale = max(media.w, 0.001)
            * max(WeatherCoverageScale, 0.001);
        vec2 scaledRadius = max(posRadius.zw * footprintScale, vec2(1.0));
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
        float lobes = 1.0
            + 0.16 * 0.22 * sin(theta * 2.0 + seed * 3.1)
            + 0.11 * 0.22 * sin(theta * 3.0 + seed * 7.7)
            + 0.07 * 0.22 * sin(theta * 5.0 + seed * 13.9);
        r /= max(lobes, 0.35);

        float edgeStart = mix(0.78, 0.42, saturate(shape.w));
        float footprint = 1.0 - smoothstep(edgeStart, 1.0, r);
        float lifecycle = saturate(dynamics.z < -0.5
            ? -dynamics.z - 1.0
            : dynamics.z);
        float lifecycleEnvelope = lifecycle < 0.5
            ? mix(0.30, 1.0, lifecycle * 2.0)
            : mix(1.0, 0.30, (lifecycle - 0.5) * 2.0);
        float support = saturate(
            footprint * saturate(media.x) * lifecycleEnvelope
                * (1.0 + saturate(dynamics.y) * 0.16)
        );
        if (support <= 0.002) {
            continue;
        }

        vec2 curvedRange = paCumulusCurvedLayerRange(role, shape.y, shape.z, r);
        float localBase = clamp(curvedRange.x, 0.0, 1.0);
        float localTop = max(clamp(curvedRange.y, 0.0, 1.0), localBase + 0.001);
        accumulateStage(
            role,
            support,
            localBase,
            localTop,
            bestSupport,
            weightSum,
            weightedBase,
            weightedTop
        );
    }

    // The lowest accepted support (0.002) still has a fourth-power weight of
    // 1.6e-11, so this guard only handles channels with no contributing lobe.
    vec4 safeWeight = max(weightSum, vec4(1.0e-30));
    vec4 resolvedBase = weightedBase / safeWeight;
    vec4 resolvedTop = weightedTop / safeWeight;

    if (OutputMode == 1) {
        fragColor = resolvedBase * bestSupport;
    } else if (OutputMode == 2) {
        fragColor = resolvedTop * bestSupport;
    } else {
        fragColor = bestSupport;
    }
}
