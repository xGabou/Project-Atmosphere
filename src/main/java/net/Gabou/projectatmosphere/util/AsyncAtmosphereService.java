package net.Gabou.projectatmosphere.util;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A centralized async service that gives each subsystem its own thread.
 */
public class AsyncAtmosphereService {

    public enum Branch {
        TEMPERATURE("TempCalcThread"),
        HUMIDITY   ("HumidityCalcThread"),
        PRESSURE   ("PressureCalcThread"),
        STORM      ("StormCalcThread");

        private final String threadName;
        Branch(String threadName) { this.threadName = threadName; }
        public String getThreadName() { return threadName; }
    }

    private static final Map<Branch, ExecutorService> EXECUTORS = new EnumMap<>(Branch.class);

    static {
        // create one single‐thread executor per branch
        for (Branch b : Branch.values()) {
            EXECUTORS.put(b, Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, b.getThreadName());
                t.setDaemon(true);
                return t;
            }));
        }
    }

    /** No‐op init in case you need to bootstrap something later. */
    public static void init() {}

    /**
     * Schedule a task on the given branch’s dedicated thread.
     */
    public static void runAsync(Branch branch, Runnable task) {
        ExecutorService exec = EXECUTORS.get(branch);
        if (exec == null || exec.isShutdown()) {
            System.err.println("AsyncAtmosphereService [" + branch + "] unavailable, task rejected: " + task);
            return;
        }
        exec.submit(task);
    }

    /**
     * Gracefully shut down all executors.
     */
    public static void shutdown() {
        for (ExecutorService exec : EXECUTORS.values()) {
            exec.shutdown();
        }
    }
}
