#version 150

#moj_import <projectatmosphere:cloud_weather_footprint.glsl>

// Severe endpoint map (RGBA16F, premultiplied endpoints).
// OutputMode 0: R = CORE top * support; GBA = ANVIL support/base/top.
// OutputMode 1: R = TOWER support; G = TOWER base * support;
//               B = TOWER top * support; A = 0.

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

    float convectiveSupport = 0.0;
    float convectiveBestSupport = 0.0;
    float convectiveBase = 0.0;
    float convectiveTop = 0.0;
    float anvilSupport = 0.0;
    float anvilBestSupport = 0.0;
    float anvilBase = 0.0;
    float anvilTop = 0.0;

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
        bool towerOutput = OutputMode != 0;
        bool acceptedRole = towerOutput
            ? role == 4
            : (role == 3 || role == 5);
        if ((profile != 4 && profile != 7) || !acceptedRole) {
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
            + 0.16 * 0.76 * sin(theta * 2.0 + seed * 3.1)
            + 0.11 * 0.76 * sin(theta * 3.0 + seed * 7.7)
            + 0.07 * 0.76 * sin(theta * 5.0 + seed * 13.9);
        r /= max(lobes, 0.35);
        r += paSevereContourErosion(warp, theta, seed, profile, role, r);

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

        vec2 curvedRange = paSevereCurvedLayerRange(role, shape.y, shape.z, r);
        float localBase = clamp(curvedRange.x, 0.0, 1.0);
        float localTop = clamp(curvedRange.y, 0.0, 1.0);
        localTop = max(localTop, localBase + 0.001);
        if (role == 5) {
            if (support > anvilBestSupport) {
                anvilBestSupport = support;
                anvilSupport = support;
                anvilBase = localBase;
                anvilTop = localTop;
            }
        } else if (towerOutput) {
            if (support > convectiveBestSupport) {
                convectiveBestSupport = support;
                convectiveSupport = support;
                convectiveBase = localBase;
                convectiveTop = localTop;
            }
        } else {
            if (support > convectiveBestSupport) {
                convectiveBestSupport = support;
                convectiveSupport = support;
                convectiveBase = localBase;
                convectiveTop = localTop;
            }
        }
    }

    fragColor = OutputMode != 0
        ? vec4(
            convectiveSupport,
            convectiveBase * convectiveSupport,
            convectiveTop * convectiveSupport,
            0.0
        )
        : vec4(
            convectiveTop * convectiveSupport,
            anvilSupport,
            anvilBase * anvilSupport,
            anvilTop * anvilSupport
        );
}
