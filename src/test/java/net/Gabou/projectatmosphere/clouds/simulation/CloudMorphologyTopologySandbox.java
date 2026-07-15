package net.Gabou.projectatmosphere.clouds.simulation;

import java.util.List;
import java.util.Locale;

/** Reports deterministic metrics for the exact structured TOWER source table. */
public final class CloudMorphologyTopologySandbox {
    private CloudMorphologyTopologySandbox() {
    }

    public static void main(String[] args) {
        CloudMorphologyGenerators.StructuredTowerTopology topology =
                CloudMorphologyGenerators.structuredTowerTopology();
        requireCount("angles", topology.angles(), 12);
        requireCount("radial", topology.radial(), 12);
        requireCount("heights", topology.heights(), 12);
        requireCount("radii", topology.radii(), 12);

        double heightRadiusR2 = correlationSquared(topology.heights(), topology.radii(), 0);
        double heightRadialR2 = correlationSquared(topology.heights(), topology.radial(), 1);
        double crownSeparation = circularSeparation(topology.angles().get(10), topology.angles().get(11));
        double[] stageRadiusMeans = stageMeans(topology.radii());
        double[] stageRadialMeans = stageMeans(topology.radial());

        System.out.printf(
                Locale.ROOT,
                "Structured tower topology heightRadiusR2=%.6f heightRadialR2=%.6f crownSeparation=%.3f"
                        + " stageRadiusMeans=%.6f/%.6f/%.6f/%.6f"
                        + " stageRadialMeans=%.6f/%.6f/%.6f/%.6f%n",
                heightRadiusR2,
                heightRadialR2,
                crownSeparation,
                stageRadiusMeans[0],
                stageRadiusMeans[1],
                stageRadiusMeans[2],
                stageRadiusMeans[3],
                stageRadialMeans[0],
                stageRadialMeans[1],
                stageRadialMeans[2],
                stageRadialMeans[3]
        );

        requireAtMost("height/radius R^2", heightRadiusR2, 0.40D);
        requireAtMost("height/radial R^2", heightRadialR2, 0.40D);
        requireRange("crown separation", crownSeparation, 90.0D, 155.0D);
        if (stageRadiusMeans[3] < stageRadiusMeans[2] * 0.95D) {
            throw new IllegalStateException(
                    "crown radius collapsed below tower support: tower="
                            + stageRadiusMeans[2] + " crown=" + stageRadiusMeans[3]
            );
        }
    }

    private static double correlationSquared(List<Float> x, List<Float> y, int firstIndex) {
        int count = x.size() - firstIndex;
        double meanX = 0.0D;
        double meanY = 0.0D;
        for (int index = firstIndex; index < x.size(); index++) {
            meanX += x.get(index);
            meanY += y.get(index);
        }
        meanX /= count;
        meanY /= count;

        double covariance = 0.0D;
        double varianceX = 0.0D;
        double varianceY = 0.0D;
        for (int index = firstIndex; index < x.size(); index++) {
            double dx = x.get(index) - meanX;
            double dy = y.get(index) - meanY;
            covariance += dx * dy;
            varianceX += dx * dx;
            varianceY += dy * dy;
        }
        if (varianceX <= 0.0D || varianceY <= 0.0D) {
            return 0.0D;
        }
        double correlation = covariance / Math.sqrt(varianceX * varianceY);
        return correlation * correlation;
    }

    private static double circularSeparation(float firstDegrees, float secondDegrees) {
        double difference = Math.abs(firstDegrees - secondDegrees) % 360.0D;
        return Math.min(difference, 360.0D - difference);
    }

    private static double[] stageMeans(List<Float> values) {
        return new double[]{
                mean(values, 0, 4),
                mean(values, 4, 7),
                mean(values, 7, 10),
                mean(values, 10, 12)
        };
    }

    private static double mean(List<Float> values, int startInclusive, int endExclusive) {
        double sum = 0.0D;
        for (int index = startInclusive; index < endExclusive; index++) {
            sum += values.get(index);
        }
        return sum / (endExclusive - startInclusive);
    }

    private static void requireCount(String label, List<Float> values, int expected) {
        if (values.size() != expected) {
            throw new IllegalStateException(label + " expected=" + expected + " actual=" + values.size());
        }
    }

    private static void requireAtMost(String label, double actual, double maximum) {
        if (!Double.isFinite(actual) || actual > maximum) {
            throw new IllegalStateException(label + " maximum=" + maximum + " actual=" + actual);
        }
    }

    private static void requireRange(String label, double actual, double minimum, double maximum) {
        if (!Double.isFinite(actual) || actual < minimum || actual > maximum) {
            throw new IllegalStateException(
                    label + " expected=" + minimum + ".." + maximum + " actual=" + actual
            );
        }
    }
}
