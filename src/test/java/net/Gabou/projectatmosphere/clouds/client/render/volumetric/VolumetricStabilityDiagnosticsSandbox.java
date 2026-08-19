package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import net.Gabou.projectatmosphere.clouds.client.render.CloudTextureUnitContract;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Standalone deterministic checks for the CPU stability/composite analyzer. */
public final class VolumetricStabilityDiagnosticsSandbox {
    private VolumetricStabilityDiagnosticsSandbox() {
    }

    public static void main(String[] args) {
        CloudTextureUnitContract.selfCheck();
        VolumetricCloudDebugConfig.selfCheck();
        VolumetricStabilityDiagnostics.selfCheck();
        PuffLobeSpatialIndex.selfCheck();
        validateLocalPrecipitationContract();
        validateHistoryInvalidationContract();
        validateIndependentNearbyPrecipitationOwnership();
        if (Boolean.getBoolean("phase4r.failFirst")) {
            runPhase4RFailFirst();
        } else {
            runPhase4RCorrected();
        }
    }

    private static void runPhase4RFailFirst() {
        List<RegressionResult> results = new ArrayList<>();
        capture(results, "T078 rain and rendered-body agreement",
                VolumetricStabilityDiagnosticsSandbox::validateRainBodyAgreement);
        capture(results, "T079 independent lifecycle generations",
                VolumetricStabilityDiagnosticsSandbox::validateIndependentLifecycleGenerations);
        capture(results, "T079 same-frame history invalidation",
                VolumetricStabilityDiagnosticsSandbox::validateSameFrameHistoryInvalidation);
        capture(results, "T079 shaftDensity maxPrecipitation argument",
                VolumetricStabilityDiagnosticsSandbox::validateShaftMaximumPrecipitationArgument);
        reportAndFail(results);
    }

    /** Runs the retained Phase 4R invariants as the corrected pass gate. */
    private static void runPhase4RCorrected() {
        runCorrected("T078 rain and rendered-body agreement",
                VolumetricStabilityDiagnosticsSandbox::validateRainBodyAgreement);
        runCorrected("T079 independent lifecycle generations",
                VolumetricStabilityDiagnosticsSandbox::validateIndependentLifecycleGenerations);
        runCorrected("T079 same-frame history invalidation",
                VolumetricStabilityDiagnosticsSandbox::validateSameFrameHistoryInvalidation);
        runCorrected("T079 shaftDensity maxPrecipitation argument",
                VolumetricStabilityDiagnosticsSandbox::validateShaftMaximumPrecipitationArgument);
    }

    private static void validateRainBodyAgreement() {
        UUID group = new UUID(0x53544F524D000000L, 780L);
        StormLobeDescriptor left = rainDescriptor(group, 0, -180.0D, 220.0F);
        StormLobeDescriptor right = rainDescriptor(group, 1, 180.0D, 250.0F);
        List<StormLobeDescriptor> lobes = List.of(left, right);
        double probeX = 0.0D;
        double probeY = 258.0D;
        double visibleBody = StormLobeEvaluator.coverageEnvelopeAt(lobes, probeX, probeY, 0.0D);
        double leftSupport = StormLobeEvaluator.densityAt(
                left, probeX, left.baseY() + (left.topY() - left.baseY()) * 0.22D, 0.0D
        );
        double rightSupport = StormLobeEvaluator.densityAt(
                right, probeX, right.baseY() + (right.topY() - right.baseY()) * 0.22D, 0.0D
        );
        double rainSupport = 1.0D - (1.0D - leftSupport) * (1.0D - rightSupport);
        require((visibleBody > 0.01D) == (rainSupport > 0.01D),
                "visible statistical envelope and BASE-lobe rain union disagree at the same column: body="
                        + format(visibleBody) + " rainSupport=" + format(rainSupport));

        String shader = readSource(
                "src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh"
        );
        String rainFunction = functionBlock(shader, "float directStormRainSupportAt");
        require(!rainFunction.contains("directStormLobeSample"),
                "rain evaluates a separate BASE-only union instead of the exact rendered storm union");
    }

    private static StormLobeDescriptor rainDescriptor(UUID group, int index, double x, float baseY) {
        return new StormLobeDescriptor(
                new UUID(group.getMostSignificantBits(), group.getLeastSignificantBits() + index + 1L),
                group, index, 2, 0, StormLobeDescriptor.Role.BASE,
                x, 0.0D, baseY, baseY + 90.0F, 48.0F, 42.0F,
                0.0F, 1.0F, 0.0F, 0.0F,
                0.85F, 0.22F, 0.37F, 0.5F, 0.9F, 1.0F
        );
    }

