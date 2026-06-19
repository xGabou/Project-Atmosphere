#version 150

uniform sampler2D DepthSampler;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 InverseProjMat;
uniform mat4 InverseModelViewMat;
uniform vec3 CameraPos;
uniform vec4 CloudColor;
uniform vec3 SunDirection;
uniform vec3 SunColor;
uniform vec3 AmbientCloudColor;
uniform float SunsetStrength;
uniform float HorizonGlowStrength;
uniform float EdgeLightStrength;
uniform float UndersideDarkening;
uniform float LightAbsorption;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float AnimationTime;
uniform float MaxDistance;
uniform vec2 OutSize;
uniform int WriteDepth;
uniform vec3 CloudCenter;
uniform vec3 CloudPreviousCenter;
uniform vec3 CloudVelocity;
uniform float CloudRadius;
uniform float CloudBaseY;
uniform float CloudTopY;
uniform float CloudDensity;
uniform float CloudCoverage;
uniform float CloudEdgeSoftness;
uniform float CloudWorldTime;
uniform float CloudPartialTick;
uniform int CloudAgeTicks;
uniform int CloudLifetimeTicks;
uniform float CloudGrowth;
uniform float CloudDecay;
uniform float CloudVerticalThickness;
uniform float CloudEdgeErosionStrength;
uniform float CloudTopSoftness;
uniform float CloudBaseSoftness;
uniform float CloudBaseDarkness;
uniform float CloudNoiseScale;
uniform float CloudDetailNoiseScale;
uniform float CloudErosionNoiseScale;
uniform float CloudDensityMultiplier;
uniform float CloudCoverageMultiplier;
uniform float CloudHeightSquash;
uniform float CloudTowerStrength;
uniform float CloudAnvilStrength;
uniform float CloudPrecipitationCoreStrength;
uniform float CloudMaterialDarkness;
uniform float CloudMaterialPrecipitationTint;
uniform float CloudMaterialOpacityBias;
uniform float CloudMaterialUndersideDarkness;
uniform float CloudMaterialEdgeErosion;
uniform float CloudStormCoreDarkening;
uniform float CloudMaterialShadowContribution;
uniform float CloudMaterialLightningResponse;
uniform vec2 CloudShapeLobeCounts;
uniform float CloudShapeLobeStrength;
uniform float CloudShapeVerticalTilt;
uniform float CloudShapeWindShearStrength;
uniform float CloudShapeCellSplitStrength;
uniform float CloudShapeTowerNarrowing;
uniform float CloudShapeAnvilSpread;
uniform float CloudShapeBaseFlattening;
uniform float CloudShapeEdgeRaggedness;
uniform float CloudShapeStormWallStrength;
uniform int CloudMorphologyFamily;
uniform int CloudStormVisualTier;
uniform float CloudStormVisualDarkness;
uniform int CloudPrecipitationTier;
uniform float CloudPrecipitationIntensity;
uniform float CloudShadowContribution;
uniform float CloudLightningInfluence;
uniform int CloudSeed;
uniform int RaymarchSteps;
uniform float RayJitterFrame;
uniform float RayJitterStrength;
uniform float RayJitterTemporalStrength;
uniform int CloudDebugMode;

in vec2 texCoord;
out vec4 fragColor;

const int MAX_RAYMARCH_STEPS = 64;
const int MORPHOLOGY_PUFF = 0;
const int MORPHOLOGY_TOWER = 1;
const int MORPHOLOGY_STORM_ANVIL = 2;
const int MORPHOLOGY_SHEET = 3;
const int MORPHOLOGY_CELLULAR_SHEET = 4;
const int MORPHOLOGY_FILAMENT = 5;
const int MORPHOLOGY_SPIRAL_STORM = 6;

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
}

vec2 safeNormalize2(vec2 value, vec2 fallback) {
    float lenSq = dot(value, value);
    return lenSq > 0.000001 ? value * inversesqrt(lenSq) : fallback;
}

