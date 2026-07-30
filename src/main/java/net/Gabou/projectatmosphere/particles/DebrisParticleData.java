package net.Gabou.projectatmosphere.particles;

import com.mojang.serialization.Codec;
import net.Gabou.projectatmosphere.registry.ModParticles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Particle data for {@link DebrisParticle}.
 * This implementation is client-only and does not support network or command serialization.
 */
public record DebrisParticleData(DebrisOrbitSource orbitSource, double radius, double height, float angularSpeed,
                                 float verticalDrift, float radialJitter, int band) implements ParticleOptions {
    public static final Codec<DebrisParticleData> CODEC = Codec.unit(new DebrisParticleData(DebrisOrbitSource.NONE, 0, 0, 0, 0, 0, 0));

    public static final StreamCodec<FriendlyByteBuf, DebrisParticleData> STREAM_CODEC =
            StreamCodec.unit(new DebrisParticleData(DebrisOrbitSource.NONE, 0, 0, 0, 0, 0, 0));

    @Override
    public ParticleType<?> getType() {
        return ModParticles.DEBRIS.get();
    }

}
