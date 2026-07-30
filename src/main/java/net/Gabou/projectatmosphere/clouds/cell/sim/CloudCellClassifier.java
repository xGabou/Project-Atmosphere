package net.Gabou.projectatmosphere.clouds.cell.sim;

import net.Gabou.projectatmosphere.clouds.cell.CloudCell;
import net.Gabou.projectatmosphere.clouds.cell.CloudCellClassification;

/**
 * Shape-derived cloud classifier: labels are computed FROM measured cell
 * properties (vertical development, footprint, density, energy, altitude),
 * never the other way around. Classification is a pure output consumed by
 * gameplay, HUD, audio, and tornado eligibility.
 */
public final class CloudCellClassifier {
    private CloudCellClassifier() {
    }

    public static CloudCellClassification classify(CloudCell cell) {
        float verticalExtent = cell.verticalExtent();
        float verticalRatio = cell.verticalExtentRatio();
        float radius = cell.radiusMajor();
        float density = cell.density();
        float energy = cell.energy();
        float baseY = cell.baseY();

        // High, thin, weak: cirriform veil.
        if (baseY > 210.0F && density < 0.30F && verticalExtent < 40.0F) {
            return CloudCellClassification.CIRRIFORM;
        }

        // Deep convection: tall towers with real energy behind them.
        if (verticalExtent > 150.0F && energy > 0.55F) {
            return CloudCellClassification.CUMULONIMBUS;
        }
        if (verticalRatio > 0.75F && verticalExtent > 80.0F && energy > 0.35F) {
            return CloudCellClassification.CUMULUS_CONGESTUS;
        }

        // Wide, flat layers: stratiform family.
        if (radius > 380.0F && verticalRatio < 0.18F) {
            return density > 0.45F
                    ? CloudCellClassification.STRATOCUMULUS
                    : CloudCellClassification.STRATUS;
        }

        // Fair-weather cumulus by vertical development.
        if (verticalRatio > 0.40F) {
            return CloudCellClassification.CUMULUS_MEDIOCRIS;
        }
        return CloudCellClassification.CUMULUS_HUMILIS;
    }

    /** Tornado eligibility gate for the phase-8 hooks (native backend). */
    public static boolean isTornadoEligible(CloudCell cell) {
        return isTornadoEligible(cell, cell == null ? CloudCellClassification.UNCLASSIFIED : cell.classification());
    }

    /** Evaluates eligibility against an explicit freshly-derived classification. */
    public static boolean isTornadoEligible(CloudCell cell, CloudCellClassification classification) {
        return cell != null
                && classification == CloudCellClassification.CUMULONIMBUS
                && cell.energy() > 0.75F
                && cell.rotation() > 0.55F
                && cell.phase() != net.Gabou.projectatmosphere.clouds.cell.CloudCellLifecyclePhase.DISSIPATING;
    }
}
