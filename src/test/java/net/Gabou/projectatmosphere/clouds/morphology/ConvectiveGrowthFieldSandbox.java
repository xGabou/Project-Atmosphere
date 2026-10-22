package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.type.CloudFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudShapeProfile;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.SplittableRandom;

/** T257 structural contracts. Not screenshot/realism acceptance. */
public final class ConvectiveGrowthFieldSandbox {
    private static int assertions;
    private static void require(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        String[] types = {"cumulonimbus_calvus", "cumulonimbus_capillatus", "supercell"};
        long[] seeds = {20261008L, 20268927L, 20276846L};
        for (String type : types) for (long seed : seeds) {
            var shape = CloudShapeProfile.defaultFor(type, CloudFamily.CUMULONIMBUS, null);
            var p = ConvectiveGrowthFieldPrototype.compile(type, shape, seed);
            require(Arrays.equals(p.packed(), ConvectiveGrowthFieldPrototype.compile(type, shape, seed).packed()), "Deterministic recipe");
            require(p.packed().length % 4 == 0 && p.packed().length / 4 <= ConvectiveGrowthFieldPrototype.HEADER_TEXELS + ConvectiveGrowthFieldPrototype.MAX_TURRETS,
                    "Bounded transport: header + bounded turret table");
            require(p.hasCanopy() == !type.equals("cumulonimbus_calvus"), "Calvus has no canopy; mature storms do");

            // Gate/bounds exactness: the accelerated density equals the ungated reference field everywhere.
            var b = p.bounds();
            var rng = new SplittableRandom(seed ^ 0x5157L);
            float worst = 0;
            int occupied = 0;
            for (int i = 0; i < 150_000; i++) {
                // Probe a box 25 % larger than the bounds so outside-bound exactness is tested too.
                float x = lerp(b.minX(), b.maxX(), (float) rng.nextDouble(-.25, 1.25));
                float y = lerp(b.minY(), b.maxY(), (float) rng.nextDouble(-.25, 1.25));
                float z = lerp(b.minZ(), b.maxZ(), (float) rng.nextDouble(-.25, 1.25));
                float fast = p.density(x, y, z, StormMorphologyPrototype.Scale.MESO);
                float ref = clamp(.5f - p.referenceDistance(x, y, z) / p.feather());
                worst = Math.max(worst, Math.abs(fast - ref));
                if (ref > 0) occupied++;
            }
            require(worst <= 1e-5f, type + "/" + seed + " gate/bounds change density by " + worst);
            require(occupied > 1000, "Occupied probes exist");

            // Continuity: one connected component at 12-block voxels (body + attached canopy).
            int components = components(p, 12);
            require(components == 1, type + "/" + seed + " body is not one continuous volume: " + components);

            // Local Lipschitz of the field near the surface (fixed-step march, not sphere tracing).
            float lip = 0; float[] lipAt = null;
            for (int i = 0; i < 60_000; i++) {
                float x = lerp(b.minX(), b.maxX(), (float) rng.nextDouble()), y = lerp(b.minY(), b.maxY(), (float) rng.nextDouble()), z = lerp(b.minZ(), b.maxZ(), (float) rng.nextDouble());
                float d0 = p.referenceDistance(x, y, z);
                if (Math.abs(d0) > 3 * p.feather()) continue;
                float dx = (float) rng.nextDouble(-3, 3), dy = (float) rng.nextDouble(-3, 3), dz = (float) rng.nextDouble(-3, 3);
                float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (len < .5f) continue;
                float li = Math.abs(p.referenceDistance(x + dx, y + dy, z + dz) - d0) / len;
                if (li > lip) { lip = li; lipAt = new float[]{x, y, z, dx, dy, dz}; }
            }
            // Fixed-step march (not sphere tracing): L only sharpens the edge; feather/L must stay above the 4-block step.
            if (lip >= 4.0f) System.out.printf("T257_LIPSCHITZ_AT %s col=%.1f crown=%.1f canopy=%.1f%n", java.util.Arrays.toString(lipAt),
                    p.columnDistance(lipAt[0], lipAt[1], lipAt[2]), p.crownDistance(lipAt[0], lipAt[1], lipAt[2]), p.canopyEval(lipAt[0], lipAt[1], lipAt[2]));
            require(lip < 4.0f && p.feather() / lip > 4.0f, type + "/" + seed + " near-surface Lipschitz " + lip);

            // Exterior whole-volume Lipschitz (the occupancy grid's margin relies on it, not only near the surface).
            float lipAll = 0;
            for (int i = 0; i < 60_000; i++) {
                float x = lerp(b.minX(), b.maxX(), (float) rng.nextDouble()), y = lerp(b.minY(), b.maxY(), (float) rng.nextDouble()), z = lerp(b.minZ(), b.maxZ(), (float) rng.nextDouble());
                float dx = (float) rng.nextDouble(-20, 20), dy = (float) rng.nextDouble(-20, 20), dz = (float) rng.nextDouble(-20, 20);
                float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (len < 1) continue;
                // Exterior only: from a voxel centre with d - L*half >= cut the field cannot reach cut without
                // first crossing the region d >= cut, so the margin needs L only there.
                float d0 = p.referenceDistance(x, y, z), d1 = p.referenceDistance(x + dx, y + dy, z + dz);
                if (Math.min(d0, d1) < p.feather() * .5f) continue;
                lipAll = Math.max(lipAll, Math.abs(d1 - d0) / len);
            }
            require(lipAll < ConvectiveGrowthFieldPrototype.GRID_LIPSCHITZ, type + "/" + seed + " whole-volume Lipschitz " + lipAll + " exceeds the occupancy-grid margin");
            Metrics m = metrics(p, 10, p.baseY());
            var ap = ConvectiveGrowthFieldA.compile(type, shape, seed);
            Metrics a = metrics(ap, 10, ap.baseY());
            float H = p.height();
            for(int candidate=0;candidate<2;candidate++) {
                Metrics measured=candidate==0?a:m;
                final int model=candidate;
                float[] canopy=measureCanopy(candidate==0?ap.canopyBounds():p.canopyBounds(),
                        (x,y,z)->model==0?ap.canopyDistance(x,y,z):p.canopyDistance(x,y,z));
                System.out.printf(java.util.Locale.ROOT,"T257_GEOMETRY type=%s seed=%d model=%s height=%.6f towerWidth=%.6f heightToTowerWidth=%.6f baseWidthToHeight=%.6f canopyThicknessToHeight=%.6f canopyLateralExtent=%.6f upperLateralExtent=%.6f towerLean=%.6f grid=10 canopyGrid=20 densityThreshold=0.5%n",
                        type,seed,candidate==0?"A":"B",measured.depth,measured.towerWidth,measured.depthToWidth,
                        measured.baseWidth/measured.depth,canopy[1]/measured.depth,canopy[0],measured.upperSpread,
                        candidate==0?ap.axisX(1)-ap.axisX(0):p.axisX(1)-p.axisX(0));
            }
            System.out.printf("T257_METRICS type=%s seed=%d components=%d lipschitz=%.2f depthToWidth=%.2f (A %.2f) baseFlatness=%.2f (A %.2f) baseWidthToDepth=%.2f (A %.2f) upperSpreadToTower=%.2f (A %.2f) canopyMinThickToH=%.2f (A %.2f) topOffsetX=%.0f%n",
                    type, seed, components, lip, m.depthToWidth, a.depthToWidth, m.baseFlatness, a.baseFlatness,
                    m.baseWidth / m.depth, a.baseWidth / a.depth, m.upperSpread / m.towerWidth, a.upperSpread / a.towerWidth,
                    m.upperMinThickness / m.depth, a.upperMinThickness / a.depth, m.topOffsetX);
            // Reference-derived bands (photographs, 2026-10-08 review set): broad flat base, tower
            // roughly as wide as it is tall, anvil spreading well past the tower with real thickness.
            require(m.baseFlatness >= .80f, type + " base is broad and flat: " + m.baseFlatness);
            require(m.baseWidth >= (type.equals("cumulonimbus_calvus") ? .70f : .90f) * m.depth, type + " base width is comparable to the storm depth: " + m.baseWidth / m.depth);
            switch (type) {
                case "cumulonimbus_calvus" -> require(m.depthToWidth >= .9f && m.depthToWidth <= 1.8f, "Calvus tower proportions within the photographic band: " + m.depthToWidth);
                case "cumulonimbus_capillatus" -> {
                    require(m.upperSpread >= 1.6f * m.towerWidth, "Capillatus anvil spreads well beyond the tower");
                    require(m.upperMinThickness >= .15f * m.depth, "Capillatus anvil keeps substantial thickness: " + m.upperMinThickness / m.depth);
                }
                default -> {
                    require(m.topOffsetX > .10f * m.towerWidth, "Supercell updraft tilts downwind");
                    require(m.upperSpread >= 1.8f * m.towerWidth, "Supercell anvil is large and directional");
                    require(m.upperMinThickness >= .12f * m.depth, "Supercell anvil keeps substantial thickness");
                }
            }
        }
        // Independent control responses on the neutral supercell profile.
        var neutral = control("neutral");
        var n = ConvectiveGrowthFieldPrototype.compile("supercell", neutral, 20261008L);
        for (String arm : new String[]{"tilt", "shear", "split", "narrowing", "wall"}) {
            var c = ConvectiveGrowthFieldPrototype.compile("supercell", control(arm), 20261008L);
            double l1 = 0;
            var rng = new SplittableRandom(7);
            var b = n.bounds();
            for (int i = 0; i < 40_000; i++) {
                float x = lerp(b.minX() - 400, b.maxX() + 400, (float) rng.nextDouble()), y = lerp(b.minY() - 200, b.maxY() + 200, (float) rng.nextDouble()), z = lerp(b.minZ() - 400, b.maxZ() + 400, (float) rng.nextDouble());
                l1 += Math.abs(n.density(x, y, z, null) - c.density(x, y, z, null));
            }
            System.out.printf("T257_CONTROL arm=%s densityL1=%.1f%n", arm, l1);
            // Local features (wall cloud) touch few of the whole-volume probes; an inert control scores 0.
            require(l1 > 5, "Control " + arm + " changes geometry");
        }
        System.out.println("T257_SANDBOX assertions=" + assertions + " pass=true");
    }

