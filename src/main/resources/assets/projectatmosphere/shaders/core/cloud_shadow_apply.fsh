#version 150

// Fullscreen multiply pass that darkens the already-rendered scene under
// cloud cover. World positions are reconstructed from the depth buffer and
// projected onto the shadow transmittance map along the sun direction, giving
// spatially correct soft moving cloud shadows with no terrain re-render.

uniform sampler2D ShadowMapSampler;
uniform sampler2D SceneDepthSampler;

uniform mat4 InvProjMat;
uniform mat4 InvViewRotMat;
uniform vec3 CameraPos;

uniform vec2 ShadowOrigin;
uniform float ShadowExtent;
uniform float SlabBaseY;
uniform float GroundY; // reference ground height the shadow map was built for
uniform vec3 LightDir;
uniform float ShadowStrength; // 0..1 how dark shadows can get
uniform float DaylightFactor; // 0 at night (no shadows), 1 at midday

in vec2 texCoord;
out vec4 fragColor;

float saturate(float v) {
    return clamp(v, 0.0, 1.0);
}

void main() {
    float sceneDepth = texture(SceneDepthSampler, texCoord).r;
    if (sceneDepth >= 0.995 || DaylightFactor <= 0.02) {
        fragColor = vec4(1.0);
        return;
    }

    vec4 clip = vec4(texCoord * 2.0 - 1.0, sceneDepth * 2.0 - 1.0, 1.0);
    vec4 view = InvProjMat * clip;
    view /= max(abs(view.w), 0.00001);
    vec3 worldRel = (InvViewRotMat * vec4(view.xyz, 0.0)).xyz;
    vec3 world = CameraPos + worldRel;

    if (world.y >= SlabBaseY) {
        fragColor = vec4(1.0);
        return;
    }

    // Residual sun-direction correction for pixels above/below the reference
    // ground height the shadow map was integrated for.
    float sunY = max(LightDir.y, 0.08);
    vec2 offset = (LightDir.xz / sunY) * (GroundY - world.y);
    vec2 shadowUv = (world.xz + offset - ShadowOrigin) / ShadowExtent;
    if (shadowUv.x < 0.0 || shadowUv.x > 1.0 || shadowUv.y < 0.0 || shadowUv.y > 1.0) {
        fragColor = vec4(1.0);
        return;
    }

    float transmittance = texture(ShadowMapSampler, shadowUv).r;

    // Distance fade: soften shadows far from the camera so map edges never
    // pop, and clamp the floor so shadows never go black.
    float distance01 = saturate(length(worldRel) / (ShadowExtent * 0.45));
    float fade = 1.0 - smoothstep(0.75, 1.0, distance01);
    float strength = ShadowStrength * DaylightFactor * fade;
    float floorValue = 1.0 - strength;
    float factor = mix(1.0, max(transmittance, 0.0), strength);
    factor = max(factor, floorValue * 0.55 + 0.45 * factor);

    // Slight cool tint in shadowed areas.
    vec3 shadowColor = vec3(factor);
    shadowColor.b = mix(shadowColor.b, min(1.0, shadowColor.b * 1.06 + 0.02), 1.0 - factor);

    fragColor = vec4(clamp(shadowColor, vec3(0.45), vec3(1.0)), 1.0);
}