float hash1(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float interleavedGradientNoise(vec2 p) {
    return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
}

float paCloudNoise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);

    float n000 = hash1(dot(i + vec3(0.0, 0.0, 0.0), vec3(1.0, 57.0, 113.0)));
    float n100 = hash1(dot(i + vec3(1.0, 0.0, 0.0), vec3(1.0, 57.0, 113.0)));
    float n010 = hash1(dot(i + vec3(0.0, 1.0, 0.0), vec3(1.0, 57.0, 113.0)));
    float n110 = hash1(dot(i + vec3(1.0, 1.0, 0.0), vec3(1.0, 57.0, 113.0)));
    float n001 = hash1(dot(i + vec3(0.0, 0.0, 1.0), vec3(1.0, 57.0, 113.0)));
    float n101 = hash1(dot(i + vec3(1.0, 0.0, 1.0), vec3(1.0, 57.0, 113.0)));
    float n011 = hash1(dot(i + vec3(0.0, 1.0, 1.0), vec3(1.0, 57.0, 113.0)));
    float n111 = hash1(dot(i + vec3(1.0, 1.0, 1.0), vec3(1.0, 57.0, 113.0)));

    float x00 = mix(n000, n100, f.x);
    float x10 = mix(n010, n110, f.x);
    float x01 = mix(n001, n101, f.x);
    float x11 = mix(n011, n111, f.x);
    float y0 = mix(x00, x10, f.y);
    float y1 = mix(x01, x11, f.y);
    return mix(y0, y1, f.z) * 2.0 - 1.0;
}

float fbm(vec3 p, int octaves) {
    float value = 0.0;
    float amplitude = 0.5;
    float frequency = 1.0;

    for (int i = 0; i < 4; i++) {
        if (i >= octaves) {
            break;
        }
        value += paCloudNoise3(p * frequency) * amplitude;
        frequency *= 2.0;
        amplitude *= 0.5;
    }

    return value * 0.5 + 0.5;
}

vec2 rotate2(vec2 value, float angle) {
    float s = sin(angle);
    float c = cos(angle);
    return vec2(value.x * c - value.y * s, value.x * s + value.y * c);
}

float ellipsoidField(vec3 p, vec3 center, vec3 radius) {
    vec3 q = (p - center) / max(radius, vec3(0.001));
    float d = length(q);
    return 1.0 - smoothstep(0.62, 1.0, d);
}

float puffStructuralField(vec3 localVolume, vec3 seedOffset, float seedValue) {
    float field = ellipsoidField(localVolume, vec3(0.0, 0.42, 0.0), vec3(0.48, 0.28, 0.48)) * 0.72;
    for (int i = 0; i < 6; i++) {
        float fi = float(i);
        float angle = fi * 2.399963 + hash1(seedValue + fi * 17.0) * 1.10;
        float ring = mix(0.10, 0.44, hash1(seedValue + fi * 23.0));
        float y = mix(0.24, 0.66, hash1(seedValue + fi * 31.0));
        vec3 center = vec3(cos(angle) * ring, y, sin(angle) * ring);
        float radiusJitter = mix(0.82, 1.18, hash1(seedValue + fi * 47.0));
        vec3 radius = vec3(0.28, 0.20, 0.28) * radiusJitter;
        field = max(field, ellipsoidField(localVolume, center, radius));
    }
    float edgeNoise = fbm(localVolume * vec3(6.0, 3.0, 6.0) + seedOffset * 0.011, 2);
    return saturate(field * mix(0.72, 1.14, edgeNoise));
}

float towerStructuralField(vec3 localVolume, vec3 seedOffset, float seedValue) {
    float field = 0.0;
    for (int i = 0; i < 7; i++) {
        float tier = float(i) / 6.0;
        float angle = tier * 5.40 + hash1(seedValue + float(i) * 29.0) * 1.35;
        float ring = mix(0.02, 0.18, hash1(seedValue + float(i) * 37.0)) * mix(0.58, 1.12, tier);
        float y = mix(0.12, 0.88, tier);
        float shoulder = smoothstep(0.12, 0.62, tier) * (1.0 - smoothstep(0.88, 1.0, tier));
        float horizontalRadius = mix(0.20, 0.40, shoulder) * mix(0.86, 1.14, hash1(seedValue + float(i) * 43.0));
        vec3 center = vec3(cos(angle) * ring, y, sin(angle) * ring);
        vec3 radius = vec3(horizontalRadius, mix(0.13, 0.20, shoulder), horizontalRadius);
        field = max(field, ellipsoidField(localVolume, center, radius));
    }
    float sideTurbulence = fbm(localVolume * vec3(7.5, 4.5, 7.5) + seedOffset * 0.013, 2);
    return saturate(field * mix(0.68, 1.18, sideTurbulence));
}

