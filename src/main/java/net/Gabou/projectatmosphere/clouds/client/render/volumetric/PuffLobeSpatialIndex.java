package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.type.CloudMorphologyMemberTier;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Conservative spatial indirection for canonical native PUFF lobes.
 *
 * <p>The normal weather map remains the broad occupancy/pre-test source, but
 * reducing every overlapping lobe to one base/top pair destroys the 3-D
 * cauliflower silhouette. This grid stores up to eight nearby descriptor
 * indices per 16-block tile. The raymarch therefore evaluates only the real
 * local lobes instead of looping over all 96 weather cells at every sample.</p>
 */
public final class PuffLobeSpatialIndex {
    public static final int GRID_SIZE = 256;
    public static final int MAX_LOBES = 32;
    public static final int CANDIDATES_PER_TILE = 8;

    // Descriptor indices are encoded as 1..32; zero means empty. Two base-33
    // digits fit exactly in one half float (maximum 1088, below the exact
    // integer range of IEEE-754 binary16), so four channels carry eight slots.
    static final int PACK_BASE = MAX_LOBES + 1;
    private static final long GRID_SIGNATURE_VERSION = 0x504146465F475231L;
    private static final long EMPTY_GRID_SIGNATURE = 0x504146465F454D50L;

    private static final float[] POS_RADIUS = new float[MAX_LOBES * 4];
    private static final float[] SHAPE = new float[MAX_LOBES * 4];
    private static final float[] MEDIA = new float[MAX_LOBES * 4];
    private static final int[] TILE_INDICES =
            new int[GRID_SIZE * GRID_SIZE * CANDIDATES_PER_TILE];
    private static final float[] TILE_SCORES =
            new float[GRID_SIZE * GRID_SIZE * CANDIDATES_PER_TILE];
    private static final int[] TILE_COUNTS = new int[GRID_SIZE * GRID_SIZE];
    private static final FloatBuffer UPLOAD =
            BufferUtils.createFloatBuffer(GRID_SIZE * GRID_SIZE * 4);

    private static int lobeCount;
    private static int truncatedLobes;
    private static int baseTierLobes;
    private static int middleTierLobes;
    private static int crownTierLobes;
    private static int unknownTierLobes;
    private static int activeTiles;
    private static int overflowTiles;
    private static int maxCandidatesPerTile;
    private static long rebuildCount;
    private static long descriptorSignature;
    private static RenderTarget uploadedTarget;
    private static int uploadedTextureId = -1;
    private static long uploadedGridSignature = Long.MIN_VALUE;
    private static boolean uploadedTextureKnownEmpty;
    private static long gridRequests;
    private static long gridHits;
    private static long gridUploads;
    private static long emptyClears;
    private static long emptySkips;
    private static long targetChanges;
    private static double uploadedOriginX = Double.NaN;
    private static double uploadedOriginZ = Double.NaN;
    private static float uploadedExtent = Float.NaN;

    private PuffLobeSpatialIndex() {
    }

    /** Refreshes compact descriptors without rebuilding the conservative grid. */
    public static void updateDescriptors(List<VolumetricRenderCell> cells) {
        Arrays.fill(POS_RADIUS, 0.0F);
        Arrays.fill(SHAPE, 0.0F);
        Arrays.fill(MEDIA, 0.0F);
        lobeCount = 0;
        truncatedLobes = 0;
        baseTierLobes = 0;
        middleTierLobes = 0;
        crownTierLobes = 0;
        unknownTierLobes = 0;

        if (cells != null) {
            for (VolumetricRenderCell cell : cells) {
                if (!isDirectPuff(cell)) {
                    continue;
                }
                if (lobeCount >= MAX_LOBES) {
                    truncatedLobes++;
                    continue;
                }
                int base = lobeCount * 4;
                POS_RADIUS[base] = (float) cell.x();
                POS_RADIUS[base + 1] = (float) cell.z();
                POS_RADIUS[base + 2] = Math.max(1.0F, cell.radiusMajor());
                POS_RADIUS[base + 3] = Math.max(1.0F, cell.radiusMinor());
                SHAPE[base] = cell.orientationRadians();
                SHAPE[base + 1] = cell.baseY();
                SHAPE[base + 2] = Math.max(cell.baseY() + 1.0F, cell.topY());
                SHAPE[base + 3] = cell.density();
                MEDIA[base] = cell.edgeSoftness();
                MEDIA[base + 1] = cell.lifecycleStage();
                MEDIA[base + 2] = cell.seed01();
                CloudMorphologyMemberTier puffTier = cell.puffTier() == null
                        ? CloudMorphologyMemberTier.UNKNOWN
                        : cell.puffTier();
                MEDIA[base + 3] = packPuffTierAndVerticalDevelopment(
                        puffTier,
                        cell.verticalDevelopment()
                );
                switch (puffTier) {
                    case BASE -> baseTierLobes++;
                    case MIDDLE -> middleTierLobes++;
                    case CROWN -> crownTierLobes++;
                    case UNKNOWN -> unknownTierLobes++;
                }
                lobeCount++;
            }
        }
        descriptorSignature = descriptorSignature();
    }

    /** Rebuilds the tile indirection only when its horizontal geometry changed. */
    public static void rebuildIfNeeded(RenderTarget target, double originX, double originZ, float extent) {
        RenderSystem.assertOnRenderThread();
        if (target == null || target.getColorTextureId() <= 0
                || target.width != GRID_SIZE || target.height != GRID_SIZE
                || !Float.isFinite(extent) || extent <= 0.0F) {
            return;
        }

        gridRequests++;
        int textureId = target.getColorTextureId();
        boolean sameTarget = target == uploadedTarget && textureId == uploadedTextureId;
        if (!sameTarget) {
            targetChanges++;
        }

        if (lobeCount == 0 && sameTarget && uploadedTextureKnownEmpty) {
            emptySkips++;
            return;
        }

        long gridSignature = lobeCount == 0
                ? EMPTY_GRID_SIGNATURE
                : gridSignature(originX, originZ, extent);
        if (lobeCount > 0 && sameTarget && !uploadedTextureKnownEmpty
                && uploadedGridSignature == gridSignature) {
            gridHits++;
            return;
        }

        Arrays.fill(TILE_INDICES, -1);
        Arrays.fill(TILE_SCORES, Float.POSITIVE_INFINITY);
        Arrays.fill(TILE_COUNTS, 0);
        float tileWorld = extent / GRID_SIZE;
        float halfDiagonal = tileWorld * 0.70710677F;

        for (int slot = 0; slot < lobeCount; slot++) {
            int base = slot * 4;
            float centerX = POS_RADIUS[base];
            float centerZ = POS_RADIUS[base + 1];
            float radiusMajor = POS_RADIUS[base + 2];
            float radiusMinor = POS_RADIUS[base + 3];
            float bound = Math.max(radiusMajor, radiusMinor) + halfDiagonal;
            int minX = tileCoordinate(centerX - bound, originX, tileWorld);
            int maxX = tileCoordinate(centerX + bound, originX, tileWorld);
            int minZ = tileCoordinate(centerZ - bound, originZ, tileWorld);
            int maxZ = tileCoordinate(centerZ + bound, originZ, tileWorld);
            float cos = (float) Math.cos(-SHAPE[base]);
            float sin = (float) Math.sin(-SHAPE[base]);
            float conservativeMargin = halfDiagonal / Math.max(1.0F, Math.min(radiusMajor, radiusMinor));

            for (int z = minZ; z <= maxZ; z++) {
                float worldZ = (float) originZ + (z + 0.5F) * tileWorld;
                for (int x = minX; x <= maxX; x++) {
                    float worldX = (float) originX + (x + 0.5F) * tileWorld;
                    float dx = worldX - centerX;
                    float dz = worldZ - centerZ;
                    float localX = dx * cos - dz * sin;
                    float localZ = dx * sin + dz * cos;
                    float nx = localX / radiusMajor;
                    float nz = localZ / radiusMinor;
                    float radial = (float) Math.sqrt(nx * nx + nz * nz);
                    if (radial > 1.0F + conservativeMargin) {
                        continue;
                    }
                    int tile = z * GRID_SIZE + x;
                    TILE_COUNTS[tile]++;
                    insertCandidate(tile, slot, radial);
                }
            }
        }

        activeTiles = 0;
        overflowTiles = 0;
        maxCandidatesPerTile = 0;
        UPLOAD.clear();
        for (int tile = 0; tile < TILE_COUNTS.length; tile++) {
            int count = TILE_COUNTS[tile];
            if (count > 0) {
                activeTiles++;
            }
            if (count > CANDIDATES_PER_TILE) {
                overflowTiles++;
            }
            maxCandidatesPerTile = Math.max(maxCandidatesPerTile, count);
            int base = tile * CANDIDATES_PER_TILE;
            for (int pair = 0; pair < 4; pair++) {
                int first = TILE_INDICES[base + pair * 2];
                int second = TILE_INDICES[base + pair * 2 + 1];
                UPLOAD.put((float) packPair(first, second));
            }
        }
        UPLOAD.flip();

        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try (PixelUnpackState ignored = PixelUnpackState.beginTightCpuUpload()) {
            try {
                RenderSystem.bindTexture(textureId);
                GL11.glTexSubImage2D(
                        GL11.GL_TEXTURE_2D,
                        0,
                        0,
                        0,
                        GRID_SIZE,
                        GRID_SIZE,
                        GL11.GL_RGBA,
                        GL11.GL_FLOAT,
                        UPLOAD
                );
            } finally {
                RenderSystem.bindTexture(previousTexture);
            }
        }

        uploadedTarget = target;
        uploadedTextureId = textureId;
        uploadedGridSignature = gridSignature;
        uploadedTextureKnownEmpty = lobeCount == 0;
        uploadedOriginX = originX;
        uploadedOriginZ = originZ;
        uploadedExtent = extent;
        gridUploads++;
        if (lobeCount == 0) {
            emptyClears++;
        }
        rebuildCount++;
    }