    static CloudShapeProfile control(String arm) {
        var shape = CloudShapeProfile.defaultFor("supercell", CloudFamily.CUMULONIMBUS, null);
        return new CloudShapeProfile("prototype/control", shape.getBaseRadius(), shape.getBaseOffset(),
                shape.getTopOffset(), shape.getLobeCountMin(), shape.getLobeCountMax(), shape.getLobeStrength(),
                arm.equals("tilt") ? .8f : 0, arm.equals("shear") ? .8f : 0,
                arm.equals("split") ? 1 : 0, arm.equals("narrowing") ? 1 : 0,
                shape.getAnvilSpread(), shape.getBaseFlattening(), shape.getEdgeRaggedness(), arm.equals("wall") ? 1 : 0);
    }

    record Metrics(int volume, float depthToWidth, float towerWidth, float topOffsetX, float upperSpread, float upperMinThickness,
                   float depth, float baseWidth, float baseFlatness) {}

    @FunctionalInterface interface Distance {float at(float x,float y,float z);}
    /** Separate frozen canopy, not its containment box: X span and median best-Z thickness
     * in the 40–85% downwind band. No contribution from body or merge. 20-block grid. */
    static float[] measureCanopy(StormMorphologyPrototype.Bounds bounds,Distance distance) {
        if(bounds==null)return new float[]{0,0};float cell=20;
        int nx=(int)Math.ceil((bounds.maxX()-bounds.minX())/cell),ny=(int)Math.ceil((bounds.maxY()-bounds.minY())/cell),nz=(int)Math.ceil((bounds.maxZ()-bounds.minZ())/cell);
        int[] thickness=new int[nx];int first=nx,last=-1;
        for(int x=0;x<nx;x++)for(int z=0;z<nz;z++) {
            int run=0;
            for(int y=0;y<ny;y++) {
                boolean occupied=distance.at(bounds.minX()+(x+.5f)*cell,bounds.minY()+(y+.5f)*cell,bounds.minZ()+(z+.5f)*cell)<0;
                run=occupied?run+1:0;thickness[x]=Math.max(thickness[x],run);
            }
            if(thickness[x]>0){first=Math.min(first,x);last=Math.max(last,x);}
        }
        if(last<first)return new float[]{0,0};var values=new java.util.ArrayList<Integer>();
        for(int x=first+(int)((last-first)*.40);x<=first+(int)((last-first)*.85);x++)if(thickness[x]>0)values.add(thickness[x]);
        values.sort(Integer::compare);return new float[]{(last-first+1)*cell,values.isEmpty()?0:values.get(values.size()/2)*cell};
    }