float stormAnvilStructuralField(vec3 localVolume, vec3 seedOffset, float seedValue) {
    float tower = towerStructuralField(localVolume, seedOffset, seedValue) * 0.86;
    float upper = smoothstep(0.52, 0.74, localVolume.y) * (1.0 - smoothstep(0.96, 1.0, localVolume.y));
    vec2 anvilLocal = rotate2(localVolume.xz, hash1(seedValue + 541.0) * 1.2 - 0.6);
    float anvil = ellipsoidField(vec3(anvilLocal.x, localVolume.y, anvilLocal.y), vec3(0.16, 0.76, 0.0), vec3(0.92 + CloudShapeAnvilSpread * 0.36, 0.15, 0.48));
    float wall = smoothstep(0.22, 0.70, length(localVolume.xz)) * (1.0 - smoothstep(0.82, 1.08, length(localVolume.xz)));
    float ragged = fbm(localVolume * vec3(5.0, 2.0, 5.0) + seedOffset * 0.017, 2);
    return saturate(max(tower, anvil * upper) * mix(0.74, 1.18, ragged) + wall * CloudShapeStormWallStrength * 0.18);
}

float sheetStructuralField(vec3 localVolume, vec3 seedOffset, float seedValue) {
    vec2 sheetLocal = rotate2(localVolume.xz, hash1(seedValue + 607.0) * 3.14159);
    sheetLocal.x *= 0.70;
    sheetLocal.y *= 1.18;
    float horizontal = 1.0 - smoothstep(0.60, 1.04, length(sheetLocal));
    float vertical = 1.0 - smoothstep(0.16, 0.48, abs(localVolume.y - 0.46));
    float ragged = fbm(vec3(sheetLocal * 4.0, localVolume.y * 1.8) + seedOffset * 0.009, 2);
    return saturate(horizontal * vertical * mix(0.72, 1.12, ragged));
}

float cellularSheetStructuralField(vec3 localVolume, vec3 seedOffset, float seedValue) {
    float sheet = sheetStructuralField(localVolume, seedOffset, seedValue);
    vec2 cellLocal = rotate2(localVolume.xz, hash1(seedValue + 719.0) * 2.4);
    float cellsA = fbm(vec3(cellLocal * 7.2, localVolume.y * 2.0) + seedOffset * 0.015, 2);
    float cellsB = paCloudNoise3(vec3(cellLocal * 13.0, localVolume.y * 3.0) + seedOffset * 0.021) * 0.5 + 0.5;
    float occupancy = smoothstep(0.34, 0.72, mix(cellsA, cellsB, 0.38));
    return saturate(sheet * mix(0.04, 1.10, occupancy));
}

float filamentStructuralField(vec3 localVolume, vec3 seedOffset, float seedValue) {
    vec2 filamentLocal = rotate2(localVolume.xz, hash1(seedValue + 823.0) * 6.2831853);
    float trailA = 1.0 - smoothstep(0.05, 0.24, abs(filamentLocal.y + sin(filamentLocal.x * 8.0 + seedValue * 0.013) * 0.045));
    float trailB = 1.0 - smoothstep(0.04, 0.18, abs(filamentLocal.y - 0.20 + sin(filamentLocal.x * 6.0 + 1.7) * 0.035));
    float alongFade = 1.0 - smoothstep(0.76, 1.20, abs(filamentLocal.x));
    float vertical = 1.0 - smoothstep(0.10, 0.36, abs(localVolume.y - 0.52));
    float wispy = fbm(vec3(filamentLocal * vec2(7.0, 18.0), localVolume.y * 2.0) + seedOffset * 0.012, 2);
    return saturate(max(trailA, trailB * 0.72) * alongFade * vertical * mix(0.54, 1.20, wispy));
}

float spiralStormStructuralField(vec3 localVolume, vec3 seedOffset, float seedValue) {
    vec2 spiralLocal = localVolume.xz;
    float radius = length(spiralLocal);
    float angle = atan(spiralLocal.y, spiralLocal.x);
    float phase = hash1(seedValue + 929.0) * 6.2831853;
    float bandSignal = 0.5 + 0.5 * cos(angle * 3.2 - radius * 12.0 + phase);
    float secondary = 0.5 + 0.5 * cos(angle * 5.4 - radius * 18.0 - phase * 0.7);
    float bands = smoothstep(0.54, 0.88, mix(bandSignal, secondary, 0.32));
    float envelope = (1.0 - smoothstep(0.92, 1.12, radius)) * smoothstep(0.10, 0.26, radius);
    float eye = smoothstep(0.14, 0.26, radius);
    float core = 1.0 - smoothstep(0.28, 0.58, radius);
    float vertical = 1.0 - smoothstep(0.24, 0.56, abs(localVolume.y - 0.48));
    float ragged = fbm(localVolume * vec3(4.5, 2.0, 4.5) + seedOffset * 0.016, 2);
    return saturate(max(core * eye * 0.82, bands * envelope) * vertical * mix(0.70, 1.16, ragged));
}

