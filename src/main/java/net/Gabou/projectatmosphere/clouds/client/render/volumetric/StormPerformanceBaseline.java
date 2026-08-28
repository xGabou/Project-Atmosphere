package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import com.mojang.blaze3d.pipeline.RenderTarget;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * T130's on-demand, read-only capture coordinator. It records an exact
 * published descriptor group, deterministic group-relative camera fixtures,
 * fresh GPU timestamp samples, and the existing fence-gated image digest.
 * It never changes renderer uniforms, quality settings, camera state, or
 * descriptor topology.
 */
final class StormPerformanceBaseline {
    private static final int GPU_SAMPLES = 8;
    private static final double CAMERA_TOLERANCE_BLOCKS = 4.0D;

    private static Fixture fixture;
    private static PendingCapture active;
    private static volatile String latest = "not_started";

    private StormPerformanceBaseline() {
    }

    static synchronized String begin(double x, double y, double z) {
        if (active != null) {
            return "busy:view=" + active.viewpoint.serializedName();
        }
        StormMaterialRuntimeTrace.Resolution resolution = StormMaterialRuntimeTrace.resolve(x, z);
        if (!resolution.valid()) {
            latest = "no_complete_published_storm_group";
            return latest;
        }
        fixture = Fixture.from(resolution, StormGeometryBuildCoordinator.snapshot());
        latest = fixture.formatInstructions();
        return latest;
    }

    static synchronized String capture(
            String requestedViewpoint, double x, double y, double z, float yaw, float pitch
    ) {
        if (active != null) {
            return "busy:view=" + active.viewpoint.serializedName();
        }
        if (fixture == null) {
            return "begin_required";
        }
        Viewpoint viewpoint = Viewpoint.parse(requestedViewpoint);
        if (viewpoint == null) {
            return "invalid_viewpoint expected=SIDE|FAR|BELOW|ABOVE";
        }
        FixtureValidation validation = fixture.validatePublishedStructure();
        if (validation.structuralChanged()) {
            fixture = null;
            latest = validation.formatInvalidation();
            return latest;
        }
        CameraPose expected = fixture.pose(viewpoint);
        double error = expected.distanceTo(x, y, z);
        if (error > CAMERA_TOLERANCE_BLOCKS) {
            latest = "move_to_fixed_camera view=" + viewpoint.serializedName()
                    + " expected=" + expected.formatPosition()
                    + " positionError=" + fmt(error)
                    + " tolerance=" + fmt(CAMERA_TOLERANCE_BLOCKS);
            return latest;
        }
        String visualCapture = VolumetricStabilityDiagnostics.requestCapture(GPU_SAMPLES);
        if (!visualCapture.startsWith("requested")) {
            latest = "visual_reference_unavailable:" + visualCapture;
            return latest;
        }
        active = new PendingCapture(viewpoint, expected, x, y, z, yaw, pitch,
                fixture.distanceToStorm(expected), validation.captureGeneration(),
                validation.captureFingerprint(), VolumetricCloudRenderer.lastGpuTimingSample());
        latest = "acquiring view=" + viewpoint.serializedName() + " gpuSamples=0/" + GPU_SAMPLES
                + " visual=" + visualCapture + ' ' + validation.format();
        return latest;
    }

    static synchronized void observe(RenderTarget cloudTarget) {
        PendingCapture capture = active;
        if (capture == null || cloudTarget == null) {
            return;
        }
        VolumetricCloudRenderer.LastDrawInputs inputs = VolumetricCloudRenderer.lastDrawInputs();
        if (!inputs.valid() || inputs.debugView() != VolumetricCloudRaymarchDebugView.FINAL) {
            fail("non_production_draw");
            return;
        }
        if (fixture == null) {
            fail("fixture_missing");
            return;
        }
        FixtureValidation validation = fixture.validatePublishedStructure();
        if (validation.structuralChanged()) {
            fail(validation.formatInvalidation());
            return;
        }
        long timingSample = VolumetricCloudRenderer.lastGpuTimingSample();
        float gpuMs = VolumetricCloudRenderer.lastGpuMilliseconds();
        if (timingSample > capture.lastTimingSample && gpuMs >= 0.0F) {
            capture.lastTimingSample = timingSample;
            capture.gpuMilliseconds.add(gpuMs);
            capture.captureInputs(inputs, cloudTarget.width, cloudTarget.height);
        }

        String visualStatus = VolumetricStabilityDiagnostics.status();
        if (capture.gpuMilliseconds.size() >= GPU_SAMPLES && visualStatus.startsWith("ready:")) {
            CaptureResult result = capture.finish(VolumetricStabilityDiagnostics.formattedLatest(), validation);
            fixture.results.put(capture.viewpoint, result);
            active = null;
            latest = fixture.formatStatus();
            return;
        }
        latest = "acquiring view=" + capture.viewpoint.serializedName()
                + " gpuSamples=" + capture.gpuMilliseconds.size() + "/" + GPU_SAMPLES
                + " visual=" + visualStatus;
    }

