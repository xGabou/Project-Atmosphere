package net.Gabou.projectatmosphere.temperature.spike;

import com.google.gson.*;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;


import java.io.*;
import java.nio.file.*;
import java.util.*;

import static net.Gabou.projectatmosphere.temperature.Temperature.getPerWorldSavePath;

public class SpikeStateStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String FILE_NAME = "spike_data.json";

    public static void saveAll(ServerLevel world) {
        AsyncAtmosphereService.runAsync(AsyncAtmosphereService.Branch.TEMPERATURE,() -> {
            JsonObject root = new JsonObject();

            // Example for saving SpikeManager state
            for (Map.Entry<ResourceLocation, SpikeState> entry : SpikeManager.getAllStates().entrySet()) {
                JsonObject data = new JsonObject();
                SpikeState state = entry.getValue();
                data.addProperty("daysSinceLastSpike", state.daysSinceLastSpike);
                data.addProperty("remainingSpikeDays", state.remainingSpikeDays);
                data.addProperty("currentSpikeDay", state.currentSpikeDay);
                data.addProperty("spikeMagnitude", state.spikeMagnitude);
                root.add(entry.getKey().toString(), data);
            }

            try {
                Path path = getPerWorldSavePath(world, FILE_NAME); // ✅ dynamically resolved path
                Files.createDirectories(path.getParent());
                try (Writer w = Files.newBufferedWriter(path)) {
                    GSON.toJson(root, w);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }


    public static void loadAll(ServerLevel world) {
        AsyncAtmosphereService.runAsync(AsyncAtmosphereService.Branch.TEMPERATURE,() -> {
            Path SAVE_PATH = getPerWorldSavePath(world, FILE_NAME);
            if (!Files.exists(SAVE_PATH)) return;

            try (Reader r = Files.newBufferedReader(SAVE_PATH)) {
                JsonObject root = GSON.fromJson(r, JsonObject.class);
                Map<ResourceLocation, SpikeState> loaded = new HashMap<>();
                for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                    ResourceLocation biome = new ResourceLocation(entry.getKey());
                    JsonObject obj = entry.getValue().getAsJsonObject();
                    SpikeState state = new SpikeState();
                    state.daysSinceLastSpike = obj.get("daysSinceLastSpike").getAsInt();
                    state.remainingSpikeDays = obj.get("remainingSpikeDays").getAsInt();
                    state.currentSpikeDay = obj.get("currentSpikeDay").getAsInt();
                    state.spikeMagnitude = obj.get("spikeMagnitude").getAsFloat();
                    loaded.put(biome, state);
                }
                SpikeManager.setAllStates(loaded);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
    public static void clearAll(ServerLevel world) {
        AsyncAtmosphereService.runAsync(AsyncAtmosphereService.Branch.TEMPERATURE,() -> {
            Path SAVE_PATH = getPerWorldSavePath(world, FILE_NAME);
            if (Files.exists(SAVE_PATH)) {
                try {
                    Files.delete(SAVE_PATH);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
