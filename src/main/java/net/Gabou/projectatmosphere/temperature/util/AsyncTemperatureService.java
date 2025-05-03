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
        // no-op
    }

    public static void runAsync(Runnable task) {
        EXECUTOR.submit(task);
    }

    public static void shutdown() {
        EXECUTOR.shutdown();
    }
}
