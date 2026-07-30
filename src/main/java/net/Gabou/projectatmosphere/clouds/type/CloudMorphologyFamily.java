package net.Gabou.projectatmosphere.clouds.type;

import java.util.Locale;

/**
 * Stable morphology families used to select cloud structure generators.
 */
public enum CloudMorphologyFamily {
    PUFF("puff"),
    TOWER("tower"),
    STORM_ANVIL("storm_anvil"),
    SHEET("sheet"),
    CELLULAR_SHEET("cellular_sheet"),
    FILAMENT("filament"),
    SPIRAL_STORM("spiral_storm"),
    DEBUG("debug");

    private final String id;

    CloudMorphologyFamily(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static CloudMorphologyFamily byId(String id, CloudMorphologyFamily fallback) {
        if (id == null || id.isBlank()) {
            return fallback == null ? PUFF : fallback;
        }

        String normalized = id.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(':', '_');
        for (CloudMorphologyFamily family : values()) {
            if (family.name().equals(normalized) || family.id.toUpperCase(Locale.ROOT).equals(normalized)) {
                return family;
            }
        }
        return fallback == null ? PUFF : fallback;
    }

    public static CloudMorphologyFamily defaultFor(String cloudTypeId, CloudFamily family) {
        String id = cloudTypeId == null ? "" : cloudTypeId.trim().toLowerCase(Locale.ROOT);
        return switch (id) {
            case "vapor_cluster", "cumulus_humilis", "cumulus_mediocris" -> PUFF;
            case "cumulus_congestus" -> TOWER;
            case "cumulonimbus_calvus", "cumulonimbus_capillatus" -> STORM_ANVIL;
            case "supercell" -> SPIRAL_STORM;
            case "stratus_nebulosus", "nimbostratus" -> SHEET;
            case "stratocumulus" -> CELLULAR_SHEET;
            case "cirrus" -> FILAMENT;
            case "hurricane" -> SPIRAL_STORM;
            default -> inferForFamily(family);
        };
    }

    private static CloudMorphologyFamily inferForFamily(CloudFamily family) {
        if (family == null) {
            return PUFF;
        }
        return switch (family) {
            case CUMULUS, VAPOR -> PUFF;
            case CUMULONIMBUS -> STORM_ANVIL;
            case STRATUS, NIMBOSTRATUS -> SHEET;
            case STRATOCUMULUS, ALTOCUMULUS -> CELLULAR_SHEET;
            case CIRRUS -> FILAMENT;
        };
    }
}
