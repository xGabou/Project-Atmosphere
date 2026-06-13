package net.Gabou.projectatmosphere.clouds.simulation;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.state.CloudClusterState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
import net.Gabou.projectatmosphere.clouds.simulation.CloudEvolutionStructureAnalyzer.CloudStructuralInputs;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Controle l'evolution backend des types de nuages.
 * Cette classe ne rend rien et ne synchronise rien directement.
 */
final class CloudRegionEvolutionController {

    private static final int EVOLUTION_CHECK_INTERVAL_TICKS = 20;

    boolean tick(@NotNull ServerLevel level, @NotNull CloudRegionState state, @NotNull Collection<CloudRegionState> activeRegions) {
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
            CloudStructuralInputs structuralInputs = CloudEvolutionStructureAnalyzer.analyze(state, cluster, activeRegions);
            EvolutionInputs inputs = resolveInputs(level, cluster, structuralInputs);

            for (CloudEvolutionTarget target : definition.getEvolutionRules().getTargets()) {
                if (!passesStructuralGate(cluster.getCloudTypeId(), target.getTargetCloudTypeId(), structuralInputs)) {
                    continue;
                }
                boolean fullMatch = target.matches(
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
                );
                boolean earlyAdvance = !fullMatch && target.canAdvanceEarly(
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
                );
                if (fullMatch || earlyAdvance) {
                    String previousTypeId = cluster.getCloudTypeId();
                    cluster.changeCloudType(target.getTargetCloudTypeId());
                    CloudRegionTypeGeometry.apply(cluster, cluster.getCloudTypeId());
                    logEvolution(level, state, cluster, previousTypeId, target, inputs, fullMatch, earlyAdvance);
                    changed = true;
                    break;
                }
            }
        }

