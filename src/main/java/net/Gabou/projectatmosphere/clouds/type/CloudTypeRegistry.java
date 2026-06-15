package net.Gabou.projectatmosphere.clouds.type;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Registre statique initial des types de nuages PA.
 */
public final class CloudTypeRegistry {

    public static final String DEFAULT_CLOUD_TYPE_ID = "vapor_cluster";
    private static final String[] RAIN_CLOUD_IDS = {
            "stratus_nebulosus",
            "stratocumulus",
            "cumulus_congestus",
            "nimbostratus"
    };

    private static final String[] THUNDER_CLOUD_IDS = {
            "cumulonimbus_calvus",
            "cumulonimbus_capillatus"
    };

    private static final Set<String> PRECIPITATING_CLOUD_IDS = Set.of(
            RAIN_CLOUD_IDS[0],
            RAIN_CLOUD_IDS[1],
            RAIN_CLOUD_IDS[2],
            RAIN_CLOUD_IDS[3],
            THUNDER_CLOUD_IDS[0],
            THUNDER_CLOUD_IDS[1]
    );

    private static final Map<String, CloudTypeDefinition> BUILT_IN_TYPES = new LinkedHashMap<>();
    private static final Map<String, CloudTypeDefinition> TYPES = new LinkedHashMap<>();

