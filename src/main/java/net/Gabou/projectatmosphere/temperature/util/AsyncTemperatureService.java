package net.Gabou.projectatmosphere.temperature.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AsyncTemperatureService {

    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "TempCalcThread");
                t.setDaemon(true);
                return t;
            });

    public static void init() {
        // No-op placeholder in case future initialization is needed
    }

    public static void runAsync(Runnable task) {
        if (EXECUTOR.isShutdown()) {
            System.err.println("AsyncTemperatureService is shut down! Task rejected: " + task);
            return;
        }
        EXECUTOR.submit(task);
    }

    public static void shutdown() {
        EXECUTOR.shutdown();
    }
}
