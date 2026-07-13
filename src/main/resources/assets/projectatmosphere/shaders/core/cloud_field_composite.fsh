#version 150

uniform sampler2D CloudColorSampler;
uniform sampler2D CloudDepthSampler;
uniform sampler2D SceneDepthSampler;
uniform int CompositeMode;
uniform int DepthCompositeEnabled;
uniform int SceneDepthValid;

in vec2 texCoord;
out vec4 fragColor;

const float ALPHA_EPSILON = 0.001;

ivec2 clampCoord(ivec2 coord, ivec2 size) {
    return clamp(coord, ivec2(0), size - ivec2(1));
}

vec3 depthDebugColor(float depth) {
    float proximity = clamp((1.0 - depth) * 2048.0, 0.0, 1.0);
    return mix(vec3(0.04, 0.08, 0.20), vec3(1.0, 0.72, 0.12), proximity);
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

    vec4 colors[4];
    float depths[4];
    bool colorPresent = false;
    bool depthPresent = false;
    bool pairedPresent = false;
    float selectedDepth = 1.0;
    float selectedScore = -1.0;
    float sceneDepth = 1.0;
    float sceneDepthBias = 0.00002;
    bool opaqueVolumeNeighborhood = true;

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

    for (int i = 0; i < 4; i++) {
        colors[i] = texelFetch(CloudColorSampler, coords[i], 0);
        depths[i] = texelFetch(CloudDepthSampler, coords[i], 0).r;
        bool hasColor = colors[i].a > ALPHA_EPSILON;
        bool hasDepth = depths[i] < 1.0;
        bool visibleAgainstScene = SceneDepthValid == 0
            || sceneDepth >= 0.99999
            || depths[i] <= sceneDepth + sceneDepthBias;
        opaqueVolumeNeighborhood = opaqueVolumeNeighborhood
            && hasColor
            && hasDepth
            && visibleAgainstScene
            && colors[i].a >= 0.18;
        colorPresent = colorPresent || hasColor;
        depthPresent = depthPresent || hasDepth;
        pairedPresent = pairedPresent || (hasColor && hasDepth && visibleAgainstScene);
        if (hasColor && hasDepth && visibleAgainstScene) {
            float score = weights[i] * colors[i].a;
            if (score > selectedScore) {
                selectedScore = score;
                selectedDepth = depths[i];
            }
        }
    }

    if (CompositeMode == 1) {
        vec4 rawColor = texture(CloudColorSampler, texCoord);
        if (rawColor.a <= ALPHA_EPSILON) {
            discard;
        }
        vec3 straightColor = rawColor.rgb / max(rawColor.a, ALPHA_EPSILON);
        fragColor = vec4(straightColor, 1.0);
        return;
    }

    if (CompositeMode == 4) {
        vec4 rawColor = texture(CloudColorSampler, texCoord);
        if (rawColor.a <= ALPHA_EPSILON) {
            discard;
        }
        fragColor = vec4(vec3(clamp(rawColor.a, 0.0, 1.0)), 1.0);
        return;
    }

    if (DepthCompositeEnabled == 0) {
        vec4 rawColor = texture(CloudColorSampler, texCoord);
        if (rawColor.a <= ALPHA_EPSILON) {
            discard;
        }
        vec3 straightColor = rawColor.rgb / max(rawColor.a, ALPHA_EPSILON);
        fragColor = vec4(straightColor, clamp(rawColor.a, 0.0, 1.0));
        return;
    }

    if (CompositeMode == 3) {
        vec3 alignment = pairedPresent
            ? vec3(0.12, 0.90, 0.24)
            : (colorPresent && depthPresent
                ? vec3(0.95, 0.12, 0.85)
                : (colorPresent ? vec3(1.0, 0.12, 0.08) : vec3(0.10, 0.34, 1.0)));
        if (!colorPresent && !depthPresent) {
            discard;
        }
        fragColor = vec4(alignment, 1.0);
        return;
    }

    if (selectedScore < 0.0 || selectedDepth >= 1.0) {
        discard;
    }

    vec4 accumulated = vec4(0.0);
    float acceptedWeight = 0.0;
    float depthTolerance = max(0.00002, (1.0 - selectedDepth) * 0.08);
    for (int i = 0; i < 4; i++) {
        bool visibleAgainstScene = SceneDepthValid == 0
            || sceneDepth >= 0.99999
            || depths[i] <= sceneDepth + sceneDepthBias;
        bool paired = colors[i].a > ALPHA_EPSILON && depths[i] < 1.0 && visibleAgainstScene;
        bool sameSurface = abs(depths[i] - selectedDepth) <= depthTolerance;
        if (paired && (sameSurface || opaqueVolumeNeighborhood)) {
            accumulated += colors[i] * weights[i];
            acceptedWeight += weights[i];
        }
    }

    if (CompositeMode == 2) {
        fragColor = vec4(depthDebugColor(selectedDepth), 1.0);
        return;
    }

    if (acceptedWeight <= 0.0001 || accumulated.a <= ALPHA_EPSILON) {
        discard;
    }
    // Keep the absolute bilinear weights. Renormalizing only the accepted
    // opaque neighbours expands one low-resolution edge texel to full alpha,
    // producing a bright halo. RGB is premultiplied, so only the final colour
    // conversion divides by the weighted alpha.
    vec3 straightColor = accumulated.rgb / max(accumulated.a, ALPHA_EPSILON);
    fragColor = vec4(straightColor, clamp(accumulated.a, 0.0, 1.0));
    if (CompositeMode == 0) {
        gl_FragDepth = selectedDepth;
    }
}
