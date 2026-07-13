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
uniform sampler2D StormStructureMapSampler;
uniform sampler2D StormLayerHeightMapSampler;
uniform sampler2D StormTowerMapSampler;
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
    samplePos.xz -= WindVec.xz * WorldTime * (1.0 + saturate(h01) * 0.30);
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

vec4 sampleMorphology(vec2 worldXZ) {
    vec2 uv = (worldXZ - WeatherOrigin) / WeatherExtent;
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        return vec4(0.0);
    }
    // GBA are continuous traits, but R packs a categorical family + envelope
    // role. Filtering it would change both profile and role at a one-texel edge,
    // so fetch only R with nearest semantics.
    vec4 morphology = texture(MorphologyMapSampler, uv);
    ivec2 size = textureSize(MorphologyMapSampler, 0);
    ivec2 coord = clamp(ivec2(floor(uv * vec2(size))), ivec2(0), size - ivec2(1));
    morphology.r = texelFetch(MorphologyMapSampler, coord, 0).r;
    return morphology;
}

vec4 sampleStormStructure(vec2 worldXZ) {
    vec2 uv = (worldXZ - WeatherOrigin) / WeatherExtent;
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        return vec4(0.0);
    }
    vec4 structure = texture(StormStructureMapSampler, uv);
    float edgeDistance = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
    return structure * smoothstep(0.0, 0.055, edgeDistance);
}

vec4 sampleStormLayerHeights(vec2 worldXZ) {
    vec2 uv = (worldXZ - WeatherOrigin) / WeatherExtent;
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        return vec4(0.0);
    }
    vec4 heights = texture(StormLayerHeightMapSampler, uv);
    float edgeDistance = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
    return heights * smoothstep(0.0, 0.055, edgeDistance);
}

vec4 sampleStormTowers(vec2 worldXZ) {
    vec2 uv = (worldXZ - WeatherOrigin) / WeatherExtent;
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        return vec4(0.0);
    }
    vec4 towers = texture(StormTowerMapSampler, uv);
    float edgeDistance = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
    return towers * smoothstep(0.0, 0.055, edgeDistance);
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
    return int(clamp(floor(morphology.r * 63.0 + 0.5), 0.0, 63.0));
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
        float topEdge = mix(0.76, 0.90, verticalDevelopment);
        float crown = smoothstep(0.36, 0.94, h);
        float supportThreshold = mix(0.07, 0.38, crown * crown);
        float supportBody = smoothstep(
            supportThreshold,
            supportThreshold + 0.24,
            footprintCoverage
        );
        float materialSignal = baseCarrier * 0.46
            + lowFbm * 0.28
            + condensate * 0.14
            + coverage * 0.12;
        float material = mix(
            0.74,
            1.0,
            smoothstep(0.18, 0.78, materialSignal)
        );
        return verticalBand(h, 0.018, topEdge)
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

float rainShaftDensityAt(vec3 p, float mipBias) {
    if (MaxPrecipitation <= 0.02) {
        return 0.0;
    }

    float slabSpan = max(SlabTopY - SlabBaseY, 1.0);
    vec4 localWeather = sampleWeather(p.xz);
    vec4 localMorphology = sampleMorphology(p.xz);
    float localBaseY = SlabBaseY + localWeather.g * slabSpan;
    if (p.y >= localBaseY) {
        return 0.0;
    }

    vec2 windDir = cloudWindDirection();
    float localFallDistance = localBaseY - p.y;
    vec2 sourceXZ = p.xz - windDir * localFallDistance * 0.14;
    vec4 weather = sampleWeather(sourceXZ);
    vec4 morphology = sampleMorphology(sourceXZ);
    float coverage = smoothstep(0.012, 0.42, saturate(weather.r * CoverageMul));
    float precipitation = morphology.a;
    int profileId = cloudProfileId(morphology);
    float baseY = SlabBaseY + weather.g * slabSpan;
    if (precipitation <= 0.02 || coverage <= 0.01 || p.y >= baseY) {
        return 0.0;
    }

    float condensate = saturate(
        coverage * 0.36 + weather.a * 0.28 + precipitation * 0.24 + morphology.b * 0.12
    );

    float familyStrength = (profileId == 4 || profileId == 5 || profileId == 7)
        ? 1.0
        : 0.44;
    float fallDistance = baseY - p.y;
    float maxDepth = mix(48.0, 180.0,
        saturate(precipitation * 0.70 + condensate * 0.30));
    float depth01 = fallDistance / max(maxDepth, 1.0);
    if (depth01 >= 1.0) {
        return 0.0;
    }

    float reach = clamp(0.34 + condensate * 0.38 + precipitation * 0.36, 0.34, 1.0);
    float attached = smoothstep(0.02, 0.18, depth01);
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
    float precipitationCore = smoothstep(0.30, 0.75, coverage);
    return precipitationCore
        * precipitation
        * familyStrength
        * attached
        * tail
        * mix(0.06, 0.14, precipitation)
        * streaks;
}

