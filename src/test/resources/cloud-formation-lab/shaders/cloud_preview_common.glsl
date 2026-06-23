// Shared placeholder helpers for the standalone Cloud Formation Lab preview.
// Keep this lightweight. Real Minecraft cloud GLSL belongs in the renderer path.

float paHash(float n) {
    return fract(sin(n) * 43758.5453123);
}

float paNoise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n = dot(i, vec3(1.0, 57.0, 113.0));
    float a = mix(paHash(n + 0.0), paHash(n + 1.0), f.x);
    float b = mix(paHash(n + 57.0), paHash(n + 58.0), f.x);
    float c = mix(paHash(n + 113.0), paHash(n + 114.0), f.x);
    float d = mix(paHash(n + 170.0), paHash(n + 171.0), f.x);
    return mix(mix(a, b, f.y), mix(c, d, f.y), f.z);
}

float paFbm(vec3 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += paNoise3(p) * a;
        p *= 2.02;
        a *= 0.5;
    }
    return v;
}

mat3 paCameraBasis(vec3 ro, vec3 target) {
    vec3 f = normalize(target - ro);
    vec3 r = normalize(cross(f, vec3(0.0, 1.0, 0.0)));
    vec3 u = cross(r, f);
    return mat3(r, u, f);
}
