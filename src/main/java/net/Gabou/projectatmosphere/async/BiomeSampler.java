package net.Gabou.projectatmosphere.async;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public final class BiomeSampler {
    private final BiomeSource biomeSource;
    private final Climate.Sampler sampler;

    public BiomeSampler(long seed, RegistryAccess registryAccess, BiomeSource biomeSource) {
        // 1. Grab overworld noise generator settings
        Holder<NoiseGeneratorSettings> settingsHolder =
                registryAccess.registryOrThrow(Registries.NOISE_SETTINGS)
                        .getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);

        NoiseGeneratorSettings settings = settingsHolder.value();

        // 2. Build RandomState with those settings + seed
        HolderGetter<NormalNoise.NoiseParameters> noiseParams =
                registryAccess.lookupOrThrow(Registries.NOISE);
        RandomState randomState = RandomState.create(settings, noiseParams, seed);

        // 3. Save sampler
        this.sampler = randomState.sampler();

        // 4. Use the actual biome source passed from the world
        this.biomeSource = biomeSource;
    }

    public ResourceLocation getBiomeId(int x, int y, int z) {
        int qx = x >> 2;
        int qy = y >> 2;
        int qz = z >> 2;

        Holder<Biome> biome = biomeSource.getNoiseBiome(qx, qy, qz, sampler);

        return biome.unwrapKey()
                .map(ResourceKey::location)
                .orElse(ResourceLocation.fromNamespaceAndPath("minecraft", "plains"));
    }
}



