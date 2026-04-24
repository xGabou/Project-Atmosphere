package net.Gabou.projectatmosphere.modules.atmosphere;

import java.util.UUID;

public record CycloneSnapshot(
        UUID id,
        float centerX,
        float centerZ,
        float radius,
        float intensity,
        float corePressureDrop,
        long lifetimeTicks
) {
}
