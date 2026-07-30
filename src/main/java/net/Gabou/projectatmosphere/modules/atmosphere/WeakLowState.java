package net.Gabou.projectatmosphere.modules.atmosphere;

import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class WeakLowState {
    private static final String TAG_ID = "Id";
    private static final String TAG_REGION = "Region";
    private static final String TAG_CENTER_X = "CenterX";
    private static final String TAG_CENTER_Y = "CenterY";
    private static final String TAG_CENTER_Z = "CenterZ";
    private static final String TAG_RADIUS = "Radius";
    private static final String TAG_INTENSITY = "Intensity";
    private static final String TAG_SUPPORT_SCORE = "SupportScore";
    private static final String TAG_PRESSURE_ANOMALY = "PressureAnomaly";
    private static final String TAG_HUMIDITY = "Humidity";
    private static final String TAG_CLOUD_WATER = "CloudWater";
    private static final String TAG_CLOUD_COVER = "CloudCover";
    private static final String TAG_CONVERGENCE = "Convergence";
    private static final String TAG_SHEAR = "Shear";
    private static final String TAG_INSTABILITY = "Instability";
    private static final String TAG_CUMULONIMBUS_SUPPORT = "CumulonimbusSupport";
    private static final String TAG_NIMBOSTRATUS_SUPPORT = "NimbostratusSupport";
    private static final String TAG_WEATHER_CELL_SUPPORT = "WeatherCellSupport";
    private static final String TAG_AGE_TICKS = "AgeTicks";
    private static final String TAG_LIFETIME_TICKS = "LifetimeTicks";
    private static final String TAG_ACTIVE = "Active";
    private static final String TAG_BLOCKED_REASON = "BlockedReason";
    private static final String TAG_DECAY_REASON = "DecayReason";
    private static final String TAG_PROMOTED_TO_CYCLONE_SEED = "PromotedToCycloneSeed";
    private static final String TAG_REGION_X = "RegionX";
    private static final String TAG_REGION_Z = "RegionZ";
    private static final String TAG_REGION_SIZE = "RegionSize";

    private final UUID id;
    private RegionInstanceKey regionKey;
    private Vec3 center;
    private float radius;
    private float intensity;
    private float supportScore;
    private float pressureAnomaly;
    private float humidity;
    private float cloudWater;
    private float cloudCover;
    private float convergence;
    private float shear;
    private float instability;
    private float cumulonimbusSupport;
    private float nimbostratusSupport;
    private float weatherCellSupport;
    private int ageTicks;
    private int lifetimeTicks;
    private boolean active;
    private String blockedReason;
    private String decayReason;
    private boolean promotedToCycloneSeed;

    public WeakLowState(UUID id,
                        RegionInstanceKey regionKey,
                        Vec3 center,
                        float radius,
                        float intensity,
                        float supportScore,
                        int ageTicks,
                        int lifetimeTicks,
                        boolean active) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.regionKey = regionKey;
        this.center = center == null ? Vec3.ZERO : center;
        this.radius = Mth.clamp(radius, 180.0F, 1600.0F);
        this.intensity = clamp01(intensity);
        this.supportScore = clamp01(supportScore);
        this.ageTicks = Math.max(0, ageTicks);
        this.lifetimeTicks = Math.max(20, lifetimeTicks);
        this.active = active;
        this.blockedReason = "none";
        this.decayReason = "none";
    }

    public UUID getId() {
        return id;
    }

    public RegionInstanceKey getRegionKey() {
        return regionKey;
    }

    public Vec3 getCenter() {
        return center;
    }

    public float getRadius() {
        return radius;
    }

    public float getIntensity() {
        return intensity;
    }

    public float getSupportScore() {
        return supportScore;
    }

    public float getPressureAnomaly() {
        return pressureAnomaly;
    }

    public float getHumidity() {
        return humidity;
    }

    public float getCloudWater() {
        return cloudWater;
    }

    public float getCloudCover() {
        return cloudCover;
    }

    public float getConvergence() {
        return convergence;
    }

    public float getShear() {
        return shear;
    }

    public float getInstability() {
        return instability;
    }

    public float getCumulonimbusSupport() {
        return cumulonimbusSupport;
    }

    public float getNimbostratusSupport() {
        return nimbostratusSupport;
    }

    public float getWeatherCellSupport() {
        return weatherCellSupport;
    }

    public int getAgeTicks() {
        return ageTicks;
    }

    public int getLifetimeTicks() {
        return lifetimeTicks;
    }

    public boolean isActive() {
        return active;
    }

    public String getBlockedReason() {
        return blockedReason;
    }

    public String getDecayReason() {
        return decayReason;
    }

    public boolean isPromotedToCycloneSeed() {
        return promotedToCycloneSeed;
    }

    void applyCandidate(WeakLowManager.WeakLowCandidate candidate, int tickStep) {
        if (candidate == null) {
            active = false;
            decayReason = "candidate unavailable";
            return;
        }
        regionKey = candidate.regionKey();
        center = Vec3.atCenterOf(candidate.position());
        supportScore = Mth.lerp(0.18F, supportScore, candidate.supportScore());
        pressureAnomaly = candidate.pressureAnomaly();
        humidity = candidate.humidity();
        cloudWater = candidate.cloudWater();
        cloudCover = candidate.cloudCover();
        convergence = candidate.convergence();
        shear = candidate.shear();
        instability = candidate.instability();
        cumulonimbusSupport = candidate.cumulonimbusSupport();
        nimbostratusSupport = candidate.nimbostratusSupport();
        weatherCellSupport = candidate.weatherCellSupport();
        blockedReason = candidate.blockedReason();

        float targetIntensity = candidate.eligible()
                ? Mth.clamp(0.12F + candidate.supportScore() * 0.68F, 0.12F, 0.78F)
                : Math.max(0.0F, candidate.supportScore() * 0.45F);
        float tracking = candidate.eligible() ? 0.055F : 0.090F;
        intensity = Mth.lerp(tracking, intensity, targetIntensity);
        radius = Mth.clamp(Mth.lerp(0.08F, radius, 280.0F + supportScore * 720.0F), 180.0F, 1200.0F);
        ageTicks += Math.max(0, tickStep);
        if (candidate.supportScore() >= WeakLowManager.SUSTAIN_SUPPORT_THRESHOLD) {
            lifetimeTicks = Math.min(lifetimeTicks + tickStep, WeakLowManager.MAX_LIFETIME_TICKS);
            decayReason = "none";
        } else {
            lifetimeTicks -= Math.max(0, tickStep);
            decayReason = candidate.blockedReason();
        }
        if (intensity < 0.045F || lifetimeTicks <= 0) {
            active = false;
            decayReason = decayReason == null || decayReason.isBlank() || "none".equals(decayReason)
                    ? "support lost"
                    : decayReason;
        }
    }

    void markPromotedToCycloneSeed() {
        promotedToCycloneSeed = true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_ID, id);
        if (regionKey != null) {
            tag.put(TAG_REGION, saveRegionKey(regionKey));
        }
        tag.putDouble(TAG_CENTER_X, center.x());
        tag.putDouble(TAG_CENTER_Y, center.y());
        tag.putDouble(TAG_CENTER_Z, center.z());
        tag.putFloat(TAG_RADIUS, radius);
        tag.putFloat(TAG_INTENSITY, intensity);
        tag.putFloat(TAG_SUPPORT_SCORE, supportScore);
        tag.putFloat(TAG_PRESSURE_ANOMALY, pressureAnomaly);
        tag.putFloat(TAG_HUMIDITY, humidity);
        tag.putFloat(TAG_CLOUD_WATER, cloudWater);
        tag.putFloat(TAG_CLOUD_COVER, cloudCover);
        tag.putFloat(TAG_CONVERGENCE, convergence);
        tag.putFloat(TAG_SHEAR, shear);
        tag.putFloat(TAG_INSTABILITY, instability);
        tag.putFloat(TAG_CUMULONIMBUS_SUPPORT, cumulonimbusSupport);
        tag.putFloat(TAG_NIMBOSTRATUS_SUPPORT, nimbostratusSupport);
        tag.putFloat(TAG_WEATHER_CELL_SUPPORT, weatherCellSupport);
        tag.putInt(TAG_AGE_TICKS, ageTicks);
        tag.putInt(TAG_LIFETIME_TICKS, lifetimeTicks);
        tag.putBoolean(TAG_ACTIVE, active);
        tag.putString(TAG_BLOCKED_REASON, blockedReason == null ? "none" : blockedReason);
        tag.putString(TAG_DECAY_REASON, decayReason == null ? "none" : decayReason);
        tag.putBoolean(TAG_PROMOTED_TO_CYCLONE_SEED, promotedToCycloneSeed);
        return tag;
    }

    public static WeakLowState load(CompoundTag tag) {
        if (tag == null || !tag.hasUUID(TAG_ID)) {
            return null;
        }
        RegionInstanceKey region = tag.contains(TAG_REGION, Tag.TAG_COMPOUND)
                ? loadRegionKey(tag.getCompound(TAG_REGION))
                : null;
        Vec3 center = new Vec3(tag.getDouble(TAG_CENTER_X), tag.getDouble(TAG_CENTER_Y), tag.getDouble(TAG_CENTER_Z));
        WeakLowState state = new WeakLowState(
                tag.getUUID(TAG_ID),
                region,
                center,
                tag.getFloat(TAG_RADIUS),
                tag.getFloat(TAG_INTENSITY),
                tag.getFloat(TAG_SUPPORT_SCORE),
                tag.getInt(TAG_AGE_TICKS),
                tag.getInt(TAG_LIFETIME_TICKS),
                !tag.contains(TAG_ACTIVE, Tag.TAG_BYTE) || tag.getBoolean(TAG_ACTIVE)
        );
        state.pressureAnomaly = tag.getFloat(TAG_PRESSURE_ANOMALY);
        state.humidity = tag.getFloat(TAG_HUMIDITY);
        state.cloudWater = tag.getFloat(TAG_CLOUD_WATER);
        state.cloudCover = tag.getFloat(TAG_CLOUD_COVER);
        state.convergence = tag.getFloat(TAG_CONVERGENCE);
        state.shear = tag.getFloat(TAG_SHEAR);
        state.instability = tag.getFloat(TAG_INSTABILITY);
        state.cumulonimbusSupport = tag.getFloat(TAG_CUMULONIMBUS_SUPPORT);
        state.nimbostratusSupport = tag.getFloat(TAG_NIMBOSTRATUS_SUPPORT);
        state.weatherCellSupport = tag.getFloat(TAG_WEATHER_CELL_SUPPORT);
        state.blockedReason = tag.contains(TAG_BLOCKED_REASON, Tag.TAG_STRING) ? tag.getString(TAG_BLOCKED_REASON) : "none";
        state.decayReason = tag.contains(TAG_DECAY_REASON, Tag.TAG_STRING) ? tag.getString(TAG_DECAY_REASON) : "none";
        state.promotedToCycloneSeed = tag.getBoolean(TAG_PROMOTED_TO_CYCLONE_SEED);
        return state;
    }

    private static CompoundTag saveRegionKey(RegionInstanceKey key) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_REGION_X, key.regionX());
        tag.putInt(TAG_REGION_Z, key.regionZ());
        tag.putInt(TAG_REGION_SIZE, key.regionSize());
        return tag;
    }

    private static RegionInstanceKey loadRegionKey(CompoundTag tag) {
        if (tag == null || !tag.contains(TAG_REGION_X, Tag.TAG_INT) || !tag.contains(TAG_REGION_Z, Tag.TAG_INT)) {
            return null;
        }
        int size = tag.contains(TAG_REGION_SIZE, Tag.TAG_INT)
                ? tag.getInt(TAG_REGION_SIZE)
                : RegionInstanceKey.DEFAULT_REGION_SIZE;
        return new RegionInstanceKey(tag.getInt(TAG_REGION_X), tag.getInt(TAG_REGION_Z), size);
    }

    private static float clamp01(float value) {
        return Mth.clamp(value, 0.0F, 1.0F);
    }
}
