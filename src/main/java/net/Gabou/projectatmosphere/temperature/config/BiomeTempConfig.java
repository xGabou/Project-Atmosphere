package net.Gabou.projectatmosphere.temperature.config;

import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.Map;
import java.util.HashMap;

/**
 * Holds per‐biome temperature ranges for each of the four seasons, plus daily clamps.
 */
public class BiomeTempConfig {
    public enum Season { WINTER, SPRING, SUMMER, AUTUMN }

    /** Simple min/max °C pair. */
    public static record Range(float minC, float maxC) {}

    /**
     * Daily clamp specifying:
     *  - minMin: absolute minimum this season
     *  - avgNight: typical low (night) this season
     *  - avgDay: typical high (day) this season
     *  - maxMax: absolute maximum this season
     */
    public static record DailyRange(float minMin, float avgNight, float avgDay, float maxMax) {}

    /** season → (biome → Range) */
    public static final Map<Season, Map<ResourceLocation, Range>> SEASON_RANGES;
    /** season → (biome → DailyRange) */
    public static final Map<Season, Map<ResourceLocation, DailyRange>> SEASON_CLAMPS;

    static {
        SEASON_RANGES = new EnumMap<>(Season.class);
        SEASON_CLAMPS = new EnumMap<>(Season.class);
        for (Season s : Season.values()) {
            SEASON_RANGES.put(s, new HashMap<>());
            SEASON_CLAMPS.put(s, new HashMap<>());
        }

        // ─────────────────────────────────────────────────────────────────────
        // Overworld – Temperate & Humid Biomes
        // ─────────────────────────────────────────────────────────────────────
        putAllSeasons("plains",
                new float[]{-10, 5, 20, 5}, new Range[]{
                        new Range(-10f, 5f),
                        new Range( 5f,18f),
                        new Range(20f,35f),
                        new Range( 5f,18f)
                }, new DailyRange[]{
                        new DailyRange(-25f, -5f,  3f,  4f),  // winter
                        new DailyRange(  -10f,  8f, 14f, 19f),  // spring
                        new DailyRange( 6f, 15f, 22f, 36f),  // summer
                        new DailyRange(  0f,  9f, 14f, 25f)   // autumn
                }
        );
        mirrorBiome("sunflower_plains", "plains");

        putAllSeasons("forest",
                new float[]{-8, 6, 18, 6}, new Range[]{
                        new Range(-8f, 4f),
                        new Range( 6f,16f),
                        new Range(18f,30f),
                        new Range( 6f,16f)
                }, new DailyRange[]{
                        new DailyRange(-15f, -6f,  2f,  6f),
                        new DailyRange( -2f,  8f, 18f, 24f),
                        new DailyRange( 10f, 20f, 28f, 35f),
                        new DailyRange(  2f, 10f, 20f, 26f)
                }
        );
        mirrorBiome("flower_forest",       "forest");
        mirrorBiome("birch_forest",        "forest");
        mirrorBiome("old_growth_birch_forest", "forest");

        putAllSeasons("dark_forest",
                new float[]{-10, 5, 15, 5}, new Range[]{
                        new Range(-10f,  5f),
                        new Range(  5f,18f),
                        new Range( 15f,30f),
                        new Range(  5f,18f)
                }, new DailyRange[]{
                        new DailyRange(-20f, -8f,  0f,  6f),
                        new DailyRange(-20f, -8f, 10f, 21f),
                        new DailyRange(  5f, 18f, 28f, 35f),
                        new DailyRange(  0f, 12f, 22f, 30f)
                }
        );

        putAllSeasons("cherry_grove",
                new float[]{-10, 0, 10, 0}, new Range[]{
                        new Range(-10f, 5f),
                        new Range( 0f,15f),
                        new Range(10f,25f),
                        new Range( 0f,15f)
                }, new DailyRange[]{
                        new DailyRange(-15f, -3f,  4f, 10f),
                        new DailyRange( -5f, 10f, 20f, 25f),
                        new DailyRange(  5f, 15f, 22f, 28f),
                        new DailyRange( -5f, 10f, 20f, 25f)
                }
        );

        putAllSeasons("swamp",
                new float[]{-5, 10, 20, 10}, new Range[]{
                        new Range(-5f, 10f),
                        new Range(10f,22f),
                        new Range(20f,35f),
                        new Range(10f,22f)
                }, new DailyRange[]{
                        new DailyRange(-10f,  3f, 10f, 20f),
                        new DailyRange(  5f, 12f, 20f, 30f),
                        new DailyRange( 15f, 22f, 28f, 38f),
                        new DailyRange(  5f, 12f, 20f, 30f)
                }
        );

        putAllSeasons("meadow",
                new float[]{-12, 2, 15, 2}, new Range[]{
                        new Range(-12f, 3f),
                        new Range( 2f,14f),
                        new Range(15f,26f),
                        new Range( 2f,14f)
                }, new DailyRange[]{
                        new DailyRange(-18f, -6f, 3f, 12f),
                        new DailyRange( -3f,  6f,15f, 22f),
                        new DailyRange(  7f, 16f,24f, 32f),
                        new DailyRange( -2f,  7f,18f, 26f)
                }
        );

        // ─────────────────────────────────────────────────────────────────────
        // Overworld – Cold & Alpine Biomes
        // ─────────────────────────────────────────────────────────────────────
        putAllSeasons("taiga",
                new float[]{-25, -5, 10, -5}, new Range[]{
                        new Range(-25f,-5f),
                        new Range( -5f,10f),
                        new Range( 10f,22f),
                        new Range( -5f,10f)
                }, new DailyRange[]{
                        new DailyRange(-35f,-12f, 0f,  5f),
                        new DailyRange(-15f, -2f,10f, 18f),
                        new DailyRange(  5f, 12f,20f, 28f),
                        new DailyRange(-10f,  0f,12f, 20f)
                }
        );
        mirrorBiome("old_growth_spruce_taiga", "taiga");
        mirrorBiome("old_growth_pine_taiga",   "taiga");
        mirrorBiome("snowy_taiga",             "taiga");

        putAllSeasons("snowy_plains",
                new float[]{-35, -15, 5, -15}, new Range[]{
                        new Range(-35f,-10f),
                        new Range(-15f, 5f),
                        new Range(  5f,15f),
                        new Range(-15f, 5f)
                }, new DailyRange[]{
                        new DailyRange(-45f,-20f,-5f,  2f),
                        new DailyRange(-25f, -5f, 8f, 14f),
                        new DailyRange(  0f,  8f,14f, 20f),
                        new DailyRange(-20f, -5f, 6f, 14f)
                }
        );

        putAllSeasons("ice_spikes",
                new float[]{-50, -30, -5, -30}, new Range[]{
                        new Range(-50f,-30f),
                        new Range(-30f, -5f),
                        new Range( -5f,  5f),
                        new Range(-30f,  0f)
                }, new DailyRange[]{
                        new DailyRange(-55f,-25f, 0f,  5f),
                        new DailyRange(-45f,-15f, 3f, 10f),
                        new DailyRange(-20f,  0f, 8f, 15f),
                        new DailyRange(-45f,-15f, 3f, 10f)
                }
        );
        mirrorBiome("snowy_slopes", "snowy_plains");

        putAllSeasons("grove",  // same as taiga
                new float[]{-25, -5, 10, -5}, new Range[]{
                        new Range(-25f,-5f),
                        new Range( -5f,10f),
                        new Range( 10f,22f),
                        new Range( -5f,10f)
                }, new DailyRange[]{
                        new DailyRange(-35f,-12f, 0f,  5f),
                        new DailyRange(-15f, -2f,10f, 18f),
                        new DailyRange(  5f, 12f,20f, 28f),
                        new DailyRange(-10f,  0f,12f, 20f)
                }
        );

        // ─────────────────────────────────────────────────────────────────────
        // Overworld – Warm & Arid Biomes
        // ─────────────────────────────────────────────────────────────────────
        putAllSeasons("jungle",
                new float[]{20, 22, 25, 22}, new Range[]{
                        new Range(20f,25f),
                        new Range(22f,30f),
                        new Range(25f,35f),
                        new Range(22f,30f)
                }, new DailyRange[]{
                        new DailyRange(18f,25f,30f,38f),
                        new DailyRange(20f,27f,32f,38f),
                        new DailyRange(22f,30f,35f,42f),
                        new DailyRange(20f,27f,32f,38f)
                }
        );
        mirrorBiome("sparse_jungle", "jungle");
        mirrorBiome("bamboo_jungle", "jungle");

        putAllSeasons("desert",
                new float[]{5, 15, 30, 15}, new Range[]{
                        new Range( 5f,20f),
                        new Range(15f,35f),
                        new Range(30f,45f),
                        new Range(15f,35f)
                }, new DailyRange[]{
                        new DailyRange(-5f,25f,35f,50f),
                        new DailyRange( 0f,28f,38f,50f),
                        new DailyRange( 5f,30f,40f,60f),
                        new DailyRange( 0f,28f,38f,50f)
                }
        );
        mirrorBiome("badlands", "desert");
        mirrorBiome("wooded_badlands", "badlands");
        mirrorBiome("eroded_badlands", "badlands");

        putAllSeasons("savanna",
                new float[]{10,15,20,15}, new Range[]{
                        new Range(10f,25f),
                        new Range(15f,30f),
                        new Range(20f,40f),
                        new Range(15f,30f)
                }, new DailyRange[]{
                        new DailyRange( 5f,25f,32f,42f),
                        new DailyRange(15f,28f,36f,45f),
                        new DailyRange(20f,32f,40f,50f),
                        new DailyRange(15f,28f,36f,45f)
                }
        );
        mirrorBiome("savanna_plateau", "savanna");
        mirrorBiome("windswept_savanna", "savanna");
        mirrorBiome("mangrove_swamp",    "swamp");

        // ─────────────────────────────────────────────────────────────────────
        // Overworld – Oceanic & Coastal Biomes (separated)
        // ─────────────────────────────────────────────────────────────────────
        putAllSeasons("frozen_ocean",
                new float[]{-2, 2,  5,  2}, new Range[]{
                        new Range(-2f,  2f),
                        new Range( 2f,  8f),
                        new Range( 5f, 12f),
                        new Range( 2f,  8f)
                }, new DailyRange[]{
                        new DailyRange(-5f, -2f, 2f,  6f),
                        new DailyRange( 0f,  2f, 5f, 10f),
                        new DailyRange( 3f,  6f,10f, 15f),
                        new DailyRange( 0f,  2f, 5f, 10f)
                }
        );

        putAllSeasons("cold_ocean",
                new float[]{0,  5, 12, 5}, new Range[]{
                        new Range(0f, 10f),
                        new Range(5f, 15f),
                        new Range(12f,20f),
                        new Range(5f, 15f)
                }, new DailyRange[]{
                        new DailyRange(-2f, 0f, 6f, 12f),
                        new DailyRange( 2f, 8f,15f, 20f),
                        new DailyRange( 8f,12f,18f, 25f),
                        new DailyRange( 2f, 8f,15f, 20f)
                }
        );

        putAllSeasons("ocean",
                new float[]{0, 5, 15, 5}, new Range[]{
                        new Range(0f, 10f),
                        new Range(5f, 15f),
                        new Range(15f,25f),
                        new Range(5f, 15f)
                }, new DailyRange[]{
                        new DailyRange(-2f, 5f,15f, 22f),
                        new DailyRange( 0f,10f,18f, 25f),
                        new DailyRange( 2f,12f,20f, 28f),
                        new DailyRange( 0f,10f,18f, 25f)
                }
        );

        putAllSeasons("lukewarm_ocean",
                new float[]{2, 10,20,10}, new Range[]{
                        new Range(2f,12f),
                        new Range(10f,20f),
                        new Range(20f,30f),
                        new Range(10f,20f)
                }, new DailyRange[]{
                        new DailyRange( 0f, 6f,15f, 22f),
                        new DailyRange( 5f,12f,18f, 28f),
                        new DailyRange(10f,18f,25f, 32f),
                        new DailyRange( 5f,12f,18f, 28f)
                }
        );

        putAllSeasons("warm_ocean",
                new float[]{5,12,25,12}, new Range[]{
                        new Range(5f,15f),
                        new Range(12f,25f),
                        new Range(25f,35f),
                        new Range(12f,25f)
                }, new DailyRange[]{
                        new DailyRange(2f,10f,20f,28f),
                        new DailyRange(8f,18f,25f,32f),
                        new DailyRange(18f,23f,30f,38f),
                        new DailyRange(8f,18f,25f,32f)
                }
        );

        mirrorBiome("deep_frozen_ocean",   "frozen_ocean");
        mirrorBiome("deep_cold_ocean",     "cold_ocean");
        mirrorBiome("deep_ocean",          "ocean");
        mirrorBiome("deep_lukewarm_ocean", "lukewarm_ocean");
        mirrorBiome("deep_warm_ocean",     "warm_ocean");

        putAllSeasons("river",
                new float[]{-5, 5,18,5}, new Range[]{
                        new Range(-5f, 5f),
                        new Range( 5f,18f),
                        new Range(18f,30f),
                        new Range( 5f,18f)
                }, new DailyRange[]{
                        new DailyRange(-10f,  5f,18f, 30f),
                        new DailyRange(  0f,  8f,20f, 32f),
                        new DailyRange( 10f, 15f,25f, 38f),
                        new DailyRange(  0f,  8f,20f, 32f)
                }
        );
        mirrorBiome("frozen_river", "river");

        putAllSeasons("mushroom_fields",
                new float[]{5,10,15,10}, new Range[]{
                        new Range(5f,15f),
                        new Range(10f,20f),
                        new Range(15f,25f),
                        new Range(10f,20f)
                }, new DailyRange[]{
                        new DailyRange( 0f,12f,18f, 28f),
                        new DailyRange( 5f,14f,20f, 30f),
                        new DailyRange(10f,16f,22f, 32f),
                        new DailyRange( 5f,14f,20f, 30f)
                }
        );
        mirrorBiome("mushroom_field_shore", "mushroom_fields");

        putAllSeasons("lush_caves",
                new float[]{12,14,16,14}, new Range[]{
                        new Range(12f,15f),
                        new Range(14f,18f),
                        new Range(15f,20f),
                        new Range(14f,18f)
                }, new DailyRange[]{
                        new DailyRange(10f,15f,18f, 22f),
                        new DailyRange(11f,16f,19f, 23f),
                        new DailyRange(12f,18f,21f, 25f),
                        new DailyRange(11f,16f,19f, 23f)
                }
        );

        putAllSeasons("dripstone_caves",
                new float[]{8,10,12,10}, new Range[]{
                        new Range( 8f,12f),
                        new Range(10f,15f),
                        new Range(10f,15f),
                        new Range(10f,15f)
                }, new DailyRange[]{
                        new DailyRange( 5f,10f,12f,17f),
                        new DailyRange( 6f,11f,13f,18f),
                        new DailyRange( 8f,12f,14f,20f),
                        new DailyRange( 6f,11f,13f,18f)
                }
        );

        putAllSeasons("deep_dark",
                new float[]{5,7,10,7}, new Range[]{
                        new Range(5f,10f),
                        new Range(7f,10f),
                        new Range(8f,12f),
                        new Range(7f,10f)
                }, new DailyRange[]{
                        new DailyRange(3f, 8f,10f,12f),
                        new DailyRange(4f, 9f,11f,13f),
                        new DailyRange(5f,10f,12f,14f),
                        new DailyRange(4f, 9f,11f,13f)
                }
        );

        // ─────────────────────────────────────────────────────────────────────
        // Nether Biomes (no seasons – same all year)
        // ─────────────────────────────────────────────────────────────────────
        putConstSeasons("nether_wastes",    new Range(45f,50f), new DailyRange(40f,45f,47f,60f));
        putConstSeasons("basalt_deltas",    new Range(50f,55f), new DailyRange(45f,50f,55f,65f));
        putConstSeasons("crimson_forest",   new Range(40f,45f), new DailyRange(35f,40f,43f,48f));
        putConstSeasons("warped_forest",    new Range(30f,40f), new DailyRange(25f,32f,35f,45f));
        putConstSeasons("soul_sand_valley", new Range(35f,45f), new DailyRange(30f,38f,42f,50f));

        // ─────────────────────────────────────────────────────────────────────
        // End Biomes (no seasons – same all year)
        // ─────────────────────────────────────────────────────────────────────
        putConstSeasons("the_end",           new Range(5f,10f),    new DailyRange( 0f, 7f,  8f,12f));
        putConstSeasons("end_highlands",     new Range(5f,10f),    new DailyRange( 0f, 7f,  8f,12f));
        putConstSeasons("end_midlands",      new Range(5f,10f),    new DailyRange( 0f, 7f,  8f,12f));
        putConstSeasons("small_end_islands", new Range(0f, 5f),    new DailyRange(-5f, 2f,  4f, 8f));
        putConstSeasons("end_barrens",       new Range(0f, 5f),    new DailyRange(-5f, 2f,  4f, 8f));
        putConstSeasons("the_void",          new Range(-273f,-273f), new DailyRange(-273f,-273f,-273f,-273f));
    }


