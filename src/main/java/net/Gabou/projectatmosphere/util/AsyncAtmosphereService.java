package net.Gabou.projectatmosphere.util;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AsyncAtmosphereService {
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final boolean FORCE_SHARED = AtmoCommonConfig.FORCE_SHARED_EXECUTOR.get();
    private static final ExecutorService SHARED_EXECUTOR = Executors.newFixedThreadPool(
            Math.max(2, CPU_COUNT - 1),
            r -> {
                ProjectAtmosphere.LOGGER.info("Creating shared executor thread" +
                        "\nYour CPU count is: " + CPU_COUNT +
                        "\nThe shared executor will be used.");
                Thread t = new Thread(r, "SharedCalcThread");
                t.setDaemon(true);
                return t;
            });

    private static final boolean USE_TWO = !FORCE_SHARED && CPU_COUNT > 6 && CPU_COUNT <= 10;
    private static final boolean USE_FOUR = !FORCE_SHARED && CPU_COUNT > 10;
    private static final boolean USE_SHARED = FORCE_SHARED || CPU_COUNT <= 6;

    private static final ExecutorService TEMP_EXECUTOR = USE_SHARED ? SHARED_EXECUTOR :
            USE_TWO ? SHARED_EXECUTOR : Executors.newSingleThreadExecutor(r -> {
                ProjectAtmosphere.LOGGER.info("Creating temperature executor thread.");
                Thread t = new Thread(r, "TempCalcThread");
                t.setDaemon(true);
                return t;
            });

    private static final ExecutorService HUMIDITY_EXECUTOR = USE_SHARED ? SHARED_EXECUTOR :
            USE_TWO ? SHARED_EXECUTOR : Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "HumidityCalcThread");
                t.setDaemon(true);
                return t;
            });

    private static final ExecutorService STORM_EXECUTOR = (USE_SHARED || USE_TWO) ? SHARED_EXECUTOR :
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "StormCalcThread");
                t.setDaemon(true);
                return t;
            });

    private static final ExecutorService PRESSION_EXECUTOR = (USE_SHARED || USE_TWO) ? SHARED_EXECUTOR :
            Executors.newSingleThreadExecutor(r -> {
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
        } else {
            TEMP_EXECUTOR.shutdown();
            HUMIDITY_EXECUTOR.shutdown();
            STORM_EXECUTOR.shutdown();
            PRESSION_EXECUTOR.shutdown();
        }
    }
}
