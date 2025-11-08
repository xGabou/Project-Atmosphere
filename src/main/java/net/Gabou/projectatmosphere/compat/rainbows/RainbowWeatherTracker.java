package net.Gabou.projectatmosphere.compat.rainbows;

import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

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
        boolean raining = isRainingAt(level, minecraft.player.blockPosition());
        state.update(raining);
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

    private static final class TrackerState {
        private boolean wasRaining;
        private float rainVisual;
        private boolean stopFlag;

        void update(boolean raining) {
            if (wasRaining && !raining) {
                stopFlag = true;
            }
            wasRaining = raining;
            float target = raining ? 1.0f : 0.0f;
            float delta = target - rainVisual;
            float step = 0.05f;
            if (Math.abs(delta) > step) {
                rainVisual += Math.copySign(step, delta);
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
    }
}
