#version 150

uniform float Time;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 uv = texCoord;

    // Horizontal mask: hollow center
    float dist = abs(uv.x - 0.5);
    float centerFade = smoothstep(0.0, 0.2, dist); // tighter core fade = more visible edges

    // Vertical fade near top
    float vertical = uv.y;
    float verticalFade = smoothstep(1.0, 0.85, vertical); // stronger fade at top

    // Swirl effect
    float angle = uv.x * 6.2831; // 2π, no -0.5 offset
    float swirl = sin(angle * 6.0 - Time * 4.0 + uv.y * 10.0);
    float swirlFade = smoothstep(0.4, 0.0, abs(swirl)); // smoother swirl

    // Base color
    vec3 baseColor = vec3(0.05); // darker gray
    vec3 swirlColor = mix(baseColor, vec3(0.25), swirlFade * centerFade);

    // Stronger combined alpha
    float alpha = centerFade * verticalFade * (0.6 + swirlFade * 0.7); // boosted from 0.4–0.6 → 0.6–1.3

    // Clamp for safety
    alpha = clamp(alpha, 0.0, 1.0);

    fragColor = vec4(swirlColor, alpha);
}
