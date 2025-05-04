// src/main/java/net/Gabou/projectatmosphere/modules/pressure/util/PressureStorageManager.java
package net.Gabou.projectatmosphere.modules.pressure.util;

import com.google.gson.*;
import net.Gabou.projectatmosphere.modules.core.BaseStorageManager;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.nio.file.Path;

/**
 * Persists double[7] pressure forecasts per biome.
 */
public class PressureStorageManager extends BaseStorageManager<double[]> {
    @Override
    protected Path getSavePath(ServerLevel world) {
        return AtmosphereUtils.getPerWorldSavePath(world, "pressure_forecasts.json");
    }

    @Override
    protected double[] parseForecast(JsonElement element) {
        JsonArray arr = element.getAsJsonArray();
        double[] week = new double[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            week[i] = arr.get(i).getAsDouble();
        }
        return week;
    }

    @Override
    protected JsonElement serializeForecast(double[] week) {
        JsonArray arr = new JsonArray();
        for (double v : week) {
            arr.add(v);
        }
        return arr;
    }
}
