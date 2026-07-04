#version 150

uniform sampler2D CloudColorSampler;
uniform sampler2D CloudDepthSampler;
uniform sampler2D CloudHistorySampler;
uniform float BlurRadius;
uniform float BlurStrength;
uniform float HistoryBlendWeight;
uniform int UseHistory;
uniform int CompositeDebugMode;
uniform int UseFramebufferDepth;

in vec2 texCoord;
out vec4 fragColor;

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
    if (cloudDepth >= 1.0) {
        discard;
    }

    // The destination scene depth is attached to the framebuffer during this
    // pass. Sampling that same texture here creates an undefined OpenGL
    // feedback loop and caused the downscaled cloud to be discarded on some
    // drivers. Write the raymarched cloud depth and let the fixed-function
    // depth test compare it against the attached scene depth instead.
    if (UseFramebufferDepth != 0 && CompositeDebugMode == 0) {
        gl_FragDepth = cloudDepth;
    }

    fragColor = cloudColor;
}
