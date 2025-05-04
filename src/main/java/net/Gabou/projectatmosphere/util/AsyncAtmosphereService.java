// src/main/java/net/Gabou/projectatmosphere/util/AsyncAtmosphereService.java
package net.Gabou.projectatmosphere.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AsyncAtmosphereService {
    private static final ExecutorService TEMP_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "TempCalcThread");
        t.setDaemon(true);
        return t;
    });
    private static final ExecutorService HUMIDITY_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "HumidityCalcThread");
        t.setDaemon(true);
        return t;
    });
    private static final ExecutorService STORM_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "StormCalcThread");
        t.setDaemon(true);
        return t;
    });
    private static final ExecutorService PRESSION_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "PressionCalcThread");
        t.setDaemon(true);
        return t;
    });

    /** No-op; ensures executors are created early */
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
        TEMP_EXECUTOR.shutdown();
        HUMIDITY_EXECUTOR.shutdown();
        STORM_EXECUTOR.shutdown();
        PRESSION_EXECUTOR.shutdown();
    }
}
