#version 150

// T209. Spatial upscale of the temporal lattice to the display.
//
// Input: the 960x540 resolved lattice (T207) - premultiplied RGBA16F, alpha =
// 1 - transmittance, body and the current frame's rain already composited -
// and its NDC depth. Output: the same quantities at display resolution
// (1920x1080), written into a layer the composite then blends one-to-one, so
// nothing is filtered twice. Depth is written through GL_ALWAYS (a disabled
// depth test writes nothing, T164's lesson).
//
// The buffer is premultiplied, and every method keeps it so: ONE kernel, ONE
// set of weights, applied to all four channels. A transparent texel is
// legitimately (0,0,0,0) in this space, so the silhouette is an ordinary edge
// of the field and no substitute colour is ever invented for an empty tap
// (T164 had to, because it filtered straight colour). Depth is a surface
// identity: it is selected by the shipped PA rule from the 2x2 footprint and
// never filtered. A tap the scene occludes is read as transparent, exactly as
// the fused composite rejects it.
//
// UpscaleMethod:
//   0  PA bilinear - the fused composite's own reconstruction moved into this
//      pass (the control; its output through the one-to-one composite must
//      equal production).
//   1  EASU - FSR1 edge-adaptive spatial upsampling on the premultiplied RGBA
//      field, the direction taken from luma and coverage together, the
//      deringing clamp to the 2x2 centre taps on every channel (coverage never
//      leaves [min, max] of its footprint, so no negative coverage, no halo of
//      invented cloud). Colour runs inside the reversible Reinhard-max + sqrt
//      mapping T164 established for HDR radiance.
//   2  Depth-guided Catmull-Rom - a 4x4 bicubic on the premultiplied field
//      whose taps are rejected when they carry cloud on another surface than
//      the selected depth (or are scene-occluded), the weights renormalised
//      over the accepted taps, then the same 2x2 clamp.
//
// Premultiplied guard (every method): the straight colour of the output lies
// within the straight colours of the valid centre taps, channel by channel -
// neither a bright fringe nor a dark halo at a transparent edge is possible
// by construction. (The first T209 launch bounded only the bright side: the
// kernels' negative lobes drove premultiplied rgb to zero where alpha stayed
// positive, and every edge pixel of EASU and the bicubic went black - the
// edge-luma diagnostic read p0.1 = 0 and, at PLAY_VIS_NEAR, a median of 0.07
// against the native 0.55.)

uniform sampler2D CloudColorSampler;
uniform sampler2D CloudDepthSampler;
uniform sampler2D SceneDepthSampler;
uniform int SceneDepthValid;
uniform int CoverageAlphaReconstruction;
uniform int UpscaleMethod;

in vec2 texCoord;
out vec4 fragColor;

const float ALPHA_EPSILON = 0.001;

ivec2 clampCoord(ivec2 coord, ivec2 size) {
    return clamp(coord, ivec2(0), size - ivec2(1));
}

// ---------------------------------------------------------------------------
// EASU colour domain (T164): Reinhard-max, hue-preserving and exactly
// invertible, then sqrt for the roughly perceptual space FSR1 was tuned for.
// Applied to the premultiplied colour; alpha is already bounded and stays as
// it is.
// ---------------------------------------------------------------------------
vec3 toEasuDomain(vec3 linearColor) {
    vec3 c = max(linearColor, vec3(0.0));
    float peak = max(max(c.r, c.g), c.b);
    c /= (1.0 + peak);
    return sqrt(c);
}

vec3 fromEasuDomain(vec3 encoded) {
    vec3 c = max(encoded, vec3(0.0));
    c = c * c;
    float peak = max(max(c.r, c.g), c.b);
    return c / max(1.0 - peak, 1e-4);
}

// The edge feature: luma of the encoded premultiplied colour and coverage,
// half each. A sunlit silhouette is a luma edge; a dark underside against sky
// is a coverage edge; the direction estimate must see both.
float easuFeature(vec4 encoded) {
    return 0.5 * dot(encoded.rgb, vec3(0.2126, 0.7152, 0.0722)) + 0.5 * encoded.a;
}

