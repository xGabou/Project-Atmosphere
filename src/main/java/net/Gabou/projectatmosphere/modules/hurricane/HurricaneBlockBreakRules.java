package net.Gabou.projectatmosphere.modules.hurricane;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.modules.weather.StormShieldManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.common.Tags;

final class HurricaneBlockBreakRules {
    static final net.minecraft.tags.TagKey<Block> HURRICANE_FRAGILE = BlockTags.create(
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "hurricane_fragile")
    );
    static final net.minecraft.tags.TagKey<Block> HURRICANE_TREE_DAMAGE = BlockTags.create(
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "hurricane_tree_damage")
    );
    static final net.minecraft.tags.TagKey<Block> HURRICANE_NEVER_BREAK = BlockTags.create(
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "hurricane_never_break")
    );

    private HurricaneBlockBreakRules() {
    }

    static boolean canBreak(ServerLevel level, BlockPos pos, BlockState state) {
        if (!level.isLoaded(pos) || state.isAir() || StormShieldManager.isProtected(level, pos)) {
            return false;
        }
        if (!state.getFluidState().isEmpty() || state.hasBlockEntity()) {
            return false;
        }
        if (state.is(HURRICANE_NEVER_BREAK) || state.is(Tags.Blocks.ORES)) {
            return false;
        }
        if (state.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }
        PushReaction pushReaction = state.getPistonPushReaction();
        if (pushReaction == PushReaction.BLOCK || pushReaction == PushReaction.IGNORE) {
            return false;
        }
        return isFragile(state) || isTreeDamageCandidate(state);
    }

    static boolean isFragile(BlockState state) {
        return state.is(HURRICANE_FRAGILE);
    }

    static boolean isTreeDamageCandidate(BlockState state) {
        return state.is(HURRICANE_TREE_DAMAGE);
    }

    static boolean shouldBreakFragile(RandomSource random, float aggression, float radialWeight) {
        float normalizedAggression = Mth.clamp(aggression / 1.5F, 0.0F, 1.0F);
        float chance = 0.12F + normalizedAggression * 0.24F + radialWeight * 0.14F;
        return random.nextFloat() < Mth.clamp(chance, 0.0F, 0.78F);
    }

    static boolean shouldBreakTree(RandomSource random, BlockState state, float aggression, float radialWeight) {
        float normalizedAggression = Mth.clamp(aggression / 1.5F, 0.0F, 1.0F);
        float chance;
        if (state.is(BlockTags.LOGS)) {
            chance = 0.012F + normalizedAggression * 0.045F + radialWeight * 0.035F;
        } else {
            chance = 0.10F + normalizedAggression * 0.20F + radialWeight * 0.12F;
        }
        return random.nextFloat() < Mth.clamp(chance, 0.0F, 0.35F);
    }
}
