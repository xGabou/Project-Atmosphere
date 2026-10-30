// T260.18 stage 2 L-graz (PaDiagnosticLightingMode 40; experimental fixture only, never production). Injected by
// generateLeanFinalShader into the generated ..._neutral_t18graz program only; cloud_atmosphere_volume.fsh is unchanged.
// L-dir (mode 35: the baked +-30 deg ambient split sky / ground on the baked filtered normal) for 0.6 of the ambient,
// plus BOUNDED GRAZING SHADOWS for the other 0.4: six rays at +-8 deg (alternating sky / ground, 60 deg apart, the first
// toward the sun azimuth) marched per sample over ~160 blocks through the fixture density, each weighted by a soft
// (wrapped) Lambert on the same normal. Light beyond the bound is treated as open. Buried samples (baked ambient ~0)
// skip the rays. Same constants, sun and exposure as T260.5; T26011Smoke models18 "G" is the CPU mirror.
vec3 paT18Grazing(vec3 q, vec2 light) {
    vec4 dir;
    float share;
    paT17Baked(q, dir, share);
    float direct = PA_NEUTRAL_SUN_E / (4.0 * 3.14159265) * exp(-light.x);
    float m = length(dir.xyz);
    vec3 n = m > 1e-3 ? dir.xyz / m : vec3(0.0);
    float skin = saturate(1.5 * m);
    float a30 = mix(light.y, paT17DirectionalAmbient(light.y, share, n.y), skin);
    float g = 0.0;
    if (light.y > 0.002) {
        vec3 sun = paNeutralSun();
        float az = atan(sun.x, sun.z);
        for (int k = 0; k < 6; k++) {
            bool up = (k & 1) == 0;
            float el = radians(up ? 8.0 : -8.0), a = az + radians(60.0 * float(k));
            vec3 d = vec3(cos(el) * sin(a), sin(el), cos(el) * cos(a));
            float vis = exp(-paNeutralOpticalDepth(q, d, 3.0, 1.7, 64.0, 7, 8.0));
            g += (up ? PA_NEUTRAL_SKY_L : PA_NEUTRAL_GROUND_L) * vis * (1.0 + skin * dot(n, d));
        }
    }
    return vec3(PA_NEUTRAL_EXPOSURE * (direct + 0.6 * a30 + 0.4 * g / 6.0));
}

