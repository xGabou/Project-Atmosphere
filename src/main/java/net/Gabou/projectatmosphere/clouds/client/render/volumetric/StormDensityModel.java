package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

/**
 * CPU authority for the descriptor-owned storm density composition, mirrored
 * independently by {@code cloud_atmosphere_volume.fsh}.
 *
 * <p>Ordered composition, per
 * {@code contracts/storm-density-composition.md}:
 *
 * <pre>
 * per-lobe world-space geometric distance field   (StormLobeEvaluator)
 *   -&gt; smooth union lobe-to-lobe                  (StormLobeEvaluator)
 *     -&gt; smooth union group-to-group              (StormLobeEvaluator)
 *       -&gt; bounded coverage envelope              (StormLobeEvaluator)
 *         -&gt; base volumetric noise remapping      (this class)
 *           -&gt; multi-scale detail erosion         (this class)
 *             -&gt; final storm density              (this class)
 * </pre>
 *
 * <p>The coverage envelope is never a visible density. Everything downstream
 * of the storm body - underside, precipitation support and attachment,
 * camera density, whiteout - reads {@link #finalDensity} and never the
 * envelope.
 */
final class StormDensityModel {
    /**
     * Base-noise domain scale used for descriptor-owned storms. One texture
     * tile therefore spans {@code 1 / 0.0025 = 400} blocks, so the base
     * Worley octaves (periods 8/16/32) have 50, 25 and 12.5 block
     * wavelengths. The measured combined carrier feature is about 109.4
     * blocks, which is large enough to form primary billows inside the live
     * 110-block tower instead of re-carving it into fingers.
     */
    static final double STORM_BASE_NOISE_SCALE = 0.0025D;

    /**
     * Detail-noise domain scale. One tile spans {@code 1 / 0.022 = 45.45}
     * blocks, so the detail Worley octaves (periods 2/4/8) have 22.7, 11.4 and
     * 5.7 block wavelengths, each itself a three-octave FBM reaching down to
     * 1.4 blocks.
     */
    static final double DETAIL_NOISE_SCALE = 0.022D;

    /**
     * Measured 5th and 95th percentiles of {@link #baseCarrier} over the storm
     * sampling domain. Confirmed by {@code StormDensityThresholdSandbox};
     * these are properties of the baked noise, not tuning parameters.
     */
    static final double BASE_CARRIER_P05 = StormMorphologyThresholds.BASE_CARRIER_P05;
    static final double BASE_CARRIER_P95 = StormMorphologyThresholds.BASE_CARRIER_P95;

    /**
     * Detail erosion amplitude for descriptor-owned storms. Retained from the
     * pre-correction shader, where it was already the configured storm value;
     * what changed is that it now reaches the storm interior instead of being
     * gated to a thin rim.
     */
    static final double STORM_EROSION = 0.44D;

    /**
     * How far below zero the coverage remap lower bound is driven at full
     * coverage. Derived in {@link StormMorphologyThresholds} so that the storm
     * core survives worst-case detail erosion without a hole while still
     * responding to the base field.
     */
    static final double CORE_FILL = StormMorphologyThresholds.CORE_FILL;

    /** p05 erosion bite used to retain mass for the real live strength range. */
    private static final double LIVE_P05_EROSION_BITE = STORM_EROSION
            * (1.0D - StormMorphologyThresholds.DETAIL_FBM_P05);

    /** Headroom above the mathematically exact p05 survival boundary. */
    private static final double LIVE_STRENGTH_FILL_HEADROOM = 0.021D;

    /**
     * The structural silhouette is measured at 0.10 density by T124-T126.
     * A deeply covered, connected envelope must retain at least that visible
     * mass through the p05 erosion bite even when its normalized base carrier
     * reaches zero; otherwise one legitimate low carrier trough becomes a
     * false horizontal material split.
     */
    private static final double STRUCTURAL_CONTINUITY_DENSITY = 0.10D;
    private static final double STRUCTURAL_CONTINUITY_COVERAGE = 0.82D;
    private static final double STRUCTURAL_CONTINUITY_STRENGTH = 0.84D;

