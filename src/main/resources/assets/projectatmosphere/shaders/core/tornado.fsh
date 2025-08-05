#version 150

uniform float Time;

out vec4 fragColor;

void main() {
    // Simulate smoke-like grayscale
    float swirl = sin(gl_FragCoord.y * 0.1 + Time * 2.0) * 0.1;
    float brightness = 0.3 + swirl;

    fragColor = vec4(vec3(brightness), 0.9); // smoky gray with slight alpha
}
