#version 150

#moj_import <projectatmosphere:cloud_storm_tower_union.glsl>
#moj_import <projectatmosphere:cloud_ray_origin_jitter.glsl>
#moj_import <projectatmosphere:cloud_storm_tower_taper.glsl>

// Project Atmosphere volumetric cloud raymarch.
// One fullscreen pass marches a single global density field driven by the
// cell weather map, baked Perlin-Worley / Worley 3D noise, real sun lighting
// (Beer-powder, dual-lobe HG phase, multi-scattering octaves), sky ambient,
// temporal reprojection, and an analytic tornado funnel slot.

uniform sampler2D WeatherMapSampler;
uniform sampler2D MorphologyMapSampler;
uniform sampler2D CumulusStageSupportMapSampler;
uniform sampler2D CumulusStageBaseMapSampler;
uniform sampler2D CumulusStageTopMapSampler;
uniform sampler2D StormCandidateMapSampler;
uniform sampler2D StormDescriptorSampler;
uniform sampler2D PuffCandidateMapSampler;
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
uniform float CameraCloudDensity;

uniform vec2 WeatherOrigin;
uniform float WeatherExtent;
uniform float SlabBaseY;
uniform float SlabTopY;
uniform float MaxPrecipitation;
uniform int PuffLobeCount;
uniform int StormLobeCount;
uniform int PuffShapeMode; // 0=fallback, 1=legacy hybrid, 2=direct-only diagnostic
uniform int PuffDensityStage; // 0=final, 1=analytic-all, 2=analytic-indexed, 3=envelope, 4=pre-erosion, 7=continuous-all, 12=carrier-billow
uniform int PuffTierFilter; // -1=all, 0=base, 1=middle, 2=crown, 3=legacy/unknown

const int MAX_PUFF_LOBES = 32;
const int PUFF_CANDIDATES_PER_TILE = 8;
const int PUFF_PACK_BASE = 33;
uniform vec4 PuffPosRadius[MAX_PUFF_LOBES];
uniform vec4 PuffShape[MAX_PUFF_LOBES];
uniform vec4 PuffMedia[MAX_PUFF_LOBES];

const int MAX_STORM_LOBES = 64;
const int STORM_CANDIDATES_PER_TILE = 8;
const int STORM_PACK_BASE = 65;
const int MAX_STORM_GROUPS = 8;

// --- Phase 4S storm density composition constants ---------------------
// Every value here is derived, not tuned; the derivations live in
// specs/001-native-storm-rendering/validation/morphology-thresholds.md and are
// re-verified against the baked noise by stormDensityThresholdSandbox.
//
// The coverage envelope's boundary must span at least one cycle of the
// coarsest sculpting frequency, otherwise the descriptor's own geometric edge
// shows through as a seam. Lowest detail octave: period 2 over a 1/0.022 block
// tile = 22.727 blocks, so the half-width is 11.364 blocks.
const float STORM_MIN_EDGE_BLOCKS = 11.363636;

// Ceiling on the envelope boundary half-width as a fraction of a lobe's own
// half-height. Mirrors StormLobeEvaluator.VERTICAL_EDGE_BOUND_FRACTION.
const float STORM_VERTICAL_EDGE_BOUND_FRACTION = 0.75;
// Cap fillet as a fraction of the lobe's smaller extent, bounded by the same
// wavelength. Flat slabs and recognizable spheres are both rejected forms.
const float STORM_CAP_ROUNDING_FRACTION = 0.35;
// Smooth-union blend distances in world-space blocks, proportional to the
// smaller participating lobe so a narrow tower is not widened to base scale.
const float STORM_LOBE_BLEND_FRACTION = 0.25;
const float STORM_GROUP_BLEND_FRACTION = 0.18;
const float STORM_MIN_BLEND_BLOCKS = 4.0;
const float STORM_MAX_BLEND_BLOCKS = 48.0;
// T133 / SC-020.  T121's rejection also has to prove the lobe cannot set a
// groupActiveRoleMask bit, which happens when lobeDistance <= lobeSoftness.
// The guard tests verticalLowerBound, but the mask tests the SDF, and the two
// differ by up to ~3.8e-6 blocks of float32 rounding in the SDF tail.  At
// softness magnitudes (11-100 blocks) one float32 ULP is ~1e-6, so that
// shortfall is representable there and a rejected lobe could still have
// satisfied lobeDistance <= lobeSoftness - a discrete divergence, unlike the
// blend term where one ULP at union distances is far coarser than the
// shortfall and the smooth union is provably unaffected.  Derived and proven
// in StormVolumetricGeometrySandbox validateT121SoftnessBoundary(); it only
// raises the rejection threshold, so it can only reject fewer lobes, and it
// changes no density, geometry, blend, softness or union equation.
const float STORM_T121_SOFTNESS_MARGIN_BLOCKS = 0.0009765625;
// Measured 5th/95th percentiles of the Perlin-Worley carrier over the storm
// sampling domain. The raw carrier is strongly high-biased, so it is
// normalized onto its own usable range before the coverage remap.
const float STORM_CARRIER_P05 = 0.7128;
const float STORM_CARRIER_P95 = 0.8451;
// Full-strength coverage-remap floor. Weak live descriptor strengths receive
// only their mathematically necessary additional fill, so coverage authority
// is retained instead of flattening all roles to one density.
const float STORM_CORE_FILL = 0.45;
const float STORM_STRUCTURAL_CONTINUITY_DENSITY = 0.10;
const float STORM_STRUCTURAL_CONTINUITY_COVERAGE = 0.82;
const float STORM_STRUCTURAL_CONTINUITY_STRENGTH = 0.84;
const float STORM_P05_EROSION_BITE = 0.283008;
const float STORM_LIVE_STRENGTH_FILL_HEADROOM = 0.021;
// Detail erosion amplitude. Unchanged from the pre-correction storm value;
// what changed is that it now reaches the storm interior.
const float STORM_EROSION = 0.44;
// Storm base-noise domain scale: one tile spans 400 blocks, so the base Worley
// octaves have 50.0/25.0/12.5 block wavelengths.
const float STORM_BASE_NOISE_SCALE = 0.0025;

uniform vec3 LightDir;
uniform vec3 LightColor;
uniform vec3 AmbientTop;
uniform vec3 AmbientBottom;
uniform float SunsetStrength;
uniform float NightFactor;
uniform float StormDarkening;

uniform vec3 WindVec;      // direction/anisotropy only; never absolute-time translation
uniform vec2 MaterialOffset;     // integrated displacement of the presented volume
uniform vec2 MaterialFrameDelta; // current minus previous production-frame offset
uniform float WorldTime;   // world time in ticks (with partial)
uniform float FrameIndex;

uniform int RaymarchSteps;
uniform int LightSteps;
uniform int ScatterOctaves;
uniform int DetailQuality;   // 0 = off, 1 = normal, 2 = extra near-camera octave
uniform float StepScale;     // frame-time governor multiplier (0.5 .. 1.0)
uniform float ExteriorFineStep; // CPU-derived exterior surface stride in world blocks
uniform float MaxRenderDistance;

uniform int UseSceneDepth;
uniform int CoveragePretestEnabled;
uniform int CoveragePretestSamples;
uniform float CoveragePretestThreshold;
uniform int CoveragePretestDilation;
uniform int HistoryValid;
uniform float HistoryBlend;
uniform int DebugView;
uniform int StormTopologyMode;

// T133 / SC-020 diagnostic optimization mode.  Zero is NORMAL_PRODUCTION and
// is the only value the renderer uploads outside a deliberate diagnostic
// capture, so ordinary frames take exactly the optimized paths they took
// before this uniform existed.  The OFF arms exist solely to prove, against an
// unoptimized but mathematically identical execution, that the production ON
// paths preserve the image.  They change no equation, no sample position, no
// jitter sequence and no texture content: only whether an optimization is
// used.
const int PA_OPT_NORMAL_PRODUCTION = 0;
const int PA_OPT_T121_OFF = 1;
const int PA_OPT_T122_OFF = 2;
uniform int PaDiagnosticOptimizationMode;

/** True when T121's conservative rejection must be bypassed. */
bool paT121Off() {
    return (PaDiagnosticOptimizationMode & PA_OPT_T121_OFF) != 0;
}

/** True when T122's descriptor-fetch reuse must be bypassed. */
bool paT122Off() {
    return (PaDiagnosticOptimizationMode & PA_OPT_T122_OFF) != 0;
}

// T123: emitted only by the two on-demand workload views. FINAL frames retain
// the normal colour/history path and never read these values back.
int paPrimaryRaySteps = 0;
int paDescriptorEvaluations = 0;
int paDescriptorTextureFetches = 0;
int paAvoidedDescriptorTextureFetches = 0;
int paLightMarchDensityEvaluations = 0;
int paEmptySpaceRejects = 0;
int paEarlyTerminations = 0;
int paConservativeDescriptorRejects = 0;

bool paWorkloadCaptureActive() {
    return DebugView == 22 || DebugView == 23;
}
// T128 on-demand centre-line trace uniforms. They are inert unless DebugView
// is STORM_MATERIAL_TRACE (21), so they never affect production rendering.
uniform vec2 StormTraceOrigin;
uniform float StormTraceYStart;
uniform float StormTraceYInterval;
uniform int StormTraceSamples;
uniform int StormTraceStage;

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

// Storm primary billows use the same world-space warp function as Java, but
// scale its amplitude with the noise domain. A fixed 0.31 tile offset became
// a near-constant directional shear when the storm base domain was enlarged.
// The clamp preserves legacy behaviour for other cloud families while holding
// storm distortion to 8% of its sampling Jacobian.
vec3 stormBaseNoiseDomain(vec3 worldPos) {
    vec3 rotated = vec3(
        dot(worldPos, vec3(0.8138, 0.2962, -0.5000)),
        dot(worldPos, vec3(-0.1401, 0.9408, 0.3085)),
        dot(worldPos, vec3(0.5630, -0.1677, 0.8090))
    );
    const float WARP_WAVE_NUMBER = 0.00233;
    const float MAX_WARP_DISTORTION = 0.08;
    float warpAmplitude = min(
        0.31,
        MAX_WARP_DISTORTION * STORM_BASE_NOISE_SCALE / WARP_WAVE_NUMBER
    );
    return rotated * STORM_BASE_NOISE_SCALE
        + lowFrequencyDomainWarp(worldPos) * warpAmplitude;
}

vec3 detailNoiseDomain(vec3 worldPos) {
    vec3 rotated = vec3(
        dot(worldPos, vec3(0.7071, -0.4082, 0.5774)),
        dot(worldPos, vec3(0.7071, 0.4082, -0.5774)),
        dot(worldPos, vec3(0.0000, 0.8165, 0.5774))
    );
    return rotated * 0.022 + lowFrequencyDomainWarp(worldPos * 1.731) * 0.43;
}

vec2 cloudWindDirection() {
    float windLength = length(WindVec.xz);
    return windLength > 0.00001 ? WindVec.xz / windLength : vec2(1.0, 0.0);
}

float verticalBand(float h01, float baseEnd, float topStart) {
    float h = saturate(h01);
    return smoothstep(0.0, max(baseEnd, 0.001), h)
        * (1.0 - smoothstep(clamp(topStart, 0.01, 0.99), 1.0, h));
}

// Reconstructs the exact horizontal material term used by profile-1 density.
// It is evaluated once at the first visible material, never per light tap.
float stratusHorizontalMaterialSignal(
        vec3 p,
        float h01,
        vec4 weather,
        vec4 morphology) {
    float coverageSignal = saturate(weather.r * CoverageMul);
    float coverage = smoothstep(0.012, 0.42, coverageSignal);
    float condensate = saturate(
        coverage * 0.36
            + weather.a * 0.28
            + morphology.a * 0.24
            + morphology.b * 0.12
    );
    vec3 samplePos = p;
    samplePos.xz -= MaterialOffset;
    vec4 baseNoise = texture(
        BaseNoiseSampler,
        baseNoiseDomain(samplePos, 0.0031),
        0.0
    );
    float lowFbm = baseNoise.g * 0.625
        + baseNoise.b * 0.25
        + baseNoise.a * 0.125;
    float baseCarrier = saturate(remap(
        baseNoise.r,
        -(1.0 - lowFbm),
        1.0,
        0.0,
        1.0
    ));
    vec2 windDir = cloudWindDirection();
    vec2 crossWind = vec2(-windDir.y, windDir.x);
    float directionalCarrier = 0.5 + 0.5 * sin(
        dot(samplePos.xz, crossWind) * 0.018
            + dot(samplePos.xz, windDir) * 0.004
            + lowFbm * 5.1
    );
    return smoothstep(
        0.18,
        0.70,
        lowFbm * 0.58
            + baseCarrier * 0.24
            + directionalCarrier * 0.12
            + condensate * 0.06
    );
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

// Returns XY surface gradient and signed local curvature in world blocks.
// This is intentionally called once per stratus ray, never from cloudDensity
// or a light-cone tap. Empty neighbours inherit the centre height so the
// weather-map sentinel cannot create a false cliff at the field boundary.
vec3 stratusSurfaceDifferential(
        vec2 worldXZ,
        vec4 centerWeather,
        float surfaceSide) {
    ivec2 mapSize = textureSize(WeatherMapSampler, 0);
    float texelWorld = WeatherExtent / max(float(mapSize.x), 1.0);
    // Probe a broad surface footprint. The weather map is eight world blocks
    // per texel at the normal 512 resolution; a 48-block radius rejects
    // texel-scale embossing while still resolving the field-local bands.
    float probeWorld = max(48.0, texelWorld * 4.0);
    vec2 uv = (worldXZ - WeatherOrigin) / max(WeatherExtent, 1.0);
    float padding = probeWorld / max(WeatherExtent, 1.0);
    float edgeDistance = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
    float edgeWeight = smoothstep(padding, padding * 2.0, edgeDistance);
    if (edgeWeight <= 0.0) {
        return vec3(0.0);
    }

    vec4 xMinus = sampleWeather(worldXZ - vec2(probeWorld, 0.0));
    vec4 xPlus = sampleWeather(worldXZ + vec2(probeWorld, 0.0));
    vec4 zMinus = sampleWeather(worldXZ - vec2(0.0, probeWorld));
    vec4 zPlus = sampleWeather(worldXZ + vec2(0.0, probeWorld));
    float centerHeight = mix(centerWeather.g, centerWeather.b, surfaceSide);
    float xMinusHeight = mix(
        centerHeight,
        mix(xMinus.g, xMinus.b, surfaceSide),
        smoothstep(0.010, 0.080, xMinus.r)
    );
    float xPlusHeight = mix(
        centerHeight,
        mix(xPlus.g, xPlus.b, surfaceSide),
        smoothstep(0.010, 0.080, xPlus.r)
    );
    float zMinusHeight = mix(
        centerHeight,
        mix(zMinus.g, zMinus.b, surfaceSide),
        smoothstep(0.010, 0.080, zMinus.r)
    );
    float zPlusHeight = mix(
        centerHeight,
        mix(zPlus.g, zPlus.b, surfaceSide),
        smoothstep(0.010, 0.080, zPlus.r)
    );

    float slabSpan = max(SlabTopY - SlabBaseY, 1.0);
    vec2 gradient = vec2(
        xPlusHeight - xMinusHeight,
        zPlusHeight - zMinusHeight
    ) * slabSpan / (2.0 * probeWorld);
    float curvature = (
        centerHeight
            - 0.25 * (xMinusHeight + xPlusHeight + zMinusHeight + zPlusHeight)
    ) * slabSpan;
    return vec3(gradient, curvature) * edgeWeight;
}

const float MORPHOLOGY_CATEGORY_SCALE = 64.0;

bool hasMorphologyCategory(float encodedCategory) {
    return encodedCategory > (0.5 / MORPHOLOGY_CATEGORY_SCALE);
}

vec4 sampleMorphology(vec2 worldXZ) {
    vec2 uv = (worldXZ - WeatherOrigin) / WeatherExtent;
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        return vec4(0.0);
    }
    // GBA are continuous traits. R is categorical and cannot be linearly
    // interpolated, but blindly taking the nearest texel is also wrong: the
    // linearly filtered WeatherMap can still receive support from another one
    // of the same four texels when the nearest category is empty. Select the
    // highest-weight valid categorical contributor from that exact 2x2 filter
    // footprint instead.
    vec4 morphology = texture(MorphologyMapSampler, uv);
    ivec2 size = textureSize(MorphologyMapSampler, 0);
    ivec2 nearestCoord = clamp(
        ivec2(floor(uv * vec2(size))),
        ivec2(0),
        size - ivec2(1)
    );
    float nearestCategory = texelFetch(MorphologyMapSampler, nearestCoord, 0).r;
    // The nearest texel is also the maximum-weight bilinear contributor. Keep
    // the common cloud-interior path at one categorical fetch; only an empty
    // nearest texel needs the boundary recovery below.
    if (hasMorphologyCategory(nearestCategory)) {
        morphology.r = nearestCategory;
        return morphology;
    }
    vec2 texelPosition = uv * vec2(size) - vec2(0.5);
    ivec2 baseCoord = ivec2(floor(texelPosition));
    vec2 fraction = fract(texelPosition);
    float selectedCategory = 0.0;
    float selectedWeight = -1.0;
    for (int y = 0; y < 2; y++) {
        float weightY = y == 0 ? 1.0 - fraction.y : fraction.y;
        for (int x = 0; x < 2; x++) {
            float weightX = x == 0 ? 1.0 - fraction.x : fraction.x;
            ivec2 coord = clamp(
                baseCoord + ivec2(x, y),
                ivec2(0),
                size - ivec2(1)
            );
            float candidate = texelFetch(MorphologyMapSampler, coord, 0).r;
            float candidateWeight = weightX * weightY;
            if (hasMorphologyCategory(candidate) && candidateWeight > selectedWeight) {
                selectedCategory = candidate;
                selectedWeight = candidateWeight;
            }
        }
    }
    morphology.r = selectedCategory;
    return morphology;
}

vec4 sampleCumulusStageSupports(vec2 worldXZ) {
    vec2 uv = (worldXZ - WeatherOrigin) / WeatherExtent;
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        return vec4(0.0);
    }
    vec4 stages = texture(CumulusStageSupportMapSampler, uv);
    float edgeDistance = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
    return stages * smoothstep(0.0, 0.055, edgeDistance);
}

vec4 sampleCumulusStageBases(vec2 worldXZ) {
    vec2 uv = (worldXZ - WeatherOrigin) / WeatherExtent;
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        return vec4(0.0);
    }
    vec4 stages = texture(CumulusStageBaseMapSampler, uv);
    float edgeDistance = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
    return stages * smoothstep(0.0, 0.055, edgeDistance);
}

vec4 sampleCumulusStageTops(vec2 worldXZ) {
    vec2 uv = (worldXZ - WeatherOrigin) / WeatherExtent;
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        return vec4(0.0);
    }
    vec4 stages = texture(CumulusStageTopMapSampler, uv);
    float edgeDistance = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
    return stages * smoothstep(0.0, 0.055, edgeDistance);
}

int decodeStormCandidate(vec4 packedCandidates, int candidateRank) {
    float packedValue = candidateRank < 2
        ? packedCandidates.r
        : (candidateRank < 4
            ? packedCandidates.g
            : (candidateRank < 6 ? packedCandidates.b : packedCandidates.a));
    int encoded = int(floor(packedValue + 0.5));
    int pairRank = candidateRank - (candidateRank / 2) * 2;
    int digit = pairRank == 0
        ? encoded - (encoded / STORM_PACK_BASE) * STORM_PACK_BASE
        : encoded / STORM_PACK_BASE;
    return digit - 1;
}

vec4 stormCandidatesAt(vec2 worldXZ) {
    if (StormLobeCount <= 0) {
        return vec4(0.0);
    }
    vec2 uv = (worldXZ - WeatherOrigin) / WeatherExtent;
    if (uv.x < 0.0 || uv.x >= 1.0 || uv.y < 0.0 || uv.y >= 1.0) {
        return vec4(0.0);
    }
    ivec2 size = textureSize(StormCandidateMapSampler, 0);
    ivec2 coord = clamp(ivec2(floor(uv * vec2(size))), ivec2(0), size - ivec2(1));
    return texelFetch(StormCandidateMapSampler, coord, 0);
}

vec4 stormDescriptorTexel(int descriptorIndex, int texelIndex) {
    if (paWorkloadCaptureActive()) {
        paDescriptorTextureFetches++;
    }
    return texelFetch(StormDescriptorSampler, ivec2(texelIndex, descriptorIndex), 0);
}

void stormRoleProfile(
        float height01,
        int role,
        out float profileRadius,
        out float verticalShape,
        out float shearProgress) {
    if (role == 0) {
        profileRadius = mix(0.84, 0.58, height01)
            + 0.22 * pow(sin(PI * height01), 0.70);
        verticalShape = smoothstep(0.0, 0.08, height01)
            * (1.0 - smoothstep(0.48, 1.0, height01));
        shearProgress = height01 * 0.12;
    } else if (role == 1) {
        profileRadius = mix(0.84, 0.56, height01)
            + 0.18 * pow(sin(PI * height01), 0.65);
        verticalShape = smoothstep(0.0, 0.24, height01)
            * (1.0 - smoothstep(0.62, 1.0, height01));
        shearProgress = smoothstep(0.0, 1.0, height01) * 0.35;
    } else if (role == 2) {
        // Broad root across the core's upper half before continuous tapering.
        // Keep this mirrored with StormLobeEvaluator.
        profileRadius = mix(1.25, 0.60, height01)
            + 0.18 * pow(sin(PI * height01), 0.65);
        verticalShape = smoothstep(0.0, 0.28, height01)
            * (1.0 - smoothstep(0.72, 1.0, height01));
        shearProgress = pow(height01, 1.6);
    } else {
        // Broaden the anvil's root and canopy for a tower-fed upper deck;
        // sampled descriptor strength remains authoritative for coverage.
        profileRadius = mix(0.70, 2.10, smoothstep(0.0, 0.62, height01))
            + 0.08 * pow(sin(PI * height01), 0.55)
            - 0.10 * smoothstep(0.88, 1.0, height01);
        verticalShape = smoothstep(0.0, 0.35, height01)
            * (1.0 - smoothstep(0.76, 1.0, height01));
        shearProgress = smoothstep(0.0, 0.65, height01);
    }
}

// The authored vertical endpoints are shared by the exact lobe SDF and by
// T121's conservative rejection.  Keeping the role handoff extension in one
// place makes the rejection a lower bound on the exact same geometry rather
// than a second, approximate height profile.
void stormDescriptorVerticalBounds(
        vec4 positionHeight,
        int role,
        out float roleBaseY,
        out float roleTopY) {
    roleBaseY = positionHeight.z;
    roleTopY = positionHeight.w;
    if (role == 1) {
        roleTopY += 32.0;
    } else if (role == 2) {
        roleBaseY -= 28.0;
    } else if (role == 3) {
        roleBaseY -= 12.0;
        roleTopY += 16.0;
    }
}

// The rounded lobe SDF is always at least this far from a point vertically
// outside its role-specific cap interval.  It deliberately ignores horizontal
// distance, so it can only reject a descriptor that is provably unable to
// affect the smooth union, role mask, or weighted local height.
float stormVerticalDistanceLowerBound(vec3 p, vec4 positionHeight, int role) {
    float roleBaseY;
    float roleTopY;
    stormDescriptorVerticalBounds(positionHeight, role, roleBaseY, roleTopY);
    float centreY = (roleBaseY + roleTopY) * 0.5;
    return abs(p.y - centreY) - (roleTopY - roleBaseY) * 0.5;
}

