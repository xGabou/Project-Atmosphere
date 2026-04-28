package net.Gabou.projectatmosphere.modules.hurricane;

import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

final class HurricaneDestructionManager {
    private static final int MAX_SURFACE_SEARCH_DEPTH = 5;

    private HurricaneDestructionManager() {
    }

    static void apply(HurricaneInstance hurricane, ServerLevel level, long gameTime) {
        if (!AtmoCommonConfig.ENABLE_HURRICANE_DESTRUCTION.get()) {
            return;
        }
        if (!hurricane.markDestructionTick(gameTime)) {
            return;
        }

        float configuredStrength = (float) AtmoCommonConfig.HURRICANE_DESTRUCTION_STRENGTH.get().doubleValue();
        if (configuredStrength <= 0.0F || hurricane.getDestructiveStrength() <= 0.18F) {
            return;
        }

        float aggression = Mth.clamp(hurricane.getDestructiveStrength() * configuredStrength, 0.0F, 3.0F);
        if (aggression <= 0.05F) {
            return;
        }

        int maxBreaks = Mth.clamp(1 + Mth.floor(aggression * 1.25F), 1, 4);
        int samples = Mth.clamp(3 + Mth.floor(aggression * 4.0F) + hurricane.category.ordinal(), 3, 20);
        float minRadius = hurricane.getVisualEyeRadius() * 1.20F;
        float maxRadius = Mth.clamp(
                hurricane.getCoreRadius() * 0.92F,
                minRadius + 8.0F,
                144.0F + aggression * 40.0F + hurricane.category.ordinal() * 10.0F
        );

        int broken = 0;
        RandomSource random = level.random;
        for (int i = 0; i < samples && broken < maxBreaks; i++) {
            BlockPos samplePos = projectatmosphere$sampleSurface(level, hurricane, random, minRadius, maxRadius);
            if (samplePos == null) {
                continue;
            }

            BlockPos candidate = projectatmosphere$findBreakCandidate(level, samplePos);
            if (candidate == null) {
                continue;
            }

            float radialWeight = projectatmosphere$radialWeight(hurricane, candidate, minRadius, maxRadius);
            BlockState state = level.getBlockState(candidate);
            if (!HurricaneBlockBreakRules.canBreak(level, candidate, state)) {
                continue;
            }

            if (HurricaneBlockBreakRules.isFragile(state)) {
                if (!HurricaneBlockBreakRules.shouldBreakFragile(random, aggression, radialWeight)) {
                    continue;
                }
                if (level.destroyBlock(candidate, AtmoCommonConfig.HURRICANE_DROP_BROKEN_BLOCKS.get())) {
                    broken++;
                }
                continue;
            }

            if (!AtmoCommonConfig.HURRICANE_DAMAGE_TREES.get()) {
                continue;
            }

            broken += projectatmosphere$damageTree(level, candidate, random, aggression, radialWeight, maxBreaks - broken);
        }
    }

    private static BlockPos projectatmosphere$sampleSurface(ServerLevel level,
                                                            HurricaneInstance hurricane,
                                                            RandomSource random,
                                                            float minRadius,
                                                            float maxRadius) {
        float angle = random.nextFloat() * Mth.TWO_PI;
        float sampleRadius = Mth.sqrt(Mth.lerp(random.nextFloat(), minRadius * minRadius, maxRadius * maxRadius));
        int x = Mth.floor(hurricane.position.x + Math.cos(angle) * sampleRadius);
        int z = Mth.floor(hurricane.position.z + Math.sin(angle) * sampleRadius);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        if (y < level.getMinBuildHeight()) {
            return null;
        }
        return new BlockPos(x, y, z);
    }

    private static BlockPos projectatmosphere$findBreakCandidate(ServerLevel level, BlockPos origin) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int[] offsets = {0, 1, -1, -2, -3, -4, -5};
        for (int offset : offsets) {
            if (Math.abs(offset) > MAX_SURFACE_SEARCH_DEPTH) {
                continue;
            }
            cursor.set(origin.getX(), origin.getY() + offset, origin.getZ());
            if (!level.isLoaded(cursor)) {
                continue;
            }
            BlockState state = level.getBlockState(cursor);
            if (HurricaneBlockBreakRules.isFragile(state) || HurricaneBlockBreakRules.isTreeDamageCandidate(state)) {
                return cursor.immutable();
            }
        }
        return null;
    }

    private static float projectatmosphere$radialWeight(HurricaneInstance hurricane, BlockPos pos, float minRadius, float maxRadius) {
        double dx = pos.getX() + 0.5D - hurricane.position.x;
        double dz = pos.getZ() + 0.5D - hurricane.position.z;
        float distance = (float) Math.sqrt(dx * dx + dz * dz);
        float span = Math.max(1.0F, maxRadius - minRadius);
        return 1.0F - Mth.clamp((distance - minRadius) / span, 0.0F, 1.0F);
    }

    private static int projectatmosphere$damageTree(ServerLevel level,
                                                    BlockPos origin,
                                                    RandomSource random,
                                                    float aggression,
                                                    float radialWeight,
                                                    int remainingBudget) {
        if (remainingBudget <= 0) {
            return 0;
        }

        int destroyed = 0;
        destroyed += projectatmosphere$tryBreakTreeBlock(level, origin, random, aggression, radialWeight);
        if (destroyed >= remainingBudget) {
            return destroyed;
        }

        int localBudget = Math.min(remainingBudget, 1 + Mth.floor(aggression * 0.85F));
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int attempts = 6 + localBudget * 4;
        for (int i = 0; i < attempts && destroyed < localBudget; i++) {
            cursor.set(
                    origin.getX() + random.nextInt(5) - 2,
                    origin.getY() + random.nextInt(5) - 1,
                    origin.getZ() + random.nextInt(5) - 2
            );
            destroyed += projectatmosphere$tryBreakTreeBlock(level, cursor, random, aggression, radialWeight);
        }
        return destroyed;
    }

    private static int projectatmosphere$tryBreakTreeBlock(ServerLevel level,
                                                           BlockPos pos,
                                                           RandomSource random,
                                                           float aggression,
                                                           float radialWeight) {
        if (!level.isLoaded(pos)) {
            return 0;
        }

        BlockState state = level.getBlockState(pos);
        if (!HurricaneBlockBreakRules.isTreeDamageCandidate(state) || !HurricaneBlockBreakRules.canBreak(level, pos, state)) {
            return 0;
        }
        if (!HurricaneBlockBreakRules.shouldBreakTree(random, state, aggression, radialWeight)) {
            return 0;
        }
        return level.destroyBlock(pos, AtmoCommonConfig.HURRICANE_DROP_BROKEN_BLOCKS.get()) ? 1 : 0;
    }
}
