#version 150

// Time-driven animation for swirling motion
uniform float Time;
// Speed factor supplied by the CPU to control spin intensity
uniform float TwistSpeed;

// Base funnel texture
uniform sampler2D Sampler0;

in vec2 texCoord;
out vec4 fragColor;

const float PI = 3.14159265;

// Simple 3D value noise ----------------------------------------------------
float hash(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.1, 0.2, 0.3));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);

    float n000 = hash(i + vec3(0.0, 0.0, 0.0));
    float n100 = hash(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash(i + vec3(1.0, 1.0, 1.0));

    vec3 u = f * f * (3.0 - 2.0 * f);

    return mix(mix(mix(n000, n100, u.x), mix(n010, n110, u.x), u.y),
               mix(mix(n001, n101, u.x), mix(n011, n111, u.x), u.y), u.z);
}

// Fade vertically so the funnel softens near top and bottom
float verticalFade(float y) {
    float top = smoothstep(1.0, 0.7, y);
    float bottom = smoothstep(0.0, 0.1, y);
    return top * bottom;
}

// Build UV coordinates after all distortions
vec2 buildSwirlUV(float angle, float y, vec2 offset) {
    vec2 uv;
    uv.x = angle / (2.0 * PI); // convert angle to [0,1]
    uv.y = y;
    uv += offset * 0.01;       // fine distortion
    return uv;
}

void main() {
    float height = clamp(texCoord.y, 0.0, 1.0);
    float angle = texCoord.x * 2.0 * PI;

    // Base rotation derived from time and height
    float rotation = Time * TwistSpeed + height * 12.0;

    // Low-frequency noise for large curls
    vec2 polar = vec2(texCoord.x, height);
    float n1 = noise(vec3(polar * 3.0, Time * 0.05));
    float n2 = noise(vec3(polar * 6.0, -Time * 0.04));

    angle += rotation + n1 * 4.0;

    // High-frequency noise for fine turbulent motion
    vec2 offset;
    offset.x = noise(vec3(texCoord * 5.0, Time * 0.02));
    offset.y = noise(vec3(texCoord.yx * 5.0, -Time * 0.02));
    angle += (offset.x - 0.5) * 3.0;

    // Build final UV for sampling the base texture
    vec2 swirlUV = buildSwirlUV(angle, height, offset);

    // Fetch the base funnel texture and modulate it
    vec4 base = texture(Sampler0, swirlUV);

    vec3 color = base.rgb * (0.5 + n1 * 0.5);

    float alpha = base.a * verticalFade(height);
    alpha *= 0.7 + n2 * 0.3;

    fragColor = vec4(color, alpha);
}