    private StormDensityModel() {
    }

    // -----------------------------------------------------------------
    // Noise domains (mirror of the GLSL helpers)
    // -----------------------------------------------------------------

    /**
     * Legacy fixed domain-warp amplitude, in texture tiles. Shared by the
     * non-storm cloud families, whose behaviour this feature does not change.
     */
    static final double LEGACY_WARP_AMPLITUDE = 0.31D;

    /**
     * Magnitude of the domain warp's wave-number vectors, in radians per
     * block. The three warp axes have nearly equal magnitude; this is the
     * first, {@code |(0.00173, 0.00091, -0.00127)|}.
     */
    static final double WARP_WAVE_NUMBER = 0.00233D;

    /**
     * Largest tolerated ratio of warp-induced domain distortion to the domain
     * scale itself.
     *
     * <p>The warp adds {@code amplitude * sin(k . p)} to a domain that
     * otherwise advances at {@code scale} per block, so it distorts the
     * sampling Jacobian by {@code amplitude * |k| / scale}. Because the warp's
     * own wavelength (about 3600 blocks) is an order of magnitude larger than
     * a storm, that distortion is effectively a *constant directional shear*
     * across one storm rather than a varying churn - it stretches the noise
     * along a fixed axis, which reads as radial fingers and streaking.
     *
     * <p>0.08 matches what the detail domain already applies, so storms are
     * not held to a stricter standard than the rest of the renderer; it simply
     * stops the warp from growing without bound as the storm domain scale is
     * lowered.
     */
    static final double MAX_WARP_DISTORTION = 0.08D;

    /** Warp amplitude that holds {@link #MAX_WARP_DISTORTION} at this domain scale. */
    static double proportionalWarpAmplitude(double scale) {
        return Math.min(LEGACY_WARP_AMPLITUDE, MAX_WARP_DISTORTION * scale / WARP_WAVE_NUMBER);
    }

    static void baseNoiseDomain(double x, double y, double z, double scale, double[] out) {
        baseNoiseDomain(x, y, z, scale, proportionalWarpAmplitude(scale), out);
    }

    static void baseNoiseDomain(
            double x,
            double y,
            double z,
            double scale,
            double warpAmplitude,
            double[] out
    ) {
        double rx = x * 0.8138D + y * 0.2962D - z * 0.5000D;
        double ry = -x * 0.1401D + y * 0.9408D + z * 0.3085D;
        double rz = x * 0.5630D - y * 0.1677D + z * 0.8090D;
        out[0] = rx * scale + warpX(x, y, z) * warpAmplitude;
        out[1] = ry * scale + warpY(x, y, z) * warpAmplitude;
        out[2] = rz * scale + warpZ(x, y, z) * warpAmplitude;
    }

    static void detailNoiseDomain(double x, double y, double z, double[] out) {
        double rx = x * 0.7071D - y * 0.4082D + z * 0.5774D;
        double ry = x * 0.7071D + y * 0.4082D - z * 0.5774D;
        double rz = y * 0.8165D + z * 0.5774D;
        double wx = x * 1.731D;
        double wy = y * 1.731D;
        double wz = z * 1.731D;
        out[0] = rx * DETAIL_NOISE_SCALE + warpX(wx, wy, wz) * 0.43D;
        out[1] = ry * DETAIL_NOISE_SCALE + warpY(wx, wy, wz) * 0.43D;
        out[2] = rz * DETAIL_NOISE_SCALE + warpZ(wx, wy, wz) * 0.43D;
    }

    private static double warpX(double x, double y, double z) {
        return Math.sin(x * 0.00173D + y * 0.00091D - z * 0.00127D + 1.7D);
    }

    private static double warpY(double x, double y, double z) {
        return Math.sin(-x * 0.00111D + y * 0.00149D + z * 0.00083D - 2.3D);
    }

    private static double warpZ(double x, double y, double z) {
        return Math.sin(x * 0.00079D - y * 0.00131D + z * 0.00191D + 4.1D);
    }

    // -----------------------------------------------------------------
    // Noise field reductions
    // -----------------------------------------------------------------

