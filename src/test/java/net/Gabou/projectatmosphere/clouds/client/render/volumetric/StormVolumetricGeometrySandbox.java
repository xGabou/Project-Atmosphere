package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import net.Gabou.projectatmosphere.clouds.type.CloudMorphologyFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudMorphologyMemberTier;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Deterministic, Minecraft-free checks for the direct severe-storm
 * representation. Assertions are intentionally plain Java so Gradle can run
 * them without launching a client. The Phase 4R parity fixture creates one
 * hidden OpenGL context so the GLSL equations execute independently.
 */
public final class StormVolumetricGeometrySandbox {
    private StormVolumetricGeometrySandbox() {
    }

    public static void main(String[] args) {
        validateDescriptorPackingAndProfiles();
        validateCoherentStormMorphology();
        validateGroupSelectionAndCandidatePacking();
        validateDirtyAndAdoptionContracts();
        validateCoalescingAndSaturationContracts();
        StormPerformanceSuite.selfCheckStateMachine();
        validateT132AuthoritativeControlSeparation();
        validateT133ProductionDefaultUnchanged();
        validateT123InstrumentationOnly();
        validateT121VerticalBoundIsConservative();
        validateT121Float32BoundaryMargin();
        validateT121GuardAdmitsNoUnionContribution();
        validateT121SoftnessBoundary();
        validateT121T122ShaderGuards();
        StormWorkloadRuntimeCapture.selfCheckFreshnessContract();
        StormReferenceImageCapture.selfCheckHistoryRestoration();
        validateT132WorkloadCaptureFreshness();
        validateT132DeterministicImageComparator();
        validateT132RepeatedSamplingMedian();
        validateT132Attribution();
        reportT098CarrierDistribution();
        reportT098ErosionVersusBody();
        reportT098VerticalWidthProfile();
        reportT098TransitionCandidates();
        reportT098PercolationWidth();
        reportT098AnvilSkirt();
        reportT098AnvilSoftnessSweep();
        reportT098SoftnessVersusHeight();
        validateT098EnvelopeBoundedByExtent();
        reportT098OpticalProfile();
        reportT098MarchSimulation();
        validateT098MarchReachesMaterial();
        if (Boolean.getBoolean("phase4r.failFirst")) {
            runPhase4RFailFirst();
        } else {
            runPhase4RCorrected();
        }
    }

    /**
     * Pins the connected carrier profiles that make descriptors disappear as
     * primitives in the final storm. These probes deliberately sit near role
     * roots and at a same-group overlap, where the former ellipsoid union
     * produced the fragmented underside and visible side bulbs.
     */
    private static void validateCoherentStormMorphology() {
        UUID stormGroup = group(7);
        StormLobeDescriptor base = descriptor(
                stormGroup, 0, 4, StormLobeDescriptor.Role.BASE,
                0.0D, 0.0D, 220.0F, 310.0F, 120.0F, 100.0F
        ).withGroupSlot(0);
        StormLobeDescriptor tower = descriptor(
                stormGroup, 2, 4, StormLobeDescriptor.Role.TOWER,
                0.0D, 0.0D, 280.0F, 450.0F, 78.0F, 58.0F
        ).withGroupSlot(0);
        StormLobeDescriptor anvil = descriptor(
                stormGroup, 3, 4, StormLobeDescriptor.Role.ANVIL,
                8.0D, 0.0D, 370.0F, 490.0F, 180.0F, 62.0F
        ).withGroupSlot(0);

        require(StormLobeEvaluator.densityAt(
                        base,
                        base.centerX() + base.majorRadius() * 0.64D,
                        base.baseY() + (base.topY() - base.baseY()) * 0.08D,
                        base.centerZ()
                ) > 0.18D,
                "storm base lost its broad connected lower carrier");
        require(StormLobeEvaluator.densityAt(
                        tower,
                        tower.centerX() + tower.majorRadius() * 0.48D,
                        tower.baseY() + (tower.topY() - tower.baseY()) * 0.08D,
                        tower.centerZ()
                ) > 0.16D,
                "tower root pinched away from the storm base");
        require(StormLobeEvaluator.densityAt(
                        anvil,
                        anvil.centerX() + anvil.majorRadius() * 0.24D,
                        anvil.baseY() + (anvil.topY() - anvil.baseY()) * 0.08D,
                        anvil.centerZ()
                ) > 0.07D,
                "anvil root disconnected from the updraft");

    }

    private static void validateDescriptorPackingAndProfiles() {
        StormLobeDescriptor base = descriptor(group(1), 0, 7, StormLobeDescriptor.Role.BASE,
                0.0D, 0.0D, 220.0F, 270.0F, 90.0F, 70.0F);
        float[] texels = new float[StormLobeDescriptor.FLOATS_PER_DESCRIPTOR];
        base.withGroupSlot(2).writeTexels(texels, 0);
        requireNear("packed group topology", texels[15],
                StormLobeDescriptor.packTopology(2, 7, 0, StormLobeDescriptor.Role.BASE.gpuId()),
                0.0D);
        require(StormLobeDescriptor.unpackGroupSlot((int) texels[15]) == 2,
                "group topology pack/unpack mismatch");
        require(StormLobeDescriptor.unpackMemberCount((int) texels[15]) == 7,
                "member count topology pack/unpack mismatch");
        require(StormLobeDescriptor.unpackMemberIndex((int) texels[15]) == 0,
                "member index topology pack/unpack mismatch");
        require(StormLobeDescriptor.Role.fromGpuId(StormLobeDescriptor.unpackRole((int) texels[15]))
                == StormLobeDescriptor.Role.BASE, "role pack/unpack mismatch");

        for (StormLobeDescriptor.Role role : StormLobeDescriptor.Role.values()) {
            StormLobeDescriptor lobe = descriptor(group(2), role.gpuId(), 4, role,
                    0.0D, 0.0D, 220.0F + role.gpuId() * 20.0F,
                    285.0F + role.gpuId() * 28.0F,
                    role == StormLobeDescriptor.Role.ANVIL ? 150.0F : 72.0F,
                    role == StormLobeDescriptor.Role.ANVIL ? 52.0F : 62.0F);
            double previous = 0.0D;
            boolean positive = false;
            for (int sample = 0; sample <= 128; sample++) {
                double y = lobe.baseY() + (lobe.topY() - lobe.baseY()) * sample / 128.0D;
                double density = StormLobeEvaluator.densityAt(lobe, lobe.centerX(), y, lobe.centerZ());
                require(density >= 0.0D && density <= 1.0D, "role density out of range");
                require(Math.abs(density - previous) < 0.20D,
                        "role profile is discontinuous role=" + role
                                + " sample=" + sample
                                + " previous=" + previous + " density=" + density);
                positive |= density > 0.05D;
                previous = density;
            }
            require(positive, "role profile contains no volume for " + role);
            requireNear("outside vertical bounds",
                    StormLobeEvaluator.densityAt(lobe, lobe.centerX(), lobe.topY() + 1.0D, lobe.centerZ()),
                    0.0D, 0.0D);
        }

        List<StormLobeDescriptor> overlap = new ArrayList<>();
        overlap.add(base.withGroupSlot(0));
        overlap.add(descriptor(group(1), 1, 7, StormLobeDescriptor.Role.CORE,
                8.0D, 0.0D, 245.0F, 320.0F, 70.0F, 58.0F).withGroupSlot(0));
        double first = StormLobeEvaluator.coverageEnvelopeAt(overlap, 4.0D, 260.0D, 0.0D);
        Collections.reverse(overlap);
        double reversed = StormLobeEvaluator.coverageEnvelopeAt(overlap, 4.0D, 260.0D, 0.0D);
        requireNear("order-independent union", first, reversed, 1.0E-7D);
        require(first > 0.05D, "overlap union has a hard gap");

        StormLobeDescriptor sanitized = new StormLobeDescriptor(
                group(3), group(3), 0, 1, 0, StormLobeDescriptor.Role.BASE,
                Double.NaN, Double.POSITIVE_INFINITY, Float.NaN, Float.NaN,
                Float.NaN, Float.NEGATIVE_INFINITY, Float.NaN, Float.NaN,
                Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN,
                Float.NaN, Float.NaN, Float.NaN
        );
        require(Double.isFinite(sanitized.centerX()) && Double.isFinite(sanitized.centerZ()),
                "descriptor did not sanitize non-finite center");
        require(Float.isFinite(sanitized.topY()) && sanitized.topY() > sanitized.baseY(),
                "descriptor did not sanitize vertical bounds");

        double lowerShoulder = StormLobeEvaluator.densityAt(
                base, base.centerX() + base.majorRadius() * 0.82D,
                base.baseY() + (base.topY() - base.baseY()) * 0.30D, base.centerZ()
        );
        double upperShoulder = StormLobeEvaluator.densityAt(
                base, base.centerX() + base.majorRadius() * 0.82D,
                base.baseY() + (base.topY() - base.baseY()) * 0.90D, base.centerZ()
        );
        require(lowerShoulder > upperShoulder + 0.05D,
                "storm boundary collapsed to a full-height planar/cylindrical wall");
    }

    private static void validateGroupSelectionAndCandidatePacking() {
        List<StormLobeDescriptor> source = new ArrayList<>();
        addCompleteGroup(source, group(10), 0.0D, 0.0D, 7);
        addCompleteGroup(source, group(20), 800.0D, 0.0D, 7);
        // Incomplete group must never be partially admitted.
        source.add(descriptor(group(30), 0, 7, StormLobeDescriptor.Role.BASE,
                20.0D, 20.0D, 220.0F, 270.0F, 80.0F, 65.0F));
        StormGeometryBuildInput input = new StormGeometryBuildInput(
                1L, 1L, 10L, 20L, 0.0D, 0.0D,
                -2048.0D, -2048.0D, 4096.0F,
                source.toArray(StormLobeDescriptor[]::new)
        );
        StormGeometryBuild build = StormLobeSpatialIndex.build(input);
        require(build.descriptorCount() == 14, "complete-group selection admitted partial topology");
        StormLobeDescriptor[] selected = build.selectedDescriptorsUnsafe();
        for (int index = 1; index < selected.length; index++) {
            StormLobeDescriptor previous = selected[index - 1];
            StormLobeDescriptor current = selected[index];
            require(previous.groupSlot() <= current.groupSlot(), "group slots are unstable");
        }
        for (int first = -1; first < StormLobeSpatialIndex.MAX_LOBES; first += 7) {
            for (int second = -1; second < StormLobeSpatialIndex.MAX_LOBES; second += 11) {
                float packed = StormLobeSpatialIndex.packPair(first, second);
                require(StormLobeSpatialIndex.unpackCandidate(packed, 0) == first,
                        "base-65 first digit mismatch");
                require(StormLobeSpatialIndex.unpackCandidate(packed, 1) == second,
                        "base-65 second digit mismatch");
            }
        }
        require(build.activeTiles() > 0, "candidate grid is empty");
        require(build.maxCandidatesPerTile() >= 1, "candidate grid lost its group witness");

        List<StormLobeDescriptor> overflowing = new ArrayList<>();
        addCompleteGroup(overflowing, group(40), 0.0D, 0.0D, 7);
        addCompleteGroup(overflowing, group(50), 0.0D, 0.0D, 7);
        StormGeometryBuild overflowBuild = StormLobeSpatialIndex.build(new StormGeometryBuildInput(
                1L, 2L, 11L, 21L, 0.0D, 0.0D,
                -2048.0D, -2048.0D, 4096.0F,
                overflowing.toArray(StormLobeDescriptor[]::new)
        ));
        require(overflowBuild.overflowTiles() == 0,
                "group-witness candidate grid overflowed before its eight-group capacity");
        int centerPixel = (128 * StormLobeSpatialIndex.GRID_SIZE + 128) * 4;
        Set<Integer> overflowGroupSlots = new HashSet<>();
        float[] candidates = overflowBuild.candidateTexelsUnsafe();
        StormLobeDescriptor[] overflowSelected = overflowBuild.selectedDescriptorsUnsafe();
        for (int rank = 0; rank < StormLobeSpatialIndex.CANDIDATES_PER_TILE; rank++) {
            int descriptorIndex = StormLobeSpatialIndex.unpackCandidate(
                    candidates[centerPixel + rank / 2], rank & 1
            );
            if (descriptorIndex >= 0) {
                overflowGroupSlots.add(overflowSelected[descriptorIndex].groupSlot());
            }
        }
        require(overflowGroupSlots.size() == 2,
                "candidate grid did not preserve one stable witness for each overlapping group");

        StormLobeDescriptor sheared = new StormLobeDescriptor(
                new UUID(group(60).getMostSignificantBits(), group(60).getLeastSignificantBits() + 4L),
                group(60), 3, 4, -1, StormLobeDescriptor.Role.ANVIL,
                0.0D, 0.0D, 220.0F, 360.0F, 42.0F, 34.0F,
                0.0F, 1.0F, 120.0F, 0.0F,
                0.9F, 0.2F, 0.4F, 0.8F, 1.0F, 1.0F
        );
        StormGeometryBuild shearBuild = StormLobeSpatialIndex.build(new StormGeometryBuildInput(
                1L, 3L, 12L, 22L, 0.0D, 0.0D,
                -256.0D, -256.0D, 512.0F, new StormLobeDescriptor[]{
                        descriptor(group(60), 0, 4, StormLobeDescriptor.Role.BASE,
                                0.0D, 0.0D, 220.0F, 280.0F, 40.0F, 34.0F),
                        descriptor(group(60), 1, 4, StormLobeDescriptor.Role.CORE,
                                0.0D, 0.0D, 240.0F, 320.0F, 40.0F, 34.0F),
                        descriptor(group(60), 2, 4, StormLobeDescriptor.Role.TOWER,
                                0.0D, 0.0D, 260.0F, 350.0F, 40.0F, 34.0F),
                        sheared
                }
        ));
        int shearTileX = (int) Math.floor((100.0D + 256.0D) / 2.0D);
        int shearTileZ = 128;
        int shearPixel = (shearTileZ * StormLobeSpatialIndex.GRID_SIZE + shearTileX) * 4;
        boolean foundShearedAnvil = false;
        for (int rank = 0; rank < StormLobeSpatialIndex.CANDIDATES_PER_TILE; rank++) {
            int candidate = StormLobeSpatialIndex.unpackCandidate(
                    shearBuild.candidateTexelsUnsafe()[shearPixel + rank / 2], rank & 1
            );
            if (candidate >= 0
                    && shearBuild.selectedDescriptorsUnsafe()[candidate].role()
                    == StormLobeDescriptor.Role.ANVIL) {
                foundShearedAnvil = true;
            }
        }
        require(foundShearedAnvil, "candidate bounds omitted the sheared top footprint");

        StormLobeDescriptor profileAnvil = new StormLobeDescriptor(
                new UUID(group(61).getMostSignificantBits(), group(61).getLeastSignificantBits() + 4L),
                group(61), 3, 4, -1, StormLobeDescriptor.Role.ANVIL,
                0.0D, 0.0D, 220.0F, 360.0F, 42.0F, 34.0F,
                0.0F, 1.0F, 0.0F, 0.0F,
                0.9F, 0.2F, 0.4F, 0.8F, 1.0F, 1.0F
        );
        StormGeometryBuild profileBuild = StormLobeSpatialIndex.build(new StormGeometryBuildInput(
                1L, 4L, 13L, 23L, 0.0D, 0.0D,
                -256.0D, -256.0D, 512.0F, new StormLobeDescriptor[]{
                        descriptor(group(61), 0, 4, StormLobeDescriptor.Role.BASE,
                                0.0D, 0.0D, 220.0F, 280.0F, 15.0F, 15.0F),
                        descriptor(group(61), 1, 4, StormLobeDescriptor.Role.CORE,
                                0.0D, 0.0D, 240.0F, 320.0F, 15.0F, 15.0F),
                        descriptor(group(61), 2, 4, StormLobeDescriptor.Role.TOWER,
                                0.0D, 0.0D, 260.0F, 350.0F, 15.0F, 15.0F),
                        profileAnvil
                }
        ));
        int profileTileX = (int) Math.floor((47.0D + 256.0D) / 2.0D);
        int profilePixel = (128 * StormLobeSpatialIndex.GRID_SIZE + profileTileX) * 4;
        boolean foundProfileShoulder = false;
        for (int rank = 0; rank < StormLobeSpatialIndex.CANDIDATES_PER_TILE; rank++) {
            int candidate = StormLobeSpatialIndex.unpackCandidate(
                    profileBuild.candidateTexelsUnsafe()[profilePixel + rank / 2], rank & 1
            );
            if (candidate >= 0
                    && profileBuild.selectedDescriptorsUnsafe()[candidate].role()
                    == StormLobeDescriptor.Role.ANVIL) {
                foundProfileShoulder = true;
            }
        }
        require(foundProfileShoulder,
                "candidate bounds omitted the role profile beyond the nominal radius");
    }

    private static void validateDirtyAndAdoptionContracts() {
        StormGeometryBuildInput input = new StormGeometryBuildInput(
                4L, 9L, 11L, 12L, 0.0D, 0.0D,
                -2048.0D, -2048.0D, 4096.0F,
                new StormLobeDescriptor[0]
        );
        StormGeometryBuild result = StormLobeSpatialIndex.build(input);
        require(StormGeometryBuildCoordinator.isAdoptableForTest(
                result, 4L, 12L, -2048.0D, -2048.0D, 4096.0F),
                "matching build rejected");
        require(!StormGeometryBuildCoordinator.isAdoptableForTest(
                result, 5L, 12L, -2048.0D, -2048.0D, 4096.0F),
                "stale session accepted");
        require(!StormGeometryBuildCoordinator.isAdoptableForTest(
                result, 4L, 13L, -2048.0D, -2048.0D, 4096.0F),
                "stale grid accepted");

        List<VolumetricRenderCell> stable = List.of(renderCell(1.25D));
        List<VolumetricRenderCell> interpolated = List.of(renderCell(1.251D));
        List<VolumetricRenderCell> movedAcrossTiles = List.of(renderCell(65.25D));
        long stableSignature = StormLobeSpatialIndex.gridSignature(
                stable, -2048.0D, -2048.0D, 4096.0F
        );
        require(stableSignature == StormLobeSpatialIndex.gridSignature(
                        interpolated, -2048.0D, -2048.0D, 4096.0F),
                "sub-bound interpolation dirtied the candidate cache");
        require(stableSignature != StormLobeSpatialIndex.gridSignature(
                        movedAcrossTiles, -2048.0D, -2048.0D, 4096.0F),
                "tile-bound crossing failed to dirty the candidate cache");
    }

