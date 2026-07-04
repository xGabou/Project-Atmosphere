#version 150

uniform vec3 VolumeMin;
uniform vec3 VolumeMax;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 InvModelViewMat;
uniform mat4 InvProjMat;
uniform sampler2D SceneDepthSampler;
uniform vec2 OutputSize;
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
uniform int RaymarchSteps;
uniform int DetailOctaves;
uniform int CloudletBudget;
uniform int UseSceneDepth;
uniform int DebugMode;
// Runtime tuning uniforms. Defaults are provided by cloud_field_volume.json and
// can be changed in-game with /pa cloud fields render tune ...
uniform float TuneOpacityStrength;     // default 0.34, alpha = 1.0 - exp(-density * strength)
uniform float TuneDensityThreshold;    // default 0.0016, keeps empty sky transparent
uniform float TuneMaxFinalAlpha;       // default 0.90, clamps final opacity
uniform float TuneNoiseStrength;       // default 1.0, puff/lobe breakup scale
uniform float TuneErosionStrength;     // default 1.2, edge erosion scale
uniform float TuneBrightness;          // default 1.0, neutral cloud brightness
uniform float TuneUndersideDarkening;  // default 0.52, gray underside amount
uniform float TuneDensityBoost;        // default 1.55, normal-mode readability boost
uniform float TuneAnimSpeed;           // default 0.012, subtle detail drift only

in vec2 texCoord;
in vec3 fragWorldPos;

out vec4 fragColor;

const int MAX_RAY_STEPS = 64;
const int MAX_CLOUDLET_SAMPLES = 64;
const float NORMAL_MAX_SAMPLE_ALPHA = 0.24;

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
    n = fract(n * 0.1031);
    n *= n + 33.33;
    n *= n + n;
    return fract(n);
}

float cloudletUnit(int index, int salt) {
    return hash1(float(index) * 37.719 + float(salt) * 19.371 + FieldSeed * 0.173);
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
    float amplitudeSum = 0.0;
    int octaves = clamp(DetailOctaves, 1, 4);
    for (int i = 0; i < 4; i++) {
        if (i >= octaves) {
            break;
        }
        value += noise3(p) * amplitude;
        amplitudeSum += amplitude;
        p *= 2.03;
        amplitude *= 0.50;
    }
    // Keep the density range stable across presets. Octave count changes
    // detail frequency, not the cloud's overall coverage/visibility.
    return value * (1.03125 / max(amplitudeSum, 0.0001));
}

// Large-scale cloud geometry stays identical across quality modes and does not
// need four detail octaves. Fine density detail continues to use fbm().
float fbmShape(vec3 p) {
    float value = 0.0;
    float amplitude = 0.55;
    for (int i = 0; i < 2; i++) {
        value += noise3(p) * amplitude;
        p *= 2.03;
        amplitude *= 0.50;
    }
    return value;
}

vec3 reconstructWorldPosition(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 view = InvProjMat * clip;
    view /= max(abs(view.w), 0.00001);
    vec4 world = InvModelViewMat * view;
    world /= max(abs(world.w), 0.00001);
    return world.xyz;
}

float sceneRayEnd(vec3 rayDirection, float fallbackEnd) {
    if (UseSceneDepth == 0) {
        return fallbackEnd;
    }
    vec2 uv = gl_FragCoord.xy / max(OutputSize, vec2(1.0));
    uv = clamp(uv, vec2(0.0), vec2(1.0));
    ivec2 sceneSize = textureSize(SceneDepthSampler, 0);
    ivec2 sceneCoord = clamp(ivec2(uv * vec2(sceneSize)), ivec2(0), sceneSize - ivec2(1));
    float sceneDepth = texelFetch(SceneDepthSampler, sceneCoord, 0).r;
    if (sceneDepth >= 0.99999) {
        return fallbackEnd;
    }
    vec3 sceneWorld = reconstructWorldPosition(uv, sceneDepth);
    float sceneT = dot(sceneWorld - CameraPos, rayDirection);
    return min(fallbackEnd, max(0.0, sceneT - 0.35));
}

