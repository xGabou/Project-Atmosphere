package net.Gabou.projectatmosphere.modules.seasonaltrees.integration;

import com.dtteam.dynamictrees.api.network.MapSignal;
import com.dtteam.dynamictrees.api.network.NodeInspector;
import com.dtteam.dynamictrees.api.voxmap.SimpleVoxmap;
import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.dtteam.dynamictrees.block.leaves.LeavesProperties;
import com.dtteam.dynamictrees.block.soil.SoilBlock;
import com.dtteam.dynamictrees.tree.TreeHelper;
import com.dtteam.dynamictrees.tree.family.Family;
import com.dtteam.dynamictrees.tree.species.Species;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

public final class DynamicTreesDormancyHelper {
    private DynamicTreesDormancyHelper() {
    }

    private static final class LeafContext {
        private final Species species;
        private final Family family;

        private LeafContext(Species species, Family family) {
            this.species = species;
            this.family = family;
        }
    }

    private interface LeafSwapSelector {
        boolean shouldConvert(BlockPos pos);
    }

    public static int applyDormantLeaves(ServerLevel level, BlockPos rootPos) {
        LeafContext context = resolveContext(level, rootPos);
        if (context == null) {
            return 0;
        }
        BlockState dormantState = resolveDormantLeavesState(context.species);
        if (dormantState == null || dormantState.isAir()) {
            return 0;
        }
        int converted = applyLeavesState(level, rootPos, context.species, context.family, dormantState, null);
        ProjectAtmosphere.LOGGER.debug(
                "[Atmosphere] Dynamic Trees dormancy applied at {} (converted {} leaf blocks).",
                rootPos,
                converted
        );
        return converted;
    }

    public static int applyDormantLeaves(ServerLevel level, BlockPos rootPos, float progress) {
        LeafContext context = resolveContext(level, rootPos);
        if (context == null) {
            return 0;
        }
        BlockState dormantState = resolveDormantLeavesState(context.species);
        if (dormantState == null || dormantState.isAir()) {
            return 0;
        }
        return applyLeavesState(level, rootPos, context, dormantState, progress);
    }

    public static int applyActiveLeaves(ServerLevel level, BlockPos rootPos) {
        LeafContext context = resolveContext(level, rootPos);
        if (context == null) {
            return 0;
        }
        BlockState activeState = resolveActiveLeavesState(context.species);
        if (activeState == null || activeState.isAir()) {
            return 0;
        }
        int converted = applyLeavesState(level, rootPos, context.species, context.family, activeState, null);
        ProjectAtmosphere.LOGGER.debug(
                "[Atmosphere] Dynamic Trees foliage restored at {} (converted {} leaf blocks).",
                rootPos,
                converted
        );
        return converted;
    }

    public static int applyActiveLeaves(ServerLevel level, BlockPos rootPos, float progress) {
        LeafContext context = resolveContext(level, rootPos);
        if (context == null) {
            return 0;
        }
        BlockState activeState = resolveActiveLeavesState(context.species);
        if (activeState == null || activeState.isAir()) {
            return 0;
        }
        return applyLeavesState(level, rootPos, context, activeState, progress);
    }

    private static LeafContext resolveContext(ServerLevel level, BlockPos rootPos) {
        if (level == null || rootPos == null) {
            return null;
        }
        BlockState rootState = level.getBlockState(rootPos);
        Optional<SoilBlock> rootyOpt = TreeHelper.getRootyOpt(rootState);
        if (rootyOpt.isEmpty()) {
            return null;
        }
        SoilBlock rooty = rootyOpt.get();
        Species species = rooty.getSpecies(rootState, level, rootPos);
        if (species == null || species == Species.NULL_SPECIES) {
            return null;
        }
        Family family = rooty.getFamily(rootState, level, rootPos);
        if (family == null) {
            family = species.getFamily();
        }
        if (family == null) {
            return null;
        }
        return new LeafContext(species, family);
    }

    private static int applyLeavesState(ServerLevel level, BlockPos rootPos, LeafContext context, BlockState targetState, float progress) {
        float clamped = Math.max(0.0f, Math.min(progress, 1.0f));
        if (clamped <= 0.0f) {
            return 0;
        }
        if (clamped >= 1.0f) {
            return applyLeavesState(level, rootPos, context.species, context.family, targetState, null);
        }
        long seed = level.getSeed() ^ rootPos.asLong();
        LeafSwapSelector selector = pos -> leafNoise(pos, seed) <= clamped;
        return applyLeavesState(level, rootPos, context.species, context.family, targetState, selector);
    }

