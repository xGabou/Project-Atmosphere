package net.Gabou.projectatmosphere.clouds.simulation;

import net.Gabou.projectatmosphere.clouds.state.CloudClusterState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
import net.Gabou.projectatmosphere.clouds.type.CloudEvolutionTarget;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeDefinition;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeRegistry;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * Controle l'evolution backend des types de nuages.
 * Cette classe ne rend rien et ne synchronise rien directement.
 */
final class CloudRegionEvolutionController {

    private static final int EVOLUTION_CHECK_INTERVAL_TICKS = 20;

    boolean tick(@NotNull ServerLevel level, @NotNull CloudRegionState state) {
        if (!state.isActive() || state.isEmpty()) {
            return false;
        }

        boolean changed = false;
        for (CloudClusterState cluster : state.getClusters()) {
            if (cluster == null || !cluster.isActive()) {
                continue;
            }

            cluster.incrementCloudTypeTicks();
            if (cluster.getCloudTypeTicks() % EVOLUTION_CHECK_INTERVAL_TICKS != 0) {
                continue;
            }

            CloudTypeDefinition definition = CloudTypeRegistry.getOrDefault(cluster.getCloudTypeId());
            EvolutionInputs inputs = resolveInputs(level, cluster);

            for (CloudEvolutionTarget target : definition.getEvolutionRules().getTargets()) {
                if (target.matches(
                        cluster.getCloudTypeTicks(),
                        inputs.humidity(),
                        inputs.instability(),
                        inputs.pressure(),
                        inputs.stormChance(),
                        inputs.temperature(),
                        inputs.density(),
                        inputs.coverage(),
                        inputs.lift(),
                        inputs.mergePressure()
                ) || target.canAdvanceEarly(
                        cluster.getCloudTypeTicks(),
                        inputs.humidity(),
                        inputs.instability(),
                        inputs.pressure(),
                        inputs.stormChance(),
                        inputs.temperature(),
                        inputs.density(),
                        inputs.coverage(),
                        inputs.lift(),
                        inputs.mergePressure()
                )) {
                    cluster.changeCloudType(target.getTargetCloudTypeId());
                    CloudRegionTypeGeometry.apply(cluster, cluster.getCloudTypeId());
                    changed = true;
                    break;
                }
            }
        }

        return changed;
    }

    private @NotNull EvolutionInputs resolveInputs(@NotNull ServerLevel level, @NotNull CloudClusterState cluster) {
        BlockPos samplePos = BlockPos.containing(
                cluster.getCenter().x(),
                cluster.getCenter().y(),
                cluster.getCenter().z()
        );
        RegionInstanceKey regionKey = RegionInstanceKey.from(samplePos);
        long tick = level.getGameTime();

        float humidity = ForecastOrchestrator.getCurrentHumidity(regionKey, tick);
        float temperature = ForecastOrchestrator.getCurrentTemperature(regionKey, tick);
        float pressure = ForecastOrchestrator.getCurrentPressure(regionKey, tick);
        float stormChance = ForecastOrchestrator.getCurrentStormChance(regionKey, tick);

        RegionAtmosphereState atmosphereState = AtmosphericStateRegistry.getState(regionKey);
        float instability = atmosphereState == null
                ? fallbackInstability(cluster, humidity, temperature, stormChance)
                : computeInstability(atmosphereState, cluster, humidity, temperature, stormChance);

        float density = Mth.clamp(cluster.getDensity() * 0.90F + cluster.getMergePressure() * 0.10F, 0.0F, 1.0F);
        float coverage = Mth.clamp(cluster.getCoverage() * 0.94F + cluster.getMergePressure() * 0.06F, 0.0F, 1.0F);
        float lift = computeLift(cluster, humidity, temperature, instability, stormChance);
        float mergePressure = cluster.getMergePressure();

        return new EvolutionInputs(humidity, temperature, instability, pressure, stormChance, density, coverage, lift, mergePressure);
    }

    private float computeInstability(
            @NotNull RegionAtmosphereState atmosphereState,
            @NotNull CloudClusterState cluster,
            float humidity,
            float temperature,
            float stormChance
    ) {
        float cloudCover = Mth.clamp(atmosphereState.getCloudCover(), 0.0F, 1.0F);
        float rain = Mth.clamp(atmosphereState.getRainIntensity(), 0.0F, 1.0F);
        float wind = Mth.clamp(atmosphereState.getWindStrength() / 18.0F, 0.0F, 1.0F);
        float warmth = temperatureWarmth(temperature);
        float base = (cloudCover * 0.35F) + (rain * 0.30F) + (wind * 0.20F) + (stormChance * 0.10F) + (warmth * 0.05F);
        return Mth.clamp(base + cluster.getMergePressure() * 0.08F + humidity * 0.03F, 0.0F, 1.0F);
    }

    private float fallbackInstability(@NotNull CloudClusterState cluster, float humidity, float temperature, float stormChance) {
        float geometricLift = computeLift(cluster, humidity, temperature, 0.0F, stormChance);
        float warmth = temperatureWarmth(temperature);
        float base = (cluster.getCoverage() * 0.40F) + (cluster.getDensity() * 0.25F) + (cluster.getMergePressure() * 0.12F) + (warmth * 0.08F);
        return Mth.clamp(base + geometricLift * 0.06F, 0.0F, 1.0F);
    }

    private float computeLift(@NotNull CloudClusterState cluster, float humidity, float temperature, float instability, float stormChance) {
        float verticalSpan = Math.max(1.0F, cluster.getTopY() - cluster.getBaseY());
        float shapeLift = Mth.clamp(verticalSpan / 128.0F, 0.0F, 1.0F);
        float ageLift = Mth.clamp((float) cluster.getCloudTypeTicks() / 600.0F, 0.0F, 1.0F);
        float temperatureLift = temperatureWarmth(temperature);
        return Mth.clamp(
                shapeLift * 0.34F
                        + temperatureLift * 0.24F
                        + humidity * 0.16F
                        + instability * 0.16F
                        + stormChance * 0.07F
                        + ageLift * 0.03F,
                0.0F,
                1.0F
        );
    }

    private float temperatureWarmth(float temperature) {
        return Mth.clamp((temperature + 12.0F) / 36.0F, 0.0F, 1.0F);
    }

    private record EvolutionInputs(
            float humidity,
            float temperature,
            float instability,
            float pressure,
            float stormChance,
            float density,
            float coverage,
            float lift,
            float mergePressure
    ) {
    }
}
