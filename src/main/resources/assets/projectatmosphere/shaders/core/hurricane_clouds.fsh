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
uniform vec3 VolumeMin;
uniform vec3 VolumeMax;
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
in vec3 fragPos;
out vec4 fragColor;

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

float cloudSpaceToDepth(vec3 pos) {
    vec4 clip = ProjMat * ModelViewMat * vec4(pos, 1.0);
    float ndcZ = clip.z / clip.w;
    return ndcZ * 0.5 + 0.5;
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

HurricaneSample sampleStorm(int index, vec3 position) {
    vec3 pos = getStormPos(index);
    float height = max(StormHeights[index], 0.001);
    float localY = (position.y - pos.y) / height;
    if (localY <= 0.0 || localY >= 1.06) {
        return HurricaneSample(0.0, 0.0);
    }

    vec2 rel = position.xz - pos.xz;
    float r = length(rel);
    float theta = atan(rel.y, rel.x);
    float intensity = saturate(StormIntensities[index]);
    float spin = StormSpins[index];
    float seed = StormSeeds[index];

    float eyeRadius = max(EyeRadii[index], 0.001);
    float effectiveEye = eyeRadiusAtHeight(index, localY, eyeRadius);
    if (r < effectiveEye * 0.98) {
        return HurricaneSample(0.0, 0.0);
    }

    float eyeClear = eyeRadiusAtHeight(index, localY, max(EyeClearRadii[index], eyeRadius * 1.04));
    float wallOuter = effectiveEye + max(EyewallThicknesses[index], 0.001) * mix(0.82, 1.22, saturate(localY));
    float wallSoft = max(EyewallThicknesses[index] * 0.16, 0.22);
    float wallBand = band(r, effectiveEye, wallOuter, wallSoft);
    float wallHeight = verticalWindow(localY, 0.02, 0.94, 0.08);

    vec2 flowUv = fract(rel * 0.010 + vec2(spin * 0.010, -AnimationTime * 0.012) + vec2(seed, localY * 0.23));
    vec2 flow = texture(FlowSampler, flowUv).rg * 2.0 - 1.0;
    float baseTex = texture(BaseSampler, fract(rel * 0.018 + flow * 0.060 + vec2(spin * 0.004, localY * 0.17))).r;
    float detailTex = texture(NoiseSampler, fract(rel * 0.055 - flow.yx * 0.025 + vec2(localY * 0.21, seed))).r;
    float volumeNoise = fbm(vec3(rel * 0.055 + flow * 1.8, localY * 3.3 + seed * 7.0), 3, 2.0, 0.5, 1.0);
    float noiseField = saturate(baseTex * 0.46 + detailTex * 0.26 + volumeNoise * 0.28 + 0.46);

    float canopyHeight = verticalWindow(localY, CanopyBaseFactors[index], CanopyTopFactors[index], 0.09);
    float canopyRadius = max(CanopyRadii[index], wallOuter + 0.001);
    float canopyRadiusAtY = mix(wallOuter * 1.28, canopyRadius, smoothstep(CanopyBaseFactors[index], CanopyTopFactors[index], localY));
    float canopyDisk = 1.0 - smoothstep(canopyRadiusAtY * 0.88, canopyRadiusAtY * 1.05, r);
    float canopyHole = smoothstep(eyeClear * 0.96, eyeClear * 1.28, r);

    float shieldHeight = verticalWindow(localY, ShieldBaseFactors[index], ShieldTopFactors[index], 0.08);
    float shieldRadius = max(ShieldRadii[index], canopyRadius * 1.02);
    float shield = (1.0 - smoothstep(canopyRadius * 0.94, shieldRadius, r)) * shieldHeight;
    shield *= smoothstep(eyeClear * 1.08, eyeClear * 1.52, r);

    float bandWindow = band(r, BandStartRadii[index], BandEndRadii[index], max(BandWidths[index] * 0.22, 0.4));
    float bandAngle = theta * max(BandCounts[index], 1.0) - r * 0.050 - AnimationTime * (0.24 + intensity * 0.22) - spin * 0.065 + seed * TAU;
    float spiral = smoothstep(0.38, 0.90, sin(bandAngle) * 0.5 + 0.5);
    spiral *= bandWindow * BandStrengths[index];
    spiral *= verticalWindow(localY, ShieldBaseFactors[index], min(ShieldTopFactors[index] + 0.08, 1.02), 0.10);
    spiral *= mix(0.35, 1.0, noiseField);

    float wallDensity = wallBand * wallHeight * mix(1.28, 2.15, intensity);
    wallDensity *= mix(0.68, 1.32, noiseField);

    float canopyDensity = canopyDisk * canopyHole * canopyHeight * mix(0.22, 0.88, noiseField);
    canopyDensity *= mix(0.76, 1.18, intensity);

    float shieldDensity = shield * mix(0.14, 0.34, noiseField);
    shieldDensity *= mix(0.72, 1.05, intensity);

    float density = max(wallDensity, canopyDensity);
    density = max(density, shieldDensity);
    density = max(density, spiral);

    float brightness = 0.22;
    brightness += wallBand * 0.22;
    brightness += smoothstep(CanopyBaseFactors[index], CanopyTopFactors[index], localY) * 0.44;
    brightness += canopyDisk * 0.08;
    brightness = saturate(brightness);

    return HurricaneSample(density, brightness);
}

void main() {
    vec2 screenUv = gl_FragCoord.xy / OutSize;
    float sceneDepth = texture(DepthSampler, screenUv).r;
    float cappedDepth = sceneDepth < 1.0 ? sceneDepth : 1.0;
    vec3 rayEnd = reconstructPosition(screenUv, cappedDepth);
    vec3 ro = CameraPos;
    vec3 rd = normalize(fragPos - ro);

    float tNear;
    float tFar;
    if (!intersectAabb(ro, rd, VolumeMin, VolumeMax, tNear, tFar)) {
        discard;
    }
    tNear = max(tNear, 0.0);
    float maxRay = min(tFar, MaxDistance);
    if (sceneDepth < 1.0) {
        maxRay = min(maxRay, length(rayEnd - ro));
    }
    if (maxRay <= tNear + 0.001) {
        discard;
    }

    vec3 accum = vec3(0.0);
    float transmittance = 1.0;
    float nearestT = tNear;
    float firstHitDepth = 1.0;
    bool wroteDepth = false;

    float interval = maxRay - tNear;
    int steps = int(clamp(interval / 0.92, 18.0, 56.0));
    float stepSize = interval / float(max(steps, 1));
    float jitter = hash1(screenUv.x * OutSize.x + screenUv.y * OutSize.y + 19.17);
    float t = tNear + stepSize * (0.18 + jitter * 0.82);

    for (int step = 0; step < 56; step++) {
        if (step >= steps || transmittance < 0.03) {
            break;
        }

        vec3 samplePos = ro + rd * t;
        HurricaneSample storm = sampleStorm(0, samplePos);
        float sigma = max(storm.density, 0.0) * 0.095;
        if (sigma > 0.0005) {
            if (!wroteDepth) {
                firstHitDepth = clamp(cloudSpaceToDepth(samplePos), 0.0, 1.0);
                wroteDepth = true;
            }
            float alpha = 1.0 - exp(-sigma * stepSize * 5.6);
            vec3 lowColor = CloudColor.rgb * mix(0.16, 0.56, storm.brightness);
            vec3 highColor = CloudColor.rgb * mix(0.66, 0.98, storm.brightness);
            vec3 localColor = mix(lowColor, highColor, smoothstep(0.40, 1.0, storm.brightness));
            accum += localColor * alpha * transmittance;
            transmittance *= (1.0 - alpha);
        }

        t += stepSize;
    }

    float alpha = 1.0 - transmittance;
    if (alpha < 0.01) {
        discard;
    }

    vec3 color = accum / max(alpha, 0.0001);
    float fogFactor = smoothstep(FogStart, FogEnd, nearestT);
    color = mix(color, FogColor.rgb, fogFactor * 0.50);

    if (wroteDepth) {
        gl_FragDepth = firstHitDepth;
    }
    fragColor = vec4(color, saturate(alpha));
}
