package net.Gabou.projectatmosphere.util;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;

import java.util.ArrayDeque;
import java.util.Queue;

public class CloudRegionQueue {

    private static final Queue<Entry> cloudRegionsQueue = new ArrayDeque<>();
    private static final Queue<Entry> cloudRegionsNextQueue = new ArrayDeque<>();
    private CloudRegionQueue() {
    }

    public static void shuffle() {
        cloudRegionsQueue.addAll(cloudRegionsNextQueue);
        cloudRegionsNextQueue.clear();
    }

    public static void enqueueAdd(CloudRegion region) {
        cloudRegionsNextQueue.add(new Entry(region, TaskType.ADD));

    }
    public static void enqueueRemove(CloudRegion region) {
        cloudRegionsNextQueue.add(new Entry(region, TaskType.REMOVE));

    }
    public static boolean isEmpty() {
        return cloudRegionsQueue.isEmpty();
    }

    public static Entry poll() {
        return cloudRegionsQueue.poll();
    }

    public static int size() {
        return cloudRegionsQueue.size();
    }

    public static int nextSize() {
        return cloudRegionsNextQueue.size();
    }

    public static void clear() {
        cloudRegionsQueue.clear();
        cloudRegionsNextQueue.clear();
    }

    public enum TaskType {
        REMOVE,
        ADD
    }


    public record Entry(CloudRegion region, TaskType type) {
    }
}