float morphologyStructuralField(vec3 localVolume, vec3 seedOffset, float seedValue) {
    if (CloudMorphologyFamily == MORPHOLOGY_TOWER) {
        return towerStructuralField(localVolume, seedOffset, seedValue);
    }
    if (CloudMorphologyFamily == MORPHOLOGY_STORM_ANVIL) {
        return stormAnvilStructuralField(localVolume, seedOffset, seedValue);
    }
    if (CloudMorphologyFamily == MORPHOLOGY_SHEET) {
        return sheetStructuralField(localVolume, seedOffset, seedValue);
    }
    if (CloudMorphologyFamily == MORPHOLOGY_CELLULAR_SHEET) {
        return cellularSheetStructuralField(localVolume, seedOffset, seedValue);
    }
    if (CloudMorphologyFamily == MORPHOLOGY_FILAMENT) {
        return filamentStructuralField(localVolume, seedOffset, seedValue);
    }
    if (CloudMorphologyFamily == MORPHOLOGY_SPIRAL_STORM) {
        return spiralStormStructuralField(localVolume, seedOffset, seedValue);
    }
    return puffStructuralField(localVolume, seedOffset, seedValue);
}

vec3 seedOffset3() {
    float seed = float(CloudSeed);
    return vec3(
        hash1(seed + 17.0),
        hash1(seed + 41.0),
        hash1(seed + 73.0)
    ) * 840.0;
}

vec3 reconstructWorld(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 view = InverseProjMat * clip;
    view /= view.w;
    vec4 world = InverseModelViewMat * vec4(view.xyz, 1.0);
    return world.xyz / world.w;
}

vec3 getWorldRay(vec2 uv) {
    vec4 clip = vec4(uv * 2.0 - 1.0, 1.0, 1.0);
    vec4 view = InverseProjMat * clip;
    view /= view.w;
    return normalize((InverseModelViewMat * vec4(normalize(view.xyz), 0.0)).xyz);
}

float projectDepth(vec3 worldPos) {
    vec4 clip = ProjMat * ModelViewMat * vec4(worldPos, 1.0);
    if (abs(clip.w) <= 0.000001) {
        return 1.0;
    }
    float ndcDepth = clip.z / clip.w;
    return clamp(ndcDepth * 0.5 + 0.5, 0.0, 1.0);
}

bool intersectAabb(vec3 ro, vec3 rd, vec3 bmin, vec3 bmax, out float tNear, out float tFar) {
    vec3 inv = 1.0 / rd;
    vec3 t0 = (bmin - ro) * inv;
    vec3 t1 = (bmax - ro) * inv;
    vec3 tsmaller = min(t0, t1);
    vec3 tbigger = max(t0, t1);
    tNear = max(max(tsmaller.x, tsmaller.y), tsmaller.z);
    tFar = min(min(tbigger.x, tbigger.y), tbigger.z);
    return tFar > max(tNear, 0.0);
}

