package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import net.Gabou.projectatmosphere.clouds.type.CloudMorphologyFamily;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pure-CPU selection and conservative tile index for direct severe lobes. */
public final class StormLobeSpatialIndex {
    public static final int GRID_SIZE = 256;
    public static final int MAX_LOBES = 64;
    public static final int MAX_GROUPS = 8;
    public static final int CANDIDATES_PER_TILE = 8;
    public static final int PACK_BASE = MAX_LOBES + 1;
    public static final int DESCRIPTOR_WIDTH = StormLobeDescriptor.TEXELS_PER_DESCRIPTOR;
    public static final int DESCRIPTOR_HEIGHT = MAX_LOBES;

    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final ThreadLocal<GridScratch> GRID_SCRATCH =
            ThreadLocal.withInitial(GridScratch::new);

    private StormLobeSpatialIndex() {
    }

    public static boolean isDirectStorm(VolumetricRenderCell cell) {
        if (cell == null
                || cell.morphologyFamily() != CloudMorphologyFamily.STORM_ANVIL.ordinal()) {
            return false;
        }
        return switch (cell.envelopeRole()) {
            case BASE, CORE, TOWER, ANVIL -> true;
            default -> false;
        };
    }

    /**
     * Only canonical cluster members participate in direct storm topology.
     * Macro/LOD projections remain a broad-map fallback and must not churn the
     * descriptor grid or invalidate temporal history.
     */
    private static boolean isClusterDirectStorm(VolumetricRenderCell cell) {
        return isDirectStorm(cell) && !cell.macroCarrier();
    }

    public static long topologySignature(List<VolumetricRenderCell> cells) {
        long hash = FNV_OFFSET;
        int count = 0;
        if (cells != null) {
            for (VolumetricRenderCell cell : cells) {
                if (!isClusterDirectStorm(cell)) {
                    continue;
                }
                hash = mixUuid(hash, cell.morphologyGroupId());
                hash = mixUuid(hash, cell.fieldId());
                hash = mix(hash, cell.morphologyMemberIndex());
                hash = mix(hash, cell.morphologyMemberCount());
                hash = mix(hash, cell.envelopeRole().gpuId());
                count++;
            }
        }
        return mix(hash, count);
    }

    /**
     * Changes only when conservative tile bounds or topology changes. Smooth
     * motion inside the padded tile footprint refreshes descriptors but does
     * not rebuild the 256-square index.
     */
    public static long gridSignature(
            List<VolumetricRenderCell> cells,
            double originX,
            double originZ,
            float extent
    ) {
        long hash = mix(mix(mix(FNV_OFFSET, quantize(originX, 16.0D)),
                quantize(originZ, 16.0D)), Float.floatToIntBits(extent));
        float tileWorld = extent / GRID_SIZE;
        int count = 0;
        if (cells != null && tileWorld > 0.0F) {
            for (VolumetricRenderCell cell : cells) {
                if (!isClusterDirectStorm(cell)) {
                    continue;
                }
                float shear = conservativeShear(cell);
                float profileScale = conservativeProfileScale(
                        StormLobeDescriptor.Role.fromEnvelope(cell.envelopeRole())
                );
                float bound = Math.max(cell.radiusMajor(), cell.radiusMinor())
                        * profileScale + shear + tileWorld;
                hash = mixUuid(hash, cell.morphologyGroupId());
                hash = mixUuid(hash, cell.fieldId());
                hash = mix(hash, cell.morphologyMemberIndex());
                hash = mix(hash, tileCoordinate(cell.x() - bound, originX, tileWorld));
                hash = mix(hash, tileCoordinate(cell.x() + bound, originX, tileWorld));
                hash = mix(hash, tileCoordinate(cell.z() - bound, originZ, tileWorld));
                hash = mix(hash, tileCoordinate(cell.z() + bound, originZ, tileWorld));
                count++;
            }
        }
        return mix(hash, count);
    }

    public static StormGeometryBuildInput captureInput(
            long sessionGeneration,
            long requestGeneration,
            List<VolumetricRenderCell> cells,
            double cameraX,
            double cameraZ,
            double originX,
            double originZ,
            float extent
    ) {
        int severeCount = 0;
        if (cells != null) {
            for (VolumetricRenderCell cell : cells) {
                if (isClusterDirectStorm(cell)) {
                    severeCount++;
                }
            }
        }
        StormLobeDescriptor[] descriptors = new StormLobeDescriptor[severeCount];
        int index = 0;
        if (cells != null) {
            for (VolumetricRenderCell cell : cells) {
                if (isClusterDirectStorm(cell)) {
                    descriptors[index++] = StormLobeDescriptor.fromCell(cell, -1);
                }
            }
        }
        return new StormGeometryBuildInput(
                sessionGeneration,
                requestGeneration,
                topologySignature(cells),
                gridSignature(cells, originX, originZ, extent),
                cameraX,
                cameraZ,
                originX,
                originZ,
                extent,
                descriptors
        );
    }