// Signed geometric distance from p to this lobe's surface, in world-space
// blocks. Negative inside, zero on the surface, positive outside - and valid
// everywhere outside, including directly above or below the lobe, where a
// density-derived pseudo-distance carries no information at all.
//
// The lobe is its oriented, sheared, height-varying ellipse intersected with
// its own vertical span, closed by a rounded-box fillet. The normalized
// ellipse coordinate is converted to blocks by dividing out the magnitude of
// its gradient, which is exact for a circular section and a well-behaved
// first-order distance at the eccentricities the role profiles produce.
//
// Returns +1e9 for an unused descriptor slot so a sentinel can never pull the
// union toward world origin.
float directStormLobeDistanceFromData(
        vec3 p,
        vec4 positionHeight,
        vec4 radiusRotation,
        vec4 shearMedia,
        int groupSlot,
        int role) {
    if (paWorkloadCaptureActive()) {
        paDescriptorEvaluations++;
    }

    // Bounded role handoffs use the stored endpoints as anchors, then extend
    // only the semantic transition side so the stages overlap over height.
    float roleBaseY;
    float roleTopY;
    stormDescriptorVerticalBounds(positionHeight, role, roleBaseY, roleTopY);
    float height = max(roleTopY - roleBaseY, 1.0);
    float centerY = (roleBaseY + roleTopY) * 0.5;
    float halfHeight = height * 0.5;
    float height01 = clamp((p.y - roleBaseY) / height, 0.0, 1.0);

    float profileRadius;
    float verticalShape;
    float shearProgress;
    stormRoleProfile(height01, role, profileRadius, verticalShape, shearProgress);

    vec2 local = p.xz - positionHeight.xy - shearMedia.xy * shearProgress;
    vec2 oriented = vec2(
        local.x * radiusRotation.w + local.y * radiusRotation.z,
        -local.x * radiusRotation.z + local.y * radiusRotation.w
    );
    vec2 radii = max(radiusRotation.xy * profileRadius, vec2(1.0));
    // The supplied anvil ellipses are intentionally wind-aligned. Widen only
    // their short horizontal axis so four members read as one lateral canopy
    // from FAR/BELow views instead of a thin directional ribbon.
    if (role == 3) {
        radii.y *= 1.56;
    }
    float radial = length(oriented / radii);
    float groupOffset = float(max(groupSlot, 0)) * 997.0;
    vec3 morphologyWarp = lowFrequencyDomainWarp(
        p * 2.3 + vec3(groupOffset, groupOffset * 0.61, -groupOffset * 0.73)
    );
    radial += dot(morphologyWarp, vec3(0.45, 0.20, 0.35)) * 0.08;

    float gradient = length(oriented / (radii * radii));
    float effectiveRadius = gradient > 1.0e-9 ? radial / gradient : min(radii.x, radii.y);

    float wallDistance = (radial - 1.0) * effectiveRadius;
    float capDistance = abs(p.y - centerY) - halfHeight;
    float rounding = min(
        min(effectiveRadius, halfHeight) * STORM_CAP_ROUNDING_FRACTION,
        STORM_MIN_EDGE_BLOCKS
    );
    float roundedWall = wallDistance + rounding;
    float roundedCap = capDistance + rounding;
    vec2 outside = max(vec2(roundedWall, roundedCap), vec2(0.0));
    return length(outside) + min(max(roundedWall, roundedCap), 0.0) - rounding;
}

// Retained for consumers that do not already have the descriptor in
// registers. The primary storm-group evaluation uses the FromData form below
// so its four fetched texels are evaluated once rather than re-fetched by the
// exact SDF and softness calculation (T122).
float directStormLobeDistance(vec3 p, int descriptorIndex, out int groupSlotOut, out int roleOut) {
    vec4 positionHeight = stormDescriptorTexel(descriptorIndex, 0);
    vec4 radiusRotation = stormDescriptorTexel(descriptorIndex, 1);
    vec4 shearMedia = stormDescriptorTexel(descriptorIndex, 2);
    vec4 lifecycleRole = stormDescriptorTexel(descriptorIndex, 3);
    groupSlotOut = -1;
    roleOut = -1;
    if (lifecycleRole.w < -0.5) {
        return 1.0e9;
    }
    int packedTopology = int(floor(lifecycleRole.w + 0.5));
    int role = packedTopology - (packedTopology / 8) * 8;
    int groupSlot = packedTopology / (8 * 64 * 64);
    groupSlotOut = groupSlot;
    roleOut = role;
    return directStormLobeDistanceFromData(
        p, positionHeight, radiusRotation, shearMedia, groupSlot, role
    );
}

float stormSmallerRadius(int descriptorIndex) {
    vec4 radiusRotation = stormDescriptorTexel(descriptorIndex, 1);
    return min(radiusRotation.x, radiusRotation.y);
}

// Blend distances are world-space blocks. A blend radius expressed in density
// units has no consistent spatial meaning: it varies with the local density
// gradient rather than with distance, which is why the previous form
// over-smoothed broad lobes while leaving creases between narrow ones.
float stormLobeBlendRadius(
        float firstRadius,
        float secondRadius,
        int firstRole,
        int secondRole) {
    float smaller = firstRadius <= 0.0 ? secondRadius : min(firstRadius, secondRadius);
    bool coreTower = ((firstRole == 1 || firstRole == 2)
            && (secondRole == 1 || secondRole == 2))
        || (firstRole == 3 && secondRole == 3);
    return clamp(
        smaller * (coreTower ? 0.60 : STORM_LOBE_BLEND_FRACTION),
        STORM_MIN_BLEND_BLOCKS,
        STORM_MAX_BLEND_BLOCKS
    );
}

float stormGroupBlendRadius(float firstRadius, float secondRadius) {
    float smaller = firstRadius <= 0.0 ? secondRadius : min(firstRadius, secondRadius);
    return clamp(
        smaller * STORM_GROUP_BLEND_FRACTION,
        STORM_MIN_BLEND_BLOCKS,
        STORM_MAX_BLEND_BLOCKS
    );
}

// Interpolation factor of the smooth minimum. Envelope strength and boundary
// softness are blended by the same factor so they stay continuous wherever the
// distance field is; a nearest-lobe-wins selection would reintroduce the
// winner-switch seams the union exists to remove.
float stormBlendFactor(float first, float second, float blendRadius) {
    return saturate(0.5 + 0.5 * (second - first) / max(blendRadius, 0.0001));
}

// Polynomial smooth minimum on world-space distances.
float stormSmoothMinimum(float first, float second, float blendRadius) {
    float radius = max(blendRadius, 0.0001);
    float h = saturate(0.5 + 0.5 * (second - first) / radius);
    return mix(second, first, h) - radius * h * (1.0 - h);
}

// Envelope boundary half-width in blocks for one descriptor.
float stormEdgeWidthBlocksFromData(
        vec4 positionHeight, vec4 radiusRotation, vec4 shearMedia, int role) {
    float edgeSoftness = shearMedia.w;
    float normalized = role == 3
        ? max(0.12, edgeSoftness * 1.65)
        : (role == 0
            ? max(0.06, edgeSoftness * 0.66)
            : max(0.06, edgeSoftness * 0.62));
    float horizontal = normalized * min(radiusRotation.x, radiusRotation.y);
    // T098, mirroring StormLobeEvaluator.edgeWidthBlocks. The coverage envelope
    // fades over +/- this width isotropically, but the width is derived from a
    // horizontal extent, so a role much wider than it is tall gets a boundary
    // wider than its own body. Measured on the severe fixture that was every
    // ANVIL member at 2.47-2.58 half-heights, hanging ~150 blocks of
    // very-low-coverage haze below its own base for the carrier to shred.
    // Bounding by the lobe's vertical extent binds only that degenerate case.
    float roleBaseY;
    float roleTopY;
    stormDescriptorVerticalBounds(positionHeight, role, roleBaseY, roleTopY);
    float halfHeight = max(roleTopY - roleBaseY, 1.0) * 0.5;
    return max(
        STORM_MIN_EDGE_BLOCKS,
        min(horizontal, halfHeight * STORM_VERTICAL_EDGE_BOUND_FRACTION)
    );
}

float stormEdgeWidthBlocks(int descriptorIndex, int role) {
    return stormEdgeWidthBlocksFromData(
        stormDescriptorTexel(descriptorIndex, 0),
        stormDescriptorTexel(descriptorIndex, 1),
        stormDescriptorTexel(descriptorIndex, 2),
        role
    );
}

// Maps the unioned world-space distance to a bounded coverage envelope. This
// is stage 4 of the composition: it is never a visible density, and nothing
// downstream may treat it as one.
float stormEnvelopeFromDistance(float distanceBlocks, float softnessBlocks, float strength) {
    float softness = max(softnessBlocks, STORM_MIN_EDGE_BLOCKS);
    return saturate((1.0 - smoothstep(-softness, softness, distanceBlocks)) * saturate(strength));
}

// Stage 5: remaps the base volumetric noise against local coverage, so the
// visible body inside the envelope is formed by noise rather than by the
// descriptor geometry. The derivative with respect to the base field is
// 1 / (1 - lowerBound), strictly positive at every coverage value, so the
// storm interior is never noise-independent.
float stormCoreFillForStrength(float envelopeStrength) {
    float strength = max(saturate(envelopeStrength), 0.0001);
    float required = 1.0 / (strength * (1.0 - STORM_P05_EROSION_BITE)) - 1.0;
    return max(STORM_CORE_FILL, required + STORM_LIVE_STRENGTH_FILL_HEADROOM);
}

float stormCoreFillForCoverageAndStrength(float coverage, float envelopeStrength) {
    float normalizedCoverage = saturate(coverage);
    float strengthFill = stormCoreFillForStrength(envelopeStrength);
    float normalizedStrength = saturate(envelopeStrength);
    if (normalizedCoverage <= STORM_STRUCTURAL_CONTINUITY_COVERAGE
            || normalizedStrength <= STORM_STRUCTURAL_CONTINUITY_STRENGTH) {
        return strengthFill;
    }
    float retainedBody = STORM_STRUCTURAL_CONTINUITY_DENSITY + STORM_P05_EROSION_BITE;
    float continuityRequired = 1.0 / max(
        normalizedCoverage * (1.0 - retainedBody),
        0.0001
    ) - 1.0;
    float continuityFill = max(
        strengthFill,
        continuityRequired + STORM_LIVE_STRENGTH_FILL_HEADROOM
    );
    return mix(
        strengthFill,
        continuityFill,
        smoothstep(STORM_STRUCTURAL_CONTINUITY_COVERAGE, 0.90, normalizedCoverage)
            * smoothstep(STORM_STRUCTURAL_CONTINUITY_STRENGTH, 0.87, normalizedStrength)
    );
}

float stormCoverageLowerBound(
        float coverage,
        float envelopeStrength,
        bool embeddedConvectiveOverlap) {
    float fill = embeddedConvectiveOverlap
        ? stormCoreFillForCoverageAndStrength(coverage, envelopeStrength)
        : stormCoreFillForStrength(envelopeStrength);
    return mix(1.0, -fill,
        saturate(coverage));
}

float stormBody(
        float coverage,
        float envelopeStrength,
        float baseField,
        bool embeddedConvectiveOverlap) {
    float lowerBound = stormCoverageLowerBound(
        coverage, envelopeStrength, embeddedConvectiveOverlap);
    return saturate((baseField - lowerBound) / max(1.0 - lowerBound, 0.0001));
}

// Normalizes the high-biased Perlin-Worley carrier onto its measured range.
float stormBaseField(float carrier) {
    return smoothstep(STORM_CARRIER_P05, STORM_CARRIER_P95, carrier);
}

bool stormDescriptorIsValid(int descriptorIndex) {
    return descriptorIndex >= 0 && descriptorIndex < StormLobeCount
        && descriptorIndex < MAX_STORM_LOBES
        && stormDescriptorTexel(descriptorIndex, 3).w >= -0.5;
}

int stormDescriptorGroupSlot(int descriptorIndex) {
    // `packed` is a GLSL layout qualifier on the runtime compiler. Keep the
    // exact group/role decode, but avoid using that reserved identifier.
    int topology = int(floor(stormDescriptorTexel(descriptorIndex, 3).w + 0.5));
    return clamp(topology / (8 * 64 * 64), 0, MAX_STORM_GROUPS - 1);
}

int stormDescriptorMemberIndex(int descriptorIndex) {
    int topology = int(floor(stormDescriptorTexel(descriptorIndex, 3).w + 0.5));
    return (topology / 8) - (topology / (8 * 64)) * 64;
}

int stormDescriptorMemberCount(int descriptorIndex) {
    int topology = int(floor(stormDescriptorTexel(descriptorIndex, 3).w + 0.5));
    return ((topology / (8 * 64)) - (topology / (8 * 64 * 64)) * 64) + 1;
}

// T119 A/B reference only. This faithfully recreates the previous bounded
// group-range discovery: both endpoints scan the descriptor texture. It is
// never selected by default and has no density authority of its own.
int stormGroupFirstIndexLegacy(int groupSlot) {
    int first = StormLobeCount;
    for (int index = 0; index < MAX_STORM_LOBES; index++) {
        if (index >= StormLobeCount) {
            break;
        }
        if (stormDescriptorIsValid(index) && stormDescriptorGroupSlot(index) == groupSlot) {
            first = min(first, index);
        }
    }
    return first;
}

int stormGroupEndIndexLegacy(int groupSlot) {
    int endIndex = 0;
    for (int index = 0; index < MAX_STORM_LOBES; index++) {
        if (index >= StormLobeCount) {
            break;
        }
        if (stormDescriptorIsValid(index) && stormDescriptorGroupSlot(index) == groupSlot) {
            endIndex = max(endIndex, index + 1);
        }
    }
    return endIndex;
}

int stormGroupFirstIndex(int witnessIndex, int groupSlot) {
    if (StormTopologyMode == 1) {
        return stormGroupFirstIndexLegacy(groupSlot);
    }
    // T119: CPU build ordering is group-contiguous and the witness carries
    // its member index/count. Derive the exact range without a 64-slot scan.
    return max(0, witnessIndex - stormDescriptorMemberIndex(witnessIndex));
}

int stormGroupEndIndex(int witnessIndex, int groupSlot) {
    if (StormTopologyMode == 1) {
        return stormGroupEndIndexLegacy(groupSlot);
    }
    return min(StormLobeCount,
        stormGroupFirstIndex(witnessIndex, groupSlot)
            + stormDescriptorMemberCount(witnessIndex));
}

void directStormGroupField(
        vec3 p,
        int witnessIndex,
        int groupSlot,
        out float groupDistance,
        out float groupMinimumRadius,
        out float groupStrength,
        out float groupSoftness,
        out float groupHeight01,
        out int groupActiveRoleMask,
        out bool ownsGroup) {
    groupDistance = 1.0e9;
    groupMinimumRadius = 1000000.0;
    groupStrength = 0.0;
    groupSoftness = 0.0;
    groupHeight01 = 0.0;
    groupActiveRoleMask = 0;
    ownsGroup = false;
    bool started = false;
    float previousRadius = 0.0;
    int previousRole = -1;
    float heightWeight = 0.0;
    float heightAccum = 0.0;
    int firstIndex = stormGroupFirstIndex(witnessIndex, groupSlot);
    int endIndex = stormGroupEndIndex(witnessIndex, groupSlot);
    for (int descriptorIndex = firstIndex; descriptorIndex < MAX_STORM_LOBES; descriptorIndex++) {
        if (descriptorIndex >= endIndex) {
            break;
        }
        vec4 positionHeight = stormDescriptorTexel(descriptorIndex, 0);
        vec4 radiusRotation = stormDescriptorTexel(descriptorIndex, 1);
        vec4 shearMedia = stormDescriptorTexel(descriptorIndex, 2);
        vec4 lifecycleRole = stormDescriptorTexel(descriptorIndex, 3);
        if (lifecycleRole.w < -0.5) {
            continue;
        }
        int packedTopology = int(floor(lifecycleRole.w + 0.5));
        int lobeRole = packedTopology - (packedTopology / 8) * 8;
        vec2 ownershipCenter = positionHeight.xy + shearMedia.xy * 0.5;
        float extentX = length(vec2(
            radiusRotation.x * radiusRotation.w,
            radiusRotation.y * radiusRotation.z
        ));
        float extentZ = length(vec2(
            radiusRotation.x * radiusRotation.z,
            radiusRotation.y * radiusRotation.w
        ));
        vec2 ownershipRadii = max(vec2(extentX, extentZ) * 1.85, vec2(1.0));
        ownsGroup = ownsGroup || length((p.xz - ownershipCenter) / ownershipRadii) <= 1.0;

        float lobeRadius = min(radiusRotation.x, radiusRotation.y);
        // T122 OFF re-issues the two texel fetches the reuse form avoids and
        // then applies the identical FromData equation to the refetched
        // values.  Same texture, same descriptor index, same texel indices, so
        // the refetched values are bit-identical to the ones in registers -
        // this is the real pre-reuse work, not a copy of the ON result.
        float lobeSoftness = paT122Off()
            ? stormEdgeWidthBlocks(descriptorIndex, lobeRole)
            : stormEdgeWidthBlocksFromData(
                positionHeight, radiusRotation, shearMedia, lobeRole);
        // T122: the FromData softness form consumes the two descriptor texels
        // already fetched above.  Count those two precise wrapper fetches that
        // are avoided; this is a diagnostic-only counter, not an estimate
        // derived from descriptor evaluations.
        if (paWorkloadCaptureActive() && !paT122Off()) {
            paAvoidedDescriptorTextureFetches += 2;
        }
        groupMinimumRadius = min(groupMinimumRadius, lobeRadius);
        float verticalLowerBound = stormVerticalDistanceLowerBound(
            p, positionHeight, lobeRole
        );
        // A smooth minimum is exactly unchanged once the incoming distance is
        // more than its blend radius beyond the current union.  The global
        // maximum is used here instead of a guessed local value.  Requiring
        // the same bound to clear softness also proves this lobe cannot alter
        // the active role mask or height weighting.  Preserve previous role /
        // radius because the existing ordered union uses them to choose the
        // next lobe's exact blend radius.
        // T121 OFF evaluates every lobe the conservative bound would have
        // rejected, through the identical exact SDF and the identical ordered
        // smooth union.  Nothing else about the loop changes, so any image
        // difference between the arms is attributable to the rejection alone.
        if (started && !paT121Off() && verticalLowerBound > max(
                lobeSoftness + STORM_T121_SOFTNESS_MARGIN_BLOCKS,
                groupDistance + STORM_MAX_BLEND_BLOCKS)) {
            // T121: this is the sole conservative vertical-cap branch.  It
            // skips the exact descriptor SDF only after its lower bound proves
            // that the lobe cannot contribute to the ordered smooth union.
            if (paWorkloadCaptureActive()) {
                paConservativeDescriptorRejects++;
            }
            previousRadius = lobeRadius;
            previousRole = lobeRole;
            continue;
        }

        // T122: the exact SDF consumes the same four texels already resident
        // in registers rather than invoking its fetch wrapper again.
        if (paWorkloadCaptureActive() && !paT122Off()) {
            paAvoidedDescriptorTextureFetches += 4;
        }
        // T122 OFF refetches the three texels the exact SDF consumes and
        // passes the refetched values into the same equation, in the same
        // order, with the same groupSlot and role already decoded above.
        float lobeDistance = paT122Off()
            ? directStormLobeDistanceFromData(
                p,
                stormDescriptorTexel(descriptorIndex, 0),
                stormDescriptorTexel(descriptorIndex, 1),
                stormDescriptorTexel(descriptorIndex, 2),
                groupSlot,
                lobeRole)
            : directStormLobeDistanceFromData(
                p, positionHeight, radiusRotation, shearMedia, groupSlot, lobeRole
            );
        // Every lobe of the group contributes. A lobe is never dropped because
        // its local density evaluates to zero: that is exactly the region
        // where a smooth union needs its distance, and dropping it is what
        // made blends collapse into visible primitive intersections.
        float lobeStrength = saturate(shearMedia.z);
        if (lobeDistance <= lobeSoftness) {
            groupActiveRoleMask |= 1 << lobeRole;
        }
        float localHeight01 = clamp(
            (p.y - positionHeight.z) / max(positionHeight.w - positionHeight.z, 1.0),
            0.0,
            1.0
        );
        if (!started) {
            groupDistance = lobeDistance;
            groupStrength = lobeStrength;
            groupSoftness = lobeSoftness;
            started = true;
        } else {
            float blend = stormLobeBlendRadius(previousRadius, lobeRadius, previousRole, lobeRole);
            float mixFactor = stormBlendFactor(groupDistance, lobeDistance, blend);
            groupDistance = stormSmoothMinimum(groupDistance, lobeDistance, blend);
            groupStrength = mix(lobeStrength, groupStrength, mixFactor);
            groupSoftness = mix(lobeSoftness, groupSoftness, mixFactor);
        }
        previousRadius = lobeRadius;
        previousRole = lobeRole;
        float heightContribution = 1.0 - smoothstep(0.0, lobeSoftness, lobeDistance);
        heightWeight += heightContribution;
        heightAccum += localHeight01 * heightContribution;
    }
    if (!started) {
        groupDistance = 1.0e9;
    }
    if (heightWeight > 0.0) {
        groupHeight01 = heightAccum / heightWeight;
    }
}

// Candidate witnesses identify only bounded complete groups. Within each
// admitted group the exact descriptor distance field and both smooth-union
// levels stay authoritative; the candidate texture supplies no density values.
//
// Returns the bounded COVERAGE ENVELOPE, not a visible density. The visible
// storm body is formed from this envelope by the base-noise remap and the
// multi-scale erosion in cloudDensity().
float directStormShape(
        vec3 p,
        out bool ownsDescriptorGroup,
        out float dominantHeight01,
        out float envelopeStrength,
        out int activeRoleMask) {
    // Compact bit mask instead of a bool[MAX_STORM_GROUPS]. The array form
    // cost an allocation and an 8-iteration clear loop on every density
    // sample, and indexed writes into a local array defeat register
    // allocation on several drivers. Group slots are 0..7, so one int holds
    // the whole visitation set.
    int groupVisited = 0;
    float stormDistance = 1.0e9;
    float stormStrength = 0.0;
    float stormSoftness = 0.0;
    bool started = false;
    float previousGroupRadius = 0.0;
    float nearestGroupDistance = 1.0e9;
    ownsDescriptorGroup = false;
    dominantHeight01 = 0.0;
    envelopeStrength = 0.0;
    activeRoleMask = 0;
    vec4 candidates = stormCandidatesAt(p.xz);
    for (int rank = 0; rank < STORM_CANDIDATES_PER_TILE; rank++) {
        int witnessIndex = decodeStormCandidate(candidates, rank);
        if (!stormDescriptorIsValid(witnessIndex)) {
            continue;
        }
        int groupSlot = stormDescriptorGroupSlot(witnessIndex);
        int groupBit = 1 << groupSlot;
        if ((groupVisited & groupBit) != 0) {
            continue;
        }
        groupVisited |= groupBit;
        float groupDistance;
        float groupMinimumRadius;
        float groupStrength;
        float groupSoftness;
        float groupHeight01;
        int groupActiveRoleMask;
        bool ownsGroup;
        directStormGroupField(
            p, witnessIndex, groupSlot,
            groupDistance, groupMinimumRadius, groupStrength, groupSoftness,
            groupHeight01, groupActiveRoleMask, ownsGroup
        );
        activeRoleMask |= groupActiveRoleMask;
        ownsDescriptorGroup = ownsDescriptorGroup || ownsGroup;
        if (groupDistance > 1.0e8) {
            continue;
        }
        if (groupDistance < nearestGroupDistance) {
            nearestGroupDistance = groupDistance;
            dominantHeight01 = groupHeight01;
        }
        if (!started) {
            stormDistance = groupDistance;
            stormStrength = groupStrength;
            stormSoftness = groupSoftness;
            started = true;
        } else {
            float blend = stormGroupBlendRadius(previousGroupRadius, groupMinimumRadius);
            float mixFactor = stormBlendFactor(stormDistance, groupDistance, blend);
            stormDistance = stormSmoothMinimum(stormDistance, groupDistance, blend);
            stormStrength = mix(groupStrength, stormStrength, mixFactor);
            stormSoftness = mix(groupSoftness, stormSoftness, mixFactor);
        }
        previousGroupRadius = groupMinimumRadius;
    }
    if (!started) {
        return 0.0;
    }
    envelopeStrength = stormStrength;
    return stormEnvelopeFromDistance(stormDistance, stormSoftness, stormStrength);
}

// Storm base and detail noise reductions, shared by the raymarch body and by
// every consumer that needs a visible storm value outside it.
float stormBaseCarrierAt(vec3 samplePos, float mipBias, out float detailFbm) {
    vec4 baseNoise = texture(
        BaseNoiseSampler,
        stormBaseNoiseDomain(samplePos),
        mipBias
    );
    float lowFbm = baseNoise.g * 0.625 + baseNoise.b * 0.25 + baseNoise.a * 0.125;
    vec3 detailPos = detailNoiseDomain(samplePos);
    detailPos += (baseNoise.gbr - 0.5) * 0.18;
    vec4 detail = texture(DetailNoiseSampler, detailPos, mipBias);
    detailFbm = detail.r * 0.625 + detail.g * 0.25 + detail.b * 0.125;
    return saturate(remap(baseNoise.r, -(1.0 - lowFbm), 1.0, 0.0, 1.0));
}

// Final descriptor-owned storm density: the complete ordered composition.
// Rain support and attachment, camera density and whiteout all read this and
// never the coverage envelope, so what those systems believe about the storm
// is what the frame actually shows.
float directStormFinalDensity(
        vec3 p,
        float mipBias,
        out bool ownsDescriptorGroup,
        out float dominantHeight01) {
    float envelopeStrength;
    int activeRoleMask;
    float coverage = directStormShape(
        p, ownsDescriptorGroup, dominantHeight01, envelopeStrength, activeRoleMask);
    if (coverage <= 0.0) {
        return 0.0;
    }
    vec3 samplePos = p;
    samplePos.xz -= MaterialOffset;
    float detailFbm;
    float carrier = stormBaseCarrierAt(samplePos, mipBias, detailFbm);
    bool embeddedConvectiveOverlap = (activeRoleMask & 7) == 7;
    float body = stormBody(
        coverage, envelopeStrength, stormBaseField(carrier), embeddedConvectiveOverlap);
    return max(body - (1.0 - detailFbm) * STORM_EROSION, 0.0);
}

