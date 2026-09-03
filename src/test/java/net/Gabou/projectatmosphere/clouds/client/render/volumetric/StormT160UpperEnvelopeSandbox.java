package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * T160: bounded upper-cloud root-cause experiment.
 *
 * <p>The severe cumulonimbus reads from ABOVE and SIDE as a rounded dome whose
 * horizontal expansion stops early and whose top looks cut off. This determines
 * whether the upper TOWER/ANVIL morphology is <em>terminated</em> before the
 * existing profile reaches its natural maximum width, or whether the existing
 * profile inherently produces that cap.
 *
 * <p>It measures the real production density path - descriptor envelope, base
 * noise remap, multi-scale erosion, final density - on the real baked noise
 * volumes and one real ten-member severe fixture. Nothing here is a rendering
 * approximation: {@link StormDensityModel} is the CPU authority the shader
 * mirrors, so the cross-sections are the production density, not descriptor
 * geometry.
 *
 * <p>Diagnostic only. The relaxed arm is off by default, is enabled around a
 * single measurement and switched back, and no value in it is a shipping
 * proposal - final morphology belongs to T098b.
 */
public final class StormT160UpperEnvelopeSandbox {

    /** Iso-level at which the visible silhouette is measured, as elsewhere. */
    private static final double OCCUPIED_ISO = 0.10D;
    /** Any density at all, for the outermost support. */
    private static final double NONZERO_ISO = 1.0E-4D;
    /** Representative dense material, well above the visible threshold. */
    private static final double DENSE_ISO = 0.35D;

    /** Half-extent of each horizontal section, in blocks. */
    private static final double SECTION_HALF_EXTENT = 900.0D;
    private static final double SECTION_STEP = 6.0D;
    /** Vertical step for the support scan. */
    private static final double SUPPORT_STEP = 4.0D;

    private static byte[] basePixels;
    private static byte[] detailPixels;

    private StormT160UpperEnvelopeSandbox() {
    }

    public static void main(String[] args) {
        basePixels = CloudNoiseFieldModel.bakeBase();
        detailPixels = CloudNoiseFieldModel.bakeDetail();
        StormFieldSampler sampler = StormFieldSampler.of(
                StormFieldSampler.Composition.CORRECTED_PHASE_4S, basePixels, detailPixels);
        List<StormLobeDescriptor> lobes = severeStorm();

        System.out.println("=== T160 upper-cloud root-cause experiment ===");
        System.out.printf(Locale.ROOT,
                "fixture: %d descriptors, noise=%s, iso=%.2f dense=%.2f nonzero=%.0e%n",
                lobes.size(), sampler.hasNoise(), OCCUPIED_ISO, DENSE_ISO, NONZERO_ISO);

        step1Constraints(lobes);
        Geometry geometry = systemGeometry(lobes);
        step2Baseline(sampler, lobes, geometry);
        step4RelaxedAb(sampler, lobes, geometry);
        step5StageTrace(sampler, lobes, geometry);
        step6Transition(sampler, lobes, geometry);

        // The arm must be off when this process ends, and off for every other
        // consumer of the evaluator in the same JVM.
        StormT160UpperExtentArm.setRelaxed(false);
        System.out.printf(Locale.ROOT,
                "%n=== T160 complete; relaxed arm restored to %s ===%n",
                StormT160UpperExtentArm.relaxed());
    }

    // -----------------------------------------------------------------
    // Step 1 - enumerate every production upper extent constraint
    // -----------------------------------------------------------------

