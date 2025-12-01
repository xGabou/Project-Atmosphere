package net.Gabou.projectatmosphere.modules.temperature.config;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.EnumMap;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.LinkedHashSet;

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
        putAllSeasons("alexscaves:candy_cavity", new Range[]{
                new Range(18f, 25f),
                new Range(20f, 30f),
                new Range(18f, 26f),
                new Range(17f, 24f)
        });

        putAllSeasons("ars_nouveau:archwood_forest", new Range[]{
                new Range(8f, 16f),
                new Range(18f, 26f),
                new Range(10f, 18f),
                new Range(0f, 8f)
        });

        putAllSeasons("atmospheric:aspen_parkland", new Range[]{
                new Range(5f, 15f),
                new Range(18f, 28f),
                new Range(8f, 18f),
                new Range(-5f, 5f)
        });

        putAllSeasons("atmospheric:dunes", new Range[]{
                new Range(15f, 35f),
                new Range(25f, 45f),
                new Range(18f, 32f),
                new Range(8f, 22f)
        });

        putAllSeasons("atmospheric:flourishing_dunes", new Range[]{
                new Range(16f, 33f),
                new Range(26f, 44f),
                new Range(20f, 34f),
                new Range(10f, 25f)
        });

        putAllSeasons("atmospheric:kousa_jungle", new Range[]{
                new Range(20f, 30f),
                new Range(24f, 35f),
                new Range(22f, 32f),
                new Range(20f, 30f)
        });

        putAllSeasons("atmospheric:laurel_forest", new Range[]{
                new Range(12f, 20f),
                new Range(18f, 28f),
                new Range(14f, 22f),
                new Range(8f, 15f)
        });

        putAllSeasons("atmospheric:petrified_dunes", new Range[]{
                new Range(12f, 32f),
                new Range(22f, 44f),
                new Range(16f, 34f),
                new Range(6f, 20f)
        });

        putAllSeasons("atmospheric:rainforest", new Range[]{
                new Range(23f, 32f),
                new Range(25f, 34f),
                new Range(24f, 33f),
                new Range(22f, 31f)
        });

        putAllSeasons("atmospheric:rainforest_basin", new Range[]{
                new Range(24f, 33f),
                new Range(25f, 35f),
                new Range(24f, 33f),
                new Range(23f, 32f)
        });

        putAllSeasons("atmospheric:rocky_dunes", new Range[]{
                new Range(14f, 30f),
                new Range(25f, 43f),
                new Range(18f, 32f),
                new Range(8f, 20f)
        });

        putAllSeasons("atmospheric:scrubland", new Range[]{
                new Range(10f, 25f),
                new Range(18f, 33f),
                new Range(12f, 26f),
                new Range(5f, 18f)
        });

        putAllSeasons("atmospheric:snowy_scrubland", new Range[]{
                new Range(2f, 12f),
                new Range(10f, 20f),
                new Range(5f, 15f),
                new Range(-10f, 2f)
        });

        putAllSeasons("atmospheric:sparse_rainforest", new Range[]{
                new Range(20f, 30f),
                new Range(24f, 34f),
                new Range(22f, 32f),
                new Range(20f, 30f)
        });

        putAllSeasons("atmospheric:sparse_rainforest_basin", new Range[]{
                new Range(21f, 31f),
                new Range(25f, 34f),
                new Range(23f, 32f),
                new Range(21f, 30f)
        });

        putAllSeasons("atmospheric:spiny_thicket", new Range[]{
                new Range(10f, 28f),
                new Range(20f, 38f),
                new Range(14f, 30f),
                new Range(8f, 20f)
        });

        putAllSeasons("autumnity:maple_forest", new Range[]{
                new Range(10f, 18f),
                new Range(18f, 26f),
                new Range(8f, 15f),
                new Range(-2f, 8f)
        });

        putAllSeasons("autumnity:pumpkin_fields", new Range[]{
                new Range(8f, 18f),
                new Range(20f, 28f),
                new Range(10f, 20f),
                new Range(0f, 10f)
        });

        putAllSeasons("biomesoplenty:aspen_glade", new Range[]{
                new Range(6f, 16f),
                new Range(16f, 28f),
                new Range(10f, 20f),
                new Range(-3f, 8f)
        });

        putAllSeasons("biomesoplenty:auroral_garden", new Range[]{
                new Range(0f, 10f),
                new Range(5f, 15f),
                new Range(2f, 10f),
                new Range(-15f, -2f)
        });

        putAllSeasons("biomesoplenty:bayou", new Range[]{
                new Range(15f, 26f),
                new Range(22f, 33f),
                new Range(18f, 28f),
                new Range(10f, 22f)
        });

        putAllSeasons("biomesoplenty:bog", new Range[]{
                new Range(5f, 14f),
                new Range(14f, 22f),
                new Range(6f, 16f),
                new Range(-5f, 5f)
        });

        putAllSeasons("biomesoplenty:cold_desert", new Range[]{
                new Range(5f, 20f),
                new Range(15f, 35f),
                new Range(8f, 22f),
                new Range(-10f, 10f)
        });

        putAllSeasons("biomesoplenty:coniferous_forest", new Range[]{
                new Range(5f, 14f),
                new Range(16f, 24f),
                new Range(8f, 15f),
                new Range(-5f, 5f)
        });

        putAllSeasons("biomesoplenty:crag", new Range[]{
                new Range(0f, 10f),
                new Range(8f, 18f),
                new Range(2f, 12f),
                new Range(-10f, 2f)
        });

        putAllSeasons("biomesoplenty:dead_forest", new Range[]{
                new Range(5f, 15f),
                new Range(16f, 25f),
                new Range(8f, 16f),
                new Range(-2f, 8f)
        });

        putAllSeasons("biomesoplenty:dryland", new Range[]{
                new Range(12f, 28f),
                new Range(20f, 40f),
                new Range(15f, 30f),
                new Range(8f, 20f)
        });

        putAllSeasons("biomesoplenty:dune_beach", new Range[]{
                new Range(15f, 30f),
                new Range(25f, 40f),
                new Range(18f, 32f),
                new Range(12f, 25f)
        });

        putAllSeasons("biomesoplenty:field", new Range[]{
                new Range(10f, 20f),
                new Range(18f, 28f),
                new Range(12f, 22f),
                new Range(0f, 10f)
        });

        putAllSeasons("biomesoplenty:fir_clearing", new Range[]{
                new Range(5f, 14f),
                new Range(15f, 24f),
                new Range(8f, 16f),
                new Range(-5f, 5f)
        });

        putAllSeasons("biomesoplenty:floodplain", new Range[]{
                new Range(14f, 24f),
                new Range(20f, 32f),
                new Range(16f, 26f),
                new Range(8f, 18f)
        });

        putAllSeasons("biomesoplenty:forested_field", new Range[]{
                new Range(10f, 18f),
                new Range(18f, 26f),
                new Range(12f, 20f),
                new Range(0f, 10f)
        });

        putAllSeasons("biomesoplenty:fungal_jungle", new Range[]{
                new Range(22f, 30f),
                new Range(25f, 34f),
                new Range(23f, 31f),
                new Range(21f, 30f)
        });

        putAllSeasons("biomesoplenty:glowing_grotto", new Range[]{
                new Range(16f, 20f),
                new Range(18f, 22f),
                new Range(17f, 21f),
                new Range(16f, 20f)
        });

        putAllSeasons("biomesoplenty:grassland", new Range[]{
                new Range(10f, 20f),
                new Range(20f, 30f),
                new Range(12f, 22f),
                new Range(0f, 10f)
        });

        putAllSeasons("biomesoplenty:gravel_beach", new Range[]{
                new Range(10f, 18f),
                new Range(20f, 28f),
                new Range(14f, 22f),
                new Range(8f, 15f)
        });

        putAllSeasons("biomesoplenty:highland", new Range[]{
                new Range(5f, 15f),
                new Range(10f, 22f),
                new Range(6f, 16f),
                new Range(-5f, 5f)
        });

        putAllSeasons("biomesoplenty:hot_springs", new Range[]{
                new Range(10f, 20f),
                new Range(15f, 28f),
                new Range(12f, 22f),
                new Range(5f, 15f)
        });

        putAllSeasons("biomesoplenty:jacaranda_glade", new Range[]{
                new Range(8f, 18f),
                new Range(18f, 26f),
                new Range(10f, 20f),
                new Range(2f, 10f)
        });

        putAllSeasons("biomesoplenty:jade_cliffs", new Range[]{
                new Range(5f, 15f),
                new Range(15f, 25f),
                new Range(8f, 18f),
                new Range(-3f, 8f)
        });

        putAllSeasons("biomesoplenty:lavender_field", new Range[]{
                new Range(12f, 22f),
                new Range(20f, 32f),
                new Range(15f, 25f),
                new Range(5f, 15f)
        });

        putAllSeasons("biomesoplenty:lush_desert", new Range[]{
                new Range(18f, 35f),
                new Range(25f, 45f),
                new Range(20f, 34f),
                new Range(10f, 25f)
        });

        putAllSeasons("biomesoplenty:lush_savanna", new Range[]{
                new Range(20f, 32f),
                new Range(24f, 38f),
                new Range(22f, 34f),
                new Range(18f, 28f)
        });

        putAllSeasons("biomesoplenty:maple_woods", new Range[]{
                new Range(10f, 18f),
                new Range(18f, 26f),
                new Range(8f, 16f),
                new Range(-2f, 8f)
        });

        putAllSeasons("biomesoplenty:marsh", new Range[]{
                new Range(12f, 22f),
                new Range(20f, 30f),
                new Range(15f, 25f),
                new Range(8f, 18f)
        });

        putAllSeasons("biomesoplenty:mediterranean_forest", new Range[]{
                new Range(12f, 22f),
                new Range(22f, 34f),
                new Range(14f, 24f),
                new Range(6f, 15f)
        });

        putAllSeasons("biomesoplenty:moor", new Range[]{
                new Range(8f, 16f),
                new Range(14f, 24f),
                new Range(10f, 18f),
                new Range(0f, 8f)
        });

        putAllSeasons("biomesoplenty:muskeg", new Range[]{
                new Range(4f, 12f),
                new Range(10f, 20f),
                new Range(6f, 14f),
                new Range(-8f, 4f)
        });

        putAllSeasons("biomesoplenty:mystic_grove", new Range[]{
                new Range(12f, 20f),
                new Range(18f, 28f),
                new Range(14f, 22f),
                new Range(8f, 16f)
        });

        putAllSeasons("biomesoplenty:old_growth_dead_forest", new Range[]{
                new Range(4f, 12f),
                new Range(14f, 22f),
                new Range(6f, 14f),
                new Range(-4f, 6f)
        });

        putAllSeasons("biomesoplenty:old_growth_woodland", new Range[]{
                new Range(8f, 18f),
                new Range(18f, 26f),
                new Range(10f, 20f),
                new Range(2f, 10f)
        });

        putAllSeasons("biomesoplenty:ominous_woods", new Range[]{
                new Range(6f, 14f),
                new Range(12f, 22f),
                new Range(8f, 16f),
                new Range(0f, 8f)
        });

        putAllSeasons("biomesoplenty:orchard", new Range[]{
                new Range(10f, 18f),
                new Range(18f, 28f),
                new Range(12f, 20f),
                new Range(2f, 10f)
        });

        putAllSeasons("biomesoplenty:overgrown_greens", new Range[]{
                new Range(14f, 24f),
                new Range(20f, 32f),
                new Range(16f, 26f),
                new Range(10f, 20f)
        });

        putAllSeasons("biomesoplenty:pasture", new Range[]{
                new Range(10f, 20f),
                new Range(18f, 30f),
                new Range(12f, 22f),
                new Range(0f, 10f)
        });

        putAllSeasons("biomesoplenty:prairie", new Range[]{
                new Range(8f, 18f),
                new Range(18f, 28f),
                new Range(10f, 20f),
                new Range(-2f, 8f)
        });

        putAllSeasons("biomesoplenty:pumpkin_patch", new Range[]{
                new Range(10f, 18f),
                new Range(18f, 28f),
                new Range(12f, 20f),
                new Range(2f, 10f)
        });

        putAllSeasons("biomesoplenty:rainforest", new Range[]{
                new Range(23f, 32f),
                new Range(25f, 34f),
                new Range(24f, 33f),
                new Range(22f, 31f)
        });

        putAllSeasons("biomesoplenty:redwood_forest", new Range[]{
                new Range(10f, 20f),
                new Range(18f, 26f),
                new Range(12f, 22f),
                new Range(4f, 12f)
        });

        putAllSeasons("biomesoplenty:rocky_rainforest", new Range[]{
                new Range(20f, 30f),
                new Range(24f, 34f),
                new Range(22f, 32f),
                new Range(20f, 30f)
        });

        putAllSeasons("biomesoplenty:rocky_shrubland", new Range[]{
                new Range(8f, 22f),
                new Range(18f, 34f),
                new Range(10f, 24f),
                new Range(4f, 18f)
        });

        putAllSeasons("biomesoplenty:scrubland", new Range[]{
                new Range(10f, 25f),
                new Range(18f, 33f),
                new Range(12f, 26f),
                new Range(5f, 18f)
        });

        putAllSeasons("biomesoplenty:seasonal_forest", new Range[]{
                new Range(10f, 20f),
                new Range(18f, 28f),
                new Range(8f, 18f),
                new Range(-5f, 8f)
        });
        putAllSeasons("biomesoplenty:seasonal_forest", new Range[]{
                new Range(10f, 20f),
                new Range(18f, 28f),
                new Range(8f, 18f),
                new Range(-5f, 8f)
        });

        putAllSeasons("biomesoplenty:shrubland", new Range[]{
                new Range(8f, 22f),
                new Range(18f, 34f),
                new Range(10f, 24f),
                new Range(4f, 18f)
        });

        putAllSeasons("biomesoplenty:snowblossom_grove", new Range[]{
                new Range(0f, 10f),
                new Range(5f, 18f),
                new Range(2f, 12f),
                new Range(-15f, 0f)
        });

        putAllSeasons("biomesoplenty:snowy_coniferous_forest", new Range[]{
                new Range(0f, 10f),
                new Range(5f, 18f),
                new Range(2f, 10f),
                new Range(-20f, -2f)
        });

        putAllSeasons("biomesoplenty:snowy_fir_clearing", new Range[]{
                new Range(0f, 10f),
                new Range(6f, 16f),
                new Range(2f, 10f),
                new Range(-18f, -2f)
        });

        putAllSeasons("biomesoplenty:snowy_maple_woods", new Range[]{
                new Range(2f, 12f),
                new Range(10f, 20f),
                new Range(4f, 14f),
                new Range(-10f, 2f)
        });

        putAllSeasons("biomesoplenty:spider_nest", new Range[]{
                new Range(14f, 22f),
                new Range(20f, 30f),
                new Range(16f, 24f),
                new Range(10f, 18f)
        });

        putAllSeasons("biomesoplenty:tropics", new Range[]{
                new Range(25f, 33f),
                new Range(26f, 35f),
                new Range(25f, 34f),
                new Range(24f, 33f)
        });

        putAllSeasons("biomesoplenty:tundra", new Range[]{
                new Range(-15f, -2f),
                new Range(-5f, 8f),
                new Range(-10f, 0f),
                new Range(-25f, -8f)
        });

        putAllSeasons("biomesoplenty:volcanic_plains", new Range[]{
                new Range(20f, 35f),
                new Range(25f, 45f),
                new Range(22f, 36f),
                new Range(15f, 28f)
        });

        putAllSeasons("biomesoplenty:volcano", new Range[]{
                new Range(25f, 45f),
                new Range(30f, 55f),
                new Range(28f, 48f),
                new Range(20f, 38f)
        });

        putAllSeasons("biomesoplenty:wasteland", new Range[]{
                new Range(10f, 30f),
                new Range(20f, 45f),
                new Range(15f, 32f),
                new Range(5f, 20f)
        });

        putAllSeasons("biomesoplenty:wasteland_steppe", new Range[]{
                new Range(8f, 25f),
                new Range(18f, 40f),
                new Range(12f, 28f),
                new Range(0f, 15f)
        });

        putAllSeasons("biomesoplenty:wetland", new Range[]{
                new Range(12f, 24f),
                new Range(20f, 32f),
                new Range(15f, 26f),
                new Range(8f, 20f)
        });

        putAllSeasons("biomesoplenty:woodland", new Range[]{
                new Range(10f, 18f),
                new Range(18f, 26f),
                new Range(12f, 20f),
                new Range(4f, 12f)
        });

        putAllSeasons("galosphere:crystal_canyons", new Range[]{
                new Range(4f, 14f),
                new Range(10f, 22f),
                new Range(6f, 16f),
                new Range(-6f, 6f)
        });

        putAllSeasons("integrateddynamics:meneglin", new Range[]{
                new Range(8f, 18f),
                new Range(16f, 26f),
                new Range(10f, 20f),
                new Range(2f, 12f)
        });

        putAllSeasons("neapolitan:strawberry_fields", new Range[]{
                new Range(12f, 22f),
                new Range(20f, 30f),
                new Range(15f, 25f),
                new Range(6f, 16f)
        });
