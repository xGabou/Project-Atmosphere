package net.Gabou.projectatmosphere.modules.temperature.spike;

import net.Gabou.projectatmosphere.util.BiomeInstanceKey;

public record SpikeData(BiomeInstanceKey biome, float[][] week, SpikeState state) {
}
