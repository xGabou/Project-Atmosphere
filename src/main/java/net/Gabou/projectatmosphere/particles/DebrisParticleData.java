package net.Gabou.projectatmosphere.particles;

import com.mojang.brigadier.StringReader;
import com.mojang.serialization.Codec;
import net.Gabou.projectatmosphere.registry.ModParticles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Particle data for {@link DebrisParticle}.
 * This implementation is client-only and does not support network or command serialization.
 */
public record DebrisParticleData(DebrisOrbitSource orbitSource, double radius, double height, float angularSpeed,
                                 float verticalDrift, float radialJitter, int band) implements ParticleOptions {
    public static final Codec<DebrisParticleData> CODEC = Codec.unit(new DebrisParticleData(DebrisOrbitSource.NONE, 0, 0, 0, 0, 0, 0));

    public static final Deserializer<DebrisParticleData> DESERIALIZER = new Deserializer<>() {
        @Override
        public DebrisParticleData fromCommand(ParticleType<DebrisParticleData> type, StringReader reader) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DebrisParticleData fromNetwork(ParticleType<DebrisParticleData> type, FriendlyByteBuf buf) {
            throw new UnsupportedOperationException();
        }
    };

    @Override
    public ParticleType<?> getType() {
        return ModParticles.DEBRIS.get();
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String writeToString() {
        return "debris";
    }
}

