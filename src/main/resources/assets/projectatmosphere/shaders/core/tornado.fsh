#version 150

// ──────── Uniforms ────────
uniform float Time;
uniform float TwistSpeed;
uniform float BaseRadius;
uniform float TopRadius;
uniform float Height;
uniform float DustIntensity;
uniform float CoreTightness;
uniform float FlowIntensity;

uniform float LightDirX;
uniform float LightDirY;
uniform float LightDirZ;

uniform float SkyColorR;
uniform float SkyColorG;
uniform float SkyColorB;

uniform sampler2D Sampler0;
uniform sampler2D FlowMap;
uniform sampler2D NormalMap;
uniform sampler2D NoiseMap;

// ──────── Varyings ────────
in vec2 texCoord;
out vec4 fragColor;

// ──────── Constants ────────
const float PI = 3.14159265;

// ──────── Noise Functions ────────
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

// ──────── Helpers ────────
float verticalFade(float y) {
    float top = smoothstep(1.0, 0.7, y);
    float bottom = smoothstep(0.0, 0.1, y);
    return top * bottom;
}

vec2 buildSwirlUV(float angle, float y, vec2 offset) {
    vec2 uv;
    uv.x = fract(angle / (2.0 * PI));
    uv.y = y;
    uv += offset * 0.01;
    return uv;
}

// ──────── Main ────────
void main() {
    float height = clamp(texCoord.y, 0.0, 1.0);
    float angle = texCoord.x * 2.0 * PI;

    // Rotation based on height and time
    float rotation = Time * TwistSpeed + height * 12.0;

    // Core falloff calculation using CoreTightness
    float coreFalloff = exp(-pow((BaseRadius - (BaseRadius - TopRadius) * height) * CoreTightness, 2.0));

    // Large-scale swirls
    vec2 polar = vec2(texCoord.x, height);
    float n1 = noise(vec3(polar * 3.0, Time * 0.05));
    float n2 = noise(vec3(polar * 6.0, -Time * 0.04));
    angle += rotation + smoothstep(0.0, 1.0, n1) * 4.0;

    // Fine turbulence
    vec2 offset;
    offset.x = noise(vec3(texCoord * 5.0, Time * 0.02));
    offset.y = noise(vec3(texCoord.yx * 5.0, -Time * 0.02));
    angle += (offset.x - 0.5) * 3.0;

    // Final UVs
    vec2 swirlUV = buildSwirlUV(angle, height, offset);

    // Apply flow warping
    vec2 flow = texture(FlowMap, texCoord).rg - 0.5;
    swirlUV += flow * FlowIntensity;

    // Base texture sample
    vec4 base = texture(Sampler0, swirlUV);

    // Normal map for lighting
    vec3 normal = normalize(texture(NormalMap, swirlUV).rgb * 2.0 - 1.0);
    vec3 lightDir = normalize(vec3(LightDirX, LightDirY, LightDirZ));
    float lighting = max(dot(normal, lightDir), 0.0);

    // Final smoke color
    vec3 skyColor = vec3(SkyColorR, SkyColorG, SkyColorB);
    vec3 color = mix(skyColor, base.rgb, DustIntensity);
    color *= 0.4 + 0.6 * lighting;
    color *= 0.5 + n1 * 0.5;

    // Final alpha
    float alpha = base.a * verticalFade(height);
    alpha *= mix(0.8, 1.0, texture(NoiseMap, swirlUV * 4.0).r);
    alpha *= 0.7 + n2 * 0.3;
    alpha = clamp(alpha * coreFalloff, 0.5, 1.0);

    fragColor = vec4(color, alpha);
}