    private static void step1Constraints(List<StormLobeDescriptor> lobes) {
        System.out.println("\n--- STEP 1: production upper extent constraints ---");
        System.out.println("expression|role|value|what it limits");
        System.out.printf(Locale.ROOT,
                "profileRadius lerp knee|ANVIL|%.2f|radius stops growing above this height fraction%n",
                StormT160UpperExtentArm.ANVIL_RADIUS_KNEE);
        System.out.printf(Locale.ROOT,
                "profileRadius lerp endpoint|ANVIL|%.2f|maximum radius multiplier%n",
                StormT160UpperExtentArm.ANVIL_RADIUS_MAX);
        System.out.println(
                "profileRadius sin term|ANVIL|0.08 pow 0.55|adds at mid-height, zero at both caps");
        System.out.println(
                "profileRadius top taper|ANVIL|-0.10 smoothstep(0.88,1.0)|narrows the last 12%");
        System.out.printf(Locale.ROOT,
                "verticalShape fade start|ANVIL|%.2f|density decreases above this height fraction%n",
                StormT160UpperExtentArm.ANVIL_FADE_START);
        System.out.println(
                "verticalShape fade end|ANVIL|1.00|density is exactly zero at the role top");
        System.out.printf(Locale.ROOT,
                "verticalShape fade start|TOWER|%.2f|density decreases above this height fraction%n",
                StormT160UpperExtentArm.TOWER_FADE_START);
        System.out.println("profileRadius lerp|TOWER|1.25 -> 0.60|tower narrows monotonically with height");
        System.out.println("minor widening|ANVIL|x1.56|anvil minor radius only");
        System.out.printf(Locale.ROOT,
                "maximumProfileRadius|ANVIL|%.2f|conservative bound feeding horizontalReachBlocks%n",
                StormT160UpperExtentArm.ANVIL_MAX_PROFILE_RADIUS);
        System.out.printf(Locale.ROOT,
                "roleTopY extension|ANVIL|+%.0f blocks|role envelope top above descriptor top%n",
                StormT160UpperExtentArm.ANVIL_TOP_EXTENSION);
        System.out.println("roleTopY extension|CORE|+32 blocks|role envelope top above descriptor top");
        System.out.println("roleBaseY extension|ANVIL|-12 blocks|role envelope base below descriptor base");
        System.out.println("roleBaseY extension|TOWER|-28 blocks|role envelope base below descriptor base");
        System.out.println("edgeWidthBlocks|ANVIL|max(0.12, softness*1.65)|envelope fade half-width");
        System.out.println("horizontalShape|all|1-smoothstep(1-edgeWidth,1,radial)|radial cutoff at radial=1");

        System.out.println("\nper-descriptor upper roles, exact current values:");
        System.out.println("role|member|centre|descriptorY|roleY|major|minor|density|maxProfileHalfWidth");
        for (StormLobeDescriptor lobe : lobes) {
            if (lobe.role() != StormLobeDescriptor.Role.TOWER
                    && lobe.role() != StormLobeDescriptor.Role.ANVIL) {
                continue;
            }
            double anvilWiden = lobe.role() == StormLobeDescriptor.Role.ANVIL ? 1.56D : 1.0D;
            double maxProfile = StormLobeEvaluator.maximumProfileRadius(lobe.role());
            System.out.printf(Locale.ROOT,
                    "%s|%d|(%.0f,%.0f)|%.0f..%.0f|%.0f..%.0f|%.0f|%.0f|%.4f|%.0f x %.0f%n",
                    lobe.role(), lobe.memberIndex(), lobe.centerX(), lobe.centerZ(),
                    lobe.baseY(), lobe.topY(),
                    StormLobeEvaluator.roleBaseY(lobe), StormLobeEvaluator.roleTopY(lobe),
                    lobe.majorRadius(), lobe.minorRadius(), lobe.density(),
                    lobe.majorRadius() * maxProfile, lobe.minorRadius() * anvilWiden * maxProfile);
        }

        System.out.println("\nprofileRadius(ANVIL) and verticalShape(ANVIL) by height fraction:");
        System.out.println("v|profileRadius|verticalShape|radiusStillGrowing");
        double previous = Double.NaN;
        for (double v = 0.40D; v <= 1.0001D; v += 0.05D) {
            double radius = StormLobeEvaluator.profileRadius(
                    StormLobeDescriptor.Role.ANVIL, Math.min(1.0D, v));
            double shape = StormLobeEvaluator.verticalShape(
                    StormLobeDescriptor.Role.ANVIL, Math.min(1.0D, v));
            String growing = Double.isNaN(previous) ? "-"
                    : (radius > previous + 1.0E-6D ? "yes" : "NO");
            System.out.printf(Locale.ROOT, "%.2f|%.4f|%.4f|%s%n",
                    Math.min(1.0D, v), radius, shape, growing);
            previous = radius;
        }
    }

