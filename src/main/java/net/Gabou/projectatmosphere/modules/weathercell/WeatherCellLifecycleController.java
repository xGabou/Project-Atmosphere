package net.Gabou.projectatmosphere.modules.weathercell;

import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericSupportEvaluator;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.modules.atmosphere.WeakLowManager;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

final class WeatherCellLifecycleController {
    private static final int TICK_STEP = WeatherCellManager.TICK_INTERVAL;
    private static final float EVOLUTION_TRACKING = 0.08F;
    private static final int RAIN_TO_THUNDER_MIN_AGE_TICKS = 20 * 90;
    private static final int THUNDER_TO_SUPERCELL_MIN_AGE_TICKS = 20 * 180;

    boolean tick(ServerLevel level, WeatherCellState cell) {
        if (level == null || cell == null || !cell.isActive()) {
            return false;
        }
        cell.incrementAge(TICK_STEP);
        if (cell.getAgeTicks() >= cell.getLifetimeTicks()) {
            cell.setActive(false);
            return true;
        }

        CellProfile profile = CellProfile.forType(cell.getType());
        if (profile == null) {
            cell.setActive(false);
            return true;
        }

        RegionInstanceKey currentKey = WeatherCellSupport.currentRegionKey(cell);
        RegionAtmosphereState currentState = WeatherCellSupport.currentAtmosphere(cell);
        AtmosphericSupportEvaluator.Support support = AtmosphericSupportEvaluator.evaluate(currentKey, currentState);
        WeakLowManager.WeatherCellBoost lowBoost = WeakLowManager.weatherCellBoost(
                currentKey,
                currentState == null ? null : currentState.getPosition()
        );
        float evolutionTarget = resolveEvolutionTarget(cell, support, lowBoost);
        float severeEvolutionTarget = resolveSevereEvolutionTarget(cell, support, lowBoost);
        cell.setEvolutionScore(Mth.lerp(EVOLUTION_TRACKING, cell.getEvolutionScore(), evolutionTarget));
        cell.setSevereEvolutionScore(Mth.lerp(EVOLUTION_TRACKING, cell.getSevereEvolutionScore(), severeEvolutionTarget));
        updateTypeFromSupport(cell);
        profile = CellProfile.forType(cell.getType());

        float activeSupport = activeSupportForType(cell);
        float targetIntensity = profile.targetIntensity(activeSupport);
        float nextIntensity = Mth.lerp(profile.trackingRate(activeSupport), cell.getIntensity(), targetIntensity);
        cell.setIntensity(nextIntensity);
        cell.setRainIntensity(Mth.clamp(nextIntensity * profile.rainScale(), 0.0F, profile.maxRainIntensity()));
        cell.setRadius(Mth.lerp(profile.sizeTracking(), cell.getRadius(), profile.targetRadius(cell.getRadius(), activeSupport)));

        if (support.hasState()) {
            applyConservativeRainFeedback(support.state(), cell, profile);
            cell.setMoisture(support.humidity());
            cell.setCloudWater(support.state().getCloudWater());
            cell.setPressureAnomaly(1013.25F - support.pressure());
            cell.setWindInfluence(Mth.clamp(support.windConvergence() * 0.65F + Math.max(0.0F, support.humidityTransport()) * 8.0F, 0.0F, 1.0F));
            cell.setInstability(cell.getEvolutionScore());
        }

        if (cell.getIntensity() < 0.035F
                && cell.getEvolutionScore() < AtmosphericSupportEvaluator.WEATHER_RAIN_THRESHOLD
                && cell.getAgeTicks() > 20 * 120) {
            cell.setActive(false);
        }
        return true;
    }

    private static float resolveEvolutionTarget(WeatherCellState cell,
                                                AtmosphericSupportEvaluator.Support support,
                                                WeakLowManager.WeatherCellBoost lowBoost) {
        if (!support.hasState()) {
            return 0.0F;
        }
        float ageDecay = Mth.clamp((cell.getLifetimeTicks() - cell.getAgeTicks()) / (float) (20 * 180), 0.0F, 1.0F);
        float base = switch (cell.getType()) {
            case SUPERCELL, THUNDERSTORM -> support.thunderstormSupport();
            case RAIN_CELL -> support.rainCellSustain();
            case CYCLONE, BLIZZARD -> 0.0F;
        };
        float weakLowBoost = lowBoost == null ? 0.0F : lowBoost.evolutionBoost();
        return Mth.clamp((base + weakLowBoost) * ageDecay, 0.0F, 1.0F);
    }

    private static float resolveSevereEvolutionTarget(WeatherCellState cell,
                                                      AtmosphericSupportEvaluator.Support support,
                                                      WeakLowManager.WeatherCellBoost lowBoost) {
        if (!support.hasState() || !isPhase4EvolutionType(cell.getType())) {
            return 0.0F;
        }
        float ageDecay = Mth.clamp((cell.getLifetimeTicks() - cell.getAgeTicks()) / (float) (20 * 180), 0.0F, 1.0F);
        float weakLowBoost = lowBoost == null ? 0.0F : lowBoost.severeBoost();
        return Mth.clamp((support.supercellSupport() + weakLowBoost) * ageDecay, 0.0F, 1.0F);
    }

