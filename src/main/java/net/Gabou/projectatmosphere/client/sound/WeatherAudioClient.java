package net.Gabou.projectatmosphere.client.sound;

import net.Gabou.projectatmosphere.clouds.CloudWeatherSample;
import net.Gabou.projectatmosphere.clouds.WeatherCloudQueries;
import net.Gabou.projectatmosphere.clouds.client.ClientCloudRegionDataCache;
import net.Gabou.projectatmosphere.clouds.transport.CloudRegionRenderData;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeRegistry;
import net.Gabou.projectatmosphere.compat.coolrain.CoolRainCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class WeatherAudioClient {
    private static final int THUNDER_MIN_DELAY_TICKS = 120;
    private static final int THUNDER_RANDOM_DELAY_TICKS = 260;

    private static RainTier activeTier = RainTier.NONE;
    private static RainLoop activeLoop;
    private static int thunderCooldownTicks;

    private WeatherAudioClient() {
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null || minecraft.isPaused()) {
            stopAll();
            return;
        }

        BlockPos pos = minecraft.player.blockPosition();
        CloudWeatherSample sample = WeatherCloudQueries.sampleAt(minecraft.level, pos, true);
        updateRainLoop(minecraft, sample.rainStrength());
        updateThunder(minecraft, sample);
    }

    public static void stopAll() {
        stopRainLoop();
        activeTier = RainTier.NONE;
        thunderCooldownTicks = 0;
    }

    private static void updateRainLoop(Minecraft minecraft, float rainStrength) {
        if (CoolRainCompat.isLoaded()) {
            stopRainLoop();
            return;
        }

        RainTier targetTier = RainTier.fromRainStrength(rainStrength);
        if (targetTier != activeTier) {
            stopRainLoop();
            activeTier = targetTier;
            activeLoop = targetTier == RainTier.NONE ? null : new RainLoop(targetTier);
            if (activeLoop != null) {
                minecraft.getSoundManager().play(activeLoop);
            }
        }

        if (activeLoop != null) {
            activeLoop.setTargetVolume(targetTier.volumeFor(rainStrength));
        }
    }

    private static void updateThunder(Minecraft minecraft, CloudWeatherSample sample) {
        if (thunderCooldownTicks > 0) {
            thunderCooldownTicks--;
            return;
        }
        if (!sample.hasThunder()) {
            return;
        }

        ThunderCandidate candidate = nearestThunderCloud(minecraft.player.position());
        if (candidate == null || candidate.strength() <= 0.04F) {
            return;
        }

        SoundEvent sound = soundForThunderDistance(candidate.distance());
        float volume = Mth.clamp(0.25F + sample.thunderStrength() * 0.85F + candidate.strength() * 0.35F, 0.2F, 1.4F);
        float pitch = 0.92F + minecraft.level.random.nextFloat() * 0.16F;
        minecraft.level.playLocalSound(candidate.position().x(), candidate.position().y(), candidate.position().z(), sound, SoundSource.WEATHER, volume, pitch, false);
        thunderCooldownTicks = THUNDER_MIN_DELAY_TICKS + minecraft.level.random.nextInt(THUNDER_RANDOM_DELAY_TICKS);
    }

    private static SoundEvent soundForThunderDistance(float distance) {
        if (distance < 96.0F) {
            return net.Gabou.projectatmosphere.registry.ModSounds.THUNDER_RUMBLING_CLOSE.get();
        }
        if (distance < 256.0F) {
            return net.Gabou.projectatmosphere.registry.ModSounds.THUNDER_HIT_SEMI_DISTANT.get();
        }
        return distance < 512.0F
                ? net.Gabou.projectatmosphere.registry.ModSounds.THUNDER_HIT_DISTANT.get()
                : net.Gabou.projectatmosphere.registry.ModSounds.THUNDER_RUMBLING_DISTANT.get();
    }

    private static ThunderCandidate nearestThunderCloud(Vec3 playerPosition) {
        ThunderCandidate best = null;
        for (CloudRegionRenderData region : ClientCloudRegionDataCache.getCurrentRegions()) {
            if (region == null || !region.isActive() || !CloudTypeRegistry.isThunderCloud(region.getCloudTypeId())) {
                continue;
            }

            float distance = (float) playerPosition.distanceTo(region.getCenter());
            float density = Mth.clamp(region.getDensity() * region.getDensityMultiplier(), 0.0F, 1.0F);
            float coverage = Mth.clamp(region.getCoverage() * region.getCoverageMultiplier(), 0.0F, 1.0F);
            float strength = Mth.clamp(density * 0.55F + coverage * 0.35F + region.getLightningInfluence() * 0.35F, 0.0F, 1.0F);
            if (best == null || distance < best.distance()) {
                best = new ThunderCandidate(region.getCenter(), distance, strength);
            }
        }
        return best;
    }

    private static void stopRainLoop() {
        if (activeLoop != null) {
            activeLoop.stopSound();
            activeLoop = null;
        }
        activeTier = RainTier.NONE;
    }

    private enum RainTier {
        NONE(null),
        LIGHT(SoundEvents.WEATHER_RAIN),
        MEDIUM(SoundEvents.WEATHER_RAIN),
        HEAVY(SoundEvents.WEATHER_RAIN);

        private final SoundEvent sound;

        RainTier(SoundEvent sound) {
            this.sound = sound;
        }

        static RainTier fromRainStrength(float rainStrength) {
            float rain = Mth.clamp(rainStrength, 0.0F, 1.0F);
            if (rain < 0.08F) {
                return NONE;
            }
            if (rain < 0.34F) {
                return LIGHT;
            }
            if (rain < 0.68F) {
                return MEDIUM;
            }
            return HEAVY;
        }

        float volumeFor(float rainStrength) {
            return switch (this) {
                case NONE -> 0.0F;
                case LIGHT -> Mth.clamp(rainStrength * 1.65F, 0.10F, 0.46F);
                case MEDIUM -> Mth.clamp(0.28F + rainStrength * 0.58F, 0.30F, 0.74F);
                case HEAVY -> Mth.clamp(0.56F + rainStrength * 0.58F, 0.58F, 1.0F);
            };
        }

        SoundEvent soundEvent() {
            return sound;
        }
    }

    private static final class RainLoop extends AbstractTickableSoundInstance {
        private float targetVolume;

        private RainLoop(RainTier tier) {
            super(tier.soundEvent(), SoundSource.WEATHER, Minecraft.getInstance().level.getRandom());
            this.looping = true;
            this.relative = true;
            this.volume = 0.0F;
            this.targetVolume = 0.0F;
            this.pitch = 1.0F;
        }

        @Override
        public void tick() {
            if (Minecraft.getInstance().level == null) {
                stop();
                return;
            }

            this.volume = Mth.lerp(0.08F, this.volume, targetVolume);
        }

        private void setTargetVolume(float targetVolume) {
            this.targetVolume = Mth.clamp(targetVolume, 0.0F, 1.0F);
        }

        private void stopSound() {
            stop();
        }
    }

    private record ThunderCandidate(Vec3 position, float distance, float strength) {
    }
}