float cloudDensity(
        vec3 p,
        float mipBias,
        bool useDetail,
        bool nearCamera,
        bool includePrecipitation) {
    vec4 weather = sampleWeather(p.xz);
    vec4 morphology = sampleMorphology(p.xz);
    // Weather-map coverage already includes the cloudlet density. Its normal
    // spawned-field range is roughly 0.08..0.35; treating 0.92 as the full
    // point erased those clouds before the raymarch ever saw them.
    float coverageSignal = saturate(weather.r * CoverageMul);
    float energy = weather.a;
    int profileId = cloudProfileId(morphology);
    int envelopeRole = cloudEnvelopeRole(morphology);
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

    // The exact severe maps are irrelevant to all other families. Keep both
    // fetches behind this spatially coherent branch.
    vec4 stormStructure = stormProfile
        ? sampleStormStructure(p.xz)
        : vec4(0.0);
    vec4 stormLayerHeights = stormProfile
        ? sampleStormLayerHeights(p.xz)
        : vec4(0.0);
    vec4 stormTowers = stormProfile
        ? sampleStormTowers(p.xz)
        : vec4(0.0);

    bool precipitationCandidate = includePrecipitation
        && MaxPrecipitation > 0.02
        && p.y < SlabBaseY + 48.0;
    if (coverage <= 0.008 && funnel <= 0.001 && !precipitationCandidate) {
        return 0.0;
    }

    float cloud = 0.0;
    if (h01 > -0.02 && h01 < 1.02 && coverage > 0.008) {
        float anvil = stormProfile
            ? smoothstep(0.62, 0.94, saturate(h01))
                * energy * (0.20 + verticalDevelopment * 0.42)
            : 0.0;
        float coverageMod = saturate(coverage * (1.02 + anvil * 0.30));

        // Sampling p - velocity*time makes the procedural material advect in
        // the same positive direction as the field envelope. The old plus
        // sign moved the texture against its cloud and the second time-only
        // detail animation amplified that sliding.
        vec3 samplePos = p;
        samplePos.xz -= WindVec.xz * WorldTime * (1.0 + saturate(h01) * 0.30);

        // Cloudlets are tens to low hundreds of blocks wide. The old 0.0016
        // scale sampled an almost constant value across an entire cloudlet,
        // producing one smooth oval instead of separate billows.
        float baseNoiseScale = 0.0052;
        if (sheetProfile) {
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
        float rolePresence = max(
            max(max(stormStructure.r, stormStructure.b), stormTowers.r),
            stormLayerHeights.g
        );
        float layerHeightPresence = max(
            max(stormStructure.g, stormStructure.a),
            max(
                max(stormLayerHeights.r, max(stormLayerHeights.b, stormLayerHeights.a)),
                max(stormTowers.g, stormTowers.b)
            )
        );
        bool useStormStructure = stormProfile
            && rolePresence > 0.004
            && layerHeightPresence > 0.0001;
        float macroShape = useStormStructure
            ? stormStructureShape(
                profileId,
                stormStructure,
                stormLayerHeights,
                stormTowers,
                p.y,
                baseY,
                topY,
                slabSpan,
                verticalDevelopment,
                condensate,
                baseCarrier,
                lowFbm,
                directionalCarrier
            )
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
        float envelopeCoverage = useStormStructure
            ? mix(0.72, 1.0, coverageMod)
            : coverageMod;
        if (!useStormStructure && profileId == 1) {
            // A unioned sheet map encodes support as coverage, not as a second
            // density multiplier. The square-root response keeps the interior
            // continuous while the explicit smooth gate preserves a soft,
            // deterministic field boundary.
            envelopeCoverage = sqrt(max(coverageMod, 0.0))
                * smoothstep(0.008, 0.12, coverageMod);
        }
        cloud = macroShape * envelopeCoverage;

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
            float erosion = 0.26;
            if (profileId == 1 || profileId == 5) {
                erosion = 0.10;
            } else if (profileId == 2) {
                erosion = 0.17;
            } else if (stormProfile) {
                erosion = envelopeRole == 5 ? 0.26 : 0.22;
            } else if (profileId == 6) {
                erosion = 0.06;
            }
            // Noise adds detail at exposed edges instead of drilling holes
            // through the protected meteorological core.
            float edgeExposure = 1.0 - smoothstep(0.26, 0.72, cloud);
            if (profileId == 6) {
                edgeExposure = 1.0;
            }
            cloud = max(cloud - (1.0 - detailFbm) * erosion * edgeExposure, 0.0);
        }

        // Storm cells hold more condensed water low in the cloud.
        cloud *= mix(1.0, 1.18, energy * (1.0 - saturate(h01)) * 0.6);
        cloud *= mix(0.90, 1.10, condensate);
        cloud *= 1.0 + precipitation * (1.0 - saturate(h01)) * 0.32;
        if (profileId == 5) {
            cloud *= 1.0 + precipitation * 0.12;
        }
        cloud *= smoothstep(0.010, 0.080, coverageMod);
    }

    float rainShaft = includePrecipitation
        ? rainShaftDensityAt(p, mipBias)
        : 0.0;
    float familyDensityScale = 0.88;
    if (profileId == 1) {
        familyDensityScale = 0.62;
    } else if (profileId == 2) {
        familyDensityScale = 0.76;
    } else if (profileId == 4) {
        familyDensityScale = 0.72;
    } else if (profileId == 5) {
        familyDensityScale = 0.72;
    } else if (profileId == 6) {
        familyDensityScale = 0.78;
    } else if (profileId == 7) {
        familyDensityScale = 0.74;
    }
    float density = (max(cloud, 0.0) * familyDensityScale + rainShaft) * DensityMul;
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
        float density = cloudDensity(pos + offset, float(i) * 0.6, i < 2, false, false);
        opticalDepth += density * stepLength;
        if (cameraStartsInsideSlab && opticalDepth * ExtinctionScale >= 28.0) {
            break;
        }
        stepLength *= 1.42;
    }
    return opticalDepth;
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
        bool cameraInsideCloud) {
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

    float combinedStorm = saturate(max(StormDarkening * 0.58, localStorm * 0.72));
    float undersideShade = saturate(
        (0.18 + materialDarkness * 0.82)
            * pow(1.0 - saturate(h01ForAmbient), 0.70)
            * (0.46 + localDensity * 0.72)
    );
    float directTransmission = exp(-opticalDepth);
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

    vec3 rainTint = mix(vec3(0.76, 0.80, 0.86), vec3(0.48, 0.54, 0.64), NightFactor);
    radiance *= mix(vec3(1.0), rainTint, rainFraction * 0.50);
    radiance = max(radiance, vec3(0.0));
    // A peak normalization mapped every energetic sample to exactly white.
    // Filmic exponential compression preserves highlight ordering and colour
    // while keeping the final LDR composite bounded.
    float toneExposure = mix(1.30, 1.48, NightFactor);
    return vec3(1.0) - exp(-radiance * toneExposure);
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
    // The CPU already knows whether any rendered cell precipitates, so extend
    // the slab once for that frame and let the coverage pre-test reject clear
    // rays before the expensive march.
    return mix(0.0, 180.0, smoothstep(0.02, 0.85, MaxPrecipitation));
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

    // Coverage pre-test: sample the weather map along the ray and skip fully
    // clear rays. This is the biggest saver on clear days.
    bool anyCoverage = FunnelCount > 0 || CoveragePretestEnabled == 0;
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

    float originJitterDistance = paRayOriginJitterDistance(
        fineStep,
        cameraStartsInsideSlab,
        cameraInsideCloud
    );
    float t = t0 + blue * originJitterDistance;
    float transmittance = 1.0;
    vec3 accumulated = vec3(0.0);
    float weightedT = 0.0;
    float weightSum = 0.0;
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
            break;
        }
        bool fine = sinceHit < 6;
        float distanceGrowth = 1.0 + (t / max(MaxRenderDistance, 1.0)) * 2.2;
        float stepLength = (fine ? fineStep : coarseStep) * distanceGrowth;

        vec3 p = CameraPos + rayDir * t;
        // The second near-camera detail octave cannot be resolved through a
        // dense whiteout and doubles its 3-D detail fetches. Keep it unchanged
        // for every exterior view and omit it only when the canonical camera
        // density confirms the camera is inside rendered cloud material.
        bool nearCamera = t < 220.0 && !cameraInsideCloud;
        float density = cloudDensity(p, 0.0, DetailQuality > 0, nearCamera, true);

        if (density > 0.0008) {
            vec4 weather = sampleWeather(p.xz);
            vec4 morphology = sampleMorphology(p.xz);
            float slabSpan = max(SlabTopY - SlabBaseY, 1.0);
            float baseY = SlabBaseY + weather.g * slabSpan;
            float topY = SlabBaseY + weather.b * slabSpan;
            bool precipitationSample = FunnelCount == 0
                && MaxPrecipitation > 0.02
                && p.y < baseY;
            if (!fine && !precipitationSample) {
                // Entered cloud with a coarse stride: back up and resolve fine.
                // Clamp to the ray entry point: for an in-cloud camera t0 is
                // zero, and the first coarse hit previously stepped behind the
                // camera, corrupting representative depth and reprojection.
                t = max(t0, t - stepLength * 0.6);
                sinceHit = 0;
                continue;
            }
            // Shafts stay on coarse strides. Their broad envelope and streak
            // noise do not need cloud-surface resolution, and keeping the fine
            // state here multiplies their cost by the full 180-block depth.
            sinceHit = precipitationSample ? 100 : 0;

            float h01 = saturate((p.y - baseY) / max(topY - baseY, 2.0));
            int profileId = cloudProfileId(morphology);
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
            float rainFraction = precipitationSample || p.y < baseY
                ? saturate(0.25 + max(morphology.a, MaxPrecipitation) * 0.75)
                : 0.0;

            float extinction = density * ExtinctionScale;
            float stepTrans = exp(-extinction * stepLength);
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
                cameraInsideCloud
            );

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

    // Temporal reprojection: reproject the representative cloud point into the
    // previous frame and blend history when it lands on-screen. History is
    // CLAMPED to the current result +- a margin instead of confidence-rejected:
    // rejection keyed on alpha difference throws history away exactly where
    // the jitter noise is (noise -> alpha delta -> rejection -> permanent
    // grain), while clamping bounds any ghost to a few frames and still
    // integrates the dither everywhere.
    if (currentCloudHit && HistoryValid == 1 && HistoryBlend > 0.001) {
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

    if (result.a < 0.002) {
        gl_FragDepth = 1.0;
        fragColor = vec4(0.0);
        return;
    }

    gl_FragDepth = resultDepth;
    fragColor = result;
}
