package net.Gabou.projectatmosphere.clouds.cell;

/**
 * Cloud type labels derived from measured cell/region properties. These are
 * outputs of the classifier and inputs only to consumers (gameplay, HUD,
 * audio, tornado eligibility) - never to the shape pipeline.
 */
public enum CloudCellClassification {
    UNCLASSIFIED,
    CUMULUS_HUMILIS,
    CUMULUS_MEDIOCRIS,
    CUMULUS_CONGESTUS,
    CUMULONIMBUS,
    STRATOCUMULUS,
    STRATUS,
    CIRRIFORM;

    public static CloudCellClassification byOrdinal(int ordinal) {
        CloudCellClassification[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return UNCLASSIFIED;
        }
        return values[ordinal];
    }

    public boolean isConvective() {
        return this == CUMULUS_CONGESTUS || this == CUMULONIMBUS;
    }
}
