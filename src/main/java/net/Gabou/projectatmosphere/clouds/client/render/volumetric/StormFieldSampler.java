package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import java.util.List;

/**
 * Single CPU entry point for descriptor-owned storm density.
 *
 * <p>Binds a descriptor set to the baked cloud noise volumes and runs the
 * ordered composition in {@code contracts/storm-density-composition.md}:
 * coverage envelope, base volumetric noise remapping, multi-scale detail
 * erosion, final density. Every consumer that needs a visible storm value -
 * camera density, whiteout, precipitation support and attachment - reads
 * {@link #densityAt} here rather than a descriptor union directly.
 *
 * <p>Instances hold reusable scratch storage and are therefore single-thread
 * objects. The render path keeps one per consumer; the sandboxes create their
 * own.
 *
 * <p>{@link Composition#AUDITED_PHASE_4R} reproduces the pre-correction path
 * exactly, so the Phase 4S fail-first evidence stays reproducible indefinitely
 * rather than living only in version control.
 */
final class StormFieldSampler {
    /** Which density composition this sampler evaluates. */
    enum Composition {
        /**
         * Pre-correction behaviour: the descriptor union <em>is</em> the
         * visible density, and detail erosion is gated away across the storm
         * interior by an edge-exposure factor and an erosion floor. The base
         * noise field is not consulted at all.
         */
        AUDITED_PHASE_4R,
        /** Corrected behaviour: envelope, base noise remap, multi-scale erosion. */
        CORRECTED_PHASE_4S
    }

    /** Audited edge-exposure gate bounds; retained for the fail-first reproduction only. */
    private static final double AUDITED_EXPOSURE_LOW = 0.26D;
    private static final double AUDITED_EXPOSURE_HIGH = 0.72D;
    private static final double AUDITED_EROSION_FLOOR = 0.42D;

    /**
     * Field values used when the noise bake has not completed yet. The median
     * of each field keeps the mirror continuous with the eventual result
     * instead of collapsing the storm to zero or to a solid block.
     */
    private static final double PENDING_BASE_FIELD = 0.5D;
    private static final double PENDING_DETAIL_FBM = 0.5D;

    private final Composition composition;
    /** True when the volumes are read from the live bake instead of being pinned. */
    private final boolean liveBake;
    private final byte[] pinnedBasePixels;
    private final byte[] pinnedDetailPixels;

    private final double[] domain = new double[3];
    private final double[] texel = new double[4];
    private final double[] fields = new double[2];

    private StormFieldSampler(
            Composition composition,
            boolean liveBake,
            byte[] pinnedBasePixels,
            byte[] pinnedDetailPixels
    ) {
        this.composition = composition;
        this.liveBake = liveBake;
        this.pinnedBasePixels = pinnedBasePixels;
        this.pinnedDetailPixels = pinnedDetailPixels;
    }

    /**
     * Production sampler bound to the baked volumes the GPU is sampling. The
     * bake completes asynchronously, so the volumes are read on each sample
     * rather than captured at construction; until they exist the noise stages
     * fall back to their median values.
     */
    static StormFieldSampler production() {
        return new StormFieldSampler(Composition.CORRECTED_PHASE_4S, true, null, null);
    }

    static StormFieldSampler of(Composition composition, byte[] basePixels, byte[] detailPixels) {
        return new StormFieldSampler(composition, false, basePixels, detailPixels);
    }

    private byte[] basePixels() {
        return liveBake ? CloudNoiseTextureManager.bakedBasePixels() : pinnedBasePixels;
    }

    private byte[] detailPixels() {
        return liveBake ? CloudNoiseTextureManager.bakedDetailPixels() : pinnedDetailPixels;
    }

    Composition composition() {
        return composition;
    }

    /** True when the baked volumes are available and the noise stages are live. */
    boolean hasNoise() {
        return basePixels() != null && detailPixels() != null;
    }

    // -----------------------------------------------------------------
    // Stage 4 output
    // -----------------------------------------------------------------

    double coverageAt(List<StormLobeDescriptor> lobes, double x, double y, double z) {
        return composition == Composition.AUDITED_PHASE_4R
                ? StormLobeEvaluator.auditedUnionDensityAt(lobes, x, y, z)
                : StormLobeEvaluator.coverageEnvelopeAt(lobes, x, y, z);
    }

    double coverageAt(StormLobeDescriptor[] lobes, double x, double y, double z) {
        return StormLobeEvaluator.coverageEnvelopeAt(lobes, x, y, z);
    }

    /** Strength chosen by the same smooth unions as the descriptor envelope. */
    double envelopeStrengthAt(List<StormLobeDescriptor> lobes, double x, double y, double z) {
        return composition == Composition.AUDITED_PHASE_4R
                ? 1.0D : StormLobeEvaluator.envelopeStrengthAt(lobes, x, y, z);
    }

    // -----------------------------------------------------------------
    // Stage 5 and 6 inputs
    // -----------------------------------------------------------------

