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
uniform float CloudShapeBaseRadius;
uniform float CloudShapeBaseOffset;
uniform float CloudShapeTopOffset;
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
const int CLOUD_DEBUG_OFF = 0;
const int CLOUD_DEBUG_BOUNDS = 1;
const int CLOUD_DEBUG_RAY_ENTRY_EXIT = 2;
const int CLOUD_DEBUG_LOCAL_RGB = 3;
const int CLOUD_DEBUG_VERTICAL_Y01 = 4;
const int CLOUD_DEBUG_PRIMARY_MASS = 5;
const int CLOUD_DEBUG_VERTICAL_ENVELOPE = 6;
const int CLOUD_DEBUG_FINAL_DENSITY = 7;
const int CLOUD_DEBUG_UNLIT_NO_LIGHTING = 8;

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

vec3 normalizeAabbLocal(vec3 samplePos, vec3 bmin, vec3 bmax) {
    return clamp((samplePos - bmin) / max(bmax - bmin, vec3(0.001)), 0.0, 1.0);
}

float normalizeCloudVertical(vec3 samplePos) {
    return saturate((samplePos.y - CloudBaseY) / max(CloudTopY - CloudBaseY, 0.001));
}

void emitCloudDebug(vec4 color, vec3 depthPos, float sceneDepth) {
    gl_FragDepth = WriteDepth != 0 ? projectDepth(depthPos) : sceneDepth;
    fragColor = color;
}

struct CloudDensitySample {
    float finalDensity;
    float unlitDensity;
    float primaryMass;
    float secondaryLobes;
    float tertiaryPuffs;
    float heightFactor;
    float edgeFactor;
    float coreFactor;
    float erosionFactor;
    float opticalDepthHint;
    float precipitationCoreFactor;
};