float worldDepth(vec3 p) {
    vec4 clip = ProjMat * ModelViewMat * vec4(p, 1.0);
    float ndcDepth = clip.z / max(abs(clip.w), 0.00001);
    return clamp(ndcDepth * 0.5 + 0.5, 0.0, 1.0);
}

float sourceVisualMultiplier() {
    if (FieldSourceKind == 1) {
        return 0.92; // manual debug source: real CloudField, useful for QA
    }
    if (FieldSourceKind == 2) {
        return 0.38; // WEATHER_SUMMARY: coarse weather cloud/haze source
    }
    if (FieldSourceKind == 3) {
        return 1.00; // PA_CLUSTER: strongest direct CloudField source
    }
    if (FieldSourceKind == 4) {
        return 0.78; // PA_REGION: broad regional source
    }
    return 0.55; // UNKNOWN: conservative
}

vec3 fieldLocalAt(vec3 p) {
    return vec3(p.x - FieldCenter.x, p.y - FieldBaseY, p.z - FieldCenter.z);
}

float localBaseOffsetAt(vec3 p) {
    float heightSpan = max(FieldTopY - FieldBaseY, 1.0);
    vec2 localXZ = p.xz - FieldCenter.xz;
    float radius = max(FieldRadius, 1.0);
    float radial = length(localXZ) / radius;
    float interior = 1.0 - smoothstep(0.28, 0.84, radial);
    float low = fbmShape(vec3(localXZ * 0.020, FieldSeed * 0.021));
    float medium = fbmShape(vec3(localXZ * 0.055, FieldSeed * 0.047));
    return ((low - 0.5) * 0.045 + (medium - 0.5) * 0.018)
        * heightSpan
        * interior
        * TuneNoiseStrength;
}

float localTopOffsetAt(vec3 p) {
    float heightSpan = max(FieldTopY - FieldBaseY, 1.0);
    vec2 localXZ = p.xz - FieldCenter.xz;
    float radius = max(FieldRadius, 1.0);
    float radial = length(localXZ) / radius;
    float interior = 1.0 - smoothstep(0.25, 0.88, radial);
    float topLobe = fbmShape(vec3(localXZ * 0.017, FieldSeed * 0.063));
    float towerBoost = mix(0.035, 0.090, saturate(FieldVerticalDevelopment));
    return (topLobe - 0.5) * heightSpan * towerBoost * interior * TuneNoiseStrength;
}

float localBaseYAt(vec3 p) {
    return FieldBaseY + localBaseOffsetAt(p);
}

float localTopYAt(vec3 p) {
    float baseY = localBaseYAt(p);
    return max(baseY + 1.0, FieldTopY + localTopOffsetAt(p));
}

float height01At(vec3 p) {
    float baseY = localBaseYAt(p);
    float topY = localTopYAt(p);
    float heightSpan = max(topY - baseY, 1.0);
    return saturate((p.y - baseY) / heightSpan);
}

// The normal raymarch needs base, top, and normalized height together. The old
// path recomputed the same FBM base/top offsets every time a mask needed the
// height, resulting in 31 FBM evaluations per density sample. Compute the
// shared geometry once for the normal path instead.
void shapeGeometryAt(vec3 p, out float baseY, out float topY, out float height01) {
    float heightSpan = max(FieldTopY - FieldBaseY, 1.0);
    vec2 localXZ = p.xz - FieldCenter.xz;
    float radial = length(localXZ) / max(FieldRadius, 1.0);

    float baseInterior = 1.0 - smoothstep(0.28, 0.84, radial);
    float low = fbmShape(vec3(localXZ * 0.020, FieldSeed * 0.021));
    float medium = fbmShape(vec3(localXZ * 0.055, FieldSeed * 0.047));
    float baseOffset = ((low - 0.5) * 0.045 + (medium - 0.5) * 0.018)
        * heightSpan
        * baseInterior
        * TuneNoiseStrength;
    baseY = FieldBaseY + baseOffset;

    float topInterior = 1.0 - smoothstep(0.25, 0.88, radial);
    float topLobe = fbmShape(vec3(localXZ * 0.017, FieldSeed * 0.063));
    float towerBoost = mix(0.035, 0.090, saturate(FieldVerticalDevelopment));
    float topOffset = (topLobe - 0.5) * heightSpan * towerBoost * topInterior * TuneNoiseStrength;
    topY = max(baseY + 1.0, FieldTopY + topOffset);
    height01 = saturate((p.y - baseY) / max(topY - baseY, 1.0));
}