    /** Helper: populate all four seasons for a single biome key. */
    private static void putAllSeasons(String biomeKey, float[] s, Range[] ranges, DailyRange[] clamps) {
        ResourceLocation id = ResourceLocation.withDefaultNamespace(biomeKey);
        Season[] S = Season.values();
        for (int i = 0; i < S.length; i++) {
            SEASON_RANGES.get(S[i]).put(id, ranges[i]);
            SEASON_CLAMPS.get(S[i]).put(id, clamps[i]);
        }
    }

    /** Helper: copy one biome’s settings to another (all seasons). */
    private static void mirrorBiome(String targetKey, String sourceKey) {
        ResourceLocation src = ResourceLocation.withDefaultNamespace(sourceKey);
        ResourceLocation dst = ResourceLocation.withDefaultNamespace(targetKey);
        for (Season s : Season.values()) {
            Range      r = SEASON_RANGES.get(s).get(src);
            DailyRange d = SEASON_CLAMPS.get(s).get(src);
            SEASON_RANGES.get(s).put(dst, r);
            SEASON_CLAMPS.get(s).put(dst, d);
        }
    }

    /** Helper: for biomes with no seasonal change (Nether, End). */
    private static void putConstSeasons(String biomeKey, Range r, DailyRange d) {
        ResourceLocation id = ResourceLocation.withDefaultNamespace(biomeKey);
        for (Season s : Season.values()) {
            SEASON_RANGES.get(s).put(id, r);
            SEASON_CLAMPS.get(s).put(id, d);
        }
    }

    /** Retrieve the min/max °C for a given biome and season. */
    public static Range getRange(ResourceLocation biome, Season season) {
        return SEASON_RANGES.getOrDefault(season, Map.of())
                .getOrDefault(biome, new Range(0f, 0f));
    }

    /** Retrieve the daily clamp for a given biome and season. */
    public static DailyRange getClamp(ResourceLocation biome, Season season) {
        return SEASON_CLAMPS.getOrDefault(season, Map.of())
                .getOrDefault(biome, new DailyRange(0f, 0f, 0f, 0f));
    }
}
