#version 150

#moj_import <projectatmosphere:cloud_weather_footprint.glsl>

// Rasterizes interpolated cloud cells into the weather map.
// Output channels (RGBA8):
//   R = coverage (smooth union of all cell footprints + regional layer)
//   G = cloud base height, normalized inside [SlabBaseY, SlabTopY]
//   B = cloud top height, normalized inside [SlabBaseY, SlabTopY]
//   A = energy (storminess: darker bases, stronger erosion, anvil spread)

uniform vec2 WeatherOrigin;   // world XZ of texel (0,0)
uniform float WeatherExtent;  // world size covered by the full map
uniform float SlabBaseY;
uniform float SlabTopY;
uniform float RegionalCoverage; // 0..1 stratus/overcast layer amount
uniform float RegionalEnergy;
uniform float WeatherCoverageScale;
uniform int SentinelHeightsEnabled;
uniform float WorldTime;
uniform int CellCount;

// Uploaded manually (glUniform4fv), not through the vanilla uniform system:
// CellPosRadius[i] = (worldX, worldZ, radiusMajor, radiusMinor)
// CellShape[i]     = (orientationRadians, base01, top01, edgeSoftness)
// CellMedia[i]     = (density, energy, seed01, adaptiveFootprintScale)
// CellMorphology[i]= (typeProfile, materialDarkness, verticalDevelopment, humidity)
// CellDynamics[i]  = (anvilStrength, precipitationIntensity, lifecycle/tag,
//                     typeProfile + envelopeRole/16)
const int MAX_CELLS = 96;
uniform vec4 CellPosRadius[MAX_CELLS];
uniform vec4 CellShape[MAX_CELLS];
uniform vec4 CellMedia[MAX_CELLS];
uniform vec4 CellDynamics[MAX_CELLS];

in vec2 texCoord;
out vec4 fragColor;

