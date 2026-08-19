package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

/**
 * Measured noise statistics and the morphology thresholds derived from them.
 *
 * <p>Every value here is either measured directly from the baked cloud noise
 * volumes or computed from the rendering model in
 * {@link StormDensityModel}. None of them is a free tuning parameter, and none
 * may be adjusted to make a test pass: {@code StormDensityThresholdSandbox}
 * re-measures the noise and fails if any recorded value drifts from its
 * derivation. The full write-up lives in
 * {@code specs/001-native-storm-rendering/validation/morphology-thresholds.md}.
 */
final class StormMorphologyThresholds {
    private StormMorphologyThresholds() {
    }

    // -----------------------------------------------------------------
    // Measured noise statistics
    // -----------------------------------------------------------------
    // Measured over 262144 deterministic samples across a storm-sized world
    // volume, using the production base-noise scale and detail-noise domain.
    // Tolerance for re-measurement is +/- 0.010 absolute.

    /** Standard deviation of the detail FBM used by erosion. */
    static final double DETAIL_FBM_SD = 0.0742D;

    /** Standard deviation of the storm base field after percentile normalization. */
    static final double STORM_BASE_FIELD_SD = 0.3294D;

    /** 5th/95th percentiles of the raw Perlin-Worley carrier. */
    static final double BASE_CARRIER_P05 = 0.7128D;
    static final double BASE_CARRIER_P95 = 0.8451D;

    /** 5th percentile of the detail FBM: the worst-case erosion depth. */
    static final double DETAIL_FBM_P05 = 0.3568D;

    /** Measured share of detail-field variance carried by each octave band. */
    static final double DETAIL_BAND_SHARE_R = 0.8520D;
    static final double DETAIL_BAND_SHARE_G = 0.1225D;
    static final double DETAIL_BAND_SHARE_B = 0.0255D;

    /** Standard deviation of the base volume's Worley FBM reduction. */
    static final double BASE_LOW_FBM_SD = 0.0788D;

    /** Re-measurement tolerance for every measured statistic above. */
    static final double MEASUREMENT_TOLERANCE = 0.010D;

    // -----------------------------------------------------------------
    // Derived model constants
    // -----------------------------------------------------------------

    /**
     * Coverage remap fill at full coverage.
     *
     * <p>Derivation: at full coverage the storm core must survive worst-case
     * erosion without a hole. The weakest core sample has
     * {@code baseField = 0} (the p05 point of the normalized carrier) and the
     * deepest erosion bite is
     * {@code STORM_EROSION * (1 - DETAIL_FBM_P05)}. Requiring
     * {@code body(0) > bite} gives
     * {@code CORE_FILL / (1 + CORE_FILL) > 0.44 * (1 - 0.3568) = 0.2830},
     * hence {@code CORE_FILL > 0.3948}. Rounding up to the next twentieth
     * leaves headroom for 8-bit noise quantization and ray-integration
     * smoothing without inflating the core toward uniform density.
     */
    static final double CORE_FILL = 0.45D;

    /**
     * Minimum density standard deviation over an occupied storm region.
     *
     * <p>Derivation: over an unsaturated region the two independent noise
     * contributions are the remapped base field, with sensitivity
     * {@code 1 / (1 + CORE_FILL)} at full coverage, and detail erosion, with
     * sensitivity {@code STORM_EROSION}:
     *
     * <pre>
     * sd = sqrt( (STORM_BASE_FIELD_SD / (1 + CORE_FILL))^2
     *          + (STORM_EROSION * DETAIL_FBM_SD)^2 )
     *    = sqrt( (0.3294 / 1.45)^2 + (0.44 * 0.0742)^2 )
     *    = sqrt( 0.2272^2 + 0.0327^2 ) = 0.2295
     * </pre>
     *
     * <p>Half of that is required, covering ray-integration smoothing along
     * the sample interval, partial saturation near the top of the body, and
     * texture filtering. The allowance is a tolerance on a derived value, not
     * a target chosen to pass.
     */
    static final double MIN_OCCUPIED_REGION_SD = 0.1148D;

    /** Minimum density variance over an occupied region; the square of the SD above. */
    static final double MIN_OCCUPIED_REGION_VARIANCE = MIN_OCCUPIED_REGION_SD * MIN_OCCUPIED_REGION_SD;

    /**
     * Fraction of the analytic derivative that an interior probe must show
     * when the noise field is perturbed.
     *
     * <p>The analytic sensitivities are exact only where the result is
     * unsaturated and the remap is locally linear. Half is a generous
     * allowance that still fails decisively against the pre-correction path,
     * which produced exactly zero interior response because its edge-exposure
     * factor reached zero above {@code cloud = 0.72}.
     */
    static final double INTERIOR_SENSITIVITY_FRACTION = 0.5D;

    /**
     * Coverage above which a probe counts as storm interior rather than edge.
     * Chosen at the coverage where the remap lower bound first goes negative
     * ({@code coverageLowerBound(0.69) = 0}), which is the geometric point at
     * which a sample is inside the body rather than on its boundary.
     */
    static final double INTERIOR_COVERAGE = 0.75D;

    /**
     * Fraction of its nominal weight-squared share that each detail octave
     * must still contribute to the final density. Half the nominal share
     * absorbs quantization and the coverage remap's local nonlinearity.
     */
    static final double MIN_BAND_SHARE_FRACTION = 0.5D;

    /**
     * Lowest detail octave wavelength in blocks: detail period 2 over a
     * {@code 1 / DETAIL_NOISE_SCALE} block tile. Occupied regions sampled for
     * variance must span at least three of these so the measurement describes
     * the field rather than the sample window.
     */
    static final double LOWEST_DETAIL_WAVELENGTH_BLOCKS =
            1.0D / (StormDensityModel.DETAIL_NOISE_SCALE * CloudNoiseFieldModel.DETAIL_FBM_PERIODS[0]);

    /** Minimum edge length of a region sampled for the variance proxy. */
    static final double MIN_VARIANCE_REGION_BLOCKS = 3.0D * LOWEST_DETAIL_WAVELENGTH_BLOCKS;

    /** Samples outside this band are saturated and excluded from variance proxies. */
    static final double OCCUPIED_DENSITY_MIN = 0.05D;
    static final double OCCUPIED_DENSITY_MAX = 0.95D;
}