float horizontalMaskAtHeight(vec3 p, float height01) {
    float radius = max(FieldRadius, 1.0);
    vec2 localXZ = p.xz - FieldCenter.xz;
    float radial = length(localXZ) / radius;
    float coverage = saturate(FieldCoverage);
    float verticalDevelopment = saturate(FieldVerticalDevelopment);
    float storm = saturate(FieldStormPotential);

    // A rounded cumulus envelope avoids the old cylinder/slab body. It is
    // narrow at the base, full through the middle, and gently tapered at the
    // top unless storm growth adds a small anvil spread.
    float baseScale = mix(0.48, 1.0, smoothstep(0.02, 0.24, height01));
    float upperScale = mix(1.0, mix(0.86, 0.58, verticalDevelopment), smoothstep(0.52, 1.0, height01));
    float anvilSpread = smoothstep(0.76, 0.98, height01) * storm * 0.20;
    float profileScale = max(0.22, baseScale * upperScale + anvilSpread);

    // Restrict silhouette noise to the edge of the body. This keeps breakup
    // visible without spawning large side shoots detached from the main mass.
    float lobeNoise = fbmShape(vec3(localXZ * 0.012, FieldSeed * 0.013));
    float scallopNoise = fbmShape(vec3(localXZ * 0.040, FieldSeed * 0.041 + height01 * 2.0));
    float edgeBand = smoothstep(0.36, 0.96, radial) * (1.0 - smoothstep(1.02, 1.20, radial));
    float silhouetteOffset = ((lobeNoise - 0.5) * 0.11 + (scallopNoise - 0.5) * 0.07)
        * edgeBand
        * TuneNoiseStrength;
    float shapedRadial = radial / max(0.24, profileScale + silhouetteOffset);

    float edgeStart = mix(0.50, 0.74, coverage);
    float edgeWidth = mix(0.25, 0.17, coverage);
    float verticalContour = smoothstep(0.00, 0.14, height01)
        * (1.0 - smoothstep(0.88 + storm * 0.06, 1.0, height01));
    return saturate((1.0 - smoothstep(edgeStart, edgeStart + edgeWidth, shapedRadial)) * verticalContour);
}

float horizontalMaskAt(vec3 p) {
    return horizontalMaskAtHeight(p, height01At(p));
}

float verticalMaskAtHeight(vec3 p, float height01) {
    float stormLift = saturate(FieldStormPotential) * 0.08;
    vec2 localXZ = p.xz - FieldCenter.xz;
    float undersideNoise = fbmShape(vec3(localXZ * 0.050, FieldSeed * 0.071));
    float topNoise = fbmShape(vec3(localXZ * 0.026, FieldSeed * 0.093));
    float baseStart = mix(0.025, 0.060, undersideNoise);
    float baseFalloff = smoothstep(baseStart, baseStart + 0.145, height01);
    float topStart = 0.80 + stormLift + (topNoise - 0.5) * 0.035;
    float topFalloff = 1.0 - smoothstep(topStart, 1.0, height01);
    float middleLift = 0.82 + 0.24 * (1.0 - saturate(abs(height01 - 0.48) * 2.0));
    return saturate(baseFalloff * topFalloff * middleLift);
}