float saturate(float v) {
    return clamp(v, 0.0, 1.0);
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm2(vec2 p) {
    return valueNoise(p) * 0.65 + valueNoise(p * 2.13 + vec2(19.7)) * 0.35;
}

float profileWeight(float profile, float expected) {
    return 1.0 - smoothstep(0.20, 0.90, abs(profile - expected));
}

void main() {
    vec2 worldXZ = WeatherOrigin + texCoord * WeatherExtent;
    float slabSpan = max(SlabTopY - SlabBaseY, 1.0);

    // Stable world-space signal for boundary detail only. The macro footprint
    // itself must remain the analytic per-cell ellipse: the former fixed
    // 42-block displacement could exceed a young lobe's complete radius and
    // split or stretch its silhouette into fins.
    vec2 warp = vec2(
        fbm2(worldXZ * 0.010 + vec2(3.7, 9.1)),
        fbm2(worldXZ * 0.010 + vec2(-7.3, 1.9))
    ) - 0.5;

    float coverage = 0.0;
    float baseAccum = 0.0;
    float topAccum = 0.0;
    float energyAccum = 0.0;
    float weightAccum = 0.0;
    float severeConnector = 0.0;
    float severeBaseMin = 1.0;
    float severeTopMax = 0.0;
    float severeBaseSupport = 0.0;
    float severeTopSupport = 0.0;
    float carrierSupport = 0.0;
    float carrierBase = 1.0;
    float carrierTop = 0.0;
    float cumulusBaseMin = 1.0;
    float cumulusTopMax = 0.0;
    float cumulusDominantWeight = -1.0;
    float cumulusDominantBase = 1.0;
    float cumulusDominantTop = 0.0;
    float cumulusEnergyAccum = 0.0;
    float cumulusWeightAccum = 0.0;
    float stratusSurfaceWeight = 0.0;
    float stratusSurfaceBaseAccum = 0.0;
    float stratusSurfaceTopAccum = 0.0;
    float dominantCategoryWeight = -1.0;
    float dominantCategoryProfile = 0.0;

    for (int i = 0; i < MAX_CELLS; i++) {
        if (i >= CellCount) {
            break;
        }
        vec4 posRadius = CellPosRadius[i];
        vec4 shape = CellShape[i];
        vec4 media = CellMedia[i];
        vec4 dynamics = CellDynamics[i];
        float packedProfileRole = clamp(dynamics.w, 0.0, 7.999);
        float profile = floor(packedProfileRole + 0.0001);
        int envelopeRole = int(floor(fract(packedProfileRole) * 16.0 + 0.5));
        float stratus = profileWeight(profile, 1.0);
        float stratocumulus = profileWeight(profile, 2.0);
        float cumulus = profileWeight(profile, 3.0);
        float storm = max(profileWeight(profile, 4.0), profileWeight(profile, 7.0));
        float nimbostratus = profileWeight(profile, 5.0);
        float cirrus = profileWeight(profile, 6.0);
        float sheet = max(stratus, nimbostratus);
        bool macroCarrier = dynamics.z < -0.5;
        bool envelopeOnly = macroCarrier && envelopeRole == 6;

        // Sheet cloudlets carry randomized local heights. Averaging dozens of
        // overlapping tiles erased that variance and reduced stratus to one
        // flat slab. Every field already has one stable macro carrier, even
        // when it becomes coverage-invisible at higher LOD. Use that carrier
        // solely as a field-local surface controller: it moves and rotates with
        // the field, is seeded by the field, and is independent of the number
        // of detail cloudlets accepted this frame.
        if (macroCarrier && stratus > 0.5) {
            vec2 anchorDelta = worldXZ - posRadius.xy;
            float anchorCos = cos(-shape.x);
            float anchorSin = sin(-shape.x);
            vec2 anchorLocal = vec2(
                anchorDelta.x * anchorCos - anchorDelta.y * anchorSin,
                anchorDelta.x * anchorSin + anchorDelta.y * anchorCos
            );
            vec2 anchorRadius = max(posRadius.zw * 1.35, vec2(1.0));
            float anchorR = length(anchorLocal / anchorRadius);
            float anchorSupport = 1.0 - smoothstep(0.78, 1.0, anchorR);
            if (anchorSupport > 0.001) {
                vec2 seedOffset = vec2(
                    11.3 + media.z * 173.0,
                    -7.1 + media.z * 97.0
                );
                // Long wavelength along the wind-facing local X axis and a
                // shorter cross-wind wavelength form broad stratiform bands,
                // not isolated cellular bumps.
                float broadBand = fbm2(
                    vec2(anchorLocal.x * 0.0032, anchorLocal.y * 0.0095)
                        + seedOffset
                );
                float crossBand = valueNoise(
                    vec2(
                        anchorLocal.x * 0.0054 + anchorLocal.y * 0.0017,
                        anchorLocal.y * 0.0046 - anchorLocal.x * 0.0013
                    ) + seedOffset * 1.73 + vec2(31.7, -18.9)
                );
                float baseSignal = saturate(broadBand * 0.72 + crossBand * 0.28);
                float topSignal = saturate(broadBand * 0.34 + crossBand * 0.66);
                float anchorSpan = max(shape.z - shape.y, 2.0 / slabSpan);
                float surfaceBase = shape.y
                    + anchorSpan * mix(0.12, 0.32, baseSignal);
                float surfaceTop = shape.y
                    + anchorSpan * mix(0.65, 0.90, topSignal);
                surfaceTop = max(surfaceTop, surfaceBase + anchorSpan * 0.34);
                float surfaceWeight = anchorSupport * anchorSupport;
                stratusSurfaceBaseAccum += surfaceBase * surfaceWeight;
                stratusSurfaceTopAccum += surfaceTop * surfaceWeight;
                stratusSurfaceWeight += surfaceWeight;
            }
        }
        float footprintScale = max(media.w, 0.001) * max(WeatherCoverageScale, 0.001);
        vec2 scaledRadius = max(posRadius.zw * footprintScale, vec2(1.0));
        vec2 delta = worldXZ - posRadius.xy;
        float maxRadius = max(scaledRadius.x, scaledRadius.y) * 1.45;
        if (dot(delta, delta) > maxRadius * maxRadius) {
            continue;
        }

        float cosO = cos(-shape.x);
        float sinO = sin(-shape.x);
        vec2 local = vec2(
            delta.x * cosO - delta.y * sinO,
            delta.x * sinO + delta.y * cosO
        );
        vec2 normalized = local / scaledRadius;
        float r = length(normalized);

        // Per-cell angular harmonics seeded by the cell id give each cell a
        // stable, unique lobed silhouette that moves rigidly with the cell.
        float theta = atan(normalized.y, normalized.x);
        float seed = media.z * 6.2831853;
        float lobeStrength = 1.0;
        lobeStrength = mix(lobeStrength, 0.20, sheet);
        lobeStrength = mix(lobeStrength, 0.72, stratocumulus);
        // Authoritative PA clusters already are the cumulus lobes. Strong
        // per-primitive harmonics added a second synthetic topology and formed
        // symmetric triangular fins when viewed from above.
        lobeStrength = mix(lobeStrength, 0.22, cumulus);
        lobeStrength = mix(lobeStrength, 0.76, storm);
        lobeStrength = mix(lobeStrength, 0.34, cirrus);
        float lobes = 1.0
            + 0.16 * lobeStrength * sin(theta * 2.0 + seed * 3.1)
            + 0.11 * lobeStrength * sin(theta * 3.0 + seed * 7.7)
            + 0.07 * lobeStrength * sin(theta * 5.0 + seed * 13.9);
        r /= max(lobes, 0.35);
        r += paSevereContourErosion(
            warp,
            theta,
            seed,
            int(profile),
            envelopeRole,
            r
        );

        float edgeStart = mix(0.78, 0.42, saturate(shape.w));
        edgeStart = mix(edgeStart, 0.76, sheet);
        edgeStart = mix(edgeStart, 0.62, stratocumulus);
        edgeStart = mix(edgeStart, 0.58, cirrus);
        float footprint = 1.0 - smoothstep(edgeStart, 1.0, r);
        // Lifecycle is continuous: forming cells condense, mature cells keep
        // their full mass and dissipating cells erode without a hard pop.
        float lifecycle = saturate(macroCarrier ? -dynamics.z - 1.0 : dynamics.z);
        float lifecycleEnvelope = lifecycle < 0.5
            ? mix(0.30, 1.0, lifecycle * 2.0)
            : mix(1.0, 0.30, (lifecycle - 0.5) * 2.0);
        float precipitationPacking = 1.0 + saturate(dynamics.y) * 0.16;
        float categoricalCoverage = envelopeOnly
            ? 0.0
            : footprint * saturate(media.x) * lifecycleEnvelope;
        float categoryWeight = categoricalCoverage
            * categoricalCoverage * categoricalCoverage;
        if (categoryWeight > dominantCategoryWeight) {
            dominantCategoryWeight = categoryWeight;
            dominantCategoryProfile = profile;
        }
        float cellCoverage = footprint * saturate(media.x) * lifecycleEnvelope * precipitationPacking;
        if (cellCoverage <= 0.002) {
            continue;
        }

        float visibleCellCoverage = envelopeOnly ? 0.0 : cellCoverage;
        coverage = 1.0 - (1.0 - coverage) * (1.0 - visibleCellCoverage);
        // General families retain a broad blend for continuous decks. PUFF uses
        // a separate cubic reduction below, but only when it also wins the
        // categorical profile decision for this texel.
        float weight = cellCoverage * cellCoverage;
        float carrierEnvelopeBoost = 1.0;
        carrierEnvelopeBoost = mix(carrierEnvelopeBoost, 7.0, sheet);
        carrierEnvelopeBoost = mix(carrierEnvelopeBoost, 5.0, stratocumulus);
        carrierEnvelopeBoost = mix(carrierEnvelopeBoost, 2.0, cumulus);
        carrierEnvelopeBoost = mix(carrierEnvelopeBoost, 10.0, storm);
        carrierEnvelopeBoost = mix(carrierEnvelopeBoost, 6.0, cirrus);
        if (macroCarrier) {
            weight *= carrierEnvelopeBoost;
        }
        // The old symmetric 42% collapse made every family ellipsoidal. Bases
        // now remain nearly level for layers and convection while rounded
        // cumulus tops, thin cirrus edges and storm anvils retain distinct
        // upper envelopes.
        float edge01 = 1.0 - footprint;
        float cellSpan = max(shape.z - shape.y, 2.0 / slabSpan);
        float baseCollapse = 0.08;
        float topCollapse = 0.36;
        baseCollapse = mix(baseCollapse, 0.012, sheet);
        topCollapse = mix(topCollapse, nimbostratus > 0.5 ? 0.14 : 0.18, sheet);
        baseCollapse = mix(baseCollapse, 0.035, stratocumulus);
        topCollapse = mix(topCollapse, 0.28, stratocumulus);
        baseCollapse = mix(baseCollapse, 0.025, cumulus);
        topCollapse = mix(topCollapse, 0.48, cumulus);
        if (storm > 0.5) {
            if (envelopeRole == 2) {
                // Wide storm base: bottom stays almost level; only the top
                // rounds into the connecting updraft.
                baseCollapse = 0.020;
                topCollapse = 0.30;
            } else if (envelopeRole == 5) {
                // Thin anvil/outflow sheet with a soft symmetric edge.
                baseCollapse = 0.24;
                topCollapse = 0.24;
            } else if (envelopeRole == 3 || envelopeRole == 4) {
                // Core/tower cloudlets must narrow strongly with height. The
                // old 16% top collapse left nearly vertical cylinder walls.
                baseCollapse = 0.12;
                topCollapse = 0.70;
            } else if (macroCarrier) {
                baseCollapse = 0.08;
                topCollapse = 0.56;
            } else {
                baseCollapse = 0.08;
                topCollapse = 0.48;
            }
        }
        baseCollapse = mix(baseCollapse, 0.22, cirrus);
        topCollapse = mix(topCollapse, 0.22, cirrus);
        float edgeLift = edge01 * cellSpan * baseCollapse;
        float edgeDrop = edge01 * cellSpan * topCollapse;
        if (cumulus > 0.5) {
            vec2 curvedRange = paCumulusCurvedLayerRange(
                envelopeRole,
                shape.y,
                shape.z,
                r
            );
            edgeLift = curvedRange.x - shape.y;
            edgeDrop = shape.z - curvedRange.y;
        }
        float localBase = shape.y + edgeLift;
        float localTop = shape.z - edgeDrop;
        if (envelopeOnly) {
            float support = smoothstep(0.02, 0.24, cellCoverage);
            if (support > carrierSupport) {
                carrierSupport = support;
                // The invisible carrier is only an internal continuity bridge.
                // Reusing its full field-height interval rebuilt the monolithic
                // cuboid that CARRIER_ONLY was introduced to remove.
                float carrierSpan = max(localTop - localBase, 2.0 / slabSpan);
                carrierBase = localBase + carrierSpan * 0.16;
                carrierTop = localBase + carrierSpan * 0.84;
            }
            continue;
        }
        if (cumulus > 0.5) {
            float cumulusWeight = cellCoverage
                * cellCoverage * cellCoverage;
            if (cumulusWeight > cumulusDominantWeight) {
                cumulusDominantWeight = cumulusWeight;
                cumulusDominantBase = localBase;
                cumulusDominantTop = localTop;
            }
            // Collapse each interval toward its centre in the weak footprint
            // fringe before taking extrema. Strong lobe cores retain their
            // real bounds; a barely-covered high lobe cannot raise a needle.
            float footprintCore = smoothstep(0.08, 0.46, footprint);
            float massGate = smoothstep(0.002, 0.025, saturate(cellCoverage));
            float unionSupport = footprintCore * massGate;
            if (unionSupport > 0.001) {
                // Collapse weak candidates toward neutral extrema, not toward
                // the opposite endpoint of their own interval. A faint upper
                // lobe fringe previously retained its already-high base and
                // could raise the global crown when a different lobe supplied
                // the global union blend.
                float supportedBase = mix(1.0, localBase, unionSupport);
                float supportedTop = mix(0.0, localTop, unionSupport);
                cumulusBaseMin = min(cumulusBaseMin, supportedBase);
                cumulusTopMax = max(cumulusTopMax, supportedTop);
            }
            cumulusEnergyAccum += max(
                media.y,
                max(dynamics.x * 0.85, dynamics.y * 0.72)
            ) * cumulusWeight;
            cumulusWeightAccum += cumulusWeight;
        }
        baseAccum += localBase * weight;
        topAccum += localTop * weight;
        energyAccum += max(media.y, max(dynamics.x * 0.85, dynamics.y * 0.72)) * weight;
        weightAccum += weight;

        // Severe fields need one connected central column, not a global fill
        // under every anvil texel. Roles are packed in Dynamics.w: the base may
        // lower the envelope, the anvil may raise it, and only macro/core/tower
        // coverage authorizes joining the two at this texel. Outside that
        // connector, anvil overhangs retain their own thin interval.
        if (storm > 0.5) {
            float roleSupport = smoothstep(0.025, 0.14, cellCoverage);
            bool connectorRole = envelopeRole == 1 || envelopeRole == 3 || envelopeRole == 4;
            bool baseRole = connectorRole || envelopeRole == 2;
            bool topRole = connectorRole || envelopeRole == 5;
            if (connectorRole) {
                severeConnector = max(severeConnector, cellCoverage);
            }
            if (roleSupport > 0.05 && baseRole) {
                severeBaseMin = min(severeBaseMin, localBase);
                severeBaseSupport = max(severeBaseSupport, roleSupport);
            }
            if (roleSupport > 0.05 && topRole) {
                severeTopMax = max(severeTopMax, localTop);
                severeTopSupport = max(severeTopSupport, roleSupport);
            }
        }
    }

    // Region-scale stratus/overcast layer: broad, flat, low-energy coverage
    // driven by regional weather instead of individual cells.
    float regional = saturate(RegionalCoverage);
    if (regional > 0.01) {
        float sheetNoise = fbm2(worldXZ * 0.0011 + vec2(WorldTime * 0.00021, WorldTime * 0.00013));
        float sheet = saturate((sheetNoise - (1.0 - regional * 0.9)) * 3.2) * regional;
        if (sheet > 0.002) {
            float sheetBase = 0.30 + sheetNoise * 0.05;
            float sheetTop = sheetBase + 0.12 + regional * 0.08;
            coverage = 1.0 - (1.0 - coverage) * (1.0 - sheet * 0.85);
            // Same sharpened weighting as the cloudlets so the sheet only
            // dictates base/top where it actually dominates local coverage.
            float sheetWeight = sheet * 0.6;
            float weight = sheetWeight * sheetWeight * sheetWeight;
            if (weight > dominantCategoryWeight) {
                dominantCategoryWeight = weight;
                dominantCategoryProfile = 1.0;
            }
            baseAccum += sheetBase * weight;
            topAccum += sheetTop * weight;
            energyAccum += RegionalEnergy * weight;
            weightAccum += weight;
        }
    }

    // Old mode preserves the original fringe sentinel threshold exactly.
    // New mode only uses the sentinel when no cloudlet contributed at all;
    // even a tiny positive cubed weight still carries a valid weighted
    // base/top pair and is numerically safe to normalize.
    if (weightAccum <= 0.0
            || (SentinelHeightsEnabled == 1
                && (weightAccum <= 0.0000005 || coverage <= 0.002))) {
        fragColor = vec4(0.0, 0.35, 0.45, 0.0);
        return;
    }

    bool useCumulusEnvelope = dominantCategoryProfile > 2.5
        && dominantCategoryProfile < 3.5
        && cumulusWeightAccum > 0.0;
    float base01 = clamp(useCumulusEnvelope
        ? min(cumulusDominantBase, cumulusBaseMin)
        : baseAccum / weightAccum, 0.0, 1.0);
    float top01 = clamp(useCumulusEnvelope
        ? max(cumulusDominantTop, cumulusTopMax)
        : topAccum / weightAccum, 0.0, 1.0);
    bool useStratusSurface = dominantCategoryProfile > 0.5
        && dominantCategoryProfile < 1.5
        && stratusSurfaceWeight > 0.0;
    if (useStratusSurface) {
        float surfaceInfluence = smoothstep(0.02, 0.25, stratusSurfaceWeight);
        float surfaceBase = stratusSurfaceBaseAccum / stratusSurfaceWeight;
        float surfaceTop = stratusSurfaceTopAccum / stratusSurfaceWeight;
        base01 = mix(base01, surfaceBase, surfaceInfluence);
        top01 = mix(top01, surfaceTop, surfaceInfluence);
    }
    float severeLink = smoothstep(0.035, 0.16, severeConnector);
    if (severeLink > 0.0) {
        float carrierLink = severeLink
            * smoothstep(0.05, 0.70, carrierSupport)
            * 0.42;
        base01 = mix(base01, min(base01, carrierBase), carrierLink);
        top01 = mix(top01, max(top01, carrierTop), carrierLink);
        float baseLink = severeLink
            * smoothstep(0.05, 0.70, severeBaseSupport)
            * 0.60;
        float topLink = severeLink
            * smoothstep(0.05, 0.70, severeTopSupport)
            * 0.60;
        base01 = mix(base01, min(base01, severeBaseMin), baseLink);
        top01 = mix(top01, max(top01, severeTopMax), topLink);
    }
    top01 = max(top01, base01 + 2.0 / slabSpan);
    float energy = clamp(useCumulusEnvelope
        ? cumulusEnergyAccum / cumulusWeightAccum
        : energyAccum / weightAccum, 0.0, 1.0);
    if (coverage <= 0.002) {
        // Coverage still gates density. In the new comparison mode, retain
        // real fringe heights so bilinear filtering cannot pull neighboring
        // cloud texels toward the fixed empty-map sentinel slab.
        fragColor = SentinelHeightsEnabled == 1
            ? vec4(0.0, 0.35, 0.45, 0.0)
            : vec4(0.0, base01, top01, 0.0);
        return;
    }
    fragColor = vec4(saturate(coverage), base01, top01, energy);
}