// === Biomes You'll Go (BYG) Biomes ===

        putAllSeasons("biomeswevegone:allium_shrubland", new Range[]{
                new Range(8f, 18f),  // Temperate meadow with flowers
                new Range(12f, 22f),
                new Range(18f, 27f),
                new Range(10f, 20f)
        });

        putAllSeasons("biomeswevegone:amaranth_grassland", new Range[]{
                new Range(10f, 20f),  // Warm grassland with floral variety
                new Range(15f, 25f),
                new Range(22f, 32f),
                new Range(12f, 22f)
        });

        putAllSeasons("biomeswevegone:araucaria_savanna", new Range[]{
                new Range(14f, 26f),  // Warm savanna biome
                new Range(18f, 30f),
                new Range(25f, 36f),
                new Range(16f, 26f)
        });

        putAllSeasons("biomeswevegone:aspen_boreal", new Range[]{
                new Range(-5f, 6f),  // Cold boreal forest
                new Range(2f, 14f),
                new Range(10f, 20f),
                new Range(-1f, 10f)
        });

        putAllSeasons("biomeswevegone:atacama_outback", new Range[]{
                new Range(18f, 32f),  // Hot desert outback
                new Range(25f, 42f),
                new Range(35f, 54f),
                new Range(22f, 38f)
        });

        putAllSeasons("biomeswevegone:baobab_savanna", new Range[]{
                new Range(15f, 26f),  // Savanna with Baobab trees
                new Range(20f, 30f),
                new Range(28f, 38f),
                new Range(18f, 28f)
        });

        putAllSeasons("biomeswevegone:basalt_barrera", new Range[]{
                new Range(2f, 10f),  // Volcanic rocky region
                new Range(5f, 14f),
                new Range(8f, 18f),
                new Range(4f, 12f)
        });

        putAllSeasons("biomeswevegone:bayou", new Range[]{
                new Range(16f, 25f),  // Tropical swamp
                new Range(20f, 32f),
                new Range(26f, 38f),
                new Range(18f, 28f)
        });

        putAllSeasons("biomeswevegone:black_forest", new Range[]{
                new Range(2f, 10f),  // Cold, dark pine forest
                new Range(8f, 16f),
                new Range(15f, 24f),
                new Range(4f, 12f)
        });

        putAllSeasons("biomeswevegone:canadian_shield", new Range[]{
                new Range(-10f, 2f),  // Cold northern biome
                new Range(-2f, 10f),
                new Range(5f, 18f),
                new Range(-5f, 8f)
        });

        putAllSeasons("biomeswevegone:cika_woods", new Range[]{
                new Range(5f, 14f),  // Mild temperate forest
                new Range(10f, 20f),
                new Range(18f, 26f),
                new Range(8f, 18f)
        });

        putAllSeasons("biomeswevegone:coconino_meadow", new Range[]{
                new Range(8f, 18f),  // Temperate meadow
                new Range(14f, 24f),
                new Range(20f, 30f),
                new Range(10f, 20f)
        });

        putAllSeasons("biomeswevegone:coniferous_forest", new Range[]{
                new Range(-8f, 3f),  // Cold conifer forest
                new Range(2f, 14f),
                new Range(10f, 20f),
                new Range(-2f, 8f)
        });

        putAllSeasons("biomeswevegone:crag_gardens", new Range[]{
                new Range(6f, 14f),  // Elevated tropical cliffs
                new Range(10f, 20f),
                new Range(20f, 30f),
                new Range(8f, 18f)
        });

        putAllSeasons("biomeswevegone:crimson_tundra", new Range[]{
                new Range(-12f, -2f),  // Cold crimson tundra
                new Range(-6f, 6f),
                new Range(2f, 12f),
                new Range(-8f, 2f)
        });

        putAllSeasons("biomeswevegone:cypress_swamplands", new Range[]{
                new Range(16f, 25f),  // Warm swamp with cypress
                new Range(20f, 32f),
                new Range(26f, 36f),
                new Range(18f, 28f)
        });

        putAllSeasons("biomeswevegone:cypress_wetlands", new Range[]{
                new Range(18f, 26f),  // Tropical flooded wetlands
                new Range(22f, 32f),
                new Range(28f, 38f),
                new Range(20f, 30f)
        });

        putAllSeasons("biomeswevegone:dacite_ridges", new Range[]{
                new Range(-2f, 8f),  // Mountainous forest ridges
                new Range(5f, 15f),
                new Range(10f, 20f),
                new Range(2f, 10f)
        });

        putAllSeasons("biomeswevegone:dacite_shore", new Range[]{
                new Range(10f, 20f),  // Cool coastal shore
                new Range(15f, 25f),
                new Range(22f, 30f),
                new Range(12f, 22f)
        });

        putAllSeasons("biomeswevegone:dead_sea", new Range[]{
                new Range(18f, 26f),  // Saline sea biome
                new Range(22f, 34f),
                new Range(28f, 40f),
                new Range(20f, 30f)
        });

        putAllSeasons("biomeswevegone:ebony_woods", new Range[]{
                new Range(10f, 18f),  // Dense forest with mushrooms
                new Range(14f, 24f),
                new Range(20f, 28f),
                new Range(12f, 20f)
        });

        putAllSeasons("biomeswevegone:enchanted_tangle", new Range[]{
                new Range(8f, 16f),  // Mystical forest
                new Range(12f, 22f),
                new Range(18f, 26f),
                new Range(10f, 20f)
        });

        putAllSeasons("biomeswevegone:eroded_borealis", new Range[]{
                new Range(-18f, -4f),  // Arctic ice spikes
                new Range(-10f, 0f),
                new Range(-5f, 5f),
                new Range(-12f, -2f)
        });

        putAllSeasons("biomeswevegone:firecracker_chaparral", new Range[]{
                new Range(12f, 22f),  // Warm shrubland
                new Range(20f, 30f),
                new Range(25f, 36f),
                new Range(15f, 25f)
        });

        putAllSeasons("biomeswevegone:forgotten_forest", new Range[]{
                new Range(8f, 16f),  // Magical forest
                new Range(14f, 22f),
                new Range(18f, 26f),
                new Range(10f, 18f)
        });

        putAllSeasons("biomeswevegone:fragment_jungle", new Range[]{
                new Range(18f, 26f),  // Humid tropical cliffs
                new Range(22f, 34f),
                new Range(28f, 40f),
                new Range(20f, 30f)
        });

        putAllSeasons("biomeswevegone:frosted_coniferous_forest", new Range[]{
                new Range(-12f, -2f),  // Frozen coniferous region
                new Range(-5f, 6f),
                new Range(2f, 12f),
                new Range(-8f, 2f)
        });

        putAllSeasons("biomeswevegone:frosted_taiga", new Range[]{
                new Range(-14f, -5f),  // Snow-covered taiga
                new Range(-8f, 2f),
                new Range(2f, 10f),
                new Range(-10f, 0f)
        });

        putAllSeasons("biomeswevegone:howling_peaks", new Range[]{
                new Range(-10f, -2f),  // Mountainous peaks
                new Range(-5f, 5f),
                new Range(5f, 14f),
                new Range(-6f, 4f)
        });

        putAllSeasons("biomeswevegone:ironwood_gour", new Range[]{
                new Range(15f, 25f),  // Semi-arid terrain
                new Range(20f, 32f),
                new Range(30f, 42f),
                new Range(18f, 28f)
        });

        putAllSeasons("biomeswevegone:jacaranda_jungle", new Range[]{
                new Range(18f, 25f),  // Tropical jungle
                new Range(22f, 32f),
                new Range(28f, 38f),
                new Range(20f, 30f)
        });

        putAllSeasons("biomeswevegone:lush_stacks", new Range[]{
                new Range(16f, 24f),  // Humid oceanic region
                new Range(20f, 30f),
                new Range(26f, 38f),
                new Range(18f, 28f)
        });

        putAllSeasons("biomeswevegone:maple_taiga", new Range[]{
                new Range(-8f, 2f),  // Cold taiga forest
                new Range(2f, 12f),
                new Range(10f, 20f),
                new Range(0f, 10f)
        });

        putAllSeasons("biomeswevegone:mojave_desert", new Range[]{
                new Range(20f, 34f),  // Hot desert
                new Range(28f, 42f),
                new Range(36f, 54f),
                new Range(25f, 40f)
        });

        putAllSeasons("biomeswevegone:orchard", new Range[]{
                new Range(6f, 16f),  // Mild temperate orchard
                new Range(12f, 22f),
                new Range(20f, 30f),
                new Range(10f, 20f)
        });

        putAllSeasons("biomeswevegone:overgrowth_woodlands", new Range[]{
                new Range(4f, 14f),  // Dense shaded forest
                new Range(10f, 20f),
                new Range(18f, 26f),
                new Range(8f, 18f)
        });

        putAllSeasons("biomeswevegone:pale_bog", new Range[]{
                new Range(2f, 12f),  // Eerie swamp bog
                new Range(8f, 18f),
                new Range(15f, 25f),
                new Range(5f, 15f)
        });

        putAllSeasons("biomeswevegone:prairie", new Range[]{
                new Range(6f, 18f),  // Open grassland
                new Range(12f, 24f),
                new Range(22f, 32f),
                new Range(10f, 20f)
        });

        putAllSeasons("biomeswevegone:pumpkin_valley", new Range[]{
                new Range(5f, 15f),  // Temperate valley
                new Range(12f, 22f),
                new Range(20f, 28f),
                new Range(8f, 18f)
        });

        putAllSeasons("biomeswevegone:rainbow_beach", new Range[]{
                new Range(12f, 22f),  // Warm colorful coastal biome
                new Range(18f, 28f),
                new Range(24f, 34f),
                new Range(14f, 24f)
        });

        putAllSeasons("biomeswevegone:red_rock_peaks", new Range[]{
                new Range(5f, 15f),  // Warm elevated desert peaks
                new Range(12f, 25f),
                new Range(25f, 38f),
                new Range(10f, 22f)
        });

        putAllSeasons("biomeswevegone:red_rock_valley", new Range[]{
                new Range(8f, 20f),  // Semi-arid canyon valley
                new Range(15f, 28f),
                new Range(28f, 40f),
                new Range(12f, 24f)
        });

        putAllSeasons("biomeswevegone:redwood_thicket", new Range[]{
                new Range(4f, 12f),  // Temperate redwood forest
                new Range(10f, 20f),
                new Range(18f, 26f),
                new Range(8f, 18f)
        });

        putAllSeasons("biomeswevegone:rose_fields", new Range[]{
                new Range(10f, 20f),  // Warm floral fields
                new Range(14f, 24f),
                new Range(22f, 30f),
                new Range(12f, 22f)
        });

        putAllSeasons("biomeswevegone:rugged_badlands", new Range[]{
                new Range(15f, 26f),  // Arid badlands terrain
                new Range(22f, 34f),
                new Range(30f, 46f),
                new Range(18f, 30f)
        });

        putAllSeasons("biomeswevegone:sakura_grove", new Range[]{
                new Range(6f, 16f),  // Temperate cherry blossom grove
                new Range(12f, 22f),
                new Range(20f, 28f),
                new Range(10f, 20f)
        });

        putAllSeasons("biomeswevegone:shattered_glacier", new Range[]{
                new Range(-18f, -6f),  // Extreme frozen glacier
                new Range(-10f, 0f),
                new Range(-5f, 5f),
                new Range(-12f, -2f)
        });

        putAllSeasons("biomeswevegone:sierra_badlands", new Range[]{
                new Range(10f, 20f),  // Rugged badlands with vegetation
                new Range(18f, 30f),
                new Range(28f, 40f),
                new Range(15f, 25f)
        });

        putAllSeasons("biomeswevegone:skyrise_vale", new Range[]{
                new Range(6f, 16f),  // High-altitude forest biome
                new Range(10f, 22f),
                new Range(20f, 28f),
                new Range(8f, 18f)
        });

        putAllSeasons("biomeswevegone:temperate_grove", new Range[]{
                new Range(4f, 14f),  // Mild forest biome
                new Range(10f, 20f),
                new Range(18f, 26f),
                new Range(8f, 18f)
        });

        putAllSeasons("biomeswevegone:tropical_rainforest", new Range[]{
                new Range(20f, 28f),  // Hot humid rainforest
                new Range(24f, 34f),
                new Range(30f, 40f),
                new Range(22f, 32f)
        });

        putAllSeasons("biomeswevegone:weeping_witch_forest", new Range[]{
                new Range(4f, 12f),  // Magical forest biome
                new Range(10f, 20f),
                new Range(18f, 26f),
                new Range(8f, 18f)
        });

        putAllSeasons("biomeswevegone:white_mangrove_marshes", new Range[]{
                new Range(18f, 26f),  // Warm mangrove swamp
                new Range(22f, 32f),
                new Range(28f, 38f),
                new Range(20f, 30f)
        });

        putAllSeasons("biomeswevegone:windswept_desert", new Range[]{
                new Range(20f, 34f),  // Harsh hot desert
                new Range(28f, 44f),
                new Range(36f, 54f),
                new Range(25f, 40f)
        });

        putAllSeasons("biomeswevegone:zelkova_forest", new Range[]{
                new Range(6f, 16f),  // Temperate zelkova forest
                new Range(12f, 22f),
                new Range(20f, 28f),
                new Range(10f, 20f)
        });

        putAllSeasons("regions_unexplored:alpha_grove", new Range[]{
                new Range(-4f, 6f),
                new Range(6f, 15f),
                new Range(18f, 27f),
                new Range(8f, 16f)
        }); // Early-Minecraft-style temperate biome with nostalgic foliage

        putAllSeasons("regions_unexplored:arid_mountains", new Range[]{
                new Range(5f, 15f),
                new Range(15f, 25f),
                new Range(30f, 42f),
                new Range(18f, 26f)
        }); // Hot dry peaks, similar to badlands but elevated

        putAllSeasons("regions_unexplored:ashen_woodland", new Range[]{
                new Range(-2f, 8f),
                new Range(8f, 16f),
                new Range(14f, 25f),
                new Range(6f, 14f)
        }); // Charred grey biome with volcanic residue, mild-warm overall

        putAllSeasons("regions_unexplored:autumnal_maple_forest", new Range[]{
                new Range(-6f, 6f),
                new Range(6f, 16f),
                new Range(18f, 26f),
                new Range(4f, 12f)
        }); // Warm fall-colored forest, temperate humidity
        putAllSeasons("regions_unexplored:bamboo_forest", new Range[]{
                new Range(8f, 18f),
                new Range(18f, 25f),
                new Range(25f, 34f),
                new Range(18f, 26f)
        }); // Dense humid jungle-like forest with tall bamboo

        putAllSeasons("regions_unexplored:baobab_savanna", new Range[]{
                new Range(10f, 22f),
                new Range(18f, 30f),
                new Range(30f, 42f),
                new Range(20f, 28f)
        }); // Warm dry savanna biome with tall baobab trees

        putAllSeasons("regions_unexplored:barley_fields", new Range[]{
                new Range(-2f, 8f),
                new Range(6f, 16f),
                new Range(18f, 27f),
                new Range(6f, 14f)
        }); // Rolling temperate fields with barley vegetation

        putAllSeasons("regions_unexplored:bayou", new Range[]{
                new Range(8f, 16f),
                new Range(16f, 26f),
                new Range(26f, 34f),
                new Range(18f, 24f)
        }); // Hot swamp biome with willows and Spanish moss

        putAllSeasons("regions_unexplored:blackwood_taiga", new Range[]{
                new Range(-10f, 0f),
                new Range(0f, 8f),
                new Range(10f, 20f),
                new Range(-2f, 6f)
        }); // Cold dark taiga with dense canopy

        putAllSeasons("regions_unexplored:boreal_taiga", new Range[]{
                new Range(-18f, -6f),
                new Range(-4f, 8f),
                new Range(10f, 18f),
                new Range(-2f, 6f)
        }); // Boreal coniferous forest, cold winters mild summers

        putAllSeasons("regions_unexplored:chalk_cliffs", new Range[]{
                new Range(2f, 10f),
                new Range(10f, 18f),
                new Range(18f, 26f),
                new Range(8f, 16f)
        }); // Coastal chalk cliffs, mild maritime climate

        putAllSeasons("regions_unexplored:clover_plains", new Range[]{
                new Range(-3f, 8f),
                new Range(6f, 15f),
                new Range(18f, 27f),
                new Range(6f, 14f)
        }); // Gentle plains dotted with clovers and lupines

        putAllSeasons("regions_unexplored:cold_boreal_taiga", new Range[]{
                new Range(-20f, -10f),
                new Range(-8f, 4f),
                new Range(8f, 16f),
                new Range(-6f, 2f)
        }); // Snowy conifer forest variant of boreal taiga

        putAllSeasons("regions_unexplored:cold_deciduous_forest", new Range[]{
                new Range(-18f, -8f),
                new Range(-6f, 6f),
                new Range(8f, 16f),
                new Range(-4f, 4f)
        }); // Frozen red-leaf forest, long cold seasons

        putAllSeasons("regions_unexplored:cold_river", new Range[]{
                new Range(-12f, -4f),
                new Range(-4f, 8f),
                new Range(8f, 16f),
                new Range(-2f, 6f)
        }); // Cold river biome flowing through snowy lands

        putAllSeasons("regions_unexplored:deciduous_forest", new Range[]{
                new Range(-4f, 8f),
                new Range(8f, 16f),
                new Range(18f, 28f),
                new Range(8f, 14f)
        }); // Balanced mixed forest, typical temperate climate

        putAllSeasons("regions_unexplored:dry_bushland", new Range[]{
                new Range(8f, 18f),
                new Range(16f, 26f),
                new Range(26f, 38f),
                new Range(18f, 26f)
        }); // Semi-arid shrubland with acacia and silt soil
        putAllSeasons("regions_unexplored:eucalyptus_forest", new Range[]{
                new Range(8f, 18f),
                new Range(16f, 26f),
                new Range(26f, 36f),
                new Range(18f, 26f)
        }); // Warm-humid subtropical forest with colorful trees

        putAllSeasons("regions_unexplored:fen", new Range[]{
                new Range(0f, 10f),
                new Range(8f, 18f),
                new Range(16f, 26f),
                new Range(6f, 14f)
        }); // Cool wetland with cattails and pine

        putAllSeasons("regions_unexplored:flower_fields", new Range[]{
                new Range(2f, 10f),
                new Range(10f, 20f),
                new Range(18f, 28f),
                new Range(8f, 16f)
        }); // Colorful open plains with abundant flora

        putAllSeasons("regions_unexplored:frozen_pine_taiga", new Range[]{
                new Range(-25f, -12f),
                new Range(-10f, 2f),
                new Range(6f, 14f),
                new Range(-8f, 0f)
        }); // Harsh snowy pine forest, sub-arctic temperatures

        putAllSeasons("regions_unexplored:frozen_tundra", new Range[]{
                new Range(-30f, -18f),
                new Range(-16f, -4f),
                new Range(-2f, 6f),
                new Range(-14f, -4f)
        }); // Flat tundra, frozen year-round with permafrost

        putAllSeasons("regions_unexplored:fungal_fen", new Range[]{
                new Range(2f, 10f),
                new Range(10f, 18f),
                new Range(18f, 26f),
                new Range(8f, 16f)
        }); // Humid fungal swamp variant of fen

