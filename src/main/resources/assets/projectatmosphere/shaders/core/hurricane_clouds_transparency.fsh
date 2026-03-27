#version 430

uniform sampler2D BaseSampler;
uniform sampler2D NoiseSampler;
uniform sampler2D FlowSampler;
uniform sampler2D DepthSampler;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 InverseProjMat;
uniform mat4 InverseModelViewMat;
uniform vec3 CameraPos;
uniform vec4 CloudColor;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float AnimationTime;
uniform float MaxDistance;
uniform vec2 OutSize;
uniform int StormCount;
uniform float StormPositions[12];
uniform vec4 StormHeights;
uniform vec4 EyeRadii;
uniform vec4 EyeClearRadii;
uniform vec4 EyeSlopes;
uniform vec4 EyewallThicknesses;
uniform vec4 CanopyRadii;
uniform vec4 ShieldRadii;
uniform vec4 CanopyBaseFactors;
uniform vec4 CanopyTopFactors;
uniform vec4 ShieldBaseFactors;
uniform vec4 ShieldTopFactors;
uniform vec4 BandStartRadii;
uniform vec4 BandEndRadii;
uniform vec4 BandWidths;
uniform vec4 BandStrengths;
uniform vec4 BandCounts;
uniform vec4 FringeStrengths;
uniform vec4 StormSpins;
uniform vec4 StormIntensities;
uniform vec4 StormSeeds;

in vec2 texCoord;

layout(location = 0) out vec4 accumColor;
layout(location = 1) out float revealage;

const float TAU = 6.28318530718;
const int MAX_STORMS_COUNT = 4;

struct HurricaneSample {
    float density;
    float brightness;
};

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

float fbm(vec3 x, int octaves, float lacunarity, float gain, float amplitude) {
    float y = 0.0;
    for (int i = 0; i < octaves; i++) {
        y += amplitude * noise3(x);
        x *= lacunarity;
        amplitude *= gain;
    }
    return y;
}

