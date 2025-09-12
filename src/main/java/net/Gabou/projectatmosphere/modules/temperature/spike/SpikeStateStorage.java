package net.Gabou.projectatmosphere.modules.temperature.spike;

import com.google.gson.*;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.StorageUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;


import java.io.*;
import java.nio.file.*;
import java.util.*;

import static net.Gabou.projectatmosphere.util.StorageUtils.getPerWorldSavePath;


public class SpikeStateStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String FILE_NAME = "spike_data.json";

    public static void saveAll(ServerLevel world) {
            JsonObject root = new JsonObject();

            for (Map.Entry<BiomeInstanceKey, SpikeState> entry : SpikeManager.getAllStates().entrySet()) {
                JsonObject data = new JsonObject();
                SpikeState state = entry.getValue();

                data.addProperty("biome", entry.getKey().biomeType().toString());
                data.add("pos", AtmosphereUtils.serializeBlockPos(entry.getKey().samplePos()));

                data.addProperty("daysSinceLastSpike", state.daysSinceLastSpike);
                data.addProperty("remainingSpikeDays", state.remainingSpikeDays);
                data.addProperty("currentSpikeDay", state.currentSpikeDay);
                data.addProperty("spikeMagnitude", state.spikeMagnitude);

                root.add(entry.getKey().toString(), data);
            }

            try {
                Path path = getPerWorldSavePath(world, FILE_NAME);
                Files.createDirectories(path.getParent());
                try (Writer w = Files.newBufferedWriter(path)) {
                    GSON.toJson(root, w);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

    }



    public static void loadAll(ServerLevel world) {

            Path SAVE_PATH = getPerWorldSavePath(world, FILE_NAME);
            if (!Files.exists(SAVE_PATH)) return;

            try (Reader r = Files.newBufferedReader(SAVE_PATH)) {
                JsonObject root = GSON.fromJson(r, JsonObject.class);
                Map<BiomeInstanceKey, SpikeState> loaded = new HashMap<>();

                for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                    JsonObject obj = entry.getValue().getAsJsonObject();

                    ResourceLocation biome = ResourceLocation.parse(obj.get("biome").getAsString());
                    BlockPos pos = AtmosphereUtils.deserializeBlockPos(obj.get("pos").getAsJsonObject());

                    BiomeInstanceKey key = new BiomeInstanceKey(biome, pos);

                    SpikeState state = new SpikeState();
                    state.daysSinceLastSpike = obj.get("daysSinceLastSpike").getAsInt();
                    state.remainingSpikeDays = obj.get("remainingSpikeDays").getAsInt();
                    state.currentSpikeDay = obj.get("currentSpikeDay").getAsInt();
                    state.spikeMagnitude = obj.get("spikeMagnitude").getAsFloat();

                    loaded.put(key, state);
                }

                SpikeManager.setAllStates(loaded);
            } catch (IOException e) {
                e.printStackTrace();
            }

    }

    public static void clearAll(ServerLevel world) {

        SpikeManager.clearSpikeCache( world );
        StorageUtils.clearCache(world, FILE_NAME);
    }
}
