#version 150

uniform sampler2D SceneDepthSampler;
uniform sampler2D CloudShadowSampler;
uniform mat4 InverseProjMat;
uniform mat4 InverseModelViewMat;
uniform vec4 ShadowBounds;
uniform float ShadowStrength;

in vec2 texCoord;
out vec4 fragColor;

vec3 reconstructWorld(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 view = InverseProjMat * clip;
    view /= view.w;
    vec4 world = InverseModelViewMat * vec4(view.xyz, 1.0);
    return world.xyz / world.w;
}

void main() {
    float sceneDepth = texture(SceneDepthSampler, texCoord).r;
    if (sceneDepth >= 0.9999) {
        discard;
    }

    vec3 world = reconstructWorld(texCoord, sceneDepth);
    vec2 boundsSize = max(ShadowBounds.zw - ShadowBounds.xy, vec2(1.0));
    vec2 shadowUv = (world.xz - ShadowBounds.xy) / boundsSize;
    if (shadowUv.x < 0.0 || shadowUv.x > 1.0 || shadowUv.y < 0.0 || shadowUv.y > 1.0) {
        discard;
    }

    float shadow = texture(CloudShadowSampler, shadowUv).r;
    float alpha = clamp(shadow * ShadowStrength, 0.0, 0.72);
    if (alpha <= 0.002) {
        discard;
    }

    fragColor = vec4(0.0, 0.0, 0.0, alpha);
}
