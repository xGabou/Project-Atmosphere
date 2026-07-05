#version 150

// Rasterizes interpolated cloud cells into the weather map.
// Output channels (RGBA8):
//   R = coverage (smooth union of all cell footprints + regional layer)
//   G = cloud base height, normalized inside [SlabBaseY, SlabTopY]
//   B = cloud top height, normalized inside [SlabBaseY, SlabTopY]
//   A = energy (storminess: darker bases, stronger erosion, anvil spread)

uniform vec2 WeatherOrigin;   // world XZ of texel (0,0)
uniform float WeatherExtent;  // world size covered by the full map
uniform float SlabBaseY;
uniform float SlabTopY;
uniform float RegionalCoverage; // 0..1 stratus/overcast layer amount
uniform float RegionalEnergy;
uniform float WeatherCoverageScale;
uniform float WorldTime;
uniform int CellCount;

// Uploaded manually (glUniform4fv), not through the vanilla uniform system:
// CellPosRadius[i] = (worldX, worldZ, radiusMajor, radiusMinor)
// CellShape[i]     = (orientationRadians, base01, top01, edgeSoftness)
// CellMedia[i]     = (density, energy, seed01, unused)
const int MAX_CELLS = 96;
uniform vec4 CellPosRadius[MAX_CELLS];
uniform vec4 CellShape[MAX_CELLS];
uniform vec4 CellMedia[MAX_CELLS];

in vec2 texCoord;
out vec4 fragColor;

float saturate(float v) {
    return clamp(v, 0.0, 1.0);
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
    return valueNoise(p) * 0.65 + valueNoise(p * 2.13 + vec2(19.7)) * 0.35;
}

void main() {
    vec2 worldXZ = WeatherOrigin + texCoord * WeatherExtent;
    float slabSpan = max(SlabTopY - SlabBaseY, 1.0);

    // World-anchored domain warp: silhouettes stay glued to world space and
    // translate coherently as cells drift, instead of noise "swimming".
    vec2 warp = vec2(
        fbm2(worldXZ * 0.010 + vec2(3.7, 9.1)),
        fbm2(worldXZ * 0.010 + vec2(-7.3, 1.9))
    ) - 0.5;
    vec2 warpedXZ = worldXZ + warp * 42.0;

    float coverage = 0.0;
    float baseAccum = 0.0;
    float topAccum = 0.0;
    float energyAccum = 0.0;
    float weightAccum = 0.0;

    for (int i = 0; i < MAX_CELLS; i++) {
        if (i >= CellCount) {
            break;
        }
        vec4 posRadius = CellPosRadius[i];
        vec4 shape = CellShape[i];
        vec4 media = CellMedia[i];
        vec2 scaledRadius = max(posRadius.zw * max(WeatherCoverageScale, 0.001), vec2(1.0));
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

        // Per-cell angular harmonics seeded by the cell id give each cell a
        // stable, unique lobed silhouette that moves rigidly with the cell.
        float theta = atan(normalized.y, normalized.x);
        float seed = media.z * 6.2831853;
        float lobes = 1.0
            + 0.16 * sin(theta * 2.0 + seed * 3.1)
            + 0.11 * sin(theta * 3.0 + seed * 7.7)
            + 0.07 * sin(theta * 5.0 + seed * 13.9);
        r /= max(lobes, 0.35);

        float edgeStart = mix(0.78, 0.42, saturate(shape.w));
        float footprint = 1.0 - smoothstep(edgeStart, 1.0, r);
        float cellCoverage = footprint * saturate(media.x);
        if (cellCoverage <= 0.002) {
            continue;
        }

        coverage = 1.0 - (1.0 - coverage) * (1.0 - cellCoverage);
        // Sharpened weight: the locally dominant cloudlet decides base/top
        // instead of every overlapping cloudlet being averaged into one broad
        // planar slab (the source of the flat sheets and horizontal band
        // artifacts where many cloudlets overlap).
        float weight = cellCoverage * cellCoverage * cellCoverage;
        // Collapse both vertical faces toward the middle at the footprint
        // edge. A 2D footprint extruded between a flat base/top reads as a
        // camera-facing card even when its density contains noise; this
        // encodes an ellipsoidal cloudlet envelope in the weather map.
        float edge01 = 1.0 - footprint;
        float cellSpan = max(shape.z - shape.y, 2.0 / slabSpan);
        float edgeLift = edge01 * cellSpan * 0.42;
        float edgeDrop = edge01 * cellSpan * 0.42;
        baseAccum += (shape.y + edgeLift) * weight;
        topAccum += (shape.z - edgeDrop) * weight;
        energyAccum += media.y * weight;
        weightAccum += weight;
    }

    // Region-scale stratus/overcast layer: broad, flat, low-energy coverage
    // driven by regional weather instead of individual cells.
    float regional = saturate(RegionalCoverage);
    if (regional > 0.01) {
        float sheetNoise = fbm2(worldXZ * 0.0011 + vec2(WorldTime * 0.00021, WorldTime * 0.00013));
        float sheet = saturate((sheetNoise - (1.0 - regional * 0.9)) * 3.2) * regional;
        if (sheet > 0.002) {
            float sheetBase = 0.30 + sheetNoise * 0.05;
            float sheetTop = sheetBase + 0.12 + regional * 0.08;
            coverage = 1.0 - (1.0 - coverage) * (1.0 - sheet * 0.85);
            // Same sharpened weighting as the cloudlets so the sheet only
            // dictates base/top where it actually dominates local coverage.
            float sheetWeight = sheet * 0.6;
            float weight = sheetWeight * sheetWeight * sheetWeight;
            baseAccum += sheetBase * weight;
            topAccum += sheetTop * weight;
            energyAccum += RegionalEnergy * weight;
            weightAccum += weight;
        }
    }

    // weightAccum guard is numeric-safety only: cubed weights get tiny at
    // cloudlet edges while coverage is still meaningful there.
    if (weightAccum <= 0.0000005 || coverage <= 0.002) {
        fragColor = vec4(0.0, 0.35, 0.45, 0.0);
        return;
    }

    float base01 = clamp(baseAccum / weightAccum, 0.0, 1.0);
    float top01 = clamp(topAccum / weightAccum, 0.0, 1.0);
    top01 = max(top01, base01 + 2.0 / slabSpan);
    float energy = clamp(energyAccum / weightAccum, 0.0, 1.0);
    fragColor = vec4(saturate(coverage), base01, top01, energy);
}
