package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * T132 criterion-5 attribution.
 *
 * <p>The suite's two passes run the same binary, the same shader and the same
 * performance paths, so a PASS A/PASS B image difference is not by itself
 * evidence that an optimization moved the picture. The structural fingerprint
 * deliberately excludes advection state and the per-tick descriptor runtime
 * profile - "the source cells intentionally evolve radius, aspect, shear,
 * density and detail weight every tick" - so {@code structuralChanged=false}
 * means the topology is frozen, not that the storm is.
 *
 * <p>This class records what the fingerprint excludes so a failed image
 * comparison can be attributed. It tracks four evolving inputs:
 *
 * <ul>
 *   <li>the material advection offset;</li>
 *   <li>the per-descriptor runtime profile;</li>
 *   <li>{@code WorldTime}, which is <em>conditionally</em> render-relevant. The
 *       renderer computes
 *       {@code worldTimeAffectsDensity = weather.maxPrecipitation() > 0.02F || funnels > 0},
 *       and the shader consumes {@code WorldTime} in the precipitation shaft
 *       domain ({@code p.y * 0.0014 - WorldTime * 0.0015}) and the funnel terms.
 *       When it is irrelevant, a differing clock cannot move the image and must
 *       not be reported as instability;</li>
 *   <li>{@code LightDir}, which the renderer derives from the celestial phase.</li>
 * </ul>
 *
 * <p>It changes nothing about advection, descriptor evolution, the clock, the
 * lighting, or the fingerprint; it only observes them.
 *
 * <p>Pure and GL-free so the deterministic sandbox can exercise it headlessly.
 */
final class StormSceneStability {
    private StormSceneStability() {
    }

    /**
     * Per-descriptor runtime state, in published order. Identity is carried so
     * PASS A and PASS B compare descriptor-for-descriptor rather than by
     * position alone.
     */
    record DescriptorRuntime(
            String fieldId, int memberIndex, int role,
            float majorRadius, float minorRadius, float shearX, float shearZ,
            float density, float detailWeight, float lifecycle, float verticalDevelopment
    ) {
        /** Runtime-controlled horizontal aspect, reported because the role profiles scale it. */
        float aspect() {
            return majorRadius == 0.0F ? 0.0F : minorRadius / majorRadius;
        }

        String describe() {
            return String.format(Locale.ROOT,
                    "member=%d role=%d major=%.5f minor=%.5f aspect=%.5f shear=%.5f,%.5f "
                            + "density=%.5f detailWeight=%.5f lifecycle=%.5f verticalDevelopment=%.5f",
                    memberIndex, role, majorRadius, minorRadius, aspect(), shearX, shearZ,
                    density, detailWeight, lifecycle, verticalDevelopment);
        }
    }

    /** Ordered runtime profile of one capture plus its deterministic digest. */
    record Snapshot(String digest, List<DescriptorRuntime> descriptors) {
        static Snapshot of(List<DescriptorRuntime> descriptors) {
            List<DescriptorRuntime> copy = List.copyOf(descriptors);
            long hash = 0xcbf29ce484222325L;
            for (DescriptorRuntime descriptor : copy) {
                hash = mix(hash, descriptor.fieldId().hashCode());
                hash = mix(hash, descriptor.memberIndex());
                hash = mix(hash, descriptor.role());
                hash = mix(hash, Float.floatToIntBits(descriptor.majorRadius()));
                hash = mix(hash, Float.floatToIntBits(descriptor.minorRadius()));
                hash = mix(hash, Float.floatToIntBits(descriptor.shearX()));
                hash = mix(hash, Float.floatToIntBits(descriptor.shearZ()));
                hash = mix(hash, Float.floatToIntBits(descriptor.density()));
                hash = mix(hash, Float.floatToIntBits(descriptor.detailWeight()));
                hash = mix(hash, Float.floatToIntBits(descriptor.lifecycle()));
                hash = mix(hash, Float.floatToIntBits(descriptor.verticalDevelopment()));
            }
            hash = mix(hash, copy.size());
            return new Snapshot(String.format(Locale.ROOT, "%016x", hash), copy);
        }

        private static long mix(long hash, long value) {
            long result = hash ^ value;
            result *= 0x100000001b3L;
            return result;
        }
    }

