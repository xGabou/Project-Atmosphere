package net.Gabou.projectatmosphere.util;

import net.Gabou.projectatmosphere.ProjectAtmosphere;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Async runner sized by ProjectAtmosphere.SystemProfile.
 *
 * Public API preserved:
 *   - runStorm(Runnable)
 *   - runWeather(Runnable) -> CompletableFuture<Void>
 *   - runClient(Runnable)
 *
 * Call once at boot:
 *   AsyncAtmosphereService.init(isClient-> true|false);
        */
public final class AsyncAtmosphereService {

    private static volatile boolean initialized = false;

    /** pools */
    private static ThreadPoolExecutor WEATHER_POOL;
    private static ThreadPoolExecutor STORM_POOL;
    private static ThreadPoolExecutor CLIENT_POOL;
    private static ThreadPoolExecutor SHARED_POOL; // low-spec fallback

    private AsyncAtmosphereService() {}

    /** prefer this: we’ll size pools using the detected SystemProfile */
    public static void init(boolean isClient) {
        if (initialized) return;
        synchronized (AsyncAtmosphereService.class) {
            if (initialized) return;

            ProjectAtmosphere.SystemProfile profile = ProjectAtmosphere.SystemProfile.create(isClient);

            final int cpu = Math.max(1, profile.cpuCount);
            final long memMB = Math.max(1, profile.maxMemoryMB);
            final boolean lowSpec = profile.isLowSpec();
            final boolean goodGpu = profile.isGoodEnoughGPU();

            // choose mode
            final boolean USE_SHARED = lowSpec || cpu <= 4;
            final boolean SPLIT_POOLS = !USE_SHARED;

            // queue caps: smaller on low-mem
            final int baseQueue = memMB <= 2048 ? 256 : memMB <= 4096 ? 512 : 1024;
            final int weatherQueueCap = baseQueue;
            final int stormQueueCap   = baseQueue;
            final int clientQueueCap  = 256;

            // thread sizing helpers
            final int weatherCore = clamp( USE_SHARED ? 1 : Math.min(3, Math.max(2, cpu - 2)), 1, 8);
            final int weatherMax  = clamp( USE_SHARED ? 1 : Math.min(5, cpu * 2),                 weatherCore, 16);
            final int stormCore   = clamp( USE_SHARED ? 1 : Math.min(3, Math.max(2, cpu - 2)), 1, 8);
            final int stormMax    = clamp( USE_SHARED ? 1 : Math.min(5, cpu * 2),                 stormCore, 16);

            if (USE_SHARED) {
                SHARED_POOL = new ThreadPoolExecutor(
                        Math.min(2, Math.max(1, cpu - 1)), // 1–2 threads on low spec
                        Math.min(2, Math.max(1, cpu - 1)),
                        60L, TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(baseQueue),
                        namedFactory("SharedCalc"),
                        new ThreadPoolExecutor.CallerRunsPolicy()
                );
                SHARED_POOL.allowCoreThreadTimeOut(true);

                WEATHER_POOL = SHARED_POOL;
                STORM_POOL   = SHARED_POOL;

                ProjectAtmosphere.LOGGER.info(
                        "[AsyncAtmosphere] init(shared) | cpu={} mem={}MB gpuOK={} | shared(core={},q={})",
                        cpu, memMB, goodGpu, SHARED_POOL.getCorePoolSize(), baseQueue
                );
            } else {
                WEATHER_POOL = new ThreadPoolExecutor(
                        weatherCore,
                        weatherMax,
                        60L, TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(weatherQueueCap),
                        namedFactory("WeatherMgr"),
                        new ThreadPoolExecutor.CallerRunsPolicy()
                );
                WEATHER_POOL.allowCoreThreadTimeOut(true);

                STORM_POOL = new ThreadPoolExecutor(
                        stormCore,
                        stormMax,
                        60L, TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(stormQueueCap),
                        namedFactory("StormCalc"),
                        new ThreadPoolExecutor.CallerRunsPolicy()
                );
                STORM_POOL.allowCoreThreadTimeOut(true);

                ProjectAtmosphere.LOGGER.info(
                        "[AsyncAtmosphere] init(split)  | cpu={} mem={}MB gpuOK={} | weather(core={},max={},q={}) storm(core={},max={},q={})",
                        cpu, memMB, goodGpu,
                        weatherCore, weatherMax, weatherQueueCap,
                        stormCore, stormMax, stormQueueCap
                );
            }

            // client: ordered single-thread (bounded queue)
            CLIENT_POOL = new ThreadPoolExecutor(
                    1, 1,
                    30L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(clientQueueCap),
                    namedFactory("ClientMgr"),
                    new ThreadPoolExecutor.CallerRunsPolicy()
            );

            ProjectAtmosphere.LOGGER.info(
                    "[AsyncAtmosphere] client(core=1,q={}) | profile.gpu='{}'",
                    clientQueueCap, safeGpuName(profile)
            );

            initialized = true;
        }
    }

