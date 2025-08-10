package net.Gabou.projectatmosphere.modules.core;

import net.minecraft.resources.ResourceLocation;

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
    public static int getSeverityFromCloudId(String id) {
        return switch (id) {
            case "cumulonimbus" -> 7;
            case "nimbostratus" -> 6;
            case "stratocumulus" -> 5;
            case "stratus" -> 4;
            case "cumulus" -> 3;
            case "small_cumulus" -> 2;
            default -> 1; 
        };
    }
    public static int getSeverityFromRessourceLocation(ResourceLocation id) {
        return getSeverityFromCloudId(id.getPath());
    }

}