    /**
     * The complete production uniform state of one reference frame, plus the
     * weather map's own input signature.
     *
     * <p>The named component hashes come straight from the renderer's existing
     * {@code UniformComponentSignatures}; they are not recomputed here. The
     * component list is read reflectively from that record, so a group added to
     * the renderer later is compared automatically instead of being silently
     * omitted by a hard-coded list.
     */
    record RenderInputs(
            long comparisonUniformSignature,
            VolumetricCloudRenderer.LastDrawInputs.UniformComponentSignatures components,
            long weatherMapInputSignature,
            String projectionStability,
            /** The topology strategy this frame was actually drawn with. */
            StormTopologyMode stormTopologyMode,
            /** The optimization mode this frame was actually drawn with. */
            StormOptimizationDiagnosticMode optimizationDiagnosticMode
    ) {
        String format() {
            return "renderInputs={comparisonUniformSignature="
                    + Long.toHexString(comparisonUniformSignature)
                    + " weatherMapInputSignature=" + Long.toHexString(weatherMapInputSignature)
                    + " namedUniformGroups=" + componentNames().size() + '}'
                    + (projectionStability == null ? "" : ' ' + projectionStability);
        }
    }

    /** Every named uniform group the renderer currently exposes. */
    static List<String> componentNames() {
        List<String> names = new ArrayList<>();
        for (RecordComponent component
                : VolumetricCloudRenderer.LastDrawInputs.UniformComponentSignatures
                        .class.getRecordComponents()) {
            names.add(component.getName());
        }
        return List.copyOf(names);
    }

    /**
     * Compares every named uniform group and the weather-map signature. Returns
     * the differing groups as {@code name=A->B}, so an attribution names the
     * input rather than reporting a bare hash mismatch.
     */
    static RenderInputComparison compareRenderInputs(RenderInputs a, RenderInputs b) {
        if (a == null || b == null || a.components() == null || b.components() == null) {
            return new RenderInputComparison(false, false, false, false,
                    0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, List.of(), "render_inputs_missing");
        }
        List<String> changed = new ArrayList<>();
        try {
            for (RecordComponent component
                    : VolumetricCloudRenderer.LastDrawInputs.UniformComponentSignatures
                            .class.getRecordComponents()) {
                Object first = component.getAccessor().invoke(a.components());
                Object second = component.getAccessor().invoke(b.components());
                if (!Objects.equals(first, second)) {
                    changed.add(component.getName() + '=' + first + "->" + second);
                }
            }
        } catch (ReflectiveOperationException exception) {
            return new RenderInputComparison(false, false, false, false,
                    a.comparisonUniformSignature(), b.comparisonUniformSignature(),
                    a.weatherMapInputSignature(), b.weatherMapInputSignature(),
                    0L, 0L, 0L, 0L, List.of(), "uniform_component_read_failed");
        }
        boolean weatherMatch = a.weatherMapInputSignature() == b.weatherMapInputSignature();
        long projectionA = a.components().projection();
        long projectionB = b.components().projection();
        long inverseProjectionA = a.components().inverseProjection();
        long inverseProjectionB = b.components().inverseProjection();
        boolean componentsMatch = changed.isEmpty();
        boolean signatureMatch = a.comparisonUniformSignature() == b.comparisonUniformSignature();
        return new RenderInputComparison(true, componentsMatch && weatherMatch,
                componentsMatch, weatherMatch,
                a.comparisonUniformSignature(), b.comparisonUniformSignature(),
                a.weatherMapInputSignature(), b.weatherMapInputSignature(),
                projectionA, projectionB, inverseProjectionA, inverseProjectionB,
                List.copyOf(changed), signatureMatch ? "" : "comparison_signature_differs");
    }

