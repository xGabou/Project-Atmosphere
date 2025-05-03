package net.Gabou.projectatmosphere.temperature.util;

import java.util.concurrent.*;

public class AsyncTemperatureService {
    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "TempCalcThread"));

    public static void init() {
        // nothing yet
    }

    public static void runAsync(Runnable task) {
        EXECUTOR.submit(task);
    }
    /** Gracefully shuts down the executor */
    public static void shutdown() {
        EXECUTOR.shutdown();
        // Or: EXECUTOR.shutdownNow(); if you need immediate interrupt
    }
}