    // -----------------------------------------------------------------
    // Step 2 - baseline cross-sections through the real density path
    // -----------------------------------------------------------------

    private record Geometry(double baseY, double topY) {
        double span() {
            return topY - baseY;
        }

        double yAt(double normalized) {
            return baseY + normalized * span();
        }
    }

    private static Geometry systemGeometry(List<StormLobeDescriptor> lobes) {
        double base = Double.POSITIVE_INFINITY;
        double top = Double.NEGATIVE_INFINITY;
        for (StormLobeDescriptor lobe : lobes) {
            base = Math.min(base, StormLobeEvaluator.roleBaseY(lobe));
            top = Math.max(top, StormLobeEvaluator.roleTopY(lobe));
        }
        return new Geometry(base, top);
    }

    /** One horizontal section measured on the final production density. */
    private record Section(
            double y, double normalized,
            double maxXRadius, double maxZRadius,
            double occupiedArea, double nonzeroRadius, double denseRadius) {
    }

    private static Section measureSection(
            StormFieldSampler sampler,
            List<StormLobeDescriptor> lobes,
            double y,
            double normalized,
            double centreX,
            double centreZ) {
        double maxX = 0.0D;
        double maxZ = 0.0D;
        double nonzero = 0.0D;
        double dense = 0.0D;
        long occupied = 0L;
        double[] fields = new double[2];
        for (double x = -SECTION_HALF_EXTENT; x <= SECTION_HALF_EXTENT; x += SECTION_STEP) {
            for (double z = -SECTION_HALF_EXTENT; z <= SECTION_HALF_EXTENT; z += SECTION_STEP) {
                double wx = centreX + x;
                double wz = centreZ + z;
                double coverage = sampler.coverageAt(lobes, wx, y, wz);
                if (coverage <= 0.0D) {
                    continue;
                }
                double strength = sampler.envelopeStrengthAt(lobes, wx, y, wz);
                sampler.noiseFieldsAt(wx, y, wz, fields);
                double density = sampler.densityFromFields(
                        coverage, strength, fields[0], fields[1]);
                if (density > NONZERO_ISO) {
                    nonzero = Math.max(nonzero, Math.hypot(x, z));
                }
                if (density >= OCCUPIED_ISO) {
                    occupied++;
                    maxX = Math.max(maxX, Math.abs(x));
                    maxZ = Math.max(maxZ, Math.abs(z));
                }
                if (density >= DENSE_ISO) {
                    dense = Math.max(dense, Math.hypot(x, z));
                }
            }
        }
        double cell = SECTION_STEP * SECTION_STEP;
        return new Section(y, normalized, maxX, maxZ, occupied * cell, nonzero, dense);
    }

    private static final double[] SAMPLE_HEIGHTS = {0.50D, 0.60D, 0.70D, 0.80D, 0.90D, 0.95D};

    private static List<Section> sectionSeries(
            StormFieldSampler sampler,
            List<StormLobeDescriptor> lobes,
            Geometry geometry,
            double centreX,
            double centreZ) {
        List<Section> out = new ArrayList<>();
        for (double normalized : SAMPLE_HEIGHTS) {
            out.add(measureSection(
                    sampler, lobes, geometry.yAt(normalized), normalized, centreX, centreZ));
        }
        return out;
    }