bool stormGroupSegmentMayIntersect(
        vec3 segmentStart,
        vec3 segmentEnd,
        int witnessIndex,
        int groupSlot) {
    int firstIndex = stormGroupFirstIndex(witnessIndex, groupSlot);
    int endIndex = stormGroupEndIndex(witnessIndex, groupSlot);
    vec3 segment = segmentEnd - segmentStart;
    float segmentLengthSquared = max(dot(segment, segment), 0.0001);
    for (int index = firstIndex; index < MAX_STORM_LOBES; index++) {
        if (index >= endIndex) {
            break;
        }
        vec4 positionHeight = stormDescriptorTexel(index, 0);
        vec4 radiusRotation = stormDescriptorTexel(index, 1);
        vec4 shearMedia = stormDescriptorTexel(index, 2);
        vec3 center = vec3(
            positionHeight.x + shearMedia.x * 0.5,
            (positionHeight.z + positionHeight.w) * 0.5,
            positionHeight.y + shearMedia.y * 0.5
        );
        float horizontalRadius = max(radiusRotation.x, radiusRotation.y) * 1.24
            + length(shearMedia.xy) + 2.0;
        float halfHeight = (positionHeight.w - positionHeight.z) * 0.5 + 2.0;
        float boundRadius = length(vec2(horizontalRadius, halfHeight));
        float along = clamp(dot(center - segmentStart, segment) / segmentLengthSquared, 0.0, 1.0);
        if (distance(segmentStart + segment * along, center) <= boundRadius) {
            return true;
        }
    }
    return false;
}

bool directStormSegmentMayIntersect(vec3 segmentStart, vec3 segmentEnd) {
    int groupVisited = 0;
    for (int sample = 0; sample < 3; sample++) {
        float fraction = sample == 0 ? 0.0 : (sample == 1 ? 0.5 : 1.0);
        vec4 candidates = stormCandidatesAt(mix(segmentStart.xz, segmentEnd.xz, fraction));
        for (int rank = 0; rank < STORM_CANDIDATES_PER_TILE; rank++) {
            int witnessIndex = decodeStormCandidate(candidates, rank);
            if (!stormDescriptorIsValid(witnessIndex)) {
                continue;
            }
            int groupSlot = stormDescriptorGroupSlot(witnessIndex);
            int groupBit = 1 << groupSlot;
            if ((groupVisited & groupBit) != 0) {
                continue;
            }
            groupVisited |= groupBit;
            if (stormGroupSegmentMayIntersect(
                    segmentStart, segmentEnd, witnessIndex, groupSlot)) {
                return true;
            }
        }
    }
    return false;
}

int decodePuffCandidate(vec4 packedCandidates, int candidateRank) {
    float packedValue = candidateRank < 2
        ? packedCandidates.r
        : (candidateRank < 4
            ? packedCandidates.g
            : (candidateRank < 6 ? packedCandidates.b : packedCandidates.a));
    int encoded = int(floor(packedValue + 0.5));
    int pairRank = candidateRank - (candidateRank / 2) * 2;
    int digit = pairRank == 0
        ? encoded - (encoded / PUFF_PACK_BASE) * PUFF_PACK_BASE
        : encoded / PUFF_PACK_BASE;
    return digit - 1;
}

vec4 puffCandidatesAt(vec2 worldXZ) {
    if (PuffLobeCount <= 0) {
        return vec4(0.0);
    }
    vec2 uv = (worldXZ - WeatherOrigin) / WeatherExtent;
    if (uv.x < 0.0 || uv.x >= 1.0 || uv.y < 0.0 || uv.y >= 1.0) {
        return vec4(0.0);
    }
    ivec2 size = textureSize(PuffCandidateMapSampler, 0);
    ivec2 coord = clamp(
        ivec2(floor(uv * vec2(size))),
        ivec2(0),
        size - ivec2(1)
    );
    return texelFetch(PuffCandidateMapSampler, coord, 0);
}

// Evaluates one canonical PUFF member once and exposes both the historical
// analytic surface and a normalized depth inside its meteorological envelope.
// The production carrier is built only after all members have been unioned;
// this prevents each descriptor from remaining visible as a final ellipsoid.
// x=analytic mass, y=envelope depth, z=weighted envelope depth,
// w=local descriptor height.
vec4 directPuffLobeSample(vec3 p, int candidateIndex, out int puffTier) {
    vec4 posRadius = PuffPosRadius[candidateIndex];
    vec4 shape = PuffShape[candidateIndex];
    vec4 media = PuffMedia[candidateIndex];
    float span = max(shape.z - shape.y, 1.0);
    float h = (p.y - shape.y) / span;
    puffTier = int(clamp(floor(media.w + 0.00001), 0.0, 3.0));
    if (h <= 0.0 || h >= 1.0) {
        return vec4(0.0);
    }

    vec2 delta = p.xz - posRadius.xy;
    float cosO = cos(-shape.x);
    float sinO = sin(-shape.x);
    vec2 local = vec2(
        delta.x * cosO - delta.y * sinO,
        delta.x * sinO + delta.y * cosO
    );
    vec2 radii = max(posRadius.zw, vec2(1.0));
    float radial = length(local / radii);
    if (radial >= 1.05) {
        return vec4(0.0);
    }

    // A structured cumulus member is one radial/vertical implicit volume.
    // Runtime tier-isolation proved that evaluating a horizontal profile and
    // two independent base/top planes exposed 7.4..9.6 block discs at the
    // roots of MIDDLE/CROWN members. Their roots are already buried inside
    // parent material, so close only those roots instead of moving the layout.
    // Member identity, unlike world position, does not drift with the field.
    // Give adjacent lobes slightly different equator heights so their union
    // cannot expose one synchronized horizontal shelf from every azimuth.
    // Descriptor slots are sorted by camera distance on the CPU. Basing the
    // profile on candidateIndex therefore changed a stationary lobe whenever
    // the camera reordered the list. PuffMedia.z is the persisted lobe seed.
    float lobePhase = fract(media.z * 0.754877666 + 0.17320508);
    // PuffMedia.w packs the persisted layout tier in its integer part and the
    // former per-lobe verticalDevelopment value in one quarter of its
    // fractional part. Legacy/unversioned groups use tier 3 and retain the old
    // generic profile instead of being reinterpreted from their member index.
    if (PuffTierFilter >= 0 && puffTier != PuffTierFilter) {
        return vec4(0.0);
    }
    float peakHeight;
    float rootRadius;
    float equatorRadius;
    float upperPower;
    if (puffTier == 0) {
        // BASE members still keep a coherent lower deck, but the carrier below
        // decides their visible edge instead of an artificially narrow foot.
        peakHeight = mix(0.32, 0.38, lobePhase);
        rootRadius = mix(0.70, 0.76, lobePhase);
        equatorRadius = mix(0.92, 0.98, lobePhase);
        upperPower = mix(0.95, 1.15, lobePhase);
    } else if (puffTier == 1) {
        peakHeight = mix(0.38, 0.44, lobePhase);
        rootRadius = 0.0;
        equatorRadius = mix(0.94, 1.00, lobePhase);
        upperPower = mix(1.30, 1.55, lobePhase);
    } else if (puffTier == 2) {
        peakHeight = mix(0.43, 0.50, lobePhase);
        rootRadius = 0.0;
        equatorRadius = mix(0.90, 0.96, lobePhase);
        upperPower = mix(1.70, 2.00, lobePhase);
    } else {
        peakHeight = mix(0.33, 0.43, lobePhase);
        rootRadius = mix(0.38, 0.46, lobePhase);
        equatorRadius = 1.0;
        upperPower = 1.35;
    }
    // Analytic geometry must not breathe when the governor changes quality.
    // Segment/AABB refinement now protects thin support, so fixed world-space
    // fades are both stable and adequately sampled at every quality level.
    float desiredBaseFeather = puffTier == 0 ? 5.0 : 4.0;
    float desiredTopFeather = puffTier == 2 ? 3.5 : 4.0;
    float featherScale = min(
        1.0,
        span * 0.70 / max(0.001, desiredBaseFeather + desiredTopFeather)
    );
    float baseFeatherH = desiredBaseFeather * featherScale / span;
    float lifecycle = saturate(media.y);
    float lifecycleEnvelope = lifecycle < 0.5
        ? mix(0.30, 1.0, lifecycle * 2.0)
        : mix(1.0, 0.30, (lifecycle - 0.5) * 2.0);
    float materialMass = mix(0.62, 1.0, saturate(shape.w));
    float envelopeDepth;
    if (puffTier <= 2) {
        // BASE retains one softly feathered meteorological condensation plane.
        // Upper tiers start and end at a point in this implicit coordinate, so
        // their support radius grows as O(h) and axial density as O(h^2).
        // There is no non-zero root disc and no independent planar clamp.
        float rootRatio = puffTier == 0
            ? rootRadius / max(equatorRadius, 0.001)
            : 0.0;
        float verticalAtBase = -sqrt(max(
            0.0,
            1.0 - rootRatio * rootRatio
        ));
        float verticalCoordinate;
        if (h <= peakHeight) {
            verticalCoordinate = mix(
                verticalAtBase,
                0.0,
                smoothstep(0.0, peakHeight, h)
            );
        } else {
            // The tier-specific upperPower was previously assigned above but
            // ignored here, which gave BASE, MIDDLE and CROWN the same
            // normalized spherical cap. Preserve the shared peak/top
            // endpoints while keeping BASE close to the proven spherical
            // cap and making MIDDLE/CROWN progressively fuller. Using half
            // this exponent made BASE/MIDDLE exponents smaller than one and
            // removed the exact upper support needed by mediocris crowns.
            float upper = saturate(
                (h - peakHeight) / max(1.0 - peakHeight, 0.001)
            );
            float upperProgress = smoothstep(0.0, 1.0, upper);
            verticalCoordinate = pow(
                upperProgress,
                upperPower
            );
        }
        float implicitRadius = length(vec2(
            radial / max(equatorRadius, 0.001),
            verticalCoordinate
        ));
        envelopeDepth = max(1.0 - implicitRadius, 0.0);
        if (puffTier == 0) {
            envelopeDepth *= smoothstep(0.0, baseFeatherH, h);
        }
    } else {
        // Unversioned persisted groups have no authored tier. Preserve their
        // legacy profile until a save migration can prove a safe role mapping.
        float radiusAtHeight;
        if (h <= peakHeight) {
            radiusAtHeight = mix(
                rootRadius,
                equatorRadius,
                smoothstep(0.0, peakHeight, h)
            );
        } else {
            float upper = saturate((h - peakHeight) / (1.0 - peakHeight));
            radiusAtHeight = equatorRadius
                * sqrt(max(0.0, 1.0 - pow(upper, upperPower)));
        }
        float radialFeather = mix(0.08, 0.14, saturate(media.x));
        float outerRadius = max(radiusAtHeight, 0.001);
        float horizontal = 1.0 - smoothstep(
            max(0.0, outerRadius - radialFeather),
            outerRadius,
            radial
        );
        float topFeatherH = desiredTopFeather * featherScale / span;
        float baseFade = smoothstep(0.0, baseFeatherH, h);
        float topFade = 1.0 - smoothstep(1.0 - topFeatherH, 1.0, h);
        envelopeDepth = horizontal * baseFade * topFade;
    }
    float analyticMass = envelopeDepth * lifecycleEnvelope * materialMass;
    return vec4(
        analyticMass,
        envelopeDepth,
        envelopeDepth * lifecycleEnvelope * materialMass,
        h
    );
}

float directPuffLobeShape(vec3 p, int candidateIndex) {
    int puffTier = 3;
    return directPuffLobeSample(p, candidateIndex, puffTier).x;
}

void accumulatePuffShape(inout vec2 accumulated, float candidate) {
    // Retain the two strongest members. A probabilistic sum grows with every
    // overlapping descriptor and turned seven valid lobes into one monolith.
    // Top-two accumulation is order independent and cannot saturate merely
    // because another weak member exists elsewhere in the hierarchy.
    float previousMaximum = accumulated.x;
    accumulated.x = max(previousMaximum, candidate);
    accumulated.y = max(accumulated.y, min(previousMaximum, candidate));
}

float resolvePuffShape(vec2 accumulated) {
    // One lobe remains exact. The second strongest can bridge a seam by at
    // most 0.0625, while no support is created outside either input.
    return accumulated.x + 0.25 * accumulated.y * (1.0 - accumulated.x);
}

// Exhaustive descriptor evaluation is intentionally expensive and is selected
// only by the diagnostic stage. It proves whether packing/indexing changes the
// analytic support without introducing WeatherMap occupancy.
float directPuffShapeAll(vec3 p) {
    vec2 accumulated = vec2(0.0);
    for (int candidateIndex = 0;
            candidateIndex < MAX_PUFF_LOBES;
            candidateIndex++) {
        if (candidateIndex >= PuffLobeCount) {
            break;
        }
        accumulatePuffShape(accumulated, directPuffLobeShape(p, candidateIndex));
    }
    return resolvePuffShape(accumulated);
}

// Indexed analytic diagnostic: the candidate map limits each sample to nearby
// members while preserving the historical analytic response for A/B proof.
float directPuffShape(
        vec3 p,
        out bool indexedTile,
        out float dominantHeight01) {
    vec4 candidateTexel = puffCandidatesAt(p.xz);
    indexedTile = false;
    dominantHeight01 = 0.0;
    vec2 accumulated = vec2(0.0);
    float strongestShape = 0.0;
    for (int candidateRank = 0;
            candidateRank < PUFF_CANDIDATES_PER_TILE;
            candidateRank++) {
        int candidateIndex = decodePuffCandidate(candidateTexel, candidateRank);
        if (candidateIndex < 0
                || candidateIndex >= PuffLobeCount
                || candidateIndex >= MAX_PUFF_LOBES) {
            continue;
        }
        indexedTile = true;
        float candidateShape = directPuffLobeShape(p, candidateIndex);
        if (candidateShape > strongestShape) {
            strongestShape = candidateShape;
            vec4 candidateBounds = PuffShape[candidateIndex];
            dominantHeight01 = saturate(
                (p.y - candidateBounds.y)
                    / max(candidateBounds.z - candidateBounds.y, 1.0)
            );
        }
        accumulatePuffShape(accumulated, candidateShape);
    }
    return resolvePuffShape(accumulated);
}

float resolvePuffContinuousField(
        vec2 envelopeAccumulated,
        vec2 weightedAccumulated,
        vec2 baseRootAccumulated,
        float carrierSignal,
        float billowSignal,
        float billowStrength) {
    float envelope = resolvePuffShape(envelopeAccumulated);
    if (envelope <= 0.0) {
        return 0.0;
    }
    float weighted = resolvePuffShape(weightedAccumulated);
    // The second component is now the second-strongest contributor rather
    // than a probabilistic union. It is the direct, count-independent measure
    // of a real lobe junction.
    float overlap = envelopeAccumulated.y;
    float baseRootOverlap = baseRootAccumulated.y;
    float materialFactor = saturate(weighted / max(envelope, 0.0001));

    // BaseNoise.G already contains three coherent Worley octaves. Its baked
    // p05/p95 range is 0.2844/0.6775, so this remap uses the measured signal
    // instead of an arbitrary threshold. One world-stable carrier is applied
    // after the union; descriptor IDs and time never enter its domain.
    float carrier = smoothstep(0.28, 0.68, carrierSignal);
    float exposedIso = mix(0.34, 0.08, carrier);
    float coreProtection = smoothstep(0.38, 0.55, envelope);
    float junctionProtection = smoothstep(0.015, 0.075, overlap);
    float baseJunctionProtection = smoothstep(
        0.004,
        0.016,
        baseRootOverlap
    );
    float protection = max(
        coreProtection,
        max(junctionProtection, baseJunctionProtection)
    );
    // BaseNoise.B is already present in the same RGBA fetch as the macro G
    // carrier. Its approximately half-scale cells add medium cauliflower
    // relief without another texture lookup. The modulation owns only the
    // exposed shell: core, real lobe junctions and BASE-root corridors all
    // converge to the same 0.012 protected isovalue. A zero strength remains
    // bit-for-bit equivalent to the former G-only carrier.
    float mediumBillow = smoothstep(0.28, 0.68, billowSignal);
    float billowIso = clamp(
        exposedIso + (0.5 - mediumBillow) * 0.12 * billowStrength,
        0.04,
        0.40
    );
    float surfaceIso = mix(billowIso, 0.012, protection);
    float continuousShape = max(
        (envelope - surfaceIso) / max(1.0 - surfaceIso, 0.001),
        0.0
    );
    return continuousShape * materialFactor;
}

void accumulatePuffContinuousSample(
        inout vec2 envelopeAccumulated,
        inout vec2 weightedAccumulated,
        inout vec2 baseRootAccumulated,
        vec4 lobeSample,
        int puffTier) {
    accumulatePuffShape(envelopeAccumulated, lobeSample.y);
    accumulatePuffShape(weightedAccumulated, lobeSample.z);
    float baseRootCandidate = puffTier == 0
        ? lobeSample.y * (1.0 - smoothstep(0.34, 0.55, lobeSample.w))
        : 0.0;
    accumulatePuffShape(baseRootAccumulated, baseRootCandidate);
}

// Exhaustive carrier diagnostic. This deliberately bypasses the candidate
// texture just like ANALYTIC_ALL so packing can be compared independently.
float directPuffContinuousShapeAll(
        vec3 p,
        float carrierSignal,
        float billowSignal,
        float billowStrength) {
    vec2 envelopeAccumulated = vec2(0.0);
    vec2 weightedAccumulated = vec2(0.0);
    vec2 baseRootAccumulated = vec2(0.0);
    for (int candidateIndex = 0;
            candidateIndex < MAX_PUFF_LOBES;
            candidateIndex++) {
        if (candidateIndex >= PuffLobeCount) {
            break;
        }
        int puffTier = 3;
        vec4 lobeSample = directPuffLobeSample(p, candidateIndex, puffTier);
        accumulatePuffContinuousSample(
            envelopeAccumulated,
            weightedAccumulated,
            baseRootAccumulated,
            lobeSample,
            puffTier
        );
    }
    return resolvePuffContinuousField(
        envelopeAccumulated,
        weightedAccumulated,
        baseRootAccumulated,
        carrierSignal,
        billowSignal,
        billowStrength
    );
}

// Exhaustive raw-envelope control. It retains the exact descriptor profiles
// and order-independent union, but removes the carrier isovalue, all protection
// thresholds, lifecycle/material weighting and every noise texture lookup.
// Comparing this against the constant-carrier cuts identifies whether banding
// first appears in descriptor geometry or in the carrier transfer function.
float directPuffEnvelopeShapeAll(vec3 p) {
    vec2 envelopeAccumulated = vec2(0.0);
    for (int candidateIndex = 0;
            candidateIndex < MAX_PUFF_LOBES;
            candidateIndex++) {
        if (candidateIndex >= PuffLobeCount) {
            break;
        }
        int puffTier = 3;
        vec4 lobeSample = directPuffLobeSample(p, candidateIndex, puffTier);
        accumulatePuffShape(envelopeAccumulated, lobeSample.y);
    }
    return resolvePuffShape(envelopeAccumulated);
}

// Production carrier: candidate selection remains indexed, but the selected
// descriptors contribute to one continuous field before the surface is cut.
float directPuffContinuousShape(
        vec3 p,
        float carrierSignal,
        float billowSignal,
        float billowStrength,
        out bool indexedTile,
        out float dominantHeight01) {
    vec4 candidateTexel = puffCandidatesAt(p.xz);
    indexedTile = false;
    dominantHeight01 = 0.0;
    vec2 envelopeAccumulated = vec2(0.0);
    vec2 weightedAccumulated = vec2(0.0);
    vec2 baseRootAccumulated = vec2(0.0);
    float strongestEnvelope = 0.0;
    for (int candidateRank = 0;
            candidateRank < PUFF_CANDIDATES_PER_TILE;
            candidateRank++) {
        int candidateIndex = decodePuffCandidate(candidateTexel, candidateRank);
        if (candidateIndex < 0
                || candidateIndex >= PuffLobeCount
                || candidateIndex >= MAX_PUFF_LOBES) {
            continue;
        }
        indexedTile = true;
        int puffTier = 3;
        vec4 lobeSample = directPuffLobeSample(p, candidateIndex, puffTier);
        if (lobeSample.z > strongestEnvelope) {
            strongestEnvelope = lobeSample.z;
            dominantHeight01 = lobeSample.w;
        }
        accumulatePuffContinuousSample(
            envelopeAccumulated,
            weightedAccumulated,
            baseRootAccumulated,
            lobeSample,
            puffTier
        );
    }
    return resolvePuffContinuousField(
        envelopeAccumulated,
        weightedAccumulated,
        baseRootAccumulated,
        carrierSignal,
        billowSignal,
        billowStrength
    );
}

bool puffBoundsClipAxis(
        float origin,
        float delta,
        float minimumBound,
        float maximumBound,
        inout float segmentMinimum,
        inout float segmentMaximum) {
    if (abs(delta) < 0.00001) {
        return origin >= minimumBound && origin <= maximumBound;
    }
    float first = (minimumBound - origin) / delta;
    float second = (maximumBound - origin) / delta;
    segmentMinimum = max(segmentMinimum, min(first, second));
    segmentMaximum = min(segmentMaximum, max(first, second));
    return segmentMaximum >= segmentMinimum;
}

// Point-sampled coarse search can jump completely over a compact lobe. Test
// the whole proposed segment against each descriptor's conservative AABB and
// switch to fine traversal before advancing through a possible support volume.
// This preserves long clear-air strides without tying silhouette visibility to
// the screen-row-dependent ray/slab span.
bool directPuffSegmentMayIntersect(vec3 segmentStart, vec3 segmentEnd) {
    vec3 delta = segmentEnd - segmentStart;
    for (int candidateIndex = 0;
            candidateIndex < MAX_PUFF_LOBES;
            candidateIndex++) {
        if (candidateIndex >= PuffLobeCount) {
            break;
        }
        vec4 posRadius = PuffPosRadius[candidateIndex];
        vec4 shape = PuffShape[candidateIndex];
        int puffTier = int(clamp(
            floor(PuffMedia[candidateIndex].w + 0.00001),
            0.0,
            3.0
        ));
        if (PuffTierFilter >= 0 && puffTier != PuffTierFilter) {
            continue;
        }
        // A rotated ellipse can extend by its major radius on either world
        // axis. The former X=major/Z=minor box missed valid support whenever
        // the major axis approached Z. max(major, minor) is a cheap, strictly
        // conservative bound without per-step trigonometry.
        float horizontalPadding = max(
            max(posRadius.z, posRadius.w) * 1.05,
            1.0
        );
        vec3 padding = vec3(horizontalPadding, 0.0, horizontalPadding);
        vec3 minimumBounds = vec3(
            posRadius.x - padding.x,
            shape.y,
            posRadius.y - padding.z
        );
        vec3 maximumBounds = vec3(
            posRadius.x + padding.x,
            shape.z,
            posRadius.y + padding.z
        );
        float segmentMinimum = 0.0;
        float segmentMaximum = 1.0;
        bool intersects = puffBoundsClipAxis(
            segmentStart.x, delta.x,
            minimumBounds.x, maximumBounds.x,
            segmentMinimum, segmentMaximum
        );
        intersects = intersects && puffBoundsClipAxis(
            segmentStart.y, delta.y,
            minimumBounds.y, maximumBounds.y,
            segmentMinimum, segmentMaximum
        );
        intersects = intersects && puffBoundsClipAxis(
            segmentStart.z, delta.z,
            minimumBounds.z, maximumBounds.z,
            segmentMinimum, segmentMaximum
        );
        if (intersects) {
            return true;
        }
    }
    return false;
}

