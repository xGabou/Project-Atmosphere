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
