#version 150

in vec2 texCoord;
uniform float Time;

out vec4 fragColor;

vec3 mod289(vec3 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
vec2 mod289(vec2 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
vec3 permute(vec3 x) { return mod289((x * 34.0 + 1.0) * x); }

float snoise(vec2 v) {
    const vec4 C = vec4(0.211324865, 0.366025403, -0.577350269, 0.0243902439);
    vec2 i = floor(v + dot(v, C.yy));
    vec2 x0 = v - i + dot(i, C.xx);
    vec2 i1 = (x0.x > x0.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
    vec4 x12 = x0.xyxy + C.xxzz; x12.xy -= i1;
    vec3 p0 = vec3(x0, 0.0); vec3 p1 = vec3(x12.xy, 0.0); vec3 p2 = vec3(x12.zw, 0.0);
    i = mod289(i);
    vec3 perm = permute(permute(i.y + vec3(0.0, i1.y, 1.0)) + i.x + vec3(0.0, i1.x, 1.0));
    vec3 m = max(0.5 - vec3(dot(p0,p0), dot(p1,p1), dot(p2,p2)), 0.0);
    m = m * m * m * m;
    vec3 x = 2.0 * fract(perm * C.www) - 1.0;
    vec3 h = abs(x) - 0.5; vec3 ox = floor(x + 0.5); vec3 a0 = x - ox;
    m *= 1.79284291400159 - 0.85373472095314 * (a0*a0 + h*h);
    vec3 g;
    g.x = a0.x * p0.x + h.x * p0.y;
    g.y = a0.y * p1.x + h.y * p1.y;
    g.z = a0.z * p2.x + h.z * p2.y;
    return 130.0 * dot(m, g);
}

void main() {
    vec2 uv = texCoord;
    vec2 center = vec2(0.5, 0.5);
    vec2 pos = uv - center;

    float angle = Time * 1.5;
    float s = sin(angle);
    float c = cos(angle);
    mat2 rot = mat2(c, -s, s, c);
    pos = rot * pos;
    pos += center;
    pos.y += Time * 0.25;

    float noiseVal = snoise(pos * 5.0);
    float alpha = smoothstep(0.2, 0.5, noiseVal);
    float fadeTop = smoothstep(1.0, 0.6, uv.y);

    fragColor = vec4(vec3(0.2, 0.2, 0.2), alpha * fadeTop);
}
