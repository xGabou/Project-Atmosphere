package net.Gabou.projectatmosphere.modules.sandStorm;

import com.BreadRes.desertstormwarming.logic.SandstormManager;
import com.BreadRes.desertstormwarming.logic.SandstormPhase;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.SandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static net.Gabou.projectatmosphere.modules.wind.WindMath.getWindOffset;

public class SandStormAPI {

    private SandStormAPI() {
        // Prevent instantiation
    }
    private static final List<BiomeInstanceKey> scheduledStormBiome = new ArrayList<>();

    public static List<BiomeInstanceKey> getScheduledStormBiome() {
        return scheduledStormBiome;
    }

    /**
     * Starts a sandstorm at the given phase using the Desert Storm Warming mod.
     * No internal logic or condition checks.
     *
     * @param phase The sandstorm phase to begin with.
     */
    public static void startSandstorm(SandstormPhase phase, BiomeInstanceKey biomeInstanceKey) {
        SandstormManager.start(phase);

        scheduledStormBiome.add(biomeInstanceKey);
    }

    /**
     * Stops the currently active sandstorm, if any.
     */
    public static void stopSandstorm(BiomeInstanceKey biomeInstanceKey) {
        SandstormManager.stop();
        scheduledStormBiome.remove(biomeInstanceKey);
    }

    /**
     * Checks if a sandstorm is currently active.
     */
    public static boolean isSandstormActive() {
        return SandstormManager.isActive();
    }

    /**
     * Sets the current sandstorm phase.
     */
    public static void setPhase(SandstormPhase phase) {
        SandstormManager.setPhase(phase);
    }

    public static void onSandStormManagerTick(Level level) {

    }

    public static void maybeMoveSand(Level level, BlockPos sourcePos, WindVector wind) {
        BlockPos offset = getWindOffset(wind);
        BlockPos target = sourcePos.offset(offset);


        // Check if we can move sand to the target
        if (level.isEmptyBlock(target)) {
            BlockState sand = level.getBlockState(sourcePos);

            // Remove sand from source
            level.setBlock(sourcePos, Blocks.AIR.defaultBlockState(), 3);

            // Place sand at target
            level.setBlock(target, sand, 3);

            // Add visual particles at source and target
            if (level instanceof ServerLevel serverLevel) {
                // Source burst
                serverLevel.sendParticles(
                        new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()),
                        sourcePos.getX() + 0.5, sourcePos.getY() + 0.5, sourcePos.getZ() + 0.5,
                        10, 0.2, 0.2, 0.2, 0.05
                );


                // Landing puff
                serverLevel.sendParticles(ParticleTypes.CLOUD,
                        target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 0.5,
                        5, 0.2, 0.1, 0.2, 0.01);
            }
        }
    }
    public static void blowSandInBiome(ServerLevel level, BiomeInstanceKey key, WindVector wind) {
        BlockPos center = key.samplePos();

        // Define a sampling area (e.g., 16x16x4 around the center)
        int radiusXZ = 8;
        int height = 4;

        List<BlockPos> sandBlocks = new ArrayList<>();

        BlockPos.betweenClosedStream(
                        new BlockPos(center.getX() - radiusXZ, 0, center.getZ() - radiusXZ),
                        new BlockPos(center.getX() + radiusXZ, 0, center.getZ() + radiusXZ)
                )
                .map(pos -> {
                    int surfaceY = Math.abs(level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()));
                     return new BlockPos(pos.getX(), surfaceY-1, pos.getZ());
                })
                .filter(pos -> {
                    BlockState state = level.getBlockState(pos);
                    return state.is(Blocks.SAND) && level.isEmptyBlock(pos.above());
                })
                .forEach(pos -> sandBlocks.add(pos.immutable()));



        if (sandBlocks.isEmpty()) return;

        // Pick 10 to 30 random sand blocks to move
        int countToMove = Mth.clamp(10 + level.random.nextInt(21), 1, sandBlocks.size()); // 10–30
        Collections.shuffle(sandBlocks, (Random) level.random);

        for (int i = 0; i < countToMove; i++) {
            maybeMoveSand(level, sandBlocks.get(i), wind);
        }
    }





}
