#version 150

// Project Atmosphere volumetric cloud raymarch.
// One fullscreen pass marches a single global density field driven by the
// cell weather map, baked Perlin-Worley / Worley 3D noise, real sun lighting
// (Beer-powder, dual-lobe HG phase, multi-scattering octaves), sky ambient,
// temporal reprojection, and an analytic tornado funnel slot.

uniform sampler2D WeatherMapSampler;
uniform sampler2D MorphologyMapSampler;
uniform sampler2D BlueNoiseSampler;
uniform sampler2D SceneDepthSampler;
uniform sampler2D HistorySampler;
uniform sampler2D HistoryDepthSampler;
uniform sampler3D BaseNoiseSampler;   // bound manually (3D)
uniform sampler3D DetailNoiseSampler; // bound manually (3D)

// Deliberately NOT named "ProjMat": VertexBuffer.drawWithShader overwrites the
// vanilla-managed "ProjMat"/"ModelViewMat" uniforms with the matrices passed to
// it (identity for this fullscreen pass), which would break depth output.
uniform mat4 CloudProjMat;
uniform mat4 ViewRotMat;      // world->view rotation (camera-relative)
uniform mat4 InvProjMat;
uniform mat4 InvViewRotMat;
uniform mat4 PrevViewProjMat; // maps current-camera-relative offsets to prev clip

uniform vec3 CameraPos;

uniform vec2 WeatherOrigin;
uniform float WeatherExtent;
uniform float SlabBaseY;
uniform float SlabTopY;

uniform vec3 LightDir;
uniform vec3 LightColor;
uniform vec3 AmbientTop;
uniform vec3 AmbientBottom;
uniform float SunsetStrength;
uniform float NightFactor;
uniform float StormDarkening;

uniform vec3 WindVec;      // blocks per tick, horizontal drift for detail
uniform float WorldTime;   // world time in ticks (with partial)
uniform float FrameIndex;

uniform int RaymarchSteps;
uniform int LightSteps;
uniform int ScatterOctaves;
uniform int DetailQuality;   // 0 = off, 1 = normal, 2 = extra near-camera octave
uniform float StepScale;     // frame-time governor multiplier (0.5 .. 1.0)
uniform float MaxRenderDistance;

uniform int UseSceneDepth;
uniform int CoveragePretestEnabled;
uniform int CoveragePretestSamples;
uniform float CoveragePretestThreshold;
uniform int CoveragePretestDilation;
uniform int HistoryValid;
uniform float HistoryBlend;

uniform float DensityMul;
uniform float CoverageMul;
uniform float ExtinctionScale;

// Analytic funnel slots (tornado readiness). Each funnel:
//  A = (baseX, baseZ, attachY, groundY)
//  B = (radiusTop, radiusBottom, strength, bendPhase)
uniform int FunnelCount;
uniform vec4 Funnel0A;
uniform vec4 Funnel0B;
uniform vec4 Funnel1A;
uniform vec4 Funnel1B;

in vec2 texCoord;
out vec4 fragColor;

const int MAX_STEPS = 128;
const int MAX_LIGHT_STEPS = 8;
const float PI = 3.14159265;

float saturate(float v) { return clamp(v, 0.0, 1.0); }
vec3 saturate3(vec3 v) { return clamp(v, vec3(0.0), vec3(1.0)); }

float remap(float value, float low1, float high1, float low2, float high2) {
    return low2 + (value - low1) * (high2 - low2) / max(high1 - low1, 0.0001);
}

vec3 lowFrequencyDomainWarp(vec3 worldPos) {
    return vec3(
        sin(dot(worldPos, vec3(0.00173, 0.00091, -0.00127)) + 1.7),
        sin(dot(worldPos, vec3(-0.00111, 0.00149, 0.00083)) - 2.3),
        sin(dot(worldPos, vec3(0.00079, -0.00131, 0.00191)) + 4.1)
    );
}