    record RenderInputComparison(
            boolean evaluated,
            boolean renderInputsMatch,
            boolean uniformComponentsMatch,
            boolean weatherMapInputSignatureMatch,
            long comparisonUniformSignatureA,
            long comparisonUniformSignatureB,
            long weatherMapInputSignatureA,
            long weatherMapInputSignatureB,
            long projectionSignatureA,
            long projectionSignatureB,
            long inverseProjectionSignatureA,
            long inverseProjectionSignatureB,
            List<String> changedUniformComponents,
            String note
    ) {
        boolean projectionMatch() {
            return projectionSignatureA == projectionSignatureB;
        }

        boolean inverseProjectionMatch() {
            return inverseProjectionSignatureA == inverseProjectionSignatureB;
        }

        boolean comparisonUniformSignatureMatch() {
            return comparisonUniformSignatureA == comparisonUniformSignatureB;
        }

        String format() {
            if (!evaluated) {
                return "renderInputs evaluated=false reason=" + note;
            }
            StringBuilder out = new StringBuilder("renderInputs evaluated=true renderInputsMatch=")
                    .append(renderInputsMatch)
                    .append(" comparisonUniformSignatureA=")
                    .append(Long.toHexString(comparisonUniformSignatureA))
                    .append(" comparisonUniformSignatureB=")
                    .append(Long.toHexString(comparisonUniformSignatureB))
                    .append(" comparisonUniformSignatureMatch=").append(comparisonUniformSignatureMatch())
                    .append(" uniformComponentsMatch=").append(uniformComponentsMatch)
                    .append(" changedUniformComponentCount=").append(changedUniformComponents.size())
                    .append(" comparedUniformGroups=").append(componentNames().size())
                    .append(" weatherMapInputSignatureA=").append(Long.toHexString(weatherMapInputSignatureA))
                    .append(" weatherMapInputSignatureB=").append(Long.toHexString(weatherMapInputSignatureB))
                    .append(" weatherMapInputSignatureMatch=").append(weatherMapInputSignatureMatch)
                    .append(" projectionSignatureA=").append(Long.toHexString(projectionSignatureA))
                    .append(" projectionSignatureB=").append(Long.toHexString(projectionSignatureB))
                    .append(" projectionMatch=").append(projectionMatch())
                    .append(" inverseProjectionSignatureA=")
                    .append(Long.toHexString(inverseProjectionSignatureA))
                    .append(" inverseProjectionSignatureB=")
                    .append(Long.toHexString(inverseProjectionSignatureB))
                    .append(" inverseProjectionMatch=").append(inverseProjectionMatch())
                    .append(" changedUniformComponents=")
                    .append(changedUniformComponents.isEmpty()
                            ? "none" : String.join(",", changedUniformComponents));
            return out.toString();
        }
    }

    /** The evolving, non-structural inputs of a single capture. */
    record AnimatedInputs(
            float materialOffsetX, float materialOffsetZ,
            /** The clock the reference frame actually rendered at. */
            float effectiveWorldTime,
            /** The live world clock at that moment, retained for auditability. */
            float liveWorldTime,
            boolean worldTimePinned,
            boolean worldTimeAffectsDensity,
            float lightDirX, float lightDirY, float lightDirZ,
            Snapshot runtimeProfile
    ) {
    }

