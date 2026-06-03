package net.Gabou.projectatmosphere.modules.hurricane;

import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.Gabou.projectatmosphere.client.hurricane.cache.ClientHurricaneStateCache;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
public final class HurricaneSemantics {
    private static final float MIN_COVERAGE = 0.02F;
    private static final float OUTER_RAIN_FLOOR = 0.58F;
    private static final Field CLOUD_MANAGER_LEVEL_FIELD = projectatmosphere$initCloudManagerLevelField();

    private HurricaneSemantics() {
    }

    public static HurricaneSemanticSample sampleBest(Level level, double worldX, double worldZ) {
        HurricaneSemanticSample best = HurricaneSemanticSample.none();
        if (level.isClientSide) {
            for (HurricaneRenderSnapshot hurricane : ClientHurricaneStateCache.getSemanticSnapshots()) {
                HurricaneSemanticSample sample = sample(hurricane, worldX, worldZ);
                if (sample.inEye() && !best.isPresent()) {
                    best = sample;
                    continue;
                }
                if (sample.coverage() > best.coverage()) {
                    best = sample;
                }
            }
            return best;
        }

        for (HurricaneInstance hurricane : HurricaneManager.getActiveHurricanes()) {
            HurricaneSemanticSample sample = sample(hurricane, worldX, worldZ);
            if (sample.inEye() && !best.isPresent()) {
                best = sample;
                continue;
            }
            if (sample.coverage() > best.coverage()) {
                best = sample;
            }
        }
        return best;
    }

    public static boolean intersectsReservation(Level level, double worldX, double worldZ, double extraRadius) {
        if (level.isClientSide) {
            for (HurricaneRenderSnapshot hurricane : ClientHurricaneStateCache.getSemanticSnapshots()) {
                if (intersectsReservation(hurricane, worldX, worldZ, extraRadius)) {
                    return true;
                }
            }
            return false;
        }

        for (HurricaneInstance hurricane : HurricaneManager.getActiveHurricanes()) {
            if (intersectsReservation(hurricane, worldX, worldZ, extraRadius)) {
                return true;
            }
        }
        return false;
    }

    public static boolean intersectsReservation(HurricaneInstance hurricane, double worldX, double worldZ, double extraRadius) {
        return intersectsReservation(hurricane.position.x, hurricane.position.z, hurricane.getStormExtentRadius(), worldX, worldZ, extraRadius);
    }

    public static boolean intersectsReservation(HurricaneRenderSnapshot hurricane, double worldX, double worldZ, double extraRadius) {
        return intersectsReservation(hurricane.centerX(), hurricane.centerZ(), hurricane.stormExtentRadius(), worldX, worldZ, extraRadius);
    }

    public static @Nullable CloudRegion getReservationRegionAt(Level level, double worldX, double worldZ) {
        if (!level.isClientSide) {
            return HurricaneManager.getReservationRegionAt(worldX, worldZ);
        }
        for (HurricaneRenderSnapshot snapshot : ClientHurricaneStateCache.getSemanticSnapshots()) {
            if (intersectsReservation(snapshot, worldX, worldZ, 0.0D)) {
                return ClientHurricaneStateCache.getReservationRegion(snapshot);
            }
        }
        return null;
    }

    public static CloudRegion createReservationRegion(HurricaneInstance hurricane) {
        CloudRegion region = new CloudRegion(
                hurricane.createRenderSnapshot().cloudTypeId(),
                new Vec2(0.0F, 0.0F),
                0.0F,
                0.0F,
                (float)(hurricane.position.x / (double)SimpleCloudsConstants.CLOUD_SCALE),
                (float)(hurricane.position.z / (double)SimpleCloudsConstants.CLOUD_SCALE),
                (hurricane.getStormExtentRadius() + SimpleCloudsConstants.MIN_SPAWN_DIST_BETWEEN_REGIONS) / (float)SimpleCloudsConstants.CLOUD_SCALE,
                0.0F,
                1.0F,
                Integer.MAX_VALUE,
                0,
                Integer.MIN_VALUE
        );
        updateReservationRegion(region, hurricane);
        return region;
    }

