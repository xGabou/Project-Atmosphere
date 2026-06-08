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

    float horizontalDistance = length(samplePos.xz - CloudCenter.xz);
    float normalizedHorizontal = horizontalDistance / max(CloudRadius, 0.001);
    if (normalizedHorizontal >= 1.0) {
        return 0.0;
    }

    float lifecycleFactor = saturate(CloudGrowth * (1.0 - CloudDecay));
    float effectiveDensity = saturate(CloudDensity * CloudCoverage * lifecycleFactor);

    float edgeSoftness = max(saturate(CloudEdgeSoftness), 0.001);
    float horizontalFade = 1.0 - smoothstep(1.0 - edgeSoftness, 1.0, normalizedHorizontal);
    float verticalFade = smoothstep(0.0, 0.15, vertical) * (1.0 - smoothstep(0.85, 1.0, vertical));
    float interiorFade = 1.0;

    vec3 motion = (CloudCenter - CloudPreviousCenter) * (0.35 + CloudPartialTick * 0.15);
    vec3 noisePos = samplePos * vec3(0.045, 0.075, 0.045) + vec3(0.0, CloudWorldTime * 0.0025, 0.0) + motion * 0.2;
    float noise = fbm(noisePos, 3);
    float noiseShape = mix(0.58, 1.08, noise);
    float edgeFactor = smoothstep(0.55, 1.0, normalizedHorizontal);
    float edgeErosion = mix(1.0, noiseShape, edgeFactor);

    return saturate(effectiveDensity * horizontalFade * verticalFade * interiorFade * edgeErosion);
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
    vec3 baseLighting = AmbientCloudColor * compatibilityTint * mix(1.0 - UndersideDarkening, 1.0, vertical);
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
    float jitter = hash1(screenUv.x * OutSize.x + screenUv.y * OutSize.y + CloudWorldTime * 0.013);

    float t = tNear + stepSize * (0.2 + jitter * 0.8);
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
            float alpha = 1.0 - exp(-softenedDensity * stepSize * 5.4);
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
