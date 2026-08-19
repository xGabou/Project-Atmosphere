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
        requireNear("packed group/role", texels[15], 16.0D, 0.0D);
        require(StormLobeDescriptor.Role.fromGpuId(((int) texels[15]) % 8)
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
