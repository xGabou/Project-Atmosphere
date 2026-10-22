// T257 candidate B continuous convective growth field. CPU mirror: ConvectiveGrowthFieldPrototype (keep in step).
// Experimental fixture only; only the *_convective_* fixture variants define PA_CONVECTIVE_GROWTH_FIELD.
// Constant cost: one spine, one cross section, one crown cap, bounded turret tables, one canopy sheet.
vec4 paCgfTexel(int i) { return texelFetch(StormDescriptorSampler, ivec2(i, 0), 0); }
const int PA_CGF_HEADER = 24;
const int PA_CGF_MAP_W = 512, PA_CGF_MAP_H = 256, PA_CGF_GRID = 64;
// Conservative occupancy grid (four voxels per texel after the map rows; mirror Program.gridOccupied).
bool paCgfGridOccupied(vec3 p) {
    ivec3 v = clamp(ivec3(floor((p - PaMorphologyMin) / (PaMorphologyMax - PaMorphologyMin) * float(PA_CGF_GRID))), ivec3(0), ivec3(PA_CGF_GRID - 1));
    int voxel = (v.z * PA_CGF_GRID + v.y) * PA_CGF_GRID + v.x, texel = voxel / 4;
    return texelFetch(StormDescriptorSampler, ivec2(texel - (texel / PA_CGF_MAP_W) * PA_CGF_MAP_W, PA_CGF_MAP_H + 1 + texel / PA_CGF_MAP_W), 0)[voxel - texel * 4] > 0.5;
}
// Baked surface-displacement map (rows 1..MAP_H): R column (azimuth, s), G crown (azimuth, elevation).
// Bilinear, azimuth wraps, second coordinate clamps (mirror Program.sampleMap).
float paCgfMap(int lane, float theta, float v01) {
    float fx = (theta + 3.14159265) / 6.28318531 * float(PA_CGF_MAP_W), fy = clamp(v01, 0.0, 1.0) * float(PA_CGF_MAP_H - 1);
    int x0 = int(floor(fx)), y0 = min(int(floor(fy)), PA_CGF_MAP_H - 2);
    float tx = fx - float(x0), ty = fy - float(y0);
    int xa = x0 - (x0 / PA_CGF_MAP_W) * PA_CGF_MAP_W; if (xa < 0) xa += PA_CGF_MAP_W;
    int xb = xa + 1 == PA_CGF_MAP_W ? 0 : xa + 1;
    float a = texelFetch(StormDescriptorSampler, ivec2(xa, y0 + 1), 0)[lane], b = texelFetch(StormDescriptorSampler, ivec2(xb, y0 + 1), 0)[lane];
    float c = texelFetch(StormDescriptorSampler, ivec2(xa, y0 + 2), 0)[lane], d = texelFetch(StormDescriptorSampler, ivec2(xb, y0 + 2), 0)[lane];
    return mix(mix(a, b, tx), mix(c, d, tx), ty);
}
float paCgfSmin(float a, float b, float k) { float t = max(k - abs(a - b), 0.0) / k; return min(a, b) - t * t * k * 0.25; }
float paCgfSmax(float a, float b, float k) { float t = max(k - abs(a - b), 0.0) / k; return max(a, b) + t * t * k * 0.25; }
float paCgfEllipse(vec2 p, vec2 r) { float norm = length(p / r); return norm == 0.0 ? -min(r.x, r.y) : (norm - 1.0) * length(p) / norm; }
float paCgfWrap(float a) { return a - 6.28318531 * floor(a / 6.28318531 + 0.5); }
float paCgfHemi(float t2) { const float e = 0.12; return (sqrt(max(0.0, 1.0 - t2) + e) - sqrt(e)) / (sqrt(1.0 + e) - sqrt(e)); }

// Head fields are evaluated on the CPU and baked into the displacement map (Program.columnDisplacement /
// crownDisplacement); the GPU only samples it.