float verticalMaskAt(vec3 p) {
    return verticalMaskAtHeight(p, height01At(p));
}

float cloudletShapeAt(vec3 p, float localBaseY, float height01, float envelopeMask) {
    int activeCount = int(max(FieldCloudletCount + 0.5, 0.0));
    int generatedCount = activeCount > 0 ? activeCount : 6;
    int sampleBudget = clamp(CloudletBudget, 1, MAX_CLOUDLET_SAMPLES);
    int sampleCount = clamp(min(generatedCount, sampleBudget), 1, MAX_CLOUDLET_SAMPLES);
    float heightSpan = max(FieldTopY - FieldBaseY, 1.0);
    float fieldRadius = max(FieldRadius, 1.0);
    vec3 fieldLocal = fieldLocalAt(p);
    fieldLocal.y = p.y - localBaseY;
    float accumulated = 0.0;
    float nearest = 0.0;

    for (int i = 0; i < MAX_CLOUDLET_SAMPLES; i++) {
        if (i >= sampleCount) {
            break;
        }

        int cloudletIndex = int(floor((float(i) + 0.5) * float(generatedCount) / float(sampleCount)));
        float angle = cloudletUnit(cloudletIndex, 1) * 6.2831853;
        float ring = sqrt(cloudletUnit(cloudletIndex, 2)) * fieldRadius * 0.76;
        float cloudletHeight01 = 0.12 + cloudletUnit(cloudletIndex, 3) * 0.76;
        float radiusScale = mix(0.13, 0.28, cloudletUnit(cloudletIndex, 4)) * mix(1.08, 0.66, cloudletHeight01);
        float horizontalRadius = max(2.0, fieldRadius * radiusScale);
        float verticalRadius = max(2.0, horizontalRadius * mix(0.62, 1.18, cloudletUnit(cloudletIndex, 5)) * mix(0.72, 1.12, saturate(heightSpan / max(fieldRadius, 1.0))));
        float densityScale = mix(0.72, 1.12, cloudletUnit(cloudletIndex, 6));
        float coverageWeight = mix(0.58, 1.00, cloudletUnit(cloudletIndex, 7));
        vec3 center = vec3(
            cos(angle) * ring,
            heightSpan * cloudletHeight01,
            sin(angle) * ring
        );

        vec3 q = vec3(
            (fieldLocal.x - center.x) / horizontalRadius,
            (fieldLocal.y - center.y) / verticalRadius,
            (fieldLocal.z - center.z) / horizontalRadius
        );
        float distance01 = length(q);
        float core = 1.0 - smoothstep(0.42, 1.08, distance01);
        float feather = 1.0 - smoothstep(0.82, 1.34, distance01);
        float cloudlet = saturate(core * 0.82 + feather * 0.24) * densityScale * coverageWeight;
        accumulated += cloudlet * 0.36;
        nearest = max(nearest, cloudlet);
    }

    float merged = saturate(max(nearest, accumulated));
    float coverageGate = smoothstep(mix(0.30, 0.12, saturate(FieldCoverage)), 0.86, merged);
    return saturate(merged * coverageGate * smoothstep(0.02, 0.18, envelopeMask));
}

