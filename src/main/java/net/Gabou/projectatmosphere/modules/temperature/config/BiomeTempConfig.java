package net.Gabou.projectatmosphere.modules.temperature.config;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.Map;
import java.util.HashMap;

/**
 * Holds per‐biome temperature ranges for each of the four seasons,
 * plus automatically‐derived daily clamps.
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

        
        
        
        putAllSeasons("plains", new Range[]{
                new Range(-20f,  5f),  
                new Range(  -10f, 18f),  
                new Range( 15f, 36f),  
                new Range(  -6f, 18f)   
        });
        mirrorBiome("sunflower_plains", "plains");

        putAllSeasons("forest", new Range[]{
                new Range(-18f,  5f),  
                new Range(  -7f, 13f),  
                new Range( 7f, 28f),  
                new Range(  -2f, 19f)   
        });
        mirrorBiome("flower_forest",   "forest");
        mirrorBiome("birch_forest",    "forest");
        mirrorBiome("old_growth_birch_forest", "forest");

        putAllSeasons("dark_forest", new Range[]{
                new Range(-22f,  3f),
                new Range(  -12f, 11f),
                new Range( 8f, 24f),
                new Range(  -9f, 14f)
        });
        putAllSeasons("beach", new Range[]{
                new Range(-2f, 8f),
                new Range(2f, 14f),
                new Range(15f, 30f),
                new Range(5f, 18f)
        });


        putAllSeasons("cherry_grove", new Range[]{
                new Range(-12f,  2f),
                new Range(  -1f, 14f),
                new Range( 8f, 28f),
                new Range(  4f, 21f)
        });

        putAllSeasons("swamp", new Range[]{
                new Range( -5f, 10f),
                new Range( 10f, 22f),
                new Range( 20f, 35f),
                new Range( 10f, 22f)
        });

        putAllSeasons("meadow", new Range[]{
                new Range(-12f,  3f),
                new Range(  2f, 14f),
                new Range( 15f, 26f),
                new Range(  2f, 14f)
        });

        
        
        
        putAllSeasons("taiga", new Range[]{
                new Range(-25f, -5f),
                new Range( -5f, 10f),
                new Range( 10f, 22f),
                new Range( -5f, 10f)
        });
        mirrorBiome("old_growth_spruce_taiga", "taiga");
        mirrorBiome("old_growth_pine_taiga",   "taiga");
        mirrorBiome("snowy_taiga",             "taiga");

        putAllSeasons("snowy_plains", new Range[]{
                new Range(-35f, -10f),
                new Range(-15f,   5f),
                new Range(  5f,  15f),
                new Range(-15f,   5f)
        });

        putAllSeasons("ice_spikes", new Range[]{
                new Range(-50f, -30f),
                new Range(-30f,  -5f),
                new Range( -5f,   5f),
                new Range(-30f,   0f)
        });
        mirrorBiome("snowy_slopes", "snowy_plains");

        putAllSeasons("frozen_peaks", new Range[]{
                new Range(-45f, -25f),
                new Range(-25f, -10f),
                new Range(-10f,   0f),
                new Range(-25f,  -5f)
        });

        
        
        
        putAllSeasons("jungle", new Range[]{
                new Range(20f, 25f),
                new Range(22f, 30f),
                new Range(25f, 35f),
                new Range(22f, 30f)
        });
        mirrorBiome("sparse_jungle", "jungle");
        mirrorBiome("bamboo_jungle", "jungle");

        putAllSeasons("desert", new Range[]{
                new Range( 5f, 20f),
                new Range(15f, 35f),
                new Range(30f, 45f),
                new Range(15f, 35f)
        });

        putAllSeasons("badlands", new Range[]{
                new Range( 0f, 20f),
                new Range(10f, 30f),
                new Range(25f, 40f),
                new Range(10f, 30f)
        });
        mirrorBiome("wooded_badlands", "badlands");
        mirrorBiome("eroded_badlands", "badlands");

        putAllSeasons("savanna", new Range[]{
                new Range(10f, 25f),
                new Range(15f, 30f),
                new Range(20f, 40f),
                new Range(15f, 30f)
        });
        mirrorBiome("windswept_savanna", "savanna");
        mirrorBiome("mangrove_swamp",    "swamp");

        
        
        
        putAllSeasons("ocean", new Range[]{
                new Range( 0f, 10f),
                new Range( 5f, 15f),
                new Range(10f, 20f),
                new Range( 5f, 15f)
        });
        mirrorBiome("lukewarm_ocean",      "ocean");
        mirrorBiome("warm_ocean",          "ocean");
        mirrorBiome("cold_ocean",          "ocean");
        mirrorBiome("frozen_ocean",        "ocean");
        mirrorBiome("deep_ocean",          "ocean");
        mirrorBiome("deep_lukewarm_ocean", "ocean");
        mirrorBiome("deep_warm_ocean",     "ocean");
        mirrorBiome("deep_cold_ocean",     "ocean");
        mirrorBiome("deep_frozen_ocean",   "ocean");

        putAllSeasons("river", new Range[]{
                new Range(-5f,   5f),
                new Range( 5f,  18f),
                new Range(18f,  30f),
                new Range( 5f,  18f)
        });
        mirrorBiome("frozen_river", "river");

        putAllSeasons("mushroom_fields", new Range[]{
                new Range( 5f, 15f),
                new Range(10f, 20f),
                new Range(15f, 25f),
                new Range(10f, 20f)
        });
        mirrorBiome("mushroom_field_shore", "mushroom_fields");

        
        
        
        putAllSeasons("lush_caves", new Range[]{
                new Range(12f, 15f),
                new Range(14f, 18f),
                new Range(15f, 20f),
                new Range(14f, 18f)
        });

        putAllSeasons("dripstone_caves", new Range[]{
                new Range( 8f, 12f),
                new Range(10f, 15f),
                new Range(10f, 15f),
                new Range(10f, 15f)
        });

        putAllSeasons("deep_dark", new Range[]{
                new Range(5f, 10f),
                new Range(7f, 10f),
                new Range(8f, 12f),
                new Range(7f, 10f)
        });
        mirrorBiome("savanna_plateau",        "savanna");
        mirrorBiome("stony_shore",            "beach");
        mirrorBiome("snowy_beach",            "beach");
        mirrorBiome("windswept_gravelly_hills", "taiga");       
        mirrorBiome("windswept_forest",       "taiga");
        mirrorBiome("windswept_hills",        "taiga");
        mirrorBiome("jagged_peaks",           "frozen_peaks");
        mirrorBiome("stony_peaks",            "frozen_peaks");
        mirrorBiome("grove",                  "snowy_slopes");




        
        
        
        putConstSeasons("nether_wastes",    new Range(45f,50f), new DailyRange(40f,45f,47f,52f));
        putConstSeasons("basalt_deltas",    new Range(50f,55f), new DailyRange(45f,50f,53f,60f));
        putConstSeasons("crimson_forest",   new Range(40f,45f), new DailyRange(35f,40f,43f,48f));
        putConstSeasons("warped_forest",    new Range(30f,40f), new DailyRange(25f,32f,35f,45f));
        putConstSeasons("soul_sand_valley", new Range(35f,45f), new DailyRange(30f,38f,42f,50f));

        
        
        
        putConstSeasons("the_end",           new Range(5f,10f),    new DailyRange(0f,7f,8f,12f));
        putConstSeasons("end_highlands",     new Range(5f,10f),    new DailyRange(0f,7f,8f,12f));
        putConstSeasons("end_midlands",      new Range(5f,10f),    new DailyRange(0f,7f,8f,12f));
        putConstSeasons("small_end_islands", new Range(0f,5f),     new DailyRange(-5f,2f,4f,8f));
        putConstSeasons("end_barrens",       new Range(0f,5f),     new DailyRange(-5f,2f,4f,8f));
        putConstSeasons("the_void",          new Range(-273f,-273f), new DailyRange(-273f,-273f,-273f,-273f));
    }

    /** Populates both SEASON_RANGES and SEASON_CLAMPS (automatically‐derived) */
    private static void putAllSeasons(String biomeKey, Range[] ranges) {
        var id = ResourceLocation.withDefaultNamespace(biomeKey);
        var seasons = Season.values();
        for (int i = 0; i < seasons.length; i++) {
            Season season = seasons[i];
            Range  r = ranges[i];
            SEASON_RANGES.get(season).put(id, r);
            SEASON_CLAMPS.get(season).put(id, deriveDaily(r));
        }
    }

    /** Copies one biome’s settings (all seasons) to another. */
    private static void mirrorBiome(String dstKey, String srcKey) {
        var src = ResourceLocation.withDefaultNamespace(srcKey);
        var dst = ResourceLocation.withDefaultNamespace(dstKey);
        for (Season s : Season.values()) {
            SEASON_RANGES.get(s).put(dst, SEASON_RANGES.get(s).get(src));
            SEASON_CLAMPS.get(s).put(dst, SEASON_CLAMPS.get(s).get(src));
        }
    }

    /** For biomes that never change with season (Nether, End). */
    private static void putConstSeasons(String biomeKey, Range r, DailyRange d) {
        var id = ResourceLocation.withDefaultNamespace(biomeKey);
        for (Season s : Season.values()) {
            SEASON_RANGES.get(s).put(id, r);
            SEASON_CLAMPS.get(s).put(id, d);
        }
    }

    /** Derives DailyRange from a min/max Range using a simple 10% buffer + quartiles. */
    private static DailyRange deriveDaily(Range r) {
        float span     = r.maxC - r.minC;
        float minMin   = r.minC - 0.10f * span;
        float avgNight = r.minC + 0.25f * span;
        float avgDay   = r.minC + 0.75f * span;
        float maxMax   = r.maxC + 0.10f * span;
        return new DailyRange(minMin, avgNight, avgDay, maxMax);
    }

    /** Retrieve the min/max °C for a given biome and season. */
    public static Range getRange(ResourceLocation biome, Season season) {
        if (!SEASON_RANGES.getOrDefault(season, Map.of()).containsKey(biome)) {
            ProjectAtmosphere.LOGGER.warn("❌ No temperature range defined for biome {}", biome);
        }
        return SEASON_RANGES.getOrDefault(season, Map.of())
                .getOrDefault(biome, new Range(0f,0f));
    }

    /** Retrieve the daily clamp for a given biome and season. */
    public static DailyRange getClamp(ResourceLocation biome, Season season) {
        return SEASON_CLAMPS.getOrDefault(season, Map.of())
                .getOrDefault(biome, new DailyRange(0f,0f,0f,0f));
    }
}