    private static void printSections(String label, List<Section> sections) {
        System.out.println(label);
        System.out.println("v|y|maxXRadius|maxZRadius|occupiedArea|nonzeroRadius|denseRadius|widening");
        double previous = Double.NaN;
        for (Section s : sections) {
            double width = Math.max(s.maxXRadius(), s.maxZRadius());
            String widening = Double.isNaN(previous) ? "-"
                    : (width > previous + 1.0E-6D ? "yes" : "NO");
            System.out.printf(Locale.ROOT, "%.2f|%.0f|%.0f|%.0f|%.0f|%.0f|%.0f|%s%n",
                    s.normalized(), s.y(), s.maxXRadius(), s.maxZRadius(),
                    s.occupiedArea(), s.nonzeroRadius(), s.denseRadius(), widening);
            previous = width;
        }
    }

    /** Highest Y carrying any production density, and the width there. */
    private record Support(double highestY, double widthAtTop, double maxWidth, double maxWidthY) {
    }

    private static Support scanSupport(
            StormFieldSampler sampler,
            List<StormLobeDescriptor> lobes,
            Geometry geometry,
            double centreX,
            double centreZ) {
        double highest = Double.NaN;
        double widthAtTop = 0.0D;
        double maxWidth = 0.0D;
        double maxWidthY = Double.NaN;
        for (double y = geometry.baseY(); y <= geometry.topY() + 200.0D; y += SUPPORT_STEP) {
            Section section = measureSection(sampler, lobes, y, 0.0D, centreX, centreZ);
            double width = Math.max(section.maxXRadius(), section.maxZRadius());
            if (section.nonzeroRadius() > 0.0D) {
                highest = y;
                widthAtTop = width;
            }
            if (width > maxWidth) {
                maxWidth = width;
                maxWidthY = y;
            }
        }
        return new Support(highest, widthAtTop, maxWidth, maxWidthY);
    }

    private static void step2Baseline(
            StormFieldSampler sampler, List<StormLobeDescriptor> lobes, Geometry geometry) {
        System.out.println("\n--- STEP 2: baseline cross-sections, production density ---");
        System.out.printf(Locale.ROOT,
                "system role envelope: y=%.0f..%.0f span=%.0f blocks%n",
                geometry.baseY(), geometry.topY(), geometry.span());

        double centreX = 0.0D;
        double centreZ = 0.0D;
        List<Section> sections = sectionSeries(sampler, lobes, geometry, centreX, centreZ);
        printSections("\nPRODUCTION sections (max radius from system axis, blocks):", sections);

        Support support = scanSupport(sampler, lobes, geometry, centreX, centreZ);
        double intended = intendedMaxHalfWidth(lobes);
        System.out.printf(Locale.ROOT,
                "%nhighest nonzero density y = %.0f (role envelope top %.0f, headroom %.0f)%n",
                support.highestY(), geometry.topY(), geometry.topY() - support.highestY());
        System.out.printf(Locale.ROOT,
                "maximum final-density half-width = %.0f blocks at y=%.0f%n",
                support.maxWidth(), support.maxWidthY());
        System.out.printf(Locale.ROOT,
                "descriptor maximum intended half-width = %.0f blocks%n", intended);
        System.out.printf(Locale.ROOT,
                "realised fraction of intended width = %.3f%n", support.maxWidth() / intended);
        double height = support.highestY() - geometry.baseY();
        System.out.printf(Locale.ROOT,
                "width/height ratio = %.3f (2 x %.0f / %.0f)%n",
                2.0D * support.maxWidth() / height, support.maxWidth(), height);

        boolean stillWidening = isWideningAtTermination(sections);
        System.out.printf(Locale.ROOT,
                "%nIS HORIZONTAL WIDTH STILL INCREASING WHEN FINAL DENSITY TERMINATES? %s%n",
                stillWidening ? "YES" : "NO");
    }

    private static boolean isWideningAtTermination(List<Section> sections) {
        double last = 0.0D;
        double previous = 0.0D;
        for (Section s : sections) {
            double width = Math.max(s.maxXRadius(), s.maxZRadius());
            if (width > 0.0D) {
                previous = last;
                last = width;
            }
        }
        return last > previous + 1.0E-6D;
    }