float densityAndHeightAt(vec3 p, out float sampledHeight01) {
    sampledHeight01 = 0.0;
    float heightSpan = max(FieldTopY - FieldBaseY, 1.0);
    float cheapVerticalPad = max(4.0, heightSpan * 0.18);
    if (p.y < FieldBaseY - cheapVerticalPad || p.y > FieldTopY + cheapVerticalPad) {
        return 0.0;
    }
    float cheapRadius = max(FieldRadius, 1.0) * (1.14 + saturate(FieldStormPotential) * 0.10);
    if (dot(p.xz - FieldCenter.xz, p.xz - FieldCenter.xz) > cheapRadius * cheapRadius) {
        return 0.0;
    }

    // Reject samples well outside the analytic cumulus envelope before any
    // procedural noise. The generous margin retains noisy silhouette lobes.
    float approximateHeight01 = saturate((p.y - FieldBaseY) / heightSpan);
    float approximateRadial = length(p.xz - FieldCenter.xz) / max(FieldRadius, 1.0);
    float approximateBaseScale = mix(0.48, 1.0, smoothstep(0.02, 0.24, approximateHeight01));
    float approximateUpperScale = mix(
        1.0,
        mix(0.86, 0.58, saturate(FieldVerticalDevelopment)),
        smoothstep(0.52, 1.0, approximateHeight01)
    );
    float approximateAnvil = smoothstep(0.76, 0.98, approximateHeight01)
        * saturate(FieldStormPotential)
        * 0.20;
    float approximateProfile = max(0.22, approximateBaseScale * approximateUpperScale + approximateAnvil);
    if (approximateRadial > approximateProfile * 1.30 + 0.10) {
        return 0.0;
    }

    float localBaseY;
    float localTopY;
    float height01;
    shapeGeometryAt(p, localBaseY, localTopY, height01);
    sampledHeight01 = height01;
    float horizontalMask = horizontalMaskAtHeight(p, height01);
    float verticalMask = verticalMaskAtHeight(p, height01);
    float baseShape = horizontalMask * verticalMask;
    if (baseShape <= 0.0001) {
        return 0.0;
    }

    vec3 fieldLocal = fieldLocalAt(p);
    fieldLocal.y = p.y - localBaseY;
    float cloudletShape = cloudletShapeAt(p, localBaseY, height01, baseShape);
    if (cloudletShape <= 0.0001) {
        return 0.0;
    }

    float largeNoise = fbm(fieldLocal * 0.010 + vec3(FieldSeed * 0.003));
    float erosionNoise = fbm(fieldLocal * 0.038 + vec3(FieldSeed * 0.017));
    float undersideNoise = fbm(fieldLocal * vec3(0.050, 0.018, 0.050) + vec3(FieldSeed * 0.061));
    float topLobeNoise = fbm(fieldLocal * vec3(0.024, 0.018, 0.024) + vec3(FieldSeed * 0.083));

    // Animated detail is deliberately tiny and slow. It adds a faint internal
    // drift without changing the stable lobe/erosion layout.
    float edgeRegion = 1.0 - smoothstep(0.30, 0.90, baseShape);
    float detailNoise = 0.5;
    if (edgeRegion > 0.001 && TuneAnimSpeed > 0.0001) {
        vec3 detailDirection = FieldWind;
        if (length(detailDirection) < 0.001) {
            detailDirection = vec3(0.35, 0.0, 0.12);
        }
        vec3 detailOffset = normalize(detailDirection) * AnimationTime * TuneAnimSpeed;
        detailNoise = fbm(fieldLocal * 0.075 + detailOffset + vec3(FieldSeed * 0.031));
    }
    float erodedShape = cloudletShape * mix(0.92 - 0.10 * TuneNoiseStrength, 1.08 + 0.10 * TuneNoiseStrength, largeNoise);
    erodedShape -= edgeRegion * erosionNoise * mix(0.12, 0.34, 1.0 - saturate(FieldCoverage)) * TuneErosionStrength;
    float undersideRegion = (1.0 - smoothstep(0.08, 0.32, height01)) * smoothstep(0.16, 0.50, horizontalMask);
    erodedShape -= undersideRegion * undersideNoise * 0.10 * TuneErosionStrength;
    float topRegion = smoothstep(0.42, 0.90, height01);
    erodedShape *= mix(1.0, mix(0.88, 1.14, topLobeNoise), topRegion * 0.24 * TuneNoiseStrength);
    erodedShape += (detailNoise - 0.5) * edgeRegion * 0.06 * TuneNoiseStrength * smoothstep(0.0, 0.05, TuneAnimSpeed);
    erodedShape = saturate(erodedShape) * baseShape;

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
    return saturate(erodedShape * weatherDensity * endOfLifeFade * cloudletDensityHint * humidityBoost * stormBoost * TuneDensityBoost * sourceVisualMultiplier());
}