void easuSet(
        inout vec2 dir,
        inout float len,
        vec2 pp,
        bool biS, bool biT, bool biU, bool biV,
        float lA, float lB, float lC, float lD, float lE) {
    float w = 0.0;
    if (biS) { w = (1.0 - pp.x) * (1.0 - pp.y); }
    if (biT) { w = pp.x * (1.0 - pp.y); }
    if (biU) { w = (1.0 - pp.x) * pp.y; }
    if (biV) { w = pp.x * pp.y; }

    float dc = lD - lC;
    float cb = lC - lB;
    float lenX = 1.0 / max(max(abs(dc), abs(cb)), 1e-6);
    float dirX = lD - lB;
    dir.x += dirX * w;
    lenX = clamp(abs(dirX) * lenX, 0.0, 1.0);
    lenX *= lenX;
    len += lenX * w;

    float ec = lE - lC;
    float ca = lC - lA;
    float lenY = 1.0 / max(max(abs(ec), abs(ca)), 1e-6);
    float dirY = lE - lA;
    dir.y += dirY * w;
    lenY = clamp(abs(dirY) * lenY, 0.0, 1.0);
    lenY *= lenY;
    len += lenY * w;
}

// One kernel weight for the whole texel: the same w reaches r, g, b and a.
void easuTap(
        inout vec4 aC,
        inout float aW,
        vec2 off,
        vec2 dir,
        vec2 len2,
        float lob,
        float clp,
        vec4 c) {
    vec2 v = vec2(off.x * dir.x + off.y * dir.y, off.x * -dir.y + off.y * dir.x);
    v *= len2;
    float d2 = min(v.x * v.x + v.y * v.y, clp);
    float wB = 2.0 / 5.0 * d2 - 1.0;
    float wA = lob * d2 - 1.0;
    wB *= wB;
    wA *= wA;
    wB = 25.0 / 16.0 * wB - (25.0 / 16.0 - 1.0);
    float w = wB * wA;
    aC += c * w;
    aW += w;
}

// Catmull-Rom (a = -0.5) weight for a tap at distance x in source texels.
float catmullRom(float x) {
    x = abs(x);
    if (x < 1.0) {
        return 1.5 * x * x * x - 2.5 * x * x + 1.0;
    }
    if (x < 2.0) {
        return -0.5 * x * x * x + 2.5 * x * x - 4.0 * x + 2.0;
    }
    return 0.0;
}