float sampleCloudField(vec3 samplePos, vec3 seedOffset, float seedValue) {
    float heightRange = max(CloudTopY - CloudBaseY, 0.001);
    float vertical = (samplePos.y - CloudBaseY) / heightRange;
    float sheetPadding = max(0.001, smoothstep(1.20, 3.20, CloudHeightSquash) * min(0.45, 14.0 / heightRange));
    if (vertical < -sheetPadding || vertical > 1.0 + sheetPadding) {
        return 0.0;
    }
    float verticalFeather = smoothstep(-sheetPadding, 0.0, vertical)
        * (1.0 - smoothstep(1.0, 1.0 + sheetPadding, vertical));

    float lifecycleFactor = saturate(CloudGrowth * (1.0 - CloudDecay));
    float materialOpacity = clamp(CloudMaterialOpacityBias, 0.0, 2.0);
    float precipitationPacking = 1.0 + saturate(CloudPrecipitationIntensity + CloudPrecipitationCoreStrength) * 0.18;
    float effectiveDensity = saturate(CloudDensity * CloudCoverage * CloudDensityMultiplier * CloudCoverageMultiplier * lifecycleFactor * materialOpacity * precipitationPacking);

    vec3 motion = (CloudCenter - CloudPreviousCenter) * (0.35 + CloudPartialTick * 0.15);
    vec3 baseNoisePos = samplePos + motion * 0.2 + seedOffset;
    float visualThickness = max(CloudVerticalThickness, 0.05);
    float heightSquash = max(CloudHeightSquash, 0.10);
    float effectiveVerticalThickness = clamp(visualThickness * heightSquash, 0.18, 1.35);
    float shapedVertical = (vertical - 0.5) / effectiveVerticalThickness + 0.5;
    if (shapedVertical < -0.08 || shapedVertical > 1.08) {
        return 0.0;
    }

    vec2 localHorizontal = samplePos.xz - CloudCenter.xz;
    vec2 shearDirection = safeNormalize2(abs(CloudVelocity.x) + abs(CloudVelocity.z) > 0.001
        ? CloudVelocity.xz
        : vec2(hash1(seedValue + 211.0) - 0.5, hash1(seedValue + 223.0) - 0.5), vec2(1.0, 0.0));
    float shearLift = (shapedVertical - 0.35) * CloudShapeWindShearStrength + shapedVertical * CloudShapeVerticalTilt;
    localHorizontal -= shearDirection * CloudRadius * shearLift * 0.24;
    float horizontalDistance = length(localHorizontal);
    float radiusInv = 1.0 / max(CloudRadius, 0.001);
    float baseHorizontal = horizontalDistance * radiusInv;
    if (baseHorizontal >= 1.24) {
        return 0.0;
    }

    float edgeAngle = atan(localHorizontal.y, localHorizontal.x);
    float minLobes = max(1.0, CloudShapeLobeCounts.x);
    float maxLobes = max(minLobes, CloudShapeLobeCounts.y);
    float lobeCount = mix(minLobes, maxLobes, hash1(seedValue + 131.0));
    float lobePhase = hash1(seedValue + 149.0) * 6.2831853;
    float lobeWave = sin(edgeAngle * lobeCount + lobePhase);
    float silhouetteNoise = paCloudNoise3(vec3(
        localHorizontal * 0.015 + seedOffset.xz * 0.021,
        shapedVertical * 2.0 + seedOffset.y * 0.017
    )) * mix(0.30, 0.72, saturate(CloudShapeEdgeRaggedness));
    float seededRadiusWarp = 1.0 + (lobeWave * 0.12 * CloudShapeLobeStrength + silhouetteNoise * 0.18) * smoothstep(0.12, 1.0, baseHorizontal);
    float rawHorizontal = horizontalDistance / max(CloudRadius * clamp(seededRadiusWarp, 0.78, 1.20), 0.001);

    float topWarp = paCloudNoise3(baseNoisePos * vec3(0.018, 0.0, 0.018) + vec3(19.7, CloudWorldTime * 0.0012, 4.1) + seedOffset * 0.011) * 0.5;
    float baseWarp = paCloudNoise3(baseNoisePos * vec3(0.014, 0.0, 0.014) + vec3(3.4, CloudWorldTime * -0.0008, 27.5) + seedOffset * 0.009) * 0.5;
    float warpedVertical = shapedVertical + topWarp * 0.16 * smoothstep(0.45, 1.0, shapedVertical) - baseWarp * 0.08 * (1.0 - smoothstep(0.0, 0.35, shapedVertical));
    float baseSoftness = max(CloudBaseSoftness, 0.01);
    float topSoftness = max(CloudTopSoftness, 0.01);
    float verticalFade = smoothstep(0.0, baseSoftness, warpedVertical) * (1.0 - smoothstep(1.0 - topSoftness, 1.0, warpedVertical));
    float interiorFade = verticalFeather;

    float layerBias = saturate((CloudHeightSquash - 1.20) * 0.36 + CloudShapeBaseFlattening * 0.55);
    float puffyRadius = mix(0.18, 1.00, smoothstep(0.04, 0.46, warpedVertical))
        * mix(1.0, 0.22, smoothstep(0.58, 1.0, warpedVertical));
    float layerRadius = mix(0.88, 1.06, smoothstep(0.04, 0.34, warpedVertical))
        * mix(1.0, 0.96, smoothstep(0.88, 1.0, warpedVertical));
    float towerRadius = mix(0.30, 0.68, smoothstep(0.04, 0.38, warpedVertical))
        * mix(1.0, mix(0.68, 0.34, CloudShapeTowerNarrowing), smoothstep(0.78, 1.0, warpedVertical));
    float anvilRadius = CloudAnvilStrength
        * smoothstep(0.56, 0.88, warpedVertical)
        * (1.0 - smoothstep(0.94, 1.0, warpedVertical))
        * mix(0.48, 0.88, CloudShapeAnvilSpread);
    float effectiveRadiusFactor = mix(puffyRadius, layerRadius, layerBias);
    effectiveRadiusFactor = mix(effectiveRadiusFactor, towerRadius, saturate(CloudTowerStrength));
    effectiveRadiusFactor = clamp(effectiveRadiusFactor + anvilRadius, 0.12, 1.08);

    float normalizedHorizontal = rawHorizontal / effectiveRadiusFactor;
    if (normalizedHorizontal >= 1.16) {
        return 0.0;
    }

    float edgeSoftness = max(saturate(CloudEdgeSoftness), 0.001);
    float horizontalFade = 1.0 - smoothstep(1.0 - edgeSoftness, 1.10, normalizedHorizontal);
    float edgeFactor = smoothstep(0.55, 1.0, normalizedHorizontal);
    float silhouettePower = mix(2.35, 0.95, layerBias);
    float silhouetteFade = pow(1.0 - smoothstep(0.36, 1.0, normalizedHorizontal), silhouettePower);
    vec2 splitDirection = safeNormalize2(vec2(hash1(seedValue + 307.0) - 0.5, hash1(seedValue + 331.0) - 0.5), vec2(0.0, 1.0));
    float splitLine = abs(dot(safeNormalize2(localHorizontal + splitDirection * 0.001, splitDirection), splitDirection));
    float splitCarve = mix(1.0, smoothstep(0.08, 0.42, splitLine), CloudShapeCellSplitStrength * edgeFactor);

    float noiseScale = max(CloudNoiseScale, 0.001);
    float detailNoiseScale = max(CloudDetailNoiseScale, 0.001);
    float erosionNoiseScale = max(CloudErosionNoiseScale, 0.001);
    float lobeNoise = fbm(baseNoisePos * vec3(noiseScale, noiseScale * 1.75, noiseScale) + vec3(0.0, CloudWorldTime * 0.0015, 0.0) + seedOffset * 0.017, 2);
    float layerNoise = paCloudNoise3(baseNoisePos * vec3(detailNoiseScale * 0.50, detailNoiseScale, detailNoiseScale * 0.50) + vec3(12.0, CloudWorldTime * 0.0025, 8.0) + seedOffset * 0.013) * 0.5 + 0.5;
    float detailNoise = paCloudNoise3(baseNoisePos * vec3(erosionNoiseScale, erosionNoiseScale * 1.25, erosionNoiseScale) + vec3(31.0, CloudWorldTime * -0.0030, 6.0) + seedOffset * 0.019) * 0.5 + 0.5;
    float verticalLayerNoise = fbm(vec3(localHorizontal * max(detailNoiseScale * 0.34, 0.012), warpedVertical * 6.5 + seedOffset.y * 0.01), 2);
    float verticalVolumeBreakup = mix(1.0, mix(0.74, 1.16, verticalLayerNoise), saturate(0.45 + layerBias * 0.35 + CloudTowerStrength * 0.20));
    vec3 localVolume = vec3(localHorizontal.x * radiusInv, warpedVertical, localHorizontal.y * radiusInv);
    float morphologyField = morphologyStructuralField(localVolume, seedOffset, seedValue);

    float seedLobeBias = mix(-0.08, 0.08, hash1(seedValue + 197.0));
    float lobeShape = mix(0.74 + seedLobeBias, 1.18 + seedLobeBias, lobeNoise);
    float layeredShape = mix(0.82, 1.10, layerNoise);
    float centerWeight = 1.0 - smoothstep(0.0, 0.58, normalizedHorizontal);
    float structuralStrength = mix(0.42, 0.86, saturate(CloudShapeLobeStrength + CloudShapeEdgeRaggedness * 0.35));
    float qualityFactor = smoothstep(12.0, 44.0, float(RaymarchSteps));
    float preservedCore = max(morphologyField, centerWeight * mix(0.58, 0.78, CloudPrecipitationCoreStrength));
    float morphologyMask = mix(1.0, preservedCore, structuralStrength * mix(0.54, 1.0, qualityFactor));
    float edgeBreakup = smoothstep(0.20, 0.86, detailNoise + silhouetteNoise * 0.20);
    float materialErosion = saturate(CloudMaterialEdgeErosion);
    float edgeCarve = mix(1.0, edgeBreakup, edgeFactor * saturate(CloudEdgeErosionStrength + materialErosion + CloudShapeEdgeRaggedness * 0.78));
    float corePreserve = mix(edgeCarve, max(edgeCarve, 0.86), centerWeight);
    float towerBoost = 1.0 + CloudTowerStrength * centerWeight * smoothstep(0.22, 0.86, warpedVertical) * 0.42;
    float anvilBoost = 1.0 + CloudAnvilStrength * smoothstep(0.58, 1.0, warpedVertical) * smoothstep(0.20, 0.92, normalizedHorizontal) * 0.34;
    float precipitationCore = 1.0 + (CloudPrecipitationCoreStrength + CloudPrecipitationIntensity * 0.45) * centerWeight * (1.0 - smoothstep(0.28, 0.72, warpedVertical)) * 0.35;
    float stormStrength = saturate(max(CloudStormVisualDarkness, CloudStormCoreDarkening));
    float stormWall = 1.0 + (CloudShapeStormWallStrength + stormStrength * 0.35) * smoothstep(0.48, 0.88, normalizedHorizontal) * (1.0 - smoothstep(0.90, 1.0, normalizedHorizontal)) * 0.48;
    float underside = saturate(max(CloudBaseDarkness, CloudMaterialUndersideDarkness));
    float baseProfile = mix(1.0, 0.90 - underside * 0.12, underside * (1.0 - smoothstep(0.0, 0.42, warpedVertical)));
    float verticalBody = smoothstep(-0.04, 0.20, warpedVertical) * (1.0 - smoothstep(0.86, 1.08, warpedVertical));
    verticalBody = mix(verticalBody, 1.0, saturate(CloudTowerStrength + CloudAnvilStrength * 0.45));

    return saturate(effectiveDensity * horizontalFade * silhouetteFade * verticalFade * verticalBody * interiorFade * morphologyMask * lobeShape * layeredShape * verticalVolumeBreakup * corePreserve * splitCarve * towerBoost * anvilBoost * precipitationCore * stormWall * baseProfile);
}

