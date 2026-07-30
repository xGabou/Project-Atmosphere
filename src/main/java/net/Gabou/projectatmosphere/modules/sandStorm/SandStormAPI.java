package net.Gabou.projectatmosphere.modules.sandStorm;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static net.Gabou.projectatmosphere.modules.wind.WindMath.getWindOffset;

public final class SandStormAPI {
    private static final String MANAGER_CLASS = "com.BreadRes.desertstormwarming.logic.SandstormManager";
    private static final String PHASE_CLASS = "com.BreadRes.desertstormwarming.logic.SandstormPhase";
    private static final String SOUNDS_CLASS = "com.BreadRes.desertstormwarming.sounds.SandstormSounds";
    private static final List<RegionInstanceKey> SCHEDULED_STORM_REGIONS = new ArrayList<>();

    private SandStormAPI() {
    }

    public enum SandstormPhase {
        PHASE_1, PHASE_2, PHASE_3, PHASE_4, PHASE_5
    }

    public static SandstormPhase getSandstormPhase() {
        Object value = invokeManager("getPhase");
        if (value instanceof Enum<?> phase) {
            try {
                return SandstormPhase.valueOf(phase.name());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return SandstormPhase.PHASE_1;
    }

    public static List<RegionInstanceKey> getScheduledStormRegions() {
        return Collections.unmodifiableList(SCHEDULED_STORM_REGIONS);
    }

    public static void startSandstorm(SandstormPhase phase, RegionInstanceKey regionKey) {
        Object externalPhase = externalPhase(phase);
        if (externalPhase != null && invokeManager("start", externalPhase) != InvocationFailure.INSTANCE
                && regionKey != null && !SCHEDULED_STORM_REGIONS.contains(regionKey)) {
            SCHEDULED_STORM_REGIONS.add(regionKey);
        }
    }

    public static void stopSandstorm(RegionInstanceKey regionKey) {
        invokeManager("stop");
        SCHEDULED_STORM_REGIONS.remove(regionKey);
    }

    public static boolean isSandstormActive() {
        return Boolean.TRUE.equals(invokeManager("isActive"));
    }

    public static void setPhase(SandstormPhase phase) {
        Object externalPhase = externalPhase(phase);
        if (externalPhase != null) {
            invokeManager("setPhase", externalPhase);
        }
    }

    public static List<SoundEvent> getSoundsForCurrentPhase() {
        Object externalPhase = externalPhase(getSandstormPhase());
        if (externalPhase == null) {
            return List.of();
        }
        try {
            Class<?> soundsClass = Class.forName(SOUNDS_CLASS);
            Method method = soundsClass.getMethod("getSoundsForPhase", externalPhase.getClass());
            Object result = method.invoke(null, externalPhase);
            if (result instanceof Iterable<?> iterable) {
                List<SoundEvent> sounds = new ArrayList<>();
                for (Object value : iterable) {
                    if (value instanceof SoundEvent sound) {
                        sounds.add(sound);
                    }
                }
                return sounds;
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            ProjectAtmosphere.LOGGER.debug("Desert Storm phase sounds are unavailable", exception);
        }
        return List.of();
    }

    public static void onSandStormManagerTick(Level level) {
    }

    public static void maybeMoveSand(Level level, BlockPos sourcePos, WindVector wind) {
        BlockPos target = sourcePos.offset(getWindOffset(wind));
        if (!level.isEmptyBlock(target)) {
            return;
        }
        BlockState sand = level.getBlockState(sourcePos);
        level.setBlock(sourcePos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(target, sand, 3);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()),
                    sourcePos.getX() + 0.5, sourcePos.getY() + 0.5, sourcePos.getZ() + 0.5,
                    10, 0.2, 0.2, 0.2, 0.05
            );
            serverLevel.sendParticles(
                    ParticleTypes.CLOUD,
                    target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 0.5,
                    5, 0.2, 0.1, 0.2, 0.01
            );
        }
    }

    public static void blowSandInRegion(
            ServerLevel level,
            RegionInstanceKey key,
            BlockPos anchor,
            WindVector wind
    ) {
        BlockPos center = anchor == null ? key.center() : anchor;
        int radiusXZ = 8;
        List<BlockPos> sandBlocks = new ArrayList<>();

        BlockPos.betweenClosedStream(
                        new BlockPos(center.getX() - radiusXZ, 0, center.getZ() - radiusXZ),
                        new BlockPos(center.getX() + radiusXZ, 0, center.getZ() + radiusXZ)
                )
                .map(pos -> new BlockPos(
                        pos.getX(),
                        Math.abs(level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ())) - 1,
                        pos.getZ()
                ))
                .filter(pos -> level.getBlockState(pos).is(Blocks.SAND) && level.isEmptyBlock(pos.above()))
                .forEach(pos -> sandBlocks.add(pos.immutable()));

        if (sandBlocks.isEmpty()) {
            return;
        }
        int countToMove = Mth.clamp(10 + level.random.nextInt(21), 1, sandBlocks.size());
        Collections.shuffle(sandBlocks);
        for (int i = 0; i < countToMove; i++) {
            maybeMoveSand(level, sandBlocks.get(i), wind);
        }
    }

    private static Object externalPhase(SandstormPhase phase) {
        try {
            Class<?> phaseClass = Class.forName(PHASE_CLASS);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object value = Enum.valueOf((Class<? extends Enum>) phaseClass.asSubclass(Enum.class), phase.name());
            return value;
        } catch (ReflectiveOperationException | IllegalArgumentException | LinkageError exception) {
            return null;
        }
    }

    private static Object invokeManager(String methodName, Object... arguments) {
        try {
            Class<?> managerClass = Class.forName(MANAGER_CLASS);
            for (Method method : managerClass.getMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == arguments.length) {
                    return method.invoke(null, arguments);
                }
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            ProjectAtmosphere.LOGGER.debug("Desert Storm method {} is unavailable", methodName, exception);
        }
        return InvocationFailure.INSTANCE;
    }

    private enum InvocationFailure {
        INSTANCE
    }
}
