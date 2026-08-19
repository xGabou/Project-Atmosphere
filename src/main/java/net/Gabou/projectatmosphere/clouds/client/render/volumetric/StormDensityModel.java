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
     * tile therefore spans {@code 1 / 0.0052 = 192.3} blocks, so the base
     * Worley octaves (periods 8/16/32) have 24.0, 12.0 and 6.0 block
     * wavelengths.
     */
    static final double STORM_BASE_NOISE_SCALE = 0.0052D;

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

    private StormDensityModel() {
    }

    // -----------------------------------------------------------------
    // Noise domains (mirror of the GLSL helpers)
    // -----------------------------------------------------------------

    static void baseNoiseDomain(double x, double y, double z, double scale, double[] out) {
        double rx = x * 0.8138D + y * 0.2962D - z * 0.5000D;
        double ry = -x * 0.1401D + y * 0.9408D + z * 0.3085D;
        double rz = x * 0.5630D - y * 0.1677D + z * 0.8090D;
        out[0] = rx * scale + warpX(x, y, z) * 0.31D;
        out[1] = ry * scale + warpY(x, y, z) * 0.31D;
        out[2] = rz * scale + warpZ(x, y, z) * 0.31D;
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
     * base field, and at full coverage it reaches {@code -CORE_FILL} so the
     * convective core stays dense while still varying with the base field.
     * The partial derivative with respect to {@code baseField} is
     * {@code 1 / (1 - lowerBound)}, which is strictly positive for every
     * coverage value - the interior is never noise-independent.
     */
    static double stormBody(double coverage, double baseField) {
        double lowerBound = coverageLowerBound(coverage);
        return clamp01((baseField - lowerBound) / Math.max(1.0D - lowerBound, 1.0E-4D));
    }

    static double coverageLowerBound(double coverage) {
        return lerp(clamp01(coverage), 1.0D, -CORE_FILL);
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
        if (coverage <= 0.0D) {
            return 0.0D;
        }
        return erode(stormBody(coverage, baseField), detailFbm);
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
