package net.Gabou.projectatmosphere.client.sound;

import net.Gabou.projectatmosphere.clouds.CloudWeatherSample;
import net.Gabou.projectatmosphere.clouds.WeatherCloudQueries;
import net.Gabou.projectatmosphere.clouds.client.ClientCloudRegionDataCache;
import net.Gabou.projectatmosphere.clouds.transport.CloudRegionRenderData;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeRegistry;
import net.Gabou.projectatmosphere.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.RegistryObject;

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
        if (activeLoop != null) {
            activeLoop.stopSound();
            activeLoop = null;
        }
        activeTier = RainTier.NONE;
        thunderCooldownTicks = 0;
    }

    private static void updateRainLoop(Minecraft minecraft, float rainStrength) {
        RainTier targetTier = RainTier.fromRainStrength(rainStrength);
        if (targetTier != activeTier) {
            if (activeLoop != null) {
                activeLoop.fadeOut();
            }
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

        SoundEvent sound = soundForThunderDistance(candidate.distance()).get();
        float volume = Mth.clamp(0.25F + sample.thunderStrength() * 0.85F + candidate.strength() * 0.35F, 0.2F, 1.4F);
        float pitch = 0.92F + minecraft.level.random.nextFloat() * 0.16F;
        minecraft.level.playLocalSound(candidate.position().x(), candidate.position().y(), candidate.position().z(), sound, SoundSource.WEATHER, volume, pitch, false);
        thunderCooldownTicks = THUNDER_MIN_DELAY_TICKS + minecraft.level.random.nextInt(THUNDER_RANDOM_DELAY_TICKS);
    }

    private static RegistryObject<SoundEvent> soundForThunderDistance(float distance) {
        if (distance < 96.0F) {
            return ModSounds.THUNDER_RUMBLING_CLOSE;
        }
        if (distance < 256.0F) {
            return ModSounds.THUNDER_HIT_SEMI_DISTANT;
        }
        return distance < 512.0F ? ModSounds.THUNDER_HIT_DISTANT : ModSounds.THUNDER_RUMBLING_DISTANT;
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

    private enum RainTier {
        NONE(null),
        LIGHT(ModSounds.RAIN_LIGHT),
        MEDIUM(ModSounds.RAIN_MEDIUM),
        HEAVY(ModSounds.RAIN_HEAVY);

        private final RegistryObject<SoundEvent> sound;

        RainTier(RegistryObject<SoundEvent> sound) {
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
            return sound.get();
        }
    }

    private static final class RainLoop extends AbstractTickableSoundInstance {
        private float targetVolume;
        private boolean fadingOut;

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
            if (Minecraft.getInstance().level == null || fadingOut && volume <= 0.01F) {
                stop();
                return;
            }

            float target = fadingOut ? 0.0F : targetVolume;
            this.volume = Mth.lerp(0.08F, this.volume, target);
        }

        private void setTargetVolume(float targetVolume) {
            this.targetVolume = Mth.clamp(targetVolume, 0.0F, 1.0F);
        }

        private void fadeOut() {
            this.fadingOut = true;
        }

        private void stopSound() {
            stop();
        }
    }

    private record ThunderCandidate(Vec3 position, float distance, float strength) {
    }
}
