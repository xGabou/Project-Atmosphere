// T258 candidate C hierarchical storm anatomy. CPU mirror: StormAnatomyCandidateC.Program (keep in step).
// Experimental fixture only; only the *_convective_c_* fixture variants define PA_STORM_ANATOMY_C.
// The whole growth hierarchy is baked on the CPU into a distance volume plus a conservative occupancy
// grid. Per sample: one grid fetch; in occupied cells two texel fetches and one trilinear blend.
vec4 paSacTexel(int i) { return texelFetch(StormDescriptorSampler, ivec2(i, 0), 0); }
vec4 paSacFetch(int t, int w) { return texelFetch(StormDescriptorSampler, ivec2(t - (t / w) * w, t / w), 0); }

// Conservative occupancy grid, four cells per texel (mirror Program.gridOccupied).
bool paSacOccupied(vec3 p, vec4 g6, vec4 t7) {
    int G = int(g6.w + 0.5);
    ivec3 v = clamp(ivec3(floor((p - PaMorphologyMin) / (PaMorphologyMax - PaMorphologyMin) * float(G))), ivec3(0), ivec3(G - 1));
    int cell = (v.z * G + v.y) * G + v.x;
    int idx = int(t7.y + 0.5) * 4 + cell, texel = idx / 4;
    return paSacFetch(texel, int(t7.z + 0.5))[idx - texel * 4] > 0.5;
}

// Trilinear distance: texel(i,j,k) = (s[i,j,k], s[i+1,j,k], s[i,j,k+1], s[i+1,j,k+1]); +nx is j+1
// (mirror Program.sampledDistance).
float paSacDistance(vec3 p, vec4 dims, vec4 t7) {
    ivec3 n = ivec3(dims.xyz + 0.5);
    vec3 g = (p - PaMorphologyMin) / dims.w;
    ivec3 i0 = clamp(ivec3(floor(g)), ivec3(0), n - 2);
    vec3 f = clamp(g - vec3(i0), 0.0, 1.0);
    int w = int(t7.z + 0.5);
    int t = int(t7.x + 0.5) + (i0.z * n.y + i0.y) * n.x + i0.x;
    vec4 a = paSacFetch(t, w), b = paSacFetch(t + n.x, w);
    vec4 m = mix(a, b, f.y);
    return mix(mix(m.x, m.y, f.x), mix(m.z, m.w, f.x), f.z);
}

void paMorphRayIntervals(vec3 origin, vec3 direction, float low, float high, out vec2 intervals[3]) {
    vec3 local = origin - PaMorphologyOrigin;
    int count = int(paSacTexel(1).w + 0.5);
    for (int i = 0; i < 3; i++) {
        intervals[i] = vec2(1e20, -1.0);
        if (i >= count) continue;
        vec3 lo = paSacTexel(2 + i * 2).xyz, hi = paSacTexel(3 + i * 2).xyz;
        float a = low, b = high;
        for (int axis = 0; axis < 3; axis++) {
            if (abs(direction[axis]) < 1e-8) { if (local[axis] < lo[axis] || local[axis] > hi[axis]) b = -1.0; }
            else { vec2 t = vec2(lo[axis] - local[axis], hi[axis] - local[axis]) / direction[axis]; a = max(a, min(t.x, t.y)); b = min(b, max(t.x, t.y)); }
        }
        if (b >= a) intervals[i] = vec2(a, b);
    }
    // Overlapping boxes: merge so the skip never jumps past a box still open.
    if (intervals[0].x > intervals[1].x) { vec2 t = intervals[0]; intervals[0] = intervals[1]; intervals[1] = t; }
    if (intervals[1].y >= 0.0 && intervals[0].y >= intervals[1].x) {
        intervals[0] = vec2(intervals[0].x, max(intervals[0].y, intervals[1].y));
        intervals[1] = vec2(1e20, -1.0);
    }
}

float paMorphDensity(vec3 world) {
    vec3 p = world - PaMorphologyOrigin;
    if (any(lessThan(p, PaMorphologyMin)) || any(greaterThan(p, PaMorphologyMax))) return 0.0;
    vec4 g6 = paSacTexel(6), t7 = paSacTexel(7);
    if (!paSacOccupied(p, g6, t7)) return 0.0;
    vec4 dims = paSacTexel(0);
    float feather = paSacTexel(1).x;
    return clamp(0.5 - paSacDistance(p, dims, t7) / feather, 0.0, 1.0);
}