        return changed;
    }

    private @NotNull EvolutionInputs resolveInputs(
            @NotNull ServerLevel level,
            @NotNull CloudClusterState cluster,
            @NotNull CloudStructuralInputs structuralInputs
    ) {
        BlockPos samplePos = BlockPos.containing(
                cluster.getCenter().x(),
                cluster.getCenter().y(),
                cluster.getCenter().z()
        );
        RegionInstanceKey regionKey = RegionInstanceKey.from(samplePos);
        long tick = level.getGameTime();

        float humidity = normalizeHumidity(ForecastOrchestrator.getCurrentHumidity(regionKey, tick));
        float temperature = ForecastOrchestrator.getCurrentTemperature(regionKey, tick);
        float pressure = ForecastOrchestrator.getCurrentPressure(regionKey, tick);
        float stormChance = ForecastOrchestrator.getCurrentStormChance(regionKey, tick);

        RegionAtmosphereState atmosphereState = AtmosphericStateRegistry.getState(regionKey);
        float instability = atmosphereState == null
                ? fallbackInstability(cluster, humidity, temperature, stormChance)
                : computeInstability(atmosphereState, cluster, humidity, temperature, stormChance);

        float density = Mth.clamp(cluster.getDensity() * 0.86F + cluster.getMergePressure() * 0.08F + structuralInputs.neighborClusterDensity() * 0.06F, 0.0F, 1.0F);
        float coverage = Mth.clamp(cluster.getCoverage() * 0.88F + cluster.getMergePressure() * 0.06F + structuralInputs.groupCoverage() * 0.06F, 0.0F, 1.0F);
        float lift = computeLift(cluster, humidity, temperature, instability, stormChance);
        float mergePressure = cluster.getMergePressure();

        return new EvolutionInputs(humidity, temperature, instability, pressure, stormChance, density, coverage, lift, mergePressure, structuralInputs);
    }

    private float normalizeHumidity(float humidity) {
        float normalized = humidity > 1.5F ? humidity / 100.0F : humidity;
        return Mth.clamp(normalized, 0.0F, 1.0F);
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

    private boolean passesStructuralGate(
            @NotNull String currentTypeId,
            @NotNull String targetTypeId,
            @NotNull CloudStructuralInputs structure
    ) {
        if ("vapor_cluster".equals(currentTypeId) && "cumulus_humilis".equals(targetTypeId)) {
            return true;
        }
        if ("cumulus_mediocris".equals(targetTypeId)) {
            return structure.cloudSize() >= 56.0F || structure.nearbyClusterCount() >= 2 || structure.canMerge();
        }
        if ("cumulus_congestus".equals(targetTypeId)) {
            return structure.nearbyClusterCount() >= 2
                    && structure.mergedMass() >= 1.60F
                    && structure.groupRadius() >= 120.0F;
        }
        if ("cumulonimbus_calvus".equals(targetTypeId)) {
            return structure.nearbyClusterCount() >= 3
                    && structure.mergedMass() >= 2.50F
                    && structure.groupRadius() >= 200.0F
                    && structure.groupCoverage() >= 0.55F
                    && structure.canMerge();
        }
        if ("cumulonimbus_capillatus".equals(targetTypeId)) {
            return structure.mergedMass() >= 3.00F
                    && structure.groupRadius() >= 240.0F
                    && structure.groupCoverage() >= 0.65F;
        }
        if ("nimbostratus".equals(targetTypeId) || "stratocumulus".equals(targetTypeId)) {
            return structure.cloudSize() >= 72.0F || structure.nearbyClusterCount() >= 2;
        }
        return true;
    }

    private void logEvolution(
            @NotNull ServerLevel level,
            @NotNull CloudRegionState state,
            @NotNull CloudClusterState cluster,
            @NotNull String previousTypeId,
            @NotNull CloudEvolutionTarget target,
            @NotNull EvolutionInputs inputs,
            boolean fullMatch,
            boolean earlyAdvance
    ) {
        int satisfied = target.satisfiedCount(
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
        );
        int active = target.activeThresholdCount();

        ProjectAtmosphere.LOGGER.info(
                "[CloudEvolution] region={} cluster={} from={} to={} mode={} quota={}/{} worldTime={} center={} typeTicks={} reason={} inputs={}",
                state.getRegionId(),
                cluster.getClusterId(),
                previousTypeId,
                target.getTargetCloudTypeId(),
                fullMatch ? "threshold_match" : earlyAdvance ? "early_advance" : "unknown",
                satisfied,
                active,
                level.getGameTime(),
                formatVec(cluster.getCenter()),
                cluster.getCloudTypeTicks(),
                describeThresholds(target, cluster.getCloudTypeTicks(), inputs),
                formatInputs(inputs)
        );
    }

    private String describeThresholds(@NotNull CloudEvolutionTarget target, int cloudTypeTicks, @NotNull EvolutionInputs inputs) {
        List<String> checks = new ArrayList<>();
        appendCheck(checks, "ageTicks", cloudTypeTicks, ">=", target.getMinAgeTicks(), cloudTypeTicks >= target.getMinAgeTicks());
        if (isActiveThreshold(target.getMinHumidity())) {
            appendCheck(checks, "humidity", inputs.humidity(), ">=", target.getMinHumidity(), inputs.humidity() >= target.getMinHumidity());
        }
        if (isActiveThreshold(target.getMinInstability())) {
            appendCheck(checks, "instability", inputs.instability(), ">=", target.getMinInstability(), inputs.instability() >= target.getMinInstability());
        }
        if (isActiveThreshold(target.getMaxPressure())) {
            appendCheck(checks, "pressure", inputs.pressure(), "<=", target.getMaxPressure(), inputs.pressure() <= target.getMaxPressure());
        }
        if (isActiveThreshold(target.getMinStormChance())) {
            appendCheck(checks, "stormChance", inputs.stormChance(), ">=", target.getMinStormChance(), inputs.stormChance() >= target.getMinStormChance());
        }
        if (isActiveThreshold(target.getMinTemperature())) {
            appendCheck(checks, "temperature", inputs.temperature(), ">=", target.getMinTemperature(), inputs.temperature() >= target.getMinTemperature());
        }
        if (isActiveThreshold(target.getMaxTemperature())) {
            appendCheck(checks, "temperature", inputs.temperature(), "<=", target.getMaxTemperature(), inputs.temperature() <= target.getMaxTemperature());
        }
        if (isActiveThreshold(target.getMinDensity())) {
            appendCheck(checks, "density", inputs.density(), ">=", target.getMinDensity(), inputs.density() >= target.getMinDensity());
        }
        if (isActiveThreshold(target.getMinCoverage())) {
            appendCheck(checks, "coverage", inputs.coverage(), ">=", target.getMinCoverage(), inputs.coverage() >= target.getMinCoverage());
        }
        if (isActiveThreshold(target.getMinLift())) {
            appendCheck(checks, "lift", inputs.lift(), ">=", target.getMinLift(), inputs.lift() >= target.getMinLift());
        }
        if (isActiveThreshold(target.getMinMergePressure())) {
            appendCheck(checks, "mergePressure", inputs.mergePressure(), ">=", target.getMinMergePressure(), inputs.mergePressure() >= target.getMinMergePressure());
        }
        checks.add(String.format(Locale.ROOT, "chancePerCheck=%.3f", target.getChancePerCheck()));
        return String.join(", ", checks);
    }

    private void appendCheck(List<String> checks, String label, float actual, String operator, float target, boolean passed) {
        checks.add(String.format(Locale.ROOT, "%s=%s%s%.3f[%s]", label, formatFloat(actual), operator, target, passed ? "pass" : "fail"));
    }

    private void appendCheck(List<String> checks, String label, int actual, String operator, int target, boolean passed) {
        checks.add(String.format(Locale.ROOT, "%s=%d%s%d[%s]", label, actual, operator, target, passed ? "pass" : "fail"));
    }

    private boolean isActiveThreshold(float value) {
        return !Float.isNaN(value);
    }

    private String formatInputs(@NotNull EvolutionInputs inputs) {
        return String.format(
                Locale.ROOT,
                "humidity=%.3f temperature=%.3f instability=%.3f pressure=%.3f stormChance=%.3f density=%.3f coverage=%.3f lift=%.3f mergePressure=%.3f %s",
                inputs.humidity(),
                inputs.temperature(),
                inputs.instability(),
                inputs.pressure(),
                inputs.stormChance(),
                inputs.density(),
                inputs.coverage(),
                inputs.lift(),
                inputs.mergePressure(),
                inputs.structuralInputs().describe()
        );
    }

    private String formatVec(@NotNull net.minecraft.world.phys.Vec3 vec) {
        return String.format(Locale.ROOT, "%.1f,%.1f,%.1f", vec.x(), vec.y(), vec.z());
    }

    private String formatFloat(float value) {
        return String.format(Locale.ROOT, "%.3f", value);
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
            float mergePressure,
            CloudStructuralInputs structuralInputs
    ) {
    }
}
