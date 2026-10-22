// T255 opt-in recipe evaluator. Mirrors StormMorphologyPrototype; no material inputs.
#ifndef PA_MORPHOLOGY_EXPERIMENT
float paMorphDensity(vec3 world) { return 0.0; }
void paMorphRayIntervals(vec3 origin, vec3 direction, float low, float high, out vec2 intervals[3]) {
    for (int i = 0; i < 3; i++) intervals[i] = vec2(1e20, -1.0);
}
#else

vec4 paMorphTexel(int index) { return texelFetch(StormDescriptorSampler, ivec2(index, 0), 0); }
void paMorphRayIntervals(vec3 origin, vec3 direction, float low, float high, out vec2 intervals[3]) {
    vec3 local = origin - PaMorphologyOrigin;
    int count = int(paMorphTexel(5).w + 0.5);
    for (int i = 0; i < 3; i++) {
        intervals[i] = vec2(1e20, -1.0);
        if (i >= count) continue;
        vec3 lo = paMorphTexel(6 + i * 2).xyz;
        vec3 hi = paMorphTexel(7 + i * 2).xyz;
        float a = low, b = high;
        for (int axis = 0; axis < 3; axis++) {
            if (abs(direction[axis]) < 1e-8) {
                if (local[axis] < lo[axis] || local[axis] > hi[axis]) b = -1.0;
            } else {
                vec2 times = vec2(lo[axis] - local[axis], hi[axis] - local[axis]) / direction[axis];
                a = max(a, min(times.x, times.y)); b = min(b, max(times.x, times.y));
            }
        }
        if (b >= a) intervals[i] = vec2(a, b);
    }
    // Three complete structural groups; never a capped/truncated authored-feature list.
    vec2 temporary;
    if (intervals[0].x > intervals[1].x) { temporary = intervals[0]; intervals[0] = intervals[1]; intervals[1] = temporary; }
    if (intervals[1].x > intervals[2].x) { temporary = intervals[1]; intervals[1] = intervals[2]; intervals[2] = temporary; }
    if (intervals[0].x > intervals[1].x) { temporary = intervals[0]; intervals[0] = intervals[1]; intervals[1] = temporary; }
}
float paMorphUnion(float a, float b, float k) {
    float h = max(k - abs(a - b), 0.0) / k;
    return min(a, b) - h * h * k * 0.25;
}
float paMorphAxis(float t, vec4 dimensions, vec4 growth) {
    t = clamp(t, 0.0, 1.0);
    return dimensions.y * (0.9 * growth.x * t + 0.38 * growth.y * t * t);
}
float paMorphWidth(float t, vec4 dimensions, vec4 growth) {
    t = clamp(t, 0.0, 1.0);
    float upper = dimensions.w < 0.5 ? 0.20 : 0.08;
    return dimensions.x * (1.0 - 0.16 * t - 0.26 * growth.z * smoothstep(0.10, 0.70, t)
        + upper * smoothstep(0.62, 0.88, t));
}
float paMorphEllipse(vec2 p, vec2 r) {
    float norm = length(p / r);
    return norm == 0.0 ? -min(r.x, r.y) : (norm - 1.0) * length(p) / norm;
}
float paMorphDensity(vec3 world) {
    vec3 p = world - PaMorphologyOrigin;
    if (any(lessThan(p, PaMorphologyMin)) || any(greaterThan(p, PaMorphologyMax))) return 0.0;
    vec4 dim = paMorphTexel(0); // R, H, base depth, archetype
    vec4 growth = paMorphTexel(1); // tilt, shear, narrowing, spread
    vec4 lower = paMorphTexel(2); // flattening, raggedness, split, wall
    vec4 counts = paMorphTexel(3); // strength, swept axis count, ribbon count, version
    vec4 anatomy = paMorphTexel(4); // tower width/top bound, canopy Y interval
    vec4 hierarchy = paMorphTexel(5); // bundle Y interval, total support allowance
    float radius = dim.x;
    float height = dim.y;
    float t = clamp(p.y / height, 0.0, 1.0);
    float axis = paMorphAxis(t, dim, growth);
    vec2 radial = vec2(p.x - axis, p.z);
    bool supercell = dim.w > 1.5;
    float foot = max(0.0, 1.0 - pow(t / 0.38, 2.0));
    foot *= foot;
    float q = length(supercell ? radial / vec2(1.0 + 0.55 * foot, 1.0 + 0.30 * foot) : radial);
    float margin = hierarchy.z;
    float wallDepth = supercell ? radius * 0.9 * lower.w : 0.0;
    bool bodyNear = q <= anatomy.x + margin && p.y >= -dim.z - wallDepth - margin && p.y <= anatomy.y + margin;
    float diverge = smoothstep(0.08, 0.48, t);
    float secondaryOffset = radius * lower.z * (supercell ? 1.3 : 0.45);
    vec2 secondaryCentre = vec2(axis - radius * 0.22 * diverge, secondaryOffset * diverge);
    bool branchNear = secondaryOffset > 0.0 && length(p.xz - secondaryCentre) <= radius * 0.55 * lower.z + margin
        && p.y >= -dim.z - wallDepth - margin && p.y <= height * (0.60 + 0.18 * lower.z) + margin;
    float canopyLength = radius * (dim.w < 0.5 ? 0.35 + 0.65 * growth.w
        : supercell ? 1.8 + 3.2 * growth.w : 1.4 + 2.7 * growth.w);
    float canopyAxis = paMorphAxis(0.88, dim, growth);
    bool canopyNear = dim.w >= 0.5 && p.x >= canopyAxis - radius * 0.65 - margin && p.x <= canopyAxis + canopyLength + margin
        && abs(p.z) <= radius * (1.7 + growth.w + (supercell ? 0.45 * abs(growth.y) : 0.0)) + margin
        && p.y >= anatomy.z - margin && p.y <= anatomy.w + margin;
    bool fibreNear = PaMorphologyScale > 0 && counts.z > 0.0 && p.x >= canopyAxis + canopyLength * 0.12 - margin
        && p.y >= hierarchy.x - margin && p.y <= hierarchy.y + margin;
    vec3 crownRadius = vec3(radius * 0.82 + canopyLength * 0.35, height * 0.15, radius * 0.76);
    vec3 crownDelta = p - vec3(paMorphAxis(0.80, dim, growth) + canopyLength * 0.25, height * 0.79, 0.0);
    bool crownNear = dim.w < 0.5 && all(lessThanEqual(abs(crownDelta), crownRadius + margin));
    if (!bodyNear && !branchNear && !canopyNear && !fibreNear && !crownNear) return 0.0;
    float width = paMorphWidth(t, dim, growth);
    int sectorCount = int(counts.y + 0.5);
    int ribbonCount = int(counts.z + 0.5);
    float floorY = -dim.z + (1.0 - lower.x) * radius * 0.12 * pow(q / radius, 2.0);
    if (wallDepth > 0.0) {
        float lowerQ = length(p.xz + radius * vec2(0.12, 0.16)) / (radius * 0.65);
        float bowl = max(0.0, 1.0 - lowerQ * lowerQ);
        floorY -= wallDepth * bowl * bowl;
    }
    float topY = height - radius * 0.43 * pow(q / max(width, 1.0), 2.0);
    float body = bodyNear ? max(q - width, max(floorY - p.y, p.y - topY)) : radius * 100.0;
    if (bodyNear && PaMorphologyScale > 0) {
        for (int i = 0; i < sectorCount; i++) {
            vec4 a = paMorphTexel(12 + i * 2); // direction X/Z, birth/tip height fractions
            vec4 b = paMorphTexel(13 + i * 2); // root/tip radial placement, radius
            if (b.z <= 0.0 || p.y < a.z * height - margin || p.y > a.w * height + margin) continue;
            float v = clamp((p.y / height - a.z) / (a.w - a.z), 0.0, 1.0);
            float radialPlacement = mix(b.x, b.y, smoothstep(0.0, 1.0, v));
            float v2 = v * v;
            float v4 = v2 * v2;
            float cap = sqrt(max(0.0, 1.0 - v4 * v4));
            float growthRadius = b.z * (0.5 + 2.0 * v * (1.0 - v)) * cap;
            float growthDistance = max(length(p.xz - vec2(axis, 0.0) - a.xy * radialPlacement) - growthRadius,
                max(a.z * height - p.y, p.y - a.w * height));
            body = paMorphUnion(body, growthDistance, radius * 0.08);
        }
    }
    if (branchNear) {
        float branchWidth = paMorphWidth(t, dim, growth) * (0.55 - 0.18 * t) * lower.z;
        float branch = max(length(p.xz - secondaryCentre) - branchWidth,
            max(floorY - p.y, p.y - height * (0.60 + 0.18 * lower.z)));
        body = paMorphUnion(body, branch, radius * 0.16);
    }
    if (dim.w < 0.5) {
        float norm = length(crownDelta / crownRadius);
        float shoulder = norm == 0.0 ? -min(crownRadius.x, min(crownRadius.y, crownRadius.z)) : (norm - 1.0) * length(crownDelta) / norm;
        body = paMorphUnion(body, shoulder, radius * 0.20);
    } else {
        if (canopyNear) {
        float cx = p.x - canopyAxis;
        float u = cx / canopyLength;
        float fanCap = sqrt(smoothstep(-radius * 0.65, radius * 0.18, cx));
        float cross = radius * (0.70 + (1.0 + growth.w) * smoothstep(-0.1, 0.58, u)) * fanCap;
        float sweep = supercell ? radius * 0.45 * growth.y * smoothstep(0.12, 1.0, u) : 0.0;
        float cz = p.z - sweep;
        float cy = height * 0.87 + height * 0.07 * clamp(u, -0.25, 1.2)
            - radius * 0.05 * pow(clamp(cz / max(cross, 1.0), -1.0, 1.0), 2.0);
        float halfDepth = radius * 0.18 * (1.0 - 0.66 * smoothstep(0.05, 1.1, u)) * fanCap;
        float canopy = max(paMorphEllipse(vec2(cz, p.y - cy), max(vec2(1.0), vec2(cross, halfDepth))),
            max(-radius * 0.65 - cx, cx - canopyLength + radius * 0.12 * pow(clamp(cz / max(cross, 1.0), -1.0, 1.0), 2.0)));
        body = paMorphUnion(body, canopy, radius * 0.22);
        if (supercell) {
            float shieldWidth = radius * (1.0 + 0.85 * growth.w) * fanCap;
            float shieldBottom = height * (0.47 + 0.35 * clamp(u, 0.0, 1.0));
            if (cy > shieldBottom) {
                float shield = max(paMorphEllipse(vec2(cz, p.y - (cy + shieldBottom) * 0.5),
                    max(vec2(1.0), vec2(shieldWidth, (cy - shieldBottom) * 0.5))),
                    max(-radius * 0.40 - cx, cx - canopyLength * 0.82));
                body = paMorphUnion(body, shield, radius * 0.14);
            }
        }
        }
        if (fibreNear) {
            for (int i = 0; i < ribbonCount; i++) {
                vec4 f = paMorphTexel(12 + sectorCount * 2 + i * 2);
                vec4 g = paMorphTexel(13 + sectorCount * 2 + i * 2);
                float v = clamp((p.x - f.x) / f.y, 0.0, 1.0);
                float fz = f.z + f.w * (v * 0.7 + v * v * 0.3);
                float fy = g.x + g.y * v + height * 0.015 * sin(v * 3.14159265);
                float taper = 0.25 + 0.75 * (1.0 - v);
                float ribbon = max(paMorphEllipse(vec2(p.z - fz, p.y - fy), g.zw * taper), max(f.x - p.x, p.x - f.x - f.y));
                body = paMorphUnion(body, ribbon, radius * 0.025);
            }
        }
    }
    if (PaMorphologyScale > 1) {
        float micro = sin(p.x / 17.0 + sin(p.z / 31.0))
            * sin(p.y / 23.0 + sin(p.x / 41.0)) * cos(p.z / 19.0);
        body += micro * radius * 0.022 * lower.y;
    }
    return clamp(0.5 - body / max(2.0, radius * 0.055), 0.0, 1.0);
}
#endif