    public static CloudRegion createReservationRegion(HurricaneRenderSnapshot hurricane) {
        CloudRegion region = new CloudRegion(
                hurricane.cloudTypeId(),
                new Vec2(0.0F, 0.0F),
                0.0F,
                0.0F,
                (float)(hurricane.centerX() / (double)SimpleCloudsConstants.CLOUD_SCALE),
                (float)(hurricane.centerZ() / (double)SimpleCloudsConstants.CLOUD_SCALE),
                (hurricane.stormExtentRadius() + SimpleCloudsConstants.MIN_SPAWN_DIST_BETWEEN_REGIONS) / (float)SimpleCloudsConstants.CLOUD_SCALE,
                0.0F,
                1.0F,
                Integer.MAX_VALUE,
                0,
                Integer.MIN_VALUE
        );
        region.moveToWorldPos((float)hurricane.centerX(), (float)hurricane.centerZ());
        region.setWorldRadius(hurricane.stormExtentRadius() + SimpleCloudsConstants.MIN_SPAWN_DIST_BETWEEN_REGIONS);
        region.setStretchFactor(1.0F);
        region.setRotation(0.0F);
        return region;
    }

    public static void updateReservationRegion(CloudRegion region, HurricaneInstance hurricane) {
        region.moveToWorldPos((float)hurricane.position.x, (float)hurricane.position.z);
        region.setWorldRadius(hurricane.getStormExtentRadius() + SimpleCloudsConstants.MIN_SPAWN_DIST_BETWEEN_REGIONS);
        region.setStretchFactor(1.0F);
        region.setRotation(0.0F);
    }

    public static void updateReservationRegion(CloudRegion region, HurricaneRenderSnapshot hurricane) {
        region.moveToWorldPos((float)hurricane.centerX(), (float)hurricane.centerZ());
        region.setWorldRadius(hurricane.stormExtentRadius() + SimpleCloudsConstants.MIN_SPAWN_DIST_BETWEEN_REGIONS);
        region.setStretchFactor(1.0F);
        region.setRotation(0.0F);
    }

