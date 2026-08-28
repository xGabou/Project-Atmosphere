package net.Gabou.projectatmosphere.clouds.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

/**
 * Async OpenGL timer using timestamp query pairs.
 * Timestamp queries avoid nesting conflicts with other GL_TIME_ELAPSED users.
 */
public final class CloudGpuTimer {
    private static final double NS_TO_MS = 1.0D / 1_000_000.0D;
    private static final int QUERY_PAIR_COUNT = 8;

    private final int[] startQueries = new int[QUERY_PAIR_COUNT];
    private final int[] endQueries = new int[QUERY_PAIR_COUNT];
    private final boolean[] inFlight = new boolean[QUERY_PAIR_COUNT];
    private final long[] submittedFrames = new long[QUERY_PAIR_COUNT];
    private int nextIndex;
    private int activeIndex = -1;
    private long frameSerial;
    private long lastResultFrame = -1L;
    private long lastResultSerial;
    private boolean active;
    private boolean supported = true;
    private boolean hasResult;
    private float lastMilliseconds;

    public CloudGpuTimer() {
    }

    public void begin() {
        if (!supported || active || !RenderSystem.isOnRenderThread()) {
            return;
        }

        try {
            ensureQueries();
            poll();
            int queryIndex = findFreeQueryIndex();
            if (queryIndex < 0) {
                return;
            }

            GL33.glQueryCounter(startQueries[queryIndex], GL33.GL_TIMESTAMP);
            active = true;
            activeIndex = queryIndex;
        } catch (Throwable throwable) {
            disable();
        }
    }

    public void end() {
        if (!supported || !active || activeIndex < 0 || !RenderSystem.isOnRenderThread()) {
            return;
        }

        try {
            GL33.glQueryCounter(endQueries[activeIndex], GL33.GL_TIMESTAMP);
            frameSerial++;
            inFlight[activeIndex] = true;
            submittedFrames[activeIndex] = frameSerial;
            nextIndex = (activeIndex + 1) % QUERY_PAIR_COUNT;
            active = false;
            activeIndex = -1;
            poll();
        } catch (Throwable throwable) {
            disable();
        }
    }

    public void poll() {
        if (!supported || !RenderSystem.isOnRenderThread()) {
            return;
        }

        try {
            for (int i = 0; i < QUERY_PAIR_COUNT; i++) {
                if (!inFlight[i]) {
                    continue;
                }
                if (!isQueryReady(startQueries[i]) || !isQueryReady(endQueries[i])) {
                    continue;
                }

                long startNs = GL33.glGetQueryObjectui64(startQueries[i], GL15.GL_QUERY_RESULT);
                long endNs = GL33.glGetQueryObjectui64(endQueries[i], GL15.GL_QUERY_RESULT);
                lastMilliseconds = (float) (Math.max(0L, endNs - startNs) * NS_TO_MS);
                hasResult = true;
                lastResultFrame = submittedFrames[i];
                lastResultSerial++;
                inFlight[i] = false;
            }
        } catch (Throwable throwable) {
            disable();
        }
    }

    public float getLastMilliseconds() {
        return supported && hasResult ? lastMilliseconds : -1.0F;
    }

    public boolean isSupported() {
        return supported;
    }

    public boolean hasResult() {
        return supported && hasResult;
    }

    public int getLastResultAgeFrames() {
        if (!hasResult || lastResultFrame < 0L) {
            return -1;
        }
        return (int) Math.max(0L, frameSerial - lastResultFrame);
    }

    public int getPendingQueries() {
        int pending = 0;
        for (boolean queryInFlight : inFlight) {
            if (queryInFlight) {
                pending++;
            }
        }
        return pending;
    }

    /** Monotonically advances only when a new timestamp-pair result is available. */
    public long getLastResultSerial() {
        return lastResultSerial;
    }

    /** Releases timestamp-query objects so a renderer reload cannot leak GL names. */
    public void close() {
        if (!RenderSystem.isOnRenderThreadOrInit()) {
            throw new IllegalStateException("CloudGpuTimer.close must run on the render thread");
        }
        for (int i = 0; i < QUERY_PAIR_COUNT; i++) {
            if (startQueries[i] != 0) {
                GL15.glDeleteQueries(startQueries[i]);
                startQueries[i] = 0;
            }
            if (endQueries[i] != 0) {
                GL15.glDeleteQueries(endQueries[i]);
                endQueries[i] = 0;
            }
            inFlight[i] = false;
            submittedFrames[i] = 0L;
        }
        nextIndex = 0;
        activeIndex = -1;
        frameSerial = 0L;
        lastResultFrame = -1L;
        lastResultSerial = 0L;
        active = false;
        supported = true;
        hasResult = false;
        lastMilliseconds = 0.0F;
    }

    private void ensureQueries() {
        if (startQueries[0] != 0 && endQueries[0] != 0) {
            return;
        }

        for (int i = 0; i < QUERY_PAIR_COUNT; i++) {
            startQueries[i] = GL15.glGenQueries();
            endQueries[i] = GL15.glGenQueries();
        }
    }

    private boolean isQueryReady(int queryId) {
        return queryId != 0
                && GL15.glIsQuery(queryId)
                && GL15.glGetQueryObjecti(queryId, GL15.GL_QUERY_RESULT_AVAILABLE) != 0;
    }

    private int findFreeQueryIndex() {
        for (int i = 0; i < QUERY_PAIR_COUNT; i++) {
            int index = (nextIndex + i) % QUERY_PAIR_COUNT;
            if (!inFlight[index]) {
                return index;
            }
        }
        return -1;
    }

    private void disable() {
        supported = false;
        active = false;
        activeIndex = -1;
    }
}
