#version 150

uniform sampler2D TornadoColorSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 color = texture(TornadoColorSampler, texCoord);
    if (color.a <= 0.001) {
        discard;
    }

    vec3 straightColor = color.rgb / max(color.a, 0.001);
    fragColor = vec4(straightColor, color.a);
}