    private static void validateIndependentLifecycleGenerations() {
        try {
            Method method = VolumetricHistoryValidity.Key.class.getDeclaredMethod(
                    "nativeFrame",
                    long.class, long.class, long.class, long.class, long.class, long.class
            );
            VolumetricHistoryValidity.Key key = (VolumetricHistoryValidity.Key) method.invoke(
                    null, 11L, 12L, 13L, 14L, 15L, 16L
            );
            require(key.worldGeneration() == 11L
                            && key.dimensionGeneration() == 12L
                            && key.ownerGeneration() == 13L
                            && key.resourceGeneration() == 14L,
                    "native history key collapsed independent lifecycle generations");
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException(
                    "nativeFrame accepts one lifecycle generation and copies it into world, dimension, "
                            + "owner, and resource fields",
                    exception
            );
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("could not exercise independent history generations", exception);
        }
    }

    private static void validateSameFrameHistoryInvalidation() {
        String lifecycle = readSource(
                "src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/"
                        + "VolumetricCloudClientLifecycle.java"
        );
        String reload = functionBlock(lifecycle, "public static void onResourceReload");
        int deferred = reload.indexOf("runOnRenderThread");
        require(deferred >= 0, "resource reload no longer exposes a render-thread transition");
        String beforeDeferredReset = reload.substring(0, deferred);
        require(beforeDeferredReset.contains("VolumetricCloudRenderer.invalidateHistory")
                        || beforeDeferredReset.contains("invalidateBeforeNextComposite"),
                "resource reload defers history invalidation to the render-call queue, leaving a frame "
                        + "able to composite with the old key");
    }

    private static void validateShaftMaximumPrecipitationArgument() {
        try {
            VolumetricPrecipitationModel.class.getDeclaredMethod(
                    "shaftDensity",
                    double.class, double.class, double.class, double.class,
                    double.class, double.class, double.class
            );
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException(
                    "shaftDensity has no independent maxPrecipitation parameter and therefore passes "
                            + "localPrecipitation in both rainEligible positions",
                    exception
            );
        }
    }

    private static void capture(List<RegressionResult> results, String name, Regression regression) {
        try {
            regression.run();
            results.add(new RegressionResult(name, false, "invariant unexpectedly passed"));
        } catch (Throwable failure) {
            results.add(new RegressionResult(name, true,
                    failure.getClass().getSimpleName() + ": " + failure.getMessage()));
        }
    }

    private static void runCorrected(String name, Regression regression) {
        try {
            regression.run();
            System.out.println("PHASE4R_RESULT|" + name + "|PASSED|invariant satisfied");
        } catch (Exception exception) {
            throw new IllegalStateException("PHASE4R_RESULT|" + name + "|FAILED|"
                    + oneLine(exception.getMessage()), exception);
        }
    }

    private static void reportAndFail(List<RegressionResult> results) {
        int failures = 0;
        for (RegressionResult result : results) {
            failures += result.failed() ? 1 : 0;
            System.out.println("PHASE4R_RESULT|" + result.name() + "|"
                    + (result.failed() ? "FAILED" : "PASSED") + "|" + oneLine(result.reason()));
        }
        require(failures > 0, "Phase 4R stability regressions unexpectedly all passed");
        throw new IllegalStateException("Phase 4R stability fail-first captured "
                + failures + "/" + results.size() + " expected invariant failures");
    }

    private static String readSource(String relative) {
        try {
            return Files.readString(Path.of(System.getProperty("user.dir", ".")).resolve(relative))
                    .replace("\r\n", "\n");
        } catch (IOException exception) {
            throw new IllegalStateException("could not inspect " + relative, exception);
        }
    }