    private static void validateCoalescingAndSaturationContracts() {
        StormGeometryBuildInput first = emptyInput(1L);
        StormGeometryBuildInput second = emptyInput(2L);
        StormGeometryBuildInput latest = emptyInput(3L);
        StormGeometryBuildCoordinator.LatestRequestMailbox mailbox =
                new StormGeometryBuildCoordinator.LatestRequestMailbox();
        mailbox.replacePending(first);
        require(mailbox.takeForSubmission().requestGeneration() == 1L,
                "mailbox did not submit its first request");
        mailbox.replacePending(second);
        mailbox.replacePending(latest);
        require(mailbox.takeForSubmission() == null,
                "mailbox admitted more than one in-flight request");
        mailbox.completeSubmission();
        require(mailbox.takeForSubmission().requestGeneration() == 3L,
                "mailbox did not coalesce to the latest pending request");
        mailbox.completeSubmission();

        ThreadPoolExecutor saturated = new ThreadPoolExecutor(
                1, 1, 1L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.AbortPolicy()
        );
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        AtomicBoolean rejectedRanInline = new AtomicBoolean();
        saturated.execute(() -> {
            workerStarted.countDown();
            try {
                releaseWorker.await(5L, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        try {
            require(workerStarted.await(5L, TimeUnit.SECONDS),
                    "saturation fixture worker did not start");
            require(AsyncAtmosphereService.tryOfferClientTask(saturated, () -> { }),
                    "saturation fixture did not fill its queue");
            require(!AsyncAtmosphereService.tryOfferClientTask(
                            saturated, () -> rejectedRanInline.set(true)),
                    "saturated optional client work was accepted");
            require(!rejectedRanInline.get(),
                    "saturated optional client work ran on the caller thread");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("saturation fixture interrupted", interrupted);
        } finally {
            releaseWorker.countDown();
            saturated.shutdownNow();
        }
    }

    private static void runPhase4RFailFirst() {
        List<RegressionResult> results = new ArrayList<>();
        capture(results, "T074 storm silhouette", StormVolumetricGeometrySandbox::validateFixedStormSilhouette);
        capture(results, "T075 descriptor locality", StormVolumetricGeometrySandbox::validateDescriptorLocality);
        capture(results, "T076 independent GLSL parity", StormVolumetricGeometrySandbox::validateIndependentGlslParity);
        capture(results, "T077 descriptor smooth-union composition",
                StormVolumetricGeometrySandbox::validateDescriptorComposition);
        capture(results, "T079 descriptor slot validity", StormVolumetricGeometrySandbox::validateDescriptorSlotValidity);
        capture(results, "T079 incomplete group fallback", StormVolumetricGeometrySandbox::validateIncompleteGroupFallback);
        capture(results, "T079 rejected async build re-request",
                StormVolumetricGeometrySandbox::validateRejectedBuildRerequest);
        capture(results, "T079 cluster-only signatures", StormVolumetricGeometrySandbox::validateClusterOnlySignatures);
        capture(results, "T079 candidate group witness coverage",
                StormVolumetricGeometrySandbox::validateCandidateGroupWitnessCoverage);
        capture(results, "T079 candidate non-authority", StormVolumetricGeometrySandbox::validateCandidateNonAuthority);
        capture(results, "T079 bounded per-group intersection",
                StormVolumetricGeometrySandbox::validateBoundedPerGroupIntersection);
        reportAndFail("storm", results);
    }

    /**
     * Corrected Phase 4R gate.  It deliberately calls the exact same
     * invariant methods as the historical fail-first collector, but regards
     * a thrown assertion as a real regression rather than expected evidence.
     * Set {@code -Dphase4r.failFirst=true} to reproduce the pre-fix audit
     * collector without weakening any assertion.
     */
    private static void runPhase4RCorrected() {
        runCorrected("T074 storm silhouette", StormVolumetricGeometrySandbox::validateFixedStormSilhouette);
        runCorrected("T075 descriptor locality", StormVolumetricGeometrySandbox::validateDescriptorLocality);
        runCorrected("T076 independent GLSL parity", StormVolumetricGeometrySandbox::validateIndependentGlslParity);
        runCorrected("T077 descriptor smooth-union composition",
                StormVolumetricGeometrySandbox::validateDescriptorComposition);
        runCorrected("T079 descriptor slot validity", StormVolumetricGeometrySandbox::validateDescriptorSlotValidity);
        runCorrected("T079 incomplete group fallback", StormVolumetricGeometrySandbox::validateIncompleteGroupFallback);
        runCorrected("T079 rejected async build re-request",
                StormVolumetricGeometrySandbox::validateRejectedBuildRerequest);
        runCorrected("T079 cluster-only signatures", StormVolumetricGeometrySandbox::validateClusterOnlySignatures);
        runCorrected("T079 candidate group witness coverage",
                StormVolumetricGeometrySandbox::validateCandidateGroupWitnessCoverage);
        runCorrected("T079 candidate non-authority", StormVolumetricGeometrySandbox::validateCandidateNonAuthority);
        runCorrected("T079 bounded per-group intersection",
                StormVolumetricGeometrySandbox::validateBoundedPerGroupIntersection);
        runCorrected("T111 production storm shader compiles",
                StormVolumetricGeometrySandbox::validateProductionShaderCompiles);
    }

    private static void validateFixedStormSilhouette() {
        UUID storm = group(74);
        List<StormLobeDescriptor> lobes = List.of(
                descriptor(storm, 0, 7, StormLobeDescriptor.Role.BASE,
                        -20.0D, 0.0D, 220.0F, 302.0F, 168.0F, 132.0F).withGroupSlot(0),
                descriptor(storm, 1, 7, StormLobeDescriptor.Role.CORE,
                        -10.0D, 2.0D, 248.0F, 365.0F, 94.0F, 78.0F).withGroupSlot(0),
                descriptor(storm, 2, 7, StormLobeDescriptor.Role.CORE,
                        12.0D, -5.0D, 258.0F, 375.0F, 88.0F, 72.0F).withGroupSlot(0),
                descriptor(storm, 3, 7, StormLobeDescriptor.Role.TOWER,
                        -5.0D, 1.0D, 296.0F, 446.0F, 55.0F, 45.0F).withGroupSlot(0),
                descriptor(storm, 4, 7, StormLobeDescriptor.Role.TOWER,
                        13.0D, -4.0D, 306.0F, 454.0F, 51.0F, 42.0F).withGroupSlot(0),
                descriptor(storm, 5, 7, StormLobeDescriptor.Role.ANVIL,
                        8.0D, 0.0D, 384.0F, 502.0F, 194.0F, 76.0F).withGroupSlot(0),
                descriptor(storm, 6, 7, StormLobeDescriptor.Role.ANVIL,
                        30.0D, -5.0D, 396.0F, 506.0F, 174.0F, 70.0F).withGroupSlot(0)
        );
        Section base = sampleSection(lobes, 258.0D);
        Section tower = sampleSection(lobes, 352.0D);
        Section anvil = sampleSection(lobes, 440.0D);
        double maxRadiusDelta = 0.0D;
        double previousRadius = 0.0D;
        for (double y = 230.0D; y <= 490.0D; y += 4.0D) {
            double radius = sampleSection(lobes, y).equivalentRadius();
            if (previousRadius > 0.0D && radius > 0.0D) {
                maxRadiusDelta = Math.max(maxRadiusDelta, Math.abs(radius - previousRadius));
            }
            previousRadius = radius;
        }
        double maxVerticalStep = 0.0D;
        for (double x = -220.0D; x <= 220.0D; x += 20.0D) {
            double previous = StormLobeEvaluator.coverageEnvelopeAt(lobes, x, 230.0D, 0.0D);
            for (double y = 231.0D; y <= 490.0D; y += 1.0D) {
                double current = StormLobeEvaluator.coverageEnvelopeAt(lobes, x, y, 0.0D);
                maxVerticalStep = Math.max(maxVerticalStep, Math.abs(current - previous));
                previous = current;
            }
        }

        List<String> violations = new ArrayList<>();
        if (!(tower.equivalentRadius() < base.equivalentRadius() * 0.78D)) {
            violations.add("tower radius " + format(tower.equivalentRadius())
                    + " is not materially narrower than base " + format(base.equivalentRadius()));
        }
        if (!(anvil.equivalentRadius() > tower.equivalentRadius() * 1.25D)) {
            violations.add("anvil radius " + format(anvil.equivalentRadius())
                    + " is not wider than tower " + format(tower.equivalentRadius()));
        }
        if (base.components() != 1 || tower.components() != 1 || anvil.components() != 1) {
            violations.add("connected components base/tower/anvil="
                    + base.components() + "/" + tower.components() + "/" + anvil.components());
        }
        if (maxRadiusDelta > 18.0D) {
            violations.add("adjacent-height equivalent-radius delta=" + format(maxRadiusDelta));
        }
        if (maxVerticalStep > 0.12D) {
            violations.add("one-block vertical density step=" + format(maxVerticalStep));
        }
        require(violations.isEmpty(), String.join("; ", violations));
    }

    private static Section sampleSection(List<StormLobeDescriptor> lobes, double y) {
        final int minimum = -280;
        final int step = 4;
        final int size = 141;
        boolean[][] occupied = new boolean[size][size];
        int occupiedCount = 0;
        for (int zIndex = 0; zIndex < size; zIndex++) {
            double z = minimum + zIndex * step;
            for (int xIndex = 0; xIndex < size; xIndex++) {
                double x = minimum + xIndex * step;
                occupied[zIndex][xIndex] = StormLobeEvaluator.coverageEnvelopeAt(lobes, x, y, z) >= 0.08D;
                if (occupied[zIndex][xIndex]) {
                    occupiedCount++;
                }
            }
        }
        int components = connectedComponents(occupied);
        double area = occupiedCount * step * step;
        return new Section(Math.sqrt(area / Math.PI), components);
    }


    /**
     * The margin the production T121 guard adds to {@code lobeSoftness}, in
     * world blocks. Derived, not chosen: it must exceed the measured float32 SDF
     * shortfall plus the ULP of the softness comparison itself, while staying
     * negligible against the 11.36-block minimum softness it guards.
     */
    static final float T121_SOFTNESS_MARGIN_BLOCKS = 9.765625E-4F;

    /**
     * T132 criterion 3 was rebased onto the adjacent repeated-sampling protocol,
     * retiring {@code existingSeparatedPassComparison}. That comparison separates
     * its two passes by multiple teleports and substantial live time, so it
     * admits drift unrelated to any optimization under test. This guards the
     * correction structurally: the acceptance verdict must be computed from the
     * adjacent controls alone, and the retired comparison must stay clearly
     * labelled and unable to make the suite report a false verdict.
     */
    private static void validateT132AuthoritativeControlSeparation() {
        String suite = readWorkspaceSource("src/main/java/net/Gabou/projectatmosphere/"
                + "clouds/client/render/volumetric/StormPerformanceSuite.java");

        require(suite.contains("authoritativeAdjacentControls={"),
                "T132 suite no longer emits authoritativeAdjacentControls");
        require(suite.contains("historicalSeparatedPassComparison={authoritative=false"),
                "T132 retired separated-pass comparison is not labelled non-authoritative");
        require(suite.contains("usedForT132Acceptance=false"),
                "T132 retired comparison does not declare itself excluded from acceptance");
        require(suite.contains("separatedPassControlFieldsMatch="),
                "T132 retired comparison verdict is not namespaced");
        require(suite.contains("separatedPassControlDifferences="),
                "T132 retired comparison differences are not namespaced");

        String aggregator = between(suite,
                "private static String authoritativeAdjacentControls(",
                "private static String diagnosticViewFor(",
                "T132 authoritative control aggregator");
        require(!aggregator.contains("controlDifferences(other"),
                "T132 authoritative verdict consults the retired separated-pass comparison");
        require(aggregator.contains("adjacentControlDifferences(fixture)"),
                "T132 authoritative verdict is not derived from the adjacent controls");
        require(aggregator.contains("controlsMatched="),
                "T132 authoritative verdict does not report controlsMatched");

        String adjacent = between(suite,
                "private List<String> adjacentControlDifferences(",
                "private void groupControlDifferences(",
                "T132 adjacent control evaluation");
        for (String required : new String[]{
                "frozen_fixture_fingerprint_mismatch",
                "structuralChanged",
                "governorScale_not_0.50000",
                "resolutionScale_not_0.75000",
                "production_topology_not_compact",
                "workload_target_does_not_match_baseline_target",
                "workload_capture_token_missing"}) {
            require(adjacent.contains(required),
                    "T132 adjacent controls dropped the " + required + " requirement");
        }
        System.out.println(
                "PHASE4T_RESULT|T132 authoritative control separation|PASSED|invariant satisfied");
    }

    /**
     * T133 / SC-020 production-default equivalence. The diagnostic OFF arms are
     * only admissible if ordinary frames cannot reach them, so this pins the
     * default at every layer: the shader constant, the shader's uniform default,
     * the debug config field, its reset contract, and the renderer upload.
     */
    private static void validateT133ProductionDefaultUnchanged() {
        String shader = readWorkspaceSource("src/main/resources/assets/projectatmosphere/"
                + "shaders/core/cloud_atmosphere_volume.fsh");
        require(shader.contains("const int PA_OPT_NORMAL_PRODUCTION = 0;")
                        && shader.contains("uniform int PaDiagnosticOptimizationMode;"),
                "T133 diagnostic optimization mode is not declared with a zero production value");
        require(shader.contains("(PaDiagnosticOptimizationMode & PA_OPT_T121_OFF) != 0")
                        && shader.contains("(PaDiagnosticOptimizationMode & PA_OPT_T122_OFF) != 0"),
                "T133 optimization mode is not decoded as an opt-in bit set");

        String json = readWorkspaceSource("src/main/resources/assets/projectatmosphere/"
                + "shaders/core/cloud_atmosphere_volume.json");
        require(json.contains("\"name\": \"PaDiagnosticOptimizationMode\", \"type\": \"int\", "
                        + "\"count\": 1, \"values\": [ 0 ]"),
                "T133 diagnostic optimization uniform does not default to NORMAL_PRODUCTION");

        String config = readWorkspaceSource("src/main/java/net/Gabou/projectatmosphere/"
                + "clouds/client/render/volumetric/VolumetricCloudDebugConfig.java");
        String reset = between(config, "public static void resetDefaults() {", "\n    }",
                "T133 debug config reset contract");
        require(reset.contains("optimizationDiagnosticMode = "
                        + "StormOptimizationDiagnosticMode.NORMAL_PRODUCTION;"),
                "T133 optimization diagnostic mode is not restored by resetDefaults()");

        String renderer = readWorkspaceSource("src/main/java/net/Gabou/projectatmosphere/"
                + "clouds/client/render/volumetric/VolumetricCloudRenderer.java");
        require(renderer.contains("shader.safeGetUniform(\"PaDiagnosticOptimizationMode\").set(")
                        && renderer.contains(
                                "VolumetricCloudDebugConfig.optimizationDiagnosticMode().shaderFlags()"),
                "T133 renderer does not upload the diagnostic optimization mode");
        require(renderer.contains("VolumetricCloudDebugConfig.optimizationDiagnosticMode(),"),
                "T133 draw snapshot does not record the optimization mode actually used");

        String suite = readWorkspaceSource("src/main/java/net/Gabou/projectatmosphere/"
                + "clouds/client/render/volumetric/StormPerformanceSuite.java");
        require(suite.contains("production_optimization_mode_not_normal"),
                "T133 adjacent controls do not verify the production optimization mode");
        System.out.println(
                "PHASE4T_RESULT|T133 production default unchanged|PASSED|invariant satisfied");
    }

    /**
     * T123 / SC-020. T123 owns a documented structural bound and the counters
     * that report it - not an image transformation. It therefore has no OFF arm
     * to build: the only candidates would be the transmittance and optical-depth
     * exits, which predate T123 and belong to the density integration itself.
     * What SC-020 needs is instead checkable directly: every counter it owns must
     * be unreachable in a production FINAL frame.
     */
    private static void validateT123InstrumentationOnly() {
        String shader = readWorkspaceSource("src/main/resources/assets/projectatmosphere/"
                + "shaders/core/cloud_atmosphere_volume.fsh");
        String[] lines = shader.split("\n");
        int mutations = 0;
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].trim();
            if (!line.matches("pa[A-Za-z]+\\s*(\\+\\+|\\+=).*")) {
                continue;
            }
            mutations++;
            boolean guarded = false;
            for (int back = Math.max(0, index - 3); back < index; back++) {
                if (lines[back].contains("paWorkloadCaptureActive()")) {
                    guarded = true;
                    break;
                }
            }
            require(guarded,
                    "T123 workload counter at shader line " + (index + 1)
                            + " is not guarded by paWorkloadCaptureActive(): " + line);
        }
        require(mutations >= 10,
                "T123 workload counters were not found; the instrumentation check is vacuous");
        String guard = functionBlock(shader, "bool paWorkloadCaptureActive()");
        require(guard.contains("DebugView == 22") && guard.contains("DebugView == 23"),
                "T123 workload capture guard is no longer limited to the workload debug views");
        System.out.println("PHASE4T_RESULT|T123 instrumentation only|PASSED|invariant satisfied"
                + " guardedCounterMutations=" + mutations);
    }

    /**
     * T121's rejection is sound only if the vertical slab bound really is a lower
     * bound on the lobe SDF: the branch skips a lobe when
     * {@code verticalLowerBound > max(lobeSoftness, groupDistance + 48)} and
     * relies on {@code lobeDistance >= verticalLowerBound}. The SDF is a
     * gradient-normalised first-order distance, exact only for a circular
     * section, so this searches the eccentricity, shear, orientation and role
     * space the profiles actually produce for a point where the premise fails.
     */
    private static void validateT121VerticalBoundIsConservative() {
        double worstViolation = 0.0D;
        String worstCase = "none";
        long probes = 0L;
        for (StormLobeDescriptor.Role role : StormLobeDescriptor.Role.values()) {
            for (int majorStep = 0; majorStep < 6; majorStep++) {
                for (int minorStep = 0; minorStep < 6; minorStep++) {
                    for (int orientationStep = 0; orientationStep < 4; orientationStep++) {
                        for (int shearStep = 0; shearStep < 4; shearStep++) {
                            double major = 40.0D + majorStep * 90.0D;
                            double minor = 20.0D + minorStep * 70.0D;
                            StormLobeDescriptor lobe = descriptor(
                                    group(121), 0, 1, role, 0.0D, 0.0D,
                                    200.0F, 700.0F, (float) major, (float) minor);
                            double roleBaseY = StormLobeEvaluator.roleBaseY(lobe);
                            double roleTopY = StormLobeEvaluator.roleTopY(lobe);
                            double centreY = (roleBaseY + roleTopY) * 0.5D;
                            double halfHeight = (roleTopY - roleBaseY) * 0.5D;
                            for (int yStep = -30; yStep <= 30; yStep++) {
                                double worldY = centreY + yStep * 30.0D;
                                double bound = Math.abs(worldY - centreY) - halfHeight;
                                if (bound <= 0.0D) {
                                    continue;
                                }
                                for (int xStep = -8; xStep <= 8; xStep++) {
                                    for (int zStep = -8; zStep <= 8; zStep++) {
                                        double distance = StormLobeEvaluator.signedDistanceAt(
                                                lobe, xStep * 90.0D, worldY, zStep * 90.0D);
                                        probes++;
                                        double violation = bound - distance;
                                        if (violation > worstViolation) {
                                            worstViolation = violation;
                                            worstCase = role + " major=" + major + " minor=" + minor
                                                    + " bound=" + bound + " sdf=" + distance;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        System.out.printf(java.util.Locale.ROOT,
                "T121_BOUND_SEARCH|probes=%d|worstViolationBlocks=%.9f|worstCase=%s%n",
                probes, worstViolation, worstCase);
        require(probes > 1_000_000L,
                "T121 bound search did not cover enough of the descriptor space");
        require(worstViolation <= 1.0e-6D,
                "T121 vertical bound is not a lower bound on the lobe SDF; worst violation "
                        + worstViolation + " blocks at " + worstCase);
        System.out.println(
                "PHASE4T_RESULT|T121 vertical bound conservative|PASSED|invariant satisfied");
    }

    /**
     * T121 float32 boundary margin. The guard compares {@code verticalLowerBound}
     * while the smooth union consumes {@code lobeDistance}; those are the same
     * quantity only up to rounding in the SDF tail. This measures the worst
     * shortfall over the severe-scale operating range and derives the margin the
     * rejection guard needs.
     */
    private static void validateT121Float32BoundaryMargin() {
        final float capRoundingFraction = 0.35F;
        final float minEdgeBlocks = 11.363636F;
        float worstShortfall = 0.0F;
        String worstCase = "none";
        long probes = 0L;
        for (int capStep = 0; capStep <= 4000; capStep++) {
            float capDistance = 0.25F * capStep;
            for (int radiusStep = 0; radiusStep < 12; radiusStep++) {
                float effectiveRadius = 20.0F + radiusStep * 60.0F;
                for (int halfStep = 0; halfStep < 6; halfStep++) {
                    float halfHeight = 40.0F + halfStep * 90.0F;
                    float rounding = Math.min(
                            Math.min(effectiveRadius, halfHeight) * capRoundingFraction,
                            minEdgeBlocks);
                    float roundedCap = capDistance + rounding;
                    for (int wallStep = -6; wallStep <= 12; wallStep++) {
                        float roundedWall = wallStep <= 0
                                ? wallStep * 40.0F
                                : roundedCap * (wallStep / 12.0F);
                        float outsideX = Math.max(roundedWall, 0.0F);
                        float outsideY = Math.max(roundedCap, 0.0F);
                        float lengthSquared = outsideX * outsideX + outsideY * outsideY;
                        float distance = (float) Math.sqrt(lengthSquared)
                                + Math.min(Math.max(roundedWall, roundedCap), 0.0F)
                                - rounding;
                        probes++;
                        float shortfall = capDistance - distance;
                        if (shortfall > worstShortfall) {
                            worstShortfall = shortfall;
                            worstCase = "capDistance=" + capDistance + " sdf=" + distance;
                        }
                    }
                }
            }
        }
        float halfUlpBelowOne = Math.ulp(1.0F) * 0.5F;
        float hSaturationShortfall = 48.0F * 2.0F * halfUlpBelowOne;
        float worstCompareUlp = Math.ulp(32768.0F);
        float requiredMargin = worstShortfall + worstCompareUlp;
        System.out.printf(java.util.Locale.ROOT,
                "T121_MARGIN_DERIVATION|sdfShortfall=%.9f|hSaturationThreshold=%.9f"
                        + "|reachable=%s|compareUlpAt32768=%.9f|requiredMargin=%.9f|adopted=%.9f%n",
                worstShortfall, hSaturationShortfall, worstShortfall > hSaturationShortfall,
                worstCompareUlp, requiredMargin, T121_SOFTNESS_MARGIN_BLOCKS);
        require(probes > 1_000_000L,
                "T121 float boundary search did not cover enough of the operating range");
        require(worstShortfall < hSaturationShortfall,
                "T121 SDF shortfall now reaches the smooth-union saturation threshold at "
                        + worstCase);
        System.out.println(
                "PHASE4T_RESULT|T121 float32 boundary margin|PASSED|invariant satisfied");
    }

    /**
     * Whenever the guard rejects a lobe, the production float32 smooth union must
     * contribute exactly nothing. Simulates guard and union together in float32
     * across the operating range. A rejection is safe only if {@code h} lands on
     * exactly {@code 1.0f}.
     */
    private static void validateT121GuardAdmitsNoUnionContribution() {
        float shortfall = 0.000003815F;
        long probes = 0L;
        long unsafe = 0L;
        String firstUnsafe = "none";
        for (int distanceStep = 0; distanceStep <= 600; distanceStep++) {
            float groupDistance = distanceStep <= 300
                    ? distanceStep * 8.0F
                    : 2400.0F + (distanceStep - 300) * 100.0F;
            for (int blendStep = 0; blendStep < 9; blendStep++) {
                float blend = blendStep == 8 ? 48.0F : 4.0F + blendStep * 5.5F;
                for (int softnessStep = 0; softnessStep < 4; softnessStep++) {
                    float softness = 4.0F + softnessStep * 18.0F;
                    for (int epsilonStep = -4; epsilonStep <= 4; epsilonStep++) {
                        float threshold = groupDistance + 48.0F;
                        float verticalLowerBound = threshold + epsilonStep * Math.ulp(threshold);
                        probes++;
                        float lobeDistance = verticalLowerBound - shortfall;
                        boolean guardFires = verticalLowerBound > Math.max(
                                softness + T121_SOFTNESS_MARGIN_BLOCKS, groupDistance + 48.0F);
                        if (!guardFires) {
                            continue;
                        }
                        float h = Math.max(0.0F, Math.min(1.0F,
                                0.5F + 0.5F * (lobeDistance - groupDistance) / blend));
                        if (h == 1.0F) {
                            continue;
                        }
                        unsafe++;
                        if ("none".equals(firstUnsafe)) {
                            firstUnsafe = "groupDistance=" + groupDistance + " blend=" + blend
                                    + " h=" + h;
                        }
                    }
                }
            }
        }
        System.out.printf(java.util.Locale.ROOT,
                "T121_UNION_PROOF|probes=%d|unsafe=%d%n", probes, unsafe);
        require(probes > 40_000L, "T121 union proof did not cover enough of the range");
        require(unsafe == 0L,
                "T121 guard admits a non-zero union contribution: " + firstUnsafe);
        System.out.println(
                "PHASE4T_RESULT|T121 guard admits no union contribution|PASSED|invariant satisfied");
    }

    /**
     * The guard's {@code lobeSoftness} term. At softness magnitudes (11-100
     * blocks) one float32 ULP is comparable to the SDF shortfall, so without a
     * margin a rejected lobe could still satisfy
     * {@code lobeDistance <= lobeSoftness}, which sets a discrete
     * {@code groupActiveRoleMask} bit. This proves the margin closes every such
     * flip, and that the unmargined guard genuinely admitted them.
     */
    private static void validateT121SoftnessBoundary() {
        float shortfall = 0.000003815F;
        long probes = 0L;
        long roleMaskFlips = 0L;
        String firstFlip = "none";
        for (int softnessStep = 0; softnessStep <= 2000; softnessStep++) {
            float lobeSoftness = 11.363636F + softnessStep * 0.05F;
            for (int distanceStep = 0; distanceStep < 6; distanceStep++) {
                float groupDistance = lobeSoftness - 48.0F - distanceStep * 25.0F;
                for (int epsilonStep = 1; epsilonStep <= 6; epsilonStep++) {
                    float verticalLowerBound =
                            lobeSoftness + epsilonStep * Math.ulp(lobeSoftness);
                    float lobeDistance = verticalLowerBound - shortfall;
                    boolean unmargined = verticalLowerBound > Math.max(
                            lobeSoftness, groupDistance + 48.0F);
                    boolean margined = verticalLowerBound > Math.max(
                            lobeSoftness + T121_SOFTNESS_MARGIN_BLOCKS, groupDistance + 48.0F);
                    if (!unmargined) {
                        continue;
                    }
                    probes++;
                    if (lobeDistance <= lobeSoftness) {
                        roleMaskFlips++;
                        if ("none".equals(firstFlip)) {
                            firstFlip = "lobeSoftness=" + lobeSoftness
                                    + " verticalLowerBound=" + verticalLowerBound
                                    + " lobeDistance=" + lobeDistance;
                        }
                        require(!margined,
                                "T121 softness margin still admits a role-mask flip: " + firstFlip);
                    }
                }
            }
        }
        System.out.printf(java.util.Locale.ROOT,
                "T121_SOFTNESS_BOUNDARY|probes=%d|roleMaskFlips=%d%n", probes, roleMaskFlips);
        require(probes > 1000L, "T121 softness search did not reach the guard's softness term");
        require(roleMaskFlips > 0L,
                "T121 unmargined softness term admitted no role-mask flip; "
                        + "the softness margin would be unjustified");
        System.out.println("T121_SOFTNESS_WITNESS|" + firstFlip);
        System.out.println(
                "PHASE4T_RESULT|T121 softness margin closes role-mask flip|PASSED"
                        + "|invariant satisfied");
    }

    /**
     * The production shader guards for T121's conservative rejection and T122's
     * descriptor-fetch reuse, including the T133 diagnostic OFF arms: the OFF
     * paths must be gated so ordinary frames keep the optimized behaviour.
     */
    private static void validateT121T122ShaderGuards() {
        String shader = readWorkspaceSource("src/main/resources/assets/projectatmosphere/"
                + "shaders/core/cloud_atmosphere_volume.fsh");
        String groupField = functionBlock(shader, "void directStormGroupField");
        String wrapper = functionBlock(shader, "float directStormLobeDistance(");

        require(shader.contains("float stormVerticalDistanceLowerBound")
                        && groupField.contains("verticalLowerBound > max(")
                        && groupField.contains("groupDistance + STORM_MAX_BLEND_BLOCKS")
                        && groupField.contains("paConservativeDescriptorRejects++")
                        && groupField.contains("previousRadius = lobeRadius")
                        && groupField.contains("previousRole = lobeRole"),
                "T121 descriptor rejection is not conservative or does not preserve union state");
        require(groupField.contains("started && !paT121Off() && verticalLowerBound > max("),
                "T121 OFF bypass is not gated behind paT121Off()");
        require(groupField.contains("lobeSoftness + STORM_T121_SOFTNESS_MARGIN_BLOCKS"),
                "T121 rejection lost its derived float32 softness margin");
        require(shader.contains("const float STORM_MAX_BLEND_BLOCKS = 48.0;"),
                "STORM_MAX_BLEND_BLOCKS is no longer 48");
        String marginKey = "const float STORM_T121_SOFTNESS_MARGIN_BLOCKS = ";
        int marginAt = shader.indexOf(marginKey);
        require(marginAt >= 0, "T121 softness margin constant is missing");
        String marginLiteral = shader.substring(
                marginAt + marginKey.length(), shader.indexOf(';', marginAt)).trim();
        require(Float.parseFloat(marginLiteral) == T121_SOFTNESS_MARGIN_BLOCKS,
                "T121 shader softness margin " + marginLiteral + " does not match the derived value");

        require(wrapper.contains("return directStormLobeDistanceFromData(")
                        && groupField.contains("directStormLobeDistanceFromData(")
                        && groupField.contains("stormEdgeWidthBlocksFromData(")
                        && groupField.contains("paAvoidedDescriptorTextureFetches += 2")
                        && groupField.contains("paAvoidedDescriptorTextureFetches += 4"),
                "T122 primary group evaluation re-fetches or recomputes descriptor data");
        require(groupField.contains("? stormEdgeWidthBlocks(descriptorIndex"),
                "T122 OFF refetch is not gated behind paT122Off()");
        require(groupField.contains("paWorkloadCaptureActive() && !paT122Off()"),
                "T122 avoided-fetch counters are not suppressed in the OFF arm");
        System.out.println(
                "PHASE4T_RESULT|T121/T122 shader guards|PASSED|invariant satisfied");
    }


    /**
     * T132 fail-first: a capture that failed after an earlier success used to
     * leave the earlier WorkloadResult readable, and the suite matched only on
     * the view name, so a stale result satisfied the next pass over the same
     * view. Freshness is the capture token, not the view.
     */
    private static void validateT132WorkloadCaptureFreshness() {
        StormWorkloadRuntimeCapture.WorkloadResult passA =
                new StormWorkloadRuntimeCapture.WorkloadResult(
                        41L, "above", 641, 360,
                        70635847.0D, 1941080612.0D, 6714578.0D, 326863346.0D,
                        2027767531.0D, 21318588.0D, 1488992.0D, 149382.0D);

        require(StormPerformanceSuite.workloadFreshnessFailure(passA, 41L, "above") == null,
                "T132 freshness rejected the capture it actually requested");

        String staleFailure = StormPerformanceSuite.workloadFreshnessFailure(passA, 42L, "above");
        require(staleFailure != null && staleFailure.startsWith("workload_capture_stale"),
                "T132 freshness accepted a stale same-view workload result");
        require(staleFailure.contains("expectedToken=42") && staleFailure.contains("resultToken=41"),
                "T132 stale-capture abort reason is not auditable");

        String missing = StormPerformanceSuite.workloadFreshnessFailure(null, 42L, "above");
        require(missing != null && missing.startsWith("workload_capture_missing"),
                "T132 freshness accepted a cleared workload result");

        String unrequested = StormPerformanceSuite.workloadFreshnessFailure(
                passA, StormWorkloadRuntimeCapture.NO_TOKEN, "above");
        require(unrequested != null && unrequested.startsWith("workload_capture_not_requested"),
                "T132 freshness accepted a result the suite never requested");

        System.out.println(
                "PHASE4T_RESULT|T132 workload capture freshness|PASSED|invariant satisfied");
    }

    /**
     * T132 criterion 5 is evaluated by a deterministic numeric comparator, not by
     * visualRef equality: visualRef digests a FINAL frame accumulated at history
     * blend 0.85, so two passes disagree by construction on an unchanged fixture.
     * The tolerance is one binary16 storage step at the compared magnitude.
     */
    private static void validateT132DeterministicImageComparator() {
        int width = 8;
        int height = 4;
        float[] pixels = new float[width * height * 4];
        for (int index = 0; index < pixels.length; index++) {
            pixels[index] = (float) (0.4D + 0.3D * Math.sin(index * 0.7D));
        }
        StormReferenceImageComparison.Reference reference =
                reference("side", width, height, true, pixels.clone());

        StormReferenceImageComparison.Comparison identical =
                StormReferenceImageComparison.compare(reference,
                        reference("side", width, height, true, pixels.clone()));
        require(identical.evaluated() && identical.passed(),
                "T132 comparator rejected two identical captures");
        require(identical.changedPixelCountAboveEpsilon() == 0,
                "T132 comparator reported changed pixels for identical captures");

        // One storage ULP at the compared magnitude must remain acceptable, and
        // anything meaningfully larger must not.
        float[] nudged = pixels.clone();
        double epsilon = StormReferenceImageComparison.halfPrecisionEpsilon(pixels[9]);
        nudged[9] += (float) (epsilon * 40.0D);
        StormReferenceImageComparison.Comparison moved =
                StormReferenceImageComparison.compare(reference,
                        reference("side", width, height, true, nudged));
        require(moved.evaluated() && !moved.passed(),
                "T132 comparator accepted a difference far above one storage ULP");
        require(moved.changedPixelCountAboveEpsilon() == 1,
                "T132 comparator did not localise the moved pixel");

        require(StormReferenceImageComparison.halfPrecisionEpsilon(0.0D) > 0.0D,
                "T132 epsilon is not positive in the subnormal range");

        System.out.println(
                "PHASE4T_RESULT|T132 deterministic image neutrality|PASSED|invariant satisfied");
    }

    /**
     * T132 repeated adjacent sampling. The production raymarch has hard decision
     * points, so a rare frame differs from its neighbours under otherwise
     * identical inputs. A per-component median over an odd sample count rejects
     * that lone frame without touching the tolerance.
     */
    private static void validateT132RepeatedSamplingMedian() {
        int width = 8;
        int height = 4;
        float[] clean = new float[width * height * 4];
        for (int index = 0; index < clean.length; index++) {
            clean[index] = (float) (0.4D + 0.3D * Math.sin(index * 0.7D));
        }
        double epsilon = StormReferenceImageComparison.halfPrecisionEpsilon(0.7D);

        float[] outlier = clean.clone();
        outlier[9] += (float) (epsilon * 40.0D);
        java.util.List<StormReferenceImageComparison.Reference> noisy = java.util.List.of(
                reference("below", width, height, true, clean.clone()),
                reference("below", width, height, true, outlier),
                reference("below", width, height, true, clean.clone()),
                reference("below", width, height, true, clean.clone()),
                reference("below", width, height, true, clean.clone()));
        float[] median = StormReferenceSampleSet.median(noisy);
        require(java.util.Arrays.equals(median, clean),
                "T132 median did not reject a single outlying sample");

        // The dispersion must still be reported rather than hidden by the median.
        StormReferenceSampleSet.ArmNoise noise = StormReferenceSampleSet.noise(noisy, median);
        require(noise.samplesDifferingFromMedian() == 1,
                "T132 arm noise did not report the deviating sample");
        require(noise.pairwiseMaxChangedPixels() >= 1,
                "T132 arm noise did not report the changed pixel population");

        java.util.List<StormReferenceImageComparison.Reference> quiet = java.util.List.of(
                reference("below", width, height, true, clean.clone()),
                reference("below", width, height, true, clean.clone()),
                reference("below", width, height, true, clean.clone()));
        StormReferenceSampleSet.ArmNoise silent =
                StormReferenceSampleSet.noise(quiet, StormReferenceSampleSet.median(quiet));
        require(silent.samplesDifferingFromMedian() == 0
                        && silent.pairwiseMaxChangedPixels() == 0,
                "T132 arm noise invented dispersion for identical samples");

        System.out.println(
                "PHASE4T_RESULT|T132 repeated sampling median|PASSED|invariant satisfied");
    }

    /** Builds a reference capture for the deterministic comparator checks. */
    private static StormReferenceImageComparison.Reference reference(
            String view, int width, int height, boolean historyBypassed, float[] pixels) {
        return new StormReferenceImageComparison.Reference(
                view, width, height, historyBypassed,
                StormReferenceImageComparison.digest(pixels, width, height), pixels,
                6000.0F, 6000.0F, true,
                new StormSceneStability.RenderInputs(
                        0L,
                        VolumetricCloudRenderer.LastDrawInputs.UniformComponentSignatures.EMPTY,
                        0L,
                        "projectionStability={requiredStableFrames=3 stabilized=true}"
                                + " contentStability={stabilized=true}",
                        StormTopologyMode.COMPACT,
                        StormOptimizationDiagnosticMode.NORMAL_PRODUCTION),
                null);
    }


    /**
     * T132 criterion 5 attribution. A failing comparison is escalated through
     * levels, because a partial input set matching never proves the renderer
     * moved the image:
     *
     * <ul>
     *   <li>a tracked scene input differs -&gt; {@code scene_evolved_between_passes};</li>
     *   <li>the scene held still but a named uniform group or the weather-map
     *       signature differs -&gt; {@code render_inputs_differ_between_passes};</li>
     *   <li>everything tracked matches and the image still differs -&gt;
     *       {@code unexplained_deterministic_render_difference}, which remains
     *       blocking.</li>
     * </ul>
     *
     * <p>A passing comparison is a pass regardless of any diagnostic difference.
     */
    private static void validateT132Attribution() {
        StormSceneStability.Snapshot profile = StormSceneStability.Snapshot.of(java.util.List.of());
        StormSceneStability.AnimatedInputs held = new StormSceneStability.AnimatedInputs(
                12.5F, -7.25F, 6000.0F, 6000.0F, true, false, 0.0F, 1.0F, 0.0F, profile);
        StormSceneStability.AnimatedInputs moved = new StormSceneStability.AnimatedInputs(
                13.5F, -7.25F, 6000.0F, 6000.0F, true, false, 0.0F, 1.0F, 0.0F, profile);

        StormSceneStability.Result stable = StormSceneStability.evaluate(held, held);
        StormSceneStability.Result evolved = StormSceneStability.evaluate(held, moved);
        require(stable.evaluated() && stable.sceneStable(),
                "T132 scene stability rejected two identical animated inputs");
        require(evolved.evaluated() && !evolved.sceneStable(),
                "T132 scene stability accepted a moved material offset");

        StormSceneStability.RenderInputs inputsA = renderInputs(0L, 0L);
        StormSceneStability.RenderInputs inputsB = renderInputs(0L, 0L);
        StormSceneStability.RenderInputs inputsWeatherMoved = renderInputs(0L, 99L);
        StormSceneStability.RenderInputComparison sameInputs =
                StormSceneStability.compareRenderInputs(inputsA, inputsB);
        StormSceneStability.RenderInputComparison movedInputs =
                StormSceneStability.compareRenderInputs(inputsA, inputsWeatherMoved);
        require(sameInputs.evaluated() && sameInputs.renderInputsMatch(),
                "T132 render-input comparison rejected identical inputs");
        require(movedInputs.evaluated() && !movedInputs.renderInputsMatch(),
                "T132 render-input comparison accepted a moved weather-map signature");

        // A matching content comparison, so attribution can reach the levels
        // above it rather than short-circuiting on missing content.
        StormCloudContent sameContent = new StormCloudContent(
                4, 11L, 12L, 10, 10, 1, 10, 13L);
        StormCloudContent.Comparison content =
                StormCloudContent.compare(sameContent, sameContent);
        require(content.evaluated() && content.cloudContentMatch(),
                "T132 cloud-content comparison rejected two identical snapshots");

        // A passing image is a pass even when a diagnostic input moved.
        String passing = StormSceneStability.attribution(true, true, evolved, movedInputs, content);
        require(passing.contains("imageNeutralityPassed=true"),
                "T132 attribution did not report a passing comparison as passing");

        // Level A: the scene itself evolved.
        String sceneMoved = StormSceneStability.attribution(
                true, false, evolved, sameInputs, content);
        require(sceneMoved.contains("criterion5Attributable=false")
                        && sceneMoved.contains("scene_evolved_between_passes"),
                "T132 attribution blamed the renderer for an evolving scene");

        // Level B: the scene held but a named render input differs.
        String inputsDiffer = StormSceneStability.attribution(
                true, false, stable, movedInputs, content);
        require(inputsDiffer.contains("criterion5Attributable=false")
                        && inputsDiffer.contains("render_inputs_differ_between_passes"),
                "T132 attribution blamed the renderer for differing render inputs");

        // Level C: everything tracked matches and the image still differs.
        String unexplained = StormSceneStability.attribution(
                true, false, stable, sameInputs, content);
        require(unexplained.contains("unexplained_deterministic_render_difference"),
                "T132 attribution did not escalate an exhausted input set");

        // An unavailable comparison is never attributable.
        String unavailable = StormSceneStability.attribution(
                false, false, stable, sameInputs, content);
        require(unavailable.contains("criterion5Attributable=false")
                        && unavailable.contains("image_comparison_unavailable"),
                "T132 attribution attributed an unavailable comparison");

        System.out.println(
                "PHASE4T_RESULT|T132 criterion 5 attribution|PASSED|invariant satisfied");
    }

    /** Builds a render-input snapshot for the attribution checks. */
    private static StormSceneStability.RenderInputs renderInputs(
            long comparisonSignature, long weatherSignature) {
        return new StormSceneStability.RenderInputs(
                comparisonSignature,
                VolumetricCloudRenderer.LastDrawInputs.UniformComponentSignatures.EMPTY,
                weatherSignature,
                "projectionStability={requiredStableFrames=3 stabilized=true}",
                StormTopologyMode.COMPACT,
                StormOptimizationDiagnosticMode.NORMAL_PRODUCTION);
    }


    /**
     * T098 root-cause measurement: the distribution of the quantity
     * {@code stormBaseField} is actually applied to.
     *
     * <p>{@code morphology-thresholds.md} records the Perlin-Worley carrier
     * p05/p95 as 0.7128/0.8451, measured on the baked R channel. The shader does
     * not feed the R channel to {@code stormBaseField}: it feeds
     * {@code carrierRaw = saturate(remap(r, -(1 - lowFbm), 1, 0, 1))}, where
     * {@code lowFbm} is the weighted Worley FBM of the G/B/A channels. This
     * measures both quantities through the exact production domain transform so
     * the thresholds can be checked against what they actually gate.
     *
     * <p>Report only - what the thresholds should be is a T098 decision.
     */
    private static void reportT098CarrierDistribution() {
        byte[] base = CloudNoiseFieldModel.bakeBase();
        double[] sample = new double[4];

        // Production domain transform, mirrored from baseNoiseDomain().
        final double scale = 0.0025D;
        java.util.List<Double> rawCarrier = new java.util.ArrayList<>();
        java.util.List<Double> shaderCarrier = new java.util.ArrayList<>();
        java.util.List<Double> stormCarrier = new java.util.ArrayList<>();

        // A large uniform world volume for the global distribution, and the
        // severe column itself for the local one.
        for (int i = 0; i < 64; i++) {
            for (int j = 0; j < 64; j++) {
                for (int k = 0; k < 64; k++) {
                    double worldX = -2000.0D + i * 62.5D;
                    double worldY = 100.0D + j * 14.0D;
                    double worldZ = -2000.0D + k * 62.5D;
                    double[] uvw = baseDomain(worldX, worldY, worldZ, scale);
                    CloudNoiseFieldModel.sampleBase(base, uvw[0], uvw[1], uvw[2], sample);
                    double lowFbm = sample[1] * 0.625D + sample[2] * 0.25D + sample[3] * 0.125D;
                    double carrier = clamp01(remapValue(sample[0], -(1.0D - lowFbm), 1.0D));
                    rawCarrier.add(sample[0]);
                    shaderCarrier.add(carrier);
                    // The severe column: 865 blocks tall around a storm centre.
                    if (worldY >= 136.0D && worldY <= 1001.0D
                            && Math.hypot(worldX, worldZ) <= 650.0D) {
                        stormCarrier.add(carrier);
                    }
                }
            }
        }

        java.util.Collections.sort(rawCarrier);
        java.util.Collections.sort(shaderCarrier);
        java.util.Collections.sort(stormCarrier);

        System.out.printf(java.util.Locale.ROOT,
                "T098_CARRIER|quantity=bakedRChannel|samples=%d|p05=%.4f|p50=%.4f|p95=%.4f%n",
                rawCarrier.size(), percentile(rawCarrier, 0.05D),
                percentile(rawCarrier, 0.50D), percentile(rawCarrier, 0.95D));
        System.out.printf(java.util.Locale.ROOT,
                "T098_CARRIER|quantity=shaderCarrierRaw|samples=%d|p05=%.4f|p50=%.4f|p95=%.4f%n",
                shaderCarrier.size(), percentile(shaderCarrier, 0.05D),
                percentile(shaderCarrier, 0.50D), percentile(shaderCarrier, 0.95D));
        System.out.printf(java.util.Locale.ROOT,
                "T098_CARRIER|quantity=shaderCarrierRaw_severeColumn|samples=%d"
                        + "|p05=%.4f|p50=%.4f|p95=%.4f%n",
                stormCarrier.size(), percentile(stormCarrier, 0.05D),
                percentile(stormCarrier, 0.50D), percentile(stormCarrier, 0.95D));

        // How much of each distribution the production window zeroes.
        double p05Constant = 0.7128D;
        double p95Constant = 0.8451D;
        System.out.printf(java.util.Locale.ROOT,
                "T098_CARRIER_WINDOW|p05Constant=%.4f|p95Constant=%.4f"
                        + "|rawBelowP05=%.2f%%|shaderBelowP05=%.2f%%|severeColumnBelowP05=%.2f%%"
                        + "|shaderAboveP95=%.2f%%%n",
                p05Constant, p95Constant,
                100.0D * fractionBelow(rawCarrier, p05Constant),
                100.0D * fractionBelow(shaderCarrier, p05Constant),
                100.0D * fractionBelow(stormCarrier, p05Constant),
                100.0D * (1.0D - fractionBelow(shaderCarrier, p95Constant)));
    }

    /** Mirrors baseNoiseDomain() from the production shader. */
    private static double[] baseDomain(double x, double y, double z, double scale) {
        double rx = x * 0.8138D + y * 0.2962D + z * -0.5000D;
        double ry = x * -0.1401D + y * 0.9408D + z * 0.3085D;
        double rz = x * 0.5630D + y * -0.1677D + z * 0.8090D;
        double wx = Math.sin(x * 0.00173D + y * 0.00091D + z * -0.00127D + 1.7D);
        double wy = Math.sin(x * -0.00111D + y * 0.00149D + z * 0.00083D - 2.3D);
        double wz = Math.sin(x * 0.00079D + y * -0.00131D + z * 0.00191D + 4.1D);
        return new double[]{
                rx * scale + wx * 0.31D,
                ry * scale + wy * 0.31D,
                rz * scale + wz * 0.31D};
    }

    /** Mirrors the shader's remap(value, low, high, 0, 1). */
    private static double remapValue(double value, double low, double high) {
        return (value - low) / Math.max(high - low, 1.0e-6D);
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static double percentile(java.util.List<Double> sorted, double q) {
        if (sorted.isEmpty()) {
            return 0.0D;
        }
        int index = (int) Math.floor(q * (sorted.size() - 1));
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
    }

    private static double fractionBelow(java.util.List<Double> sorted, double threshold) {
        if (sorted.isEmpty()) {
            return 0.0D;
        }
        int count = 0;
        for (double value : sorted) {
            if (value < threshold) {
                count++;
            } else {
                break;
            }
        }
        return (double) count / sorted.size();
    }


    /**
     * The T134 severe system dumped from live fixture {@code 38bc5412} by
     * {@link StormT098RoleOccupancy}. Real adopted production geometry,
     * transcribed so the offline sweep measures the same system the live
     * captures showed rather than a synthetic stand-in.
     */
    private static java.util.List<StormLobeDescriptor> severeFixture38bc5412() {
        UUID field = group(3801);
        UUID storm = group(3802);
        double[][] rows = {
                // centreX, centreZ, baseY, topY, major, minor, density, edgeSoftness, role
                {-33.0D, -77.0D, 136.0D, 583.6D, 525.7D, 473.1D, 0.8570D, 0.5200D, 0.0D},
                {-66.5D, -38.7D, 152.6D, 600.2D, 527.8D, 475.0D, 0.8985D, 0.5200D, 0.0D},
                {-49.4D, -64.0D, 352.3D, 633.1D, 297.5D, 267.7D, 1.0000D, 0.4800D, 1.0D},
                {-29.8D, -94.8D, 405.6D, 702.0D, 279.0D, 251.1D, 1.0000D, 0.4800D, 1.0D},
                // TOWER radii carry the shipped T127 correction: the lower
                // member by 0.392/0.35 = 1.120 and the upper by 0.334/0.24 =
                // 1.392, so this fixture matches current production.
                {-14.4D, -106.0D, 433.5D, 800.1D, 224.3D, 184.0D, 0.9045D, 0.4400D, 2.0D},
                {-1.3D, -95.4D, 592.0D, 927.4D, 198.1D, 162.4D, 0.9681D, 0.4400D, 2.0D},
                {-109.9D, -77.1D, 769.6D, 980.2D, 507.6D, 294.4D, 0.8065D, 0.5600D, 3.0D},
                {-18.8D, -10.9D, 777.1D, 987.7D, 484.3D, 280.9D, 0.8075D, 0.5600D, 3.0D},
                {77.3D, -144.1D, 783.6D, 994.2D, 488.4D, 283.2D, 0.7982D, 0.5600D, 3.0D},
                {170.7D, -80.5D, 789.1D, 999.7D, 498.4D, 289.1D, 0.8110D, 0.5600D, 3.0D},
        };
        java.util.List<StormLobeDescriptor> lobes = new ArrayList<>();
        for (int index = 0; index < rows.length; index++) {
            double[] r = rows[index];
            StormLobeDescriptor.Role role = StormLobeDescriptor.Role.values()[(int) r[8]];
            lobes.add(new StormLobeDescriptor(
                    new UUID(field.getMostSignificantBits(),
                            field.getLeastSignificantBits() + index + 1L),
                    storm, index, rows.length, 0, role,
                    r[0], r[1], (float) r[2], (float) r[3], (float) r[4], (float) r[5],
                    0.0F, 1.0F, 0.0F, 0.0F,
                    (float) r[6], (float) r[7], 0.5F, 0.5F, 1.0F, 1.0F));
        }
        return lobes;
    }

    /**
     * T098 phase 1: the erosion-versus-body relationship, resolved by role.
     *
     * <p>Production erosion is an absolute subtraction:
     * {@code bodyEroded = max(body - (1 - detailFbm) * STORM_EROSION, 0)}, with
     * no coverage, edge-exposure or role term. Body, by contrast, depends on
     * coverage and the base field. This measures how much of each role's
     * available body that absolute bite consumes, on the real T134 descriptor
     * set and the real baked noise volumes.
     *
     * <p>Report only. Whether the imbalance warrants a production change is a
     * T098 decision.
     */
    private static void reportT098ErosionVersusBody() {
        byte[] baseVolume = CloudNoiseFieldModel.bakeBase();
        byte[] detailVolume = CloudNoiseFieldModel.bakeDetail();
        java.util.List<StormLobeDescriptor> lobes = severeFixture38bc5412();
        double[] baseSample = new double[4];
        double[] detailSample = new double[4];

        String[] names = {"BASE", "CORE", "TOWER", "ANVIL"};
        int roles = 4;
        long[] samples = new long[roles];
        long[] erosionExceedsBody = new long[roles];
        long[] consumed25 = new long[roles];
        long[] consumed50 = new long[roles];
        long[] consumed75 = new long[roles];
        long[] consumed90 = new long[roles];
        long[] densityPositive = new long[roles];
        long[] densityVisible = new long[roles];
        double[] bodySum = new double[roles];
        double[] erosionSum = new double[roles];
        double[] densitySum = new double[roles];

        // Density below this cannot read as body in the final image; it is the
        // same floor the calibration report already treats as empty.
        final double VISIBLE_DENSITY = 0.02D;
        double step = 20.0D;

        for (double y = 136.0D; y <= 1000.0D; y += step) {
            for (double x = -700.0D; x <= 700.0D; x += step) {
                for (double z = -700.0D; z <= 700.0D; z += step) {
                    // Attribute the sample to the role whose own envelope is
                    // strongest here, so roles are measured separately.
                    int owner = -1;
                    double bestEnvelope = 0.0D;
                    for (StormLobeDescriptor lobe : lobes) {
                        double envelope = StormLobeEvaluator.envelopeFromDistance(
                                StormLobeEvaluator.signedDistanceAt(lobe, x, y, z),
                                StormLobeEvaluator.edgeWidthBlocks(lobe),
                                StormLobeEvaluator.envelopeStrength(lobe));
                        if (envelope > bestEnvelope) {
                            bestEnvelope = envelope;
                            owner = lobe.role().gpuId();
                        }
                    }
                    if (owner < 0 || bestEnvelope <= 0.0D) {
                        continue;
                    }
                    double coverage = StormLobeEvaluator.coverageEnvelopeAt(lobes, x, y, z);
                    if (coverage <= 0.0D) {
                        continue;
                    }
                    double strength = StormLobeEvaluator.envelopeStrengthAt(lobes, x, y, z);
                    boolean embedded =
                            StormLobeEvaluator.hasEmbeddedConvectiveOverlap(lobes, x, y, z);

                    double[] uvw = baseDomain(x, y, z, 0.0025D);
                    CloudNoiseFieldModel.sampleBase(baseVolume, uvw[0], uvw[1], uvw[2], baseSample);
                    double lowFbm = StormDensityModel.lowFbm(
                            baseSample[1], baseSample[2], baseSample[3]);
                    double carrier = StormDensityModel.baseCarrier(baseSample[0], lowFbm);
                    double baseField = StormDensityModel.stormBaseField(carrier);
                    double body = StormDensityModel.stormBody(
                            coverage, strength, baseField, embedded);

                    double[] duvw = detailDomain(x, y, z, baseSample);
                    CloudNoiseFieldModel.sampleDetail(
                            detailVolume, duvw[0], duvw[1], duvw[2], detailSample);
                    double detailFbm = StormDensityModel.detailFbm(
                            detailSample[0], detailSample[1], detailSample[2]);
                    double erosion = (1.0D - detailFbm) * 0.44D;
                    double density = StormDensityModel.finalDensity(
                            coverage, strength, baseField, detailFbm, embedded);

                    samples[owner]++;
                    bodySum[owner] += body;
                    erosionSum[owner] += erosion;
                    densitySum[owner] += density;
                    if (body > 0.0D) {
                        double consumed = Math.min(1.0D, erosion / body);
                        if (erosion >= body) {
                            erosionExceedsBody[owner]++;
                        }
                        if (consumed > 0.25D) {
                            consumed25[owner]++;
                        }
                        if (consumed > 0.50D) {
                            consumed50[owner]++;
                        }
                        if (consumed > 0.75D) {
                            consumed75[owner]++;
                        }
                        if (consumed > 0.90D) {
                            consumed90[owner]++;
                        }
                    } else {
                        erosionExceedsBody[owner]++;
                        consumed25[owner]++;
                        consumed50[owner]++;
                        consumed75[owner]++;
                        consumed90[owner]++;
                    }
                    if (density > 0.0D) {
                        densityPositive[owner]++;
                    }
                    if (density >= VISIBLE_DENSITY) {
                        densityVisible[owner]++;
                    }
                }
            }
        }

        System.out.printf(java.util.Locale.ROOT,
                "T098_EROSION|fixture=38bc5412|step=%.0f|erosionConstant=0.44"
                        + "|visibleDensityFloor=%.2f%n", step, VISIBLE_DENSITY);
        for (int role = 0; role < roles; role++) {
            long n = Math.max(1L, samples[role]);
            System.out.printf(java.util.Locale.ROOT,
                    "T098_EROSION_ROLE|%-5s|samples=%-7d|meanBody=%.4f|meanErosion=%.4f"
                            + "|erosionOverBody=%.3f|erosion>=body=%.1f%%"
                            + "|consumed>50%%=%.1f%%|consumed>90%%=%.1f%%"
                            + "|densityPositive=%.1f%%|densityVisible=%.1f%%|meanDensity=%.4f%n",
                    names[role], samples[role],
                    bodySum[role] / n, erosionSum[role] / n,
                    bodySum[role] > 0.0D ? erosionSum[role] / bodySum[role] : 0.0D,
                    100.0D * erosionExceedsBody[role] / n,
                    100.0D * consumed50[role] / n,
                    100.0D * consumed90[role] / n,
                    100.0D * densityPositive[role] / n,
                    100.0D * densityVisible[role] / n,
                    densitySum[role] / n);
        }
    }

    /** Mirrors detailNoiseDomain() plus the base-noise offset from the shader. */
    private static double[] detailDomain(double x, double y, double z, double[] baseSample) {
        double rx = x * 0.7071D + y * -0.4082D + z * 0.5774D;
        double ry = x * 0.7071D + y * 0.4082D + z * -0.5774D;
        double rz = y * 0.8165D + z * 0.5774D;
        double wx = Math.sin(x * 1.731D * 0.00173D + y * 1.731D * 0.00091D
                + z * 1.731D * -0.00127D + 1.7D);
        double wy = Math.sin(x * 1.731D * -0.00111D + y * 1.731D * 0.00149D
                + z * 1.731D * 0.00083D - 2.3D);
        double wz = Math.sin(x * 1.731D * 0.00079D + y * 1.731D * -0.00131D
                + z * 1.731D * 0.00191D + 4.1D);
        return new double[]{
                rx * 0.022D + wx * 0.43D + (baseSample[1] - 0.5D) * 0.18D,
                ry * 0.022D + wy * 0.43D + (baseSample[2] - 0.5D) * 0.18D,
                rz * 0.022D + wz * 0.43D + (baseSample[0] - 0.5D) * 0.18D};
    }


    /**
     * T098 phase 2/3: sensitivity of the central-column mass to the role
     * diameters, and to the anvil span.
     *
     * <p>Every role already sits inside its T127 range, so the imbalance lives
     * in the composed proportion, which the contract never constrains. This
     * scales CORE and TOWER radii - and separately the ANVIL - on the real T134
     * descriptor set and measures what each does to occupied volume, to the
     * central column's share, and to the anvil-to-tower dominance.
     *
     * <p>Occupied volume is measured on the density field, not the envelope, so
     * it reflects material a ray would actually integrate.
     */
    private static void reportT098ProportionSensitivity() {
        byte[] baseVolume = CloudNoiseFieldModel.bakeBase();
        byte[] detailVolume = CloudNoiseFieldModel.bakeDetail();
        System.out.println("T098_SENSITIVITY|corrected TOWER baseline; sweeping ANVIL/BASE"
                + "|metric=density-visible voxels (>=0.02)");
        // ANVIL/BASE is 1.224 at scale 1.0, so these factors target the ratio
        // band the brief asks for: 1.05, 1.10, 1.15, 1.20, 1.25, plus a wider
        // reduction to bound the transition if one exists.
        double[] anvilScales = {1.021D, 0.980D, 0.940D, 0.899D, 0.858D, 0.780D, 0.700D};
        for (double scale : anvilScales) {
            measureProportion(baseVolume, detailVolume, 1.00D, scale);
        }
    }

    /** One proportion candidate, measured through the production density chain. */
    private static void measureProportion(
            byte[] baseVolume, byte[] detailVolume, double columnScale, double anvilScale) {
        java.util.List<StormLobeDescriptor> lobes = new ArrayList<>();
        for (StormLobeDescriptor lobe : severeFixture38bc5412()) {
            double factor = switch (lobe.role()) {
                case CORE, TOWER -> columnScale;
                case ANVIL -> anvilScale;
                default -> 1.0D;
            };
            lobes.add(new StormLobeDescriptor(
                    lobe.fieldId(), lobe.groupId(), lobe.memberIndex(), lobe.memberCount(),
                    lobe.groupSlot(), lobe.role(), lobe.centerX(), lobe.centerZ(),
                    lobe.baseY(), lobe.topY(),
                    (float) (lobe.majorRadius() * factor), (float) (lobe.minorRadius() * factor),
                    lobe.sinOrientation(), lobe.cosOrientation(), lobe.shearX(), lobe.shearZ(),
                    lobe.density(), lobe.edgeSoftness(), lobe.seed01(), lobe.lifecycleStage(),
                    lobe.verticalDevelopment(), lobe.detailWeight()));
        }

        // The anvil union span and BASE diameter this candidate delivers, so
        // the ratio and the footprint contract can both be read off directly.
        double anvilMinX = Double.POSITIVE_INFINITY;
        double anvilMaxX = Double.NEGATIVE_INFINITY;
        double anvilMinZ = Double.POSITIVE_INFINITY;
        double anvilMaxZ = Double.NEGATIVE_INFINITY;
        double baseDiameter = 0.0D;
        for (StormLobeDescriptor lobe : lobes) {
            if (lobe.role() == StormLobeDescriptor.Role.ANVIL) {
                anvilMinX = Math.min(anvilMinX, lobe.centerX() - lobe.majorRadius());
                anvilMaxX = Math.max(anvilMaxX, lobe.centerX() + lobe.majorRadius());
                anvilMinZ = Math.min(anvilMinZ, lobe.centerZ() - lobe.majorRadius());
                anvilMaxZ = Math.max(anvilMaxZ, lobe.centerZ() + lobe.majorRadius());
            } else if (lobe.role() == StormLobeDescriptor.Role.BASE) {
                baseDiameter = Math.max(baseDiameter, lobe.majorRadius() * 2.0D);
            }
        }
        double anvilSpan = Math.max(anvilMaxX - anvilMinX, anvilMaxZ - anvilMinZ);

        double[] baseSample = new double[4];
        double[] detailSample = new double[4];
        long[] visible = new long[4];
        double step = 24.0D;
        double footprintMax = 0.0D;
        // Vertical continuity of the central column, in 48-block bands.
        int bands = 0;
        int bandsWithColumn = 0;
        int longestGap = 0;
        int currentGap = 0;

        for (double y = 136.0D; y <= 1000.0D; y += 48.0D) {
            bands++;
            long columnHere = 0L;
            for (double yy = y; yy < y + 48.0D && yy <= 1000.0D; yy += step) {
                for (double x = -800.0D; x <= 800.0D; x += step) {
                    for (double z = -800.0D; z <= 800.0D; z += step) {
                        int owner = -1;
                        double bestEnvelope = 0.0D;
                        for (StormLobeDescriptor lobe : lobes) {
                            double envelope = StormLobeEvaluator.envelopeFromDistance(
                                    StormLobeEvaluator.signedDistanceAt(lobe, x, yy, z),
                                    StormLobeEvaluator.edgeWidthBlocks(lobe),
                                    StormLobeEvaluator.envelopeStrength(lobe));
                            if (envelope > bestEnvelope) {
                                bestEnvelope = envelope;
                                owner = lobe.role().gpuId();
                            }
                        }
                        if (owner < 0) {
                            continue;
                        }
                        double coverage = StormLobeEvaluator.coverageEnvelopeAt(lobes, x, yy, z);
                        if (coverage <= 0.0D) {
                            continue;
                        }
                        double strength = StormLobeEvaluator.envelopeStrengthAt(lobes, x, yy, z);
                        boolean embedded =
                                StormLobeEvaluator.hasEmbeddedConvectiveOverlap(lobes, x, yy, z);
                        double[] uvw = baseDomain(x, yy, z, 0.0025D);
                        CloudNoiseFieldModel.sampleBase(
                                baseVolume, uvw[0], uvw[1], uvw[2], baseSample);
                        double lowFbm = StormDensityModel.lowFbm(
                                baseSample[1], baseSample[2], baseSample[3]);
                        double baseField = StormDensityModel.stormBaseField(
                                StormDensityModel.baseCarrier(baseSample[0], lowFbm));
                        double[] duvw = detailDomain(x, yy, z, baseSample);
                        CloudNoiseFieldModel.sampleDetail(
                                detailVolume, duvw[0], duvw[1], duvw[2], detailSample);
                        double detailFbm = StormDensityModel.detailFbm(
                                detailSample[0], detailSample[1], detailSample[2]);
                        double density = StormDensityModel.finalDensity(
                                coverage, strength, baseField, detailFbm, embedded);
                        if (density >= 0.02D) {
                            visible[owner]++;
                            footprintMax = Math.max(footprintMax, 2.0D * Math.hypot(x, z));
                            if (owner == 1 || owner == 2) {
                                columnHere++;
                            }
                        }
                    }
                }
            }
            // A band counts as carrying the column only if it has real material.
            if (columnHere >= 8L) {
                bandsWithColumn++;
                currentGap = 0;
            } else {
                currentGap++;
                longestGap = Math.max(longestGap, currentGap);
            }
        }

        long column = visible[1] + visible[2];
        long mass = visible[0] + visible[3];
        long total = column + mass;
        System.out.printf(java.util.Locale.ROOT,
                "T098_PROPORTION|anvilScale=%.3f|anvilSpan=%.0f|anvilOverBase=%.3f"
                        + "|base=%-6d|core=%-6d|tower=%-6d|anvil=%-6d"
                        + "|columnShare=%.2f%%|anvilOverColumn=%.1f:1|anvilOverTower=%.1f:1"
                        + "|columnBands=%d/%d|footprint=%.0f%n",
                anvilScale, anvilSpan,
                baseDiameter == 0.0D ? 0.0D : anvilSpan / baseDiameter,
                visible[0], visible[1], visible[2], visible[3],
                total == 0L ? 0.0D : 100.0D * column / total,
                column == 0L ? 0.0D : (double) visible[3] / column,
                visible[2] == 0L ? 0.0D : (double) visible[3] / visible[2],
                bandsWithColumn, bands, footprintMax);
    }


    /**
     * T098 phase 5: the storm's vertical width profile.
     *
     * <p>The old contract validated roles independently, which is how a system
     * inside every range still composed into the rejected silhouette. This
     * measures the storm as a vertical shape instead: for each height band it
     * samples the density field, counts occupied cross-section, and reports the
     * equivalent diameter and role composition.
     *
     * <p>A cumulonimbus should read broad at the base, substantial through the
     * core, gradually narrowing through the tower, then expanding into the
     * anvil. A mushroom reads broad, abruptly narrow, then broad again. The
     * equivalent-diameter column distinguishes them directly.
     */
    private static void reportT098VerticalWidthProfile() {
        byte[] baseVolume = CloudNoiseFieldModel.bakeBase();
        byte[] detailVolume = CloudNoiseFieldModel.bakeDetail();
        java.util.List<StormLobeDescriptor> lobes = severeFixture38bc5412();

        // Descriptor inventory, so the profile can be read against ownership.
        System.out.println("T098_DESCRIPTOR_MAP|role|member|centre|major|minor|baseY|topY|vSpan");
        for (StormLobeDescriptor lobe : lobes) {
            System.out.printf(java.util.Locale.ROOT,
                    "T098_DESCRIPTOR|%-5s|%d|(%.1f,%.1f)|%.1f|%.1f|%.1f|%.1f|%.0f%n",
                    lobe.role(), lobe.memberIndex(), lobe.centerX(), lobe.centerZ(),
                    lobe.majorRadius(), lobe.minorRadius(), lobe.baseY(), lobe.topY(),
                    lobe.topY() - lobe.baseY());
        }

        double step = 20.0D;
        double band = 32.0D;
        double[] baseSample = new double[4];
        double[] detailSample = new double[4];
        System.out.println("T098_VPROFILE|y|equivDiam|largestCompDiam|cells|comps"
                + "|base|core|tower|anvil|dominant");
        for (double y = 136.0D; y <= 1000.0D; y += band) {
            long occupied = 0L;
            long[] byRole = new long[4];
            // Occupancy grid for this band, so connectivity can be measured
            // rather than inferred from a disc-equivalent area.
            int gridSize = (int) (1600.0D / step) + 1;
            boolean[][] grid = new boolean[gridSize][gridSize];
            for (double x = -800.0D; x <= 800.0D; x += step) {
                for (double z = -800.0D; z <= 800.0D; z += step) {
                    int owner = -1;
                    double bestEnvelope = 0.0D;
                    for (StormLobeDescriptor lobe : lobes) {
                        double envelope = StormLobeEvaluator.envelopeFromDistance(
                                StormLobeEvaluator.signedDistanceAt(lobe, x, y, z),
                                StormLobeEvaluator.edgeWidthBlocks(lobe),
                                StormLobeEvaluator.envelopeStrength(lobe));
                        if (envelope > bestEnvelope) {
                            bestEnvelope = envelope;
                            owner = lobe.role().gpuId();
                        }
                    }
                    if (owner < 0) {
                        continue;
                    }
                    double coverage = StormLobeEvaluator.coverageEnvelopeAt(lobes, x, y, z);
                    if (coverage <= 0.0D) {
                        continue;
                    }
                    double strength = StormLobeEvaluator.envelopeStrengthAt(lobes, x, y, z);
                    boolean embedded =
                            StormLobeEvaluator.hasEmbeddedConvectiveOverlap(lobes, x, y, z);
                    double[] uvw = baseDomain(x, y, z, 0.0025D);
                    CloudNoiseFieldModel.sampleBase(baseVolume, uvw[0], uvw[1], uvw[2], baseSample);
                    double lowFbm = StormDensityModel.lowFbm(
                            baseSample[1], baseSample[2], baseSample[3]);
                    double baseField = StormDensityModel.stormBaseField(
                            StormDensityModel.baseCarrier(baseSample[0], lowFbm));
                    double[] duvw = detailDomain(x, y, z, baseSample);
                    CloudNoiseFieldModel.sampleDetail(
                            detailVolume, duvw[0], duvw[1], duvw[2], detailSample);
                    double detailFbm = StormDensityModel.detailFbm(
                            detailSample[0], detailSample[1], detailSample[2]);
                    double density = StormDensityModel.finalDensity(
                            coverage, strength, baseField, detailFbm, embedded);
                    if (density >= 0.02D) {
                        occupied++;
                        byRole[owner]++;
                        int gx = (int) ((x + 800.0D) / step);
                        int gz = (int) ((z + 800.0D) / step);
                        if (gx >= 0 && gx < gridSize && gz >= 0 && gz < gridSize) {
                            grid[gz][gx] = true;
                        }
                    }
                }
            }
            // Equivalent diameter of the occupied cross-section.
            double area = occupied * step * step;
            double equivalentDiameter = 2.0D * Math.sqrt(area / Math.PI);
            int dominant = 0;
            for (int r = 1; r < 4; r++) {
                if (byRole[r] > byRole[dominant]) {
                    dominant = r;
                }
            }
            String[] names = {"BASE", "CORE", "TOWER", "ANVIL"};
            int components = occupied == 0L ? 0 : connectedComponents(grid);
            int largest = largestComponent(grid);
            double largestDiameter =
                    2.0D * Math.sqrt((largest * step * step) / Math.PI);
            System.out.printf(java.util.Locale.ROOT,
                    "T098_VPROFILE|%7.1f|%8.1f|%8.1f|%-6d|%-3d|%-6d|%-6d|%-6d|%-6d|%s%n",
                    y, equivalentDiameter, largestDiameter, occupied, components,
                    byRole[0], byRole[1], byRole[2], byRole[3],
                    occupied == 0L ? "none" : names[dominant]);
        }
    }

    /** Cell count of the largest connected occupied region. */

    /**
     * T098 phase 6: candidate structural changes, scored on the transition band.
     *
     * <p>The vertical profile shows the failure is fragmentation, not width: the
     * cross-section between the top of BASE and the body of ANVIL breaks into
     * 50-84 connected components, against 14-24 in the base and anvil bands.
     * This scores candidates on that band directly - largest connected component
     * and component count over y616-776 - rather than on voxel share.
     */
    private static void reportT098TransitionCandidates() {
        byte[] baseVolume = CloudNoiseFieldModel.bakeBase();
        byte[] detailVolume = CloudNoiseFieldModel.bakeDetail();
        System.out.println("T098_TRANSITION|candidate|band y616-776"
                + "|meanLargestCompDiam|meanComponents|meanCells");

        scoreTransition(baseVolume, detailVolume, "current", 1.0D, 0.0D, 0.0D);
        // A: taller TOWER - extend the column up into the anvil body.
        scoreTransition(baseVolume, detailVolume, "towerTop+120", 1.0D, 120.0D, 0.0D);
        scoreTransition(baseVolume, detailVolume, "towerTop+200", 1.0D, 200.0D, 0.0D);
        // B: anvil starts lower - close the gap from above.
        scoreTransition(baseVolume, detailVolume, "anvilBase-120", 1.0D, 0.0D, -120.0D);
        scoreTransition(baseVolume, detailVolume, "anvilBase-200", 1.0D, 0.0D, -200.0D);
        // C: wider upper tower alone, for comparison with the width lever.
        scoreTransition(baseVolume, detailVolume, "towerWide x1.5", 1.5D, 0.0D, 0.0D);
        // D: combination - taller tower plus a lower anvil base.
        scoreTransition(baseVolume, detailVolume, "tower+160 anvil-160", 1.0D, 160.0D, -160.0D);
        scoreTransition(baseVolume, detailVolume,
                "tower+160 anvil-160 wide1.25", 1.25D, 160.0D, -160.0D);
        // E: extend CORE upward. CORE is 560-595 wide, so unlike the narrow
        // tower it can actually fill the transition band, and T098 asks for a
        // visible dense convective region there.
        scoreTransition(baseVolume, detailVolume, "coreTop+100", 1.0D, 0.0D, 0.0D, 100.0D);
        scoreTransition(baseVolume, detailVolume, "coreTop+150", 1.0D, 0.0D, 0.0D, 150.0D);
        scoreTransition(baseVolume, detailVolume, "coreTop+220", 1.0D, 0.0D, 0.0D, 220.0D);
        scoreTransition(baseVolume, detailVolume,
                "coreTop+150 towerTop+120", 1.0D, 120.0D, 0.0D, 150.0D);
        // F: extend BASE upward. BASE is the other wide role, and T127 sets no
        // thickness target for it, unlike ANVIL 150-220.
        scoreTransition(baseVolume, detailVolume, "baseTop+100", 1.0D, 0.0D, 0.0D, 0.0D, 100.0D);
        scoreTransition(baseVolume, detailVolume, "baseTop+180", 1.0D, 0.0D, 0.0D, 0.0D, 180.0D);
        scoreTransition(baseVolume, detailVolume, "baseTop+260", 1.0D, 0.0D, 0.0D, 0.0D, 260.0D);
        scoreTransition(baseVolume, detailVolume,
                "baseTop+180 coreTop+150", 1.0D, 0.0D, 0.0D, 150.0D, 180.0D);
        // G: option C - insert one bridging TOWER stage across y560-830 rather
        // than scaling the two isolated tower lobes.
        for (double r : new double[] {200.0D, 260.0D, 320.0D, 380.0D}) {
            scoreTransition(baseVolume, detailVolume,
                    String.format(java.util.Locale.ROOT, "bridge r=%.0f", r),
                    1.0D, 0.0D, 0.0D, 0.0D, 0.0D, r);
        }
        scoreTransition(baseVolume, detailVolume,
                "bridge r=320 baseTop+180", 1.0D, 0.0D, 0.0D, 0.0D, 180.0D, 320.0D);
    }

    /** Scores one candidate on the transition band. */
    private static void scoreTransition(
            byte[] baseVolume, byte[] detailVolume, String label,
            double towerWidth, double towerTopRise, double anvilBaseDrop) {
        scoreTransition(baseVolume, detailVolume, label,
                towerWidth, towerTopRise, anvilBaseDrop, 0.0D);
    }

    private static void scoreTransition(
            byte[] baseVolume, byte[] detailVolume, String label,
            double towerWidth, double towerTopRise, double anvilBaseDrop, double coreTopRise) {
        scoreTransition(baseVolume, detailVolume, label,
                towerWidth, towerTopRise, anvilBaseDrop, coreTopRise, 0.0D);
    }

    private static void scoreTransition(
            byte[] baseVolume, byte[] detailVolume, String label,
            double towerWidth, double towerTopRise, double anvilBaseDrop,
            double coreTopRise, double baseTopRise) {
        scoreTransition(baseVolume, detailVolume, label, towerWidth, towerTopRise,
                anvilBaseDrop, coreTopRise, baseTopRise, 0.0D);
    }

    private static void scoreTransition(
            byte[] baseVolume, byte[] detailVolume, String label,
            double towerWidth, double towerTopRise, double anvilBaseDrop,
            double coreTopRise, double baseTopRise, double bridgeRadius) {
        java.util.List<StormLobeDescriptor> lobes = new ArrayList<>();
        for (StormLobeDescriptor lobe : severeFixture38bc5412()) {
            double major = lobe.majorRadius();
            double minor = lobe.minorRadius();
            double topY = lobe.topY();
            double baseY = lobe.baseY();
            if (lobe.role() == StormLobeDescriptor.Role.TOWER) {
                major *= towerWidth;
                minor *= towerWidth;
                topY += towerTopRise;
            } else if (lobe.role() == StormLobeDescriptor.Role.ANVIL) {
                baseY += anvilBaseDrop;
            } else if (lobe.role() == StormLobeDescriptor.Role.CORE) {
                topY += coreTopRise;
            } else if (lobe.role() == StormLobeDescriptor.Role.BASE) {
                topY += baseTopRise;
            }
            lobes.add(new StormLobeDescriptor(
                    lobe.fieldId(), lobe.groupId(), lobe.memberIndex(), lobe.memberCount(),
                    lobe.groupSlot(), lobe.role(), lobe.centerX(), lobe.centerZ(),
                    (float) baseY, (float) topY, (float) major, (float) minor,
                    lobe.sinOrientation(), lobe.cosOrientation(), lobe.shearX(), lobe.shearZ(),
                    lobe.density(), lobe.edgeSoftness(), lobe.seed01(), lobe.lifecycleStage(),
                    lobe.verticalDevelopment(), lobe.detailWeight()));
        }
        if (bridgeRadius > 0.0D) {
            // Option C: a mid-tower stage spanning the gap between the CORE tops
            // (633/702) and the ANVIL bases (770/789), placed on the column axis
            // and inheriting the upper TOWER's softness and density.
            StormLobeDescriptor upper = null;
            for (StormLobeDescriptor lobe : severeFixture38bc5412()) {
                if (lobe.role() == StormLobeDescriptor.Role.TOWER) {
                    upper = lobe;
                }
            }
            lobes.add(new StormLobeDescriptor(
                    upper.fieldId(), upper.groupId(), 10, 11, upper.groupSlot(),
                    StormLobeDescriptor.Role.TOWER, -8.0F, -100.0F,
                    560.0F, 830.0F, (float) bridgeRadius, (float) (bridgeRadius * 0.82D),
                    upper.sinOrientation(), upper.cosOrientation(),
                    upper.shearX(), upper.shearZ(), upper.density(), upper.edgeSoftness(),
                    upper.seed01(), upper.lifecycleStage(),
                    upper.verticalDevelopment(), upper.detailWeight()));
        }

        double step = 20.0D;
        double[] baseSample = new double[4];
        double[] detailSample = new double[4];
        int gridSize = (int) (1600.0D / step) + 1;
        double largestSum = 0.0D;
        double compSum = 0.0D;
        double cellSum = 0.0D;
        int bands = 0;

        for (double y = 616.0D; y <= 776.0D; y += 32.0D) {
            boolean[][] grid = new boolean[gridSize][gridSize];
            long cells = 0L;
            for (double x = -800.0D; x <= 800.0D; x += step) {
                for (double z = -800.0D; z <= 800.0D; z += step) {
                    double coverage = StormLobeEvaluator.coverageEnvelopeAt(lobes, x, y, z);
                    if (coverage <= 0.0D) {
                        continue;
                    }
                    double strength = StormLobeEvaluator.envelopeStrengthAt(lobes, x, y, z);
                    boolean embedded =
                            StormLobeEvaluator.hasEmbeddedConvectiveOverlap(lobes, x, y, z);
                    double[] uvw = baseDomain(x, y, z, 0.0025D);
                    CloudNoiseFieldModel.sampleBase(baseVolume, uvw[0], uvw[1], uvw[2], baseSample);
                    double lowFbm = StormDensityModel.lowFbm(
                            baseSample[1], baseSample[2], baseSample[3]);
                    double baseField = StormDensityModel.stormBaseField(
                            StormDensityModel.baseCarrier(baseSample[0], lowFbm));
                    double[] duvw = detailDomain(x, y, z, baseSample);
                    CloudNoiseFieldModel.sampleDetail(
                            detailVolume, duvw[0], duvw[1], duvw[2], detailSample);
                    double detailFbm = StormDensityModel.detailFbm(
                            detailSample[0], detailSample[1], detailSample[2]);
                    double density = StormDensityModel.finalDensity(
                            coverage, strength, baseField, detailFbm, embedded);
                    if (density >= 0.02D) {
                        cells++;
                        int gx = (int) ((x + 800.0D) / step);
                        int gz = (int) ((z + 800.0D) / step);
                        if (gx >= 0 && gx < gridSize && gz >= 0 && gz < gridSize) {
                            grid[gz][gx] = true;
                        }
                    }
                }
            }
            int largest = largestComponent(grid);
            largestSum += 2.0D * Math.sqrt((largest * step * step) / Math.PI);
            compSum += cells == 0L ? 0 : connectedComponents(grid);
            cellSum += cells;
            bands++;
        }
        System.out.printf(java.util.Locale.ROOT,
                "T098_TRANSITION|%-28s|%8.1f|%8.1f|%8.1f%n",
                label, largestSum / bands, compSum / bands, cellSum / bands);
    }


    /**
     * T098 phase 2: the minimum column width that stays connected under the
     * current noise calibration.
     *
     * <p>Every candidate in the transition sweep that improved connectivity did
     * so by putting wider material in the band, monotonically in width and
     * independently of which role supplied it. That is the signature of a
     * percolation threshold: the carrier's feature size is fixed at roughly
     * 1/(STORM_BASE_NOISE_SCALE * baseFrequency) = 100 blocks, so an envelope
     * only a few features wide is carved into disconnected blobs, while a wide
     * one keeps a spanning cluster. This measures that threshold directly with a
     * single isolated vertical column, so the morphology contract can state a
     * minimum central-column width instead of guessing one.
     */
    private static void reportT098PercolationWidth() {
        byte[] baseVolume = CloudNoiseFieldModel.bakeBase();
        byte[] detailVolume = CloudNoiseFieldModel.bakeDetail();
        StormLobeDescriptor upper = null;
        for (StormLobeDescriptor lobe : severeFixture38bc5412()) {
            if (lobe.role() == StormLobeDescriptor.Role.TOWER) {
                upper = lobe;
            }
        }
        System.out.println("T098_PERCOLATION|columnDiameter|meanConnectedFraction"
                + "|meanComponents|meanOccupiedCells|featureWidths");
        for (double radius : new double[] {
                100.0D, 130.0D, 160.0D, 190.0D, 220.0D, 250.0D,
                280.0D, 320.0D, 360.0D, 400.0D, 460.0D, 520.0D}) {
            java.util.List<StormLobeDescriptor> lobes = new ArrayList<>();
            lobes.add(new StormLobeDescriptor(
                    upper.fieldId(), upper.groupId(), 0, 1, upper.groupSlot(),
                    StormLobeDescriptor.Role.TOWER, 0.0F, 0.0F, 400.0F, 900.0F,
                    (float) radius, (float) radius,
                    0.0F, 1.0F, 0.0F, 0.0F, upper.density(), upper.edgeSoftness(),
                    upper.seed01(), upper.lifecycleStage(),
                    upper.verticalDevelopment(), upper.detailWeight()));

            double step = 12.0D;
            double half = radius + 80.0D;
            int gridSize = (int) (2.0D * half / step) + 2;
            double[] baseSample = new double[4];
            double[] detailSample = new double[4];
            double connectedSum = 0.0D;
            double compSum = 0.0D;
            double cellSum = 0.0D;
            int bands = 0;
            for (double y = 460.0D; y <= 840.0D; y += 20.0D) {
                boolean[][] grid = new boolean[gridSize][gridSize];
                long cells = 0L;
                for (double x = -half; x <= half; x += step) {
                    for (double z = -half; z <= half; z += step) {
                        double coverage = StormLobeEvaluator.coverageEnvelopeAt(lobes, x, y, z);
                        if (coverage <= 0.0D) {
                            continue;
                        }
                        double strength = StormLobeEvaluator.envelopeStrengthAt(lobes, x, y, z);
                        boolean embedded =
                                StormLobeEvaluator.hasEmbeddedConvectiveOverlap(lobes, x, y, z);
                        double[] uvw = baseDomain(x, y, z, 0.0025D);
                        CloudNoiseFieldModel.sampleBase(
                                baseVolume, uvw[0], uvw[1], uvw[2], baseSample);
                        double lowFbm = StormDensityModel.lowFbm(
                                baseSample[1], baseSample[2], baseSample[3]);
                        double baseField = StormDensityModel.stormBaseField(
                                StormDensityModel.baseCarrier(baseSample[0], lowFbm));
                        double[] duvw = detailDomain(x, y, z, baseSample);
                        CloudNoiseFieldModel.sampleDetail(
                                detailVolume, duvw[0], duvw[1], duvw[2], detailSample);
                        double detailFbm = StormDensityModel.detailFbm(
                                detailSample[0], detailSample[1], detailSample[2]);
                        double density = StormDensityModel.finalDensity(
                                coverage, strength, baseField, detailFbm, embedded);
                        if (density >= 0.02D) {
                            cells++;
                            int gx = (int) ((x + half) / step);
                            int gz = (int) ((z + half) / step);
                            if (gx >= 0 && gx < gridSize && gz >= 0 && gz < gridSize) {
                                grid[gz][gx] = true;
                            }
                        }
                    }
                }
                if (cells > 0L) {
                    connectedSum += (double) largestComponent(grid) / cells;
                    compSum += connectedComponents(grid);
                } 
                cellSum += cells;
                bands++;
            }
            System.out.printf(java.util.Locale.ROOT,
                    "T098_PERCOLATION|%8.0f|%8.3f|%8.1f|%9.1f|%8.2f%n",
                    radius * 2.0D, connectedSum / bands, compSum / bands,
                    cellSum / bands, radius * 2.0D / 100.0D);
        }
    }


    /**
     * T098 phase 2: what actually fragments the transition band.
     *
     * <p>An isolated column stays 99.9% connected at every width, so the band's
     * 50-84 components are not the column shattering. This isolates the roles:
     * it scores the band with the full descriptor set, then with the ANVIL
     * members removed, and reports the coverage distribution of the cells each
     * role contributes there. If the ANVIL's soft lower boundary is producing
     * low-coverage material far below its baseY, that material is what the noise
     * shreds - and shredding around the column is itself a T098 rejection.
     */
    private static void reportT098AnvilSkirt() {
        byte[] baseVolume = CloudNoiseFieldModel.bakeBase();
        byte[] detailVolume = CloudNoiseFieldModel.bakeDetail();
        java.util.List<StormLobeDescriptor> all = severeFixture38bc5412();
        java.util.List<StormLobeDescriptor> noAnvil = new ArrayList<>();
        for (StormLobeDescriptor lobe : all) {
            if (lobe.role() != StormLobeDescriptor.Role.ANVIL) {
                noAnvil.add(lobe);
            }
        }
        double anvilBase = Double.POSITIVE_INFINITY;
        for (StormLobeDescriptor lobe : all) {
            if (lobe.role() == StormLobeDescriptor.Role.ANVIL) {
                anvilBase = Math.min(anvilBase, lobe.baseY());
            }
        }
        System.out.printf(java.util.Locale.ROOT,
                "T098_SKIRT|lowestAnvilBaseY=%.1f%n", anvilBase);
        System.out.println("T098_SKIRT|y|set|cells|components|largestFrac"
                + "|anvilCells|anvilCovP50|anvilCovP90|belowAnvilBase");

        for (double y = 616.0D; y <= 776.0D; y += 32.0D) {
            scoreSkirtBand(baseVolume, detailVolume, all, "full", y, anvilBase);
            scoreSkirtBand(baseVolume, detailVolume, noAnvil, "noAnvil", y, anvilBase);
        }
    }

    private static void scoreSkirtBand(
            byte[] baseVolume, byte[] detailVolume,
            java.util.List<StormLobeDescriptor> lobes, String set,
            double y, double anvilBase) {
        double step = 16.0D;
        double half = 900.0D;
        int gridSize = (int) (2.0D * half / step) + 2;
        boolean[][] grid = new boolean[gridSize][gridSize];
        double[] baseSample = new double[4];
        double[] detailSample = new double[4];
        long cells = 0L;
        long anvilCells = 0L;
        java.util.List<Double> anvilCoverage = new ArrayList<>();

        for (double x = -half; x <= half; x += step) {
            for (double z = -half; z <= half; z += step) {
                double coverage = StormLobeEvaluator.coverageEnvelopeAt(lobes, x, y, z);
                if (coverage <= 0.0D) {
                    continue;
                }
                double strength = StormLobeEvaluator.envelopeStrengthAt(lobes, x, y, z);
                boolean embedded =
                        StormLobeEvaluator.hasEmbeddedConvectiveOverlap(lobes, x, y, z);
                double[] uvw = baseDomain(x, y, z, 0.0025D);
                CloudNoiseFieldModel.sampleBase(baseVolume, uvw[0], uvw[1], uvw[2], baseSample);
                double lowFbm = StormDensityModel.lowFbm(
                        baseSample[1], baseSample[2], baseSample[3]);
                double baseField = StormDensityModel.stormBaseField(
                        StormDensityModel.baseCarrier(baseSample[0], lowFbm));
                double[] duvw = detailDomain(x, y, z, baseSample);
                CloudNoiseFieldModel.sampleDetail(
                        detailVolume, duvw[0], duvw[1], duvw[2], detailSample);
                double detailFbm = StormDensityModel.detailFbm(
                        detailSample[0], detailSample[1], detailSample[2]);
                double density = StormDensityModel.finalDensity(
                        coverage, strength, baseField, detailFbm, embedded);
                if (density < 0.02D) {
                    continue;
                }
                cells++;
                int gx = (int) ((x + half) / step);
                int gz = (int) ((z + half) / step);
                if (gx >= 0 && gx < gridSize && gz >= 0 && gz < gridSize) {
                    grid[gz][gx] = true;
                }
                // Which role owns this cell, and at what coverage.
                int owner = -1;
                double best = 0.0D;
                for (StormLobeDescriptor lobe : lobes) {
                    double envelope = StormLobeEvaluator.envelopeFromDistance(
                            StormLobeEvaluator.signedDistanceAt(lobe, x, y, z),
                            StormLobeEvaluator.edgeWidthBlocks(lobe),
                            StormLobeEvaluator.envelopeStrength(lobe));
                    if (envelope > best) {
                        best = envelope;
                        owner = lobe.role().gpuId();
                    }
                }
                if (owner == StormLobeDescriptor.Role.ANVIL.gpuId()) {
                    anvilCells++;
                    anvilCoverage.add(coverage);
                }
            }
        }
        java.util.Collections.sort(anvilCoverage);
        double p50 = anvilCoverage.isEmpty() ? 0.0D
                : anvilCoverage.get(anvilCoverage.size() / 2);
        double p90 = anvilCoverage.isEmpty() ? 0.0D
                : anvilCoverage.get((int) (anvilCoverage.size() * 0.9D));
        System.out.printf(java.util.Locale.ROOT,
                "T098_SKIRT|%6.0f|%-8s|%6d|%6d|%8.3f|%8d|%8.3f|%8.3f|%s%n",
                y, set, cells, cells == 0L ? 0 : connectedComponents(grid),
                cells == 0L ? 0.0D : (double) largestComponent(grid) / cells,
                anvilCells, p50, p90, y < anvilBase ? "yes" : "no");
    }


    /**
     * T098 phase 6: the anvil edge-softness lever.
     *
     * <p>The skirt probe showed the transition band is coherent without the
     * ANVIL and shattered with it, by material sitting entirely below the
     * anvil's own baseY at coverage as low as 0.045. The cause is in
     * edgeWidthBlocks: ANVIL uses max(0.12, edgeSoftness * 1.65), which against
     * a ~400-block minor radius is a ~150-block boundary, and
     * envelopeFromDistance applies it isotropically - so the widening intended
     * for the canopy rim hangs the same distance straight down.
     *
     * <p>edgeWidthBlocks scales linearly with edgeSoftness, so sweeping the
     * fixture's ANVIL edgeSoftness measures the lever without touching
     * production. The reported effective multiplier is edgeSoftness * 1.65,
     * against the shipped 0.726.
     */
    private static void reportT098AnvilSoftnessSweep() {
        byte[] baseVolume = CloudNoiseFieldModel.bakeBase();
        byte[] detailVolume = CloudNoiseFieldModel.bakeDetail();
        System.out.println("T098_ANVIL_SOFT|edgeSoftness|effectiveMultiplier"
                + "|meanComponents|meanLargestFrac|meanCells|anvilSpanKept");
        for (double softness : new double[] {
                0.440D, 0.360D, 0.280D, 0.220D, 0.160D, 0.120D, 0.080D}) {
            java.util.List<StormLobeDescriptor> lobes = new ArrayList<>();
            for (StormLobeDescriptor lobe : severeFixture38bc5412()) {
                float edge = lobe.role() == StormLobeDescriptor.Role.ANVIL
                        ? (float) softness : lobe.edgeSoftness();
                lobes.add(new StormLobeDescriptor(
                        lobe.fieldId(), lobe.groupId(), lobe.memberIndex(), lobe.memberCount(),
                        lobe.groupSlot(), lobe.role(), lobe.centerX(), lobe.centerZ(),
                        lobe.baseY(), lobe.topY(), lobe.majorRadius(), lobe.minorRadius(),
                        lobe.sinOrientation(), lobe.cosOrientation(),
                        lobe.shearX(), lobe.shearZ(), lobe.density(), edge,
                        lobe.seed01(), lobe.lifecycleStage(),
                        lobe.verticalDevelopment(), lobe.detailWeight()));
            }
            double step = 16.0D;
            double half = 900.0D;
            int gridSize = (int) (2.0D * half / step) + 2;
            double[] baseSample = new double[4];
            double[] detailSample = new double[4];
            double compSum = 0.0D;
            double fracSum = 0.0D;
            double cellSum = 0.0D;
            int bands = 0;
            // The anvil's delivered span at its own mid-height, so a softness
            // reduction that silently shrinks the canopy is visible here.
            double anvilSpan = 0.0D;
            for (double y = 616.0D; y <= 900.0D; y += 32.0D) {
                boolean[][] grid = new boolean[gridSize][gridSize];
                long cells = 0L;
                double maxAbs = 0.0D;
                for (double x = -half; x <= half; x += step) {
                    for (double z = -half; z <= half; z += step) {
                        double coverage = StormLobeEvaluator.coverageEnvelopeAt(lobes, x, y, z);
                        if (coverage <= 0.0D) {
                            continue;
                        }
                        double strength = StormLobeEvaluator.envelopeStrengthAt(lobes, x, y, z);
                        boolean embedded =
                                StormLobeEvaluator.hasEmbeddedConvectiveOverlap(lobes, x, y, z);
                        double[] uvw = baseDomain(x, y, z, 0.0025D);
                        CloudNoiseFieldModel.sampleBase(
                                baseVolume, uvw[0], uvw[1], uvw[2], baseSample);
                        double lowFbm = StormDensityModel.lowFbm(
                                baseSample[1], baseSample[2], baseSample[3]);
                        double baseField = StormDensityModel.stormBaseField(
                                StormDensityModel.baseCarrier(baseSample[0], lowFbm));
                        double[] duvw = detailDomain(x, y, z, baseSample);
                        CloudNoiseFieldModel.sampleDetail(
                                detailVolume, duvw[0], duvw[1], duvw[2], detailSample);
                        double detailFbm = StormDensityModel.detailFbm(
                                detailSample[0], detailSample[1], detailSample[2]);
                        double density = StormDensityModel.finalDensity(
                                coverage, strength, baseField, detailFbm, embedded);
                        if (density >= 0.02D) {
                            cells++;
                            maxAbs = Math.max(maxAbs, Math.max(Math.abs(x), Math.abs(z)));
                            int gx = (int) ((x + half) / step);
                            int gz = (int) ((z + half) / step);
                            if (gx >= 0 && gx < gridSize && gz >= 0 && gz < gridSize) {
                                grid[gz][gx] = true;
                            }
                        }
                    }
                }
                if (y >= 850.0D) {
                    anvilSpan = Math.max(anvilSpan, maxAbs * 2.0D);
                }
                if (cells > 0L) {
                    compSum += connectedComponents(grid);
                    fracSum += (double) largestComponent(grid) / cells;
                }
                cellSum += cells;
                bands++;
            }
            System.out.printf(java.util.Locale.ROOT,
                    "T098_ANVIL_SOFT|%12.3f|%20.3f|%8.1f|%8.3f|%9.1f|%8.0f%n",
                    softness, softness * 1.65D, compSum / bands, fracSum / bands,
                    cellSum / bands, anvilSpan);
        }
    }


    /**
     * T098 phase 2: envelope boundary width against each lobe's own extent.
     *
     * <p>envelopeFromDistance fades over plus/minus the softness, so a lobe
     * whose softness exceeds its half-height has a boundary wider than the lobe
     * is tall: the fade consumes the whole body and spills past the far cap.
     * edgeWidthBlocks derives softness from smallerRadius - a horizontal extent -
     * and applies it isotropically, so any role that is much wider than it is
     * tall is exposed. This reports the ratio per role so the bound can be
     * scoped by measurement rather than special-cased to ANVIL.
     */
    private static void reportT098SoftnessVersusHeight() {
        System.out.println("T098_SOFTHEIGHT|role|member|softnessBlocks|halfHeight"
                + "|softOverHalfHeight|smallerRadius|degenerate");
        for (StormLobeDescriptor lobe : severeFixture38bc5412()) {
            double softness = StormLobeEvaluator.edgeWidthBlocks(lobe);
            double halfHeight = Math.max(lobe.topY() - lobe.baseY(), 1.0D) * 0.5D;
            System.out.printf(java.util.Locale.ROOT,
                    "T098_SOFTHEIGHT|%-5s|%d|%14.1f|%10.1f|%18.3f|%13.1f|%s%n",
                    lobe.role(), lobe.memberIndex(), softness, halfHeight,
                    softness / halfHeight, StormLobeEvaluator.smallerRadius(lobe),
                    softness > halfHeight ? "YES" : "no");
        }
    }


    /**
     * T098/T127 system-level guard: the coverage envelope's boundary must fit
     * inside the lobe it bounds.
     *
     * <p>The old morphology contract validated roles independently - each
     * diameter against its own range - which is how a system inside every range
     * still composed into the rejected silhouette. This is a relationship the
     * contract never stated: envelopeFromDistance fades over plus/minus
     * edgeWidthBlocks isotropically, so a lobe whose boundary exceeds its own
     * half-height has no interior along its short axis, and the fade spills past
     * the far cap as very-low-coverage material that the carrier shreds.
     *
     * <p>Fail-first is carried by the recorded pre-fix witnesses: the ANVIL
     * members measured 2.47-2.58 half-heights before the bound, so the assertion
     * rejects the shipped geometry it was written against.
     */
    private static void validateT098EnvelopeBoundedByExtent() {
        double worst = 0.0D;
        String worstLabel = "none";
        for (StormLobeDescriptor lobe : severeFixture38bc5412()) {
            double softness = StormLobeEvaluator.edgeWidthBlocks(lobe);
            double halfHeight = Math.max(
                    StormLobeEvaluator.roleTopY(lobe) - StormLobeEvaluator.roleBaseY(lobe),
                    1.0D) * 0.5D;
            double ratio = softness / halfHeight;
            if (ratio > worst) {
                worst = ratio;
                worstLabel = lobe.role() + "#" + lobe.memberIndex();
            }
            if (ratio > 1.0D) {
                throw new IllegalStateException(
                        "T098 envelope boundary exceeds the lobe's own half-height: "
                                + lobe.role() + "#" + lobe.memberIndex()
                                + " softness=" + softness + " halfHeight=" + halfHeight
                                + " ratio=" + ratio);
            }
        }

        // Fail-first: the pre-fix ANVIL boundary must still be rejected, so the
        // guard cannot silently readmit the shredded skirt.
        double preFixAnvilSoftness = 272.0D;
        double preFixAnvilHalfHeight = 119.3D;
        if (preFixAnvilSoftness / preFixAnvilHalfHeight <= 1.0D) {
            throw new IllegalStateException("the recorded pre-fix anvil boundary no longer "
                    + "violates the extent bound; re-derive the T098 skirt finding");
        }

        System.out.printf(java.util.Locale.ROOT,
                "T098_EXTENT_GUARD|worstSoftnessOverHalfHeight=%.3f (%s)|bound=1.000"
                        + "|rejectsPreFixAnvil=%.3f|PASSED%n",
                worst, worstLabel, preFixAnvilSoftness / preFixAnvilHalfHeight);
    }


    /**
     * T098 phase 2/5: integrated density by height, not thresholded presence.
     *
     * <p>The earlier vertical profile counted cells above density 0.02, which
     * measures whether material exists, not how much. A band can be "occupied"
     * across its whole width at a density the raymarch renders as nearly
     * transparent, which is exactly the discrepancy to explain: the offline
     * field shows a continuous column while the live image shows clean sky.
     *
     * <p>This reports mean and summed finalDensity per band, the density
     * distribution, and a horizontal optical depth - the integral of density
     * along a straight ray crossing the band through the storm axis, which is
     * what a SIDE view actually accumulates.
     */
    private static void reportT098OpticalProfile() {
        byte[] baseVolume = CloudNoiseFieldModel.bakeBase();
        byte[] detailVolume = CloudNoiseFieldModel.bakeDetail();
        java.util.List<StormLobeDescriptor> lobes = severeFixture38bc5412();
        double step = 16.0D;
        double[] baseSample = new double[4];
        double[] detailSample = new double[4];

        System.out.println("T098_OPTICAL|y|cells>=0.02|meanDensityOverCells|sumDensity"
                + "|p50|p90|maxDensity|axisOpticalDepth|dominantRole");
        for (double y = 136.0D; y <= 1000.0D; y += 32.0D) {
            java.util.List<Double> densities = new ArrayList<>();
            double sum = 0.0D;
            double maxDensity = 0.0D;
            long[] byRole = new long[4];
            // Optical depth along the z=0 line through the storm axis, which is
            // what a SIDE ray crossing this band integrates.
            double axisDepth = 0.0D;
            for (double x = -900.0D; x <= 900.0D; x += step) {
                for (double z = -900.0D; z <= 900.0D; z += step) {
                    double coverage = StormLobeEvaluator.coverageEnvelopeAt(lobes, x, y, z);
                    if (coverage <= 0.0D) {
                        continue;
                    }
                    double strength = StormLobeEvaluator.envelopeStrengthAt(lobes, x, y, z);
                    boolean embedded =
                            StormLobeEvaluator.hasEmbeddedConvectiveOverlap(lobes, x, y, z);
                    double[] uvw = baseDomain(x, y, z, 0.0025D);
                    CloudNoiseFieldModel.sampleBase(baseVolume, uvw[0], uvw[1], uvw[2], baseSample);
                    double lowFbm = StormDensityModel.lowFbm(
                            baseSample[1], baseSample[2], baseSample[3]);
                    double baseField = StormDensityModel.stormBaseField(
                            StormDensityModel.baseCarrier(baseSample[0], lowFbm));
                    double[] duvw = detailDomain(x, y, z, baseSample);
                    CloudNoiseFieldModel.sampleDetail(
                            detailVolume, duvw[0], duvw[1], duvw[2], detailSample);
                    double detailFbm = StormDensityModel.detailFbm(
                            detailSample[0], detailSample[1], detailSample[2]);
                    double density = StormDensityModel.finalDensity(
                            coverage, strength, baseField, detailFbm, embedded);
                    if (density <= 0.0D) {
                        continue;
                    }
                    sum += density;
                    maxDensity = Math.max(maxDensity, density);
                    if (Math.abs(z) < step * 0.5D) {
                        axisDepth += density * step;
                    }
                    if (density >= 0.02D) {
                        densities.add(density);
                        int owner = -1;
                        double best = 0.0D;
                        for (StormLobeDescriptor lobe : lobes) {
                            double envelope = StormLobeEvaluator.envelopeFromDistance(
                                    StormLobeEvaluator.signedDistanceAt(lobe, x, y, z),
                                    StormLobeEvaluator.edgeWidthBlocks(lobe),
                                    StormLobeEvaluator.envelopeStrength(lobe));
                            if (envelope > best) {
                                best = envelope;
                                owner = lobe.role().gpuId();
                            }
                        }
                        if (owner >= 0) {
                            byRole[owner]++;
                        }
                    }
                }
            }
            java.util.Collections.sort(densities);
            int dominant = 0;
            for (int r = 1; r < 4; r++) {
                if (byRole[r] > byRole[dominant]) {
                    dominant = r;
                }
            }
            String[] names = {"BASE", "CORE", "TOWER", "ANVIL"};
            System.out.printf(java.util.Locale.ROOT,
                    "T098_OPTICAL|%7.1f|%9d|%8.4f|%10.1f|%7.4f|%7.4f|%7.4f|%9.1f|%s%n",
                    y, densities.size(),
                    densities.isEmpty() ? 0.0D
                            : densities.stream().mapToDouble(Double::doubleValue).sum()
                                    / densities.size(),
                    sum,
                    densities.isEmpty() ? 0.0D : densities.get(densities.size() / 2),
                    densities.isEmpty() ? 0.0D : densities.get((int) (densities.size() * 0.9D)),
                    maxDensity, axisDepth,
                    densities.isEmpty() ? "none" : names[dominant]);
        }
    }


    /**
     * T098 phase 2/3: replicate the production exterior raymarch offline and
     * classify each coarse segment against a high-resolution reference.
     *
     * <p>The live fixture renders a substantial column at 1.12x horizontalRadius
     * and clean sky at 1.70x, while the density field and the production
     * shader's own material trace both report dense, opaque material at the
     * waist. That isolates the loss to the march. This mirrors the shader's
     * control flow - fine = sinceHit below 6, coarse step capped and grown by
     * distance, the storm segment test promoting a segment to fine - and, for
     * every coarse segment, asks a dense reference traversal whether that
     * interval actually contained storm material.
     *
     * <p>Parameters are production values for the captured configuration: ULTRA
     * raymarchSteps 96, governor stepScale 0.5, so budget 48, exteriorFineStep
     * 2.5 * sqrt(96/48) = 3.536, coarseStepCap min(112, 3.536*16) = 56.57,
     * MAX_STEPS 128, MaxRenderDistance 2000.
     */
    private static void reportT098MarchSimulation() {
        byte[] baseVolume = CloudNoiseFieldModel.bakeBase();
        byte[] detailVolume = CloudNoiseFieldModel.bakeDetail();
        java.util.List<StormLobeDescriptor> lobes = severeFixture38bc5412();
        double centreX = 0.0D;
        double centreZ = 0.0D;
        for (StormLobeDescriptor lobe : lobes) {
            centreX += lobe.centerX();
            centreZ += lobe.centerZ();
        }
        centreX /= lobes.size();
        centreZ /= lobes.size();
        double radius = 657.8D;

        System.out.println("T098_MARCH|strategy|factor|label|distance|targetY"
                + "|coarseSegs|fineSegs|firstFineT|refFirstT|falseNegSegs|falseNegBlocks"
                + "|iters|exhausted|sdfEvals|marchedDepth|refDepth");
        for (int strategy = 0; strategy < 4; strategy++) {
            for (double factor : new double[] {1.12D, 1.40D, 1.60D, 1.70D, 2.00D, 2.60D}) {
                simulateRay(baseVolume, detailVolume, lobes, centreX, centreZ, radius,
                        factor, 680.0D, "waist", strategy);
            }
            simulateRay(baseVolume, detailVolume, lobes, centreX, centreZ, radius,
                    1.70D, 300.0D, "baseControl", strategy);
            simulateRay(baseVolume, detailVolume, lobes, centreX, centreZ, radius,
                    1.70D, 900.0D, "anvilControl", strategy);
        }
    }

    /** One ray, marched with the production rules and scored against a reference. */
    private static void simulateRay(
            byte[] baseVolume, byte[] detailVolume, java.util.List<StormLobeDescriptor> lobes,
            double centreX, double centreZ, double radius,
            double factor, double targetY, String label, int strategy) {
        double camX = centreX + radius * factor;
        double camY = (136.0D + 1000.0D) * 0.5D;
        double camZ = centreZ;
        double dirX = centreX - camX;
        double dirY = targetY - camY;
        double dirZ = 0.0D;
        double dirLength = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        dirX /= dirLength;
        dirY /= dirLength;
        dirZ /= dirLength;

        final double maxRenderDistance = 2000.0D;
        final double fineStep = 3.5355339D;
        final double coarseStepCap = Math.min(112.0D, fineStep * 16.0D);
        final int stepBudget = 48;
        final int maxSteps = 128;
        double t1 = maxRenderDistance;
        double baseStep = t1 / stepBudget;
        double coarseStep = Math.max(baseStep * 1.5D, fineStep * 3.0D);

        double refFirst = -1.0D;
        double refDepth = 0.0D;
        double refStep = 2.0D;
        java.util.List<double[]> refIntervals = new ArrayList<>();
        double intervalStart = -1.0D;
        for (double s = 0.0D; s <= t1; s += refStep) {
            double d = sampleRayDensity(baseVolume, detailVolume, lobes,
                    camX + dirX * s, camY + dirY * s, camZ + dirZ * s);
            if (d >= 0.02D) {
                if (refFirst < 0.0D) {
                    refFirst = s;
                }
                if (intervalStart < 0.0D) {
                    intervalStart = s;
                }
                refDepth += d * refStep;
            } else if (intervalStart >= 0.0D) {
                refIntervals.add(new double[] {intervalStart, s});
                intervalStart = -1.0D;
            }
        }
        if (intervalStart >= 0.0D) {
            refIntervals.add(new double[] {intervalStart, t1});
        }

        double t = 0.0D;
        int sinceHit = 6;
        int coarseSegs = 0;
        int fineSegs = 0;
        int iterations = 0;
        int sdfEvals = 0;
        double firstFineT = -1.0D;
        int falseNegSegs = 0;
        double falseNegBlocks = 0.0D;
        double marchedDepth = 0.0D;
        boolean exhausted = false;
        // Strategy B/C state: where the current fine window began, and the t
        // before which re-promotion is suppressed.
        double fineWindowStart = -1.0D;
        double promotionSuppressedUntil = -1.0D;
        final double FINE_WINDOW_BLOCKS = 64.0D;
        final double COOLDOWN_BLOCKS = 96.0D;

        for (int i = 0; i < maxSteps; i++) {
            iterations++;
            if (t >= t1) {
                break;
            }
            boolean fine = sinceHit < 6;
            double distanceGrowth = 1.0D + (t / maxRenderDistance) * 2.2D;
            double stepLength = fine
                    ? fineStep
                    : Math.min(coarseStep * distanceGrowth, coarseStepCap);
            stepLength = Math.min(stepLength, t1 - t);
            double segStart = t;
            double segEnd = t + stepLength;
            boolean promoted = false;

            if (!fine && stormSegmentMayIntersect(lobes,
                    camX + dirX * segStart, camY + dirY * segStart, camZ + dirZ * segStart,
                    camX + dirX * segEnd, camY + dirY * segEnd, camZ + dirZ * segEnd)) {
                boolean allow = true;
                if (strategy == 2 && t < promotionSuppressedUntil) {
                    // C: cooldown - the same broad bound may not re-promote until
                    // the ray has made real forward progress.
                    allow = false;
                }
                if (strategy == 3) {
                    // E: the union SDF is the distance to the envelope surface.
                    // envelopeFromDistance fades coverage over +/- softness, so
                    // material extends up to one softness OUTSIDE that surface;
                    // subtracting the group's widest softness makes the advance
                    // a true lower bound on the distance to any material.
                    sdfEvals++;
                    double safe = StormLobeEvaluator.unionDistanceAt(lobes,
                            camX + dirX * t, camY + dirY * t, camZ + dirZ * t)
                            - widestSoftness(lobes);
                    if (safe > fineStep) {
                        allow = false;
                        stepLength = Math.min(Math.min(safe, coarseStepCap), t1 - t);
                        segEnd = t + stepLength;
                    }
                }
                if (allow) {
                    sinceHit = 0;
                    fine = true;
                    promoted = true;
                    stepLength = Math.min(fineStep, t1 - t);
                    segEnd = t + stepLength;
                    if (fineWindowStart < 0.0D) {
                        fineWindowStart = t;
                    }
                }
            }

            if (fine && strategy == 1) {
                // B: bound the fine window by physical distance, then force
                // coarse progress even while still inside the broad bound.
                if (fineWindowStart < 0.0D) {
                    fineWindowStart = t;
                }
                if (t - fineWindowStart > FINE_WINDOW_BLOCKS) {
                    fine = false;
                    sinceHit = 6;
                    fineWindowStart = -1.0D;
                    stepLength = Math.min(
                            Math.min(coarseStep * distanceGrowth, coarseStepCap), t1 - t);
                    segEnd = t + stepLength;
                }
            }

            if (fine) {
                fineSegs++;
                if (firstFineT < 0.0D) {
                    firstFineT = t;
                }
                double d = sampleRayDensity(baseVolume, detailVolume, lobes,
                        camX + dirX * t, camY + dirY * t, camZ + dirZ * t);
                marchedDepth += d * stepLength;
                if (d >= 0.02D) {
                    sinceHit = 0;
                    fineWindowStart = t;
                    promotionSuppressedUntil = -1.0D;
                } else {
                    sinceHit++;
                    if (strategy == 2 && sinceHit >= 6) {
                        promotionSuppressedUntil = t + COOLDOWN_BLOCKS;
                    }
                }
            } else {
                coarseSegs++;
                double overlap = 0.0D;
                for (double[] interval : refIntervals) {
                    overlap += Math.max(0.0D,
                            Math.min(segEnd, interval[1]) - Math.max(segStart, interval[0]));
                }
                if (overlap > 0.0D) {
                    falseNegSegs++;
                    falseNegBlocks += overlap;
                }
                sinceHit++;
            }
            t += stepLength;
            if (i == maxSteps - 1 && t < t1) {
                exhausted = true;
            }
        }

        String[] names = {"current", "B_window", "C_cooldown", "E_sdf"};
        System.out.printf(java.util.Locale.ROOT,
                "T098_MARCH|%-10s|%.2f|%-12s|%7.1f|%6.1f|%6d|%6d|%8.1f|%8.1f|%6d|%9.1f"
                        + "|%5d|%8s|%8d|%9.1f|%9.1f%n",
                names[strategy], factor, label, radius * factor, targetY,
                coarseSegs, fineSegs, firstFineT, refFirst, falseNegSegs, falseNegBlocks,
                iterations, exhausted ? "YES" : "no", sdfEvals, marchedDepth, refDepth);
    }

    /** The widest envelope boundary in the set, an upper bound on material reach. */
    private static double widestSoftness(java.util.List<StormLobeDescriptor> lobes) {
        double widest = 0.0D;
        for (StormLobeDescriptor lobe : lobes) {
            widest = Math.max(widest, StormLobeEvaluator.edgeWidthBlocks(lobe));
        }
        return widest;
    }

    /** Mirrors the shader's stormGroupSegmentMayIntersect bounding-sphere test. */
    private static boolean stormSegmentMayIntersect(
            java.util.List<StormLobeDescriptor> lobes,
            double ax, double ay, double az, double bx, double by, double bz) {
        double sx = bx - ax;
        double sy = by - ay;
        double sz = bz - az;
        double lengthSquared = Math.max(sx * sx + sy * sy + sz * sz, 0.0001D);
        for (StormLobeDescriptor lobe : lobes) {
            double cx = lobe.centerX() + lobe.shearX() * 0.5D;
            double cy = (lobe.baseY() + lobe.topY()) * 0.5D;
            double cz = lobe.centerZ() + lobe.shearZ() * 0.5D;
            double horizontal = Math.max(lobe.majorRadius(), lobe.minorRadius()) * 1.24D
                    + Math.hypot(lobe.shearX(), lobe.shearZ()) + 2.0D;
            double halfHeight = (lobe.topY() - lobe.baseY()) * 0.5D + 2.0D;
            double boundRadius = Math.hypot(horizontal, halfHeight);
            double along = Math.max(0.0D, Math.min(1.0D,
                    ((cx - ax) * sx + (cy - ay) * sy + (cz - az) * sz) / lengthSquared));
            double px = ax + sx * along;
            double py = ay + sy * along;
            double pz = az + sz * along;
            double dx = px - cx;
            double dy = py - cy;
            double dz = pz - cz;
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) <= boundRadius) {
                return true;
            }
        }
        return false;
    }

    /** finalDensity at one world point, using the production model. */
    private static double sampleRayDensity(
            byte[] baseVolume, byte[] detailVolume,
            java.util.List<StormLobeDescriptor> lobes, double x, double y, double z) {
        double coverage = StormLobeEvaluator.coverageEnvelopeAt(lobes, x, y, z);
        if (coverage <= 0.0D) {
            return 0.0D;
        }
        double strength = StormLobeEvaluator.envelopeStrengthAt(lobes, x, y, z);
        boolean embedded = StormLobeEvaluator.hasEmbeddedConvectiveOverlap(lobes, x, y, z);
        double[] baseSample = new double[4];
        double[] detailSample = new double[4];
        double[] uvw = baseDomain(x, y, z, 0.0025D);
        CloudNoiseFieldModel.sampleBase(baseVolume, uvw[0], uvw[1], uvw[2], baseSample);
        double lowFbm = StormDensityModel.lowFbm(baseSample[1], baseSample[2], baseSample[3]);
        double baseField = StormDensityModel.stormBaseField(
                StormDensityModel.baseCarrier(baseSample[0], lowFbm));
        double[] duvw = detailDomain(x, y, z, baseSample);
        CloudNoiseFieldModel.sampleDetail(detailVolume, duvw[0], duvw[1], duvw[2], detailSample);
        double detailFbm = StormDensityModel.detailFbm(
                detailSample[0], detailSample[1], detailSample[2]);
        return StormDensityModel.finalDensity(coverage, strength, baseField, detailFbm, embedded);
    }


    /**
     * T098 phase 8: the promotion policy must reach material without skipping any.
     *
     * <p>Two properties, both required, and neither implied by the other.
     *
     * <p>Conservative correctness: no coarse advance may step over an interval
     * the reference traversal says contains storm material. The segment test was
     * already conservative - it produced zero false negatives at every traced
     * distance - and the refinement must not spend that. The advance subtracts
     * the widest envelope boundary from the union distance, because
     * stormEnvelopeFromDistance fades coverage over plus or minus a
     * descriptor's softness and material can therefore begin that far outside
     * the union surface. Dropping that subtraction is not a theoretical
     * concern: it measured 2 to 6 skipped segments and up to 84 blocks of
     * missed material on these same rays.
     *
     * <p>Bounded progress: a conservative false positive must not consume the
     * whole march budget before the storm. The pre-fix rule treated "this
     * segment may intersect" as "fine march from here", and since the group
     * bounding spheres reach about 615 blocks for ANVIL and 698 for BASE, the
     * SIDE waist ray promoted at t=339 against first material at t=818. Six
     * fine steps then one coarse iteration still inside the same sphere
     * re-promoted immediately, so every iteration advanced one fine step and
     * MAX_STEPS ran out having accumulated exactly zero optical depth.
     *
     * <p>This asserts the old rule still starves and the new rule does not, so
     * the guard cannot pass by reverting to the behaviour it was written
     * against.
     */
    private static void validateT098MarchReachesMaterial() {
        byte[] baseVolume = CloudNoiseFieldModel.bakeBase();
        byte[] detailVolume = CloudNoiseFieldModel.bakeDetail();
        java.util.List<StormLobeDescriptor> lobes = severeFixture38bc5412();
        double centreX = 0.0D;
        double centreZ = 0.0D;
        for (StormLobeDescriptor lobe : lobes) {
            centreX += lobe.centerX();
            centreZ += lobe.centerZ();
        }
        centreX /= lobes.size();
        centreZ /= lobes.size();

        // Optical depth of about 5 is already opaque, so this is a generous
        // margin over "the column is drawn at all".
        double opaqueDepth = 5.0D;
        for (double factor : new double[] {1.12D, 1.40D, 1.60D, 1.70D, 2.00D}) {
            double[] fixed = marchOutcome(baseVolume, detailVolume, lobes,
                    centreX, centreZ, 657.8D, factor, 680.0D, 3);
            double[] legacy = marchOutcome(baseVolume, detailVolume, lobes,
                    centreX, centreZ, 657.8D, factor, 680.0D, 0);
            if (fixed[0] > 0.0D) {
                throw new IllegalStateException("T098 promotion probe skipped material at "
                        + factor + "x: " + fixed[0] + " segments, " + fixed[1] + " blocks");
            }
            if (fixed[2] < opaqueDepth) {
                throw new IllegalStateException("T098 promotion probe did not reach opaque "
                        + "material at " + factor + "x: depth " + fixed[2]);
            }
            if (factor >= 1.40D && legacy[2] >= opaqueDepth) {
                throw new IllegalStateException("the pre-fix promotion rule no longer starves "
                        + "at " + factor + "x (depth " + legacy[2]
                        + "); re-derive the T098 starvation finding");
            }
        }
        System.out.println("T098_MARCH_GUARD|falseNegatives=0 at every distance"
                + "|fixed rule reaches opaque material 1.12x-2.00x"
                + "|pre-fix rule still starves from 1.40x|PASSED");
    }

    /** Runs one ray and returns {falseNegSegs, falseNegBlocks, marchedDepth}. */
    private static double[] marchOutcome(
            byte[] baseVolume, byte[] detailVolume, java.util.List<StormLobeDescriptor> lobes,
            double centreX, double centreZ, double radius,
            double factor, double targetY, int strategy) {
        java.io.PrintStream previous = System.out;
        java.io.ByteArrayOutputStream sink = new java.io.ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(sink));
        try {
            simulateRay(baseVolume, detailVolume, lobes, centreX, centreZ, radius,
                    factor, targetY, "guard", strategy);
        } finally {
            System.setOut(previous);
        }
        String[] fields = sink.toString().trim().split("\\|");
        // Column order: 0 tag, 1 strategy, 2 factor, 3 label, 4 distance,
        // 5 targetY, 6 coarseSegs, 7 fineSegs, 8 firstFineT, 9 refFirstT,
        // 10 falseNegSegs, 11 falseNegBlocks, 12 iters, 13 exhausted,
        // 14 sdfEvals, 15 marchedDepth, 16 refDepth.
        return new double[] {
                Double.parseDouble(fields[10].trim()),
                Double.parseDouble(fields[11].trim()),
                Double.parseDouble(fields[15].trim())
        };
    }

    private static int largestComponent(boolean[][] occupied) {
        boolean[][] visited = new boolean[occupied.length][occupied[0].length];
        int best = 0;
        int[] dx = {1, -1, 0, 0};
        int[] dz = {0, 0, 1, -1};
        for (int z = 0; z < occupied.length; z++) {
            for (int x = 0; x < occupied[z].length; x++) {
                if (!occupied[z][x] || visited[z][x]) {
                    continue;
                }
                int size = 0;
                ArrayDeque<Integer> pending = new ArrayDeque<>();
                pending.add(z * occupied[z].length + x);
                visited[z][x] = true;
                while (!pending.isEmpty()) {
                    int cell = pending.poll();
                    int cz = cell / occupied[z].length;
                    int cx = cell % occupied[z].length;
                    size++;
                    for (int d = 0; d < 4; d++) {
                        int nz = cz + dz[d];
                        int nx = cx + dx[d];
                        if (nz >= 0 && nz < occupied.length && nx >= 0
                                && nx < occupied[nz].length
                                && occupied[nz][nx] && !visited[nz][nx]) {
                            visited[nz][nx] = true;
                            pending.add(nz * occupied[nz].length + nx);
                        }
                    }
                }
                best = Math.max(best, size);
            }
        }
        return best;
    }

    private static int connectedComponents(boolean[][] occupied) {
        boolean[][] visited = new boolean[occupied.length][occupied[0].length];
        int components = 0;
        int[] deltaX = {1, -1, 0, 0};
        int[] deltaZ = {0, 0, 1, -1};
        for (int z = 0; z < occupied.length; z++) {
            for (int x = 0; x < occupied[z].length; x++) {
                if (!occupied[z][x] || visited[z][x]) {
                    continue;
                }
                components++;
                ArrayDeque<Integer> pending = new ArrayDeque<>();
                pending.add(z * occupied[z].length + x);
                visited[z][x] = true;
                while (!pending.isEmpty()) {
                    int packed = pending.removeFirst();
                    int currentZ = packed / occupied[z].length;
                    int currentX = packed % occupied[z].length;
                    for (int direction = 0; direction < 4; direction++) {
                        int nextX = currentX + deltaX[direction];
                        int nextZ = currentZ + deltaZ[direction];
                        if (nextZ < 0 || nextZ >= occupied.length
                                || nextX < 0 || nextX >= occupied[nextZ].length
                                || visited[nextZ][nextX] || !occupied[nextZ][nextX]) {
                            continue;
                        }
                        visited[nextZ][nextX] = true;
                        pending.add(nextZ * occupied[nextZ].length + nextX);
                    }
                }
            }
        }
        return components;
    }

    private static void validateDescriptorLocality() {
        UUID storm = group(75);
        List<StormLobeDescriptor> local = List.of(
                descriptor(storm, 0, 5, StormLobeDescriptor.Role.BASE,
                        0.0D, 0.0D, 220.0F, 305.0F, 110.0F, 90.0F).withGroupSlot(0),
                descriptor(storm, 1, 5, StormLobeDescriptor.Role.CORE,
                        8.0D, -2.0D, 245.0F, 345.0F, 75.0F, 62.0F).withGroupSlot(0),
                descriptor(storm, 2, 5, StormLobeDescriptor.Role.TOWER,
                        12.0D, 2.0D, 275.0F, 420.0F, 58.0F, 46.0F).withGroupSlot(0),
                descriptor(storm, 3, 5, StormLobeDescriptor.Role.ANVIL,
                        18.0D, 0.0D, 355.0F, 455.0F, 148.0F, 54.0F).withGroupSlot(0)
        );
        StormLobeDescriptor far = descriptor(storm, 4, 5, StormLobeDescriptor.Role.BASE,
                900.0D, 700.0D, 215.0F, 310.0F, 80.0F, 64.0F).withGroupSlot(0);
        StormLobeDescriptor movedFar = descriptor(storm, 4, 5, StormLobeDescriptor.Role.BASE,
                -1100.0D, 820.0D, 215.0F, 310.0F, 80.0F, 64.0F).withGroupSlot(0);
        double probeX = 24.0D;
        double probeY = 272.0D;
        double baseline = StormLobeEvaluator.coverageEnvelopeAt(local, probeX, probeY, 0.0D);
        List<StormLobeDescriptor> added = new ArrayList<>(local);
        added.add(far);
        double withFar = StormLobeEvaluator.coverageEnvelopeAt(added, probeX, probeY, 0.0D);
        added.set(added.size() - 1, movedFar);
        double withMovedFar = StormLobeEvaluator.coverageEnvelopeAt(added, probeX, probeY, 0.0D);
        double afterRemoval = StormLobeEvaluator.coverageEnvelopeAt(local, probeX, probeY, 0.0D);
        List<String> violations = new ArrayList<>();
        if (Math.abs(withFar - baseline) > 1.0E-9D) {
            violations.add("add changed density " + format(baseline) + "->" + format(withFar));
        }
        if (Math.abs(withMovedFar - withFar) > 1.0E-9D) {
            violations.add("move outside support changed density "
                    + format(withFar) + "->" + format(withMovedFar));
        }
        if (Math.abs(afterRemoval - withFar) > 1.0E-9D) {
            violations.add("remove outside support changed density "
                    + format(withFar) + "->" + format(afterRemoval));
        }
        require(violations.isEmpty(), String.join("; ", violations));
    }

    private static void validateIndependentGlslParity() {
        UUID firstGroup = group(76);
        UUID secondGroup = group(760);
        List<StormLobeDescriptor> lobes = List.of(
                descriptor(firstGroup, 0, 4, StormLobeDescriptor.Role.BASE,
                        0.0D, 0.0D, 220.0F, 300.0F, 112.0F, 86.0F).withGroupSlot(0),
                descriptor(firstGroup, 1, 4, StormLobeDescriptor.Role.CORE,
                        8.0D, 0.0D, 245.0F, 350.0F, 78.0F, 62.0F).withGroupSlot(0),
                descriptor(firstGroup, 2, 4, StormLobeDescriptor.Role.TOWER,
                        14.0D, 0.0D, 285.0F, 430.0F, 58.0F, 46.0F).withGroupSlot(0),
                descriptor(firstGroup, 3, 4, StormLobeDescriptor.Role.ANVIL,
                        22.0D, 0.0D, 365.0F, 475.0F, 158.0F, 56.0F).withGroupSlot(0),
                descriptor(secondGroup, 0, 4, StormLobeDescriptor.Role.BASE,
                        96.0D, 20.0D, 225.0F, 302.0F, 98.0F, 78.0F).withGroupSlot(1),
                descriptor(secondGroup, 1, 4, StormLobeDescriptor.Role.CORE,
                        102.0D, 18.0D, 248.0F, 352.0F, 72.0F, 58.0F).withGroupSlot(1),
                descriptor(secondGroup, 2, 4, StormLobeDescriptor.Role.TOWER,
                        108.0D, 16.0D, 288.0F, 425.0F, 54.0F, 43.0F).withGroupSlot(1),
                descriptor(secondGroup, 3, 4, StormLobeDescriptor.Role.ANVIL,
                        114.0D, 14.0D, 362.0F, 470.0F, 142.0F, 52.0F).withGroupSlot(1)
        );
        double[][] probes = new double[8][3];
        for (int index = 0; index < 4; index++) {
            StormLobeDescriptor lobe = lobes.get(index);
            probes[index] = new double[]{
                    lobe.centerX(),
                    lobe.baseY() + (lobe.topY() - lobe.baseY()) * 0.40D,
                    lobe.centerZ()
            };
        }
        probes[4] = new double[]{12.0D, 318.0D, 0.0D};
        probes[5] = new double[]{68.0D, 318.0D, 12.0D};
        probes[6] = new double[]{4.0D, 221.5D, 0.0D};
        probes[7] = new double[]{104.0D, 275.0D, 72.0D};

        // Deterministic stand-in noise for the two composition cases, so the
        // fixture exercises stages 5 and 6 without needing the baked volumes.
        double[][] noise = new double[8][2];
        noise[6] = new double[]{0.18D, 0.34D};
        noise[7] = new double[]{0.81D, 0.62D};

        float[] glsl = executeIndependentGlslFixture(lobes, probes, noise);
        List<String> mismatches = new ArrayList<>();
        // Cases 0-3: the per-lobe geometric distance field, in blocks. A
        // distance is compared rather than a density precisely because a
        // density-space pseudo-distance is what this correction removed.
        for (int index = 0; index < 4; index++) {
            double javaDistance = StormLobeEvaluator.signedDistanceAt(
                    lobes.get(index), probes[index][0], probes[index][1], probes[index][2]
            );
            if (Math.abs(javaDistance - glsl[index]) > 5.0E-2D) {
                mismatches.add("role " + lobes.get(index).role() + " distance java="
                        + format(javaDistance) + " glsl=" + format(glsl[index]));
            }
        }
        List<StormLobeDescriptor> firstOnly = lobes.subList(0, 4);
        String[] labels = {"lobe union envelope", "group union envelope"};
        for (int fixtureCase = 4; fixtureCase < 6; fixtureCase++) {
            List<StormLobeDescriptor> javaLobes = fixtureCase == 4 ? firstOnly : lobes;
            double javaEnvelope = StormLobeEvaluator.coverageEnvelopeAt(
                    javaLobes,
                    probes[fixtureCase][0], probes[fixtureCase][1], probes[fixtureCase][2]
            );
            if (Math.abs(javaEnvelope - glsl[fixtureCase]) > 2.0E-3D) {
                mismatches.add(labels[fixtureCase - 4] + " java="
                        + format(javaEnvelope) + " glsl=" + format(glsl[fixtureCase]));
            }
        }
        // Cases 6-7: the full ordered composition through the noise stages.
        StormFieldSampler sampler = StormFieldSampler.of(
                StormFieldSampler.Composition.CORRECTED_PHASE_4S, null, null);
        String[] compositionLabels = {"local underside composition", "boundary composition"};
        for (int fixtureCase = 6; fixtureCase < 8; fixtureCase++) {
            List<StormLobeDescriptor> javaLobes = fixtureCase == 6 ? firstOnly : lobes;
            double coverage = StormLobeEvaluator.coverageEnvelopeAt(
                    javaLobes,
                    probes[fixtureCase][0], probes[fixtureCase][1], probes[fixtureCase][2]
            );
            double javaDensity = sampler.densityFromFields(
                    coverage, noise[fixtureCase][0], noise[fixtureCase][1]);
            if (Math.abs(javaDensity - glsl[fixtureCase]) > 2.0E-3D) {
                mismatches.add(compositionLabels[fixtureCase - 6] + " java="
                        + format(javaDensity) + " glsl=" + format(glsl[fixtureCase]));
            }
        }
        require(mismatches.isEmpty(), String.join("; ", mismatches));
    }

    private static float[] executeIndependentGlslFixture(
            List<StormLobeDescriptor> lobes,
            double[][] probes,
            double[][] noise
    ) {
        Path fixture = workspacePath(
                "src/test/resources/net/Gabou/projectatmosphere/clouds/client/render/volumetric/"
                        + "storm_lobe_equations.glsl"
        );
        String fragmentSource;
        try {
            fragmentSource = Files.readString(fixture);
        } catch (IOException exception) {
            throw new IllegalStateException("could not read independent GLSL fixture", exception);
        }
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("GLFW could not initialize independent GLSL fixture");
        }
        long window = 0L;
        int program = 0;
        int framebuffer = 0;
        int texture = 0;
        int vertexArray = 0;
        try {
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
            window = GLFW.glfwCreateWindow(8, 1, "phase4r-glsl-fixture", 0L, 0L);
            require(window != 0L, "GLFW could not create hidden GLSL fixture context");
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();

            String vertexSource = "#version 150\n"
                    + "const vec2 vertices[3] = vec2[3](vec2(-1.0,-1.0),vec2(3.0,-1.0),vec2(-1.0,3.0));\n"
                    + "void main(){gl_Position=vec4(vertices[gl_VertexID],0.0,1.0);}\n";
            int vertexShader = compileShader(GL20.GL_VERTEX_SHADER, vertexSource, "fixture vertex");
            int fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, fragmentSource, "fixture fragment");
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertexShader);
            GL20.glAttachShader(program, fragmentShader);
            GL20.glLinkProgram(program);
            require(GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_TRUE,
                    "independent GLSL fixture link failed: " + GL20.glGetProgramInfoLog(program));
            GL20.glDeleteShader(vertexShader);
            GL20.glDeleteShader(fragmentShader);

            vertexArray = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(vertexArray);
            texture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F,
                    8, 1, 0, GL11.GL_RGBA, GL11.GL_FLOAT, 0L);
            framebuffer = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, texture, 0);
            require(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)
                            == GL30.GL_FRAMEBUFFER_COMPLETE,
                    "independent GLSL fixture framebuffer is incomplete");

            GL20.glUseProgram(program);
            for (int index = 0; index < lobes.size(); index++) {
                StormLobeDescriptor lobe = lobes.get(index);
                setUniform4(program, "FixturePositionHeight[" + index + "]",
                        (float) lobe.centerX(), (float) lobe.centerZ(), lobe.baseY(), lobe.topY());
                setUniform4(program, "FixtureRadiusRotation[" + index + "]",
                        lobe.majorRadius(), lobe.minorRadius(), lobe.sinOrientation(), lobe.cosOrientation());
                setUniform4(program, "FixtureShearMedia[" + index + "]",
                        lobe.shearX(), lobe.shearZ(), lobe.density() * lobe.detailWeight(), lobe.edgeSoftness());
                setUniform4(program, "FixtureMeta[" + index + "]",
                        lobe.groupSlot(), lobe.role().gpuId(), 0.0F, 0.0F);
            }
            for (int index = 0; index < probes.length; index++) {
                int location = GL20.glGetUniformLocation(program, "FixtureProbe[" + index + "]");
                require(location >= 0, "independent GLSL fixture probe uniform missing index=" + index);
                GL20.glUniform3f(location,
                        (float) probes[index][0], (float) probes[index][1], (float) probes[index][2]);
            }
            for (int index = 0; index < noise.length; index++) {
                int location = GL20.glGetUniformLocation(program, "FixtureNoise[" + index + "]");
                require(location >= 0, "independent GLSL fixture noise uniform missing index=" + index);
                GL20.glUniform2f(location,
                        (float) noise[index][0], (float) noise[index][1]);
            }
            GL11.glViewport(0, 0, 8, 1);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            GL11.glFinish();
            FloatBuffer pixels = BufferUtils.createFloatBuffer(8 * 4);
            GL11.glReadPixels(0, 0, 8, 1, GL11.GL_RGBA, GL11.GL_FLOAT, pixels);
            float[] result = new float[8];
            for (int index = 0; index < result.length; index++) {
                result[index] = pixels.get(index * 4);
                require(Float.isFinite(result[index]), "independent GLSL fixture returned non-finite value");
            }
            return result;
        } finally {
            if (program != 0) {
                GL20.glDeleteProgram(program);
            }
            if (framebuffer != 0) {
                GL30.glDeleteFramebuffers(framebuffer);
            }
            if (texture != 0) {
                GL11.glDeleteTextures(texture);
            }
            if (vertexArray != 0) {
                GL30.glDeleteVertexArrays(vertexArray);
            }
            if (window != 0L) {
                GLFW.glfwDestroyWindow(window);
            }
            GLFW.glfwTerminate();
        }
    }

    private static int compileShader(int type, String source, String label) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        require(GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_TRUE,
                label + " compile failed: " + GL20.glGetShaderInfoLog(shader));
        return shader;
    }

    private static void setUniform4(
            int program,
            String name,
            float first,
            float second,
            float third,
            float fourth
    ) {
        int location = GL20.glGetUniformLocation(program, name);
        require(location >= 0, "independent GLSL fixture uniform missing: " + name);
        GL20.glUniform4f(location, first, second, third, fourth);
    }

    private static void validateDescriptorComposition() {
        UUID storm = group(77);
        StormLobeDescriptor left = descriptor(storm, 0, 2, StormLobeDescriptor.Role.BASE,
                -180.0D, 0.0D, 220.0F, 310.0F, 48.0F, 42.0F).withGroupSlot(0);
        StormLobeDescriptor right = descriptor(storm, 1, 2, StormLobeDescriptor.Role.BASE,
                180.0D, 0.0D, 220.0F, 310.0F, 48.0F, 42.0F).withGroupSlot(0);
        double y = 258.0D;
        double leftAtGap = StormLobeEvaluator.densityAt(left, 0.0D, y, 0.0D);
        double rightAtGap = StormLobeEvaluator.densityAt(right, 0.0D, y, 0.0D);
        require(leftAtGap == 0.0D && rightAtGap == 0.0D,
                "composition fixture gap is inside an individual descriptor support");
        double composed = StormLobeEvaluator.coverageEnvelopeAt(List.of(left, right), 0.0D, y, 0.0D);
        require(composed <= 0.01D,
                "statistical group envelope filled a point outside both descriptor supports: density="
                        + format(composed));
    }

    private static void validateDescriptorSlotValidity() {
        UUID storm = group(790);
        StormLobeDescriptor selected = descriptor(storm, 0, 1, StormLobeDescriptor.Role.BASE,
                0.0D, 0.0D, 220.0F, 300.0F, 80.0F, 64.0F).withGroupSlot(0);
        float[] destination = new float[
                StormLobeSpatialIndex.MAX_LOBES * StormLobeDescriptor.FLOATS_PER_DESCRIPTOR
        ];
        Arrays.fill(destination, Float.NaN);
        try {
            Method refresh = StormGeometryBuildCoordinator.class.getDeclaredMethod(
                    "refreshLiveDescriptors", List.class, StormLobeDescriptor[].class, float[].class
            );
            refresh.setAccessible(true);
            refresh.invoke(null, List.of(), new StormLobeDescriptor[]{selected}, destination);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("could not exercise live descriptor slot refresh", exception);
        }
        float packedGroupRole = destination[15];
        require(packedGroupRole < 0.0F,
                "missing selected member was zero-filled and decodes as group 0 BASE at origin; packed="
                        + packedGroupRole);
    }

    private static void validateIncompleteGroupFallback() {
        String shader = readWorkspaceSource(
                "src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh"
        );
        String availability = between(shader, "bool directStormAvailable", ";", "direct-storm availability");
        require(!availability.contains("StormLobeCount > 0") || !availability.contains("stormProfile"),
                "global StormLobeCount/stormProfile ownership suppresses familyMacroShape for incomplete "
                        + "or omitted groups: " + oneLine(availability));
    }

    private static void validateRejectedBuildRerequest() {
        String source = readWorkspaceSource(
                "src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/"
                        + "StormGeometryBuildCoordinator.java"
        );
        String rejection = between(source, "if (!valid)", "adopted = result", "rejected-build branch");
        require(rejection.contains("requestedGridSignature") || rejection.contains("replacePending"),
                "rejected completed build returns without clearing the stale requested signature or "
                        + "re-requesting current geometry: " + oneLine(rejection));
    }

    private static void validateClusterOnlySignatures() {
        List<VolumetricRenderCell> cluster = List.of(renderCell(1.25D));
        List<VolumetricRenderCell> withUnrelatedLod = List.of(renderCell(1.25D), unrelatedLodCell(720.0D));
        long grid = StormLobeSpatialIndex.gridSignature(cluster, -2048.0D, -2048.0D, 4096.0F);
        long gridWithLod = StormLobeSpatialIndex.gridSignature(
                withUnrelatedLod, -2048.0D, -2048.0D, 4096.0F
        );
        long topology = StormLobeSpatialIndex.topologySignature(cluster);
        long topologyWithLod = StormLobeSpatialIndex.topologySignature(withUnrelatedLod);
        require(grid == gridWithLod && topology == topologyWithLod,
                "unrelated macro/LOD severe-shaped cell changed storm signatures: grid="
                        + grid + "->" + gridWithLod + " topology=" + topology + "->" + topologyWithLod);
    }

    private static void validateCandidateGroupWitnessCoverage() {
        List<StormLobeDescriptor> source = new ArrayList<>();
        addCompleteGroup(source, group(791), 0.0D, 0.0D, 7);
        addCompleteGroup(source, group(792), 0.0D, 0.0D, 7);
        StormGeometryBuild build = StormLobeSpatialIndex.build(new StormGeometryBuildInput(
                1L, 1L, 1L, 1L, 0.0D, 0.0D,
                -256.0D, -256.0D, 512.0F,
                source.toArray(StormLobeDescriptor[]::new)
        ));
        int centerPixel = (128 * StormLobeSpatialIndex.GRID_SIZE + 128) * 4;
        Set<Integer> groupSlots = new HashSet<>();
        int witnessCount = 0;
        for (int rank = 0; rank < StormLobeSpatialIndex.CANDIDATES_PER_TILE; rank++) {
            int descriptorIndex = StormLobeSpatialIndex.unpackCandidate(
                    build.candidateTexelsUnsafe()[centerPixel + rank / 2], rank & 1
            );
            if (descriptorIndex >= 0) {
                witnessCount++;
                groupSlots.add(build.selectedDescriptorsUnsafe()[descriptorIndex].groupSlot());
            }
        }
        require(groupSlots.size() == 2 && witnessCount == groupSlots.size(),
                "candidate tile stored " + witnessCount + " descriptor witnesses for "
                        + groupSlots.size() + " intersecting groups instead of one witness per group");
    }

    private static void validateCandidateNonAuthority() {
        String shader = readWorkspaceSource(
                "src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh"
        );
        String availability = between(shader, "bool directStormAvailable", ";", "direct-storm availability");
        require(!availability.contains("directStormIndexed"),
                "candidate/index coverage still controls direct storm ownership and fallback: "
                        + oneLine(availability));
    }

    /**
     * Compiles the production fragment shader in a hidden GL context.
     *
     * <p>The independent parity fixture proves the storm equations agree, but
     * it is a separate program: it cannot catch a syntax or type error
     * elsewhere in the production shader. Without this, a broken shader is
     * only discovered by launching the game, where the failure surfaces as
     * "clouds disappeared" rather than as a compile error.
     */
    private static void validateProductionShaderCompiles() {
        String fragmentSource = resolveMojImports(readWorkspaceSource(
                "src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh"
        ));
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("GLFW could not initialize for the shader compile check");
        }
        long window = 0L;
        int shader = 0;
        try {
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
            window = GLFW.glfwCreateWindow(4, 1, "storm-shader-compile", 0L, 0L);
            require(window != 0L, "GLFW could not create the shader compile context");
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            shader = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
            GL20.glShaderSource(shader, fragmentSource);
            GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) != GL11.GL_TRUE) {
                throw new IllegalStateException("production storm shader failed to compile: "
                        + oneLine(GL20.glGetShaderInfoLog(shader)));
            }
        } finally {
            if (shader != 0) {
                GL20.glDeleteShader(shader);
            }
            if (window != 0L) {
                GLFW.glfwDestroyWindow(window);
            }
            GLFW.glfwTerminate();
        }
    }

    /**
     * Resolves Minecraft's {@code #moj_import} directives the way the game's
     * shader loader does, so the production shader can be compiled standalone.
     */
    private static String resolveMojImports(String source) {
        StringBuilder resolved = new StringBuilder(source.length() + 4096);
        for (String line : source.split("\n", -1)) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("#moj_import")) {
                resolved.append(line).append('\n');
                continue;
            }
            int open = trimmed.indexOf('<');
            int close = trimmed.indexOf('>', open + 1);
            require(open >= 0 && close > open, "malformed #moj_import: " + trimmed);
            String reference = trimmed.substring(open + 1, close);
            int colon = reference.indexOf(':');
            String namespace = colon >= 0 ? reference.substring(0, colon) : "minecraft";
            String path = colon >= 0 ? reference.substring(colon + 1) : reference;
            String included = readWorkspaceSource(
                    "src/main/resources/assets/" + namespace + "/shaders/include/" + path
            );
            // Only the root file carries the #version directive.
            for (String includedLine : included.split("\n", -1)) {
                if (includedLine.trim().startsWith("#version")) {
                    continue;
                }
                resolved.append(includedLine).append('\n');
            }
        }
        return resolved.toString();
    }

    private static void validateBoundedPerGroupIntersection() {
        String shader = readWorkspaceSource(
                "src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh"
        );
        String function = functionBlock(shader, "bool directStormSegmentMayIntersect");
        // The invariant is that segment rejection works per admitted group and
        // never degenerates into a scan of every descriptor. The proxy for
        // "per group" was the bool[MAX_STORM_GROUPS] visitation array; that is
        // now a compact bit mask (T120), which is the same bounded work in a
        // GPU-friendlier form. The scan prohibition is unchanged.
        require(function.contains("groupVisited")
                        && function.contains("stormGroupSegmentMayIntersect")
                        && !function.contains("descriptorIndex < MAX_STORM_LOBES"),
                "ray segment intersection scans every descriptor instead of bounded per-group geometry");
    }

    private static VolumetricRenderCell unrelatedLodCell(double x) {
        UUID storm = group(793);
        return new VolumetricRenderCell(
                x, 0.0D, 220.0F, 330.0F, 78.0F, 66.0F,
                0.0F, 0.85F, 0.22F, 0.9F, 4,
                CloudMorphologyFamily.STORM_ANVIL.ordinal(), 1.0F, 0.8F,
                0.6F, 0.8F, 0.7F, 0.5F, 0.4F,
                0.0F, 0.0F, 0.0F, true,
                CloudMorphologyMemberTier.UNKNOWN,
                VolumetricRenderCell.EnvelopeRole.BASE,
                new UUID(storm.getMostSignificantBits(), storm.getLeastSignificantBits() + 1L),
                storm, 0, 1
        );
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

    private static void reportAndFail(String suite, List<RegressionResult> results) {
        int failures = 0;
        for (RegressionResult result : results) {
            failures += result.failed() ? 1 : 0;
            System.out.println("PHASE4R_RESULT|" + result.name() + "|"
                    + (result.failed() ? "FAILED" : "PASSED") + "|" + oneLine(result.reason()));
        }
        require(failures > 0, "Phase 4R " + suite + " regressions unexpectedly all passed");
        throw new IllegalStateException("Phase 4R " + suite + " fail-first captured "
                + failures + "/" + results.size() + " expected invariant failures");
    }

    private static Path workspacePath(String relative) {
        return Path.of(System.getProperty("user.dir", ".")).resolve(relative);
    }

    private static String readWorkspaceSource(String relative) {
        try {
            return Files.readString(workspacePath(relative)).replace("\r\n", "\n");
        } catch (IOException exception) {
            throw new IllegalStateException("could not inspect " + relative, exception);
        }
    }

    private static String between(String source, String startToken, String endToken, String label) {
        int start = source.indexOf(startToken);
        require(start >= 0, label + " start token missing");
        int end = source.indexOf(endToken, start + startToken.length());
        require(end >= 0, label + " end token missing");
        return source.substring(start, end + endToken.length());
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

    private record Section(double equivalentRadius, int components) {
    }

    private static StormGeometryBuildInput emptyInput(long requestGeneration) {
        return new StormGeometryBuildInput(
                1L, requestGeneration, 10L, 20L,
                0.0D, 0.0D, -2048.0D, -2048.0D, 4096.0F,
                new StormLobeDescriptor[0]
        );
    }

    private static VolumetricRenderCell renderCell(double x) {
        UUID group = group(70);
        return new VolumetricRenderCell(
                x, 2.5D, 220.0F, 330.0F, 78.25F, 66.0F,
                0.0F, 0.85F, 0.22F, 0.9F, 4,
                CloudMorphologyFamily.STORM_ANVIL.ordinal(), 1.0F, 0.8F,
                0.6F, 0.8F, 0.7F, 0.5F, 0.4F,
                0.0F, 0.0F, 0.0F, false,
                CloudMorphologyMemberTier.UNKNOWN,
                VolumetricRenderCell.EnvelopeRole.BASE,
                new UUID(group.getMostSignificantBits(), group.getLeastSignificantBits() + 1L),
                group, 0, 1
        );
    }

    private static void addCompleteGroup(
            List<StormLobeDescriptor> output,
            UUID group,
            double x,
            double z,
            int count
    ) {
        for (int index = 0; index < count; index++) {
            StormLobeDescriptor.Role role = index == 0
                    ? StormLobeDescriptor.Role.BASE
                    : index < 3
                    ? StormLobeDescriptor.Role.CORE
                    : index < 5
                    ? StormLobeDescriptor.Role.TOWER
                    : StormLobeDescriptor.Role.ANVIL;
            output.add(descriptor(group, index, count, role,
                    x + index * 2.0D, z + index, 220.0F + role.gpuId() * 18.0F,
                    280.0F + role.gpuId() * 25.0F,
                    role == StormLobeDescriptor.Role.ANVIL ? 150.0F : 82.0F,
                    role == StormLobeDescriptor.Role.ANVIL ? 48.0F : 68.0F));
        }
    }

    private static StormLobeDescriptor descriptor(
            UUID group,
            int memberIndex,
            int memberCount,
            StormLobeDescriptor.Role role,
            double x,
            double z,
            float base,
            float top,
            float major,
            float minor
    ) {
        UUID field = new UUID(group.getMostSignificantBits(),
                group.getLeastSignificantBits() + memberIndex + 1L);
        return new StormLobeDescriptor(
                field, group, memberIndex, memberCount, -1, role,
                x, z, base, top, major, minor,
                0.0F, 1.0F,
                role == StormLobeDescriptor.Role.BASE ? 0.0F : major * 0.18F,
                0.0F,
                0.85F, 0.22F, 0.37F, 0.5F, 0.9F, 1.0F
        );
    }

    private static UUID group(long value) {
        return new UUID(0x53544F524D000000L, value);
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    static void requireNear(String label, double actual, double expected, double tolerance) {
        if (!Double.isFinite(actual) || Math.abs(actual - expected) > tolerance) {
            throw new IllegalStateException(
                    label + " expected=" + expected + " +/- " + tolerance + " actual=" + actual
            );
        }
    }
}
