#version 430

uniform sampler2D SourceColorSampler;
uniform sampler2D SourceDepthSampler;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 InverseProjMat;
uniform mat4 InverseModelViewMat;
uniform vec3 CameraPos;
uniform float AnimationTime;
uniform float MaxDistance;
uniform vec2 OutSize;
uniform int ProtectionEnabled;
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
out vec4 fragColor;

const int MAX_STORMS_COUNT = 4;

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

float eyeRadiusAtHeight(int index, float localY, float baseRadius) {
    float slope = max(EyeSlopes[index], 0.01);
    return mix(baseRadius, baseRadius * slope, clamp(localY, 0.0, 1.0));
}

bool rayHitsProtectedEye(int index, vec3 ro, vec3 rd, float maxT) {
    vec3 stormPos = getStormPos(index);
    float height = max(StormHeights[index], 0.001);
    float maxEyeRadius = max(EyeClearRadii[index], EyeClearRadii[index] * EyeSlopes[index]) * 1.08;
    vec3 bmin = vec3(stormPos.x - maxEyeRadius, stormPos.y - 2.0, stormPos.z - maxEyeRadius);
    vec3 bmax = vec3(stormPos.x + maxEyeRadius, stormPos.y + height + 2.0, stormPos.z + maxEyeRadius);

    float tNear;
    float tFar;
    if (!intersectAabb(ro, rd, bmin, bmax, tNear, tFar)) {
        return false;
    }

    tNear = max(tNear, 0.0);
    tFar = min(tFar, maxT);
    if (tFar <= tNear) {
        return false;
    }

    float interval = tFar - tNear;
    int steps = int(clamp(interval / 0.95, 10.0, 18.0));
    float stepSize = interval / float(max(steps, 1));
    float t = tNear + stepSize * 0.35;

    for (int step = 0; step < 18; step++) {
        if (step >= steps) {
            break;
        }

        vec3 samplePos = ro + rd * t;
        float localY = (samplePos.y - stormPos.y) / height;
        if (localY >= 0.0 && localY <= 1.04) {
            vec2 rel = samplePos.xz - stormPos.xz;
            float clearRadius = eyeRadiusAtHeight(index, localY, max(EyeClearRadii[index], EyeRadii[index] * 1.04));
            if (length(rel) < clearRadius) {
                return true;
            }
        }
        t += stepSize;
    }

    return false;
}

void main() {
    vec4 sourceColor = texture(SourceColorSampler, texCoord);
    float sourceDepth = texture(SourceDepthSampler, texCoord).r;

    if (ProtectionEnabled == 0 || StormCount <= 0) {
        fragColor = sourceColor;
        gl_FragDepth = sourceDepth;
        return;
    }

    float rayDepth = sourceDepth < 1.0 ? sourceDepth : 1.0;
    vec3 rayEnd = reconstructPosition(texCoord, rayDepth);
    vec3 ro = CameraPos;
    vec3 rd = normalize(rayEnd - ro);
    float maxT = sourceDepth < 1.0 ? length(rayEnd - ro) : MaxDistance;
    if (maxT <= 0.001) {
        fragColor = sourceColor;
        gl_FragDepth = sourceDepth;
        return;
    }

    for (int i = 0; i < MAX_STORMS_COUNT; i++) {
        if (i >= StormCount) {
            break;
        }
        if (rayHitsProtectedEye(i, ro, rd, maxT)) {
            fragColor = vec4(0.0);
            gl_FragDepth = 1.0;
            return;
        }
    }

    fragColor = sourceColor;
    gl_FragDepth = sourceDepth;
}
