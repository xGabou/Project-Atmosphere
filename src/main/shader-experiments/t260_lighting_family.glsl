// T260.L-family (PaDiagnosticLightingMode 42; experimental fixture only, never production). Injected by
// generateLeanFinalShader into the generated ..._neutral_lfam program only; cloud_atmosphere_volume.fsh is unchanged.
// The PRODUCTION lighting family (evaluateLightingComponents) on the frozen T260 fixture, fed by the baked light lattice
// instead of a light march and by fixed noon constants instead of the world's LightColor / ambient uniforms:
// - Hillaire multiple scattering: three octaves (extinction x1 / 0.42 / 0.1764, weights 1 / 0.52 / 0.27, phase mixed
//   toward isotropic per octave, normalised) of the UNCAPPED baked sun optical depth (T260.17 directional block);
// - Beer-powder on the local density, dual-lobe HG phase (production dualLobePhase) of the baked sun against the view ray;
// - silver lining on the optically thin forward shell;
// - production ambient: AmbientBottom -> AmbientTop by height in the system box, underside shade and light-path retention
//   with the cumulonimbus material darkness (0.78), T244 shadow neutralisation; the one adaptation is a soft sky-visibility
//   occlusion from the baked T260.5 ambient (crevices and buried skin see less sky);
// - filmic 1 - exp(-1.30 radiance), as production returns it per sample.
// No grazing march and no extra density taps: two baked lattice lookups per sample.
// Mode 43 (controlled arm, ..._neutral_lfam_cone) changes ONE input: the sun optical depth near the skin. The lattice
// (14-16 blocks) interpolates the lit skin from interior nodes; 43 integrates the first lattice cell toward the sun
// exactly with two density taps and adds the baked uncapped depth one cell sun-ward (the production light march's
// first taps, then the bake as its tail). Everything else is mode 42.
const vec3 PA_LF_SUN = vec3(1.00, 0.97, 0.90);           // production DAY_SUN (VolumetricCloudLighting)
const vec3 PA_LF_AMBIENT_TOP = vec3(0.47, 0.65, 1.00);   // production noon ambientTop: max(sky, (0.30, 0.36, 0.46)), plains sky
const vec3 PA_LF_AMBIENT_BOTTOM = vec3(0.30, 0.34, 0.41); // production noon ambientBottom: mix(ground tone, 0.55 sky, 0.35)
const float PA_LF_MATERIAL_DARKNESS = 0.78;              // production CUMULONIMBUS material darkness
const float PA_LF_OPEN_AMBIENT = 0.1475;                 // T260.5 baked ambient of a fully open sample (3 x 0.15 + 3 x 0.145) / 6

vec3 paLFamilyLighting(vec3 p, float localDensity, bool skinCone, out float sunOpticalDepth) {
    vec3 q = p - PaMorphologyOrigin;
    vec2 light = paNeutralBaked(q);
    vec4 dir;
    float share;
    float tau;
    if (skinCone) {
        vec3 sun = paNeutralSun();
        float cell = paNeutralHeaderA.w;
        paT17Baked(q + sun * cell, dir, share);
        float tail = paNeutralHeaderC.w > 0.5 ? dir.w : paNeutralBaked(q + sun * cell).x;
        float near = (paNeutralDensity(q + sun * (0.25 * cell)) + paNeutralDensity(q + sun * (0.75 * cell))) * 0.5 * cell;
        tau = near * ExtinctionScale + tail;
    } else {
        paT17Baked(q, dir, share);
        tau = paNeutralHeaderC.w > 0.5 ? max(dir.w, light.x) : light.x; // uncapped when the directional block exists
    }
    sunOpticalDepth = tau;
    float cosTheta = dot(normalize(p - CameraPos), paNeutralSun());
    float h01 = saturate((q.y - PaMorphologyMin.y) / max(PaMorphologyMax.y - PaMorphologyMin.y, 1.0));

    float scatter = 0.0, scatterWeight = 0.0, a = 1.0, b = 1.0;
    for (int o = 0; o < 3; o++) {
        scatter += b * mix(0.0795775, dualLobePhase(cosTheta), a) * exp(-tau * a);
        scatterWeight += b;
        a *= 0.42;
        b *= 0.52;
    }
    scatter /= scatterWeight;
    float powder = 1.0 - exp(-localDensity * 24.0);
    float powderTerm = mix(1.0, saturate(powder * 1.35), saturate(cosTheta * 0.5 + 0.5) * 0.72);

    float undersideShade = saturate((0.18 + PA_LF_MATERIAL_DARKNESS * 0.82) * pow(1.0 - h01, 0.70) * (0.46 + localDensity * 0.72));
    float directTransmission = exp(-tau);
    vec3 sunTerm = PA_LF_SUN * scatter * powderTerm * (4.0 * 3.14159265);
    sunTerm *= mix(1.0, 0.70, undersideShade);

    vec3 ambient = mix(PA_LF_AMBIENT_BOTTOM, PA_LF_AMBIENT_TOP, h01);
    ambient *= mix(1.0, 0.58, undersideShade);
    ambient *= mix(mix(0.74, 0.58, PA_LF_MATERIAL_DARKNESS), 1.0, directTransmission);
    ambient *= mix(0.55, 1.0, saturate(2.0 * light.y / PA_LF_OPEN_AMBIENT));
    {
        float shadowed = 1.0 - directTransmission;
        float lowerWeight = mix(0.75, 1.0, 1.0 - h01);
        float ambientLuma = dot(ambient, vec3(0.2126, 0.7152, 0.0722));
        ambient = mix(ambient, ambientLuma * vec3(1.03, 1.0, 0.95), shadowed * lowerWeight);
        ambient *= 1.0 - 0.12 * shadowed * lowerWeight;
    }

    float forward = saturate(cosTheta);
    float forward2 = forward * forward;
    float forward4 = forward2 * forward2;
    float edgeShell = smoothstep(0.008, 0.09, localDensity) * (1.0 - smoothstep(0.22, 0.66, localDensity));
    float silver = forward4 * forward4 * forward4 * sqrt(directTransmission) * edgeShell;
    vec3 radiance = max(sunTerm + ambient * 0.86 + PA_LF_SUN * silver * 0.48, vec3(0.0));
    return vec3(1.0) - exp(-radiance * 1.30);
}

