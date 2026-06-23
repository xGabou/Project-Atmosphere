// Placeholder field density for the standalone lab.
// Uniform arrays are indexed directly by the sceneDensity loop variable for WebGL 1.

float sceneDensity(vec3 p, out float storm) {
    float d = 0.0;
    storm = 0.0;
    for (int i = 0; i < MAX_FIELDS; i++) {
        if (i >= uFieldCount) break;

        vec3 center = uFieldCenter[i];
        float radius = max(8.0, uFieldRadius[i]);
        float baseY = uFieldBaseY[i];
        float topY = max(baseY + 4.0, uFieldTopY[i]);
        float height01 = clamp((p.y - baseY) / (topY - baseY), 0.0, 1.0);
        vec2 xz = (p.xz - center.xz) / radius;
        float horizontal = length(xz);
        float shape = 1.0 - smoothstep(0.58, 1.04, horizontal);
        float baseFalloff = smoothstep(0.00, 0.16, height01);
        float topFalloff = 1.0 - smoothstep(0.78, 1.0, height01);
        float vertical = baseFalloff * topFalloff;
        float tower = mix(1.0, 1.0 - smoothstep(0.20, 0.82, horizontal), uFieldVerticalDevelopment[i]);
        vec3 windOffset = uFieldWind[i] * uTime * 0.045;
        float n = paFbm((p + windOffset + uFieldSeed[i] * 0.017) * mix(0.018, 0.035, uFieldVerticalDevelopment[i]));
        float detail = smoothstep(0.25, mix(0.72, 0.42, uFieldCoverage[i] * uCoverageMultiplier), n);
        float hydration = clamp(uFieldHydration[i] * uHydrationMultiplier, 0.0, 1.5);
        float fieldD = shape * vertical * tower * detail;
        fieldD *= uFieldDensity[i] * uDensityMultiplier;
        fieldD *= mix(0.42, 1.2, clamp(uFieldCoverage[i] * uCoverageMultiplier, 0.0, 1.0));
        fieldD *= mix(0.35, 1.0, hydration);
        fieldD *= 1.0 - uFieldDecay[i] * 0.55;
        d += max(0.0, fieldD);
        storm = max(storm, uFieldStormPotential[i]);
    }
    return clamp(d, 0.0, 2.5);
}