vec3 baseNoiseDomain(vec3 worldPos, float scale) {
    // Orthonormal-ish rotation removes alignment with world axes. The slow
    // incommensurate warp prevents the texture's repeat period from lining up
    // at the same world interval without another 3D texture sample.
    vec3 rotated = vec3(
        dot(worldPos, vec3(0.8138, 0.2962, -0.5000)),
        dot(worldPos, vec3(-0.1401, 0.9408, 0.3085)),
        dot(worldPos, vec3(0.5630, -0.1677, 0.8090))
    );
    return rotated * scale + lowFrequencyDomainWarp(worldPos) * 0.31;
}

vec3 detailNoiseDomain(vec3 worldPos) {
    vec3 rotated = vec3(
        dot(worldPos, vec3(0.7071, -0.4082, 0.5774)),
        dot(worldPos, vec3(0.7071, 0.4082, -0.5774)),
        dot(worldPos, vec3(0.0000, 0.8165, 0.5774))
    );
    vec3 animation = vec3(WorldTime * 0.0006, WorldTime * 0.0002, -WorldTime * 0.0004);
    return rotated * 0.022 + lowFrequencyDomainWarp(worldPos * 1.731) * 0.43 + animation;
}

// ---------------------------------------------------------------------------
// Weather + density field
// ---------------------------------------------------------------------------

vec4 sampleWeather(vec2 worldXZ) {
    vec2 uv = (worldXZ - WeatherOrigin) / WeatherExtent;
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        return vec4(0.0, 0.35, 0.45, 0.0);
    }
    vec4 weather = texture(WeatherMapSampler, uv);
    float edgeDistance = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
    float edgeFade = smoothstep(0.0, 0.055, edgeDistance);
    weather.r *= edgeFade;
    weather.a *= edgeFade;
    return weather;
}

vec4 sampleMorphology(vec2 worldXZ) {
    vec2 uv = (worldXZ - WeatherOrigin) / WeatherExtent;
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        return vec4(0.0);
    }
    return texture(MorphologyMapSampler, uv);
}

float profileWeight(float profile, float expected) {
    return 1.0 - smoothstep(0.20, 0.90, abs(profile - expected));
}

float pretestWeatherCoverage(vec2 worldXZ) {
    float bestCoverage = sampleWeather(worldXZ).r;
    int dilation = clamp(CoveragePretestDilation, 0, 2);
    if (dilation <= 0) {
        return bestCoverage;
    }

    vec2 texelWorld = vec2(WeatherExtent) / vec2(textureSize(WeatherMapSampler, 0));
    for (int y = -2; y <= 2; y++) {
        if (abs(y) > dilation) {
            continue;
        }
        for (int x = -2; x <= 2; x++) {
            if (abs(x) > dilation || (x == 0 && y == 0)) {
                continue;
            }
            vec2 neighborXZ = worldXZ + vec2(float(x), float(y)) * texelWorld;
            bestCoverage = max(bestCoverage, sampleWeather(neighborXZ).r);
        }
    }
    return bestCoverage;
}

float heightGradient(float h01, float energy, float profile, float verticalDevelopment) {
    float stratus = profileWeight(profile, 1.0);
    float stratocumulus = profileWeight(profile, 2.0);
    float cumulus = profileWeight(profile, 3.0);
    float cumulonimbus = profileWeight(profile, 4.0);
    float nimbostratus = profileWeight(profile, 5.0);
    float cirrus = profileWeight(profile, 6.0);
    float supercell = profileWeight(profile, 7.0);
    float sheet = max(stratus, max(stratocumulus, nimbostratus));
    float deepConvection = max(cumulonimbus, supercell);

    float baseSoftness = mix(0.10, 0.045, deepConvection * verticalDevelopment);
    baseSoftness = mix(baseSoftness, 0.16, cirrus);
    float base = smoothstep(0.0, baseSoftness, h01);
    float topStart = 0.62 + energy * 0.18;
    topStart = mix(topStart, 0.70, sheet);
    topStart = mix(topStart, 0.80 + verticalDevelopment * 0.12, deepConvection);
    topStart = mix(topStart, 0.52, cirrus);
    topStart = mix(topStart, 0.66 + verticalDevelopment * 0.10, cumulus);
    float top = 1.0 - smoothstep(topStart, 1.0, h01);
    return saturate(base * top);
}

