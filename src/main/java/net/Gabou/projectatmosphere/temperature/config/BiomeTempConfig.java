package net.Gabou.projectatmosphere.temperature.config;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.HashMap;

public class BiomeTempConfig {

    /**
     * This class is used to define the temperature range for each biome.
     * The temperature is represented in Celsius.
     * The default range is [-20, 56] °C.
     */
    public record Range(float minC, float maxC){}

    /**
     * This class is used to define the daily temperature range for each biome.
     * The temperature is represented in Celsius.
     * The daily range is defined as [minMin, maxMin] and [minMax, maxMax].
     */
    public record DailyRange(float minMin, float maxMin, float minMax, float maxMax) {}

    /**
     * The default temperature range for all biomes is [-20, 56] °C.
     * This range is used by Forge's BiomeManager and aligns with the vanilla biome temperature system.
     * It represents a sensible default for most biomes, covering a wide variety of environmental conditions.
     */
    private static final Range DEFAULT = new Range(-20f, 56f);
    /**
     * A map that stores the temperature range for each biome.
     * The key is the biome's ResourceLocation and the value is the Range object.
     */
    private static final Map<ResourceLocation, Range> RANGES = new HashMap<>();
    /**
     * A map that stores the daily temperature range for each biome.
     * The key is the biome's ResourceLocation and the value is the DailyRange object.
     */
    public static final Map<ResourceLocation, DailyRange> DAILY_CLAMPS = new HashMap<>();