    static {
        register(new CloudTypeDefinition(
                DEFAULT_CLOUD_TYPE_ID,
                "Vapor cluster",
                CloudFamily.VAPOR,
                CloudMorphologyFamily.PUFF,
                new CloudVisualProfile(0.34F, 0.34F, 0.28F, 0.24F, 0.08F, 0.034F, 0.110F, 0.128F, 0.36F, 0.44F, 1.28F, 0.00F, 0.00F, 0.00F),
                fairWeather(0.28F, 0.82F),
                new CloudEvolutionRules(List.of(
                        new CloudEvolutionTarget("cumulus_humilis", minutes(4), 0.34F, 0.10F, 1018.0F, 0.02F, 0.12F, 0.16F, 0.06F, 0.05F, 0.003F)
                ))
        ));

        register(new CloudTypeDefinition(
                "cumulus_humilis",
                "Cumulus humilis",
                CloudFamily.CUMULUS,
                CloudMorphologyFamily.PUFF,
                new CloudVisualProfile(1.00F, 0.54F, 0.20F, 0.16F, 0.18F, 0.022F, 0.126F, 0.156F, 0.82F, 0.82F, 0.74F, 0.12F, 0.00F, 0.00F),
                fairWeather(0.35F, 0.95F),
                new CloudEvolutionRules(List.of(
                        new CloudEvolutionTarget("cumulus_mediocris", minutes(10), 0.50F, 0.20F, 1015.0F, 0.05F, 4.0F, 34.0F, 0.22F, 0.28F, 0.10F, 0.08F, 0.002F),
                        new CloudEvolutionTarget("stratocumulus", minutes(10), 0.58F, 0.16F, 1016.0F, 0.00F, -18.0F, 8.0F, 0.16F, 0.22F, 0.06F, 0.08F, 0.002F)
                ))
        ));

        register(new CloudTypeDefinition(
                "cumulus_mediocris",
                "Cumulus mediocris",
                CloudFamily.CUMULUS,
                CloudMorphologyFamily.PUFF,
                new CloudVisualProfile(1.48F, 0.54F, 0.24F, 0.16F, 0.28F, 0.018F, 0.126F, 0.156F, 1.00F, 0.96F, 0.62F, 0.42F, 0.00F, 0.00F),
                fairWeather(0.45F, 1.00F),
                new CloudEvolutionRules(List.of(
                        new CloudEvolutionTarget("cumulus_congestus", minutes(16), 0.60F, 0.38F, 1011.0F, 0.14F, 6.0F, 32.0F, 0.32F, 0.40F, 0.14F, 0.10F, 0.001F),
                        new CloudEvolutionTarget("nimbostratus", minutes(16), 0.70F, 0.24F, 1014.0F, 0.06F, -14.0F, 10.0F, 0.24F, 0.30F, 0.08F, 0.08F, 0.001F)
                ))
        ));

        register(new CloudTypeDefinition(
                "cumulus_congestus",
                "Cumulus congestus",
                CloudFamily.CUMULUS,
                CloudMorphologyFamily.TOWER,
                new CloudVisualProfile(2.05F, 0.60F, 0.28F, 0.13F, 0.42F, 0.015F, 0.128F, 0.168F, 1.18F, 1.02F, 0.42F, 0.84F, 0.03F, 0.10F),
                fairWeather(0.58F, 1.00F),
                new CloudEvolutionRules(List.of(
                        new CloudEvolutionTarget("cumulonimbus_calvus", minutes(24), 0.72F, 0.58F, 1007.0F, 0.28F, 8.0F, 28.0F, 0.44F, 0.56F, 0.18F, 0.14F, 0.0005F)
                ))
        ));

        register(new CloudTypeDefinition(
                "cumulonimbus_calvus",
                "Cumulonimbus calvus",
                CloudFamily.CUMULONIMBUS,
                CloudMorphologyFamily.STORM_ANVIL,
                new CloudVisualProfile(2.58F, 0.66F, 0.34F, 0.12F, 0.64F, 0.012F, 0.132F, 0.176F, 1.38F, 1.16F, 0.34F, 1.00F, 0.22F, 0.30F),
                stormReady(),
                new CloudEvolutionRules(List.of(
                        new CloudEvolutionTarget("cumulonimbus_capillatus", minutes(32), 0.78F, 0.72F, 1004.0F, 0.42F, 0.0F, 22.0F, 0.58F, 0.72F, 0.30F, 0.20F, 0.0002F)
                ))
        ));

        register(new CloudTypeDefinition(
                "cumulonimbus_capillatus",
                "Cumulonimbus capillatus",
                CloudFamily.CUMULONIMBUS,
                CloudMorphologyFamily.STORM_ANVIL,
                new CloudVisualProfile(2.78F, 0.70F, 0.46F, 0.12F, 0.74F, 0.011F, 0.140F, 0.184F, 1.48F, 1.22F, 0.30F, 1.00F, 0.88F, 0.48F),
                stormReady(),
                new CloudEvolutionRules(List.of())
        ));

        register(new CloudTypeDefinition(
                "stratus_nebulosus",
                "Stratus nebulosus",
                CloudFamily.STRATUS,
                CloudMorphologyFamily.SHEET,
                new CloudVisualProfile(0.24F, 0.14F, 0.50F, 0.46F, 0.42F, 0.008F, 0.044F, 0.058F, 0.72F, 1.20F, 3.70F, 0.00F, 0.00F, 0.00F),
                new CloudSpawnConditions(0.62F, 1.00F, -20.0F, 24.0F, 0.92F, 1.08F, 0.00F, 0.00F, 0.00F),
                new CloudEvolutionRules(List.of())
        ));

        register(new CloudTypeDefinition(
                "stratocumulus",
                "Stratocumulus",
                CloudFamily.STRATOCUMULUS,
                CloudMorphologyFamily.CELLULAR_SHEET,
                new CloudVisualProfile(0.62F, 0.50F, 0.34F, 0.24F, 0.36F, 0.014F, 0.092F, 0.128F, 0.82F, 0.96F, 2.35F, 0.04F, 0.00F, 0.00F),
                new CloudSpawnConditions(0.48F, 1.00F, -15.0F, 30.0F, 0.90F, 1.10F, 0.00F, 0.00F, 0.00F),
                new CloudEvolutionRules(List.of())
        ));

        register(new CloudTypeDefinition(
                "nimbostratus",
                "Nimbostratus",
                CloudFamily.NIMBOSTRATUS,
                CloudMorphologyFamily.SHEET,
                new CloudVisualProfile(0.34F, 0.16F, 0.52F, 0.40F, 0.82F, 0.009F, 0.054F, 0.070F, 1.12F, 1.28F, 3.20F, 0.00F, 0.00F, 0.54F),
                new CloudSpawnConditions(0.72F, 1.00F, -10.0F, 22.0F, 0.80F, 1.00F, 0.15F, 0.10F, 0.05F),
                new CloudEvolutionRules(List.of())
        ));

        register(new CloudTypeDefinition(
                "cirrus",
                "Cirrus",
                CloudFamily.CIRRUS,
                CloudMorphologyFamily.FILAMENT,
                new CloudVisualProfile(0.12F, 0.72F, 0.60F, 0.54F, 0.06F, 0.005F, 0.050F, 0.112F, 0.26F, 0.38F, 4.00F, 0.00F, 0.08F, 0.00F),
                new CloudSpawnConditions(0.15F, 0.75F, -60.0F, 0.0F, 0.80F, 1.15F, 0.00F, 0.00F, 0.00F),
                new CloudEvolutionRules(List.of())
        ));
    }