    static String latest() {
        return latest;
    }

    static double cameraToleranceBlocks() {
        return CAMERA_TOLERANCE_BLOCKS;
    }

    /**
     * Read-only bridge for the automated T121--T123 suite.  The suite uses
     * the exact fixture created by {@link #begin(double, double, double)};
     * it does not independently resolve a nearby storm or manufacture poses.
     */
    static synchronized SuiteFixture suiteFixture() {
        if (fixture == null) {
            return null;
        }
        List<SuitePose> poses = new ArrayList<>(Viewpoint.values().length);
        for (Viewpoint viewpoint : Viewpoint.values()) {
            CameraPose pose = fixture.pose(viewpoint);
            poses.add(new SuitePose(viewpoint.serializedName(), pose.x(), pose.y(), pose.z()));
        }
        return new SuiteFixture(
                fixture.groupId.toString(), fixture.fingerprintAtBegin.value(),
                fixture.centerX, fixture.centerZ, (fixture.baseY + fixture.topY) * 0.5D,
                fixture.baseY, fixture.topY, fixture.horizontalRadius, fixture.descriptorCount,
                fixture.fingerprintAtBegin.describe(),
                List.copyOf(poses)
        );
    }

    static synchronized String suiteFixtureValidation() {
        if (fixture == null) {
            return "fixture_missing";
        }
        FixtureValidation validation = fixture.validatePublishedStructure();
        return validation.structuralChanged() ? validation.formatInvalidation() : "fixture_valid " + validation.format();
    }

    static synchronized boolean suiteCaptureComplete(String viewpoint) {
        Viewpoint parsed = Viewpoint.parse(viewpoint);
        return fixture != null && parsed != null && fixture.results.containsKey(parsed);
    }

    static synchronized String suiteCaptureResult(String viewpoint) {
        Viewpoint parsed = Viewpoint.parse(viewpoint);
        if (fixture == null || parsed == null) {
            return "missing";
        }
        CaptureResult result = fixture.results.get(parsed);
        return result == null ? "pending" : result.format();
    }

    /** Immutable baseline sample retained by the two-pass performance suite. */
    static synchronized CaptureResult suiteCapture(String viewpoint) {
        Viewpoint parsed = Viewpoint.parse(viewpoint);
        return fixture == null || parsed == null ? null : fixture.results.get(parsed);
    }

    static synchronized boolean suiteCaptureActive() {
        return active != null;
    }

    record SuiteFixture(
            String groupId, String structuralFingerprint, double centerX, double centerZ, double centerY,
            double baseY, double topY, double horizontalRadius, int descriptorCount,
            String descriptorStructure,
            List<SuitePose> poses
    ) {
        SuitePose pose(String view) {
            for (SuitePose pose : poses) {
                if (pose.view().equalsIgnoreCase(view)) {
                    return pose;
                }
            }
            return null;
        }
    }

    record SuitePose(String view, double x, double y, double z) {
    }