    private static String functionBlock(String source, String signature) {
        int start = source.indexOf(signature);
        require(start >= 0, "function missing: " + signature);
        int open = source.indexOf('{', start);
        require(open >= 0, "function body missing: " + signature);
        int depth = 0;
        for (int index = open; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(start, index + 1);
                }
            }
        }
        throw new IllegalStateException("unterminated function: " + signature);
    }

    private static String oneLine(String value) {
        return value == null ? "null"
                : value.replace("\n", " ").replace("||", "OR").replace("|", "OR").trim();
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    @FunctionalInterface
    private interface Regression {
        void run() throws Exception;
    }

    private record RegressionResult(String name, boolean failed, String reason) {
    }

    private static void validateLocalPrecipitationContract() {
        require(!VolumetricPrecipitationModel.rainEligible(0.9D, 0.0D, 0.0D),
                "global precipitation enabled a locally clear shaft");
        require(!VolumetricPrecipitationModel.rainEligible(0.9D, 0.8D, 0.0D),
                "local precipitation without visible support enabled a shaft");
        require(VolumetricPrecipitationModel.rainEligible(0.9D, 0.8D, 0.75D),
                "locally supported precipitation was rejected");

        double unsupported = VolumetricPrecipitationModel.shaftDensity(
                0.0D, 0.9D, 260.0D, 180.0D, 160.0D, 1.0D
        );
        double attached = VolumetricPrecipitationModel.shaftDensity(
                0.85D, 0.9D, 260.0D, 259.5D, 160.0D, 1.0D
        );
        require(unsupported == 0.0D, "unsupported shaft produced density");
        require(attached > 0.0D, "rain did not attach continuously below its local cloud base");

        VolumetricPrecipitationModel.SampleFunction linear = (x, y, z) -> x + y * 2.0D + z * 3.0D;
        double first = VolumetricPrecipitationModel.integrateCoarseSegment(
                10.0D, 20.0D, 30.0D, 26.0D, 28.0D, 38.0D, linear
        );
        double repeated = VolumetricPrecipitationModel.integrateCoarseSegment(
                10.0D, 20.0D, 30.0D, 26.0D, 28.0D, 38.0D, linear
        );
        requireNear("deterministic coarse rain integration", first, repeated, 0.0D);
        requireNear("linear coarse rain integration", first, 168.0D, 1.0E-9D);

        require(VolumetricPrecipitationModel.clearAirMaySkip(false, false, false, false),
                "clear air did not take the empty-space fast path");
        require(!VolumetricPrecipitationModel.clearAirMaySkip(false, true, false, false),
                "local rain support was skipped as clear air");
        require(!VolumetricPrecipitationModel.clearAirMaySkip(false, false, false, true),
                "direct storm support was skipped as clear air");
    }

    private static void validateHistoryInvalidationContract() {
        VolumetricHistoryValidity.Key stable = new VolumetricHistoryValidity.Key(
                1L, 2L, 3L, 4L, 5L, 6L
        );
        require(VolumetricHistoryValidity.canRetain(stable, stable),
                "unchanged/interpolated descriptor frame discarded history");
        requireInvalid(stable, new VolumetricHistoryValidity.Key(1L, 2L, 3L, 4L, 7L, 6L),
                "topology");
        requireInvalid(stable, new VolumetricHistoryValidity.Key(7L, 2L, 3L, 4L, 5L, 6L),
                "world");
        requireInvalid(stable, new VolumetricHistoryValidity.Key(1L, 7L, 3L, 4L, 5L, 6L),
                "dimension");
        requireInvalid(stable, new VolumetricHistoryValidity.Key(1L, 2L, 7L, 4L, 5L, 6L),
                "owner");
        requireInvalid(stable, new VolumetricHistoryValidity.Key(1L, 2L, 3L, 7L, 5L, 6L),
                "resource");
        requireInvalid(stable, new VolumetricHistoryValidity.Key(1L, 2L, 3L, 4L, 5L, 7L),
                "resolution");
    }

    private static void validateIndependentNearbyPrecipitationOwnership() {
        Path root = Path.of(System.getProperty("user.dir", "."));
        Path renderer = root.resolve("src/main/java/net/Gabou/projectatmosphere/clouds/client/render/CustomPrecipitationRenderer.java");
        Path hook = root.resolve("src/main/java/net/Gabou/projectatmosphere/mixin/client/MixinLevelRenderer.java");
        try {
            String rendererSource = Files.readString(renderer);
            String hookSource = Files.readString(hook);
            require(rendererSource.contains("return false;")
                            && rendererSource.contains("return true;"),
                    "nearby precipitation renderer no longer exposes custom/fallback ownership");
            require(hookSource.contains("if (CustomPrecipitationRenderer.renderSnowAndRain")
                            && hookSource.contains("ci.cancel();"),
                    "vanilla precipitation is no longer cancelled only after custom rendering succeeds");
        } catch (IOException exception) {
            throw new IllegalStateException("could not inspect precipitation ownership contract", exception);
        }
    }

    private static void requireInvalid(
            VolumetricHistoryValidity.Key previous,
            VolumetricHistoryValidity.Key current,
            String reason
    ) {
        require(!VolumetricHistoryValidity.canRetain(previous, current),
                reason + " change retained stale temporal history");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static void requireNear(String label, double actual, double expected, double tolerance) {
        if (!Double.isFinite(actual) || Math.abs(actual - expected) > tolerance) {
            throw new IllegalStateException(label + " expected=" + expected + " actual=" + actual);
        }
    }
}
