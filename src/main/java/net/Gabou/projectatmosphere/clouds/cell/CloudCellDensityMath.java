package net.Gabou.projectatmosphere.clouds.cell;

import java.util.Collection;

/**
 * Analytic (noise-free) cell density math shared by the CPU. Approximates the
 * GPU density field closely enough for gameplay queries, merge/split
 * fallbacks, and shadow estimates without any GPU roundtrip.
 */
public final class CloudCellDensityMath {
    private CloudCellDensityMath() {
    }

    /**
     * Analytic normalized density (0..1) of one cell at a world position.
     */
    public static float cellDensityAt(CloudCell cell, double worldX, double worldY, double worldZ) {
        float footprint = footprint01(cell, worldX, worldZ);
        if (footprint <= 0.0001F) {
            return 0.0F;
        }
        float height01 = (float) ((worldY - cell.baseY()) / Math.max(cell.verticalExtent(), 1.0F));
        if (height01 < -0.05F || height01 > 1.05F) {
            return 0.0F;
        }
        float vertical = verticalProfile(height01, cell.energy());
        return clamp01(footprint * vertical * cell.density());
    }

    /**
     * Analytic combined density over a set of cells (smooth-max union).
     */
    public static float densityAt(Collection<CloudCell> cells, double worldX, double worldY, double worldZ) {
        if (cells == null || cells.isEmpty()) {
            return 0.0F;
        }
        float accumulated = 0.0F;
        for (CloudCell cell : cells) {
            if (cell == null) {
                continue;
            }
            float density = cellDensityAt(cell, worldX, worldY, worldZ);
            // Smooth union: complements multiply so overlaps saturate smoothly.
            accumulated = 1.0F - (1.0F - accumulated) * (1.0F - density);
        }
        return clamp01(accumulated);
    }

    /**
     * Normalized elliptical footprint of a cell at a world XZ position:
     * 1 in the core, falling to 0 at the perturbed edge.
     */
    public static float footprint01(CloudCell cell, double worldX, double worldZ) {
        double dx = worldX - cell.x();
        double dz = worldZ - cell.z();
        float cos = (float) Math.cos(-cell.orientationRadians());
        float sin = (float) Math.sin(-cell.orientationRadians());
        double localX = dx * cos - dz * sin;
        double localZ = dx * sin + dz * cos;
        double normalized = Math.sqrt(
                (localX * localX) / (double) (cell.radiusMajor() * cell.radiusMajor())
                        + (localZ * localZ) / (double) (cell.radiusMinor() * cell.radiusMinor())
        );
        float edgeStart = 0.55F + (1.0F - cell.edgeSoftness()) * 0.25F;
        return 1.0F - smoothstep(edgeStart, 1.0F, (float) normalized);
    }

    /**
     * Cumulus-style vertical envelope: flat base, full middle, rounded top;
     * energetic cells spread near the top (anvil tendency).
     */
    public static float verticalProfile(float height01, float energy) {
        float base = smoothstep(0.0F, 0.12F, height01);
        float topStart = 0.72F + energy * 0.14F;
        float top = 1.0F - smoothstep(topStart, 1.0F, height01);
        float anvil = smoothstep(0.78F, 0.98F, height01) * energy * 0.35F;
        return clamp01(base * top + anvil * base);
    }

    /**
     * Analytic overlap score between two cells (0 = disjoint, 1 = coincident
     * footprints with dense bridging). Used as the CPU merge-evidence fallback
     * when GPU analytics are unavailable.
     */
    public static float overlapScore(CloudCell first, CloudCell second) {
        if (first == null || second == null) {
            return 0.0F;
        }
        double dx = second.x() - first.x();
        double dz = second.z() - first.z();
        double distance = Math.sqrt(dx * dx + dz * dz);
        double combined = effectiveRadiusToward(first, dx, dz) + effectiveRadiusToward(second, -dx, -dz);
        if (combined <= 0.001D) {
            return 0.0F;
        }
        float geometric = 1.0F - smoothstep(0.55F, 1.0F, (float) (distance / combined));
        // Sample the midpoint density bridge at mid-cloud height.
        double midX = (first.x() + second.x()) * 0.5D;
        double midZ = (first.z() + second.z()) * 0.5D;
        double midY = (first.baseY() + first.topY() + second.baseY() + second.topY()) * 0.25D;
        float bridge = Math.min(
                cellDensityAt(first, midX, midY, midZ),
                cellDensityAt(second, midX, midY, midZ)
        );
        return clamp01(geometric * 0.5F + bridge * 1.4F);
    }

    private static double effectiveRadiusToward(CloudCell cell, double directionX, double directionZ) {
        double length = Math.sqrt(directionX * directionX + directionZ * directionZ);
        if (length < 0.001D) {
            return cell.radiusMajor();
        }
        float cos = (float) Math.cos(-cell.orientationRadians());
        float sin = (float) Math.sin(-cell.orientationRadians());
        double localX = (directionX / length) * cos - (directionZ / length) * sin;
        double localZ = (directionX / length) * sin + (directionZ / length) * cos;
        double inv = (localX * localX) / (double) (cell.radiusMajor() * cell.radiusMajor())
                + (localZ * localZ) / (double) (cell.radiusMinor() * cell.radiusMinor());
        return inv <= 0.0D ? cell.radiusMajor() : 1.0D / Math.sqrt(inv);
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        if (edge0 == edge1) {
            return value < edge0 ? 0.0F : 1.0F;
        }
        float t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3.0F - 2.0F * t);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