float funnelDensityAt(vec3 p, vec4 A, vec4 B) {
    float strength = B.z;
    if (strength <= 0.005) {
        return 0.0;
    }
    float attachY = A.z;
    float groundY = A.w;
    // Funnel descends progressively with strength: the condensation funnel
    // reaches the ground only near full strength.
    float tipY = mix(attachY, groundY, smoothstep(0.25, 0.95, strength));
    if (p.y > attachY + 22.0 || p.y < tipY - 4.0) {
        return 0.0;
    }
    float t = saturate((attachY - p.y) / max(attachY - tipY, 1.0));

    // Curved axis: gentle helical bend so the funnel sways instead of being a
    // rigid cone; bendPhase animates from cell rotation.
    float bendAmp = mix(0.0, 14.0, t) * (0.4 + 0.6 * strength);
    vec2 axisXZ = A.xy + vec2(
        sin(B.w + t * 2.7) * bendAmp,
        cos(B.w * 1.3 + t * 2.2) * bendAmp * 0.8
    );

    // Radius profile: wide at the wall cloud, narrowing toward the tip with a
    // slight flare at ground contact.
    float radius = mix(B.x, B.y, pow(t, 0.55));
    radius *= 1.0 + smoothstep(0.9, 1.0, t) * 0.35;

    float radial = length(p.xz - axisXZ);
    float core = 1.0 - smoothstep(radius * 0.45, radius, radial);

    // Swirl erosion so the funnel surface churns.
    float angle = atan(p.z - axisXZ.y, p.x - axisXZ.x);
    float swirl = sin(angle * 3.0 + p.y * 0.11 - WorldTime * 0.35 + B.w) * 0.5 + 0.5;
    core *= mix(0.72, 1.0, swirl);

    return saturate(core) * strength * 1.4;
}

// Wall-cloud collar: pulls the cloud base down around an active funnel so the
// funnel grows out of the parent cell instead of hanging detached.
float funnelBaseLowering(vec2 worldXZ, vec4 A, vec4 B) {
    float strength = B.z;
    if (strength <= 0.005) {
        return 0.0;
    }
    float radial = length(worldXZ - A.xy);
    float collarRadius = B.x * 3.2;
    float collar = exp(-(radial * radial) / max(collarRadius * collarRadius, 1.0));
    return collar * strength;
}