    /**
     * Evaluates whether the scene itself held still between two captures.
     *
     * <p>{@code sceneStable} is true only when every tracked render-relevant
     * evolving input is bit-identical. {@code WorldTime} participates only when
     * the renderer marked it render-relevant for at least one of the passes.
     */
    static Result evaluate(AnimatedInputs a, AnimatedInputs b) {
        if (a == null || b == null || a.runtimeProfile() == null || b.runtimeProfile() == null) {
            return Result.unavailable("animated_inputs_missing");
        }
        boolean offsetMatch = same(a.materialOffsetX(), b.materialOffsetX())
                && same(a.materialOffsetZ(), b.materialOffsetZ());
        float offsetDeltaX = b.materialOffsetX() - a.materialOffsetX();
        float offsetDeltaZ = b.materialOffsetZ() - a.materialOffsetZ();

        // Stability is judged on the clock the reference frames actually
        // rendered at. While T132 pins that clock the live world clock keeps
        // advancing, and that live drift is reported but is not instability:
        // it cannot have moved a frame that never used it.
        boolean worldTimeMatch = same(a.effectiveWorldTime(), b.effectiveWorldTime());
        // Conservative: if either pass reported the clock render-relevant, a
        // difference can have moved the image.
        boolean worldTimeRelevant = a.worldTimeAffectsDensity() || b.worldTimeAffectsDensity();
        float worldTimeDelta = b.effectiveWorldTime() - a.effectiveWorldTime();
        float liveWorldTimeDelta = b.liveWorldTime() - a.liveWorldTime();
        boolean worldTimePinned = a.worldTimePinned() && b.worldTimePinned();

        boolean lightDirMatch = same(a.lightDirX(), b.lightDirX())
                && same(a.lightDirY(), b.lightDirY())
                && same(a.lightDirZ(), b.lightDirZ());
        double lightDirDelta = Math.sqrt(
                sq(b.lightDirX() - a.lightDirX())
                        + sq(b.lightDirY() - a.lightDirY())
                        + sq(b.lightDirZ() - a.lightDirZ()));

        List<DescriptorRuntime> first = a.runtimeProfile().descriptors();
        List<DescriptorRuntime> second = b.runtimeProfile().descriptors();
        List<String> changed = new ArrayList<>();
        double maxMajorRadius = 0.0D;
        double maxMinorRadius = 0.0D;
        double maxAspect = 0.0D;
        double maxShear = 0.0D;
        double maxDensity = 0.0D;
        double maxDetailWeight = 0.0D;
        double maxLifecycle = 0.0D;
        double maxVerticalDevelopment = 0.0D;
        int changedDescriptors = 0;

        if (first.size() != second.size()) {
            changed.add("descriptorCount " + first.size() + "->" + second.size());
            changedDescriptors = Math.max(first.size(), second.size());
        }
        int shared = Math.min(first.size(), second.size());
        for (int index = 0; index < shared; index++) {
            DescriptorRuntime left = first.get(index);
            DescriptorRuntime right = second.get(index);
            if (!left.fieldId().equals(right.fieldId())
                    || left.memberIndex() != right.memberIndex()
                    || left.role() != right.role()) {
                changed.add("identity[" + index + "] " + left.describe() + " -> " + right.describe());
                changedDescriptors++;
                continue;
            }
            maxMajorRadius = Math.max(maxMajorRadius, Math.abs(right.majorRadius() - left.majorRadius()));
            maxMinorRadius = Math.max(maxMinorRadius, Math.abs(right.minorRadius() - left.minorRadius()));
            maxAspect = Math.max(maxAspect, Math.abs(right.aspect() - left.aspect()));
            maxShear = Math.max(maxShear, Math.max(
                    Math.abs(right.shearX() - left.shearX()),
                    Math.abs(right.shearZ() - left.shearZ())));
            maxDensity = Math.max(maxDensity, Math.abs(right.density() - left.density()));
            maxDetailWeight = Math.max(maxDetailWeight,
                    Math.abs(right.detailWeight() - left.detailWeight()));
            maxLifecycle = Math.max(maxLifecycle, Math.abs(right.lifecycle() - left.lifecycle()));
            maxVerticalDevelopment = Math.max(maxVerticalDevelopment,
                    Math.abs(right.verticalDevelopment() - left.verticalDevelopment()));
            if (!sameRuntime(left, right)) {
                changedDescriptors++;
                changed.add("descriptor[" + index + "] " + left.describe() + " -> " + right.describe());
            }
        }

        boolean runtimeMatch = changedDescriptors == 0 && changed.isEmpty();
        List<String> unstable = new ArrayList<>();
        if (!offsetMatch) {
            unstable.add("materialOffset");
        }
        if (worldTimeRelevant && !worldTimeMatch) {
            unstable.add("worldTime");
        }
        if (!lightDirMatch) {
            unstable.add("lightDir");
        }
        if (!runtimeMatch) {
            unstable.add("runtimeProfile");
        }

        return new Result(true, unstable.isEmpty(),
                offsetMatch, offsetDeltaX, offsetDeltaZ,
                a.effectiveWorldTime(), b.effectiveWorldTime(), worldTimeDelta,
                a.liveWorldTime(), b.liveWorldTime(), liveWorldTimeDelta, worldTimePinned,
                a.worldTimeAffectsDensity(), b.worldTimeAffectsDensity(),
                worldTimeRelevant, worldTimeMatch,
                a.lightDirX(), a.lightDirY(), a.lightDirZ(),
                b.lightDirX(), b.lightDirY(), b.lightDirZ(),
                lightDirDelta, lightDirMatch,
                runtimeMatch, a.runtimeProfile().digest(), b.runtimeProfile().digest(),
                changedDescriptors, shared,
                maxMajorRadius, maxMinorRadius, maxAspect, maxShear, maxDensity,
                maxDetailWeight, maxLifecycle, maxVerticalDevelopment,
                List.copyOf(changed), List.copyOf(unstable), "");
    }

    private static boolean same(float first, float second) {
        return Float.floatToIntBits(first) == Float.floatToIntBits(second);
    }

    private static double sq(double value) {
        return value * value;
    }

