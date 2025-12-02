package net.Gabou.projectatmosphere.modules.region;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.StorageUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * RegionPersistence implementation that stores per-region fallback JSON alongside existing data files.
 */
public final class FileRegionPersistence implements RegionPersistence {
    private static final String FOLDER = "region_fallbacks";
    private final ServerLevel level;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public FileRegionPersistence(ServerLevel level) {
        this.level = level;
    }

    @Override
    public Optional<BiomeFallbackSnapshot> loadFallback(ForecastRegionId id) {
        Path path = pathFor(id);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try (Reader r = Files.newBufferedReader(path)) {
            JsonObject root = gson.fromJson(r, JsonObject.class);
            List<BiomeInstanceKey> sourceBiomes = new ArrayList<>();
            JsonArray biomesArr = root.getAsJsonArray("biomes");
            if (biomesArr != null) {
                for (var el : biomesArr) {
                    JsonObject obj = el.getAsJsonObject();
                    ResourceLocation biome = ResourceLocation.parse(obj.get("id").getAsString());
                    BlockPos pos = AtmosphereUtils.deserializeBlockPos(obj.get("pos").getAsJsonObject());
                    sourceBiomes.add(new BiomeInstanceKey(biome, pos));
                }
            }

            ForecastRegion.Section[] sections = new ForecastRegion.Section[8];
            JsonArray sectionsArr = root.getAsJsonArray("sections");
            for (int i = 0; i < sections.length; i++) {
                if (sectionsArr == null || i >= sectionsArr.size()) {
                    sections[i] = new ForecastRegion.Section(0f, null);
                    continue;
                }
                JsonObject obj = sectionsArr.get(i).getAsJsonObject();
                float factor = obj.get("factor").getAsFloat();
                BiomeForecastSnapshot snapshot = null;
                if (obj.has("biome")) {
                    JsonObject biomeObj = obj.getAsJsonObject("biome");
                    ResourceLocation biome = ResourceLocation.parse(biomeObj.get("id").getAsString());
                    BlockPos pos = AtmosphereUtils.deserializeBlockPos(biomeObj.get("pos").getAsJsonObject());
                    BiomeInstanceKey key = new BiomeInstanceKey(biome, pos);
                    float[][] temp = deserialize2d(obj.getAsJsonArray("temperature"));
                    float[][] hum = deserialize2d(obj.getAsJsonArray("humidity"));
                    float[][] pressure = deserialize2d(obj.getAsJsonArray("pressure"));
                    WindVector[] wind = deserializeWindWeek(obj.getAsJsonArray("wind"));
                    snapshot = new BiomeForecastSnapshot(key, temp, hum, pressure, wind);
                }
                sections[i] = new ForecastRegion.Section(factor, snapshot);
            }
            BiomeFallbackSnapshot fb = new BiomeFallbackSnapshot(id, sourceBiomes, sections);
            return Optional.of(fb);
        } catch (IOException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    @Override
    public BiomeFallbackSnapshot saveFallback(ForecastRegionId id, ForecastRegion.Section[] sections, List<BiomeInstanceKey> sourceBiomes) {
        JsonObject root = new JsonObject();
        root.addProperty("rx", id.rx());
        root.addProperty("rz", id.rz());
        root.addProperty("dimension", id.dimension().location().toString());

        JsonArray biomesArr = new JsonArray();
        for (BiomeInstanceKey key : sourceBiomes) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", key.biomeType().toString());
            obj.add("pos", AtmosphereUtils.serializeBlockPos(key.samplePos()));
            biomesArr.add(obj);
        }
        root.add("biomes", biomesArr);

        JsonArray sectionsArr = new JsonArray();
        for (ForecastRegion.Section section : sections) {
            JsonObject obj = new JsonObject();
            obj.addProperty("factor", section.factor());
            BiomeForecastSnapshot snap = section.snapshot();
            if (snap != null) {
                JsonObject biomeObj = new JsonObject();
                biomeObj.addProperty("id", snap.biomeKey().biomeType().toString());
                biomeObj.add("pos", AtmosphereUtils.serializeBlockPos(snap.biomeKey().samplePos()));
                obj.add("biome", biomeObj);
                obj.add("temperature", serialize2d(snap.temperatureCurve()));
                obj.add("humidity", serialize2d(snap.humidityCurve()));
                obj.add("pressure", serialize2d(snap.pressureCurve()));
                obj.add("wind", serializeWindWeek(snap.windCurve()));
            }
            sectionsArr.add(obj);
        }
        root.add("sections", sectionsArr);

        Path path = pathFor(id);
        try {
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path)) {
                gson.toJson(root, w);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new BiomeFallbackSnapshot(id, Collections.unmodifiableList(sourceBiomes), sections);
    }

    private Path pathFor(ForecastRegionId id) {
        String safeDim = id.dimension().location().toString().replace(":", "_");
        String fileName = FOLDER + "/" + id.rx() + "_" + id.rz() + "_" + safeDim + ".json";
        return StorageUtils.getPerWorldSavePath(level, fileName);
    }

    private static JsonArray serialize2d(float[][] data) {
        JsonArray arr = new JsonArray();
        if (data == null) {
            return arr;
        }
        for (float[] row : data) {
            JsonArray rowArr = new JsonArray();
            if (row != null) {
                for (float v : row) {
                    rowArr.add(v);
                }
            }
            arr.add(rowArr);
        }
        return arr;
    }

    private static float[][] deserialize2d(JsonArray arr) {
        if (arr == null) {
            return null;
        }
        float[][] out = new float[arr.size()][];
        for (int i = 0; i < arr.size(); i++) {
            JsonArray rowArr = arr.get(i).getAsJsonArray();
            float[] row = new float[rowArr.size()];
            for (int j = 0; j < rowArr.size(); j++) {
                row[j] = rowArr.get(j).getAsFloat();
            }
            out[i] = row;
        }
        return out;
    }

    private static JsonArray serializeWindWeek(WindVector[] week) {
        JsonArray arr = new JsonArray();
        if (week == null) {
            return arr;
        }
        for (WindVector w : week) {
            JsonObject obj = new JsonObject();
            if (w != null) {
                obj.addProperty("base", w.baseSpeed());
                obj.addProperty("angle", w.angleRadians());
                obj.addProperty("gust", w.gustSpeed());
            } else {
                obj.addProperty("base", 0f);
                obj.addProperty("angle", 0f);
                obj.addProperty("gust", 0f);
            }
            arr.add(obj);
        }
        return arr;
    }

    private static WindVector[] deserializeWindWeek(JsonArray arr) {
        if (arr == null) {
            return null;
        }
        WindVector[] week = new WindVector[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            JsonObject obj = arr.get(i).getAsJsonObject();
            float base = obj.has("base") ? obj.get("base").getAsFloat() : 0f;
            float angle = obj.has("angle") ? obj.get("angle").getAsFloat() : 0f;
            float gust = obj.has("gust") ? obj.get("gust").getAsFloat() : base;
            week[i] = new WindVector(base, angle, gust);
        }
        return week;
    }
}
