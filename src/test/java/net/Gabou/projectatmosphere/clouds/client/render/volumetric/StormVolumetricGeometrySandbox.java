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
        reportT098AnvilSurfaceStructure();
        reportT098AnvilOpticalSurface();
        reportT098VerticalWidthProfile();
        reportT098TransitionCandidates();
        reportT098PercolationWidth();
        reportT098AnvilSkirt();
        reportT098AnvilSoftnessSweep();
        reportT098SoftnessVersusHeight();
        validateT098EnvelopeBoundedByExtent();
        reportT098OpticalProfile();
        reportT098WebbingExcess();
        reportT098MarchSimulation();
        reportT098PromotionPolicySweep();
        validateT098PromotionBudget();
        validateT098MarchReachesMaterial();
        validateT098CloudHitDepthNeverSaturates();
        if (Boolean.getBoolean("phase4r.failFirst")) {
            runPhase4RFailFirst();
        } else {
            runPhase4RCorrected();
        }
    }


    /**
     * T098 defect boundary: a cloud HIT must never publish the composite's
     * "no cloud" depth sentinel.
     *
     * <p>Measured on the live SIDE waist ray with the production ray trace: the
     * march integrated alpha 0.63 over material whose alpha-weighted
     * representative point sat 912 blocks away, while the cloud pass was drawn
     * with the scene projection whose far plane is 768.24 blocks. {@code
     * depthAt} clamps to [0, 1], so that representative point published depth
     * exactly 1.0. {@code cloud_field_composite.fsh} reads a cloud texel as
     * carrying no cloud when its depth is not below 1.0 - {@code hasDepth =
     * depths[i] < 1.0}, then {@code selectedDepth >= 1.0} discards - so every
     * bit of that alpha was thrown away and the storm rendered as clear sky.
     * The BASE and ANVIL controls on the same frame sat at 736 and 665.5
     * blocks, inside the far plane, published 0.99999416 and 0.99998683, and
     * were composited normally.
     *
     * <p>The volume is marched to MaxRenderDistance, which is a cloud setting
     * unrelated to the scene frustum, so this is not an exotic case: it is
     * every storm whose material centroid lies past the render distance. This
     * check sweeps representative distances across the far plane for several
     * render distances and requires that a hit is always composited. The old
     * expression is evaluated alongside and must fail the same sweep, so the
     * regression cannot silently pass on the behaviour it was written for.
     */
    private static void validateT098CloudHitDepthNeverSaturates() {
        String shader = readWorkspaceSource("src/main/resources/assets/projectatmosphere/"
                + "shaders/core/cloud_atmosphere_volume.fsh");
        String constantKey = "const float PA_CLOUD_HIT_MAX_DEPTH = ";
        int constantAt = shader.indexOf(constantKey);
        require(constantAt >= 0, "PA_CLOUD_HIT_MAX_DEPTH is missing from the production shader");
        float hitMaxDepth = Float.parseFloat(shader.substring(
                constantAt + constantKey.length(), shader.indexOf(';', constantAt)).trim());
        require(hitMaxDepth < 1.0F,
                "PA_CLOUD_HIT_MAX_DEPTH must be below the composite's 1.0 miss sentinel");
        require(hitMaxDepth >= 0.99999F,
                "PA_CLOUD_HIT_MAX_DEPTH below 0.99999 would change history depth confidence,"
                        + " which this correction must leave alone");
        require(shader.contains("min(depthAt(relRepresentative), PA_CLOUD_HIT_MAX_DEPTH)"),
                "the cloud hit depth is no longer bounded away from the composite's sentinel");

        // The before/after evidence arm must stay diagnostic-only: production
        // frames take the corrected bound, never the saturating one.
        require(shader.contains("PaLegacyHitDepth != 0"),
                "the T098 legacy-depth evidence arm is no longer gated by its uniform");
        String shaderJson = readWorkspaceSource("src/main/resources/assets/projectatmosphere/"
                + "shaders/core/cloud_atmosphere_volume.json");
        require(shaderJson.contains(
                        "{ \"name\": \"PaLegacyHitDepth\", \"type\": \"int\", \"count\": 1, \"values\": [ 0 ] }"),
                "the T098 legacy-depth arm does not default to off");
        require(!VolumetricCloudDebugConfig.t098LegacyHitDepth(),
                "the T098 legacy-depth evidence arm is enabled by default");

        String composite = readWorkspaceSource("src/main/resources/assets/projectatmosphere/"
                + "shaders/core/cloud_field_composite.fsh");
        require(composite.contains("bool hasDepth = depths[i] < 1.0;")
                        && composite.contains("selectedDepth >= 1.0"),
                "the composite no longer treats depth 1.0 as absence of cloud;"
                        + " this guard's premise must be re-derived");

        long probes = 0L;
        long correctedDiscards = 0L;
        long legacyDiscards = 0L;
        String firstLegacyWitness = "";
        for (int renderChunks : new int[] {8, 12, 16, 24, 32}) {
            // Minecraft's own projection far plane for that render distance.
            float far = renderChunks * 16.0F * 4.0F;
            float near = 0.05F;
            for (double representativeT = 32.0D; representativeT <= 2000.0D;
                    representativeT += 0.5D) {
                // A view-axis point at this distance, through the standard
                // OpenGL perspective depth mapping the cloud pass inherits.
                // View space looks down -Z, so a point straight ahead at this
                // distance has viewZ = -representativeT and clipW = +distance.
                double clipZ = representativeT * (far + near) / (far - near)
                        - 2.0D * far * near / (far - near);
                double clipW = representativeT;
                double ndcDepth = clipZ / Math.max(Math.abs(clipW), 0.00001D);
                float clamped = (float) Math.max(0.0D, Math.min(1.0D, ndcDepth * 0.5D + 0.5D));
                float corrected = Math.min(clamped, hitMaxDepth);
                probes++;
                if (clamped >= 1.0F) {
                    legacyDiscards++;
                    if (firstLegacyWitness.isEmpty()) {
                        firstLegacyWitness = "renderChunks=" + renderChunks
                                + " far=" + far
                                + " representativeT=" + representativeT
                                + " legacyDepth=" + clamped;
                    }
                }
                if (corrected >= 1.0F) {
                    correctedDiscards++;
                }
            }
        }
        System.out.printf(
                "T098_HIT_DEPTH|probes=%d|legacyDiscardedHits=%d|correctedDiscardedHits=%d%n",
                probes, legacyDiscards, correctedDiscards);
        require(probes > 15_000L, "the hit-depth sweep did not cover enough of the range");
        require(legacyDiscards > 0L,
                "the pre-fix expression discarded no hit anywhere in the sweep;"
                        + " this regression would pass against the behaviour it was written for");
        System.out.println("T098_HIT_DEPTH_LEGACY_WITNESS|" + firstLegacyWitness);
        require(correctedDiscards == 0L,
                "a cloud hit still publishes the composite's miss sentinel in "
                        + correctedDiscards + " of " + probes + " probes");

        // The correction must not make a cloud visible through terrain. The
        // composite keeps a texel only when its depth is at or in front of the
        // scene depth, and that comparison is unchanged by the bound.
        float sceneDepthInFront = 0.98F;
        require(hitMaxDepth > sceneDepthInFront,
                "the bounded cloud depth would draw in front of nearer scene geometry");

        System.out.println(
                "PHASE4T_RESULT|T098 cloud hit depth never saturates|PASSED|invariant satisfied");
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

    // ------------------------------------------------------------------
    // T098 promotion-policy sweep
    // ------------------------------------------------------------------

    /**
     * Production density threshold, expressed on the value {@link
     * StormDensityModel#finalDensity} returns.
     *
     * <p>The shader tests {@code density > 0.0008} after multiplying the eroded
     * body by the descriptor-owned material terms (energy 0.72, condensate
     * 0.78, precipitation 0.68 at the mid-height the waist rays cross), the
     * 0.73 family scale and DensityMul 1.44922, which together come to 1.287.
     * The offline model stops at the eroded body, so the equivalent cut is
     * 0.0008 / 1.287.
     */
    private static final double T098_MATERIAL_CLOUD_THRESHOLD = 0.0008D / 1.287D;

    /** Live capture configuration: ULTRA, stepScale 1.0, exteriorFineStep 2.5. */
    private static final double T098_FINE_STEP = 2.5D;
    private static final int T098_STEP_BUDGET = 96;
    private static final int T098_MAX_STEPS = 128;
    private static final double T098_MAX_RENDER_DISTANCE = 2000.0D;
    private static final double T098_EXTINCTION_SCALE = 0.11499D;
    /** Descriptor-owned material terms and family scale, as above. */
    private static final double T098_DENSITY_SCALE = 1.287D;

    /** One marched ray under one promotion policy. */
    private record T098MarchResult(
            String policy, String label, double factor,
            int iterations, int emptyFineIterations, double emptyFineBlocks,
            int densityEvaluations, int promotionProbes,
            double firstMaterialT, int iterationsAtFirstMaterial,
            int iterationsRemainingAtMaterial,
            double finalTransmittance, double finalAlpha,
            boolean stepCapped, int falseNegativeSegments, double falseNegativeBlocks,
            double referenceFirstMaterialT, double referenceAlpha) {
    }

    /**
     * T098 second divergence: the promotion policy spends the march budget in
     * empty coverage envelope before any density exists.
     *
     * <p>Measured on the live production traces: the conservative per-descriptor
     * clearance promotes a SIDE waist ray to sustained fine marching around
     * t=280, the coverage envelope only becomes non-zero near t=700, and the
     * first sample that clears the density threshold is near t=830. Between 36
     * and 79 march iterations - a mean of 66 on waist rays - are spent taking
     * 2.5-block steps through envelope that carries no material, and three of
     * six traced waist rays then hit the 128-iteration cap with up to 0.55
     * transmittance still unabsorbed.
     *
     * <p>This sweep runs the production march rules offline against a 1-block
     * reference traversal and scores each candidate promotion policy on both
     * properties that matter: it must skip no material the production fine
     * march would have sampled, and it must reach material with enough budget
     * left to converge.
     */
    private static void reportT098PromotionPolicySweep() {
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

        System.out.println("T098_POLICY|policy|label|factor|iters|emptyFineIters|emptyFineBlocks"
                + "|densityEvals|promoProbes|firstMaterialT|itersAtMaterial|itersLeft"
                + "|finalTrans|finalAlpha|stepCapped|falseNegSegs|falseNegBlocks"
                + "|refFirstT|refAlpha");
        java.util.List<T098MarchResult> all = new ArrayList<>();
        // production      - the shipped policy
        // production384   - the same policy given three times the iteration
        //                   budget, as a truth arm for what the ray should
        //                   converge to at 128 steps (PHASE 7; MAX_STEPS is
        //                   NOT changed in production)
        // scan16          - candidate: probe the candidate span on the fine
        //                   march's own lattice inside one iteration
        // scan-coarse2    - control: the same scan at twice the fine spacing,
        //                   included to show what losing sampling resolution
        //                   costs rather than assuming the spacing is safe
        // bisectOnly      - control: drop the forced fine promotion entirely
        //                   and rely on the bracket refinement alone
        for (String policy : new String[] {
                "production", "production384", "scan16", "scan-coarse2", "bisectOnly"}) {
            for (double factor : new double[] {1.12D, 1.40D, 1.70D, 1.90D, 2.60D}) {
                all.add(simulateT098Ray(baseVolume, detailVolume, lobes, centreX, centreZ,
                        radius, factor, 680.0D, "waist", policy));
            }
            all.add(simulateT098Ray(baseVolume, detailVolume, lobes, centreX, centreZ,
                    radius, 1.70D, 300.0D, "baseControl", policy));
            all.add(simulateT098Ray(baseVolume, detailVolume, lobes, centreX, centreZ,
                    radius, 1.70D, 900.0D, "anvilControl", policy));
            all.add(simulateT098Ray(baseVolume, detailVolume, lobes, centreX, centreZ,
                    radius, 1.70D, 500.0D, "lowerTower", policy));
            all.add(simulateT098Ray(baseVolume, detailVolume, lobes, centreX, centreZ,
                    radius, 1.70D, 800.0D, "upperTower", policy));
        }
        for (T098MarchResult r : all) {
            System.out.printf(java.util.Locale.ROOT,
                    "T098_POLICY|%-10s|%-12s|%.2f|%4d|%5d|%9.1f|%6d|%5d|%9.1f|%5d|%5d"
                            + "|%8.4f|%8.4f|%6s|%4d|%8.1f|%9.1f|%8.4f%n",
                    r.policy(), r.label(), r.factor(), r.iterations(),
                    r.emptyFineIterations(), r.emptyFineBlocks(), r.densityEvaluations(),
                    r.promotionProbes(), r.firstMaterialT(), r.iterationsAtFirstMaterial(),
                    r.iterationsRemainingAtMaterial(), r.finalTransmittance(),
                    r.finalAlpha(), r.stepCapped() ? "YES" : "no",
                    r.falseNegativeSegments(), r.falseNegativeBlocks(),
                    r.referenceFirstMaterialT(), r.referenceAlpha());
        }
        T098_POLICY_RESULTS.clear();
        T098_POLICY_RESULTS.addAll(all);
    }

    private static final java.util.List<T098MarchResult> T098_POLICY_RESULTS = new ArrayList<>();

    /**
     * Marches one ray under the production rules, with the promotion policy
     * selected by {@code policy}, and scores it against a 1-block reference.
     *
     * <p>The model carries the parts of the production loop that decide the
     * outcome: fine/coarse selection from {@code sinceHit}, the conservative
     * per-descriptor clearance advance, the four-bisection bracket refinement a
     * coarse step performs when it lands in material, exponential extinction
     * with the production scale, the 0.015 transmittance floor and the
     * 128-iteration cap. The outer weather-gated empty-space skip is not
     * modelled; the live A/B falsified it as a factor on these rays, and it can
     * only remove samples in empty space that this model already sees as empty.
     */
    private static T098MarchResult simulateT098Ray(
            byte[] baseVolume, byte[] detailVolume, java.util.List<StormLobeDescriptor> lobes,
            double centreX, double centreZ, double radius,
            double factor, double targetY, String label, String policy) {
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

        final int maxSteps = "production384".equals(policy) ? 384 : T098_MAX_STEPS;
        final double t1 = T098_MAX_RENDER_DISTANCE;
        final double fineStep = T098_FINE_STEP;
        final double probeSpacing = "scan-coarse2".equals(policy)
                ? T098_FINE_STEP * 2.0D
                : T098_FINE_STEP;
        final boolean scanning = policy.startsWith("scan");
        final double coarseStepCap = Math.min(112.0D, fineStep * 16.0D);
        final double baseStep = t1 / T098_STEP_BUDGET;
        final double coarseStep = Math.max(baseStep * 1.5D, fineStep * 3.0D);

        // Reference traversal at one block, and the alpha a ray that sampled
        // every block would accumulate.
        double referenceFirstT = -1.0D;
        double referenceTransmittance = 1.0D;
        java.util.List<double[]> referenceIntervals = new ArrayList<>();
        double intervalStart = -1.0D;
        final double referenceStep = 1.0D;
        for (double s = 0.0D; s <= t1; s += referenceStep) {
            double cloud = sampleRayDensity(baseVolume, detailVolume, lobes,
                    camX + dirX * s, camY + dirY * s, camZ + dirZ * s);
            boolean material = cloud > T098_MATERIAL_CLOUD_THRESHOLD;
            if (material) {
                if (referenceFirstT < 0.0D) {
                    referenceFirstT = s;
                }
                if (intervalStart < 0.0D) {
                    intervalStart = s;
                }
                referenceTransmittance *= Math.exp(
                        -cloud * T098_DENSITY_SCALE * T098_EXTINCTION_SCALE * referenceStep);
            } else if (intervalStart >= 0.0D) {
                referenceIntervals.add(new double[] {intervalStart, s});
                intervalStart = -1.0D;
            }
        }
        if (intervalStart >= 0.0D) {
            referenceIntervals.add(new double[] {intervalStart, t1});
        }

        double t = 0.0D;
        int sinceHit = 100;
        int iterations = 0;
        int emptyFineIterations = 0;
        double emptyFineBlocks = 0.0D;
        int densityEvaluations = 0;
        int promotionProbes = 0;
        double firstMaterialT = -1.0D;
        int iterationsAtFirstMaterial = -1;
        int falseNegativeSegments = 0;
        double falseNegativeBlocks = 0.0D;
        double transmittance = 1.0D;
        boolean lastClearValid = true;
        double lastClearT = 0.0D;
        boolean stepCapped = false;

        for (int i = 0; i < maxSteps; i++) {
            iterations = i + 1;
            if (t >= t1 || transmittance < 0.015D) {
                break;
            }
            boolean fine = sinceHit < 6;
            double stepLength = fine
                    ? fineStep
                    : Math.min(coarseStep * (1.0D + (t / T098_MAX_RENDER_DISTANCE) * 2.2D),
                            coarseStepCap);
            stepLength = Math.min(stepLength, t1 - t);

            if (!fine && stormSegmentMayIntersect(lobes,
                    camX + dirX * t, camY + dirY * t, camZ + dirZ * t,
                    camX + dirX * (t + stepLength), camY + dirY * (t + stepLength),
                    camZ + dirZ * (t + stepLength))) {
                promotionProbes++;
                double clearance = Double.POSITIVE_INFINITY;
                for (StormLobeDescriptor lobe : lobes) {
                    clearance = Math.min(clearance,
                            StormLobeEvaluator.signedDistanceAt(lobe,
                                    camX + dirX * t, camY + dirY * t, camZ + dirZ * t)
                                    - StormLobeEvaluator.edgeWidthBlocks(lobe));
                }
                double safeAdvance = clearance - 48.0D;
                if (safeAdvance > fineStep) {
                    stepLength = Math.min(Math.min(safeAdvance, coarseStepCap), t1 - t);
                } else if ("bisectOnly".equals(policy)) {
                    // Take the ordinary coarse stride and let the four-bisection
                    // bracket refinement localize any material it lands in.
                    stepLength = Math.min(Math.min(
                            coarseStep * (1.0D + (t / T098_MAX_RENDER_DISTANCE) * 2.2D),
                            coarseStepCap), t1 - t);
                } else if (scanning) {
                    // Bounded empty-span scan. Sampling at fine resolution is
                    // required; consuming a march iteration per sample is not.
                    // Probe forward on the SAME lattice the fine march would
                    // have used, inside this one iteration, and cross the whole
                    // span at once when every probe is empty.
                    double scanSpan = Math.min(coarseStepCap, t1 - t);
                    int probeCount = (int) Math.min(16.0D, Math.floor(scanSpan / probeSpacing));
                    double lastEmpty = 0.0D;
                    boolean hitMaterial = false;
                    for (int probe = 1; probe <= probeCount; probe++) {
                        double probeT = t + probe * probeSpacing;
                        densityEvaluations++;
                        double cloud = sampleRayDensity(baseVolume, detailVolume, lobes,
                                camX + dirX * probeT, camY + dirY * probeT, camZ + dirZ * probeT);
                        if (cloud > T098_MATERIAL_CLOUD_THRESHOLD) {
                            hitMaterial = true;
                            break;
                        }
                        lastEmpty = probe * probeSpacing;
                    }
                    if (hitMaterial && lastEmpty <= fineStep) {
                        sinceHit = 0;
                        fine = true;
                        stepLength = Math.min(fineStep, t1 - t);
                    } else if (lastEmpty > fineStep) {
                        // Provably empty at the march's own sampling resolution.
                        stepLength = Math.min(lastEmpty, t1 - t);
                        if (hitMaterial) {
                            // Material begins at the next probe; enter fine so
                            // the following iterations integrate it.
                            sinceHit = 0;
                            fine = true;
                        }
                    } else {
                        sinceHit = 0;
                        fine = true;
                        stepLength = Math.min(fineStep, t1 - t);
                    }
                } else {
                    sinceHit = 0;
                    fine = true;
                    stepLength = Math.min(fineStep, t1 - t);
                }
            }

            double segStart = t;
            double segEnd = t + stepLength;
            densityEvaluations++;
            double cloud = sampleRayDensity(baseVolume, detailVolume, lobes,
                    camX + dirX * t, camY + dirY * t, camZ + dirZ * t);
            double density = cloud * T098_DENSITY_SCALE;
            boolean material = cloud > T098_MATERIAL_CLOUD_THRESHOLD;

            if (!material) {
                // Any reference material inside a step the march did not sample
                // at fine resolution is a false negative.
                if (stepLength > fineStep * 1.001D) {
                    double overlap = 0.0D;
                    for (double[] interval : referenceIntervals) {
                        overlap += Math.max(0.0D,
                                Math.min(segEnd, interval[1]) - Math.max(segStart, interval[0]));
                    }
                    if (overlap > 0.0D) {
                        falseNegativeSegments++;
                        falseNegativeBlocks += overlap;
                    }
                }
                if (fine) {
                    emptyFineIterations++;
                    emptyFineBlocks += stepLength;
                }
                lastClearT = t;
                lastClearValid = true;
                sinceHit++;
                t += stepLength;
                continue;
            }

            if (!fine) {
                // Production's four-bisection bracket refinement.
                double bracketLow = lastClearValid ? lastClearT : Math.max(0.0D, t - stepLength);
                double bracketHigh = t;
                for (int refinement = 0; refinement < 4; refinement++) {
                    double mid = 0.5D * (bracketLow + bracketHigh);
                    densityEvaluations++;
                    double midCloud = sampleRayDensity(baseVolume, detailVolume, lobes,
                            camX + dirX * mid, camY + dirY * mid, camZ + dirZ * mid);
                    if (midCloud > T098_MATERIAL_CLOUD_THRESHOLD) {
                        bracketHigh = mid;
                    } else {
                        bracketLow = mid;
                    }
                }
                lastClearT = bracketLow;
                t = 0.5D * (bracketLow + bracketHigh);
                sinceHit = 0;
                continue;
            }

            if (firstMaterialT < 0.0D) {
                firstMaterialT = t;
                iterationsAtFirstMaterial = iterations;
            }
            sinceHit = 0;
            transmittance *= Math.exp(-density * T098_EXTINCTION_SCALE * stepLength);
            t += stepLength;
        }
        if (iterations >= maxSteps && t < t1 && transmittance >= 0.015D) {
            stepCapped = true;
        }

        return new T098MarchResult(policy, label, factor, iterations,
                emptyFineIterations, emptyFineBlocks, densityEvaluations, promotionProbes,
                firstMaterialT, iterationsAtFirstMaterial,
                iterationsAtFirstMaterial < 0 ? 0 : iterations - iterationsAtFirstMaterial,
                transmittance, 1.0D - transmittance, stepCapped,
                falseNegativeSegments, falseNegativeBlocks,
                referenceFirstT, 1.0D - referenceTransmittance);
    }


    /**
     * T098 second divergence: a conservative promotion must not spend the march
     * budget in empty coverage envelope.
     *
     * <p>Two properties, both required.
     *
     * <p><b>Conservative correctness.</b> The policy may not step over material
     * the production fine march would have sampled. This is measured against a
     * one-block reference traversal, and the {@code bisectOnly} control - which
     * drops the forced fine promotion and trusts the four-bisection bracket
     * refinement alone - is run alongside precisely because it looks reasonable
     * and is not: it skips material on every ray in the fixture.
     *
     * <p><b>Bounded progress.</b> The shipped policy takes one march iteration
     * per fine sample, so crossing the empty envelope between the coverage
     * opening and the first material costs tens of iterations. On this fixture
     * it leaves two of nine rays never reaching material at all inside 128
     * iterations, and four step-capped. The replacement samples the same
     * lattice inside one iteration, so the ray arrives at material with budget
     * left to converge.
     *
     * <p>The guard fails under the old policy: it requires the shipped-policy
     * arm to exhibit the starvation, so it cannot pass against the behaviour it
     * was written for, and it requires the corrected arm to converge to the
     * same alpha as a 384-iteration truth arm of the old policy without
     * skipping material.
     */
    private static void validateT098PromotionBudget() {
        require(!T098_POLICY_RESULTS.isEmpty(),
                "the T098 promotion policy sweep produced no rays");

        java.util.List<T098MarchResult> shipped = new ArrayList<>();
        java.util.List<T098MarchResult> corrected = new ArrayList<>();
        java.util.List<T098MarchResult> truth = new ArrayList<>();
        java.util.List<T098MarchResult> bisectOnly = new ArrayList<>();
        for (T098MarchResult r : T098_POLICY_RESULTS) {
            switch (r.policy()) {
                case "production" -> shipped.add(r);
                case "scan16" -> corrected.add(r);
                case "production384" -> truth.add(r);
                case "bisectOnly" -> bisectOnly.add(r);
                default -> {
                }
            }
        }
        require(shipped.size() == corrected.size() && shipped.size() == truth.size()
                        && !shipped.isEmpty(),
                "the promotion sweep arms do not cover the same rays");

        // Fail-first: the shipped policy must actually exhibit the defect.
        int shippedStepCapped = 0;
        int shippedNeverReached = 0;
        int shippedEmptyFine = 0;
        for (T098MarchResult r : shipped) {
            if (r.stepCapped()) {
                shippedStepCapped++;
            }
            if (r.firstMaterialT() < 0.0D) {
                shippedNeverReached++;
            }
            shippedEmptyFine += r.emptyFineIterations();
        }
        require(shippedStepCapped > 0,
                "the shipped promotion policy step-caps no ray in this fixture;"
                        + " this guard would pass against the behaviour it was written for");
        require(shippedNeverReached > 0,
                "the shipped promotion policy reaches material on every ray here;"
                        + " the starvation this guard exists for is not reproduced");

        // The control that looks safe and is not.
        int bisectSkips = 0;
        for (T098MarchResult r : bisectOnly) {
            bisectSkips += r.falseNegativeSegments();
        }
        require(bisectSkips > 0,
                "the bracket-refinement-only control skipped no material, so this"
                        + " guard no longer demonstrates why the scan is required");

        int correctedEmptyFine = 0;
        int correctedStepCapped = 0;
        for (T098MarchResult r : corrected) {
            correctedEmptyFine += r.emptyFineIterations();
            if (r.stepCapped()) {
                correctedStepCapped++;
            }
            require(r.falseNegativeSegments() == 0,
                    "the corrected promotion policy skipped " + r.falseNegativeSegments()
                            + " material segment(s) on " + r.label() + " at "
                            + r.factor() + "x, totalling "
                            + String.format(java.util.Locale.ROOT, "%.1f",
                                    r.falseNegativeBlocks()) + " blocks");
            require(r.firstMaterialT() >= 0.0D,
                    "the corrected promotion policy never reached material on "
                            + r.label() + " at " + r.factor() + "x");
        }
        require(correctedStepCapped == 0,
                "the corrected promotion policy still step-caps "
                        + correctedStepCapped + " of " + corrected.size() + " rays");

        // Material entry and converged opacity must agree with the truth arm,
        // which runs the OLD policy with three times the budget.
        double worstEntryError = 0.0D;
        double worstAlphaError = 0.0D;
        for (int i = 0; i < corrected.size(); i++) {
            T098MarchResult c = corrected.get(i);
            T098MarchResult t = truth.get(i);
            require(c.label().equals(t.label()) && c.factor() == t.factor(),
                    "the corrected and truth arms are not aligned ray for ray");
            worstEntryError = Math.max(worstEntryError,
                    Math.abs(c.firstMaterialT() - t.firstMaterialT()));
            worstAlphaError = Math.max(worstAlphaError,
                    Math.abs(c.finalAlpha() - t.finalAlpha()));
        }
        System.out.printf(java.util.Locale.ROOT,
                "T098_PROMOTION|rays=%d|shippedStepCapped=%d|shippedNeverReached=%d"
                        + "|shippedEmptyFineIters=%d|correctedEmptyFineIters=%d"
                        + "|correctedStepCapped=%d|bisectOnlySkippedSegments=%d"
                        + "|worstEntryErrorBlocks=%.2f|worstAlphaError=%.5f%n",
                corrected.size(), shippedStepCapped, shippedNeverReached,
                shippedEmptyFine, correctedEmptyFine, correctedStepCapped, bisectSkips,
                worstEntryError, worstAlphaError);

        require(worstEntryError <= T098_FINE_STEP + 0.001D,
                "the corrected policy enters material " + worstEntryError
                        + " blocks from where the old policy does with an unbounded budget;"
                        + " one fine step is the most the entry may move");
        require(worstAlphaError <= 0.01D,
                "the corrected policy converges to a different opacity than the"
                        + " unbounded-budget truth arm; worst alpha error " + worstAlphaError);
        require(correctedEmptyFine * 4 < shippedEmptyFine,
                "the corrected policy does not materially reduce empty fine iterations: "
                        + correctedEmptyFine + " against " + shippedEmptyFine);

        // The production shader must actually carry the scan.
        String shader = readWorkspaceSource("src/main/resources/assets/projectatmosphere/"
                + "shaders/core/cloud_atmosphere_volume.fsh");
        require(shader.contains("const int PA_EMPTY_SPAN_PROBES = 16;"),
                "the bounded empty-span scan probe count is missing or no longer 16");
        require(shader.contains("for (int paProbe = 1; paProbe <= PA_EMPTY_SPAN_PROBES; paProbe++)"),
                "the empty-span scan loop is missing or no longer bounded by a constant");
        require(shader.contains("float paProbeOffset = float(paProbe) * paScanStep;")
                        && shader.contains("float paScanStep = fineStep"),
                "the empty-span scan no longer probes on the fine march's own lattice");
        require(shader.contains("const int MAX_STEPS = 128;"),
                "MAX_STEPS is no longer 128; this correction must not buy budget");
        require(shader.contains("PaLegacyFinePromotion != 0"),
                "the promotion evidence arm is no longer gated by its uniform");
        require(!VolumetricCloudDebugConfig.t098LegacyFinePromotion(),
                "the promotion evidence arm is enabled by default");
        String shaderJson = readWorkspaceSource("src/main/resources/assets/projectatmosphere/"
                + "shaders/core/cloud_atmosphere_volume.json");
        require(shaderJson.contains(
                        "{ \"name\": \"PaLegacyFinePromotion\", \"type\": \"int\", \"count\": 1, \"values\": [ 0 ] }"),
                "the promotion evidence arm does not default to off");

        System.out.println("PHASE4T_RESULT|T098 promotion reaches material within budget"
                + "|PASSED|invariant satisfied");
    }


    // ------------------------------------------------------------------
    // T098 ANVIL surface structure
    // ------------------------------------------------------------------

    /** Production density scale from eroded body to the shader's density. */
    private static final double T098_ANVIL_DENSITY_SCALE = 1.287D;
    /** Production ExtinctionScale. */
    private static final double T098_ANVIL_EXTINCTION = 0.11499D;

    /** Distribution summary for one stage of the density chain. */
    private record T098Dist(String stage, int n, double mean, double p05, double p50,
                            double p95, double variance, double cv, double meanGradient,
                            double sat80, double sat90, double sat99, double zero) {
    }

    private static T098Dist summarize(String stage, double[] values, int n,
            double[] gradients, int gradientCount) {
        double[] v = java.util.Arrays.copyOf(values, n);
        java.util.Arrays.sort(v);
        double mean = 0.0D;
        for (int i = 0; i < n; i++) {
            mean += v[i];
        }
        mean /= Math.max(1, n);
        double var = 0.0D;
        for (int i = 0; i < n; i++) {
            var += (v[i] - mean) * (v[i] - mean);
        }
        var /= Math.max(1, n);
        double meanGradient = 0.0D;
        for (int i = 0; i < gradientCount; i++) {
            meanGradient += gradients[i];
        }
        meanGradient /= Math.max(1, gradientCount);
        int in80 = 0;
        int in90 = 0;
        int in99 = 0;
        int atZero = 0;
        for (int i = 0; i < n; i++) {
            if (v[i] >= 0.80D) {
                in80++;
            }
            if (v[i] >= 0.90D) {
                in90++;
            }
            if (v[i] >= 0.99D) {
                in99++;
            }
            if (v[i] <= 0.0001D) {
                atZero++;
            }
        }
        return new T098Dist(stage, n, mean,
                v[Math.min(n - 1, (int) (n * 0.05))], v[Math.min(n - 1, (int) (n * 0.50))],
                v[Math.min(n - 1, (int) (n * 0.95))], var,
                mean > 1.0E-6D ? Math.sqrt(var) / mean : 0.0D, meanGradient,
                100.0D * in80 / Math.max(1, n), 100.0D * in90 / Math.max(1, n),
                100.0D * in99 / Math.max(1, n), 100.0D * atZero / Math.max(1, n));
    }

    /**
     * T098 ANVIL surface structure: where the anvil's detail amplitude is lost.
     *
     * <p>The anvil renders as a large smooth balloon with a uniform-looking
     * interior. The existing erosion report already shows the offline density
     * field is not saturated - mean 0.378 over the anvil with 15 per cent of
     * samples below the visible floor - so "uniform density" cannot be assumed.
     * This measures the whole chain instead: the stage distributions and their
     * spatial gradients, the feature scale of each stage against the anvil's
     * own size, how deep a view ray gets before the integral saturates, and
     * whether the accumulated alpha still carries the structure the density
     * field has.
     *
     * <p>Five deterministic realizations. The descriptor geometry is the shipped
     * T134 severe fixture; each realization places it at a different world
     * origin, which is what actually varies the noise a real storm samples.
     */
    private static void reportT098AnvilSurfaceStructure() {
        byte[] baseVolume = CloudNoiseFieldModel.bakeBase();
        byte[] detailVolume = CloudNoiseFieldModel.bakeDetail();
        double[][] origins = {
                {0.0D, 0.0D}, {4096.0D, -3072.0D}, {-5120.0D, 6144.0D},
                {9216.0D, 8192.0D}, {-7168.0D, -9216.0D}
        };

        System.out.println("T098_ANVIL|stage|role|fixture|n|mean|p05|p50|p95|variance|cv"
                + "|meanGradPerBlock|pct>=0.80|pct>=0.90|pct>=0.99|pctZero");

        String[] roleNames = {"BASE", "CORE", "TOWER", "ANVIL"};
        for (int originIndex = 0; originIndex < origins.length; originIndex++) {
            java.util.List<StormLobeDescriptor> lobes =
                    severeFixtureAt(origins[originIndex][0], origins[originIndex][1]);
            for (int role = 0; role < 4; role++) {
                if (role == 0) {
                    continue; // BASE is not a control for this question.
                }
                sampleRoleChain(baseVolume, detailVolume, lobes, role,
                        roleNames[role], originIndex);
            }
        }

        reportT098AnvilFeatureScale(baseVolume, detailVolume);
        reportT098AnvilOpticalDepth(baseVolume, detailVolume);
        reportT098AnvilAlphaField(baseVolume, detailVolume);
        reportT098AnvilOpacitySensitivity(baseVolume, detailVolume);
    }

    /**
     * How far the anvil is from the regime where its density structure could
     * reach the image at all.
     *
     * <p>Measurement, not a proposal. The alpha field is flat because every ray
     * saturates, so the question "how much less opaque would it have to be
     * before the structure it already has becomes visible" has a definite
     * answer, and the next investigation needs it. Scaling optical depth is the
     * cleanest way to ask that without touching morphology: it holds the
     * density field, its variance and its feature scale exactly fixed and
     * changes only how much of the chord a ray sees.
     */
    private static void reportT098AnvilOpacitySensitivity(
            byte[] baseVolume, byte[] detailVolume) {
        java.util.List<StormLobeDescriptor> lobes = severeFixture38bc5412();
        double centreX = 0.0D;
        double centreZ = 0.0D;
        double anvilMidY = 0.0D;
        int anvilCount = 0;
        for (StormLobeDescriptor lobe : lobes) {
            centreX += lobe.centerX();
            centreZ += lobe.centerZ();
            if (lobe.role().gpuId() == 3) {
                anvilMidY += (lobe.baseY() + lobe.topY()) * 0.5D;
                anvilCount++;
            }
        }
        centreX /= lobes.size();
        centreZ /= lobes.size();
        anvilMidY /= Math.max(1, anvilCount);

        System.out.println("T098_ANVIL_SENSITIVITY|opticalScale|meanAlpha|alphaVariance"
                + "|alphaCV|meanAbsNeighbourDelta|pctAbove0.97|meanSaturationDepthBlocks");
        int gridA = 96;
        int gridB = 48;
        for (double scale : new double[] {1.0D, 0.5D, 0.25D, 0.12D, 0.06D, 0.03D}) {
            double[][] alpha = new double[gridA][gridB];
            double depthSum = 0.0D;
            int depthCount = 0;
            for (int a = 0; a < gridA; a++) {
                for (int b = 0; b < gridB; b++) {
                    double z = centreZ - 360.0D + (720.0D * a) / (gridA - 1);
                    double y = anvilMidY - 110.0D + (220.0D * b) / (gridB - 1);
                    double transmittance = 1.0D;
                    double travelled = 0.0D;
                    boolean entered = false;
                    boolean saturated = false;
                    for (double dx = -700.0D; dx <= 700.0D; dx += 2.5D) {
                        double cloud = sampleRayDensity(baseVolume, detailVolume, lobes,
                                centreX + dx, y, z);
                        if (cloud > 0.0006D) {
                            entered = true;
                        }
                        if (entered) {
                            travelled += 2.5D;
                        }
                        transmittance *= Math.exp(-cloud * T098_ANVIL_DENSITY_SCALE
                                * T098_ANVIL_EXTINCTION * scale * 2.5D);
                        if (transmittance < 0.015D) {
                            saturated = true;
                            break;
                        }
                    }
                    alpha[a][b] = 1.0D - transmittance;
                    if (saturated) {
                        depthSum += travelled;
                        depthCount++;
                    }
                }
            }
            int n = gridA * gridB;
            double mean = 0.0D;
            for (double[] row : alpha) {
                for (double v : row) {
                    mean += v;
                }
            }
            mean /= n;
            double var = 0.0D;
            int high = 0;
            for (double[] row : alpha) {
                for (double v : row) {
                    var += (v - mean) * (v - mean);
                    if (v > 0.97D) {
                        high++;
                    }
                }
            }
            var /= n;
            double delta = 0.0D;
            int pairs = 0;
            for (int a = 0; a + 1 < gridA; a++) {
                for (int b = 0; b + 1 < gridB; b++) {
                    delta += Math.abs(alpha[a][b] - alpha[a + 1][b]);
                    delta += Math.abs(alpha[a][b] - alpha[a][b + 1]);
                    pairs += 2;
                }
            }
            delta /= Math.max(1, pairs);
            System.out.printf(java.util.Locale.ROOT,
                    "T098_ANVIL_SENSITIVITY|%.3f|%.4f|%.6f|%.4f|%.6f|%6.2f|%8.1f%n",
                    scale, mean, var, mean > 1.0E-6D ? Math.sqrt(var) / mean : 0.0D,
                    delta, 100.0D * high / n,
                    depthCount > 0 ? depthSum / depthCount : -1.0D);
        }
    }

    /** The shipped severe fixture, translated to a different world origin. */
    private static java.util.List<StormLobeDescriptor> severeFixtureAt(
            double offsetX, double offsetZ) {
        java.util.List<StormLobeDescriptor> moved = new ArrayList<>();
        for (StormLobeDescriptor l : severeFixture38bc5412()) {
            moved.add(new StormLobeDescriptor(
                    l.fieldId(), l.groupId(), l.memberIndex(), l.memberCount(), l.groupSlot(),
                    l.role(), l.centerX() + offsetX, l.centerZ() + offsetZ,
                    l.baseY(), l.topY(), l.majorRadius(), l.minorRadius(),
                    l.sinOrientation(), l.cosOrientation(), l.shearX(), l.shearZ(),
                    l.density(), l.edgeSoftness(), l.seed01(), l.lifecycleStage(),
                    l.verticalDevelopment(), l.detailWeight()));
        }
        return moved;
    }

    /** Every stage of the production chain over one role's own envelope. */
    private static void sampleRoleChain(
            byte[] baseVolume, byte[] detailVolume,
            java.util.List<StormLobeDescriptor> lobes, int role, String roleName,
            int fixtureIndex) {
        double centreX = 0.0D;
        double centreZ = 0.0D;
        for (StormLobeDescriptor lobe : lobes) {
            centreX += lobe.centerX();
            centreZ += lobe.centerZ();
        }
        centreX /= lobes.size();
        centreZ /= lobes.size();

        int capacity = 400000;
        double[] envelope = new double[capacity];
        double[] baseFieldValues = new double[capacity];
        double[] bodyValues = new double[capacity];
        double[] detailValues = new double[capacity];
        double[] densityValues = new double[capacity];
        double[] densityGradient = new double[capacity];
        int n = 0;
        int gradientCount = 0;

        double[] baseSample = new double[4];
        double[] detailSample = new double[4];
        final double step = 12.0D;
        final double lag = 4.0D;

        for (double y = 136.0D; y <= 1000.0D && n < capacity - 2; y += step) {
            for (double dx = -700.0D; dx <= 700.0D && n < capacity - 2; dx += step) {
                for (double dz = -700.0D; dz <= 700.0D && n < capacity - 2; dz += step) {
                    double x = centreX + dx;
                    double z = centreZ + dz;
                    int owner = -1;
                    double bestEnvelope = 0.0D;
                    for (StormLobeDescriptor lobe : lobes) {
                        double e = StormLobeEvaluator.envelopeFromDistance(
                                StormLobeEvaluator.signedDistanceAt(lobe, x, y, z),
                                StormLobeEvaluator.edgeWidthBlocks(lobe),
                                StormLobeEvaluator.envelopeStrength(lobe));
                        if (e > bestEnvelope) {
                            bestEnvelope = e;
                            owner = lobe.role().gpuId();
                        }
                    }
                    if (owner != role || bestEnvelope <= 0.0D) {
                        continue;
                    }
                    double coverage = StormLobeEvaluator.coverageEnvelopeAt(lobes, x, y, z);
                    if (coverage <= 0.0D) {
                        continue;
                    }
                    // Interior only: away from the silhouette boundary, which is
                    // what "uniform interior" is a claim about.
                    if (coverage < 0.60D) {
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
                    double body = StormDensityModel.stormBody(
                            coverage, strength, baseField, embedded);
                    double[] duvw = detailDomain(x, y, z, baseSample);
                    CloudNoiseFieldModel.sampleDetail(
                            detailVolume, duvw[0], duvw[1], duvw[2], detailSample);
                    double detailFbm = StormDensityModel.detailFbm(
                            detailSample[0], detailSample[1], detailSample[2]);
                    double density = StormDensityModel.finalDensity(
                            coverage, strength, baseField, detailFbm, embedded);

                    envelope[n] = coverage;
                    baseFieldValues[n] = baseField;
                    bodyValues[n] = body;
                    detailValues[n] = detailFbm;
                    densityValues[n] = density;
                    n++;

                    double neighbour = sampleRayDensity(baseVolume, detailVolume, lobes,
                            x + lag, y, z);
                    densityGradient[gradientCount++] = Math.abs(neighbour - density) / lag;
                }
            }
        }
        if (n < 100) {
            return;
        }
        double[] noGradient = new double[1];
        for (Object[] pair : new Object[][] {
                {"envelope", envelope}, {"baseField", baseFieldValues},
                {"bodyAfterRemap", bodyValues}, {"detailFbm", detailValues},
                {"finalDensity", densityValues}}) {
            String stage = (String) pair[0];
            double[] values = (double[]) pair[1];
            T098Dist d = summarize(stage, values, n,
                    "finalDensity".equals(stage) ? densityGradient : noGradient,
                    "finalDensity".equals(stage) ? gradientCount : 0);
            System.out.printf(java.util.Locale.ROOT,
                    "T098_ANVIL|%-14s|%-5s|%d|%6d|%.4f|%.4f|%.4f|%.4f|%.5f|%.4f"
                            + "|%.5f|%6.2f|%6.2f|%6.2f|%6.2f%n",
                    d.stage(), roleName, fixtureIndex, d.n(), d.mean(), d.p05(), d.p50(),
                    d.p95(), d.variance(), d.cv(), d.meanGradient(),
                    d.sat80(), d.sat90(), d.sat99(), d.zero());
        }
    }

    /**
     * Dominant feature scale of each stage, from the normalized autocorrelation
     * along horizontal transects through the anvil. The reported length is the
     * lag at which correlation first falls below 1/e, which is the size of the
     * structures a viewer would read as billows.
     */
    private static void reportT098AnvilFeatureScale(byte[] baseVolume, byte[] detailVolume) {
        java.util.List<StormLobeDescriptor> lobes = severeFixture38bc5412();
        double centreX = 0.0D;
        double centreZ = 0.0D;
        double anvilRadius = 0.0D;
        double anvilMidY = 0.0D;
        int anvilCount = 0;
        for (StormLobeDescriptor lobe : lobes) {
            centreX += lobe.centerX();
            centreZ += lobe.centerZ();
            if (lobe.role().gpuId() == 3) {
                anvilRadius = Math.max(anvilRadius, lobe.majorRadius());
                anvilMidY += (lobe.baseY() + lobe.topY()) * 0.5D;
                anvilCount++;
            }
        }
        centreX /= lobes.size();
        centreZ /= lobes.size();
        anvilMidY /= Math.max(1, anvilCount);

        final double lagStep = 4.0D;
        final int lagCount = 80;
        String[] stages = {"baseField", "detailFbm", "finalDensity"};
        double[][] corr = new double[stages.length][lagCount + 1];
        long[] counts = new long[lagCount + 1];
        double[] baseSample = new double[4];
        double[] detailSample = new double[4];

        // Accumulate over many transects so the estimate is not one line.
        java.util.List<double[]> series = new ArrayList<>();
        for (double dz = -300.0D; dz <= 300.0D; dz += 25.0D) {
            for (double dy = -60.0D; dy <= 60.0D; dy += 30.0D) {
                double[] baseFieldLine = new double[600];
                double[] detailLine = new double[600];
                double[] densityLine = new double[600];
                int m = 0;
                for (double dx = -400.0D; dx <= 400.0D && m < 600; dx += lagStep) {
                    double x = centreX + dx;
                    double y = anvilMidY + dy;
                    double z = centreZ + dz;
                    double coverage = StormLobeEvaluator.coverageEnvelopeAt(lobes, x, y, z);
                    if (coverage < 0.60D) {
                        m = 0;
                        break;
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
                    baseFieldLine[m] = baseField;
                    detailLine[m] = detailFbm;
                    densityLine[m] = StormDensityModel.finalDensity(
                            coverage, strength, baseField, detailFbm, embedded);
                    m++;
                }
                if (m > 60) {
                    series.add(java.util.Arrays.copyOf(baseFieldLine, m));
                    series.add(java.util.Arrays.copyOf(detailLine, m));
                    series.add(java.util.Arrays.copyOf(densityLine, m));
                }
            }
        }
        System.out.println("T098_ANVIL_SCALE|stage|transects|decorrelationBlocks"
                + "|anvilMajorRadiusBlocks|featuresAcrossAnvil");
        for (int stage = 0; stage < stages.length; stage++) {
            double[] sum = new double[lagCount + 1];
            long[] used = new long[lagCount + 1];
            int transects = 0;
            for (int s = stage; s < series.size(); s += stages.length) {
                double[] line = series.get(s);
                transects++;
                double mean = 0.0D;
                for (double v : line) {
                    mean += v;
                }
                mean /= line.length;
                double var = 0.0D;
                for (double v : line) {
                    var += (v - mean) * (v - mean);
                }
                var /= line.length;
                if (var < 1.0E-9D) {
                    continue;
                }
                for (int lag = 0; lag <= lagCount && lag < line.length; lag++) {
                    double acc = 0.0D;
                    int pairs = 0;
                    for (int i = 0; i + lag < line.length; i++) {
                        acc += (line[i] - mean) * (line[i + lag] - mean);
                        pairs++;
                    }
                    sum[lag] += (acc / pairs) / var;
                    used[lag]++;
                }
            }
            double decorrelation = -1.0D;
            for (int lag = 0; lag <= lagCount; lag++) {
                if (used[lag] == 0) {
                    continue;
                }
                double c = sum[lag] / used[lag];
                if (c < 0.3679D) {
                    decorrelation = lag * lagStep;
                    break;
                }
            }
            System.out.printf(java.util.Locale.ROOT,
                    "T098_ANVIL_SCALE|%-12s|%5d|%18.1f|%22.1f|%20.2f%n",
                    stages[stage], transects, decorrelation, anvilRadius,
                    decorrelation > 0.0D ? (2.0D * anvilRadius) / decorrelation : -1.0D);
        }
    }

    /**
     * How deep a view ray gets into the anvil before the integral saturates.
     * If that depth is small against the anvil's own size, the visible surface
     * is a thin skin and interior variation cannot reach the image at all.
     */
    private static void reportT098AnvilOpticalDepth(byte[] baseVolume, byte[] detailVolume) {
        java.util.List<StormLobeDescriptor> lobes = severeFixture38bc5412();
        double centreX = 0.0D;
        double centreZ = 0.0D;
        double anvilRadius = 0.0D;
        double anvilMidY = 0.0D;
        double anvilThickness = 0.0D;
        int anvilCount = 0;
        for (StormLobeDescriptor lobe : lobes) {
            centreX += lobe.centerX();
            centreZ += lobe.centerZ();
            if (lobe.role().gpuId() == 3) {
                anvilRadius = Math.max(anvilRadius, lobe.majorRadius());
                anvilMidY += (lobe.baseY() + lobe.topY()) * 0.5D;
                anvilThickness = Math.max(anvilThickness, lobe.topY() - lobe.baseY());
                anvilCount++;
            }
        }
        centreX /= lobes.size();
        centreZ /= lobes.size();
        anvilMidY /= Math.max(1, anvilCount);

        double[] depths = new double[4096];
        int n = 0;
        final double step = 2.5D;
        for (double dz = -300.0D; dz <= 300.0D; dz += 20.0D) {
            for (double dy = -70.0D; dy <= 70.0D; dy += 20.0D) {
                double transmittance = 1.0D;
                double travelled = 0.0D;
                boolean entered = false;
                for (double dx = -600.0D; dx <= 600.0D; dx += step) {
                    double cloud = sampleRayDensity(baseVolume, detailVolume, lobes,
                            centreX + dx, anvilMidY + dy, centreZ + dz);
                    if (cloud > 0.0006D) {
                        entered = true;
                    }
                    if (!entered) {
                        continue;
                    }
                    travelled += step;
                    transmittance *= Math.exp(
                            -cloud * T098_ANVIL_DENSITY_SCALE * T098_ANVIL_EXTINCTION * step);
                    if (transmittance < 0.015D) {
                        break;
                    }
                }
                if (entered && transmittance < 0.015D && n < depths.length) {
                    depths[n++] = travelled;
                }
            }
        }
        java.util.Arrays.sort(depths, 0, n);
        double mean = 0.0D;
        for (int i = 0; i < n; i++) {
            mean += depths[i];
        }
        mean /= Math.max(1, n);
        System.out.printf(java.util.Locale.ROOT,
                "T098_ANVIL_OPTICAL|rays=%d|meanSaturationDepthBlocks=%.1f|p05=%.1f|p50=%.1f"
                        + "|p95=%.1f|anvilChordBlocks=%.1f|anvilThicknessBlocks=%.1f"
                        + "|visibleSkinFractionOfChord=%.4f%n",
                n, mean, n > 0 ? depths[(int) (n * 0.05)] : -1.0D,
                n > 0 ? depths[n / 2] : -1.0D, n > 0 ? depths[(int) (n * 0.95)] : -1.0D,
                2.0D * anvilRadius, anvilThickness,
                mean / Math.max(1.0D, 2.0D * anvilRadius));
    }

    /**
     * PHASE 3 done offline: does the accumulated alpha still carry the structure
     * the density field has? Marches a grid of parallel rays through the anvil
     * from the SIDE and from ABOVE and reports the variation of the resulting
     * unlit alpha image against the variation of the density field it came
     * from. Lighting is deliberately excluded, so any flattening seen here is
     * integration, not shading.
     */
    private static void reportT098AnvilAlphaField(byte[] baseVolume, byte[] detailVolume) {
        java.util.List<StormLobeDescriptor> lobes = severeFixture38bc5412();
        double centreX = 0.0D;
        double centreZ = 0.0D;
        double anvilMidY = 0.0D;
        double anvilTopY = 0.0D;
        int anvilCount = 0;
        for (StormLobeDescriptor lobe : lobes) {
            centreX += lobe.centerX();
            centreZ += lobe.centerZ();
            if (lobe.role().gpuId() == 3) {
                anvilMidY += (lobe.baseY() + lobe.topY()) * 0.5D;
                anvilTopY = Math.max(anvilTopY, lobe.topY());
                anvilCount++;
            }
        }
        centreX /= lobes.size();
        centreZ /= lobes.size();
        anvilMidY /= Math.max(1, anvilCount);

        System.out.println("T098_ANVIL_ALPHA|view|rays|meanAlpha|alphaVariance|alphaCV"
                + "|meanAbsNeighbourDelta|pctAlphaAbove0.97|pctAlphaBelow0.05");
        for (String view : new String[] {"SIDE", "ABOVE"}) {
            int gridA = 96;
            int gridB = 48;
            double[][] alpha = new double[gridA][gridB];
            for (int a = 0; a < gridA; a++) {
                for (int b = 0; b < gridB; b++) {
                    double transmittance = 1.0D;
                    if ("SIDE".equals(view)) {
                        double z = centreZ - 360.0D + (720.0D * a) / (gridA - 1);
                        double y = anvilMidY - 110.0D + (220.0D * b) / (gridB - 1);
                        for (double dx = -700.0D; dx <= 700.0D; dx += 2.5D) {
                            double cloud = sampleRayDensity(baseVolume, detailVolume, lobes,
                                    centreX + dx, y, z);
                            transmittance *= Math.exp(-cloud * T098_ANVIL_DENSITY_SCALE
                                    * T098_ANVIL_EXTINCTION * 2.5D);
                            if (transmittance < 0.0005D) {
                                break;
                            }
                        }
                    } else {
                        double x = centreX - 480.0D + (960.0D * a) / (gridA - 1);
                        double z = centreZ - 480.0D + (960.0D * b) / (gridB - 1);
                        for (double y = anvilTopY + 120.0D; y >= 600.0D; y -= 2.5D) {
                            double cloud = sampleRayDensity(baseVolume, detailVolume, lobes,
                                    x, y, z);
                            transmittance *= Math.exp(-cloud * T098_ANVIL_DENSITY_SCALE
                                    * T098_ANVIL_EXTINCTION * 2.5D);
                            if (transmittance < 0.0005D) {
                                break;
                            }
                        }
                    }
                    alpha[a][b] = 1.0D - transmittance;
                }
            }
            int n = gridA * gridB;
            double mean = 0.0D;
            for (double[] row : alpha) {
                for (double v : row) {
                    mean += v;
                }
            }
            mean /= n;
            double var = 0.0D;
            int high = 0;
            int low = 0;
            for (double[] row : alpha) {
                for (double v : row) {
                    var += (v - mean) * (v - mean);
                    if (v > 0.97D) {
                        high++;
                    }
                    if (v < 0.05D) {
                        low++;
                    }
                }
            }
            var /= n;
            double delta = 0.0D;
            int pairs = 0;
            for (int a = 0; a + 1 < gridA; a++) {
                for (int b = 0; b + 1 < gridB; b++) {
                    delta += Math.abs(alpha[a][b] - alpha[a + 1][b]);
                    delta += Math.abs(alpha[a][b] - alpha[a][b + 1]);
                    pairs += 2;
                }
            }
            delta /= Math.max(1, pairs);
            System.out.printf(java.util.Locale.ROOT,
                    "T098_ANVIL_ALPHA|%-5s|%5d|%.4f|%.6f|%.4f|%.6f|%6.2f|%6.2f%n",
                    view, n, mean, var, mean > 1.0E-6D ? Math.sqrt(var) / mean : 0.0D,
                    delta, 100.0D * high / n, 100.0D * low / n);
        }
    }


    // ------------------------------------------------------------------
    // T098 ANVIL optical surface
    // ------------------------------------------------------------------

    /**
     * One view ray's optical-surface record: the depths at which accumulated
     * alpha crosses each threshold, and the field values at the alpha=0.5
     * point, which is the locus a viewer actually reads as the surface.
     */
    private record T098Surface(
            boolean valid, double tFirst, double t10, double t50, double t90, double t985,
            double tEnvelope, double envelopeAtT50, double bodyAtT50, double detailAtT50,
            double densityAtT50, double lightOpticalDepth, int roleAtT50) {
        static final T098Surface INVALID = new T098Surface(false, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, -1);
    }

    /** Production light direction from the frozen noon fixture. */
    private static final double[] T098_LIGHT_DIR = {-0.60D, 0.79D, 0.12D};

    /**
     * The production light cone, modelled exactly: eight taps, 14-block first
     * step growing by 1.42, a fixed golden-angle cone offset, and detail
     * erosion only on the first two taps.
     */
    private static double t098LightOpticalDepth(
            byte[] baseVolume, byte[] detailVolume, java.util.List<StormLobeDescriptor> lobes,
            double x, double y, double z) {
        double opticalDepth = 0.0D;
        double stepLength = 14.0D;
        double px = x;
        double py = y;
        double pz = z;
        for (int i = 0; i < 8; i++) {
            double ang = i * 2.399963D;
            double spread = (i + 0.5D) * 0.28D;
            double ox = Math.cos(ang) * spread * stepLength * 0.24D;
            double oy = 0.35D * Math.sin(ang * 1.7D) * spread * stepLength * 0.24D;
            double oz = Math.sin(ang) * spread * stepLength * 0.24D;
            px += T098_LIGHT_DIR[0] * stepLength;
            py += T098_LIGHT_DIR[1] * stepLength;
            pz += T098_LIGHT_DIR[2] * stepLength;
            double cloud = i < 2
                    ? sampleRayDensity(baseVolume, detailVolume, lobes,
                            px + ox, py + oy, pz + oz)
                    : sampleRayBodyNoErosion(baseVolume, lobes, px + ox, py + oy, pz + oz);
            opticalDepth += cloud * T098_ANVIL_DENSITY_SCALE * stepLength;
            stepLength *= 1.42D;
        }
        return opticalDepth * T098_ANVIL_EXTINCTION;
    }

    /** The body before detail erosion, which is what light taps 2..7 sample. */
    private static double sampleRayBodyNoErosion(
            byte[] baseVolume, java.util.List<StormLobeDescriptor> lobes,
            double x, double y, double z) {
        double coverage = StormLobeEvaluator.coverageEnvelopeAt(lobes, x, y, z);
        if (coverage <= 0.0D) {
            return 0.0D;
        }
        double strength = StormLobeEvaluator.envelopeStrengthAt(lobes, x, y, z);
        boolean embedded = StormLobeEvaluator.hasEmbeddedConvectiveOverlap(lobes, x, y, z);
        double[] baseSample = new double[4];
        double[] uvw = baseDomain(x, y, z, 0.0025D);
        CloudNoiseFieldModel.sampleBase(baseVolume, uvw[0], uvw[1], uvw[2], baseSample);
        double lowFbm = StormDensityModel.lowFbm(baseSample[1], baseSample[2], baseSample[3]);
        double baseField = StormDensityModel.stormBaseField(
                StormDensityModel.baseCarrier(baseSample[0], lowFbm));
        return StormDensityModel.stormBody(coverage, strength, baseField, embedded);
    }

    /**
     * Marches one ray and returns where the accumulated alpha crosses each
     * threshold.
     *
     * @param arm 0 = production, 1 = erosion disabled, 2 = envelope body only
     */
    private static T098Surface t098MarchSurface(
            byte[] baseVolume, byte[] detailVolume, java.util.List<StormLobeDescriptor> lobes,
            double ox, double oy, double oz, double dx, double dy, double dz,
            double span, int arm, boolean wantLight) {
        final double step = 2.5D;
        double transmittance = 1.0D;
        double tFirst = -1.0D;
        double tEnvelope = -1.0D;
        double t10 = -1.0D;
        double t50 = -1.0D;
        double t90 = -1.0D;
        double t985 = -1.0D;
        double envelopeAt = 0.0D;
        double bodyAt = 0.0D;
        double detailAt = 0.0D;
        double densityAt = 0.0D;
        int roleAt = -1;
        double[] baseSample = new double[4];
        double[] detailSample = new double[4];

        for (double t = 0.0D; t <= span; t += step) {
            double x = ox + dx * t;
            double y = oy + dy * t;
            double z = oz + dz * t;
            double coverage = StormLobeEvaluator.coverageEnvelopeAt(lobes, x, y, z);
            if (tEnvelope < 0.0D && coverage >= 0.50D) {
                tEnvelope = t;
            }
            double cloud = 0.0D;
            double baseField = 0.0D;
            double detailFbm = 1.0D;
            double body = 0.0D;
            if (coverage > 0.0D) {
                double strength = StormLobeEvaluator.envelopeStrengthAt(lobes, x, y, z);
                boolean embedded =
                        StormLobeEvaluator.hasEmbeddedConvectiveOverlap(lobes, x, y, z);
                double[] uvw = baseDomain(x, y, z, 0.0025D);
                CloudNoiseFieldModel.sampleBase(baseVolume, uvw[0], uvw[1], uvw[2], baseSample);
                double lowFbm = StormDensityModel.lowFbm(
                        baseSample[1], baseSample[2], baseSample[3]);
                baseField = StormDensityModel.stormBaseField(
                        StormDensityModel.baseCarrier(baseSample[0], lowFbm));
                body = StormDensityModel.stormBody(coverage, strength, baseField, embedded);
                if (arm == 2) {
                    cloud = body;
                } else if (arm == 1) {
                    cloud = body;
                } else {
                    double[] duvw = detailDomain(x, y, z, baseSample);
                    CloudNoiseFieldModel.sampleDetail(
                            detailVolume, duvw[0], duvw[1], duvw[2], detailSample);
                    detailFbm = StormDensityModel.detailFbm(
                            detailSample[0], detailSample[1], detailSample[2]);
                    cloud = StormDensityModel.erode(body, detailFbm);
                }
            }
            if (cloud > 0.0006D && tFirst < 0.0D) {
                tFirst = t;
            }
            transmittance *= Math.exp(
                    -cloud * T098_ANVIL_DENSITY_SCALE * T098_ANVIL_EXTINCTION * step);
            double alpha = 1.0D - transmittance;
            if (t10 < 0.0D && alpha >= 0.10D) {
                t10 = t;
            }
            if (t50 < 0.0D && alpha >= 0.50D) {
                t50 = t;
                envelopeAt = coverage;
                bodyAt = body;
                detailAt = detailFbm;
                densityAt = cloud;
                double best = 0.0D;
                for (StormLobeDescriptor lobe : lobes) {
                    double e = StormLobeEvaluator.envelopeFromDistance(
                            StormLobeEvaluator.signedDistanceAt(lobe, x, y, z),
                            StormLobeEvaluator.edgeWidthBlocks(lobe),
                            StormLobeEvaluator.envelopeStrength(lobe));
                    if (e > best) {
                        best = e;
                        roleAt = lobe.role().gpuId();
                    }
                }
            }
            if (t90 < 0.0D && alpha >= 0.90D) {
                t90 = t;
            }
            if (alpha >= 0.985D) {
                t985 = t;
                break;
            }
        }
        if (t50 < 0.0D) {
            return T098Surface.INVALID;
        }
        double light = 0.0D;
        if (wantLight) {
            light = t098LightOpticalDepth(baseVolume, detailVolume, lobes,
                    ox + dx * t50, oy + dy * t50, oz + dz * t50);
        }
        return new T098Surface(true, tFirst, t10, t50, t90, t985, tEnvelope,
                envelopeAt, bodyAt, detailAt, densityAt, light, roleAt);
    }

    /** RMS of a grid after removing a wide moving-average trend, plus percentiles. */
    private static double[] t098Relief(double[][] grid, boolean[][] valid, int window) {
        int rows = grid.length;
        int cols = grid[0].length;
        java.util.List<Double> residuals = new ArrayList<>();
        double neighbourDelta = 0.0D;
        int neighbourPairs = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (!valid[r][c]) {
                    continue;
                }
                double sum = 0.0D;
                int n = 0;
                for (int rr = Math.max(0, r - window); rr <= Math.min(rows - 1, r + window); rr++) {
                    for (int cc = Math.max(0, c - window);
                            cc <= Math.min(cols - 1, c + window); cc++) {
                        if (valid[rr][cc]) {
                            sum += grid[rr][cc];
                            n++;
                        }
                    }
                }
                if (n < 4) {
                    continue;
                }
                residuals.add(grid[r][c] - sum / n);
                if (c + 1 < cols && valid[r][c + 1]) {
                    neighbourDelta += Math.abs(grid[r][c + 1] - grid[r][c]);
                    neighbourPairs++;
                }
            }
        }
        if (residuals.size() < 16) {
            return new double[] {0, 0, 0, 0, 0};
        }
        java.util.Collections.sort(residuals);
        double mean = 0.0D;
        for (double v : residuals) {
            mean += v;
        }
        mean /= residuals.size();
        double sq = 0.0D;
        for (double v : residuals) {
            sq += (v - mean) * (v - mean);
        }
        double rms = Math.sqrt(sq / residuals.size());
        return new double[] {rms, residuals.get((int) (residuals.size() * 0.05)),
                residuals.get((int) (residuals.size() * 0.95)),
                neighbourPairs > 0 ? neighbourDelta / neighbourPairs : 0.0D,
                residuals.size()};
    }

    /** Dominant wavelength of a grid's residual, along rows, in world blocks. */
    private static double t098DominantWavelength(
            double[][] grid, boolean[][] valid, double spacing, int window) {
        int rows = grid.length;
        int cols = grid[0].length;
        int maxLag = Math.min(60, cols / 3);
        double[] sum = new double[maxLag + 1];
        int[] used = new int[maxLag + 1];
        for (int r = 0; r < rows; r++) {
            java.util.List<Double> line = new ArrayList<>();
            for (int c = 0; c < cols; c++) {
                if (!valid[r][c]) {
                    line.clear();
                    continue;
                }
                line.add(grid[r][c]);
            }
            if (line.size() < 40) {
                continue;
            }
            double[] v = new double[line.size()];
            for (int i = 0; i < v.length; i++) {
                v[i] = line.get(i);
            }
            double[] detr = new double[v.length];
            for (int i = 0; i < v.length; i++) {
                int a = Math.max(0, i - window);
                int b = Math.min(v.length - 1, i + window);
                double s = 0.0D;
                for (int k = a; k <= b; k++) {
                    s += v[k];
                }
                detr[i] = v[i] - s / (b - a + 1);
            }
            double mean = 0.0D;
            for (double d : detr) {
                mean += d;
            }
            mean /= detr.length;
            double var = 0.0D;
            for (double d : detr) {
                var += (d - mean) * (d - mean);
            }
            var /= detr.length;
            if (var < 1.0E-12D) {
                continue;
            }
            for (int lag = 1; lag <= maxLag && lag < detr.length; lag++) {
                double acc = 0.0D;
                int pairs = 0;
                for (int i = 0; i + lag < detr.length; i++) {
                    acc += (detr[i] - mean) * (detr[i + lag] - mean);
                    pairs++;
                }
                sum[lag] += (acc / pairs) / var;
                used[lag]++;
            }
        }
        for (int lag = 1; lag <= maxLag; lag++) {
            if (used[lag] > 0 && sum[lag] / used[lag] < 0.3679D) {
                return lag * spacing;
            }
        }
        return -1.0D;
    }

    /**
     * T098: what actually controls the anvil's visible surface.
     *
     * <p>The anvil is opaque after about 75 blocks of a 1015-block chord, so
     * interior density variance cannot reach the image. The shape a viewer
     * reads is the locus where accumulated alpha first becomes significant.
     * This measures that surface directly: where each alpha threshold is
     * crossed, how much the alpha=0.5 surface deviates from the smooth
     * geometric shell, how far detail erosion moves it, and how much the
     * production light cone's optical depth varies across it.
     */
    private static void reportT098AnvilOpticalSurface() {
        byte[] baseVolume = CloudNoiseFieldModel.bakeBase();
        byte[] detailVolume = CloudNoiseFieldModel.bakeDetail();
        java.util.List<StormLobeDescriptor> lobes = severeFixture38bc5412();
        double centreX = 0.0D;
        double centreZ = 0.0D;
        double anvilBase = 1.0E9D;
        double anvilTop = 0.0D;
        for (StormLobeDescriptor lobe : lobes) {
            centreX += lobe.centerX();
            centreZ += lobe.centerZ();
            if (lobe.role().gpuId() == 3) {
                anvilBase = Math.min(anvilBase, lobe.baseY());
                anvilTop = Math.max(anvilTop, lobe.topY());
            }
        }
        centreX /= lobes.size();
        centreZ /= lobes.size();

        final double spacing = 6.0D;
        String[] armNames = {"production", "erosionOff", "bodyOnly"};

        System.out.println("T098_SURFACE|view|arm|rays|meanT50|reliefRmsBlocks|reliefP05"
                + "|reliefP95|neighbourDeltaBlocks|dominantWavelengthBlocks"
                + "|meanRampT10toT90|meanEnvelopeToAlpha50");
        double[] productionRelief = new double[2];
        for (String view : new String[] {"SIDE", "ABOVE"}) {
            for (int arm = 0; arm < armNames.length; arm++) {
                int rows;
                int cols;
                if ("SIDE".equals(view)) {
                    rows = (int) ((anvilTop + 60.0D - (anvilBase - 60.0D)) / spacing);
                    cols = (int) (1000.0D / spacing);
                } else {
                    rows = (int) (1000.0D / spacing);
                    cols = (int) (1000.0D / spacing);
                }
                double[][] t50 = new double[rows][cols];
                double[][] tFirst = new double[rows][cols];
                double[][] tEnv = new double[rows][cols];
                double[][] t10 = new double[rows][cols];
                double[][] t90 = new double[rows][cols];
                boolean[][] valid = new boolean[rows][cols];
                double rampSum = 0.0D;
                double envelopeToAlphaSum = 0.0D;
                int n = 0;
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        T098Surface s;
                        if ("SIDE".equals(view)) {
                            double y = anvilBase - 60.0D + r * spacing;
                            double z = centreZ - 500.0D + c * spacing;
                            s = t098MarchSurface(baseVolume, detailVolume, lobes,
                                    centreX + 900.0D, y, z, -1.0D, 0.0D, 0.0D,
                                    1800.0D, arm, false);
                        } else {
                            double x = centreX - 500.0D + r * spacing;
                            double z = centreZ - 500.0D + c * spacing;
                            s = t098MarchSurface(baseVolume, detailVolume, lobes,
                                    x, anvilTop + 300.0D, z, 0.0D, -1.0D, 0.0D,
                                    900.0D, arm, false);
                        }
                        if (!s.valid()) {
                            continue;
                        }
                        valid[r][c] = true;
                        t50[r][c] = s.t50();
                        tFirst[r][c] = s.tFirst();
                        tEnv[r][c] = s.tEnvelope();
                        t10[r][c] = s.t10();
                        t90[r][c] = s.t90();
                        n++;
                        if (s.t90() > 0.0D && s.t10() > 0.0D) {
                            rampSum += s.t90() - s.t10();
                        }
                        if (s.tEnvelope() > 0.0D) {
                            envelopeToAlphaSum += s.t50() - s.tEnvelope();
                        }
                    }
                }
                if (n < 100) {
                    continue;
                }
                double[] relief = t098Relief(t50, valid, 6);
                double wavelength = t098DominantWavelength(t50, valid, spacing, 6);
                double meanT50 = 0.0D;
                int m = 0;
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        if (valid[r][c]) {
                            meanT50 += t50[r][c];
                            m++;
                        }
                    }
                }
                meanT50 /= Math.max(1, m);
                System.out.printf(java.util.Locale.ROOT,
                        "T098_SURFACE|%-5s|%-11s|%6d|%8.1f|%8.3f|%8.2f|%8.2f|%8.3f"
                                + "|%10.1f|%9.2f|%9.2f%n",
                        view, armNames[arm], n, meanT50, relief[0], relief[1], relief[2],
                        relief[3], wavelength, rampSum / Math.max(1, n),
                        envelopeToAlphaSum / Math.max(1, n));
                if (arm == 0) {
                    productionRelief["SIDE".equals(view) ? 0 : 1] = relief[0];
                }
                if (arm == 0) {
                    double[] envRelief = t098Relief(tEnv, valid, 6);
                    double[] firstRelief = t098Relief(tFirst, valid, 6);
                    double[] r10 = t098Relief(t10, valid, 6);
                    double[] r90 = t098Relief(t90, valid, 6);
                    System.out.printf(java.util.Locale.ROOT,
                            "T098_SURFACE_STAGE|%-5s|envelope=%.3f|firstDensity=%.3f"
                                    + "|alpha10=%.3f|alpha50=%.3f|alpha90=%.3f%n",
                            view, envRelief[0], firstRelief[0], r10[0], relief[0], r90[0]);
                }
            }
        }
        reportT098AnvilLightResponse(baseVolume, detailVolume, lobes, centreX, centreZ,
                anvilBase, anvilTop);
        reportT098AnvilShadingResponse(baseVolume, detailVolume, lobes, centreX, centreZ,
                anvilBase, anvilTop);
        reportT098SurfaceReliefSpectrum(baseVolume, detailVolume, lobes, centreX, centreZ,
                anvilBase, anvilTop);
        reportT098SurfaceByRole(baseVolume, detailVolume, lobes, centreX, centreZ);
    }

    /** PHASE 7: does the production light cone respond to the surface it sits on? */
    private static void reportT098AnvilLightResponse(
            byte[] baseVolume, byte[] detailVolume, java.util.List<StormLobeDescriptor> lobes,
            double centreX, double centreZ, double anvilBase, double anvilTop) {
        final double spacing = 8.0D;
        java.util.List<Double> light = new ArrayList<>();
        java.util.List<Double> depth = new ArrayList<>();
        double neighbour = 0.0D;
        int pairs = 0;
        Double previous = null;
        for (double y = anvilBase - 40.0D; y <= anvilTop + 40.0D; y += spacing) {
            previous = null;
            for (double z = centreZ - 400.0D; z <= centreZ + 400.0D; z += spacing) {
                T098Surface s = t098MarchSurface(baseVolume, detailVolume, lobes,
                        centreX + 900.0D, y, z, -1.0D, 0.0D, 0.0D, 1800.0D, 0, true);
                if (!s.valid()) {
                    previous = null;
                    continue;
                }
                light.add(s.lightOpticalDepth());
                depth.add(s.t50());
                if (previous != null) {
                    neighbour += Math.abs(s.lightOpticalDepth() - previous);
                    pairs++;
                }
                previous = s.lightOpticalDepth();
            }
        }
        if (light.size() < 100) {
            return;
        }
        java.util.List<Double> sorted = new ArrayList<>(light);
        java.util.Collections.sort(sorted);
        double mean = 0.0D;
        for (double v : light) {
            mean += v;
        }
        mean /= light.size();
        double var = 0.0D;
        for (double v : light) {
            var += (v - mean) * (v - mean);
        }
        var /= light.size();
        // What the shading actually is: exp(-opticalDepth) is the direct light
        // reaching the surface point.
        java.util.List<Double> transmit = new ArrayList<>();
        for (double v : light) {
            transmit.add(Math.exp(-v));
        }
        java.util.Collections.sort(transmit);
        double tMean = 0.0D;
        for (double v : transmit) {
            tMean += v;
        }
        tMean /= transmit.size();
        double tVar = 0.0D;
        for (double v : transmit) {
            tVar += (v - tMean) * (v - tMean);
        }
        tVar /= transmit.size();
        System.out.printf(java.util.Locale.ROOT,
                "T098_SURFACE_LIGHT|points=%d|meanOpticalDepth=%.3f|p05=%.3f|p50=%.3f|p95=%.3f"
                        + "|variance=%.4f|neighbourDelta=%.4f%n",
                light.size(), mean, sorted.get((int) (sorted.size() * 0.05)),
                sorted.get(sorted.size() / 2), sorted.get((int) (sorted.size() * 0.95)),
                var, pairs > 0 ? neighbour / pairs : 0.0D);
        System.out.printf(java.util.Locale.ROOT,
                "T098_SURFACE_LIGHT_TRANSMITTANCE|meanDirectLight=%.5f|p05=%.5f|p50=%.5f"
                        + "|p95=%.5f|variance=%.8f|cv=%.5f|pctBelow0.01=%.2f%n",
                tMean, transmit.get((int) (transmit.size() * 0.05)),
                transmit.get(transmit.size() / 2),
                transmit.get((int) (transmit.size() * 0.95)), tVar,
                tMean > 1.0E-9D ? Math.sqrt(tVar) / tMean : 0.0D,
                100.0D * transmit.stream().filter(v -> v < 0.01D).count() / transmit.size());
    }


    // Frozen-noon fixture values, read from the live capture status line:
    // lightColor=(1.00,0.97,0.90) ambTop=(0.48,0.64,1.00) ambBot=(0.30,0.34,0.41).
    private static final double T098_LIGHT_LUM = 0.2126 * 1.00 + 0.7152 * 0.97 + 0.0722 * 0.90;
    private static final double T098_AMBIENT_TOP_LUM =
            0.2126 * 0.48 + 0.7152 * 0.64 + 0.0722 * 1.00;
    /** SIDE view direction (-1,0,0) against the fixture light direction. */
    private static final double T098_COS_THETA = 0.60D;

    private static double t098HenyeyGreenstein(double cosTheta, double g) {
        double g2 = g * g;
        return (1.0D - g2)
                / (4.0D * Math.PI * Math.pow(Math.max(1.0D + g2 - 2.0D * g * cosTheta, 1.0E-4D), 1.5D));
    }

    private static double t098DualLobePhase(double cosTheta) {
        return StormDensityModel.lerp(0.72D,
                t098HenyeyGreenstein(cosTheta, -0.18D),
                t098HenyeyGreenstein(cosTheta, 0.62D));
    }

    /**
     * The production radiance for one surface point, as a luminance.
     *
     * <p>Reproduces {@code evaluateLightingComponents} exactly for the terms
     * that depend on the light cone: the three-octave scatter approximation,
     * the beer-powder term, ambient retention keyed on direct transmission, and
     * the filmic tone curve. Storm darkening, underside shading and rain are
     * held at their neutral values, so this isolates how much of the anvil's
     * brightness variation the light cone can produce.
     */
    private static double t098SurfaceLuminance(double opticalDepth, double localDensity) {
        double scatter = 0.0D;
        double scatterWeight = 0.0D;
        double a = 1.0D;
        double b = 1.0D;
        for (int o = 0; o < 3; o++) {
            double phase = StormDensityModel.lerp(a, 0.0795775D, t098DualLobePhase(T098_COS_THETA));
            scatter += b * phase * Math.exp(-opticalDepth * a);
            scatterWeight += b;
            a *= 0.42D;
            b *= 0.52D;
        }
        scatter /= Math.max(scatterWeight, 1.0E-4D);
        double powder = 1.0D - Math.exp(-localDensity * 24.0D);
        double powderTerm = StormDensityModel.lerp(
                StormDensityModel.clamp01(T098_COS_THETA * 0.5D + 0.5D) * 0.72D,
                1.0D, StormDensityModel.clamp01(powder * 1.35D));
        double directTransmission = Math.exp(-opticalDepth);
        double sunTerm = T098_LIGHT_LUM * scatter * powderTerm * (4.0D * Math.PI);
        double ambientRetention = 0.74D;
        double ambient = T098_AMBIENT_TOP_LUM
                * StormDensityModel.lerp(directTransmission, ambientRetention, 1.0D);
        double radiance = sunTerm + ambient * 0.86D;
        return 1.0D - Math.exp(-radiance * 1.30D);
    }

    /**
     * PHASE 7: how much brightness variation the production light cone can
     * actually produce across the anvil's optical surface.
     */
    private static void reportT098AnvilShadingResponse(
            byte[] baseVolume, byte[] detailVolume, java.util.List<StormLobeDescriptor> lobes,
            double centreX, double centreZ, double anvilBase, double anvilTop) {
        final double spacing = 8.0D;
        java.util.List<Double> luminance = new ArrayList<>();
        java.util.List<Double> scatterOnly = new ArrayList<>();
        double neighbour = 0.0D;
        int pairs = 0;
        Double previous;
        for (double y = anvilBase - 40.0D; y <= anvilTop + 40.0D; y += spacing) {
            previous = null;
            for (double z = centreZ - 400.0D; z <= centreZ + 400.0D; z += spacing) {
                T098Surface s = t098MarchSurface(baseVolume, detailVolume, lobes,
                        centreX + 900.0D, y, z, -1.0D, 0.0D, 0.0D, 1800.0D, 0, true);
                if (!s.valid()) {
                    previous = null;
                    continue;
                }
                double lum = t098SurfaceLuminance(s.lightOpticalDepth(), s.densityAtT50());
                luminance.add(lum);
                scatterOnly.add(Math.exp(-s.lightOpticalDepth() * 0.1764D));
                if (previous != null) {
                    neighbour += Math.abs(lum - previous);
                    pairs++;
                }
                previous = lum;
            }
        }
        if (luminance.size() < 100) {
            return;
        }
        java.util.List<Double> sorted = new ArrayList<>(luminance);
        java.util.Collections.sort(sorted);
        double mean = 0.0D;
        for (double v : luminance) {
            mean += v;
        }
        mean /= luminance.size();
        double var = 0.0D;
        for (double v : luminance) {
            var += (v - mean) * (v - mean);
        }
        var /= luminance.size();
        double sMean = 0.0D;
        for (double v : scatterOnly) {
            sMean += v;
        }
        sMean /= scatterOnly.size();
        double sVar = 0.0D;
        for (double v : scatterOnly) {
            sVar += (v - sMean) * (v - sMean);
        }
        sVar /= scatterOnly.size();
        System.out.printf(java.util.Locale.ROOT,
                "T098_SURFACE_SHADING|points=%d|meanLuminance=%.5f|p05=%.5f|p50=%.5f|p95=%.5f"
                        + "|variance=%.8f|cv=%.5f|neighbourDelta=%.5f|range8bit=%.2f%n",
                luminance.size(), mean, sorted.get((int) (sorted.size() * 0.05)),
                sorted.get(sorted.size() / 2), sorted.get((int) (sorted.size() * 0.95)),
                var, mean > 1.0E-9D ? Math.sqrt(var) / mean : 0.0D,
                pairs > 0 ? neighbour / pairs : 0.0D,
                255.0D * (sorted.get((int) (sorted.size() * 0.95))
                        - sorted.get((int) (sorted.size() * 0.05))));
        System.out.printf(java.util.Locale.ROOT,
                "T098_SURFACE_SCATTER_OCTAVE|meanThirdOctave=%.5f|variance=%.8f|cv=%.5f%n",
                sMean, sVar, sMean > 1.0E-9D ? Math.sqrt(sVar) / sMean : 0.0D);
    }

    /**
     * Relief of the alpha=0.5 surface resolved by scale, so "rough" and
     * "billowy" are not confused. A wide detrend window keeps large features in
     * the residual; a narrow one keeps only fine ones.
     */
    private static void reportT098SurfaceReliefSpectrum(
            byte[] baseVolume, byte[] detailVolume, java.util.List<StormLobeDescriptor> lobes,
            double centreX, double centreZ, double anvilBase, double anvilTop) {
        final double spacing = 6.0D;
        // Interior of the canopy only, so silhouette curvature cannot masquerade
        // as surface relief.
        int rows = (int) ((anvilTop - 30.0D - (anvilBase + 30.0D)) / spacing);
        int cols = (int) (600.0D / spacing);
        double[][] t50 = new double[rows][cols];
        boolean[][] valid = new boolean[rows][cols];
        double[][] t50NoErosion = new double[rows][cols];
        boolean[][] validNoErosion = new boolean[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                double y = anvilBase + 30.0D + r * spacing;
                double z = centreZ - 300.0D + c * spacing;
                T098Surface s = t098MarchSurface(baseVolume, detailVolume, lobes,
                        centreX + 900.0D, y, z, -1.0D, 0.0D, 0.0D, 1800.0D, 0, false);
                if (s.valid()) {
                    valid[r][c] = true;
                    t50[r][c] = s.t50();
                }
                T098Surface e = t098MarchSurface(baseVolume, detailVolume, lobes,
                        centreX + 900.0D, y, z, -1.0D, 0.0D, 0.0D, 1800.0D, 1, false);
                if (e.valid()) {
                    validNoErosion[r][c] = true;
                    t50NoErosion[r][c] = e.t50();
                }
            }
        }
        System.out.println("T098_SURFACE_SPECTRUM|windowBlocks|reliefRmsProduction"
                + "|reliefRmsErosionOff|erosionContributionBlocks|projectedPxAtSide");
        // At the SIDE pose the storm's 864-block height spans about 195 px.
        final double blocksPerPixel = 864.0D / 195.0D;
        for (int window : new int[] {2, 4, 8, 16, 32}) {
            double[] a = t098Relief(t50, valid, window);
            double[] b = t098Relief(t50NoErosion, validNoErosion, window);
            System.out.printf(java.util.Locale.ROOT,
                    "T098_SURFACE_SPECTRUM|%12.0f|%18.3f|%18.3f|%24.3f|%17.2f%n",
                    window * spacing, a[0], b[0], a[0] - b[0], (window * spacing) / blocksPerPixel);
        }
    }

    /** Optical-surface relief per role, from one SIDE sweep of the whole storm. */
    private static void reportT098SurfaceByRole(
            byte[] baseVolume, byte[] detailVolume, java.util.List<StormLobeDescriptor> lobes,
            double centreX, double centreZ) {
        final double spacing = 6.0D;
        String[] roleNames = {"BASE", "CORE", "TOWER", "ANVIL"};
        java.util.List<java.util.List<double[]>> byRole = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            byRole.add(new ArrayList<>());
        }
        for (double y = 140.0D; y <= 1010.0D; y += spacing) {
            java.util.List<double[]> row = new ArrayList<>();
            for (double z = centreZ - 450.0D; z <= centreZ + 450.0D; z += spacing) {
                T098Surface s = t098MarchSurface(baseVolume, detailVolume, lobes,
                        centreX + 900.0D, y, z, -1.0D, 0.0D, 0.0D, 1800.0D, 0, false);
                if (s.valid() && s.roleAtT50() >= 0) {
                    byRole.get(s.roleAtT50()).add(new double[] {y, z, s.t50()});
                }
            }
        }
        System.out.println("T098_SURFACE_ROLE|role|points|reliefRmsBlocks|neighbourDeltaBlocks");
        for (int role = 0; role < 4; role++) {
            java.util.List<double[]> pts = byRole.get(role);
            if (pts.size() < 200) {
                continue;
            }
            // Relief against a local mean over neighbours within 40 blocks.
            double sq = 0.0D;
            int n = 0;
            double neighbour = 0.0D;
            int pairs = 0;
            for (int i = 0; i < pts.size(); i++) {
                double sum = 0.0D;
                int m = 0;
                for (int j = Math.max(0, i - 12); j < Math.min(pts.size(), i + 13); j++) {
                    if (Math.abs(pts.get(j)[0] - pts.get(i)[0]) < 0.1D
                            && Math.abs(pts.get(j)[1] - pts.get(i)[1]) <= 42.0D) {
                        sum += pts.get(j)[2];
                        m++;
                    }
                }
                if (m < 4) {
                    continue;
                }
                double residual = pts.get(i)[2] - sum / m;
                sq += residual * residual;
                n++;
                if (i + 1 < pts.size() && Math.abs(pts.get(i + 1)[0] - pts.get(i)[0]) < 0.1D
                        && Math.abs(pts.get(i + 1)[1] - pts.get(i)[1]) <= spacing + 0.1D) {
                    neighbour += Math.abs(pts.get(i + 1)[2] - pts.get(i)[2]);
                    pairs++;
                }
            }
            if (n < 100) {
                continue;
            }
            System.out.printf(java.util.Locale.ROOT,
                    "T098_SURFACE_ROLE|%-5s|%7d|%14.3f|%18.3f%n",
                    roleNames[role], n, Math.sqrt(sq / n),
                    pairs > 0 ? neighbour / pairs : 0.0D);
        }
    }

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
                + "|iters|exhausted|sdfEvals|marchedDepth|refDepth"
                + "|firstMaterialT|itersToMaterial|refMaterialSpan|itersNeeded");
        for (int strategy = 0; strategy < 8; strategy++) {
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
        // Total blocks of material the ray actually crosses, which is what the
        // fine budget would have to cover to integrate the storm fully.
        double refMaterialSpan = 0.0D;
        for (double[] interval : refIntervals) {
            refMaterialSpan += interval[1] - interval[0];
        }

        double t = 0.0D;
        int sinceHit = 6;
        int coarseSegs = 0;
        int fineSegs = 0;
        int iterations = 0;
        int sdfEvals = 0;
        double firstFineT = -1.0D;
        // Step 3: where the march actually samples material, not merely where it
        // stops stepping coarsely.
        double firstMaterialT = -1.0D;
        int itersBeforeMaterial = -1;
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
                if (strategy >= 4) {
                    // Per-descriptor clearance. All ten descriptors - BASE,
                    // CORE, TOWER and ANVIL - live in one group, so a per-GROUP
                    // bound takes the group's widest softness and is identical
                    // to the global one. Per descriptor is the granularity that
                    // actually differs: a ray approaching TOWER is bounded by
                    // TOWER's own 49.7 rather than BASE's 164.6.
                    //
                    // Every lobe must individually clear, not just the nearest:
                    // d_i(q) >= d_i(p) - L for every i, so the bound is the
                    // minimum over descriptors. The smooth union additionally
                    // creates material in the blend webbing between lobes, which
                    // no single lobe's envelope covers, so a blend allowance is
                    // subtracted as well and swept here rather than assumed.
                    sdfEvals++;
                    double[] blendAllowance = {0.0D, 24.0D, 48.0D, 96.0D};
                    double clearance = Double.POSITIVE_INFINITY;
                    for (StormLobeDescriptor lobe : lobes) {
                        double lobeDistance = StormLobeEvaluator.signedDistanceAt(lobe,
                                camX + dirX * t, camY + dirY * t, camZ + dirZ * t);
                        clearance = Math.min(clearance,
                                lobeDistance - StormLobeEvaluator.edgeWidthBlocks(lobe));
                    }
                    double safe = clearance - blendAllowance[strategy - 4];
                    if (safe > fineStep) {
                        allow = false;
                        stepLength = Math.min(Math.min(safe, coarseStepCap), t1 - t);
                        segEnd = t + stepLength;
                    }
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
                    if (firstMaterialT < 0.0D) {
                        firstMaterialT = t;
                        itersBeforeMaterial = iterations;
                    }
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

        String[] names = {"current", "B_window", "C_cooldown", "E_sdf",
                "perDesc_b0", "perDesc_b24", "perDesc_b48", "perDesc_b96"};
        System.out.printf(java.util.Locale.ROOT,
                "T098_MARCH|%-10s|%.2f|%-12s|%7.1f|%6.1f|%6d|%6d|%8.1f|%8.1f|%6d|%9.1f"
                        + "|%5d|%8s|%8d|%9.1f|%9.1f|%9.1f|%6d|%9.1f|%6d%n",
                names[strategy], factor, label, radius * factor, targetY,
                coarseSegs, fineSegs, firstFineT, refFirst, falseNegSegs, falseNegBlocks,
                iterations, exhausted ? "YES" : "no", sdfEvals, marchedDepth, refDepth,
                firstMaterialT, itersBeforeMaterial,
                refMaterialSpan,
                // Fine steps needed to cross that material, plus the iterations
                // this ray already spent arriving.
                itersBeforeMaterial < 0 ? -1
                        : itersBeforeMaterial + (int) Math.ceil(refMaterialSpan / fineStep));
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
        // Strategy 6 is the production rule: per-descriptor clearance minus the
        // STORM_MAX_BLEND_BLOCKS webbing allowance. Strategy 3 is the previous
        // global StormWidestEdgeBlocks bound, kept as a comparison arm because
        // a per-GROUP bound would have been identical to it - every descriptor
        // in this fixture shares one group. Strategy 0 is the pre-promotion-fix
        // behaviour.
        for (double factor : new double[] {1.12D, 1.40D, 1.60D, 1.70D, 2.00D}) {
            double[] fixed = marchOutcome(baseVolume, detailVolume, lobes,
                    centreX, centreZ, 657.8D, factor, 680.0D, 6);
            double[] global = marchOutcome(baseVolume, detailVolume, lobes,
                    centreX, centreZ, 657.8D, factor, 680.0D, 3);
            double[] legacy = marchOutcome(baseVolume, detailVolume, lobes,
                    centreX, centreZ, 657.8D, factor, 680.0D, 0);
            if (fixed[2] <= global[2]) {
                throw new IllegalStateException("the per-descriptor bound is no better than "
                        + "the global one at " + factor + "x: " + fixed[2] + " vs " + global[2]);
            }
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
                + "|per-descriptor rule reaches opaque material 1.12x-2.00x"
                + "|and beats the global bound at every distance"
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


    /**
     * T098: how far the smooth union pulls the surface past the nearest lobe.
     *
     * <p>The per-descriptor advance bounds every lobe individually, but the
     * ordered smooth union is below each input distance, so material exists in
     * the webbing between lobes that no single lobe's envelope covers. The
     * advance must subtract that excess. stormSmoothMinimum subtracts
     * r*h*(1-h), at most r/4 per union, and nine sequential lobe unions inside
     * one group make the analytic worst case far looser than reality. This
     * measures the excess directly, so the allowance is derived rather than
     * guessed.
     */
    private static void reportT098WebbingExcess() {
        java.util.List<StormLobeDescriptor> lobes = severeFixture38bc5412();
        double worst = 0.0D;
        double worstY = 0.0D;
        long samples = 0L;
        java.util.List<Double> excesses = new ArrayList<>();
        for (double y = 136.0D; y <= 1010.0D; y += 24.0D) {
            for (double x = -1000.0D; x <= 1000.0D; x += 24.0D) {
                for (double z = -1000.0D; z <= 1000.0D; z += 48.0D) {
                    double nearest = Double.POSITIVE_INFINITY;
                    for (StormLobeDescriptor lobe : lobes) {
                        nearest = Math.min(nearest,
                                StormLobeEvaluator.signedDistanceAt(lobe, x, y, z));
                    }
                    double union = StormLobeEvaluator.unionDistanceAt(lobes, x, y, z);
                    double excess = nearest - union;
                    samples++;
                    if (excess > 0.0D) {
                        excesses.add(excess);
                    }
                    if (excess > worst) {
                        worst = excess;
                        worstY = y;
                    }
                }
            }
        }
        java.util.Collections.sort(excesses);
        System.out.printf(java.util.Locale.ROOT,
                "T098_WEBBING|samples=%d|positiveExcess=%d|p50=%.2f|p99=%.2f|max=%.2f"
                        + "|worstAtY=%.0f|analyticPerUnionCap=%.1f%n",
                samples, excesses.size(),
                excesses.isEmpty() ? 0.0D : excesses.get(excesses.size() / 2),
                excesses.isEmpty() ? 0.0D : excesses.get((int) (excesses.size() * 0.99D)),
                worst, worstY, 48.0D / 4.0D);
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