    private static boolean sameRuntime(DescriptorRuntime first, DescriptorRuntime second) {
        return same(first.majorRadius(), second.majorRadius())
                && same(first.minorRadius(), second.minorRadius())
                && same(first.shearX(), second.shearX())
                && same(first.shearZ(), second.shearZ())
                && same(first.density(), second.density())
                && same(first.detailWeight(), second.detailWeight())
                && same(first.lifecycle(), second.lifecycle())
                && same(first.verticalDevelopment(), second.verticalDevelopment());
    }

    /**
     * Criterion-5 verdict for one view.
     *
     * <p>A failed image comparison is only chargeable to the performance path
     * when every tracked render-relevant input provably held still. Otherwise it
     * is recorded as unattributable, naming the inputs that moved, because the
     * same code produced both passes.
     */
    static String attribution(
            boolean imageEvaluated, boolean imagePassed, Result stability, RenderInputComparison render,
            StormCloudContent.Comparison cloudContent) {
        if (!imageEvaluated) {
            return "imageNeutralityPassed=false criterion5Attributable=false"
                    + " reason=image_comparison_unavailable";
        }
        if (imagePassed) {
            // A passing comparison is a pass regardless of any diagnostic
            // difference: the rendered result is what criterion 5 measures.
            return "imageNeutralityPassed=true criterion5Attributable=true reason=none";
        }
        if (!stability.evaluated()) {
            return "imageNeutralityPassed=false criterion5Attributable=false"
                    + " reason=" + stability.unavailableReason();
        }
        // Level A - a tracked scene input evolved.
        if (!stability.sceneStable()) {
            return "imageNeutralityPassed=false criterion5Attributable=false"
                    + " reason=scene_evolved_between_passes"
                    + " differingInputs=" + String.join(",", stability.unstableInputs());
        }
        if (render == null || !render.evaluated()) {
            return "imageNeutralityPassed=false criterion5Attributable=false"
                    + " reason=render_inputs_unavailable";
        }
        // Level B - the scene held still but another production render input moved.
        if (!render.renderInputsMatch()) {
            List<String> differing = new ArrayList<>(render.changedUniformComponents());
            if (!render.weatherMapInputSignatureMatch()) {
                differing.add("weatherMapInputSignature="
                        + Long.toHexString(render.weatherMapInputSignatureA()) + "->"
                        + Long.toHexString(render.weatherMapInputSignatureB()));
            }
            return "imageNeutralityPassed=false criterion5Attributable=false"
                    + " reason=render_inputs_differ_between_passes"
                    + " differingInputs=" + String.join(",", differing);
        }
        if (cloudContent == null || !cloudContent.evaluated()) {
            return "imageNeutralityPassed=false criterion5Attributable=false"
                    + " reason=cloud_content_unavailable";
        }
        // Level C - the fixture freezes one storm group. Any other cloud in the
        // frame - another storm group, or the PUFF family, which advects on its
        // own tick - renders through the same shader into the same buffer.
        if (!cloudContent.cloudContentMatch()) {
            return "imageNeutralityPassed=false criterion5Attributable=false"
                    + " reason=cloud_content_changed_between_passes"
                    + " differingInputs=" + String.join(",", cloudContent.differingCategories());
        }
        // Level D - every known deterministic render input and the whole-frame
        // cloud content match. This is not a proven renderer defect and not
        // proven GPU nondeterminism; it means the known inputs are exhausted
        // and a deeper investigation is due.
        return "imageNeutralityPassed=false criterion5Attributable=true"
                + " reason=unexplained_deterministic_render_difference";
    }

