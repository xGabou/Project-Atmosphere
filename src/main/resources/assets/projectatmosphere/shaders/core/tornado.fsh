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

// Compute the radius of the funnel at a given height (0..1)
float funnelRadius(float y) {
    float base = 0.40;  // radius at ground
    float top = 0.05;   // radius near the top
    return mix(base, top, y);
}

// Fade out the core so the tornado is hollow inside
float coreMask(float r, float inner) {
    return smoothstep(inner, inner - 0.02, r);
}

// Fade out towards the outside edge of the funnel
float edgeMask(float r, float radius) {
    return smoothstep(radius, radius - 0.03, r);
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
    vec2 uv = texCoord;
    // Translate to center-based coordinates (-0.5..0.5)
    vec2 p = uv - 0.5;

    float height = clamp(uv.y, 0.0, 1.0);
    float baseRadius = funnelRadius(height);

    float r = length(p);
    float angle = atan(p.y, p.x);

    // Determine masking to clip pixels outside the funnel
    float outer = edgeMask(r, baseRadius);
    float inner = coreMask(r, 0.02);
    float mask = outer * inner * verticalFade(height);

    if (mask <= 0.0) {
        fragColor = vec4(0.0);
        return;
    }

    // Base rotation derived from time and height
    float rotation = Time * TwistSpeed + height * 12.0;

    // Low-frequency noise for large curls
    vec2 polar = vec2(angle / (2.0 * PI), height);
    float n1 = noise(vec3(polar * 3.0, Time * 0.05));
    float n2 = noise(vec3(polar * 6.0, -Time * 0.04));

    angle += rotation + n1 * 4.0;
    r += (n2 - 0.5) * 0.05;

    // High-frequency noise for fine turbulent motion
    vec2 offset;
    offset.x = noise(vec3(uv * 5.0, Time * 0.02));
    offset.y = noise(vec3(uv.yx * 5.0, -Time * 0.02));
    angle += (offset.x - 0.5) * 3.0;
    r += (offset.y - 0.5) * 0.03;

    // Build final UV for sampling the base texture
    vec2 swirlUV = buildSwirlUV(angle, height, offset);

    // Fetch the base funnel texture and modulate it
    vec4 base = texture(Sampler0, swirlUV);

    vec3 color = base.rgb * (0.5 + n1 * 0.5);

    float alpha = base.a * mask;
    alpha *= 0.7 + n2 * 0.3;

    fragColor = vec4(color, alpha);
}

