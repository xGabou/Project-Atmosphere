// T260 convective-system sparse-brick volume. CPU mirror: SparseBrickVolume.densityFromTexture (keep in step).
// Experimental fixture only; only the *_convective_system_* fixture variants define PA_CONVECTIVE_SYSTEM_T260.
// Per sample: one index fetch; in brick cells two brick fetches (lattice rows j, j+1) and one trilinear blend.
// Header texels: 0 lattice min + voxel, 1 grid dims + B, 2 feather/band/boxes/version, 3 index/brick starts,
// 4..9 three skip boxes, 10 code scale/offset.
vec4 paCsTexel(int i) { return texelFetch(StormDescriptorSampler, ivec2(i, 0), 0); }
vec4 paCsFetch(int t) { return texelFetch(StormDescriptorSampler, ivec2(t - (t / 2048) * 2048, t / 2048), 0); }

void paCsDecode(vec4 v, out vec4 rowK, out vec4 rowK1) {
    vec4 hi = floor(v / 4096.0);
    vec4 lo = v - hi * 4096.0;
    rowK = vec4(hi.x, lo.x, hi.y, lo.y);
    rowK1 = vec4(hi.z, lo.z, hi.w, lo.w);
}

float paMorphDensity(vec3 world) {
    vec3 p = world - PaMorphologyOrigin;
    if (any(lessThan(p, PaMorphologyMin)) || any(greaterThan(p, PaMorphologyMax))) return 0.0;
    vec4 h0 = paCsTexel(0), h1 = paCsTexel(1), h3 = paCsTexel(3);
    ivec3 grid = ivec3(h1.xyz + 0.5);
    vec3 g = (p - h0.xyz) / h0.w;
    ivec3 c = clamp(ivec3(floor(g)), ivec3(0), grid * 8 - 1);
    ivec3 bc = c / 8;
    int cell = (bc.z * grid.y + bc.y) * grid.x + bc.x;
    int it = int(h3.x + 0.5) + cell / 4;
    float s = paCsFetch(it)[cell - (cell / 4) * 4];
    if (s < 0.5) return 0.0;
    if (s < 1.5) return 1.0;
    int brick = int(s + 0.5) - 2;
    ivec3 l = c - bc * 8;
    vec3 f = clamp(g - vec3(c), 0.0, 1.0);
    int r = min(l.x / 3, 2), xi = l.x - 3 * r;
    int t = int(h3.y + 0.5) + brick * 216 + (l.z * 9 + l.y) * 3 + r;
    vec4 aK, aK1, bK, bK1;
    paCsDecode(paCsFetch(t), aK, aK1);
    paCsDecode(paCsFetch(t + 3), bK, bK1);
    vec4 w0 = vec4(equal(ivec4(0, 1, 2, 3), ivec4(xi)));
    vec4 w1 = vec4(equal(ivec4(0, 1, 2, 3), ivec4(xi + 1)));
    float mk0 = mix(dot(aK, w0), dot(bK, w0), f.y), mk1 = mix(dot(aK, w1), dot(bK, w1), f.y);
    float mz0 = mix(dot(aK1, w0), dot(bK1, w0), f.y), mz1 = mix(dot(aK1, w1), dot(bK1, w1), f.y);
    float code = mix(mix(mk0, mk1, f.x), mix(mz0, mz1, f.x), f.z);
    vec4 h2 = paCsTexel(2), h10 = paCsTexel(10);
    float d = code * h10.x + h10.y;
    return clamp(0.5 - d / h2.x, 0.0, 1.0);
}

// Up to three cell-aligned boxes; intervals sorted by entry and merged so the skip never passes an open box.
void paMorphRayIntervals(vec3 origin, vec3 direction, float low, float high, out vec2 intervals[3]) {
    vec3 local = origin - PaMorphologyOrigin;
    int count = int(paCsTexel(2).z + 0.5);
    for (int i = 0; i < 3; i++) {
        intervals[i] = vec2(1e20, -1.0);
        if (i >= count) continue;
        vec3 lo = paCsTexel(4 + i * 2).xyz, hi = paCsTexel(5 + i * 2).xyz;
        float a = low, b = high;
        for (int axis = 0; axis < 3; axis++) {
            if (abs(direction[axis]) < 1e-8) { if (local[axis] < lo[axis] || local[axis] > hi[axis]) b = -1.0; }
            else { vec2 tt = vec2(lo[axis] - local[axis], hi[axis] - local[axis]) / direction[axis]; a = max(a, min(tt.x, tt.y)); b = min(b, max(tt.x, tt.y)); }
        }
        if (b >= a) intervals[i] = vec2(a, b);
    }
    // Sort the three by entry (empty intervals carry entry 1e20 and sink to the end).
    if (intervals[1].x < intervals[0].x) { vec2 tt = intervals[0]; intervals[0] = intervals[1]; intervals[1] = tt; }
    if (intervals[2].x < intervals[1].x) { vec2 tt = intervals[1]; intervals[1] = intervals[2]; intervals[2] = tt; }
    if (intervals[1].x < intervals[0].x) { vec2 tt = intervals[0]; intervals[0] = intervals[1]; intervals[1] = tt; }
    // Merge overlaps front to back.
    if (intervals[1].y >= 0.0 && intervals[1].x <= intervals[0].y) { intervals[0].y = max(intervals[0].y, intervals[1].y); intervals[1] = intervals[2]; intervals[2] = vec2(1e20, -1.0); }
    if (intervals[1].y >= 0.0 && intervals[1].x <= intervals[0].y) { intervals[0].y = max(intervals[0].y, intervals[1].y); intervals[1] = intervals[2]; intervals[2] = vec2(1e20, -1.0); }
    if (intervals[2].y >= 0.0 && intervals[2].x <= intervals[1].y) { intervals[1].y = max(intervals[1].y, intervals[2].y); intervals[2] = vec2(1e20, -1.0); }
}
