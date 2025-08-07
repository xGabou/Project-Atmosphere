//#version 150
//
//// ----------------------------------------------------------------------------
//// Uniforms (must match tornado.json)
//// ----------------------------------------------------------------------------
//
//// Matrices (inherited from vertex shader)
//uniform mat4 ModelViewMat;
//uniform mat4 ProjMat;
//
//// Time and spin
//uniform float Time;
//uniform float TwistSpeed;
//
//// Geometry shape (if you still read these in the shader)
//uniform float BaseRadius;
//uniform float TopRadius;
//uniform float Height;
//
//// Dust/core
//uniform float DustIntensity;
//uniform float CoreTightness;
//
//// Flow‐map strength
//uniform float FlowIntensity;
//
//// Lighting
//uniform vec3 LightDir;
//
//// Sky blending
//uniform vec3 SkyColor;
//
//// Your four textures
//uniform sampler2D smokeUni;   // slot 0: smoke α‐map
//uniform sampler2D FlowMap;    // slot 1: flowmap.png
//uniform sampler2D NormalMap;  // slot 2: tornado_normal.png
//uniform sampler2D NoiseMap;   // slot 3: noise.png
//
//in vec2 texCoord;
//
//out vec4 fragColor;
//
//
//const float PI = 3.14159265;
//
//// ----------------------------------------------------------------------------
//// 3D Noise + FBM
//// ----------------------------------------------------------------------------
//float hash(vec3 p) {
//    p = fract(p * 0.3183099 + vec3(0.1,0.2,0.3));
//    p *= 17.0;
//    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
//}
//float noise(vec3 p) {
//    vec3 i = floor(p), f = fract(p), u = f*f*(3.0-2.0*f);
//    float n000 = hash(i + vec3(0,0,0));
//    float n100 = hash(i + vec3(1,0,0));
//    float n010 = hash(i + vec3(0,1,0));
//    float n110 = hash(i + vec3(1,1,0));
//    float n001 = hash(i + vec3(0,0,1));
//    float n101 = hash(i + vec3(1,0,1));
//    float n011 = hash(i + vec3(0,1,1));
//    float n111 = hash(i + vec3(1,1,1));
//    return mix(
//        mix(mix(n000,n100,u.x),mix(n010,n110,u.x),u.y),
//        mix(mix(n001,n101,u.x),mix(n011,n111,u.x),u.y),
//        u.z
//    );
//}
//float fbm(vec3 p) {
//    float v = 0.0, amp = 0.5;
//    for(int i=0; i<5; i++){
//        v    += amp * noise(p);
//        p    *= 2.0;
//        amp *= 0.5;
//    }
//    return v;
//}
//
//// ----------------------------------------------------------------------------
//// Vertical fade for smooth ends
//// ----------------------------------------------------------------------------
//float verticalFade(float h) {
//    float top    = smoothstep(1.0, 0.8, h);
//    float bottom = smoothstep(0.0, 0.2, h);
//    return top * bottom;
//}
//
//// ----------------------------------------------------------------------------
//// Build swirl UV
//// ----------------------------------------------------------------------------
//vec2 buildSwirlUV(float angle, float h, vec2 fineOffset) {
//    vec2 uv;
//    uv.x = fract(angle / (2.0 * PI)); // [0..1]
//    uv.y = h;
//    return uv + fineOffset * 0.005;
//}
//
//// ----------------------------------------------------------------------------
//// Main
//// ----------------------------------------------------------------------------
//void main() {
//    // basic swirl coords
//    float h       = clamp(texCoord.y, 0.0, 1.0);
//    float baseAng = texCoord.x * 2.0 * PI;
//    float tAng    = Time * TwistSpeed + h * 8.0;
//
//    // layered FBM curls
//    float curl1 = fbm(vec3(texCoord*2.0,  Time*0.08)) * 3.0;
//    float curl2 = fbm(vec3(texCoord*5.0, -Time*0.06)) * 1.5;
//    float angle = baseAng + tAng + curl1 + curl2;
//
//    // fine turbulence
//    vec2 fine;
//    fine.x = noise(vec3(texCoord*12.0,  Time*0.15));
//    fine.y = noise(vec3(texCoord.yx*12.0, -Time*0.15));
//
//    // initial swirl UV
//    vec2 swirlUV = buildSwirlUV(angle, h, fine);
//
//    // advect via FlowMap
//    vec2 flow = texture(FlowMap, swirlUV).rg * 2.0 - 1.0;
//    swirlUV  += flow * FlowIntensity * sin(Time * 0.5);
//
//    // **static noise sample** (fast 2D lookup)
//    // tile 8×8 and animate offset
//    vec2 noiseUV = swirlUV * 8.0 + vec2(Time * 0.05, -Time * 0.03);
//    float staticN = texture(NoiseMap, noiseUV).r;
//
//    // base smoke lookup
//    vec4 base = texture(smokeUni, swirlUV);
//
//    // lighting via normal map
//    vec3 n        = texture(NormalMap, swirlUV).rgb * 2.0 - 1.0;
//    float diff    = max(dot(normalize(n), normalize(LightDir)), 0.0);
//    vec3 litColor = base.rgb * (0.6 + 0.4 * diff);
//
//    // tint warm at bottom
//    vec3 warmTint = mix(vec3(0.8,0.7,0.6), vec3(1.0), h * 0.5);
//    vec3 color    = mix(litColor, warmTint, 0.3 * (1.0 - h));
//
//    // **apply static‐noise flicker**
//    color *= mix(0.9, 1.1, staticN);
//
//    // dust specks (unchanged)
//    float dustNoise = noise(vec3(texCoord*30.0, Time*1.0));
//    float dustMask  = step(0.92, dustNoise) * DustIntensity * (1.0 - h);
//
//    // final alpha with static jitter
//    float alpha = base.a
//    * smoothstep(0.5 + CoreTightness*0.5, 0.0, abs(texCoord.x-0.5)*2.0)
//    * verticalFade(h)
//    * mix(0.7, 1.0, fbm(vec3(texCoord*3.0, Time*0.2)))
//    * mix(0.95, 1.05, staticN);
//
//    float skyFade = smoothstep(0.6, 1.0, texCoord.y);
//    color       = mix(color, SkyColor, skyFade);
//    alpha      *= (1.0 - skyFade);
//    fragColor = vec4(1.0, 0.0, 0.0, 1.0);
//
//}
#version 150

