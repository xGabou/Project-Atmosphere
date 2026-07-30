package net.Gabou.projectatmosphere.clouds.type;

import java.util.Locale;

/**
 * Persistent semantic tier of one member inside a versioned cloud layout.
 *
 * <p>This is deliberately independent from render-stage enums. A renderer may
 * disable an experimental stage-map path without erasing the stable
 * BASE/MIDDLE/CROWN identity authored by the simulation.</p>
 */
public enum CloudMorphologyMemberTier {
    UNKNOWN(3),
    BASE(0),
    MIDDLE(1),
    CROWN(2);

    private final int gpuId;

    CloudMorphologyMemberTier(int gpuId) {
        this.gpuId = gpuId;
    }

    public int gpuId() {
        return gpuId;
    }

    public static CloudMorphologyMemberTier byId(String id) {
        if (id == null || id.isBlank()) {
            return UNKNOWN;
        }
        try {
            return valueOf(id.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return UNKNOWN;
        }
    }
}
