package net.Gabou.projectatmosphere.modules.core;

import java.util.List;

public class CloudLibrary {

    public static String getCloudIdFromSeverity(int severity) {
        return switch (severity) {
            case 7 -> "cumulonimbus";
            case 6 -> "nimbostratus";
            case 5 -> "stratocumulus";
            case 4 -> "stratus";
            case 3 -> "cumulus";
            case 2 -> "small_cumulus";
            default -> "itty_bitty";
        };
    }

}