// ======== G ========

        putAllSeasons("regions_unexplored:glistering_meadow", new Range[]{
                new Range(0f, 10f),
                new Range(10f, 18f),
                new Range(18f, 28f),
                new Range(8f, 16f)
        }); // Bright temperate meadow with luminescent flowers

        putAllSeasons("regions_unexplored:golden_forest", new Range[]{
                new Range(-2f, 8f),
                new Range(8f, 16f),
                new Range(18f, 28f),
                new Range(6f, 14f)
        }); // Temperate deciduous forest with golden leaves

        putAllSeasons("regions_unexplored:golden_fields", new Range[]{
                new Range(-1f, 9f),
                new Range(9f, 17f),
                new Range(17f, 27f),
                new Range(7f, 15f)
        }); // Warm golden grasslands with scattered oaks

        putAllSeasons("regions_unexplored:grassy_beach", new Range[]{
                new Range(4f, 12f),
                new Range(12f, 20f),
                new Range(20f, 30f),
                new Range(10f, 18f)
        }); // Mild coastal transition between plains and ocean

        putAllSeasons("regions_unexplored:grassy_plains", new Range[]{
                new Range(-4f, 8f),
                new Range(8f, 18f),
                new Range(18f, 28f),
                new Range(6f, 14f)
        }); // Classic open temperate plains biome

        putAllSeasons("regions_unexplored:grove", new Range[]{
                new Range(-6f, 6f),
                new Range(6f, 14f),
                new Range(14f, 22f),
                new Range(4f, 12f)
        }); // Small forest clearings, mild and balanced temperatures

