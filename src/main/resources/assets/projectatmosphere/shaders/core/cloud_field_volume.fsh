#version 150

uniform vec3 VolumeMin;
uniform vec3 VolumeMax;
uniform vec3 CameraPos;
uniform vec3 FieldCenter;
uniform float FieldRadius;
uniform float FieldBaseY;
uniform float FieldTopY;
uniform float FieldDensity;
uniform float FieldCoverage;
uniform float FieldHydration;
uniform float FieldGrowth;
uniform float FieldDecay;
uniform float FieldHumidityInfluence;
uniform vec3 FieldWind;
uniform float FieldVerticalDevelopment;
uniform float FieldStormPotential;
uniform float FieldAgeTicks;
uniform float FieldLifetimeTicks;
uniform float FieldSeed;
uniform float FieldCloudletCount;
uniform int FieldSourceKind;
uniform float AnimationTime;
uniform int DebugMode;
// Runtime tuning uniforms. Defaults are provided by cloud_field_volume.json and
// can be changed in-game with /pa cloud fields render tune ...
uniform float TuneOpacityStrength;     // default 0.42, alpha = 1.0 - exp(-density * strength)
uniform float TuneDensityThreshold;    // default 0.0012, keeps empty sky transparent
uniform float TuneMaxFinalAlpha;       // default 0.95, clamps final opacity
uniform float TuneNoiseStrength;       // default 1.0, puff/lobe breakup scale
uniform float TuneErosionStrength;     // default 1.0, edge erosion scale
uniform float TuneBrightness;          // default 1.08, neutral cloud brightness
uniform float TuneUndersideDarkening;  // default 0.34, gray underside amount
uniform float TuneDensityBoost;        // default 2.0, normal-mode readability boost
uniform float TuneAnimSpeed;           // default 0.015, subtle detail drift only

in vec2 texCoord;
in vec3 fragWorldPos;

out vec4 fragColor;

const int RAY_STEPS = 28;
const float NORMAL_MAX_SAMPLE_ALPHA = 0.32;

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
}

vec3 saturate(vec3 value) {
    return clamp(value, vec3(0.0), vec3(1.0));
}

float safeInverse(float value) {
    if (abs(value) < 0.00001) {
        return value < 0.0 ? -100000.0 : 100000.0;
    }
    return 1.0 / value;
}

vec2 intersectBox(vec3 rayOrigin, vec3 rayDirection) {
    vec3 invDirection = vec3(
        safeInverse(rayDirection.x),
        safeInverse(rayDirection.y),
        safeInverse(rayDirection.z)
    );
    vec3 t0 = (VolumeMin - rayOrigin) * invDirection;
    vec3 t1 = (VolumeMax - rayOrigin) * invDirection;
    vec3 nearPlane = min(t0, t1);
    vec3 farPlane = max(t0, t1);
    float nearT = max(max(nearPlane.x, nearPlane.y), nearPlane.z);
    float farT = min(min(farPlane.x, farPlane.y), farPlane.z);
    return vec2(nearT, farT);
}

float hash1(float n) {
    return fract(sin(n) * 43758.5453123);
}

float noise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n = dot(i, vec3(1.0, 57.0, 113.0)) + FieldSeed;

    float x00 = mix(hash1(n + 0.0), hash1(n + 1.0), f.x);
    float x10 = mix(hash1(n + 57.0), hash1(n + 58.0), f.x);
    float x01 = mix(hash1(n + 113.0), hash1(n + 114.0), f.x);
    float x11 = mix(hash1(n + 170.0), hash1(n + 171.0), f.x);
    float y0 = mix(x00, x10, f.y);
    float y1 = mix(x01, x11, f.y);
    return mix(y0, y1, f.z);
}

float fbm(vec3 p) {
    float value = 0.0;
    float amplitude = 0.55;
    for (int i = 0; i < 4; i++) {
        value += noise3(p) * amplitude;
        p *= 2.03;
        amplitude *= 0.50;
    }
    return value;
}

float height01At(vec3 p) {
    float heightSpan = max(FieldTopY - FieldBaseY, 1.0);
    return saturate((p.y - FieldBaseY) / heightSpan);
}

