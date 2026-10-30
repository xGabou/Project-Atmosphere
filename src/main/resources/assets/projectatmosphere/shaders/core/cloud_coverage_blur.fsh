#version 150

// T253. The nimbostratus deck coverage, pre-blurred instead of per density sample.
// Each texel holds T248's kernel: the mean of saturate(coverage * CoverageMul) (with
// the map-border fade sampleWeather applies) at its centre and the eight neighbours
// 40 blocks away. The target has the weather map's extent at 8-block texels, so the
// 40-block offsets are whole texels and one bilinear fetch reproduces the nine-tap
// average wherever the per-tap clamp and border fade are inactive. Rebuilt when the
// weather raster is re-baked or CoverageMul changes (CloudWeatherMapRenderer).

uniform sampler2D WeatherMapSampler;
uniform float WeatherExtent;
uniform float CoverageMul;

in vec2 texCoord;
out vec4 fragColor;

float rawCoverage(vec2 uv) {
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        return 0.0;
    }
    float edgeDistance = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
    return clamp(texture(WeatherMapSampler, uv).r * smoothstep(0.0, 0.055, edgeDistance) * CoverageMul, 0.0, 1.0);
}

void main() {
    vec2 offset = vec2(40.0 / WeatherExtent);
    float sum = 0.0;
    for (int dz = -1; dz <= 1; dz++) {
        for (int dx = -1; dx <= 1; dx++) {
            sum += rawCoverage(texCoord + vec2(float(dx), float(dz)) * offset);
        }
    }
    // P3 (stabilisation perf): G = occupancy, 1 where any weather texel within one texel (8 blocks) has
    // coverage - covers sampleWeather's bilinear reach. The renderer mipmaps this target after the pass; a
    // half-float average keeps any non-zero, so a coarse level answers "may anything be here" for a whole tile
    // (the march's empty-space jump, PaEmptySkip).
    vec2 texel = 1.0 / vec2(textureSize(WeatherMapSampler, 0));
    float occupied = 0.0;
    for (int dz = -1; dz <= 1; dz++) {
        for (int dx = -1; dx <= 1; dx++) {
            vec2 uv = texCoord + vec2(float(dx), float(dz)) * texel;
            if (uv.x >= 0.0 && uv.x <= 1.0 && uv.y >= 0.0 && uv.y <= 1.0
                    && texture(WeatherMapSampler, uv).r > 0.0) {
                occupied = 1.0;
            }
        }
    }
    fragColor = vec4(sum / 9.0, occupied, 0.0, 1.0);
}
