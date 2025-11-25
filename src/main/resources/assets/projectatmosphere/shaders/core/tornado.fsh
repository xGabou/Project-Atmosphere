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
uniform float Scale;

uniform float LightDirX;
uniform float LightDirY;
uniform float LightDirZ;

uniform sampler2D Sampler0;
uniform sampler2D FlowMap;
uniform sampler2D NormalMap;
uniform sampler2D NoiseMap;
uniform sampler2D CloudScene;
uniform float ScreenSizeX;
uniform float ScreenSizeY;

// ──────── Varyings ────────
in vec2 texCoord;
out vec4 fragColor;

// ──────── Constants ────────
const float PI = 3.14159265;

// ──────── Noise Functions ────────
float noise(in vec3 x) {
    vec3 p = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    vec2 uv = (p.xy + vec2(37.0, 17.0) * p.z) + f.xy;
    vec2 rg = textureLod(NoiseMap, (uv + 0.5) / 256.0, 0.0).yx;
    return -1.0 + 2.4 * mix(rg.x, rg.y, f.z);
}

mat2 Spin(float angle) {
    return mat2(cos(angle), -sin(angle), sin(angle), cos(angle));
}

float ridged(float f) {
    return 1.0 - 2.0 * abs(f);
}

float Shape(vec3 q) {
    q.y += 45.0;
    float h = 90.0;
    float t = Time;
    vec3 spin_pos = vec3(Spin(t - sqrt(q.y)) * q.xz, q.y - t * 5.0);
    float zcurve = pow(q.y, 1.5) * 0.03;
    float v = abs(length(q.xz) - zcurve) - 5.5 - clamp(zcurve * 0.2, 0.1, 1.0) * noise(spin_pos * vec3(0.1));
    v = v - ridged(noise(vec3(Spin(t * 1.5 + 0.1 * q.y) * q.xz, q.y - t * 4.0) * 0.3)) * 1.2;
    v = max(v, q.y - h);
    return min(max(v, -q.y), 0.0) + max(v, -q.y);
}

// ──────── Helpers ────────
float verticalFade(float y) {
    float top = smoothstep(1.0, 0.7, y);
    float bottom = smoothstep(0.0, 0.1, y);
    return top * bottom;
}

vec2 buildSwirlUV(float angle, float y, vec2 offset) {
    vec2 uv;
    uv.x = mod(angle / (2.0 * PI), 1.0);
    uv.y = y;
    uv += offset * 0.01;
    return uv;
}

// ──────── Main ────────
void main() {
    // v = normalized vertical coordinate (0..1) from mesh UVs
    float v = clamp(texCoord.y, 0.0, 1.0);

    // Apply overall scale
    float baseR = BaseRadius * Scale;
    float topR = TopRadius * Scale;
    float height = Height * Scale;

    // yWorld if/when you need actual units (blocks/meters)
    float yWorld = v * height;

    float angle = texCoord.x * 2.0 * PI;

    // Rotation based on time + vertical factor
    float rotation = Time * TwistSpeed + v * 12.0;

    // Core falloff uses normalized v (keep this as 0..1)
    float coreFalloff = exp(-pow((baseR - (baseR - topR) * v) * CoreTightness, 2.0));

    // Large-scale swirls
    vec2 polar = vec2(texCoord.x, v);
    float n1 = noise(vec3(polar * 3.0, Time * 0.05));
    float n2 = noise(vec3(polar * 6.0, -Time * 0.04));
    angle += rotation + smoothstep(0.0, 1.0, n1) * 4.0;

    // Fine turbulence
    vec2 offset;
    offset.x = noise(vec3(texCoord * 5.0, Time * 0.02));
    offset.y = noise(vec3(texCoord.yx * 5.0, -Time * 0.02));
    angle += (offset.x - 0.5) * 3.0;

    // Final UVs
    vec2 swirlUV = buildSwirlUV(angle, v, offset);

    // World position for volumetric shaping
    float radius = mix(baseR, topR, v);
    vec2 pos = vec2(cos(angle), sin(angle)) * radius;
    vec3 shapePos = vec3(pos.x, yWorld, pos.y);
    float density = max(-Shape(shapePos), 0.0);
    float filledDensity = clamp(density * 1.35, 0.6, 1.5);

    // Flow warp
    vec2 flow = texture(FlowMap, texCoord).rg - 0.5;
    swirlUV += flow * FlowIntensity;

    // Base + normal/lighting
    vec4 base = texture(Sampler0, swirlUV);
    vec3 normal = normalize(texture(NormalMap, swirlUV).rgb * 2.0 - 1.0);
    vec3 lightDir = normalize(vec3(LightDirX, LightDirY, LightDirZ));
    float lighting = max(dot(normal, lightDir), 0.0);

    // Screen-space cloud sampling (fix: don't normalize screen size)
    vec2 scrUV = gl_FragCoord.xy / vec2(ScreenSizeX, ScreenSizeY);
    vec3 cloudTint = texture(CloudScene, scrUV).rgb;

    float k = clamp(DustIntensity, 0.0, 1.0);
    vec3 color = mix(base.rgb, vec3(DustIntensity), k);
    color = mix(color, cloudTint, 0.35);
    color *= 0.5 + 0.5 * lighting;
    color *= 0.7 + 0.3 * filledDensity;

    // Alpha uses normalized v (0..1)
    float alphaBase = max(base.a, 0.95);
    float alpha = alphaBase * verticalFade(v);
    alpha *= clamp(filledDensity, 0.8, 1.2);
    alpha = clamp(alpha * coreFalloff, 0.8, 1.0);

    fragColor = vec4(color, alpha);
}
