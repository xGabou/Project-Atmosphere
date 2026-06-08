#version 150

uniform sampler2D CloudColorSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 cloudColor = texture(CloudColorSampler, texCoord);
    if (cloudColor.a <= 0.001) {
        discard;
    }

    fragColor = cloudColor;
}
