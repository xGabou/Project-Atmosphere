package net.Gabou.projectatmosphere.client;

/**
 * Shared (client-safe) state for on-screen overlay messages.
 * <p>
 * This class intentionally avoids any net.minecraft.client imports so it can be
 * referenced from common code (e.g. network payload handlers) without breaking
 * dedicated server classloading.
 */
public final class OverlayMessageState {
    private static volatile String message;
    private static volatile long displayUntilMillis;

    private OverlayMessageState() { }

    public static void show(String msg, long durationMillis) {
        message = msg;
        displayUntilMillis = System.currentTimeMillis() + Math.max(0L, durationMillis);
    }

    public static String getMessage() {
        return message;
    }

    public static long getDisplayUntilMillis() {
        return displayUntilMillis;
    }

    public static void clear() {
        message = null;
        displayUntilMillis = 0L;
    }
}

