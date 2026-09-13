#version 150

// T207. Temporal lattice resolve: four raw subpixel phases into one target of
// twice the phase resolution, plus the current frame's rain.
//
// One phase is marched per frame at a quarter-pixel screen-space offset
// (CloudTemporalPhaseState), so four consecutive phases place one ray at each
// of the 2x2 lattice sites a phase texel covers. This pass decides, per
// lattice pixel, which phase owns the site and fetches that phase's sample:
// the phase rendered this frame at zero bilinear fraction (the exact ray
// through this pixel), the other three reprojected ONCE from the camera each
// was rendered from, and validated against the CURRENT phase's evidence in
// the neighbourhood of the site. A history sample that fails falls back to
// the current phase's jitter-corrected bilinear reconstruction - this frame's
// own answer at shipped quality - never to stale content.
//
// Nothing here writes back into a phase. The phases are raw march output and
// only raw march output ever enters this pass (the T165 measurement: once-
// reprojected raw phases keep 62-84% of the stationary lattice gain; a
// recursively resampled accumulation buffer collapses it to 11%). The resolved
// target is consumed by the composite and by nothing temporal.
//
// Validity (T165's blocker was a point comparison between the depths of two
// DIFFERENT subpixel rays, which rejects exactly the thin structure the lattice
// recovers): a history sample is compared against the interval of answers the
// current phase gives in the 3x3 phase-texel neighbourhood of the site -
// coverage (alpha) and linear ray distance - carried through the same warp.
// Legitimate cross-ray disagreement lies inside that interval; content left
// over from before a real change (new sky, new cloud, a moved silhouette, a
// changed depth order) does not.
//
// Rain is the current frame's, always. A phase stores its body without the
// rain composite and, on its aux attachment, the body's accumulation and
// transmittance at the rain's depth; the rain texel of THIS frame is then
// composited at that point: A = before + T_before * R + T_r * (body - before),
// T = T_body * T_r - the in-march equation, so a stationary frame with rain
// reproduces the production composite exactly.

uniform sampler2D Phase0Color;
uniform sampler2D Phase1Color;
uniform sampler2D Phase2Color;
uniform sampler2D Phase3Color;
uniform sampler2D Phase0Depth;
uniform sampler2D Phase1Depth;
uniform sampler2D Phase2Depth;
uniform sampler2D Phase3Depth;
uniform sampler2D RainPassSampler;
// Bound manually on the PA units (Minecraft tracks twelve JSON samplers).
uniform sampler2D Phase0Aux;
uniform sampler2D Phase1Aux;
uniform sampler2D Phase2Aux;
uniform sampler2D Phase3Aux;

uniform mat4 InvProjMat;
uniform mat4 InvViewRotMat;
// This frame's projection * view rotation, for the rain's depth.
uniform mat4 CloudViewProjMat;
// Projection * view rotation as it was when each phase was rendered.
uniform mat4 PhaseViewProj0;
uniform mat4 PhaseViewProj1;
uniform mat4 PhaseViewProj2;
uniform mat4 PhaseViewProj3;
// Current camera position minus the position each phase was rendered from:
// camera-relative, so absolute world coordinates never enter the shader.
uniform vec3 PhaseCamDelta0;
uniform vec3 PhaseCamDelta1;
uniform vec3 PhaseCamDelta2;
uniform vec3 PhaseCamDelta3;
// 1 when the phase holds a raw frame rendered within the last four frames
// from a camera this frame can reproject (age, generation and camera-cut
// checks live on the CPU; a 0 here is an age/phase reject).
uniform int PhaseValid0;
uniform int PhaseValid1;
uniform int PhaseValid2;
uniform int PhaseValid3;
// Each phase's subpixel offset in phase pixels. Phase texel i holds a sample
// taken at i + 0.5 + jitter, so a bilinear read of a phase subtracts its
// jitter first, or it reads the wrong lattice site and blurs.
uniform vec2 PhaseJitter0;
uniform vec2 PhaseJitter1;
uniform vec2 PhaseJitter2;
uniform vec2 PhaseJitter3;
uniform int CurrentPhase;
uniform float MaxRenderDistance;
// 1 lattice with coverage + depth validity; 2 the same plus the colour clip;
// 3 current phase only (the no-history baseline: the fallback everywhere);
// 4 trust-all (diagnostic - ghosts by construction, never production).
uniform int ResolveMode;
// 0 clouds; 1 the acceptance class per pixel in R (see CLASS_*), for the
// campaign's validity counters.
uniform int DebugOutput;