    public static StormGeometryBuild build(StormGeometryBuildInput input) {
        long started = System.nanoTime();
        List<GroupBucket> groups = collectCompleteGroups(input.descriptorsUnsafe());
        groups.sort(Comparator
                .comparingDouble((GroupBucket group) -> group.distanceSquared(input.cameraX(), input.cameraZ()))
                .thenComparing(GroupBucket::groupId));

        List<StormLobeDescriptor> selected = new ArrayList<>(MAX_LOBES);
        int omittedGroups = 0;
        int groupSlot = 0;
        for (GroupBucket group : groups) {
            if (!group.complete()
                    || groupSlot >= MAX_GROUPS
                    || selected.size() + group.members().size() > MAX_LOBES) {
                omittedGroups++;
                continue;
            }
            group.members().sort(StormLobeDescriptor.STABLE_IDENTITY_ORDER);
            for (StormLobeDescriptor descriptor : group.members()) {
                selected.add(descriptor.withGroupSlot(groupSlot));
            }
            groupSlot++;
        }

        StormLobeDescriptor[] selectedArray = selected.toArray(StormLobeDescriptor[]::new);
        float[] descriptorTexels = new float[MAX_LOBES * StormLobeDescriptor.FLOATS_PER_DESCRIPTOR];
        for (int index = 0; index < selectedArray.length; index++) {
            selectedArray[index].writeTexels(
                    descriptorTexels,
                    index * StormLobeDescriptor.FLOATS_PER_DESCRIPTOR
            );
        }

        GridBuild grid = buildGrid(selectedArray, input.originX(), input.originZ(), input.extent());
        return new StormGeometryBuild(
                input,
                selectedArray,
                descriptorTexels,
                grid.packedTexels(),
                grid.activeTiles(),
                grid.overflowTiles(),
                grid.maxCandidatesPerTile(),
                omittedGroups,
                System.nanoTime() - started
        );
    }

    static int packPair(int firstIndex, int secondIndex) {
        int first = firstIndex < 0 ? 0 : firstIndex + 1;
        int second = secondIndex < 0 ? 0 : secondIndex + 1;
        return first + second * PACK_BASE;
    }

    static int unpackCandidate(float packed, int pairRank) {
        int encoded = Math.round(packed);
        int digit = pairRank == 0 ? encoded % PACK_BASE : encoded / PACK_BASE;
        return digit - 1;
    }