    private static int applyLeavesState(ServerLevel level, BlockPos rootPos, Species species, Family family, BlockState targetState, LeafSwapSelector selector) {
        LeafSwapNode node = new LeafSwapNode(level, species, family, targetState, selector);
        TreeHelper.startAnalysisFromRoot(level, rootPos, new MapSignal(node));
        return node.getConvertedLeaves();
    }

    private static final class LeafSwapNode implements NodeInspector {
        private final ServerLevel level;
        private final Species species;
        private final Family family;
        private final BlockState targetState;
        private final LeafSwapSelector selector;
        private final SimpleVoxmap leafCluster;
        private final int clusterLenX;
        private final int clusterLenY;
        private final int clusterLenZ;
        private int convertedLeaves;

        private LeafSwapNode(ServerLevel level, Species species, Family family, BlockState targetState, LeafSwapSelector selector) {
            this.level = level;
            this.species = species;
            this.family = family;
            this.targetState = targetState;
            this.selector = selector;
            LeavesProperties leavesProperties = species.getLeavesProperties();
            this.leafCluster = leavesProperties.getCellKit().getLeafCluster();
            this.clusterLenX = leafCluster.getLenX();
            this.clusterLenY = leafCluster.getLenY();
            this.clusterLenZ = leafCluster.getLenZ();
        }

        private int getConvertedLeaves() {
            return convertedLeaves;
        }

        @Override
        public boolean run(BlockState state, LevelAccessor levelAccessor, BlockPos pos, Direction fromDir) {
            BranchBlock branch = TreeHelper.getBranch(state);
            if (branch == null) {
                return false;
            }
            if (family.getBranch().map(candidate -> candidate != branch).orElse(false)) {
                return false;
            }
            int radius = branch.getRadius(state);
            if (radius <= family.getPrimaryThickness()) {
                applyTargetLeaves(pos);
            }
            return true;
        }

        @Override
        public boolean returnRun(BlockState state, LevelAccessor levelAccessor, BlockPos pos, Direction fromDir) {
            return false;
        }

        private void applyTargetLeaves(BlockPos branchPos) {
            if (level.isClientSide) {
                return;
            }
            if (targetState == null || targetState.isAir()) {
                return;
            }
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            int minX = branchPos.getX() - clusterLenX;
            int maxX = branchPos.getX() + clusterLenX;
            int minY = branchPos.getY() - clusterLenY;
            int maxY = branchPos.getY() + clusterLenY;
            int minZ = branchPos.getZ() - clusterLenZ;
            int maxZ = branchPos.getZ() + clusterLenZ;
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        cursor.set(x, y, z);
                        if (leafCluster.getVoxel(branchPos, cursor) == 0) {
                            continue;
                        }
                        if (selector != null && !selector.shouldConvert(cursor)) {
                            continue;
                        }
                        if (!level.hasChunkAt(cursor)) {
                            continue;
                        }
                        BlockState state = level.getBlockState(cursor);
                        if (!family.isCompatibleGenericLeaves(species, state, level, cursor)) {
                            continue;
                        }
                        if (state == targetState) {
                            continue;
                        }
                        level.setBlock(cursor, targetState, 3);
                        convertedLeaves++;
                    }
                }
            }
        }
    }

    private static float leafNoise(BlockPos pos, long seed) {
        long hash = pos.asLong() ^ seed;
        hash ^= (hash >>> 33);
        hash *= 0xff51afd7ed558ccdL;
        hash ^= (hash >>> 33);
        hash *= 0xc4ceb9fe1a85ec53L;
        hash ^= (hash >>> 33);
        return (float) ((hash >>> 40) & 0xFFFFFF) / (float) 0xFFFFFF;
    }

    private static BlockState resolveDormantLeavesState(Species species) {
        if (species == null) {
            return null;
        }
        LeavesProperties bare = LeavesProperties.REGISTRY
                .getOptional(ResourceLocation.fromNamespaceAndPath("dynamictrees", "bare"))
                .orElse(null);
        if (bare != null) {
            BlockState candidate = bare.getDynamicLeavesState();
            if (candidate != null && !candidate.isAir()) {
                return candidate;
            }
        }
        return null;
    }

    private static BlockState resolveActiveLeavesState(Species species) {
        if (species == null) {
            return null;
        }
        BlockState candidate = species.getLeavesProperties().getDynamicLeavesState();
        if (candidate != null && !candidate.isAir()) {
            return candidate;
        }
        return null;
    }
}
