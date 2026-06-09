#version 150

uniform sampler2D CloudColorSampler;
uniform sampler2D CloudDepthSampler;
uniform sampler2D SceneDepthSampler;

in vec2 texCoord;
out vec4 fragColor;

const float DEPTH_BIAS = 0.0005;

void main() {
    vec4 cloudColor = texture(CloudColorSampler, texCoord);
    if (cloudColor.a <= 0.001) {
        discard;
    }

    ivec2 cloudDepthSize = textureSize(CloudDepthSampler, 0);
    ivec2 cloudDepthCoord = clamp(ivec2(texCoord * vec2(cloudDepthSize)), ivec2(0), cloudDepthSize - ivec2(1));
    float cloudDepth = texelFetch(CloudDepthSampler, cloudDepthCoord, 0).r;
    float sceneDepth = texture(SceneDepthSampler, texCoord).r;
    if (sceneDepth + DEPTH_BIAS < cloudDepth) {
        discard;
    }

    fragColor = cloudColor;
}