    /** Worley FBM reduction of the base volume's G/B/A octaves. */
    static double lowFbm(double g, double b, double a) {
        return g * CloudNoiseFieldModel.DETAIL_WEIGHT_R
                + b * CloudNoiseFieldModel.DETAIL_WEIGHT_G
                + a * CloudNoiseFieldModel.DETAIL_WEIGHT_B;
    }

    /** Standard Perlin-Worley carrier: the base volume's R channel remapped by its Worley FBM. */
    static double baseCarrier(double r, double lowFbm) {
        return clamp01(remap(r, -(1.0D - lowFbm), 1.0D, 0.0D, 1.0D));
    }

    /** Worley FBM reduction of the detail volume's R/G/B octaves. */
    static double detailFbm(double r, double g, double b) {
        return r * CloudNoiseFieldModel.DETAIL_WEIGHT_R
                + g * CloudNoiseFieldModel.DETAIL_WEIGHT_G
                + b * CloudNoiseFieldModel.DETAIL_WEIGHT_B;
    }

    /**
     * Normalizes the raw Perlin-Worley carrier onto its measured usable range
     * so the remap in {@link #stormBody} operates on a well-distributed field
     * rather than a strongly high-biased one.
     */
    static double stormBaseField(double carrier) {
        return smoothstep(BASE_CARRIER_P05, BASE_CARRIER_P95, carrier);
    }

    // -----------------------------------------------------------------
    // Stage 5: base volumetric noise remapping
    // -----------------------------------------------------------------

    /**
     * Remaps the base noise field against the local coverage envelope. This is
     * the stage that makes the noise field, not the descriptor geometry, form
     * the visible storm body.
     *
     * <p>At {@code coverage == 0} the lower bound is 1.0 and nothing survives.
     * As coverage rises the bound falls, admitting progressively more of the
     * base field. At full coverage the strength-aware fill keeps weaker live
     * BASE/ANVIL descriptors connected without changing their authority over
     * coverage, while the full-strength floor remains {@code CORE_FILL}.
     * The partial derivative with respect to {@code baseField} is
     * {@code 1 / (1 - lowerBound)}, which is strictly positive for every
     * coverage value - the interior is never noise-independent.
     */
    static double stormBody(double coverage, double baseField) {
        return stormBody(coverage, 1.0D, baseField);
    }

    /**
     * Strength-aware body remap. Descriptor strength remains authoritative
     * coverage: it is not normalized away. It only selects the minimum fill
     * needed for a weak live lobe to retain mass at its own envelope ceiling.
     */
    static double stormBody(double coverage, double envelopeStrength, double baseField) {
        return stormBody(coverage, envelopeStrength, baseField, false);
    }

    static double stormBody(
            double coverage,
            double envelopeStrength,
            double baseField,
            boolean embeddedConvectiveOverlap
    ) {
        double lowerBound = coverageLowerBound(coverage, envelopeStrength, embeddedConvectiveOverlap);
        return clamp01((baseField - lowerBound) / Math.max(1.0D - lowerBound, 1.0E-4D));
    }

    static double coverageLowerBound(double coverage) {
        return coverageLowerBound(coverage, 1.0D);
    }

    static double coverageLowerBound(double coverage, double envelopeStrength) {
        return coverageLowerBound(coverage, envelopeStrength, false);
    }

    static double coverageLowerBound(
            double coverage,
            double envelopeStrength,
            boolean embeddedConvectiveOverlap
    ) {
        return lerp(clamp01(coverage), 1.0D,
                -(embeddedConvectiveOverlap
                        ? coreFillForCoverageAndStrength(coverage, envelopeStrength)
                        : coreFillForEnvelopeStrength(envelopeStrength)));
    }

    static double coreFillForEnvelopeStrength(double envelopeStrength) {
        double strength = Math.max(clamp01(envelopeStrength), 1.0E-4D);
        double required = 1.0D / (strength * (1.0D - LIVE_P05_EROSION_BITE)) - 1.0D;
        return Math.max(CORE_FILL, required + LIVE_STRENGTH_FILL_HEADROOM);
    }