in vec2  texCoord;
in float vRadius;   // carried from the vertex shader

// all your declared funnel/tint parameters
uniform float BaseRadius;
uniform float TopRadius;
uniform float Height;
uniform float DustIntensity;
uniform float CoreTightness;
uniform float FlowIntensity;
uniform vec3  LightDir;
uniform vec3  SkyColor;

// all your declared samplers
uniform sampler2D smokeUni;
uniform sampler2D FlowMap;
uniform sampler2D NormalMap;
uniform sampler2D NoiseMap;

out vec4 fragColor;

void main() {
    // 1) base alpha mask
    vec4 smoke = texture(smokeUni, texCoord);

    // 2) UV warp from your flow map
    vec2 flowSample = texture(FlowMap, texCoord).rg - 0.5;
    vec2 warpedUV  = texCoord + flowSample * FlowIntensity;

    // 3) break edges with noise (tiled by radius)
    float noise = texture(NoiseMap, warpedUV * (vRadius / BaseRadius)).r;

    // 4) simple normal‐based lighting
    vec3 normal     = normalize(texture(NormalMap, texCoord).rgb * 2.0 - 1.0);
    float lightFact = clamp(dot(normal, normalize(LightDir)), 0.0, 1.0);

    // 5) thinner core based on CoreTightness
    float coreMask = smoothstep(CoreTightness * 0.5, CoreTightness, abs(texCoord.x - 0.5));

    // 6) combine alpha: smoke shape, core hollow, plus noise fuzz
    float alpha = mix(smoke.a, smoke.a * 0.2, coreMask) + noise * 0.1;
    alpha *= DustIntensity;
    alpha = clamp(alpha, 0.0, 1.0);

    // 7) tint color by sky + a little radius‐based variation
    vec3 baseCol = mix(vec3(0.2, 0.3, 0.4), SkyColor, vRadius / TopRadius);
    vec3 litCol  = baseCol * (0.3 + lightFact * 0.7);

    // 8) output
    fragColor = vec4(litCol, alpha);
}
