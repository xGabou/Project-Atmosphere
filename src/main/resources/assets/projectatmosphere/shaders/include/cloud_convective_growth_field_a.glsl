// T257 candidate A (REJECTED), frozen for comparison. CPU mirror: ConvectiveGrowthFieldA.
// Experimental fixture only; only the *_convective_a_* fixture variants define PA_CONVECTIVE_GROWTH_FIELD_A.
// Constant cost: one spine, one cross section, a bounded turret table, one canopy sheet.
vec4 paCgfTexel(int i) { return texelFetch(StormDescriptorSampler, ivec2(i, 0), 0); }
const int PA_CGF_HEADER = 22;
const float PA_CGF_BILLOW_MEAN = 0.62;
const float PA_CGF_PI = 3.14159265;
float paCgfSmin(float a, float b, float k) { float t = max(k - abs(a - b), 0.0) / k; return min(a, b) - t * t * k * 0.25; }
float paCgfSmax(float a, float b, float k) { float t = max(k - abs(a - b), 0.0) / k; return max(a, b) + t * t * k * 0.25; }
float paCgfEllipse(vec2 p, vec2 r) { float norm = length(p / r); return norm == 0.0 ? -min(r.x, r.y) : (norm - 1.0) * length(p) / norm; }
float paCgfWrap(float a) { return a - 6.28318531 * floor(a / 6.28318531 + 0.5); }

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

float paCgfProfile(float s, vec4 knots, float rc, float sc) {
    if (s < 0.20) return mix(knots.x, knots.y, smoothstep(0.0, 0.20, s));
    if (s < 0.50) return mix(knots.y, knots.z, smoothstep(0.20, 0.50, s));
    if (s < 0.74) return mix(knots.z, knots.w, smoothstep(0.50, 0.74, s));
    if (s < sc) return mix(knots.w, rc, smoothstep(0.74, sc, s));
    float u = (s - sc) / (1.0 - sc);
    return rc * sqrt(max(0.0, 1.0 - u * u));
}

float paCgfCanopy(vec3 p) {
    if (paCgfTexel(1).z < 0.5 || !paCgfInside(p, 4)) return 1e6;
    vec4 root = paCgfTexel(15), dir = paCgfTexel(16), w = paCgfTexel(17), sh = paCgfTexel(18), st = paCgfTexel(19);
    float L = root.w;
    vec2 d = p.xz - root.xz;
    float a = dot(d, dir.xy);
    float an = clamp(a / L, 0.0, 1.0);
    float c = -d.x * dir.y + d.y * dir.x - dir.w * an * an;
    float width = mix(w.x, w.y, pow(an, 0.7));
    float phase = st.z;
    {
        // Exact cheap pre-gate (mirrors the CPU): beyond feather/2 + 1.25 merge the sheet cannot matter.
        float topMax = root.y + sh.x * smoothstep(0.0, 1.0, an);
        float thickMax = mix(w.z, w.w, pow(an, 0.85)) * (1.0 + 0.55 * (1.0 - smoothstep(0.0, 0.30, an)));
        float lb = max(p.y - topMax, topMax - thickMax - p.y);
        if (a >= 0.0 && a <= 0.55 * L) lb = max(lb, abs(c) - width * (1.0 + sh.w));
        if (lb >= paCgfTexel(1).x * 0.5 + 1.25 * paCgfTexel(9).y) return 1e6;
    }
    width *= 1.0 + sh.w * sin(a * 2.1 / L * PA_CGF_PI + phase) * an;
    float ff = sh.z;
    float fib = 0.5 * sin(c * ff + phase) + 0.3 * sin(c * ff * 2.37 + 1.3 * phase)
        + 0.2 * sin(c * ff * 4.13 + 2.1 * phase + 1.7 * sin(c * ff * 0.61));
    float lEff = L * (1.0 + sh.y * fib);
    float tipStart = 0.55 * L;
    float dp = a > tipStart ? paCgfEllipse(vec2(a - tipStart, c), vec2(max(lEff - tipStart, 1.0), width))
        : a < 0.0 ? paCgfEllipse(vec2(a, c), vec2(dir.z, width)) : abs(c) - width;
    float topY = root.y + sh.x * smoothstep(0.0, 1.0, an);
    float cw = clamp(abs(c) / max(width, 1.0), 0.0, 1.0);
    float flare = 1.0 + 0.55 * (1.0 - smoothstep(0.0, 0.30, an)) * (1.0 - cw * cw);
    float thick = mix(w.z, w.w, pow(an, 0.85)) * (1.0 - 0.40 * cw * cw) * flare;
    float streak = 0.5 + 0.5 * sin(c * st.y + phase + 0.9 * sin(a * 3.3 / L));
    float bottom = topY - thick + thick * st.x * streak * smoothstep(0.15, 0.6, an);
    float dv = max(p.y - topY, bottom - p.y);
    return paCgfSmax(dp, dv, max(4.0, thick * 0.5));
}

