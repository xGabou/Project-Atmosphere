ROOT = 'C:/Users/matga/Downloads/Project-Atmosphere/'
SIM = 'src/main/java/net/Gabou/projectatmosphere/clouds/simulation/'


def edit(path, pairs):
    raw = open(ROOT + path, encoding='utf-8', newline='').read()
    crlf = '\r\n' in raw
    s = raw.replace('\r\n', '\n')
    for a, b in pairs:
        assert s.count(a) == 1, (path, a[:70], s.count(a))
        s = s.replace(a, b)
    if crlf:
        s = s.replace('\n', '\r\n')
    open(ROOT + path, 'w', encoding='utf-8', newline='').write(s)


edit(SIM + 'CloudMorphologyGenerators.java', [
    ("""import net.Gabou.projectatmosphere.clouds.state.CloudClusterState;
""", """import net.Gabou.projectatmosphere.clouds.state.CloudClusterState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionStateStore;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeRegistry;
import net.minecraft.server.level.ServerLevel;
"""),
    ("""    private static final float STORM_ANVIL_DOWNWIND_POSITION = 0.52F;
""", """    private static final float STORM_ANVIL_DOWNWIND_POSITION = 0.52F;

    /**
     * T237. The storm template: 0 is the T134/T127 layout (a symmetric
     * canopy of four large anvil lobes over a short tower and a tall base
     * ball - the mushroom T236 attributed), 1 the T237 layout (a flatter
     * two-lobe base, a column-scale tower that spans from inside the base
     * into the anvil, and varied anvil lobes placed downwind along +X, the
     * axis the anvil row has always used).
     */
    static final int PRODUCTION_STORM_TEMPLATE = 0;
    static volatile int stormTemplateVersion = PRODUCTION_STORM_TEMPLATE;
    // T237 V1 anvil row: along-wind position (x groupRadius), cross offset
    // (x groupRadius) and radius multiplier, upwind to downwind.
    private static final float[] V1_ANVIL_ALONG = {0.15F, 0.55F, 0.95F, 1.35F};
    private static final float[] V1_ANVIL_CROSS = {0.10F, -0.18F, 0.20F, -0.08F};
    private static final float[] V1_ANVIL_RADIUS = {0.42F, 0.62F, 0.72F, 0.60F};
"""),
    ("""    private static Vec3 stormCell(Vec3 origin, SpawnPlan plan, int clusterIndex, RandomSource random) {
        CloudMorphologyMembership.Stage stage = new CloudMorphologyMembership(""", """    private static Vec3 stormCell(Vec3 origin, SpawnPlan plan, int clusterIndex, RandomSource random) {
        if (stormTemplateVersion == 1) {
            return stormCellV1(origin, plan, clusterIndex, random);
        }
        CloudMorphologyMembership.Stage stage = new CloudMorphologyMembership("""),
    ("""    private static Vec3 spiralCell(Vec3 origin, SpawnPlan plan, int clusterIndex, RandomSource random) {""",
     """    /** T237 V1 member placement; member 0 stays at the origin (caller). */
    private static Vec3 stormCellV1(Vec3 origin, SpawnPlan plan, int clusterIndex, RandomSource random) {
        CloudMorphologyMembership.Stage stage = new CloudMorphologyMembership(
                null, clusterIndex, plan.clusterCount()).stageFor(CloudMorphologyFamily.STORM_ANVIL);
        float r = plan.radius();
        float g = plan.groupRadius();
        float t = plan.topRise();
        int anvilStart = plan.clusterCount() / 2 + 1;
        int baseCount = Math.max(1, anvilStart / 3);
        int coreCount = Math.max(1, (anvilStart - baseCount) / 2);
        int towerStart = baseCount + coreCount;
        float jitterA = (random.nextFloat() - 0.5F);
        float jitterC = (random.nextFloat() - 0.5F);
        float along;
        float cross;
        float y;
        switch (stage) {
            case BASE -> {
                along = 0.22F * r + jitterA * 0.08F * r;
                cross = -0.14F * r + jitterC * 0.08F * r;
                y = 0.0F;
            }
            case CORE -> {
                int ordinal = Math.max(0, clusterIndex - baseCount);
                along = (0.04F + 0.05F * ordinal) * r + jitterA * 0.04F * r;
                cross = (ordinal == 0 ? 0.06F : -0.06F) * r + jitterC * 0.04F * r;
                y = t * (0.10F + 0.08F * ordinal);
            }
            case TOWER -> {
                int ordinal = Math.max(0, clusterIndex - towerStart);
                // The column leans downwind with height.
                along = (0.08F + 0.10F * ordinal) * r + jitterA * 0.05F * r;
                cross = (ordinal == 0 ? 0.05F : -0.04F) * r + jitterC * 0.05F * r;
                y = t * (0.30F + 0.28F * ordinal);
            }
            case ANVIL -> {
                int a = Math.min(V1_ANVIL_ALONG.length - 1, Math.max(0, clusterIndex - anvilStart));
                along = g * V1_ANVIL_ALONG[a] * (0.88F + (jitterA + 0.5F) * 0.24F);
                cross = g * (V1_ANVIL_CROSS[a] + jitterC * 0.10F);
                y = t * (0.80F - 0.01F * a);
            }
            default -> throw new IllegalStateException("Unexpected storm stage " + stage);
        }
        return origin.add(along, y + (random.nextFloat() - 0.5F) * 6.0F, cross);
    }

    /**
     * T237. Re-lays every live STORM_ANVIL region out with the given template,
     * in place: member 0 keeps its centre (the plan origin), every other
     * member is re-placed and re-sized from a random seeded by the region and
     * member index, so two templates applied to one storm differ only in the
     * layout. Radii are set at their targets (no growth animation). Used by
     * the same-storm capture A/B; returns a one-line summary.
     */
    static String relayoutStormRegions(ServerLevel level, int version) {
        int previous = stormTemplateVersion;
        int regions = 0;
        int members = 0;
        try {
            stormTemplateVersion = version;
            for (CloudRegionState region : CloudRegionStateStore.getAll(level)) {
                List<CloudClusterState> clusters = region.getClusters();
                CloudClusterState origin = null;
                int count = 0;
                for (CloudClusterState cluster : clusters) {
                    if (cluster.getMorphologyFamily() == CloudMorphologyFamily.STORM_ANVIL
                            && cluster.getMorphologyCount() >= 4) {
                        count = cluster.getMorphologyCount();
                        if (cluster.getMorphologyIndex() == 0) {
                            origin = cluster;
                        }
                    }
                }
                if (origin == null || count < 4) {
                    continue;
                }
                CloudTypeDefinition definition = CloudTypeRegistry.getOrDefault(origin.getCloudTypeId());
                SpawnPlan plan = stormAnvilPlanForCount(definition, count);
                Vec3 originCentre = origin.getCenter();
                long regionSeed = region.getRegionId().getMostSignificantBits()
                        ^ region.getRegionId().getLeastSignificantBits();
                for (CloudClusterState cluster : clusters) {
                    if (cluster.getMorphologyFamily() != CloudMorphologyFamily.STORM_ANVIL) {
                        continue;
                    }
                    int index = cluster.getMorphologyIndex();
                    RandomSource random = RandomSource.create(regionSeed ^ (index * 0x9E3779B97F4A7C15L));
                    Vec3 centre = index == 0 ? originCentre
                            : createClusterCenter(originCentre, plan, index, random);
                    StormLobeSpec spec = stormLobeSpec(plan, index, (float) centre.y);
                    float radius = Math.max(8.0F, plan.radius() * spec.radiusMultiplier());
                    cluster.setCenter(centre);
                    cluster.setPreviousCenter(centre);
                    cluster.setRadius(radius);
                    cluster.setSpawnRadius(radius);
                    cluster.setTargetRadius(radius);
                    cluster.setVerticalBounds(spec.baseY(), spec.topY());
                    members++;
                }
                regions++;
            }
            CloudRegionStateStore.markDirty(level);
        } finally {
            stormTemplateVersion = previous;
        }
        return "t237_relayout version=" + version + " regions=" + regions + " members=" + members;
    }

    private static Vec3 spiralCell(Vec3 origin, SpawnPlan plan, int clusterIndex, RandomSource random) {"""),
    ("""        switch (stage) {
            case BASE -> {
                radiusMultiplier = 1.16F;
                baseY = centerY - plan.baseDrop();
                topY = centerY + plan.topRise() * 0.42F;
            }""", """        if (stormTemplateVersion == 1) {
            return stormLobeSpecV1(plan, clusterIndex, centerY, stage, baseCount, coreCount, towerStart, anvilStart);
        }
        switch (stage) {
            case BASE -> {
                radiusMultiplier = 1.16F;
                baseY = centerY - plan.baseDrop();
                topY = centerY + plan.topRise() * 0.42F;
            }"""),
    ("""    record StormLobeSpec(""", """    /**
     * T237 V1 sizing. BASE: two broad, flat lobes (top at 0.20 / 0.16 of the
     * top rise, ~290 blocks tall instead of ~450). TOWER: column-scale radii
     * (0.60 / 0.52 of the plan radius instead of 0.39 / 0.33) spanning from
     * inside the base (0.04 T) up into the anvil (0.86 T). ANVIL: varied,
     * smaller lobes (0.42-0.72) and a thinner deck.
     */
    private static StormLobeSpec stormLobeSpecV1(SpawnPlan plan, int clusterIndex, float centerY,
            CloudMorphologyMembership.Stage stage, int baseCount, int coreCount, int towerStart,
            int anvilStart) {
        float t = plan.topRise();
        float radiusMultiplier;
        float baseY;
        float topY;
        switch (stage) {
            case BASE -> {
                boolean primary = clusterIndex == 0;
                radiusMultiplier = primary ? 1.30F : 1.02F;
                baseY = centerY - plan.baseDrop() * (primary ? 1.0F : 0.75F);
                topY = centerY + t * (primary ? 0.20F : 0.16F);
            }
            case CORE -> {
                int ordinal = Math.max(0, clusterIndex - baseCount);
                radiusMultiplier = ordinal == 0 ? 0.62F : 0.58F;
                baseY = centerY - t * 0.08F;
                topY = centerY + t * (ordinal == 0 ? 0.26F : 0.30F);
            }
            case TOWER -> {
                int ordinal = Math.max(0, clusterIndex - towerStart);
                radiusMultiplier = ordinal == 0 ? 0.60F : 0.52F;
                baseY = centerY - t * (ordinal == 0 ? 0.26F : 0.20F);
                topY = centerY + t * (ordinal == 0 ? 0.24F : 0.28F);
            }
            case ANVIL -> {
                int a = Math.min(V1_ANVIL_RADIUS.length - 1, Math.max(0, clusterIndex - anvilStart));
                radiusMultiplier = V1_ANVIL_RADIUS[a];
                baseY = centerY - t * 0.10F;
                topY = centerY + t * 0.10F;
            }
            default -> throw new IllegalStateException("Unexpected storm lobe stage " + stage);
        }
        return new StormLobeSpec(stage, radiusMultiplier, baseY, Math.max(baseY + 8.0F, topY));
    }

    record StormLobeSpec("""),
])
edit(SIM + 'CloudGroupSpawner.java', [
    ("""    public static @Nullable CloudRegionState spawnRequestedCloud(""", """    /**
     * T237. Re-lays the live storms out with a storm template version (0 the
     * T134/T127 layout, 1 the T237 layout) for the same-storm capture A/B.
     * Server thread only.
     */
    public static String relayoutStorms(ServerLevel level, int version) {
        return CloudMorphologyGenerators.relayoutStormRegions(level, version);
    }

    public static @Nullable CloudRegionState spawnRequestedCloud("""),
])
print('ok')
