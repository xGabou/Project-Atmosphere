#version 150

uniform sampler2D TornadoSampler;
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
uniform float CloudScale;
uniform float RenderQuality;
uniform int StormCount;
uniform int DebugMode;
uniform int DebugSelectedStorm;
uniform int DebugFreeze;
uniform float StormPositions[24];
uniform float StormHeights[8];
uniform float StormWidths[8];
uniform float StormSizes[8];
uniform float StormSpins[8];
uniform float StormIntensities[8];
uniform float StormShapes[8];
uniform float StormProgress[8];

in vec2 texCoord;
in vec3 fragPos;
out vec4 fragColor;

const float PI = 3.1415926535897932384626433832795;
const float TAU = 6.2831853071795864769252867665590;
const int MAX_STORMS_COUNT = 8;
const int DEBUG_OFF = 0;
const int DEBUG_BOX = 1;
const int DEBUG_HIT = 2;
const int DEBUG_FILL = 3;
const int DEBUG_FUNNEL = 4;
const int DEBUG_HEIGHT = 5;
const int DEBUG_RADIAL = 6;
const int DEBUG_RADIUS = 7;
const int DEBUG_DENSITY = 8;
const int DEBUG_ALPHA = 9;
const int DEBUG_WALLCLOUD = 10;
const int DEBUG_CONNECTION = 11;
const int DEBUG_FULL = 12;
const float TORNADO_ROTATION_SPEED_MULTIPLIER = 3.0;
const float TORNADO_TOP_DARKEN_FACTOR = 0.45;

struct StormSample {
    float cloud;
    float dust;
    float upper;
    float material;
};