    /**
     * A static block that initializes the temperature ranges for each biome.
     * It populates the RANGES map with the default range for every vanilla biome.
     * It also sets specific temperature ranges for certain biomes based on real-world data.
     */
    static {
        // 1) Populate every vanilla biome with the default range
        for (ResourceLocation id : ForgeRegistries.BIOMES.getKeys()) {
            if (!("minecraft".equals(id.getNamespace()))) {
                RANGES.put(id, DEFAULT);
            }
        }
        // <editor-fold desc="Overworld Biome Temperature Ranges">

//        // 2) Overworld‐specific real‐world temperature ranges (°C):
//        RANGES.put(ResourceLocation.withDefaultNamespace("frozen_ocean"), new Range(-2f, 2f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("cold_ocean"), new Range(2f, 8f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("deep_cold_ocean"), new Range(2f, 4f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("deep_frozen_ocean"), new Range(-2f, 0f));
//
//        RANGES.put(ResourceLocation.withDefaultNamespace("lukewarm_ocean"), new Range(18f, 23f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("deep_lukewarm_ocean"), new Range(6f, 11f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("warm_ocean"), new Range(23f, 29f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("deep_ocean"), new Range(2f, 4f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("ocean"), new Range(2f, 30f));
//
//        RANGES.put(ResourceLocation.withDefaultNamespace("beach"), new Range(2f, 32f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("snowy_beach"), new Range(-5f, 10f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("river"), new Range(0f, 25f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("frozen_river"), new Range(-2f, 2f));
//
//        RANGES.put(ResourceLocation.withDefaultNamespace("plains"), new Range(-5f, 35f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("sunflower_plains"), new Range(0f, 30f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("meadow"), new Range(-5f, 25f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("snowy_plains"), new Range(-30f, 5f));
//
//        RANGES.put(ResourceLocation.withDefaultNamespace("forest"), new Range(-5f, 30f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("flower_forest"), new Range(0f, 30f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("dark_forest"), new Range(-5f, 25f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("birch_forest"), new Range(-10f, 25f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("old_growth_birch_forest"), new Range(-15f, 20f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("old_growth_spruce_taiga"), new Range(-25f, 10f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("old_growth_pine_taiga"), new Range(-25f, 10f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("windswept_forest"), new Range(-5f, 20f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("grove"), new Range(0f, 25f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("cherry_grove"), new Range(0f, 25f));
//
//        RANGES.put(ResourceLocation.withDefaultNamespace("taiga"), new Range(-25f, 15f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("snowy_taiga"), new Range(-25f, 5f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("windswept_gravelly_hills"), new Range(-10f, 15f));
//
//        RANGES.put(ResourceLocation.withDefaultNamespace("ice_spikes"), new Range(-20f, 0f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("frozen_peaks"), new Range(-40f, -5f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("jagged_peaks"), new Range(-40f, 5f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("stony_peaks"), new Range(-20f, 10f));
//
//        RANGES.put(ResourceLocation.withDefaultNamespace("snowy_slopes"), new Range(-30f, 0f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("sparse_jungle"), new Range(20f, 35f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("jungle"), new Range(20f, 40f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("bamboo_jungle"), new Range(20f, 40f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("swamp"), new Range(10f, 35f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("mangrove_swamp"), new Range(20f, 35f));
//
//        RANGES.put(ResourceLocation.withDefaultNamespace("desert"), new Range(30f, 56f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("badlands"), new Range(10f, 45f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("eroded_badlands"), new Range(10f, 45f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("wooded_badlands"), new Range(5f, 40f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("windswept_hills"), new Range(0f, 30f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("savanna"), new Range(18f, 40f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("savanna_plateau"), new Range(18f, 35f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("windswept_savanna"), new Range(15f, 35f));
//
//        RANGES.put(ResourceLocation.withDefaultNamespace("dripstone_caves"), new Range(8f, 14f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("lush_caves"), new Range(12f, 18f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("deep_dark"), new Range(10f, 14f));
//        RANGES.put(ResourceLocation.withDefaultNamespace("mushroom_fields"), new Range(10f, 18f));

        RANGES.put(ResourceLocation.withDefaultNamespace("frozen_ocean"), new Range(-2f, 2f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("frozen_ocean"), new DailyRange(-2f, 1f, 0f, 2f));

        RANGES.put(ResourceLocation.withDefaultNamespace("savanna_plateau"), new Range(18f, 35f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("savanna_plateau"), new DailyRange(18f, 24f, 28f, 35f));

        RANGES.put(ResourceLocation.withDefaultNamespace("taiga"), new Range(-25f, 15f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("taiga"), new DailyRange(-25f, -5f, 5f, 15f));

        RANGES.put(ResourceLocation.withDefaultNamespace("savanna"), new Range(18f, 40f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("savanna"), new DailyRange(18f, 25f, 30f, 40f));

        RANGES.put(ResourceLocation.withDefaultNamespace("dripstone_caves"), new Range(8f, 14f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("dripstone_caves"), new DailyRange(8f, 10f, 12f, 14f));

        RANGES.put(ResourceLocation.withDefaultNamespace("swamp"), new Range(10f, 35f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("swamp"), new DailyRange(10f, 22f, 25f, 35f));

        RANGES.put(ResourceLocation.withDefaultNamespace("basalt_deltas"), new Range(40f, 80f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("basalt_deltas"), new DailyRange(40f, 60f, 65f, 80f));

        RANGES.put(ResourceLocation.withDefaultNamespace("ice_spikes"), new Range(-20f, 0f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("ice_spikes"), new DailyRange(-20f, -10f, -5f, 0f));

        RANGES.put(ResourceLocation.withDefaultNamespace("cherry_grove"), new Range(0f, 25f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("cherry_grove"), new DailyRange(0f, 12f, 18f, 25f));

        RANGES.put(ResourceLocation.withDefaultNamespace("crimson_forest"), new Range(40f, 70f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("crimson_forest"), new DailyRange(40f, 55f, 60f, 70f));

        RANGES.put(ResourceLocation.withDefaultNamespace("frozen_peaks"), new Range(-40f, -5f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("frozen_peaks"), new DailyRange(-45f, -25f, -35f, -10f));

        RANGES.put(ResourceLocation.withDefaultNamespace("dark_forest"), new Range(-5f, 25f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("dark_forest"), new DailyRange(-5f, 10f, 15f, 25f));

        RANGES.put(ResourceLocation.withDefaultNamespace("lush_caves"), new Range(12f, 18f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("lush_caves"), new DailyRange(12f, 15f, 16f, 18f));

        RANGES.put(ResourceLocation.withDefaultNamespace("old_growth_spruce_taiga"), new Range(-25f, 10f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("old_growth_spruce_taiga"), new DailyRange(-25f, -5f, 5f, 10f));

        RANGES.put(ResourceLocation.withDefaultNamespace("deep_dark"), new Range(10f, 14f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("deep_dark"), new DailyRange(10f, 12f, 13f, 14f));

        RANGES.put(ResourceLocation.withDefaultNamespace("frozen_river"), new Range(-2f, 2f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("frozen_river"), new DailyRange(-2f, 0f, 0f, 2f));

        RANGES.put(ResourceLocation.withDefaultNamespace("lukewarm_ocean"), new Range(18f, 23f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("lukewarm_ocean"), new DailyRange(18f, 20f, 21f, 23f));

        RANGES.put(ResourceLocation.withDefaultNamespace("mushroom_fields"), new Range(10f, 18f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("mushroom_fields"), new DailyRange(10f, 14f, 15f, 18f));

        RANGES.put(ResourceLocation.withDefaultNamespace("warm_ocean"), new Range(23f, 29f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("warm_ocean"), new DailyRange(23f, 25f, 27f, 29f));

        RANGES.put(ResourceLocation.withDefaultNamespace("forest"), new Range(-5f, 30f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("forest"), new DailyRange(-5f, 15f, 20f, 30f));

        RANGES.put(ResourceLocation.withDefaultNamespace("end_midlands"), new Range(-100f, -50f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("end_midlands"), new DailyRange(-100f, -75f, -60f, -50f));

        RANGES.put(ResourceLocation.withDefaultNamespace("windswept_forest"), new Range(-5f, 20f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("windswept_forest"), new DailyRange(-5f, 10f, 15f, 20f));

        RANGES.put(ResourceLocation.withDefaultNamespace("deep_ocean"), new Range(2f, 4f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("deep_ocean"), new DailyRange(2f, 3f, 3f, 4f));

        RANGES.put(ResourceLocation.withDefaultNamespace("sunflower_plains"), new Range(0f, 30f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("sunflower_plains"), new DailyRange(0f, 15f, 20f, 30f));

        RANGES.put(ResourceLocation.withDefaultNamespace("stony_peaks"), new Range(-20f, 10f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("stony_peaks"), new DailyRange(-20f, -5f, 0f, 10f));

        RANGES.put(ResourceLocation.withDefaultNamespace("stony_shore"), new Range(2f, 28f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("stony_shore"), new DailyRange(2f, 15f, 20f, 28f));

        RANGES.put(ResourceLocation.withDefaultNamespace("nether_wastes"), new Range(60f, 90f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("nether_wastes"), new DailyRange(60f, 75f, 80f, 90f));

        RANGES.put(ResourceLocation.withDefaultNamespace("deep_lukewarm_ocean"), new Range(6f, 11f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("deep_lukewarm_ocean"), new DailyRange(6f, 8f, 9f, 11f));

        RANGES.put(ResourceLocation.withDefaultNamespace("flower_forest"), new Range(0f, 30f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("flower_forest"), new DailyRange(0f, 15f, 20f, 30f));

        RANGES.put(ResourceLocation.withDefaultNamespace("old_growth_birch_forest"), new Range(-15f, 20f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("old_growth_birch_forest"), new DailyRange(-15f, 0f, 10f, 20f));

        RANGES.put(ResourceLocation.withDefaultNamespace("desert"), new Range(30f, 56f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("desert"), new DailyRange(10f, 30f, 40f, 56f));

        RANGES.put(ResourceLocation.withDefaultNamespace("snowy_taiga"), new Range(-25f, 5f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("snowy_taiga"), new DailyRange(-25f, -10f, 0f, 5f));

        RANGES.put(ResourceLocation.withDefaultNamespace("beach"), new Range(2f, 32f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("beach"), new DailyRange(2f, 20f, 25f, 32f));

        RANGES.put(ResourceLocation.withDefaultNamespace("grove"), new Range(0f, 25f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("grove"), new DailyRange(0f, 10f, 15f, 25f));

        RANGES.put(ResourceLocation.withDefaultNamespace("deep_frozen_ocean"), new Range(-2f, 0f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("deep_frozen_ocean"), new DailyRange(-2f, -1f, -1f, 0f));

        RANGES.put(ResourceLocation.withDefaultNamespace("river"), new Range(0f, 25f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("river"), new DailyRange(0f, 15f, 20f, 25f));

        RANGES.put(ResourceLocation.withDefaultNamespace("old_growth_pine_taiga"), new Range(-25f, 10f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("old_growth_pine_taiga"), new DailyRange(-25f, -5f, 5f, 10f));

        RANGES.put(ResourceLocation.withDefaultNamespace("the_void"), new Range(-273f, -273f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("the_void"), new DailyRange(-273f, -273f, -273f, -273f));

        RANGES.put(ResourceLocation.withDefaultNamespace("deep_cold_ocean"), new Range(2f, 4f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("deep_cold_ocean"), new DailyRange(2f, 3f, 3f, 4f));

        RANGES.put(ResourceLocation.withDefaultNamespace("windswept_gravelly_hills"), new Range(-10f, 15f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("windswept_gravelly_hills"), new DailyRange(-10f, 5f, 10f, 15f));

        RANGES.put(ResourceLocation.withDefaultNamespace("snowy_plains"), new Range(-30f, 5f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("snowy_plains"), new DailyRange(-30f, -10f, -5f, 5f));

        RANGES.put(ResourceLocation.withDefaultNamespace("end_highlands"), new Range(-100f, -50f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("end_highlands"), new DailyRange(-100f, -75f, -60f, -50f));

        RANGES.put(ResourceLocation.withDefaultNamespace("jagged_peaks"), new Range(-40f, 5f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("jagged_peaks"), new DailyRange(-40f, -20f, -10f, 5f));

        RANGES.put(ResourceLocation.withDefaultNamespace("eroded_badlands"), new Range(10f, 45f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("eroded_badlands"), new DailyRange(10f, 25f, 30f, 45f));

        RANGES.put(ResourceLocation.withDefaultNamespace("bamboo_jungle"), new Range(20f, 40f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("bamboo_jungle"), new DailyRange(22f, 26f, 30f, 40f));

        RANGES.put(ResourceLocation.withDefaultNamespace("end_barrens"), new Range(-100f, -50f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("end_barrens"), new DailyRange(-100f, -75f, -60f, -50f));

        RANGES.put(ResourceLocation.withDefaultNamespace("plains"), new Range(-5f, 35f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("plains"), new DailyRange(-5f, 15f, 20f, 35f));

        RANGES.put(ResourceLocation.withDefaultNamespace("small_end_islands"), new Range(-100f, -50f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("small_end_islands"), new DailyRange(-100f, -75f, -60f, -50f));

        RANGES.put(ResourceLocation.withDefaultNamespace("meadow"), new Range(-5f, 25f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("meadow"), new DailyRange(-5f, 10f, 15f, 25f));

        RANGES.put(ResourceLocation.withDefaultNamespace("the_end"), new Range(-100f, -50f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("the_end"), new DailyRange(-100f, -75f, -60f, -50f));

        RANGES.put(ResourceLocation.withDefaultNamespace("snowy_beach"), new Range(-5f, 10f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("snowy_beach"), new DailyRange(-5f, 3f, 6f, 10f));

        RANGES.put(ResourceLocation.withDefaultNamespace("sparse_jungle"), new Range(20f, 35f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("sparse_jungle"), new DailyRange(22f, 25f, 30f, 35f));

        RANGES.put(ResourceLocation.withDefaultNamespace("jungle"), new Range(20f, 40f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("jungle"), new DailyRange(22f, 27f, 29f, 35f));

        RANGES.put(ResourceLocation.withDefaultNamespace("snowy_slopes"), new Range(-30f, 0f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("snowy_slopes"), new DailyRange(-30f, -15f, -5f, 0f));

        RANGES.put(ResourceLocation.withDefaultNamespace("birch_forest"), new Range(-10f, 25f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("birch_forest"), new DailyRange(-10f, 5f, 15f, 25f));

        RANGES.put(ResourceLocation.withDefaultNamespace("mangrove_swamp"), new Range(20f, 35f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("mangrove_swamp"), new DailyRange(21f, 26f, 29f, 35f));

        RANGES.put(ResourceLocation.withDefaultNamespace("ocean"), new Range(2f, 30f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("ocean"), new DailyRange(2f, 18f, 20f, 30f));

        RANGES.put(ResourceLocation.withDefaultNamespace("cold_ocean"), new Range(2f, 8f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("cold_ocean"), new DailyRange(2f, 5f, 6f, 8f));

        RANGES.put(ResourceLocation.withDefaultNamespace("soul_sand_valley"), new Range(45f, 75f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("soul_sand_valley"), new DailyRange(45f, 60f, 65f, 75f));

        RANGES.put(ResourceLocation.withDefaultNamespace("warped_forest"), new Range(45f, 75f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("warped_forest"), new DailyRange(45f, 60f, 65f, 75f));

        RANGES.put(ResourceLocation.withDefaultNamespace("badlands"), new Range(10f, 45f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("badlands"), new DailyRange(10f, 25f, 30f, 45f));

        RANGES.put(ResourceLocation.withDefaultNamespace("windswept_hills"), new Range(0f, 30f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("windswept_hills"), new DailyRange(0f, 15f, 20f, 30f));

        RANGES.put(ResourceLocation.withDefaultNamespace("windswept_savanna"), new Range(15f, 35f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("windswept_savanna"), new DailyRange(15f, 25f, 28f, 35f));

        RANGES.put(ResourceLocation.withDefaultNamespace("wooded_badlands"), new Range(5f, 40f));
        DAILY_CLAMPS.put(ResourceLocation.withDefaultNamespace("wooded_badlands"), new DailyRange(5f, 20f, 30f, 40f));

        // 3) Nether & End biomes remain at DEFAULT [-20,56], or override as needed
        //    e.g. you could do:
        //    RANGES.put(ResourceLocation.withDefaultNamespace("nether_wastes"), new Range(200f, 800f));
        // </editor-fold>
    }



/**
 * Returns the [minC, maxC] range for this biome,
 * or a sensible default if none is specified.
 */

public static Range getRange(ResourceLocation biome) {
    if (biome == null) return DEFAULT;
    return RANGES.getOrDefault(biome, DEFAULT);
}


    public static DailyRange getClamp(ResourceLocation biome) {
        return DAILY_CLAMPS.get(biome);
    }
}
