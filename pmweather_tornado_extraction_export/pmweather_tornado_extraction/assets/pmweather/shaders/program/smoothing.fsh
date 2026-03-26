#version 330

uniform sampler2D DiffuseSampler;
uniform sampler2D PreviousSampler;

uniform vec2 OutSize;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 texel = texture(DiffuseSampler, texCoord);
    vec4 lastTexel = texture(PreviousSampler, texCoord);

    fragColor = mix(lastTexel, texel, 0.3);
}