float horizontalMaskAt(vec3 p) {
    float radius = max(FieldRadius, 1.0);
    float height01 = height01At(p);
    float radial = length(p.xz - FieldCenter.xz) / radius;

    // Vertical development narrows the upper part of the field into a simple
    // cumulus/congestus tower hint. Storm potential lets the top spread back
    // outward slightly as a crude anvil placeholder.
    float towerScale = mix(1.0, mix(1.15, 0.58, height01), saturate(FieldVerticalDevelopment));
    float anvilSpread = smoothstep(0.72, 1.0, height01) * saturate(FieldStormPotential) * 0.24;

    // Low-frequency lobe noise breaks the perfect cylinder side wall without
    // depending on per-frame random data.
    float lobeNoise = fbm(vec3((p.xz - FieldCenter.xz) * 0.018, FieldSeed * 0.013));
    float lobeOffset = (lobeNoise - 0.5) * mix(0.10, 0.24, saturate(FieldVerticalDevelopment)) * TuneNoiseStrength;
    float shapedRadial = radial / max(0.25, towerScale + anvilSpread + lobeOffset);

    float edgeStart = mix(0.42, 0.78, saturate(FieldCoverage));
    return 1.0 - smoothstep(edgeStart, 1.0, shapedRadial);
}

float verticalMaskAt(vec3 p) {
    float height01 = height01At(p);
    float stormLift = saturate(FieldStormPotential) * 0.08;
    // Flatter lower deck, then a rounded upper body. This keeps the debug
    // cloud from reading like a perfect dome or hard vertical cylinder.
    float baseFalloff = smoothstep(0.03, 0.11, height01);
    float topFalloff = 1.0 - smoothstep(0.76 + stormLift, 1.0, height01);
    float upperBody = mix(0.92, 1.12, smoothstep(0.20, 0.68, height01) * (1.0 - smoothstep(0.88, 1.0, height01)));
    return saturate(baseFalloff * topFalloff * upperBody);
}

float densityAt(vec3 p) {
    float horizontalMask = horizontalMaskAt(p);
    float verticalMask = verticalMaskAt(p);
    float baseShape = horizontalMask * verticalMask;

    // Stable field-local noise owns the visible puff layout and silhouette.
    // It intentionally does not use time, so the main shape does not regenerate.
    vec3 fieldLocal = vec3(p.x - FieldCenter.x, p.y - FieldBaseY, p.z - FieldCenter.z);
    float largeNoise = fbm(fieldLocal * 0.010 + vec3(FieldSeed * 0.003));
    float erosionNoise = fbm(fieldLocal * 0.038 + vec3(FieldSeed * 0.017));

    // Animated detail is deliberately tiny and slow. It adds a faint internal
    // drift without changing the stable lobe/erosion layout.
    vec3 detailDirection = FieldWind;
    if (length(detailDirection) < 0.001) {
        detailDirection = vec3(0.35, 0.0, 0.12);
    }
    vec3 detailOffset = normalize(detailDirection) * AnimationTime * TuneAnimSpeed;
    float detailNoise = fbm(fieldLocal * 0.075 + detailOffset + vec3(FieldSeed * 0.031));
    float edgeRegion = 1.0 - smoothstep(0.34, 0.92, baseShape);
    float coverageThreshold = mix(0.60, 0.24, saturate(FieldCoverage));
    float puffMask = smoothstep(coverageThreshold, 0.95, largeNoise + baseShape * 0.42);
    float erodedShape = baseShape * mix(1.0 - 0.22 * TuneNoiseStrength, 1.0 + 0.18 * TuneNoiseStrength, largeNoise);
    erodedShape -= edgeRegion * erosionNoise * mix(0.20, 0.44, 1.0 - saturate(FieldCoverage)) * TuneErosionStrength;
    erodedShape += (detailNoise - 0.5) * edgeRegion * 0.06 * TuneNoiseStrength * smoothstep(0.0, 0.05, TuneAnimSpeed);
    erodedShape = saturate(erodedShape) * puffMask;

    float weatherDensity = saturate(FieldDensity)
        * saturate(FieldCoverage)
        * saturate(FieldHydration)
        * saturate(FieldGrowth)
        * (1.0 - saturate(FieldDecay));
    float life01 = saturate(FieldAgeTicks / max(FieldLifetimeTicks, 1.0));
    float endOfLifeFade = 1.0 - smoothstep(0.94, 1.0, life01) * 0.35;
    float cloudletDensityHint = mix(0.95, 1.05, saturate(FieldCloudletCount / 96.0));
    float humidityBoost = mix(0.85, 1.15, saturate(FieldHumidityInfluence));
    float stormBoost = mix(1.0, 1.35, saturate(FieldStormPotential));
    return saturate(erodedShape * weatherDensity * endOfLifeFade * cloudletDensityHint * humidityBoost * stormBoost * TuneDensityBoost);
}

