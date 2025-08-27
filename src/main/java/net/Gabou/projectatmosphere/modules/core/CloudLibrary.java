package net.Gabou.projectatmosphere.modules.core;

import net.minecraft.resources.ResourceLocation;
import java.util.Random;
import java.util.Set;

public class CloudLibrary {

    private static final Random RANDOM = new Random();

    private static final String[] SNOWSTORM_CLOUDS = {
            "snow",
            "nimbostratus",
            "severe_nimbostratus"
    };
    private static final Set<String> SNOW_CLOUDS = Set.of(SNOWSTORM_CLOUDS);

    private static final Set<String> THUNDER_CLOUDS = Set.of(
            "cumulonimbus",
            "severe_cumulonimbus",
            "tsegrus",
            "dense_tsegrus",
            "dark_wall",
            "custom_cumulonimbus"
    );

    private static final String[] SEVERITY_7_CLOUDS = {
            "cumulonimbus",
            "severe_cumulonimbus",
            "tsegrus",
            "dense_tsegrus",
            "dark_wall",
            "custom_cumulonimbus"
    };

    private static final String[] SEVERITY_6_CLOUDS = {
            "nimbostratus",
            "severe_nimbostratus"
    };

    private static final String[] SEVERITY_5_CLOUDS = {
            "stratocumulus",
            "dense_stratocumulus",
            "smaller_stratocumulus",
            "thicker_stratocumulus",
            "dithering",
            "islands",
            "pathway",
            "spots",
            "spotted",
            "stripe",
            "stripe_side"
    };

    private static final String[] SEVERITY_4_CLOUDS = {
            "stratus",
            "heavy_stratus",
            "overcast",
            "stronger_stratus",
            "floating_farlands",
            "mammatus_thin",
            "matrix",
            "pattern",
            "snow",
            "cookie",
            "balls"
    };

    private static final String[] SEVERITY_3_CLOUDS = {
            "cumulus",
            "dense_cumulus",
            "cumulus_noise",
            "tall_noise",
            "tall_weirdness"
    };

    private static final String[] SEVERITY_2_CLOUDS = {
            "small_cumulus",
            "itty_bitty_bigger"
    };

    private static final String[] SEVERITY_1_CLOUDS = {
            "itty_bitty",
            "dense_itty_bitty",
            "real_itty_bitty"
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
        return getRandomFrom(SNOWSTORM_CLOUDS);
    }

    public static boolean isThunderCloud(String id) {
        return THUNDER_CLOUDS.contains(id);
    }

    public static boolean isSnowCloud(String id) {
        return SNOW_CLOUDS.contains(id);
    }

    public static int getSeverityFromCloudId(String id) {
        return switch (id) {
            case "cumulonimbus", "severe_cumulonimbus", "tsegrus", "dense_tsegrus", "dark_wall", "custom_cumulonimbus" -> 7;
            case "nimbostratus", "severe_nimbostratus" -> 6;
            case "stratocumulus", "dense_stratocumulus", "smaller_stratocumulus", "thicker_stratocumulus", "dithering",
                    "islands", "pathway", "spots", "spotted", "stripe", "stripe_side" -> 5;
            case "stratus", "heavy_stratus", "overcast", "stronger_stratus", "floating_farlands", "mammatus_thin",
                    "matrix", "pattern", "snow", "cookie", "balls" -> 4;
            case "cumulus", "dense_cumulus", "cumulus_noise", "tall_noise", "tall_weirdness" -> 3;
            case "small_cumulus", "itty_bitty_bigger" -> 2;
            case "itty_bitty", "dense_itty_bitty", "real_itty_bitty" -> 1;
            default -> 1;
        };
    }
    public static int getSeverityFromRessourceLocation(ResourceLocation id) {
        return getSeverityFromCloudId(id.getPath());
    }

}
