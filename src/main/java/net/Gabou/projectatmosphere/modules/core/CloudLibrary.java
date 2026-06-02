package net.Gabou.projectatmosphere.modules.core;

import net.minecraft.resources.ResourceLocation;

import java.util.Random;
import java.util.Set;

public class CloudLibrary {

    private static final Random RANDOM = new Random();

    private static final String[] RAINSTORM_CLOUDS = {
            "heavy_stratus",
            "dense_stratocumulus",
            "overcast",
            "stratus",
            "floating_farlands",
            "thicker_stratocumulus",
            "nimbostratus",
            "severe_nimbostratus",
            "cumulus_congestus",
            "cumulus_mediocris",
            "mammatus_thin"

    };
    private static final Set<String> SNOW_CLOUDS = Set.of(RAINSTORM_CLOUDS);

    private static final Set<String> THUNDER_CLOUDS = Set.of(
            "cumulonimbus",
            "tsegrus",
            "stronger_stratus",
            "cookie",
            "severe_cumulonimbus",
            "dense_tsegrus",
            "dark_wall",
            "custom_cumulonimbus"
    );

    private static final String[] THUNDER_LOW_CLOUDS = {
            "cumulonimbus",
            "tsegrus",
            "custom_cumulonimbus",
            "nimbostratus"
    };

    private static final String[] THUNDER_HIGH_CLOUDS = {
            "severe_cumulonimbus",
            "dense_tsegrus",
            "dark_wall",
            "severe_nimbostratus"
    };

    private static final String[] SEVERITY_7_CLOUDS = {
            "severe_cumulonimbus",
            "dense_tsegrus",
            "dark_wall",
            "custom_cumulonimbus",
    };

    private static final String[] SEVERITY_6_CLOUDS = {
            "cumulonimbus",
            "tsegrus",
            "stronger_stratus",
            "cookie"
    };

    private static final String[] SEVERITY_5_CLOUDS = {
            "heavy_stratus",
            "dense_stratocumulus",
            "overcast",
            "severe_nimbostratus",
            "cumulus_congestus"

    };

    private static final String[] SEVERITY_4_CLOUDS = {
            "stratus",
            "nimbostratus",
            "floating_farlands",
            "thicker_stratocumulus",
            "cumulus_mediocris"
    };

    private static final String[] SEVERITY_3_CLOUDS = {
            "dense_itty_bitty",
            "stratocumulus",
            "cumulus",
            "mammatus_thin"
    };

    private static final String[] SEVERITY_2_CLOUDS = {
            "small_cumulus",
            "smaller_stratocumulus",
            "islands",
            "spots",
            "pattern",
            "balls",
            "cumulus_noise",
            "dense_cumulus",
            "tall_noise"

    };

    private static final String[] SEVERITY_1_CLOUDS = {
            "itty_bitty",
            "real_itty_bitty",
            "itty_bitty_bigger",
            "pathway",
            "spotted",
            "tall_weirdness"
    };

    private static String getRandomFrom(String[] clouds) {
        return clouds[RANDOM.nextInt(clouds.length)];
    }

    public static String getCloudIdFromSeverity(int severity) {
        return switch (severity) {
            case 7 -> getRandomFrom(SEVERITY_7_CLOUDS);
            case 6 -> getRandomFrom(SEVERITY_6_CLOUDS);
            case 5 -> getRandomFrom(SEVERITY_5_CLOUDS);
            case 4 -> getRandomFrom(SEVERITY_4_CLOUDS);
            case 3 -> getRandomFrom(SEVERITY_3_CLOUDS);
            case 2 -> getRandomFrom(SEVERITY_2_CLOUDS);
            default -> getRandomFrom(SEVERITY_1_CLOUDS);
        };
    }

    public static String getSnowstormCloudId() {
        return getRandomFrom(RAINSTORM_CLOUDS);
    }

    public static String getRandomThunderCloud(int intensity) {
        return intensity == 2 ? getRandomFrom(THUNDER_HIGH_CLOUDS) : getRandomFrom(THUNDER_LOW_CLOUDS);
    }

    public static String getRandomRainCloud(int intensity, boolean includeThunder) {
        if (includeThunder && RANDOM.nextInt(3) == 0) {
            return getRandomThunderCloud(intensity);
        }
        int severity = 4 + RANDOM.nextInt(2);
        return getCloudIdFromSeverity(severity);
    }

    public static boolean isThunderCloud(String id) {
        return THUNDER_CLOUDS.contains(id);
    }

    public static boolean isSnowCloud(String id) {
        return SNOW_CLOUDS.contains(id);
    }

    public static int getSeverityFromCloudId(String id) {
        return switch (id) {
            case "severe_cumulonimbus", "dense_tsegrus", "dark_wall", "custom_cumulonimbus", "severe_nimbostratus" -> 7;
            case "nimbostratus",
                 "cumulonimbus",
                 "tsegrus",
                 "stronger_stratus",
                 "cookie" -> 6;
            case "heavy_stratus",
                 "dense_stratocumulus",
                 "overcast" -> 5;
            case "stratus",
                 "floating_farlands",
                 "thicker_stratocumulus" -> 4;
            case "dense_itty_bitty",
                 "stratocumulus",
                 "cumulus" -> 3;
            case "small_cumulus",
                 "smaller_stratocumulus",
                 "islands",
                 "spots",
                 "pattern",
                 "balls",
                 "cumulus_noise",
                 "dense_cumulus",
                 "tall_noise" -> 2;
            case "itty_bitty",
                 "real_itty_bitty",
                 "itty_bitty_bigger",
                 "pathway",
                 "spotted",
                 "mammatus_thin",
                 "tall_weirdness" -> 1;
            default -> 0;
        };
    }

    public static int getSeverityFromRessourceLocation(ResourceLocation id) {
        return getSeverityFromCloudId(id.getPath());
    }

}
