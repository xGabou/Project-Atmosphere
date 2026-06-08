package net.Gabou.projectatmosphere.clouds;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record CloudWeatherSample(
        float rainStrength,
        float thunderStrength,
        float cloudCoverStrength,
        boolean inPrecipitationColumn,
        boolean canPrecipitateAtPosition,
        boolean snowing,
        @Nullable String cloudTypeId,
        @Nullable UUID regionId
) {
    public static final CloudWeatherSample NONE = new CloudWeatherSample(
            0.0F,
            0.0F,
            0.0F,
            false,
            false,
            false,
            null,
            null
    );

    public boolean hasRain() {
        return rainStrength > 0.02F;
    }

    public boolean hasThunder() {
        return thunderStrength > 0.02F;
    }

    public @NotNull String describeSource() {
        if (cloudTypeId == null || cloudTypeId.isBlank()) {
            return "none";
        }
        return regionId == null ? cloudTypeId : cloudTypeId + "/" + regionId.toString().substring(0, 8);
    }
}