    private CloudTypeRegistry() {

    }

    public static Optional<CloudTypeDefinition> get(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(TYPES.get(id));
    }

    public static CloudTypeDefinition getOrDefault(String id) {
        return get(id).orElse(TYPES.get(DEFAULT_CLOUD_TYPE_ID));
    }

    public static Map<String, CloudTypeDefinition> getAll() {
        return Map.copyOf(TYPES);
    }

    public static synchronized void replaceDataPackDefinitions(Map<String, CloudTypeDefinition> dataPackDefinitions) {
        TYPES.clear();
        TYPES.putAll(BUILT_IN_TYPES);
        if (dataPackDefinitions == null || dataPackDefinitions.isEmpty()) {
            return;
        }

        for (CloudTypeDefinition definition : dataPackDefinitions.values()) {
            if (definition != null && definition.getId() != null && !definition.getId().isBlank()) {
                TYPES.put(definition.getId(), definition);
            }
        }
    }

    public static String getClearWeatherCloudId() {
        return DEFAULT_CLOUD_TYPE_ID;
    }

    public static String getRandomRainCloud(int intensity) {
        return pick(intensity >= 2 ? new String[] {
                "nimbostratus",
                "cumulus_congestus"
        } : RAIN_CLOUD_IDS);
    }

    public static String getRandomThunderCloud(int intensity) {
        return pick(intensity >= 2 ? new String[] {
                "cumulonimbus_capillatus"
        } : THUNDER_CLOUD_IDS);
    }

    public static boolean isRainCloud(String id) {
        return PRECIPITATING_CLOUD_IDS.contains(normalize(id));
    }

    public static boolean isThunderCloud(String id) {
        String normalized = normalize(id);
        for (String thunderCloudId : THUNDER_CLOUD_IDS) {
            if (thunderCloudId.equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isPrecipitatingCloud(String id) {
        return isRainCloud(id);
    }

    private static void register(CloudTypeDefinition definition) {
        BUILT_IN_TYPES.put(definition.getId(), definition);
        TYPES.put(definition.getId(), definition);
    }

    private static String pick(String[] ids) {
        return ids[ThreadLocalRandom.current().nextInt(ids.length)];
    }

    private static String normalize(String id) {
        return getOrDefault(id).getId();
    }

    private static CloudSpawnConditions fairWeather(float minHumidity, float maxHumidity) {
        return new CloudSpawnConditions(minHumidity, maxHumidity, -15.0F, 38.0F, 0.88F, 1.12F, 0.00F, 0.00F, 0.00F);
    }

    private static CloudSpawnConditions stormReady() {
        return new CloudSpawnConditions(0.66F, 1.00F, 4.0F, 42.0F, 0.78F, 0.99F, 0.22F, 0.45F, 0.18F);
    }

    private static int minutes(int minutes) {
        return 20 * 60 * Math.max(1, minutes);
    }
}
