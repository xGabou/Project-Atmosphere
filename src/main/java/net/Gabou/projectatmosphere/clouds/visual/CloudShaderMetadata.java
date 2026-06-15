package net.Gabou.projectatmosphere.clouds.visual;

import net.Gabou.projectatmosphere.clouds.type.CloudMorphologyFamily;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Shader-facing metadata only. This class does not register uniforms, modify
 * shaders, or participate in render pass setup.
 */
public record CloudShaderMetadata(
        UUID regionId,
        String cloudTypeId,
        CloudMorphologyFamily morphologyFamily,
        Vec3 position,
        Vec3 velocity,
        float radius,
        float baseY,
        float topY,
        float opacity,
        float density,
        float coverage,
        float cloudWater,
        float precipitationStrength,
        float stormStrength,
        float visualDarkness,
        float shadowPotential,
        float verticalDevelopment,
        int cloudSeed
) {
    public static CloudShaderMetadata from(CloudVisualState state) {
        return new CloudShaderMetadata(
                state.regionId(),
                state.cloudTypeId(),
                state.morphologyFamily(),
                state.position(),
                state.velocity(),
                state.radius(),
                state.baseY(),
                state.topY(),
                state.opacity(),
                state.density(),
                state.coverage(),
                state.cloudWater(),
                state.precipitationStrength(),
                state.stormStrength(),
                state.visualDarkness(),
                state.shadowPotential(),
                state.verticalDevelopment(),
                state.cloudSeed()
        );
    }
}