    /** Deterministic guard for the T130 identity contract; no GL or renderer state. */
    static void selfCheckStructuralFingerprint() {
        UUID group = new UUID(0x130L, 0x1L);
        StormLobeDescriptor first = new StormLobeDescriptor(
                new UUID(0x130L, 0x2L), group, 0, 1, 0, StormLobeDescriptor.Role.BASE,
                10.0D, 20.0D, 220.0F, 300.0F, 100.0F, 80.0F,
                0.0F, 1.0F, 0.0F, 0.0F, 0.82F, 0.35F, 0.25F, 0.6F, 0.8F, 1.0F
        );
        StormRenderSnapshot generationSix = new StormRenderSnapshot(
                1L, 6L, 10L, 0.0D, 0.0D, 1000.0F, new StormLobeDescriptor[]{first});
        StormRenderSnapshot generationSeven = new StormRenderSnapshot(
                1L, 7L, 11L, 16.0D, 0.0D, 1000.0F, new StormLobeDescriptor[]{first});
        StructuralFingerprint baseline = StructuralFingerprint.from(group, generationSix);
        StructuralFingerprint republished = StructuralFingerprint.from(group, generationSeven);
        if (!baseline.differences(republished).isEmpty()) {
            throw new IllegalStateException("T130 fingerprint treated a candidate-grid republish as a structural change");
        }
        StormLobeDescriptor lifecycleEvolved = new StormLobeDescriptor(
                first.fieldId(), group, 0, 1, 0, StormLobeDescriptor.Role.BASE,
                10.0D, 20.0D, 220.0F, 300.0F, 112.0F, 89.6F,
                0.0F, 1.0F, 11.2F, 0.0F, 0.83F, 0.35F, 0.25F, 0.7F, 0.85F, 1.0F
        );
        StructuralFingerprint evolved = StructuralFingerprint.from(group,
                new StormRenderSnapshot(1L, 8L, 12L, 32.0D, 0.0D, 1000.0F,
                        new StormLobeDescriptor[]{lifecycleEvolved}));
        if (!baseline.differences(evolved).isEmpty() || baseline.runtimeProfileDifferences(evolved).isEmpty()) {
            throw new IllegalStateException("T130 fingerprint treated lifecycle profile drift as a topology change");
        }
        StormLobeDescriptor densityProfileChanged = new StormLobeDescriptor(
                first.fieldId(), group, 0, 1, 0, StormLobeDescriptor.Role.BASE,
                10.0D, 20.0D, 220.0F, 300.0F, 180.0F, 54.0F,
                0.0F, 1.0F, 37.0F, -19.0F, 0.30F, 0.35F, 0.25F, 0.6F, 0.8F, 0.7F
        );
        StructuralFingerprint densityProfileEvolved = StructuralFingerprint.from(group,
                new StormRenderSnapshot(1L, 8L, 12L, 32.0D, 0.0D, 1000.0F,
                        new StormLobeDescriptor[]{densityProfileChanged}));
        if (!baseline.differences(densityProfileEvolved).isEmpty()
                || baseline.runtimeProfileDifferences(densityProfileEvolved).isEmpty()) {
            throw new IllegalStateException("T130 fingerprint treated density/profile evolution as a topology change");
        }
        StormLobeDescriptor relocated = new StormLobeDescriptor(
                first.fieldId(), group, 0, 1, 0, StormLobeDescriptor.Role.BASE,
                14.0D, 20.0D, 220.0F, 300.0F, 100.0F, 80.0F,
                0.0F, 1.0F, 0.0F, 0.0F, 0.82F, 0.35F, 0.25F, 0.6F, 0.8F, 1.0F
        );
        if (baseline.differences(StructuralFingerprint.from(group,
                new StormRenderSnapshot(1L, 9L, 13L, 32.0D, 0.0D, 1000.0F,
                        new StormLobeDescriptor[]{relocated}))).stream()
                .noneMatch(difference -> difference.contains("centerX"))) {
            throw new IllegalStateException("T130 fingerprint missed a regenerated descriptor position");
        }
        StormLobeDescriptor reassignedRole = new StormLobeDescriptor(
                first.fieldId(), group, 0, 1, 0, StormLobeDescriptor.Role.CORE,
                10.0D, 20.0D, 220.0F, 300.0F, 100.0F, 80.0F,
                0.0F, 1.0F, 0.0F, 0.0F, 0.82F, 0.35F, 0.25F, 0.6F, 0.8F, 1.0F
        );
        if (baseline.differences(StructuralFingerprint.from(group,
                new StormRenderSnapshot(1L, 10L, 14L, 32.0D, 0.0D, 1000.0F,
                        new StormLobeDescriptor[]{reassignedRole}))).stream()
                .noneMatch(difference -> difference.contains("role"))) {
            throw new IllegalStateException("T130 fingerprint missed a descriptor role reassignment");
        }
    }

    private static void fail(String reason) {
        active = null;
        latest = "capture_failed:" + reason;
    }

    private enum Viewpoint {
        SIDE, FAR, BELOW, ABOVE,
        // SC-018's three reference viewing distances (T127 "Required live
        // views"): absolute block distances from the resolved group centre,
        // not radius multiples like SIDE/FAR/BELOW.
        DISTANCE600, DISTANCE900, DISTANCE1200;

        /** Absolute lateral distance in blocks, or 0 for the radius-derived views. */
        double sc018Distance() {
            return switch (this) {
                case DISTANCE600 -> 600.0D;
                case DISTANCE900 -> 900.0D;
                case DISTANCE1200 -> 1200.0D;
                default -> 0.0D;
            };
        }

