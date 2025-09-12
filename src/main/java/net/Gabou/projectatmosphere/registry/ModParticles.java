package net.Gabou.projectatmosphere.registry;

import com.mojang.serialization.Codec;
import net.Gabou.projectatmosphere.particles.DebrisParticleData;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, "projectatmosphere");

    public static final RegistryObject<SimpleParticleType> WIND_STREAK =
            PARTICLES.register("wind_streak", () -> new SimpleParticleType(true));

    public static final RegistryObject<SimpleParticleType> TRIANGLE_VERT =
            PARTICLES.register("triangle_vert", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> TRIANGLE_ORANGE =
            PARTICLES.register("triangle_orange", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> TRIANGLE_JAUNE =
            PARTICLES.register("triangle_jaune", () -> new SimpleParticleType(true));

    public static final RegistryObject<SimpleParticleType> ROUND_VERT =
            PARTICLES.register("round_vert", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> ROUND_ORANGE =
            PARTICLES.register("round_orange", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> ROUND_JAUNE =
            PARTICLES.register("round_jaune", () -> new SimpleParticleType(true));

    public static final RegistryObject<SimpleParticleType> HEART_VERT =
            PARTICLES.register("heart_vert", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> HEART_ORANGE =
            PARTICLES.register("heart_orange", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> HEART_JAUNE =
            PARTICLES.register("heart_jaune", () -> new SimpleParticleType(true));

    public static final RegistryObject<SimpleParticleType> WIND_STREAKS =
            PARTICLES.register("wind_streaks", () -> new SimpleParticleType(true));

    public static final RegistryObject<ParticleType<DebrisParticleData>> DEBRIS =
            PARTICLES.register("debris", () -> new ParticleType<DebrisParticleData>(true, DebrisParticleData.DESERIALIZER) {
                @Override
                public Codec<DebrisParticleData> codec() {
                    return DebrisParticleData.CODEC;
                }
            });

    public static void register(IEventBus bus) {
        PARTICLES.register(bus);
    }
}
