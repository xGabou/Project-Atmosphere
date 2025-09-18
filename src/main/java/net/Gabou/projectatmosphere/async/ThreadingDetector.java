package net.Gabou.projectatmosphere.async;

import net.minecraft.server.level.ServerLevel;

public final class ThreadingDetector {
    public static boolean isMainThread(ServerLevel level) {
        return level.getServer().isSameThread();
    }
}