vec3 computeSampleLighting(vec3 samplePos, float density, vec3 rayDir) {
    float heightRange = max(CloudTopY - CloudBaseY, 0.001);
    float vertical = saturate((samplePos.y - CloudBaseY) / heightRange);
    float bottomFactor = 1.0 - smoothstep(0.15, 0.65, vertical);
    float topFactor = smoothstep(0.35, 1.0, vertical);

    float horizontalDistance = length(samplePos.xz - CloudCenter.xz);
    float normalizedHorizontal = horizontalDistance / max(CloudRadius, 0.001);
    float edgeFactor = smoothstep(0.55, 1.0, normalizedHorizontal);
    float sunFacing = saturate(dot(normalize(SunDirection), normalize(-rayDir)));
    float rimLight = pow(edgeFactor, 1.5)
        * SunsetStrength
        * EdgeLightStrength
        * (0.30 + sunFacing * 0.70)
        * (0.45 + topFactor * 0.55);

    float stormDarkness = saturate(max(CloudStormVisualDarkness, CloudStormCoreDarkening));
    float materialDarkness = saturate(CloudMaterialDarkness + stormDarkness * 0.55);
    float shadowing = saturate(CloudShadowContribution + CloudMaterialShadowContribution * 0.65);
    float densityDarkening = exp(-density * LightAbsorption * (1.0 + shadowing * 0.55 + stormDarkness * 0.45));
    vec3 precipitationTint = mix(vec3(1.0), vec3(0.62, 0.68, 0.74), saturate(CloudMaterialPrecipitationTint + CloudPrecipitationIntensity * 0.35));
    vec3 compatibilityTint = mix(CloudColor.rgb * precipitationTint, vec3(1.0), 0.78 - materialDarkness * 0.18);
    float undersideDarkening = saturate(UndersideDarkening + CloudBaseDarkness * 0.30 + CloudMaterialUndersideDarkness * 0.45 + stormDarkness * 0.35);
    vec3 baseLighting = AmbientCloudColor * compatibilityTint * mix(1.0 - undersideDarkening, 1.0, vertical);
    vec3 sunLighting = SunColor * rimLight;
    vec3 lightningLift = SunColor * CloudLightningInfluence * CloudMaterialLightningResponse * density * 0.08;
    vec3 horizonGlow = SunColor
        * HorizonGlowStrength
        * SunsetStrength
        * bottomFactor
        * (0.18 + sunFacing * 0.32);

    return baseLighting * densityDarkening + sunLighting + horizonGlow + lightningLift;
}