void paMorphRayIntervals(vec3 origin, vec3 direction, float low, float high, out vec2 intervals[3]) {
    vec3 local = origin - PaMorphologyOrigin;
    int count = int(paCgfTexel(1).w + 0.5);
    for (int i = 0; i < 3; i++) {
        intervals[i] = vec2(1e20, -1.0);
        if (i >= count) continue;
        vec3 lo = paCgfTexel(2 + i * 2).xyz, hi = paCgfTexel(3 + i * 2).xyz;
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

bool paCgfInside(vec3 p, int loTexel) {
    vec3 lo = paCgfTexel(loTexel).xyz, hi = paCgfTexel(loTexel + 1).xyz;
    return all(greaterThanEqual(p, lo)) && all(lessThanEqual(p, hi));
}

vec2 paCgfSpine(float s, vec4 spine, vec4 spine2, float H) {
    return H * vec2(spine.x * s + spine.z * s * s + spine2.x * s * s * s, spine.y * s + spine.w * s * s + spine2.y * s * s * s);
}

float paCgfColumn(vec3 p) {
    vec4 dim = paCgfTexel(0), head = paCgfTexel(1), spine = paCgfTexel(6), spine2 = paCgfTexel(7);
    vec4 knots = paCgfTexel(8), asp = paCgfTexel(10), m1 = paCgfTexel(11), m3 = paCgfTexel(12), wall = paCgfTexel(13);
    float H = dim.x, yBase = dim.y, sc = spine2.z;
    float s = clamp((p.y - yBase) / H, 0.0, 1.0);
    vec2 q = p.xz - paCgfSpine(s, spine, spine2, H);
    vec2 g = vec2(spine.x + 2.0 * spine.z * s + 3.0 * spine2.x * s * s, spine.y + 2.0 * spine.w * s + 3.0 * spine2.y * s * s);
    float G = dot(g, g);
    if (G > 1e-8) {
        vec2 n = g * inversesqrt(G);
        float qa = dot(q, n) / sqrt(1.0 + G), qp = -n.y * q.x + n.x * q.y;
        q = n * qa + vec2(-n.y, n.x) * qp;
    }
    float t = smoothstep(0.10, 0.70, s);
    float aspect = mix(asp.x, asp.y, t), orient = mix(asp.z, asp.w, t);
    float co = cos(orient), so = sin(orient);
    float u = q.x * co + q.y * so, w = -q.x * so + q.y * co, sa = sqrt(aspect);
    float rho = length(vec2(u / sa, w * sa));
    float theta = atan(q.y, q.x);
    float r0 = s < 0.20 ? mix(knots.x, knots.y, smoothstep(0.0, 0.20, s))
        : s < 0.50 ? mix(knots.y, knots.z, smoothstep(0.20, 0.50, s)) : mix(knots.z, knots.w, smoothstep(0.50, 0.74, s));
    float tm = smoothstep(0.15, 0.85, s);
    float m = 1.0 + mix(m1.x, m1.y, tm) * cos(theta - mix(m1.z, m1.w, tm)) + mix(m3.x, m3.y, tm) * cos(3.0 * theta - mix(m3.z, m3.w, tm));
    float turret = paCgfMap(0, theta, s);
    float dSide = rho - (r0 * m + turret);
    vec2 wd = p.xz - wall.xy;
    float baseY = yBase - wall.w * exp(-dot(wd, wd) / (wall.z * wall.z));
    float d = paCgfSmax(dSide, baseY - p.y, spine2.w);
    return max(d, p.y - (yBase + H * sc));
}

float paCgfCrown(vec3 p) {
    vec4 dim = paCgfTexel(0), head = paCgfTexel(1), spine = paCgfTexel(6), spine2 = paCgfTexel(7), cr = paCgfTexel(9), bnd = paCgfTexel(14);
    float H = dim.x, sc = spine2.z;
    vec2 cxz = paCgfSpine(sc, spine, spine2, H);
    vec3 e = vec3((p.x - cxz.x) / cr.x, (p.y - (dim.y + H * sc)) / cr.y, (p.z - cxz.y) / cr.x);
    float n = length(e);
    float D = 0.0;
    if (n > 1e-4) {
        float az = atan(e.z, e.x), el = asin(clamp(e.y / n, -1.0, 1.0));
        D = paCgfMap(1, az, el / 3.14159265 + 0.5);
    }
    return (n - 1.0 - D) * min(cr.x, cr.y);
}

float paCgfCanopy(vec3 p) {
    if (paCgfTexel(1).z < 0.5 || !paCgfInside(p, 4)) return 1e6;
    vec4 dim = paCgfTexel(0), root = paCgfTexel(15), dir = paCgfTexel(16), w = paCgfTexel(17), sh = paCgfTexel(18), st = paCgfTexel(19);
    vec2 t20 = paCgfTexel(20).xy;
    float L = root.w, phase = t20.x, wave = t20.y;
    vec2 d = p.xz - root.xz;
    float a = dot(d, dir.xy);
    float an = clamp(a / L, 0.0, 1.0);
    float c = -d.x * dir.y + d.y * dir.x - dir.w * an * an;
    float dax = length(d);
    float topBase = root.y + sh.z * an * an + sh.y * exp(-(dax * dax) / (0.2 * sh.x * sh.x));
    {
        // Exact cheap pre-gate (mirrors canopyGated): beyond feather/2 + 1.25 merge the sheet cannot matter.
        float thickMax = mix(w.z, w.w, smoothstep(0.0, 1.0, dax / sh.x));
        float lb = max(p.y - topBase - wave, topBase - wave - thickMax - p.y);
        if (a >= 0.0 && a <= 0.55 * L) lb = max(lb, abs(c) - mix(w.x, w.y, pow(an, 0.6)) * (1.0 + sh.w));
        if (lb >= dim.w * 0.5 + 1.25 * paCgfTexel(9).w) return 1e6;
    }
    float width = mix(w.x, w.y, pow(an, 0.6));
    width *= 1.0 + sh.w * (0.45 * sin(a / L * 6.9115 + phase) + 0.30 * sin(a / L * 16.022 + 2.0 * phase)
        + 0.25 * sin(a / L * 27.5 + 3.1 * phase));
    float ff = st.y;
    float fib = 0.5 * sin(c * ff + phase) + 0.3 * sin(c * ff * 2.37 + 1.3 * phase) + 0.2 * sin(c * ff * 4.13 + 2.1 * phase);
    float lEff = L * (1.0 + st.x * fib);
    float tipStart = 0.55 * L;
    float dp = a > tipStart ? paCgfEllipse(vec2(a - tipStart, c), vec2(max(lEff - tipStart, 1.0), width))
        : a < 0.0 ? paCgfEllipse(vec2(a, c), vec2(dir.z, width)) : abs(c) - width;
    float cw = clamp(abs(c) / max(width, 1.0), 0.0, 1.0);
    float lens = 0.2 + 0.8 * ((sqrt(clamp(-dp / (0.40 * dim.x), 0.0, 1.0) + 0.1) - 0.31622777) / 0.73258877);
    float thick = mix(w.z, w.w, smoothstep(0.0, 1.0, dax / sh.x)) * (1.0 - 0.35 * cw * cw) * lens;
    float top = topBase + wave * lens * sin(a / L * 9.4 + phase) * sin(c / max(width, 1.0) * 4.7 + 1.9 * phase);
    float streak = 0.5 + 0.5 * sin(c * st.w + phase + 0.9 * sin(a * 3.3 / L));
    float bottom = top - thick + thick * st.z * streak * smoothstep(0.30, 0.80, an);
    float dv = max(p.y - top, bottom - p.y);
    return max(dp, paCgfSmax(dv, dp, max(6.0, thick * 0.35)));
}

// Trig-free lower bounds (mirror columnLowerBound / crownLowerBound).
float paCgfColumnLb(vec3 p) {
    vec4 dim = paCgfTexel(0), spine = paCgfTexel(6), spine2 = paCgfTexel(7), knots = paCgfTexel(8), asp = paCgfTexel(10);
    vec4 m1 = paCgfTexel(11), m3 = paCgfTexel(12), b14 = paCgfTexel(14), c21 = paCgfTexel(21);
    float H = dim.x, yBase = dim.y, sc = spine2.z;
    float s = clamp((p.y - yBase) / H, 0.0, 1.0);
    vec2 q = p.xz - paCgfSpine(s, spine, spine2, H);
    vec2 g = vec2(spine.x + 2.0 * spine.z * s + 3.0 * spine2.x * s * s, spine.y + 2.0 * spine.w * s + 3.0 * spine2.y * s * s);
    float rhoLb = length(q) / (sqrt(1.0 + dot(g, g)) * sqrt(max(asp.x, asp.y)));
    float r0 = s < 0.20 ? mix(knots.x, knots.y, smoothstep(0.0, 0.20, s))
        : s < 0.50 ? mix(knots.y, knots.z, smoothstep(0.20, 0.50, s)) : mix(knots.z, knots.w, smoothstep(0.50, 0.74, s));
    float radial = 1.0 + max(abs(m1.x), abs(m1.y)) + max(abs(m3.x), abs(m3.y));
    float side = rhoLb - (r0 * radial + max(b14.x, c21.z) + paCgfTexel(23).x);
    return max(max(side, yBase - paCgfTexel(13).w - p.y), p.y - (yBase + H * sc));
}
float paCgfColumnUb(vec3 p) {
    vec4 dim = paCgfTexel(0), spine = paCgfTexel(6), spine2 = paCgfTexel(7), knots = paCgfTexel(8), asp = paCgfTexel(10);
    vec4 m1 = paCgfTexel(11), m3 = paCgfTexel(12);
    float H = dim.x, yBase = dim.y, sc = spine2.z;
    float s = clamp((p.y - yBase) / H, 0.0, 1.0);
    vec2 q = p.xz - paCgfSpine(s, spine, spine2, H);
    float r0 = s < 0.20 ? mix(knots.x, knots.y, smoothstep(0.0, 0.20, s))
        : s < 0.50 ? mix(knots.y, knots.z, smoothstep(0.20, 0.50, s)) : mix(knots.z, knots.w, smoothstep(0.50, 0.74, s));
    float mMin = 1.0 - max(abs(m1.x), abs(m1.y)) - max(abs(m3.x), abs(m3.y));
    float side = length(q) * sqrt(max(asp.x, asp.y)) - r0 * mMin;
    return max(max(side, yBase - p.y) + spine2.w * 0.25, p.y - (yBase + H * sc));
}
float paCgfCrownUb(vec3 p) {
    vec4 dim = paCgfTexel(0), spine = paCgfTexel(6), spine2 = paCgfTexel(7), cr = paCgfTexel(9);
    float H = dim.x, sc = spine2.z;
    vec2 cxz = paCgfSpine(sc, spine, spine2, H);
    vec3 e = vec3((p.x - cxz.x) / cr.x, (p.y - (dim.y + H * sc)) / cr.y, (p.z - cxz.y) / cr.x);
    return (length(e) - 1.0) * min(cr.x, cr.y);
}
float paCgfCrownLb(vec3 p) {
    vec4 dim = paCgfTexel(0), spine = paCgfTexel(6), spine2 = paCgfTexel(7), cr = paCgfTexel(9), b14 = paCgfTexel(14);
    float H = dim.x, sc = spine2.z;
    vec2 cxz = paCgfSpine(sc, spine, spine2, H);
    vec3 e = vec3((p.x - cxz.x) / cr.x, (p.y - (dim.y + H * sc)) / cr.y, (p.z - cxz.y) / cr.x);
    return (length(e) - 1.0 - (max(b14.y, paCgfTexel(22).z) + paCgfTexel(23).y)) * min(cr.x, cr.y);
}

float paMorphDensity(vec3 world) {
    vec3 p = world - PaMorphologyOrigin;
    if (any(lessThan(p, PaMorphologyMin)) || any(greaterThan(p, PaMorphologyMax)) || !paCgfGridOccupied(p)) return 0.0;
    float feather = paCgfTexel(0).w;
    float c = paCgfCanopy(p), d = c;
    if (paCgfInside(p, 2)) {
        // Exact gates (mirror Program.distance): skip the body when it cannot reach the union or the
        // density; evaluate the nearer surface in full, the other only if it can enter the blend.
        vec4 k9 = paCgfTexel(9);
        float kc = k9.z, km = k9.w, cut = feather * 0.5 + km * 0.25;
        // Deep interior (mirror Program.distance): density is exactly 1, no head field evaluated.
        if (min(paCgfColumnUb(p), paCgfCrownUb(p)) <= -feather * 0.5) return 1.0;
        float colLb = paCgfColumnLb(p), crLb = paCgfCrownLb(p);
        float bodyLb = min(colLb, crLb) - kc * 0.25;
        if (!(bodyLb >= c + km || (bodyLb >= cut && c >= cut))) {
            float body;
            if (colLb <= crLb) {
                float col = paCgfColumn(p);
                body = crLb >= col + kc ? col : paCgfSmin(col, paCgfCrown(p), kc);
            } else {
                float cr = paCgfCrown(p);
                body = colLb >= cr + kc ? cr : paCgfSmin(paCgfColumn(p), cr, kc);
            }
            d = paCgfSmin(body, c, km);
        }
    }
    return clamp(0.5 - d / feather, 0.0, 1.0);
}