    public static @Nullable Level resolveLevel(@Nullable Object owner) {
        if (!(owner instanceof CloudManager<?> cloudManager) || CLOUD_MANAGER_LEVEL_FIELD == null) {
            return null;
        }
        try {
            Object level = CLOUD_MANAGER_LEVEL_FIELD.get(cloudManager);
            return level instanceof Level resolved ? resolved : null;
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private static boolean intersectsReservation(double centerX, double centerZ, double stormExtentRadius,
                                                 double worldX, double worldZ, double extraRadius) {
        double dx = worldX - centerX;
        double dz = worldZ - centerZ;
        double reservedRadius = stormExtentRadius + Math.max(0.0D, extraRadius);
        return dx * dx + dz * dz <= reservedRadius * reservedRadius;
    }

    private static HurricaneSemanticSample sample(HurricaneInstance hurricane, double worldX, double worldZ) {
        return sample(
                hurricane.position.x,
                hurricane.position.z,
                hurricane.getAnchorY(),
                hurricane.getCoreRadius(),
                hurricane.getStormExtentRadius(),
                hurricane.getVisualEyeRadius(),
                hurricane.getVisualEdgeFade(),
                hurricane.getBandCount(),
                hurricane.getBandWidth(),
                hurricane.getSpiralTightness(),
                hurricane.getRotationPhase(),
                hurricane.getTransitionStart(),
                hurricane.getTransitionEnd(),
                HurricaneInstance.HURRICANE_CLOUD_TYPE_ID,
                worldX,
                worldZ
        );
    }

    private static HurricaneSemanticSample sample(HurricaneRenderSnapshot hurricane, double worldX, double worldZ) {
        return sample(
                hurricane.centerX(),
                hurricane.centerZ(),
                hurricane.anchorY(),
                hurricane.coreRadius(),
                hurricane.stormExtentRadius(),
                hurricane.eyeRadius(),
                hurricane.edgeFade(),
                hurricane.bandCount(),
                hurricane.bandWidth(),
                hurricane.spiralTightness(),
                hurricane.rotationPhase(),
                hurricane.transitionStart(),
                hurricane.transitionEnd(),
                hurricane.cloudTypeId(),
                worldX,
                worldZ
        );
    }

    private static HurricaneSemanticSample sample(double centerX, double centerZ, float anchorY, float coreRadius,
                                                  float stormExtentRadius, float eyeRadius, float edgeFade, int bandCount,
                                                  float bandWidth, float spiralTightness, float rotationPhase,
                                                  float transitionStart, float transitionEnd,
                                                  net.minecraft.resources.ResourceLocation cloudTypeId,
                                                  double worldX, double worldZ) {
        float dx = (float)(worldX - centerX);
        float dz = (float)(worldZ - centerZ);
        float radius = Mth.sqrt(dx * dx + dz * dz);
        if (radius > stormExtentRadius + edgeFade * 1.20F) {
            return HurricaneSemanticSample.none();
        }

        boolean inEye = radius <= eyeRadius;
        float angle = (float)Math.atan2(dz, dx);
        float normalizedRadius = stormExtentRadius > 0.0F ? saturate(radius / stormExtentRadius) : 0.0F;
        float spinPhase = angle + Math.max(radius - eyeRadius, 0.0F) * spiralTightness - rotationPhase;

        float outerMask = 1.0F - smoothstep(stormExtentRadius - edgeFade * 0.34F, stormExtentRadius + edgeFade * 0.92F, radius);
        float eyeHole = inEye ? 0.0F : smoothstep(eyeRadius + edgeFade * 0.06F, eyeRadius + edgeFade * 0.82F, radius);

        float eyewallCenter = eyeRadius + bandWidth * 0.36F;
        float eyewallThickness = bandWidth * 0.88F + edgeFade * 0.14F;
        float eyewall = 1.0F - smoothstep(eyewallThickness * 0.26F, eyewallThickness, Math.abs(radius - eyewallCenter));
        eyewall *= eyeHole;

        float armNoiseA = cos01(spinPhase * bandCount + normalizedRadius * 7.0F);
        float armNoiseB = cos01(spinPhase * (bandCount * 0.72F + 1.10F) - normalizedRadius * 9.5F);
        float armNoise = smoothstep(0.60F, 0.93F, Mth.lerp(0.42F, armNoiseA, armNoiseB));

        float spiralEnvelope = smoothstep(eyeRadius + bandWidth * 0.12F, eyeRadius + bandWidth * 1.28F, radius);
        spiralEnvelope *= 1.0F - smoothstep(coreRadius * 0.78F, coreRadius * 1.18F, radius);

        float coreCoverage = Math.max(eyewall, armNoise * spiralEnvelope);

        // Start the cumulonimbus recovery close to the eyewall so the core never hands off into a dead gap.
        float bridgeStart = eyeRadius + bandWidth * 0.52F;
        float bridgeBuildEnd = Math.max(bridgeStart + bandWidth * 1.85F, coreRadius * 0.36F);
        float bridgeFadeEnd = Math.max(coreRadius * 1.08F, bridgeBuildEnd + bandWidth * 2.40F);

        float cbStart = Math.min(transitionStart, bridgeStart);
        float cbEnvelope = smoothstep(cbStart, transitionEnd, radius);
        cbEnvelope *= 1.0F - smoothstep(stormExtentRadius * 1.02F, stormExtentRadius + edgeFade * 0.78F, radius);

        float cbNoiseA = cos01(angle * 1.9F - rotationPhase * 0.05F + normalizedRadius * 6.8F);
        float cbNoiseB = cos01(angle * 4.4F + rotationPhase * 0.025F - normalizedRadius * 12.6F);
        float cbNoiseC = cos01(angle * 2.7F - normalizedRadius * 4.4F);
        float cbNoise = smoothstep(0.18F, 0.90F, Mth.lerp(0.30F, Mth.lerp(0.45F, cbNoiseA, cbNoiseB), cbNoiseC));

        float innerCbA = cos01(spinPhase * (bandCount * 0.92F + 0.85F) - normalizedRadius * 6.4F);
        float innerCbB = cos01(angle * 2.2F - rotationPhase * 0.10F + normalizedRadius * 4.8F);
        float innerCbMask = smoothstep(0.20F, 0.82F, Mth.lerp(0.42F, innerCbA, innerCbB));

        float innerBridgeEnvelope = smoothstep(bridgeStart, bridgeBuildEnd, radius);
        innerBridgeEnvelope *= 1.0F - smoothstep(coreRadius * 0.94F, bridgeFadeEnd, radius);

        float outerBandEnvelope = smoothstep(coreRadius * 0.42F, stormExtentRadius * 0.90F, radius);
        outerBandEnvelope *= 1.0F - smoothstep(stormExtentRadius * 0.96F, stormExtentRadius + edgeFade * 0.72F, radius);

        float outerBandA = smoothstep(
                0.58F,
                0.94F,
                cos01(spinPhase * (bandCount * 0.42F + 1.05F) - normalizedRadius * 15.0F)
        );
        float outerBandB = smoothstep(
                0.56F,
                0.92F,
                cos01((angle - rotationPhase * 0.16F) * (bandCount * 0.30F + 1.85F) + normalizedRadius * 21.0F)
        );
        float outerBandMask = smoothstep(0.34F, 0.88F, Mth.lerp(0.38F, outerBandA, outerBandB));

        float cbMass = cbEnvelope * (0.22F + cbNoise * 0.26F + outerBandMask * 0.52F);

        float innerBridge = innerBridgeEnvelope * (0.54F + innerCbMask * 0.26F + armNoise * 0.20F);

        float continuityBand = smoothstep(bridgeStart, coreRadius * 0.96F, radius);
        continuityBand *= 1.0F - smoothstep(coreRadius * 1.08F, transitionEnd * 0.92F, radius);
        continuityBand *= 0.48F + Mth.lerp(0.50F, armNoise, outerBandMask) * 0.44F;

        float spiralShoulders = outerBandEnvelope * (0.28F + outerBandMask * 0.72F);
        spiralShoulders *= 0.52F + cbNoise * 0.30F;

        float anvilEdge = smoothstep(stormExtentRadius * 0.70F, stormExtentRadius * 0.95F, radius);
        anvilEdge *= 1.0F - smoothstep(stormExtentRadius * 1.04F, stormExtentRadius + edgeFade * 0.94F, radius);
        anvilEdge *= smoothstep(0.34F, 0.88F, cbNoiseB) * (0.42F + outerBandMask * 0.58F);

        float outerCoverage = Math.max(Math.max(cbMass, spiralShoulders), Math.max(innerBridge, continuityBand + anvilEdge * 0.20F));
        float coverage = Math.max(coreCoverage, outerCoverage);
        coverage *= outerMask * eyeHole;
        coverage = smoothstep(0.04F, 0.88F, saturate(coverage));

        if (inEye) {
            return new HurricaneSemanticSample(cloudTypeId, anchorY, 0.0F, 0.0F, true, 0.0F, 0.0F);
        }
        if (coverage <= MIN_COVERAGE) {
            return HurricaneSemanticSample.none();
        }

        float rainStrength = Math.max(outerCoverage * 0.78F, coreCoverage * 0.94F);
        if (outerCoverage > 0.12F) {
            rainStrength = Math.max(rainStrength, OUTER_RAIN_FLOOR);
        }
        rainStrength = Mth.clamp(rainStrength, 0.0F, 1.0F);

        return new HurricaneSemanticSample(
                cloudTypeId,
                anchorY,
                coverage,
                rainStrength,
                false,
                Mth.clamp(coreCoverage, 0.0F, 1.0F),
                Mth.clamp(outerCoverage, 0.0F, 1.0F)
        );
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        if (edge1 <= edge0) {
            return value >= edge1 ? 1.0F : 0.0F;
        }
        float t = saturate((value - edge0) / (edge1 - edge0));
        return t * t * (3.0F - 2.0F * t);
    }

    private static float saturate(float value) {
        return Mth.clamp(value, 0.0F, 1.0F);
    }

    private static float cos01(float value) {
        return 0.5F + 0.5F * Mth.cos(value);
    }

    private static @Nullable Field projectatmosphere$initCloudManagerLevelField() {
        try {
            Field field = CloudManager.class.getDeclaredField("level");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