    record Result(
            boolean evaluated,
            boolean sceneStable,
            boolean materialOffsetMatch,
            float materialOffsetDeltaX,
            float materialOffsetDeltaZ,
            float worldTimeA,
            float worldTimeB,
            float worldTimeDelta,
            float liveWorldTimeA,
            float liveWorldTimeB,
            float liveWorldTimeDelta,
            boolean worldTimePinned,
            boolean worldTimeAffectsDensityA,
            boolean worldTimeAffectsDensityB,
            boolean worldTimeRelevant,
            boolean worldTimeMatch,
            float lightDirAX,
            float lightDirAY,
            float lightDirAZ,
            float lightDirBX,
            float lightDirBY,
            float lightDirBZ,
            double lightDirDelta,
            boolean lightDirMatch,
            boolean runtimeProfileMatch,
            String runtimeProfileDigestA,
            String runtimeProfileDigestB,
            int changedDescriptorCount,
            int comparedDescriptorCount,
            double maxMajorRadiusDelta,
            double maxMinorRadiusDelta,
            double maxAspectDelta,
            double maxShearDelta,
            double maxDensityDelta,
            double maxDetailWeightDelta,
            double maxLifecycleDelta,
            double maxVerticalDevelopmentDelta,
            List<String> changedDescriptors,
            List<String> unstableInputs,
            String unavailableReason
    ) {
        static Result unavailable(String reason) {
            return new Result(false, false, false, 0.0F, 0.0F,
                    0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, false,
                    false, false, false, false,
                    0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0D, false,
                    false, "", "", 0, 0,
                    0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D,
                    List.of(), List.of(), reason);
        }

        String format() {
            if (!evaluated) {
                return "sceneStability evaluated=false reason=" + unavailableReason;
            }
            StringBuilder out = new StringBuilder("sceneStability evaluated=true sceneStable=")
                    .append(sceneStable)
                    .append(" unstableInputs=")
                    .append(unstableInputs.isEmpty() ? "none" : String.join(",", unstableInputs))
                    .append(" materialOffsetMatch=").append(materialOffsetMatch)
                    .append(" materialOffsetDeltaX=").append(sci(materialOffsetDeltaX))
                    .append(" materialOffsetDeltaZ=").append(sci(materialOffsetDeltaZ))
                    .append(" worldTimePinned=").append(worldTimePinned)
                    .append(" effectiveWorldTimeA=").append(ticks(worldTimeA))
                    .append(" effectiveWorldTimeB=").append(ticks(worldTimeB))
                    .append(" effectiveWorldTimeMatch=").append(worldTimeMatch)
                    .append(" effectiveWorldTimeDelta=").append(ticks(worldTimeDelta))
                    .append(" liveWorldTimeA=").append(ticks(liveWorldTimeA))
                    .append(" liveWorldTimeB=").append(ticks(liveWorldTimeB))
                    .append(" liveWorldTimeDelta=").append(ticks(liveWorldTimeDelta))
                    .append(" worldTimeAffectsDensityA=").append(worldTimeAffectsDensityA)
                    .append(" worldTimeAffectsDensityB=").append(worldTimeAffectsDensityB)
                    .append(" worldTimeRelevant=").append(worldTimeRelevant)
                    .append(" worldTimeMatch=").append(worldTimeMatch)
                    .append(" lightDirA=").append(vec(lightDirAX, lightDirAY, lightDirAZ))
                    .append(" lightDirB=").append(vec(lightDirBX, lightDirBY, lightDirBZ))
                    .append(" lightDirDelta=").append(sci(lightDirDelta))
                    .append(" lightDirMatch=").append(lightDirMatch)
                    .append(" runtimeProfileMatch=").append(runtimeProfileMatch)
                    .append(" runtimeProfileDigestA=").append(runtimeProfileDigestA)
                    .append(" runtimeProfileDigestB=").append(runtimeProfileDigestB)
                    .append(" changedDescriptorCount=").append(changedDescriptorCount)
                    .append('/').append(comparedDescriptorCount)
                    .append(" maxMajorRadiusDelta=").append(sci(maxMajorRadiusDelta))
                    .append(" maxMinorRadiusDelta=").append(sci(maxMinorRadiusDelta))
                    .append(" maxAspectDelta=").append(sci(maxAspectDelta))
                    .append(" maxShearDelta=").append(sci(maxShearDelta))
                    .append(" maxDensityDelta=").append(sci(maxDensityDelta))
                    .append(" maxDetailWeightDelta=").append(sci(maxDetailWeightDelta))
                    .append(" maxLifecycleDelta=").append(sci(maxLifecycleDelta))
                    .append(" maxVerticalDevelopmentDelta=").append(sci(maxVerticalDevelopmentDelta));
            for (String entry : changedDescriptors) {
                out.append("\n    changed ").append(entry);
            }
            return out.toString();
        }

        private static String sci(double value) {
            return String.format(Locale.ROOT, "%.6e", value);
        }

        private static String ticks(float value) {
            return String.format(Locale.ROOT, "%.5f", value);
        }

        private static String vec(float x, float y, float z) {
            return String.format(Locale.ROOT, "(%.6f,%.6f,%.6f)", x, y, z);
        }
    }
}
