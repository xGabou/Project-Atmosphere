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
    public enum Season {WINTER, SPRING, SUMMER, AUTUMN}

    /**
     * Simple min/max °C pair.
     */
    public static record Range(float minC, float maxC) {
    }

    /**
     * Daily clamp specifying:
     * - minMin: absolute minimum this season
     * - avgNight: typical low (night) this season
     * - avgDay: typical high (day) this season
     * - maxMax: absolute maximum this season
     */
    public static record DailyRange(float minMin, float avgNight, float avgDay, float maxMax) {
    }

    /**
     * season → (biome → Range)
     */
    public static final Map<Season, Map<ResourceLocation, Range>> SEASON_RANGES;
    /**
     * season → (biome → DailyRange)
     */
    public static final Map<Season, Map<ResourceLocation, DailyRange>> SEASON_CLAMPS;

    static {

        SEASON_RANGES = new EnumMap<>(Season.class);
        SEASON_CLAMPS = new EnumMap<>(Season.class);
        for (Season s : Season.values()) {
            SEASON_RANGES.put(s, new HashMap<>());
            SEASON_CLAMPS.put(s, new HashMap<>());
        }


        putAllSeasons("plains", new Range[]{
                new Range(-20f, 5f),
                new Range(-10f, 18f),
                new Range(15f, 36f),
                new Range(-6f, 18f)
        });
        mirrorBiome("sunflower_plains", "plains");

        putAllSeasons("forest", new Range[]{
                new Range(-18f, 5f),
                new Range(-7f, 13f),
                new Range(7f, 28f),
                new Range(-2f, 19f)
        });
        mirrorBiome("flower_forest", "forest");
        mirrorBiome("birch_forest", "forest");
        mirrorBiome("old_growth_birch_forest", "forest");

        putAllSeasons("dark_forest", new Range[]{
                new Range(-22f, 3f),
                new Range(-12f, 11f),
                new Range(8f, 24f),
                new Range(-9f, 14f)
        });
        putAllSeasons("beach", new Range[]{
                new Range(-2f, 8f),
                new Range(2f, 14f),
                new Range(15f, 30f),
                new Range(5f, 18f)
        });


        putAllSeasons("cherry_grove", new Range[]{
                new Range(-12f, 2f),
                new Range(-1f, 14f),
                new Range(8f, 28f),
                new Range(4f, 21f)
        });

        putAllSeasons("swamp", new Range[]{
                new Range(-5f, 10f),
                new Range(10f, 22f),
                new Range(20f, 35f),
                new Range(10f, 22f)
        });

        putAllSeasons("meadow", new Range[]{
                new Range(-12f, 3f),
                new Range(2f, 14f),
                new Range(15f, 26f),
                new Range(2f, 14f)
        });


        putAllSeasons("taiga", new Range[]{
                new Range(-25f, -5f),
                new Range(-5f, 10f),
                new Range(10f, 22f),
                new Range(-5f, 10f)
        });
        mirrorBiome("old_growth_spruce_taiga", "taiga");
        mirrorBiome("old_growth_pine_taiga", "taiga");
        mirrorBiome("snowy_taiga", "taiga");

        putAllSeasons("snowy_plains", new Range[]{
                new Range(-35f, -10f),
                new Range(-15f, 5f),
                new Range(5f, 15f),
                new Range(-15f, 5f)
        });

        putAllSeasons("ice_spikes", new Range[]{
                new Range(-50f, -30f),
                new Range(-30f, -5f),
                new Range(-5f, 5f),
                new Range(-30f, 0f)
        });
        mirrorBiome("snowy_slopes", "snowy_plains");

        putAllSeasons("frozen_peaks", new Range[]{
                new Range(-45f, -25f),
                new Range(-25f, -10f),
                new Range(-10f, 0f),
                new Range(-25f, -5f)
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
                new Range(5f, 20f),
                new Range(15f, 35f),
                new Range(30f, 45f),
                new Range(15f, 35f)
        });

        putAllSeasons("badlands", new Range[]{
                new Range(0f, 20f),
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
        mirrorBiome("mangrove_swamp", "swamp");


        putAllSeasons("ocean", new Range[]{
                new Range(0f, 10f),
                new Range(5f, 15f),
                new Range(10f, 20f),
                new Range(5f, 15f)
        });
        mirrorBiome("lukewarm_ocean", "ocean");
        mirrorBiome("warm_ocean", "ocean");
        mirrorBiome("cold_ocean", "ocean");
        mirrorBiome("frozen_ocean", "ocean");
        mirrorBiome("deep_ocean", "ocean");
        mirrorBiome("deep_lukewarm_ocean", "ocean");
        mirrorBiome("deep_warm_ocean", "ocean");
        mirrorBiome("deep_cold_ocean", "ocean");
        mirrorBiome("deep_frozen_ocean", "ocean");

        putAllSeasons("river", new Range[]{
                new Range(-5f, 5f),
                new Range(5f, 18f),
                new Range(18f, 30f),
                new Range(5f, 18f)
        });
        mirrorBiome("frozen_river", "river");

        putAllSeasons("mushroom_fields", new Range[]{
                new Range(5f, 15f),
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
                new Range(8f, 12f),
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
        mirrorBiome("savanna_plateau", "savanna");
        mirrorBiome("stony_shore", "beach");
        mirrorBiome("snowy_beach", "beach");
        mirrorBiome("windswept_gravelly_hills", "taiga");
        mirrorBiome("windswept_forest", "taiga");
        mirrorBiome("windswept_hills", "taiga");
        mirrorBiome("jagged_peaks", "frozen_peaks");
        mirrorBiome("stony_peaks", "frozen_peaks");
        mirrorBiome("grove", "snowy_slopes");


        putConstSeasons("nether_wastes", new Range(45f, 50f), new DailyRange(40f, 45f, 47f, 52f));
        putConstSeasons("basalt_deltas", new Range(50f, 55f), new DailyRange(45f, 50f, 53f, 60f));
        putConstSeasons("crimson_forest", new Range(40f, 45f), new DailyRange(35f, 40f, 43f, 48f));
        putConstSeasons("warped_forest", new Range(30f, 40f), new DailyRange(25f, 32f, 35f, 45f));
        putConstSeasons("soul_sand_valley", new Range(35f, 45f), new DailyRange(30f, 38f, 42f, 50f));


        putConstSeasons("the_end", new Range(5f, 10f), new DailyRange(0f, 7f, 8f, 12f));
        putConstSeasons("end_highlands", new Range(5f, 10f), new DailyRange(0f, 7f, 8f, 12f));
        putConstSeasons("end_midlands", new Range(5f, 10f), new DailyRange(0f, 7f, 8f, 12f));
        putConstSeasons("small_end_islands", new Range(0f, 5f), new DailyRange(-5f, 2f, 4f, 8f));
        putConstSeasons("end_barrens", new Range(0f, 5f), new DailyRange(-5f, 2f, 4f, 8f));
        putConstSeasons("the_void", new Range(-273f, -273f), new DailyRange(-273f, -273f, -273f, -273f));


        // Biomes O’ Plenty mapping estimé (températures basées sur le mod + ajustements réalistes)

// Exemple : Auroral Garden (Temperature = –0.25 dans le Wiki BOP) :contentReference[oaicite:5]{index=5}
        putAllSeasons("auroral_garden", new Range[]{
                new Range(-30f, 0f),   // hiver
                new Range(-10f, 10f),    // printemps
                new Range(5f, 20f),    // été
                new Range(-5f, 10f)     // automne
        });

        // Bamboo Grove (temp = 0.6) :contentReference[oaicite:6]{index=6}
        putAllSeasons("bamboo_grove", new Range[]{
                new Range(5f, 15f),
                new Range(10f, 25f),
                new Range(20f, 35f),
                new Range(10f, 25f)
        });

        // Bayou (temp = 0.95) :contentReference[oaicite:7]{index=7}
        putAllSeasons("bayou", new Range[]{
                new Range(10f, 25f),
                new Range(20f, 35f),
                new Range(25f, 40f),
                new Range(20f, 35f)
        });

        // Bog (temp = 0.2) :contentReference[oaicite:8]{index=8}
        putAllSeasons("bog", new Range[]{
                new Range(-5f, 10f),
                new Range(5f, 20f),
                new Range(15f, 30f),
                new Range(5f, 20f)
        });

        // Cold Desert (temp = 0.25) :contentReference[oaicite:9]{index=9}
        putAllSeasons("cold_desert", new Range[]{
                new Range(-20f, 5f),
                new Range(0f, 15f),
                new Range(10f, 25f),
                new Range(0f, 15f)
        });

        // Coniferous Forest (temp = 0.45) :contentReference[oaicite:10]{index=10}
        putAllSeasons("coniferous_forest", new Range[]{
                new Range(-5f, 10f),
                new Range(5f, 20f),
                new Range(15f, 30f),
                new Range(5f, 20f)
        });

        // Crag (temp = 0.6) :contentReference[oaicite:11]{index=11}
        putAllSeasons("crag", new Range[]{
                new Range(5f, 15f),
                new Range(10f, 25f),
                new Range(20f, 35f),
                new Range(10f, 25f)
        });

        // Dead Forest (temp = 0.2) :contentReference[oaicite:12]{index=12}
        putAllSeasons("dead_forest", new Range[]{
                new Range(-10f, 5f),
                new Range(0f, 15f),
                new Range(10f, 25f),
                new Range(0f, 15f)
        });

        // Dryland (temp = 0.85) :contentReference[oaicite:13]{index=13}
        putAllSeasons("dryland", new Range[]{
                new Range(10f, 25f),
                new Range(20f, 35f),
                new Range(30f, 45f),
                new Range(20f, 35f)
        });

        // Dune Beach (temp = 0.7) :contentReference[oaicite:14]{index=14}
        putAllSeasons("dune_beach", new Range[]{
                new Range(5f, 15f),
                new Range(10f, 25f),
                new Range(20f, 35f),
                new Range(10f, 25f)
        });

        // Field (temp = 0.4) :contentReference[oaicite:15]{index=15}
        putAllSeasons("field", new Range[]{
                new Range(0f, 15f),
                new Range(10f, 25f),
                new Range(20f, 35f),
                new Range(10f, 25f)
        });

        // Fir Clearing (temp = 0.45) :contentReference[oaicite:16]{index=16}
        putAllSeasons("fir_clearing", new Range[]{
                new Range(-5f, 10f),
                new Range(5f, 20f),
                new Range(15f, 30f),
                new Range(5f, 20f)
        });

        // Floodplain (temp = 1.2) :contentReference[oaicite:17]{index=17}
        putAllSeasons("floodplain", new Range[]{
                new Range(20f, 35f),
                new Range(30f, 45f),
                new Range(35f, 50f),
                new Range(30f, 45f)
        });

        // Forested Field (temp = 0.4) :contentReference[oaicite:18]{index=18}
        putAllSeasons("forested_field", new Range[]{
                new Range(0f, 15f),
                new Range(10f, 25f),
                new Range(20f, 35f),
                new Range(10f, 25f)
        });

        // Fungal Jungle (temp = 0.9) :contentReference[oaicite:19]{index=19}
        putAllSeasons("fungal_jungle", new Range[]{
                new Range(15f, 30f),
                new Range(25f, 40f),
                new Range(30f, 45f),
                new Range(25f, 40f)
        });

        // Grassland (temp = 0.6) :contentReference[oaicite:20]{index=20}
        putAllSeasons("grassland", new Range[]{
                new Range(5f, 20f),
                new Range(15f, 30f),
                new Range(25f, 40f),
                new Range(15f, 30f)
        });

        // Highland (temp = 0.6) :contentReference[oaicite:21]{index=21}
        putAllSeasons("highland", new Range[]{
                new Range(5f, 20f),
                new Range(15f, 30f),
                new Range(25f, 40f),
                new Range(15f, 30f)
        });

        // Highland Moor (temp = 0.6) :contentReference[oaicite:22]{index=22}
        putAllSeasons("highland_moor", new Range[]{
                new Range(5f, 20f),
                new Range(15f, 30f),
                new Range(25f, 40f),
                new Range(15f, 30f)
        });

        // Hot Springs (temp = 0.17) :contentReference[oaicite:23]{index=23}
        putAllSeasons("hot_springs", new Range[]{
                new Range(0f, 10f),
                new Range(5f, 20f),
                new Range(15f, 30f),
                new Range(5f, 20f)
        });

        // Jacaranda Glade (temp = 0.8) :contentReference[oaicite:24]{index=24}
        putAllSeasons("jacaranda_glade", new Range[]{
                new Range(10f, 25f),
                new Range(20f, 35f),
                new Range(25f, 40f),
                new Range(20f, 35f)
        });

        // Jade Cliffs (temp = 0.8) :contentReference[oaicite:25]{index=25}
        putAllSeasons("jade_cliffs", new Range[]{
                new Range(10f, 25f),
                new Range(20f, 35f),
                new Range(25f, 40f),
                new Range(20f, 35f)
        });

        // Lavender Field (temp = 0.8) :contentReference[oaicite:26]{index=26}
        putAllSeasons("lavender_field", new Range[]{
                new Range(10f, 25f),
                new Range(20f, 35f),
                new Range(25f, 40f),
                new Range(20f, 35f)
        });

        // Lush Desert (temp = 0.9) :contentReference[oaicite:27]{index=27}
        putAllSeasons("lush_desert", new Range[]{
                new Range(15f, 30f),
                new Range(25f, 40f),
                new Range(30f, 45f),
                new Range(25f, 40f)
        });

        // Lush Savanna (temp = 0.9) :contentReference[oaicite:28]{index=28}
        putAllSeasons("lush_savanna", new Range[]{
                new Range(15f, 30f),
                new Range(25f, 40f),
                new Range(30f, 45f),
                new Range(25f, 40f)
        });

        // Maple Woods (temp = 0.25) :contentReference[oaicite:29]{index=29}
        putAllSeasons("maple_woods", new Range[]{
                new Range(-10f, 5f),
                new Range(0f, 15f),
                new Range(10f, 25f),
                new Range(0f, 15f)
        });

        // Marsh (temp = 0.65) :contentReference[oaicite:30]{index=30}
        putAllSeasons("marsh", new Range[]{
                new Range(5f, 20f),
                new Range(15f, 30f),
                new Range(25f, 40f),
                new Range(15f, 30f)
        });

        // Mediterranean Forest (temp = 0.8) :contentReference[oaicite:31]{index=31}
        putAllSeasons("mediterranean_forest", new Range[]{
                new Range(10f, 25f),
                new Range(20f, 35f),
                new Range(25f, 40f),
                new Range(20f, 35f)
        });

        // Muskeg (temp = 0.0) :contentReference[oaicite:32]{index=32}
        putAllSeasons("muskeg", new Range[]{
                new Range(-20f, 5f),
                new Range(-5f, 15f),
                new Range(5f, 20f),
                new Range(-5f, 15f)
        });

        // Mystic Grove (temp = 0.7) :contentReference[oaicite:33]{index=33}
        putAllSeasons("mystic_grove", new Range[]{
                new Range(5f, 20f),
                new Range(15f, 30f),
                new Range(25f, 40f),
                new Range(15f, 30f)
        });

        // Old Growth Dead Forest (temp = 0.3) :contentReference[oaicite:34]{index=34}
        putAllSeasons("old_growth_dead_forest", new Range[]{
                new Range(-10f, 5f),
                new Range(0f, 15f),
                new Range(10f, 25f),
                new Range(0f, 15f)
        });

        // Old Growth Woodland (temp = 0.6) :contentReference[oaicite:35]{index=35}
        putAllSeasons("old_growth_woodland", new Range[]{
                new Range(5f, 20f),
                new Range(15f, 30f),
                new Range(25f, 40f),
                new Range(15f, 30f)
        });

        // Ominous Woods (temp = 0.6) :contentReference[oaicite:36]{index=36}
        putAllSeasons("ominous_woods", new Range[]{
                new Range(5f, 20f),
                new Range(15f, 30f),
                new Range(25f, 40f),
                new Range(15f, 30f)
        });

        // Orchard (temp = 0.8) :contentReference[oaicite:37]{index=37}
        putAllSeasons("orchard", new Range[]{
                new Range(10f, 25f),
                new Range(20f, 35f),
                new Range(25f, 40f),
                new Range(20f, 35f)
        });

        // Origin Valley (temp = 0.6) :contentReference[oaicite:38]{index=38}
        putAllSeasons("origin_valley", new Range[]{
                new Range(5f, 20f),
                new Range(15f, 30f),
                new Range(25f, 40f),
                new Range(15f, 30f)
        });

        // Pasture (temp = 0.8) :contentReference[oaicite:39]{index=39}
        putAllSeasons("pasture", new Range[]{
                new Range(10f, 25f),
                new Range(20f, 35f),
                new Range(25f, 40f),
                new Range(20f, 35f)
        });

        // Prairie (temp = 0.8) :contentReference[oaicite:40]{index=40}
        putAllSeasons("prairie", new Range[]{
                new Range(10f, 25f),
                new Range(20f, 35f),
                new Range(25f, 40f),
                new Range(20f, 35f)
        });

        // Pumpkin Patch (temp = 0.4) :contentReference[oaicite:41]{index=41}
        putAllSeasons("pumpkin_patch", new Range[]{
                new Range(0f, 15f),
                new Range(10f, 25f),
                new Range(20f, 35f),
                new Range(10f, 25f)
        });

        // Rainforest (temp = 1.2) :contentReference[oaicite:42]{index=42}
        putAllSeasons("rainforest", new Range[]{
                new Range(20f, 35f),
                new Range(30f, 45f),
                new Range(35f, 50f),
                new Range(30f, 45f)
        });

        // Redwood Forest (temperate) :contentReference[oaicite:43]{index=43}
        putAllSeasons("redwood_forest", new Range[]{
                new Range(5f, 20f),
                new Range(15f, 30f),
                new Range(25f, 40f),
                new Range(15f, 30f)
        });

        // Rocky Rainforest (warm) :contentReference[oaicite:44]{index=44}
        putAllSeasons("rocky_rainforest", new Range[]{
                new Range(15f, 30f),
                new Range(25f, 40f),
                new Range(30f, 45f),
                new Range(25f, 40f)
        });

        // Rocky Shrubland (temperate) :contentReference[oaicite:45]{index=45}
        putAllSeasons("rocky_shrubland", new Range[]{
                new Range(0f, 15f),
                new Range(10f, 25f),
                new Range(20f, 35f),
                new Range(10f, 25f)
        });

        // Scrubland (temperate) :contentReference[oaicite:46]{index=46}
        putAllSeasons("scrubland", new Range[]{
                new Range(0f, 15f),
                new Range(10f, 25f),
                new Range(20f, 35f),
                new Range(10f, 25f)
        });

        // Seasonal Forest (cold) :contentReference[oaicite:47]{index=47}
        putAllSeasons("seasonal_forest", new Range[]{
                new Range(-10f, 5f),
                new Range(0f, 15f),
                new Range(10f, 25f),
                new Range(0f, 15f)
        });

        // Shrubland (temperate) — déjà mappé comme “scrubland”, si tu veux alias
        mirrorBiome("shrubland", "scrubland");

        // Snowblossom Grove (icy) :contentReference[oaicite:48]{index=48}
        putAllSeasons("snowblossom_grove", new Range[]{
                new Range(-30f, 0f),
                new Range(-15f, 5f),
                new Range(0f, 15f),
                new Range(-15f, 5f)
        });

        // Snowy Coniferous Forest (icy) :contentReference[oaicite:49]{index=49}
        putAllSeasons("snowy_coniferous_forest", new Range[]{
                new Range(-30f, -5f),
                new Range(-15f, 5f),
                new Range(0f, 15f),
                new Range(-15f, 5f)
        });

        // Snowy Fir Clearing (icy) :contentReference[oaicite:50]{index=50}
        putAllSeasons("snowy_fir_clearing", new Range[]{
                new Range(-30f, -5f),
                new Range(-15f, 5f),
                new Range(0f, 15f),
                new Range(-15f, 5f)
        });

        // Snowy Maple Woods (icy) :contentReference[oaicite:51]{index=51}
        putAllSeasons("snowy_maple_woods", new Range[]{
                new Range(-30f, -5f),
                new Range(-15f, 5f),
                new Range(0f, 15f),
                new Range(-15f, 5f)
        });

        // Tropics (very rare, “warm”) :contentReference[oaicite:52]{index=52}
        putAllSeasons("tropics", new Range[]{
                new Range(20f, 35f),
                new Range(30f, 45f),
                new Range(35f, 50f),
                new Range(30f, 45f)
        });

        // Tundra (cold) :contentReference[oaicite:53]{index=53}
        putAllSeasons("tundra", new Range[]{
                new Range(-35f, -5f),
                new Range(-20f, 5f),
                new Range(0f, 15f),
                new Range(-20f, 5f)
        });

        // Volcanic Plains (warm) :contentReference[oaicite:54]{index=54}
        putAllSeasons("volcanic_plains", new Range[]{
                new Range(15f, 30f),
                new Range(25f, 40f),
                new Range(30f, 45f),
                new Range(25f, 40f)
        });

        // Volcano (very rare, hot) :contentReference[oaicite:55]{index=55}
        putAllSeasons("volcano", new Range[]{
                new Range(25f, 40f),
                new Range(35f, 50f),
                new Range(40f, 60f),
                new Range(35f, 50f)
        });

        // Wasteland (warm / dry) :contentReference[oaicite:56]{index=56}
        putAllSeasons("wasteland", new Range[]{
                new Range(10f, 25f),
                new Range(20f, 35f),
                new Range(25f, 40f),
                new Range(20f, 35f)
        });

        // Wetland (temperate / humid) :contentReference[oaicite:57]{index=57}
        putAllSeasons("wetland", new Range[]{
                new Range(5f, 20f),
                new Range(15f, 30f),
                new Range(25f, 40f),
                new Range(15f, 30f)
        });

        // Woodland (temperate) :contentReference[oaicite:58]{index=58}
        putAllSeasons("woodland", new Range[]{
                new Range(0f, 15f),
                new Range(10f, 25f),
                new Range(20f, 35f),
                new Range(10f, 25f)
        });


        // Atmospheric biomes (realistic °C mapping)

        putAllSeasons("aspen_parkland", new Range[]{
                new Range(-15f, 5f),     // Winter – continental cold
                new Range(0f, 18f),      // Spring – mild
                new Range(15f, 30f),     // Summer – warm continental
                new Range(0f, 15f)       // Autumn – cooling
        });

        putAllSeasons("dunes", new Range[]{
                new Range(10f, 22f),     // Winter – mild desert nights
                new Range(18f, 32f),     // Spring – hot days
                new Range(28f, 45f),     // Summer – extreme heat
                new Range(18f, 32f)      // Autumn – warm
        });

        putAllSeasons("flourishing_dunes", new Range[]{
                new Range(12f, 25f),     // Winter – mild
                new Range(20f, 35f),     // Spring – warm
                new Range(28f, 42f),     // Summer – humid heat
                new Range(20f, 32f)      // Autumn – warm moderate
        });

        putAllSeasons("kousa_jungle", new Range[]{
                new Range(-10f, 2f),     // Winter – snow on ground
                new Range(0f, 15f),      // Spring – mild thaw
                new Range(15f, 25f),     // Summer – cool jungle
                new Range(0f, 12f)       // Autumn – chilly
        });

        putAllSeasons("laurel_forest", new Range[]{
                new Range(-2f, 8f),      // Winter – temperate forest
                new Range(5f, 18f),      // Spring
                new Range(15f, 30f),     // Summer
                new Range(5f, 18f)       // Autumn
        });

        putAllSeasons("petrified_dunes", new Range[]{
                new Range(8f, 22f),      // Winter
                new Range(18f, 32f),     // Spring
                new Range(28f, 45f),     // Summer
                new Range(18f, 32f)      // Autumn
        });

        putAllSeasons("rainforest", new Range[]{
                new Range(20f, 28f),     // Winter – constant warmth
                new Range(22f, 32f),     // Spring – humid
                new Range(25f, 36f),     // Summer – very humid
                new Range(22f, 32f)      // Autumn – stable warm
        });

        putAllSeasons("rainforest_basin", new Range[]{
                new Range(21f, 29f),
                new Range(23f, 33f),
                new Range(26f, 36f),
                new Range(23f, 33f)
        });

        putAllSeasons("rocky_dunes", new Range[]{
                new Range(10f, 22f),
                new Range(18f, 32f),
                new Range(28f, 43f),
                new Range(18f, 32f)
        });

        putAllSeasons("scrubland", new Range[]{
                new Range(5f, 18f),      // Winter – semi-arid cool
                new Range(15f, 28f),     // Spring
                new Range(25f, 38f),     // Summer
                new Range(15f, 28f)      // Autumn
        });

        putAllSeasons("snowy_scrubland", new Range[]{
                new Range(-25f, -5f),    // Winter – snow cover
                new Range(-10f, 8f),     // Spring – melting
                new Range(5f, 18f),      // Summer – mild
                new Range(-5f, 8f)       // Autumn – cooling again
        });

        putAllSeasons("sparse_rainforest", new Range[]{
                new Range(18f, 25f),
                new Range(20f, 30f),
                new Range(24f, 35f),
                new Range(20f, 30f)
        });

        putAllSeasons("sparse_rainforest_basin", new Range[]{
                new Range(18f, 26f),
                new Range(21f, 31f),
                new Range(25f, 36f),
                new Range(21f, 31f)
        });

        putAllSeasons("spiny_thicket", new Range[]{
                new Range(10f, 25f),     // Winter – warm
                new Range(20f, 35f),     // Spring – arid heat
                new Range(30f, 45f),     // Summer – harsh desert
                new Range(20f, 35f)      // Autumn – still hot
        });

        // ===== STILL LIFE and related biomes (realistic °C mapping) =====

        putAllSeasons("alpine_heathlands", new Range[]{
                new Range(-12f, 2f),    // winter
                new Range(-2f, 12f),    // spring
                new Range(6f, 20f),     // summer
                new Range(-2f, 10f)     // autumn
        });

        putAllSeasons("alpine_plains", new Range[]{
                new Range(-12f, 2f),
                new Range(-2f, 12f),
                new Range(6f, 20f),
                new Range(-2f, 10f)
        });

        putAllSeasons("alpine_tundra", new Range[]{
                new Range(-18f, -5f),
                new Range(-8f, 6f),
                new Range(2f, 14f),
                new Range(-8f, 4f)
        });

        putAllSeasons("arctic_beach", new Range[]{
                new Range(-18f, -2f),
                new Range(-8f, 6f),
                new Range(2f, 12f),
                new Range(-10f, 2f)
        });

        putAllSeasons("arctic_deep_ocean", new Range[]{
                new Range(-4f, 2f),
                new Range(0f, 6f),
                new Range(4f, 10f),
                new Range(0f, 6f)
        });

        putAllSeasons("arctic_desert", new Range[]{
                new Range(-35f, -10f),
                new Range(-20f, 0f),
                new Range(-5f, 10f),
                new Range(-20f, -2f)
        });

        putAllSeasons("arctic_desert_basin", new Range[]{
                new Range(-20f, -5f),
                new Range(-10f, 6f),
                new Range(2f, 14f),
                new Range(-8f, 4f)
        });

        putAllSeasons("arctic_glacier", new Range[]{
                new Range(-45f, -25f),
                new Range(-30f, -10f),
                new Range(-12f, 2f),
                new Range(-28f, -8f)
        });

        putAllSeasons("arctic_shallow_ocean", new Range[]{
                new Range(-3f, 2f),
                new Range(0f, 7f),
                new Range(5f, 12f),
                new Range(0f, 6f)
        });

        putAllSeasons("arctic_wetlands", new Range[]{
                new Range(-15f, -2f),
                new Range(-5f, 10f),
                new Range(8f, 18f),
                new Range(-2f, 8f)
        });

        putAllSeasons("arid_beach", new Range[]{
                new Range(12f, 22f),
                new Range(18f, 30f),
                new Range(24f, 38f),
                new Range(18f, 28f)
        });

        putAllSeasons("arid_desert", new Range[]{
                new Range(10f, 22f),
                new Range(20f, 34f),
                new Range(28f, 45f),
                new Range(18f, 32f)
        });

        putAllSeasons("arid_highlands", new Range[]{
                new Range(8f, 20f),
                new Range(16f, 30f),
                new Range(24f, 40f),
                new Range(14f, 28f)
        });

        putAllSeasons("barren_arctic_desert", new Range[]{
                new Range(-40f, -20f),
                new Range(-28f, -8f),
                new Range(-12f, 2f),
                new Range(-28f, -8f)
        });

        putAllSeasons("barren_caves", new Range[]{
                new Range(6f, 10f),
                new Range(8f, 12f),
                new Range(10f, 14f),
                new Range(8f, 12f)
        });

        putAllSeasons("barren_mediterranean_highlands", new Range[]{
                new Range(5f, 15f),
                new Range(10f, 22f),
                new Range(18f, 32f),
                new Range(10f, 20f)
        });

        putAllSeasons("barren_peaks", new Range[]{
                new Range(-8f, 2f),
                new Range(0f, 12f),
                new Range(6f, 20f),
                new Range(-2f, 10f)
        });

        putAllSeasons("birch_forest", new Range[]{
                new Range(-6f, 8f),
                new Range(4f, 18f),
                new Range(14f, 28f),
                new Range(4f, 16f)
        });

        putAllSeasons("bog", new Range[]{
                new Range(-4f, 6f),
                new Range(4f, 16f),
                new Range(12f, 24f),
                new Range(4f, 14f)
        });

        putAllSeasons("boreal_alpine_tundra", new Range[]{
                new Range(-20f, -6f),
                new Range(-8f, 6f),
                new Range(4f, 16f),
                new Range(-6f, 6f)
        });

        putAllSeasons("boreal_river", new Range[]{
                new Range(-8f, 2f),
                new Range(2f, 12f),
                new Range(10f, 20f),
                new Range(0f, 10f)
        });

        putAllSeasons("cold_barren_highlands", new Range[]{
                new Range(-10f, 0f),
                new Range(-2f, 12f),
                new Range(6f, 20f),
                new Range(-2f, 10f)
        });

        putAllSeasons("cold_deep_ocean", new Range[]{
                new Range(0f, 6f),
                new Range(4f, 10f),
                new Range(8f, 14f),
                new Range(4f, 10f)
        });

        putAllSeasons("cold_desert", new Range[]{
                new Range(-6f, 10f),
                new Range(6f, 20f),
                new Range(16f, 30f),
                new Range(6f, 20f)
        });

        putAllSeasons("cold_semi_desert", new Range[]{
                new Range(-4f, 12f),
                new Range(6f, 22f),
                new Range(16f, 32f),
                new Range(6f, 22f)
        });

        putAllSeasons("cold_shallow_ocean", new Range[]{
                new Range(1f, 7f),
                new Range(5f, 12f),
                new Range(9f, 15f),
                new Range(5f, 11f)
        });

        putAllSeasons("cold_steppe", new Range[]{
                new Range(-10f, 2f),
                new Range(2f, 16f),
                new Range(12f, 26f),
                new Range(0f, 14f)
        });

        putAllSeasons("cold_taiga", new Range[]{
                new Range(-25f, -5f),
                new Range(-5f, 10f),
                new Range(10f, 20f),
                new Range(-5f, 8f)
        });

        putAllSeasons("cold_taiga_clearings", new Range[]{
                new Range(-24f, -4f),
                new Range(-4f, 12f),
                new Range(10f, 22f),
                new Range(-4f, 10f)
        });

        putAllSeasons("cold_tundra_beach", new Range[]{
                new Range(-14f, -2f),
                new Range(-4f, 8f),
                new Range(4f, 14f),
                new Range(-6f, 4f)
        });

        putAllSeasons("desert_river", new Range[]{
                new Range(12f, 22f),
                new Range(18f, 32f),
                new Range(26f, 42f),
                new Range(18f, 30f)
        });

        putAllSeasons("desert_wetlands", new Range[]{
                new Range(14f, 24f),
                new Range(20f, 34f),
                new Range(26f, 40f),
                new Range(20f, 32f)
        });

        putAllSeasons("dry_cold_steppe", new Range[]{
                new Range(-12f, 0f),
                new Range(0f, 14f),
                new Range(10f, 24f),
                new Range(-2f, 12f)
        });

        putAllSeasons("dry_tropical_highlands", new Range[]{
                new Range(10f, 20f),
                new Range(16f, 28f),
                new Range(20f, 34f),
                new Range(14f, 26f)
        });

        putAllSeasons("evergreen_taiga", new Range[]{
                new Range(-22f, -4f),
                new Range(-4f, 12f),
                new Range(10f, 22f),
                new Range(-4f, 10f)
        });

        putAllSeasons("fen", new Range[]{
                new Range(0f, 10f),
                new Range(6f, 20f),
                new Range(14f, 28f),
                new Range(6f, 18f)
        });

        putAllSeasons("flooded_grasslands", new Range[]{
                new Range(6f, 16f),
                new Range(12f, 24f),
                new Range(18f, 30f),
                new Range(10f, 22f)
        });

        putAllSeasons("flooded_savanna", new Range[]{
                new Range(16f, 26f),
                new Range(22f, 34f),
                new Range(26f, 38f),
                new Range(20f, 32f)
        });

        putAllSeasons("frozen_caves", new Range[]{
                new Range(-2f, 2f),
                new Range(0f, 4f),
                new Range(2f, 6f),
                new Range(0f, 4f)
        });

        putAllSeasons("glacial_basin", new Range[]{
                new Range(-42f, -22f),
                new Range(-28f, -8f),
                new Range(-10f, 4f),
                new Range(-26f, -6f)
        });

        putAllSeasons("glowing_caves", new Range[]{
                new Range(8f, 12f),
                new Range(10f, 14f),
                new Range(12f, 16f),
                new Range(10f, 14f)
        });

        putAllSeasons("grassland_savanna", new Range[]{
                new Range(14f, 24f),
                new Range(20f, 32f),
                new Range(26f, 38f),
                new Range(18f, 30f)
        });

        putAllSeasons("haunted_depths", new Range[]{
                new Range(7f, 11f),
                new Range(9f, 13f),
                new Range(11f, 15f),
                new Range(9f, 13f)
        });

        putAllSeasons("humid_savanna", new Range[]{
                new Range(18f, 26f),
                new Range(22f, 34f),
                new Range(26f, 38f),
                new Range(22f, 32f)
        });

        putAllSeasons("infested_tunnels", new Range[]{
                new Range(7f, 11f),
                new Range(9f, 13f),
                new Range(11f, 15f),
                new Range(9f, 13f)
        });

        putAllSeasons("larch_woodlands", new Range[]{
                new Range(-8f, 6f),
                new Range(2f, 16f),
                new Range(12f, 26f),
                new Range(2f, 14f)
        });

        putAllSeasons("mangrove_marsh", new Range[]{
                new Range(16f, 24f),
                new Range(20f, 30f),
                new Range(24f, 34f),
                new Range(20f, 28f)
        });

        putAllSeasons("mediterranean_beach", new Range[]{
                new Range(8f, 16f),
                new Range(14f, 24f),
                new Range(20f, 30f),
                new Range(14f, 24f)
        });

        putAllSeasons("mediterranean_forest", new Range[]{
                new Range(4f, 14f),
                new Range(10f, 22f),
                new Range(20f, 34f),
                new Range(10f, 22f)
        });

        putAllSeasons("mediterranean_highlands", new Range[]{
                new Range(2f, 12f),
                new Range(8f, 20f),
                new Range(16f, 30f),
                new Range(8f, 20f)
        });

        putAllSeasons("mediterranean_marsh", new Range[]{
                new Range(8f, 18f),
                new Range(14f, 26f),
                new Range(20f, 32f),
                new Range(12f, 24f)
        });

        putAllSeasons("mediterranean_river", new Range[]{
                new Range(8f, 16f),
                new Range(14f, 24f),
                new Range(20f, 30f),
                new Range(14f, 22f)
        });

        putAllSeasons("mediterranean_shrubland", new Range[]{
                new Range(6f, 16f),
                new Range(12f, 24f),
                new Range(20f, 34f),
                new Range(10f, 22f)
        });

        putAllSeasons("mediterranean_steppe", new Range[]{
                new Range(4f, 16f),
                new Range(12f, 26f),
                new Range(20f, 36f),
                new Range(10f, 24f)
        });

        putAllSeasons("mediterranean_swamp", new Range[]{
                new Range(8f, 18f),
                new Range(14f, 26f),
                new Range(20f, 32f),
                new Range(12f, 24f)
        });

        putAllSeasons("mediterranean_wooded_highlands", new Range[]{
                new Range(2f, 12f),
                new Range(8f, 20f),
                new Range(16f, 30f),
                new Range(8f, 20f)
        });

        putAllSeasons("mire", new Range[]{
                new Range(0f, 10f),
                new Range(8f, 20f),
                new Range(14f, 26f),
                new Range(6f, 18f)
        });

        putAllSeasons("mixed_forest_steppe", new Range[]{
                new Range(-6f, 6f),
                new Range(4f, 18f),
                new Range(14f, 28f),
                new Range(4f, 16f)
        });

        putAllSeasons("monsoon_forest", new Range[]{
                new Range(18f, 26f),
                new Range(22f, 34f),
                new Range(24f, 36f),
                new Range(20f, 32f)
        });

        putAllSeasons("mushroom_caves", new Range[]{
                new Range(10f, 12f),
                new Range(12f, 14f),
                new Range(14f, 16f),
                new Range(12f, 14f)
        });

        putAllSeasons("old_growth_temperate_forest", new Range[]{
                new Range(-4f, 8f),
                new Range(6f, 18f),
                new Range(14f, 28f),
                new Range(6f, 18f)
        });

        putAllSeasons("old_growth_tropical_rainforest", new Range[]{
                new Range(20f, 26f),
                new Range(22f, 32f),
                new Range(24f, 34f),
                new Range(22f, 30f)
        });

        putAllSeasons("pine_woods", new Range[]{
                new Range(-10f, 4f),
                new Range(2f, 16f),
                new Range(12f, 26f),
                new Range(2f, 14f)
        });

        putAllSeasons("savanna_beach", new Range[]{
                new Range(16f, 24f),
                new Range(20f, 30f),
                new Range(24f, 34f),
                new Range(20f, 28f)
        });

        putAllSeasons("savanna_highlands", new Range[]{
                new Range(14f, 24f),
                new Range(20f, 32f),
                new Range(26f, 38f),
                new Range(18f, 30f)
        });

        putAllSeasons("savanna_river", new Range[]{
                new Range(16f, 24f),
                new Range(22f, 32f),
                new Range(26f, 36f),
                new Range(20f, 30f)
        });

        putAllSeasons("scorched_caves", new Range[]{
                new Range(18f, 22f),
                new Range(20f, 24f),
                new Range(22f, 28f),
                new Range(20f, 24f)
        });

        putAllSeasons("semiarid_desert", new Range[]{
                new Range(8f, 22f),
                new Range(18f, 32f),
                new Range(26f, 42f),
                new Range(16f, 30f)
        });

        putAllSeasons("shrub_steppe", new Range[]{
                new Range(2f, 14f),
                new Range(10f, 24f),
                new Range(18f, 32f),
                new Range(8f, 22f)
        });

        putAllSeasons("snowy_barren_peaks", new Range[]{
                new Range(-22f, -6f),
                new Range(-10f, 4f),
                new Range(0f, 12f),
                new Range(-10f, 2f)
        });

        putAllSeasons("snowy_peaks", new Range[]{
                new Range(-20f, -5f),
                new Range(-8f, 6f),
                new Range(2f, 14f),
                new Range(-8f, 4f)
        });

        putAllSeasons("snowy_tundra", new Range[]{
                new Range(-28f, -8f),
                new Range(-12f, 4f),
                new Range(2f, 14f),
                new Range(-12f, 2f)
        });

        putAllSeasons("steppe_river", new Range[]{
                new Range(0f, 10f),
                new Range(10f, 22f),
                new Range(18f, 30f),
                new Range(8f, 20f)
        });

        putAllSeasons("subtropical_deep_ocean", new Range[]{
                new Range(16f, 22f),
                new Range(20f, 26f),
                new Range(24f, 30f),
                new Range(20f, 26f)
        });

        putAllSeasons("subtropical_shallow_ocean", new Range[]{
                new Range(18f, 24f),
                new Range(22f, 28f),
                new Range(26f, 32f),
                new Range(22f, 28f)
        });

        putAllSeasons("taiga_beach", new Range[]{
                new Range(-10f, 2f),
                new Range(0f, 12f),
                new Range(8f, 20f),
                new Range(0f, 10f)
        });

        putAllSeasons("taiga_highlands", new Range[]{
                new Range(-16f, -2f),
                new Range(-4f, 10f),
                new Range(8f, 20f),
                new Range(-4f, 8f)
        });

        putAllSeasons("temperate_beach", new Range[]{
                new Range(4f, 12f),
                new Range(10f, 20f),
                new Range(16f, 26f),
                new Range(10f, 18f)
        });

        putAllSeasons("temperate_deep_ocean", new Range[]{
                new Range(10f, 14f),
                new Range(12f, 16f),
                new Range(14f, 18f),
                new Range(12f, 16f)
        });

        putAllSeasons("temperate_forest", new Range[]{
                new Range(-4f, 8f),
                new Range(6f, 18f),
                new Range(14f, 28f),
                new Range(6f, 18f)
        });

        putAllSeasons("temperate_marsh", new Range[]{
                new Range(0f, 10f),
                new Range(8f, 20f),
                new Range(14f, 26f),
                new Range(6f, 18f)
        });

        putAllSeasons("temperate_mixed_forest", new Range[]{
                new Range(-6f, 8f),
                new Range(6f, 18f),
                new Range(14f, 28f),
                new Range(6f, 18f)
        });

        putAllSeasons("temperate_river", new Range[]{
                new Range(0f, 10f),
                new Range(8f, 20f),
                new Range(14f, 26f),
                new Range(6f, 18f)
        });

        putAllSeasons("temperate_shallow_ocean", new Range[]{
                new Range(12f, 16f),
                new Range(14f, 18f),
                new Range(16f, 20f),
                new Range(14f, 18f)
        });

        putAllSeasons("temperate_swamp", new Range[]{
                new Range(4f, 12f),
                new Range(10f, 22f),
                new Range(16f, 28f),
                new Range(10f, 20f)
        });

        putAllSeasons("tropical_beach", new Range[]{
                new Range(20f, 26f),
                new Range(24f, 30f),
                new Range(26f, 32f),
                new Range(24f, 30f)
        });

        putAllSeasons("tropical_deep_ocean", new Range[]{
                new Range(18f, 24f),
                new Range(22f, 28f),
                new Range(26f, 30f),
                new Range(22f, 28f)
        });

        putAllSeasons("tropical_dry_forest", new Range[]{
                new Range(18f, 26f),
                new Range(22f, 34f),
                new Range(24f, 36f),
                new Range(20f, 32f)
        });

        putAllSeasons("tropical_highlands", new Range[]{
                new Range(14f, 22f),
                new Range(18f, 28f),
                new Range(20f, 30f),
                new Range(16f, 26f)
        });

        putAllSeasons("tropical_rainforest", new Range[]{
                new Range(22f, 28f),
                new Range(24f, 32f),
                new Range(24f, 34f),
                new Range(24f, 30f)
        });

        putAllSeasons("tropical_rainforest_river", new Range[]{
                new Range(22f, 28f),
                new Range(24f, 32f),
                new Range(24f, 34f),
                new Range(24f, 30f)
        });

        putAllSeasons("tropical_shallow_ocean", new Range[]{
                new Range(22f, 26f),
                new Range(24f, 30f),
                new Range(26f, 32f),
                new Range(24f, 30f)
        });

        putAllSeasons("tropical_wetlands", new Range[]{
                new Range(22f, 28f),
                new Range(24f, 32f),
                new Range(24f, 34f),
                new Range(24f, 30f)
        });

        putAllSeasons("tundra_beach", new Range[]{
                new Range(-10f, -2f),
                new Range(-4f, 8f),
                new Range(4f, 14f),
                new Range(-6f, 4f)
        });

        putAllSeasons("warm_temperate_clearings", new Range[]{
                new Range(2f, 12f),
                new Range(10f, 22f),
                new Range(18f, 32f),
                new Range(8f, 20f)
        });

        putAllSeasons("warm_temperate_river", new Range[]{
                new Range(4f, 12f),
                new Range(10f, 22f),
                new Range(18f, 30f),
                new Range(10f, 20f)
        });

        putAllSeasons("warm_temperate_woodlands", new Range[]{
                new Range(2f, 12f),
                new Range(10f, 22f),
                new Range(18f, 32f),
                new Range(8f, 20f)
        });

        putAllSeasons("wooded_highlands", new Range[]{
                new Range(-6f, 6f),
                new Range(4f, 18f),
                new Range(12f, 26f),
                new Range(4f, 16f)
        });

        putAllSeasons("wooded_mediterranean_steppe", new Range[]{
                new Range(6f, 16f),
                new Range(12f, 26f),
                new Range(20f, 36f),
                new Range(10f, 24f)
        });

        putAllSeasons("wooded_savanna", new Range[]{
                new Range(16f, 26f),
                new Range(22f, 34f),
                new Range(26f, 38f),
                new Range(20f, 32f)
        });

        putAllSeasons("wooded_semiarid_desert", new Range[]{
                new Range(8f, 22f),
                new Range(18f, 32f),
                new Range(26f, 42f),
                new Range(16f, 30f)
        });

        putAllSeasons("xeric_shrubland", new Range[]{
                new Range(8f, 22f),
                new Range(18f, 32f),
                new Range(26f, 42f),
                new Range(16f, 30f)
        });

        // ===== Tundra and Arctic biome mappings (realistic °C) =====

        putAllSeasons("arctic_river", new Range[]{
                new Range(-18f, -2f),  // Winter — frozen river surfaces
                new Range(-8f, 6f),    // Spring — partial thaw
                new Range(4f, 14f),    // Summer — active flow
                new Range(-6f, 4f)     // Autumn — refreezing onset
        });

        putAllSeasons("arctic_tundra", new Range[]{
                new Range(-40f, -20f), // Winter — extreme cold
                new Range(-25f, -5f),  // Spring — thaw begins
                new Range(-5f, 12f),   // Summer — short warm season
                new Range(-20f, -4f)   // Autumn — rapid cooling
        });

        putAllSeasons("barren_tundra", new Range[]{
                new Range(-35f, -15f), // Winter — dry polar
                new Range(-22f, -2f),  // Spring — transition
                new Range(-2f, 10f),   // Summer — limited warmth
                new Range(-18f, -4f)   // Autumn — return to frost
        });

        putAllSeasons("lush_tundra", new Range[]{
                new Range(-28f, -10f), // Winter — moderate tundra cold
                new Range(-10f, 4f),   // Spring — thaw and moss growth
                new Range(2f, 14f),    // Summer — lush greenery
                new Range(-8f, 2f)     // Autumn — frost returns
        });

        putAllSeasons("snowy_boreal_alpine_tundra", new Range[]{
                new Range(-30f, -12f), // Winter — alpine snowpack
                new Range(-14f, 2f),   // Spring — meltwater
                new Range(4f, 16f),    // Summer — mild highland climate
                new Range(-10f, 2f)    // Autumn — early snow onset
        });

        putAllSeasons("tundra_river", new Range[]{
                new Range(-20f, -5f),  // Winter — ice-locked river
                new Range(-10f, 4f),   // Spring — partial thaw
                new Range(4f, 14f),    // Summer — flowing season
                new Range(-8f, 2f)     // Autumn — refreezing
        });

        putAllSeasons("wooded_tundra", new Range[]{
                new Range(-26f, -8f),  // Winter — snowy forest tundra
                new Range(-10f, 6f),   // Spring — low forest melt
                new Range(6f, 18f),    // Summer — short but mild
                new Range(-6f, 4f)     // Autumn — cooling transition
        });



    }

    /**
     * Populates both SEASON_RANGES and SEASON_CLAMPS (automatically‐derived)
     */
    private static void putAllSeasons(String biomeKey, Range[] ranges) {
        var id = ResourceLocation.withDefaultNamespace(biomeKey);
        var seasons = Season.values();
        for (int i = 0; i < seasons.length; i++) {
            Season season = seasons[i];
            Range r = ranges[i];
            SEASON_RANGES.get(season).put(id, r);
            SEASON_CLAMPS.get(season).put(id, deriveDaily(r));
        }
    }

    /**
     * Copies one biome’s settings (all seasons) to another.
     */
    private static void mirrorBiome(String dstKey, String srcKey) {
        var src = ResourceLocation.withDefaultNamespace(srcKey);
        var dst = ResourceLocation.withDefaultNamespace(dstKey);
        for (Season s : Season.values()) {
            SEASON_RANGES.get(s).put(dst, SEASON_RANGES.get(s).get(src));
            SEASON_CLAMPS.get(s).put(dst, SEASON_CLAMPS.get(s).get(src));
        }
    }

    /**
     * For biomes that never change with season (Nether, End).
     */
    private static void putConstSeasons(String biomeKey, Range r, DailyRange d) {
        var id = ResourceLocation.withDefaultNamespace(biomeKey);
        for (Season s : Season.values()) {
            SEASON_RANGES.get(s).put(id, r);
            SEASON_CLAMPS.get(s).put(id, d);
        }
    }

    /**
     * Derives DailyRange from a min/max Range using a simple 10% buffer + quartiles.
     */
    public static DailyRange deriveDaily(Range r) {
        float span = r.maxC - r.minC;
        float minMin = r.minC - 0.10f * span;
        float avgNight = r.minC + 0.25f * span;
        float avgDay = r.minC + 0.75f * span;
        float maxMax = r.maxC + 0.10f * span;
        return new DailyRange(minMin, avgNight, avgDay, maxMax);
    }

    /**
     * Retrieve the min/max °C for a given biome and season.
     */
    public static Range getRange(ResourceLocation biome, Season season) {
        if (!SEASON_RANGES.getOrDefault(season, Map.of()).containsKey(biome)) {
            ProjectAtmosphere.LOGGER.warn("❌ No temperature range defined for biome {}", biome);
        }
        return SEASON_RANGES.getOrDefault(season, Map.of())
                .getOrDefault(biome, new Range(0f, 0f));
    }

    /**
     * Retrieve the daily clamp for a given biome and season.
     */
    public static DailyRange getClamp(ResourceLocation biome, Season season) {
        return SEASON_CLAMPS.getOrDefault(season, Map.of())
                .getOrDefault(biome, new DailyRange(0f, 0f, 0f, 0f));
    }
}