        private static Viewpoint parse(String value) {
            if (value == null) {
                return null;
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        private String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final class Fixture {
        private final UUID groupId;
        private final long generationAtBegin;
        private final StructuralFingerprint fingerprintAtBegin;
        private final double centerX;
        private final double centerZ;
        private final float baseY;
        private final float topY;
        private final double horizontalRadius;
        private final int descriptorCount;
        private final EnumMap<Viewpoint, CameraPose> poses;
        private final EnumMap<Viewpoint, CaptureResult> results = new EnumMap<>(Viewpoint.class);

        private Fixture(UUID groupId, long generationAtBegin, StructuralFingerprint fingerprintAtBegin,
                        double centerX, double centerZ,
                        float baseY, float topY, double horizontalRadius, int descriptorCount,
                        EnumMap<Viewpoint, CameraPose> poses) {
            this.groupId = groupId;
            this.generationAtBegin = generationAtBegin;
            this.fingerprintAtBegin = fingerprintAtBegin;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.baseY = baseY;
            this.topY = topY;
            this.horizontalRadius = horizontalRadius;
            this.descriptorCount = descriptorCount;
            this.poses = poses;
        }

        private static Fixture from(StormMaterialRuntimeTrace.Resolution resolution, StormRenderSnapshot snapshot) {
            float baseY = Float.POSITIVE_INFINITY;
            float topY = Float.NEGATIVE_INFINITY;
            double radius = 1.0D;
            int count = 0;
            for (StormLobeDescriptor descriptor : snapshot.descriptorsUnsafe()) {
                if (!resolution.groupId().equals(descriptor.groupId())) {
                    continue;
                }
                baseY = Math.min(baseY, descriptor.baseY());
                topY = Math.max(topY, descriptor.topY());
                radius = Math.max(radius, Math.hypot(
                        descriptor.centerX() - resolution.centerX(),
                        descriptor.centerZ() - resolution.centerZ()
                ) + Math.max(descriptor.majorRadius(), descriptor.minorRadius()));
                count++;
            }
            if (!Float.isFinite(baseY) || !Float.isFinite(topY)) {
                baseY = 0.0F;
                topY = 1.0F;
            }
            double height = Math.max(1.0D, topY - baseY);
            double midY = (baseY + topY) * 0.5D;
            double sideOffset = radius * 1.35D;
            EnumMap<Viewpoint, CameraPose> poses = new EnumMap<>(Viewpoint.class);
            poses.put(Viewpoint.SIDE, new CameraPose(resolution.centerX() + sideOffset, midY, resolution.centerZ()));
            poses.put(Viewpoint.FAR, new CameraPose(resolution.centerX() + radius * 3.0D, midY, resolution.centerZ()));
            poses.put(Viewpoint.BELOW, new CameraPose(resolution.centerX() + radius * 0.8D,
                    baseY - Math.max(96.0D, height * 0.45D), resolution.centerZ()));
            poses.put(Viewpoint.ABOVE, new CameraPose(resolution.centerX(),
                    topY + Math.max(96.0D, height * 0.45D), resolution.centerZ()));
            // SC-018: eye level at the system mid-height, so the whole column
            // subtends the frame at each documented distance.
            for (Viewpoint viewpoint : Viewpoint.values()) {
                double distance = viewpoint.sc018Distance();
                if (distance > 0.0D) {
                    poses.put(viewpoint, new CameraPose(
                            resolution.centerX() + distance, midY, resolution.centerZ()));
                }
            }
            StructuralFingerprint fingerprint = StructuralFingerprint.from(resolution.groupId(), snapshot);
            return new Fixture(resolution.groupId(), snapshot.topologyGeneration(), fingerprint, resolution.centerX(),
                    resolution.centerZ(), baseY, topY, radius, count, poses);
        }

        private CameraPose pose(Viewpoint viewpoint) {
            return poses.get(viewpoint);
        }

        private double distanceToStorm(CameraPose pose) {
            return pose.distanceTo(centerX, (baseY + topY) * 0.5D, centerZ);
        }

        private FixtureValidation validatePublishedStructure() {
            StormRenderSnapshot snapshot = StormGeometryBuildCoordinator.snapshot();
            StructuralFingerprint captureFingerprint = StructuralFingerprint.from(groupId, snapshot);
            List<String> differences = fingerprintAtBegin.differences(captureFingerprint);
            List<String> runtimeProfileChanges = fingerprintAtBegin.runtimeProfileDifferences(captureFingerprint);
            return new FixtureValidation(generationAtBegin, snapshot.topologyGeneration(),
                    fingerprintAtBegin, captureFingerprint, !differences.isEmpty(), differences, runtimeProfileChanges);
        }

        private String formatInstructions() {
            StringBuilder out = new StringBuilder("T130 frozen baseline fixture")
                    .append("\ngroup=").append(groupId)
                    .append(" generationAtBegin=").append(generationAtBegin)
                    .append(" structuralFingerprintAtBegin=").append(fingerprintAtBegin.value())
                    .append(" centre=").append(fmt(centerX)).append(',').append(fmt(centerZ))
                    .append(" baseTop=").append(fmt(baseY)).append("..").append(fmt(topY))
                    .append(" horizontalRadius=").append(fmt(horizontalRadius))
                    .append(" descriptors=").append(descriptorCount)
                    .append("\nMove to each fixed position, face the group centre, then run ")
                    .append("/pa system volumetric diagnostics stormPerformanceBaseline capture <view>.");
            for (Viewpoint viewpoint : Viewpoint.values()) {
                out.append("\n").append(viewpoint.serializedName()).append('=').append(pose(viewpoint).formatPosition())
                        .append(" distance=").append(fmt(pose(viewpoint).distanceTo(centerX, (baseY + topY) * 0.5D, centerZ)));
            }
            return out.toString();
        }

        private String formatStatus() {
            StringBuilder out = new StringBuilder("T130 frozen baseline")
                    .append("\ngroup=").append(groupId)
                    .append(" generationAtBegin=").append(generationAtBegin)
                    .append(" structuralFingerprintAtBegin=").append(fingerprintAtBegin.value())
                    .append(" centre=").append(fmt(centerX)).append(',').append(fmt(centerZ));
            for (Viewpoint viewpoint : Viewpoint.values()) {
                CaptureResult result = results.get(viewpoint);
                out.append("\n").append(viewpoint.serializedName()).append('=')
                        .append(result == null ? "pending fixed=" + pose(viewpoint).formatPosition() : result.format());
            }
            out.append("\nmeasurable=gpuMs(min/mean/max from fresh timestamp queries), targetPixels, configuredRaySteps, "
                    + "configuredLightSteps, history, resolution/governor-equivalent StepScale, compact topology metadata reads")
                    .append("\nrequires_instrumentation=actual primary ray steps, descriptor evaluations, descriptor texture fetches, "
                            + "light-march density evaluations, empty-space rejects, early terminations")
                    .append("\ncurrentTopology=compact metadata range (0 group-boundary scan iterations; 3 metadata reads/group evaluation); "
                            + "legacy-vs-compact timing comparison requires the separate T119 A/B path and is not accepted by T130.");
            return out.toString();
        }
    }

    /**
     * Ordered, non-volatile identity of the selected rendered storm.  The
     * source cells intentionally evolve radius, aspect, shear, density and
     * detail weight every tick; those fields are retained as runtime-profile
     * diagnostics, but do not constitute a topology regeneration. The stable
     * descriptor identity, ordered membership, role, centre, vertical bounds,
     * orientation, edge profile and seed still make a regenerated storm fail
     * the fixture check. Candidate-grid origin, request generation, uploads,
     * advection offsets, frame time and history are likewise excluded because
     * they can republish the same storm.
     */
    private record StructuralFingerprint(String value, List<DescriptorState> descriptors) {
        private static StructuralFingerprint from(UUID groupId, StormRenderSnapshot snapshot) {
            long hash = 0xcbf29ce484222325L;
            hash = mix(hash, groupId == null ? 0L : groupId.getMostSignificantBits());
            hash = mix(hash, groupId == null ? 0L : groupId.getLeastSignificantBits());
            List<DescriptorState> states = new ArrayList<>();
            if (snapshot != null) {
                for (StormLobeDescriptor descriptor : snapshot.descriptorsUnsafe()) {
                    if (groupId != null && groupId.equals(descriptor.groupId())) {
                        DescriptorState state = DescriptorState.from(descriptor);
                        states.add(state);
                        hash = state.mixInto(hash);
                    }
                }
            }
            hash = mix(hash, states.size());
            return new StructuralFingerprint(String.format(Locale.ROOT, "%016x", hash), List.copyOf(states));
        }

        private List<String> differences(StructuralFingerprint current) {
            if (current == null) {
                return List.of("published_group_missing");
            }
            List<String> differences = new ArrayList<>();
            if (descriptors.size() != current.descriptors.size()) {
                differences.add("descriptorCount " + descriptors.size() + "->" + current.descriptors.size());
            }
            int shared = Math.min(descriptors.size(), current.descriptors.size());
            for (int index = 0; index < shared; index++) {
                DescriptorState before = descriptors.get(index);
                DescriptorState after = current.descriptors.get(index);
                before.appendDifferences(after, index, differences);
            }
            if (differences.isEmpty() && !value.equals(current.value)) {
                // This is a guard against a future field omission. The hash
                // must never silently override an empty structural diff.
                differences.add("fingerprint_hash_mismatch");
            }
            return List.copyOf(differences);
        }

        private List<String> runtimeProfileDifferences(StructuralFingerprint current) {
            if (current == null) {
                return List.of("published_group_missing");
            }
            List<String> differences = new ArrayList<>();
            int shared = Math.min(descriptors.size(), current.descriptors.size());
            for (int index = 0; index < shared; index++) {
                descriptors.get(index).appendRuntimeProfileDifferences(current.descriptors.get(index), index, differences);
            }
            return List.copyOf(differences);
        }

        /** Ordered runtime profile of this capture, for T132 attribution only. */
        private StormSceneStability.Snapshot runtimeSnapshot() {
            List<StormSceneStability.DescriptorRuntime> runtime = new ArrayList<>(descriptors.size());
            for (DescriptorState descriptor : descriptors) {
                runtime.add(descriptor.runtimeView());
            }
            return StormSceneStability.Snapshot.of(runtime);
        }

        private String describe() {
            StringBuilder out = new StringBuilder("count=").append(descriptors.size());
            for (int index = 0; index < descriptors.size(); index++) {
                out.append(" descriptor[").append(index).append("]{")
                        .append(descriptors.get(index).describe()).append('}');
            }
            return out.toString();
        }
    }

    private record DescriptorState(
            UUID fieldId, int memberIndex, int memberCount, int groupSlot, int role,
            long centerX, long centerZ, int baseY, int topY,
            int sinOrientation, int cosOrientation, int edgeSoftness, int seed,
            RuntimeProfile runtimeProfile
    ) {
        private static DescriptorState from(StormLobeDescriptor descriptor) {
            float majorRadius = descriptor.majorRadius();
            float minorRadius = descriptor.minorRadius();
            float weightedDensity = descriptor.density() * descriptor.detailWeight();
            return new DescriptorState(
                    descriptor.fieldId(), descriptor.memberIndex(), descriptor.memberCount(),
                    descriptor.groupSlot(), descriptor.role().gpuId(),
                    Double.doubleToLongBits(descriptor.centerX()), Double.doubleToLongBits(descriptor.centerZ()),
                    Float.floatToIntBits(descriptor.baseY()), Float.floatToIntBits(descriptor.topY()),
                    Float.floatToIntBits(descriptor.sinOrientation()), Float.floatToIntBits(descriptor.cosOrientation()),
                    Float.floatToIntBits(descriptor.edgeSoftness()), Float.floatToIntBits(descriptor.seed01()),
                    new RuntimeProfile(
                            Float.floatToIntBits(majorRadius), Float.floatToIntBits(minorRadius),
                            Float.floatToIntBits(descriptor.shearX()), Float.floatToIntBits(descriptor.shearZ()),
                            Float.floatToIntBits(descriptor.density()), Float.floatToIntBits(descriptor.detailWeight()),
                            Float.floatToIntBits(weightedDensity), Float.floatToIntBits(descriptor.lifecycleStage()),
                            Float.floatToIntBits(descriptor.verticalDevelopment())
                    )
            );
        }

        /**
      * Projects the excluded runtime fields for T132 attribution. This does
      * not participate in the structural fingerprint and does not change it.
      */
        private StormSceneStability.DescriptorRuntime runtimeView() {
            return new StormSceneStability.DescriptorRuntime(
                    fieldId.toString(), memberIndex, role,
                    Float.intBitsToFloat(runtimeProfile.majorRadius()),
                    Float.intBitsToFloat(runtimeProfile.minorRadius()),
                    Float.intBitsToFloat(runtimeProfile.shearX()),
                    Float.intBitsToFloat(runtimeProfile.shearZ()),
                    Float.intBitsToFloat(runtimeProfile.density()),
                    Float.intBitsToFloat(runtimeProfile.detailWeight()),
                    Float.intBitsToFloat(runtimeProfile.lifecycle()),
                    Float.intBitsToFloat(runtimeProfile.verticalDevelopment()));
        }

        private long mixInto(long hash) {
            hash = mix(hash, fieldId.getMostSignificantBits());
            hash = mix(hash, fieldId.getLeastSignificantBits());
            hash = mix(hash, memberIndex);
            hash = mix(hash, memberCount);
            // groupSlot is assigned by camera-distance ordering in
            // StormLobeSpatialIndex.build(), so it moves when the camera moves
            // even though the storm has not. The suite teleports between poses
            // by design, so including it made the fixture invalidate itself. It
            // is a per-frame render-ordering artifact, not storm identity.
            hash = mix(hash, role);
            hash = mix(hash, centerX);
            hash = mix(hash, centerZ);
            hash = mix(hash, baseY);
            hash = mix(hash, topY);
            hash = mix(hash, sinOrientation);
            hash = mix(hash, cosOrientation);
            hash = mix(hash, edgeSoftness);
            hash = mix(hash, seed);
            return hash;
        }

        private void appendDifferences(DescriptorState current, int index, List<String> differences) {
            String prefix = "descriptor[" + index + "] ";
            if (!fieldId.equals(current.fieldId) || memberIndex != current.memberIndex || memberCount != current.memberCount) {
                differences.add(prefix + "membership/order");
                return;
            }
            append(differences, prefix, "role", role, current.role);
            append(differences, prefix, "centerX", centerX, current.centerX);
            append(differences, prefix, "centerZ", centerZ, current.centerZ);
            append(differences, prefix, "baseY", baseY, current.baseY);
            append(differences, prefix, "topY", topY, current.topY);
            append(differences, prefix, "sinOrientation", sinOrientation, current.sinOrientation);
            append(differences, prefix, "cosOrientation", cosOrientation, current.cosOrientation);
            append(differences, prefix, "edgeSoftness", edgeSoftness, current.edgeSoftness);
            append(differences, prefix, "seed", seed, current.seed);
        }

        private void appendRuntimeProfileDifferences(DescriptorState current, int index, List<String> differences) {
            if (!fieldId.equals(current.fieldId) || memberIndex != current.memberIndex || memberCount != current.memberCount) {
                return;
            }
            String prefix = "runtime descriptor[" + index + "] ";
            runtimeProfile.appendDifferences(current.runtimeProfile, prefix, differences);
        }

        private String describe() {
            return "field=" + fieldId
                    + ",member=" + memberIndex + '/' + memberCount
                    + ",slot=" + groupSlot
                    + ",role=" + role
                    + ",centreBits=" + Long.toUnsignedString(centerX) + '/' + Long.toUnsignedString(centerZ)
                    + ",baseTopBits=" + Integer.toUnsignedString(baseY) + '/' + Integer.toUnsignedString(topY)
                    + ",orientationBits=" + Integer.toUnsignedString(sinOrientation) + '/' + Integer.toUnsignedString(cosOrientation)
                    + ",edge=" + Integer.toUnsignedString(edgeSoftness)
                    + ",seed=" + Integer.toUnsignedString(seed)
                    + runtimeProfile.describe();
        }

        private static void append(List<String> out, String prefix, String field, long before, long after) {
            if (before != after) {
                out.add(prefix + field + " " + Long.toUnsignedString(before) + "->" + Long.toUnsignedString(after));
            }
        }
    }

    /** Runtime values deliberately observed, but not used to invalidate topology identity. */
    private record RuntimeProfile(
            int majorRadius, int minorRadius, int shearX, int shearZ,
            int density, int detailWeight, int densityDetailWeight,
            int lifecycle, int verticalDevelopment
    ) {
        private void appendDifferences(RuntimeProfile current, String prefix, List<String> differences) {
            DescriptorState.append(differences, prefix, "majorRadius", majorRadius, current.majorRadius);
            DescriptorState.append(differences, prefix, "minorRadius", minorRadius, current.minorRadius);
            DescriptorState.append(differences, prefix, "shearX", shearX, current.shearX);
            DescriptorState.append(differences, prefix, "shearZ", shearZ, current.shearZ);
            DescriptorState.append(differences, prefix, "density", density, current.density);
            DescriptorState.append(differences, prefix, "detailWeight", detailWeight, current.detailWeight);
            DescriptorState.append(differences, prefix, "densityDetailWeight", densityDetailWeight, current.densityDetailWeight);
            DescriptorState.append(differences, prefix, "lifecycle", lifecycle, current.lifecycle);
            DescriptorState.append(differences, prefix, "verticalDevelopment", verticalDevelopment, current.verticalDevelopment);
        }

        private String describe() {
            return ",radiiBits=" + Integer.toUnsignedString(majorRadius) + '/' + Integer.toUnsignedString(minorRadius)
                    + ",shearBits=" + Integer.toUnsignedString(shearX) + '/' + Integer.toUnsignedString(shearZ)
                    + ",density=" + Integer.toUnsignedString(density)
                    + ",detailWeight=" + Integer.toUnsignedString(detailWeight)
                    + ",densityDetailWeight=" + Integer.toUnsignedString(densityDetailWeight)
                    + ",lifecycle=" + Integer.toUnsignedString(lifecycle)
                    + ",verticalDevelopment=" + Integer.toUnsignedString(verticalDevelopment);
        }
    }

    private record FixtureValidation(
            long generationAtBegin, long captureGeneration,
            StructuralFingerprint beginFingerprint, StructuralFingerprint captureFingerprint,
            boolean structuralChanged, List<String> changedFields, List<String> runtimeProfileChanges
    ) {
        private String format() {
            return "generationAtBegin=" + generationAtBegin
                    + " generationAtCapture=" + captureGeneration
                    + " structuralFingerprintAtBegin=" + beginFingerprint.value()
                    + " structuralFingerprintAtCapture=" + captureFingerprint.value()
                    + " structuralChanged=" + structuralChanged
                    + " runtimeProfileChanged=" + !runtimeProfileChanges.isEmpty();
        }

        private String formatInvalidation() {
            return "fixture_invalidated_by_structural_change " + format()
                    + " changedFields=" + String.join(",", changedFields);
        }
    }

    private static final class PendingCapture {
        private final Viewpoint viewpoint;
        private final CameraPose expected;
        private final double cameraX;
        private final double cameraY;
        private final double cameraZ;
        private final float yaw;
        private final float pitch;
        private final double stormDistance;
        private final long generationAtCapture;
        private final StructuralFingerprint fingerprintAtCapture;
        private final List<Float> gpuMilliseconds = new ArrayList<>(GPU_SAMPLES);
        private long lastTimingSample;
        private VolumetricCloudRenderer.LastDrawInputs inputs;
        private int targetWidth;
        private int targetHeight;

        private PendingCapture(Viewpoint viewpoint, CameraPose expected, double cameraX, double cameraY,
                               double cameraZ, float yaw, float pitch, double stormDistance,
                               long generationAtCapture, StructuralFingerprint fingerprintAtCapture,
                               long lastTimingSample) {
            this.viewpoint = viewpoint;
            this.expected = expected;
            this.cameraX = cameraX;
            this.cameraY = cameraY;
            this.cameraZ = cameraZ;
            this.yaw = yaw;
            this.pitch = pitch;
            this.stormDistance = stormDistance;
            this.generationAtCapture = generationAtCapture;
            this.fingerprintAtCapture = fingerprintAtCapture;
            this.lastTimingSample = lastTimingSample;
        }

        private void captureInputs(VolumetricCloudRenderer.LastDrawInputs candidate, int width, int height) {
            // Keep the final sampled production state: the first timestamp
            // result can belong to the pre-settlement history frame after a
            // camera teleport, whereas the final one is the frozen view.
            inputs = candidate;
            targetWidth = width;
            targetHeight = height;
        }

        private CaptureResult finish(String visualReport, FixtureValidation finalValidation) {
            float min = Float.POSITIVE_INFINITY;
            float max = Float.NEGATIVE_INFINITY;
            float sum = 0.0F;
            for (float sample : gpuMilliseconds) {
                min = Math.min(min, sample);
                max = Math.max(max, sample);
                sum += sample;
            }
            List<Float> ordered = new ArrayList<>(gpuMilliseconds);
            java.util.Collections.sort(ordered);
            float median = ordered.size() % 2 == 1
                    ? ordered.get(ordered.size() / 2)
                    : (ordered.get(ordered.size() / 2 - 1) + ordered.get(ordered.size() / 2)) * 0.5F;
            return new CaptureResult(viewpoint, expected, cameraX, cameraY, cameraZ, yaw, pitch,
                    stormDistance, min, sum / gpuMilliseconds.size(), max, median,
                    gpuMilliseconds.size(),
                    targetWidth, targetHeight, inputs, VolumetricCloudRenderer.lastResolutionScale(),
                    generationAtCapture, finalValidation.captureGeneration(),
                    fingerprintAtCapture.value(), finalValidation.captureFingerprint().value(),
                    finalValidation.structuralChanged(), sha256(visualReport),
                    finalValidation.captureFingerprint().runtimeSnapshot());
        }
    }

    record CaptureResult(
            Viewpoint viewpoint, CameraPose expected, double cameraX, double cameraY, double cameraZ,
            float yaw, float pitch, double stormDistance,
            float gpuMin, float gpuMean, float gpuMax, float gpuMedian, int gpuSamples,
            int targetWidth, int targetHeight, VolumetricCloudRenderer.LastDrawInputs inputs,
            float resolutionScale,
            long generationAtCapture, long generationAtComplete,
            String fingerprintAtCapture, String fingerprintAtComplete, boolean structuralChanged,
            String visualReferenceHash,
            StormSceneStability.Snapshot runtimeProfile
    ) {
        String format() {
            int pixels = targetWidth * targetHeight;
            return "camera=" + fmt(cameraX) + ',' + fmt(cameraY) + ',' + fmt(cameraZ)
                    + " yawPitch=" + fmt(yaw) + ',' + fmt(pitch)
                    + " stormDistance=" + fmt(stormDistance)
                    + " gpuMs=" + fmt(gpuMin) + '/' + fmt(gpuMean) + '/' + fmt(gpuMax)
                    + " gpuMedianMs=" + fmt(gpuMedian)
                    + " samples=" + gpuSamples
                    + " target=" + targetWidth + 'x' + targetHeight + " pixels=" + pixels
                    + " rayStepsConfigured=" + inputs.raymarchSteps()
                    + " lightStepsConfigured=" + inputs.lightSteps()
                    + " governorScale=" + fmt(inputs.stepScale())
                    + " history=" + inputs.historyValid() + '/' + fmt(inputs.historyBlend())
                    + " resolutionScale=" + fmt(resolutionScale)
                    + " generationAtCapture=" + generationAtCapture
                    + " generationAtComplete=" + generationAtComplete
                    + " structuralFingerprintAtCapture=" + fingerprintAtCapture
                    + " structuralFingerprintAtComplete=" + fingerprintAtComplete
                    + " structuralChanged=" + structuralChanged
                    + " topology=" + inputs.stormTopologyMode().serializedName()
                    + " scans=" + (inputs.stormTopologyMode() == StormTopologyMode.COMPACT ? 0 : "legacy_two_64_slot_scans")
                    + " metadataReadsPerGroup="
                    + (inputs.stormTopologyMode() == StormTopologyMode.COMPACT ? 3 : "scan_dependent")
                    + " visualRef=" + visualReferenceHash;
        }
    }

    private record CameraPose(double x, double y, double z) {
        private double distanceTo(double otherX, double otherY, double otherZ) {
            return Math.sqrt((x - otherX) * (x - otherX)
                    + (y - otherY) * (y - otherY) + (z - otherZ) * (z - otherZ));
        }

        private String formatPosition() {
            return fmt(x) + ',' + fmt(y) + ',' + fmt(z);
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(16);
            for (int index = 0; index < 8; index++) {
                out.append(String.format(Locale.ROOT, "%02x", digest[index]));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException exception) {
            return "sha256_unavailable";
        }
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.5f", value);
    }

    private static long mix(long hash, long value) {
        return (hash ^ value) * 0x100000001b3L;
    }
}