float paCgfBody(vec3 p, float gateCanopy) {
    vec4 dim = paCgfTexel(0), head = paCgfTexel(1), spine = paCgfTexel(6), spine2 = paCgfTexel(7);
    vec4 knots = paCgfTexel(8), k9 = paCgfTexel(9), asp = paCgfTexel(10), m1 = paCgfTexel(11), m3 = paCgfTexel(12);
    vec4 wb = paCgfTexel(13), bil = paCgfTexel(14), bound = paCgfTexel(20);
    float H = dim.y, yBase = dim.z, feather = head.x, merge = k9.y;
    float s = clamp((p.y - yBase) / H, 0.0, 1.0);
    vec2 q = p.xz - H * vec2(spine.x * s + spine.z * s * s + spine2.x * s * s * s,
                             spine.y * s + spine.w * s * s + spine2.y * s * s * s);
    vec2 g = vec2(spine.x + 2.0 * spine.z * s + 3.0 * spine2.x * s * s,
                  spine.y + 2.0 * spine.w * s + 3.0 * spine2.y * s * s);
    float G = dot(g, g);
    float sc = spine2.z;
    float r0 = paCgfProfile(s, knots, k9.x, sc);
    {
        // Trig-free pre-gate: stretched radius >= |q| / (sqrt(1+G) sqrt(maxAspect)).
        float rhoLb = length(q) / (sqrt(1.0 + G) * sqrt(max(asp.x, asp.y)));
        float radBound = 1.0 + max(abs(m1.x), abs(m1.y)) + max(abs(m3.x), abs(m3.y));
        float radUb = r0 * radBound + bound.x + PA_CGF_BILLOW_MEAN * wb.z * r0;
        float side0 = rhoLb - radUb;
        float lb0 = max(max(side0 > 0.0 ? side0 * 0.3162278 : side0, p.y - (yBase + H)), yBase - k9.w - p.y);
        float cut0 = feather * 0.5 + merge * 0.25;
        if (lb0 >= gateCanopy + merge || (lb0 >= cut0 && gateCanopy >= cut0)) return 1e6;
    }
    if (G > 1e-8) {
        vec2 n = g * inversesqrt(G);
        float qa = dot(q, n) / sqrt(1.0 + G), qp = -n.y * q.x + n.x * q.y;
        q = n * qa + vec2(-n.y, n.x) * qp;
    }
    float t = smoothstep(0.10, 0.80, s);
    float aspect = mix(asp.x, asp.y, t), orient = mix(asp.z, asp.w, t);
    float co = cos(orient), so = sin(orient);
    float u = q.x * co + q.y * so, w = -q.x * so + q.y * co, sa = sqrt(aspect);
    float rho = length(vec2(u / sa, w * sa));
    float theta = atan(q.y, q.x);
    float slope = 0.0;
    if (s > sc) {
        float uu = min((s - sc) / (1.0 - sc), 0.9999);
        slope = k9.x * uu / (sqrt(1.0 - uu * uu) * (1.0 - sc) * H);
    }
    slope = min(slope, 3.0);
    float invLen = 1.0 / sqrt(1.0 + slope * slope);
    float tm = smoothstep(0.15, 0.85, s);
    float m = 1.0 + mix(m1.x, m1.y, tm) * cos(theta - mix(m1.z, m1.w, tm))
        + mix(m3.x, m3.y, tm) * cos(3.0 * theta - mix(m3.z, m3.w, tm));
    float rad = r0 * m;
    float fade = 1.0 - smoothstep(0.90, 1.0, s);
    float billowBand = smoothstep(0.08, 0.30, s) * (1.0 - smoothstep(bil.z - 0.14, bil.z, s));
    float billowMax = PA_CGF_BILLOW_MEAN * wb.z * r0 * billowBand;
    float wallDepth = k9.w;
    float top = p.y - (yBase + H);
    // Exact lower-bound gate: a skipped body cannot change the smooth union or the density.
    float lb = max(max((rho - rad - bound.x * fade - billowMax) * invLen, top), yBase - wallDepth - p.y);
    float cut = feather * 0.5 + merge * 0.25;
    if (lb >= gateCanopy + merge || (lb >= cut && gateCanopy >= cut)) return 1e6;
    float turret = 0.0;
    int count = int(head.y + 0.5);
    for (int i = 0; i < count; i++) {
        vec4 tr = paCgfTexel(PA_CGF_HEADER + i);
        float dy = (p.y - tr.x) / (tr.w * 0.85);
        if (abs(dy) > 3.0) continue;
        float dt = paCgfWrap(theta - tr.y) * r0 / tr.w;
        turret = max(turret, tr.z * exp(-dt * dt - dy * dy));
    }
    float n = bil.x, mm = bil.y, ph = wb.w;
    float ba = abs(sin(theta * n + 2.3 * s + ph) * sin(PA_CGF_PI * s * mm + theta + ph * 1.7));
    float bb = abs(sin(theta * (n + 3.0) - 3.1 * s + ph * 2.3) * sin(PA_CGF_PI * s * mm * 1.37 - 2.0 * theta + ph * 0.6));
    float billow = wb.z * r0 * billowBand * ((sqrt(max(ba, bb) + 0.01) - 0.1) / 0.90499 - PA_CGF_BILLOW_MEAN);
    rad += turret * fade + billow;
    float dSide = (rho - rad) * invLen;
    vec2 wd = p.xz - wb.xy;
    float baseY = yBase - wallDepth * exp(-dot(wd, wd) / (k9.z * k9.z));
    float d = paCgfSmax(dSide, baseY - p.y, spine2.w);
    return max(d, top);
}

float paMorphDensity(vec3 world) {
    vec3 p = world - PaMorphologyOrigin;
    if (any(lessThan(p, PaMorphologyMin)) || any(greaterThan(p, PaMorphologyMax))) return 0.0;
    float c = paCgfCanopy(p);
    float b = paCgfInside(p, 2) ? paCgfBody(p, c) : 1e6;
    float d = paCgfSmin(b, c, paCgfTexel(9).y);
    return clamp(0.5 - d / paCgfTexel(1).x, 0.0, 1.0);
}