    /** Widest half-width the descriptors and role profiles could produce. */
    private static double intendedMaxHalfWidth(List<StormLobeDescriptor> lobes) {
        double widest = 0.0D;
        for (StormLobeDescriptor lobe : lobes) {
            double anvilWiden = lobe.role() == StormLobeDescriptor.Role.ANVIL ? 1.56D : 1.0D;
            double maxProfile = StormLobeEvaluator.maximumProfileRadius(lobe.role());
            double reach = Math.hypot(lobe.centerX(), lobe.centerZ())
                    + Math.max(lobe.majorRadius() * maxProfile,
                            lobe.minorRadius() * anvilWiden * maxProfile);
            widest = Math.max(widest, reach);
        }
        return widest;
    }

    // -----------------------------------------------------------------
    // Step 4 - same-fixture A/B against the relaxed arm
    // -----------------------------------------------------------------

    private static void step4RelaxedAb(
            StormFieldSampler sampler, List<StormLobeDescriptor> lobes, Geometry geometry) {
        System.out.println("\n--- STEP 4: production vs relaxed extents, same fixture ---");

        List<Section> production = sectionSeries(sampler, lobes, geometry, 0.0D, 0.0D);
        Support productionSupport = scanSupport(sampler, lobes, geometry, 0.0D, 0.0D);

        StormT160UpperExtentArm.setRelaxed(true);
        Geometry relaxedGeometry = systemGeometry(lobes);
        List<Section> relaxed = sectionSeries(sampler, lobes, relaxedGeometry, 0.0D, 0.0D);
        Support relaxedSupport = scanSupport(sampler, lobes, relaxedGeometry, 0.0D, 0.0D);
        StormT160UpperExtentArm.setRelaxed(false);

        printSections("\nPRODUCTION:", production);
        printSections("\nRELAXED EXTENTS:", relaxed);

        double productionHeight = productionSupport.highestY() - geometry.baseY();
        double relaxedHeight = relaxedSupport.highestY() - relaxedGeometry.baseY();
        System.out.println("\nmetric|production|relaxed|ratio");
        System.out.printf(Locale.ROOT, "highest nonzero y|%.0f|%.0f|%.3f%n",
                productionSupport.highestY(), relaxedSupport.highestY(),
                relaxedSupport.highestY() / productionSupport.highestY());
        System.out.printf(Locale.ROOT, "cloud height|%.0f|%.0f|%.3f%n",
                productionHeight, relaxedHeight, relaxedHeight / productionHeight);
        System.out.printf(Locale.ROOT, "max half-width|%.0f|%.0f|%.3f%n",
                productionSupport.maxWidth(), relaxedSupport.maxWidth(),
                relaxedSupport.maxWidth() / productionSupport.maxWidth());
        System.out.printf(Locale.ROOT, "width/height ratio|%.3f|%.3f|%.3f%n",
                2.0D * productionSupport.maxWidth() / productionHeight,
                2.0D * relaxedSupport.maxWidth() / relaxedHeight,
                (2.0D * relaxedSupport.maxWidth() / relaxedHeight)
                        / (2.0D * productionSupport.maxWidth() / productionHeight));

        double widthGain = relaxedSupport.maxWidth() / productionSupport.maxWidth();
        boolean relaxedWidens = isWideningAtTermination(relaxed);
        double topArea = relaxed.get(relaxed.size() - 1).occupiedArea();
        double midArea = relaxed.get(0).occupiedArea();
        System.out.printf(Locale.ROOT,
                "%nrelaxed still widening at termination: %s; topArea/midArea = %.3f%n",
                relaxedWidens ? "YES" : "NO", midArea <= 0.0D ? 0.0D : topArea / midArea);
        System.out.printf(Locale.ROOT, "CLASSIFICATION: %s%n", classify(widthGain, relaxedWidens));
    }

    private static String classify(double widthGain, boolean relaxedWidens) {
        if (widthGain > 10.0D) {
            return "D - relaxation produces unbounded/malformed density";
        }
        if (widthGain < 1.10D) {
            return "C - relaxation changes very little";
        }
        return relaxedWidens
                ? "A - the cloud continues outward and forms a substantially broader anvil"
                : "B - relaxation only produces a larger dome/ellipsoid";
    }