in vec2 texCoord;
out vec4 fragColor;

const float ALPHA_EPSILON = 0.001;
const float CLASS_CURRENT = 0.0;
const float CLASS_ACCEPTED = 1.0;
const float CLASS_OFFSCREEN = 2.0;
const float CLASS_AGE = 3.0;
const float CLASS_COVERAGE = 4.0;
const float CLASS_DISOCCLUSION = 5.0;
const float CLASS_DEPTH = 6.0;
const float CLASS_COLOUR = 7.0;
const float CLASS_FALLBACK = 8.0;

vec2 phaseJitter(int phase) {
    if (phase == 0) { return PhaseJitter0; }
    if (phase == 1) { return PhaseJitter1; }
    if (phase == 2) { return PhaseJitter2; }
    return PhaseJitter3;
}

mat4 phaseViewProj(int phase) {
    if (phase == 0) { return PhaseViewProj0; }
    if (phase == 1) { return PhaseViewProj1; }
    if (phase == 2) { return PhaseViewProj2; }
    return PhaseViewProj3;
}

vec3 phaseCamDelta(int phase) {
    if (phase == 0) { return PhaseCamDelta0; }
    if (phase == 1) { return PhaseCamDelta1; }
    if (phase == 2) { return PhaseCamDelta2; }
    return PhaseCamDelta3;
}

bool phaseValid(int phase) {
    if (phase == 0) { return PhaseValid0 != 0; }
    if (phase == 1) { return PhaseValid1 != 0; }
    if (phase == 2) { return PhaseValid2 != 0; }
    return PhaseValid3 != 0;
}

vec4 phaseColorTexel(int phase, ivec2 coord) {
    if (phase == 0) { return texelFetch(Phase0Color, coord, 0); }
    if (phase == 1) { return texelFetch(Phase1Color, coord, 0); }
    if (phase == 2) { return texelFetch(Phase2Color, coord, 0); }
    return texelFetch(Phase3Color, coord, 0);
}

float phaseDepthTexel(int phase, ivec2 coord) {
    if (phase == 0) { return texelFetch(Phase0Depth, coord, 0).r; }
    if (phase == 1) { return texelFetch(Phase1Depth, coord, 0).r; }
    if (phase == 2) { return texelFetch(Phase2Depth, coord, 0).r; }
    return texelFetch(Phase3Depth, coord, 0).r;
}

vec4 phaseAuxTexel(int phase, ivec2 coord) {
    if (phase == 0) { return texelFetch(Phase0Aux, coord, 0); }
    if (phase == 1) { return texelFetch(Phase1Aux, coord, 0); }
    if (phase == 2) { return texelFetch(Phase2Aux, coord, 0); }
    return texelFetch(Phase3Aux, coord, 0);
}

/** Linear ray distance of an NDC depth at an NDC position, this frame's projection. */
float rayDistance(vec2 ndc, float ndcDepth) {
    vec4 clip = vec4(ndc, ndcDepth * 2.0 - 1.0, 1.0);
    vec4 view = InvProjMat * clip;
    return length(view.xyz / max(abs(view.w), 1e-6));
}

/** NDC depth of a camera-relative point, this frame's projection. */
float depthAt(vec3 relPos) {
    vec4 clip = CloudViewProjMat * vec4(relPos, 1.0);
    float ndcDepth = clip.z / max(abs(clip.w), 0.00001);
    return clamp(ndcDepth * 0.5 + 0.5, 0.0, 0.999999);
}

/**
 * One sample of a phase at a UV in that phase's own space: jitter-corrected
 * bilinear of the body and the aux, depth taken from the nearest texel (a
 * depth interpolated across a silhouette is a depth nothing has). This is the
 * single resample a history phase receives.
 */
