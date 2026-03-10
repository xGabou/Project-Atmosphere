package net.Gabou.projectatmosphere.compat.rainbows;

import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

/**
 * Mirrors Serene Seasons Plus' rain state helper on the client so Rainbows can react to
 * Project Atmosphere precipitation (which bypasses vanilla rain).
 */
@OnlyIn(Dist.CLIENT)
public final class RainbowWeatherTracker {

    private static final Map<ResourceKey<Level>, TrackerState> STATES = new HashMap<>();
    private static boolean enabled;

    private RainbowWeatherTracker() {
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            STATES.clear();
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void tick(Minecraft minecraft) {
        if (!enabled) {
            return;
        }
        if (minecraft.level == null || minecraft.player == null) {
            if (minecraft.level == null) {
                STATES.clear();
            }
            return;
        }
        ClientLevel level = minecraft.level;
        ResourceKey<Level> dimension = level.dimension();
        TrackerState state = STATES.computeIfAbsent(dimension, key -> new TrackerState());
        if (!state.isServerAuthoritative()) {
            boolean raining = isRainingAt(level, minecraft.player.blockPosition());
            state.applyFallback(raining);
        }
        state.step();
    }

    private static boolean isRainingAt(ClientLevel level, BlockPos pos) {
        try {
            return CloudManager.get(level).isRainingAt(pos);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static float getRainLevel(ResourceKey<Level> dimension) {
        TrackerState state = STATES.get(dimension);
        return state != null ? state.rainVisual : 0.0f;
    }

    public static boolean consumeRainStop(ResourceKey<Level> dimension) {
        TrackerState state = STATES.get(dimension);
        return state != null && state.consumeStopFlag();
    }

    public static void applyServerUpdate(ResourceKey<Level> dimension, float rainLevel) {
        if (!enabled) {
            return;
        }
        TrackerState state = STATES.computeIfAbsent(dimension, key -> new TrackerState());
        state.setServerTarget(rainLevel);
    }

    private static final class TrackerState {
        private static final float STEP = 0.05f;

        private float target;
        private float rainVisual;
        private boolean stopFlag;
        private boolean serverAuthoritative;
        private boolean wasTargetRaining;

        void setServerTarget(float value) {
            serverAuthoritative = true;
            applyTarget(value);
        }

        void applyFallback(boolean raining) {
            if (serverAuthoritative) {
                return;
            }
            applyTarget(raining ? 1.0f : 0.0f);
        }

        private void applyTarget(float value) {
            float clamped = Mth.clamp(value, 0.0f, 1.0f);
            if (Math.abs(clamped - target) <= 0.0005f) {
                return;
            }
            boolean newRaining = clamped > 0.01f;
            if (wasTargetRaining && !newRaining) {
                stopFlag = true;
            }
            wasTargetRaining = newRaining;
            target = clamped;
        }

        void step() {
            float delta = target - rainVisual;
            if (Math.abs(delta) > STEP) {
                rainVisual += Math.copySign(STEP, delta);
            } else {
                rainVisual = target;
            }
        }

        boolean consumeStopFlag() {
            if (stopFlag) {
                stopFlag = false;
                return true;
            }
            return false;
        }

        boolean isServerAuthoritative() {
            return serverAuthoritative;
        }
    }
}
