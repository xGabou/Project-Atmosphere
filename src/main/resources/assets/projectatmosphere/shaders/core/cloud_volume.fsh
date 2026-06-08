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
uniform int CloudSeed;
uniform int RaymarchSteps;

in vec2 texCoord;
out vec4 fragColor;

const int MAX_RAYMARCH_STEPS = 64;

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
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

float noise3(vec3 p) {
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
        value += noise3(p * frequency) * amplitude;
        frequency *= 2.0;
        amplitude *= 0.5;
    }

    return value * 0.5 + 0.5;
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

float sampleCloudField(vec3 samplePos) {
    float heightRange = max(CloudTopY - CloudBaseY, 0.001);
    float vertical = (samplePos.y - CloudBaseY) / heightRange;
    if (vertical < 0.0 || vertical > 1.0) {
        return 0.0;
    }

    float lifecycleFactor = saturate(CloudGrowth * (1.0 - CloudDecay));
    float effectiveDensity = saturate(CloudDensity * CloudCoverage * CloudDensityMultiplier * CloudCoverageMultiplier * lifecycleFactor);

    vec3 motion = (CloudCenter - CloudPreviousCenter) * (0.35 + CloudPartialTick * 0.15);
    vec3 seedOffset = seedOffset3();
    vec3 baseNoisePos = samplePos + motion * 0.2 + seedOffset;
    float visualThickness = max(CloudVerticalThickness, 0.05);
    float heightSquash = max(CloudHeightSquash, 0.10);
    float shapedVertical = (vertical - 0.5) * heightSquash / visualThickness + 0.5;
    if (shapedVertical < 0.0 || shapedVertical > 1.0) {
        return 0.0;
    }

    vec2 localHorizontal = samplePos.xz - CloudCenter.xz;
    float horizontalDistance = length(localHorizontal);
    float edgeAngle = atan(localHorizontal.y, localHorizontal.x);
    float seedValue = float(CloudSeed);
    float lobeCount = mix(4.0, 9.0, hash1(seedValue + 131.0));
    float lobePhase = hash1(seedValue + 149.0) * 6.2831853;
    float lobeWave = sin(edgeAngle * lobeCount + lobePhase);
    float silhouetteNoise = fbm(vec3(
        localHorizontal * 0.015 + seedOffset.xz * 0.021,
        shapedVertical * 2.0 + seedOffset.y * 0.017
    ), 2) - 0.5;
    float seededRadiusWarp = 1.0 + (lobeWave * 0.055 + silhouetteNoise * 0.16) * smoothstep(0.12, 1.0, horizontalDistance / max(CloudRadius, 0.001));
    float rawHorizontal = horizontalDistance / max(CloudRadius * clamp(seededRadiusWarp, 0.78, 1.20), 0.001);

    float topWarp = fbm(baseNoisePos * vec3(0.018, 0.0, 0.018) + vec3(19.7, CloudWorldTime * 0.0012, 4.1) + seedOffset * 0.011, 2) - 0.5;
    float baseWarp = fbm(baseNoisePos * vec3(0.014, 0.0, 0.014) + vec3(3.4, CloudWorldTime * -0.0008, 27.5) + seedOffset * 0.009, 2) - 0.5;
    float warpedVertical = shapedVertical + topWarp * 0.16 * smoothstep(0.45, 1.0, shapedVertical) - baseWarp * 0.08 * (1.0 - smoothstep(0.0, 0.35, shapedVertical));
    float baseSoftness = max(CloudBaseSoftness, 0.01);
    float topSoftness = max(CloudTopSoftness, 0.01);
    float verticalFade = smoothstep(0.0, baseSoftness, warpedVertical) * (1.0 - smoothstep(1.0 - topSoftness, 1.0, warpedVertical));
    float interiorFade = 1.0;

    float layerBias = saturate((CloudHeightSquash - 1.20) * 0.36);
    float puffyRadius = mix(0.18, 1.00, smoothstep(0.04, 0.46, warpedVertical))
        * mix(1.0, 0.22, smoothstep(0.58, 1.0, warpedVertical));
    float layerRadius = mix(0.88, 1.06, smoothstep(0.04, 0.34, warpedVertical))
        * mix(1.0, 0.96, smoothstep(0.88, 1.0, warpedVertical));
    float towerRadius = mix(0.30, 0.68, smoothstep(0.04, 0.38, warpedVertical))
        * mix(1.0, 0.58, smoothstep(0.78, 1.0, warpedVertical));
    float anvilRadius = CloudAnvilStrength
        * smoothstep(0.56, 0.88, warpedVertical)
        * (1.0 - smoothstep(0.94, 1.0, warpedVertical))
        * 0.48;
    float effectiveRadiusFactor = mix(puffyRadius, layerRadius, layerBias);
    effectiveRadiusFactor = mix(effectiveRadiusFactor, towerRadius, saturate(CloudTowerStrength));
    effectiveRadiusFactor = clamp(effectiveRadiusFactor + anvilRadius, 0.12, 1.08);

    float normalizedHorizontal = rawHorizontal / effectiveRadiusFactor;
    if (normalizedHorizontal >= 1.0) {
        return 0.0;
    }

    float edgeSoftness = max(saturate(CloudEdgeSoftness), 0.001);
    float horizontalFade = 1.0 - smoothstep(1.0 - edgeSoftness, 1.0, normalizedHorizontal);
    float edgeFactor = smoothstep(0.55, 1.0, normalizedHorizontal);
    float silhouettePower = mix(2.35, 0.95, layerBias);
    float silhouetteFade = pow(1.0 - smoothstep(0.36, 1.0, normalizedHorizontal), silhouettePower);

    float noiseScale = max(CloudNoiseScale, 0.001);
    float detailNoiseScale = max(CloudDetailNoiseScale, 0.001);
    float erosionNoiseScale = max(CloudErosionNoiseScale, 0.001);
    float lobeNoise = fbm(baseNoisePos * vec3(noiseScale, noiseScale * 1.75, noiseScale) + vec3(0.0, CloudWorldTime * 0.0015, 0.0) + seedOffset * 0.017, 3);
    float layerNoise = fbm(baseNoisePos * vec3(detailNoiseScale * 0.50, detailNoiseScale, detailNoiseScale * 0.50) + vec3(12.0, CloudWorldTime * 0.0025, 8.0) + seedOffset * 0.013, 3);
    float detailNoise = fbm(baseNoisePos * vec3(erosionNoiseScale, erosionNoiseScale * 1.25, erosionNoiseScale) + vec3(31.0, CloudWorldTime * -0.0030, 6.0) + seedOffset * 0.019, 2);

    float seedLobeBias = mix(-0.08, 0.08, hash1(seedValue + 197.0));
    float lobeShape = mix(0.74 + seedLobeBias, 1.18 + seedLobeBias, lobeNoise);
    float layeredShape = mix(0.82, 1.10, layerNoise);
    float edgeCarve = mix(1.0, smoothstep(0.24, 0.86, detailNoise), edgeFactor * saturate(CloudEdgeErosionStrength));
    float centerWeight = 1.0 - smoothstep(0.0, 0.58, normalizedHorizontal);
    float corePreserve = mix(edgeCarve, max(edgeCarve, 0.86), centerWeight);
    float towerBoost = 1.0 + CloudTowerStrength * centerWeight * smoothstep(0.22, 0.86, warpedVertical) * 0.42;
    float anvilBoost = 1.0 + CloudAnvilStrength * smoothstep(0.58, 1.0, warpedVertical) * smoothstep(0.20, 0.92, normalizedHorizontal) * 0.34;
    float precipitationCore = 1.0 + CloudPrecipitationCoreStrength * centerWeight * (1.0 - smoothstep(0.28, 0.72, warpedVertical)) * 0.35;
    float baseProfile = mix(1.0, 0.94, saturate(CloudBaseDarkness) * (1.0 - smoothstep(0.0, 0.42, warpedVertical)));

    return saturate(effectiveDensity * horizontalFade * silhouetteFade * verticalFade * interiorFade * lobeShape * layeredShape * corePreserve * towerBoost * anvilBoost * precipitationCore * baseProfile);
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

    float densityDarkening = exp(-density * LightAbsorption);
    vec3 compatibilityTint = mix(CloudColor.rgb, vec3(1.0), 0.85);
    float undersideDarkening = saturate(UndersideDarkening + CloudBaseDarkness * 0.30);
    vec3 baseLighting = AmbientCloudColor * compatibilityTint * mix(1.0 - undersideDarkening, 1.0, vertical);
    vec3 sunLighting = SunColor * rimLight;
    vec3 horizonGlow = SunColor
        * HorizonGlowStrength
        * SunsetStrength
        * bottomFactor
        * (0.18 + sunFacing * 0.32);

    return baseLighting * densityDarkening + sunLighting + horizonGlow;
}