void phaseSample(int phase, vec2 uv, out vec4 color, out vec4 aux, out float depth) {
    ivec2 size = textureSize(Phase0Color, 0);
    vec2 coord = uv * vec2(size) - vec2(0.5) - phaseJitter(phase);
    vec2 base = floor(coord);
    vec2 f = coord - base;
    ivec2 b = ivec2(base);
    ivec2 c00 = clamp(b, ivec2(0), size - ivec2(1));
    ivec2 c10 = clamp(b + ivec2(1, 0), ivec2(0), size - ivec2(1));
    ivec2 c01 = clamp(b + ivec2(0, 1), ivec2(0), size - ivec2(1));
    ivec2 c11 = clamp(b + ivec2(1, 1), ivec2(0), size - ivec2(1));
    color = mix(mix(phaseColorTexel(phase, c00), phaseColorTexel(phase, c10), f.x),
                mix(phaseColorTexel(phase, c01), phaseColorTexel(phase, c11), f.x), f.y);
    aux = mix(mix(phaseAuxTexel(phase, c00), phaseAuxTexel(phase, c10), f.x),
              mix(phaseAuxTexel(phase, c01), phaseAuxTexel(phase, c11), f.x), f.y);
    ivec2 nearest = clamp(ivec2(floor(coord + vec2(0.5))), ivec2(0), size - ivec2(1));
    depth = phaseDepthTexel(phase, nearest);
}

