package net.Gabou.projectatmosphere.particles;

import com.mojang.serialization.Codec;
import net.Gabou.projectatmosphere.modules.tornado.TornadoInstance;
import net.Gabou.projectatmosphere.registry.ModParticles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Particle data for {@link DebrisParticle}.
 * Client-only; no network or command serialization.
 */
public record DebrisParticleData(TornadoInstance tornado,
                                 double radius,
                                 double height,
                                 float angularSpeed) implements ParticleOptions {

    // Minimal codec (JSON side) → always dummy particle
    public static final Codec<DebrisParticleData> CODEC =
            Codec.unit(new DebrisParticleData(null, 0, 0, 0));

    // Minimal stream codec (network side) → no data transfer
    public static final StreamCodec<FriendlyByteBuf, DebrisParticleData> STREAM_CODEC =
            StreamCodec.unit(new DebrisParticleData(null, 0, 0, 0));

    @Override
    public ParticleType<?> getType() {
        return ModParticles.DEBRIS.get();
    }
}