    /** Occupancy metrics on a voxel grid: tower depth/width, top lean, upper spread, canopy thickness. */
    /** baseY: the main (rain-free) base level; a wall-cloud lowering below it is not the base. */
    static Metrics metrics(StormGeometryProgram p, float cell, float baseY) {
        var b = p.bounds();
        int nx = (int) Math.ceil((b.maxX() - b.minX()) / cell), ny = (int) Math.ceil((b.maxY() - b.minY()) / cell), nz = (int) Math.ceil((b.maxZ() - b.minZ()) / cell);
        boolean[][][] g = new boolean[nx][ny][nz];
        int volume = 0, minY = ny, maxY = -1;
        for (int i = 0; i < nx; i++) for (int j = 0; j < ny; j++) for (int k = 0; k < nz; k++) {
            g[i][j][k] = p.density(b.minX() + (i + .5f) * cell, b.minY() + (j + .5f) * cell, b.minZ() + (k + .5f) * cell, StormMorphologyPrototype.Scale.MACRO) > .5f;
            if (g[i][j][k]) { volume++; minY = Math.min(minY, j); maxY = Math.max(maxY, j); }
        }
        // Tower width: mean x-extent over the 25-55 % height band.
        float widthSum = 0; int widthRows = 0;
        float upperSpread = 0, topCentre = 0, baseCentre = 0;
        for (int j = minY; j <= maxY; j++) {
            float h = (j - minY) / (float) Math.max(1, maxY - minY);
            int lo = nx, hi = -1;
            float centre = 0; int count = 0;
            for (int i = 0; i < nx; i++) for (int k = 0; k < nz; k++) if (g[i][j][k]) { lo = Math.min(lo, i); hi = Math.max(hi, i); centre += i; count++; }
            if (hi < 0) continue;
            if (h >= .25f && h <= .55f) {
                // B: tower width from the body field alone, so an anvil underside in the band is not counted.
                if (p instanceof ConvectiveGrowthFieldPrototype.Program bp) {
                    int blo = nx, bhi = -1;
                    float y = b.minY() + (j + .5f) * cell;
                    for (int i = 0; i < nx; i++) for (int k = 0; k < nz; k++)
                        if (bp.bodyDistance(b.minX() + (i + .5f) * cell, y, b.minZ() + (k + .5f) * cell) < bp.feather() * .5f) { blo = Math.min(blo, i); bhi = Math.max(bhi, i); }
                    if (bhi >= 0) { lo = blo; hi = bhi; }
                }
                widthSum += (hi - lo + 1) * cell; widthRows++;
            }
            if (h >= .70f) upperSpread = Math.max(upperSpread, (hi - lo + 1) * cell);
            if (h <= .10f) baseCentre = centre / count * cell;
        }
        // Top lean: x of the highest occupied voxels relative to the base column centre.
        int tc = 0; float tx = 0;
        for (int i = 0; i < nx; i++) for (int k = 0; k < nz; k++) for (int j = maxY; j >= Math.max(minY, maxY - 2); j--) if (g[i][j][k]) { tx += i; tc++; break; }
        topCentre = tc == 0 ? 0 : tx / tc * cell;
        float towerWidth = widthRows == 0 ? 1 : widthSum / widthRows;
        // Upper-structure thinness: over upper-half columns (x,z) occupied above 70 % height, the
        // smallest vertical run in the outer 60 % of the downwind extent (a blade gives ~1 cell).
        float minThick = Float.MAX_VALUE;
        int upperStart = minY + (int) ((maxY - minY) * .60f);
        int xFirst = nx, xLast = -1;
        for (int i = 0; i < nx; i++) for (int k = 0; k < nz; k++) for (int j = upperStart; j <= maxY; j++) if (g[i][j][k]) { xFirst = Math.min(xFirst, i); xLast = Math.max(xLast, i); }
        for (int i = xFirst + (int) ((xLast - xFirst) * .4f); i <= xFirst + (int) ((xLast - xFirst) * .85f) && xLast > 0; i++) {
            int best = 0;
            for (int k = 0; k < nz; k++) {
                int run = 0;
                for (int j = upperStart; j <= maxY; j++) { run = g[i][j][k] ? run + 1 : 0; best = Math.max(best, run); }
            }
            if (best > 0) minThick = Math.min(minThick, best * cell);
        }
        if (minThick == Float.MAX_VALUE) minThick = 0;
        float depth = (maxY - minY + 1) * cell;
        int mainBase = Math.max(minY, (int) Math.floor((baseY - b.minY()) / cell));
        int baseLayer = mainBase + Math.max(1, (int) ((maxY - mainBase) * .02f)), refLayer = mainBase + (int) ((maxY - mainBase) * .10f);
        int baseArea = 0, refArea = 0, bLo = nx, bHi = -1;
        for (int i = 0; i < nx; i++) for (int k = 0; k < nz; k++) {
            if (g[i][baseLayer][k]) { baseArea++; bLo = Math.min(bLo, i); bHi = Math.max(bHi, i); }
            if (g[i][refLayer][k]) refArea++;
        }
        float flatness = refArea == 0 ? 0 : Math.min(1, baseArea / (float) refArea);
        return new Metrics(volume, depth / towerWidth, towerWidth, topCentre - baseCentre, upperSpread, minThick,
                depth, bHi < 0 ? 0 : (bHi - bLo + 1) * cell, flatness);
    }

