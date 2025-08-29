package net.Gabou.projectatmosphere.registry;

import com.mojang.serialization.Codec;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.particles.DebrisParticleData;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, ProjectAtmosphere.MODID);

    // Simple particles
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WIND_STREAK =
            PARTICLES.register("wind_streak", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> TRIANGLE_VERT =
            PARTICLES.register("triangle_vert", () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> TRIANGLE_ORANGE =
            PARTICLES.register("triangle_orange", () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> TRIANGLE_JAUNE =
            PARTICLES.register("triangle_jaune", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ROUND_VERT =
            PARTICLES.register("round_vert", () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ROUND_ORANGE =
            PARTICLES.register("round_orange", () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ROUND_JAUNE =
            PARTICLES.register("round_jaune", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HEART_VERT =
            PARTICLES.register("heart_vert", () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HEART_ORANGE =
            PARTICLES.register("heart_orange", () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HEART_JAUNE =
            PARTICLES.register("heart_jaune", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, ParticleType<DebrisParticleData>> DEBRIS =
            PARTICLES.register("debris", () -> new ParticleType<DebrisParticleData>(true) {
                @Override
                public com.mojang.serialization.MapCodec<DebrisParticleData> codec() {
                    return com.mojang.serialization.Codec.unit(
                            new DebrisParticleData(null, 0, 0, 0)
                    ).fieldOf("debris");
                }

                @Override
                public net.minecraft.network.codec.StreamCodec<FriendlyByteBuf, DebrisParticleData> streamCodec() {
                    return DebrisParticleData.STREAM_CODEC;
                }
            });




    public static void register(IEventBus bus) {
        PARTICLES.register(bus);
    }
}