    private static GridBuild buildGrid(
            StormLobeDescriptor[] descriptors,
            double originX,
            double originZ,
            float extent
    ) {
        int tileCount = GRID_SIZE * GRID_SIZE;
        GridScratch scratch = GRID_SCRATCH.get();
        int[] indices = scratch.indices;
        int[] rawCounts = scratch.rawCounts;
        Arrays.fill(indices, -1);
        Arrays.fill(rawCounts, 0);
        float tileWorld = extent / GRID_SIZE;
        float halfDiagonal = tileWorld * 0.70710677F;

        // One stable ANVIL witness represents each complete group. Its padded
        // AABB is the union of all member bounds, so it may admit false
        // positives but cannot omit a lobe that the descriptor field can see.
        // Eight admitted groups fit the eight packed candidate slots exactly.
        for (int groupSlot = 0; groupSlot < MAX_GROUPS; groupSlot++) {
            int witness = -1;
            float minXWorld = Float.POSITIVE_INFINITY;
            float maxXWorld = Float.NEGATIVE_INFINITY;
            float minZWorld = Float.POSITIVE_INFINITY;
            float maxZWorld = Float.NEGATIVE_INFINITY;
            for (int descriptorIndex = 0; descriptorIndex < descriptors.length; descriptorIndex++) {
                StormLobeDescriptor descriptor = descriptors[descriptorIndex];
                if (descriptor.groupSlot() != groupSlot) {
                    continue;
                }
                if (witness < 0
                        || (descriptor.role() == StormLobeDescriptor.Role.ANVIL
                        && descriptors[witness].role() != StormLobeDescriptor.Role.ANVIL)) {
                    witness = descriptorIndex;
                }
                float shear = (float) Math.hypot(descriptor.shearX(), descriptor.shearZ());
                float profileScale = conservativeProfileScale(descriptor.role());
                float bound = Math.max(descriptor.majorRadius(), descriptor.minorRadius())
                        * profileScale + shear + halfDiagonal + tileWorld;
                minXWorld = Math.min(minXWorld, (float) descriptor.centerX() - bound);
                maxXWorld = Math.max(maxXWorld, (float) descriptor.centerX() + bound);
                minZWorld = Math.min(minZWorld, (float) descriptor.centerZ() - bound);
                maxZWorld = Math.max(maxZWorld, (float) descriptor.centerZ() + bound);
            }
            if (witness < 0) {
                continue;
            }
            int minX = tileCoordinate(minXWorld, originX, tileWorld);
            int maxX = tileCoordinate(maxXWorld, originX, tileWorld);
            int minZ = tileCoordinate(minZWorld, originZ, tileWorld);
            int maxZ = tileCoordinate(maxZWorld, originZ, tileWorld);
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    int tile = z * GRID_SIZE + x;
                    int slot = rawCounts[tile]++;
                    if (slot < CANDIDATES_PER_TILE) {
                        indices[tile * CANDIDATES_PER_TILE + slot] = witness;
                    }
                }
            }
        }

        float[] packed = new float[tileCount * 4];
        int active = 0;
        int overflow = 0;
        int maximum = 0;
        for (int tile = 0; tile < tileCount; tile++) {
            int count = rawCounts[tile];
            if (count > 0) {
                active++;
            }
            if (count > CANDIDATES_PER_TILE) {
                overflow++;
            }
            maximum = Math.max(maximum, count);
            int candidateBase = tile * CANDIDATES_PER_TILE;
            int packedBase = tile * 4;
            for (int pair = 0; pair < 4; pair++) {
                packed[packedBase + pair] = packPair(
                        indices[candidateBase + pair * 2],
                        indices[candidateBase + pair * 2 + 1]
                );
            }
        }
        return new GridBuild(packed, active, overflow, maximum);
    }

    private static List<GroupBucket> collectCompleteGroups(StormLobeDescriptor[] descriptors) {
        Map<UUID, GroupBucket> byId = new HashMap<>();
        for (StormLobeDescriptor descriptor : descriptors) {
            byId.computeIfAbsent(
                    descriptor.groupId(),
                    ignored -> new GroupBucket(descriptor.groupId(), descriptor.memberCount())
            ).add(descriptor);
        }
        return new ArrayList<>(byId.values());
    }

    private static float conservativeShear(VolumetricRenderCell cell) {
        float roleScale = switch (cell.envelopeRole()) {
            case BASE, CORE, TOWER, ANVIL -> StormLobeDescriptor.shearScale(
                    StormLobeDescriptor.Role.fromEnvelope(cell.envelopeRole())
            );
            default -> 0.0F;
        };
        return cell.radiusMajor() * roleScale * (0.35F + 0.65F * cell.verticalDevelopment());
    }

    /**
     * Upper bound for the role-specific radial profile in the shader, with a
     * small guard band for quantized tile lookup. Candidate tiles gate whole
     * storm groups, so underestimating this support exposes a tile-aligned
     * planar face even though the analytic lobe itself is curved.
     */
    private static float conservativeProfileScale(StormLobeDescriptor.Role role) {
        return switch (role) {
            case BASE -> 1.20F;
            case CORE -> 1.20F;
            case TOWER -> 1.20F;
            case ANVIL -> 1.24F;
        };
    }

    private static int tileCoordinate(double world, double origin, float tileWorld) {
        return Math.max(0, Math.min(GRID_SIZE - 1, (int) Math.floor((world - origin) / tileWorld)));
    }

    private static long quantize(double value, double scale) {
        return Double.isFinite(value) ? Math.round(value * scale) : 0L;
    }

    private static long mixUuid(long hash, UUID value) {
        UUID safe = value == null ? new UUID(0L, 0L) : value;
        return mix(mix(hash, safe.getMostSignificantBits()), safe.getLeastSignificantBits());
    }

    private static long mix(long hash, long value) {
        return (hash ^ value) * FNV_PRIME;
    }

    private record GridBuild(float[] packedTexels, int activeTiles, int overflowTiles, int maxCandidatesPerTile) {
    }

    /** Worker-local scratch avoids allocating the multi-megabyte sort grid on every rebuild. */
    private static final class GridScratch {
        private final int[] indices = new int[GRID_SIZE * GRID_SIZE * CANDIDATES_PER_TILE];
        private final int[] rawCounts = new int[GRID_SIZE * GRID_SIZE];
    }

    private static final class GroupBucket {
        private final UUID groupId;
        private final int expectedCount;
        private final List<StormLobeDescriptor> members = new ArrayList<>();
        private int roleMask;
        private long indexMask;

        private GroupBucket(UUID groupId, int expectedCount) {
            this.groupId = groupId;
            this.expectedCount = Math.max(1, expectedCount);
        }

        private void add(StormLobeDescriptor descriptor) {
            if (descriptor.memberIndex() < 64) {
                long bit = 1L << descriptor.memberIndex();
                if ((indexMask & bit) != 0L) {
                    return;
                }
                indexMask |= bit;
            }
            members.add(descriptor);
            roleMask |= 1 << descriptor.role().gpuId();
        }

        private boolean complete() {
            return members.size() == expectedCount && roleMask == 0b1111;
        }

        private double distanceSquared(double cameraX, double cameraZ) {
            double x = 0.0D;
            double z = 0.0D;
            for (StormLobeDescriptor member : members) {
                x += member.centerX();
                z += member.centerZ();
            }
            double scale = 1.0D / Math.max(1, members.size());
            double dx = x * scale - cameraX;
            double dz = z * scale - cameraZ;
            return dx * dx + dz * dz;
        }

        private UUID groupId() { return groupId; }
        private List<StormLobeDescriptor> members() { return members; }
    }
}
