#version 150

uniform sampler2D CloudColorSampler;
uniform sampler2D CloudDepthSampler;
uniform sampler2D SceneDepthSampler;
uniform sampler2D CloudHistorySampler;
uniform float BlurRadius;
uniform float BlurStrength;
uniform float HistoryBlendWeight;
uniform int UseHistory;
uniform int CompositeDebugMode;

in vec2 texCoord;
out vec4 fragColor;

const float DEPTH_BIAS = 0.0005;

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
}

void accumulateAlphaWeightedSample(
        vec2 uv,
        vec2 offset,
        float weight,
        inout vec3 weightedRgb,
        inout float weightedAlpha,
        inout float rgbWeightTotal,
        inout float alphaWeightTotal
) {
    vec4 sampleColor = texture(CloudColorSampler, uv + offset);
    if (sampleColor.a > 0.001) {
        sampleColor.rgb /= sampleColor.a;
    }
    float alphaWeight = weight * max(sampleColor.a, 0.001);
    weightedRgb += sampleColor.rgb * alphaWeight;
    weightedAlpha += sampleColor.a * weight;
    rgbWeightTotal += alphaWeight;
    alphaWeightTotal += weight;
}

vec4 alphaWeightedBlur(vec2 uv) {
    vec4 center = texture(CloudColorSampler, uv);
    if (center.a > 0.001) {
        center.rgb /= center.a;
    }
    float radius = max(BlurRadius, 0.0);
    float strength = saturate(BlurStrength);
    if (radius <= 0.001 || strength <= 0.001) {
        return center;
    }

    vec2 texelSize = 1.0 / vec2(textureSize(CloudColorSampler, 0));
    vec3 weightedRgb = vec3(0.0);
    float weightedAlpha = 0.0;
    float rgbWeightTotal = 0.0;
    float alphaWeightTotal = 0.0;

    accumulateAlphaWeightedSample(uv, vec2(0.0), 3.8, weightedRgb, weightedAlpha, rgbWeightTotal, alphaWeightTotal);
    accumulateAlphaWeightedSample(uv, texelSize * vec2(0.78, 0.18) * radius, 1.15, weightedRgb, weightedAlpha, rgbWeightTotal, alphaWeightTotal);
    accumulateAlphaWeightedSample(uv, texelSize * vec2(-0.62, 0.42) * radius, 1.05, weightedRgb, weightedAlpha, rgbWeightTotal, alphaWeightTotal);
    accumulateAlphaWeightedSample(uv, texelSize * vec2(0.31, -0.71) * radius, 1.05, weightedRgb, weightedAlpha, rgbWeightTotal, alphaWeightTotal);
    accumulateAlphaWeightedSample(uv, texelSize * vec2(-0.25, -0.86) * radius, 0.95, weightedRgb, weightedAlpha, rgbWeightTotal, alphaWeightTotal);
    accumulateAlphaWeightedSample(uv, texelSize * vec2(0.91, -0.54) * radius, 0.85, weightedRgb, weightedAlpha, rgbWeightTotal, alphaWeightTotal);
    accumulateAlphaWeightedSample(uv, texelSize * vec2(-0.94, -0.11) * radius, 0.85, weightedRgb, weightedAlpha, rgbWeightTotal, alphaWeightTotal);
    accumulateAlphaWeightedSample(uv, texelSize * vec2(0.18, 0.94) * radius, 0.85, weightedRgb, weightedAlpha, rgbWeightTotal, alphaWeightTotal);
    accumulateAlphaWeightedSample(uv, texelSize * vec2(-0.52, -0.61) * radius, 0.85, weightedRgb, weightedAlpha, rgbWeightTotal, alphaWeightTotal);

    vec4 blurred = vec4(center.rgb, center.a);
    if (rgbWeightTotal > 0.0001) {
        blurred.rgb = weightedRgb / rgbWeightTotal;
    }
    if (alphaWeightTotal > 0.0001) {
        blurred.a = weightedAlpha / alphaWeightTotal;
    }
    return mix(center, blurred, strength);
}

vec4 blendHistory(vec4 currentColor, vec2 uv) {
    if (UseHistory == 0 || HistoryBlendWeight <= 0.001) {
        return currentColor;
    }

    vec4 historyColor = texture(CloudHistorySampler, uv);
    float currentPresence = smoothstep(0.015, 0.14, currentColor.a);
    float alphaAgreement = 1.0 - smoothstep(0.16, 0.55, abs(currentColor.a - historyColor.a));
    float historyWeight = saturate(HistoryBlendWeight) * currentPresence * alphaAgreement;
    if (historyWeight <= 0.001) {
        return currentColor;
    }

    vec3 currentPremul = currentColor.rgb * currentColor.a;
    vec3 historyPremul = historyColor.rgb * historyColor.a;
    float resolvedAlpha = mix(currentColor.a, historyColor.a, historyWeight);
    vec3 resolvedPremul = mix(currentPremul, historyPremul, historyWeight);
    vec3 resolvedRgb = resolvedAlpha > 0.0001 ? resolvedPremul / resolvedAlpha : currentColor.rgb;
    return vec4(resolvedRgb, resolvedAlpha);
}

void main() {
    vec4 cloudColor = alphaWeightedBlur(texCoord);

    if (cloudColor.a <= 0.001) {
        discard;
    }

    ivec2 cloudDepthSize = textureSize(CloudDepthSampler, 0);
    ivec2 cloudDepthCoord = clamp(ivec2(texCoord * vec2(cloudDepthSize)), ivec2(0), cloudDepthSize - ivec2(1));
    float cloudDepth = texelFetch(CloudDepthSampler, cloudDepthCoord, 0).r;

    float sceneDepth = texture(SceneDepthSampler, texCoord).r;

    if (CompositeDebugMode == 0 && sceneDepth + DEPTH_BIAS < cloudDepth) {
        discard;
    }

    fragColor = blendHistory(cloudColor, texCoord);
}
