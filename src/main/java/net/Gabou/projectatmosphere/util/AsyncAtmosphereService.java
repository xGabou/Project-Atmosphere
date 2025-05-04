package net.Gabou.projectatmosphere.util;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AsyncAtmosphereService {

    private static ExecutorService TEMP_EXECUTOR, HUMIDITY_EXECUTOR, STORM_EXECUTOR, PRESSURE_EXECUTOR;
    private static ExecutorService SHARED_EXECUTOR, GROUP_A_EXECUTOR, GROUP_B_EXECUTOR;

    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;

        int CPU_COUNT = Runtime.getRuntime().availableProcessors();
        boolean forceShared;

        try {
            forceShared = /*AtmoCommonConfig.FORCE_SHARED_EXECUTOR.get()*/false;
        } catch (IllegalStateException e) {
            ProjectAtmosphere.LOGGER.warn("⚠ Tried to access config before it was ready. Defaulting to shared executor.");
            forceShared = true;
        }

        boolean USE_TWO = !forceShared && CPU_COUNT > 6 && CPU_COUNT <= 10;
        boolean USE_FOUR = !forceShared && CPU_COUNT > 10;
        boolean USE_SHARED = forceShared || CPU_COUNT <= 6;

        SHARED_EXECUTOR = Executors.newFixedThreadPool(Math.max(2, CPU_COUNT - 1), r -> {
            ProjectAtmosphere.LOGGER.info("🔁 Creating SHARED executor pool (all async tasks)\nCPU: " + CPU_COUNT);
            Thread t = new Thread(r, "SharedCalcThread");
            t.setDaemon(true);
            return t;
        });

        if (USE_TWO) {
            GROUP_A_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
                ProjectAtmosphere.LOGGER.info("🔄 Creating GROUP A executor (Temperature & Humidity)");
                Thread t = new Thread(r, "GroupAExecutor");
                t.setDaemon(true);
                return t;
            });
            GROUP_B_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
                ProjectAtmosphere.LOGGER.info("🔄 Creating GROUP B executor (Storm & Pressure)");
                Thread t = new Thread(r, "GroupBExecutor");
                t.setDaemon(true);
                return t;
            });
        }

        TEMP_EXECUTOR = USE_SHARED ? SHARED_EXECUTOR :
                USE_TWO ? GROUP_A_EXECUTOR : Executors.newSingleThreadExecutor(r -> {
                    ProjectAtmosphere.LOGGER.info("🧊 Creating TEMP executor");
                    Thread t = new Thread(r, "TempCalcThread");
                    t.setDaemon(true);
                    return t;
                });

        HUMIDITY_EXECUTOR = USE_SHARED ? SHARED_EXECUTOR :
                USE_TWO ? GROUP_A_EXECUTOR : Executors.newSingleThreadExecutor(r -> {
                    ProjectAtmosphere.LOGGER.info("💧 Creating HUMIDITY executor");
                    Thread t = new Thread(r, "HumidityCalcThread");
                    t.setDaemon(true);
                    return t;
                });

        STORM_EXECUTOR = USE_SHARED ? SHARED_EXECUTOR :
                USE_TWO ? GROUP_B_EXECUTOR : Executors.newSingleThreadExecutor(r -> {
                    ProjectAtmosphere.LOGGER.info("🌪 Creating STORM executor");
                    Thread t = new Thread(r, "StormCalcThread");
                    t.setDaemon(true);
                    return t;
                });

        PRESSURE_EXECUTOR = USE_SHARED ? SHARED_EXECUTOR :
                USE_TWO ? GROUP_B_EXECUTOR : Executors.newSingleThreadExecutor(r -> {
                    ProjectAtmosphere.LOGGER.info("🧪 Creating PRESSURE executor");
                    Thread t = new Thread(r, "PressureCalcThread");
                    t.setDaemon(true);
                    return t;
                });
    }

    public static void runTemperature(Runnable task) {
        if (TEMP_EXECUTOR != null && !TEMP_EXECUTOR.isShutdown()) TEMP_EXECUTOR.submit(task);
    }

    public static void runHumidity(Runnable task) {
        if (HUMIDITY_EXECUTOR != null && !HUMIDITY_EXECUTOR.isShutdown()) HUMIDITY_EXECUTOR.submit(task);
    }

    public static void runStorm(Runnable task) {
        if (STORM_EXECUTOR != null && !STORM_EXECUTOR.isShutdown()) STORM_EXECUTOR.submit(task);
    }

    public static void runPression(Runnable task) {
        if (PRESSURE_EXECUTOR != null && !PRESSURE_EXECUTOR.isShutdown()) PRESSURE_EXECUTOR.submit(task);
    }

    public static void shutdown() {
        if (SHARED_EXECUTOR != null) SHARED_EXECUTOR.shutdown();
        if (GROUP_A_EXECUTOR != null) GROUP_A_EXECUTOR.shutdown();
        if (GROUP_B_EXECUTOR != null) GROUP_B_EXECUTOR.shutdown();
        if (TEMP_EXECUTOR != null && TEMP_EXECUTOR != SHARED_EXECUTOR && TEMP_EXECUTOR != GROUP_A_EXECUTOR) TEMP_EXECUTOR.shutdown();
        if (HUMIDITY_EXECUTOR != null && HUMIDITY_EXECUTOR != SHARED_EXECUTOR && HUMIDITY_EXECUTOR != GROUP_A_EXECUTOR) HUMIDITY_EXECUTOR.shutdown();
        if (STORM_EXECUTOR != null && STORM_EXECUTOR != SHARED_EXECUTOR && STORM_EXECUTOR != GROUP_B_EXECUTOR) STORM_EXECUTOR.shutdown();
        if (PRESSURE_EXECUTOR != null && PRESSURE_EXECUTOR != SHARED_EXECUTOR && PRESSURE_EXECUTOR != GROUP_B_EXECUTOR) PRESSURE_EXECUTOR.shutdown();
    }
}