    static int components(ConvectiveGrowthFieldPrototype.Program p, float cell) {
        var b = p.bounds();
        int nx = (int) Math.ceil((b.maxX() - b.minX()) / cell), ny = (int) Math.ceil((b.maxY() - b.minY()) / cell), nz = (int) Math.ceil((b.maxZ() - b.minZ()) / cell);
        boolean[] g = new boolean[nx * ny * nz];
        for (int i = 0; i < nx; i++) for (int j = 0; j < ny; j++) for (int k = 0; k < nz; k++)
            g[(i * ny + j) * nz + k] = p.density(b.minX() + (i + .5f) * cell, b.minY() + (j + .5f) * cell, b.minZ() + (k + .5f) * cell, null) > .5f;
        boolean[] seen = new boolean[g.length];
        int comps = 0;
        var queue = new ArrayDeque<Integer>();
        for (int start = 0; start < g.length; start++) {
            if (!g[start] || seen[start]) continue;
            comps++;
            queue.add(start); seen[start] = true;
            int size = 0; float sx = 0, sy = 0, sz = 0;
            while (!queue.isEmpty()) {
                int c = queue.poll(), i = c / (ny * nz), j = (c / nz) % ny, k = c % nz;
                size++; sx += i; sy += j; sz += k;
                int[][] nb = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
                for (int[] d : nb) {
                    int a = i + d[0], e = j + d[1], f = k + d[2];
                    if (a < 0 || e < 0 || f < 0 || a >= nx || e >= ny || f >= nz) continue;
                    int id = (a * ny + e) * nz + f;
                    if (g[id] && !seen[id]) { seen[id] = true; queue.add(id); }
                }
            }
            if (comps > 1 || size < 50) System.out.printf("T257_COMPONENT n=%d size=%d centre=(%.0f,%.0f,%.0f)%n", comps, size,
                    b.minX() + (sx / size + .5f) * cell, b.minY() + (sy / size + .5f) * cell, b.minZ() + (sz / size + .5f) * cell);
        }
        return comps;
    }

    static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    static float clamp(float v) { return Math.max(0, Math.min(1, v)); }
}