// ======== H ========

        putAllSeasons("regions_unexplored:heath", new Range[]{
                new Range(0f, 10f),
                new Range(10f, 18f),
                new Range(18f, 28f),
                new Range(8f, 16f)
        }); // Low-vegetation temperate heathland

        putAllSeasons("regions_unexplored:highlands", new Range[]{
                new Range(-10f, 2f),
                new Range(2f, 10f),
                new Range(12f, 20f),
                new Range(0f, 8f)
        }); // Cold mountain slopes with rocky terrain

        putAllSeasons("regions_unexplored:holly_woods", new Range[]{
                new Range(-4f, 8f),
                new Range(8f, 16f),
                new Range(16f, 26f),
                new Range(6f, 14f)
        }); // Mixed forest with holly and birch, mild humidity

        putAllSeasons("regions_unexplored:hot_springs", new Range[]{
                new Range(6f, 16f),
                new Range(14f, 24f),
                new Range(18f, 30f),
                new Range(10f, 20f)
        }); // Warm alpine biome with geysers and natural pools

        putAllSeasons("regions_unexplored:hyacinth_fields", new Range[]{
                new Range(2f, 10f),
                new Range(10f, 20f),
                new Range(18f, 28f),
                new Range(8f, 16f)
        }); // Flower plains with hyacinths, temperate weather