float boundsAlphaAt(vec3 p) {
    vec3 size = max(VolumeMax - VolumeMin, vec3(1.0));
    vec3 local = saturate((p - VolumeMin) / size);
    float edgeDistance = min(
        min(min(local.x, 1.0 - local.x), min(local.y, 1.0 - local.y)),
        min(local.z, 1.0 - local.z)
    );
    float edge = 1.0 - smoothstep(0.0, 0.035, edgeDistance);
    float shell = 0.018;
    return shell + edge * 0.15;
}

vec4 debugColor(vec3 p) {
    if (DebugMode == 1) {
        return vec4(0.20, 0.68, 1.00, boundsAlphaAt(p));
    }
    if (DebugMode == 2) {
        float horizontalMask = horizontalMaskAt(p);
        return vec4(horizontalMask, horizontalMask, horizontalMask, 0.72);
    }
    if (DebugMode == 3) {
        float height01 = height01At(p);
        return vec4(height01, 0.30, 1.0 - height01, 0.72);
    }
    if (DebugMode == 4) {
        float verticalMask = verticalMaskAt(p);
        return vec4(verticalMask, verticalMask * 0.75, 1.0 - verticalMask, 0.72);
    }
    if (DebugMode == 5) {
        float density = densityAt(p);
        return vec4(density, density, density, 0.78);
    }
    if (DebugMode == 6) {
        if (FieldSourceKind == 0) {
            return vec4(0.62, 0.62, 0.68, 0.55); // unknown
        }
        if (FieldSourceKind == 1) {
            return vec4(0.42, 0.90, 0.55, 0.55); // manual debug
        }
        if (FieldSourceKind == 2) {
            return vec4(0.36, 0.72, 0.95, 0.55); // weather summary
        }
        if (FieldSourceKind == 3) {
            return vec4(0.95, 0.76, 0.38, 0.55); // PA cluster
        }
        if (FieldSourceKind == 4) {
            return vec4(0.78, 0.54, 0.95, 0.55); // PA region
        }
        return vec4(0.95, 0.44, 0.48, 0.55);
    }
    return vec4(0.0);
}

void main() {
    vec3 toFragment = fragWorldPos - CameraPos;
    if (dot(toFragment, toFragment) < 0.000001) {
        discard;
    }

    vec3 rayDirection = normalize(toFragment);
    vec2 hit = intersectBox(CameraPos, rayDirection);
    float rayStart = max(hit.x, 0.0);
    float rayEnd = hit.y;
    if (rayEnd <= rayStart) {
        discard;
    }

    vec3 midpoint = CameraPos + rayDirection * ((rayStart + rayEnd) * 0.5);
    if (DebugMode > 0) {
        vec3 debugPoint = DebugMode == 1 ? fragWorldPos : midpoint;
        fragColor = debugColor(debugPoint);
        return;
    }

    float stepSize = (rayEnd - rayStart) / float(RAY_STEPS);
    float transmittance = 1.0;
    vec3 accumulated = vec3(0.0);

    for (int i = 0; i < RAY_STEPS; i++) {
        float t = rayStart + (float(i) + 0.5) * stepSize;
        vec3 p = CameraPos + rayDirection * t;
        float density = densityAt(p);
        if (density <= TuneDensityThreshold) {
            continue;
        }

        float height01 = height01At(p);
        float underside = mix(1.0 - TuneUndersideDarkening, 1.0, smoothstep(0.10, 0.76, height01));
        vec3 sideNormal = normalize(vec3(p.x - FieldCenter.x, FieldRadius * 0.28, p.z - FieldCenter.z));
        float sunSide = dot(sideNormal, normalize(vec3(-0.45, 0.62, 0.25))) * 0.5 + 0.5;
        float softLight = mix(0.94, 1.10, sunSide) * mix(0.92, 1.08, height01);
        float stormDarkening = mix(1.0, 0.64, saturate(FieldStormPotential) * (1.0 - height01));
        vec3 undersideColor = vec3(0.62, 0.62, 0.60);
        vec3 topColor = vec3(1.00, 1.00, 0.96) * TuneBrightness;
        vec3 cloudColor = mix(undersideColor, topColor, underside) * stormDarkening * softLight;

        float sampleAlpha = clamp(1.0 - exp(-density * TuneOpacityStrength), 0.0, NORMAL_MAX_SAMPLE_ALPHA);
        accumulated += transmittance * sampleAlpha * cloudColor;
        transmittance *= 1.0 - sampleAlpha;
        if (transmittance < 0.02) {
            break;
        }
    }

    float alpha = saturate(1.0 - transmittance);
    if (alpha < 0.01) {
        discard;
    }

    vec3 color = accumulated / max(alpha, 0.0001);
    fragColor = vec4(color, min(alpha, TuneMaxFinalAlpha));
}