void main() {
    ivec2 phaseSize = textureSize(Phase0Color, 0);
    if (phaseSize.x <= 0 || phaseSize.y <= 0) {
        discard;
    }
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    // The phase whose ray passes through this lattice pixel: jitters are
    // ordered (-,-), (+,-), (-,+), (+,+) and a negative offset lands on the
    // lower lattice pixel of the pair.
    int lattice = (pixel.x & 1) + 2 * (pixel.y & 1);
    ivec2 currentCoord = clamp(pixel / 2, ivec2(0), phaseSize - ivec2(1));

    // The current phase's own answer for this 2x2 block, and the fallback:
    // its jitter-corrected bilinear reconstruction at this pixel - what the
    // composite would have made of this frame alone.
    vec4 currentColor = phaseColorTexel(CurrentPhase, currentCoord);
    float currentDepth = phaseDepthTexel(CurrentPhase, currentCoord);
    vec4 fallbackColor;
    vec4 fallbackAux;
    float fallbackDepth;
    phaseSample(CurrentPhase, texCoord, fallbackColor, fallbackAux, fallbackDepth);
    // The fallback's depth is the block's own: the bilinear body is a
    // reconstruction of this block's samples, not of a neighbour's surface.
    fallbackDepth = currentDepth;

    vec4 body = fallbackColor;
    vec4 aux = fallbackAux;
    float bodyDepth = fallbackDepth;
    float acceptance = CLASS_FALLBACK;

    if (lattice == CurrentPhase) {
        body = currentColor;
        aux = phaseAuxTexel(CurrentPhase, currentCoord);
        bodyDepth = currentDepth;
        acceptance = CLASS_CURRENT;
    } else if (ResolveMode == 3) {
        acceptance = CLASS_FALLBACK;
    } else if (!phaseValid(lattice)) {
        acceptance = CLASS_AGE;
    } else {
        // The current phase's evidence around this site: the interval of
        // coverage and of linear distance its 3x3 neighbourhood reports, and
        // the colour statistics for the optional clip.
        float alphaMin = 1.0;
        float alphaMax = 0.0;
        float nearD = 1e30;
        float farD = -1e30;
        vec4 boxSum = vec4(0.0);
        vec4 boxSumSq = vec4(0.0);
        vec2 ndc = texCoord * 2.0 - 1.0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                ivec2 c = clamp(currentCoord + ivec2(dx, dy), ivec2(0), phaseSize - ivec2(1));
                vec4 v = phaseColorTexel(CurrentPhase, c);
                float d = phaseDepthTexel(CurrentPhase, c);
                alphaMin = min(alphaMin, v.a);
                alphaMax = max(alphaMax, v.a);
                boxSum += v;
                boxSumSq += v * v;
                if (d < 1.0) {
                    float dist = rayDistance(ndc, d);
                    nearD = min(nearD, dist);
                    farD = max(farD, dist);
                }
            }
        }
        vec4 boxMean = boxSum / 9.0;
        vec4 boxSigma = sqrt(max(boxSumSq / 9.0 - boxMean * boxMean, vec4(0.0)));
        bool currentHasHit = farD >= nearD;

        // Reprojection distance: this block's own hit, else the nearest hit
        // the neighbourhood reports, else the volume's far end (sky).
        float reprojectDistance = currentDepth < 1.0
            ? rayDistance(ndc, currentDepth)
            : (currentHasHit ? nearD : MaxRenderDistance);
        vec4 viewDir4 = InvProjMat * vec4(ndc, -1.0, 1.0);
        vec3 viewDir = normalize(viewDir4.xyz / max(abs(viewDir4.w), 1e-6));
        vec3 rayDir = normalize((InvViewRotMat * vec4(viewDir, 0.0)).xyz);
        vec3 camDelta = phaseCamDelta(lattice);
        vec3 relative = rayDir * reprojectDistance + camDelta;
        vec4 clip = phaseViewProj(lattice) * vec4(relative, 1.0);
        vec2 uv = clip.w > 1e-6 ? (clip.xy / clip.w) * 0.5 + 0.5 : vec2(-1.0);
        if (clip.w <= 1e-6 || any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) {
            acceptance = CLASS_OFFSCREEN;
        } else {
            vec4 historyColor;
            vec4 historyAux;
            float historyDepth;
            phaseSample(lattice, uv, historyColor, historyAux, historyDepth);
            bool historyHit = historyDepth < 1.0;
            // The history's depth is NDC in ITS projection; as a linear
            // distance from the camera it was rendered from it is comparable
            // to the current interval carried through the same warp.
            float historyDist = historyHit ? rayDistance(uv * 2.0 - 1.0, historyDepth) : -1.0;
            bool trustAll = ResolveMode == 4;

            // Coverage: the history's alpha must lie within the interval the
            // current phase reports around the site, padded by a quarter of
            // that interval's width plus a floor for the lattice's own
            // sub-texel variation. Two disocclusion cases are named apart:
            // history cloud where the current frame sees only sky, and
            // history sky where the current frame sees only cloud.
            float alphaPad = 0.06 + 0.25 * (alphaMax - alphaMin);
            bool coverageAgrees = historyColor.a >= alphaMin - alphaPad
                && historyColor.a <= alphaMax + alphaPad;
            // A thin history wisp against an all-sky neighbourhood is the
            // lattice's own value (structure between the current phase's
            // rays - 191 such sites at PLAY_VIS_NEAR, every one correct);
            // only substantial history coverage against sky is new sky.
            bool newSky = alphaMax <= ALPHA_EPSILON && historyColor.a > 0.25;
            bool newCloud = alphaMin >= 0.5 && historyColor.a < 0.05;
            // Disocclusion needs motion. Where the history sample landed
            // within half a phase texel of this ray, the two rays differ by
            // the lattice offset alone and every disagreement is sub-texel
            // structure (the current 3x3 can miss a wisp the history ray
            // hit); coverage and disocclusion are judged only once the
            // reprojection has actually moved the sample.
            float motionTexels = length((uv - texCoord) * vec2(phaseSize));
            bool subTexel = motionTexels < 0.5;
            if (subTexel) {
                coverageAgrees = true;
                newSky = false;
                newCloud = false;
            }

            // Depth: the history's own linear distance (from the camera it
            // was rendered from) against the current interval carried
            // through the same warp, with a tolerance for the sub-texel
            // structure the interval does not span.
            bool depthAgrees = true;
            if (historyHit && currentHasHit) {
                float a = length(rayDir * nearD + camDelta);
                float b = length(rayDir * farD + camDelta);
                float lo = min(a, b);
                float hi = max(a, b);
                float tolerance = max(2.0, 0.06 * hi + 0.5 * (hi - lo));
                depthAgrees = historyDist >= lo - tolerance && historyDist <= hi + tolerance;
            } else if (historyHit != currentHasHit) {
                // A hit against an all-sky neighbourhood, or sky against an
                // all-hit one, is a coverage change and is judged there.
                depthAgrees = true;
            }

            // Colour: variance clip against the neighbourhood (mode 2). A
            // single extreme neighbour must not blow the region open, so
            // sigma, not min/max.
            bool colourAgrees = true;
            if (ResolveMode == 2) {
                vec4 lo = boxMean - boxSigma * 1.5;
                vec4 hi = boxMean + boxSigma * 1.5;
                vec4 pad = (hi - lo) * 0.25 + vec4(0.002);
                colourAgrees = all(greaterThanEqual(historyColor.rgb, lo.rgb - pad.rgb))
                    && all(lessThanEqual(historyColor.rgb, hi.rgb + pad.rgb));
            }

            // Parallax. The reprojection is exact for a point; the sample is
            // a volume integral along the history ray, which diverges from
            // this ray through the medium as the camera moves. Once the
            // camera has moved more than a tenth of the sampled distance
            // since the phase was rendered - never at a storm hundreds of
            // blocks away, within half a block of motion inside the cloud
            // body - the history is refused (counted with the depth
            // rejects). The route's two in-cloud episodes (frames 1248-1252
            // and 1340-1362 of the first T207M run) were this.
            bool parallaxTooLarge = length(camDelta) > 0.1 * reprojectDistance;
            if (trustAll) {
                acceptance = CLASS_ACCEPTED;
            } else if (parallaxTooLarge) {
                acceptance = CLASS_DEPTH;
            } else if (newSky || newCloud) {
                acceptance = CLASS_DISOCCLUSION;
            } else if (!coverageAgrees) {
                acceptance = CLASS_COVERAGE;
            } else if (!depthAgrees) {
                acceptance = CLASS_DEPTH;
            } else if (!colourAgrees) {
                acceptance = CLASS_COLOUR;
            } else {
                acceptance = CLASS_ACCEPTED;
            }
            if (acceptance == CLASS_ACCEPTED) {
                body = historyColor;
                aux = historyAux;
                // The composite needs a depth in this frame's projection: the
                // history point projected onto this ray, its distance
                // corrected to first order for the camera's motion along it.
                bodyDepth = historyHit
                    ? depthAt(rayDir * max(historyDist - dot(camDelta, rayDir), 1.0))
                    : 1.0;
            }
        }
    }

    if (DebugOutput != 0) {
        fragColor = vec4(acceptance, body.a, bodyDepth, 1.0);
        gl_FragDepth = 1.0;
        return;
    }

    // The current frame's rain at this block: the rain pass marched this
    // frame's phase, so its texel for this block is the ray of the current
    // phase - fetched, never interpolated (its A packs two 12-bit values).
    vec4 rainIn = texelFetch(RainPassSampler, currentCoord, 0);
    float rainA = floor(rainIn.a / 4096.0);
    float rainB = rainIn.a - rainA * 4096.0;
    float rainT = rainA / 4095.0 * MaxRenderDistance;
    float rainTr = rainB / 4095.0;
    vec3 rainRgb = rainIn.rgb;
    bool rainPresent = rainTr < 0.9998 || dot(rainRgb, vec3(1.0)) > 0.0;

    // The in-march composite, at the point the phase split its body:
    // A = before + T_before * R + T_r * (body - before), T = T_body * T_r.
    vec3 outRgb = body.rgb;
    float outAlpha = body.a;
    float outDepth = bodyDepth;
    if (rainPresent) {
        float bodyTransmittance = 1.0 - clamp(body.a, 0.0, 1.0);
        outRgb = aux.rgb + aux.a * rainRgb + rainTr * (body.rgb - aux.rgb);
        outAlpha = 1.0 - bodyTransmittance * rainTr;
        if (body.a <= ALPHA_EPSILON) {
            // Pure rain: the representative point is the rain's own depth.
            vec2 ndc = texCoord * 2.0 - 1.0;
            vec4 viewDir4 = InvProjMat * vec4(ndc, -1.0, 1.0);
            vec3 viewDir = normalize(viewDir4.xyz / max(abs(viewDir4.w), 1e-6));
            vec3 rayDir = normalize((InvViewRotMat * vec4(viewDir, 0.0)).xyz);
            outDepth = depthAt(rayDir * rainT);
        }
    }

    if (outAlpha < 0.002) {
        gl_FragDepth = 1.0;
        fragColor = vec4(0.0);
        return;
    }
    gl_FragDepth = outDepth < 1.0 ? min(outDepth, 0.999999) : 1.0;
    fragColor = vec4(outRgb, clamp(outAlpha, 0.0, 1.0));
}