    /**
     * Extends the live-strength derivation to the actual, already-strength-
     * weighted coverage at a sample. This is not a density normalization:
     * strength still limits coverage, and the remap remains monotonic with a
     * nonzero base-field derivative. It only prevents a zero base carrier from
     * deleting visible mass in a deep continuous envelope.
     */
    static double coreFillForCoverageAndStrength(double coverage, double envelopeStrength) {
        double normalizedCoverage = clamp01(coverage);
        double strengthFill = coreFillForEnvelopeStrength(envelopeStrength);
        double normalizedStrength = clamp01(envelopeStrength);
        if (normalizedCoverage <= STRUCTURAL_CONTINUITY_COVERAGE
                || normalizedStrength <= STRUCTURAL_CONTINUITY_STRENGTH) {
            return strengthFill;
        }
        double safeCoverage = Math.max(normalizedCoverage, 1.0E-4D);
        double retainedBody = STRUCTURAL_CONTINUITY_DENSITY + LIVE_P05_EROSION_BITE;
        double continuityRequired = 1.0D / (safeCoverage * (1.0D - retainedBody)) - 1.0D;
        double continuityFill = Math.max(strengthFill,
                continuityRequired + LIVE_STRENGTH_FILL_HEADROOM);
        return lerp(
                smoothstep(STRUCTURAL_CONTINUITY_COVERAGE, 0.90D, normalizedCoverage)
                        * smoothstep(STRUCTURAL_CONTINUITY_STRENGTH, 0.87D, normalizedStrength),
                strengthFill,
                continuityFill
        );
    }

    /** Sensitivity of body density to the base field at this coverage. */
    static double baseFieldSensitivity(double coverage) {
        return 1.0D / Math.max(1.0D - coverageLowerBound(coverage), 1.0E-4D);
    }

    // -----------------------------------------------------------------
    // Stage 6: multi-scale detail erosion
    // -----------------------------------------------------------------

    /**
     * Subtractive multi-scale erosion applied across the whole storm body.
     *
     * <p>The pre-correction path multiplied by an edge-exposure factor that
     * decayed to zero above {@code cloud = 0.72} and then clamped the result to
     * an erosion floor, so the interior received no detail at all. The
     * subtractive form keeps a constant nonzero sensitivity of
     * {@link #STORM_EROSION} everywhere the result is unsaturated, while still
     * leaving the dense core intact by magnitude rather than by a gate.
     */
    static double erode(double body, double detailFbm) {
        return Math.max(body - (1.0D - clamp01(detailFbm)) * STORM_EROSION, 0.0D);
    }

    /** Sensitivity of final density to the detail field wherever the result is unsaturated. */
    static double detailFieldSensitivity() {
        return STORM_EROSION;
    }

    /** Full stages 5 and 6 for one sample. */
    static double finalDensity(double coverage, double baseField, double detailFbm) {
        return finalDensity(coverage, 1.0D, baseField, detailFbm);
    }

    static double finalDensity(
            double coverage,
            double envelopeStrength,
            double baseField,
            double detailFbm
    ) {
        return finalDensity(coverage, envelopeStrength, baseField, detailFbm, false);
    }

    static double finalDensity(
            double coverage,
            double envelopeStrength,
            double baseField,
            double detailFbm,
            boolean embeddedConvectiveOverlap
    ) {
        if (coverage <= 0.0D) {
            return 0.0D;
        }
        return erode(stormBody(coverage, envelopeStrength, baseField, embeddedConvectiveOverlap), detailFbm);
    }

    // -----------------------------------------------------------------
    // Shared helpers
    // -----------------------------------------------------------------

    static double remap(double value, double low1, double high1, double low2, double high2) {
        return low2 + (value - low1) * (high2 - low2) / Math.max(high1 - low1, 1.0E-6D);
    }

    static double smoothstep(double edge0, double edge1, double value) {
        double normalized = clamp01((value - edge0) / Math.max(1.0E-8D, edge1 - edge0));
        return normalized * normalized * (3.0D - 2.0D * normalized);
    }

    static double lerp(double amount, double start, double end) {
        return start + (end - start) * amount;
    }

    static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
