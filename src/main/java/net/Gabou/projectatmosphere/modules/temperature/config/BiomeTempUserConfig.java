package net.Gabou.projectatmosphere.modules.temperature.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads user-defined biome temperature ranges from a JSON file in the config directory
 * and merges them into the default mappings populated by {@link net.Gabou.projectatmosphere.modules.temperature.config.BiomeTempConfig}.
 */
public final class BiomeTempUserConfig {
    private static final String FILE_NAME = "biome_temps.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private BiomeTempUserConfig() {
    }

    public static void load() {
        try {
            Path configDir = FMLPaths.CONFIGDIR.get().resolve(ProjectAtmosphere.MODID);
            Files.createDirectories(configDir);
            Path file = configDir.resolve(FILE_NAME);

            if (Files.notExists(file)) {
                writeTemplate(file);
                ProjectAtmosphere.LOGGER.info("Created biome temperature config template at {}", file);
                return;
            }

            String raw = Files.readString(file, StandardCharsets.UTF_8);
            if (raw.isBlank()) {
                ProjectAtmosphere.LOGGER.warn("Biome temperature config is empty: {}", file);
                return;
            }

            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            JsonObject biomes = root.has("biomes") && root.get("biomes").isJsonObject()
                    ? root.getAsJsonObject("biomes")
                    : null;

            if (biomes == null) {
                ProjectAtmosphere.LOGGER.warn("No 'biomes' object found in {} — nothing to load.", file);
                return;
            }

            int applied = 0;
            for (Map.Entry<String, com.google.gson.JsonElement> e : biomes.entrySet()) {
                String key = e.getKey();
                ResourceLocation id = parseId(key);
                if (id == null) {
                    ProjectAtmosphere.LOGGER.warn("Skipping invalid biome id in config: {}", key);
                    continue;
                }

                if (!e.getValue().isJsonObject()) {
                    ProjectAtmosphere.LOGGER.warn("Skipping biome {} because its value is not an object.", key);
                    continue;
                }

                JsonObject obj = e.getValue().getAsJsonObject();

                RangeOrAll ro = parseRanges(obj);
                if (ro == null) {
                    ProjectAtmosphere.LOGGER.warn("Biome {} entry missing seasons or 'all' range — skipped.", key);
                    continue;
                }

                net.Gabou.projectatmosphere.modules.temperature.config.BiomeTempConfig.Range[] ranges = ro.asSeasonArray();
                net.Gabou.projectatmosphere.modules.temperature.config.BiomeTempConfig.Season[] seasons = net.Gabou.projectatmosphere.modules.temperature.config.BiomeTempConfig.Season.values();
                for (int i = 0; i < seasons.length; i++) {
                    net.Gabou.projectatmosphere.modules.temperature.config.BiomeTempConfig.Range r = ranges[i];
                    if (r == null) continue;
                    net.Gabou.projectatmosphere.modules.temperature.config.BiomeTempConfig.SEASON_RANGES.get(seasons[i]).put(id, r);
                    net.Gabou.projectatmosphere.modules.temperature.config.BiomeTempConfig.SEASON_CLAMPS.get(seasons[i]).put(id, BiomeTempConfig.deriveDaily(r));
                }
                applied++;
            }

            if (applied > 0) {
                ProjectAtmosphere.LOGGER.info("Applied {} biome temperature override(s) from {}", applied, file);
            } else {
                ProjectAtmosphere.LOGGER.info("No biome temperature overrides were applied from {}", file);
            }

        } catch (Exception ex) {
            ProjectAtmosphere.LOGGER.error("Failed loading biome temperature config", ex);
        }
    }

    private static ResourceLocation parseId(String s) {
        if (s == null) return null;
        s = s.trim();
        ResourceLocation parsed = ResourceLocation.tryParse(s);
        if (parsed != null) return parsed;
        try {
            return ResourceLocation.withDefaultNamespace(s);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void writeTemplate(Path file) throws IOException {
        Map<String, Object> root = new HashMap<>();
        Map<String, Object> biomes = new HashMap<>();

        Map<String, Object> sample1 = new HashMap<>();
        Map<String, Float> winter = new HashMap<>(); winter.put("min", -10f); winter.put("max", 5f);
        Map<String, Float> spring = new HashMap<>(); spring.put("min", 0f); spring.put("max", 15f);
        Map<String, Float> summer = new HashMap<>(); summer.put("min", 10f); summer.put("max", 25f);
        Map<String, Float> autumn = new HashMap<>(); autumn.put("min", 0f); autumn.put("max", 15f);
        sample1.put("winter", winter);
        sample1.put("spring", spring);
        sample1.put("summer", summer);
        sample1.put("autumn", autumn);
        biomes.put("minecraft:plains", sample1);

        Map<String, Object> sample2 = new HashMap<>();
        Map<String, Float> all = new HashMap<>(); all.put("min", 20f); all.put("max", 30f);
        sample2.put("all", all);
        biomes.put("examplemod:hot_biome", sample2);

        root.put("_note", "Add your biome ids under 'biomes'. Provide either per-season min/max or an 'all' min/max to apply to all seasons. Temperatures are in Celsius.");
        root.put("biomes", biomes);

        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write(GSON.toJson(root));
            w.write(System.lineSeparator());
        }
    }

    private static RangeOrAll parseRanges(JsonObject obj) {
        // Accept either an 'all' object or four seasonal objects
        net.Gabou.projectatmosphere.modules.temperature.config.BiomeTempConfig.Range all = parseRange(obj, "all");
        if (all != null) {
            return RangeOrAll.all(all);
        }

        net.Gabou.projectatmosphere.modules.temperature.config.BiomeTempConfig.Range w = parseRange(obj, "winter");
        net.Gabou.projectatmosphere.modules.temperature.config.BiomeTempConfig.Range sp = parseRange(obj, "spring");
        net.Gabou.projectatmosphere.modules.temperature.config.BiomeTempConfig.Range su = parseRange(obj, "summer");
        net.Gabou.projectatmosphere.modules.temperature.config.BiomeTempConfig.Range au = parseRange(obj, "autumn");

        if (w == null || sp == null || su == null || au == null) return null;
        return RangeOrAll.perSeason(w, sp, su, au);
    }

    private static net.Gabou.projectatmosphere.modules.temperature.config.BiomeTempConfig.Range parseRange(JsonObject parent, String key) {
        if (!parent.has(key) || !parent.get(key).isJsonObject()) return null;
        JsonObject o = parent.getAsJsonObject(key);
        if (!o.has("min") || !o.has("max")) return null;
        try {
            float min = o.get("min").getAsFloat();
            float max = o.get("max").getAsFloat();
            return new net.Gabou.projectatmosphere.modules.temperature.config.BiomeTempConfig.Range(min, max);
        } catch (Exception ex) {
            return null;
        }
    }

    private record RangeOrAll(net.Gabou.projectatmosphere.modules.temperature.config.BiomeTempConfig.Range winter,
                              net.Gabou.projectatmosphere.modules.temperature.config.BiomeTempConfig.Range spring,
                              net.Gabou.projectatmosphere.modules.temperature.config.BiomeTempConfig.Range summer,
                              net.Gabou.projectatmosphere.modules.temperature.config.BiomeTempConfig.Range autumn) {
        static RangeOrAll all(net.Gabou.projectatmosphere.modules.temperature.config.BiomeTempConfig.Range r) {
            return new RangeOrAll(r, r, r, r);
        }

        static RangeOrAll perSeason(net.Gabou.projectatmosphere.modules.temperature.config.BiomeTempConfig.Range w, net.Gabou.projectatmosphere.modules.temperature.config.BiomeTempConfig.Range sp, net.Gabou.projectatmosphere.modules.temperature.config.BiomeTempConfig.Range su, net.Gabou.projectatmosphere.modules.temperature.config.BiomeTempConfig.Range au) {
            return new RangeOrAll(w, sp, su, au);
        }

        net.Gabou.projectatmosphere.modules.temperature.config.BiomeTempConfig.Range[] asSeasonArray() {
            return new BiomeTempConfig.Range[]{winter, spring, summer, autumn};
        }
    }
}