float cloudDensity(vec3 p, float mipBias, bool useDetail, bool nearCamera) {
    vec4 weather = sampleWeather(p.xz);
    vec4 morphology = sampleMorphology(p.xz);
    // Weather-map coverage already includes the cloudlet density. Its normal
    // spawned-field range is roughly 0.08..0.35; treating 0.92 as the full
    // point erased those clouds before the raymarch ever saw them.
    float coverage = smoothstep(0.012, 0.42, saturate(weather.r * CoverageMul));
    float energy = weather.a;
    float profile = morphology.r * 7.0;
    float verticalDevelopment = morphology.g;
    float humidity = morphology.b;
    float precipitation = morphology.a;
    float stratusProfile = profileWeight(profile, 1.0);
    float stratocumulusProfile = profileWeight(profile, 2.0);
    float cumulusProfile = profileWeight(profile, 3.0);
    float cumulonimbusProfile = profileWeight(profile, 4.0);
    float nimbostratusProfile = profileWeight(profile, 5.0);
    float cirrusProfile = profileWeight(profile, 6.0);
    float supercellProfile = profileWeight(profile, 7.0);
    float sheetProfile = max(stratusProfile, max(stratocumulusProfile, nimbostratusProfile));
    float stormProfile = max(cumulonimbusProfile, supercellProfile);

    float funnel = 0.0;
    float baseLower = 0.0;
    if (FunnelCount > 0) {
        funnel = funnelDensityAt(p, Funnel0A, Funnel0B);
        baseLower = funnelBaseLowering(p.xz, Funnel0A, Funnel0B);
        if (FunnelCount > 1) {
            funnel = max(funnel, funnelDensityAt(p, Funnel1A, Funnel1B));
            baseLower = max(baseLower, funnelBaseLowering(p.xz, Funnel1A, Funnel1B));
        }
    }

    if (coverage <= 0.008 && funnel <= 0.001) {
        return 0.0;
    }

    float slabSpan = max(SlabTopY - SlabBaseY, 1.0);
    float baseY = SlabBaseY + weather.g * slabSpan - baseLower * 34.0;
    float topY = SlabBaseY + weather.b * slabSpan;
    float layerSpan = max(topY - baseY, 2.0);
    float h01 = (p.y - baseY) / layerSpan;

    float cloud = 0.0;
    if (h01 > -0.02 && h01 < 1.02 && coverage > 0.008) {
        float hg = heightGradient(saturate(h01), energy, profile, verticalDevelopment);

        // Only storm-anvil/supercell profiles spread at the upper levels.
        // Neighbor taps expand the high cloud horizontally without widening
        // the base, which the old single extruded footprint could not do.
        float anvil = smoothstep(0.64, 0.94, saturate(h01))
            * stormProfile * energy * (0.22 + verticalDevelopment * 0.46);
        float anvilCoverage = coverage;
        if (anvil > 0.002) {
            float spread = mix(18.0, 92.0, saturate(energy * verticalDevelopment));
            anvilCoverage = max(anvilCoverage, smoothstep(0.012, 0.42,
                sampleWeather(p.xz + vec2(spread, 0.0)).r * CoverageMul));
            anvilCoverage = max(anvilCoverage, smoothstep(0.012, 0.42,
                sampleWeather(p.xz - vec2(spread, 0.0)).r * CoverageMul));
            anvilCoverage = max(anvilCoverage, smoothstep(0.012, 0.42,
                sampleWeather(p.xz + vec2(0.0, spread * 0.72)).r * CoverageMul));
            anvilCoverage = max(anvilCoverage, smoothstep(0.012, 0.42,
                sampleWeather(p.xz - vec2(0.0, spread * 0.72)).r * CoverageMul));
        }
        float coverageMod = saturate(mix(coverage, anvilCoverage, anvil) * (1.04 + anvil * 0.36));

        vec3 wind = WindVec * WorldTime;
        vec3 samplePos = p + vec3(wind.x, 0.0, wind.z);
        // Height shear: tops drift ahead of bases.
        samplePos.xz += LightDir.xz * 0.0 + WindVec.xz * WorldTime * saturate(h01) * 0.35;

        // Cloudlets are tens to low hundreds of blocks wide. The old 0.0016
        // scale sampled an almost constant value across an entire cloudlet,
        // producing one smooth oval instead of separate billows.
        float baseNoiseScale = 0.0052;
        baseNoiseScale = mix(baseNoiseScale, 0.0031, sheetProfile);
        baseNoiseScale = mix(baseNoiseScale, 0.0085, cirrusProfile);
        baseNoiseScale = mix(baseNoiseScale, 0.0046, stormProfile);
        vec4 baseNoise = texture(BaseNoiseSampler, baseNoiseDomain(samplePos, baseNoiseScale), mipBias);
        float lowFbm = baseNoise.g * 0.625 + baseNoise.b * 0.25 + baseNoise.a * 0.125;
        float baseShape = remap(baseNoise.r, -(1.0 - lowFbm), 1.0, 0.0, 1.0);
        baseShape *= hg;
        float openSkyBreakup = mix(0.76, 0.30, coverageMod);
        openSkyBreakup = mix(openSkyBreakup, mix(0.36, 0.20, humidity), sheetProfile);
        openSkyBreakup = mix(openSkyBreakup, 0.55, cirrusProfile);
        openSkyBreakup = mix(openSkyBreakup, 0.27, stormProfile * verticalDevelopment);
        cloud = remap(baseShape, openSkyBreakup, 1.0, 0.0, 1.0) * coverageMod;

        if (cloud > 0.003 && useDetail) {
            vec3 detailPos = detailNoiseDomain(samplePos);
            // Cheap curl-ish churn: offset detail lookup by low-freq noise.
            detailPos += (baseNoise.gbr - 0.5) * 0.18;
            vec4 detail = texture(DetailNoiseSampler, detailPos, mipBias);
            float detailFbm = detail.r * 0.625 + detail.g * 0.25 + detail.b * 0.125;
            if (nearCamera && DetailQuality >= 2) {
                vec4 fine = texture(
                    DetailNoiseSampler,
                    detailPos * 2.71 + vec3(0.173, -0.291, 0.417),
                    mipBias
                );
                detailFbm = detailFbm * 0.72 + (fine.r * 0.625 + fine.g * 0.25 + fine.b * 0.125) * 0.28;
            }
            // Wispy erosion near base, billowy rounding near top.
            float hfMod = mix(detailFbm, 1.0 - detailFbm, saturate(h01 * 4.0));
            float erosion = mix(0.28, 0.48, energy);
            erosion = mix(erosion, 0.20, sheetProfile);
            erosion = mix(erosion, 0.60, cirrusProfile);
            erosion = mix(erosion, 0.42, stormProfile);
            cloud = remap(cloud, hfMod * erosion, 1.0, 0.0, 1.0);
        }

        // Storm cells hold more condensed water low in the cloud.
        cloud *= mix(1.0, 1.35, energy * (1.0 - saturate(h01)) * 0.6);
        cloud *= mix(0.88, 1.10, humidity);
        cloud *= 1.0 + precipitation * (1.0 - saturate(h01)) * 0.32;
        // Cumulus stays broken and rounded; nimbostratus holds a continuous,
        // precipitation-heavy lower deck.
        cloud *= mix(1.0, mix(0.92, 1.08, hg), cumulusProfile * (1.0 - stormProfile));
        cloud *= mix(1.0, 1.12, nimbostratusProfile * precipitation);
        cloud *= smoothstep(0.012, 0.095, coverage);
    }

    float density = max(cloud, 0.0) * DensityMul;
    if (funnel > 0.001) {
        // Smooth union so the funnel inherits the cloud material seamlessly.
        vec3 funnelNoisePos = p * 0.010 + vec3(0.0, -WorldTime * 0.004, 0.0);
        float funnelNoise = texture(BaseNoiseSampler, baseNoiseDomain(funnelNoisePos, 1.0)).g;
        float funnelDensity = funnel * mix(0.7, 1.15, funnelNoise) * DensityMul * 1.6;
        density = density + funnelDensity - density * funnelDensity * 0.5;
    }
    return max(density, 0.0);
}

