package net.Gabou.projectatmosphere.clouds.field;

import net.Gabou.projectatmosphere.clouds.field.backend.CloudFieldSourceType;

import java.util.Locale;

/**
 * Render-facing source category for synced CloudField snapshots. This keeps
 * the client renderer from inferring source type from visual heuristics.
 */
public enum CloudFieldSourceKind {
    UNKNOWN(0, "unknown"),
    MANUAL_DEBUG(1, "manual_debug"),
    WEATHER_SUMMARY(2, "weather_summary"),
    PA_CLUSTER(3, "pa_cluster"),
    PA_REGION(4, "pa_region");

    private final int shaderId;
    private final String serializedName;

    CloudFieldSourceKind(int shaderId, String serializedName) {
        this.shaderId = shaderId;
        this.serializedName = serializedName;
    }

    /**
     * Maps backend source categories to the small client render contract.
     *
     * @param sourceType backend source type
     * @return render-facing source kind
     */
    public static CloudFieldSourceKind fromSourceType(CloudFieldSourceType sourceType) {
        if (sourceType == null) {
            return UNKNOWN;
        }
        return switch (sourceType) {
            case MANUAL_DEBUG -> MANUAL_DEBUG;
            case WEATHER_SUMMARY -> WEATHER_SUMMARY;
            case PA_CLUSTER -> PA_CLUSTER;
            case PA_REGION -> PA_REGION;
            default -> UNKNOWN;
        };
    }

    /**
     * Returns a compact integer used by the prototype shader debug mode.
     *
     * @return source kind shader id
     */
    public int shaderId() {
        return shaderId;
    }

    /**
     * Returns a command-readable source kind name.
     *
     * @return serialized source kind name
     */
    public String serializedName() {
        return serializedName;
    }

    public static CloudFieldSourceKind byName(String name) {
        if (name == null || name.isBlank()) {
            return UNKNOWN;
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        for (CloudFieldSourceKind kind : values()) {
            if (kind.name().equals(normalized) || kind.serializedName.equalsIgnoreCase(name.trim())) {
                return kind;
            }
        }
        return UNKNOWN;
    }
}