void main() {
    ivec2 sourceSize = textureSize(CloudColorSampler, 0);
    ivec2 depthSize = textureSize(CloudDepthSampler, 0);
    if (sourceSize.x <= 0 || sourceSize.y <= 0 || depthSize != sourceSize) {
        discard;
    }

    vec2 sourcePosition = texCoord * vec2(sourceSize) - vec2(0.5);
    ivec2 baseCoord = ivec2(floor(sourcePosition));
    vec2 fraction = fract(sourcePosition);

    ivec2 coords[4];
    coords[0] = clampCoord(baseCoord, sourceSize);
    coords[1] = clampCoord(baseCoord + ivec2(1, 0), sourceSize);
    coords[2] = clampCoord(baseCoord + ivec2(0, 1), sourceSize);
    coords[3] = clampCoord(baseCoord + ivec2(1, 1), sourceSize);

    float weights[4];
    weights[0] = (1.0 - fraction.x) * (1.0 - fraction.y);
    weights[1] = fraction.x * (1.0 - fraction.y);
    weights[2] = (1.0 - fraction.x) * fraction.y;
    weights[3] = fraction.x * fraction.y;

    // ---- The shipped PA neighbourhood analysis, unchanged from the fused
    // composite: the scene test, the surface selection, the opaque-volume
    // relaxation. It supplies the depth for every method and the colour and
    // alpha for method 0. ----
    float sceneDepth = 1.0;
    float sceneDepthBias = 0.00002;
    if (SceneDepthValid != 0) {
        ivec2 sceneSize = textureSize(SceneDepthSampler, 0);
        if (sceneSize.x > 0 && sceneSize.y > 0) {
            ivec2 sceneCoord = clampCoord(ivec2(texCoord * vec2(sceneSize)), sceneSize);
            sceneDepth = texelFetch(SceneDepthSampler, sceneCoord, 0).r;
            float sceneRight = texelFetch(SceneDepthSampler, clampCoord(sceneCoord + ivec2(1, 0), sceneSize), 0).r;
            float sceneUp = texelFetch(SceneDepthSampler, clampCoord(sceneCoord + ivec2(0, 1), sceneSize), 0).r;
            float depthGradient = max(abs(sceneDepth - sceneRight), abs(sceneDepth - sceneUp));
            sceneDepthBias += min(depthGradient * 0.25, 0.00035);
        }
    }
    bool sceneOpen = SceneDepthValid == 0 || sceneDepth >= 0.99999;

    vec4 colors[4];
    float depths[4];
    bool paired[4];
    float selectedDepth = 1.0;
    float selectedScore = -1.0;
    bool opaqueVolumeNeighborhood = true;
    for (int i = 0; i < 4; i++) {
        colors[i] = texelFetch(CloudColorSampler, coords[i], 0);
        depths[i] = texelFetch(CloudDepthSampler, coords[i], 0).r;
        bool hasColor = colors[i].a > ALPHA_EPSILON;
        bool hasDepth = depths[i] < 1.0;
        bool visibleAgainstScene = sceneOpen || depths[i] <= sceneDepth + sceneDepthBias;
        paired[i] = hasColor && hasDepth && visibleAgainstScene;
        opaqueVolumeNeighborhood = opaqueVolumeNeighborhood && paired[i] && colors[i].a >= 0.18;
        if (paired[i]) {
            float score = weights[i] * colors[i].a;
            if (score > selectedScore) {
                selectedScore = score;
                selectedDepth = depths[i];
            }
        }
    }

    if (selectedScore < 0.0 || selectedDepth >= 1.0) {
        // Nothing visible in the footprint. Written, not discarded: the layer
        // is read one-to-one and an undefined texel would be composited.
        fragColor = vec4(0.0);
        gl_FragDepth = 1.0;
        return;
    }

    float depthTolerance = max(0.00002, (1.0 - selectedDepth) * 0.08);

    // The 2x2 as the filter sees it: an occluded tap is transparent. These
    // are the deringing bounds and the source of the premultiplied guard.
    vec4 centre[4];
    vec4 centreMin = vec4(1e30);
    vec4 centreMax = vec4(-1e30);
    vec3 straightMax = vec3(0.0);
    vec3 straightMin = vec3(1e30);
    for (int i = 0; i < 4; i++) {
        bool visibleAgainstScene = sceneOpen || depths[i] <= sceneDepth + sceneDepthBias;
        centre[i] = visibleAgainstScene ? colors[i] : vec4(0.0);
        centreMin = min(centreMin, centre[i]);
        centreMax = max(centreMax, centre[i]);
        if (centre[i].a > ALPHA_EPSILON) {
            vec3 straight = centre[i].rgb / centre[i].a;
            straightMax = max(straightMax, straight);
            straightMin = min(straightMin, straight);
        }
    }
    straightMin = min(straightMin, straightMax);

    vec4 outTexel;

    if (UpscaleMethod == 1) {
        // ---- EASU on the premultiplied field ----
        ivec2 fp = baseCoord;
        vec2 frac = fraction;

        ivec2 offsets[12];
        offsets[0] = ivec2(0, -1);
        offsets[1] = ivec2(1, -1);
        offsets[2] = ivec2(-1, 0);
        offsets[3] = ivec2(0, 0);
        offsets[4] = ivec2(1, 0);
        offsets[5] = ivec2(2, 0);
        offsets[6] = ivec2(-1, 1);
        offsets[7] = ivec2(0, 1);
        offsets[8] = ivec2(1, 1);
        offsets[9] = ivec2(2, 1);
        offsets[10] = ivec2(0, 2);
        offsets[11] = ivec2(1, 2);

        vec4 tap[12];
        float feature[12];
        for (int i = 0; i < 12; i++) {
            ivec2 c = clampCoord(fp + offsets[i], sourceSize);
            vec4 texel = texelFetch(CloudColorSampler, c, 0);
            float tapDepth = texelFetch(CloudDepthSampler, c, 0).r;
            bool visibleAgainstScene = sceneOpen || tapDepth <= sceneDepth + sceneDepthBias;
            vec4 v = visibleAgainstScene ? texel : vec4(0.0);
            tap[i] = vec4(toEasuDomain(v.rgb), clamp(v.a, 0.0, 1.0));
            feature[i] = easuFeature(tap[i]);
        }

        vec2 dir = vec2(0.0);
        float len = 0.0;
        // The four bilinear quadrants, in FSR1 tap naming: b e f g j,
        // c f g h k, f i j k n, g j k l o.
        easuSet(dir, len, frac, true, false, false, false,
                feature[0], feature[2], feature[3], feature[4], feature[7]);
        easuSet(dir, len, frac, false, true, false, false,
                feature[1], feature[3], feature[4], feature[5], feature[8]);
        easuSet(dir, len, frac, false, false, true, false,
                feature[3], feature[6], feature[7], feature[8], feature[10]);
        easuSet(dir, len, frac, false, false, false, true,
                feature[4], feature[7], feature[8], feature[9], feature[11]);

        vec2 dir2 = dir * dir;
        float dirR = dir2.x + dir2.y;
        bool zro = dirR < (1.0 / 32768.0);
        dirR = inversesqrt(max(dirR, 1e-12));
        dirR = zro ? 1.0 : dirR;
        dir.x = zro ? 1.0 : dir.x;
        dir *= dirR;

        len = len * 0.5;
        len *= len;
        float stretch = (dir.x * dir.x + dir.y * dir.y)
            / max(max(abs(dir.x), abs(dir.y)), 1e-6);
        vec2 len2 = vec2(1.0 + (stretch - 1.0) * len, 1.0 - 0.5 * len);
        float lob = 0.5 + ((1.0 / 4.0 - 0.04) - 0.5) * len;
        float clp = 1.0 / max(lob, 1e-6);

        vec4 aC = vec4(0.0);
        float aW = 0.0;
        for (int i = 0; i < 12; i++) {
            easuTap(aC, aW, vec2(offsets[i]) - frac, dir, len2, lob, clp, tap[i]);
        }

        // Deringing on every channel: the 2x2 centre taps f, g, j, k.
        vec4 mn4 = min(min(tap[3], tap[4]), min(tap[7], tap[8]));
        vec4 mx4 = max(max(tap[3], tap[4]), max(tap[7], tap[8]));
        vec4 encoded = clamp(aC / max(aW, 1e-6), mn4, mx4);
        outTexel = vec4(fromEasuDomain(encoded.rgb), encoded.a);
    } else if (UpscaleMethod == 2) {
        // ---- Depth-guided Catmull-Rom on the premultiplied field ----
        vec4 sum = vec4(0.0);
        float weightSum = 0.0;
        for (int dy = -1; dy <= 2; dy++) {
            float wy = catmullRom(float(dy) - fraction.y);
            for (int dx = -1; dx <= 2; dx++) {
                float w = catmullRom(float(dx) - fraction.x) * wy;
                ivec2 c = clampCoord(baseCoord + ivec2(dx, dy), sourceSize);
                vec4 texel = texelFetch(CloudColorSampler, c, 0);
                float tapDepth = texelFetch(CloudDepthSampler, c, 0).r;
                bool visibleAgainstScene = sceneOpen || tapDepth <= sceneDepth + sceneDepthBias;
                bool hasCloud = texel.a > ALPHA_EPSILON && tapDepth < 1.0;
                bool sameSurface = abs(tapDepth - selectedDepth) <= depthTolerance;
                // A transparent tap is a legitimate zero of the field and
                // stays; a tap with cloud on another surface, or one the
                // scene hides, is dropped and its weight redistributed.
                bool accepted = !hasCloud
                    || (visibleAgainstScene && (sameSurface || opaqueVolumeNeighborhood));
                if (accepted) {
                    sum += texel * w;
                    weightSum += w;
                }
            }
        }
        vec4 filtered = abs(weightSum) > 1e-4 ? sum / weightSum : centre[0] * weights[0]
            + centre[1] * weights[1] + centre[2] * weights[2] + centre[3] * weights[3];
        outTexel = clamp(filtered, centreMin, centreMax);
    } else {
        // ---- Method 0: the fused composite's reconstruction ----
        vec4 accumulated = vec4(0.0);
        float acceptedWeight = 0.0;
        float coverageAlpha = 0.0;
        for (int i = 0; i < 4; i++) {
            bool sameSurface = abs(depths[i] - selectedDepth) <= depthTolerance;
            if (paired[i]) {
                coverageAlpha += colors[i].a * weights[i];
            }
            if (paired[i] && (sameSurface || opaqueVolumeNeighborhood)) {
                accumulated += colors[i] * weights[i];
                acceptedWeight += weights[i];
            }
        }
        if (acceptedWeight <= 0.0001 || accumulated.a <= ALPHA_EPSILON) {
            fragColor = vec4(0.0);
            gl_FragDepth = 1.0;
            return;
        }
        float outputAlpha = CoverageAlphaReconstruction != 0 ? coverageAlpha : accumulated.a;
        // Premultiplied by the alpha the composite will use: its straight
        // colour is accumulated.rgb / accumulated.a either way.
        outTexel = vec4(accumulated.rgb * (outputAlpha / max(accumulated.a, ALPHA_EPSILON)),
                outputAlpha);
    }

    float outAlpha = clamp(outTexel.a, 0.0, 1.0);
    // The premultiplied guard: the straight colour within the valid centre
    // taps' range - no bright fringe, no dark halo.
    vec3 outColor = clamp(outTexel.rgb, outAlpha * straightMin, outAlpha * straightMax);
    if (outAlpha <= ALPHA_EPSILON) {
        fragColor = vec4(0.0);
        gl_FragDepth = 1.0;
        return;
    }
    fragColor = vec4(outColor, outAlpha);
    gl_FragDepth = selectedDepth;
}
