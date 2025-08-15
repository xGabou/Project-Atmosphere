package net.Gabou.projectatmosphere.modules.core;

import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public record WindVector(float baseSpeed, float angleRadians, float gustSpeed) {
    private static final Map<BiomeInstanceKey, WindSample> CURRENT = new HashMap<>();

    public WindVector add(WindVector other) {
        return new WindVector(
                this.baseSpeed + other.baseSpeed,
                this.angleRadians + other.angleRadians,
                this.gustSpeed + other.gustSpeed
        );
    }

    public WindVector subtract(WindVector other) {
        return new WindVector(
                this.baseSpeed - other.baseSpeed,
                this.angleRadians - other.angleRadians,
                this.gustSpeed - other.gustSpeed
        );
    }

    public WindVector divide(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Count must be greater than zero");
        }
        return new WindVector(
                this.baseSpeed / count,
                this.angleRadians / count,
                this.gustSpeed / count
        );
    }

    
    public static WindVector fromBase(float baseSpeed, float angleRadians) {
        return new WindVector(baseSpeed, angleRadians, baseSpeed);
    }

    public static void set(BiomeInstanceKey key, float effectiveSpeed, float directionDeg) {
        CURRENT.put(key, new WindSample(effectiveSpeed, directionDeg));
    }

    public static WindSample getOrFallback(BiomeInstanceKey key, ServerLevel level) {
        return CURRENT.computeIfAbsent(key, k -> randomSample(new Random()));
    }

    private static WindSample randomSample(Random rng) {
        float speed = rng.nextFloat();
        float dir = rng.nextFloat() * 360f;
        return new WindSample(speed, dir);
    }

    public record WindSample(float speedMps, float directionDeg) { }
}
