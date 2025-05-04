// src/main/java/net/Gabou/projectatmosphere/manager/AtmosphereManager.java
package net.Gabou.projectatmosphere.manager;

import net.Gabou.projectatmosphere.modules.core.IAtmosphereModule;
import net.Gabou.projectatmosphere.modules.humidity.HumidityModule;
import net.Gabou.projectatmosphere.modules.pressure.PressureModule;
import net.Gabou.projectatmosphere.modules.storm.StormModule;
import net.Gabou.projectatmosphere.modules.temperature.TemperatureModule;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class AtmosphereManager {
    private static final List<IAtmosphereModule> MODULES = List.of(
            new TemperatureModule(),
            new StormModule(),
            new HumidityModule(),
            new PressureModule()
    );

    public static void onServerStarting(ServerLevel world) {
        BlockPos spawn = world.getSharedSpawnPos();
        MODULES.forEach(mod -> mod.init(world, spawn));
    }

    public static void onPlayerJoined(ServerLevel world, ServerPlayer player) {
        MODULES.forEach(mod -> mod.onPlayerJoined(world, player.blockPosition()));
    }

    public static void onPrecomputeProfiles(ServerLevel world) {
        MODULES.forEach(mod -> mod.onPrecomputeProfiles(world));
    }

    public static void onSwapProfiles(ServerLevel world) {
        MODULES.forEach(mod -> mod.onSwapProfiles(world));
    }

    public static void onSeasonChange(ServerLevel world) {
        BlockPos spawn = world.getSharedSpawnPos();
        MODULES.forEach(mod -> mod.onSeasonChange(world, spawn));
    }
    public static <T extends IAtmosphereModule> T getModule(Class<T> type) {
        return (T) MODULES.stream()
                .filter(type::isInstance)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Module not found: " + type.getName()));
    }
}