// ---------------------------------------------------------------------------
// Lighting
// ---------------------------------------------------------------------------

float henyeyGreenstein(float cosTheta, float g) {
    float g2 = g * g;
    return (1.0 - g2) / (4.0 * PI * pow(max(1.0 + g2 - 2.0 * g * cosTheta, 0.0001), 1.5));
}

float dualLobePhase(float cosTheta) {
    return mix(henyeyGreenstein(cosTheta, -0.18), henyeyGreenstein(cosTheta, 0.62), 0.72);
}

float lightMarchOpticalDepth(vec3 p) {
    int steps = clamp(LightSteps, 2, MAX_LIGHT_STEPS);
    float opticalDepth = 0.0;
    float stepLength = 14.0;
    vec3 pos = p;
    for (int i = 0; i < MAX_LIGHT_STEPS; i++) {
        if (i >= steps) {
            break;
        }
        // Cone spread: successive taps wander off-axis a little so thin gaps
        // do not read as hard occluders. The pattern is a FIXED golden-angle
        // spiral per tap index: deriving it from per-pixel jitter gives every
        // pixel a different light path, which shows up as salt-and-pepper
        // radiance noise that temporal filtering cannot integrate away.
        float ang = float(i) * 2.399963;
        float spread = (float(i) + 0.5) * 0.28;
        vec3 offset = vec3(
            cos(ang),
            0.35 * sin(ang * 1.7),
            sin(ang)
        ) * spread * stepLength * 0.24;
        pos += LightDir * stepLength;
        float density = cloudDensity(pos + offset, float(i) * 0.6, i < 2, false);
        opticalDepth += density * stepLength;
        stepLength *= 1.42;
    }
    return opticalDepth;
}

