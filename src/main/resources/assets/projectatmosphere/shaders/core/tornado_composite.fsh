#version 150

uniform sampler2D TornadoColorSampler;
uniform sampler2D TornadoDepthSampler;
uniform sampler2D SceneDepthSampler;
uniform int UseSceneDepth;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(TornadoColorSampler, 0));
    vec3 weightedColor = vec3(0.0);
    float weightedAlpha = 0.0;
    float totalWeight = 0.0;

    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 offset = vec2(float(x), float(y));
            float distanceWeight = x == 0 && y == 0 ? 4.0 : (x == 0 || y == 0 ? 2.0 : 1.0);
            vec4 sampleColor = texture(TornadoColorSampler, texCoord + offset * texel);
            float alphaWeight = sampleColor.a * distanceWeight;
            weightedColor += sampleColor.rgb * alphaWeight;
            weightedAlpha += sampleColor.a * distanceWeight;
            totalWeight += distanceWeight;
        }
    }

    float alpha = weightedAlpha / max(totalWeight, 0.0001);
    if (alpha <= 0.006) {
        discard;
    }

    if (UseSceneDepth != 0) {
        float tornadoDepth = texture(TornadoDepthSampler, texCoord).r;
        float sceneDepth = texture(SceneDepthSampler, texCoord).r;
        if (sceneDepth < 1.0 && tornadoDepth > sceneDepth + 0.0005) {
            discard;
        }
    }

    vec3 color = weightedColor / max(weightedAlpha, 0.0001);
    fragColor = vec4(color, alpha);
}
