package net.Gabou.projectatmosphere.util;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AsyncAtmosphereService {
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final boolean FORCE_SHARED = AtmoCommonConfig.FORCE_SHARED_EXECUTOR.get();

    private static final boolean USE_TWO = !FORCE_SHARED && CPU_COUNT > 6 && CPU_COUNT <= 10;
    private static final boolean USE_FOUR = !FORCE_SHARED && CPU_COUNT > 10;
    private static final boolean USE_SHARED = FORCE_SHARED || CPU_COUNT <= 6;

    // Shared pool (1 for all)
    private static final ExecutorService SHARED_EXECUTOR = Executors.newFixedThreadPool(
            Math.max(2, CPU_COUNT - 1),
            r -> {
                ProjectAtmosphere.LOGGER.info("🔁 Creating SHARED executor pool (all async tasks)\nCPU: " + CPU_COUNT);
                Thread t = new Thread(r, "SharedCalcThread");
                t.setDaemon(true);
                return t;
            });

    // Grouped pools (2 executors)
    private static final ExecutorService GROUP_A_EXECUTOR = USE_TWO ? Executors.newFixedThreadPool(2, r -> {
        ProjectAtmosphere.LOGGER.info("🔄 Creating GROUP A executor (Temperature & Humidity)");
        Thread t = new Thread(r, "GroupAExecutor");
        t.setDaemon(true);
        return t;
    }) : null;

    private static final ExecutorService GROUP_B_EXECUTOR = USE_TWO ? Executors.newFixedThreadPool(2, r -> {
        ProjectAtmosphere.LOGGER.info("🔄 Creating GROUP B executor (Storm & Pressure)");
        Thread t = new Thread(r, "GroupBExecutor");
        t.setDaemon(true);
        return t;
    }) : null;

    // Individual pools (one per category)
    private static final ExecutorService TEMP_EXECUTOR = USE_SHARED ? SHARED_EXECUTOR :
            USE_TWO ? GROUP_A_EXECUTOR : Executors.newSingleThreadExecutor(r -> {
                ProjectAtmosphere.LOGGER.info("🧊 Creating TEMP executor");
                Thread t = new Thread(r, "TempCalcThread");
                t.setDaemon(true);
                return t;
            });

    private static final ExecutorService HUMIDITY_EXECUTOR = USE_SHARED ? SHARED_EXECUTOR :
            USE_TWO ? GROUP_A_EXECUTOR : Executors.newSingleThreadExecutor(r -> {
                ProjectAtmosphere.LOGGER.info("💧 Creating HUMIDITY executor");
                Thread t = new Thread(r, "HumidityCalcThread");
                t.setDaemon(true);
                return t;
            });

    private static final ExecutorService STORM_EXECUTOR = USE_SHARED ? SHARED_EXECUTOR :
            USE_TWO ? GROUP_B_EXECUTOR : Executors.newSingleThreadExecutor(r -> {
                ProjectAtmosphere.LOGGER.info("🌪 Creating STORM executor");
                Thread t = new Thread(r, "StormCalcThread");
                t.setDaemon(true);
                return t;
            });

    private static final ExecutorService PRESSION_EXECUTOR = USE_SHARED ? SHARED_EXECUTOR :
            USE_TWO ? GROUP_B_EXECUTOR : Executors.newSingleThreadExecutor(r -> {
                ProjectAtmosphere.LOGGER.info("🧪 Creating PRESSURE executor");
                Thread t = new Thread(r, "PressionCalcThread");
                t.setDaemon(true);
                return t;
            });

    public static void init() {}

    public static void runTemperature(Runnable task) {
        if (!TEMP_EXECUTOR.isShutdown()) TEMP_EXECUTOR.submit(task);
    }

    public static void runHumidity(Runnable task) {
        if (!HUMIDITY_EXECUTOR.isShutdown()) HUMIDITY_EXECUTOR.submit(task);
    }

    public static void runStorm(Runnable task) {
        if (!STORM_EXECUTOR.isShutdown()) STORM_EXECUTOR.submit(task);
    }

    public static void runPression(Runnable task) {
        if (!PRESSION_EXECUTOR.isShutdown()) PRESSION_EXECUTOR.submit(task);
    }

    public static void shutdown() {
        if (USE_SHARED) {
            SHARED_EXECUTOR.shutdown();
        } else if (USE_TWO) {
            if (GROUP_A_EXECUTOR != null) GROUP_A_EXECUTOR.shutdown();
            if (GROUP_B_EXECUTOR != null) GROUP_B_EXECUTOR.shutdown();
        } else {
            TEMP_EXECUTOR.shutdown();
            HUMIDITY_EXECUTOR.shutdown();
            STORM_EXECUTOR.shutdown();
            PRESSION_EXECUTOR.shutdown();
        }
    }
}
