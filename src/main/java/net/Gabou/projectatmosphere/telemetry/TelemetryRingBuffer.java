package net.Gabou.projectatmosphere.telemetry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Lightweight bounded queue used by the telemetry collector. The buffer drops the oldest
 * entries to avoid unbounded memory growth and supports snapshotting for export.
 */
public final class TelemetryRingBuffer<T> {

    private final int capacity;
    private final ArrayDeque<T> queue;

    public TelemetryRingBuffer(int capacity) {
        this.capacity = Math.max(1, capacity);
        this.queue = new ArrayDeque<>(this.capacity);
    }

    public synchronized void add(T value) {
        if (queue.size() >= capacity) {
            queue.pollFirst();
        }
        queue.addLast(value);
    }

    public synchronized void addAll(Collection<T> values) {
        for (T value : values) {
            add(value);
        }
    }

    public synchronized List<T> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(queue));
    }

    public synchronized void replaceAll(Collection<T> values) {
        queue.clear();
        addAll(values);
    }

    public int capacity() {
        return capacity;
    }
}