void main() {
    vec2 screenUv = gl_FragCoord.xy / OutSize;
    vec3 rayDir = getWorldRay(screenUv);
    vec3 rayOrigin = CameraPos;

    float heightRange = max(CloudTopY - CloudBaseY, 0.001);
    float volumePaddingY = smoothstep(1.20, 3.20, CloudHeightSquash) * min(28.0, max(heightRange * 0.45, CloudRadius * 0.035));
    vec3 volumeMin = vec3(CloudCenter.x - CloudRadius, CloudBaseY - volumePaddingY, CloudCenter.z - CloudRadius);
    vec3 volumeMax = vec3(CloudCenter.x + CloudRadius, CloudTopY + volumePaddingY, CloudCenter.z + CloudRadius);

    float tNear;
    float tFar;
    if (!intersectAabb(rayOrigin, rayDir, volumeMin, volumeMax, tNear, tFar)) {
        discard;
    }

    tNear = max(tNear, 0.0);

    float sceneDepth = texture(DepthSampler, screenUv).r;
    float sceneDistance = MaxDistance;
    if (sceneDepth < 1.0) {
        vec3 sceneWorld = reconstructWorld(screenUv, sceneDepth);
        sceneDistance = max(0.0, length(sceneWorld - rayOrigin) - 0.35);
    }

    float maxRay = min(min(tFar, MaxDistance), sceneDistance);
    if (maxRay <= tNear + 0.001) {
        if (CloudDebugMode != 0) {
            gl_FragDepth = sceneDepth;
            fragColor = vec4(1.0, 0.84, 0.05, 0.70);
            return;
        }
        discard;
    }

    float interval = maxRay - tNear;
    float firstCloudDepth = sceneDepth;
    bool firstCloudDepthSet = false;
    int steps = int(clamp(float(RaymarchSteps), 1.0, float(MAX_RAYMARCH_STEPS)));
    float stepSize = interval / float(steps);

    vec2 pixel = floor(gl_FragCoord.xy);
    float seed = mod(abs(float(CloudSeed)), 4096.0);
    float frameGroup = mod(floor(RayJitterFrame * 0.25), 8.0);
    vec2 cycleOffset = vec2(hash1(frameGroup * 19.17 + seed), hash1(frameGroup * 43.31 + seed)) * 11.0;
    float stableJitter = hash12(pixel + seed * vec2(0.071, 0.113));
    float gradientJitter = interleavedGradientNoise(pixel + seed * 0.37);
    float temporalJitter = hash12(pixel + cycleOffset + seed * vec2(0.017, 0.029));
    float baseJitter = mix(stableJitter, gradientJitter, 0.28);
    float jitter = mix(baseJitter, temporalJitter, clamp(RayJitterTemporalStrength, 0.0, 1.0));
    float t = tNear + jitter * stepSize * clamp(RayJitterStrength, 0.0, 1.0);
    float transmittance = 1.0;
    vec3 accum = vec3(0.0);
    vec3 seedOffset = seedOffset3();
    float seedValue = float(CloudSeed);
    float maxDensitySample = 0.0;

    for (int step = 0; step < MAX_RAYMARCH_STEPS; step++) {
        if (step >= steps || transmittance < 0.02) {
            break;
        }

        vec3 samplePos = rayOrigin + rayDir * t;
        float density = sampleCloudField(samplePos, seedOffset, seedValue);
        maxDensitySample = max(maxDensitySample, density);
        if (density > 0.0005) {
            if (!firstCloudDepthSet && density > 0.018) {
                firstCloudDepth = projectDepth(samplePos);
                firstCloudDepthSet = true;
            }
            float softenedDensity = pow(density, 1.18);
            float extinction = mix(3.0, 5.6, saturate(CloudDensity * CloudCoverage * CloudDensityMultiplier * CloudCoverageMultiplier));
            float alpha = 1.0 - exp(-softenedDensity * stepSize * extinction);
            vec3 cloudTint = computeSampleLighting(samplePos, density, rayDir);
            accum += cloudTint * alpha * transmittance;
            transmittance *= (1.0 - alpha);
        }

        t += stepSize;
    }

    float rawAlpha = 1.0 - transmittance;
    if (rawAlpha <= 0.001) {
        if (CloudDebugMode != 0) {
            gl_FragDepth = WriteDepth != 0 ? firstCloudDepth : sceneDepth;
            fragColor = maxDensitySample > 0.0005
                ? vec4(0.05, 0.88, 1.0, 0.70)
                : vec4(1.0, 0.05, 0.95, 0.70);
            return;
        }
        discard;
    }

    vec3 color = accum / max(rawAlpha, 0.0001);
    float fogFactor = smoothstep(FogStart, FogEnd, min(t, MaxDistance));
    color = mix(color, FogColor.rgb, fogFactor * 0.35);

    float opacityBoost = mix(0.92, 1.18, saturate(CloudDensity * CloudCoverage * CloudDensityMultiplier * CloudCoverageMultiplier));
    opacityBoost *= mix(0.78, 1.18, saturate(CloudMaterialOpacityBias * 0.5));
    gl_FragDepth = WriteDepth != 0 && firstCloudDepthSet ? firstCloudDepth : sceneDepth;
    fragColor = vec4(color, clamp(rawAlpha * opacityBoost, 0.0, 1.0));
}