    /** legacy: keep for compatibility (defaults to server profile) */
    public static void init() {
        init(false);
    }

    // ----------------- public API (unchanged) -----------------

    public static void runStorm(Runnable task) {
        ensureInit();
        Objects.requireNonNull(task, "task");
        executeSafe(STORM_POOL, wrap(task, "Storm"));
    }

    public static CompletableFuture<Void> runWeather(Runnable task) {
        ensureInit();
        Objects.requireNonNull(task, "task");
        CompletableFuture<Void> cf = new CompletableFuture<>();
        executeSafe(WEATHER_POOL, () -> {
            try {
                wrap(task, "Weather").run();
                cf.complete(null);
            } catch (Throwable t) {
                cf.completeExceptionally(t);
            }
        });
        return cf;
    }

    public static void runClient(Runnable task) {
        ensureInit();
        Objects.requireNonNull(task, "task");
        executeSafe(CLIENT_POOL, wrap(task, "Client"));
    }

    public static void shutdown() {
        if (!initialized) return;
        synchronized (AsyncAtmosphereService.class) {
            if (!initialized) return;

            ProjectAtmosphere.LOGGER.info("[AsyncAtmosphere] shutdown");
            if (SHARED_POOL != null && SHARED_POOL == WEATHER_POOL) {
                // shared mode: shut it once
                shutdownPool("Shared", SHARED_POOL);
                WEATHER_POOL = null;
                STORM_POOL   = null;
                SHARED_POOL  = null;
            } else {
                shutdownPool("Weather", WEATHER_POOL);
                shutdownPool("Storm",   STORM_POOL);
            }
            shutdownPool("Client",  CLIENT_POOL);

            WEATHER_POOL = null;
            STORM_POOL   = null;
            CLIENT_POOL  = null;

            initialized = false;
        }
    }

    // ----------------- helpers -----------------

    private static void ensureInit() {
        if (!initialized) init(false);
    }

    private static void executeSafe(ExecutorService svc, Runnable r) {
        if (svc == null || svc.isShutdown()) {
            ProjectAtmosphere.LOGGER.warn("[AsyncAtmosphere] executor not available; running in caller thread");
            r.run();
            return;
        }
        try {
            svc.execute(r);
        } catch (RejectedExecutionException rex) {
            ProjectAtmosphere.LOGGER.warn("[AsyncAtmosphere] task rejected; running in caller thread", rex);
            r.run();
        }
    }

    private static Runnable wrap(Runnable r, String tag) {
        return () -> {
            try {
                 //uncomment for trace:
                 ProjectAtmosphere.LOGGER.debug("[{}] start on {}", tag,  Thread.currentThread().getName());
                r.run();
            } catch (Throwable t) {
                ProjectAtmosphere.LOGGER.error("[{}] task failed", tag, t);
                throw t;
            } finally {
                // ProjectAtmosphere.LOGGER.debug("[{}] end", tag);
            }
        };
    }

    private static ThreadFactory namedFactory(String base) {
        AtomicInteger idx = new AtomicInteger(1);
        return r -> {
            Thread t = new Thread(r, base + "-" + idx.getAndIncrement());
            t.setDaemon(false);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        };
    }

    private static void shutdownPool(String name, ExecutorService svc) {
        if (svc == null) return;
        svc.shutdown();
        try {
            if (!svc.awaitTermination(5, TimeUnit.SECONDS)) {
                ProjectAtmosphere.LOGGER.warn("[AsyncAtmosphere] {} pool timed out; forcing shutdownNow()", name);
                svc.shutdownNow();
            } else {
                ProjectAtmosphere.LOGGER.info("[AsyncAtmosphere] {} pool terminated", name);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            ProjectAtmosphere.LOGGER.warn("[AsyncAtmosphere] {} pool shutdown interrupted; forcing shutdownNow()", name);
            svc.shutdownNow();
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static String safeGpuName(ProjectAtmosphere.SystemProfile p) {
        try { return String.valueOf(p.getGPUName()); } catch (Throwable t) { return "unknown"; }
    }
}