float densityAt(vec3 p) {
    float sampledHeight01;
    return densityAndHeightAt(p, sampledHeight01);
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

float debugEnvelopeAt(vec3 p) {
    return saturate(horizontalMaskAt(p) * verticalMaskAt(p));
}

float debugMaskAlpha(float mask, float maxAlpha) {
    return smoothstep(0.015, 0.18, saturate(mask)) * maxAlpha;
}

vec4 debugColor(vec3 p) {
    if (DebugMode == 1) {
        return vec4(0.20, 0.68, 1.00, boundsAlphaAt(p));
    }
    if (DebugMode == 2) {
        float horizontalMask = horizontalMaskAt(p);
        return vec4(horizontalMask, horizontalMask, horizontalMask, debugMaskAlpha(horizontalMask, 0.72));
    }
    if (DebugMode == 3) {
        float height01 = height01At(p);
        return vec4(height01, 0.30, 1.0 - height01, debugMaskAlpha(debugEnvelopeAt(p), 0.72));
    }
    if (DebugMode == 4) {
        float verticalMask = verticalMaskAt(p);
        return vec4(verticalMask, verticalMask * 0.75, 1.0 - verticalMask, debugMaskAlpha(debugEnvelopeAt(p), 0.72));
    }
    if (DebugMode == 5) {
        float density = densityAt(p);
        return vec4(density, density, density, debugMaskAlpha(density, 0.78));
    }
    if (DebugMode == 6) {
        float sourceAlpha = debugMaskAlpha(debugEnvelopeAt(p), 0.55);
        if (FieldSourceKind == 0) {
            return vec4(0.62, 0.62, 0.68, sourceAlpha); // unknown
        }
        if (FieldSourceKind == 1) {
            return vec4(0.42, 0.90, 0.55, sourceAlpha); // manual debug
        }
        if (FieldSourceKind == 2) {
            return vec4(0.36, 0.72, 0.95, sourceAlpha); // weather summary
        }
        if (FieldSourceKind == 3) {
            return vec4(0.95, 0.76, 0.38, sourceAlpha); // PA cluster
        }
        if (FieldSourceKind == 4) {
            return vec4(0.78, 0.54, 0.95, sourceAlpha); // PA region
        }
        return vec4(0.95, 0.44, 0.48, sourceAlpha);
    }
    if (DebugMode == 7) {
        float density = densityAt(p);
        if (density <= TuneDensityThreshold) {
            return vec4(0.0);
        }
        float visible = smoothstep(TuneDensityThreshold, max(TuneDensityThreshold * 8.0, 0.08), density);
        return vec4(visible, visible, visible, max(0.22, visible));
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
    float rawRayEnd = hit.y;
    if (rawRayEnd <= rayStart) {
        discard;
    }

    if (DebugMode == 7) {
        int stepBudget = clamp(RaymarchSteps, 4, MAX_RAY_STEPS);
        float rayLength = rawRayEnd - rayStart;
        float stepSize = rayLength / float(stepBudget);
        float maxDensity = 0.0;
        for (int i = 0; i < MAX_RAY_STEPS; i++) {
            if (i >= stepBudget) {
                break;
            }
            float t = rayStart + (float(i) + 0.5) * stepSize;
            vec3 p = CameraPos + rayDirection * t;
            float height01;
            float density = densityAndHeightAt(p, height01);
            maxDensity = max(maxDensity, density);
        }
        if (maxDensity <= TuneDensityThreshold) {
            discard;
        }
        float visible = smoothstep(TuneDensityThreshold, max(TuneDensityThreshold * 8.0, 0.08), maxDensity);
        gl_FragDepth = gl_FragCoord.z;
        fragColor = vec4(visible, visible, visible, 0.92);
        return;
    }

    if (DebugMode > 0) {
        vec3 midpoint = CameraPos + rayDirection * ((rayStart + rawRayEnd) * 0.5);
        vec3 debugPoint = DebugMode == 1 ? fragWorldPos : midpoint;
        gl_FragDepth = gl_FragCoord.z;
        fragColor = debugColor(debugPoint);
        if (fragColor.a <= 0.001) {
            discard;
        }
        return;
    }

    float rayEnd = sceneRayEnd(rayDirection, rawRayEnd);
    if (rayEnd <= rayStart) {
        discard;
    }

    int stepBudget = clamp(RaymarchSteps, 4, MAX_RAY_STEPS);
    float rayLength = rayEnd - rayStart;
    float cloudReferenceLength = min(
        max(FieldRadius, 1.0) * 2.0,
        max(FieldTopY - FieldBaseY, 1.0)
    );
    float targetStepSize = max(cloudReferenceLength / float(stepBudget), 0.50);
    int stepCount = int(clamp(ceil(rayLength / targetStepSize), 4.0, float(stepBudget)));
    float stepSize = rayLength / float(stepCount);
    float referenceStepSize = max((max(FieldRadius, 1.0) * 2.0) / 64.0, 1.0);
    float jitter = hash1(dot(floor(gl_FragCoord.xy), vec2(12.9898, 78.233)) + FieldSeed) - 0.5;
    float transmittance = 1.0;
    vec3 accumulated = vec3(0.0);
    float firstCloudDepth = 1.0;
    bool hasCloudDepth = false;

    for (int i = 0; i < MAX_RAY_STEPS; i++) {
        if (i >= stepCount) {
            break;
        }
        float t = rayStart + (float(i) + 0.5 + jitter * 0.35) * stepSize;
        vec3 p = CameraPos + rayDirection * t;
        float height01;
        float density = densityAndHeightAt(p, height01);
        if (density <= TuneDensityThreshold) {
            continue;
        }
        if (!hasCloudDepth) {
            firstCloudDepth = worldDepth(p);
            hasCloudDepth = true;
        }

        float underside = mix(1.0 - TuneUndersideDarkening, 1.0, smoothstep(0.10, 0.76, height01));
        vec3 sideNormal = normalize(vec3(p.x - FieldCenter.x, FieldRadius * 0.28, p.z - FieldCenter.z));
        float sunSide = dot(sideNormal, normalize(vec3(-0.45, 0.62, 0.25))) * 0.5 + 0.5;
        float softLight = mix(0.94, 1.10, sunSide) * mix(0.92, 1.08, height01);
        float stormDarkening = mix(1.0, 0.64, saturate(FieldStormPotential) * (1.0 - height01));
        vec3 undersideColor = vec3(0.62, 0.62, 0.60);
        vec3 topColor = vec3(1.00, 1.00, 0.96) * TuneBrightness;
        vec3 cloudColor = mix(undersideColor, topColor, underside) * stormDarkening * softLight;

        float stepOpacityScale = clamp(stepSize / referenceStepSize, 0.20, 4.0);
        float sampleAlpha = clamp(1.0 - exp(-density * TuneOpacityStrength * stepOpacityScale), 0.0, NORMAL_MAX_SAMPLE_ALPHA);
        accumulated += transmittance * sampleAlpha * cloudColor;
        transmittance *= 1.0 - sampleAlpha;
        if (transmittance < 0.02) {
            break;
        }
    }

    if (!hasCloudDepth) {
        discard;
    }

    float alpha = saturate(1.0 - transmittance);
    if (alpha < 0.01) {
        discard;
    }

    vec3 color = accumulated / max(alpha, 0.0001);
    gl_FragDepth = firstCloudDepth;
    fragColor = vec4(color, min(alpha, TuneMaxFinalAlpha));
}