vec3 sampleLighting(vec3 p, float localDensity, float h01ForAmbient, float cosTheta, float distance01) {
    float opticalDepth = lightMarchOpticalDepth(p) * ExtinctionScale;

    // Multi-scattering octaves (Hillaire): each octave sees weaker extinction
    // and a flatter phase, which keeps thick storm cores luminous.
    float scatter = 0.0;
    float a = 1.0;
    float b = 1.0;
    int octaves = clamp(ScatterOctaves, 1, 3);
    for (int o = 0; o < 3; o++) {
        if (o >= octaves) {
            break;
        }
        float phase = mix(0.0795775, dualLobePhase(cosTheta), a); // isotropic falloff per octave
        scatter += b * phase * exp(-opticalDepth * a);
        a *= 0.42;
        b *= 0.52;
    }

    // Beer-powder: dark creases where in-scattering has not built up yet.
    float powder = 1.0 - exp(-localDensity * 24.0);
    float powderTerm = mix(1.0, saturate(powder * 1.35), saturate(cosTheta * 0.5 + 0.5) * 0.72);

    // Distant clouds redden more at sunset: longer light path through air.
    vec3 sunTint = mix(vec3(1.0), vec3(1.0, 0.52, 0.30), SunsetStrength * distance01 * 0.65);

    vec3 sunTerm = LightColor * sunTint * scatter * powderTerm * (4.0 * PI);
    vec3 ambient = mix(AmbientBottom, AmbientTop, saturate(h01ForAmbient));
    ambient *= mix(1.0, 0.52, StormDarkening);
    ambient *= max(1.0 - localDensity * 0.35, 0.0);

    return sunTerm + ambient * 0.85;
}

// ---------------------------------------------------------------------------
// Scene depth / reprojection helpers
// ---------------------------------------------------------------------------

float sceneRayLimit(vec3 rayDir, float fallback) {
    if (UseSceneDepth == 0) {
        return fallback;
    }
    float sceneDepth = texture(SceneDepthSampler, texCoord).r;
    if (sceneDepth >= 0.99999) {
        return fallback;
    }
    vec4 clip = vec4(texCoord * 2.0 - 1.0, sceneDepth * 2.0 - 1.0, 1.0);
    vec4 view = InvProjMat * clip;
    view /= max(abs(view.w), 0.00001);
    vec4 worldRel = InvViewRotMat * vec4(view.xyz, 0.0);
    float sceneT = dot(worldRel.xyz, rayDir);
    return min(fallback, max(0.0, sceneT - 0.3));
}