    private static void updateTypeFromSupport(WeatherCellState cell) {
        if (!isPhase4EvolutionType(cell.getType())) {
            return;
        }
        float score = cell.getEvolutionScore();
        float severeScore = cell.getSevereEvolutionScore();
        WeatherCellType current = cell.getType();
        switch (current) {
            case RAIN_CELL -> {
                if (cell.getAgeTicks() >= RAIN_TO_THUNDER_MIN_AGE_TICKS
                        && score >= AtmosphericSupportEvaluator.WEATHER_THUNDER_THRESHOLD) {
                    cell.setType(WeatherCellType.THUNDERSTORM);
                }
            }
            case THUNDERSTORM -> {
                if (cell.getAgeTicks() >= THUNDER_TO_SUPERCELL_MIN_AGE_TICKS
                        && severeScore >= AtmosphericSupportEvaluator.WEATHER_SEVERE_THRESHOLD) {
                    cell.setType(WeatherCellType.SUPERCELL);
                } else if (score < AtmosphericSupportEvaluator.WEATHER_THUNDER_WEAKEN_THRESHOLD) {
                    cell.setType(WeatherCellType.RAIN_CELL);
                }
            }
            case SUPERCELL -> {
                if (severeScore < AtmosphericSupportEvaluator.WEATHER_SUPERCELL_WEAKEN_THRESHOLD) {
                    cell.setType(WeatherCellType.THUNDERSTORM);
                }
            }
            case CYCLONE, BLIZZARD -> {
            }
        }
    }

    private static float activeSupportForType(WeatherCellState cell) {
        if (cell.getType() == WeatherCellType.SUPERCELL) {
            return Math.max(cell.getEvolutionScore(), cell.getSevereEvolutionScore());
        }
        return cell.getEvolutionScore();
    }

    private static boolean isPhase4EvolutionType(WeatherCellType type) {
        return type == WeatherCellType.RAIN_CELL
                || type == WeatherCellType.THUNDERSTORM
                || type == WeatherCellType.SUPERCELL;
    }

    private static void applyConservativeRainFeedback(RegionAtmosphereState state, WeatherCellState cell, CellProfile profile) {
        float rainTarget = Mth.clamp(cell.getRainIntensity(), 0.0F, profile.maxRainIntensity());
        if (rainTarget > state.getRainIntensity()) {
            state.setRainIntensity(Mth.lerp(profile.rainFeedbackTracking(), state.getRainIntensity(), rainTarget));
        }
        float cloudCoverTarget = Mth.clamp(profile.cloudCoverFloor() * cell.getIntensity(), 0.0F, 1.0F);
        if (cloudCoverTarget > state.getCloudCover()) {
            state.setCloudCover(Mth.lerp(0.05F, state.getCloudCover(), cloudCoverTarget));
        }
        float drain = Mth.clamp(cell.getIntensity() * profile.cloudWaterDrainScale(), 0.0F, profile.maxCloudWaterDrain());
        state.setCloudWater(Math.max(0.0F, state.getCloudWater() - drain));
    }

    private record CellProfile(
            float minimumActiveSupport,
            float targetIntensityScale,
            float trackingActive,
            float trackingWeakening,
            float rainScale,
            float maxRainIntensity,
            float rainFeedbackTracking,
            float cloudWaterDrainScale,
            float maxCloudWaterDrain,
            float cloudCoverFloor,
            float radiusGrowth,
            float maxRadius,
            float sizeTracking
    ) {
        static CellProfile forType(WeatherCellType type) {
            return switch (type) {
                case RAIN_CELL -> new CellProfile(
                        AtmosphericSupportEvaluator.WEATHER_RAIN_THRESHOLD,
                        0.78F,
                        0.055F,
                        0.085F,
                        0.62F,
                        0.45F,
                        0.08F,
                        0.003F,
                        0.006F,
                        0.55F,
                        90.0F,
                        520.0F,
                        0.015F
                );
                case THUNDERSTORM -> new CellProfile(
                        AtmosphericSupportEvaluator.WEATHER_THUNDER_THRESHOLD,
                        0.86F,
                        0.050F,
                        0.080F,
                        0.74F,
                        0.62F,
                        0.09F,
                        0.004F,
                        0.008F,
                        0.72F,
                        180.0F,
                        760.0F,
                        0.014F
                );
                case SUPERCELL -> new CellProfile(
                        AtmosphericSupportEvaluator.WEATHER_SEVERE_THRESHOLD,
                        0.94F,
                        0.045F,
                        0.075F,
                        0.86F,
                        0.78F,
                        0.10F,
                        0.005F,
                        0.010F,
                        0.86F,
                        260.0F,
                        1000.0F,
                        0.012F
                );
                case CYCLONE, BLIZZARD -> new CellProfile(
                        1.0F,
                        0.0F,
                        0.0F,
                        0.075F,
                        0.0F,
                        0.0F,
                        0.0F,
                        0.0F,
                        0.0F,
                        0.0F,
                        0.0F,
                        1200.0F,
                        0.0F
                );
            };
        }

        float targetIntensity(float support) {
            if (support < minimumActiveSupport) {
                return 0.0F;
            }
            return Mth.clamp((support - minimumActiveSupport * 0.70F) * targetIntensityScale, 0.0F, 1.0F);
        }

        float trackingRate(float support) {
            return support >= minimumActiveSupport ? trackingActive : trackingWeakening;
        }

        float targetRadius(float currentRadius, float support) {
            if (support < minimumActiveSupport) {
                return currentRadius;
            }
            return Mth.clamp(currentRadius + support * radiusGrowth, 180.0F, maxRadius);
        }
    }
}