void main() {
    vec2 screenUv = gl_FragCoord.xy / OutSize;
    vec3 rayDir = getWorldRay(screenUv);
    vec3 rayOrigin = CameraPos;

    vec3 volumeMin = vec3(CloudCenter.x - CloudRadius, CloudBaseY, CloudCenter.z - CloudRadius);
    vec3 volumeMax = vec3(CloudCenter.x + CloudRadius, CloudTopY, CloudCenter.z + CloudRadius);

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
        sceneDistance = length(sceneWorld - rayOrigin);
    }

    float maxRay = min(min(tFar, MaxDistance), sceneDistance);
    if (maxRay <= tNear + 0.001) {
        discard;
    }

    float interval = maxRay - tNear;
    int steps = int(clamp(float(RaymarchSteps), 1.0, float(MAX_RAYMARCH_STEPS)));
    float stepSize = interval / float(steps);

    // Jitter stable par pixel pour casser les plans de sampling sans scintillement temporel.
    float jitter = hash12(gl_FragCoord.xy);
    float t = tNear + jitter * stepSize;
    float transmittance = 1.0;
    vec3 accum = vec3(0.0);

    for (int step = 0; step < MAX_RAYMARCH_STEPS; step++) {
        if (step >= steps || transmittance < 0.02) {
            break;
        }

        vec3 samplePos = rayOrigin + rayDir * t;
        float density = sampleCloudField(samplePos);
        if (density > 0.0005) {
            float softenedDensity = pow(density, 1.18);
            float alpha = 1.0 - exp(-softenedDensity * stepSize * 3.2);
            vec3 cloudTint = computeSampleLighting(samplePos, density, rayDir);
            accum += cloudTint * alpha * transmittance;
            transmittance *= (1.0 - alpha);
        }

        t += stepSize;
    }

    float rawAlpha = 1.0 - transmittance;
    if (rawAlpha <= 0.001) {
        discard;
    }

    vec3 color = accum / max(rawAlpha, 0.0001);
    float fogFactor = smoothstep(FogStart, FogEnd, min(t, MaxDistance));
    color = mix(color, FogColor.rgb, fogFactor * 0.35);

    fragColor = vec4(color, clamp(rawAlpha * 0.92, 0.0, 1.0));
}
