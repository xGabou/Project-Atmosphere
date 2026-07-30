package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import net.Gabou.projectatmosphere.clouds.cell.CloudCell;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldSnapshot;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Integrates the procedural material domain from the movement of the visual
 * representation itself. Identified fields/cells contribute only when the
 * same UUID exists in consecutive rendered frames, so LOD churn cannot move
 * the density texture. Regional sheets, which have no identity, use ordinary
 * incremental velocity integration instead of velocity times absolute time.
 */
final class VolumetricMaterialAdvectionTracker {
    private static final SampleAdapter<CloudFieldSnapshot> FIELD_ADAPTER = new SampleAdapter<>() {
        @Override
        public UUID id(CloudFieldSnapshot sample) {
            return sample.fieldId();
        }

        @Override
        public double x(CloudFieldSnapshot sample) {
            return sample.center().x;
        }

        @Override
        public double z(CloudFieldSnapshot sample) {
            return sample.center().z;
        }

        @Override
        public double weight(CloudFieldSnapshot sample) {
            return Math.max(
                    0.01D,
                    sample.effectiveDensity() * sample.effectiveCoverage() * Math.max(1.0F, sample.radius())
            );
        }
    };
    private static final SampleAdapter<CloudCell> CELL_ADAPTER = new SampleAdapter<>() {
        @Override
        public UUID id(CloudCell sample) {
            return sample.id();
        }

        @Override
        public double x(CloudCell sample) {
            return sample.x();
        }

        @Override
        public double z(CloudCell sample) {
            return sample.z();
        }

        @Override
        public double weight(CloudCell sample) {
            return Math.max(0.01D, sample.density() * Math.max(1.0F, sample.radiusMajor()));
        }
    };

    private final Map<UUID, TrackedPosition> trackedPositions = new HashMap<>();
    private double[] matchedDeltaX = new double[16];
    private double[] matchedDeltaZ = new double[16];
    private double[] matchedWeight = new double[16];

    private SourceKind sourceKind = SourceKind.NONE;
    private String dimensionId = "";
    private long generation;
    private double materialOffsetX;
    private double materialOffsetZ;
    private double offsetCompensationX;
    private double offsetCompensationZ;
    private double previousRenderTime = Double.NaN;
    private float previousRegionalWindX;
    private float previousRegionalWindZ;
    private Frame lastFrame = Frame.empty();

    Frame updateFields(String dimension, double renderTime, List<CloudFieldSnapshot> fields) {
        return updateIdentified(SourceKind.FIELDS, dimension, renderTime, fields, FIELD_ADAPTER);
    }

    Frame updateCells(String dimension, double renderTime, List<CloudCell> cells) {
        return updateIdentified(SourceKind.CELLS, dimension, renderTime, cells, CELL_ADAPTER);
    }

    Frame updateRegional(String dimension, double renderTime, Vector3f wind) {
        boolean dimensionChanged = changeDimensionIfNeeded(dimension);
        boolean hadSource = sourceKind != SourceKind.NONE;
        boolean sourceChanged = sourceKind != SourceKind.REGIONAL;
        if (sourceChanged) {
            trackedPositions.clear();
            sourceKind = SourceKind.REGIONAL;
            previousRenderTime = Double.NaN;
        }

        float windX = wind == null || !Float.isFinite(wind.x) ? 0.0F : wind.x;
        float windZ = wind == null || !Float.isFinite(wind.z) ? 0.0F : wind.z;
        double deltaX = 0.0D;
        double deltaZ = 0.0D;
        if (Double.isFinite(previousRenderTime) && Double.isFinite(renderTime)) {
            double deltaTicks = renderTime - previousRenderTime;
            if (deltaTicks >= 0.0D) {
                deltaX = (previousRegionalWindX + windX) * 0.5D * deltaTicks;
                deltaZ = (previousRegionalWindZ + windZ) * 0.5D * deltaTicks;
            } else {
                sourceChanged = true;
            }
        }
        addOffset(deltaX, deltaZ);
        previousRenderTime = renderTime;
        previousRegionalWindX = windX;
        previousRegionalWindZ = windZ;
        lastFrame = new Frame(
                SourceKind.REGIONAL.label,
                (float) materialOffsetX,
                (float) materialOffsetZ,
                (float) deltaX,
                (float) deltaZ,
                0,
                0,
                0,
                0,
                0.0F,
                0.0F,
                dimensionChanged || (hadSource && sourceChanged)
        );
        return lastFrame;
    }

