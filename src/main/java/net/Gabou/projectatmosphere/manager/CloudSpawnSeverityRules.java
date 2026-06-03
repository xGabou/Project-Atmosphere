package net.Gabou.projectatmosphere.manager;

import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.modules.core.CloudLibrary;
import net.Gabou.projectatmosphere.modules.storm.GlobalStormHistoryData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

public final class CloudSpawnSeverityRules {
    private static final float PRESSURE_AVERAGE = 1013.25f;
    private static final float STORM_BIAS = 1.1f;

    private CloudSpawnSeverityRules() {
    }

    public static float calculateDewPoint(float temperature, float humidity) {
        final float aConst = 17.62f;
        final float bConst = 243.12f;

        float result = (aConst * temperature) / (bConst + temperature) + (float) Math.log(humidity / 100.0f);
        return (bConst * result) / (aConst - result);
    }

    public static int determineCloudSeverity(
            float temperature,
            float humidity,
            float pressure,
            float dewPoint,
            float stormFactor,
            ServerLevel level
    ) {
        RandomSource random = RandomSource.create();
        float dewGap = Math.max(0f, temperature - dewPoint);
        float dewGapFactor = 1.0f - Math.min(dewGap / 12.0f, 1.0f);

        float pressureFactor = (PRESSURE_AVERAGE - pressure) / 60.0f;
        pressureFactor = Math.max(-1f, Math.min(pressureFactor, 1f));

        float humidityFactor = humidity / 100.0f;
        float tempIdealness = 1.0f - Math.abs(temperature - 18.0f) / 45.0f;

        float instability = (dewGapFactor * 0.4f)
                + (pressureFactor * 0.25f)
                + (humidityFactor * 0.55f)
                + (tempIdealness * 0.3f);
        instability = Math.min(Math.max(instability, 0f), 1f);

        int currentDay = (int) (level.getDayTime() / 24000L);
        GlobalStormHistoryData data = GlobalStormHistoryData.get(level);

        int lastStrong = data.getLastSevereDay();
        int daysSince = (lastStrong == Integer.MIN_VALUE)
                ? Integer.MAX_VALUE : Math.max(0, currentDay - lastStrong);

        int recentStreak = data.getRecentSevereCount();
        int cooldown = data.getCooldownDaysRemaining();

        if (recentStreak >= 4 && cooldown <= 0) {
            cooldown = 3 + random.nextInt(3);
            data.setCooldownDaysRemaining(cooldown);
        }

        if (cooldown > 0 && daysSince > 0) {
            cooldown = Math.max(0, cooldown - daysSince);
            data.setCooldownDaysRemaining(cooldown);
        }

        if (cooldown > 0) {
            stormFactor *= 0.25f;
            instability *= 0.5f;
        }

        int severity = getSeverity(stormFactor, daysSince, instability);

        if (severity >= 5) {
            data.recordSevere(currentDay);
        } else {
            data.resetIfCalm(currentDay);
        }

        return severity;
    }

    public static String selectCloudId(int severity, boolean freezing) {
        if (severity > 5 && freezing) {
            return CloudLibrary.getSnowstormCloudId();
        }
        String cloudId = CloudLibrary.getCloudIdFromSeverity(severity);
        if (CloudLibrary.isThunderCloud(cloudId) && freezing) {
            return CloudLibrary.getCloudIdFromSeverity(5);
        }
        return cloudId;
    }

    private static int getSeverity(float stormFactor, int daysSince, float instability) {
        float boost;
        if (daysSince <= 2) {
            boost = 1f / (5 - daysSince);
        } else {
            boost = 1f + 0.07f * daysSince;
            boost = Math.min(boost, 1.7f);
        }

        float adjustedChance = Math.min(1f, stormFactor * boost * STORM_BIAS);
        return calculateSeverity(daysSince, instability, adjustedChance);
    }

    private static int calculateSeverity(int daysSince, float instability, float adjustedChance) {
        double weighted = instability * adjustedChance * AtmoCommonConfig.STORM_SEVERITY_BOOSTER.get();
        float raw = (float) (1.0 / (1.0 + Math.exp(-2.3 * (weighted - 1.0))));

        float dayBias = Math.min(1f, daysSince / 10f);
        float biasAdjusted = raw + (0.25f * dayBias * (1f - raw));

        int severity = (int) Math.floor(biasAdjusted * 6.0f) + 1;
        return Math.max(1, Math.min(7, severity));
    }
}
