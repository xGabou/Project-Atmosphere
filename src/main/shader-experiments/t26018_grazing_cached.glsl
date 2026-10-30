// T260.18 stage 2 L-graz-c (PaDiagnosticLightingMode 41; experimental fixture only, never production). Injected by
// generateLeanFinalShader into the generated ..._neutral_t18grazc program only, after t26018_grazing.glsl. The budget
// form of L-graz: the same six bounded grazing rays, evaluated ONCE PER SURFACE - at the first skin sample along the
// pixel's view ray - and reused for every sample within 20 blocks of it; samples with no such surface use the isotropic
// baked ambient for that 0.4 share. T26011Smoke models18 "Gc" is the CPU mirror.
bool paT18Cached = false;
vec3 paT18CacheQ;
float paT18CacheG;

vec3 paT18GrazingCached(vec3 q, vec2 light) {
    vec4 dir;
    float share;
    paT17Baked(q, dir, share);
    float direct = PA_NEUTRAL_SUN_E / (4.0 * 3.14159265) * exp(-light.x);
    float m = length(dir.xyz);
    vec3 n = m > 1e-3 ? dir.xyz / m : vec3(0.0);
    float skin = saturate(1.5 * m);
    float a30 = mix(light.y, paT17DirectionalAmbient(light.y, share, n.y), skin);
    if (skin > 0.05 && light.y > 0.002 && (!paT18Cached || distance(q, paT18CacheQ) > 20.0)) {
        vec3 sun = paNeutralSun();
        float az = atan(sun.x, sun.z), g = 0.0;
        for (int k = 0; k < 6; k++) {
            bool up = (k & 1) == 0;
            float el = radians(up ? 8.0 : -8.0), a = az + radians(60.0 * float(k));
            vec3 d = vec3(cos(el) * sin(a), sin(el), cos(el) * cos(a));
            float vis = exp(-paNeutralOpticalDepth(q, d, 3.0, 1.7, 64.0, 7, 8.0));
            g += (up ? PA_NEUTRAL_SKY_L : PA_NEUTRAL_GROUND_L) * vis * (1.0 + skin * dot(n, d));
        }
        paT18Cached = true; paT18CacheQ = q; paT18CacheG = g / 6.0;
    }
    float gUse = paT18Cached && distance(q, paT18CacheQ) <= 20.0 ? paT18CacheG : light.y;
    return vec3(PA_NEUTRAL_EXPOSURE * (direct + 0.6 * a30 + 0.4 * gUse));
}