    void suspend() {
        trackedPositions.clear();
        sourceKind = SourceKind.NONE;
        previousRenderTime = Double.NaN;
        previousRegionalWindX = 0.0F;
        previousRegionalWindZ = 0.0F;
        lastFrame = new Frame(
                SourceKind.NONE.label,
                (float) materialOffsetX,
                (float) materialOffsetZ,
                0.0F,
                0.0F,
                0,
                0,
                0,
                0,
                0.0F,
                0.0F,
                false
        );
    }

    void reset() {
        trackedPositions.clear();
        sourceKind = SourceKind.NONE;
        dimensionId = "";
        generation = 0L;
        materialOffsetX = 0.0D;
        materialOffsetZ = 0.0D;
        offsetCompensationX = 0.0D;
        offsetCompensationZ = 0.0D;
        previousRenderTime = Double.NaN;
        previousRegionalWindX = 0.0F;
        previousRegionalWindZ = 0.0F;
        lastFrame = Frame.empty();
    }

    Frame frame() {
        return lastFrame;
    }

    private <T> Frame updateIdentified(
            SourceKind requestedSource,
            String dimension,
            double renderTime,
            List<T> samples,
            SampleAdapter<T> adapter
    ) {
        boolean dimensionChanged = changeDimensionIfNeeded(dimension);
        boolean hadSource = sourceKind != SourceKind.NONE;
        boolean sourceChanged = sourceKind != requestedSource;
        if (sourceChanged) {
            trackedPositions.clear();
            sourceKind = requestedSource;
        }
        int previousCount = trackedPositions.size();
        advanceGeneration();

        double weightedDeltaX = 0.0D;
        double weightedDeltaZ = 0.0D;
        double totalWeight = 0.0D;
        int matched = 0;
        int entered = 0;
        int rejected = 0;
        int uniqueCurrent = 0;

        if (samples != null) {
            ensureMatchedCapacity(samples.size());
            for (T sample : samples) {
                if (sample == null) {
                    rejected++;
                    continue;
                }
                UUID id = adapter.id(sample);
                double x = adapter.x(sample);
                double z = adapter.z(sample);
                double weight = adapter.weight(sample);
                if (id == null || !Double.isFinite(x) || !Double.isFinite(z)
                        || !Double.isFinite(weight) || weight <= 0.0D) {
                    rejected++;
                    continue;
                }
                TrackedPosition tracked = trackedPositions.get(id);
                if (tracked != null && tracked.generation == generation) {
                    rejected++;
                    continue;
                }
                uniqueCurrent++;
                if (tracked != null && tracked.generation == generation - 1L) {
                    double deltaX = x - tracked.x;
                    double deltaZ = z - tracked.z;
                    matchedDeltaX[matched] = deltaX;
                    matchedDeltaZ[matched] = deltaZ;
                    matchedWeight[matched] = weight;
                    weightedDeltaX += deltaX * weight;
                    weightedDeltaZ += deltaZ * weight;
                    totalWeight += weight;
                    matched++;
                } else {
                    entered++;
                }
                if (tracked == null) {
                    trackedPositions.put(id, new TrackedPosition(x, z, generation));
                } else {
                    tracked.x = x;
                    tracked.z = z;
                    tracked.generation = generation;
                }
            }
        }

        Iterator<Map.Entry<UUID, TrackedPosition>> iterator = trackedPositions.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().generation != generation) {
                iterator.remove();
            }
        }
        int left = Math.max(0, previousCount - matched);
        double frameDeltaX = totalWeight > 0.0D ? weightedDeltaX / totalWeight : 0.0D;
        double frameDeltaZ = totalWeight > 0.0D ? weightedDeltaZ / totalWeight : 0.0D;
        double residualWeightedSum = 0.0D;
        double residualMax = 0.0D;
        for (int i = 0; i < matched; i++) {
            double residualX = matchedDeltaX[i] - frameDeltaX;
            double residualZ = matchedDeltaZ[i] - frameDeltaZ;
            double residualSquared = residualX * residualX + residualZ * residualZ;
            residualWeightedSum += residualSquared * matchedWeight[i];
            residualMax = Math.max(residualMax, Math.sqrt(residualSquared));
        }
        double residualRms = totalWeight > 0.0D
                ? Math.sqrt(Math.max(0.0D, residualWeightedSum / totalWeight))
                : 0.0D;

        addOffset(frameDeltaX, frameDeltaZ);
        previousRenderTime = renderTime;
        boolean lostIdentityContinuity = previousCount > 0 && uniqueCurrent > 0 && matched == 0;
        lastFrame = new Frame(
                requestedSource.label,
                (float) materialOffsetX,
                (float) materialOffsetZ,
                (float) frameDeltaX,
                (float) frameDeltaZ,
                matched,
                entered,
                left,
                rejected,
                (float) residualRms,
                (float) residualMax,
                dimensionChanged || (hadSource && sourceChanged) || lostIdentityContinuity
        );
        return lastFrame;
    }

    private boolean changeDimensionIfNeeded(String dimension) {
        String safeDimension = dimension == null ? "" : dimension;
        if (dimensionId.isEmpty()) {
            dimensionId = safeDimension;
            return false;
        }
        if (dimensionId.equals(safeDimension)) {
            return false;
        }
        reset();
        dimensionId = safeDimension;
        return true;
    }

    private void advanceGeneration() {
        if (generation == Long.MAX_VALUE) {
            trackedPositions.clear();
            generation = 1L;
        } else {
            generation++;
        }
    }

    private void ensureMatchedCapacity(int requestedCapacity) {
        if (requestedCapacity <= matchedDeltaX.length) {
            return;
        }
        int capacity = matchedDeltaX.length;
        while (capacity < requestedCapacity) {
            capacity *= 2;
        }
        matchedDeltaX = new double[capacity];
        matchedDeltaZ = new double[capacity];
        matchedWeight = new double[capacity];
    }

    private void addOffset(double deltaX, double deltaZ) {
        if (!Double.isFinite(deltaX) || !Double.isFinite(deltaZ)) {
            return;
        }
        double correctedX = deltaX - offsetCompensationX;
        double nextX = materialOffsetX + correctedX;
        offsetCompensationX = (nextX - materialOffsetX) - correctedX;
        materialOffsetX = nextX;

        double correctedZ = deltaZ - offsetCompensationZ;
        double nextZ = materialOffsetZ + correctedZ;
        offsetCompensationZ = (nextZ - materialOffsetZ) - correctedZ;
        materialOffsetZ = nextZ;
    }

    record Frame(
            String source,
            float offsetX,
            float offsetZ,
            float frameDeltaX,
            float frameDeltaZ,
            int matched,
            int entered,
            int left,
            int rejected,
            float motionResidualRms,
            float motionResidualMax,
            boolean discontinuity
    ) {
        private static Frame empty() {
            return new Frame("none", 0.0F, 0.0F, 0.0F, 0.0F,
                    0, 0, 0, 0, 0.0F, 0.0F, false);
        }

        String summary() {
            return String.format(
                    Locale.ROOT,
                    "materialAdvection[source=%s offset=(%.6f,%.6f) delta=(%.6f,%.6f) "
                            + "matched=%d entered=%d left=%d rejected=%d residualRms=%.6f residualMax=%.6f "
                            + "discontinuity=%s]",
                    source,
                    offsetX,
                    offsetZ,
                    frameDeltaX,
                    frameDeltaZ,
                    matched,
                    entered,
                    left,
                    rejected,
                    motionResidualRms,
                    motionResidualMax,
                    discontinuity
            );
        }
    }

    private enum SourceKind {
        NONE("none"),
        FIELDS("fields"),
        CELLS("cells"),
        REGIONAL("regional");

        private final String label;

        SourceKind(String label) {
            this.label = label;
        }
    }

    private interface SampleAdapter<T> {
        UUID id(T sample);

        double x(T sample);

        double z(T sample);

        double weight(T sample);
    }

    private static final class TrackedPosition {
        private double x;
        private double z;
        private long generation;

        private TrackedPosition(double x, double z, long generation) {
            this.x = x;
            this.z = z;
            this.generation = generation;
        }
    }
}
