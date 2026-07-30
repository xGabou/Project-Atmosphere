package net.Gabou.projectatmosphere.clouds.client;

import net.Gabou.projectatmosphere.clouds.CloudWeatherSample;
import net.Gabou.projectatmosphere.clouds.WeatherCloudQueries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

@OnlyIn(Dist.CLIENT)
public final class ClientLocalizedWeatherState {
    private static final float RAIN_TRACKING = 0.18F;
    private static final float THUNDER_TRACKING = 0.16F;
    private static final float ACTIVE_THRESHOLD = 0.02F;

    private static long lastGameTime = Long.MIN_VALUE;
    private static float previousRainLevel;
    private static float rainLevel;
    private static float targetRainLevel;
    private static float previousThunderLevel;
    private static float thunderLevel;
    private static float targetThunderLevel;
    private static BlockPos lastSamplePos = BlockPos.ZERO;
    private static CloudWeatherSample lastSample = CloudWeatherSample.NONE;

    private ClientLocalizedWeatherState() {
    }

    public static boolean isRaining(ClientLevel level) {
        update(level);
        return rainLevel > ACTIVE_THRESHOLD;
    }

    public static boolean isThundering(ClientLevel level) {
        update(level);
        return thunderLevel > ACTIVE_THRESHOLD;
    }

    public static float getRainLevel(ClientLevel level, float partialTick) {
        update(level);
        return Mth.clamp(Mth.lerp(partialTick, previousRainLevel, rainLevel), 0.0F, 1.0F);
    }

    public static float getThunderLevel(ClientLevel level, float partialTick) {
        update(level);
        return Mth.clamp(Mth.lerp(partialTick, previousThunderLevel, thunderLevel), 0.0F, 1.0F);
    }

    public static @NotNull Diagnostics getDiagnostics() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level != null) {
            update(level);
        }
        return new Diagnostics(lastSamplePos, lastSample, targetRainLevel, rainLevel, targetThunderLevel, thunderLevel);
    }

    private static void update(ClientLevel level) {
        if (level == null) {
            clear();
            return;
        }

        long gameTime = level.getGameTime();
        if (gameTime == lastGameTime) {
            return;
        }
        lastGameTime = gameTime;

        previousRainLevel = rainLevel;
        previousThunderLevel = thunderLevel;
        lastSamplePos = resolveSamplePos();
        lastSample = WeatherCloudQueries.sampleAt(level, lastSamplePos, true);
        targetRainLevel = lastSample.rainStrength();
        targetThunderLevel = lastSample.thunderStrength();
        rainLevel = Mth.lerp(RAIN_TRACKING, rainLevel, targetRainLevel);
        thunderLevel = Mth.lerp(THUNDER_TRACKING, thunderLevel, targetThunderLevel);
    }

    private static BlockPos resolveSamplePos() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameRenderer != null) {
            Camera camera = minecraft.gameRenderer.getMainCamera();
            Vec3 position = camera.getPosition();
            return BlockPos.containing(position);
        }
        if (minecraft.player != null) {
            return BlockPos.containing(minecraft.player.getEyePosition());
        }
        return BlockPos.ZERO;
    }

    private static void clear() {
        lastGameTime = Long.MIN_VALUE;
        previousRainLevel = 0.0F;
        rainLevel = 0.0F;
        targetRainLevel = 0.0F;
        previousThunderLevel = 0.0F;
        thunderLevel = 0.0F;
        targetThunderLevel = 0.0F;
        lastSamplePos = BlockPos.ZERO;
        lastSample = CloudWeatherSample.NONE;
    }

    public record Diagnostics(
            BlockPos samplePos,
            CloudWeatherSample sample,
            float targetRainLevel,
            float smoothedRainLevel,
            float targetThunderLevel,
            float smoothedThunderLevel
    ) {
    }
}
