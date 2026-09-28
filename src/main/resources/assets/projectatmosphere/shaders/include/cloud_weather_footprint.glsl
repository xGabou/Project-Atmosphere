float paSevereContourErosion(
        vec2 worldWarp,
        float theta,
        float seed,
        int profile,
        int role,
        float radial) {
    if ((profile != 4 && profile != 7)
            || (role != 2 && role != 5)) {
        return 0.0;
    }

    vec2 seedAxis = vec2(
        cos(seed * 1.73 + 0.41),
        sin(seed * 1.73 + 0.41)
    );
    float broad = clamp(
        0.5 + dot(worldWarp, seedAxis) * 1.15,
        0.0,
        1.0
    );
    float harmonic = role == 2 ? 7.0 : 9.0;
    float scallop = 0.5
        + 0.5 * sin(theta * harmonic + seed * 5.37);
    float contour = mix(
        broad,
        scallop,
        role == 2 ? 0.28 : 0.20
    );
    float amplitude = role == 2 ? 0.18 : 0.13;

    // Positive-only displacement keeps the detailed role footprint inside the
    // pre-existing weather envelope. Applying it only near the boundary avoids
    // noise-shaped holes through the meteorological mass.
    return amplitude
        * (1.0 - contour)
        * smoothstep(0.38, 0.92, radial);
}

float paSevereCarrierRadiusScale(int profile) {
    return 0.18;
}

float paSevereCarrierSupportScale(int profile) {
    return profile == 7 ? 0.30 : 0.22;
}

vec2 paSevereCarrierVerticalRange(int profile) {
    return profile == 7 ? vec2(0.10, 0.88) : vec2(0.12, 0.84);
}

vec2 paSevereCurvedLayerRange(
        int role,
        float layerBase,
        float layerTop,
        float radial) {
    float span = max(layerTop - layerBase, 0.001);
    float radial01 = smoothstep(0.0, 1.0, clamp(radial, 0.0, 1.0));
    float baseLift = 0.12;
    float topDrop = 0.68;
    float curvePower = 1.35;

    if (role == 2) {
        // Broad storm base: the exact frozen-field A/B retained the level
        // precipitation floor while measurably contracting upper shoulders.
        baseLift = 0.035;
        topDrop = 0.48;
        curvePower = 1.70;
    } else if (role == 3) {
        // Primary convective core: attached lower column with a broad dome.
        baseLift = 0.08;
        topDrop = 0.68;
        curvePower = 1.35;
    } else if (role == 4) {
        // Secondary towers are compact domes that overlap the main updraft.
        baseLift = 0.14;
        topDrop = 0.70;
        curvePower = 1.25;
    } else if (role == 5) {
        // The wind-aligned anvil is a thin lens, not a vertical slab.
        baseLift = 0.40;
        topDrop = 0.40;
        curvePower = 0.90;
    }

    float curve = pow(radial01, curvePower);
    float curvedBase = layerBase + span * baseLift * curve;
    float curvedTop = layerTop - span * topDrop * curve;
    return vec2(curvedBase, max(curvedTop, curvedBase + 0.001));
}

// Profile-3 lobes are true convective domes. CROWN deliberately has its own
// role (7): reusing severe ANVIL role 5 would flatten it into a thin lens.
vec2 paCumulusCurvedLayerRange(
        int role,
        float layerBase,
        float layerTop,
        float radial) {
    float span = max(layerTop - layerBase, 0.001);
    float radial01 = clamp(radial, 0.0, 1.0);
    float baseLift = 0.025;
    float topDrop = 0.58;
    float curvePower = 1.45;

    if (role == 2) {
        // Primary and shoulder lobes share a coherent condensation base while
        // their upper surfaces remain rounded.
        baseLift = 0.025;
        topDrop = 0.48;
        curvePower = 1.55;
    } else if (role == 3) {
        baseLift = 0.09;
        topDrop = 0.58;
        curvePower = 1.38;
    } else if (role == 4) {
        baseLift = 0.13;
        topDrop = 0.64;
        curvePower = 1.24;
    } else if (role == 7) {
        baseLift = 0.18;
        topDrop = 0.68;
        curvePower = 1.08;
    }

    float curve = pow(radial01, curvePower);
    float curvedBase = layerBase + span * baseLift * curve;
    float curvedTop = layerTop - span * topDrop * curve;
    return vec2(curvedBase, max(curvedTop, curvedBase + 0.001));
}

// T242. An irregular member outline: two octaves of value noise in
// the member's own normalised frame (about 2-3 bumps across its radius at any
// size, moving rigidly with it), returned in standard deviations (the octave
// sum has sd ~0.12 around 0.5), clamped to +-2. Shared by every splat so the
// coverage, category and cumulus-stage footprints keep one outline.
float paOutlineHash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float paOutlineNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(paOutlineHash(i), paOutlineHash(i + vec2(1.0, 0.0)), f.x),
        mix(paOutlineHash(i + vec2(0.0, 1.0)), paOutlineHash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float paMemberOutline(vec2 normalized, float seed) {
    vec2 q = normalized * 2.3 + vec2(seed * 17.31, seed * 9.73);
    float n = paOutlineNoise(q) * 0.65 + paOutlineNoise(q * 2.07 + vec2(5.3, -2.1)) * 0.35;
    return clamp((n - 0.5) / 0.12, -2.0, 2.0);
}
