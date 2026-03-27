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
uniform int StormCount;
uniform float StormPositions[24];
uniform float StormHeights[8];
uniform float StormWidths[8];
uniform float StormSizes[8];
uniform float StormSpins[8];
uniform float StormIntensities[8];
uniform float StormShapes[8];
uniform float StormProgress[8];

in vec2 texCoord;
out vec4 fragColor;

const float PI = 3.1415926535897932384626433832795;
const float TAU = 6.2831853071795864769252867665590;
const int MAX_STORMS_COUNT = 8;

struct StormSample {
    float cloud;
    float dust;
    float upper;
    float material;
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

float sampleMaterialField(vec3 localTorPos, float percFnlHeight, float widPerc, float wid, float spinPhaseA, float spinPhaseB) {
    vec2 swirlA = spin(spinPhaseA) * localTorPos.xz;
    vec2 swirlB = spin(spinPhaseB) * localTorPos.xz;

    vec2 flowA = texture(
        FlowSampler,
        fract(swirlA * 0.020 + vec2(AnimationTime * 0.012, -AnimationTime * 0.014))
    ).rg * 2.0 - 1.0;
    vec2 flowB = texture(
        FlowSampler,
        fract(swirlB * 0.033 + vec2(-AnimationTime * 0.018, AnimationTime * 0.016))
    ).rg * 2.0 - 1.0;

    vec2 uvA = fract(swirlA * 0.055 + flowA * 0.060 + vec2(percFnlHeight * 0.30, AnimationTime * 0.030));
    vec2 uvB = fract(swirlB * 0.095 + flowB * 0.040 + vec2(-AnimationTime * 0.042, percFnlHeight * 0.88));
    vec4 texA = texture(TornadoSampler, uvA);
    vec4 texB = texture(TornadoSampler, uvB);
    float lumA = max(texA.a, dot(texA.rgb, vec3(0.299, 0.587, 0.114)));
    float lumB = max(texB.a, dot(texB.rgb, vec3(0.299, 0.587, 0.114)));

    float noiseA = texture(NoiseSampler, fract(swirlA * 0.020 + vec2(0.17, 0.63))).r;
    float noiseB = texture(NoiseSampler, fract(swirlB * 0.037 + vec2(0.48, 0.22))).r;
    float planarField = mix(lumA, lumB, 0.58);

    float radial = length(localTorPos.xz);
    float angular = atan(localTorPos.z, localTorPos.x) / TAU + 0.5;
    vec2 cylFlow = texture(
        FlowSampler,
        fract(vec2(angular * 1.35 + AnimationTime * 0.022, percFnlHeight * 1.20 - AnimationTime * 0.016) + vec2(0.19, 0.43))
    ).rg * 2.0 - 1.0;
    vec2 cylUvA = fract(vec2(
        angular * 2.60 + AnimationTime * 0.085 + cylFlow.x * 0.08,
        percFnlHeight * 1.55 + radial * 0.050 + cylFlow.y * 0.06
    ));
    vec2 cylUvB = fract(vec2(
        angular * 4.10 - AnimationTime * 0.132 - cylFlow.y * 0.05,
        percFnlHeight * 2.05 - radial * 0.032 + cylFlow.x * 0.04
    ));
    vec4 cylTexA = texture(TornadoSampler, cylUvA);
    vec4 cylTexB = texture(TornadoSampler, cylUvB);
    float cylLumA = max(cylTexA.a, dot(cylTexA.rgb, vec3(0.299, 0.587, 0.114)));
    float cylLumB = max(cylTexB.a, dot(cylTexB.rgb, vec3(0.299, 0.587, 0.114)));
    float cylindricalField = mix(cylLumA, cylLumB, 0.52);

    float lowerBlend = 1.0 - smoothstep(0.18, 0.46, percFnlHeight);
    lowerBlend *= mix(0.55, 1.0, widPerc);
    lowerBlend *= 1.0 - smoothstep(1.2, 3.0, wid);

    float texField = mix(planarField, cylindricalField, lowerBlend);
    float turbulence = saturate(texField * 0.72 + noiseA * 0.18 + noiseB * 0.10);
    return mix(0.84, 1.18, turbulence) * mix(0.88, 1.14, widPerc);
}

StormSample sampleStorm(int index, vec3 position) {
    vec3 pos = getStormPos(index);
    float baseHeight = pos.y + StormHeights[index];
    float width = max(StormWidths[index], 0.001);
    float stormSize = max(StormSizes[index], width * 2.0);
    float stormSpin = StormSpins[index];
    float intensity = saturate(StormIntensities[index]);
    float torPerc = saturate(StormProgress[index]);
    float tornadoShape = StormShapes[index];

    float dist = distance(position.xz, pos.xz);
    float wallcloudRadius = stormSize * 0.35;
    float wallcloudLower = 15.0 * pow(max(1.0 - saturate(dist / max(wallcloudRadius, 0.001)), 0.0), 0.25) * saturate((intensity - 0.45) * 2.2);

    float wallcloud = 0.0;
    if (position.y <= baseHeight && position.y >= baseHeight - wallcloudLower) {
        float wallPerc = 1.0 - saturate(dist / max(wallcloudRadius, 0.001));
        wallcloud = pow(max(wallPerc, 0.0), 0.55) * saturate((intensity - 0.40) * 2.6);
        wallcloud *= 0.7 + onoise(vec3(position.xz / 20.0, AnimationTime / 150.0)) * 0.3;
    }

    StormSample outSample;
    outSample.cloud = wallcloud;
    outSample.dust = 0.0;
    outSample.upper = wallcloud;
    outSample.material = wallcloud * 0.45;

    if (!(position.y < baseHeight - wallcloudLower && position.y > pos.y - 8.5 && dist < max(width * 5.0, stormSize / 2.6))) {
        return outSample;
    }

    float fnlTop = max(baseHeight - 13.125, pos.y + 3.75);
    float percFnlHeight = saturate((position.y - pos.y) / max(fnlTop - pos.y, 0.001));
    float percCos = (-cos(percFnlHeight * PI) + 1.0) * 0.5;
    float torShape = mix(tornadoShape, 20.0, pow(saturate(width / 62.5), 1.75));
    float wid = (width / 2.5)
        + ((width / 2.5) * percFnlHeight * torPerc)
        + ((stormSize / mix(torShape + 2.0, torShape, torPerc)) * pow(percFnlHeight, 4.0));
    wid = mix(wid, 0.0, (1.0 - percFnlHeight) * (1.0 - torPerc));
    float tornadoHeight = mix(fnlTop, pos.y - 0.25, torPerc);
    float th = 1.0 - saturate((position.y - tornadoHeight) / 3.75);
    wid = mix(wid, 0.0, th * th * th);
    float maxWid = (width / 4.0) + ((width / 4.0) * torPerc) + ((stormSize / 8.0) * torPerc);

    float ropeMod = mix(3.0, 1.0, saturate(width / 3.75));
    ropeMod = mix(ropeMod, 1.0, saturate((intensity - 0.55) * 2.4));
    ropeMod = mix(0.1, ropeMod, saturate(torPerc * 1.35));

    float swayTime = AnimationTime / 220.0;
    float nx = mix(
        onoise(vec3(pos.xz / 62.5, swayTime)),
        noise3(vec3(pos.xz / 35.0, swayTime * 0.6)),
        0.35
    ) * 5.0 * ropeMod;
    float nz = mix(
        onoise(vec3(swayTime, pos.zx / 62.5)),
        noise3(vec3(swayTime * 0.6, pos.zx / 35.0)),
        0.35
    ) * 5.0 * ropeMod;
    vec3 attachmentPoint = vec3(nx, 0.0, nz);

    float xAdd = mix(
        onoise(vec3(pos.xz / 31.25, swayTime + ((position.y * ropeMod) / 6.25))),
        noise3(vec3(pos.xz / 18.0, (swayTime * 0.8) + ((position.y * ropeMod) / 9.5))),
        0.30
    ) * 2.5 * ropeMod;
    float zAdd = mix(
        onoise(vec3(swayTime + ((position.y * ropeMod) / 6.25), pos.zx / 31.25)),
        noise3(vec3((swayTime * 0.8) + ((position.y * ropeMod) / 9.5), pos.zx / 18.0)),
        0.30
    ) * 2.5 * ropeMod;
    float a = pow(percFnlHeight, 0.75);
    xAdd *= a;
    zAdd *= a;

    vec3 torPos = pos + mix(vec3(0.0), vec3(attachmentPoint.x, 0.0, attachmentPoint.z), percCos) + vec3(xAdd, 0.0, zAdd);
    float torDist = distance(torPos.xz, position.xz);
    vec3 localTorPos = position - torPos;

    float widPerc = 1.0 - saturate(torDist / max(wid, 0.001));
    float widMaxPerc = saturate(wid / max(maxWid, 0.001));
    float rotation = -stormSpin * 3.0;
    float rotation2 = -stormSpin / 1.5;

    mat2 torSpin = spin(rotation + (torDist / 6.25));
    mat2 torSpin2 = spin(rotation2 + (torDist / 18.75));
    mat2 torSpin3 = spin(rotation2 + (torDist / 7.5));
    vec3 torSpinPos = vec3(torSpin * localTorPos.xz, position.y - (AnimationTime / 2.0));
    vec3 torSpinPos2 = vec3(torSpin2 * localTorPos.xz, position.y - (AnimationTime / 2.0));
    vec3 torSpinPos3 = vec3(torSpin3 * localTorPos.xz, position.y - (AnimationTime / 2.0));

    float nComp1 = fbm(torSpinPos / 2.5, 3, 2.0, 0.5, 1.0);
    float nComp2 = fbm(torSpinPos2 / 5.0, 3, 2.0, 0.5, 1.0);
    float torNoise1 = mix(nComp1, nComp2, sqrt(widMaxPerc));
    float torNoise2 = fbm((torSpinPos + vec3(9.2, -5.7, 3.1)) / 1.6, 2, 2.0, 0.55, 1.0);

    wid *= mix(0.8 + (torNoise1 * 0.2), 0.9, saturate(width / 125.0) * 0.9);
    wid *= 1.0 + torNoise2 * 0.035;
    widPerc = 1.0 - saturate(torDist / max(wid, 0.001));

    float materialField = sampleMaterialField(localTorPos, percFnlHeight, widPerc, wid, rotation + (torDist / 6.25), rotation2 + (torDist / 18.75));
    float innerDensity = pow(max(widPerc, 0.0), mix(1.15, 1.55, 1.0 - intensity)) * 4.0;
    float shearBand = saturate(widPerc * (1.0 - widPerc) * 4.0);
    float turbulence = 0.82 + (torNoise1 * 0.14) + (torNoise2 * 0.08);
    float tornado = innerDensity * saturate((position.y - tornadoHeight) / 2.5) * turbulence;
    tornado += shearBand * 0.55 * (0.85 + materialField * 0.25);
    tornado *= materialField;
    tornado *= mix(0.78, 1.06, intensity);

    float dcNoise1 = fbm(torSpinPos3 / 2.5, 3, 2.0, 0.5, 1.0);
    float baseContactRadius = max(width * 0.24, 0.72) + intensity * 0.42;
    float baseContactPerc = 1.0 - saturate(torDist / max(baseContactRadius, 0.001));
    float touchdown = pow(max(baseContactPerc, 0.0), 0.48);
    touchdown *= saturate((position.y - (pos.y - 2.2)) / 2.6);
    touchdown *= 1.0 - saturate((position.y - (pos.y + 1.8)) / 3.4);
    touchdown *= 0.72 + dcNoise1 * 0.12;
    tornado = max(tornado, touchdown * 1.05);

    float dcPerc = saturate((intensity - 0.35) * 1.9);
    float h = 5.0 + (dcNoise1 * 1.875);
    float dcTop = pos.y + (max(dcPerc, 0.35) * h);
    float percDCHeight = saturate((position.y - (pos.y - 1.25)) / max(dcTop - pos.y, 0.001));

    float dustWid = ((width / 1.5) + ((width / 1.5) * percFnlHeight * torPerc) + 3.125) + (3.125 * pow(percDCHeight, 1.5) * pow(dcPerc, 0.75));
    dustWid *= mix(0.6 + (dcNoise1 * 0.5), 0.85, saturate(width / 62.5) * 0.9);
    float dustWidPerc = 1.0 - saturate(torDist / max(dustWid, 0.001));
    dustWidPerc = pow(max(dustWidPerc, 0.0), 0.25);
    float edge = saturate(torDist / max(dustWid * 0.9, 0.001));
    dustWidPerc *= edge * edge * edge;
    float dust = pow(max(dustWidPerc, 0.0), 1.5) * 0.15;
    dust *= saturate((dcTop - position.y) / 2.5);
    dust *= saturate((position.y - (pos.y - 2.5)) / 2.5);
    dust *= 0.8 + (dcNoise1 * 0.2);
    dust *= dcPerc;
    dust *= 1.0 - saturate((width - 6.25) / 25.0);

    float connectionRadius = max(wid * mix(1.8, 2.5, intensity), stormSize * 0.28);
    float connectionPerc = 1.0 - saturate(torDist / max(connectionRadius, 0.001));
    float connection = pow(max(connectionPerc, 0.0), 0.55);
    connection *= smoothstep(fnlTop - 1.8, baseHeight + 1.8, position.y);
    connection *= saturate((intensity - 0.25) * 1.7);
    connection *= 0.82 + onoise(vec3((position.xz + pos.xz) / 20.0, AnimationTime / 140.0)) * 0.18;

    outSample.cloud = max(outSample.cloud, max(tornado, dust));
    outSample.cloud = max(outSample.cloud, connection * 0.9);
    outSample.dust = max(outSample.dust, dust);
    outSample.upper = max(outSample.upper, wallcloud + tornado * smoothstep(0.68, 1.0, percFnlHeight) * 0.30);
    outSample.upper = max(outSample.upper, connection);
    outSample.material = max(outSample.material, materialField * saturate(tornado + connection * 0.35));
    return outSample;
}

void main() {
    float sceneDepth = texture(DepthSampler, texCoord).r;
    vec3 rayEnd = reconstructPosition(texCoord, sceneDepth < 1.0 ? sceneDepth : 1.0);
    vec3 ro = CameraPos;
    vec3 rd = normalize(rayEnd - ro);
    float maxRay = min(length(rayEnd - ro), MaxDistance);
    if (maxRay <= 0.001) {
        discard;
    }

    vec3 accum = vec3(0.0);
    float transmittance = 1.0;
    float nearestT = MaxDistance;
    float firstHitDepth = 1.0;
    bool wroteDepth = false;

    for (int i = 0; i < MAX_STORMS_COUNT; i++) {
        if (i >= StormCount) {
            break;
        }

        vec3 stormPos = getStormPos(i);
        float width = max(StormWidths[i], 0.001);
        float stormSize = max(StormSizes[i], width * 2.0);
        float boundsRadius = max(width * 5.4, stormSize * 0.58);
        vec3 bmin = vec3(stormPos.x - boundsRadius, stormPos.y - 8.0, stormPos.z - boundsRadius);
        vec3 bmax = vec3(stormPos.x + boundsRadius, stormPos.y + StormHeights[i] + 12.0, stormPos.z + boundsRadius);

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
        int steps = int(clamp(interval / 0.42, 18.0, 52.0));
        float stepSize = interval / float(max(steps, 1));
        float jitter = hash1(texCoord.x * OutSize.x + texCoord.y * OutSize.y + float(i) * 17.13);
        float t = tNear + stepSize * (0.20 + jitter * 0.80);

        for (int step = 0; step < 52; step++) {
            if (step >= steps || transmittance < 0.025) {
                break;
            }

            vec3 samplePos = ro + rd * t;
            StormSample storm = sampleStorm(i, samplePos);
            float sigma = max(storm.cloud, 0.0) * 0.115;
            if (sigma > 0.0005) {
                if (!wroteDepth) {
                    firstHitDepth = clamp(cloudSpaceToDepth(samplePos), 0.0, 1.0);
                    wroteDepth = true;
                }
                float alpha = 1.0 - exp(-sigma * stepSize * 5.8);
                float bodyDark = mix(0.16, 0.34, saturate(storm.material));
                vec3 cloudBase = CloudColor.rgb * bodyDark;
                vec3 upperCol = mix(cloudBase, CloudColor.rgb * 0.90, saturate(storm.upper));
                vec3 dustCol = vec3(0.20, 0.125, 0.071);
                float dustTint = saturate(pow(storm.dust, 0.55)) * (1.0 - saturate(storm.material * 0.45));
                vec3 localColor = mix(upperCol, dustCol, dustTint);
                accum += localColor * alpha * transmittance;
                transmittance *= (1.0 - alpha);
            }

            t += stepSize;
        }
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
