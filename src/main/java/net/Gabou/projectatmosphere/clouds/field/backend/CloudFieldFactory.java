package net.Gabou.projectatmosphere.clouds.field.backend;

import net.Gabou.projectatmosphere.clouds.field.CloudField;
import net.Gabou.projectatmosphere.clouds.type.CloudShapeProfile;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates stable CloudFields from neutral backend source data.
 */
public final class CloudFieldFactory {
    private static final int MIN_DERIVED_CLOUDLETS = 16;
    private static final int MAX_DERIVED_CLOUDLETS = 512;

    public Optional<CloudField> create(CloudFieldSource source) {
        if (source == null || !source.isUsable()) {
            return Optional.empty();
        }

        CloudField field = new CloudField(
                fieldIdFor(source),
                source.seed(),
                source.dimensionId(),
                source.center(),
                source.radius(),
                source.baseY(),
                source.topY(),
                source.density(),
                source.coverage(),
                source.growth(),
                source.decay(),
                source.humidityInfluence(),
                source.wind(),
                source.verticalDevelopment(),
                source.stormPotential(),
                cloudletCountFor(source),
                source.ageTicks(),
                source.lifetimeTicks()
        );
        return Optional.of(field);
    }

    public CloudField createOrThrow(CloudFieldSource source) {
        return create(source).orElseThrow(() -> new IllegalArgumentException("source cannot produce a CloudField"));
    }

    public UUID fieldIdFor(CloudFieldSource source) {
        Objects.requireNonNull(source, "source");
        String identity = source.stableKey() + ":seed=" + source.seed();
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
    }

    public int cloudletCountFor(CloudFieldSource source) {
        Objects.requireNonNull(source, "source");
        if (source.cloudletCountHint() > 0) {
            return Math.min(MAX_DERIVED_CLOUDLETS, source.cloudletCountHint());
        }
        if (source.effectiveDensity() <= 0.001F || source.effectiveCoverage() <= 0.001F) {
            return 0;
        }

        float footprint = source.radius()
                * Math.max(0.15F, source.effectiveCoverage())
                * Math.max(0.15F, source.effectiveDensity());
        int derived = Math.round(footprint / 10.0F);
        return clamp(Math.max(derived, profileCloudletFloor(source)), MIN_DERIVED_CLOUDLETS, MAX_DERIVED_CLOUDLETS);
    }

    private static int profileCloudletFloor(CloudFieldSource source) {
        CloudShapeProfile profile = CloudShapeProfile.defaultFor(source.cloudTypeId(), null, null);
        int lobes = profile == null ? 0 : profile.getLobeCountMax();
        int typeFloor = Math.max(MIN_DERIVED_CLOUDLETS, lobes * 2);
        float radiusScale = source.radius() >= 96.0F ? 1.5F : 1.0F;
        return Math.round(typeFloor * radiusScale);
    }

    public CloudFieldSource sourceFromField(CloudField field, CloudFieldSourceType sourceType, String sourceId) {
        Objects.requireNonNull(field, "field");
        return new CloudFieldSource(
                sourceId == null || sourceId.isBlank() ? field.fieldId().toString() : sourceId,
                sourceType,
                field.dimensionId(),
                field.center(),
                field.radius(),
                field.baseY(),
                field.topY(),
                field.density(),
                field.coverage(),
                field.humidityInfluence(),
                field.windVector() == null ? Vec3.ZERO : field.windVector(),
                field.growth(),
                field.decay(),
                field.verticalDevelopment(),
                field.stormPotential(),
                field.seed(),
                field.ageTicks(),
                field.lifetimeTicks(),
                field.cloudletCount(),
                null,
                null,
                !field.isExpired()
        );
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