CloudDensitySample emptyDensitySample() {
    return CloudDensitySample(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
}

bool isSheetFamily() {
    return CloudMorphologyFamily == MORPHOLOGY_SHEET
        || CloudMorphologyFamily == MORPHOLOGY_CELLULAR_SHEET
        || CloudMorphologyFamily == MORPHOLOGY_FILAMENT;
}

bool isTowerFamily() {
    return CloudMorphologyFamily == MORPHOLOGY_TOWER
        || CloudMorphologyFamily == MORPHOLOGY_STORM_ANVIL
        || CloudMorphologyFamily == MORPHOLOGY_SPIRAL_STORM;
}

float deriveEffectiveDensity() {
    float lifecycle = saturate(CloudGrowth * (1.0 - CloudDecay));
    float precipitationPacking = 1.0 + saturate(CloudPrecipitationIntensity + CloudPrecipitationCoreStrength) * 0.14;
    return saturate(CloudDensity * CloudDensityMultiplier * lifecycle * clamp(CloudMaterialOpacityBias, 0.0, 2.0) * precipitationPacking);
}

float deriveEffectiveCoverage() {
    float lifecycle = saturate(CloudGrowth * (1.0 - CloudDecay));
    return saturate(CloudCoverage * CloudCoverageMultiplier * lifecycle);
}

float deriveSheetness() {
    float sheetness = saturate((CloudHeightSquash - 1.0) * 0.42);
    return isSheetFamily() ? 1.0 : sheetness;
}

float deriveTowerness() {
    float towerness = saturate(CloudTowerStrength);
    return isTowerFamily() ? max(0.72, towerness) : towerness;
}

float deriveStormDepth(float towerness) {
    return saturate(
        max(CloudStormVisualDarkness, CloudStormCoreDarkening) * 0.45
        + CloudPrecipitationCoreStrength * 0.24
        + towerness * 0.20
        + CloudAnvilStrength * 0.11
    );
}

int deriveLobeCount(float seedValue) {
    float minCount = max(1.0, CloudShapeLobeCounts.x);
    float maxCount = max(minCount, CloudShapeLobeCounts.y);
    return int(clamp(floor(mix(minCount, maxCount, hash1(seedValue + 17.0)) + 0.5), 1.0, 16.0));
}

vec3 toLocalVolume(vec3 samplePos, float seedValue, float vertical) {
    vec2 localHorizontal = samplePos.xz - CloudCenter.xz;
    vec2 shearDirection = safeNormalize2(abs(CloudVelocity.x) + abs(CloudVelocity.z) > 0.001
        ? CloudVelocity.xz
        : vec2(hash1(seedValue + 211.0) - 0.5, hash1(seedValue + 223.0) - 0.5), vec2(1.0, 0.0));
    float shear = (vertical - 0.35) * (CloudShapeWindShearStrength * 0.22 + CloudShapeVerticalTilt * 0.14);
    localHorizontal -= shearDirection * CloudRadius * shear;
    float radiusInv = 1.0 / max(CloudRadius, 0.001);
    return vec3(localHorizontal.x * radiusInv, vertical, localHorizontal.y * radiusInv);
}

float sampleVerticalEnvelope(float y, float sheetness, float towerness) {
    float verticalThickness = clamp(CloudVerticalThickness, 0.05, 4.0);
    float padding = clamp(0.045 + verticalThickness * 0.045, 0.045, 0.22);
    float baseSoftness = max(CloudBaseSoftness, 0.025);
    float topSoftness = max(CloudTopSoftness, 0.035);
    float base = smoothstep(-padding, baseSoftness, y);
    float top = 1.0 - smoothstep(1.0 - topSoftness, 1.0 + padding, y);
    float denseStart = mix(0.08, 0.02, sheetness);
    float denseEnd = mix(0.70 + verticalThickness * 0.08, 0.94, towerness);
    denseEnd = mix(denseEnd, 0.58 + verticalThickness * 0.06, sheetness);
    float denseBand = smoothstep(-0.02, denseStart, y) * (1.0 - smoothstep(denseEnd, 1.06, y));
    float fillWeight = saturate(verticalThickness * 0.30 + towerness * 0.46 + sheetness * 0.24);
    float puffyTop = mix(1.0, 0.82 + smoothstep(0.38, 0.86, y) * 0.18, towerness);
    return saturate(base * top * mix(denseBand, 1.0, fillWeight) * puffyTop);
}

float samplePrimaryMass(vec3 localVolume, float sheetness, float towerness) {
    float y = localVolume.y;
    float heightRange = max(CloudTopY - CloudBaseY, 0.001);
    float baseRadiusScale = clamp(CloudShapeBaseRadius / max(CloudRadius, 1.0), 0.24, 1.45);
    float baseOffset = clamp(CloudShapeBaseOffset / heightRange, 0.0, 0.92);
    float topOffset = clamp(CloudShapeTopOffset / heightRange, 0.02, 1.75);
    float baseRadius = mix(baseRadiusScale, max(baseRadiusScale, 0.96), sheetness);
    float topNarrow = mix(1.0, mix(0.82, 0.46, CloudShapeTowerNarrowing), towerness * smoothstep(0.42, 1.0, y));
    float anvilSpread = 1.0 + CloudAnvilStrength * CloudShapeAnvilSpread * smoothstep(0.58, 0.90, y) * 0.82;
    float horizontalRadius = baseRadius * topNarrow * anvilSpread * mix(0.92, 1.30, sheetness);
    float zRadius = horizontalRadius * mix(0.88, 1.12, sheetness);
    float bodyCenterY = mix(0.48, 0.42 + baseOffset * 0.10, sheetness);
    float bodyHeight = clamp(mix(
        0.36 + CloudVerticalThickness * 0.11 + towerness * 0.26 + topOffset * 0.04,
        0.20 + CloudVerticalThickness * 0.05 + topOffset * 0.025,
        sheetness
    ), 0.16, 0.86);
    float body = ellipsoidField(localVolume, vec3(0.0, bodyCenterY, 0.0), vec3(horizontalRadius, bodyHeight, zRadius));
    float baseShelf = (1.0 - smoothstep(0.04, 0.28, y))
        * (1.0 - smoothstep(0.72, 1.18, length(localVolume.xz)))
        * (0.18 + CloudShapeBaseFlattening * 0.46);
    float towerColumn = towerness
        * (1.0 - smoothstep(0.22, 0.72, length(localVolume.xz)))
        * smoothstep(0.08, 0.32, y)
        * (1.0 - smoothstep(0.95, 1.08, y))
        * 0.48;
    float sheetLayer = sheetness
        * (1.0 - smoothstep(0.58, 1.12, length(localVolume.xz)))
        * (1.0 - smoothstep(0.18 + CloudVerticalThickness * 0.04, 0.52, abs(y - 0.48)))
        * 0.52;
    return saturate(max(body, max(baseShelf, max(towerColumn, sheetLayer))));
}

float sampleSecondaryLobes(vec3 localVolume, float seedValue, int lobeCount, float sheetness, float towerness) {
    float result = 0.0;
    for (int i = 0; i < 16; i++) {
        if (i >= lobeCount) {
            break;
        }
        float fi = float(i);
        float a = fi * 2.399963 + hash1(seedValue + 41.0 + fi * 11.0) * 1.65;
        float ring = mix(0.08, mix(0.52, 0.92, sheetness), hash1(seedValue + 97.0 + fi * 13.0));
        float yRand = hash1(seedValue + 173.0 + fi * 17.0);
        float y = mix(0.20, 0.78, yRand);
        y = mix(y, 0.42 + (yRand - 0.5) * 0.30, sheetness);
        y = mix(y, mix(0.10, 0.92, fi / max(1.0, float(lobeCount) - 1.0)), towerness * 0.72);
        float radialJitter = mix(0.74, 1.16, hash1(seedValue + 251.0 + fi * 19.0));
        vec3 center = vec3(cos(a) * ring * radialJitter, y, sin(a) * ring * radialJitter);
        float radiusJitter = mix(0.78, 1.24, hash1(seedValue + 307.0 + fi * 23.0));
        vec3 radius = vec3(
            mix(0.18, 0.36, hash1(seedValue + 359.0 + fi * 29.0)),
            mix(0.12, 0.28, hash1(seedValue + 421.0 + fi * 31.0)),
            mix(0.18, 0.35, hash1(seedValue + 463.0 + fi * 37.0))
        ) * radiusJitter;
        radius.xz *= vec2(mix(1.0, 1.58, sheetness), mix(1.0, 1.38, sheetness));
        radius.y *= mix(1.14, 0.62, sheetness);
        radius.xz *= mix(vec2(1.0), vec2(mix(0.82, 0.48, CloudShapeTowerNarrowing)), towerness * smoothstep(0.58, 1.0, y));
        radius.y *= 1.0 + towerness * 0.30;
        result = max(result, ellipsoidField(localVolume, center, radius));
    }
    float attach = 1.0 - smoothstep(1.04, 1.44, length(localVolume.xz));
    return saturate(result * attach);
}

float sampleTertiaryPuffs(vec3 localVolume, vec3 seedOffset, float sheetness, float towerness) {
    vec3 noisePos = vec3(localVolume.x * 4.0, localVolume.y * 2.4 + CloudWorldTime * 0.0007, localVolume.z * 4.0) + seedOffset * 0.00031;
    float bodyNoise = fbm(noisePos, 3);
    float detailNoise = fbm(noisePos * 2.13 + vec3(17.0, 3.0, 11.0), 2);
    float edgeWeight = smoothstep(0.30, 1.08, length(localVolume.xz));
    float topWeight = smoothstep(0.34, 0.86, localVolume.y);
    float puff = saturate((bodyNoise * 0.62 + detailNoise * 0.38 - 0.42) * 1.35);
    return puff * saturate(edgeWeight * 0.58 + topWeight * 0.34 + sheetness * 0.34);
}

float sampleEdgeFactor(vec3 localVolume) {
    float horizontalEdge = smoothstep(0.48, 1.10, length(localVolume.xz));
    float baseEdge = 1.0 - smoothstep(0.05, 0.22, localVolume.y);
    float topEdge = smoothstep(0.78, 1.04, localVolume.y);
    return saturate(max(horizontalEdge, max(baseEdge, topEdge) * 0.45));
}

float sampleSoftErosion(vec3 localVolume, vec3 seedOffset, float edgeFactor, float sheetness) {
    float erosionStrength = saturate(CloudEdgeErosionStrength + CloudMaterialEdgeErosion * 0.45 + CloudShapeEdgeRaggedness * 0.45);
    if (erosionStrength <= 0.001) {
        return 0.0;
    }
    vec3 noisePos = vec3(localVolume.x * 5.3, localVolume.y * 3.1 - CloudWorldTime * 0.0009, localVolume.z * 5.3) + seedOffset * 0.00017;
    float erosionNoise = fbm(noisePos, 3);
    float verticalEdge = max(1.0 - smoothstep(0.05, 0.24, localVolume.y), smoothstep(0.76, 1.04, localVolume.y));
    float erosionMask = saturate(edgeFactor * 0.82 + verticalEdge * 0.18);
    float cellular = sheetness > 0.55
        ? saturate((0.56 - erosionNoise) * CloudShapeCellSplitStrength) * 0.24
        : 0.0;
    float softErosion = smoothstep(0.42, 0.86, erosionNoise) * erosionMask * mix(0.05, 0.34, erosionStrength);
    return saturate(softErosion + cellular) * 0.42;
}

CloudDensitySample sampleCloudDensity(vec3 samplePos, vec3 seedOffset, float seedValue) {
    float heightRange = max(CloudTopY - CloudBaseY, 0.001);
    float vertical = (samplePos.y - CloudBaseY) / heightRange;
    float verticalPadding = clamp(0.045 + clamp(CloudVerticalThickness, 0.05, 4.0) * 0.045, 0.045, 0.22);
    if (vertical < -verticalPadding || vertical > 1.0 + verticalPadding) {
        return emptyDensitySample();
    }

    float effectiveDensity = deriveEffectiveDensity();
    float effectiveCoverage = deriveEffectiveCoverage();
    if (effectiveDensity <= 0.001 || effectiveCoverage <= 0.001) {
        return emptyDensitySample();
    }

    vec3 localVolume = toLocalVolume(samplePos, seedValue, vertical);
    if (length(localVolume.xz) > 1.65) {
        return emptyDensitySample();
    }

    float sheetness = deriveSheetness();
    float towerness = deriveTowerness();
    float stormDepth = deriveStormDepth(towerness);
    int lobeCount = deriveLobeCount(seedValue);
    float lobeStrength = saturate(CloudShapeLobeStrength);
    float puffStrength = clamp(0.10 + lobeStrength * 0.26 + towerness * 0.16 - sheetness * 0.08, 0.04, 0.48);
    float heightFactor = sampleVerticalEnvelope(localVolume.y, sheetness, towerness);
    if (heightFactor <= 0.0001) {
        return emptyDensitySample();
    }

    float primaryMass = samplePrimaryMass(localVolume, sheetness, towerness);
    float secondaryLobes = sampleSecondaryLobes(localVolume, seedValue, lobeCount, sheetness, towerness);
    float tertiaryPuffs = sampleTertiaryPuffs(localVolume, seedOffset, sheetness, towerness);
    float unlitDensity = clamp(max(primaryMass, secondaryLobes * lobeStrength) + tertiaryPuffs * puffStrength, 0.0, 1.35);
    float edgeFactor = sampleEdgeFactor(localVolume);
    float coreFactor = saturate(smoothstep(0.34, 0.80, unlitDensity) * (1.0 - edgeFactor * 0.68));
    float erosionFactor = sampleSoftErosion(localVolume, seedOffset, edgeFactor, sheetness);
    float erodedMass = max(0.0, unlitDensity - erosionFactor);
    float softMass = smoothstep(0.045, 0.72, erodedMass);
    float densityShape = max(softMass, coreFactor * mix(0.24, 0.46, stormDepth));
    float coverageFill = mix(0.28, 1.0, effectiveCoverage);
    float finalDensity = saturate(densityShape * heightFactor * effectiveDensity * coverageFill);
    float opticalDepthHint = saturate(finalDensity * (0.42 + effectiveCoverage * 0.36 + stormDepth * 0.22));
    float precipitationCoreFactor = saturate(
        (1.0 - smoothstep(0.22, 0.74, length(localVolume.xz)))
        * (1.0 - smoothstep(0.42, 1.02, localVolume.y))
        * (CloudPrecipitationCoreStrength + CloudPrecipitationIntensity * 0.55)
    );

    return CloudDensitySample(
        finalDensity,
        unlitDensity,
        primaryMass,
        secondaryLobes,
        tertiaryPuffs,
        heightFactor,
        edgeFactor,
        coreFactor,
        erosionFactor,
        opticalDepthHint,
        precipitationCoreFactor
    );
}

vec3 computeSampleLighting(vec3 samplePos, CloudDensitySample sample, vec3 rayDir) {
    float density = sample.finalDensity;
    float heightRange = max(CloudTopY - CloudBaseY, 0.001);
    float vertical = saturate((samplePos.y - CloudBaseY) / heightRange);
    float bottomFactor = 1.0 - smoothstep(0.15, 0.65, vertical);
    float topFactor = smoothstep(0.35, 1.0, vertical);

    float edgeFactor = sample.edgeFactor;
    float sunFacing = saturate(dot(normalize(SunDirection), normalize(-rayDir)));
    float rimLight = pow(edgeFactor, 1.5)
        * EdgeLightStrength
        * (0.35 + SunsetStrength * 0.65)
        * (0.30 + sunFacing * 0.70)
        * (0.45 + topFactor * 0.55);

    float stormDarkness = saturate(max(CloudStormVisualDarkness, CloudStormCoreDarkening));
    float materialDarkness = saturate(CloudMaterialDarkness + stormDarkness * 0.36);
    float shadowing = saturate(CloudShadowContribution + CloudMaterialShadowContribution * 0.65);
    float densityDarkening = exp(-sample.opticalDepthHint * LightAbsorption * (0.58 + shadowing * 0.30 + stormDarkness * 0.24));
    vec3 precipitationTint = mix(vec3(1.0), vec3(0.62, 0.68, 0.74), saturate(CloudMaterialPrecipitationTint + CloudPrecipitationIntensity * 0.35));
    vec3 compatibilityTint = mix(CloudColor.rgb * precipitationTint, vec3(1.0), 0.72 - materialDarkness * 0.12);
    float undersideDarkening = saturate(UndersideDarkening * 0.66 + CloudBaseDarkness * 0.22 + CloudMaterialUndersideDarkness * 0.30 + stormDarkness * 0.20);
    float undersideLift = mix(0.54, 0.84, topFactor) + sample.coreFactor * 0.08;
    vec3 baseLighting = AmbientCloudColor * compatibilityTint * mix(undersideLift * (1.0 - undersideDarkening), 1.0, vertical);
    vec3 sunLighting = SunColor * rimLight * (0.55 + sample.edgeFactor * 0.45);
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
    float shapeRadiusScale = clamp(CloudShapeBaseRadius / max(CloudRadius, 1.0), 1.0, 1.45);
    float lobeReach = mix(1.16, 1.42, saturate(CloudShapeLobeStrength));
    float anvilReach = 1.0 + CloudAnvilStrength * CloudShapeAnvilSpread * 0.82;
    float volumeRadius = CloudRadius * min(1.58, max(max(shapeRadiusScale, lobeReach), anvilReach));
    vec3 volumeMin = vec3(CloudCenter.x - volumeRadius, CloudBaseY - volumePaddingY, CloudCenter.z - volumeRadius);
    vec3 volumeMax = vec3(CloudCenter.x + volumeRadius, CloudTopY + volumePaddingY, CloudCenter.z + volumeRadius);

    float tNear;
    float tFar;
    if (!intersectAabb(rayOrigin, rayDir, volumeMin, volumeMax, tNear, tFar)) {
        discard;
    }

    tNear = max(tNear, 0.0);
    float debugFar = max(tFar, tNear);
    float debugMidT = mix(tNear, debugFar, 0.5);
    vec3 debugMidPos = rayOrigin + rayDir * debugMidT;

    if (CloudDebugMode == CLOUD_DEBUG_BOUNDS) {
        float debugSceneDepth = texture(DepthSampler, screenUv).r;
        emitCloudDebug(vec4(0.55, 0.55, 0.55, 0.35), rayOrigin + rayDir * tNear, debugSceneDepth);
        return;
    }
    if (CloudDebugMode == CLOUD_DEBUG_RAY_ENTRY_EXIT) {
        float debugSceneDepth = texture(DepthSampler, screenUv).r;
        float invMaxDistance = 1.0 / max(MaxDistance, 0.001);
        vec3 color = vec3(
            saturate(tNear * invMaxDistance),
            saturate(debugFar * invMaxDistance),
            saturate((debugFar - tNear) * invMaxDistance)
        );
        emitCloudDebug(vec4(color, 0.75), rayOrigin + rayDir * tNear, debugSceneDepth);
        return;
    }
    if (CloudDebugMode == CLOUD_DEBUG_LOCAL_RGB) {
        float debugSceneDepth = texture(DepthSampler, screenUv).r;
        emitCloudDebug(vec4(normalizeAabbLocal(debugMidPos, volumeMin, volumeMax), 0.80), debugMidPos, debugSceneDepth);
        return;
    }
    if (CloudDebugMode == CLOUD_DEBUG_VERTICAL_Y01) {
        float debugSceneDepth = texture(DepthSampler, screenUv).r;
        float y01 = normalizeCloudVertical(debugMidPos);
        emitCloudDebug(vec4(vec3(y01), 0.80), debugMidPos, debugSceneDepth);
        return;
    }

    float sceneDepth = texture(DepthSampler, screenUv).r;
    float sceneDistance = MaxDistance;
    if (sceneDepth < 1.0) {
        vec3 sceneWorld = reconstructWorld(screenUv, sceneDepth);
        sceneDistance = max(0.0, length(sceneWorld - rayOrigin) - 0.35);
    }

    float maxRay = min(min(tFar, MaxDistance), sceneDistance);
    if (maxRay <= tNear + 0.001) {
        if (CloudDebugMode != CLOUD_DEBUG_OFF && CloudDebugMode < CLOUD_DEBUG_PRIMARY_MASS) {
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
    bool scalarDebugMode = CloudDebugMode == CLOUD_DEBUG_PRIMARY_MASS
        || CloudDebugMode == CLOUD_DEBUG_VERTICAL_ENVELOPE
        || CloudDebugMode == CLOUD_DEBUG_FINAL_DENSITY
        || CloudDebugMode == CLOUD_DEBUG_UNLIT_NO_LIGHTING;

    for (int step = 0; step < MAX_RAYMARCH_STEPS; step++) {
        if (step >= steps || transmittance < 0.02) {
            break;
        }

        vec3 samplePos = rayOrigin + rayDir * t;
        CloudDensitySample densitySample = sampleCloudDensity(samplePos, seedOffset, seedValue);
        float debugDensity = densitySample.finalDensity;
        if (CloudDebugMode == CLOUD_DEBUG_PRIMARY_MASS) {
            debugDensity = densitySample.primaryMass;
        } else if (CloudDebugMode == CLOUD_DEBUG_VERTICAL_ENVELOPE) {
            debugDensity = densitySample.heightFactor;
        }
        float density = scalarDebugMode ? saturate(debugDensity) : densitySample.finalDensity;
        maxDensitySample = max(maxDensitySample, density);
        if (density > 0.0005) {
            if (!firstCloudDepthSet && density > 0.018) {
                firstCloudDepth = projectDepth(samplePos);
                firstCloudDepthSet = true;
            }
            float softenedDensity = pow(density, 1.04);
            float extinction = mix(1.35, 3.10, saturate(deriveEffectiveDensity() * deriveEffectiveCoverage()));
            extinction *= mix(0.84, 1.14, densitySample.coreFactor);
            float alpha = 1.0 - exp(-softenedDensity * stepSize * extinction);
            vec3 cloudTint = computeSampleLighting(samplePos, densitySample, rayDir);
            if (CloudDebugMode == CLOUD_DEBUG_UNLIT_NO_LIGHTING) {
                cloudTint = vec3(0.62);
            } else if (scalarDebugMode) {
                cloudTint = vec3(saturate(debugDensity));
            }
            accum += cloudTint * alpha * transmittance;
            transmittance *= (1.0 - alpha);
        }

        t += stepSize;
    }

    float rawAlpha = 1.0 - transmittance;
    if (rawAlpha <= 0.001) {
        if (CloudDebugMode != CLOUD_DEBUG_OFF && CloudDebugMode < CLOUD_DEBUG_PRIMARY_MASS) {
            gl_FragDepth = WriteDepth != 0 ? firstCloudDepth : sceneDepth;
            fragColor = maxDensitySample > 0.0005
                ? vec4(0.05, 0.88, 1.0, 0.70)
                : vec4(1.0, 0.05, 0.95, 0.70);
            return;
        }
        discard;
    }

    vec3 color = accum / max(rawAlpha, 0.0001);
    if (!scalarDebugMode) {
        float fogFactor = smoothstep(FogStart, FogEnd, min(t, MaxDistance));
        color = mix(color, FogColor.rgb, fogFactor * 0.35);
    }

    float opacityBoost = 1.0;
    if (!scalarDebugMode) {
        opacityBoost = mix(0.92, 1.18, saturate(CloudDensity * CloudCoverage * CloudDensityMultiplier * CloudCoverageMultiplier));
        opacityBoost *= mix(0.78, 1.18, saturate(CloudMaterialOpacityBias * 0.5));
    }
    gl_FragDepth = WriteDepth != 0 && firstCloudDepthSet ? firstCloudDepth : sceneDepth;
    fragColor = vec4(color, clamp(rawAlpha * opacityBoost, 0.0, 1.0));
}