// ======== I ========

        putAllSeasons("regions_unexplored:ice_spikes", new Range[]{
                new Range(-30f, -18f),
                new Range(-18f, -6f),
                new Range(-10f, 0f),
                new Range(-20f, -8f)
        }); // Frozen tundra with large ice structures

        putAllSeasons("regions_unexplored:icy_tundra", new Range[]{
                new Range(-26f, -14f),
                new Range(-14f, -4f),
                new Range(-6f, 4f),
                new Range(-16f, -6f)
        }); // Open frozen plain with permafrost ground

        putAllSeasons("regions_unexplored:ironwood_gour", new Range[]{
                new Range(6f, 16f),
                new Range(14f, 24f),
                new Range(26f, 36f),
                new Range(14f, 22f)
        }); // Arid savanna-like biome with sparse trees

// ======== J ========

        putAllSeasons("regions_unexplored:jacaranda_forest", new Range[]{
                new Range(6f, 14f),
                new Range(14f, 22f),
                new Range(22f, 32f),
                new Range(12f, 20f)
        }); // Warm colorful forest with jacaranda and green mushrooms

// ======== K ========

        putAllSeasons("regions_unexplored:kapok_jungle", new Range[]{
                new Range(20f, 26f),
                new Range(26f, 34f),
                new Range(30f, 38f),
                new Range(22f, 30f)
        }); // Dense tropical jungle with giant kapok trees

// ======== L ========

        putAllSeasons("regions_unexplored:lavender_meadow", new Range[]{
                new Range(4f, 12f),
                new Range(12f, 20f),
                new Range(18f, 26f),
                new Range(8f, 16f)
        }); // Calm temperate meadow filled with lavender and tall grass

        putAllSeasons("regions_unexplored:lush_meadow", new Range[]{
                new Range(4f, 14f),
                new Range(14f, 22f),
                new Range(18f, 28f),
                new Range(8f, 18f)
        }); // Verdant meadow biome, warm and humid

        putAllSeasons("regions_unexplored:lush_stacks", new Range[]{
                new Range(8f, 18f),
                new Range(16f, 26f),
                new Range(24f, 32f),
                new Range(14f, 22f)
        }); // Warm oceanic biome with lush vegetation and spires

// ======== M ========

        putAllSeasons("regions_unexplored:maple_taiga", new Range[]{
                new Range(-8f, 2f),
                new Range(2f, 10f),
                new Range(10f, 20f),
                new Range(0f, 8f)
        }); // Cold taiga biome with maple trees

        putAllSeasons("regions_unexplored:marsh", new Range[]{
                new Range(2f, 10f),
                new Range(10f, 18f),
                new Range(18f, 26f),
                new Range(8f, 16f)
        }); // Wetland biome with shallow water and mud

        putAllSeasons("regions_unexplored:mojave_desert", new Range[]{
                new Range(18f, 28f),
                new Range(26f, 36f),
                new Range(38f, 50f),
                new Range(24f, 34f)
        }); // Extremely hot desert biome with cracked sand

// ======== N ========

        putAllSeasons("regions_unexplored:neon_oasis", new Range[]{
                new Range(20f, 26f),
                new Range(26f, 34f),
                new Range(32f, 42f),
                new Range(22f, 30f)
        }); // Vibrant oasis biome in desert, high heat and humidity

// ======== O ========

        putAllSeasons("regions_unexplored:orchard", new Range[]{
                new Range(2f, 10f),
                new Range(10f, 20f),
                new Range(20f, 30f),
                new Range(10f, 18f)
        }); // Fruit tree fields, warm temperate with lush flora

        putAllSeasons("regions_unexplored:overgrown_woodlands", new Range[]{
                new Range(4f, 12f),
                new Range(12f, 20f),
                new Range(20f, 28f),
                new Range(10f, 18f)
        }); // Dense forest canopy, humid with glowberries

// ======== P ========

        putAllSeasons("regions_unexplored:pale_bog", new Range[]{
                new Range(-2f, 6f),
                new Range(6f, 14f),
                new Range(14f, 22f),
                new Range(4f, 10f)
        }); // Mysterious bog with pale trees and fog

        putAllSeasons("regions_unexplored:pink_grove", new Range[]{
                new Range(6f, 14f),
                new Range(14f, 22f),
                new Range(18f, 28f),
                new Range(8f, 18f)
        }); // Warm flower forest variant with pink petals

        putAllSeasons("regions_unexplored:prairie", new Range[]{
                new Range(-4f, 8f),
                new Range(8f, 18f),
                new Range(18f, 28f),
                new Range(6f, 14f)
        }); // Flat grassy biome, mild temperatures

        putAllSeasons("regions_unexplored:pumpkin_valley", new Range[]{
                new Range(-2f, 8f),
                new Range(8f, 16f),
                new Range(16f, 24f),
                new Range(6f, 14f)
        }); // Temperate pumpkin patch fields

// ======== R ========

        putAllSeasons("regions_unexplored:rainbow_beach", new Range[]{
                new Range(10f, 20f),
                new Range(20f, 28f),
                new Range(26f, 34f),
                new Range(18f, 26f)
        }); // Colorful sand beach, tropical temperature

        putAllSeasons("regions_unexplored:red_rock_peaks", new Range[]{
                new Range(4f, 12f),
                new Range(12f, 20f),
                new Range(20f, 30f),
                new Range(10f, 18f)
        }); // Dry red peaks with moderate summer heat

        putAllSeasons("regions_unexplored:redwood_thicket", new Range[]{
                new Range(2f, 10f),
                new Range(10f, 18f),
                new Range(18f, 26f),
                new Range(8f, 16f)
        }); // Cool redwood forest, mild maritime weather

        putAllSeasons("regions_unexplored:rose_fields", new Range[]{
                new Range(0f, 10f),
                new Range(10f, 18f),
                new Range(18f, 26f),
                new Range(8f, 16f)
        }); // Rolling flower plains of roses

        putAllSeasons("regions_unexplored:rugged_badlands", new Range[]{
                new Range(8f, 18f),
                new Range(16f, 26f),
                new Range(30f, 42f),
                new Range(20f, 30f)
        }); // Harsh arid terrain with scattered shrubs

// ======== S ========

        putAllSeasons("regions_unexplored:sakura_grove", new Range[]{
                new Range(4f, 14f),
                new Range(14f, 22f),
                new Range(20f, 30f),
                new Range(10f, 18f)
        }); // Warm cherry blossom forest

        putAllSeasons("regions_unexplored:sandy_dunes", new Range[]{
                new Range(16f, 26f),
                new Range(26f, 34f),
                new Range(36f, 46f),
                new Range(24f, 32f)
        }); // Very hot arid desert biome

        putAllSeasons("regions_unexplored:shattered_glacier", new Range[]{
                new Range(-28f, -16f),
                new Range(-16f, -6f),
                new Range(-8f, 2f),
                new Range(-18f, -8f)
        }); // Harsh frozen ice biome with deep glaciers

        putAllSeasons("regions_unexplored:sierra_badlands", new Range[]{
                new Range(6f, 16f),
                new Range(16f, 24f),
                new Range(28f, 38f),
                new Range(18f, 26f)
        }); // Warm mountainous badlands with rock formations

        putAllSeasons("regions_unexplored:skyris_vale", new Range[]{
                new Range(2f, 12f),
                new Range(10f, 18f),
                new Range(16f, 26f),
                new Range(8f, 16f)
        }); // Elevated forest biome with skyris trees

        putAllSeasons("regions_unexplored:snowy_taiga", new Range[]{
                new Range(-20f, -8f),
                new Range(-8f, 2f),
                new Range(2f, 10f),
                new Range(-6f, 0f)
        }); // Classic snowy pine biome

        putAllSeasons("regions_unexplored:steppe", new Range[]{
                new Range(-8f, 4f),
                new Range(4f, 12f),
                new Range(12f, 24f),
                new Range(2f, 10f)
        }); // Cold dry grassland, moderate summer heat

        putAllSeasons("regions_unexplored:stone_prairie", new Range[]{
                new Range(-2f, 8f),
                new Range(8f, 16f),
                new Range(16f, 24f),
                new Range(6f, 14f)
        }); // Rocky plains biome, mild temperature variation

// ======== T ========

        putAllSeasons("regions_unexplored:temperate_grove", new Range[]{
                new Range(-4f, 8f),
                new Range(8f, 18f),
                new Range(18f, 28f),
                new Range(6f, 14f)
        }); // Classic temperate biome with balanced flora

        putAllSeasons("regions_unexplored:tropical_rainforest", new Range[]{
                new Range(20f, 26f),
                new Range(26f, 34f),
                new Range(30f, 38f),
                new Range(24f, 32f)
        }); // Hot humid rainforest with dense canopy

// ======== W ========

        putAllSeasons("regions_unexplored:weeping_witch_forest", new Range[]{
                new Range(0f, 10f),
                new Range(10f, 18f),
                new Range(18f, 26f),
                new Range(8f, 16f)
        }); // Dark enchanted forest with weeping trees

        putAllSeasons("regions_unexplored:white_mangrove_marsh", new Range[]{
                new Range(8f, 18f),
                new Range(16f, 26f),
                new Range(26f, 34f),
                new Range(18f, 26f)
        }); // Tropical mangrove swamp with constant humidity

        putAllSeasons("regions_unexplored:windswept_desert", new Range[]{
                new Range(18f, 26f),
                new Range(26f, 34f),
                new Range(36f, 46f),
                new Range(24f, 32f)
        }); // Hot dry desert with strong winds and dunes

        putAllSeasons("regions_unexplored:windswept_peaks", new Range[]{
                new Range(-10f, 0f),
                new Range(0f, 8f),
                new Range(8f, 16f),
                new Range(-2f, 6f)
        }); // High-altitude cold mountain ridges

