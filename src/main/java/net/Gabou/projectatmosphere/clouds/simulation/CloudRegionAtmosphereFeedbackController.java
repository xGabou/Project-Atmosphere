package net.Gabou.projectatmosphere.clouds.simulation;

import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeDefinition;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeRegistry;
import net.Gabou.projectatmosphere.clouds.type.CloudVisualProfile;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Projects PA-native cloud regions back into the live atmosphere layer.
 * This is backend simulation feedback only; it does not render precipitation
 * or change cloud morphology.
 */
final class CloudRegionAtmosphereFeedbackController {
    private static final float COVER_TRACKING = 0.10F;
    private static final float WATER_TRACKING = 0.08F;
    private static final float UNSUPPORTED_COVER_DECAY = 0.006F;
    private static final float UNSUPPORTED_WATER_DECAY = 0.002F;

    boolean tick(ServerLevel level, Collection<CloudRegionState> activeRegions) {
        if (level == null) {
            return false;
        }
        boolean changed = false;
        Map<RegionInstanceKey, FeedbackTarget> targets = new HashMap<>();

        for (CloudRegionState region : activeRegions) {
            if (region == null || !region.isActive() || region.getCenter() == null) {
                continue;
            }
            RegionInstanceKey key = RegionInstanceKey.from(BlockPos.containing(region.getCenter()));
            RegionAtmosphereState state = AtmosphericStateRegistry.getState(key);
            if (state == null) {
                continue;
            }
            CloudTypeDefinition definition = CloudTypeRegistry.getOrDefault(region.getCloudTypeId());
            CloudVisualProfile visual = definition.getVisualProfile();
            float cloudCoverTarget = Mth.clamp(
                    region.getCoverage() * visual.getCoverageMultiplier() * 0.28F
                            + region.getDensity() * 0.12F,
                    0.0F,
                    0.72F
            );
            float cloudWaterTarget = Mth.clamp(
                    cloudCoverTarget * 0.35F
                            + visual.getPrecipitationCoreStrength() * 0.18F,
                    0.0F,
                    1.2F
            );
            targets.computeIfAbsent(key, ignored -> new FeedbackTarget()).include(cloudCoverTarget, cloudWaterTarget);
        }

        for (Map.Entry<RegionInstanceKey, FeedbackTarget> entry : targets.entrySet()) {
            RegionAtmosphereState state = AtmosphericStateRegistry.getState(entry.getKey());
            if (state == null) {
                continue;
            }
            FeedbackTarget target = entry.getValue();
            float nextCover = Mth.lerp(COVER_TRACKING, state.getCloudCover(), target.cloudCover());
            nextCover = Math.max(state.getCycloneCloudFloor(), nextCover);
            if (Math.abs(nextCover - state.getCloudCover()) > 0.0001F) {
                state.setCloudCover(nextCover);
                changed = true;
            }
            if (target.cloudWater() > state.getCloudWater()) {
                state.setCloudWater(Mth.lerp(WATER_TRACKING, state.getCloudWater(), target.cloudWater()));
                changed = true;
            } else if (state.getRainIntensity() < 0.02F && state.getCloudWater() > target.cloudWater()) {
                state.setCloudWater(Math.max(target.cloudWater(), state.getCloudWater() - UNSUPPORTED_WATER_DECAY));
                changed = true;
            }
        }

        Set<RegionInstanceKey> supported = new HashSet<>(targets.keySet());
        for (RegionInstanceKey key : AtmosphericStateRegistry.getActiveStates()) {
            if (supported.contains(key)) {
                continue;
            }
            RegionAtmosphereState state = AtmosphericStateRegistry.getState(key);
            if (state == null) {
                continue;
            }
            float nextCover = Math.max(state.getCycloneCloudFloor(), state.getCloudCover() - UNSUPPORTED_COVER_DECAY);
            if (nextCover != state.getCloudCover()) {
                state.setCloudCover(nextCover);
                changed = true;
            }
            if (state.getRainIntensity() < 0.02F && state.getCloudWater() > 0.0F) {
                state.setCloudWater(Math.max(0.0F, state.getCloudWater() - UNSUPPORTED_WATER_DECAY));
                changed = true;
            }
        }

        return changed;
    }

    private static final class FeedbackTarget {
        private float cloudCover;
        private float cloudWater;

        void include(float cloudCoverTarget, float cloudWaterTarget) {
            cloudCover = Math.max(cloudCover, cloudCoverTarget);
            cloudWater = Math.max(cloudWater, cloudWaterTarget);
        }

        float cloudCover() {
            return cloudCover;
        }

        float cloudWater() {
            return cloudWater;
        }
    }
}