vec3 reconstructPosition(vec2 uv, float depth) {
    vec4 ndc = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 clip = InverseProjMat * ndc;
    clip /= clip.w;
    vec4 result = InverseModelViewMat * clip;
    return result.xyz / result.w;
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

vec3 getStormPos(int index) {
    return vec3(StormPositions[index * 3], StormPositions[index * 3 + 1], StormPositions[index * 3 + 2]);
}

float verticalWindow(float localY, float startFactor, float endFactor, float edge) {
    float enter = smoothstep(startFactor - edge, startFactor + edge, localY);
    float leave = 1.0 - smoothstep(endFactor - edge, endFactor + edge, localY);
    return enter * leave;
}

float band(float r, float inner, float outer, float soft) {
    float enter = smoothstep(inner - soft, inner + soft, r);
    float leave = 1.0 - smoothstep(outer - soft, outer + soft, r);
    return enter * leave;
}

float eyeRadiusAtHeight(int index, float localY, float baseRadius) {
    float slope = max(EyeSlopes[index], 0.01);
    return mix(baseRadius, baseRadius * slope, saturate(localY));
}

HurricaneSample sampleStormFringe(int index, vec3 position) {
    vec3 pos = getStormPos(index);
    float height = max(StormHeights[index], 0.001);
    float localY = (position.y - pos.y) / height;
    if (localY <= 0.0 || localY >= 1.08) {
        return HurricaneSample(0.0, 0.0);
    }

    vec2 rel = position.xz - pos.xz;
    float r = length(rel);
    float theta = atan(rel.y, rel.x);
    float intensity = saturate(StormIntensities[index]);
    float spin = StormSpins[index];
    float seed = StormSeeds[index];
    float clearEye = eyeRadiusAtHeight(index, localY, max(EyeClearRadii[index], EyeRadii[index] * 1.05));

    if (r < clearEye * 1.02) {
        return HurricaneSample(0.0, 0.0);
    }

    vec2 flowUv = fract(rel * 0.009 + vec2(spin * 0.007, -AnimationTime * 0.010) + vec2(seed * 0.7, localY * 0.18));
    vec2 flow = texture(FlowSampler, flowUv).rg * 2.0 - 1.0;
    float baseTex = texture(BaseSampler, fract(rel * 0.013 + flow * 0.052 + vec2(localY * 0.17, seed))).r;
    float detailTex = texture(NoiseSampler, fract(rel * 0.049 - flow.yx * 0.018 + vec2(seed, localY * 0.29))).r;
    float volumeNoise = fbm(vec3(rel * 0.045 + flow * 1.2, localY * 2.8 + seed * 5.0), 3, 2.0, 0.5, 1.0);
    float noiseField = saturate(baseTex * 0.42 + detailTex * 0.30 + volumeNoise * 0.28 + 0.44);

    float canopyRadius = max(CanopyRadii[index], clearEye * 2.0);
    float shieldRadius = max(ShieldRadii[index], canopyRadius * 1.03);
    float shieldHeight = verticalWindow(localY, ShieldBaseFactors[index], min(ShieldTopFactors[index] + 0.10, 1.05), 0.10);
    float shieldShell = band(r, canopyRadius * 0.92, shieldRadius * 1.02, max(BandWidths[index] * 0.22, 0.55));
    shieldShell *= shieldHeight * mix(0.18, 0.42, noiseField) * FringeStrengths[index];

    float canopyFringeHeight = verticalWindow(localY, CanopyBaseFactors[index], min(CanopyTopFactors[index] + 0.06, 1.03), 0.09);
    float canopyFringe = band(r, canopyRadius * 0.84, canopyRadius * 1.08, max(EyewallThicknesses[index] * 0.18, 0.45));
    canopyFringe *= canopyFringeHeight * mix(0.14, 0.34, noiseField) * FringeStrengths[index];

    float spiralWindow = band(r, BandStartRadii[index], BandEndRadii[index], max(BandWidths[index] * 0.25, 0.55));
    float spiralPhase = theta * max(BandCounts[index], 1.0) - r * 0.046 - AnimationTime * (0.18 + intensity * 0.24) - spin * 0.052 + seed * TAU;
    float spiral = smoothstep(0.50, 0.94, sin(spiralPhase) * 0.5 + 0.5);
    spiral *= spiralWindow * BandStrengths[index] * FringeStrengths[index];
    spiral *= verticalWindow(localY, ShieldBaseFactors[index], min(ShieldTopFactors[index] + 0.14, 1.08), 0.10);
    spiral *= mix(0.30, 1.0, noiseField);

    float veil = (1.0 - smoothstep(shieldRadius * 0.90, shieldRadius * 1.20, r));
    veil *= verticalWindow(localY, max(CanopyBaseFactors[index] - 0.06, 0.0), min(ShieldTopFactors[index] + 0.18, 1.08), 0.12);
    veil *= smoothstep(clearEye * 1.24, clearEye * 1.72, r);
    veil *= mix(0.08, 0.20, noiseField) * FringeStrengths[index];

    float density = max(max(shieldShell, canopyFringe), spiral);
    density = max(density, veil);

    float brightness = 0.28 + smoothstep(0.44, 1.0, localY) * 0.36 + spiral * 0.18;
    return HurricaneSample(density, saturate(brightness));
}

void main() {
    float sceneDepth = texture(DepthSampler, texCoord).r;
    float cappedDepth = sceneDepth < 1.0 ? sceneDepth : 1.0;
    vec3 rayEnd = reconstructPosition(texCoord, cappedDepth);
    vec3 ro = CameraPos;
    vec3 rd = normalize(rayEnd - ro);
    float maxRay = sceneDepth < 1.0 ? min(length(rayEnd - ro), MaxDistance) : MaxDistance;
    if (maxRay <= 0.001) {
        discard;
    }

    vec3 accum = vec3(0.0);
    float transmittance = 1.0;
    float nearestT = MaxDistance;

    for (int i = 0; i < MAX_STORMS_COUNT; i++) {
        if (i >= StormCount) {
            break;
        }

        vec3 stormPos = getStormPos(i);
        float boundsRadius = max(ShieldRadii[i] * 1.18, CanopyRadii[i] * 1.28);
        vec3 bmin = vec3(stormPos.x - boundsRadius, stormPos.y - 2.0, stormPos.z - boundsRadius);
        vec3 bmax = vec3(stormPos.x + boundsRadius, stormPos.y + StormHeights[i] + 4.0, stormPos.z + boundsRadius);

        float tNear;
        float tFar;
        if (!intersectAabb(ro, rd, bmin, bmax, tNear, tFar)) {
            continue;
        }

        tNear = max(tNear, 0.0);
        tFar = min(tFar, maxRay);
        if (tFar <= tNear) {
            continue;
        }

        nearestT = min(nearestT, tNear);
        float interval = tFar - tNear;
        int steps = int(clamp(interval / 1.15, 12.0, 34.0));
        float stepSize = interval / float(max(steps, 1));
        float jitter = hash1(texCoord.x * OutSize.x + texCoord.y * OutSize.y + float(i) * 13.0);
        float t = tNear + stepSize * (0.12 + jitter * 0.88);

        for (int step = 0; step < 34; step++) {
            if (step >= steps || transmittance < 0.04) {
                break;
            }

            vec3 samplePos = ro + rd * t;
            HurricaneSample storm = sampleStormFringe(i, samplePos);
            float sigma = max(storm.density, 0.0) * 0.060;
            if (sigma > 0.0004) {
                float alpha = 1.0 - exp(-sigma * stepSize * 4.4);
                vec3 lowColor = CloudColor.rgb * mix(0.26, 0.58, storm.brightness);
                vec3 highColor = CloudColor.rgb * mix(0.70, 1.02, storm.brightness);
                vec3 localColor = mix(lowColor, highColor, smoothstep(0.40, 1.0, storm.brightness));
                accum += localColor * alpha * transmittance;
                transmittance *= (1.0 - alpha);
            }

            t += stepSize;
        }
    }

    float alpha = 1.0 - transmittance;
    if (alpha < 0.003) {
        discard;
    }

    vec3 color = accum / max(alpha, 0.0001);
    float fogFactor = smoothstep(FogStart, FogEnd, nearestT);
    color = mix(color, FogColor.rgb, fogFactor * 0.42);

    vec4 premul = vec4(color * alpha, alpha);
    float z = min(nearestT / 1000.0, 1.0);
    float weight = max(premul.a * 3000.0 * pow(1.0 - z, 3.0), 0.01);

    accumColor = premul * weight;
    revealage = premul.a;
}