    /** Uploads only the compact descriptor arrays; the candidate texture owns indices. */
    public static void uploadDescriptors(int program) {
        uploadVec4Array(program, "PuffPosRadius", POS_RADIUS);
        uploadVec4Array(program, "PuffShape", SHAPE);
        uploadVec4Array(program, "PuffMedia", MEDIA);
    }

    public static int lobeCount() {
        return lobeCount;
    }

    public static boolean directRepresentationComplete() {
        return lobeCount > 0
                && truncatedLobes == 0
                && overflowTiles == 0
                && uploadedTarget != null
                && uploadedTextureId > 0
                && uploadedTextureId == uploadedTarget.getColorTextureId()
                && !uploadedTextureKnownEmpty;
    }

    public static VolumetricPuffShapeMode effectiveShapeMode() {
        return resolveShapeMode(
                VolumetricCloudDebugConfig.puffShapeMode(),
                VolumetricCloudDebugConfig.puffDensityStage(),
                directRepresentationComplete()
        );
    }

    private static VolumetricPuffShapeMode resolveShapeMode(
            VolumetricPuffShapeMode requested,
            VolumetricPuffDensityStage densityStage,
            boolean representationComplete
    ) {
        VolumetricPuffShapeMode selected = densityStage != null && densityStage.isDiagnostic()
                ? VolumetricPuffShapeMode.DIRECT_ONLY
                : (requested == null ? VolumetricPuffShapeMode.HYBRID : requested);
        // A partial descriptor set is never safe for either direct mode. In
        // HYBRID, one candidate in a tile suppresses the fallback even if a
        // truncated ninth candidate is missing, producing grid-boundary holes.
        if (selected.usesDirectDescriptors() && !representationComplete) {
            return VolumetricPuffShapeMode.FALLBACK_ONLY;
        }
        return selected;
    }

    public static long descriptorSignatureForDiagnostics() {
        return descriptorSignature;
    }

    public static String status() {
        return String.format(
                Locale.ROOT,
                "puffIndex[mode=%s effective=%s densityStage=%s tierFilter=%s complete=%s lobes=%d tiers[base=%d,middle=%d,crown=%d,unknown=%d] truncated=%d tiles=%d overflow=%d maxPerTile=%d rebuilds=%d "
                        + "requests=%d hits=%d uploads=%d emptyClears=%d emptySkips=%d targetChanges=%d sig=%016x]",
                VolumetricCloudDebugConfig.puffShapeMode().serializedName(),
                effectiveShapeMode().serializedName(),
                VolumetricCloudDebugConfig.puffDensityStage().serializedName(),
                VolumetricCloudDebugConfig.puffTierFilter().serializedName(),
                directRepresentationComplete(),
                lobeCount,
                baseTierLobes,
                middleTierLobes,
                crownTierLobes,
                unknownTierLobes,
                truncatedLobes,
                activeTiles,
                overflowTiles,
                maxCandidatesPerTile,
                rebuildCount,
                gridRequests,
                gridHits,
                gridUploads,
                emptyClears,
                emptySkips,
                targetChanges,
                descriptorSignature
        );
    }

    /** Exact CPU descriptor payload paired with the one-shot GPU index proof. */
    public static String descriptorReport() {
        if (lobeCount <= 0) {
            return "descriptors=empty";
        }
        StringBuilder report = new StringBuilder("descriptors=").append(lobeCount);
        for (int index = 0; index < lobeCount; index++) {
            int base = index * 4;
            int tierId = unpackPuffTierGpuId(MEDIA[base + 3]);
            String tier = switch (tierId) {
                case 0 -> "base";
                case 1 -> "middle";
                case 2 -> "crown";
                default -> "unknown";
            };
            report.append(String.format(
                    Locale.ROOT,
                    "%n- #%d tier=%s center=(%.3f,%.3f) radius=(%.3f,%.3f) "
                            + "orientation=%.5f baseTop=%.3f..%.3f density=%.4f "
                            + "edge=%.4f lifecycle=%.4f seed=%.6f vertical=%.4f",
                    index,
                    tier,
                    POS_RADIUS[base],
                    POS_RADIUS[base + 1],
                    POS_RADIUS[base + 2],
                    POS_RADIUS[base + 3],
                    SHAPE[base],
                    SHAPE[base + 1],
                    SHAPE[base + 2],
                    SHAPE[base + 3],
                    MEDIA[base],
                    MEDIA[base + 1],
                    MEDIA[base + 2],
                    unpackPuffVerticalDevelopment(MEDIA[base + 3])
            ));
        }
        return report.toString();
    }

    /**
     * One-shot runtime proof for the CPU tile build and the texture actually
     * visible to OpenGL. This is deliberately command-driven: glGetTexImage is
     * synchronous and must never enter the frame loop.
     */
    public static String verifyCurrentRepresentation() {
        RenderSystem.assertOnRenderThread();
        if (uploadedTarget == null || uploadedTextureId <= 0
                || uploadedTextureId != uploadedTarget.getColorTextureId()
                || !Double.isFinite(uploadedOriginX) || !Double.isFinite(uploadedOriginZ)
                || !Float.isFinite(uploadedExtent) || uploadedExtent <= 0.0F) {
            String unavailable = "unavailable target/grid not uploaded; " + status();
            ProjectAtmosphere.LOGGER.warn("[PuffLobeSpatialIndex] verify {}", unavailable);
            return unavailable;
        }

        FloatBuffer readback = BufferUtils.createFloatBuffer(GRID_SIZE * GRID_SIZE * 4);
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try (PixelPackState ignored = PixelPackState.beginTightCpuReadback()) {
            try {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, uploadedTextureId);
                GL11.glGetTexImage(
                        GL11.GL_TEXTURE_2D,
                        0,
                        GL11.GL_RGBA,
                        GL11.GL_FLOAT,
                        readback
                );
            } finally {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
                GL13.glActiveTexture(previousActiveTexture);
            }
        }

        int[] gpuPacked = new int[GRID_SIZE * GRID_SIZE * 4];
        long cpuHash = 0xcbf29ce484222325L;
        long gpuHash = 0xcbf29ce484222325L;
        int exactTiles = 0;
        int mismatchedTiles = 0;
        int cpuActiveTiles = 0;
        int gpuActiveTiles = 0;
        int nonIntegerChannels = 0;
        int outOfRangeChannels = 0;
        int firstMismatchTile = -1;
        int[] firstExpected = new int[4];
        int[] firstActual = new int[4];
        Bounds cpuBounds = new Bounds();
        Bounds gpuBounds = new Bounds();

        for (int tile = 0; tile < GRID_SIZE * GRID_SIZE; tile++) {
            boolean tileMatches = true;
            boolean cpuActive = false;
            boolean gpuActive = false;
            int base = tile * CANDIDATES_PER_TILE;
            for (int component = 0; component < 4; component++) {
                int expected = packPair(
                        TILE_INDICES[base + component * 2],
                        TILE_INDICES[base + component * 2 + 1]
                );
                float raw = readback.get(tile * 4 + component);
                int actual = Math.round(raw);
                gpuPacked[tile * 4 + component] = actual;
                if (!Float.isFinite(raw) || Math.abs(raw - actual) > 1.0E-4F) {
                    nonIntegerChannels++;
                }
                if (actual < 0 || actual > PACK_BASE * (PACK_BASE - 1) + PACK_BASE - 1) {
                    outOfRangeChannels++;
                }
                if (expected != actual) {
                    tileMatches = false;
                }
                cpuActive |= expected != 0;
                gpuActive |= actual != 0;
                cpuHash = mix(cpuHash, expected);
                gpuHash = mix(gpuHash, actual);
                if (firstMismatchTile < 0 && expected != actual) {
                    firstMismatchTile = tile;
                    for (int firstComponent = 0; firstComponent < 4; firstComponent++) {
                        firstExpected[firstComponent] = packPair(
                                TILE_INDICES[base + firstComponent * 2],
                                TILE_INDICES[base + firstComponent * 2 + 1]
                        );
                        firstActual[firstComponent] = Math.round(
                                readback.get(tile * 4 + firstComponent)
                        );
                    }
                }
            }
            int x = tile % GRID_SIZE;
            int z = tile / GRID_SIZE;
            if (tileMatches) {
                exactTiles++;
            } else {
                mismatchedTiles++;
            }
            if (cpuActive) {
                cpuActiveTiles++;
                cpuBounds.include(x, z);
            }
            if (gpuActive) {
                gpuActiveTiles++;
                gpuBounds.include(x, z);
            }
        }

