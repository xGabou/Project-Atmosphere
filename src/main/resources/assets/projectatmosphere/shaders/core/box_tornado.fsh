#version 150

in vec2 uv;
out vec4 fragColor;

uniform vec3  CameraPos;
uniform mat4  InvViewProj;
uniform float Time;
uniform float TwistSpeed;
uniform float BaseRadius;
uniform float TopRadius;
uniform float Height;
uniform float DustIntensity;

const float PI = 3.14159265;
const int   STEPS = 32;
const float MAX_DIST = 200.0;

// Simple 3D noise (replace with your preferred FBM)
float hash(float x) { return fract(sin(x*12.9898)*43758.5453); }
float noise(vec3 p) { return hash(p.x + p.y*57.0 + p.z*113.0); }

// Sample tornado density at world‐pos p
float sampleTornado(vec3 p) {
    float h = clamp(p.y/Height, 0.0, 1.0);
    float r = mix(BaseRadius, TopRadius, h);
    float a = atan(p.z, p.x) + Time*TwistSpeed + h*6.0;
    vec2 sp = vec2(cos(a), sin(a)) * r;
    float d = length(p.xz - sp);

    // <-- corrected line:
    float dens = 1.0 - smoothstep(r*0.8, r, d);

    // add some noise breakup
    dens *= 1.0 - noise(vec3(p.xz*0.1, Time*0.1))*0.5;
    return clamp(dens, 0.0, 1.0);
}


void main() {
    // Reconstruct world‐space ray
    vec4 clip = vec4(uv*2.0-1.0, 0.0, 1.0);
    vec4 world = InvViewProj * clip;
    world /= world.w;
    vec3 rayDir = normalize(world.xyz - CameraPos);

    // Raymarch
    float t=0.0, alpha=0.0;
    vec3  col=vec3(0.0);
    for(int i=0; i<STEPS; i++){
        if(t>MAX_DIST||alpha>0.99) break;
        vec3 pos = CameraPos + rayDir*t;
        if(pos.y>=0.0 && pos.y<=Height){
            float d = sampleTornado(pos);
            alpha += (1.0-alpha)*d*(DustIntensity*0.1);
            col   += (1.0-alpha)*vec3(0.8,0.7,0.6)*d;
        }
        t += MAX_DIST/float(STEPS);
    }

    fragColor = vec4(uv, 0.0, 1.0);

}
