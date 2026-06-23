precision highp float;

#define MAX_FIELDS 8

varying vec2 vUv;
uniform vec2 uResolution;
uniform float uTime;
uniform vec3 uCameraPos;
uniform vec3 uCameraTarget;
uniform vec3 uSunDirection;
uniform int uFieldCount;
uniform vec3 uFieldCenter[MAX_FIELDS];
uniform float uFieldRadius[MAX_FIELDS];
uniform float uFieldBaseY[MAX_FIELDS];
uniform float uFieldTopY[MAX_FIELDS];
uniform float uFieldDensity[MAX_FIELDS];
uniform float uFieldCoverage[MAX_FIELDS];
uniform float uFieldGrowth[MAX_FIELDS];
uniform float uFieldDecay[MAX_FIELDS];
uniform float uFieldHumidityInfluence[MAX_FIELDS];
uniform vec3 uFieldWind[MAX_FIELDS];
uniform float uFieldVerticalDevelopment[MAX_FIELDS];
uniform float uFieldStormPotential[MAX_FIELDS];
uniform float uFieldSeed[MAX_FIELDS];
uniform float uFieldHydration[MAX_FIELDS];
uniform float uFieldAge[MAX_FIELDS];
uniform float uFieldCloudletCount[MAX_FIELDS];
uniform float uDensityMultiplier;
uniform float uCoverageMultiplier;
uniform float uHydrationMultiplier;
uniform float uLightingStrength;
uniform int uDensityMode;
uniform int uShowBounds;

// ForecastParameterState uniforms.
uniform float uForecastTemperature;
uniform float uForecastHumidity;
uniform float uForecastPressure;
uniform float uForecastWindSpeed;
uniform float uForecastWindDirection;
uniform float uForecastCloudCover;
uniform float uForecastRainIntensity;
uniform float uForecastStormChance;
uniform float uForecastInstability;
uniform float uForecastVerticalDevelopment;
uniform float uForecastBaseY;
uniform float uForecastTopY;
uniform float uForecastHour;
uniform float uForecastSeed;

// Region/spawn simulation uniforms.
uniform float uRegionAllowsClouds;
uniform float uRegionMoisture;
uniform float uTerrainLift;
uniform float uFrontConvergence;
uniform float uSpawnSuppression;

// Preview state uniforms.
uniform int uPreviewFieldMode;
uniform float uPreviewRendering;
uniform float uPreviewSpeed;

#include "cloud_preview_common.glsl"
#include "cloud_preview_density.glsl"

vec3 sky(vec3 rd) {
    float t = clamp(rd.y * 0.5 + 0.5, 0.0, 1.0);
    return mix(vec3(0.48, 0.62, 0.78), vec3(0.12, 0.20, 0.30), 1.0 - t);
}

void main() {
    vec2 uv = (gl_FragCoord.xy * 2.0 - uResolution.xy) / max(uResolution.x, uResolution.y);
    vec3 ro = uCameraPos;
    mat3 basis = paCameraBasis(ro, uCameraTarget);
    vec3 rd = normalize(basis * normalize(vec3(uv, 1.35)));
    vec3 col = sky(rd);
    float alpha = 0.0;
    float totalDensity = 0.0;
    float t = 0.0;

    for (int step = 0; step < 72; step++) {
        vec3 p = ro + rd * t;
        float storm = 0.0;
        float d = sceneDensity(p, storm);
        if (d > 0.002) {
            float light = clamp(dot(normalize(uSunDirection), vec3(0.2, 0.75, 0.3)) * 0.5 + 0.5, 0.0, 1.0);
            vec3 cloudColor = mix(vec3(0.92, 0.95, 0.98), vec3(0.42, 0.45, 0.50), storm * 0.65);
            cloudColor *= mix(0.52, 1.18, light * uLightingStrength);
            float a = 1.0 - exp(-d * 0.055);
            col = mix(col, cloudColor, a * (1.0 - alpha));
            alpha += a * (1.0 - alpha);
            totalDensity += d;
        }
        t += 7.5;
        if (alpha > 0.96 || t > 760.0) break;
    }

    if (uDensityMode == 1) {
        float v = clamp(totalDensity * 0.05, 0.0, 1.0);
        col = mix(vec3(0.04, 0.08, 0.12), vec3(v, v * 0.92, v * 0.78), v);
    }

    if (uShowBounds == 1 && uFieldCount > 0) {
        for (int i = 0; i < MAX_FIELDS; i++) {
            if (i >= uFieldCount) break;
            vec3 c = uFieldCenter[i];
            float r = uFieldRadius[i];
            float planeHit = smoothstep(0.018, 0.0, abs(rd.y));
            float baseHint = smoothstep(5.0, 0.0, abs((ro.y + rd.y * 260.0) - uFieldBaseY[i]));
            float topHint = smoothstep(5.0, 0.0, abs((ro.y + rd.y * 260.0) - uFieldTopY[i]));
            float radial = abs(length((ro.xz + rd.xz * 260.0) - c.xz) - r);
            float ring = smoothstep(5.0, 0.0, radial) * planeHit;
            col = mix(col, vec3(0.9, 0.78, 0.35), clamp(ring + baseHint * 0.18 + topHint * 0.22, 0.0, 0.55));
        }
    }

    gl_FragColor = vec4(col, 1.0);
}