// Recovers the dominant descriptor height used to distinguish cloud body from
// precipitation during the primary march. It deliberately uses the analytic
// envelope already in registers/textures instead of paying a second 3D carrier
// lookup for every accepted material sample.
bool dominantDirectPuffHeightAt(vec3 p, out float localHeight01) {
    vec4 cumulusStageSupports = sampleCumulusStageSupports(p.xz);
    vec4 cumulusStageBases = sampleCumulusStageBases(p.xz);
    vec4 cumulusStageTops = sampleCumulusStageTops(p.xz);
    float cumulusRolePresence = max(
        max(cumulusStageSupports.r, cumulusStageSupports.g),
        max(cumulusStageSupports.b, cumulusStageSupports.a)
    );
    float cumulusHeightPresence = max(
        max(max(cumulusStageBases.r, cumulusStageBases.g),
            max(cumulusStageBases.b, cumulusStageBases.a)),
        max(max(cumulusStageTops.r, cumulusStageTops.g),
            max(cumulusStageTops.b, cumulusStageTops.a))
    );
    if (cumulusRolePresence > 0.004
            && cumulusHeightPresence > 0.0001) {
        localHeight01 = 0.0;
        return false;
    }
    vec4 candidateTexel = puffCandidatesAt(p.xz);
    float bestShape = 0.0;
    float dominantHeight = 0.0;
    for (int candidateRank = 0;
            candidateRank < PUFF_CANDIDATES_PER_TILE;
            candidateRank++) {
        int candidateIndex = decodePuffCandidate(candidateTexel, candidateRank);
        if (candidateIndex < 0
                || candidateIndex >= PuffLobeCount
                || candidateIndex >= MAX_PUFF_LOBES) {
            continue;
        }
        int puffTier = 3;
        vec4 lobeSample = directPuffLobeSample(p, candidateIndex, puffTier);
        if (lobeSample.x > bestShape) {
            bestShape = lobeSample.x;
            dominantHeight = lobeSample.w;
        }
    }
    if (bestShape <= 0.0) {
        localHeight01 = 0.0;
        return false;
    }
    localHeight01 = dominantHeight;
    return true;
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

int cloudMorphologyCode(vec4 morphology) {
    int encodedCode = int(clamp(
        floor(morphology.r * MORPHOLOGY_CATEGORY_SCALE + 0.5),
        0.0,
        MORPHOLOGY_CATEGORY_SCALE
    ));
    return max(encodedCode - 1, 0);
}

int cloudProfileId(vec4 morphology) {
    return cloudMorphologyCode(morphology) / 8;
}

int cloudEnvelopeRole(vec4 morphology) {
    int code = cloudMorphologyCode(morphology);
    return code - (code / 8) * 8;
}

float familyMacroShape(
        int profileId,
        int envelopeRole,
        float h01,
        float coverage,
        float footprintCoverage,
        float verticalDevelopment,
        float condensate,
        float precipitation,
        float baseCarrier,
        float lowFbm,
        float directionalCarrier) {
    float h = saturate(h01);
    if (profileId == 1) {
        // A stratus deck remains continuous, but its top and optical mass are
        // shaped by broad horizontal condensate bands instead of saturated
        // coverage. Keep the dense core so primary rays terminate promptly;
        // reducing this mass made a transparent full-screen veil and increased
        // GPU cost by forcing many more primary steps.
        float horizontalDetail = smoothstep(0.18, 0.70,
            lowFbm * 0.58 + baseCarrier * 0.24
                + directionalCarrier * 0.12 + condensate * 0.06);
        float topEdge = mix(0.62, 0.90, horizontalDetail);
        // Horizontal detail may thin the material, but it must not punch an
        // optically transparent hole through an otherwise continuous deck.
        // Keep a real stratiform condensate floor; the coherent weather-map
        // base/top surface still owns the large-scale silhouette.
        return verticalBand(h, 0.020, topEdge)
            * mix(0.32, 1.0, horizontalDetail);
    }
    if (profileId == 2) {
        float cells = smoothstep(0.18, 0.72,
            baseCarrier * 0.55 + lowFbm * 0.35 + footprintCoverage * 0.10);
        float cellularTop = mix(
            0.54,
            mix(0.72, 0.84, verticalDevelopment),
            cells
        );
        return verticalBand(h, 0.025, cellularTop)
            * mix(0.26, 1.0, cells);
    }
    if (profileId == 3) {
        // The weather splat already carries the fused PUFF silhouette and its
        // role-curved local height interval. Let that meteorological support
        // own the shape; low-frequency 3-D noise only modulates condensate.
        // The previous height-dependent noise threshold turned isolated high
        // noise columns into narrow needles above an otherwise broad cumulus.
        float topEdge = mix(0.82, 0.94, verticalDevelopment);
        // The splat now preserves the dominant local interval plus only
        // strongly-supported sibling extrema. Applying another height-driven
        // footprint threshold here turned that already-shaped envelope into a
        // single cone/teardrop. Keep only a height-independent boundary gate;
        // the local base/top map owns the actual cauliflower crown.
        float supportBody = smoothstep(0.035, 0.20, footprintCoverage);
        float materialSignal = baseCarrier * 0.46
            + lowFbm * 0.28
            + condensate * 0.14
            + coverage * 0.12;
        float material = mix(
            0.66,
            1.0,
            smoothstep(0.18, 0.78, materialSignal)
        );
        return verticalBand(h, 0.055, topEdge)
            * supportBody
            * material;
    }
    if (profileId == 0) {
        float topStart = mix(0.70, 0.88, verticalDevelopment);
        float domeThreshold = mix(0.16, 0.66, h * h);
        float billowSignal = baseCarrier * 0.72 + lowFbm * 0.28 + coverage * 0.06;
        float billow = smoothstep(domeThreshold, 0.86, billowSignal);
        return verticalBand(h, 0.020, topStart) * billow;
    }
    if (profileId == 4) {
        if (envelopeRole == 2) {
            // BASE is a broad, low, dark shelf. It must not grow a second
            // miniature tower/anvil inside its already-low local interval.
            float baseTexture = smoothstep(0.18, 0.70,
                lowFbm * 0.46 + baseCarrier * 0.28
                    + directionalCarrier * 0.16 + coverage * 0.10);
            float shelfTop = mix(0.54, 0.84, baseTexture);
            return verticalBand(h, 0.018, shelfTop)
                * mix(0.12, 1.0, baseTexture);
        }
        if (envelopeRole == 5) {
            // ANVIL is a thin wind-shaped sheet outside the connecting core.
            float sheetTexture = smoothstep(0.20, 0.72,
                lowFbm * 0.44 + directionalCarrier * 0.30
                    + baseCarrier * 0.18 + coverage * 0.08);
            return verticalBand(h, mix(0.06, 0.11, sheetTexture),
                    mix(0.72, 0.91, sheetTexture))
                * mix(0.08, 1.0, sheetTexture)
                * mix(0.68, 1.0, verticalDevelopment);
        }
        // Cumulonimbus is one continuous lower mass/updraft/anvil system.  A
        // height-dependent threshold tapers the updraft without creating the
        // empty middle left by the old independent lower/core masks.
        float horizontalTaper = smoothstep(
            mix(0.04, 0.42, smoothstep(0.08, 0.96, h)),
            mix(0.34, 0.72, smoothstep(0.08, 0.96, h)),
            footprintCoverage);
        float updraftTexture = smoothstep(0.28, 0.76,
            baseCarrier * 0.58 + lowFbm * 0.20 + footprintCoverage * 0.24);
        float updraft = horizontalTaper * mix(0.26, 1.0, updraftTexture);
        float lowerMass = (1.0 - smoothstep(0.48, 0.72, h))
            * smoothstep(0.06, 0.48,
                lowFbm * 0.30 + baseCarrier * 0.16 + coverage * 0.66);
        float bridge = verticalBand(h, 0.018, 0.90)
            * horizontalTaper
            * smoothstep(0.22, 0.66,
                baseCarrier * 0.48 + lowFbm * 0.18 + footprintCoverage * 0.20);
        float tower = verticalBand(h, 0.015, 0.97)
            * max(max(updraft, lowerMass), bridge * 0.78);
        float anvilBand = smoothstep(0.58, 0.72, h)
            * (1.0 - smoothstep(0.94, 1.0, h));
        float anvil = anvilBand
            * smoothstep(0.10, 0.56,
                lowFbm * 0.42 + footprintCoverage * 0.24 + directionalCarrier * 0.34)
            * mix(0.62, 1.0, verticalDevelopment);
        return max(tower, anvil);
    }
    if (profileId == 5) {
        // Nimbostratus keeps a connected rain deck while allowing large,
        // world-stable condensate variations and a less planar upper edge.
        float sheet = smoothstep(0.16, 0.68,
            lowFbm * 0.52 + baseCarrier * 0.22
                + directionalCarrier * 0.10 + precipitation * 0.16);
        return verticalBand(h, 0.018, mix(0.62, 0.92, sheet))
            * mix(0.42, 1.0, sheet);
    }
    if (profileId == 6) {
        float filament = smoothstep(0.16, 0.62,
            directionalCarrier * 0.70 + lowFbm * 0.22 + coverage * 0.18);
        return verticalBand(h, 0.10, 0.76) * filament * 0.82;
    }

    if (envelopeRole == 2) {
        float rotatingShelf = smoothstep(0.18, 0.70,
            directionalCarrier * 0.34 + lowFbm * 0.34
                + baseCarrier * 0.22 + coverage * 0.10);
        return verticalBand(h, 0.012, mix(0.50, 0.82, rotatingShelf))
            * mix(0.30, 1.0, rotatingShelf);
    }
    if (envelopeRole == 5) {
        float sweptAnvil = smoothstep(0.20, 0.72,
            directionalCarrier * 0.34 + lowFbm * 0.36
                + baseCarrier * 0.20 + coverage * 0.10);
        return verticalBand(h, mix(0.05, 0.11, sweptAnvil),
                mix(0.74, 0.93, sweptAnvil))
            * mix(0.16, 1.0, sweptAnvil)
            * mix(0.76, 1.08, verticalDevelopment);
    }

    // Supercells retain a broad asymmetric rotating base, but only the
    // concentrated updraft reaches the upper levels. Overlap the base,
    // updraft and anvil bands so no mushroom cap can detach from the tower.
    float rotatingTaper = smoothstep(
        mix(0.05, 0.46, smoothstep(0.05, 0.97, h)),
        mix(0.36, 0.76, smoothstep(0.05, 0.97, h)),
        footprintCoverage);
    float updraftTexture = smoothstep(0.30, 0.78,
        baseCarrier * 0.54 + lowFbm * 0.18
            + footprintCoverage * 0.22 + directionalCarrier * 0.10);
    float updraft = rotatingTaper * mix(0.24, 1.0, updraftTexture);
    float rotatingBase = verticalBand(h, 0.010, 0.48)
        * smoothstep(0.08, 0.56,
            directionalCarrier * 0.42 + lowFbm * 0.16 + coverage * 0.58);
    float midBridge = verticalBand(h, 0.08, 0.88)
        * rotatingTaper
        * smoothstep(0.24, 0.68,
            baseCarrier * 0.46 + footprintCoverage * 0.22
                + directionalCarrier * 0.16);
    float tower = verticalBand(h, 0.012, 0.985)
        * max(max(updraft, rotatingBase), midBridge * 0.76);
    float anvilBand = smoothstep(0.56, 0.70, h)
        * (1.0 - smoothstep(0.96, 1.0, h));
    float anvil = anvilBand * smoothstep(0.10, 0.56,
        lowFbm * 0.40 + footprintCoverage * 0.24 + directionalCarrier * 0.38)
        * mix(0.78, 1.16, verticalDevelopment);
    return max(tower, anvil);
}

float cumulusStructureShape(
        vec4 encodedSupports,
        vec4 encodedBases,
        vec4 encodedTops,
        float sampleY,
        float slabSpan,
        float condensate,
        float baseCarrier,
        float lowFbm) {
    vec4 rawSupport = max(encodedSupports, vec4(0.0));
    vec4 base01 = encodedBases / max(rawSupport, vec4(0.001));
    vec4 top01 = encodedTops / max(rawSupport, vec4(0.001));
    vec4 stageBaseY = vec4(SlabBaseY) + clamp(base01, 0.0, 1.0) * slabSpan;
    vec4 stageTopY = vec4(SlabBaseY) + clamp(top01, 0.0, 1.0) * slabSpan;
    stageTopY = max(stageTopY, stageBaseY + vec4(1.0));

    vec4 support = smoothstep(
        vec4(0.012),
        vec4(0.36),
        clamp(rawSupport * CoverageMul, 0.0, 1.0)
    );

    vec4 h = (vec4(sampleY) - stageBaseY) / max(stageTopY - stageBaseY, vec4(1.0));
    float macroSignal = baseCarrier * 0.48 + lowFbm * 0.34 + condensate * 0.18;
    float billowTexture = smoothstep(0.18, 0.78, macroSignal);

    // BASE owns the coherent condensation floor, but its curved endpoint map
    // still produces several broad lower lobes instead of a rectangular slab.
    float baseMaterial = mix(0.78, 1.0, billowTexture);
    float baseMass = verticalBand(h.r, 0.028, 0.94)
        * support.r * baseMaterial;

    // Higher stages remain real overlapping domes. Noise only modulates their
    // condensate; it cannot erase the analytic morphology or create columns.
    float coreMaterial = mix(0.72, 1.0, billowTexture);
    float towerMaterial = mix(0.69, 1.0,
        smoothstep(0.16, 0.76, baseCarrier * 0.54 + lowFbm * 0.34 + condensate * 0.12));
    float crownMaterial = mix(0.66, 1.0,
        smoothstep(0.14, 0.74, baseCarrier * 0.58 + lowFbm * 0.32 + condensate * 0.10));
    float coreMass = verticalBand(h.g, 0.035, 0.94)
        * support.g * coreMaterial;
    float towerMass = verticalBand(h.b, 0.045, 0.93)
        * support.b * towerMaterial;
    float crownMass = verticalBand(h.a, 0.055, 0.91)
        * support.a * crownMaterial;

    // Bounded probabilistic union preserves each stage boundary while joining
    // genuine overlaps. It never invents density where every stage is empty.
    float lowerUnion = baseMass + coreMass - baseMass * coreMass;
    float upperUnion = towerMass + crownMass - towerMass * crownMass;
    return saturate(lowerUnion + upperUnion - lowerUnion * upperUnion);
}

float stormStructureShape(
        int profileId,
        vec4 encodedRoles,
        vec4 encodedHeights,
        vec4 encodedTowers,
        float sampleY,
        float globalBaseY,
        float globalTopY,
        float slabSpan,
        float verticalDevelopment,
        float condensate,
        float baseCarrier,
        float lowFbm,
        float directionalCarrier) {
    // Decode endpoints from premultiplied half-float values. Supports below the
    // caller's validity threshold never enter this path, avoiding division noise
    // at empty map fringes.
    float rawBaseSupport = encodedRoles.r;
    float rawCoreSupport = encodedRoles.b;
    float rawAnvilSupport = encodedHeights.g;
    float rawTowerSupport = encodedTowers.r;
    float baseTop01 = encodedRoles.g / max(rawBaseSupport, 0.001);
    float coreBase01 = encodedRoles.a / max(rawCoreSupport, 0.001);
    float coreTop01 = encodedHeights.r / max(rawCoreSupport, 0.001);
    float anvilBase01 = encodedHeights.b / max(rawAnvilSupport, 0.001);
    float anvilTop01 = encodedHeights.a / max(rawAnvilSupport, 0.001);
    float towerBase01 = encodedTowers.g / max(rawTowerSupport, 0.001);
    float towerTop01 = encodedTowers.b / max(rawTowerSupport, 0.001);

    float baseTopY = clamp(
        SlabBaseY + clamp(baseTop01, 0.0, 1.0) * slabSpan,
        globalBaseY + 1.0,
        globalTopY
    );
    float coreBaseY = clamp(
        SlabBaseY + clamp(coreBase01, 0.0, 1.0) * slabSpan,
        globalBaseY,
        globalTopY - 1.0
    );
    float coreTopY = clamp(
        SlabBaseY + clamp(coreTop01, 0.0, 1.0) * slabSpan,
        coreBaseY + 1.0,
        globalTopY
    );
    float anvilBaseY = clamp(
        SlabBaseY + clamp(anvilBase01, 0.0, 1.0) * slabSpan,
        globalBaseY,
        globalTopY - 1.0
    );
    float anvilTopY = clamp(
        SlabBaseY + clamp(anvilTop01, 0.0, 1.0) * slabSpan,
        anvilBaseY + 1.0,
        globalTopY
    );
    float towerBaseY = clamp(
        SlabBaseY + clamp(towerBase01, 0.0, 1.0) * slabSpan,
        globalBaseY,
        globalTopY - 1.0
    );
    float towerTopY = clamp(
        SlabBaseY + clamp(towerTop01, 0.0, 1.0) * slabSpan,
        towerBaseY + 1.0,
        globalTopY
    );

    // The exact maps retain overlapping BASE, CORE, TOWER and ANVIL supports.
    // Values are footprint-weighted just like weather coverage, so remap the
    // normal range without turning the field into a binary mask.
    float baseSupport = smoothstep(0.015, 0.38,
        saturate(rawBaseSupport * CoverageMul));
    float coreSupport = smoothstep(0.015, 0.38,
        saturate(rawCoreSupport * CoverageMul));
    float anvilSupport = smoothstep(0.015, 0.38,
        saturate(rawAnvilSupport * CoverageMul));
    float towerSupport = smoothstep(0.015, 0.38,
        saturate(rawTowerSupport * CoverageMul));

    // Cohesion is allowed only where adjacent meteorological layers overlap.
    // A floor based on one role's support redraws that role's analytic ellipse;
    // these small bridges instead join BASE->convection->ANVIL at real roots.
    float baseCoreOverlap = smoothstep(
        0.08,
        0.52,
        min(baseSupport, coreSupport)
    );
    float coreAnvilOverlap = smoothstep(
        0.06,
        0.46,
        min(coreSupport, anvilSupport)
    );

    // Exact role endpoints can be disjoint even when their horizontal supports
    // overlap. Join only those intersections, leaving genuinely separate role
    // footprints at their original heights. Eight/ten world units provide a
    // short optical bridge without inflating the whole severe envelope.
    float connectedCoreBaseY = mix(
        coreBaseY,
        clamp(
            min(coreBaseY, baseTopY - 8.0),
            globalBaseY,
            coreTopY - 1.0
        ),
        baseCoreOverlap
    );
    float connectedAnvilBaseY = mix(
        anvilBaseY,
        clamp(
            min(anvilBaseY, coreTopY - 10.0),
            globalBaseY,
            anvilTopY - 1.0
        ),
        coreAnvilOverlap
    );
    float baseH = (sampleY - globalBaseY) / max(baseTopY - globalBaseY, 1.0);
    float coreH = (sampleY - connectedCoreBaseY)
        / max(coreTopY - connectedCoreBaseY, 1.0);
    float anvilH = (sampleY - connectedAnvilBaseY)
        / max(anvilTopY - connectedAnvilBaseY, 1.0);
    float towerH = (sampleY - towerBaseY)
        / max(towerTopY - towerBaseY, 1.0);

    float baseSignal = lowFbm * 0.46 + baseCarrier * 0.30
        + directionalCarrier * (profileId == 7 ? 0.18 : 0.16)
        + condensate * 0.08;
    float baseTexture = smoothstep(0.32, 0.76, baseSignal);
    float baseFill = baseCoreOverlap * 0.06 * mix(0.70, 1.0, lowFbm);
    float baseMaterial = baseTexture + (1.0 - baseTexture) * baseFill;
    float baseBand = verticalBand(baseH, 0.020, 0.92);
    float baseMass = baseBand * baseSupport * baseMaterial;

    float convectiveSignal = baseCarrier * 0.44 + lowFbm * 0.40
        + directionalCarrier * (profileId == 7 ? 0.16 : 0.12);
    float convectiveTexture = smoothstep(0.32, 0.78, convectiveSignal);
    float coreLowerBridge = baseCoreOverlap
        * (1.0 - smoothstep(0.42, 0.70, coreH));
    float coreUpperBridge = coreAnvilOverlap * smoothstep(0.52, 0.78, coreH);
    float coreFill = max(coreLowerBridge, coreUpperBridge)
        * 0.10 * mix(0.72, 1.0, lowFbm);
    float coreMaterial = convectiveTexture
        + (1.0 - convectiveTexture) * coreFill;
    float coreBand = verticalBand(
        coreH,
        0.016,
        0.96
    );
    float coreTaperH = smoothstep(0.08, 0.96, saturate(coreH));
    float coreTaper = smoothstep(
        mix(0.05, 0.42, coreTaperH),
        mix(0.38, 0.78, coreTaperH),
        coreSupport
    );
    float coreMass = coreBand * coreSupport * coreMaterial * coreTaper;

    // TOWER uses the same material response as CORE for this first structural
    // A/B. Only its independently preserved vertical interval differs, so any
    // new lobes can be attributed to removing the CORE/TOWER argmax collision.
    float towerBand = verticalBand(
        towerH,
        0.016,
        0.96
    );
    float towerTaperH = smoothstep(0.08, 0.96, saturate(towerH));
    float towerTaper = paStormTowerTaper(
        rawTowerSupport,
        CoverageMul,
        towerSupport,
        towerTaperH
    );
    float towerMass = towerBand * towerSupport * coreMaterial * towerTaper;

    // BASE and lower convection can share an XZ footprint while their
    // texture-driven materials both erode the short vertical overlap. Preserve
    // that intersection with a narrow root only; never fill the full BASE or
    // tower footprint. This removes a detached shelf without redrawing either
    // analytic support as a solid stamp.
    float rootOverlap = baseCoreOverlap;
    float rootBodyBaseY = connectedCoreBaseY;
    float rootBottomY = min(baseTopY, rootBodyBaseY) - 6.0;
    float rootTopY = max(baseTopY, rootBodyBaseY) + 10.0;
    float rootH = (sampleY - rootBottomY) / max(rootTopY - rootBottomY, 1.0);
    float rootTexture = smoothstep(
        0.24,
        0.72,
        lowFbm * 0.54 + baseCarrier * 0.34 + directionalCarrier * 0.12
    );
    float rootMass = verticalBand(rootH, 0.025, 0.96)
        * rootOverlap
        * mix(0.22, 0.68, rootTexture);

    float anvilSignal = directionalCarrier * 0.34 + lowFbm * 0.42
        + baseCarrier * 0.24;
    float anvilTexture = smoothstep(0.22, 0.68, anvilSignal);
    float anvilFill = coreAnvilOverlap * 0.18 * mix(0.72, 1.0, lowFbm);
    float anvilMaterial = anvilTexture + (1.0 - anvilTexture) * anvilFill;
    float anvilBand = verticalBand(
        anvilH,
        0.035,
        mix(0.82, 0.94, anvilTexture)
    );
    float anvilMass = anvilBand * anvilSupport
        * anvilMaterial
        * mix(0.72, profileId == 7 ? 1.14 : 1.04, verticalDevelopment);

    float existingStormMass = max(
        max(baseMass, coreMass),
        max(rootMass, anvilMass)
    );
    return paUnionIndependentStormTower(existingStormMass, towerMass);
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

// The underside is a local BASE-lobe result, not a group-wide minimum plane.
// This helper also supplies the interior height at which the same visible
// union is sampled for rain support.
bool directStormLocalBaseAt(vec2 worldXZ, out float attachY, out float supportY) {
    float weightedBase = 0.0;
    float weightedSupportY = 0.0;
    float totalWeight = 0.0;
    for (int descriptorIndex = 0; descriptorIndex < MAX_STORM_LOBES; descriptorIndex++) {
        if (descriptorIndex >= StormLobeCount) {
            break;
        }
        vec4 lifecycleRole = stormDescriptorTexel(descriptorIndex, 3);
        if (lifecycleRole.w < -0.5) {
            continue;
        }
        int packedTopology = int(floor(lifecycleRole.w + 0.5));
        int role = packedTopology - (packedTopology / 8) * 8;
        if (role != 0) {
            continue;
        }
        vec4 positionHeight = stormDescriptorTexel(descriptorIndex, 0);
        float localSupportY = mix(positionHeight.z, positionHeight.w, 0.22);
        // Distance-based support, so a column just outside a BASE lobe still
        // resolves smoothly instead of falling through to the global slab.
        int supportGroupSlot;
        int supportRole;
        float supportDistance = directStormLobeDistance(
            vec3(worldXZ.x, localSupportY, worldXZ.y),
            descriptorIndex,
            supportGroupSlot,
            supportRole
        );
        float supportSoftness = stormEdgeWidthBlocks(descriptorIndex, role);
        float support = saturate(
            1.0 - smoothstep(-supportSoftness, supportSoftness, supportDistance)
        ) * saturate(stormDescriptorTexel(descriptorIndex, 2).z);
        if (support <= 0.0) {
            continue;
        }
        weightedBase += positionHeight.z * support;
        weightedSupportY += localSupportY * support;
        totalWeight += support;
    }
    if (totalWeight <= 0.0001) {
        attachY = SlabBaseY;
        supportY = SlabBaseY;
        return false;
    }
    attachY = weightedBase / totalWeight;
    supportY = weightedSupportY / totalWeight;
    return true;
}

// Rain samples the exact visible storm union. Candidate/index textures are
// absent from the equation and precipitation density remains a later, separate
// material contribution below the locally derived attachment height.
float directStormRainSupportAt(
        vec2 worldXZ,
        out float attachY,
        out bool ownsDescriptorGroup) {
    float supportY;
    bool hasLocalBase = directStormLocalBaseAt(worldXZ, attachY, supportY);
    float ignoredHeight01;
    // The coverage envelope is not a visible body. Rain must attach to the
    // density the noise stages actually produced, or it can hang under a
    // region the erosion emptied.
    float bodySupport = directStormFinalDensity(
        vec3(worldXZ.x, supportY, worldXZ.y),
        0.0,
        ownsDescriptorGroup,
        ignoredHeight01
    );
    return hasLocalBase ? bodySupport : 0.0;
}

float localRainSupportAt(
        vec2 worldXZ,
        out float attachY,
        out float precipitation,
        out float familyStrength,
        out vec4 weather,
        out vec4 morphology) {
    weather = sampleWeather(worldXZ);
    morphology = sampleMorphology(worldXZ);
    precipitation = saturate(morphology.a);
    float weatherCoverage = smoothstep(0.012, 0.42, saturate(weather.r * CoverageMul));
    float slabSpan = max(SlabTopY - SlabBaseY, 1.0);
    float weatherBaseY = SlabBaseY + weather.g * slabSpan;
    int profileId = cloudProfileId(morphology);
    familyStrength = (profileId == 4 || profileId == 5 || profileId == 7)
        ? 1.0
        : 0.44;
    attachY = weatherBaseY;
    float stormBaseY = weatherBaseY;
    bool directStormOwned = false;
    float directSupport = directStormRainSupportAt(
        worldXZ, stormBaseY, directStormOwned
    );
    // Descriptor-owned storm splats are intentionally absent from the raster
    // map. Their separate precipitation intensity therefore comes from the
    // frame maximum while the exact body union alone defines shaft support.
    if (directStormOwned) {
        precipitation = MaxPrecipitation;
        familyStrength = 1.0;
    } else if (precipitation <= 0.02) {
        return 0.0;
    }
    float localSupport = directStormOwned ? directSupport : weatherCoverage;
    attachY = directStormOwned ? stormBaseY : weatherBaseY;
    if ((!hasMorphologyCategory(morphology.r) && directSupport <= 0.01)
            || localSupport <= 0.01) {
        return 0.0;
    }
    return localSupport * smoothstep(0.02, 0.12, precipitation);
}

bool rainSegmentMayContribute(vec3 segmentStart, vec3 segmentEnd) {
    if (MaxPrecipitation <= 0.02) {
        return false;
    }
    const float FIRST_SAMPLE = 0.2113248654;
    const float SECOND_SAMPLE = 0.7886751346;
    for (int sampleIndex = 0; sampleIndex < 2; sampleIndex++) {
        float along = sampleIndex == 0 ? FIRST_SAMPLE : SECOND_SAMPLE;
        vec3 p = mix(segmentStart, segmentEnd, along);
        float attachY;
        float precipitation;
        float familyStrength;
        vec4 weather;
        vec4 morphology;
        float support = localRainSupportAt(
            p.xz, attachY, precipitation, familyStrength, weather, morphology
        );
        if (support > 0.01 && p.y < attachY && p.y > attachY - 184.0) {
            return true;
        }
    }
    return false;
}

float rainShaftDensityAt(vec3 p, float mipBias) {
    if (MaxPrecipitation <= 0.02) {
        return 0.0;
    }

    float localBaseY;
    float localPrecipitation;
    float localFamilyStrength;
    vec4 localWeather;
    vec4 localMorphology;
    float preliminarySupport = localRainSupportAt(
        p.xz,
        localBaseY,
        localPrecipitation,
        localFamilyStrength,
        localWeather,
        localMorphology
    );
    if (preliminarySupport <= 0.01 || p.y >= localBaseY) {
        return 0.0;
    }

    vec2 windDir = cloudWindDirection();
    float localFallDistance = localBaseY - p.y;
    vec2 sourceXZ = p.xz - windDir * localFallDistance * 0.14;
    float baseY;
    float precipitation;
    float familyStrength;
    vec4 weather;
    vec4 morphology;
    float localSupport = localRainSupportAt(
        sourceXZ, baseY, precipitation, familyStrength, weather, morphology
    );
    if (localSupport <= 0.01 || precipitation <= 0.02 || p.y >= baseY) {
        return 0.0;
    }

    float condensate = saturate(
        localSupport * 0.36 + weather.a * 0.28 + precipitation * 0.24 + morphology.b * 0.12
    );

    float fallDistance = baseY - p.y;
    float maxDepth = mix(48.0, 180.0,
        saturate(precipitation * 0.70 + condensate * 0.30));
    float depth01 = fallDistance / max(maxDepth, 1.0);
    if (depth01 >= 1.0) {
        return 0.0;
    }

    float reach = clamp(0.34 + condensate * 0.38 + precipitation * 0.36, 0.34, 1.0);
    float tail = 1.0 - smoothstep(max(reach - 0.20, 0.05), reach, depth01);
    vec2 crossWind = vec2(-windDir.y, windDir.x);
    vec3 rainDomain = vec3(
        dot(sourceXZ, crossWind) * 0.012,
        p.y * 0.0014 - WorldTime * 0.0015,
        dot(sourceXZ, windDir) * 0.0035
    );
    vec4 rainNoise = texture(DetailNoiseSampler, rainDomain, mipBias + 0.75);
    // R is the coherent low-frequency Worley channel; G/B only roughen real
    // columns.  The previous weighted average plus a 2% floor gave every
    // precipitating texel non-zero density, producing a horizon-wide curtain.
    float streaks = smoothstep(0.48, 0.72, rainNoise.r)
        * mix(0.42, 1.0,
            smoothstep(0.32, 0.68, rainNoise.g * 0.70 + rainNoise.b * 0.30));
    return localSupport
        * precipitation
        * familyStrength
        * tail
        * mix(0.06, 0.14, precipitation)
        * streaks;
}

float rainShaftDensityOverSegment(vec3 segmentStart, vec3 segmentEnd, float mipBias) {
    // Fixed Gauss points integrate the broad shaft on coarse body steps. They
    // are world/ray anchored and never depend on FrameIndex or blue-noise
    // jitter, eliminating dotted screen-space curtains and shimmer.
    const float FIRST_SAMPLE = 0.2113248654;
    const float SECOND_SAMPLE = 0.7886751346;
    float first = rainShaftDensityAt(mix(segmentStart, segmentEnd, FIRST_SAMPLE), mipBias);
    float second = rainShaftDensityAt(mix(segmentStart, segmentEnd, SECOND_SAMPLE), mipBias);
    return (first + second) * 0.5;
}

float cloudDensity(
        vec3 p,
        float mipBias,
        bool useDetail,
        bool nearCamera,
        bool includePrecipitation) {
    if (PuffDensityStage == 1) {
        // Pure descriptor geometry: no WeatherMap and no candidate texture.
        // Empty descriptors render empty instead of silently falling through.
        float diagnosticPuff = PuffLobeCount > 0 ? directPuffShapeAll(p) : 0.0;
        return max(diagnosticPuff, 0.0) * 0.88 * DensityMul;
    }
    if (PuffDensityStage == 2) {
        // Adds only candidate packing/indexing to the exhaustive analytic cut.
        bool diagnosticIndexedTile = false;
        float diagnosticDominantHeight = 0.0;
        float diagnosticPuff = PuffLobeCount > 0
            ? directPuffShape(
                p,
                diagnosticIndexedTile,
                diagnosticDominantHeight
            )
            : 0.0;
        return max(diagnosticPuff, 0.0) * 0.88 * DensityMul;
    }
    if (PuffDensityStage == 11) {
        float diagnosticEnvelope = PuffLobeCount > 0
            ? directPuffEnvelopeShapeAll(p)
            : 0.0;
        return max(diagnosticEnvelope, 0.0) * 0.88 * DensityMul;
    }
    if ((PuffDensityStage >= 7 && PuffDensityStage <= 10)
            || PuffDensityStage == 12) {
        // Pure exhaustive carrier cuts. Stage 7 uses the production noise;
        // stages 8/10 use saturated low/high controls and stage 9 uses the
        // measured median.
        // These constants isolate carrier-domain variation from descriptor
        // envelope, ray traversal and alpha integration without changing any
        // other part of the render path.
        float diagnosticCarrier = 0.0;
        float diagnosticBillow = 0.5;
        float diagnosticBillowStrength = 0.0;
        if (PuffDensityStage == 7 || PuffDensityStage == 12) {
            vec3 diagnosticSamplePos = p;
            diagnosticSamplePos.xz -= MaterialOffset;
            vec4 diagnosticNoise = texture(
                BaseNoiseSampler,
                baseNoiseDomain(diagnosticSamplePos, 0.0032),
                mipBias
            );
            diagnosticCarrier = PuffDensityStage == 12
                ? 0.4775
                : diagnosticNoise.g;
            diagnosticBillow = diagnosticNoise.b;
            diagnosticBillowStrength = PuffDensityStage == 12 ? 1.0 : 0.0;
        } else if (PuffDensityStage == 9) {
            diagnosticCarrier = 0.4775;
        } else if (PuffDensityStage == 10) {
            diagnosticCarrier = 1.0;
        }
        float diagnosticPuff = PuffLobeCount > 0
            ? directPuffContinuousShapeAll(
                p,
                diagnosticCarrier,
                diagnosticBillow,
                diagnosticBillowStrength
            )
            : 0.0;
        return max(diagnosticPuff, 0.0) * 0.88 * DensityMul;
    }
    vec4 weather = sampleWeather(p.xz);
    vec4 morphology = sampleMorphology(p.xz);
    // Weather-map coverage already includes the cloudlet density. Its normal
    // spawned-field range is roughly 0.08..0.35; treating 0.92 as the full
    // point erased those clouds before the raymarch ever saw them.
    float coverageSignal = saturate(weather.r * CoverageMul);
    float energy = weather.a;
    int profileId = cloudProfileId(morphology);
    int envelopeRole = cloudEnvelopeRole(morphology);
    // Diagnostic only: after reserving encoded zero for empty, this cut must
    // render nothing wherever WeatherMap and MorphologyMap ownership agree.
    // Keeping it available provides a direct GPU regression check for future
    // changes to either map's footprint rules.
    bool morphologyCategoryValid = hasMorphologyCategory(morphology.r);
    bool morphologyCategoryEmpty = !morphologyCategoryValid;
    bool morphologyGapDiagnostic = PuffDensityStage == 5;
    if (morphologyGapDiagnostic && !morphologyCategoryEmpty) {
        return 0.0;
    }
    bool sheetProfile = profileId == 1 || profileId == 2 || profileId == 5;
    bool stormProfile = profileId == 4 || profileId == 7;
    float coverage = profileId == 6
        ? smoothstep(0.002, 0.20, saturate(coverageSignal * 1.8))
        : smoothstep(0.012, 0.42, coverageSignal);
    float verticalDevelopment = morphology.g;
    float materialDarkness = morphology.b;
    float precipitation = morphology.a;
    float condensate = saturate(
        coverage * 0.36 + energy * 0.28 + precipitation * 0.24 + materialDarkness * 0.12
    );
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

    float slabSpan = max(SlabTopY - SlabBaseY, 1.0);
    float baseY = SlabBaseY + weather.g * slabSpan - baseLower * 34.0;
    float topY = SlabBaseY + weather.b * slabSpan;
    float layerSpan = max(topY - baseY, 2.0);
    float h01 = (p.y - baseY) / layerSpan;

    bool directStormOwned = false;
    float directStormHeight01 = h01;
    float directStormStrength = 0.0;
    int directStormActiveRoleMask = 0;
    // Bounded COVERAGE ENVELOPE, not a density. The visible storm body is
    // formed from it below by the base-noise remap and multi-scale erosion.
    float directStormCoverage = StormLobeCount > 0
        ? directStormShape(
            p,
            directStormOwned,
            directStormHeight01,
            directStormStrength,
            directStormActiveRoleMask
        )
        : 0.0;
    // Only a valid uploaded descriptor group may suppress the legacy family
    // fallback. A global descriptor count, raster storm category, or candidate
    // tile is never authority for unrelated or incomplete groups.
    bool directStormAvailable = directStormOwned;
    stormProfile = stormProfile || directStormAvailable;

    bool precipitationCandidate = includePrecipitation
        && MaxPrecipitation > 0.02
        && p.y < SlabBaseY + 48.0;
    if (coverage <= 0.008 && funnel <= 0.001 && !precipitationCandidate
            && directStormCoverage <= 0.001) {
        return 0.0;
    }

    // Cumulus maps remain family-specific. Severe geometry comes directly
    // from the bounded descriptors and candidate grid above.
    bool cumulusProfile = profileId == 3;
    vec4 cumulusStageSupports = cumulusProfile
        ? sampleCumulusStageSupports(p.xz)
        : vec4(0.0);
    vec4 cumulusStageBases = cumulusProfile
        ? sampleCumulusStageBases(p.xz)
        : vec4(0.0);
    vec4 cumulusStageTops = cumulusProfile
        ? sampleCumulusStageTops(p.xz)
        : vec4(0.0);

    float cumulusRolePresence = max(
        max(cumulusStageSupports.r, cumulusStageSupports.g),
        max(cumulusStageSupports.b, cumulusStageSupports.a)
    );
    float cumulusHeightPresence = max(
        max(max(cumulusStageBases.r, cumulusStageBases.g),
            max(cumulusStageBases.b, cumulusStageBases.a)),
        max(max(cumulusStageTops.r, cumulusStageTops.g),
            max(cumulusStageTops.b, cumulusStageTops.a))
    );
    bool useCumulusStructure = cumulusProfile
        && cumulusRolePresence > 0.004
        && cumulusHeightPresence > 0.0001;
    bool directPuffAvailable = cumulusProfile
        && !useCumulusStructure
        && PuffLobeCount > 0
        && PuffShapeMode != 0;

    float cloud = 0.0;
    // Direct descriptors already carry exact per-lobe Y bounds. The fused
    // 8-bit WeatherMap interval is a material envelope, not a second geometry
    // clip; applying it here removed valid analytic crowns and roots.
    bool insideShapeBounds = directStormAvailable
        ? true
        : useCumulusStructure || directPuffAvailable
            ? p.y > SlabBaseY - 2.0 && p.y < SlabTopY + 2.0
            : h01 > -0.02 && h01 < 1.02;
    if (insideShapeBounds
            && (coverage > 0.008 || directStormCoverage > 0.001)
            && (morphologyCategoryValid || directStormAvailable)) {
        float anvil = stormProfile
            ? smoothstep(0.62, 0.94, saturate(h01))
                * energy * (0.20 + verticalDevelopment * 0.42)
            : 0.0;
        float coverageMod = saturate(coverage * (1.02 + anvil * 0.30));

        // The offset is integrated from UUID-matched presented positions on
        // the CPU. It therefore freezes with the visible envelope and cannot
        // retroactively jump when the instantaneous wind changes.
        vec3 samplePos = p;
        samplePos.xz -= MaterialOffset;

        // Cloudlets are tens to low hundreds of blocks wide. The old 0.0016
        // scale sampled an almost constant value across an entire cloudlet,
        // producing one smooth oval instead of separate billows.
        float baseNoiseScale = directPuffAvailable ? 0.0032 : 0.0052;
        if (directStormAvailable) {
            baseNoiseScale = STORM_BASE_NOISE_SCALE;
        } else if (sheetProfile) {
            baseNoiseScale = profileId == 2 ? 0.0042 : 0.0031;
        } else if (profileId == 6) {
            baseNoiseScale = 0.0085;
        } else if (stormProfile) {
            // Severe cloudlets are much larger than fair-weather puffs. A
            // slightly denser domain gives the same lookup several distinct
            // medium-scale billows across the tower instead of one smooth lobe.
            baseNoiseScale = 0.0052;
        }
        vec4 baseNoise = texture(BaseNoiseSampler, baseNoiseDomain(samplePos, baseNoiseScale), mipBias);
        float lowFbm = baseNoise.g * 0.625 + baseNoise.b * 0.25 + baseNoise.a * 0.125;
        float baseCarrier = saturate(remap(baseNoise.r, -(1.0 - lowFbm), 1.0, 0.0, 1.0));
        vec2 windDir = cloudWindDirection();
        vec2 crossWind = vec2(-windDir.y, windDir.x);
        float directionalCarrier = 0.5 + 0.5 * sin(
            dot(samplePos.xz, crossWind) * (profileId == 6 ? 0.042 : 0.018)
                + dot(samplePos.xz, windDir) * 0.004
                + lowFbm * 5.1
        );
        bool directPuffIndexed = false;
        float directPuffHeight01 = h01;
        float directPuff = directPuffAvailable
            ? directPuffContinuousShape(
                p,
                baseNoise.g,
                baseNoise.b,
                0.0,
                directPuffIndexed,
                directPuffHeight01
            )
            : 0.0;
        // Mode 2 deliberately keeps the analytic source global: a tile with no
        // candidates contributes zero rather than switching representations at
        // a 16-block grid boundary. Mode 1 preserves the old path for A/B.
        bool useDirectPuff = directPuffAvailable
            && (PuffShapeMode == 2 || directPuffIndexed);
        if (PuffDensityStage == 6 && !useDirectPuff) {
            return 0.0;
        }
        float macroShape = useCumulusStructure
            ? cumulusStructureShape(
                cumulusStageSupports,
                cumulusStageBases,
                cumulusStageTops,
                p.y,
                slabSpan,
                condensate,
                baseCarrier,
                lowFbm
            )
            : directStormAvailable
            ? directStormCoverage
            : useDirectPuff
            ? directPuff
            : familyMacroShape(
                profileId,
                envelopeRole,
                h01,
                coverageMod,
                weather.r,
                verticalDevelopment,
                condensate,
                precipitation,
                baseCarrier,
                lowFbm,
                directionalCarrier
            );
        // Role supports already contain footprint-weighted density. Avoid
        // squaring them through the globally unioned weather coverage while
        // retaining a soft envelope gate at map fringes.
        // A selected descriptor group owns severe-storm density as well as its
        // boundary. Multiplying it by the old per-member weather splats made
        // BASE/CORE/TOWER/ANVIL primitives visible inside the continuous
        // volume even though the outer analytic envelope was coherent.
        float envelopeCoverage = directStormAvailable
            ? 1.0
            : (useCumulusStructure || useDirectPuff)
                ? mix(0.72, 1.0, coverageMod)
                : coverageMod;
        if (!directStormAvailable && profileId == 1) {
            // A unioned sheet map encodes support as coverage, not as a second
            // density multiplier. The square-root response keeps the interior
            // continuous while the explicit smooth gate preserves a soft,
            // deterministic field boundary.
            envelopeCoverage = sqrt(max(coverageMod, 0.0))
                * smoothstep(0.008, 0.12, coverageMod);
        }
        cloud = macroShape * envelopeCoverage;

        if (directStormAvailable) {
            // STAGE 5 - base volumetric noise remapping.
            //
            // Until this point `cloud` is the descriptor coverage envelope. It
            // is deliberately near-uniform through the storm interior; that is
            // what an envelope is. The visible body is produced here, by
            // remapping the base noise field against local coverage, so what
            // the player sees is noise shaped by the descriptors rather than
            // the descriptors themselves. Treating the envelope as the body is
            // what produced a smooth balloon that passed every
            // artifact-absence check.
            bool embeddedConvectiveOverlap = (directStormActiveRoleMask & 7) == 7;
            cloud = stormBody(
                cloud,
                directStormStrength,
                stormBaseField(baseCarrier),
                embeddedConvectiveOverlap
            );
        }

        if (PuffDensityStage == 3) {
            // Preserve all weather-map early rejection, fused height bounds and
            // coverage gates, but stop before detail erosion/material boosts.
            if (!useDirectPuff) {
                return 0.0;
            }
            float weatherGate = smoothstep(0.010, 0.080, coverageMod);
            return max(cloud * weatherGate, 0.0) * 0.88 * DensityMul;
        }

        bool suppressPuffErosion = PuffDensityStage == 4 && useDirectPuff;
        bool directPuffCarrierOwnsBoundary = profileId == 3 && useDirectPuff;
        if (cloud > 0.003
                && useDetail
                && !suppressPuffErosion
                && !directPuffCarrierOwnsBoundary) {
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
            float erosion = profileId == 3 ? 0.14 : 0.26;
            if (profileId == 1 || profileId == 5) {
                erosion = 0.10;
            } else if (profileId == 2) {
                erosion = 0.17;
            } else if (directStormAvailable) {
                erosion = STORM_EROSION;
            } else if (stormProfile) {
                // Storm bodies (cumulonimbus/supercell) are large enough that
                // the shared 0.68 floor below reads as barely-visible
                // dimpling on a smooth analytic dome. Give them a stronger,
                // deeper-reaching billow without touching the protected core
                // (cloud > ~0.72 still keeps edgeExposure at 0 either way).
                erosion = envelopeRole == 5 ? 0.48 : 0.42;
            } else if (profileId == 6) {
                erosion = 0.06;
            }
            if (directStormAvailable) {
                // STAGE 6 - multi-scale detail erosion across the whole body.
                //
                // The edge-exposure gate below is deliberately NOT applied to
                // descriptor-owned storms. It computes
                // 1 - smoothstep(0.26, 0.72, cloud), which reaches zero across
                // any properly covered storm interior, so detail noise had no
                // effect over most of the body and the erosion floor clamped
                // whatever survived. The subtractive form keeps a constant
                // nonzero sensitivity of STORM_EROSION everywhere the result
                // is unsaturated, while leaving the dense core intact by
                // magnitude rather than by a gate: CORE_FILL is derived so the
                // weakest core sample still outruns the deepest erosion bite.
                cloud = max(cloud - (1.0 - detailFbm) * STORM_EROSION, 0.0);
            } else if (profileId == 6) {
                cloud = max(cloud - (1.0 - detailFbm) * erosion, 0.0);
            } else {
                // Noise adds detail at exposed edges instead of drilling holes
                // through the protected meteorological core. Non-storm
                // families keep this behaviour unchanged.
                float edgeExposure = 1.0 - smoothstep(0.26, 0.72, cloud);
                float edgeRetention = 1.0 - (1.0 - detailFbm) * erosion * edgeExposure;
                float erosionFloor = stormProfile ? 0.42 : 0.68;
                cloud *= clamp(edgeRetention, erosionFloor, 1.0);
            }
        }

        // Storm cells hold more condensed water low in the cloud.
        // The direct descriptor owns its exact vertical interval. Reusing the
        // fused WeatherMap height here introduced a horizontal material kink
        // even before lighting. Other families retain their legacy height.
        float materialHeight01 = directStormAvailable
            ? directStormHeight01
            : (useDirectPuff ? directPuffHeight01 : h01);
        // Descriptor density already contains the complete group's storm
        // intensity. Point-sampled weather material fields retain the old
        // member splats, so using them here makes those members reappear as
        // internal density primitives. Keep only group-coherent material bias;
        // precipitation placement itself remains on the US2 attachment path.
        float materialEnergy = directStormAvailable ? 0.72 : energy;
        float materialCondensate = directStormAvailable ? 0.78 : condensate;
        float materialPrecipitation = directStormAvailable ? 0.68 : precipitation;
        cloud *= mix(
            1.0,
            1.18,
            materialEnergy * (1.0 - saturate(materialHeight01)) * 0.6
        );
        cloud *= mix(0.90, 1.10, materialCondensate);
        cloud *= 1.0
            + materialPrecipitation * (1.0 - saturate(materialHeight01)) * 0.32;
        if (profileId == 5) {
            cloud *= 1.0 + precipitation * 0.12;
        }
        // The weather map supplies storm material attributes, not the final
        // boundary. Its raster footprint is nominal-radius support while the
        // analytic role profiles and shear can extend beyond that footprint;
        // applying the generic coverage cut here produces a height-independent
        // vertical wall. Direct descriptors therefore own their full boundary.
        cloud *= directStormAvailable
            ? 1.0
            : smoothstep(0.010, 0.080, coverageMod);
        if (PuffDensityStage == 4) {
            return useDirectPuff
                ? max(cloud, 0.0) * 0.88 * DensityMul
                : 0.0;
        }
    }

    if (PuffDensityStage == 3 || PuffDensityStage == 4) {
        return 0.0;
    }

    float rainShaft = includePrecipitation
        && PuffDensityStage != 5
        && PuffDensityStage != 6
        ? rainShaftDensityAt(p, mipBias)
        : 0.0;
    float familyDensityScale = directStormAvailable ? 0.73 : 0.88;
    if (!directStormAvailable && profileId == 1) {
        familyDensityScale = 0.62;
    } else if (!directStormAvailable && profileId == 2) {
        familyDensityScale = 0.76;
    } else if (!directStormAvailable && profileId == 4) {
        familyDensityScale = 0.72;
    } else if (!directStormAvailable && profileId == 5) {
        familyDensityScale = 0.72;
    } else if (!directStormAvailable && profileId == 6) {
        familyDensityScale = 0.78;
    } else if (!directStormAvailable && profileId == 7) {
        familyDensityScale = 0.74;
    }
    float density = (max(cloud, 0.0) * familyDensityScale + rainShaft) * DensityMul;
    if (PuffDensityStage == 5 || PuffDensityStage == 6) {
        // These causal cuts isolate cloud-body sources.  Funnel/rain density is
        // intentionally excluded even if a future fixture contains either.
        return max(density, 0.0);
    }
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

float lightMarchOpticalDepth(
        vec3 p,
        float localDensity,
        bool cameraStartsInsideSlab,
        bool cameraInsideCloud) {
    if (cameraInsideCloud) {
        // Dense in-cloud views cover almost the full low-resolution target.
        // Paying the complete cone for every primary step dominates frame time,
        // while the whiteout already hides distant cone detail. Preserve light
        // direction with one detached forward probe and analytically extend its
        // optical path instead of flattening the interior to a constant colour.
        if (paWorkloadCaptureActive()) {
            paLightMarchDensityEvaluations++;
        }
        float forwardDensity = cloudDensity(
            p + LightDir * 28.0,
            1.2,
            false,
            false,
            false
        );
        return localDensity * 18.0 + forwardDensity * 82.0;
    }
    int steps = clamp(LightSteps, 2, MAX_LIGHT_STEPS);
    if (cameraStartsInsideSlab) {
        steps = min(steps, 4);
    }
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
        // Rain shafts are deliberately excluded from the light cone. Their
        // fine streak noise would otherwise be paid at every light tap and
        // would over-darken the parent cloud.
        pos += LightDir * stepLength;
        if (paWorkloadCaptureActive()) {
            paLightMarchDensityEvaluations++;
        }
        float density = cloudDensity(pos + offset, float(i) * 0.6, i < 2, false, false);
        opticalDepth += density * stepLength;
        if (cameraStartsInsideSlab && opticalDepth * ExtinctionScale >= 28.0) {
            break;
        }
        stepLength *= 1.42;
    }
    return opticalDepth;
}

// Diagnostic-only paired estimator for DebugView 6. Endpoint and midpoint are
// sampled in the same loop, with one shared step count/length/growth, lateral
// offset, mip bias and density options. The production endpoint alone controls
// the inside-slab early-out, so the A/B always integrates the same segments.
// Callers skip this estimator for rain and in-cloud shortcuts, where no
// production exponential cone is evaluated.
vec2 lightMarchOpticalDepthEndpointMidpoint(
        vec3 p,
        bool cameraStartsInsideSlab) {
    int steps = clamp(LightSteps, 2, MAX_LIGHT_STEPS);
    if (cameraStartsInsideSlab) {
        steps = min(steps, 4);
    }
    float endpointOpticalDepth = 0.0;
    float midpointOpticalDepth = 0.0;
    float stepLength = 14.0;
    vec3 endpoint = p;
    for (int i = 0; i < MAX_LIGHT_STEPS; i++) {
        if (i >= steps) {
            break;
        }
        float ang = float(i) * 2.399963;
        float spread = (float(i) + 0.5) * 0.28;
        vec3 offset = vec3(
            cos(ang),
            0.35 * sin(ang * 1.7),
            sin(ang)
        ) * spread * stepLength * 0.24;
        vec3 midpoint = endpoint + LightDir * (stepLength * 0.5);
        endpoint += LightDir * stepLength;
        float endpointDensity = cloudDensity(
            endpoint + offset,
            float(i) * 0.6,
            i < 2,
            false,
            false
        );
        float midpointDensity = cloudDensity(
            midpoint + offset,
            float(i) * 0.6,
            i < 2,
            false,
            false
        );
        endpointOpticalDepth += endpointDensity * stepLength;
        midpointOpticalDepth += midpointDensity * stepLength;
        if (cameraStartsInsideSlab && endpointOpticalDepth * ExtinctionScale >= 28.0) {
            break;
        }
        stepLength *= 1.42;
    }
    return vec2(endpointOpticalDepth, midpointOpticalDepth);
}

// Diagnostic-only A/B for DebugView 7. The first channel reproduces the
// production broad-slab cap exactly. The second keeps marching the additional
// quality-profile taps as though an exterior camera were not classified only
// by slab altitude. Both channels share every tap through the capped prefix;
// only the requested continuation belongs exclusively to the full estimate.
vec2 lightMarchOpticalDepthCappedFull(
        vec3 p,
        bool cameraStartsInsideSlab) {
    int requestedSteps = clamp(LightSteps, 2, MAX_LIGHT_STEPS);
    int cappedSteps = cameraStartsInsideSlab
        ? min(requestedSteps, 4)
        : requestedSteps;
    float cappedOpticalDepth = 0.0;
    float fullOpticalDepth = 0.0;
    float stepLength = 14.0;
    vec3 pos = p;
    for (int i = 0; i < MAX_LIGHT_STEPS; i++) {
        if (i >= requestedSteps) {
            break;
        }
        float ang = float(i) * 2.399963;
        float spread = (float(i) + 0.5) * 0.28;
        vec3 offset = vec3(
            cos(ang),
            0.35 * sin(ang * 1.7),
            sin(ang)
        ) * spread * stepLength * 0.24;
        pos += LightDir * stepLength;
        float density = cloudDensity(
            pos + offset,
            float(i) * 0.6,
            i < 2,
            false,
            false
        );
        float segmentOpticalDepth = density * stepLength;
        fullOpticalDepth += segmentOpticalDepth;
        if (i < cappedSteps) {
            cappedOpticalDepth += segmentOpticalDepth;
        }
        if (cameraStartsInsideSlab
                && fullOpticalDepth * ExtinctionScale >= 28.0) {
            // Preserve the exact production early-out in both estimators. The
            // only varied factor is therefore the four-tap slab cap.
            break;
        }
        stepLength *= 1.42;
    }
    return vec2(cappedOpticalDepth, fullOpticalDepth);
}

// Diagnostic-only refined reference for DebugView 8. Every production
// exponential segment keeps its exact bounds, length, mip and detail policy.
// Two axial samples integrate each segment, while an antithetic pair of cone
// offsets supplies two independent optical depths. Their radiances are
// evaluated separately and averaged by the caller to avoid Jensen bias from
// averaging optical depth before the exponential lighting response.
vec2 lightMarchOpticalDepthRefinedPair(
        vec3 p,
        bool cameraStartsInsideSlab) {
    int steps = clamp(LightSteps, 2, MAX_LIGHT_STEPS);
    if (cameraStartsInsideSlab) {
        steps = min(steps, 4);
    }
    float plusOpticalDepth = 0.0;
    float minusOpticalDepth = 0.0;
    float stepLength = 14.0;
    vec3 segmentStart = p;
    for (int i = 0; i < MAX_LIGHT_STEPS; i++) {
        if (i >= steps) {
            break;
        }
        float ang = float(i) * 2.399963;
        float spread = (float(i) + 0.5) * 0.28;
        vec3 offset = vec3(
            cos(ang),
            0.35 * sin(ang * 1.7),
            sin(ang)
        ) * spread * stepLength * 0.24;
        vec3 axialQuarter = segmentStart + LightDir * (stepLength * 0.25);
        vec3 axialThreeQuarter = segmentStart + LightDir * (stepLength * 0.75);
        float mipBias = float(i) * 0.6;
        bool useDetail = i < 2;
        float plusQuarter = cloudDensity(
            axialQuarter + offset,
            mipBias,
            useDetail,
            false,
            false
        );
        float plusThreeQuarter = cloudDensity(
            axialThreeQuarter + offset,
            mipBias,
            useDetail,
            false,
            false
        );
        float minusQuarter = cloudDensity(
            axialQuarter - offset,
            mipBias,
            useDetail,
            false,
            false
        );
        float minusThreeQuarter = cloudDensity(
            axialThreeQuarter - offset,
            mipBias,
            useDetail,
            false,
            false
        );
        plusOpticalDepth += 0.5
            * (plusQuarter + plusThreeQuarter)
            * stepLength;
        minusOpticalDepth += 0.5
            * (minusQuarter + minusThreeQuarter)
            * stepLength;
        segmentStart += LightDir * stepLength;
        stepLength *= 1.42;
    }
    return vec2(plusOpticalDepth, minusOpticalDepth);
}

// Diagnostic-only counterfactual for DebugView 9. It preserves the exact
// production endpoints, cone offsets, segment lengths, growth, mip bias and
// slab cap, but disables fine detail in the light cone. On the direct PUFF
// path this varies only the edge erosion sampled by taps zero and one; primary
// density, analytic lobe support and all lighting inputs remain production.
float lightMarchOpticalDepthWithoutDetail(
        vec3 p,
        bool cameraStartsInsideSlab) {
    int steps = clamp(LightSteps, 2, MAX_LIGHT_STEPS);
    if (cameraStartsInsideSlab) {
        steps = min(steps, 4);
    }
    float opticalDepth = 0.0;
    float stepLength = 14.0;
    vec3 pos = p;
    for (int i = 0; i < MAX_LIGHT_STEPS; i++) {
        if (i >= steps) {
            break;
        }
        float ang = float(i) * 2.399963;
        float spread = (float(i) + 0.5) * 0.28;
        vec3 offset = vec3(
            cos(ang),
            0.35 * sin(ang * 1.7),
            sin(ang)
        ) * spread * stepLength * 0.24;
        pos += LightDir * stepLength;
        float density = cloudDensity(
            pos + offset,
            float(i) * 0.6,
            false,
            false,
            false
        );
        opticalDepth += density * stepLength;
        stepLength *= 1.42;
    }
    return opticalDepth;
}

vec3 evaluateLightingComponents(
        float opticalDepth,
        float localDensity,
        float h01ForAmbient,
        float cosTheta,
        float distance01,
        float localStorm,
        float materialDarkness,
        float rainFraction,
        out float diagnosticLightOpticalDepth,
        out float diagnosticDirectContribution,
        out float diagnosticAmbientContribution,
        out float diagnosticPhaseShadow) {
    // Multi-scattering octaves (Hillaire): each octave sees weaker extinction
    // and a flatter phase, which keeps thick storm cores luminous.
    float scatter = 0.0;
    float scatterWeight = 0.0;
    float a = 1.0;
    float b = 1.0;
    int octaves = clamp(ScatterOctaves, 1, 3);
    for (int o = 0; o < 3; o++) {
        if (o >= octaves) {
            break;
        }
        float phase = mix(0.0795775, dualLobePhase(cosTheta), a); // isotropic falloff per octave
        scatter += b * phase * exp(-opticalDepth * a);
        scatterWeight += b;
        a *= 0.42;
        b *= 0.52;
    }
    // Each HG phase integrates to one over the sphere. The octave weights
    // must therefore be normalized; otherwise Ultra injects 1.7904 times the
    // single-scattering energy and changing quality changes exposure.
    scatter /= max(scatterWeight, 0.0001);

    // Beer-powder: dark creases where in-scattering has not built up yet.
    float powder = 1.0 - exp(-localDensity * 24.0);
    float powderTerm = mix(1.0, saturate(powder * 1.35), saturate(cosTheta * 0.5 + 0.5) * 0.72);

    // Distant clouds redden more at sunset: longer light path through air.
    vec3 sunTint = mix(vec3(1.0), vec3(1.0, 0.52, 0.30), SunsetStrength * distance01 * 0.65);

    float combinedStorm = saturate(max(StormDarkening * 0.58, localStorm * 0.72));
    float undersideShade = saturate(
        (0.18 + materialDarkness * 0.82)
            * pow(1.0 - saturate(h01ForAmbient), 0.70)
            * (0.46 + localDensity * 0.72)
    );
    float directTransmission = exp(-opticalDepth);
    diagnosticLightOpticalDepth = opticalDepth;
    vec3 sunTerm = LightColor * sunTint * scatter * powderTerm * (4.0 * PI);
    sunTerm *= mix(1.0, 0.76, combinedStorm);
    sunTerm *= mix(1.0, mix(0.70, 0.82, combinedStorm), undersideShade);
    sunTerm *= mix(1.0, 0.80, rainFraction);
    vec3 ambient = mix(AmbientBottom, AmbientTop, saturate(h01ForAmbient));
    ambient *= mix(1.0, 0.68, combinedStorm);
    ambient *= mix(1.0, mix(0.58, 0.74, combinedStorm), undersideShade);
    // Reuse the actual light-cone transmission so deep material loses ambient
    // fill while exposed edges remain open to the sky. The bounded retention
    // prevents thick or night-time clouds from collapsing to black.
    float ambientRetention = mix(0.74, 0.58, materialDarkness);
    ambient *= mix(ambientRetention, 1.0, directTransmission);
    ambient *= mix(1.0, 0.68, rainFraction);

    // Silver lining is restricted to an optically thin shell looking toward
    // the light, instead of boosting the entire volume through the HG phase.
    float forward = saturate(cosTheta);
    float forward2 = forward * forward;
    float forward4 = forward2 * forward2;
    float silverPhase = forward4 * forward4 * forward4;
    float edgeShell = smoothstep(0.008, 0.09, localDensity)
        * (1.0 - smoothstep(0.22, 0.66, localDensity));
    float silver = silverPhase
        * sqrt(max(directTransmission, 0.0))
        * edgeShell
        * mix(1.0, 0.26, combinedStorm)
        * (1.0 - rainFraction * 0.78);
    vec3 radiance = sunTerm + ambient * 0.86 + LightColor * sunTint * silver * 0.48;

    diagnosticDirectContribution = dot(
        sunTerm + LightColor * sunTint * silver * 0.48,
        vec3(0.2126, 0.7152, 0.0722)
    );
    diagnosticAmbientContribution = dot(ambient * 0.86, vec3(0.2126, 0.7152, 0.0722));
    diagnosticPhaseShadow = scatter * powderTerm * directTransmission;

    vec3 rainTint = mix(vec3(0.76, 0.80, 0.86), vec3(0.48, 0.54, 0.64), NightFactor);
    radiance *= mix(vec3(1.0), rainTint, rainFraction * 0.50);
    radiance = max(radiance, vec3(0.0));
    // A peak normalization mapped every energetic sample to exactly white.
    // Filmic exponential compression preserves highlight ordering and colour
    // while keeping the final LDR composite bounded.
    float toneExposure = mix(1.30, 1.48, NightFactor);
    return vec3(1.0) - exp(-radiance * toneExposure);
}

vec3 evaluateLightingFromOpticalDepth(
        float opticalDepth,
        float localDensity,
        float h01ForAmbient,
        float cosTheta,
        float distance01,
        float localStorm,
        float materialDarkness,
        float rainFraction,
        out float diagnosticLightOpticalDepth) {
    float ignoredDirect;
    float ignoredAmbient;
    float ignoredPhaseShadow;
    return evaluateLightingComponents(
        opticalDepth,
        localDensity,
        h01ForAmbient,
        cosTheta,
        distance01,
        localStorm,
        materialDarkness,
        rainFraction,
        diagnosticLightOpticalDepth,
        ignoredDirect,
        ignoredAmbient,
        ignoredPhaseShadow
    );
}

vec3 sampleLighting(
        vec3 p,
        float localDensity,
        float h01ForAmbient,
        float cosTheta,
        float distance01,
        float localStorm,
        float materialDarkness,
        float rainFraction,
        bool cameraStartsInsideSlab,
        bool cameraInsideCloud,
        out float diagnosticLightOpticalDepth) {
    // Fine rain streaks do not need a full cloud light cone. Avoid paying the
    // multi-sample self-shadow march for every precipitation step.
    float opticalDepth = rainFraction > 0.05
        ? localDensity * 8.0 * ExtinctionScale
        : lightMarchOpticalDepth(
            p,
            localDensity,
            cameraStartsInsideSlab,
            cameraInsideCloud
        ) * ExtinctionScale;
    return evaluateLightingFromOpticalDepth(
        opticalDepth,
        localDensity,
        h01ForAmbient,
        cosTheta,
        distance01,
        localStorm,
        materialDarkness,
        rainFraction,
        diagnosticLightOpticalDepth
    );
}

// Emits four exact, raw shader stages across horizontal trace stripes. The
// Java on-demand capture reads one texel per stripe from the cloud target.
// This bypasses neither descriptor ownership nor texture sampling; it invokes
// the same direct-storm functions used by cloudDensity and lighting.
vec4 stormMaterialTraceAt(vec3 p, int stage) {
    bool owned = false;
    float height01 = 0.0;
    float strength = 0.0;
    int roleMask = 0;
    float coverage = StormLobeCount > 0
        ? directStormShape(p, owned, height01, strength, roleMask)
        : 0.0;
    vec3 samplePos = p;
    samplePos.xz -= MaterialOffset;
    vec4 baseNoise = texture(
        BaseNoiseSampler, baseNoiseDomain(samplePos, STORM_BASE_NOISE_SCALE), 0.0
    );
    float lowFbm = baseNoise.g * 0.625 + baseNoise.b * 0.25 + baseNoise.a * 0.125;
    float carrierRaw = saturate(remap(baseNoise.r, -(1.0 - lowFbm), 1.0, 0.0, 1.0));
    float baseField = stormBaseField(carrierRaw);
    bool embedded = (roleMask & 7) == 7;
    // directStormShape exposes roles whose lobe-softness region was inspected.
    // That is useful to its smooth union, but it is not an active contribution
    // when the final envelope is zero. T128 exports the latter separately.
    int activeRoleMask = coverage > 0.001 && owned ? roleMask : 0;
    float bodyBefore = owned ? stormBody(coverage, strength, baseField, false) : 0.0;
    float bodyAfter = owned ? stormBody(coverage, strength, baseField, embedded) : 0.0;
    vec3 detailPos = detailNoiseDomain(samplePos) + (baseNoise.gbr - 0.5) * 0.18;
    vec4 detail = texture(DetailNoiseSampler, detailPos, 0.0);
    float detailFbm = detail.r * 0.625 + detail.g * 0.25 + detail.b * 0.125;
    float erosion = (1.0 - detailFbm) * STORM_EROSION;
    float bodyEroded = max(bodyAfter - erosion, 0.0);
    float materialFactor = mix(1.0, 1.18, 0.72 * (1.0 - saturate(height01)) * 0.6)
        * mix(0.90, 1.10, 0.78)
        * (1.0 + 0.68 * (1.0 - saturate(height01)) * 0.32);
    float density = owned ? bodyEroded * materialFactor * 0.73 * DensityMul : 0.0;
    bool cameraStartsInsideSlab = CameraPos.y >= SlabBaseY && CameraPos.y <= SlabTopY;
    bool cameraInsideCloud = cloudDensity(CameraPos, 0.0, false, true, false) > 0.12;
    vec3 traceViewDirection = normalize(p - CameraPos);
    float lightOd = lightMarchOpticalDepth(
        p, density, cameraStartsInsideSlab, cameraInsideCloud
    ) * ExtinctionScale;
    float direct;
    float ambient;
    float phaseShadow;
    float ignoredRecordedOd;
    vec3 radiance = evaluateLightingComponents(
        lightOd,
        density,
        height01,
        dot(traceViewDirection, LightDir),
        saturate(length(p - CameraPos) / max(MaxRenderDistance, 1.0)),
        1.0,
        0.0,
        0.0,
        ignoredRecordedOd,
        direct,
        ambient,
        phaseShadow
    );
    if (stage == 0) {
        return vec4(coverage, strength, carrierRaw, baseField);
    }
    if (stage == 1) {
        return vec4(bodyBefore, bodyAfter, erosion, density);
    }
    if (stage == 2) {
        return vec4(height01, density * ExtinctionScale, lightOd, direct);
    }
    int traceFlags = activeRoleMask + (owned ? 16 : 0);
    return vec4(ambient, phaseShadow, dot(radiance, vec3(0.2126, 0.7152, 0.0722)),
        float(traceFlags) / 31.0);
}

// Diagnostic-only segment-sample integrator for DebugViews 11, 12 and 14-20.
// The caller owns the segment lattice: this helper neither searches for
// material nor advances the production ray. It evaluates one stratified sample
// inside a fine segment and updates an independent Beer/radiance lane. Dense samples must prove that
// production selected the direct analytic PUFF representation; clear samples
// are valid zero contributions inside the production-traversed fine segment.
bool integratePrimaryQuadratureSample(
        vec3 p,
        float sampleT,
        float sampleStepLength,
        float densityThreshold,
        float cosTheta,
        bool nearCamera,
        bool cameraStartsInsideSlab,
        bool cameraInsideCloud,
        inout float quadratureTransmittance,
        inout vec3 quadratureAccumulated) {
    float density = cloudDensity(
        p,
        0.0,
        DetailQuality > 0,
        nearCamera,
        false
    );
    if (density <= densityThreshold) {
        return true;
    }

    vec4 weather = sampleWeather(p.xz);
    vec4 morphology = sampleMorphology(p.xz);
    int profileId = cloudProfileId(morphology);
    float ignoredLocalHeight = 0.0;
    if (profileId != 3
            || !dominantDirectPuffHeightAt(p, ignoredLocalHeight)) {
        return false;
    }

    float slabSpan = max(SlabTopY - SlabBaseY, 1.0);
    float baseY = SlabBaseY + weather.g * slabSpan;
    float topY = SlabBaseY + weather.b * slabSpan;
    float h01 = saturate((p.y - baseY) / max(topY - baseY, 2.0));
    float materialDarkness = morphology.b;
    float localStorm = saturate(morphology.a * 0.16);
    float rainFraction = p.y < baseY
        ? saturate(0.25 + max(morphology.a, MaxPrecipitation) * 0.75)
        : 0.0;
    float ignoredLightOpticalDepth = 0.0;
    vec3 radiance = sampleLighting(
        p,
        density,
        h01,
        cosTheta,
        saturate(sampleT / MaxRenderDistance),
        localStorm,
        materialDarkness,
        rainFraction,
        cameraStartsInsideSlab,
        cameraInsideCloud,
        ignoredLightOpticalDepth
    );
    float stepTrans = exp(-density * ExtinctionScale * sampleStepLength);
    quadratureAccumulated += quadratureTransmittance
        * radiance
        * (1.0 - stepTrans);
    quadratureTransmittance *= stepTrans;
    return true;
}

// Alpha-only diagnostic estimator. Unlike the radiance quadrature above, it
// intentionally needs no WeatherMap profile or dominant-height proof: stages
// 7..11 bypass those representations by design. The caller supplies two
// quarter-point samples for the exact same fine segment as production.
void integratePrimaryAlphaQuadratureSample(
        vec3 p,
        float sampleStepLength,
        bool nearCamera,
        inout float quadratureTransmittance) {
    float density = cloudDensity(
        p,
        0.0,
        DetailQuality > 0,
        nearCamera,
        false
    );
    if (density <= 0.0008) {
        density = 0.0;
    }
    quadratureTransmittance *= exp(
        -density * ExtinctionScale * sampleStepLength
    );
}

// Samples only the primary density term used by the direct-PUFF quadrature
// diagnostics. Accepted material must still prove that it belongs to the
// analytic PUFF representation; values rejected by the production threshold
// become exact zero-density samples.
bool samplePrimaryQuadratureDensity(
        vec3 p,
        float densityThreshold,
        bool nearCamera,
        out float density) {
    density = cloudDensity(
        p,
        0.0,
        DetailQuality > 0,
        nearCamera,
        false
    );
    if (density <= densityThreshold) {
        density = 0.0;
        return true;
    }

    vec4 morphology = sampleMorphology(p.xz);
    float ignoredLocalHeight = 0.0;
    return cloudProfileId(morphology) == 3
        && dominantDirectPuffHeightAt(p, ignoredLocalHeight);
}

// Diagnostic-only two-density/one-light estimator. DebugView 18 samples its
// source once at the segment centre used by DebugView 16. DebugView 20 instead
// samples it at the Beer-opacity centroid of the two quarter-point densities.
// Both retain exact two-sample optical depth while paying for one light march.
bool integratePrimaryDensityQuadratureSegment(
        vec3 firstP,
        vec3 midpointP,
        vec3 secondP,
        float midpointT,
        float stepLength,
        float densityThreshold,
        float cosTheta,
        bool nearCamera,
        bool opacityWeightedSource,
        bool cameraStartsInsideSlab,
        bool cameraInsideCloud,
        inout float quadratureTransmittance,
        inout vec3 quadratureAccumulated) {
    float firstDensity = 0.0;
    float secondDensity = 0.0;
    bool firstValid = samplePrimaryQuadratureDensity(
        firstP,
        densityThreshold,
        nearCamera,
        firstDensity
    );
    bool secondValid = firstValid && samplePrimaryQuadratureDensity(
        secondP,
        densityThreshold,
        nearCamera,
        secondDensity
    );
    if (!secondValid) {
        return false;
    }

    float effectiveDensity = 0.5 * (firstDensity + secondDensity);
    if (effectiveDensity <= 0.0) {
        return true;
    }

    vec3 sourceP = midpointP;
    float sourceT = midpointT;
    float sourceDensity = effectiveDensity;
    if (opacityWeightedSource) {
        float halfStepLength = stepLength * 0.5;
        float firstStepTrans = exp(
            -firstDensity * ExtinctionScale * halfStepLength
        );
        float secondStepTrans = exp(
            -secondDensity * ExtinctionScale * halfStepLength
        );
        float firstWeight = 1.0 - firstStepTrans;
        float secondWeight = firstStepTrans * (1.0 - secondStepTrans);
        float sourceWeight = max(firstWeight + secondWeight, 0.000001);
        sourceP = (
            firstP * firstWeight + secondP * secondWeight
        ) / sourceWeight;
        float firstT = midpointT - stepLength * 0.25;
        float secondT = midpointT + stepLength * 0.25;
        sourceT = (
            firstT * firstWeight + secondT * secondWeight
        ) / sourceWeight;
        sourceDensity = (
            firstDensity * firstWeight + secondDensity * secondWeight
        ) / sourceWeight;
    }

    vec4 weather = sampleWeather(sourceP.xz);
    vec4 morphology = sampleMorphology(sourceP.xz);
    if (cloudProfileId(morphology) != 3) {
        return false;
    }
    float slabSpan = max(SlabTopY - SlabBaseY, 1.0);
    float baseY = SlabBaseY + weather.g * slabSpan;
    float topY = SlabBaseY + weather.b * slabSpan;
    float h01 = saturate(
        (sourceP.y - baseY) / max(topY - baseY, 2.0)
    );
    float materialDarkness = morphology.b;
    float localStorm = saturate(morphology.a * 0.16);
    float rainFraction = sourceP.y < baseY
        ? saturate(0.25 + max(morphology.a, MaxPrecipitation) * 0.75)
        : 0.0;
    float ignoredLightOpticalDepth = 0.0;
    vec3 radiance = sampleLighting(
        sourceP,
        sourceDensity,
        h01,
        cosTheta,
        saturate(sourceT / MaxRenderDistance),
        localStorm,
        materialDarkness,
        rainFraction,
        cameraStartsInsideSlab,
        cameraInsideCloud,
        ignoredLightOpticalDepth
    );
    float stepTrans = exp(
        -effectiveDensity * ExtinctionScale * stepLength
    );
    quadratureAccumulated += quadratureTransmittance
        * radiance
        * (1.0 - stepTrans);
    quadratureTransmittance *= stepTrans;
    return true;
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

float precipitationRayPadding() {
    // A camera-position probe misses every distant shaft for a horizontal ray.
    // MaxPrecipitation is feature availability only. Local support below
    // decides whether any segment actually evaluates or integrates a shaft.
    return mix(0.0, 180.0, smoothstep(0.02, 0.85, MaxPrecipitation));
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

void main() {
    if (DebugView == 21) {
        int traceSample = clamp(
            int(floor(texCoord.x * float(max(StormTraceSamples, 1)))),
            0,
            max(StormTraceSamples - 1, 0)
        );
        float traceY = StormTraceYStart + float(traceSample) * StormTraceYInterval;
        fragColor = stormMaterialTraceAt(
            vec3(StormTraceOrigin.x, traceY, StormTraceOrigin.y),
            StormTraceStage
        );
        gl_FragDepth = 1.0;
        return;
    }
    vec2 ndc = texCoord * 2.0 - 1.0;
    vec4 clipDir = vec4(ndc, -1.0, 1.0);
    vec4 viewDir4 = InvProjMat * clipDir;
    vec3 viewDir = normalize(viewDir4.xyz / max(abs(viewDir4.w), 0.00001));
    vec3 rayDir = normalize((InvViewRotMat * vec4(viewDir, 0.0)).xyz);

    // Slab intersection in world Y (camera-relative distances).
    float slabPadding = 40.0; // funnels extend below the slab base
    float lowY = SlabBaseY - precipitationRayPadding();
    if (FunnelCount > 0) {
        lowY = min(lowY, min(Funnel0A.w, SlabBaseY) - slabPadding);
    }
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
    bool cameraStartsInsideSlab = t0 <= 1.0;
    bool cameraInsideCloud = cameraStartsInsideSlab && CameraCloudDensity > 0.08;

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

    bool analyticPuffDiagnostic = PuffDensityStage == 1
        || PuffDensityStage == 2
        || (PuffDensityStage >= 7 && PuffDensityStage <= 12);

    // Coverage pre-test: sample the weather map along the ray and skip fully
    // clear rays. This is the biggest saver on clear days.
    bool anyCoverage = analyticPuffDiagnostic
        || FunnelCount > 0
        || CoveragePretestEnabled == 0;
    if (!anyCoverage) {
        // When the camera starts inside the slab, uniformly spaced probes can
        // put the first sample hundreds of blocks away and miss the local
        // cloud entirely. Test the near origin explicitly before the distant
        // sequence so horizontal in-cloud rays cannot produce a clear band.
        if (cameraStartsInsideSlab) {
            float nearProbeT = min(t1, t0 + 0.5);
            vec3 nearProbe = CameraPos + rayDir * nearProbeT;
            anyCoverage = pretestWeatherCoverage(nearProbe.xz)
                > max(CoveragePretestThreshold, 0.0);
        }
        int pretestSamples = clamp(CoveragePretestSamples, 6, 16);
        float threshold = max(CoveragePretestThreshold, 0.0);
        for (int i = 0; i < 16; i++) {
            if (anyCoverage) {
                break;
            }
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

    // Keep material search on one screen-spatial blue-noise phase. Animating
    // the complete coarse-search lattice made thin silhouette pixels alternate
    // between a hit and a miss; history cannot integrate a miss because there
    // is no representative point to reproject. Once a deterministic search has
    // confirmed a clear/material bracket, animate only the sub-step integration
    // phase so temporal history still accumulates finer samples.
    ivec2 blueSize = textureSize(BlueNoiseSampler, 0);
    vec2 searchBlueUv = gl_FragCoord.xy / vec2(blueSize);
    float searchBlue = texture(BlueNoiseSampler, searchBlueUv).r;
    float jitterFrame = HistoryValid == 1 && HistoryBlend > 0.001
        ? FrameIndex
        : 0.0;
    vec2 integrationBlueUv = (
        gl_FragCoord.xy + vec2(jitterFrame * 17.0, jitterFrame * 29.0)
    ) / vec2(blueSize);
    float integrationBlue = fract(
        texture(BlueNoiseSampler, integrationBlueUv).r
            + jitterFrame * 0.61803398875
    );

    int stepBudget = int(float(clamp(RaymarchSteps, 8, MAX_STEPS)) * clamp(StepScale, 0.4, 1.0));
    stepBudget = clamp(stepBudget, 8, MAX_STEPS);
    float span = t1 - t0;
    float baseStep = span / float(stepBudget);
    // Surface resolution must be expressed in world units. Deriving the fine
    // step from the complete ray span made a horizontal Ultra ray use roughly
    // 10-block samples while a vertical ray used 2-block samples, producing a
    // view-angle-locked stippled band. Preserve the legacy in-cloud stride for
    // whiteout cost, but give every exterior view a quality-scaled world-space
    // surface step.
    float legacyFineStep = max(baseStep * 0.5, 2.0);
    float exteriorFineStep = clamp(ExteriorFineStep, 2.5, 8.0);
    float fineStep = cameraInsideCloud ? legacyFineStep : exteriorFineStep;
    float coarseStep = max(baseStep * 1.5, fineStep * 3.0);
    float coarseStepCap = min(112.0, fineStep * 16.0);

    float cosTheta = dot(rayDir, LightDir);

    float originJitterDistance = min(
        paRayOriginJitterDistance(
            fineStep,
            cameraStartsInsideSlab,
            cameraInsideCloud
        ),
        span
    );
    float t = t0 + searchBlue * originJitterDistance;
    float lastClearT = t0;
    bool hasClearBracket = true;
    float transmittance = 1.0;
    vec3 accumulated = vec3(0.0);
    float primaryQuadratureTransmittance = 1.0;
    vec3 primaryQuadratureAccumulated = vec3(0.0);
    bool primaryQuadratureDiagnostic = DebugView == 11
        || DebugView == 12
        || DebugView == 14
        || DebugView == 15
        || DebugView == 16
        || DebugView == 17
        || DebugView == 18
        || DebugView == 19
        || DebugView == 20;
    bool primaryQuadratureDensityStageSupported = PuffDensityStage == 0
        || (DebugView == 12 && PuffDensityStage == 4)
        || (DebugView == 17
            && PuffDensityStage >= 7
            && PuffDensityStage <= 12);
    bool primaryQuadratureValid = !primaryQuadratureDiagnostic
        || (PuffShapeMode == 2
            && primaryQuadratureDensityStageSupported
            && PuffLobeCount > 0
            && FunnelCount == 0
            && MaxPrecipitation <= 0.02
            && !cameraInsideCloud);
    float weightedT = 0.0;
    float weightSum = 0.0;
    float weightedLightOcclusion = 0.0;
    float weightedAmbientHeight = 0.0;
    int sinceHit = cameraInsideCloud ? 0 : 100;
    bool firstMaterialResolved = false;
    bool firstMaterialIsStratus = false;
    float stratusRelief = 0.0;
    float stratusReliefBias = 0.0;
    float stratusMaterialSignal = 0.5;
    float stratusMaterialRelief = 0.0;
    float stratusSurfaceSide = 0.0;

    for (int i = 0; i < MAX_STEPS; i++) {
        if (t >= t1 || transmittance < 0.015) {
            if (transmittance < 0.015 && paWorkloadCaptureActive()) {
                paEarlyTerminations++;
            }
            break;
        }
        if (paWorkloadCaptureActive()) {
            paPrimaryRaySteps++;
        }
        bool fine = sinceHit < 6;
        float distanceGrowth = 1.0 + (t / max(MaxRenderDistance, 1.0)) * 2.2;
        float stepLength = fine
            ? fineStep * (cameraInsideCloud ? distanceGrowth : 1.0)
            : min(coarseStep * distanceGrowth, coarseStepCap);
        // Never integrate optical depth past the scene/slab ray endpoint.
        stepLength = min(stepLength, t1 - t);

        vec3 p = CameraPos + rayDir * t;
        if (!fine
                && PuffShapeMode != 0
                && PuffLobeCount > 0
                && directPuffSegmentMayIntersect(
                    p,
                    CameraPos + rayDir * (t + stepLength)
                )) {
            // Switch within this iteration. The former continue consumed one
            // of the fixed 128 iterations without advancing t; repeated empty
            // AABB entries could exhaust the march before the ray endpoint.
            sinceHit = 0;
            fine = true;
            stepLength = fineStep
                * (cameraInsideCloud ? distanceGrowth : 1.0);
            stepLength = min(stepLength, t1 - t);
        }
        if (!fine
                && StormLobeCount > 0
                && directStormSegmentMayIntersect(
                    p,
                    CameraPos + rayDir * (t + stepLength)
                )) {
            sinceHit = 0;
            fine = true;
            stepLength = fineStep
                * (cameraInsideCloud ? distanceGrowth : 1.0);
            stepLength = min(stepLength, t1 - t);
        }
        vec3 segmentEnd = CameraPos + rayDir * (t + stepLength);
        bool localRainSegment = PuffDensityStage == 0
            && rainSegmentMayContribute(p, segmentEnd);
        // Empty exterior coarse samples need only the weather occupancy fetch.
        // This offsets the extra surface samples without reviving the old
        // non-conservative whole-ray coverage pretest.
        if (!analyticPuffDiagnostic
                && !fine
                && FunnelCount == 0) {
            float coverageSignal = sampleWeather(p.xz).r * CoverageMul;
            if (coverageSignal <= 0.001 && !localRainSegment) {
                if (paWorkloadCaptureActive()) {
                    paEmptySpaceRejects++;
                }
                lastClearT = t;
                hasClearBracket = true;
                sinceHit++;
                t += stepLength;
                continue;
            }
        }
        // The second near-camera detail octave cannot be resolved through a
        // dense whiteout and doubles its 3-D detail fetches. Keep it unchanged
        // for every exterior view and omit it only when the canonical camera
        // density confirms the camera is inside rendered cloud material.
        bool nearCamera = t < 220.0 && !cameraInsideCloud;
        // Body sampling keeps its adaptive fine/coarse state. Rain is a
        // separate deterministic segment integral and can never force the
        // body marcher into a fine-step curtain below the cloud.
        float bodyDensity = cloudDensity(p, 0.0, DetailQuality > 0, nearCamera, false);
        float rainDensity = localRainSegment
            ? rainShaftDensityOverSegment(p, segmentEnd, 0.0) * DensityMul
            : 0.0;
        float density = bodyDensity + rainDensity;

        if (DebugView == 17
                && primaryQuadratureValid
                && fine) {
            float halfStepLength = stepLength * 0.5;
            integratePrimaryAlphaQuadratureSample(
                CameraPos + rayDir * (t + stepLength * 0.25),
                halfStepLength,
                nearCamera,
                primaryQuadratureTransmittance
            );
            integratePrimaryAlphaQuadratureSample(
                CameraPos + rayDir * (t + stepLength * 0.75),
                halfStepLength,
                nearCamera,
                primaryQuadratureTransmittance
            );
        }

        if (primaryQuadratureDiagnostic
                && primaryQuadratureValid
                && fine
                && (DebugView == 12
                    || (DebugView == 14 && density <= 0.0008)
                    || ((DebugView == 11 || DebugView == 15)
                        && density > 0.0008))) {
            float halfStepLength = stepLength * 0.5;
            float firstSampleT = t + stepLength * 0.25;
            float secondSampleT = t + stepLength * 0.75;
            bool firstSampleValid = integratePrimaryQuadratureSample(
                CameraPos + rayDir * firstSampleT,
                firstSampleT,
                halfStepLength,
                DebugView == 11 ? 0.0 : 0.0008,
                cosTheta,
                nearCamera,
                cameraStartsInsideSlab,
                cameraInsideCloud,
                primaryQuadratureTransmittance,
                primaryQuadratureAccumulated
            );
            bool secondSampleValid = firstSampleValid
                && integratePrimaryQuadratureSample(
                    CameraPos + rayDir * secondSampleT,
                    secondSampleT,
                    halfStepLength,
                    DebugView == 11 ? 0.0 : 0.0008,
                    cosTheta,
                    nearCamera,
                    cameraStartsInsideSlab,
                    cameraInsideCloud,
                    primaryQuadratureTransmittance,
                    primaryQuadratureAccumulated
                );
            if (!secondSampleValid) {
                primaryQuadratureValid = false;
            }
        }

        if (DebugView == 16
                && primaryQuadratureValid
                && fine) {
            float midpointT = t + stepLength * 0.5;
            bool midpointValid = integratePrimaryQuadratureSample(
                CameraPos + rayDir * midpointT,
                midpointT,
                stepLength,
                0.0008,
                cosTheta,
                nearCamera,
                cameraStartsInsideSlab,
                cameraInsideCloud,
                primaryQuadratureTransmittance,
                primaryQuadratureAccumulated
            );
            if (!midpointValid) {
                primaryQuadratureValid = false;
            }
        }

        if ((DebugView == 18 || DebugView == 20)
                && primaryQuadratureValid
                && fine) {
            float firstSampleT = t + stepLength * 0.25;
            float midpointT = t + stepLength * 0.5;
            float secondSampleT = t + stepLength * 0.75;
            bool densityQuadratureValid = integratePrimaryDensityQuadratureSegment(
                CameraPos + rayDir * firstSampleT,
                CameraPos + rayDir * midpointT,
                CameraPos + rayDir * secondSampleT,
                midpointT,
                stepLength,
                0.0008,
                cosTheta,
                nearCamera,
                DebugView == 20,
                cameraStartsInsideSlab,
                cameraInsideCloud,
                primaryQuadratureTransmittance,
                primaryQuadratureAccumulated
            );
            if (!densityQuadratureValid) {
                primaryQuadratureValid = false;
            }
        }

        if (density > 0.0008) {
            vec4 weather = sampleWeather(p.xz);
            vec4 morphology = sampleMorphology(p.xz);
            float slabSpan = max(SlabTopY - SlabBaseY, 1.0);
            float baseY = SlabBaseY + weather.g * slabSpan;
            float topY = SlabBaseY + weather.b * slabSpan;
            int profileId = cloudProfileId(morphology);
            float directPuffHeight01 = 0.0;
            bool directPuffBody = profileId == 3
                && PuffDensityStage == 0
                && PuffShapeMode != 0
                && dominantDirectPuffHeightAt(p, directPuffHeight01);
            bool precipitationSample = FunnelCount == 0
                && rainDensity > 0.0008
                && bodyDensity <= 0.0008
                && !directPuffBody;
            if (!fine && !precipitationSample) {
                // Resolve the actual clear/material bracket instead of an
                // arbitrary 60% rewind. Four bisections localize an exterior
                // Ultra surface to at most one 2.5-block fine step because the
                // coarse stride is capped at sixteen fine steps.
                float bracketLow = hasClearBracket
                    ? lastClearT
                    : max(t0, t - stepLength);
                float bracketHigh = t;
                for (int refinement = 0; refinement < 4; refinement++) {
                    float bracketMid = 0.5 * (bracketLow + bracketHigh);
                    vec3 bracketPos = CameraPos + rayDir * bracketMid;
                    bool bracketNearCamera = bracketMid < 220.0 && !cameraInsideCloud;
                    float bracketDensity = cloudDensity(
                        bracketPos,
                        0.0,
                        DetailQuality > 0,
                        bracketNearCamera,
                        false
                    );
                    if (bracketDensity > 0.0008) {
                        bracketHigh = bracketMid;
                    } else {
                        bracketLow = bracketMid;
                    }
                }
                lastClearT = bracketLow;
                t = mix(bracketLow, bracketHigh, integrationBlue);
                sinceHit = 0;
                continue;
            }
            // Shafts stay on coarse strides. Their broad envelope and streak
            // noise do not need cloud-surface resolution, and keeping the fine
            // state here multiplies their cost by the full 180-block depth.
            sinceHit = precipitationSample ? 100 : 0;

            float h01 = directPuffBody
                ? directPuffHeight01
                : saturate((p.y - baseY) / max(topY - baseY, 2.0));
            if (!firstMaterialResolved && !precipitationSample) {
                firstMaterialResolved = true;
                firstMaterialIsStratus = profileId == 1;
                stratusSurfaceSide = rayDir.y < 0.0 ? 1.0 : 0.0;
                float rayVerticality = smoothstep(0.08, 0.25, abs(rayDir.y));
                if (firstMaterialIsStratus
                        && !cameraInsideCloud
                        && rayVerticality > 0.0) {
                    // Resolve a deterministic point on the visible weather-map
                    // surface. The first material hit is blue-noise jittered in
                    // XYZ; reusing its XZ made an otherwise world-stable signal
                    // turn into fine stipple and grazing-angle streaks. One
                    // height refinement follows the local base/top surface.
                    float materialSurfaceY = mix(
                        baseY,
                        topY,
                        stratusSurfaceSide
                    );
                    float materialSurfaceT = clamp(
                        (materialSurfaceY - CameraPos.y) / rayDir.y,
                        t0,
                        t1
                    );
                    vec2 materialSurfaceXZ = CameraPos.xz
                        + rayDir.xz * materialSurfaceT;
                    vec4 materialWeather = sampleWeather(materialSurfaceXZ);
                    if (materialWeather.r > 0.001) {
                        materialSurfaceY = SlabBaseY + mix(
                            materialWeather.g,
                            materialWeather.b,
                            stratusSurfaceSide
                        ) * slabSpan;
                        materialSurfaceT = clamp(
                            (materialSurfaceY - CameraPos.y) / rayDir.y,
                            t0,
                            t1
                        );
                        materialSurfaceXZ = CameraPos.xz
                            + rayDir.xz * materialSurfaceT;
                    } else {
                        materialWeather = weather;
                    }
                    vec3 materialSurface = vec3(
                        materialSurfaceXZ.x,
                        materialSurfaceY,
                        materialSurfaceXZ.y
                    );
                    stratusMaterialSignal = stratusHorizontalMaterialSignal(
                        materialSurface,
                        stratusSurfaceSide,
                        materialWeather,
                        morphology
                    );
                    const float materialProbeWorld = 64.0;
                    float materialNeighbourMean = 0.25 * (
                        stratusHorizontalMaterialSignal(
                            materialSurface + vec3(materialProbeWorld, 0.0, 0.0),
                            stratusSurfaceSide,
                            materialWeather,
                            morphology
                        )
                        + stratusHorizontalMaterialSignal(
                            materialSurface - vec3(materialProbeWorld, 0.0, 0.0),
                            stratusSurfaceSide,
                            materialWeather,
                            morphology
                        )
                        + stratusHorizontalMaterialSignal(
                            materialSurface + vec3(0.0, 0.0, materialProbeWorld),
                            stratusSurfaceSide,
                            materialWeather,
                            morphology
                        )
                        + stratusHorizontalMaterialSignal(
                            materialSurface - vec3(0.0, 0.0, materialProbeWorld),
                            stratusSurfaceSide,
                            materialWeather,
                            morphology
                        )
                    );
                    stratusMaterialRelief = (
                        stratusMaterialSignal - materialNeighbourMean
                    ) * rayVerticality;
                    vec3 differential = stratusSurfaceDifferential(
                        materialSurfaceXZ,
                        materialWeather,
                        stratusSurfaceSide
                    );
                    vec3 transportNormal = normalize(vec3(
                        -differential.x * 8.0,
                        1.0,
                        -differential.y * 8.0
                    ));
                    float sideSign = mix(-1.0, 1.0, stratusSurfaceSide);
                    float horizontalLight = length(LightDir.xz);
                    vec2 sunAzimuth = horizontalLight > 0.001
                        ? LightDir.xz / horizontalLight
                        : vec2(0.0);
                    float daylight = smoothstep(-0.02, 0.20, LightDir.y)
                        * mix(1.0, 0.40, NightFactor);
                    stratusReliefBias = sideSign
                        * 0.0102
                        * rayVerticality
                        * daylight;
                    // Resolve the same geometric surface consistently from
                    // above and below. Normalize the sun azimuth so a high sun
                    // still exposes broad slopes instead of flattening them to
                    // zero; the elevation weight keeps the response bounded.
                    float elevationWeight = mix(
                        0.50,
                        1.0,
                        smoothstep(0.08, 0.55, horizontalLight)
                    );
                    float normalCue = dot(
                        transportNormal.xz * sideSign,
                        sunAzimuth
                    ) * 0.70 * elevationWeight * daylight;
                    float outwardCurvature = differential.z
                        * sideSign;
                    float curvatureCue = clamp(
                        outwardCurvature * 0.045,
                        -0.060,
                        0.050
                    );
                    stratusRelief = clamp(
                        (normalCue + curvatureCue) * rayVerticality,
                        -0.16,
                        0.12
                    );
                }
            }
            float materialDarkness = morphology.b;
            float localStorm = 0.0;
            if (profileId == 4 || profileId == 7) {
                localStorm = 0.20 + weather.a * 0.52;
            } else if (profileId == 5) {
                localStorm = morphology.a * 0.52;
            }
            localStorm = saturate(localStorm + morphology.a * 0.16);
            // A direct-PUFF body is cloud material even when the lossy fused
            // WeatherMap interval puts it below baseY. Classifying that dry
            // material as 25% rain caused the hard black underside slab.
            float rainFraction = !directPuffBody
                    && (precipitationSample || p.y < baseY)
                ? saturate(0.25 + max(morphology.a, MaxPrecipitation) * 0.75)
                : 0.0;

            if (primaryQuadratureDiagnostic && primaryQuadratureValid) {
                float ignoredLocalHeight = 0.0;
                bool productionSampleIsDirectPuff = profileId == 3
                    && dominantDirectPuffHeightAt(p, ignoredLocalHeight);
                if (!productionSampleIsDirectPuff) {
                    primaryQuadratureValid = false;
                }
            }

            float extinction = density * ExtinctionScale;
            float stepTrans = exp(-extinction * stepLength);
            float diagnosticLightOpticalDepth = 0.0;
            vec3 radiance = sampleLighting(
                p,
                density,
                h01,
                cosTheta,
                saturate(t / MaxRenderDistance),
                localStorm,
                materialDarkness,
                rainFraction,
                cameraStartsInsideSlab,
                cameraInsideCloud,
                diagnosticLightOpticalDepth
            );

            // Pure lighting-position control. B retains production density,
            // Beer opacity, local morphology scalars, prefix and termination;
            // only the origin of two half-segment light cones moves to the
            // quarter points. Updating B with A's exact stepTrans keeps alpha
            // bit-identical apart from the exported float representation.
            if (DebugView == 19 && primaryQuadratureValid) {
                if (fine) {
                    float firstLightT = t + stepLength * 0.25;
                    float secondLightT = t + stepLength * 0.75;
                    float firstIgnoredLightOpticalDepth = 0.0;
                    float secondIgnoredLightOpticalDepth = 0.0;
                    vec3 firstLight = sampleLighting(
                        CameraPos + rayDir * firstLightT,
                        density,
                        h01,
                        cosTheta,
                        saturate(t / MaxRenderDistance),
                        localStorm,
                        materialDarkness,
                        rainFraction,
                        cameraStartsInsideSlab,
                        cameraInsideCloud,
                        firstIgnoredLightOpticalDepth
                    );
                    vec3 secondLight = sampleLighting(
                        CameraPos + rayDir * secondLightT,
                        density,
                        h01,
                        cosTheta,
                        saturate(t / MaxRenderDistance),
                        localStorm,
                        materialDarkness,
                        rainFraction,
                        cameraStartsInsideSlab,
                        cameraInsideCloud,
                        secondIgnoredLightOpticalDepth
                    );
                    float halfStepTrans = exp(
                        -extinction * stepLength * 0.5
                    );
                    float firstHalfWeight = 1.0 - halfStepTrans;
                    float secondHalfWeight = halfStepTrans - stepTrans;
                    primaryQuadratureAccumulated += primaryQuadratureTransmittance
                        * (firstLight * firstHalfWeight
                            + secondLight * secondHalfWeight);
                } else {
                    primaryQuadratureAccumulated += primaryQuadratureTransmittance
                        * radiance
                        * (1.0 - stepTrans);
                }
                primaryQuadratureTransmittance *= stepTrans;
            }

            // View 14 preserves every production-accepted segment exactly in
            // its B lane. Its only additional samples were integrated above,
            // at the quarter points of fine segments whose production
            // endpoint was clear. This separates missed inter-endpoint matter
            // from the estimator replacement performed by Views 11 and 12.
            if (DebugView == 14 && primaryQuadratureValid) {
                primaryQuadratureAccumulated += primaryQuadratureTransmittance
                    * radiance
                    * (1.0 - stepTrans);
                primaryQuadratureTransmittance *= stepTrans;
            }

            // Energy-conserving analytic integration over the step.
            vec3 integrated = radiance * (1.0 - stepTrans);
            accumulated += transmittance * integrated;

            float alphaContribution = transmittance * (1.0 - stepTrans);
            weightedT += t * alphaContribution;
            weightSum += alphaContribution;
            if (DebugView == 5) {
                float diagnosticLightOcclusion = 1.0
                    - exp(-diagnosticLightOpticalDepth);
                weightedLightOcclusion += saturate(diagnosticLightOcclusion) * alphaContribution;
                weightedAmbientHeight += saturate(h01) * alphaContribution;
            } else if (DebugView == 6) {
                // This view is intentionally unavailable for the rain shortcut
                // and the analytic in-cloud probe: neither has an endpoint vs
                // midpoint cone phase. A negative A accumulator marks the
                // entire pixel as non-conclusive without adding another main
                // raymarch register.
                if (rainFraction > 0.05 || cameraInsideCloud) {
                    weightedLightOcclusion = -1.0;
                } else if (weightedLightOcclusion >= 0.0) {
                    vec2 phaseOpticalDepth = lightMarchOpticalDepthEndpointMidpoint(
                        p,
                        cameraStartsInsideSlab
                    ) * ExtinctionScale;
                    float diagnosticMidpointOcclusion = 0.0;
                    vec3 midpointRadiance = evaluateLightingFromOpticalDepth(
                        phaseOpticalDepth.y,
                        density,
                        h01,
                        cosTheta,
                        saturate(t / MaxRenderDistance),
                        localStorm,
                        materialDarkness,
                        rainFraction,
                        diagnosticMidpointOcclusion
                    );
                    weightedLightOcclusion += dot(
                        radiance,
                        vec3(0.2126, 0.7152, 0.0722)
                    ) * alphaContribution;
                    weightedAmbientHeight += dot(
                        midpointRadiance,
                        vec3(0.2126, 0.7152, 0.0722)
                    ) * alphaContribution;
                }
            } else if (DebugView == 7) {
                // A camera can start inside the global slab while remaining in
                // clear air beside a compact PUFF. Compare the exact production
                // cap against all quality-profile taps without changing the
                // primary density, alpha, phase, material or integration.
                if (rainFraction > 0.05
                        || cameraInsideCloud
                        || !cameraStartsInsideSlab
                        || LightSteps <= 4) {
                    weightedLightOcclusion = -1.0;
                } else if (weightedLightOcclusion >= 0.0) {
                    vec2 capOpticalDepth = lightMarchOpticalDepthCappedFull(
                        p,
                        cameraStartsInsideSlab
                    ) * ExtinctionScale;
                    float diagnosticFullOcclusion = 0.0;
                    vec3 fullRadiance = evaluateLightingFromOpticalDepth(
                        capOpticalDepth.y,
                        density,
                        h01,
                        cosTheta,
                        saturate(t / MaxRenderDistance),
                        localStorm,
                        materialDarkness,
                        rainFraction,
                        diagnosticFullOcclusion
                    );
                    weightedLightOcclusion += dot(
                        radiance,
                        vec3(0.2126, 0.7152, 0.0722)
                    ) * alphaContribution;
                    weightedAmbientHeight += dot(
                        fullRadiance,
                        vec3(0.2126, 0.7152, 0.0722)
                    ) * alphaContribution;
                }
            } else if (DebugView == 8) {
                // Exclude rain/in-cloud shortcuts and any production light path
                // that reached the exact OD=28 early-out. The remaining pixels
                // necessarily
                // consumed every production segment, so A/B differs only in
                // the spatial quadrature inside those same segments.
                if (rainFraction > 0.05
                        || cameraInsideCloud
                        || (cameraStartsInsideSlab
                            && diagnosticLightOpticalDepth >= 28.0)) {
                    weightedLightOcclusion = -1.0;
                } else if (weightedLightOcclusion >= 0.0) {
                    vec2 refinedOpticalDepth = lightMarchOpticalDepthRefinedPair(
                        p,
                        cameraStartsInsideSlab
                    ) * ExtinctionScale;
                    float diagnosticRefinedOcclusion = 0.0;
                    vec3 refinedPlusRadiance = evaluateLightingFromOpticalDepth(
                        refinedOpticalDepth.x,
                        density,
                        h01,
                        cosTheta,
                        saturate(t / MaxRenderDistance),
                        localStorm,
                        materialDarkness,
                        rainFraction,
                        diagnosticRefinedOcclusion
                    );
                    vec3 refinedMinusRadiance = evaluateLightingFromOpticalDepth(
                        refinedOpticalDepth.y,
                        density,
                        h01,
                        cosTheta,
                        saturate(t / MaxRenderDistance),
                        localStorm,
                        materialDarkness,
                        rainFraction,
                        diagnosticRefinedOcclusion
                    );
                    vec3 refinedRadiance = 0.5
                        * (refinedPlusRadiance + refinedMinusRadiance);
                    weightedLightOcclusion += dot(
                        radiance,
                        vec3(0.2126, 0.7152, 0.0722)
                    ) * alphaContribution;
                    weightedAmbientHeight += dot(
                        refinedRadiance,
                        vec3(0.2126, 0.7152, 0.0722)
                    ) * alphaContribution;
                }
            } else if (DebugView == 9) {
                // Preserve every production segment. Samples whose production
                // cone reached the conditional OD cutoff are excluded because
                // the no-detail counterfactual could otherwise integrate a
                // different segment count.
                if (rainFraction > 0.05
                        || cameraInsideCloud
                        || (cameraStartsInsideSlab
                            && diagnosticLightOpticalDepth >= 28.0)) {
                    weightedLightOcclusion = -1.0;
                } else if (weightedLightOcclusion >= 0.0) {
                    float noDetailOpticalDepth = lightMarchOpticalDepthWithoutDetail(
                        p,
                        cameraStartsInsideSlab
                    ) * ExtinctionScale;
                    float diagnosticNoDetailOpticalDepth = 0.0;
                    vec3 noDetailRadiance = evaluateLightingFromOpticalDepth(
                        noDetailOpticalDepth,
                        density,
                        h01,
                        cosTheta,
                        saturate(t / MaxRenderDistance),
                        localStorm,
                        materialDarkness,
                        rainFraction,
                        diagnosticNoDetailOpticalDepth
                    );
                    weightedLightOcclusion += dot(
                        radiance,
                        vec3(0.2126, 0.7152, 0.0722)
                    ) * alphaContribution;
                    weightedAmbientHeight += dot(
                        noDetailRadiance,
                        vec3(0.2126, 0.7152, 0.0722)
                    ) * alphaContribution;
                }
            } else if (DebugView == 10) {
                // This counterfactual is meaningful only for a dry, complete
                // direct-PUFF FINAL sample. It keeps production density, light
                // optical depth and integration intact and varies solely the
                // height used by underside/ambient lighting.
                float localPuffHeight = 0.0;
                bool localHeightValid = PuffShapeMode == 2
                    && PuffDensityStage == 0
                    && profileId == 3
                    && FunnelCount == 0
                    && MaxPrecipitation <= 0.02
                    && rainFraction <= 0.05
                    && !cameraInsideCloud
                    && dominantDirectPuffHeightAt(p, localPuffHeight);
                if (!localHeightValid) {
                    weightedLightOcclusion = -1.0;
                } else if (weightedLightOcclusion >= 0.0) {
                    float diagnosticLocalHeightOpticalDepth = 0.0;
                    vec3 localHeightRadiance = evaluateLightingFromOpticalDepth(
                        diagnosticLightOpticalDepth,
                        density,
                        localPuffHeight,
                        cosTheta,
                        saturate(t / MaxRenderDistance),
                        localStorm,
                        materialDarkness,
                        rainFraction,
                        diagnosticLocalHeightOpticalDepth
                    );
                    weightedLightOcclusion += dot(
                        radiance,
                        vec3(0.2126, 0.7152, 0.0722)
                    ) * alphaContribution;
                    weightedAmbientHeight += dot(
                        localHeightRadiance,
                        vec3(0.2126, 0.7152, 0.0722)
                    ) * alphaContribution;
                }
            } else if (DebugView == 13) {
                // A dry direct PUFF currently inherits rain shading solely from
                // p.y < the fused weather-map base, even when the field reports
                // no precipitation. Keep A exact and remove that classification
                // as one semantic unit in B; alpha, density and primary weights
                // remain production values.
                float ignoredLocalHeight = 0.0;
                bool dryBaseRainValid = PuffShapeMode == 2
                    && PuffDensityStage == 0
                    && profileId == 3
                    && FunnelCount == 0
                    && MaxPrecipitation <= 0.02
                    && !cameraInsideCloud
                    && dominantDirectPuffHeightAt(p, ignoredLocalHeight);
                if (!dryBaseRainValid) {
                    weightedLightOcclusion = -1.0;
                } else if (weightedLightOcclusion >= 0.0) {
                    vec3 forcedDryRadiance = radiance;
                    if (rainFraction > 0.05) {
                        float forcedDryLightOpticalDepth = 0.0;
                        forcedDryRadiance = sampleLighting(
                            p,
                            density,
                            h01,
                            cosTheta,
                            saturate(t / MaxRenderDistance),
                            localStorm,
                            materialDarkness,
                            0.0,
                            cameraStartsInsideSlab,
                            cameraInsideCloud,
                            forcedDryLightOpticalDepth
                        );
                    }
                    weightedLightOcclusion += dot(
                        radiance,
                        vec3(0.2126, 0.7152, 0.0722)
                    ) * alphaContribution;
                    weightedAmbientHeight += dot(
                        forcedDryRadiance,
                        vec3(0.2126, 0.7152, 0.0722)
                    ) * alphaContribution;
                }
            }

            transmittance *= stepTrans;
        } else {
            lastClearT = t;
            hasClearBracket = true;
            sinceHit++;
        }
        t += stepLength;
    }

    float alpha = saturate(1.0 - transmittance);
    bool currentCloudHit = weightSum > 0.0005;
    float representativeT = currentCloudHit ? weightedT / weightSum : 0.0;
    vec3 relRepresentative = rayDir * representativeT;

    vec4 result = vec4(accumulated, alpha);
    if (currentCloudHit && firstMaterialIsStratus && !cameraInsideCloud) {
        // The weather-surface differential has a small, repeatable DC bias:
        // positive on the top face and negative on the underside. Remove only
        // that daylight/angle-weighted component, then combine it with the
        // world-stable horizontal condensate bands before one soft cap. One
        // saturation stage prevents the independent cues from stacking beyond
        // the intended material range. Highlights stay tighter than shadows so
        // the response adds relief without recreating a white sheet.
        float centeredCue = stratusRelief - stratusReliefBias;
        float combinedDrive = centeredCue * 1.5
            + stratusMaterialRelief * 0.22;
        float responseCap = combinedDrive >= 0.0 ? 0.055 : 0.075;
        float reliefResponse = responseCap
            * tanh(combinedDrive / responseCap);
        reliefResponse *= smoothstep(0.25, 0.75, result.a);

        vec3 straightColour = result.rgb / max(result.a, 0.0001);
        straightColour = clamp(
            straightColour * (1.0 + reliefResponse),
            vec3(0.0),
            vec3(0.98)
        );
        result.rgb = straightColour * result.a;
    }
    float resultDepth = currentCloudHit ? depthAt(relRepresentative) : 1.0;
    float resultDepthDerivative = currentCloudHit ? fwidth(resultDepth) : 0.0;

    // Counter channels are unpremultiplied floating-point integers. The
    // on-demand readback sums them across the target, so this path is never
    // composited or used as temporal history.
    if (DebugView == 22) {
        gl_FragDepth = 1.0;
        fragColor = vec4(
            float(paPrimaryRaySteps),
            float(paDescriptorEvaluations),
            float(paDescriptorTextureFetches),
            float(paAvoidedDescriptorTextureFetches)
        );
        return;
    }
    if (DebugView == 23) {
        gl_FragDepth = 1.0;
        fragColor = vec4(
            float(paLightMarchDensityEvaluations),
            float(paEmptySpaceRejects),
            float(paEarlyTerminations),
            float(paConservativeDescriptorRejects)
        );
        return;
    }

    if (DebugView == 5) {
        if (!currentCloudHit) {
            gl_FragDepth = 1.0;
            fragColor = vec4(0.0);
            return;
        }
        float diagnosticWeight = max(weightSum, 0.000001);
        float meanLightOcclusion = saturate(weightedLightOcclusion / diagnosticWeight);
        float meanAmbientHeight = saturate(weightedAmbientHeight / diagnosticWeight);
        float straightLuminance = max(
            dot(accumulated / max(alpha, 0.0001), vec3(0.2126, 0.7152, 0.0722)),
            0.0
        );
        float radianceTone = saturate(straightLuminance);
        gl_FragDepth = resultDepth;
        fragColor = vec4(
            vec3(meanLightOcclusion, meanAmbientHeight, radianceTone) * alpha,
            alpha
        );
        return;
    }

    if (DebugView == 17) {
        float quadratureAlpha = saturate(
            1.0 - primaryQuadratureTransmittance
        );
        if (!currentCloudHit
                || cameraInsideCloud
                || !primaryQuadratureValid) {
            gl_FragDepth = 1.0;
            fragColor = vec4(0.0);
            return;
        }
        float alphaDelta = abs(alpha - quadratureAlpha);
        gl_FragDepth = resultDepth;
        fragColor = vec4(
            vec3(alpha, quadratureAlpha, alphaDelta) * alpha,
            alpha
        );
        return;
    }

    if (DebugView == 11
            || DebugView == 12
            || DebugView == 14
            || DebugView == 15
            || DebugView == 16
            || DebugView == 18
            || DebugView == 19
            || DebugView == 20) {
        float quadratureAlpha = saturate(
            1.0 - primaryQuadratureTransmittance
        );
        if (!currentCloudHit
                || cameraInsideCloud
                || !primaryQuadratureValid) {
            gl_FragDepth = 1.0;
            fragColor = vec4(0.0);
            return;
        }
        float productionRadiance = saturate(dot(
            accumulated / max(alpha, 0.0001),
            vec3(0.2126, 0.7152, 0.0722)
        ));
        float twoPointRadiance = saturate(dot(
            primaryQuadratureAccumulated / max(quadratureAlpha, 0.0001),
            vec3(0.2126, 0.7152, 0.0722)
        ));
        float estimatorDelta = abs(productionRadiance - twoPointRadiance);
        gl_FragDepth = resultDepth;
        fragColor = vec4(
            vec3(productionRadiance, twoPointRadiance, estimatorDelta) * alpha,
            alpha
        );
        return;
    }

    if (DebugView == 6
            || DebugView == 7
            || DebugView == 8
            || DebugView == 9
            || DebugView == 10
            || DebugView == 13) {
        if (!currentCloudHit || cameraInsideCloud || weightedLightOcclusion < 0.0) {
            gl_FragDepth = 1.0;
            fragColor = vec4(0.0);
            return;
        }
        float diagnosticWeight = max(weightSum, 0.000001);
        float firstRadiance = saturate(weightedLightOcclusion / diagnosticWeight);
        float secondRadiance = saturate(weightedAmbientHeight / diagnosticWeight);
        float estimatorDelta = abs(firstRadiance - secondRadiance);
        gl_FragDepth = resultDepth;
        fragColor = vec4(
            vec3(firstRadiance, secondRadiance, estimatorDelta) * alpha,
            alpha
        );
        return;
    }

    vec4 currentResult = result;
    vec4 diagnosticHistory = vec4(0.0);
    float diagnosticHistoryDepth = 1.0;
    float diagnosticHistoryWeight = 0.0;
    float diagnosticCurrentDepthConfidence = 0.0;
    float diagnosticPreviousDepthConfidence = 0.0;
    // 0.25 unavailable, 0.50 invalid/off-screen projection,
    // 0.75 missing depth and 1.00 both comparisons evaluated.
    float diagnosticDepthSpaceStatus = HistoryValid == 1 ? 0.50 : 0.25;
    // 1 unavailable, 2 off-screen/invalid projection, 3 missing depth,
    // 4 depth mismatch, 5 transmittance mismatch, 6 accepted,
    // 7 stale screen-space history on a current miss, 8 empty current/history.
    float diagnosticHistoryState = HistoryValid == 1 ? 2.0 : 1.0;

    // When the current ray misses, a stationary screen-space history sample is
    // still useful to reveal stale cloud pixels that ordinary hit-only
    // reprojection would hide from the rejection diagnostic.
    if (HistoryValid == 1 && !currentCloudHit) {
        diagnosticHistory = texture(HistorySampler, texCoord);
        diagnosticHistoryDepth = texture(HistoryDepthSampler, texCoord).r;
        diagnosticHistoryState = diagnosticHistory.a > 0.002 ? 7.0 : 8.0;
    }

    // Temporal reprojection: reproject the representative cloud point into the
    // previous frame and blend history when it lands on-screen. History is
    // CLAMPED to the current result +- a margin instead of confidence-rejected:
    // rejection keyed on alpha difference throws history away exactly where
    // the jitter noise is (noise -> alpha delta -> rejection -> permanent
    // grain), while clamping bounds any ghost to a few frames and still
    // integrates the dither everywhere.
    if (currentCloudHit && HistoryValid == 1 && (DebugView != 0 || HistoryBlend > 0.001)) {
        vec3 previousMaterialPoint = relRepresentative
            - vec3(MaterialFrameDelta.x, 0.0, MaterialFrameDelta.y);
        vec4 prevClip = PrevViewProjMat * vec4(previousMaterialPoint, 1.0);
        if (prevClip.w > 0.0001) {
            vec2 prevUv = (prevClip.xy / prevClip.w) * 0.5 + 0.5;
            if (prevUv.x > 0.001 && prevUv.x < 0.999 && prevUv.y > 0.001 && prevUv.y < 0.999) {
                vec4 history = texture(HistorySampler, prevUv);
                float historyDepth = texture(HistoryDepthSampler, prevUv).r;
                diagnosticHistory = history;
                diagnosticHistoryDepth = historyDepth;
                float depthTolerance = max(0.00035, resultDepthDerivative * 6.0);
                float depthConfidence = 1.0 - smoothstep(
                    depthTolerance,
                    depthTolerance * 12.0,
                    abs(historyDepth - resultDepth)
                );
                if (historyDepth >= 0.99999 || resultDepth >= 0.99999) {
                    depthConfidence = 0.0;
                }
                if (DebugView == 4) {
                    float expectedPreviousNdcDepth = prevClip.z
                        / max(abs(prevClip.w), 0.00001);
                    if (expectedPreviousNdcDepth >= -1.0
                            && expectedPreviousNdcDepth <= 1.0) {
                        float expectedPreviousDepth = expectedPreviousNdcDepth * 0.5 + 0.5;
                        float previousDepthConfidence = 1.0 - smoothstep(
                            depthTolerance,
                            depthTolerance * 12.0,
                            abs(historyDepth - expectedPreviousDepth)
                        );
                        if (historyDepth >= 0.99999 || expectedPreviousDepth >= 0.99999) {
                            previousDepthConfidence = 0.0;
                        }
                        diagnosticCurrentDepthConfidence = depthConfidence;
                        diagnosticPreviousDepthConfidence = previousDepthConfidence;
                        diagnosticDepthSpaceStatus =
                            historyDepth >= 0.99999
                                || resultDepth >= 0.99999
                                || expectedPreviousDepth >= 0.99999
                            ? 0.75
                            : 1.0;
                    }
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
                diagnosticHistoryWeight = historyWeight;
                if (historyDepth >= 0.99999 || resultDepth >= 0.99999) {
                    diagnosticHistoryState = 3.0;
                } else if (depthConfidence < 0.5) {
                    diagnosticHistoryState = 4.0;
                } else if (transmittanceConfidence < 0.5) {
                    diagnosticHistoryState = 5.0;
                } else {
                    diagnosticHistoryState = 6.0;
                }
                if (DebugView == 0) {
                    result = mix(result, history, historyWeight);
                }
            }
        }
    }

    if (DebugView == 1) {
        result = currentResult;
    } else if (DebugView == 2) {
        result = diagnosticHistory;
        resultDepth = diagnosticHistoryDepth;
    } else if (DebugView == 3) {
        vec3 diagnosticColor = vec3(0.12, 0.22, 0.92);
        if (diagnosticHistoryState > 1.5 && diagnosticHistoryState < 2.5) {
            diagnosticColor = vec3(0.92, 0.12, 0.82);
        } else if (diagnosticHistoryState > 2.5 && diagnosticHistoryState < 3.5) {
            diagnosticColor = vec3(0.95, 0.58, 0.08);
        } else if (diagnosticHistoryState > 3.5 && diagnosticHistoryState < 4.5) {
            diagnosticColor = vec3(0.95, 0.16, 0.10);
        } else if (diagnosticHistoryState > 4.5 && diagnosticHistoryState < 5.5) {
            diagnosticColor = vec3(0.10, 0.78, 0.92);
        } else if (diagnosticHistoryState > 5.5) {
            diagnosticColor = diagnosticHistoryState < 6.5
                ? mix(
                    vec3(0.78, 0.82, 0.16),
                    vec3(0.12, 0.92, 0.24),
                    saturate(diagnosticHistoryWeight)
                )
                : (diagnosticHistoryState < 7.5
                    ? vec3(1.0, 1.0, 1.0)
                    : vec3(0.08, 0.08, 0.08));
        }
        bool diagnosticVisible = currentCloudHit || diagnosticHistoryState == 7.0;
        result = diagnosticVisible ? vec4(diagnosticColor, 1.0) : vec4(0.0);
        if (!currentCloudHit && diagnosticHistoryState == 7.0) {
            resultDepth = diagnosticHistoryDepth;
        }
    } else if (DebugView == 4) {
        // The production path above continues to consume depthConfidence based
        // on resultDepth. This view only exposes an A/B against the depth of the
        // same representative point in the previous projection. Encoding the
        // continuous confidences avoids inferring the culprit from colours.
        result = currentCloudHit
            ? vec4(
                diagnosticCurrentDepthConfidence,
                diagnosticPreviousDepthConfidence,
                diagnosticDepthSpaceStatus,
                1.0
            )
            : vec4(0.0);
    }

    if (result.a < 0.002) {
        gl_FragDepth = 1.0;
        fragColor = vec4(0.0);
        return;
    }

    gl_FragDepth = resultDepth;
    fragColor = result;
}
