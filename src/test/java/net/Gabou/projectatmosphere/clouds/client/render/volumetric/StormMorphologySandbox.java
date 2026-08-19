package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Phase 4S positive morphology regressions (T101-T106).
 *
 * <p>These are the checks the previous acceptance set lacked. The old criteria
 * described only the absence of artifacts, which a smooth balloon satisfies;
 * these assert that the storm positively contains the structure FR-023
 * requires, and that its interior is formed by the volumetric noise field
 * rather than by descriptor geometry.
 *
 * <p>Every threshold comes from {@link StormMorphologyThresholds}, which is
 * re-derived from the measured noise by {@code StormDensityThresholdSandbox}.
 * None is chosen to make a check pass.
 *
 * <p>Run with {@code -Dphase4s.failFirst=true} to evaluate the same invariants
 * against {@link StormFieldSampler.Composition#AUDITED_PHASE_4R} and reproduce
 * the recorded fail-first evidence.
 */
public final class StormMorphologySandbox {
    /** Altitudes sampled for the structural relations, in blocks. */
    private static final double BASE_ALTITUDE = 252.0D;
    private static final double CORE_ALTITUDE = 320.0D;
    private static final double TOWER_ALTITUDE = 372.0D;
    private static final double ANVIL_ALTITUDE = 448.0D;

    private static final double SECTION_HALF_EXTENT = 320.0D;
    private static final double SECTION_STEP = 4.0D;
    /** Iso-level at which the visible silhouette is measured. */
    private static final double OCCUPIED_ISO = 0.10D;

    private static byte[] basePixels;
    private static byte[] detailPixels;

    private StormMorphologySandbox() {
    }

    public static void main(String[] args) {
        basePixels = CloudNoiseFieldModel.bakeBase();
        detailPixels = CloudNoiseFieldModel.bakeDetail();

        boolean failFirst = Boolean.getBoolean("phase4s.failFirst");
        StormFieldSampler.Composition composition = failFirst
                ? StormFieldSampler.Composition.AUDITED_PHASE_4R
                : StormFieldSampler.Composition.CORRECTED_PHASE_4S;

        List<Result> results = new ArrayList<>();
        run(results, "T101 interior noise influence", composition,
                StormMorphologySandbox::validateInteriorNoiseInfluence);
        run(results, "T102 occupied-region density variance", composition,
                StormMorphologySandbox::validateDensityVariance);
        run(results, "T103 multi-scale spectral contribution", composition,
                StormMorphologySandbox::validateSpectralContribution);
        run(results, "T104 geometric distance field", composition,
                StormMorphologySandbox::validateGeometricDistanceField);
        run(results, "T105 positive storm structure", composition,
                StormMorphologySandbox::validatePositiveStructure);
        run(results, "T106 rejected morphology forms", composition,
                StormMorphologySandbox::validateRejectedForms);
        run(results, "T117 envelope-side LOD cross-fade", composition,
                StormMorphologySandbox::validateEnvelopeCrossFade);

        report(results, failFirst);
    }

    // -----------------------------------------------------------------
    // T101 - the interior responds to the noise field (SC-013)
    // -----------------------------------------------------------------

    private static void validateInteriorNoiseInfluence(StormFieldSampler sampler) {
        List<StormLobeDescriptor> lobes = storm();
        double perturbation = 0.05D;
        double[] fields = new double[2];

        int interiorSamples = 0;
        int baseResponses = 0;
        int detailResponses = 0;
        double weakestBase = Double.MAX_VALUE;
        double weakestDetail = Double.MAX_VALUE;

        for (double y = 240.0D; y <= 470.0D; y += 6.0D) {
            for (double x = -200.0D; x <= 200.0D; x += 10.0D) {
                for (double z = -160.0D; z <= 160.0D; z += 10.0D) {
                    double coverage = sampler.coverageAt(lobes, x, y, z);
                    if (coverage < StormMorphologyThresholds.INTERIOR_COVERAGE) {
                        continue;
                    }
                    if (!deepInsideCoverage(sampler, lobes, x, y, z)) {
                        continue;
                    }
                    sampler.noiseFieldsAt(x, y, z, fields);
                    double reference = sampler.densityFromFields(coverage, fields[0], fields[1]);
                    if (reference <= StormMorphologyThresholds.OCCUPIED_DENSITY_MIN
                            || reference >= StormMorphologyThresholds.OCCUPIED_DENSITY_MAX) {
                        continue;
                    }
                    interiorSamples++;

                    // Perturb away from whichever clamp the field is nearer,
                    // otherwise a sample sitting at 0 or 1 registers as
                    // insensitive purely because the perturbation was clipped.
                    double baseStep = fields[0] > 0.5D ? -perturbation : perturbation;
                    double detailStep = fields[1] > 0.5D ? -perturbation : perturbation;
                    double basePerturbed = sampler.densityFromFields(
                            coverage, clamp01(fields[0] + baseStep), fields[1]);
                    double detailPerturbed = sampler.densityFromFields(
                            coverage, fields[0], clamp01(fields[1] + detailStep));

                    double requiredBase = StormMorphologyThresholds.INTERIOR_SENSITIVITY_FRACTION
                            * StormDensityModel.baseFieldSensitivity(coverage) * perturbation;
                    double requiredDetail = StormMorphologyThresholds.INTERIOR_SENSITIVITY_FRACTION
                            * StormDensityModel.detailFieldSensitivity() * perturbation;

                    double baseDelta = Math.abs(basePerturbed - reference);
                    double detailDelta = Math.abs(detailPerturbed - reference);
                    weakestBase = Math.min(weakestBase, baseDelta);
                    weakestDetail = Math.min(weakestDetail, detailDelta);
                    if (baseDelta >= requiredBase) {
                        baseResponses++;
                    }
                    if (detailDelta >= requiredDetail) {
                        detailResponses++;
                    }
                }
            }
        }

        require(interiorSamples >= 200,
                "too few unsaturated interior samples to judge noise influence: " + interiorSamples);
        double baseRate = baseResponses / (double) interiorSamples;
        double detailRate = detailResponses / (double) interiorSamples;
        require(baseRate >= 0.95D,
                "storm interior does not respond to the base noise field: only "
                        + percent(baseRate) + " of " + interiorSamples
                        + " unsaturated interior samples reached half the analytic derivative"
                        + " (weakest delta " + format(weakestBase) + ")");
        require(detailRate >= 0.95D,
                "storm interior does not respond to the detail noise field: only "
                        + percent(detailRate) + " of " + interiorSamples
                        + " unsaturated interior samples reached half the analytic derivative"
                        + " (weakest delta " + format(weakestDetail) + ")");
    }

    /** True when the probe is at least one lowest-octave wavelength inside the coverage boundary. */
    private static boolean deepInsideCoverage(
            StormFieldSampler sampler,
            List<StormLobeDescriptor> lobes,
            double x,
            double y,
            double z
    ) {
        double margin = StormMorphologyThresholds.LOWEST_DETAIL_WAVELENGTH_BLOCKS;
        return sampler.coverageAt(lobes, x + margin, y, z) >= StormMorphologyThresholds.INTERIOR_COVERAGE
                && sampler.coverageAt(lobes, x - margin, y, z) >= StormMorphologyThresholds.INTERIOR_COVERAGE
                && sampler.coverageAt(lobes, x, y, z + margin) >= StormMorphologyThresholds.INTERIOR_COVERAGE
                && sampler.coverageAt(lobes, x, y, z - margin) >= StormMorphologyThresholds.INTERIOR_COVERAGE;
    }

    // -----------------------------------------------------------------
    // T102 - occupied regions are not uniform (SC-012)
    // -----------------------------------------------------------------

    private static void validateDensityVariance(StormFieldSampler sampler) {
        List<StormLobeDescriptor> lobes = storm();
        double edge = StormMorphologyThresholds.MIN_VARIANCE_REGION_BLOCKS;
        double step = 4.0D;

        int regions = 0;
        double weakestSd = Double.MAX_VALUE;
        String weakestRegion = "";
        List<String> violations = new ArrayList<>();

        for (double originY = 250.0D; originY + edge <= 460.0D; originY += edge) {
            for (double originX = -180.0D; originX + edge <= 180.0D; originX += edge) {
                for (double originZ = -140.0D; originZ + edge <= 140.0D; originZ += edge) {
                    double sum = 0.0D;
                    double sumSquares = 0.0D;
                    int count = 0;
                    for (double y = originY; y < originY + edge; y += step) {
                        for (double x = originX; x < originX + edge; x += step) {
                            for (double z = originZ; z < originZ + edge; z += step) {
                                double density = sampler.densityAt(lobes, x, y, z);
                                if (density <= StormMorphologyThresholds.OCCUPIED_DENSITY_MIN
                                        || density >= StormMorphologyThresholds.OCCUPIED_DENSITY_MAX) {
                                    continue;
                                }
                                sum += density;
                                sumSquares += density * density;
                                count++;
                            }
                        }
                    }
                    // A region needs enough unsaturated occupied samples for a
                    // standard deviation to mean anything.
                    if (count < 64) {
                        continue;
                    }
                    regions++;
                    double mean = sum / count;
                    double sd = Math.sqrt(Math.max(sumSquares / count - mean * mean, 0.0D));
                    if (sd < weakestSd) {
                        weakestSd = sd;
                        weakestRegion = "(" + (int) originX + "," + (int) originY
                                + "," + (int) originZ + ")";
                    }
                    if (sd < StormMorphologyThresholds.MIN_OCCUPIED_REGION_SD) {
                        violations.add("region (" + (int) originX + "," + (int) originY + ","
                                + (int) originZ + ") sd=" + format(sd));
                    }
                }
            }
        }

        require(regions >= 8,
                "too few occupied regions of at least " + format(edge)
                        + " blocks to judge density variance: " + regions);
        require(violations.isEmpty(),
                "visually uniform storm regions found (minimum sd "
                        + format(StormMorphologyThresholds.MIN_OCCUPIED_REGION_SD)
                        + ", weakest " + format(weakestSd) + " at " + weakestRegion + "): "
                        + join(violations, 4));
    }

    // -----------------------------------------------------------------
    // T103 - every configured octave reaches the result (SC-014)
    // -----------------------------------------------------------------

    private static void validateSpectralContribution(StormFieldSampler sampler) {
        List<StormLobeDescriptor> lobes = storm();
        double[] channels = new double[4];

        // Variance of the final density attributable to each detail octave,
        // measured by neutralizing one octave at a time to its mean.
        double[] contributions = new double[3];
        int samples = 0;

        for (double y = 250.0D; y <= 460.0D; y += 5.0D) {
            for (double x = -180.0D; x <= 180.0D; x += 7.0D) {
                for (double z = -140.0D; z <= 140.0D; z += 7.0D) {
                    double coverage = sampler.coverageAt(lobes, x, y, z);
                    if (coverage <= 0.0D) {
                        continue;
                    }
                    sampler.noiseChannelsAt(x, y, z, channels);
                    double fullFbm = StormDensityModel.detailFbm(
                            channels[1], channels[2], channels[3]);
                    double reference = sampler.densityFromFields(coverage, channels[0], fullFbm);
                    if (reference <= StormMorphologyThresholds.OCCUPIED_DENSITY_MIN
                            || reference >= StormMorphologyThresholds.OCCUPIED_DENSITY_MAX) {
                        continue;
                    }
                    samples++;
                    for (int octave = 0; octave < 3; octave++) {
                        double r = octave == 0 ? 0.5D : channels[1];
                        double g = octave == 1 ? 0.5D : channels[2];
                        double b = octave == 2 ? 0.5D : channels[3];
                        double neutralized = sampler.densityFromFields(
                                coverage, channels[0],
                                StormDensityModel.detailFbm(r, g, b));
                        double delta = reference - neutralized;
                        contributions[octave] += delta * delta;
                    }
                }
            }
        }

        require(samples >= 200,
                "too few unsaturated samples to judge spectral contribution: " + samples);
        double total = contributions[0] + contributions[1] + contributions[2];
        require(total > 0.0D,
                "no detail octave reaches the final storm density: the interior carries"
                        + " no multi-scale variation at all");

        double[] required = {
                StormMorphologyThresholds.DETAIL_BAND_SHARE_R,
                StormMorphologyThresholds.DETAIL_BAND_SHARE_G,
                StormMorphologyThresholds.DETAIL_BAND_SHARE_B
        };
        String[] names = {"R (22.7-5.7 blocks)", "G (11.4-2.8 blocks)", "B (5.7-1.4 blocks)"};
        List<String> violations = new ArrayList<>();
        for (int octave = 0; octave < 3; octave++) {
            double share = contributions[octave] / total;
            double minimum = required[octave] * StormMorphologyThresholds.MIN_BAND_SHARE_FRACTION;
            if (share < minimum) {
                violations.add("octave " + names[octave] + " share=" + format(share)
                        + " below derived minimum " + format(minimum));
            }
        }
        require(violations.isEmpty(),
                "storm surface variation is missing spatial frequencies: " + join(violations, 3));
    }

    // -----------------------------------------------------------------
    // T104 - the distance field is real geometry (SC-015)
    // -----------------------------------------------------------------

    private static void validateGeometricDistanceField(StormFieldSampler sampler) {
        List<StormLobeDescriptor> lobes = storm();
        List<String> violations = new ArrayList<>();

        for (StormLobeDescriptor lobe : lobes) {
            double centreY = (lobe.baseY() + lobe.topY()) * 0.5D;
            double inside = StormLobeEvaluator.signedDistanceAt(
                    lobe, lobe.centerX(), centreY, lobe.centerZ());
            if (!(inside < 0.0D)) {
                violations.add(lobe.role() + " centre distance " + format(inside)
                        + " is not negative");
            }

            // Validity and monotonicity outside the surface, including
            // directly above the lobe where a density-space pseudo-distance
            // carries no information at all.
            double previousLateral = inside;
            for (double offset = 0.0D; offset <= 600.0D; offset += 20.0D) {
                double distance = StormLobeEvaluator.signedDistanceAt(
                        lobe, lobe.centerX() + offset, centreY, lobe.centerZ());
                if (!Double.isFinite(distance)) {
                    violations.add(lobe.role() + " lateral distance not finite at +"
                            + format(offset));
                    break;
                }
                if (offset > 0.0D && distance < previousLateral - 1.0E-6D) {
                    violations.add(lobe.role() + " lateral distance decreased moving outward at +"
                            + format(offset));
                    break;
                }
                previousLateral = distance;
            }

            double previousVertical = inside;
            for (double offset = 0.0D; offset <= 600.0D; offset += 20.0D) {
                double distance = StormLobeEvaluator.signedDistanceAt(
                        lobe, lobe.centerX(), lobe.topY() + offset, lobe.centerZ());
                if (!Double.isFinite(distance)) {
                    violations.add(lobe.role() + " vertical distance not finite at +"
                            + format(offset));
                    break;
                }
                if (offset > 0.0D && distance < previousVertical - 1.0E-6D) {
                    violations.add(lobe.role() + " vertical distance decreased moving above the cap at +"
                            + format(offset));
                    break;
                }
                previousVertical = distance;
            }

            // Above the cap the field must grow roughly one block per block:
            // that is what makes it a distance rather than a shape value.
            double near = StormLobeEvaluator.signedDistanceAt(
                    lobe, lobe.centerX(), lobe.topY() + 40.0D, lobe.centerZ());
            double far = StormLobeEvaluator.signedDistanceAt(
                    lobe, lobe.centerX(), lobe.topY() + 140.0D, lobe.centerZ());
            double rate = (far - near) / 100.0D;
            if (rate < 0.75D || rate > 1.25D) {
                violations.add(lobe.role() + " vertical distance grows at " + format(rate)
                        + " blocks per block, so it is not world-scaled");
            }
        }

        // A lobe whose local density is zero must still take part in the
        // union. The unambiguous form of that invariant: find probes where the
        // NEAREST lobe by surface distance is one whose local density is
        // exactly zero, and require that removing it changes the envelope. The
        // pre-correction path skipped precisely these lobes, which is why
        // blends collapsed into visible primitive intersections just outside a
        // lobe surface. A lobe further away than the blend radius is correctly
        // irrelevant, so those are not asserted on.
        int exercised = 0;
        for (double y = 230.0D; y <= 470.0D && exercised < 24; y += 7.0D) {
            for (double x = -220.0D; x <= 220.0D && exercised < 24; x += 11.0D) {
                for (double z = -160.0D; z <= 160.0D && exercised < 24; z += 13.0D) {
                    int nearest = -1;
                    double nearestDistance = Double.MAX_VALUE;
                    for (int index = 0; index < lobes.size(); index++) {
                        double distance = StormLobeEvaluator.signedDistanceAt(
                                lobes.get(index), x, y, z);
                        if (!Double.isFinite(distance)) {
                            violations.add("lobe distance not finite inside the storm volume");
                            continue;
                        }
                        if (distance < nearestDistance) {
                            nearestDistance = distance;
                            nearest = index;
                        }
                    }
                    if (nearest < 0) {
                        continue;
                    }
                    // Only probes where the nearest lobe contributes no
                    // legacy density exercise the no-skip rule.
                    if (StormLobeEvaluator.densityAt(lobes.get(nearest), x, y, z) > 0.0D) {
                        continue;
                    }
                    if (nearestDistance > StormMorphologyThresholds
                            .LOWEST_DETAIL_WAVELENGTH_BLOCKS) {
                        continue;
                    }
                    List<StormLobeDescriptor> without = new ArrayList<>(lobes);
                    without.remove(nearest);
                    // Compare the unioned distance, not the envelope: outside
                    // the boundary the envelope saturates to zero either way,
                    // which would hide the very skip this asserts against.
                    double withAll = StormLobeEvaluator.unionDistanceAt(lobes, x, y, z);
                    double withoutOne = StormLobeEvaluator.unionDistanceAt(without, x, y, z);
                    exercised++;
                    if (Math.abs(withAll - withoutOne) < 1.0E-6D) {
                        violations.add("the nearest lobe (surface " + format(nearestDistance)
                                + " blocks away) was dropped from the union because its local"
                                + " density evaluated to zero");
                    }
                }
            }
        }
        require(exercised > 0,
                "no probe found where the nearest lobe has zero local density,"
                        + " so the no-skip invariant is not being exercised");

        require(violations.isEmpty(),
                "descriptor distance field is not real geometry: " + join(violations, 4));
    }

    // -----------------------------------------------------------------
    // T105 - the storm positively contains its structure (SC-011, FR-023)
    // -----------------------------------------------------------------

    private static void validatePositiveStructure(StormFieldSampler sampler) {
        List<StormLobeDescriptor> lobes = storm();
        Section base = section(sampler, lobes, BASE_ALTITUDE);
        Section core = section(sampler, lobes, CORE_ALTITUDE);
        Section tower = section(sampler, lobes, TOWER_ALTITUDE);
        Section anvil = section(sampler, lobes, ANVIL_ALTITUDE);

        List<String> violations = new ArrayList<>();

        if (base.occupiedCells() == 0) {
            violations.add("no broad lower cloud base at all");
        }
        if (base.components() != 1) {
            violations.add("lower cloud base is not one continuous mass: "
                    + base.components() + " components");
        }
        if (!(base.equivalentRadius() > tower.equivalentRadius())) {
            violations.add("base radius " + format(base.equivalentRadius())
                    + " is not broader than the tower " + format(tower.equivalentRadius()));
        }
        if (!(tower.equivalentRadius() < base.equivalentRadius())) {
            violations.add("no vertical narrowing between base and tower");
        }
        if (!(anvil.equivalentRadius() > tower.equivalentRadius())) {
            violations.add("anvil radius " + format(anvil.equivalentRadius())
                    + " does not spread beyond the tower " + format(tower.equivalentRadius()));
        }
        if (!(core.meanDensity() > base.meanDensity())) {
            violations.add("no dense convective core: core mean " + format(core.meanDensity())
                    + " does not exceed the base mean " + format(base.meanDensity()));
        }

        // Vertical continuity: the tower must be connected to the base and the
        // anvil to the tower, with no empty band between them.
        double gap = largestEmptyVerticalBand(sampler, lobes);
        if (gap > StormMorphologyThresholds.LOWEST_DETAIL_WAVELENGTH_BLOCKS) {
            violations.add("base, tower and anvil are not continuously connected:"
                    + " largest empty vertical band " + format(gap) + " blocks");
        }

        // Progressive narrowing between base and anvil root, tolerant of the
        // local widening the noise legitimately produces.
        double previousRadius = Double.MAX_VALUE;
        double worstWidening = 0.0D;
        for (double y = BASE_ALTITUDE; y <= TOWER_ALTITUDE; y += 8.0D) {
            double radius = section(sampler, lobes, y).equivalentRadius();
            if (radius > 0.0D && previousRadius < Double.MAX_VALUE) {
                worstWidening = Math.max(worstWidening, radius - previousRadius);
            }
            if (radius > 0.0D) {
                previousRadius = radius;
            }
        }
        if (worstWidening > base.equivalentRadius() * 0.25D) {
            violations.add("vertical narrowing is not progressive: a section widened by "
                    + format(worstWidening) + " blocks between base and tower");
        }

        require(violations.isEmpty(),
                "storm does not positively contain its required structure: "
                        + join(violations, 5));
    }

    // -----------------------------------------------------------------
    // T106 - rejected forms (SC-011, FR-024)
    // -----------------------------------------------------------------

    private static void validateRejectedForms(StormFieldSampler sampler) {
        List<StormLobeDescriptor> lobes = storm();
        List<String> violations = new ArrayList<>();

        // A surface sculpted by a field of wavelength lambda whose amplitude
        // fully modulates the density must wander by a non-trivial fraction of
        // lambda. A quarter wavelength is a conservative floor; a smooth
        // analytic ellipsoid yields essentially zero.
        double minimumWander = 0.25D * StormMorphologyThresholds.LOWEST_DETAIL_WAVELENGTH_BLOCKS;

        double[] altitudes = {BASE_ALTITUDE, CORE_ALTITUDE, TOWER_ALTITUDE, ANVIL_ALTITUDE};
        String[] names = {"base", "core", "tower", "anvil"};
        for (int index = 0; index < altitudes.length; index++) {
            Section slice = section(sampler, lobes, altitudes[index]);
            if (slice.occupiedCells() == 0) {
                continue;
            }
            if (slice.components() > 1) {
                violations.add(names[index] + " section has " + slice.components()
                        + " components: isolated ears or bulbs");
            }
            double wander = silhouetteWander(sampler, lobes, altitudes[index]);
            if (wander < minimumWander) {
                violations.add(names[index] + " silhouette wanders only " + format(wander)
                        + " blocks (minimum " + format(minimumWander)
                        + "): a smooth balloon or a recognizable analytic primitive");
            }
        }

        // A flat slab underside: the attachment height must vary across the
        // footprint by more than the coarsest sculpting wavelength.
        double undersideVariation = undersideVariation(sampler, lobes);
        if (undersideVariation < minimumWander) {
            violations.add("storm underside varies by only " + format(undersideVariation)
                    + " blocks across its footprint: a flat slab");
        }

        require(violations.isEmpty(),
                "storm contains rejected morphology forms: " + join(violations, 5));
    }

    // -----------------------------------------------------------------
    // T117 - the distance cross-fade acts on the envelope, not the body
    // -----------------------------------------------------------------

    private static void validateEnvelopeCrossFade(StormFieldSampler sampler) {
        List<StormLobeDescriptor> full = storm();
        List<String> violations = new ArrayList<>();

        double probeX = 0.0D;
        double probeY = 300.0D;
        double probeZ = 0.0D;

        // The analytic side of the LOD cross-fade is carried by detailWeight.
        // It must scale the coverage envelope - the quantity that decides how
        // much of the noise field becomes cloud - and never be applied to the
        // final density, which would dim a storm uniformly instead of
        // dissolving it into the broad map.
        double previousCoverage = Double.MAX_VALUE;
        double previousDensity = Double.MAX_VALUE;
        for (double weight = 1.0D; weight >= 0.0D; weight -= 0.125D) {
            List<StormLobeDescriptor> faded = new ArrayList<>();
            for (StormLobeDescriptor lobe : full) {
                faded.add(lobe.withDetailWeight((float) weight));
            }
            double coverage = StormLobeEvaluator.coverageEnvelopeAt(faded, probeX, probeY, probeZ);
            double density = sampler.densityAt(faded, probeX, probeY, probeZ);
            if (coverage > previousCoverage + 1.0E-9D) {
                violations.add("coverage rose while the analytic LOD weight fell, at weight "
                        + format(weight));
            }
            if (density > previousDensity + 1.0E-9D) {
                violations.add("density rose while the analytic LOD weight fell, at weight "
                        + format(weight));
            }
            previousCoverage = coverage;
            previousDensity = density;
        }

        List<StormLobeDescriptor> extinguished = new ArrayList<>();
        for (StormLobeDescriptor lobe : full) {
            extinguished.add(lobe.withDetailWeight(0.0F));
        }
        double zeroCoverage = StormLobeEvaluator.coverageEnvelopeAt(
                extinguished, probeX, probeY, probeZ);
        double zeroDensity = sampler.densityAt(extinguished, probeX, probeY, probeZ);
        if (zeroCoverage > 1.0E-9D) {
            violations.add("a fully faded-out group still contributes coverage "
                    + format(zeroCoverage) + ", so the broad map cannot take ownership");
        }
        if (zeroDensity > 1.0E-9D) {
            violations.add("a fully faded-out group still contributes density "
                    + format(zeroDensity));
        }

        // Fading must sparsify the body rather than uniformly dimming it: the
        // envelope is what the noise stages consume, so a half-faded group has
        // to lose its dense core before it loses its extent.
        List<StormLobeDescriptor> half = new ArrayList<>();
        for (StormLobeDescriptor lobe : full) {
            half.add(lobe.withDetailWeight(0.5F));
        }
        double fullCoverage = StormLobeEvaluator.coverageEnvelopeAt(full, probeX, probeY, probeZ);
        double halfCoverage = StormLobeEvaluator.coverageEnvelopeAt(half, probeX, probeY, probeZ);
        if (!(halfCoverage < fullCoverage)) {
            violations.add("half-faded coverage " + format(halfCoverage)
                    + " did not fall below full coverage " + format(fullCoverage));
        }

        require(violations.isEmpty(),
                "analytic LOD cross-fade does not act on the coverage envelope: "
                        + join(violations, 4));
    }

    // -----------------------------------------------------------------
    // Measurement helpers
    // -----------------------------------------------------------------

    private static Section section(
            StormFieldSampler sampler,
            List<StormLobeDescriptor> lobes,
            double y
    ) {
        int cells = (int) Math.round((SECTION_HALF_EXTENT * 2.0D) / SECTION_STEP) + 1;
        boolean[][] occupied = new boolean[cells][cells];
        double sum = 0.0D;
        int occupiedCells = 0;
        for (int ix = 0; ix < cells; ix++) {
            double x = -SECTION_HALF_EXTENT + ix * SECTION_STEP;
            for (int iz = 0; iz < cells; iz++) {
                double z = -SECTION_HALF_EXTENT + iz * SECTION_STEP;
                double density = sampler.densityAt(lobes, x, y, z);
                if (density >= OCCUPIED_ISO) {
                    occupied[ix][iz] = true;
                    occupiedCells++;
                    sum += density;
                }
            }
        }
        double area = occupiedCells * SECTION_STEP * SECTION_STEP;
        double equivalentRadius = Math.sqrt(area / Math.PI);
        double mean = occupiedCells > 0 ? sum / occupiedCells : 0.0D;
        return new Section(
                equivalentRadius,
                countSubstantialComponents(occupied, occupiedCells),
                occupiedCells,
                mean
        );
    }

    /**
     * Standard deviation, in blocks, of the silhouette radius measured on rays
     * from the section centroid. A smooth analytic primitive gives a value
     * near zero once its elliptical trend is removed.
     */
    private static double silhouetteWander(
            StormFieldSampler sampler,
            List<StormLobeDescriptor> lobes,
            double y
    ) {
        int rays = 128;
        double[] radii = new double[rays];
        int found = 0;
        for (int ray = 0; ray < rays; ray++) {
            double angle = 2.0D * Math.PI * ray / rays;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            double last = 0.0D;
            for (double radius = 2.0D; radius <= SECTION_HALF_EXTENT; radius += 2.0D) {
                if (sampler.densityAt(lobes, dx * radius, y, dz * radius) >= OCCUPIED_ISO) {
                    last = radius;
                }
            }
            radii[ray] = last;
            if (last > 0.0D) {
                found++;
            }
        }
        if (found < rays / 2) {
            return Double.MAX_VALUE;
        }
        // Remove the best-fit ellipse, so an elongated but perfectly smooth
        // shape is not credited with variation it does not have.
        double[] residual = removeEllipseTrend(radii);
        double mean = 0.0D;
        for (double value : residual) {
            mean += value;
        }
        mean /= residual.length;
        double sum = 0.0D;
        for (double value : residual) {
            double delta = value - mean;
            sum += delta * delta;
        }
        return Math.sqrt(sum / residual.length);
    }

    /**
     * Removes the constant and second-harmonic terms of the radius series,
     * which together are exactly the radius profile of an ellipse.
     */
    private static double[] removeEllipseTrend(double[] radii) {
        int count = radii.length;
        double a0 = 0.0D;
        double a2 = 0.0D;
        double b2 = 0.0D;
        for (int index = 0; index < count; index++) {
            double angle = 2.0D * Math.PI * index / count;
            a0 += radii[index];
            a2 += radii[index] * Math.cos(2.0D * angle);
            b2 += radii[index] * Math.sin(2.0D * angle);
        }
        a0 /= count;
        a2 = 2.0D * a2 / count;
        b2 = 2.0D * b2 / count;
        double[] residual = new double[count];
        for (int index = 0; index < count; index++) {
            double angle = 2.0D * Math.PI * index / count;
            residual[index] = radii[index]
                    - (a0 + a2 * Math.cos(2.0D * angle) + b2 * Math.sin(2.0D * angle));
        }
        return residual;
    }

    private static double undersideVariation(
            StormFieldSampler sampler,
            List<StormLobeDescriptor> lobes
    ) {
        List<Double> heights = new ArrayList<>();
        for (double x = -160.0D; x <= 160.0D; x += 10.0D) {
            for (double z = -120.0D; z <= 120.0D; z += 10.0D) {
                for (double y = 200.0D; y <= 340.0D; y += 2.0D) {
                    if (sampler.densityAt(lobes, x, y, z) >= OCCUPIED_ISO) {
                        heights.add(y);
                        break;
                    }
                }
            }
        }
        if (heights.size() < 32) {
            return 0.0D;
        }
        double mean = 0.0D;
        for (double value : heights) {
            mean += value;
        }
        mean /= heights.size();
        double sum = 0.0D;
        for (double value : heights) {
            double delta = value - mean;
            sum += delta * delta;
        }
        return Math.sqrt(sum / heights.size());
    }

    private static double largestEmptyVerticalBand(
            StormFieldSampler sampler,
            List<StormLobeDescriptor> lobes
    ) {
        double largest = 0.0D;
        double current = 0.0D;
        boolean seenOccupied = false;
        for (double y = BASE_ALTITUDE; y <= ANVIL_ALTITUDE; y += 2.0D) {
            boolean occupied = section(sampler, lobes, y).occupiedCells() > 0;
            if (occupied) {
                seenOccupied = true;
                current = 0.0D;
            } else if (seenOccupied) {
                current += 2.0D;
                largest = Math.max(largest, current);
            }
        }
        return largest;
    }

    /**
     * Counts connected components that carry a meaningful share of the
     * section's mass.
     *
     * <p>A noise-formed cloud legitimately sheds small detached wisps at its
     * boundary - that is what a volumetric cloud looks like, and counting them
     * would penalize exactly the multi-scale detail FR-023 requires. What
     * FR-024 rejects is an <em>isolated ear or bulb</em>: a substantial mass
     * standing apart from the body. The threshold is therefore a share of the
     * section area rather than an absolute cell count.
     */
    private static final double SUBSTANTIAL_COMPONENT_SHARE = 0.10D;

    private static int countSubstantialComponents(boolean[][] occupied, int occupiedCells) {
        if (occupiedCells == 0) {
            return 0;
        }
        int minimumCells = (int) Math.ceil(occupiedCells * SUBSTANTIAL_COMPONENT_SHARE);
        int size = occupied.length;
        boolean[][] seen = new boolean[size][size];
        int components = 0;
        int[] stack = new int[size * size];
        for (int startX = 0; startX < size; startX++) {
            for (int startZ = 0; startZ < size; startZ++) {
                if (!occupied[startX][startZ] || seen[startX][startZ]) {
                    continue;
                }
                int componentCells = 0;
                int top = 0;
                stack[top++] = startX * size + startZ;
                seen[startX][startZ] = true;
                while (top > 0) {
                    int packed = stack[--top];
                    componentCells++;
                    int x = packed / size;
                    int z = packed % size;
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            int nx = x + dx;
                            int nz = z + dz;
                            if (nx < 0 || nz < 0 || nx >= size || nz >= size) {
                                continue;
                            }
                            if (!occupied[nx][nz] || seen[nx][nz]) {
                                continue;
                            }
                            seen[nx][nz] = true;
                            stack[top++] = nx * size + nz;
                        }
                    }
                }
                if (componentCells >= minimumCells) {
                    components++;
                }
            }
        }
        return components;
    }

    // -----------------------------------------------------------------
    // Fixture
    // -----------------------------------------------------------------

    /**
     * A complete severe storm group with all four roles, sized like the
     * authoritative generator produces: overlapping BASE members around a
     * common floor, CORE members rooted in them, TOWER members narrowing and
     * leaning, and ANVIL members spreading above the tower crown.
     */
    private static List<StormLobeDescriptor> storm() {
        UUID group = UUID.nameUUIDFromBytes("phase4s-storm".getBytes());
        return List.of(
                descriptor(group, 0, 9, StormLobeDescriptor.Role.BASE,
                        -28.0D, -6.0D, 224.0F, 300.0F, 172.0F, 138.0F),
                descriptor(group, 1, 9, StormLobeDescriptor.Role.BASE,
                        26.0D, 10.0D, 226.0F, 296.0F, 158.0F, 126.0F),
                descriptor(group, 2, 9, StormLobeDescriptor.Role.CORE,
                        -12.0D, 2.0D, 250.0F, 368.0F, 98.0F, 82.0F),
                descriptor(group, 3, 9, StormLobeDescriptor.Role.CORE,
                        14.0D, -8.0D, 256.0F, 374.0F, 92.0F, 76.0F),
                descriptor(group, 4, 9, StormLobeDescriptor.Role.TOWER,
                        -6.0D, 0.0D, 300.0F, 448.0F, 58.0F, 48.0F),
                descriptor(group, 5, 9, StormLobeDescriptor.Role.TOWER,
                        16.0D, -6.0D, 308.0F, 456.0F, 54.0F, 44.0F),
                descriptor(group, 6, 9, StormLobeDescriptor.Role.ANVIL,
                        10.0D, 0.0D, 396.0F, 504.0F, 206.0F, 82.0F),
                descriptor(group, 7, 9, StormLobeDescriptor.Role.ANVIL,
                        44.0D, -8.0D, 404.0F, 508.0F, 184.0F, 74.0F),
                descriptor(group, 8, 9, StormLobeDescriptor.Role.ANVIL,
                        -30.0D, 6.0D, 400.0F, 500.0F, 176.0F, 70.0F)
        );
    }

    private static StormLobeDescriptor descriptor(
            UUID group,
            int memberIndex,
            int memberCount,
            StormLobeDescriptor.Role role,
            double centerX,
            double centerZ,
            float baseY,
            float topY,
            float majorRadius,
            float minorRadius
    ) {
        return new StormLobeDescriptor(
                UUID.nameUUIDFromBytes(("field-" + group + "-" + memberIndex).getBytes()),
                group,
                memberIndex,
                memberCount,
                0,
                role,
                centerX,
                centerZ,
                baseY,
                topY,
                majorRadius,
                minorRadius,
                0.3420F,
                0.9397F,
                role == StormLobeDescriptor.Role.TOWER ? 26.0F : 8.0F,
                role == StormLobeDescriptor.Role.TOWER ? 6.0F : 2.0F,
                0.92F,
                0.14F,
                0.37F,
                0.62F,
                0.78F,
                1.0F
        );
    }

    // -----------------------------------------------------------------
    // Harness
    // -----------------------------------------------------------------

    private static void run(
            List<Result> results,
            String name,
            StormFieldSampler.Composition composition,
            Regression regression
    ) {
        StormFieldSampler sampler = StormFieldSampler.of(composition, basePixels, detailPixels);
        try {
            regression.run(sampler);
            results.add(new Result(name, true, "invariant satisfied"));
        } catch (Throwable failure) {
            results.add(new Result(name, false, oneLine(failure.getMessage())));
        }
    }

    private static void report(List<Result> results, boolean failFirst) {
        int failures = 0;
        for (Result result : results) {
            failures += result.passed() ? 0 : 1;
            System.out.println("PHASE4S_RESULT|" + result.name() + "|"
                    + (result.passed() ? "PASSED" : "FAILED") + "|" + result.detail());
        }
        if (failFirst) {
            require(failures > 0,
                    "Phase 4S fail-first expected the audited composition to fail, but every"
                            + " invariant passed");
            throw new IllegalStateException("Phase 4S fail-first captured " + failures + "/"
                    + results.size() + " expected invariant failures against the audited"
                    + " composition");
        }
        if (failures > 0) {
            throw new IllegalStateException("Phase 4S morphology regressions failed: "
                    + failures + "/" + results.size());
        }
    }

    @FunctionalInterface
    private interface Regression {
        void run(StormFieldSampler sampler) throws Exception;
    }

    private record Result(String name, boolean passed, String detail) {
    }

    private record Section(
            double equivalentRadius,
            int components,
            int occupiedCells,
            double meanDensity
    ) {
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static String join(List<String> values, int limit) {
        List<String> shown = values.size() <= limit ? values : values.subList(0, limit);
        String text = String.join("; ", shown);
        return values.size() > limit ? text + "; (+" + (values.size() - limit) + " more)" : text;
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value * 100.0D);
    }

    private static String oneLine(String value) {
        return value == null ? "null" : value.replace("\n", " ").replace("|", "OR").trim();
    }
}
