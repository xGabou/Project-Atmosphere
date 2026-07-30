#version 150

// Builds the cloud shadow transmittance map from the weather map. Each texel
// covers one ground position; the lookup into the weather map is offset along
// the sun direction projected from the cloud layer down to the ground plane.

uniform sampler2D WeatherMapSampler;

uniform vec2 ShadowOrigin;   // world XZ of texel (0,0) of the shadow map
uniform float ShadowExtent;  // world size covered by the shadow map
uniform vec2 WeatherOrigin;
uniform float WeatherExtent;
uniform float SlabBaseY;
uniform float SlabTopY;
uniform float GroundY;       // representative ground height (camera-based)
uniform vec3 LightDir;
uniform float DensityMul;

in vec2 texCoord;
out vec4 fragColor;

float saturate(float v) {
    return clamp(v, 0.0, 1.0);
}

vec4 sampleWeather(vec2 worldXZ) {
    vec2 uv = (worldXZ - WeatherOrigin) / WeatherExtent;
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        return vec4(0.0);
    }
    return texture(WeatherMapSampler, uv);
}

void main() {
    vec2 groundXZ = ShadowOrigin + texCoord * ShadowExtent;
    float sunY = max(LightDir.y, 0.08);
    float slabSpan = max(SlabTopY - SlabBaseY, 1.0);

    // March a few taps through the cloud layer along the sun direction so
    // tall storm towers cast longer, softer shadows than thin sheets.
    float opticalDepth = 0.0;
    for (int i = 0; i < 4; i++) {
        float layerY = mix(SlabBaseY, SlabTopY, (float(i) + 0.5) / 4.0);
        vec2 offset = (LightDir.xz / sunY) * (layerY - GroundY);
        vec4 weather = sampleWeather(groundXZ + offset);
        if (weather.r <= 0.004) {
            continue;
        }
        float baseY = SlabBaseY + weather.g * slabSpan;
        float topY = SlabBaseY + weather.b * slabSpan;
        if (layerY < baseY || layerY > topY) {
            continue;
        }
        float thickness = slabSpan / 4.0;
        opticalDepth += weather.r * (0.7 + weather.a * 0.6) * thickness;
    }

    float transmittance = exp(-opticalDepth * 0.016 * DensityMul);
    fragColor = vec4(vec3(saturate(transmittance)), 1.0);
}
