package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import net.Gabou.projectatmosphere.clouds.cell.CloudCell;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldSnapshot;
import org.joml.Vector3f;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Measures the displacement imposed on the procedural density domain against
 * the displacement of the presented cloud envelope. This is diagnostic-only:
 * it deliberately reproduces the legacy {@code WindVec * WorldTime} formula
 * without changing the values uploaded to the shader.
 */
final class VolumetricMaterialDomainDiagnostics {
    private static final double REPORT_INTERVAL_TICKS = 20.0D;

    private static boolean anchorValid;
    private static long anchorSourceSignature;
    private static int anchorSourceCount;
    private static String anchorSourceKind = "none";
    private static double anchorTime;
    private static double anchorCentroidX;
    private static double anchorCentroidZ;
    private static float anchorWindX;
    private static float anchorWindZ;
    private static float anchorLegacyX;
    private static float anchorLegacyZ;
    private static volatile String lastReport = "materialDomain[pending]";

    private VolumetricMaterialDomainDiagnostics() {
    }

    static void observe(
            List<CloudFieldSnapshot> fields,
            List<CloudCell> cells,
            Vector3f resolvedWind,
            float worldTimeTicks
    ) {
        SourceSample source = sourceSample(fields, cells);
        if (!source.valid()
                || resolvedWind == null
                || !Float.isFinite(resolvedWind.x)
                || !Float.isFinite(resolvedWind.z)
                || !Float.isFinite(worldTimeTicks)) {
            reset();
            return;
        }

        float legacyX = resolvedWind.x * worldTimeTicks;
        float legacyZ = resolvedWind.z * worldTimeTicks;
        boolean sourceChanged = !anchorValid
                || anchorSourceSignature != source.signature()
                || anchorSourceCount != source.count()
                || !anchorSourceKind.equals(source.kind());
        double elapsedTicks = worldTimeTicks - anchorTime;
        if (sourceChanged || elapsedTicks < 0.0D || elapsedTicks > 200.0D) {
            setAnchor(source, resolvedWind, worldTimeTicks, legacyX, legacyZ);
            lastReport = String.format(
                    Locale.ROOT,
                    "materialDomain[pending source=%s count=%d wind=(%.6f,%.6f) legacy=(%.6f,%.6f)]",
                    source.kind(), source.count(), resolvedWind.x, resolvedWind.z, legacyX, legacyZ
            );
            return;
        }
        if (elapsedTicks < REPORT_INTERVAL_TICKS) {
            return;
        }

        double centroidDeltaX = source.centroidX() - anchorCentroidX;
        double centroidDeltaZ = source.centroidZ() - anchorCentroidZ;
        float windDeltaX = resolvedWind.x - anchorWindX;
        float windDeltaZ = resolvedWind.z - anchorWindZ;
        float legacyDeltaX = legacyX - anchorLegacyX;
        float legacyDeltaZ = legacyZ - anchorLegacyZ;
        double slipX = legacyDeltaX - centroidDeltaX;
        double slipZ = legacyDeltaZ - centroidDeltaZ;
        float retroactiveX = worldTimeTicks * windDeltaX;
        float retroactiveZ = worldTimeTicks * windDeltaZ;
        lastReport = String.format(
                Locale.ROOT,
                "materialDomain[dt=%.3f source=%s count=%d wind=(%.6f,%.6f) dWind=(%.6f,%.6f) "
                        + "centroid=(%.6f,%.6f) dCentroid=(%.6f,%.6f) legacy=(%.6f,%.6f) "
                        + "dLegacy=(%.6f,%.6f) slip=(%.6f,%.6f) timeMulDWind=(%.6f,%.6f)]",
                elapsedTicks,
                source.kind(),
                source.count(),
                resolvedWind.x,
                resolvedWind.z,
                windDeltaX,
                windDeltaZ,
                source.centroidX(),
                source.centroidZ(),
                centroidDeltaX,
                centroidDeltaZ,
                legacyX,
                legacyZ,
                legacyDeltaX,
                legacyDeltaZ,
                slipX,
                slipZ,
                retroactiveX,
                retroactiveZ
        );
        setAnchor(source, resolvedWind, worldTimeTicks, legacyX, legacyZ);
    }

    static String status() {
        return lastReport;
    }

    static void reset() {
        anchorValid = false;
        anchorSourceSignature = 0L;
        anchorSourceCount = 0;
        anchorSourceKind = "none";
        lastReport = "materialDomain[pending]";
    }

    private static void setAnchor(
            SourceSample source,
            Vector3f resolvedWind,
            float worldTimeTicks,
            float legacyX,
            float legacyZ
    ) {
        anchorValid = true;
        anchorSourceSignature = source.signature();
        anchorSourceCount = source.count();
        anchorSourceKind = source.kind();
        anchorTime = worldTimeTicks;
        anchorCentroidX = source.centroidX();
        anchorCentroidZ = source.centroidZ();
        anchorWindX = resolvedWind.x;
        anchorWindZ = resolvedWind.z;
        anchorLegacyX = legacyX;
        anchorLegacyZ = legacyZ;
    }

    private static SourceSample sourceSample(List<CloudFieldSnapshot> fields, List<CloudCell> cells) {
        if (fields != null && !fields.isEmpty()) {
            double x = 0.0D;
            double z = 0.0D;
            long signature = 0x9E3779B97F4A7C15L;
            int count = 0;
            for (CloudFieldSnapshot field : fields) {
                if (field == null) {
                    continue;
                }
                x += field.center().x;
                z += field.center().z;
                UUID id = field.fieldId();
                long mixedId = mix64(id.getMostSignificantBits() ^ id.getLeastSignificantBits());
                signature += mixedId;
                signature ^= Long.rotateLeft(mixedId, 23);
                count++;
            }
            if (count > 0) {
                return new SourceSample(true, "fields", count, signature, x / count, z / count);
            }
        }
        if (cells != null && !cells.isEmpty()) {
            double x = 0.0D;
            double z = 0.0D;
            long signature = 0xD1B54A32D192ED03L;
            int count = 0;
            for (CloudCell cell : cells) {
                if (cell == null) {
                    continue;
                }
                x += cell.x();
                z += cell.z();
                signature += mix64(cell.seed());
                count++;
            }
            if (count > 0) {
                return new SourceSample(true, "cells", count, signature, x / count, z / count);
            }
        }
        return SourceSample.INVALID;
    }

    private static long mix64(long value) {
        long mixed = value;
        mixed ^= mixed >>> 30;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 27;
        mixed *= 0x94D049BB133111EBL;
        return mixed ^ mixed >>> 31;
    }

    private record SourceSample(
            boolean valid,
            String kind,
            int count,
            long signature,
            double centroidX,
            double centroidZ
    ) {
        private static final SourceSample INVALID = new SourceSample(false, "none", 0, 0L, 0.0D, 0.0D);
    }
}
