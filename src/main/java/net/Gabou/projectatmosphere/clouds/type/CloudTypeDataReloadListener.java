package net.Gabou.projectatmosphere.clouds.type;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.modules.weather.StormVisualTier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Loads optional PA cloud type overrides from data packs.
 */
public final class CloudTypeDataReloadListener extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final String DIRECTORY = "cloud_types";

    public CloudTypeDataReloadListener() {
        super(GSON, DIRECTORY);
    }

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new CloudTypeDataReloadListener());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<String, CloudTypeDefinition> loaded = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            try {
                JsonElement element = entry.getValue();
                if (element == null || !element.isJsonObject()) {
                    continue;
                }

                JsonObject root = element.getAsJsonObject();
                if (!isProjectAtmosphereSchema(root)) {
                    continue;
                }

                CloudTypeDefinition definition = parseDefinition(entry.getKey(), root);
                loaded.put(definition.getId(), definition);
            } catch (RuntimeException exception) {
                ProjectAtmosphere.LOGGER.warn("[CloudTypes] Failed to load cloud type {}.", entry.getKey(), exception);
            }
        }

        CloudTypeRegistry.replaceDataPackDefinitions(loaded);
        if (!loaded.isEmpty()) {
            ProjectAtmosphere.LOGGER.info("[CloudTypes] Loaded {} Project Atmosphere cloud type override(s).", loaded.size());
        }
    }

    private static boolean isProjectAtmosphereSchema(JsonObject root) {
        return root.has("family")
                || root.has("visual")
                || root.has("material")
                || root.has("shape")
                || root.has("spawn")
                || root.has("evolution");
    }

    private static CloudTypeDefinition parseDefinition(ResourceLocation resourceId, JsonObject root) {
        String id = stringValue(root, "id", idFromResource(resourceId));
        CloudTypeDefinition base = CloudTypeRegistry.getOrDefault(id);
        String displayName = stringValue(root, "displayName", stringValue(root, "display_name", base.getDisplayName()));
        CloudFamily family = enumValue(root, "family", CloudFamily.class, base.getFamily());
        CloudVisualProfile visual = parseVisual(objectValue(root, "visual"), base.getVisualProfile());
        CloudMaterialProfile material = parseMaterial(objectValue(root, "material"), base.getMaterialProfile()).withVisualDefaults(visual);
        CloudShapeProfile shape = parseShape(objectValue(root, "shape"), CloudShapeProfile.defaultFor(id, family, visual), id);
        StormVisualTier stormVisualTier = enumValue(root, "stormVisualTier", StormVisualTier.class,
                enumValue(root, "storm_visual_tier", StormVisualTier.class, base.getStormVisualTier()));
        CloudSpawnConditions spawn = parseSpawn(objectValue(root, "spawn"), base.getSpawnConditions());
        CloudEvolutionRules evolution = root.has("evolution") ? base.getEvolutionRules() : base.getEvolutionRules();

        return new CloudTypeDefinition(
                id,
                displayName,
                family,
                visual,
                material,
                shape,
                stormVisualTier,
                spawn,
                evolution
        );
    }

    private static CloudVisualProfile parseVisual(JsonObject object, CloudVisualProfile base) {
        if (object == null) {
            return base;
        }

        return new CloudVisualProfile(
                floatValue(object, "verticalThickness", base.getVerticalThickness()),
                floatValue(object, "edgeErosionStrength", base.getEdgeErosionStrength()),
                floatValue(object, "topSoftness", base.getTopSoftness()),
                floatValue(object, "baseSoftness", base.getBaseSoftness()),
                floatValue(object, "baseDarkness", base.getBaseDarkness()),
                floatValue(object, "noiseScale", base.getNoiseScale()),
                floatValue(object, "detailNoiseScale", base.getDetailNoiseScale()),
                floatValue(object, "erosionNoiseScale", base.getErosionNoiseScale()),
                floatValue(object, "densityMultiplier", base.getDensityMultiplier()),
                floatValue(object, "coverageMultiplier", base.getCoverageMultiplier()),
                floatValue(object, "heightSquash", base.getHeightSquash()),
                floatValue(object, "towerStrength", base.getTowerStrength()),
                floatValue(object, "anvilStrength", base.getAnvilStrength()),
                floatValue(object, "precipitationCoreStrength", base.getPrecipitationCoreStrength())
        );
    }

    private static CloudMaterialProfile parseMaterial(JsonObject object, CloudMaterialProfile base) {
        if (object == null) {
            return base;
        }

        return new CloudMaterialProfile(
                stringValue(object, "materialId", base.getMaterialId()),
                stringValue(object, "textureId", base.getTextureId()),
                floatValue(object, "darkness", base.getDarkness()),
                floatValue(object, "precipitationTint", base.getPrecipitationTint()),
                floatValue(object, "opacityBias", base.getOpacityBias()),
                floatValue(object, "undersideDarkness", base.getUndersideDarkness()),
                floatValue(object, "edgeErosion", base.getEdgeErosion()),
                floatValue(object, "stormCoreDarkening", base.getStormCoreDarkening()),
                floatValue(object, "shadowContribution", base.getShadowContribution()),
                floatValue(object, "lightningResponse", base.getLightningResponse())
        );
    }

    private static CloudShapeProfile parseShape(JsonObject object, CloudShapeProfile base, String cloudTypeId) {
        if (object == null) {
            return base;
        }

        return new CloudShapeProfile(
                stringValue(object, "shapeId", base.getShapeId().equals(CloudShapeProfile.DEFAULT.getShapeId()) ? "projectatmosphere:shape/" + cloudTypeId : base.getShapeId()),
                floatValue(object, "baseRadius", base.getBaseRadius()),
                floatValue(object, "baseOffset", base.getBaseOffset()),
                floatValue(object, "topOffset", base.getTopOffset()),
                intValue(object, "lobeCountMin", base.getLobeCountMin()),
                intValue(object, "lobeCountMax", base.getLobeCountMax()),
                floatValue(object, "lobeStrength", base.getLobeStrength()),
                floatValue(object, "verticalTilt", base.getVerticalTilt()),
                floatValue(object, "windShearStrength", base.getWindShearStrength()),
                floatValue(object, "cellSplitStrength", base.getCellSplitStrength()),
                floatValue(object, "towerNarrowing", base.getTowerNarrowing()),
                floatValue(object, "anvilSpread", base.getAnvilSpread()),
                floatValue(object, "baseFlattening", base.getBaseFlattening()),
                floatValue(object, "edgeRaggedness", base.getEdgeRaggedness()),
                floatValue(object, "stormWallStrength", base.getStormWallStrength())
        );
    }

    private static CloudSpawnConditions parseSpawn(JsonObject object, CloudSpawnConditions base) {
        if (object == null) {
            return base;
        }

        return new CloudSpawnConditions(
                floatValue(object, "minHumidity", base.getMinHumidity()),
                floatValue(object, "maxHumidity", base.getMaxHumidity()),
                floatValue(object, "minTemperature", base.getMinTemperature()),
                floatValue(object, "maxTemperature", base.getMaxTemperature()),
                floatValue(object, "minPressure", base.getMinPressure()),
                floatValue(object, "maxPressure", base.getMaxPressure()),
                floatValue(object, "minStormChance", base.getMinStormChance()),
                floatValue(object, "minInstability", base.getMinInstability()),
                floatValue(object, "minLift", base.getMinLift())
        );
    }

    private static String idFromResource(ResourceLocation resourceId) {
        String path = resourceId.getPath();
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static JsonObject objectValue(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String stringValue(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        String value = element.getAsString();
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static float floatValue(JsonObject object, String key, float fallback) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsFloat() : fallback;
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsInt() : fallback;
    }

    private static <T extends Enum<T>> T enumValue(JsonObject object, String key, Class<T> enumClass, T fallback) {
        String value = stringValue(object, key, "");
        if (value.isBlank()) {
            return fallback;
        }

        try {
            return Enum.valueOf(enumClass, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }
}