float depthAt(vec3 relPos) {
    vec4 clip = CloudProjMat * ViewRotMat * vec4(relPos, 1.0);
    float ndcDepth = clip.z / max(abs(clip.w), 0.00001);
    return clamp(ndcDepth * 0.5 + 0.5, 0.0, 1.0);
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

void main() {
    vec2 ndc = texCoord * 2.0 - 1.0;
    vec4 clipDir = vec4(ndc, -1.0, 1.0);
    vec4 viewDir4 = InvProjMat * clipDir;
    vec3 viewDir = normalize(viewDir4.xyz / max(abs(viewDir4.w), 0.00001));
    vec3 rayDir = normalize((InvViewRotMat * vec4(viewDir, 0.0)).xyz);

    // Slab intersection in world Y (camera-relative distances).
    float slabPadding = 40.0; // funnels extend below the slab base
    float lowY = (FunnelCount > 0 ? min(Funnel0A.w, SlabBaseY) - slabPadding : SlabBaseY);
    float highY = SlabTopY;
    float t0;
    float t1;
    if (abs(rayDir.y) < 0.0001) {
        bool inside = CameraPos.y >= lowY && CameraPos.y <= highY;
        t0 = inside ? 0.0 : -1.0;
        t1 = inside ? MaxRenderDistance : -1.0;
    } else {
        float ta = (lowY - CameraPos.y) / rayDir.y;
        float tb = (highY - CameraPos.y) / rayDir.y;
        t0 = min(ta, tb);
        t1 = max(ta, tb);
    }
    t0 = max(t0, 0.0);
    t1 = min(t1, MaxRenderDistance);

    if (t1 <= t0) {
        gl_FragDepth = 1.0;
        fragColor = vec4(0.0);
        return;
    }

    t1 = sceneRayLimit(rayDir, t1);
    if (t1 <= t0) {
        gl_FragDepth = 1.0;
        fragColor = vec4(0.0);
        return;
    }

    // Coverage pre-test: sample the weather map along the ray and skip fully
    // clear rays. This is the biggest saver on clear days.
    bool anyCoverage = FunnelCount > 0 || CoveragePretestEnabled == 0;
    if (!anyCoverage) {
        int pretestSamples = clamp(CoveragePretestSamples, 6, 16);
        float threshold = max(CoveragePretestThreshold, 0.0);
        for (int i = 0; i < 16; i++) {
            if (i >= pretestSamples) {
                break;
            }
            float t = mix(t0, t1, (float(i) + 0.5) / float(pretestSamples));
            vec3 p = CameraPos + rayDir * t;
            if (pretestWeatherCoverage(p.xz) > threshold) {
                anyCoverage = true;
                break;
            }
        }
    }
    if (!anyCoverage) {
        gl_FragDepth = 1.0;
        fragColor = vec4(0.0);
        return;
    }

    // Blue-noise jitter, animated by golden-ratio frame offset for temporal
    // integration.
    ivec2 blueSize = textureSize(BlueNoiseSampler, 0);
    vec2 blueUv = (gl_FragCoord.xy + vec2(FrameIndex * 17.0, FrameIndex * 29.0)) / vec2(blueSize);
    float blue = fract(texture(BlueNoiseSampler, blueUv).r + FrameIndex * 0.61803398875);

    int stepBudget = int(float(clamp(RaymarchSteps, 8, MAX_STEPS)) * clamp(StepScale, 0.4, 1.0));
    stepBudget = clamp(stepBudget, 8, MAX_STEPS);
    float span = t1 - t0;
    float baseStep = span / float(stepBudget);
    // Distance-based growth: far samples take larger strides.
    float fineStep = max(baseStep * 0.5, 2.0);
    float coarseStep = fineStep * 3.0;

    float cosTheta = dot(rayDir, LightDir);

    float t = t0 + blue * fineStep;
    float transmittance = 1.0;
    vec3 accumulated = vec3(0.0);
    float weightedT = 0.0;
    float weightSum = 0.0;
    int sinceHit = 100;

    for (int i = 0; i < MAX_STEPS; i++) {
        if (t >= t1 || transmittance < 0.015) {
            break;
        }
        bool fine = sinceHit < 6;
        float distanceGrowth = 1.0 + (t / max(MaxRenderDistance, 1.0)) * 2.2;
        float stepLength = (fine ? fineStep : coarseStep) * distanceGrowth;

        vec3 p = CameraPos + rayDir * t;
        bool nearCamera = t < 220.0;
        float density = cloudDensity(p, 0.0, DetailQuality > 0, nearCamera);

        if (density > 0.0008) {
            if (!fine) {
                // Entered cloud with a coarse stride: back up and resolve fine.
                t -= stepLength * 0.6;
                sinceHit = 0;
                continue;
            }
            sinceHit = 0;

            vec4 weather = sampleWeather(p.xz);
            float slabSpan = max(SlabTopY - SlabBaseY, 1.0);
            float baseY = SlabBaseY + weather.g * slabSpan;
            float topY = SlabBaseY + weather.b * slabSpan;
            float h01 = saturate((p.y - baseY) / max(topY - baseY, 2.0));

            float extinction = density * ExtinctionScale;
            float stepTrans = exp(-extinction * stepLength);
            vec3 radiance = sampleLighting(p, density, h01, cosTheta, saturate(t / MaxRenderDistance));
            radiance = max(radiance, mix(vec3(0.34, 0.35, 0.36), vec3(0.07, 0.08, 0.11), NightFactor));

            // Energy-conserving analytic integration over the step.
            vec3 integrated = radiance * (1.0 - stepTrans);
            accumulated += transmittance * integrated;

            float alphaContribution = transmittance * (1.0 - stepTrans);
            weightedT += t * alphaContribution;
            weightSum += alphaContribution;

            transmittance *= stepTrans;
        } else {
            sinceHit++;
        }
        t += stepLength;
    }

    float alpha = saturate(1.0 - transmittance);
    float representativeT = weightSum > 0.0005 ? weightedT / weightSum : (t0 + t1) * 0.5;
    vec3 relRepresentative = rayDir * representativeT;

    vec4 result = vec4(accumulated, alpha);
    float resultDepth = depthAt(relRepresentative);
    float resultDepthDerivative = fwidth(resultDepth);

    // Temporal reprojection: reproject the representative cloud point into the
    // previous frame and blend history when it lands on-screen. History is
    // CLAMPED to the current result +- a margin instead of confidence-rejected:
    // rejection keyed on alpha difference throws history away exactly where
    // the jitter noise is (noise -> alpha delta -> rejection -> permanent
    // grain), while clamping bounds any ghost to a few frames and still
    // integrates the dither everywhere.
    if (HistoryValid == 1 && HistoryBlend > 0.001) {
        vec4 prevClip = PrevViewProjMat * vec4(relRepresentative, 1.0);
        if (prevClip.w > 0.0001) {
            vec2 prevUv = (prevClip.xy / prevClip.w) * 0.5 + 0.5;
            if (prevUv.x > 0.001 && prevUv.x < 0.999 && prevUv.y > 0.001 && prevUv.y < 0.999) {
                vec4 history = texture(HistorySampler, prevUv);
                float historyDepth = texture(HistoryDepthSampler, prevUv).r;
                float depthTolerance = max(0.00035, resultDepthDerivative * 6.0);
                float depthConfidence = 1.0 - smoothstep(
                    depthTolerance,
                    depthTolerance * 12.0,
                    abs(historyDepth - resultDepth)
                );
                if (historyDepth >= 0.99999 || resultDepth >= 0.99999) {
                    depthConfidence = 0.0;
                }
                // Alpha stores one minus transmittance. Keep jitter-scale
                // differences, but reject history when a cloud appears,
                // disappears or the camera crosses its boundary.
                float transmittanceDelta = abs(history.a - result.a);
                float transmittanceConfidence = 1.0 - smoothstep(
                    0.045,
                    0.22,
                    transmittanceDelta
                );
                history = clamp(history, result - 0.25, result + 0.25);
                // Fade history out near the screen border: reprojection there
                // samples clamp-to-edge stretched texels during camera turns,
                // which otherwise smears as streaks along the edges.
                float borderDistance = min(
                    min(prevUv.x, 1.0 - prevUv.x),
                    min(prevUv.y, 1.0 - prevUv.y)
                );
                float edgeFade = smoothstep(0.0, 0.04, borderDistance);
                float historyWeight = HistoryBlend
                    * edgeFade
                    * depthConfidence
                    * transmittanceConfidence;
                result = mix(result, history, historyWeight);
            }
        }
    }

    vec3 straightFloor = mix(vec3(0.58, 0.60, 0.63), vec3(0.070, 0.080, 0.105), NightFactor);
    result.rgb = max(result.rgb, straightFloor * result.a);

    if (result.a < 0.002) {
        gl_FragDepth = 1.0;
        fragColor = vec4(0.0);
        return;
    }

    gl_FragDepth = resultDepth;
    fragColor = result;
}
