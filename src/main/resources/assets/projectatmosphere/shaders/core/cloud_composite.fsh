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

vec4 sampleCloudColor(vec2 uv) {
    return texture(CloudColorSampler, clamp(uv, vec2(0.0), vec2(1.0)));
}

void main() {
    vec4 cloudColor = sampleCloudColor(texCoord);
    if (BlurStrength > 0.001 && BlurRadius > 0.001) {
        vec2 textureDimensions = vec2(textureSize(CloudColorSampler, 0));
        vec2 texel = 1.0 / max(textureDimensions, vec2(1.0));
        vec2 radius = texel * BlurRadius;
        vec4 taps = sampleCloudColor(texCoord + vec2(radius.x, 0.0))
            + sampleCloudColor(texCoord - vec2(radius.x, 0.0))
            + sampleCloudColor(texCoord + vec2(0.0, radius.y))
            + sampleCloudColor(texCoord - vec2(0.0, radius.y));
        vec4 blurred = taps * 0.25;
        float alphaWeight = smoothstep(0.001, 0.08, max(cloudColor.a, blurred.a));
        cloudColor = mix(cloudColor, blurred, clamp(BlurStrength, 0.0, 1.0) * alphaWeight);
    }
    if (cloudColor.a > 0.001) {
        cloudColor.rgb /= cloudColor.a;
    }

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

    fragColor = cloudColor;
}