        ShiftScore bestShift = bestTranslation(gpuPacked, 16);
        int identityMatches = transformedActiveMatches(gpuPacked, Transform.IDENTITY);
        int flipXMatches = transformedActiveMatches(gpuPacked, Transform.FLIP_X);
        int flipZMatches = transformedActiveMatches(gpuPacked, Transform.FLIP_Z);
        int flipBothMatches = transformedActiveMatches(gpuPacked, Transform.FLIP_BOTH);
        int transposeMatches = transformedActiveMatches(gpuPacked, Transform.TRANSPOSE);
        CoverageProof coverage = verifyCpuTileCoverage();

        String firstMismatch = firstMismatchTile < 0
                ? "none"
                : "(" + (firstMismatchTile % GRID_SIZE) + ","
                        + (firstMismatchTile / GRID_SIZE) + ") expected="
                        + Arrays.toString(firstExpected) + " actual="
                        + Arrays.toString(firstActual);
        String report = String.format(
                Locale.ROOT,
                "texture=%d origin=(%.1f,%.1f) extent=%.1f lobes=%d "
                        + "tiles[exact=%d mismatch=%d cpuActive=%d gpuActive=%d "
                        + "cpuBounds=%s gpuBounds=%s] channels[nonInteger=%d outOfRange=%d] "
                        + "hash[cpu=%016x gpu=%016x] firstMismatch=%s "
                        + "orientationMatches[id=%d flipX=%d flipZ=%d flipBoth=%d transpose=%d] "
                        + "bestShift=(%d,%d) matches=%d/%d cpuCoverage[samples=%d missing=%d outside=%d]",
                uploadedTextureId,
                uploadedOriginX,
                uploadedOriginZ,
                uploadedExtent,
                lobeCount,
                exactTiles,
                mismatchedTiles,
                cpuActiveTiles,
                gpuActiveTiles,
                cpuBounds,
                gpuBounds,
                nonIntegerChannels,
                outOfRangeChannels,
                cpuHash,
                gpuHash,
                firstMismatch,
                identityMatches,
                flipXMatches,
                flipZMatches,
                flipBothMatches,
                transposeMatches,
                bestShift.x(),
                bestShift.z(),
                bestShift.matches(),
                cpuActiveTiles,
                coverage.samples(),
                coverage.missing(),
                coverage.outside()
        );
        ProjectAtmosphere.LOGGER.info("[PuffLobeSpatialIndex] verify {}", report);
        String descriptors = descriptorReport();
        ProjectAtmosphere.LOGGER.info("[PuffLobeSpatialIndex] {}", descriptors);
        return report + "\n" + descriptors;
    }

    public static void invalidate() {
        lobeCount = 0;
        truncatedLobes = 0;
        baseTierLobes = 0;
        middleTierLobes = 0;
        crownTierLobes = 0;
        unknownTierLobes = 0;
        activeTiles = 0;
        overflowTiles = 0;
        maxCandidatesPerTile = 0;
        descriptorSignature = 0L;
        uploadedTarget = null;
        uploadedTextureId = -1;
        uploadedGridSignature = Long.MIN_VALUE;
        uploadedTextureKnownEmpty = false;
        uploadedOriginX = Double.NaN;
        uploadedOriginZ = Double.NaN;
        uploadedExtent = Float.NaN;
        Arrays.fill(POS_RADIUS, 0.0F);
        Arrays.fill(SHAPE, 0.0F);
        Arrays.fill(MEDIA, 0.0F);
    }

    /** Pure deterministic checks shared by the standalone stability sandbox. */
    public static void selfCheck() {
        if (resolveShapeMode(VolumetricPuffShapeMode.HYBRID, VolumetricPuffDensityStage.FINAL, true)
                    != VolumetricPuffShapeMode.HYBRID
                || resolveShapeMode(VolumetricPuffShapeMode.DIRECT_ONLY, VolumetricPuffDensityStage.FINAL, true)
                    != VolumetricPuffShapeMode.DIRECT_ONLY
                || resolveShapeMode(VolumetricPuffShapeMode.FALLBACK_ONLY, VolumetricPuffDensityStage.FINAL, true)
                    != VolumetricPuffShapeMode.FALLBACK_ONLY
                || resolveShapeMode(VolumetricPuffShapeMode.HYBRID, VolumetricPuffDensityStage.ANALYTIC_INDEXED, true)
                    != VolumetricPuffShapeMode.DIRECT_ONLY
                || resolveShapeMode(VolumetricPuffShapeMode.HYBRID, VolumetricPuffDensityStage.FINAL, false)
                    != VolumetricPuffShapeMode.FALLBACK_ONLY
                || resolveShapeMode(VolumetricPuffShapeMode.DIRECT_ONLY, VolumetricPuffDensityStage.FINAL, false)
                    != VolumetricPuffShapeMode.FALLBACK_ONLY
                || resolveShapeMode(VolumetricPuffShapeMode.FALLBACK_ONLY, VolumetricPuffDensityStage.ANALYTIC_ALL, false)
                    != VolumetricPuffShapeMode.FALLBACK_ONLY) {
            throw new IllegalStateException("PUFF effective shape-mode contract is inconsistent");
        }

        int maximumPacked = 0;
        for (int first = -1; first < MAX_LOBES; first++) {
            for (int second = -1; second < MAX_LOBES; second++) {
                int packed = packPair(first, second);
                maximumPacked = Math.max(maximumPacked, packed);
                if (unpackPair(packed, 0) != first || unpackPair(packed, 1) != second) {
                    throw new IllegalStateException(
                            "PUFF candidate pack mismatch first=" + first
                                    + " second=" + second + " packed=" + packed
                    );
                }
            }
        }
        if (maximumPacked > 2048) {
            throw new IllegalStateException(
                    "PUFF candidate half-float integer is not exact: " + maximumPacked
            );
        }

        long gridHeader = gridSignatureHeader(128.0D, -64.0D, 4096.0F, 1);
        long stableGrid = mixGridLobe(gridHeader, 12.0F, -8.0F, 30.0F, 22.0F, 0.4F);
        long repeatedGrid = mixGridLobe(gridHeader, 12.0F, -8.0F, 30.0F, 22.0F, 0.4F);
        long movedGrid = mixGridLobe(gridHeader, 12.5F, -8.0F, 30.0F, 22.0F, 0.4F);
        if (stableGrid != repeatedGrid || stableGrid == movedGrid) {
            throw new IllegalStateException("PUFF spatial grid signature is not deterministic/sensitive");
        }

        CloudMorphologyMemberTier[] structuredTiers = {
                CloudMorphologyMemberTier.BASE,
                CloudMorphologyMemberTier.MIDDLE,
                CloudMorphologyMemberTier.CROWN
        };
        for (CloudMorphologyMemberTier tier : structuredTiers) {
            float minimumPeak = Float.POSITIVE_INFINITY;
            float maximumPeak = Float.NEGATIVE_INFINITY;
            for (int seedIndex = 0; seedIndex < 8; seedIndex++) {
                float seed01 = seedIndex / 7.0F;
                float peakHeight = analyticPeakHeight(seed01, tier);
                float equatorRadius = analyticEquatorRadius(seed01, tier);
                minimumPeak = Math.min(minimumPeak, peakHeight);
                maximumPeak = Math.max(maximumPeak, peakHeight);
                float bottomRadius = analyticRadiusAtHeight(0.0F, seed01, tier);
                float peakRadius = analyticRadiusAtHeight(peakHeight, seed01, tier);
                float topRadius = analyticRadiusAtHeight(1.0F, seed01, tier);
                boolean baseTier = tier == CloudMorphologyMemberTier.BASE;
                if ((baseTier && bottomRadius <= 0.0F)
                        || (!baseTier && Math.abs(bottomRadius) > 1.0E-6F)
                        || bottomRadius > equatorRadius + 1.0E-5F
                        || equatorRadius > 1.001F
                        || Math.abs(peakRadius - equatorRadius) > 1.0E-5F
                        || Math.abs(topRadius) > 1.0E-6F) {
                    throw new IllegalStateException(
                            "PUFF analytic profile outside tier/root/peak/top contract tier="
                                    + tier + " seed=" + seed01 + " values=" + bottomRadius + "/"
                                    + peakRadius + "/" + topRadius
                    );
                }
                float baseDepth = structuredPuffEnvelopeDepth(
                        0.0F,
                        0.0F,
                        seed01,
                        tier,
                        24.0F
                );
                float peakDepth = structuredPuffEnvelopeDepth(
                        0.0F,
                        peakHeight,
                        seed01,
                        tier,
                        24.0F
                );
                float topDepth = structuredPuffEnvelopeDepth(
                        0.0F,
                        1.0F,
                        seed01,
                        tier,
                        24.0F
                );
                float outsideDepth = structuredPuffEnvelopeDepth(
                        1.05F,
                        peakHeight,
                        seed01,
                        tier,
                        24.0F
                );
                float earlyUpperRadius = analyticRadiusAtHeight(0.05F, seed01, tier);
                if (baseDepth != 0.0F
                        || Math.abs(peakDepth - 1.0F) > 1.0E-5F
                        || topDepth != 0.0F
                        || outsideDepth != 0.0F
                        || (!baseTier && earlyUpperRadius > equatorRadius * 0.35F)) {
                    throw new IllegalStateException(
                            "PUFF implicit envelope contract failed tier=" + tier
                                    + " seed=" + seed01 + " depth=" + baseDepth + "/"
                                    + peakDepth + "/" + topDepth + "/" + outsideDepth
                                    + " earlyRadius=" + earlyUpperRadius
                    );
                }
                float previous = bottomRadius;
                for (int step = 1; step <= 1000; step++) {
                    float h = step / 1000.0F;
                    float radius = analyticRadiusAtHeight(h, seed01, tier);
                    if (h <= peakHeight && radius + 1.0E-5F < previous) {
                        throw new IllegalStateException(
                                "PUFF analytic lower radius decreased tier=" + tier
                                        + " seed=" + seed01 + " h=" + h
                        );
                    }
                    if (h > peakHeight && radius > previous + 0.012F) {
                        throw new IllegalStateException(
                                "PUFF analytic crown radius increased tier=" + tier
                                        + " seed=" + seed01 + " h=" + h
                        );
                    }
                    previous = radius;
                }
            }
            if (maximumPeak - minimumPeak < 0.04F) {
                throw new IllegalStateException(
                        "PUFF analytic equators are synchronized for tier=" + tier
                                + " range=" + minimumPeak + ".." + maximumPeak
                );
            }
        }
        for (int seedIndex = 0; seedIndex < 8; seedIndex++) {
            float seed01 = seedIndex / 7.0F;
            float[] normalizedUpperRadii = new float[structuredTiers.length];
            for (int tierIndex = 0; tierIndex < structuredTiers.length; tierIndex++) {
                CloudMorphologyMemberTier tier = structuredTiers[tierIndex];
                float peak = analyticPeakHeight(seed01, tier);
                float upperMidpoint = peak + (1.0F - peak) * 0.5F;
                normalizedUpperRadii[tierIndex] = analyticRadiusAtHeight(
                        upperMidpoint,
                        seed01,
                        tier
                ) / analyticEquatorRadius(seed01, tier);
            }
            if (!(normalizedUpperRadii[0] < normalizedUpperRadii[1]
                    && normalizedUpperRadii[1] < normalizedUpperRadii[2])) {
                throw new IllegalStateException(
                        "PUFF tier upper caps lost BASE<MIDDLE<CROWN ordering seed="
                                + seed01 + " radii=" + normalizedUpperRadii[0] + "/"
                                + normalizedUpperRadii[1] + "/" + normalizedUpperRadii[2]
                );
            }
        }
        float stableSeed = 0.417F;
        float stablePhase = analyticLobePhase(stableSeed);
        for (int reorderedSlot = 0; reorderedSlot < MAX_LOBES; reorderedSlot++) {
            // Slot order is deliberately absent from the production formula.
            if (Float.floatToIntBits(analyticLobePhase(stableSeed))
                    != Float.floatToIntBits(stablePhase)) {
                throw new IllegalStateException(
                        "PUFF analytic identity changed with descriptor slot " + reorderedSlot
                );
            }
        }

        for (CloudMorphologyMemberTier tier : CloudMorphologyMemberTier.values()) {
            float packed = packPuffTierAndVerticalDevelopment(tier, 0.713F);
            if (unpackPuffTierGpuId(packed) != tier.gpuId()
                    || Math.abs(unpackPuffVerticalDevelopment(packed) - 0.713F) > 1.0E-5F) {
                throw new IllegalStateException(
                        "PUFF tier/development packing failed tier=" + tier + " packed=" + packed
                );
            }
        }

        float firstShape = 0.42F;
        float secondShape = 0.57F;
        float firstOrder = resolvePuffShapes(firstShape, secondShape);
        float secondOrder = resolvePuffShapes(secondShape, firstShape);
        float fourHalfShapes = resolvePuffShapes(0.5F, 0.5F, 0.5F, 0.5F);
        float fourDenseShapes = resolvePuffShapes(0.8F, 0.8F, 0.8F, 0.8F);
        if (Math.abs(firstOrder - secondOrder) > 1.0E-6F
                || Math.abs(resolvePuffShapes(firstShape) - firstShape) > 1.0E-6F
                || Math.abs(resolvePuffShapes(firstShape, 0.0F) - firstShape) > 1.0E-6F
                || firstOrder < Math.max(firstShape, secondShape)
                || firstOrder > 1.0F
                || Math.abs(fourHalfShapes - 0.5625F) > 1.0E-5F
                || Math.abs(fourDenseShapes - 0.84F) > 1.0E-5F
                || fourHalfShapes - 0.5F > 0.0625F + 1.0E-6F) {
            throw new IllegalStateException(
                    "PUFF limited union contract failed pair/fourHalf/fourDense="
                            + firstOrder + "/" + fourHalfShapes + "/" + fourDenseShapes
            );
        }

        float emptyCarrier = resolvePuffContinuousField(
                new float[0],
                new float[0],
                new float[0],
                0.0F
        );
        float outsideEnvelope = resolvePuffContinuousField(
                new float[]{0.0F},
                new float[]{1.0F},
                new float[]{1.0F},
                1.0F
        );
        float carrierMinimumCut = resolvePuffContinuousField(
                new float[]{0.34F},
                new float[]{0.34F},
                new float[]{0.0F},
                0.0F
        );
        float carrierMinimumVisible = resolvePuffContinuousField(
                new float[]{0.35F},
                new float[]{0.35F * (0.30F * 0.62F)},
                new float[]{0.0F},
                0.0F
        );
        float minimumMaterial = 0.30F * 0.62F;
        float protectedCore = resolvePuffContinuousField(
                new float[]{0.55F},
                new float[]{0.55F * minimumMaterial},
                new float[]{0.0F},
                0.0F
        );
        float protectedJunction = resolvePuffContinuousField(
                new float[]{0.15F, 0.15F},
                new float[]{0.15F * minimumMaterial, 0.15F * minimumMaterial},
                new float[]{0.0F, 0.0F},
                0.0F
        );
        float baseRootCorridor = resolvePuffContinuousField(
                new float[]{0.016F, 0.016F},
                new float[]{0.016F * minimumMaterial, 0.016F * minimumMaterial},
                new float[]{0.016F, 0.016F},
                0.0F
        );
        float baseRootWithoutProtection = resolvePuffContinuousField(
                new float[]{0.016F, 0.016F},
                new float[]{0.016F * minimumMaterial, 0.016F * minimumMaterial},
                new float[]{0.0F, 0.0F},
                0.0F
        );
        float permutationFirst = resolvePuffContinuousField(
                new float[]{0.42F, 0.18F, 0.31F},
                new float[]{0.21F, 0.09F, 0.155F},
                new float[]{0.10F, 0.04F, 0.02F},
                0.4775F
        );
        float permutationSecond = resolvePuffContinuousField(
                new float[]{0.31F, 0.42F, 0.18F},
                new float[]{0.155F, 0.21F, 0.09F},
                new float[]{0.02F, 0.10F, 0.04F},
                0.4775F
        );
        float[] permutationEnvelope = {0.42F, 0.18F, 0.31F, 0.09F};
        float[] permutationWeighted = {0.21F, 0.09F, 0.155F, 0.045F};
        float[] permutationBaseRoot = {0.10F, 0.04F, 0.02F, 0.01F};
        float permutationReference = resolvePuffContinuousField(
                permutationEnvelope,
                permutationWeighted,
                permutationBaseRoot,
                0.4775F
        );
        int permutationCount = 0;
        for (int first = 0; first < 4; first++) {
            for (int second = 0; second < 4; second++) {
                for (int third = 0; third < 4; third++) {
                    for (int fourth = 0; fourth < 4; fourth++) {
                        if (first == second || first == third || first == fourth
                                || second == third || second == fourth || third == fourth) {
                            continue;
                        }
                        int[] order = {first, second, third, fourth};
                        float[] envelopeOrder = new float[4];
                        float[] weightedOrder = new float[4];
                        float[] baseRootOrder = new float[4];
                        for (int index = 0; index < order.length; index++) {
                            envelopeOrder[index] = permutationEnvelope[order[index]];
                            weightedOrder[index] = permutationWeighted[order[index]];
                            baseRootOrder[index] = permutationBaseRoot[order[index]];
                        }
                        float permuted = resolvePuffContinuousField(
                                envelopeOrder,
                                weightedOrder,
                                baseRootOrder,
                                0.4775F
                        );
                        if (Math.abs(permuted - permutationReference) > 1.0E-6F) {
                            throw new IllegalStateException(
                                    "PUFF continuous carrier changed under permutation="
                                            + permutationReference + "/" + permuted
                            );
                        }
                        permutationCount++;
                    }
                }
            }
        }
        if (permutationCount != 24) {
            throw new IllegalStateException(
                    "PUFF continuous carrier permutation count=" + permutationCount
            );
        }
        float matureSupport = resolvePuffContinuousField(
                new float[]{0.40F},
                new float[]{0.40F},
                new float[]{0.0F},
                0.4775F
        );
        float lifecycleMinimumSupport = resolvePuffContinuousField(
                new float[]{0.40F},
                new float[]{0.40F * minimumMaterial},
                new float[]{0.0F},
                0.4775F
        );
        float lifecycleMidSupport = resolvePuffContinuousField(
                new float[]{0.40F},
                new float[]{0.40F * 0.62F},
                new float[]{0.0F},
                0.4775F
        );
        float baseRootStart = puffBaseRootWeight(
                0.34F,
                CloudMorphologyMemberTier.BASE
        );
        float baseRootMiddle = puffBaseRootWeight(
                0.445F,
                CloudMorphologyMemberTier.BASE
        );
        float baseRootEnd = puffBaseRootWeight(
                0.55F,
                CloudMorphologyMemberTier.BASE
        );
        float nonBaseRoot = puffBaseRootWeight(
                0.34F,
                CloudMorphologyMemberTier.MIDDLE
        );
        float previousCarrierDensity = -1.0F;
        float[] carrierSignals = {0.0F, 0.28F, 0.2844F, 0.4775F, 0.6775F, 0.68F, 1.0F};
        for (float carrierSignal : carrierSignals) {
            float carrierDensity = resolvePuffContinuousField(
                    new float[]{0.20F},
                    new float[]{0.20F * minimumMaterial},
                    new float[]{0.0F},
                    carrierSignal
            );
            if (carrierDensity + 1.0E-7F < previousCarrierDensity) {
                throw new IllegalStateException(
                        "PUFF carrier is not monotonic signal/density="
                                + carrierSignal + "/" + carrierDensity
                );
            }
            previousCarrierDensity = carrierDensity;
        }
        float[] exposedEnvelope = {0.20F};
        float[] exposedWeighted = {0.20F * minimumMaterial};
        float[] noRoot = {0.0F};
        float billowDisabledLow = resolvePuffContinuousField(
                exposedEnvelope,
                exposedWeighted,
                noRoot,
                0.4775F,
                0.0F,
                0.0F
        );
        float billowDisabledHigh = resolvePuffContinuousField(
                exposedEnvelope,
                exposedWeighted,
                noRoot,
                0.4775F,
                1.0F,
                0.0F
        );
        float previousBillowDensity = -1.0F;
        float maximumBillowDensity = 0.0F;
        for (float billowSignal : carrierSignals) {
            float billowDensity = resolvePuffContinuousField(
                    exposedEnvelope,
                    exposedWeighted,
                    noRoot,
                    0.4775F,
                    billowSignal,
                    1.0F
            );
            if (!Float.isFinite(billowDensity)
                    || billowDensity + 1.0E-7F < previousBillowDensity) {
                throw new IllegalStateException(
                        "PUFF billow carrier is not finite/monotonic signal/density="
                                + billowSignal + "/" + billowDensity
                );
            }
            previousBillowDensity = billowDensity;
            maximumBillowDensity = Math.max(maximumBillowDensity, billowDensity);
        }
        float protectedCoreBillowLow = resolvePuffContinuousField(
                new float[]{0.55F},
                new float[]{0.55F * minimumMaterial},
                noRoot,
                0.4775F,
                0.0F,
                1.0F
        );
        float protectedCoreBillowHigh = resolvePuffContinuousField(
                new float[]{0.55F},
                new float[]{0.55F * minimumMaterial},
                noRoot,
                0.4775F,
                1.0F,
                1.0F
        );
        float maximumAllowedBillowDensity = exposedEnvelope[0]
                * (exposedWeighted[0] / exposedEnvelope[0]);
        if (emptyCarrier != 0.0F
                || outsideEnvelope != 0.0F
                || carrierMinimumCut != 0.0F
                || carrierMinimumVisible <= 0.0008F
                || protectedCore <= 0.10F
                || protectedJunction <= 0.02F
                || baseRootCorridor <= 0.0008F
                || baseRootWithoutProtection != 0.0F
                || Math.abs(permutationFirst - permutationSecond) > 1.0E-6F
                || matureSupport <= 0.0008F
                || lifecycleMidSupport <= 0.0008F
                || lifecycleMinimumSupport <= 0.0008F
                || Float.floatToIntBits(billowDisabledLow)
                    != Float.floatToIntBits(billowDisabledHigh)
                || Float.floatToIntBits(protectedCoreBillowLow)
                    != Float.floatToIntBits(protectedCoreBillowHigh)
                || maximumBillowDensity > maximumAllowedBillowDensity + 1.0E-6F) {
            throw new IllegalStateException(
                    "PUFF continuous carrier contract failed empty/outside/cut/visible/core/"
                            + "junction/base/noBase/permutation/lifecycle/billow="
                            + emptyCarrier + "/" + outsideEnvelope + "/" + carrierMinimumCut
                            + "/" + carrierMinimumVisible + "/" + protectedCore + "/"
                            + protectedJunction + "/" + baseRootCorridor + "/"
                            + baseRootWithoutProtection + "/" + permutationFirst + "/"
                            + permutationSecond + "/" + matureSupport + "/"
                            + lifecycleMinimumSupport + "/" + billowDisabledLow + "/"
                            + billowDisabledHigh + "/" + protectedCoreBillowLow + "/"
                            + protectedCoreBillowHigh + "/" + maximumBillowDensity
            );
        }
        if (Math.abs(baseRootStart - 1.0F) > 1.0E-6F
                || Math.abs(baseRootMiddle - 0.5F) > 1.0E-5F
                || baseRootEnd != 0.0F
                || nonBaseRoot != 0.0F) {
            throw new IllegalStateException(
                    "PUFF base-root window contract failed start/middle/end/nonBase="
                            + baseRootStart + "/" + baseRootMiddle + "/"
                            + baseRootEnd + "/" + nonBaseRoot
            );
        }

        float rotatedMajor = 30.0F;
        float rotatedMinor = 27.0F;
        float testWorldZ = 29.25F;
        float oldZPadding = rotatedMinor * 1.05F;
        float conservativePadding = conservativeHorizontalPadding(rotatedMajor, rotatedMinor);
        float rotatedRadial = testWorldZ / rotatedMajor;
        if (rotatedRadial >= 1.0F
                || testWorldZ <= oldZPadding
                || testWorldZ > conservativePadding) {
            throw new IllegalStateException(
                    "PUFF rotated AABB regression radial/old/new="
                            + rotatedRadial + "/" + oldZPadding + "/" + conservativePadding
            );
        }

        int[] qualitySteps = {24, 32, 40, 64, 96};
        float[] governorScales = {1.0F, 0.5F, 0.4F};
        float expectedBaseCore = fixedPuffCoreFraction(
                12.0F,
                CloudMorphologyMemberTier.BASE
        );
        for (int qualityStepsValue : qualitySteps) {
            for (float governorScale : governorScales) {
                float fineStep = exteriorFineStepWorld(qualityStepsValue, governorScale);
                float observedBaseCore = fixedPuffCoreFraction(
                        12.0F,
                        CloudMorphologyMemberTier.BASE
                );
                if (!Float.isFinite(fineStep) || fineStep < 2.5F || fineStep > 8.0F
                        || Float.floatToIntBits(observedBaseCore)
                            != Float.floatToIntBits(expectedBaseCore)
                        || observedBaseCore < 0.299F) {
                    throw new IllegalStateException(
                            "PUFF fixed-feather contract failed steps/scale/fine/core="
                                    + qualityStepsValue + "/" + governorScale + "/" + fineStep
                                    + "/" + observedBaseCore
                    );
                }
            }
        }
    }

    private static boolean isDirectPuff(VolumetricRenderCell cell) {
        return cell != null
                && cell.cloudProfile() == 3
                && cell.envelopeRole() == VolumetricRenderCell.EnvelopeRole.MACRO
                && !cell.macroCarrier();
    }

    static float analyticRadiusAtHeight(float height01) {
        return analyticRadiusAtHeight(
                height01,
                0.0F,
                CloudMorphologyMemberTier.UNKNOWN
        );
    }

    static float analyticRadiusAtHeight(float height01, float seed01) {
        return analyticRadiusAtHeight(
                height01,
                seed01,
                CloudMorphologyMemberTier.UNKNOWN
        );
    }

    static float analyticRadiusAtHeight(
            float height01,
            float seed01,
            CloudMorphologyMemberTier tier
    ) {
        float h = Math.max(0.0F, Math.min(1.0F, height01));
        CloudMorphologyMemberTier safeTier = tier == null
                ? CloudMorphologyMemberTier.UNKNOWN
                : tier;
        float peakHeight = analyticPeakHeight(seed01, safeTier);
        float equatorRadius = analyticEquatorRadius(seed01, safeTier);
        if (safeTier != CloudMorphologyMemberTier.UNKNOWN) {
            float rootRadius = safeTier == CloudMorphologyMemberTier.BASE
                    ? analyticRootRadius(seed01, safeTier)
                    : 0.0F;
            float rootRatio = rootRadius / Math.max(equatorRadius, 0.001F);
            float verticalAtBase = -(float) Math.sqrt(Math.max(
                    0.0F,
                    1.0F - rootRatio * rootRatio
            ));
            float verticalCoordinate;
            if (h <= peakHeight) {
                verticalCoordinate = verticalAtBase
                        + (0.0F - verticalAtBase)
                        * smoothstep(0.0F, peakHeight, h);
            } else {
                float upper = Math.max(
                        0.0F,
                        Math.min(1.0F, (h - peakHeight) / Math.max(1.0F - peakHeight, 0.001F))
                );
                float upperProgress = smoothstep(0.0F, 1.0F, upper);
                verticalCoordinate = (float) Math.pow(
                        upperProgress,
                        analyticUpperPower(seed01, safeTier)
                );
            }
            return equatorRadius * (float) Math.sqrt(Math.max(
                    0.0F,
                    1.0F - verticalCoordinate * verticalCoordinate
            ));
        }
        if (h <= peakHeight) {
            float rootRadius = analyticRootRadius(seed01, safeTier);
            float lower = smoothstep(0.0F, peakHeight, h);
            return rootRadius + (equatorRadius - rootRadius) * lower;
        }
        float upper = Math.max(0.0F, Math.min(1.0F, (h - peakHeight) / (1.0F - peakHeight)));
        return equatorRadius * (float) Math.sqrt(Math.max(
                0.0F,
                1.0F - Math.pow(upper, analyticUpperPower(seed01, safeTier))
        ));
    }

    /**
     * CPU mirror of the structured shader envelope. {@code radial} is already
     * normalized by the descriptor's oriented horizontal radii.
     */
    static float structuredPuffEnvelopeDepth(
            float radial,
            float height01,
            float seed01,
            CloudMorphologyMemberTier tier,
            float span
    ) {
        CloudMorphologyMemberTier safeTier = tier == null
                ? CloudMorphologyMemberTier.UNKNOWN
                : tier;
        if (safeTier == CloudMorphologyMemberTier.UNKNOWN) {
            throw new IllegalArgumentException("Legacy PUFF has no structured envelope");
        }
        float h = Math.max(0.0F, Math.min(1.0F, height01));
        if (h <= 0.0F || h >= 1.0F) {
            return 0.0F;
        }
        float peakHeight = analyticPeakHeight(seed01, safeTier);
        float equatorRadius = analyticEquatorRadius(seed01, safeTier);
        float rootRadius = safeTier == CloudMorphologyMemberTier.BASE
                ? analyticRootRadius(seed01, safeTier)
                : 0.0F;
        float rootRatio = rootRadius / Math.max(equatorRadius, 0.001F);
        float verticalAtBase = -(float) Math.sqrt(Math.max(
                0.0F,
                1.0F - rootRatio * rootRatio
        ));
        float verticalCoordinate = h <= peakHeight
                ? verticalAtBase
                + (0.0F - verticalAtBase) * smoothstep(0.0F, peakHeight, h)
                : (float) Math.pow(
                        smoothstep(
                                0.0F,
                                1.0F,
                                Math.max(
                                        0.0F,
                                        Math.min(
                                                1.0F,
                                                (h - peakHeight)
                                                        / Math.max(1.0F - peakHeight, 0.001F)
                                        )
                                )
                        ),
                        analyticUpperPower(seed01, safeTier)
                );
        float q = (float) Math.sqrt(
                Math.pow(radial / Math.max(equatorRadius, 0.001F), 2.0D)
                        + verticalCoordinate * verticalCoordinate
        );
        float depth = Math.max(0.0F, Math.min(1.0F, 1.0F - q));
        if (safeTier == CloudMorphologyMemberTier.BASE) {
            float safeSpan = Math.max(span, 1.0F);
            float featherScale = Math.min(1.0F, safeSpan * 0.70F / 9.0F);
            float baseFeatherH = 5.0F * featherScale / safeSpan;
            depth *= smoothstep(0.0F, baseFeatherH, h);
        }
        return depth;
    }

    private static float analyticRootRadius(
            float seed01,
            CloudMorphologyMemberTier tier
    ) {
        float phase = analyticLobePhase(seed01);
        return switch (tier) {
            case BASE -> 0.70F + 0.06F * phase;
            case MIDDLE, CROWN -> 0.0F;
            case UNKNOWN -> 0.38F + 0.08F * phase;
        };
    }

    private static float analyticPeakHeight(
            float seed01,
            CloudMorphologyMemberTier tier
    ) {
        float phase = analyticLobePhase(seed01);
        return switch (tier) {
            case BASE -> 0.32F + 0.06F * phase;
            case MIDDLE -> 0.38F + 0.06F * phase;
            case CROWN -> 0.43F + 0.07F * phase;
            case UNKNOWN -> 0.33F + 0.10F * phase;
        };
    }

    private static float analyticEquatorRadius(
            float seed01,
            CloudMorphologyMemberTier tier
    ) {
        float phase = analyticLobePhase(seed01);
        return switch (tier) {
            case BASE -> 0.92F + 0.06F * phase;
            case MIDDLE -> 0.94F + 0.06F * phase;
            case CROWN -> 0.90F + 0.06F * phase;
            case UNKNOWN -> 1.0F;
        };
    }

    private static float analyticUpperPower(
            float seed01,
            CloudMorphologyMemberTier tier
    ) {
        float phase = analyticLobePhase(seed01);
        return switch (tier) {
            case BASE -> 0.95F + 0.20F * phase;
            case MIDDLE -> 1.30F + 0.25F * phase;
            case CROWN -> 1.70F + 0.30F * phase;
            case UNKNOWN -> 1.35F;
        };
    }

    private static float analyticLobePhase(float seed01) {
        double value = seed01 * 0.754877666D + 0.17320508D;
        return (float) (value - Math.floor(value));
    }

    private static float resolvePuffShapes(float... candidates) {
        float[] accumulated = accumulatePuffShapes(candidates);
        return resolvePuffAccumulation(accumulated);
    }

    private static float[] accumulatePuffShapes(float... candidates) {
        float strongest = 0.0F;
        float secondStrongest = 0.0F;
        if (candidates != null) {
            for (float raw : candidates) {
                float candidate = Math.max(0.0F, Math.min(1.0F, raw));
                float previousMaximum = strongest;
                strongest = Math.max(previousMaximum, candidate);
                secondStrongest = Math.max(
                        secondStrongest,
                        Math.min(previousMaximum, candidate)
                );
            }
        }
        return new float[]{strongest, secondStrongest};
    }

    private static float resolvePuffAccumulation(float[] accumulated) {
        float strongest = accumulated == null || accumulated.length < 1
                ? 0.0F
                : accumulated[0];
        float secondStrongest = accumulated == null || accumulated.length < 2
                ? 0.0F
                : accumulated[1];
        return strongest + 0.25F * secondStrongest * (1.0F - strongest);
    }

    private static float resolvePuffContinuousField(
            float[] envelopeCandidates,
            float[] weightedCandidates,
            float[] baseRootCandidates,
            float carrierSignal
    ) {
        return resolvePuffContinuousField(
                envelopeCandidates,
                weightedCandidates,
                baseRootCandidates,
                carrierSignal,
                0.5F,
                0.0F
        );
    }

    private static float resolvePuffContinuousField(
            float[] envelopeCandidates,
            float[] weightedCandidates,
            float[] baseRootCandidates,
            float carrierSignal,
            float billowSignal,
            float billowStrength
    ) {
        float[] envelopeAccumulated = accumulatePuffShapes(envelopeCandidates);
        float envelope = resolvePuffAccumulation(envelopeAccumulated);
        if (envelope <= 0.0F) {
            return 0.0F;
        }
        float weighted = resolvePuffShapes(weightedCandidates);
        float[] baseRootAccumulated = accumulatePuffShapes(baseRootCandidates);
        float overlap = envelopeAccumulated[1];
        float baseRootOverlap = baseRootAccumulated[1];
        float materialFactor = Math.max(
                0.0F,
                Math.min(1.0F, weighted / Math.max(envelope, 0.0001F))
        );
        float carrier = smoothstep(0.28F, 0.68F, carrierSignal);
        float exposedIso = 0.34F + (0.08F - 0.34F) * carrier;
        float coreProtection = smoothstep(0.38F, 0.55F, envelope);
        float junctionProtection = smoothstep(0.015F, 0.075F, overlap);
        float baseJunctionProtection = smoothstep(0.004F, 0.016F, baseRootOverlap);
        float protection = Math.max(
                coreProtection,
                Math.max(junctionProtection, baseJunctionProtection)
        );
        float mediumBillow = smoothstep(0.28F, 0.68F, billowSignal);
        float billowIso = Math.max(
                0.04F,
                Math.min(
                        0.40F,
                        exposedIso + (0.5F - mediumBillow) * 0.12F * billowStrength
                )
        );
        float surfaceIso = billowIso + (0.012F - billowIso) * protection;
        float continuousShape = Math.max(
                0.0F,
                (envelope - surfaceIso) / Math.max(1.0F - surfaceIso, 0.001F)
        );
        return continuousShape * materialFactor;
    }

    private static float puffBaseRootWeight(
            float localHeight01,
            CloudMorphologyMemberTier tier
    ) {
        if (tier != CloudMorphologyMemberTier.BASE) {
            return 0.0F;
        }
        return 1.0F - smoothstep(0.34F, 0.55F, localHeight01);
    }

    private static float packPuffTierAndVerticalDevelopment(
            CloudMorphologyMemberTier tier,
            float verticalDevelopment
    ) {
        CloudMorphologyMemberTier safeTier = tier == null
                ? CloudMorphologyMemberTier.UNKNOWN
                : tier;
        float development = Math.max(0.0F, Math.min(0.999F, verticalDevelopment));
        return safeTier.gpuId() + development * 0.25F;
    }

    private static int unpackPuffTierGpuId(float packed) {
        return Math.max(0, Math.min(3, (int) Math.floor(packed + 1.0E-5F)));
    }

    private static float unpackPuffVerticalDevelopment(float packed) {
        float fraction = packed - (float) Math.floor(packed + 1.0E-5F);
        return Math.max(0.0F, Math.min(0.999F, fraction * 4.0F));
    }

    private static float conservativeHorizontalPadding(float major, float minor) {
        return Math.max(Math.max(major, minor) * 1.05F, 1.0F);
    }

    static float exteriorFineStepWorld(int raymarchSteps, float stepScale) {
        int clampedSteps = Math.max(8, Math.min(128, raymarchSteps));
        float clampedScale = Math.max(0.4F, Math.min(1.0F, stepScale));
        int budget = Math.max(8, Math.min(128, (int) (clampedSteps * clampedScale)));
        float qualityStride = (float) Math.sqrt(96.0F / budget);
        return Math.max(2.5F, Math.min(8.0F, 2.5F * qualityStride));
    }

    private static float fixedPuffFeatherScale(
            float spanWorld,
            CloudMorphologyMemberTier tier
    ) {
        float safeSpan = Math.max(1.0F, spanWorld);
        float desiredBase = tier == CloudMorphologyMemberTier.BASE ? 5.0F : 4.0F;
        float desiredTop = tier == CloudMorphologyMemberTier.CROWN ? 3.5F : 4.0F;
        float desiredTotal = desiredBase + desiredTop;
        return Math.min(1.0F, safeSpan * 0.70F / Math.max(0.001F, desiredTotal));
    }

    private static float fixedPuffCoreFraction(
            float spanWorld,
            CloudMorphologyMemberTier tier
    ) {
        float safeSpan = Math.max(1.0F, spanWorld);
        float desiredBase = tier == CloudMorphologyMemberTier.BASE ? 5.0F : 4.0F;
        float desiredTop = tier == CloudMorphologyMemberTier.CROWN ? 3.5F : 4.0F;
        float effectiveFeather = (desiredBase + desiredTop)
                * fixedPuffFeatherScale(safeSpan, tier);
        return Math.max(0.0F, 1.0F - effectiveFeather / safeSpan);
    }

    private static int packPair(int first, int second) {
        int lowDigit = first < 0 ? 0 : first + 1;
        int highDigit = second < 0 ? 0 : second + 1;
        return lowDigit + highDigit * PACK_BASE;
    }

    private static int unpackPair(int packed, int rank) {
        int digit = rank == 0 ? packed % PACK_BASE : packed / PACK_BASE;
        return digit - 1;
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = Math.max(0.0F, Math.min(1.0F, (value - edge0) / (edge1 - edge0)));
        return t * t * (3.0F - 2.0F * t);
    }

    private static int tileCoordinate(double world, double origin, float tileWorld) {
        int coordinate = (int) Math.floor((world - origin) / tileWorld);
        return Math.max(0, Math.min(GRID_SIZE - 1, coordinate));
    }

    private static CoverageProof verifyCpuTileCoverage() {
        if (lobeCount <= 0 || !Double.isFinite(uploadedOriginX)
                || !Double.isFinite(uploadedOriginZ) || !Float.isFinite(uploadedExtent)) {
            return new CoverageProof(0, 0, 0);
        }
        float tileWorld = uploadedExtent / GRID_SIZE;
        int samples = 0;
        int missing = 0;
        int outside = 0;
        for (int slot = 0; slot < lobeCount; slot++) {
            int base = slot * 4;
            float centerX = POS_RADIUS[base];
            float centerZ = POS_RADIUS[base + 1];
            float radiusMajor = POS_RADIUS[base + 2];
            float radiusMinor = POS_RADIUS[base + 3];
            float orientation = SHAPE[base];
            float cos = (float) Math.cos(orientation);
            float sin = (float) Math.sin(orientation);
            for (int localZSample = -32; localZSample <= 32; localZSample++) {
                float localZ01 = localZSample / 32.0F;
                for (int localXSample = -32; localXSample <= 32; localXSample++) {
                    float localX01 = localXSample / 32.0F;
                    if (localX01 * localX01 + localZ01 * localZ01 > 0.999F * 0.999F) {
                        continue;
                    }
                    float localX = localX01 * radiusMajor;
                    float localZ = localZ01 * radiusMinor;
                    float worldX = centerX + localX * cos - localZ * sin;
                    float worldZ = centerZ + localX * sin + localZ * cos;
                    int x = (int) Math.floor((worldX - uploadedOriginX) / tileWorld);
                    int z = (int) Math.floor((worldZ - uploadedOriginZ) / tileWorld);
                    samples++;
                    if (x < 0 || x >= GRID_SIZE || z < 0 || z >= GRID_SIZE) {
                        outside++;
                    } else if (!tileContainsCandidate(z * GRID_SIZE + x, slot)) {
                        missing++;
                    }
                }
            }
        }
        return new CoverageProof(samples, missing, outside);
    }

    private static boolean tileContainsCandidate(int tile, int slot) {
        int base = tile * CANDIDATES_PER_TILE;
        for (int rank = 0; rank < CANDIDATES_PER_TILE; rank++) {
            if (TILE_INDICES[base + rank] == slot) {
                return true;
            }
        }
        return false;
    }

    private static ShiftScore bestTranslation(int[] gpuPacked, int maximumShift) {
        ShiftScore best = new ShiftScore(0, 0, -1);
        for (int shiftZ = -maximumShift; shiftZ <= maximumShift; shiftZ++) {
            for (int shiftX = -maximumShift; shiftX <= maximumShift; shiftX++) {
                int matches = 0;
                for (int tile = 0; tile < GRID_SIZE * GRID_SIZE; tile++) {
                    if (TILE_COUNTS[tile] <= 0) {
                        continue;
                    }
                    int x = tile % GRID_SIZE;
                    int z = tile / GRID_SIZE;
                    int gpuX = x + shiftX;
                    int gpuZ = z + shiftZ;
                    if (gpuX < 0 || gpuX >= GRID_SIZE || gpuZ < 0 || gpuZ >= GRID_SIZE) {
                        continue;
                    }
                    if (tileEqualsGpu(tile, gpuZ * GRID_SIZE + gpuX, gpuPacked)) {
                        matches++;
                    }
                }
                if (matches > best.matches()) {
                    best = new ShiftScore(shiftX, shiftZ, matches);
                }
            }
        }
        return best;
    }

    private static int transformedActiveMatches(int[] gpuPacked, Transform transform) {
        int matches = 0;
        for (int tile = 0; tile < GRID_SIZE * GRID_SIZE; tile++) {
            if (TILE_COUNTS[tile] <= 0) {
                continue;
            }
            int x = tile % GRID_SIZE;
            int z = tile / GRID_SIZE;
            int gpuX;
            int gpuZ;
            switch (transform) {
                case FLIP_X -> {
                    gpuX = GRID_SIZE - 1 - x;
                    gpuZ = z;
                }
                case FLIP_Z -> {
                    gpuX = x;
                    gpuZ = GRID_SIZE - 1 - z;
                }
                case FLIP_BOTH -> {
                    gpuX = GRID_SIZE - 1 - x;
                    gpuZ = GRID_SIZE - 1 - z;
                }
                case TRANSPOSE -> {
                    gpuX = z;
                    gpuZ = x;
                }
                default -> {
                    gpuX = x;
                    gpuZ = z;
                }
            }
            if (tileEqualsGpu(tile, gpuZ * GRID_SIZE + gpuX, gpuPacked)) {
                matches++;
            }
        }
        return matches;
    }

    private static boolean tileEqualsGpu(int cpuTile, int gpuTile, int[] gpuPacked) {
        int cpuBase = cpuTile * CANDIDATES_PER_TILE;
        int gpuBase = gpuTile * 4;
        for (int component = 0; component < 4; component++) {
            int expected = packPair(
                    TILE_INDICES[cpuBase + component * 2],
                    TILE_INDICES[cpuBase + component * 2 + 1]
            );
            if (gpuPacked[gpuBase + component] != expected) {
                return false;
            }
        }
        return true;
    }

    private static void insertCandidate(int tile, int slot, float score) {
        int base = tile * CANDIDATES_PER_TILE;
        for (int rank = 0; rank < CANDIDATES_PER_TILE; rank++) {
            int index = base + rank;
            float previousScore = TILE_SCORES[index];
            int previousSlot = TILE_INDICES[index];
            if (score > previousScore
                    || (score == previousScore && previousSlot >= 0 && slot > previousSlot)) {
                continue;
            }
            for (int shift = CANDIDATES_PER_TILE - 1; shift > rank; shift--) {
                TILE_SCORES[base + shift] = TILE_SCORES[base + shift - 1];
                TILE_INDICES[base + shift] = TILE_INDICES[base + shift - 1];
            }
            TILE_SCORES[index] = score;
            TILE_INDICES[index] = slot;
            return;
        }
    }

    private static void uploadVec4Array(int program, String name, float[] values) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location < 0) {
            location = GL20.glGetUniformLocation(program, name + "[0]");
        }
        if (location >= 0) {
            GL20.glUniform4fv(location, values);
        }
    }

    private static long descriptorSignature() {
        long hash = 0xcbf29ce484222325L;
        hash = mix(hash, lobeCount);
        hash = mix(hash, truncatedLobes);
        for (int index = 0; index < lobeCount * 4; index++) {
            hash = mix(hash, Float.floatToIntBits(POS_RADIUS[index]));
            hash = mix(hash, Float.floatToIntBits(SHAPE[index]));
            hash = mix(hash, Float.floatToIntBits(MEDIA[index]));
        }
        return hash;
    }

    private static long gridSignature(double originX, double originZ, float extent) {
        long hash = gridSignatureHeader(originX, originZ, extent, lobeCount);
        for (int slot = 0; slot < lobeCount; slot++) {
            int base = slot * 4;
            hash = mixGridLobe(
                    hash,
                    POS_RADIUS[base],
                    POS_RADIUS[base + 1],
                    POS_RADIUS[base + 2],
                    POS_RADIUS[base + 3],
                    SHAPE[base]
            );
        }
        return hash;
    }

    private static long gridSignatureHeader(
            double originX,
            double originZ,
            float extent,
            int count
    ) {
        long hash = 0xcbf29ce484222325L;
        hash = mix(hash, GRID_SIGNATURE_VERSION);
        hash = mix(hash, GRID_SIZE);
        hash = mix(hash, CANDIDATES_PER_TILE);
        hash = mix(hash, Double.doubleToLongBits(originX));
        hash = mix(hash, Double.doubleToLongBits(originZ));
        hash = mix(hash, Float.floatToIntBits(extent));
        return mix(hash, count);
    }

    private static long mixGridLobe(
            long hash,
            float centerX,
            float centerZ,
            float radiusMajor,
            float radiusMinor,
            float orientationRadians
    ) {
        hash = mix(hash, Float.floatToIntBits(centerX));
        hash = mix(hash, Float.floatToIntBits(centerZ));
        hash = mix(hash, Float.floatToIntBits(radiusMajor));
        hash = mix(hash, Float.floatToIntBits(radiusMinor));
        return mix(hash, Float.floatToIntBits(orientationRadians));
    }

    /**
     * A CPU upload must not inherit pixel-unpack state from Minecraft or an
     * external renderer. In particular, a bound PBO would reinterpret the
     * direct buffer address as a byte offset.
     */
    private record PixelUnpackState(
            int buffer,
            int alignment,
            int swapBytes,
            int rowLength,
            int imageHeight,
            int skipPixels,
            int skipRows,
            int skipImages
    ) implements AutoCloseable {
        private static PixelUnpackState beginTightCpuUpload() {
            PixelUnpackState state = new PixelUnpackState(
                    GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING),
                    GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT),
                    GL11.glGetInteger(GL11.GL_UNPACK_SWAP_BYTES),
                    GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH),
                    GL11.glGetInteger(GL12.GL_UNPACK_IMAGE_HEIGHT),
                    GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS),
                    GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS),
                    GL11.glGetInteger(GL12.GL_UNPACK_SKIP_IMAGES)
            );
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_UNPACK_SWAP_BYTES, GL11.GL_FALSE);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, 0);
            return state;
        }

        @Override
        public void close() {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, alignment);
            GL11.glPixelStorei(GL11.GL_UNPACK_SWAP_BYTES, swapBytes);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, rowLength);
            GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, imageHeight);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, skipPixels);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, skipRows);
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, skipImages);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, buffer);
        }
    }

    private record PixelPackState(
            int buffer,
            int alignment,
            int swapBytes,
            int rowLength,
            int imageHeight,
            int skipPixels,
            int skipRows,
            int skipImages
    ) implements AutoCloseable {
        private static PixelPackState beginTightCpuReadback() {
            PixelPackState state = new PixelPackState(
                    GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING),
                    GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT),
                    GL11.glGetInteger(GL11.GL_PACK_SWAP_BYTES),
                    GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH),
                    GL11.glGetInteger(GL12.GL_PACK_IMAGE_HEIGHT),
                    GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS),
                    GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS),
                    GL11.glGetInteger(GL12.GL_PACK_SKIP_IMAGES)
            );
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_PACK_SWAP_BYTES, GL11.GL_FALSE);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL12.GL_PACK_IMAGE_HEIGHT, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL12.GL_PACK_SKIP_IMAGES, 0);
            return state;
        }

        @Override
        public void close() {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, alignment);
            GL11.glPixelStorei(GL11.GL_PACK_SWAP_BYTES, swapBytes);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, rowLength);
            GL11.glPixelStorei(GL12.GL_PACK_IMAGE_HEIGHT, imageHeight);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, skipPixels);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, skipRows);
            GL11.glPixelStorei(GL12.GL_PACK_SKIP_IMAGES, skipImages);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, buffer);
        }
    }

    private enum Transform {
        IDENTITY,
        FLIP_X,
        FLIP_Z,
        FLIP_BOTH,
        TRANSPOSE
    }

    private record ShiftScore(int x, int z, int matches) {
    }

    private record CoverageProof(int samples, int missing, int outside) {
    }

    private static final class Bounds {
        private int minX = GRID_SIZE;
        private int minZ = GRID_SIZE;
        private int maxX = -1;
        private int maxZ = -1;

        private void include(int x, int z) {
            minX = Math.min(minX, x);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxZ = Math.max(maxZ, z);
        }

        @Override
        public String toString() {
            return maxX < 0 ? "empty" : minX + ".." + maxX + "," + minZ + ".." + maxZ;
        }
    }

    private static long mix(long hash, long value) {
        return (hash ^ value) * 0x100000001b3L;
    }
}
