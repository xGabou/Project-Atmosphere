package net.Gabou.projectatmosphere.clouds.field.backend;

import net.Gabou.projectatmosphere.clouds.type.CloudMorphologyFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeDefinition;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeRegistry;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * Neutral backend source data that can become a CloudField. This is not a
 * renderer object and does not own cloudlet runtime state.
 */
public record CloudFieldSource(
        String sourceId,
        CloudFieldSourceType sourceType,
        String dimensionId,
        Vec3 center,
        float radius,
        float baseY,
        float topY,
        float density,
        float coverage,
        float humidityInfluence,
        Vec3 wind,
        float growth,
        float decay,
        float verticalDevelopment,
        float stormPotential,
        long seed,
        long ageTicks,
        long lifetimeTicks,
        int cloudletCountHint,
        String cloudTypeId,
        String morphologyFamily,
        boolean active
) {
    public CloudFieldSource {
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must be present");
        }
        sourceId = sourceId.trim();
        sourceType = sourceType == null ? CloudFieldSourceType.MANUAL_DEBUG : sourceType;
        dimensionId = dimensionId == null || dimensionId.isBlank()
                ? "minecraft:overworld"
                : dimensionId.trim();
        center = sanitize(center);
        radius = Math.max(0.0F, finite(radius, 0.0F));
        baseY = finite(baseY, 0.0F);
        topY = Math.max(baseY + 1.0F, finite(topY, baseY + 1.0F));
        density = clamp01(density);
        coverage = clamp01(coverage);
        humidityInfluence = clamp01(humidityInfluence);
        wind = sanitize(wind);
        growth = clamp01(growth);
        decay = clamp01(decay);
        verticalDevelopment = clamp01(verticalDevelopment);
        stormPotential = clamp01(stormPotential);
        ageTicks = Math.max(0L, ageTicks);
        lifetimeTicks = Math.max(0L, lifetimeTicks);
        cloudletCountHint = Math.max(0, cloudletCountHint);
        cloudTypeId = normalizeCloudType(cloudTypeId);
        morphologyFamily = normalizeMorphology(morphologyFamily, cloudTypeId);
    }

    public boolean isUsable() {
        return active
                && radius > 0.0F
                && topY > baseY
                && density > 0.001F
                && coverage > 0.001F;
    }

    public String stableKey() {
        return sourceType.name().toLowerCase(Locale.ROOT) + ":" + dimensionId + ":" + sourceId;
    }

    public float effectiveDensity() {
        return clamp01(density * growth * (1.0F - decay));
    }

    public float effectiveCoverage() {
        return clamp01(coverage * growth * (1.0F - decay));
    }

    public CloudMorphologyFamily resolvedMorphologyFamily() {
        CloudTypeDefinition definition = CloudTypeRegistry.getOrDefault(cloudTypeId);
        return CloudMorphologyFamily.byId(morphologyFamily, definition.getMorphologyFamily());
    }

    public float anvilStrength() {
        return CloudTypeRegistry.getOrDefault(cloudTypeId).getVisualProfile().getAnvilStrength();
    }

    public float precipitationIntensity() {
        return CloudTypeRegistry.getOrDefault(cloudTypeId).getVisualProfile().getPrecipitationCoreStrength();
    }

    private static Vec3 sanitize(Vec3 value) {
        if (value == null) {
            return Vec3.ZERO;
        }
        return new Vec3(
                finite(value.x(), 0.0D),
                finite(value.y(), 0.0D),
                finite(value.z(), 0.0D)
        );
    }

    private static String normalizeCloudType(String cloudTypeId) {
        return CloudTypeRegistry.getOrDefault(cloudTypeId).getId();
    }

    private static String normalizeMorphology(String morphologyFamily, String cloudTypeId) {
        CloudMorphologyFamily fallback = CloudTypeRegistry.getOrDefault(cloudTypeId).getMorphologyFamily();
        return CloudMorphologyFamily.byId(morphologyFamily, fallback).name();
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, finite(value, 0.0F)));
    }

    private static float finite(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }
}
