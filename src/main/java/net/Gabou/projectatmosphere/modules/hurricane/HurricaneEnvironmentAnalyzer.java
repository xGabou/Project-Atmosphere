package net.Gabou.projectatmosphere.modules.hurricane;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.CycloneSnapshot;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.modules.core.CloudLibrary;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;

import java.util.List;

final class HurricaneEnvironmentAnalyzer {
    private HurricaneEnvironmentAnalyzer() {
    }

    static CycloneEnvironment analyzeCyclone(ServerLevel level, CycloneSnapshot snapshot) {
        List<RegionAtmosphereState> states = AtmosphericStateRegistry.snapshot();
        float sampleRadius = Math.max(snapshot.radius() * 1.45F, 480.0F);
        float oceanWeight = 0.0F;
        float warmOceanWeight = 0.0F;
        float humidityWeighted = 0.0F;
        float stormSignalWeighted = 0.0F;
        float totalWeight = 0.0F;

        for (RegionAtmosphereState state : states) {
            double distance = state.distanceTo(snapshot.centerX(), snapshot.centerZ());
            if (distance > sampleRadius) {
                continue;
            }

            float weight = 1.0F - (float) (distance / sampleRadius);
            totalWeight += weight;
            humidityWeighted += state.getHumidity() * weight;
            float stateStormSignal = Math.max(
                    state.getRainIntensity(),
                    Math.max(state.getCloudCover(), state.getCycloneRainFloor())
            );
            stormSignalWeighted += stateStormSignal * weight;

            BlockPos pos = state.getPosition();
            if (pos != null && level.getBiome(pos).is(BiomeTags.IS_OCEAN)) {
                oceanWeight += weight;
                if (state.getTemperature() >= 24.0F) {
                    warmOceanWeight += weight;
                }
            }
        }

        float convectiveCoverage = sampleConvectiveCoverage(level, snapshot);
        WindVector wind = resolveCycloneWind(level, snapshot);
        float warmOceanCoverage = totalWeight <= 0.0F ? 0.0F : warmOceanWeight / totalWeight;
        float totalOceanCoverage = totalWeight <= 0.0F ? 0.0F : oceanWeight / totalWeight;
        float meanHumidity = totalWeight <= 0.0F ? 0.0F : humidityWeighted / totalWeight;
        float stormSignal = totalWeight <= 0.0F ? 0.0F : stormSignalWeighted / totalWeight;
        float intensificationStrength = Mth.clamp(
                snapshot.intensity() * 0.45F
                        + warmOceanCoverage * 0.20F
                        + totalOceanCoverage * 0.10F
                        + convectiveCoverage * 0.15F
                        + stormSignal * 0.10F,
                0.0F,
                1.0F
        );

        return new CycloneEnvironment(
                totalOceanCoverage,
                warmOceanCoverage,
                convectiveCoverage,
                meanHumidity,
                stormSignal,
                intensificationStrength,
                wind
        );
    }

    private static float sampleConvectiveCoverage(ServerLevel level, CycloneSnapshot snapshot) {
        CloudManager<?> manager = CloudManager.get(level);
        if (manager == null) {
            return 0.0F;
        }

        float scanRadius = Math.max(snapshot.radius() * 1.6F, 520.0F);
        float strongest = 0.0F;
        for (CloudRegion cloud : manager.getClouds()) {
            String path = cloud.getCloudTypeId().getPath();
            if (!isConvectiveStorm(path)) {
                continue;
            }
            double edgeDistance = Math.max(
                    0.0D,
                    Math.sqrt(distanceToSqr(snapshot.centerX(), snapshot.centerZ(), cloud.getWorldX(), cloud.getWorldZ()))
                            - cloud.getWorldRadius()
            );
            if (edgeDistance > scanRadius) {
                continue;
            }
            float influence = 1.0F - (float) (edgeDistance / scanRadius);
            strongest = Math.max(strongest, Mth.clamp(influence, 0.0F, 1.0F));
        }
        return strongest;
    }

    private static WindVector resolveCycloneWind(ServerLevel level, CycloneSnapshot snapshot) {
        BlockPos anchor = new BlockPos(Mth.floor(snapshot.centerX()), level.getSeaLevel(), Mth.floor(snapshot.centerZ()));
        RegionInstanceKey key = RegionInstanceKey.from(anchor);
        WindVector sampled = ForecastOrchestrator.getWind(key, level.getGameTime());
        if (sampled == null) {
            return WindVector.fromBase(10.0F, 0.0F);
        }
        return sampled;
    }

    private static boolean isConvectiveStorm(String cloudId) {
        return CloudLibrary.isThunderCloud(cloudId)
                || cloudId.contains("cumulonimbus")
                || cloudId.contains("tsegrus")
                || cloudId.contains("dark_wall");
    }

    private static double distanceToSqr(float x1, float z1, double x2, double z2) {
        double dx = x1 - x2;
        double dz = z1 - z2;
        return dx * dx + dz * dz;
    }

    record CycloneEnvironment(
            float oceanCoverage,
            float warmOceanCoverage,
            float convectiveCoverage,
            float meanHumidity,
            float stormSignal,
            float intensificationStrength,
            WindVector wind
    ) {
        boolean formationEligible(CycloneSnapshot snapshot) {
            return snapshot.intensity() >= 0.58F
                    && this.warmOceanCoverage >= 0.35F
                    && this.convectiveCoverage >= 0.25F
                    && this.meanHumidity >= 0.68F
                    && this.stormSignal >= 0.56F;
        }

        boolean sustainEligible(CycloneSnapshot snapshot) {
            return snapshot.intensity() >= 0.42F
                    && this.oceanCoverage >= 0.20F
                    && this.warmOceanCoverage >= 0.15F
                    && this.stormSignal >= 0.42F;
        }

        HurricaneCategory targetCategory(CycloneSnapshot snapshot) {
            float strength = Mth.clamp(
                    snapshot.intensity() * 0.55F
                            + this.warmOceanCoverage * 0.20F
                            + this.convectiveCoverage * 0.15F
                            + this.stormSignal * 0.10F,
                    0.0F,
                    1.0F
            );
            return HurricaneCategory.fromStrength(strength);
        }
    }
}