    // -----------------------------------------------------------------
    // Step 5 - where the horizontal spread is lost
    // -----------------------------------------------------------------

    private static void step5StageTrace(
            StormFieldSampler sampler, List<StormLobeDescriptor> lobes, Geometry geometry) {
        System.out.println("\n--- STEP 5: stage trace, first stage that loses width ---");
        System.out.println("v|y|envelopeRadius|bodyRadius|finalRadius|body/env|final/body");
        double[] fields = new double[2];
        for (double normalized : SAMPLE_HEIGHTS) {
            double y = geometry.yAt(normalized);
            double envelope = 0.0D;
            double body = 0.0D;
            double finalRadius = 0.0D;
            for (double x = -SECTION_HALF_EXTENT; x <= SECTION_HALF_EXTENT; x += SECTION_STEP) {
                for (double z = -SECTION_HALF_EXTENT; z <= SECTION_HALF_EXTENT; z += SECTION_STEP) {
                    double coverage = sampler.coverageAt(lobes, x, y, z);
                    if (coverage <= 0.0D) {
                        continue;
                    }
                    double radius = Math.hypot(x, z);
                    if (coverage >= OCCUPIED_ISO) {
                        envelope = Math.max(envelope, radius);
                    }
                    double strength = sampler.envelopeStrengthAt(lobes, x, y, z);
                    sampler.noiseFieldsAt(x, y, z, fields);
                    double bodyValue = StormDensityModel.stormBody(coverage, strength, fields[0]);
                    if (bodyValue >= OCCUPIED_ISO) {
                        body = Math.max(body, radius);
                    }
                    double density = sampler.densityFromFields(
                            coverage, strength, fields[0], fields[1]);
                    if (density >= OCCUPIED_ISO) {
                        finalRadius = Math.max(finalRadius, radius);
                    }
                }
            }
            System.out.printf(Locale.ROOT, "%.2f|%.0f|%.0f|%.0f|%.0f|%.3f|%.3f%n",
                    normalized, y, envelope, body, finalRadius,
                    envelope <= 0.0D ? 0.0D : body / envelope,
                    body <= 0.0D ? 0.0D : finalRadius / body);
        }
    }

    // -----------------------------------------------------------------
    // Step 6 - the upper TOWER -> ANVIL transition
    // -----------------------------------------------------------------

    private static void step6Transition(
            StormFieldSampler sampler, List<StormLobeDescriptor> lobes, Geometry geometry) {
        System.out.println("\n--- STEP 6: upper TOWER -> ANVIL transition ---");
        List<StormLobeDescriptor> tower = roleLobes(lobes, StormLobeDescriptor.Role.TOWER);
        List<StormLobeDescriptor> anvil = roleLobes(lobes, StormLobeDescriptor.Role.ANVIL);
        System.out.println("y|towerRadius|anvilRadius|unionRadius|dTower|dAnvil|dUnion");

        double previousTower = Double.NaN;
        double previousAnvil = Double.NaN;
        double previousUnion = Double.NaN;
        double step = 16.0D;
        for (double y = 380.0D; y <= geometry.topY() + 8.0D; y += step) {
            double towerRadius = roleRadius(sampler, tower, y);
            double anvilRadius = roleRadius(sampler, anvil, y);
            double unionRadius = roleRadius(sampler, lobes, y);
            System.out.printf(Locale.ROOT, "%.0f|%.0f|%.0f|%.0f|%s|%s|%s%n",
                    y, towerRadius, anvilRadius, unionRadius,
                    derivative(previousTower, towerRadius, step),
                    derivative(previousAnvil, anvilRadius, step),
                    derivative(previousUnion, unionRadius, step));
            previousTower = towerRadius;
            previousAnvil = anvilRadius;
            previousUnion = unionRadius;
        }
    }