    /**
     * Samples the two noise fields the storm composition consumes.
     * {@code out[0]} receives the normalized storm base field and
     * {@code out[1]} the detail FBM, both in 0..1.
     */
    void noiseFieldsAt(double x, double y, double z, double[] out) {
        byte[] base = basePixels();
        byte[] detail = detailPixels();
        if (base == null || detail == null) {
            out[0] = PENDING_BASE_FIELD;
            out[1] = PENDING_DETAIL_FBM;
            return;
        }
        StormDensityModel.baseNoiseDomain(
                x, y, z, StormDensityModel.STORM_BASE_NOISE_SCALE, domain);
        CloudNoiseFieldModel.sampleBase(base, domain[0], domain[1], domain[2], texel);
        double lowFbm = StormDensityModel.lowFbm(texel[1], texel[2], texel[3]);
        out[0] = StormDensityModel.stormBaseField(
                StormDensityModel.baseCarrier(texel[0], lowFbm));

        StormDensityModel.detailNoiseDomain(x, y, z, domain);
        CloudNoiseFieldModel.sampleDetail(detail, domain[0], domain[1], domain[2], texel);
        out[1] = StormDensityModel.detailFbm(texel[0], texel[1], texel[2]);
    }

    /**
     * Samples the individual noise channels the composition consumes:
     * {@code out[0]} the normalized storm base field and {@code out[1..3]} the
     * three detail octaves. Used by the per-band spectral regression, which
     * needs to neutralize one octave at a time.
     */
    void noiseChannelsAt(double x, double y, double z, double[] out) {
        byte[] base = basePixels();
        byte[] detail = detailPixels();
        if (base == null || detail == null) {
            out[0] = PENDING_BASE_FIELD;
            out[1] = PENDING_DETAIL_FBM;
            out[2] = PENDING_DETAIL_FBM;
            out[3] = PENDING_DETAIL_FBM;
            return;
        }
        StormDensityModel.baseNoiseDomain(
                x, y, z, StormDensityModel.STORM_BASE_NOISE_SCALE, domain);
        CloudNoiseFieldModel.sampleBase(base, domain[0], domain[1], domain[2], texel);
        double lowFbm = StormDensityModel.lowFbm(texel[1], texel[2], texel[3]);
        out[0] = StormDensityModel.stormBaseField(
                StormDensityModel.baseCarrier(texel[0], lowFbm));

        StormDensityModel.detailNoiseDomain(x, y, z, domain);
        CloudNoiseFieldModel.sampleDetail(detail, domain[0], domain[1], domain[2], texel);
        out[1] = texel[0];
        out[2] = texel[1];
        out[3] = texel[2];
    }

    // -----------------------------------------------------------------
    // Stages 5 and 6
    // -----------------------------------------------------------------

    /**
     * Final storm density from the three composition inputs. Exposed
     * separately from {@link #densityAt} so regressions can hold one input
     * fixed and perturb another.
     */
    double densityFromFields(double coverage, double baseField, double detailFbm) {
        return densityFromFields(coverage, 1.0D, baseField, detailFbm);
    }

    double densityFromFields(
            double coverage,
            double envelopeStrength,
            double baseField,
            double detailFbm
    ) {
        if (composition == Composition.AUDITED_PHASE_4R) {
            return auditedDensity(coverage, detailFbm);
        }
        return StormDensityModel.finalDensity(coverage, envelopeStrength, baseField, detailFbm);
    }

    /**
     * The audited composition. {@code coverage} arrives as the descriptor
     * union density, which the pre-correction path used directly as the
     * visible body; {@code baseField} is deliberately absent because that path
     * never consulted it. Interior samples sit above
     * {@link #AUDITED_EXPOSURE_HIGH}, where the exposure factor is zero and
     * the detail term drops out entirely.
     */
    private static double auditedDensity(double coverage, double detailFbm) {
        double cloud = coverage;
        double edgeExposure = 1.0D - StormDensityModel.smoothstep(
                AUDITED_EXPOSURE_LOW, AUDITED_EXPOSURE_HIGH, cloud);
        double edgeRetention = 1.0D
                - (1.0D - detailFbm) * StormDensityModel.STORM_EROSION * edgeExposure;
        return cloud * Math.max(AUDITED_EROSION_FLOOR, Math.min(1.0D, edgeRetention));
    }

    // -----------------------------------------------------------------
    // Full path
    // -----------------------------------------------------------------

    double densityAt(List<StormLobeDescriptor> lobes, double x, double y, double z) {
        double coverage = coverageAt(lobes, x, y, z);
        if (coverage <= 0.0D) {
            return 0.0D;
        }
        noiseFieldsAt(x, y, z, fields);
        double envelopeStrength = envelopeStrengthAt(lobes, x, y, z);
        return composition == Composition.AUDITED_PHASE_4R
                ? densityFromFields(coverage, envelopeStrength, fields[0], fields[1])
                : StormDensityModel.finalDensity(
                        coverage,
                        envelopeStrength,
                        fields[0],
                        fields[1],
                        StormLobeEvaluator.hasEmbeddedConvectiveOverlap(lobes, x, y, z)
                );
    }

    double densityAt(StormLobeDescriptor[] lobes, double x, double y, double z) {
        double coverage = coverageAt(lobes, x, y, z);
        if (coverage <= 0.0D) {
            return 0.0D;
        }
        noiseFieldsAt(x, y, z, fields);
        return densityFromFields(coverage,
                StormLobeEvaluator.envelopeStrengthAt(lobes, x, y, z), fields[0], fields[1]);
    }
}
