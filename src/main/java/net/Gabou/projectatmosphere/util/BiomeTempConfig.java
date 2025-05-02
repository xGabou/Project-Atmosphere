package net.Gabou.projectatmosphere.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.HashMap;

public class BiomeTempConfig {

    /** A simple min/max range in °C */
    public record Range(float minC, float maxC) {}

    private static final Range DEFAULT = new Range(-20f, 56f);
    private static final Map<ResourceLocation, Range> RANGES = new HashMap<>();

    static {
        // Real-world overrides for specific biomes:
        RANGES.put(new ResourceLocation("minecraft:frozen_peaks"), new Range(-40f, -5f));
        RANGES.put(new ResourceLocation("minecraft:taiga"),        new Range(-25f, 10f));
        RANGES.put(new ResourceLocation("minecraft:desert"),       new Range( 30f, 56f));
        // Add more as needed...
    }

    /**
     * Returns the [minC, maxC] range for this biome,
     * or a sensible default if none is specified.
     */
    public static Range getRange(Biome biome) {
        ResourceLocation key = ForgeRegistries.BIOMES.getKey(biome);
        if (key == null) return DEFAULT;
        return RANGES.getOrDefault(key, DEFAULT);
    }
}