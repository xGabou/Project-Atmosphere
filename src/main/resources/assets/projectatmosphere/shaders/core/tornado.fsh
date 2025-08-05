#version 150

uniform float Time;

out vec4 fragColor;

void main() {
    // Alternate red-blue color visibly over time
    float pulse = sin(Time * 5.0) * 0.5 + 0.5;
    fragColor = vec4(pulse, 0.0, 1.0 - pulse, 1.0); // from red to blue
}
