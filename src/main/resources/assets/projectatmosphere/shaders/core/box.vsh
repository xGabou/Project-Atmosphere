#version 150

in vec3 vWorldPos;
out vec4 fragColor;

uniform float DebugBox;
uniform vec3 CameraPos;
uniform vec3 BoxMin, BoxMax;

uniform float Time, TwistSpeed, BaseRadius, TopRadius, Height, DustIntensity;

float hash(float x){ return fract(sin(x*12.9898)*43758.5453); }
float noise(vec3 p){ return hash(p.x + p.y*57.0 + p.z*113.0); }

// slab ray-AABB intersect (origin already on/inside the box front face)
vec2 rayBox(vec3 ro, vec3 rd, vec3 bmin, vec3 bmax){
    vec3 invD = 1.0 / rd;
    vec3 t0 = (bmin - ro) * invD;
    vec3 t1 = (bmax - ro) * invD;
    vec3 tsm = min(t0, t1);
    vec3 tbg = max(t0, t1);
    float tmin = max(max(tsm.x, tsm.y), tsm.z);
    float tmax = min(min(tbg.x, tbg.y), tbg.z);
    return vec2(tmin, tmax);
}

void main() {
    // Front faces only (we cull back faces in JSON)
    vec3 ro = vWorldPos;
    vec3 rd = normalize(vWorldPos - CameraPos);
    ro += rd * 0.01; // step just inside

    vec2 seg = rayBox(ro, rd, BoxMin, BoxMax);
    float tExit = seg.y;
    if (tExit <= 0.0) return; // no intersection
    if (DebugBox > 0.5) {
        fragColor = vec4(1.0, 0.0, 0.0, 0.15);
        return;
    }

    int   STEPS = 32;
    float step  = tExit / float(STEPS);

    float alpha = 0.0;
    vec3  col   = vec3(0.0);

    vec3 center = 0.5 * (BoxMin + BoxMax);

    for (int i=0;i<STEPS;i++){
        vec3 p = ro + rd * (step * float(i));

        float h = clamp((p.y - BoxMin.y) / max(0.001,(BoxMax.y - BoxMin.y)), 0.0, 1.0);
        float r = mix(BaseRadius, TopRadius, h);

        float ang = atan(p.z - center.z, p.x - center.x) + Time*TwistSpeed + h*6.0;
        vec2  sp  = vec2(cos(ang), sin(ang)) * r + center.xz;

        float d = length(p.xz - sp);
        float dens = 1.0 - smoothstep(r*0.8, r, d);   // corrected falloff
        dens *= 1.0 - noise(vec3(p.xz*0.1, Time*0.1))*0.5;

        float k = dens * (DustIntensity * 0.12);
        alpha += (1.0 - alpha) * k;
        col   += (1.0 - alpha) * vec3(0.8,0.7,0.6) * k;

        if (alpha > 0.99) break;
    }

    fragColor = vec4(col, alpha);
}