// ======== Z ========

        putAllSeasons("regions_unexplored:zelkova_forest", new Range[]{
                new Range(-2f, 8f),
                new Range(8f, 16f),
                new Range(16f, 26f),
                new Range(6f, 14f)
        }); // Warm green forest with large zelkova trees

        putAllSeasons("natures_spirit:alpine_clearings", new Range[]{
                new Range(-10f, 2f),
                new Range(2f, 10f),
                new Range(10f, 18f),
                new Range(0f, 8f)
        });

        putAllSeasons("natures_spirit:alpine_highlands", new Range[]{
                new Range(-12f, 0f),
                new Range(0f, 8f),
                new Range(8f, 16f),
                new Range(-2f, 6f)
        });

        putAllSeasons("natures_spirit:amber_covert", new Range[]{
                new Range(-6f, 4f),
                new Range(4f, 12f),
                new Range(12f, 20f),
                new Range(2f, 10f)
        });

        putAllSeasons("natures_spirit:arid_highlands", new Range[]{
                new Range(8f, 18f),
                new Range(18f, 28f),
                new Range(28f, 38f),
                new Range(15f, 25f)
        });

        putAllSeasons("natures_spirit:arid_savanna", new Range[]{
                new Range(12f, 22f),
                new Range(22f, 32f),
                new Range(32f, 40f),
                new Range(18f, 28f)
        });

        putAllSeasons("natures_spirit:aspen_forest", new Range[]{
                new Range(-4f, 6f),
                new Range(6f, 14f),
                new Range(14f, 22f),
                new Range(4f, 12f)
        });

        putAllSeasons("natures_spirit:bamboo_wetlands", new Range[]{
                new Range(18f, 26f),
                new Range(26f, 32f),
                new Range(32f, 38f),
                new Range(20f, 28f)
        });

        putAllSeasons("natures_spirit:blooming_dunes", new Range[]{
                new Range(16f, 26f),
                new Range(26f, 34f),
                new Range(34f, 44f),
                new Range(20f, 30f)
        });

        putAllSeasons("natures_spirit:blooming_highlands", new Range[]{
                new Range(4f, 12f),
                new Range(12f, 20f),
                new Range(20f, 28f),
                new Range(8f, 16f)
        });

        putAllSeasons("natures_spirit:blooming_sugi_forest", new Range[]{
                new Range(2f, 10f),
                new Range(10f, 18f),
                new Range(18f, 26f),
                new Range(6f, 14f)
        });

        putAllSeasons("natures_spirit:boreal_taiga", new Range[]{
                new Range(-18f, -4f),
                new Range(-4f, 6f),
                new Range(6f, 16f),
                new Range(-2f, 6f)
        });

        putAllSeasons("natures_spirit:carnation_fields", new Range[]{
                new Range(6f, 14f),
                new Range(14f, 22f),
                new Range(22f, 30f),
                new Range(10f, 18f)
        });

        putAllSeasons("natures_spirit:chaparral", new Range[]{
                new Range(6f, 16f),
                new Range(16f, 24f),
                new Range(24f, 32f),
                new Range(10f, 20f)
        });

        putAllSeasons("natures_spirit:coniferous_covert", new Range[]{
                new Range(-6f, 4f),
                new Range(4f, 12f),
                new Range(12f, 20f),
                new Range(2f, 10f)
        });

        putAllSeasons("natures_spirit:cypress_fields", new Range[]{
                new Range(2f, 10f),
                new Range(10f, 18f),
                new Range(18f, 26f),
                new Range(6f, 14f)
        });

        putAllSeasons("natures_spirit:drylands", new Range[]{
                new Range(18f, 28f),
                new Range(28f, 36f),
                new Range(36f, 46f),
                new Range(20f, 32f)
        });

        putAllSeasons("natures_spirit:dusty_slopes", new Range[]{
                new Range(14f, 24f),
                new Range(24f, 32f),
                new Range(32f, 40f),
                new Range(16f, 26f)
        });

        putAllSeasons("natures_spirit:fir_forest", new Range[]{
                new Range(-4f, 6f),
                new Range(6f, 14f),
                new Range(14f, 22f),
                new Range(4f, 12f)
        });

        putAllSeasons("natures_spirit:floral_ridges", new Range[]{
                new Range(4f, 12f),
                new Range(12f, 20f),
                new Range(20f, 28f),
                new Range(8f, 16f)
        });

        putAllSeasons("natures_spirit:flowering_shrubland", new Range[]{
                new Range(4f, 12f),
                new Range(12f, 20f),
                new Range(20f, 28f),
                new Range(8f, 16f)
        });

        putAllSeasons("natures_spirit:golden_wilds", new Range[]{
                new Range(0f, 8f),
                new Range(8f, 16f),
                new Range(16f, 24f),
                new Range(6f, 14f)
        });

        putAllSeasons("natures_spirit:heather_fields", new Range[]{
                new Range(0f, 8f),
                new Range(8f, 16f),
                new Range(16f, 24f),
                new Range(6f, 14f)
        });

        putAllSeasons("natures_spirit:lavender_fields", new Range[]{
                new Range(8f, 16f),
                new Range(16f, 24f),
                new Range(24f, 32f),
                new Range(10f, 20f)
        });

        putAllSeasons("natures_spirit:lively_dunes", new Range[]{
                new Range(16f, 26f),
                new Range(26f, 34f),
                new Range(34f, 44f),
                new Range(20f, 30f)
        });

        putAllSeasons("natures_spirit:maple_woodlands", new Range[]{
                new Range(-2f, 8f),
                new Range(8f, 16f),
                new Range(16f, 24f),
                new Range(4f, 14f)
        });

        putAllSeasons("natures_spirit:marigold_meadows", new Range[]{
                new Range(4f, 12f),
                new Range(12f, 20f),
                new Range(20f, 28f),
                new Range(8f, 16f)
        });

        putAllSeasons("natures_spirit:marsh", new Range[]{
                new Range(2f, 10f),
                new Range(10f, 18f),
                new Range(18f, 26f),
                new Range(6f, 14f)
        });

        putAllSeasons("natures_spirit:oak_savanna", new Range[]{
                new Range(10f, 20f),
                new Range(20f, 28f),
                new Range(28f, 34f),
                new Range(14f, 24f)
        });

        putAllSeasons("natures_spirit:prairie", new Range[]{
                new Range(-2f, 8f),
                new Range(8f, 18f),
                new Range(18f, 26f),
                new Range(6f, 14f)
        });

        putAllSeasons("natures_spirit:red_peaks", new Range[]{
                new Range(-8f, 2f),
                new Range(2f, 10f),
                new Range(10f, 18f),
                new Range(0f, 8f)
        });

        putAllSeasons("natures_spirit:redwood_forest", new Range[]{
                new Range(2f, 10f),
                new Range(10f, 18f),
                new Range(18f, 24f),
                new Range(6f, 14f)
        });

        putAllSeasons("terralith:alpha_islands", new Range[]{
                new Range(2, 7),   // Winter
                new Range(9, 15),  // Spring
                new Range(17, 24), // Summer
                new Range(10, 16)  // Fall
        });
        putAllSeasons("terralith:alpine_grove", new Range[]{
                new Range(-10, -3),
                new Range(1, 8),
                new Range(10, 17),
                new Range(3, 8)
        });
        putAllSeasons("terralith:alpine_highlands", new Range[]{
                new Range(-15, -5),
                new Range(-2, 6),
                new Range(6, 14),
                new Range(0, 6)
        });
        putAllSeasons("terralith:amethyst_canyon", new Range[]{
                new Range(5, 11),
                new Range(12, 18),
                new Range(20, 28),
                new Range(14, 19)
        });
        putAllSeasons("terralith:amethyst_rainforest", new Range[]{
                new Range(15, 20),
                new Range(22, 27),
                new Range(26, 32),
                new Range(20, 25)
        });
        putAllSeasons("terralith:ancient_sands", new Range[]{
                new Range(10, 20),
                new Range(20, 28),
                new Range(30, 42),
                new Range(22, 30)
        });
        putAllSeasons("terralith:arid_highlands", new Range[]{
                new Range(5, 15),
                new Range(15, 22),
                new Range(28, 35),
                new Range(20, 25)
        });
        putAllSeasons("terralith:ashen_savanna", new Range[]{
                new Range(18, 22),
                new Range(22, 28),
                new Range(30, 36),
                new Range(25, 30)
        });
        putAllSeasons("terralith:basalt_cliffs", new Range[]{
                new Range(2, 6),
                new Range(8, 14),
                new Range(15, 22),
                new Range(10, 15)
        });
        putAllSeasons("terralith:birch_taiga", new Range[]{
                new Range(-8, 0),
                new Range(4, 10),
                new Range(14, 22),
                new Range(7, 13)
        });
        putAllSeasons("terralith:blooming_plateau", new Range[]{
                new Range(0, 5),
                new Range(10, 17),
                new Range(22, 30),
                new Range(12, 18)
        });
        putAllSeasons("terralith:brushland", new Range[]{
                new Range(5, 10),
                new Range(15, 22),
                new Range(30, 38),
                new Range(18, 25)
        });
        putAllSeasons("terralith:bryce_canyon", new Range[]{
                new Range(0, 5),
                new Range(10, 17),
                new Range(28, 35),
                new Range(15, 22)
        });
        putAllSeasons("terralith:caldera", new Range[]{
                new Range(2, 8),
                new Range(10, 18),
                new Range(18, 26),
                new Range(12, 17)
        });
        putAllSeasons("terralith:cloud_forest", new Range[]{
                new Range(10, 15),
                new Range(18, 24),
                new Range(26, 30),
                new Range(17, 22)
        });
        putAllSeasons("terralith:cold_shrubland", new Range[]{
                new Range(-8, -2),
                new Range(2, 10),
                new Range(14, 20),
                new Range(6, 11)
        });
        putAllSeasons("terralith:desert_canyon", new Range[]{
                new Range(12, 18),
                new Range(20, 28),
                new Range(35, 45),
                new Range(22, 30)
        });
        putAllSeasons("terralith:emerald_peaks", new Range[]{
                new Range(-12, -4),
                new Range(0, 6),
                new Range(8, 15),
                new Range(-2, 5)
        });
        putAllSeasons("terralith:forested_highlands", new Range[]{
                new Range(-5, 2),
                new Range(5, 12),
                new Range(15, 22),
                new Range(8, 14)
        });
        putAllSeasons("terralith:frozen_cliffs", new Range[]{
                new Range(-20, -10),
                new Range(-8, 0),
                new Range(0, 5),
                new Range(-5, 0)
        });
        putAllSeasons("terralith:glacial_chasm", new Range[]{
                new Range(-25, -15),
                new Range(-10, -5),
                new Range(-2, 4),
                new Range(-8, -3)
        });
        putAllSeasons("terralith:gravel_desert", new Range[]{
                new Range(5, 12),
                new Range(15, 22),
                new Range(30, 38),
                new Range(20, 25)
        });
        putAllSeasons("terralith:haze_mountain", new Range[]{
                new Range(0, 4),
                new Range(10, 16),
                new Range(20, 28),
                new Range(12, 18)
        });
        putAllSeasons("terralith:highlands", new Range[]{
                new Range(0, 5),
                new Range(8, 15),
                new Range(20, 26),
                new Range(12, 18)
        });
        putAllSeasons("terralith:hot_shrubland", new Range[]{
                new Range(8, 12),
                new Range(15, 22),
                new Range(28, 36),
                new Range(20, 25)
        });
        putAllSeasons("terralith:jungle_mountains", new Range[]{
                new Range(22, 25),
                new Range(25, 30),
                new Range(30, 36),
                new Range(24, 28)
        });
        putAllSeasons("terralith:lavender_forest", new Range[]{
                new Range(5, 10),
                new Range(12, 18),
                new Range(22, 28),
                new Range(14, 20)
        });
        putAllSeasons("terralith:mirage_isles", new Range[]{
                new Range(12, 18),
                new Range(20, 26),
                new Range(30, 36),
                new Range(20, 25)
        });
        putAllSeasons("terralith:moonlight_valley", new Range[]{
                new Range(4, 9),
                new Range(12, 18),
                new Range(22, 28),
                new Range(14, 20)
        });
        putAllSeasons("terralith:mountain_steppe", new Range[]{
                new Range(-5, 2),
                new Range(5, 12),
                new Range(18, 24),
                new Range(10, 16)
        });
        putAllSeasons("terralith:rocky_mountains", new Range[]{
                new Range(-8, 0),
                new Range(5, 12),
                new Range(20, 28),
                new Range(10, 16)
        });
        putAllSeasons("terralith:snowy_badlands", new Range[]{
                new Range(-5, 0),
                new Range(2, 8),
                new Range(10, 16),
                new Range(4, 9)
        });
        putAllSeasons("terralith:steppe", new Range[]{
                new Range(-3, 5),
                new Range(10, 18),
                new Range(25, 32),
                new Range(15, 22)
        });
        putAllSeasons("terralith:temperate_highlands", new Range[]{
                new Range(2, 8),
                new Range(12, 18),
                new Range(22, 30),
                new Range(14, 20)
        });
        putAllSeasons("terralith:tropical_jungle", new Range[]{
                new Range(24, 28),
                new Range(27, 32),
                new Range(32, 38),
                new Range(26, 30)
        });
        putAllSeasons("terralith:volcanic_peaks", new Range[]{
                new Range(10, 16),
                new Range(15, 22),
                new Range(25, 32),
                new Range(18, 25)
        });
        putAllSeasons("terralith:volcanic_plains", new Range[]{
                new Range(12, 18),
                new Range(18, 24),
                new Range(28, 36),
                new Range(20, 26)
        });
        putAllSeasons("terralith:weeping_witch_forest", new Range[]{
                new Range(4, 9),
                new Range(10, 16),
                new Range(20, 26),
                new Range(12, 18)
        });
        putAllSeasons("terralith:windy_snowfields", new Range[]{
                new Range(-20, -10),
                new Range(-8, 0),
                new Range(0, 5),
                new Range(-6, -1)
        });
        putAllSeasons("terralith:windswept_spires", new Range[]{
                new Range(-5, 0),
                new Range(5, 10),
                new Range(16, 22),
                new Range(8, 14)
        });
        putAllSeasons("terralith:winter_taiga", new Range[]{
                new Range(-15, -5),
                new Range(-2, 5),
                new Range(10, 16),
                new Range(2, 7)
        });
        putAllSeasons("terralith:withered_badlands", new Range[]{
                new Range(5, 10),
                new Range(15, 22),
                new Range(28, 36),
                new Range(18, 24)
        });
        putAllSeasons("terralith:wooded_badlands", new Range[]{
                new Range(5, 10),
                new Range(12, 18),
                new Range(25, 32),
                new Range(16, 22)
        });
        putAllSeasons("terralith:yellowstone", new Range[]{
                new Range(0, 5),
                new Range(10, 16),
                new Range(20, 26),
                new Range(12, 18)
        });
        putAllSeasons("terralith:yosemite_cliffs", new Range[]{
                new Range(-8, -2),
                new Range(5, 12),
                new Range(18, 26),
                new Range(10, 16)
        });
        putAllSeasons("terralith:yosemite_lowlands", new Range[]{
                new Range(0, 6),
                new Range(10, 17),
                new Range(22, 30),
                new Range(14, 20)
        });
        putAllSeasons("terralith:zen_garden", new Range[]{
                new Range(5, 10),
                new Range(12, 18),
                new Range(22, 28),
                new Range(14, 20)
        });
        putAllSeasons("terralith:ancient_dunes", new Range[]{
                new Range(8, 14),
                new Range(16, 24),
                new Range(34, 42),
                new Range(20, 28)
        });
        putAllSeasons("terralith:crimson_gardens", new Range[]{
                new Range(10, 15),
                new Range(15, 22),
                new Range(24, 30),
                new Range(16, 21)
        });
        putAllSeasons("terralith:skylands", new Range[]{
                new Range(-5, 2),
                new Range(4, 10),
                new Range(14, 22),
                new Range(6, 12)
        });
        putAllSeasons("terralith:skylands_forest", new Range[]{
                new Range(0, 5),
                new Range(8, 14),
                new Range(18, 25),
                new Range(10, 16)
        });
        putAllSeasons("terralith:skylands_plateau", new Range[]{
                new Range(-2, 4),
                new Range(7, 12),
                new Range(16, 22),
                new Range(9, 14)
        });
        putAllSeasons("terralith:skylands_valley", new Range[]{
                new Range(2, 6),
                new Range(10, 16),
                new Range(20, 26),
                new Range(12, 18)
        });
        putAllSeasons("terralith:windswept_archipelago", new Range[]{
                new Range(6, 10),
                new Range(12, 18),
                new Range(24, 30),
                new Range(16, 22)
        });
        putAllSeasons("terralith:oasis", new Range[]{
                new Range(10, 16),
                new Range(20, 26),
                new Range(30, 38),
                new Range(24, 30)
        });
        putAllSeasons("terralith:savanna_slopes", new Range[]{
                new Range(14, 20),
                new Range(22, 28),
                new Range(30, 36),
                new Range(24, 30)
        });
        putAllSeasons("terralith:red_oasis", new Range[]{
                new Range(10, 16),
                new Range(20, 26),
                new Range(32, 40),
                new Range(24, 30)
        });
        putAllSeasons("terralith:glowcave", new Range[]{
                new Range(8, 12),
                new Range(14, 20),
                new Range(22, 28),
                new Range(16, 22)
        });
        putAllSeasons("terralith:overgrown_cliffs", new Range[]{
                new Range(2, 8),
                new Range(10, 16),
                new Range(20, 26),
                new Range(12, 18)
        });
        putAllSeasons("terralith:lavender_pines", new Range[]{
                new Range(0, 5),
                new Range(10, 16),
                new Range(20, 28),
                new Range(12, 18)
        });
        putAllSeasons("terralith:rainbow_rainforest", new Range[]{
                new Range(20, 24),
                new Range(24, 28),
                new Range(30, 36),
                new Range(26, 30)
        });
        putAllSeasons("terralith:crystal_canyon", new Range[]{
                new Range(5, 10),
                new Range(12, 18),
                new Range(26, 32),
                new Range(18, 24)
        });
        putAllSeasons("terralith:desert_spires", new Range[]{
                new Range(10, 16),
                new Range(20, 28),
                new Range(35, 44),
                new Range(22, 30)
        });
        putAllSeasons("terralith:ebony_woods", new Range[]{
                new Range(6, 10),
                new Range(12, 18),
                new Range(20, 26),
                new Range(14, 20)
        });
        putAllSeasons("terralith:ancient_forest", new Range[]{
                new Range(0, 5),
                new Range(10, 16),
                new Range(20, 26),
                new Range(12, 18)
        });
        putAllSeasons("terralith:orchid_swamp", new Range[]{
                new Range(6, 12),
                new Range(14, 20),
                new Range(24, 30),
                new Range(16, 22)
        });
        putAllSeasons("terralith:scarlet_forest", new Range[]{
                new Range(8, 14),
                new Range(16, 22),
                new Range(26, 32),
                new Range(18, 24)
        });
        putAllSeasons("terralith:snowy_taiga_valley", new Range[]{
                new Range(-10, -3),
                new Range(0, 6),
                new Range(10, 18),
                new Range(2, 8)
        });
        putAllSeasons("terralith:twilight_peak", new Range[]{
                new Range(-4, 2),
                new Range(5, 12),
                new Range(16, 22),
                new Range(8, 14)
        });
        putAllSeasons("terralith:warm_beach_cliff", new Range[]{
                new Range(12, 18),
                new Range(18, 24),
                new Range(26, 32),
                new Range(20, 26)
        });
        putAllSeasons("terralith:wetland_valley", new Range[]{
                new Range(8, 14),
                new Range(14, 20),
                new Range(22, 28),
                new Range(16, 22)
        });
        putAllSeasons("terralith:windswept_savanna", new Range[]{
                new Range(12, 18),
                new Range(20, 26),
                new Range(30, 36),
                new Range(22, 28)
        });

        // TerraFirmaCraft – main biomes
        putAllSeasons("tfc:ocean", new Range[]{
                new Range(-2f, 8f),
                new Range(4f, 14f),
                new Range(12f, 24f),
                new Range(6f, 16f)
        });
        putAllSeasons("tfc:deep_ocean", new Range[]{
                new Range(-4f, 6f),
                new Range(2f, 12f),
                new Range(10f, 20f),
                new Range(4f, 14f)
        });
        putAllSeasons("tfc:plains", new Range[]{
                new Range(-6f, 8f),
                new Range(4f, 18f),
                new Range(14f, 30f),
                new Range(6f, 20f)
        });
        putAllSeasons("tfc:high_plains", new Range[]{
                new Range(-10f, 4f),
                new Range(-2f, 16f),
                new Range(10f, 26f),
                new Range(2f, 18f)
        });
        putAllSeasons("tfc:swamp", new Range[]{
                new Range(0f, 12f),
                new Range(8f, 22f),
                new Range(16f, 32f),
                new Range(8f, 22f)
        });
        putAllSeasons("tfc:mountains", new Range[]{
                new Range(-16f, -2f),
                new Range(-8f, 8f),
                new Range(2f, 18f),
                new Range(-6f, 10f)
        });
        putAllSeasons("tfc:rolling_hills", new Range[]{
                new Range(-4f, 10f),
                new Range(6f, 20f),
                new Range(14f, 28f),
                new Range(6f, 20f)
        });
        putAllSeasons("tfc:high_hills", new Range[]{
                new Range(-12f, 2f),
                new Range(-2f, 14f),
                new Range(8f, 24f),
                new Range(-4f, 12f)
        });
        putAllSeasons("tfc:mountain_range", new Range[]{
                new Range(-18f, -4f),
                new Range(-10f, 6f),
                new Range(0f, 16f),
                new Range(-8f, 8f)
        });
        putAllSeasons("tfc:salt_swamp", new Range[]{
                new Range(4f, 16f),
                new Range(10f, 24f),
                new Range(18f, 34f),
                new Range(10f, 24f)
        });
        putAllSeasons("tfc:peat_bog", new Range[]{
                new Range(-6f, 8f),
                new Range(2f, 18f),
                new Range(10f, 26f),
                new Range(2f, 18f)
        });

        // TerraFirmaCraft – technical biomes
        putAllSeasons("tfc:river", new Range[]{
                new Range(-2f, 10f),
                new Range(6f, 18f),
                new Range(12f, 26f),
                new Range(6f, 18f)
        });
        putAllSeasons("tfc:beach", new Range[]{
                new Range(2f, 14f),
                new Range(8f, 22f),
                new Range(16f, 30f),
                new Range(8f, 22f)
        });
        putAllSeasons("tfc:gravel_beach", new Range[]{
                new Range(0f, 12f),
                new Range(6f, 20f),
                new Range(14f, 28f),
                new Range(6f, 20f)
        });
        putAllSeasons("tfc:lake", new Range[]{
                new Range(-2f, 10f),
                new Range(6f, 18f),
                new Range(12f, 26f),
                new Range(6f, 18f)
        });
        putAllSeasons("tfc:shore", new Range[]{
                new Range(2f, 12f),
                new Range(8f, 20f),
                new Range(14f, 28f),
                new Range(8f, 20f)
        });
        putAllSeasons("tfc:high_hills_edge", new Range[]{
                new Range(-8f, 6f),
                new Range(0f, 16f),
                new Range(10f, 24f),
                new Range(-2f, 14f)
        });
        putAllSeasons("tfc:mountain_edge", new Range[]{
                new Range(-10f, 2f),
                new Range(-2f, 12f),
                new Range(6f, 20f),
                new Range(-4f, 10f)
        });
        putAllSeasons("tfc:mountain_range_edge", new Range[]{
                new Range(-14f, 0f),
                new Range(-6f, 10f),
                new Range(4f, 18f),
                new Range(-6f, 8f)
        });
        putAllSeasons("tfc:foothills", new Range[]{
                new Range(-6f, 8f),
                new Range(2f, 18f),
                new Range(12f, 26f),
                new Range(4f, 18f)
        });
        putAllSeasons("tfc:lakeshore", new Range[]{
                new Range(0f, 12f),
                new Range(8f, 20f),
                new Range(14f, 28f),
                new Range(8f, 20f)
        });
        putAllSeasons("tfc:riverbank", new Range[]{
                new Range(-2f, 10f),
                new Range(6f, 18f),
                new Range(12f, 26f),
                new Range(6f, 18f)
        });
        putAllSeasons("tfc:estuary", new Range[]{
                new Range(2f, 14f),
                new Range(10f, 22f),
                new Range(16f, 30f),
                new Range(10f, 22f)
        });




    }






    /**
     * Populates both SEASON_RANGES and SEASON_CLAMPS (automatically‐derived)
     */
    private static void putAllSeasons(String biomeKey, Range[] ranges) {
        Set<ResourceLocation> ids = resolveBiomeIds(biomeKey);
        var seasons = Season.values();
        for (ResourceLocation id : ids) {
            for (int i = 0; i < seasons.length; i++) {
                Season season = seasons[i];
                Range r = ranges[i];
                SEASON_RANGES.get(season).put(id, r);
                SEASON_CLAMPS.get(season).put(id, deriveDaily(r));
            }
        }
    }

    /**
     * Copies one biome’s settings (all seasons) to another.
     */
    private static void mirrorBiome(String dstKey, String srcKey) {
        Set<ResourceLocation> dsts = resolveBiomeIds(dstKey);
        Set<ResourceLocation> srcs = resolveBiomeIds(srcKey);
        if (srcs.isEmpty()) {
            ProjectAtmosphere.LOGGER.warn("mirrorBiome: no source biome resolved for '{}'", srcKey);
            return;
        }
        ResourceLocation src = srcs.iterator().next();
        for (Season s : Season.values()) {
            Range r = SEASON_RANGES.get(s).get(src);
            DailyRange d = SEASON_CLAMPS.get(s).get(src);
            if (r == null || d == null) {
                ProjectAtmosphere.LOGGER.warn("mirrorBiome: source '{}' has no ranges for season {}", src, s);
                continue;
            }
            for (ResourceLocation dst : dsts) {
                SEASON_RANGES.get(s).put(dst, r);
                SEASON_CLAMPS.get(s).put(dst, d);
            }
        }
    }

    /**
     * For biomes that never change with season (Nether, End).
     */
    private static void putConstSeasons(String biomeKey, Range r, DailyRange d) {
        Set<ResourceLocation> ids = resolveBiomeIds(biomeKey);
        for (ResourceLocation id : ids) {
            for (Season s : Season.values()) {
                SEASON_RANGES.get(s).put(id, r);
                SEASON_CLAMPS.get(s).put(id, d);
            }
        }
    }

    /**
     * Resolve a biome key which may be namespaced (e.g. "modid:path") or just a path (e.g. "plains").
     * If un-namespaced and not a vanilla biome, scans the biome registry for matching paths across mods.
     * - Unique match: returns that id
     * - Multiple matches: returns all matches and logs info
     * - No matches: falls back to minecraft namespace and logs a warning
     */
    private static Set<ResourceLocation> resolveBiomeIds(String biomeKey) {
        // Explicit namespace provided
        if (biomeKey.indexOf(':') >= 0) {
            ResourceLocation parsed = ResourceLocation.tryParse(biomeKey);
            if (parsed != null) {
                return Set.of(parsed);
            } else {
                ProjectAtmosphere.LOGGER.warn("Invalid biome id '{}'", biomeKey);
                return Set.of();
            }
        }

        ProjectAtmosphere.LOGGER.warn(
                "Biome key '{}' is missing a namespace; assuming minecraft:{} when unique. " +
                        "Prefer explicit ids such as minecraft:desert or biomesoplenty:bayou to avoid ambiguity.",
                biomeKey, biomeKey);

        // Default to minecraft namespace first
        ResourceLocation def = ResourceLocation.withDefaultNamespace(biomeKey);

        // If the vanilla key exists, prefer it
        if (ForgeRegistries.BIOMES.containsKey(def)) {
            return Set.of(def);
        }

        // Otherwise, search all registered biomes for matching path
        Set<ResourceLocation> matches = new LinkedHashSet<>();
        for (ResourceLocation key : ForgeRegistries.BIOMES.getKeys()) {
            if (key.getPath().equals(biomeKey)) {
                matches.add(key);
            }
        }

        if (matches.isEmpty()) {
            ProjectAtmosphere.LOGGER.warn("No biome found with path '{}' in registry; defaulting to {}", biomeKey, def);
            return Set.of(def);
        }
        if (matches.size() > 1) {
            ProjectAtmosphere.LOGGER.warn("Multiple biomes match path '{}': {} — specify the mod id.", biomeKey, matches);
        }
        return matches;
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
