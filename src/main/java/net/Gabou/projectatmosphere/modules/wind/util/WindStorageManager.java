// src/main/java/net/Gabou/projectatmosphere/modules/wind/util/WindStorageManager.java
package net.Gabou.projectatmosphere.modules.wind.util;

import com.google.gson.*;
import net.Gabou.projectatmosphere.modules.core.BaseStorageManager;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.nio.file.Path;

/**
 * Persists float[7][2] wind forecasts per biome.
 */
public class WindStorageManager extends BaseStorageManager<float[][]> {
    @Override
    protected Path getSavePath(ServerLevel world) {
        return AtmosphereUtils.getPerWorldSavePath(world, "wind_forecasts.json");
    }

    @Override
    protected float[][] parseForecast(JsonElement element) {
        JsonArray weekArr = element.getAsJsonArray();
        float[][] week = new float[weekArr.size()][2];
        for (int i = 0; i < weekArr.size(); i++) {
            JsonArray day = weekArr.get(i).getAsJsonArray();
            week[i][0] = day.get(0).getAsFloat();
            week[i][1] = day.get(1).getAsFloat();
        }
        return week;
    }

    @Override
    protected JsonElement serializeForecast(float[][] week) {
        JsonArray weekArr = new JsonArray();
        for (var day : week) {
            JsonArray dayArr = new JsonArray();
            dayArr.add(day[0]);
            dayArr.add(day[1]);
            weekArr.add(dayArr);
        }
        return weekArr;
    }
}
