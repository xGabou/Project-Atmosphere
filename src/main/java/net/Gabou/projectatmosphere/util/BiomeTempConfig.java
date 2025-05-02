package net.Gabou.projectatmosphere.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.HashMap;

public class BiomeTempConfig {

    /**
     * A simple min/max range in °C
     */
    public record Range(float minC, float maxC) {
    }

    private static final Range DEFAULT = new Range(-20f, 56f);
    private static final Map<ResourceLocation, Range> RANGES = new HashMap<>();

    /**
      * The default temperature range for all biomes is [-20, 56] °C.
      *
      * This range is used by Forge's BiomeManager and aligns with the vanilla biome temperature system.
      * It represents a sensible default for most biomes, covering a wide variety of environmental conditions.
      *
      * - Minimum temperature: -20°C
      * - Maximum temperature: 56°C
      *
      * This default ensures compatibility with both modded and vanilla biomes unless explicitly overridden.
      */
    static {
        // 1) Populate every vanilla biome with the default range
        for (ResourceLocation id : ForgeRegistries.BIOMES.getKeys()) {
            if (!("minecraft".equals(id.getNamespace()))) {
                RANGES.put(id, DEFAULT);
            }
        }

        // 2) Overworld‐specific real‐world temperature ranges (°C):
        RANGES.put(ResourceLocation.withDefaultNamespace("frozen_ocean"), new Range(-2f, 2f));
        RANGES.put(ResourceLocation.withDefaultNamespace("cold_ocean"), new Range(2f, 8f));
        RANGES.put(ResourceLocation.withDefaultNamespace("deep_cold_ocean"), new Range(2f, 4f));
        RANGES.put(ResourceLocation.withDefaultNamespace("deep_frozen_ocean"), new Range(-2f, 0f));

        RANGES.put(ResourceLocation.withDefaultNamespace("lukewarm_ocean"), new Range(18f, 23f));
        RANGES.put(ResourceLocation.withDefaultNamespace("deep_lukewarm_ocean"), new Range(6f, 11f));
        RANGES.put(ResourceLocation.withDefaultNamespace("warm_ocean"), new Range(23f, 29f));
        RANGES.put(ResourceLocation.withDefaultNamespace("deep_ocean"), new Range(2f, 4f));
        RANGES.put(ResourceLocation.withDefaultNamespace("ocean"), new Range(2f, 30f));

        RANGES.put(ResourceLocation.withDefaultNamespace("beach"), new Range(2f, 32f));
        RANGES.put(ResourceLocation.withDefaultNamespace("snowy_beach"), new Range(-5f, 10f));
        RANGES.put(ResourceLocation.withDefaultNamespace("river"), new Range(0f, 25f));
        RANGES.put(ResourceLocation.withDefaultNamespace("frozen_river"), new Range(-2f, 2f));

        RANGES.put(ResourceLocation.withDefaultNamespace("plains"), new Range(-5f, 35f));
        RANGES.put(ResourceLocation.withDefaultNamespace("sunflower_plains"), new Range(0f, 30f));
        RANGES.put(ResourceLocation.withDefaultNamespace("meadow"), new Range(-5f, 25f));
        RANGES.put(ResourceLocation.withDefaultNamespace("snowy_plains"), new Range(-30f, 5f));

        RANGES.put(ResourceLocation.withDefaultNamespace("forest"), new Range(-5f, 30f));
        RANGES.put(ResourceLocation.withDefaultNamespace("flower_forest"), new Range(0f, 30f));
        RANGES.put(ResourceLocation.withDefaultNamespace("dark_forest"), new Range(-5f, 25f));
        RANGES.put(ResourceLocation.withDefaultNamespace("birch_forest"), new Range(-10f, 25f));
        RANGES.put(ResourceLocation.withDefaultNamespace("old_growth_birch_forest"), new Range(-15f, 20f));
        RANGES.put(ResourceLocation.withDefaultNamespace("old_growth_spruce_taiga"), new Range(-25f, 10f));
        RANGES.put(ResourceLocation.withDefaultNamespace("old_growth_pine_taiga"), new Range(-25f, 10f));
        RANGES.put(ResourceLocation.withDefaultNamespace("windswept_forest"), new Range(-5f, 20f));
        RANGES.put(ResourceLocation.withDefaultNamespace("grove"), new Range(0f, 25f));
        RANGES.put(ResourceLocation.withDefaultNamespace("cherry_grove"), new Range(0f, 25f));

        RANGES.put(ResourceLocation.withDefaultNamespace("taiga"), new Range(-25f, 15f));
        RANGES.put(ResourceLocation.withDefaultNamespace("snowy_taiga"), new Range(-25f, 5f));
        RANGES.put(ResourceLocation.withDefaultNamespace("windswept_gravelly_hills"), new Range(-10f, 15f));

        RANGES.put(ResourceLocation.withDefaultNamespace("ice_spikes"), new Range(-20f, 0f));
        RANGES.put(ResourceLocation.withDefaultNamespace("frozen_peaks"), new Range(-40f, -5f));
        RANGES.put(ResourceLocation.withDefaultNamespace("jagged_peaks"), new Range(-40f, 5f));
        RANGES.put(ResourceLocation.withDefaultNamespace("stony_peaks"), new Range(-20f, 10f));

        RANGES.put(ResourceLocation.withDefaultNamespace("snowy_slopes"), new Range(-30f, 0f));
        RANGES.put(ResourceLocation.withDefaultNamespace("sparse_jungle"), new Range(20f, 35f));
        RANGES.put(ResourceLocation.withDefaultNamespace("jungle"), new Range(20f, 40f));
        RANGES.put(ResourceLocation.withDefaultNamespace("bamboo_jungle"), new Range(20f, 40f));
        RANGES.put(ResourceLocation.withDefaultNamespace("swamp"), new Range(10f, 35f));
        RANGES.put(ResourceLocation.withDefaultNamespace("mangrove_swamp"), new Range(20f, 35f));

        RANGES.put(ResourceLocation.withDefaultNamespace("desert"), new Range(30f, 56f));
        RANGES.put(ResourceLocation.withDefaultNamespace("badlands"), new Range(10f, 45f));
        RANGES.put(ResourceLocation.withDefaultNamespace("eroded_badlands"), new Range(10f, 45f));
        RANGES.put(ResourceLocation.withDefaultNamespace("wooded_badlands"), new Range(5f, 40f));
        RANGES.put(ResourceLocation.withDefaultNamespace("windswept_hills"), new Range(0f, 30f));
        RANGES.put(ResourceLocation.withDefaultNamespace("savanna"), new Range(18f, 40f));
        RANGES.put(ResourceLocation.withDefaultNamespace("savanna_plateau"), new Range(18f, 35f));
        RANGES.put(ResourceLocation.withDefaultNamespace("windswept_savanna"), new Range(15f, 35f));

        RANGES.put(ResourceLocation.withDefaultNamespace("dripstone_caves"), new Range(8f, 14f));
        RANGES.put(ResourceLocation.withDefaultNamespace("lush_caves"), new Range(12f, 18f));
        RANGES.put(ResourceLocation.withDefaultNamespace("deep_dark"), new Range(10f, 14f));
        RANGES.put(ResourceLocation.withDefaultNamespace("mushroom_fields"), new Range(10f, 18f));

        // 3) Nether & End biomes remain at DEFAULT [-20,56], or override as needed
        //    e.g. you could do:
        //    RANGES.put(ResourceLocation.withDefaultNamespace("nether_wastes"), new Range(200f, 800f));
    }



/**
 * Returns the [minC, maxC] range for this biome,
 * or a sensible default if none is specified.
 */
public static Range getRange(ResourceLocation biome) {;
    if (biome == null) return DEFAULT;
    return RANGES.getOrDefault(biome, DEFAULT);
}
}