    private static String derivative(double previous, double current, double step) {
        if (Double.isNaN(previous)) {
            return "-";
        }
        return String.format(Locale.ROOT, "%+.3f", (current - previous) / step);
    }

    private static double roleRadius(
            StormFieldSampler sampler, List<StormLobeDescriptor> lobes, double y) {
        if (lobes.isEmpty()) {
            return 0.0D;
        }
        double[] fields = new double[2];
        double radius = 0.0D;
        for (double x = -SECTION_HALF_EXTENT; x <= SECTION_HALF_EXTENT; x += SECTION_STEP) {
            for (double z = -SECTION_HALF_EXTENT; z <= SECTION_HALF_EXTENT; z += SECTION_STEP) {
                double coverage = sampler.coverageAt(lobes, x, y, z);
                if (coverage <= 0.0D) {
                    continue;
                }
                double strength = sampler.envelopeStrengthAt(lobes, x, y, z);
                sampler.noiseFieldsAt(x, y, z, fields);
                double density = sampler.densityFromFields(
                        coverage, strength, fields[0], fields[1]);
                if (density >= OCCUPIED_ISO) {
                    radius = Math.max(radius, Math.hypot(x, z));
                }
            }
        }
        return radius;
    }

    private static List<StormLobeDescriptor> roleLobes(
            List<StormLobeDescriptor> lobes, StormLobeDescriptor.Role role) {
        List<StormLobeDescriptor> out = new ArrayList<>();
        for (StormLobeDescriptor lobe : lobes) {
            if (lobe.role() == role) {
                out.add(lobe);
            }
        }
        return out;
    }

    // -----------------------------------------------------------------
    // Fixture - the measured ten-member severe system
    // -----------------------------------------------------------------

    private static List<StormLobeDescriptor> severeStorm() {
        UUID group = UUID.nameUUIDFromBytes("phase4s-storm".getBytes());
        return List.of(
                descriptor(group, 0, 10, StormLobeDescriptor.Role.BASE,
                        -28.0D, -6.0D, 224.0F, 300.0F, 172.0F, 138.0F, 0.7832F),
                descriptor(group, 1, 10, StormLobeDescriptor.Role.BASE,
                        26.0D, 10.0D, 226.0F, 296.0F, 158.0F, 126.0F, 0.8792F),
                descriptor(group, 2, 10, StormLobeDescriptor.Role.CORE,
                        -12.0D, 2.0D, 250.0F, 368.0F, 98.0F, 82.0F, 0.9485F),
                descriptor(group, 3, 10, StormLobeDescriptor.Role.CORE,
                        14.0D, -8.0D, 256.0F, 374.0F, 92.0F, 76.0F, 1.0000F),
                descriptor(group, 4, 10, StormLobeDescriptor.Role.TOWER,
                        -6.0D, 0.0D, 300.0F, 448.0F, 58.0F, 48.0F, 0.9700F),
                descriptor(group, 5, 10, StormLobeDescriptor.Role.TOWER,
                        16.0D, -6.0D, 308.0F, 456.0F, 54.0F, 44.0F, 0.9539F),
                descriptor(group, 6, 10, StormLobeDescriptor.Role.ANVIL,
                        10.0D, 0.0D, 396.0F, 504.0F, 206.0F, 82.0F, 0.8222F),
                descriptor(group, 7, 10, StormLobeDescriptor.Role.ANVIL,
                        44.0D, -8.0D, 404.0F, 508.0F, 184.0F, 74.0F, 0.7231F),
                descriptor(group, 8, 10, StormLobeDescriptor.Role.ANVIL,
                        -30.0D, 6.0D, 400.0F, 500.0F, 176.0F, 70.0F, 0.7851F),
                descriptor(group, 9, 10, StormLobeDescriptor.Role.ANVIL,
                        -58.0D, -12.0D, 390.0F, 498.0F, 168.0F, 68.0F, 0.7992F)
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
            float minorRadius,
            float density
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
                density,
                0.14F,
                0.37F,
                0.62F,
                0.78F,
                1.0F
        );
    }
}