struct DebugMasks {
    float heightMask;
    float radialMask;
    float radiusMask;
    float density;
    float alpha;
    float wallcloud;
    float connection;
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

float onoise(vec3 pos) {
    vec3 x = pos * 2.0;
    vec3 p = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    float n = p.x + p.y * 57.0 + 113.0 * p.z;
    return mix(
        mix(
            mix(hash1(n + 0.0), hash1(n + 1.0), f.x),
            mix(hash1(n + 57.0), hash1(n + 58.0), f.x),
            f.y
        ),
        mix(
            mix(hash1(n + 113.0), hash1(n + 114.0), f.x),
            mix(hash1(n + 170.0), hash1(n + 171.0), f.x),
            f.y
        ),
        f.z
    );
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

mat2 spin(float angle) {
    return mat2(cos(angle), -sin(angle), sin(angle), cos(angle));
}

float cloudToWorld(float value) {
    return value * CloudScale;
}

vec3 cloudToWorld(vec3 value) {
    return value * CloudScale;
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

float computeStormDetailQuality(int index) {
    vec3 cameraPosWorld = cloudToWorld(CameraPos);
    vec3 stormPosWorld = cloudToWorld(getStormPos(index));
    float horizontalDistanceWorld = distance(cameraPosWorld.xz, stormPosWorld.xz);
    float distanceQuality = 1.0 - smoothstep(96.0, 320.0, horizontalDistanceWorld);
    float screenProxyQuality = saturate(cloudToWorld(StormWidths[index]) / 42.0);
    float retainedQuality = max(distanceQuality, screenProxyQuality * 0.45);
    return clamp(RenderQuality * mix(0.42, 1.0, retainedQuality), 0.25, 1.0);
}

float sampleFunnelRadiusWorld(float widthWorld, float stormSizeWorld, float tornadoShape, float torPerc, float percFnlHeight) {
    float torShape = mix(tornadoShape, 20.0, saturate(widthWorld / 62.5));
    float widWorld = (widthWorld / 2.5)
        + ((widthWorld / 2.5) * percFnlHeight * torPerc)
        + ((stormSizeWorld / mix(torShape + 2.0, torShape, torPerc)) * pow(percFnlHeight, 4.0));
    return mix(widWorld, 0.0, (1.0 - percFnlHeight) * (1.0 - torPerc));
}

float sampleMaterialField(vec3 localTorPosWorld, float percFnlHeight, float widPerc, float widWorld,
                          float spinPhaseA, float spinPhaseB, float animTime, float detailQuality, bool freezeDebug) {
    if (freezeDebug) {
        return 1.0;
    }

    if (detailQuality < 0.45) {
        float coarse = noise3(vec3(localTorPosWorld.xz * 0.055, percFnlHeight * 2.2 + animTime * 0.018)) * 0.5 + 0.5;
        float band = noise3(vec3(localTorPosWorld.xz * 0.028 + vec2(animTime * 0.038, -animTime * 0.032), percFnlHeight * 1.35)) * 0.5 + 0.5;
        float turbulence = saturate(coarse * 0.68 + band * 0.32);
        return mix(0.90, 1.12, turbulence) * mix(0.90, 1.08, widPerc);
    }

    if (detailQuality < 0.72) {
        vec2 swirl = spin(spinPhaseA) * localTorPosWorld.xz;
        vec2 flow = texture(
            FlowSampler,
            fract(swirl * 0.024 + vec2(animTime * 0.014, -animTime * 0.012))
        ).rg * 2.0 - 1.0;
        vec2 uv = fract(swirl * 0.064 + flow * 0.052 + vec2(percFnlHeight * 0.24, animTime * 0.034));
        vec4 tex = texture(TornadoSampler, uv);
        float lum = max(tex.a, dot(tex.rgb, vec3(0.299, 0.587, 0.114)));
        float noise = texture(NoiseSampler, fract(swirl * 0.030 + vec2(0.17, 0.63))).r;
        float turbulence = saturate(lum * 0.74 + noise * 0.26);
        return mix(0.86, 1.14, turbulence) * mix(0.90, 1.12, widPerc);
    }

    vec2 swirlA = spin(spinPhaseA) * localTorPosWorld.xz;
    vec2 swirlB = spin(spinPhaseB) * localTorPosWorld.xz;

    vec2 flowA = texture(
        FlowSampler,
        fract(swirlA * 0.020 + vec2(animTime * 0.012, -animTime * 0.014))
    ).rg * 2.0 - 1.0;
    vec2 flowB = texture(
        FlowSampler,
        fract(swirlB * 0.033 + vec2(-animTime * 0.018, animTime * 0.016))
    ).rg * 2.0 - 1.0;

    vec2 uvA = fract(swirlA * 0.055 + flowA * 0.060 + vec2(percFnlHeight * 0.30, animTime * 0.030));
    vec2 uvB = fract(swirlB * 0.095 + flowB * 0.040 + vec2(-animTime * 0.042, percFnlHeight * 0.88));
    vec4 texA = texture(TornadoSampler, uvA);
    vec4 texB = texture(TornadoSampler, uvB);
    float lumA = max(texA.a, dot(texA.rgb, vec3(0.299, 0.587, 0.114)));
    float lumB = max(texB.a, dot(texB.rgb, vec3(0.299, 0.587, 0.114)));

    float noiseA = texture(NoiseSampler, fract(swirlA * 0.020 + vec2(0.17, 0.63))).r;
    float noiseB = texture(NoiseSampler, fract(swirlB * 0.037 + vec2(0.48, 0.22))).r;
    float planarField = mix(lumA, lumB, 0.58);

    float radial = length(localTorPosWorld.xz);
    float angular = atan(localTorPosWorld.z, localTorPosWorld.x) / TAU + 0.5;
    vec2 cylFlow = texture(
        FlowSampler,
        fract(vec2(angular * 1.35 + animTime * 0.022, percFnlHeight * 1.20 - animTime * 0.016) + vec2(0.19, 0.43))
    ).rg * 2.0 - 1.0;
    vec2 cylUvA = fract(vec2(
        angular * 2.60 + animTime * 0.085 + cylFlow.x * 0.08,
        percFnlHeight * 1.55 + radial * 0.050 + cylFlow.y * 0.06
    ));
    vec2 cylUvB = fract(vec2(
        angular * 4.10 - animTime * 0.132 - cylFlow.y * 0.05,
        percFnlHeight * 2.05 - radial * 0.032 + cylFlow.x * 0.04
    ));
    vec4 cylTexA = texture(TornadoSampler, cylUvA);
    vec4 cylTexB = texture(TornadoSampler, cylUvB);
    float cylLumA = max(cylTexA.a, dot(cylTexA.rgb, vec3(0.299, 0.587, 0.114)));
    float cylLumB = max(cylTexB.a, dot(cylTexB.rgb, vec3(0.299, 0.587, 0.114)));
    float cylindricalField = mix(cylLumA, cylLumB, 0.52);

    float lowerBlend = 1.0 - smoothstep(0.18, 0.46, percFnlHeight);
    lowerBlend *= mix(0.55, 1.0, widPerc);
    lowerBlend *= 1.0 - smoothstep(1.2, 3.0, widWorld);

    float texField = mix(planarField, cylindricalField, lowerBlend);
    float turbulence = saturate(texField * 0.72 + noiseA * 0.18 + noiseB * 0.10);
    return mix(0.84, 1.18, turbulence) * mix(0.88, 1.14, widPerc);
}

DebugMasks sampleDebugMasks(int index, vec3 position) {
    vec3 pos = getStormPos(index);
    vec3 posWorld = cloudToWorld(pos);
    vec3 positionWorld = cloudToWorld(position);
    float widthWorld = max(cloudToWorld(StormWidths[index]), 0.001);
    float stormSizeWorld = max(cloudToWorld(StormSizes[index]), widthWorld * 2.0);
    float baseHeightWorld = cloudToWorld(pos.y + StormHeights[index]);
    float intensity = saturate(StormIntensities[index]);
    float torPerc = saturate(StormProgress[index]);
    float tornadoShape = StormShapes[index];

    float funnelTopWorld = max(baseHeightWorld - 13.125, posWorld.y + 3.75);
    float heightMask = 0.0;
    if (positionWorld.y >= posWorld.y && positionWorld.y <= funnelTopWorld) {
        heightMask = saturate((positionWorld.y - posWorld.y) / max(funnelTopWorld - posWorld.y, 0.001));
    }

    float funnelRadiusWorld = sampleFunnelRadiusWorld(widthWorld, stormSizeWorld, tornadoShape, torPerc, heightMask);
    float radialDistanceWorld = distance(positionWorld.xz, posWorld.xz);
    float radialMask = 1.0 - saturate(radialDistanceWorld / max(funnelRadiusWorld, 0.001));
    float verticalGate = step(posWorld.y, positionWorld.y) * (1.0 - step(funnelTopWorld, positionWorld.y));
    float density = radialMask * verticalGate;
    float alpha = saturate(density * 0.85);

    float wallcloudRadiusWorld = stormSizeWorld * 0.35;
    float wallcloudLowerWorld = 15.0 * pow(max(1.0 - saturate(radialDistanceWorld / max(wallcloudRadiusWorld, 0.001)), 0.0), 0.25) * saturate((intensity - 0.45) * 2.2);
    float wallcloud = 0.0;
    if (positionWorld.y <= baseHeightWorld && positionWorld.y >= baseHeightWorld - wallcloudLowerWorld) {
        float wallPerc = 1.0 - saturate(radialDistanceWorld / max(wallcloudRadiusWorld, 0.001));
        wallcloud = pow(max(wallPerc, 0.0), 0.55) * saturate((intensity - 0.40) * 2.6);
    }

    float connectionRadiusWorld = max(funnelRadiusWorld * mix(1.8, 2.5, intensity), stormSizeWorld * 0.28);
    float connectionPerc = 1.0 - saturate(radialDistanceWorld / max(connectionRadiusWorld, 0.001));
    float connection = pow(max(connectionPerc, 0.0), 0.55);
    connection *= smoothstep(funnelTopWorld - 1.8, baseHeightWorld + 1.8, positionWorld.y);
    connection *= saturate((intensity - 0.25) * 1.7);

    DebugMasks masks;
    masks.heightMask = heightMask * verticalGate;
    masks.radialMask = radialMask;
    masks.radiusMask = saturate(funnelRadiusWorld / max(stormSizeWorld, 0.001));
    masks.density = density;
    masks.alpha = alpha;
    masks.wallcloud = wallcloud;
    masks.connection = connection;
    return masks;
}

float selectDebugMask(DebugMasks masks) {
    if (DebugMode == DEBUG_FUNNEL) {
        return max(masks.density, max(masks.wallcloud, masks.connection));
    }
    if (DebugMode == DEBUG_HEIGHT) {
        return masks.heightMask;
    }
    if (DebugMode == DEBUG_RADIAL) {
        return masks.radialMask;
    }
    if (DebugMode == DEBUG_RADIUS) {
        return masks.radiusMask;
    }
    if (DebugMode == DEBUG_DENSITY) {
        return masks.density;
    }
    if (DebugMode == DEBUG_ALPHA) {
        return masks.alpha;
    }
    if (DebugMode == DEBUG_WALLCLOUD) {
        return masks.wallcloud;
    }
    if (DebugMode == DEBUG_CONNECTION) {
        return masks.connection;
    }
    return 0.0;
}

StormSample sampleFrozenStorm(int index, vec3 position) {
    DebugMasks masks = sampleDebugMasks(index, position);
    StormSample outSample;
    outSample.cloud = max(masks.density, max(masks.wallcloud, masks.connection * 0.9));
    outSample.dust = 0.0;
    outSample.upper = max(masks.wallcloud, masks.connection);
    outSample.material = saturate(masks.alpha + masks.connection * 0.25);
    return outSample;
}

StormSample sampleStorm(int index, vec3 position) {
    vec3 pos = getStormPos(index);
    vec3 posWorld = cloudToWorld(pos);
    vec3 positionWorld = cloudToWorld(position);
    float baseHeightWorld = cloudToWorld(pos.y + StormHeights[index]);
    float widthWorld = max(cloudToWorld(StormWidths[index]), 0.001);
    float stormSizeWorld = max(cloudToWorld(StormSizes[index]), widthWorld * 2.0);
    float stormSpin = StormSpins[index];
    float intensity = saturate(StormIntensities[index]);
    float torPerc = saturate(StormProgress[index]);
    float tornadoShape = StormShapes[index];
    float detailQuality = computeStormDetailQuality(index);
    bool reducedDetail = detailQuality < 0.72;
    bool lowDetail = detailQuality < 0.45;

    float distWorld = distance(positionWorld.xz, posWorld.xz);
    float wallcloudRadiusWorld = stormSizeWorld * 0.35;
    float wallcloudLowerWorld = 15.0 * pow(max(1.0 - saturate(distWorld / max(wallcloudRadiusWorld, 0.001)), 0.0), 0.25) * saturate((intensity - 0.45) * 2.2);

    float wallcloud = 0.0;
    if (positionWorld.y <= baseHeightWorld && positionWorld.y >= baseHeightWorld - wallcloudLowerWorld) {
        float wallPerc = 1.0 - saturate(distWorld / max(wallcloudRadiusWorld, 0.001));
        wallcloud = pow(max(wallPerc, 0.0), 0.55) * saturate((intensity - 0.40) * 2.6);
        wallcloud *= 0.7 + onoise(vec3(positionWorld.xz / 20.0, AnimationTime / 150.0)) * 0.3;
    }

    StormSample outSample;
    outSample.cloud = wallcloud;
    outSample.dust = 0.0;
    outSample.upper = wallcloud;
    outSample.material = wallcloud * 0.45;

    if (!(positionWorld.y < baseHeightWorld - wallcloudLowerWorld && positionWorld.y > posWorld.y - 8.5 && distWorld < max(widthWorld * 5.0, stormSizeWorld / 2.6))) {
        return outSample;
    }

    float fnlTopWorld = max(baseHeightWorld - 13.125, posWorld.y + 3.75);
    float percFnlHeight = saturate((positionWorld.y - posWorld.y) / max(fnlTopWorld - posWorld.y, 0.001));
    float percCos = (-cos(percFnlHeight * PI) + 1.0) * 0.5;
    float torShape = mix(tornadoShape, 20.0, pow(saturate(widthWorld / 62.5), 1.75));
    float widWorld = (widthWorld / 2.5)
        + ((widthWorld / 2.5) * percFnlHeight * torPerc)
        + ((stormSizeWorld / mix(torShape + 2.0, torShape, torPerc)) * pow(percFnlHeight, 4.0));
    widWorld = mix(widWorld, 0.0, (1.0 - percFnlHeight) * (1.0 - torPerc));
    float tornadoHeightWorld = mix(fnlTopWorld, posWorld.y - 0.25, torPerc);
    float th = 1.0 - saturate((positionWorld.y - tornadoHeightWorld) / 3.75);
    widWorld = mix(widWorld, 0.0, th * th * th);
    float maxWidWorld = (widthWorld / 4.0) + ((widthWorld / 4.0) * torPerc) + ((stormSizeWorld / 8.0) * torPerc);

    float ropeMod = mix(3.0, 1.0, saturate(widthWorld / 3.75));
    ropeMod = mix(ropeMod, 1.0, saturate((intensity - 0.55) * 2.4));
    ropeMod = mix(0.1, ropeMod, saturate(torPerc * 1.35));

    float swayTime = AnimationTime / 220.0;
    float nx = mix(
        onoise(vec3(posWorld.xz / 62.5, swayTime)),
        noise3(vec3(posWorld.xz / 35.0, swayTime * 0.6)),
        0.35
    ) * 5.0 * ropeMod;
    float nz = mix(
        onoise(vec3(swayTime, posWorld.zx / 62.5)),
        noise3(vec3(swayTime * 0.6, posWorld.zx / 35.0)),
        0.35
    ) * 5.0 * ropeMod;
    vec3 attachmentPointWorld = vec3(nx, 0.0, nz);

    float xAdd = mix(
        onoise(vec3(posWorld.xz / 31.25, swayTime + ((positionWorld.y * ropeMod) / 6.25))),
        noise3(vec3(posWorld.xz / 18.0, (swayTime * 0.8) + ((positionWorld.y * ropeMod) / 9.5))),
        0.30
    ) * 2.5 * ropeMod;
    float zAdd = mix(
        onoise(vec3(swayTime + ((positionWorld.y * ropeMod) / 6.25), posWorld.zx / 31.25)),
        noise3(vec3((swayTime * 0.8) + ((positionWorld.y * ropeMod) / 9.5), posWorld.zx / 18.0)),
        0.30
    ) * 2.5 * ropeMod;
    float a = pow(percFnlHeight, 0.75);
    xAdd *= a;
    zAdd *= a;

    vec3 torPosWorld = posWorld + mix(vec3(0.0), vec3(attachmentPointWorld.x, 0.0, attachmentPointWorld.z), percCos) + vec3(xAdd, 0.0, zAdd);
    float torDistWorld = distance(torPosWorld.xz, positionWorld.xz);
    vec3 localTorPosWorld = positionWorld - torPosWorld;

    float influenceRadiusWorld = max(
        max(widWorld * mix(1.8, 2.5, intensity), stormSizeWorld * 0.28),
        max((widthWorld / 1.5) + 6.25, widthWorld * 1.8)
    );
    if (torDistWorld > influenceRadiusWorld) {
        return outSample;
    }

    float widPerc = 1.0 - saturate(torDistWorld / max(widWorld, 0.001));
    float widMaxPerc = saturate(widWorld / max(maxWidWorld, 0.001));
    float rotation = -stormSpin * 3.0 * TORNADO_ROTATION_SPEED_MULTIPLIER;
    float rotation2 = (-stormSpin / 1.5) * TORNADO_ROTATION_SPEED_MULTIPLIER;

    mat2 torSpin = spin(rotation + (torDistWorld / 6.25));
    mat2 torSpin2 = spin(rotation2 + (torDistWorld / 18.75));
    mat2 torSpin3 = spin(rotation2 + (torDistWorld / 7.5));
    vec3 torSpinPos = vec3(torSpin * localTorPosWorld.xz, positionWorld.y - (AnimationTime / 2.0));
    vec3 torSpinPos2 = vec3(torSpin2 * localTorPosWorld.xz, positionWorld.y - (AnimationTime / 2.0));
    vec3 torSpinPos3 = vec3(torSpin3 * localTorPosWorld.xz, positionWorld.y - (AnimationTime / 2.0));

    int primaryOctaves = lowDetail ? 1 : (reducedDetail ? 2 : 3);
    int secondaryOctaves = lowDetail ? 1 : 2;
    int contactOctaves = lowDetail ? 1 : (reducedDetail ? 2 : 3);
    float nComp1 = fbm(torSpinPos / 2.5, primaryOctaves, 2.0, 0.5, 1.0);
    float nComp2 = fbm(torSpinPos2 / 5.0, primaryOctaves, 2.0, 0.5, 1.0);
    float torNoise1 = mix(nComp1, nComp2, sqrt(widMaxPerc));
    float torNoise2 = lowDetail
        ? noise3((torSpinPos + vec3(9.2, -5.7, 3.1)) / 1.6)
        : fbm((torSpinPos + vec3(9.2, -5.7, 3.1)) / 1.6, secondaryOctaves, 2.0, 0.55, 1.0);

    widWorld *= mix(0.8 + (torNoise1 * 0.2), 0.9, saturate(widthWorld / 125.0) * 0.9);
    widWorld *= 1.0 + torNoise2 * 0.035;
    widPerc = 1.0 - saturate(torDistWorld / max(widWorld, 0.001));

    float materialField = sampleMaterialField(
        localTorPosWorld,
        percFnlHeight,
        widPerc,
        widWorld,
        rotation + (torDistWorld / 6.25),
        rotation2 + (torDistWorld / 18.75),
        AnimationTime,
        detailQuality,
        false
    );
    float innerDensity = pow(max(widPerc, 0.0), mix(1.15, 1.55, 1.0 - intensity)) * 4.0;
    float shearBand = saturate(widPerc * (1.0 - widPerc) * 4.0);
    float shellDensity = shearBand * (0.92 + materialField * 0.34) * mix(0.95, 1.35, intensity);
    float coreFill = pow(max(widPerc, 0.0), mix(2.8, 1.45, intensity)) * (0.22 + materialField * 0.20);
    coreFill *= smoothstep(posWorld.y - 0.8, fnlTopWorld, positionWorld.y);
    float innerVeil = smoothstep(0.18, 0.74, widPerc) * (1.0 - smoothstep(0.78, 0.98, widPerc));
    innerVeil *= 0.18 + intensity * 0.16;
    float turbulence = 0.82 + (torNoise1 * 0.14) + (torNoise2 * 0.08);
    float tornado = innerDensity * saturate((positionWorld.y - tornadoHeightWorld) / 2.5) * turbulence;
    tornado += shearBand * 0.55 * (0.85 + materialField * 0.25);
    tornado += shellDensity * 0.78;
    tornado += coreFill * 0.65;
    tornado += innerVeil * (0.80 + materialField * 0.20);
    tornado *= materialField;
    tornado *= mix(0.78, 1.06, intensity);

    float dcNoise1 = lowDetail
        ? noise3(torSpinPos3 / 2.5)
        : fbm(torSpinPos3 / 2.5, contactOctaves, 2.0, 0.5, 1.0);
    float baseContactRadiusWorld = max(widthWorld * 0.24, 0.72) + intensity * 0.42;
    float baseContactPerc = 1.0 - saturate(torDistWorld / max(baseContactRadiusWorld, 0.001));
    float touchdown = pow(max(baseContactPerc, 0.0), 0.48);
    touchdown *= saturate((positionWorld.y - (posWorld.y - 2.2)) / 2.6);
    touchdown *= 1.0 - saturate((positionWorld.y - (posWorld.y + 1.8)) / 3.4);
    touchdown *= 0.72 + dcNoise1 * 0.12;
    tornado = max(tornado, touchdown * 1.05);

    float dcPerc = saturate((intensity - 0.35) * 1.9);
    float h = 5.0 + (dcNoise1 * 1.875);
    float dcTopWorld = posWorld.y + (max(dcPerc, 0.35) * h);
    float percDCHeight = saturate((positionWorld.y - (posWorld.y - 1.25)) / max(dcTopWorld - posWorld.y, 0.001));

    float dustWidWorld = ((widthWorld / 1.5) + ((widthWorld / 1.5) * percFnlHeight * torPerc) + 3.125) + (3.125 * pow(percDCHeight, 1.5) * pow(dcPerc, 0.75));
    dustWidWorld *= mix(0.6 + (dcNoise1 * 0.5), 0.85, saturate(widthWorld / 62.5) * 0.9);
    float dustWidPerc = 1.0 - saturate(torDistWorld / max(dustWidWorld, 0.001));
    dustWidPerc = pow(max(dustWidPerc, 0.0), 0.25);
    float edge = saturate(torDistWorld / max(dustWidWorld * 0.9, 0.001));
    dustWidPerc *= edge * edge * edge;
    float dust = 0.0;
    if (!lowDetail) {
        dust = pow(max(dustWidPerc, 0.0), 1.5) * 0.15;
        dust *= saturate((dcTopWorld - positionWorld.y) / 2.5);
        dust *= saturate((positionWorld.y - (posWorld.y - 2.5)) / 2.5);
        dust *= 0.8 + (dcNoise1 * 0.2);
        dust *= dcPerc;
        dust *= 1.0 - saturate((widthWorld - 6.25) / 25.0);
    }

    float connectionRadiusWorld = max(widWorld * mix(1.8, 2.5, intensity), stormSizeWorld * 0.28);
    float connectionPerc = 1.0 - saturate(torDistWorld / max(connectionRadiusWorld, 0.001));
    float connection = pow(max(connectionPerc, 0.0), 0.55);
    connection *= smoothstep(fnlTopWorld - 1.8, baseHeightWorld + 1.8, positionWorld.y);
    connection *= saturate((intensity - 0.25) * 1.7);
    if (!reducedDetail) {
        connection *= 0.82 + onoise(vec3((positionWorld.xz + posWorld.xz) / 20.0, AnimationTime / 140.0)) * 0.18;
    }

    outSample.cloud = max(outSample.cloud, max(tornado, dust));
    outSample.cloud = max(outSample.cloud, connection * 0.9);
    outSample.dust = max(outSample.dust, dust);
    outSample.upper = max(outSample.upper, wallcloud + tornado * smoothstep(0.68, 1.0, percFnlHeight) * 0.30);
    outSample.upper = max(outSample.upper, connection);
    outSample.material = max(outSample.material, materialField * saturate(tornado + connection * 0.35));
    return outSample;
}

void main() {
    if (DebugMode == DEBUG_BOX) {
        fragColor = vec4(0.95, 0.28, 0.08, 1.0);
        return;
    }

    vec3 ro = CameraPos;
    vec3 rd = normalize(fragPos - ro);
    float tNear;
    float tFar;
    if (!intersectAabb(ro, rd, VolumeMin, VolumeMax, tNear, tFar)) {
        if (DebugMode == DEBUG_HIT) {
            fragColor = vec4(1.0, 0.0, 1.0, 1.0);
            return;
        }
        discard;
    }
    tNear = max(tNear, 0.0);

    if (DebugMode == DEBUG_HIT) {
        fragColor = vec4(0.10, 0.95, 0.15, 1.0);
        return;
    }

    if (DebugMode == DEBUG_FILL) {
        float intervalFill = max(tFar - tNear, 0.0);
        float alphaFill = saturate(1.0 - exp(-intervalFill * 0.55));
        fragColor = vec4(vec3(0.86), max(alphaFill, 0.65));
        return;
    }

    vec2 screenUv = gl_FragCoord.xy / OutSize;
    float sceneDepth = texture(DepthSampler, screenUv).r;
    bool useSceneDepthStop = DebugMode == DEBUG_OFF;
    float maxRay = min(tFar, MaxDistance);
    if (useSceneDepthStop && sceneDepth < 1.0) {
        vec3 rayEnd = reconstructPosition(screenUv, sceneDepth);
        maxRay = min(maxRay, length(rayEnd - ro));
    }
    if (maxRay <= tNear + 0.001) {
        discard;
    }

    bool debugActive = DebugMode != DEBUG_OFF;
    bool debugMaskMode = debugActive
        && DebugMode != DEBUG_BOX
        && DebugMode != DEBUG_HIT
        && DebugMode != DEBUG_FILL
        && DebugMode != DEBUG_FULL;
    float detailQuality = debugActive ? 1.0 : computeStormDetailQuality(0);

    vec3 accum = vec3(0.0);
    float transmittance = 1.0;
    float nearestT = tNear;
    float firstHitDepth = 1.0;
    float debugValue = 0.0;
    bool wroteDepth = false;

    float interval = maxRay - tNear;
    float stepSpacing = mix(1.40, 0.60, detailQuality);
    float minSteps = mix(8.0, 14.0, detailQuality);
    float maxSteps = mix(22.0, 40.0, detailQuality);
    int steps = int(clamp(interval / stepSpacing, minSteps, maxSteps));
    float stepSize = interval / float(max(steps, 1));
    float jitter = hash1(screenUv.x * OutSize.x + screenUv.y * OutSize.y + 17.13);
    float t = tNear + stepSize * (0.20 + jitter * 0.80);

    for (int step = 0; step < 40; step++) {
        if (step >= steps) {
            break;
        }
        if (!debugMaskMode && transmittance < mix(0.08, 0.03, detailQuality)) {
            break;
        }

        vec3 samplePos = ro + rd * t;
        if (debugMaskMode) {
            DebugMasks masks = sampleDebugMasks(0, samplePos);
            float value = selectDebugMask(masks);
            if (value > 0.0005) {
                debugValue = max(debugValue, value);
                if (!wroteDepth) {
                    firstHitDepth = clamp(cloudSpaceToDepth(samplePos), 0.0, 1.0);
                    wroteDepth = true;
                }
            }
        } else {
            StormSample storm = debugActive && DebugFreeze != 0
                ? sampleFrozenStorm(0, samplePos)
                : sampleStorm(0, samplePos);
            float sigma = max(storm.cloud, 0.0) * 0.195;
            if (sigma > 0.0005) {
                if (!wroteDepth) {
                    firstHitDepth = clamp(cloudSpaceToDepth(samplePos), 0.0, 1.0);
                    wroteDepth = true;
                }
                float nearField = 1.0 - saturate(t / 12.0);
                float alpha = 1.0 - exp(-sigma * stepSize * 8.4);
                alpha = saturate(alpha * (1.10 + nearField * 0.32));
                float bodyDark = mix(0.08, 0.25, saturate(storm.material));
                vec3 cloudBase = CloudColor.rgb * bodyDark;
                float upperStrength = saturate(storm.upper);
                vec3 upperCol = mix(cloudBase, CloudColor.rgb * 0.64, upperStrength * 0.62);
                upperCol *= mix(1.0, 1.0 - TORNADO_TOP_DARKEN_FACTOR, upperStrength);
                vec3 dustCol = vec3(0.20, 0.125, 0.071);
                float dustTint = saturate(pow(storm.dust, 0.55)) * (1.0 - saturate(storm.material * 0.45));
                vec3 localColor = mix(upperCol, dustCol, dustTint);
                accum += localColor * alpha * transmittance;
                transmittance *= (1.0 - alpha);
            }
        }

        t += stepSize;
    }

    if (debugMaskMode) {
        if (debugValue < 0.01) {
            discard;
        }
        if (wroteDepth) {
            gl_FragDepth = firstHitDepth;
        }
        fragColor = vec4(vec3(debugValue), saturate(debugValue));
        return;
    }

    float alpha = 1.0 - transmittance;
    if (alpha < 0.01) {
        discard;
    }

    vec3 color = accum / max(alpha, 0.0001);
    float fogFactor = smoothstep(FogStart, FogEnd, nearestT);
    color = mix(color, FogColor.rgb, fogFactor * 0.45);
    if (wroteDepth) {
        gl_FragDepth = firstHitDepth;
    }
    fragColor = vec4(color, saturate(alpha));
}
